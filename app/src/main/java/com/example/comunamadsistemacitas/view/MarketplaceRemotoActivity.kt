package com.example.comunamadsistemacitas.view

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.comunamadsistemacitas.presentation.catalog.MarketplaceCatalogViewModel
import com.example.comunamadsistemacitas.presentation.catalog.MarketplaceBrowseEvent
import com.example.comunamadsistemacitas.presentation.catalog.MarketplaceBrowseViewModel
import com.example.comunamadsistemacitas.presentation.auth.AuthEvent
import com.example.comunamadsistemacitas.presentation.auth.AuthViewModel
import com.example.comunamadsistemacitas.presentation.booking.BookingEvent
import com.example.comunamadsistemacitas.presentation.booking.BookingViewModel
import com.example.comunamadsistemacitas.presentation.business.BusinessPanelEvent
import com.example.comunamadsistemacitas.presentation.business.BusinessPanelViewModel
import com.example.comunamadsistemacitas.presentation.business.BusinessRegistrationEvent
import com.example.comunamadsistemacitas.presentation.business.BusinessRegistrationViewModel
import com.example.comunamadsistemacitas.domain.validation.AuthValidation
import com.example.comunamadsistemacitas.domain.validation.BusinessValidation
import com.example.comunamadsistemacitas.domain.validation.IsoDateTimeParser
import kotlinx.coroutines.launch
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.progressindicator.LinearProgressIndicator
import org.json.JSONArray
import org.json.JSONObject
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone


class MarketplaceRemotoActivity : UnamadBaseActivity() {
    private val catalogViewModel: MarketplaceCatalogViewModel by viewModels()
    private val marketplaceViewModel: MarketplaceBrowseViewModel by viewModels()
    private val bookingViewModel: BookingViewModel by viewModels()
    private var activeBookingDialog: AlertDialog? = null
    private var activeBookingDateText: TextView? = null
    private var activeBookingSlotContainer: LinearLayout? = null
    private var activeBookingSubmitButton: android.widget.Button? = null
    private var activeBookingProgress: LinearProgressIndicator? = null
    private var renderedBookingAvailabilityRevision = -1L
    private var activeBookingBusinessId: String? = null
    private var activeBookingListingJson: String? = null
    private var activeBookingDateMillis: Long? = null
    private var activeBookingPickupSelected: Boolean = false
    private var activeBookingQuantityDraft: String? = null
    private var activeBookingNoteDraft: String? = null
    private var activeBookingCountField: EditText? = null
    private var activeBookingNoteField: EditText? = null
    private var renderedCatalogIds: List<String> = emptyList()
    private lateinit var list: LinearLayout
    private lateinit var search: EditText
    private lateinit var status: TextView
    private lateinit var loadMoreButton: MaterialButton
    private lateinit var errorActionLayout: LinearLayout
    private lateinit var catalogFiltersContainer: LinearLayout
    private lateinit var headerStepText: TextView
    private lateinit var headerTitleText: TextView
    private lateinit var headerSubtitleText: TextView
    private var currentSection = "CATALOG"
    private var contentRequestId = 0
    private val cancellationsInFlight = mutableSetOf<String>()
    private val reservationCancelButtons = mutableMapOf<String, MaterialButton>()
    private var activeBusinessForListings: JSONObject? = null

