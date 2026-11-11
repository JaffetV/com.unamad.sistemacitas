package com.example.comunamadsistemacitas.data.repository

import android.content.Context
import com.example.comunamadsistemacitas.data.datasource.remote.BusinessRemoteDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

interface BusinessRepositoryContract {
    fun haySesion(): Boolean
    fun cerrarSesion()
    suspend fun obtenerMiPerfil(): JSONObject
    suspend fun obtenerCategorias(): JSONArray
    suspend fun registrarNegocio(datos: JSONObject): JSONObject
    suspend fun crearOtroNegocio(datos: JSONObject): JSONObject
    suspend fun obtenerMisNegocios(): JSONArray
    suspend fun obtenerMisReservas(): JSONArray
    suspend fun cancelarMiReserva(reservaId: String): JSONObject
    suspend fun obtenerNegocio(negocioId: String): JSONObject
    suspend fun obtenerPublicaciones(negocioId: String): JSONArray
    suspend fun obtenerReservasNegocio(negocioId: String): JSONArray
    suspend fun obtenerHorarios(negocioId: String): JSONArray
    suspend fun obtenerTrabajadores(negocioId: String): JSONArray
    suspend fun obtenerRecursos(negocioId: String): JSONArray
    suspend fun crearTrabajador(negocioId: String, nombre: String, especialidad: String): JSONObject
    suspend fun crearRecurso(negocioId: String, nombre: String, tipo: String = "CAPACITY"): JSONObject
    suspend fun cambiarEstadoReserva(negocioId: String, reservaId: String, estado: String): JSONObject
    suspend fun publicar(negocioId: String, publicacion: JSONObject): JSONObject
    suspend fun configurarHorarios(negocioId: String, horarios: JSONArray): JSONObject
    fun close()
}

/** Repositorio de negocios: desvincula la UI y ViewModels de los detalles HTTP de MarketplaceApiClient. */
class BusinessRepository(context: Context) : BusinessRepositoryContract {
    private val remote = BusinessRemoteDataSource(context.applicationContext)

    override fun haySesion(): Boolean = remote.haySesion()
    override fun cerrarSesion() = remote.cerrarSesion()
    override suspend fun obtenerMiPerfil(): JSONObject = withContext(Dispatchers.IO) { remote.obtenerMiPerfil() }
    override suspend fun obtenerCategorias(): JSONArray = withContext(Dispatchers.IO) { remote.obtenerCategorias() }
    override suspend fun registrarNegocio(datos: JSONObject): JSONObject = withContext(Dispatchers.IO) { remote.registrarNegocio(datos) }
    override suspend fun crearOtroNegocio(datos: JSONObject): JSONObject = withContext(Dispatchers.IO) { remote.crearOtroNegocio(datos) }
    override suspend fun obtenerMisNegocios(): JSONArray = withContext(Dispatchers.IO) { remote.obtenerMisNegocios() }
    override suspend fun obtenerMisReservas(): JSONArray = withContext(Dispatchers.IO) { remote.obtenerMisReservas() }
    override suspend fun cancelarMiReserva(reservaId: String): JSONObject = withContext(Dispatchers.IO) { remote.cancelarMiReserva(reservaId) }
    override suspend fun obtenerNegocio(negocioId: String): JSONObject = withContext(Dispatchers.IO) { remote.obtenerNegocio(negocioId) }
    override suspend fun obtenerPublicaciones(negocioId: String): JSONArray = withContext(Dispatchers.IO) { remote.obtenerPublicaciones(negocioId) }
    override suspend fun obtenerReservasNegocio(negocioId: String): JSONArray = withContext(Dispatchers.IO) { remote.obtenerReservasNegocio(negocioId) }
    override suspend fun obtenerHorarios(negocioId: String): JSONArray = withContext(Dispatchers.IO) { remote.obtenerHorarios(negocioId) }
    override suspend fun obtenerTrabajadores(negocioId: String): JSONArray = withContext(Dispatchers.IO) { remote.obtenerTrabajadores(negocioId) }
    override suspend fun obtenerRecursos(negocioId: String): JSONArray = withContext(Dispatchers.IO) { remote.obtenerRecursos(negocioId) }
    override suspend fun crearTrabajador(negocioId: String, nombre: String, especialidad: String): JSONObject = withContext(Dispatchers.IO) { remote.crearTrabajador(negocioId, nombre, especialidad) }
    override suspend fun crearRecurso(negocioId: String, nombre: String, tipo: String): JSONObject = withContext(Dispatchers.IO) { remote.crearRecurso(negocioId, nombre, tipo) }
    override suspend fun cambiarEstadoReserva(negocioId: String, reservaId: String, estado: String): JSONObject = withContext(Dispatchers.IO) { remote.cambiarEstadoReserva(negocioId, reservaId, estado) }
    override suspend fun publicar(negocioId: String, publicacion: JSONObject): JSONObject = withContext(Dispatchers.IO) { remote.publicar(negocioId, publicacion) }
    override suspend fun configurarHorarios(negocioId: String, horarios: JSONArray): JSONObject = withContext(Dispatchers.IO) { remote.configurarHorarios(negocioId, horarios) }
    override fun close() = remote.close()
}
