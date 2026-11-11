package com.example.comunamadsistemacitas.data.remote

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.example.comunamadsistemacitas.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private val bookingIdempotencyLock = Any()

/** Dispatcher compartido y acotado: evita crear hilos por pantalla y colas ilimitadas. */
private object MarketplaceIoDispatcher {
    private val threadNumber = AtomicInteger(1)
    private val threadFactory = ThreadFactory { runnable ->
        Thread(runnable, "marketplace-io-${threadNumber.getAndIncrement()}").apply { isDaemon = true }
    }
    val executor = ThreadPoolExecutor(
        4,
        4,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue<Runnable>(96),
        threadFactory,
        ThreadPoolExecutor.AbortPolicy()
    )
}

data class MarketplaceBusinessPage(
    val items: JSONArray,
    val limit: Int,
    val hasMore: Boolean,
    val nextCursor: String?
)

class MarketplaceApiException(
    val statusCode: Int,
    val errorCode: String,
    override val message: String
) : IOException(message)

/**
 * Cliente HTTP del backend v1.
 * Las funciones síncronas deben llamarse desde un hilo IO; ejecutarAsync permite hacerlo desde UI.
 * En debug se admite HTTP para emulador/LAN. En release se exige HTTPS.
 */
class MarketplaceApiClient(
    context: Context,
    baseUrl: String,
    allowInsecureForDevelopment: Boolean = BuildConfig.DEBUG,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 15_000
) {
    private val rootUrl = baseUrl.trimEnd('/')
    private val session = MarketplaceSessionStore(context.applicationContext)
    private val bookingPrefs = context.applicationContext.getSharedPreferences("central_booking_idempotency", Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioExecutor = MarketplaceIoDispatcher.executor
    private val closed = AtomicBoolean(false)
    // Permite abortar sockets pendientes cuando el ViewModel/Activity libera el cliente.
    private val activeConnections = ConcurrentHashMap.newKeySet<HttpURLConnection>()

    init {
        val parsedUrl = runCatching { URI(rootUrl) }.getOrNull()
        val scheme = parsedUrl?.scheme
        require(scheme == "https" || (scheme == "http" && BuildConfig.DEBUG && allowInsecureForDevelopment)) {
            "Usa HTTPS. HTTP solo está permitido en compilaciones debug para desarrollo local."
        }
        require(parsedUrl?.host?.isNotBlank() == true) { "Configura una URL válida para el servidor marketplace." }
    }

    fun iniciarSesion(email: String, password: String): JSONObject {
        val response = post("/auth/login", JSONObject().put("email", email).put("password", password), false)
        val data = response.requiredObject("data")
        val token = data.optString("token").takeIf { it.isNotBlank() && it != "null" }
            ?: throw IOException("El servidor no devolvió un token de sesión válido.")
        val user = data.optJSONObject("user")?.requireIdentity("full_name", "perfil del usuario")
            ?: throw IOException("El servidor no devolvió el perfil del usuario.")
        session.guardarToken(token)
        return user
    }

    fun registrarCliente(nombre: String, email: String, telefono: String, password: String): JSONObject {
        val body = JSONObject()
            .put("fullName", nombre)
            .put("email", email)
            .put("phone", telefono)
            .put("password", password)
        val data = post("/auth/register/customer", body, false).requiredObject("data")
        val token = data.optString("token").takeIf { it.isNotBlank() && it != "null" }
            ?: throw IOException("El servidor registró la cuenta sin devolver un token válido.")
        val user = data.optJSONObject("user")?.requireIdentity("full_name", "perfil del cliente")
            ?: throw IOException("El servidor no devolvió el perfil del cliente.")
        session.guardarToken(token)
        return user
    }

    fun registrarNegocio(datos: JSONObject): JSONObject {
        val data = post("/auth/register/business", datos, false).requiredObject("data")
        val token = data.optString("token").takeIf { it.isNotBlank() && it != "null" }
            ?: throw IOException("El servidor registró el negocio sin devolver un token válido.")
        val business = data.optJSONObject("business")?.requireIdentity("name", "datos del negocio")
            ?: throw IOException("El servidor no devolvió los datos del negocio.")
        session.guardarToken(token)
        return business
    }

    /** Para un usuario que ya inició sesión y quiere abrir un segundo negocio. */
    fun crearOtroNegocio(datos: JSONObject): JSONObject =
        post("/businesses", datos, true).requiredObject("data").requireIdentity("name", "negocio creado")

    fun obtenerMiPerfil(): JSONObject = get("/me", true).requiredObject("data").requireIdentity("full_name", "perfil de usuario")

    fun obtenerMisNegocios(): JSONArray = get("/me/businesses", true).requiredArray("data")

    fun obtenerCategorias(): JSONArray = get("/categories", false).requiredArray("data")

    /** Primera página o siguiente bloque del catálogo, sin descargar todos los negocios. */
    fun explorarNegociosPagina(
        texto: String = "",
        categoriaId: String? = null,
        cursor: String? = null,
        limit: Int = 24
    ): MarketplaceBusinessPage {
        require(limit in 1..50) { "El tamaño de página debe estar entre 1 y 50." }
        val query = buildList {
            if (texto.isNotBlank()) add("q=${codificar(texto)}")
            if (!categoriaId.isNullOrBlank()) add("categoryId=${codificar(categoriaId)}")
            add("limit=$limit")
            if (!cursor.isNullOrBlank()) add("cursor=${codificar(cursor)}")
        }.joinToString("&")
        val response = get("/businesses?$query", false)
        val page = response.requiredObject("page")
        val pageLimit = page.optInt("limit", -1)
        if (pageLimit !in 1..50) throw IOException("El servidor devolvió un tamaño de página inválido.")
        val hasMore = page.opt("hasMore") as? Boolean
            ?: throw IOException("La respuesta de paginación está incompleta.")
        val nextCursor = page.optString("nextCursor").takeIf { it.isNotBlank() && it != "null" }
        if (hasMore && nextCursor == null) throw IOException("La respuesta de paginación no incluye el cursor siguiente.")
        return MarketplaceBusinessPage(
            items = response.requiredArray("data"),
            limit = pageLimit,
            hasMore = hasMore,
            nextCursor = nextCursor
        )
    }

    /** Compatibilidad con pantallas anteriores: devuelve solo el primer bloque. */
    fun explorarNegocios(texto: String = "", categoriaId: String? = null): JSONArray =
        explorarNegociosPagina(texto, categoriaId).items

    fun obtenerNegocio(negocioId: String): JSONObject =
        get("/businesses/${codificarPath(negocioId)}", false).requiredObject("data").requireIdentity("name", "negocio")

    fun obtenerPublicaciones(negocioId: String): JSONArray =
        get("/businesses/${codificarPath(negocioId)}/listings", false).requiredArray("data")

    fun publicar(negocioId: String, publicacion: JSONObject): JSONObject =
        post("/businesses/${codificarPath(negocioId)}/listings", publicacion, true).requiredObject("data")

    fun obtenerHorarios(negocioId: String): JSONArray =
        get("/businesses/${codificarPath(negocioId)}/hours", false).requiredArray("data")

    fun configurarHorarios(negocioId: String, horarios: JSONArray): JSONObject =
        request("PUT", "/businesses/${codificarPath(negocioId)}/hours", JSONObject().put("hours", horarios), true)

    fun obtenerDisponibilidad(negocioId: String, servicioId: String, fechaLocal: String): JSONArray {
        val query = "listingId=${codificar(servicioId)}&date=${codificar(fechaLocal)}"
        return get("/businesses/${codificarPath(negocioId)}/availability?$query", false).requiredArray("data")
    }

    fun obtenerTrabajadores(negocioId: String): JSONArray =
        get("/businesses/${codificarPath(negocioId)}/staff", true).requiredArray("data")

    fun crearTrabajador(negocioId: String, nombre: String, especialidad: String): JSONObject =
        post("/businesses/${codificarPath(negocioId)}/staff", JSONObject().put("name", nombre).put("specialty", especialidad), true).requiredObject("data")

    fun obtenerRecursos(negocioId: String): JSONArray =
        get("/businesses/${codificarPath(negocioId)}/resources", true).requiredArray("data")

    fun crearRecurso(negocioId: String, nombre: String, tipo: String = "CAPACITY"): JSONObject =
        post("/businesses/${codificarPath(negocioId)}/resources", JSONObject().put("name", nombre).put("kind", tipo), true).requiredObject("data")

    /**
     * Crea una cita/reserva usando una clave durable en el dispositivo.
     * Si la conexión se interrumpe después de que el servidor confirmó la operación,
     * el siguiente intento con el mismo contenido reutiliza la misma clave.
     * La clave pendiente se elimina solamente después de recibir una respuesta exitosa.
     */
    fun crearReserva(reserva: JSONObject): JSONObject {
        val token = session.obtenerToken()
            ?: throw MarketplaceApiException(401, "UNAUTHENTICATED", "Inicia sesión para continuar.")
        val accountId = cuentaDeToken(token)
        val signature = sha256("$accountId\n${reserva}")
        // Una clave por solicitud pendiente evita que una segunda cita sobrescriba
        // la clave de otra cuyo POST pudo confirmarse aunque se perdiera la respuesta.
        val preferenceKey = "pending_key:$signature"
        val idempotencyKey = synchronized(bookingIdempotencyLock) {
            bookingPrefs.getString(preferenceKey, null)?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString().also { key ->
                check(bookingPrefs.edit().putString(preferenceKey, key).commit()) {
                    "No se pudo guardar la clave de la reserva para reintentos seguros."
                }
            }
        }

        val response = request("POST", "/me/reservations", reserva, true, idempotencyKey)
            .requiredObject("data").requireFields("id", "code", "status", "respuesta de reserva")
        synchronized(bookingIdempotencyLock) { bookingPrefs.edit().remove(preferenceKey).commit() }
        return response
    }

    fun obtenerMisReservas(): JSONArray = get("/me/reservations", true).requiredArray("data")

    fun cancelarMiReserva(reservaId: String): JSONObject =
        post("/me/reservations/${codificarPath(reservaId)}/cancel", JSONObject(), true).requiredObject("data")

    fun obtenerReservasNegocio(negocioId: String): JSONArray =
        get("/businesses/${codificarPath(negocioId)}/reservations", true).requiredArray("data")

    fun cambiarEstadoReserva(negocioId: String, reservaId: String, estado: String): JSONObject =
        request(
            "PATCH",
            "/businesses/${codificarPath(negocioId)}/reservations/${codificarPath(reservaId)}/status",
            JSONObject().put("status", estado),
            true
        ).requiredObject("data")

    fun guardarFavorito(negocioId: String): JSONObject =
        post("/me/favorites", JSONObject().put("businessId", negocioId), true)

    fun obtenerFavoritos(): JSONArray = get("/me/favorites", true).requiredArray("data")

    fun cerrarSesion() = session.cerrarSesion()

    /** Indica si el dispositivo conserva un token JWT legible y no corrupto. */
    fun haySesion(): Boolean = session.hayTokenGuardado()

    fun <T> ejecutarAsync(operacion: () -> T, callback: (Result<T>) -> Unit) {
        if (closed.get()) return
        try {
            ioExecutor.execute {
                val result = runCatching(operacion)
                if (!closed.get()) {
                    mainHandler.post {
                        if (!closed.get()) callback(result)
                    }
                }
            }
        } catch (_: RejectedExecutionException) {
            if (!closed.get()) {
                mainHandler.post {
                    if (!closed.get()) callback(Result.failure(IOException("Hay demasiadas solicitudes pendientes. Inténtalo nuevamente.")))
                }
            }
        }
    }

    /** Cierra los callbacks de esta pantalla; el dispatcher compartido sigue atendiendo otras pantallas. */
    fun liberar() {
        if (!closed.compareAndSet(false, true)) return
        activeConnections.toList().forEach { connection ->
            runCatching { connection.disconnect() }
            activeConnections.remove(connection)
        }
    }

    private fun get(path: String, authenticated: Boolean): JSONObject = request("GET", path, null, authenticated)

    private fun post(path: String, body: JSONObject, authenticated: Boolean): JSONObject =
        request("POST", path, body, authenticated)

    private fun request(method: String, path: String, body: JSONObject?, authenticated: Boolean, idempotencyKey: String? = null): JSONObject {
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "La solicitud HTTP intentó ejecutarse en el hilo principal. Usa Dispatchers.IO o ejecutarAsync()."
        }
        check(!closed.get()) { "El cliente HTTP ya fue liberado." }
        check(!closed.get()) { "El cliente HTTP ya fue liberado." }
        val connection = (URI("$rootUrl/api/v1$path").toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            doInput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (idempotencyKey != null) setRequestProperty("Idempotency-Key", idempotencyKey)
            if (authenticated) {
                val token = session.obtenerToken()
                    ?: throw MarketplaceApiException(401, "UNAUTHENTICATED", "Inicia sesión para continuar.")
                setRequestProperty("Authorization", "Bearer $token")
            }
            if (body != null) doOutput = true
        }
        activeConnections.add(connection)
        if (closed.get()) {
            activeConnections.remove(connection)
            connection.disconnect()
            throw IOException("El cliente HTTP fue liberado antes de iniciar la solicitud.")
        }
        try {
            if (body != null) {
                connection.outputStream.use { output ->
                    output.write(body.toString().toByteArray(Charsets.UTF_8))
                }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use { input -> BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText() }.orEmpty()
            val response = if (text.isBlank()) JSONObject() else runCatching { JSONObject(text) }.getOrElse {
                throw IOException("El servidor devolvió una respuesta no válida.")
            }
            if (status !in 200..299) {
                if (status == 401) session.cerrarSesion()
                val error = response.optJSONObject("error") ?: JSONObject()
                throw MarketplaceApiException(
                    status,
                    error.optString("code", "HTTP_ERROR"),
                    error.optString("message", "No se pudo completar la solicitud.")
                )
            }
            return response
        } finally {
            activeConnections.remove(connection)
            connection.disconnect()
        }
    }

    private fun JSONObject.requiredObject(key: String): JSONObject = optJSONObject(key)
        ?: throw IOException("La respuesta del servidor está incompleta: falta el objeto '$key'.")

    private fun JSONObject.requiredArray(key: String): JSONArray = optJSONArray(key)
        ?: throw IOException("La respuesta del servidor está incompleta: falta la lista '$key'.")

    private fun JSONObject.requireFields(vararg fieldsAndLabel: String): JSONObject {
        require(fieldsAndLabel.size >= 2) { "Se requiere al menos un campo y una etiqueta." }
        val label = fieldsAndLabel.last()
        val fields = fieldsAndLabel.dropLast(1)
        for (field in fields) {
            val value = (opt(field) as? String)?.trim()
            if (value.isNullOrBlank() || value == "null") {
                throw IOException("La respuesta del servidor está incompleta: falta '$field' en $label.")
            }
        }
        return this
    }

    private fun JSONObject.requireIdentity(nameField: String, label: String): JSONObject =
        requireFields("id", nameField, label)

    private fun codificar(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
    private fun codificarPath(value: String): String = codificar(value).replace("+", "%20")

    private fun cuentaDeToken(token: String): String = runCatching {
        val payload = token.split('.').getOrNull(1) ?: error("JWT inválido")
        val decoded = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        JSONObject(decoded.toString(Charsets.UTF_8)).getString("sub").also { require(it.isNotBlank()) }
    }.getOrElse {
        // Compatibilidad defensiva: el servidor seguirá validando la autenticidad del token.
        sha256(token)
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}