    companion object {
        private const val BUSINESS_PAGE_SIZE = 24
        private const val MAX_RENDERED_BUSINESSES = 120
        private const val STATE_BOOKING_BUSINESS = "state_booking_business"
        private const val STATE_BOOKING_LISTING = "state_booking_listing"
        private const val STATE_BOOKING_DATE = "state_booking_date"
        private const val STATE_BOOKING_PICKUP_SELECTED = "state_booking_pickup_selected"
        private const val STATE_BOOKING_QUANTITY = "state_booking_quantity"
        private const val STATE_BOOKING_NOTE = "state_booking_note"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        construirPantallaCatalogo()
        instalarNavegacionAtras()
        observarEstadoCatalogo()
        observarEstadoReserva()
        observarEventosMarketplace()

        // El catálogo se abre como primer paso de la reserva; ya no existe una opción
        // independiente llamada "Buscar" en la portada.
        val openBusinessId = intent.getStringExtra("OPEN_BUSINESS_ID")
        when {
            intent.getBooleanExtra("SHOW_RESERVATIONS", false) -> cargarMisReservas()
            !openBusinessId.isNullOrBlank() -> {
                currentSection = "BUSINESS"
                catalogFiltersContainer.visibility = View.GONE
                val requestId = nuevaContentRequestId()
                marketplaceViewModel.cargarNegocio(openBusinessId, requestId)
            }
            else -> cargarNegocios()
        }
        val restoredBusiness = savedInstanceState?.getString(STATE_BOOKING_BUSINESS)
        val restoredListing = savedInstanceState?.getString(STATE_BOOKING_LISTING)
        if (!restoredBusiness.isNullOrBlank() && !restoredListing.isNullOrBlank()) {
            activeBookingBusinessId = restoredBusiness
            activeBookingListingJson = restoredListing
            activeBookingDateMillis = savedInstanceState.getLong(STATE_BOOKING_DATE, -1L).takeIf { it > 0L }
            activeBookingPickupSelected = savedInstanceState.getBoolean(STATE_BOOKING_PICKUP_SELECTED, false)
            activeBookingQuantityDraft = savedInstanceState.getString(STATE_BOOKING_QUANTITY)
            activeBookingNoteDraft = savedInstanceState.getString(STATE_BOOKING_NOTE)
            list.post {
                if (!isFinishing && !isDestroyed) {
                    runCatching { JSONObject(restoredListing) }.onSuccess { mostrarFormularioReserva(restoredBusiness, it) }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (activeBookingDialog?.isShowing == true) {
            activeBookingBusinessId?.let { outState.putString(STATE_BOOKING_BUSINESS, it) }
            activeBookingListingJson?.let { outState.putString(STATE_BOOKING_LISTING, it) }
            activeBookingDateMillis?.let { outState.putLong(STATE_BOOKING_DATE, it) }
            outState.putBoolean(STATE_BOOKING_PICKUP_SELECTED, activeBookingPickupSelected)
            activeBookingCountField?.text?.toString()?.let { outState.putString(STATE_BOOKING_QUANTITY, it) }
            activeBookingNoteField?.text?.toString()?.let { outState.putString(STATE_BOOKING_NOTE, it) }
        }
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        nuevaContentRequestId()
        currentSection = "CATALOG"
        seleccionarDestinoNavegacion(if (intent.getBooleanExtra("SHOW_RESERVATIONS", false)) "RESERVATIONS" else "BOOK")
        when {
            intent.getBooleanExtra("SHOW_RESERVATIONS", false) -> cargarMisReservas()
            !intent.getStringExtra("OPEN_BUSINESS_ID").isNullOrBlank() -> {
                currentSection = "BUSINESS"
                catalogFiltersContainer.visibility = View.GONE
                val businessId = intent.getStringExtra("OPEN_BUSINESS_ID").orEmpty()
                if (businessId.isBlank()) {
                    cargarNegocios(reset = true)
                    return
                }
                val requestId = contentRequestId
                marketplaceViewModel.cargarNegocio(businessId, requestId)
            }
            else -> {
                search.setText(intent.getStringExtra("SEARCH_QUERY").orEmpty())
                cargarNegocios(reset = true)
            }
        }
    }

    private fun nuevaContentRequestId(): Int {
        contentRequestId = marketplaceViewModel.nuevaClaveSolicitud()
        return contentRequestId
    }

    private fun actualizarEncabezado(paso: String, titulo: String, subtitulo: String) {
        headerStepText.text = paso
        headerTitleText.text = titulo
        headerSubtitleText.text = subtitulo
    }

    private fun construirPantallaCatalogo() {
        val root = rColumn()
        val topLine = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        headerStepText = rText("PASO 1 DE 3", 11f, UnamadPalette.tealDark, true).apply { gravity = Gravity.END }
        topLine.addView(headerStepText, LinearLayout.LayoutParams(-1, -2))
        root.addView(topLine)

        val catalogHero = ImageView(this).apply {
            setImageResource(com.example.comunamadsistemacitas.R.drawable.hero_reservas)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Ilustración del proceso de selección y reserva de una cita."
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = rdp(14).toFloat()
            }
            clipToOutline = true
        }
        root.addView(catalogHero, LinearLayout.LayoutParams(-1, rdp(108)).apply {
            topMargin = rdp(10)
            bottomMargin = rdp(4)
        })
        headerTitleText = rText("Elige dónde quieres reservar", 25f, UnamadPalette.navy, true).apply {
            setPadding(0, rdp(12), 0, rdp(2))
        }
        root.addView(headerTitleText)
        headerSubtitleText = rText("Selecciona un negocio, revisa sus servicios y después elige un horario disponible.", 14f, UnamadPalette.muted)
        root.addView(headerSubtitleText)

        search = rField(getString(com.example.comunamadsistemacitas.R.string.catalog_search_hint)).apply {
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            setBackground(GradientDrawable().apply {
                setColor(Color.WHITE); cornerRadius = rdp(12).toFloat(); setStroke(rdp(1), UnamadPalette.border)
            })
            setText(intent.getStringExtra("SEARCH_QUERY") ?: "")
        }
        catalogFiltersContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        catalogFiltersContainer.rAddField(search, this)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(rButton("Buscar negocios") { cargarNegocios() }, LinearLayout.LayoutParams(0, rdp(48), 1f).apply { marginEnd = rdp(5) })
        actions.addView(rButton("Mis reservas") { cargarMisReservas() }.apply {
            backgroundTintList = android.content.res.ColorStateList.valueOf(UnamadPalette.navy)
        }, LinearLayout.LayoutParams(0, rdp(48), 1f).apply { marginStart = rdp(5) })
        catalogFiltersContainer.addView(actions)
        root.addView(catalogFiltersContainer)

        status = rText("Preparando los negocios disponibles…", 12f, UnamadPalette.muted).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            setPadding(0, rdp(9), 0, rdp(5))
        }
        root.addView(status)
        val scroller = ScrollView(this).apply { isFillViewport = false; overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, rdp(4), 0, rdp(12)) }
        scroller.addView(list)
        root.addView(scroller, LinearLayout.LayoutParams(-1, 0, 1f))

        val actionContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        loadMoreButton = rButton(getString(com.example.comunamadsistemacitas.R.string.catalog_load_more)) { cargarNegocios(reset = false) }
        loadMoreButton.visibility = View.GONE
        actionContainer.addView(loadMoreButton, LinearLayout.LayoutParams(-1, rdp(48)))

        errorActionLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        val homeBtn = rButton("Volver al inicio") { irAlPortal() }.apply {
            backgroundTintList = android.content.res.ColorStateList.valueOf(UnamadPalette.navy)
        }
        errorActionLayout.addView(homeBtn, LinearLayout.LayoutParams(-1, rdp(48)))
        actionContainer.addView(errorActionLayout, LinearLayout.LayoutParams(-1, -2))

        root.addView(actionContainer, LinearLayout.LayoutParams(-1, -2).apply { topMargin = rdp(6); bottomMargin = rdp(3) })
        agregarNavegacionPrincipal(root, "BOOK")
        setContentView(root)

        search.setOnEditorActionListener { _, _, _ -> cargarNegocios(); true }
    }

    private fun mostrarReintento(@Suppress("UNUSED_PARAMETER") error: Throwable) {
        if (::errorActionLayout.isInitialized) errorActionLayout.visibility = View.VISIBLE
    }

    private fun irAlPortal() {
        startActivity(
            Intent(this, PortalMarketplaceActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }

    private fun instalarNavegacionAtras() {
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    activeBookingDialog?.isShowing == true -> activeBookingDialog?.dismiss()
                    currentSection == "BUSINESS" -> cargarNegocios(reset = true)
                    else -> irAlPortal()
                }
            }
        })
    }

    /** La vista solo representa UiState; la paginación y las peticiones viven en el ViewModel. */
    private fun observarEstadoCatalogo() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                catalogViewModel.uiState.collect { state ->
                    if (currentSection != "CATALOG") return@collect

                    val ids = state.businesses.map { it.optString("id") }
                    if (ids != renderedCatalogIds) {
                        // Al paginar agrega solo las tarjetas nuevas y conserva la posición de scroll.
                        val isSamePrefix = ids.size >= renderedCatalogIds.size &&
                            ids.take(renderedCatalogIds.size) == renderedCatalogIds
                        if (!isSamePrefix) {
                            list.removeAllViews()
                            state.businesses.forEach(::agregarTarjetaNegocio)
                        } else {
                            if (renderedCatalogIds.isEmpty()) list.removeAllViews()
                            state.businesses.drop(renderedCatalogIds.size).forEach(::agregarTarjetaNegocio)
                        }
                        renderedCatalogIds = ids
                    }

                    status.text = when {
                        state.isLoading -> "Cargando negocios para tu reserva…"
                        state.isLoadingMore -> "Cargando más opciones…"
                        state.errorMessage != null -> mensajeErrorAmigable(IllegalStateException(state.errorMessage))
                        state.businesses.isEmpty() -> "No encontramos negocios con esa búsqueda. Prueba otro nombre, servicio o categoría."
                        state.reachedDisplayLimit -> "${state.businesses.size} negocios cargados. Afina la búsqueda para ver otros resultados."
                        else -> "${state.businesses.size} negocios cargados${if (state.hasMore) " · hay más resultados" else " · no hay más resultados"}."
                    }

                    loadMoreButton.visibility = if (state.hasMore && state.errorMessage == null && !state.reachedDisplayLimit) View.VISIBLE else View.GONE
                    errorActionLayout.visibility = if (state.errorMessage != null) View.VISIBLE else View.GONE
                    loadMoreButton.isEnabled = !state.isLoading && !state.isLoadingMore
                    loadMoreButton.text = getString(com.example.comunamadsistemacitas.R.string.catalog_load_more)
                    loadMoreButton.setOnClickListener {
                        catalogViewModel.cargarMas()
                    }

                    if (!state.isLoading && state.businesses.isEmpty() && state.errorMessage == null && list.childCount == 0) {
                        list.addView(rText("Prueba con otro nombre, servicio o categoría. Aquí aparecerán los negocios que publiquen servicios y horarios.", 14f))
                    }
                }
            }
        }
    }

    /** La Activity representa el estado de BookingViewModel; no ejecuta HTTP directamente. */
    private fun observarEstadoReserva() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    bookingViewModel.uiState.collect { state ->
                        val dialog = activeBookingDialog
                        if (dialog == null || !dialog.isShowing) return@collect
                        activeBookingSubmitButton?.let { button ->
                            button.isEnabled = !state.isSubmitting
                            button.text = if (state.isSubmitting) "Enviando…" else "Enviar solicitud"
                        }
                        activeBookingProgress?.visibility = if (state.isLoadingAvailability) View.VISIBLE else View.GONE
                        activeBookingDateText?.let { label ->
                            label.text = when {
                                state.isLoadingAvailability -> "Consultando horarios reales…"
                                state.availabilityErrorMessage != null -> "No se pudo consultar la disponibilidad. Elige la fecha nuevamente para reintentar."
                                state.selectedSlot != null -> {
                                    val selected = state.selectedSlot
                                    "Turno seleccionado: ${horaLocalDesdeIso(selected.optString("starts_at"))} · ${selected.optString("assignee_name", "Atención disponible")}"
                                }
                                state.availabilityDate != null && state.slots.isEmpty() -> "No hay turnos disponibles para esa fecha. Prueba otro día."
                                state.availabilityDate != null -> "Selecciona uno de los turnos disponibles:"
                                else -> label.text
                            }
                        }
                        val slotContainer = activeBookingSlotContainer
                        if (slotContainer != null && renderedBookingAvailabilityRevision != state.availabilityRevision) {
                            renderedBookingAvailabilityRevision = state.availabilityRevision
                            slotContainer.removeAllViews()
                            val standardHours = listOf("09:00", "10:00", "11:00", "12:00", "14:00", "15:00", "16:00", "17:00", "18:00")
                            standardHours.forEach { stdHour ->
                                val matchingSlot = state.slots.firstOrNull { slot ->
                                    horaLocalDesdeIso(slot.optString("starts_at")) == stdHour
                                }
                                if (matchingSlot != null) {
                                    val who = matchingSlot.optString("assignee_name", "Disponible")
                                    val kindLabel = if (matchingSlot.optString("assignee_type") == "STAFF") "Profesional" else "Puesto"
                                    val key = "${matchingSlot.optString("starts_at")}|${matchingSlot.optString("assignee_id")}"
                                    val button = rButton("🟢 $stdHour · Libre ($who · $kindLabel)") {
                                        bookingViewModel.seleccionarTurno(matchingSlot)
                                    }.apply {
                                        textSize = 13f
                                        tag = key
                                        slotButtonTint(this, state.selectedSlot?.let {
                                            "${it.optString("starts_at")}|${it.optString("assignee_id")}"
                                        } == key)
                                    }
                                    slotContainer.addView(button, LinearLayout.LayoutParams(-1, rdp(44)).apply { bottomMargin = rdp(5) })
                                } else {
                                    val occupiedCard = MaterialCardView(this@MarketplaceRemotoActivity).apply {
                                        radius = rdp(8).toFloat()
                                        cardElevation = 0f
                                        setCardBackgroundColor(Color.rgb(243, 244, 246))
                                        strokeColor = UnamadPalette.border
                                        strokeWidth = rdp(1)
                                        isClickable = false
                                    }
                                    val occupiedText = TextView(this@MarketplaceRemotoActivity).apply {
                                        text = "🔴 $stdHour · Ocupado / Reservado"
                                        textSize = 13f
                                        setTextColor(UnamadPalette.muted)
                                        setPadding(rdp(12), rdp(10), rdp(12), rdp(10))
                                    }
                                    occupiedCard.addView(occupiedText)
                                    slotContainer.addView(occupiedCard, LinearLayout.LayoutParams(-1, rdp(44)).apply { bottomMargin = rdp(5) })
                                }
                            }
                        } else if (slotContainer != null) {
                            val selectedKey = state.selectedSlot?.let {
                                "${it.optString("starts_at")}|${it.optString("assignee_id")}"
                            }
                            for (index in 0 until slotContainer.childCount) {
                                val button = slotContainer.getChildAt(index) as? MaterialButton ?: continue
                                slotButtonTint(button, button.tag == selectedKey)
                            }
                        }
                    }
                }
                launch {
                    bookingViewModel.events.collect { event ->
                        when (event) {
                            is BookingEvent.Created -> {
                                activeBookingDialog?.setCancelable(true)
                                activeBookingDialog?.dismiss()
                                AlertDialog.Builder(this@MarketplaceRemotoActivity).setTitle("¡Solicitud registrada!")
                                    .setMessage("Código: ${event.reservation.optString("code", "registrado")}\nEstado: ${event.reservation.optString("status", "SOLICITADA")}\n${if (event.reservation.optBoolean("replayed")) "Recuperamos la misma solicitud; no se creó una cita duplicada." else "El negocio recibirá tu solicitud y deberá confirmar la cita."}")
                                    .setNegativeButton("Cerrar", null)
                                    .setPositiveButton("Ver mis reservas") { _, _ -> cargarMisReservas() }.show()
                            }
                            is BookingEvent.Failed -> {
                                activeBookingDialog?.setCancelable(true)
                                showRemoteError(this@MarketplaceRemotoActivity, event.cause)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun cargarNegocios(reset: Boolean = true) {
        if (reset) {
            actualizarEncabezado("PASO 1 DE 3", "Elige dónde quieres reservar", "Selecciona un negocio, revisa sus servicios y después elige un horario disponible.")
            seleccionarDestinoNavegacion("BOOK")
            catalogFiltersContainer.visibility = View.VISIBLE
            nuevaContentRequestId()
            currentSection = "CATALOG"
            renderedCatalogIds = emptyList()
            list.removeAllViews()
            catalogViewModel.buscar(search.text.toString())
        } else {
            catalogViewModel.cargarMas()
        }
    }

    private fun agregarTarjetaNegocio(business: JSONObject) {
        val card = rCard()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(rdp(14), rdp(12), rdp(14), rdp(12))
        }
        val name = business.optString("name", "Negocio")
        content.addView(rText("${business.optString("emoji", "🏪")}  $name", 17f, UnamadPalette.navy, true))
        val location = listOf(business.optString("district"), business.optString("province"))
            .filter { it.isNotBlank() }.distinct().joinToString(", ")
        content.addView(rText("${business.optString("category", "Servicio")} · ${location.ifBlank { "Ubicación por confirmar" }}", 12f))
        val description = business.optString("description").trim()
        if (description.isNotBlank()) content.addView(rText(description, 13f))
        val modes = business.optJSONArray("modes")
        val modesText = modes?.let { array -> (0 until array.length()).map { array.optString(it) }.joinToString(" · ") }.orEmpty()
        if (modesText.isNotBlank()) content.addView(rText("Modalidad: $modesText", 12f, UnamadPalette.tealDark, true))
        content.addView(rButton("Ver servicios y reservar  →") { mostrarPublicaciones(business) })
        card.addView(content)
        list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = rdp(9) })
    }

    private fun mostrarPublicaciones(business: JSONObject) {
        val businessId = business.optString("id").trim()
        if (businessId.isBlank() || businessId == "null") {
            showRemoteError(this, IllegalStateException("El negocio recibido no tiene un identificador válido."))
            return
        }
        actualizarEncabezado("PASO 2 DE 3", business.optString("name", "Servicios del negocio"), "Selecciona el servicio o producto que deseas solicitar.")
        catalogFiltersContainer.visibility = View.GONE
        currentSection = "BUSINESS"
        renderedCatalogIds = emptyList()
        activeBusinessForListings = business
        nuevaContentRequestId()
        val requestId = contentRequestId
        status.text = "Cargando servicios de ${business.optString("name", "este negocio")}…"
        list.removeAllViews()
        loadMoreButton.visibility = View.GONE
        marketplaceViewModel.cargarPublicaciones(businessId, requestId)
    }

    private fun renderizarPublicaciones(business: JSONObject, listings: JSONArray) {
        val businessId = business.optString("id")
        list.removeAllViews()
        list.addView(rButton("‹  Volver a los negocios") { cargarNegocios(reset = true) }.apply {
            backgroundTintList = android.content.res.ColorStateList.valueOf(UnamadPalette.navy)
        }, LinearLayout.LayoutParams(-1, rdp(44)).apply { bottomMargin = rdp(10) })
        list.addView(rText(business.optString("name", "Negocio"), 22f, UnamadPalette.navy, true))
        val address = listOf(business.optString("district"), business.optString("province"))
            .filter { it.isNotBlank() && it != "null" }.distinct().joinToString(", ")
        if (address.isNotBlank()) list.addView(rText(address, 13f, UnamadPalette.muted))
        val description = business.optString("description").trim()
        if (description.isNotBlank()) list.addView(rText(description, 14f))
        list.addView(rText("Elige el servicio que necesitas", 17f, UnamadPalette.ink, true).apply {
            setPadding(0, rdp(14), 0, rdp(8))
        })
        val validListings = buildList {
            for (i in 0 until listings.length()) {
                val item = listings.optJSONObject(i) ?: continue
                val id = item.optString("id").trim()
                val title = item.optString("title").trim()
                val kind = item.optString("kind")
                val price = item.optDouble("price", Double.NaN)
                if (id.isNotBlank() && id != "null" && title.isNotBlank() && title != "null" &&
                    kind in setOf("SERVICIO", "PRODUCTO") && price.isFinite() && price >= 0.0) add(item)
            }
        }
        if (validListings.isEmpty()) {
            list.addView(rText("Este negocio todavía no publica servicios válidos. Vuelve más adelante para consultar sus opciones.", 14f))
        }
        for (item in validListings) {
            val card = rCard()
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(rdp(14), rdp(12), rdp(14), rdp(12)) }
            box.addView(rText(item.optString("title", "Servicio"), 17f, UnamadPalette.navy, true))
            val price = item.optString("price")
            val duration = item.optInt("duration_minutes", 0)
            val meta = buildList {
                add(if (item.optString("kind") == "PRODUCTO") "Producto" else "Servicio")
                if (price.isNotBlank() && price != "null") add("S/ $price")
                if (duration > 0 && item.optString("kind") != "PRODUCTO") add("$duration min")
            }.joinToString(" · ")
            box.addView(rText(meta, 13f, UnamadPalette.tealDark, true))
            if (item.optString("description").isNotBlank()) box.addView(rText(item.optString("description"), 13f, UnamadPalette.muted))
            box.addView(rButton(if (item.optString("kind") == "PRODUCTO") "Elegir recojo" else "Elegir este servicio") {
                mostrarFormularioReserva(businessId, item)
            }, LinearLayout.LayoutParams(-1, rdp(48)).apply { topMargin = rdp(8) })
            card.addView(box)
            list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = rdp(9) })
        }
        status.text = if (validListings.isEmpty()) "Sin servicios publicados" else "PASO 2 DE 3 · ${validListings.size} opciones disponibles"
        val pendingListing = intent.getStringExtra("RESERVE_LISTING")
        if (!pendingListing.isNullOrBlank()) {
            val pendingId = runCatching { JSONObject(pendingListing).optString("id") }.getOrNull()
            val pendingItem = validListings.firstOrNull { it.optString("id") == pendingId }
            intent.removeExtra("RESERVE_LISTING")
            if (pendingItem != null) list.post { mostrarFormularioReserva(businessId, pendingItem) }
            else status.text = "Este servicio ya no está disponible. Elige otra opción del negocio."
        }
    }

    private fun mostrarFormularioReserva(businessId: String, listing: JSONObject) {
        if (!marketplaceViewModel.haySesion()) {
            AlertDialog.Builder(this).setTitle("Inicia sesión")
                .setMessage("Para enviar una cita o reserva, necesitas una cuenta de cliente.")
                .setNegativeButton("Ahora no", null)
                .setPositiveButton("Continuar") { _, _ ->
                    startActivity(Intent(this, AccesoRemotoActivity::class.java)
                        .putExtra("AFTER_LOGIN", "BOOKING")
                        .putExtra("OPEN_BUSINESS_ID", businessId)
                        .putExtra("RESERVE_LISTING", listing.toString()))
                }.show()
            return
        }
        val listingId = listing.optString("id").takeIf { it.isNotBlank() && it != "null" }
        if (listingId == null) {
            showRemoteError(this, IllegalStateException("El servicio recibido no tiene un identificador válido. Actualiza el catálogo."))
            return
        }
        val retainedBooking = bookingViewModel.uiState.value
        if (retainedBooking.businessId != businessId || retainedBooking.listingId != listingId) {
            bookingViewModel.reset(businessId, listingId)
            renderedBookingAvailabilityRevision = -1L
        }
        activeBookingBusinessId = businessId
        activeBookingListingJson = listing.toString()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(rdp(14), rdp(10), rdp(14), rdp(10)) }
        val kind = listing.optString("kind")
        val mode = if (kind == "PRODUCTO") "RECOJO" else "CITA"

        val serviceCard = rCard()
        val serviceContent = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(rdp(14), rdp(12), rdp(14), rdp(12)) }
        serviceContent.addView(rText(listing.optString("title", "Servicio"), 17f, UnamadPalette.navy, true))
        val price = listing.optString("price")
        val duration = listing.optInt("duration_minutes", 0)
        val summaryMeta = buildList {
            add(if (mode == "CITA") "Cita de atención" else "Recogida de pedido")
            if (price.isNotBlank() && price != "null") add("S/ $price")
            if (duration > 0 && mode == "CITA") add("$duration min")
        }.joinToString(" · ")
        serviceContent.addView(rText(summaryMeta, 13f, UnamadPalette.tealDark, true))
        val desc = listing.optString("description").trim()
        if (desc.isNotBlank()) serviceContent.addView(rText(desc, 12f, UnamadPalette.muted))
        serviceCard.addView(serviceContent)
        box.addView(serviceCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = rdp(14) })

        val count = rField("Cantidad para recoger", InputType.TYPE_CLASS_NUMBER).rLimit(2).apply {
            setText(activeBookingQuantityDraft ?: "1")
        }
        val note = rField("Nota o instrucciones para el negocio (opcional)").rLimit(5000).apply {
            setText(activeBookingNoteDraft.orEmpty())
        }
        activeBookingCountField = count
        activeBookingNoteField = note
        val calendar = Calendar.getInstance(UNAMAD_TIME_ZONE, Locale("es", "PE")).apply {
            val retainedDate = activeBookingDateMillis
            if (retainedDate != null) timeInMillis = retainedDate else {
                add(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 10); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
        }
        var pickupDateTimeSelected = activeBookingPickupSelected
        val slotContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        lateinit var bookingDialog: AlertDialog

        box.addView(rText("1. Calendario de disponibilidad (Próximos días)", 14f, UnamadPalette.navy, true))
        val dateText = rText("Selecciona un día en el calendario superior para ver sus horarios libres y ocupados.", 13f, UnamadPalette.muted)
        box.addView(dateText, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = rdp(8) })

        val calendarScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val daysContainer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val dayCards = mutableListOf<MaterialCardView>()

        val tempCal = Calendar.getInstance(UNAMAD_TIME_ZONE, Locale("es", "PE"))
        for (i in 0..11) {
            val dayMillis = tempCal.timeInMillis
            val dayFormatted = formatoLima("yyyy-MM-dd", Locale.US).format(tempCal.time)
            val dayLabel = formatoLima("EEE", Locale("es", "PE")).format(tempCal.time).uppercase()
            val dayNum = formatoLima("dd", Locale.US).format(tempCal.time)
            val monthLabel = formatoLima("MMM", Locale("es", "PE")).format(tempCal.time).uppercase()

            val dayCard = MaterialCardView(this).apply {
                radius = rdp(12).toFloat()
                cardElevation = rdp(2).toFloat()
                setCardBackgroundColor(if (i == 0) UnamadPalette.navy else UnamadPalette.surface)
                strokeColor = UnamadPalette.border
                strokeWidth = rdp(1)
                isClickable = true
                isFocusable = true
            }
            val dayInner = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(rdp(12), rdp(10), rdp(12), rdp(10))
            }
            val tvDay = TextView(this).apply {
                text = dayLabel
                textSize = 11f
                setTextColor(if (i == 0) Color.WHITE else UnamadPalette.muted)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
            }
            val tvNum = TextView(this).apply {
                text = dayNum
                textSize = 16f
                setTextColor(if (i == 0) Color.WHITE else UnamadPalette.navy)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
            }
            val tvMon = TextView(this).apply {
                text = monthLabel
                textSize = 10f
                setTextColor(if (i == 0) Color.rgb(224, 239, 250) else UnamadPalette.muted)
                gravity = Gravity.CENTER
            }
            dayInner.addView(tvDay)
            dayInner.addView(tvNum)
            dayInner.addView(tvMon)
            dayCard.addView(dayInner)

            dayCard.setOnClickListener {
                dayCards.forEach { c ->
                    c.setCardBackgroundColor(UnamadPalette.surface)
                    for (childIndex in 0 until (c.getChildAt(0) as ViewGroup).childCount) {
                        val child = (c.getChildAt(0) as ViewGroup).getChildAt(childIndex) as? TextView
                        child?.setTextColor(if (childIndex == 1) UnamadPalette.navy else UnamadPalette.muted)
                    }
                }
                dayCard.setCardBackgroundColor(UnamadPalette.navy)
                tvDay.setTextColor(Color.WHITE)
                tvNum.setTextColor(Color.WHITE)
                tvMon.setTextColor(Color.rgb(224, 239, 250))

                calendar.timeInMillis = dayMillis
                activeBookingDateMillis = dayMillis
                if (mode == "CITA") {
                    slotContainer.removeAllViews()
                    dateText.text = "Horarios para ${formatoLima("EEE, dd MMM yyyy", Locale("es", "PE")).format(tempCal.time)}"
                    bookingViewModel.consultarDisponibilidad(businessId, listingId, dayFormatted)
                }
            }
            dayCards.add(dayCard)
            daysContainer.addView(dayCard, LinearLayout.LayoutParams(rdp(72), rdp(84)).apply { marginEnd = rdp(6) })
            tempCal.add(Calendar.DAY_OF_MONTH, 1)
        }
        calendarScroll.addView(daysContainer)
        box.addView(calendarScroll, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = rdp(12) })

        val availabilityProgress = LinearProgressIndicator(this).apply {
            isIndeterminate = true
            visibility = View.GONE
            trackThickness = rdp(4)
            setIndicatorColor(UnamadPalette.teal)
            trackColor = UnamadPalette.mint
        }
        box.addView(availabilityProgress, LinearLayout.LayoutParams(-1, rdp(4)).apply {
            bottomMargin = rdp(6)
        })

        if (mode == "CITA") {
            box.addView(rText("2. Horarios libres y ocupados del día", 14f, UnamadPalette.navy, true).apply {
                setPadding(0, rdp(4), 0, rdp(4))
            })
        }
        box.addView(slotContainer, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = rdp(10) })

        val detailsHeader = rText(if (mode == "CITA") "3. Detalles adicionales de tu cita" else "2. Cantidad y detalles", 14f, UnamadPalette.navy, true).apply {
            setPadding(0, rdp(4), 0, rdp(4))
        }
        box.addView(detailsHeader)
        if (mode == "RECOJO") box.rAddField(count, this)
        box.rAddField(note, this)
        val bookingScroll = ScrollView(this).apply {
            isFillViewport = false
            addView(box)
        }
        bookingDialog = AlertDialog.Builder(this).setTitle("Solicitar cita o reserva")
            .setView(bookingScroll).setNegativeButton("Cancelar", null).setPositiveButton("Enviar solicitud", null).create()
        activeBookingDialog = bookingDialog
        activeBookingDateText = dateText
        activeBookingProgress = availabilityProgress
        activeBookingSlotContainer = slotContainer
        bookingDialog.setOnDismissListener {
            if (activeBookingDialog === bookingDialog) {
                activeBookingDialog = null
                activeBookingDateText = null
                activeBookingProgress = null
                activeBookingSlotContainer = null
                activeBookingSubmitButton = null
                activeBookingCountField = null
                activeBookingNoteField = null
                if (!isChangingConfigurations) {
                    activeBookingBusinessId = null
                    activeBookingListingJson = null
                    activeBookingDateMillis = null
                    activeBookingPickupSelected = false
                    activeBookingQuantityDraft = null
                    activeBookingNoteDraft = null
                    // Un diálogo nuevo no debe reutilizar el turno elegido en una reserva anterior.
                    bookingViewModel.reset("", "")
                }
            }
        }
        bookingDialog.setOnShowListener {
            val submit = bookingDialog.getButton(AlertDialog.BUTTON_POSITIVE)
            activeBookingSubmitButton = submit
            submit.isEnabled = !bookingViewModel.uiState.value.isSubmitting
            submit.text = if (bookingViewModel.uiState.value.isSubmitting) "Enviando…" else "Enviar solicitud"
            bookingDialog.setCancelable(!bookingViewModel.uiState.value.isSubmitting)
            submit.setOnClickListener {
                if (bookingViewModel.uiState.value.isSubmitting) return@setOnClickListener
                val quantity = if (mode == "RECOJO") count.text.toString().toIntOrNull() ?: 0 else 1
                if (quantity <= 0 || (mode == "RECOJO" && (!pickupDateTimeSelected || calendar.timeInMillis <= System.currentTimeMillis()))) {
                    Toast.makeText(this, "Selecciona una fecha y hora futura, además de una cantidad válida.", Toast.LENGTH_LONG).show(); return@setOnClickListener
                }
                val selected = bookingViewModel.uiState.value.selectedSlot
                if (mode == "CITA" && selected == null) {
                    Toast.makeText(this, "Primero consulta y selecciona un turno disponible.", Toast.LENGTH_LONG).show(); return@setOnClickListener
                }
                val selectedTime = selected?.optString("starts_at").orEmpty()
                val time = if (mode == "CITA") selectedTime
                    else formatoLima("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(calendar.time)
                if (time.isBlank() || (mode == "CITA" && parseIsoDateStrict(time) == null)) {
                    showRemoteError(this, IllegalStateException("El horario seleccionado no contiene una fecha válida. Consulta nuevamente la disponibilidad."))
                    return@setOnClickListener
                }
                val safeListingId = listing.optString("id").takeIf { it.isNotBlank() && it != "null" }
                if (safeListingId == null) {
                    showRemoteError(this, IllegalStateException("El servicio ya no tiene un identificador válido."))
                    return@setOnClickListener
                }
                val payload = JSONObject().put("businessId", businessId).put("listingId", safeListingId)
                    .put("mode", mode).put("dateTime", time).put("quantity", quantity)
                    .put("paymentMethod", "EN_LOCAL").put("note", note.text.toString().trim())
                if (mode == "CITA") {
                    val assigneeId = selected?.optString("assignee_id")?.takeIf { it.isNotBlank() && it != "null" }
                    when (selected?.optString("assignee_type")) {
                        "STAFF" -> if (assigneeId != null) payload.put("staffId", assigneeId) else {
                            showRemoteError(this, IllegalStateException("El turno no identifica al profesional. Consulta otra vez.")); return@setOnClickListener
                        }
                        "RESOURCE" -> if (assigneeId != null) payload.put("resourceId", assigneeId) else {
                            showRemoteError(this, IllegalStateException("El turno no identifica el recurso disponible. Consulta otra vez.")); return@setOnClickListener
                        }
                        else -> {
                            showRemoteError(this, IllegalStateException("La asignación del turno no es válida. Consulta otra vez.")); return@setOnClickListener
                        }
                    }
                }
                submit.isEnabled = false
                submit.text = "Enviando…"
                bookingDialog.setCancelable(false)
                activeBookingSubmitButton = submit
                bookingViewModel.crearReserva(payload)
            }
        }
        bookingDialog.show()
        bookingViewModel.refrescarEstadoUi()
    }

    private fun cargarMisReservas() {
        actualizarEncabezado("MIS CITAS", "Mis citas y reservas", "Consulta el estado de tus citas solicitadas y programadas.")
        seleccionarDestinoNavegacion("RESERVATIONS")
        currentSection = "RESERVATIONS"
        catalogFiltersContainer.visibility = View.GONE
        nuevaContentRequestId()
        val requestId = contentRequestId
        loadMoreButton.visibility = View.GONE
        loadMoreButton.isEnabled = false
        list.removeAllViews()
        status.text = "Cargando tus reservas…"
        if (!marketplaceViewModel.haySesion()) {
            startActivity(Intent(this, AccesoRemotoActivity::class.java).putExtra("AFTER_LOGIN", "RESERVATIONS"))
            finish()
            return
        }
        list.addView(rText("Estamos consultando tus reservas…", 14f, UnamadPalette.muted))
        marketplaceViewModel.cargarMisReservas(requestId)
    }

    private fun renderizarMisReservas(items: JSONArray) {
        list.removeAllViews()
        list.addView(rButton("‹  Volver a elegir un servicio") { cargarNegocios(reset = true) }.apply {
            backgroundTintList = android.content.res.ColorStateList.valueOf(UnamadPalette.navy)
        }, LinearLayout.LayoutParams(-1, rdp(44)).apply { bottomMargin = rdp(10) })
        val validReservations = buildList {
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val id = item.optString("id").trim()
                val statusValue = item.optString("status")
                if (id.isNotBlank() && id != "null" && statusValue in setOf("SOLICITADA", "CONFIRMADA", "LISTA", "COMPLETADA", "CANCELADA", "NO_SHOW")) add(item)
            }
        }
        status.text = "${validReservations.size} reservas en tu cuenta"
        if (validReservations.isEmpty()) list.addView(rText("Todavía no tienes reservas válidas para mostrar. Elige un negocio y un servicio para programar tu primera cita.", 14f))
        for (item in validReservations) {
            val card = rCard(); val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(rdp(12), rdp(10), rdp(12), rdp(10)) }
            content.addView(rText(item.optString("code").ifBlank { "Reserva" }, 15f, UnamadPalette.navy, true))
            content.addView(rStatusChip(item.optString("status")), LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = rdp(6) })
            val businessName = item.optString("business_name").takeUnless { it == "null" }.orEmpty()
            val listingTitle = item.optString("listing_title").takeUnless { it == "null" }.orEmpty()
            val titleLine = listOf(businessName, listingTitle).filter { it.isNotBlank() }.joinToString(" · ")
            if (titleLine.isNotBlank()) content.addView(rText(titleLine, 13f))
            val total = item.optString("total").takeUnless { it == "null" || it.isBlank() }
            val detail = listOf(modalidadAmigable(item.optString("mode")), fechaHoraAmigable(item.optString("scheduled_at")), total?.let { "S/ $it" })
                .filterNotNull().joinToString(" · ")
            content.addView(rText(detail, 12f))
            if (item.optString("assignee_name").isNotBlank() && item.optString("assignee_name") != "null") {
                content.addView(rText("Atención asignada: ${item.optString("assignee_name")}", 12f))
            }
            val reservationId = item.optString("id").takeIf { it.isNotBlank() && it != "null" }
            if (reservationId != null && item.optString("status") in listOf("SOLICITADA", "CONFIRMADA")) {
                lateinit var cancelButton: MaterialButton
                cancelButton = rButton("Cancelar reserva") {
                    AlertDialog.Builder(this)
                        .setTitle("Cancelar reserva")
                        .setMessage("¿Deseas cancelar la reserva ${item.optString("code")}? Esta acción actualizará su estado en el servidor.")
                        .setNegativeButton("Volver", null)
                        .setPositiveButton("Sí, cancelar") { _, _ -> cancelarReserva(reservationId, cancelButton) }
                        .show()
                }
                content.addView(cancelButton)
            }
            card.addView(content); list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = rdp(8) })
        }
    }

    private fun cancelarReserva(id: String, button: MaterialButton) {
        if (!cancellationsInFlight.add(id)) return
        val requestAtStart = contentRequestId
        button.isEnabled = false
        button.text = "Cancelando…"
        reservationCancelButtons[id] = button
        marketplaceViewModel.cancelarReserva(id, requestAtStart)
    }

    private fun observarEventosMarketplace() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                marketplaceViewModel.events.collect { event ->
                    if (isFinishing || isDestroyed) return@collect
                    when (event) {
                        is MarketplaceBrowseEvent.BusinessLoaded -> {
                            if (event.requestKey == contentRequestId) mostrarPublicaciones(event.business)
                        }
                        is MarketplaceBrowseEvent.ListingsLoaded -> {
                            val business = activeBusinessForListings
                            if (event.requestKey == contentRequestId && currentSection == "BUSINESS" &&
                                business?.optString("id") == event.businessId) renderizarPublicaciones(business, event.items)
                        }
                        is MarketplaceBrowseEvent.ReservationsLoaded -> {
                            if (event.requestKey == contentRequestId && currentSection == "RESERVATIONS") renderizarMisReservas(event.items)
                        }
                        is MarketplaceBrowseEvent.ReservationCancelled -> {
                            cancellationsInFlight.remove(event.reservationId)
                            reservationCancelButtons.remove(event.reservationId)
                            Toast.makeText(this@MarketplaceRemotoActivity, "Reserva cancelada.", Toast.LENGTH_SHORT).show()
                            if (event.requestKey == contentRequestId && currentSection == "RESERVATIONS") cargarMisReservas()
                        }
                        is MarketplaceBrowseEvent.Failed -> {
                            if (event.operation == MarketplaceBrowseEvent.Operation.CANCEL_RESERVATION) {
                                val id = event.reservationId.orEmpty()
                                cancellationsInFlight.remove(id)
                                reservationCancelButtons.remove(id)?.let { button ->
                                    button.isEnabled = true
                                    button.text = "Reintentar cancelación"
                                }
                            }
                            if (event.requestKey == contentRequestId) {
                                when (event.operation) {
                                    MarketplaceBrowseEvent.Operation.BUSINESS -> {
                                        status.text = mensajeErrorAmigable(event.cause)
                                        mostrarReintento(event.cause)
                                    }
                                    MarketplaceBrowseEvent.Operation.LISTINGS -> {
                                        status.text = mensajeErrorAmigable(event.cause)
                                        list.removeAllViews()
                                        list.addView(rButton("Reintentar") {
                                            activeBusinessForListings?.let(::mostrarPublicaciones)
                                        })
                                    }
                                    MarketplaceBrowseEvent.Operation.RESERVATIONS -> {
                                        status.text = "No pudimos cargar tus reservas. Comprueba la conexión y vuelve a intentarlo."
                                        list.removeAllViews()
                                        list.addView(rButton("Reintentar") { cargarMisReservas() })
                                    }
                                    MarketplaceBrowseEvent.Operation.CANCEL_RESERVATION -> showRemoteError(this@MarketplaceRemotoActivity, event.cause)
                                    MarketplaceBrowseEvent.Operation.BUSINESSES -> Unit
                                }
                            } else if (event.operation == MarketplaceBrowseEvent.Operation.CANCEL_RESERVATION) {
                                Toast.makeText(this@MarketplaceRemotoActivity, "No se pudo completar la cancelación. Revisa Mis reservas.", Toast.LENGTH_LONG).show()
                            }
                        }
                        is MarketplaceBrowseEvent.BusinessesLoaded -> Unit
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        activeBookingDialog?.setOnDismissListener(null)
        activeBookingDialog?.dismiss()
        activeBookingDialog = null
        activeBookingDateText = null
        activeBookingSlotContainer = null
        activeBookingSubmitButton = null
        activeBookingProgress = null
        activeBookingCountField = null
        activeBookingNoteField = null
        reservationCancelButtons.clear()
        super.onDestroy()
    }
}

/** Panel remoto del propietario: negocio activo, catálogo, horarios y reservas con UUID. */
