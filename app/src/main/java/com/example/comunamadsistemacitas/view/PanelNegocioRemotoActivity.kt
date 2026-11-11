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


class PanelNegocioRemotoActivity : UnamadBaseActivity() {
    private val panelViewModel: BusinessPanelViewModel by viewModels()
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private var businessId: String = ""
    private var allowedBusinessModes: Set<String> = emptySet()
    private val dashboardActionButtons = mutableListOf<MaterialButton>()
    private val reservationChangesInFlight = mutableSetOf<String>()
    private val reservationStatusButtons = mutableMapOf<String, android.widget.Button>()
    private var pendingMutationDialog: AlertDialog? = null
    private var pendingMutationButton: android.widget.Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        businessId = getSharedPreferences(REMOTE_PREFS, MODE_PRIVATE).getString(ACTIVE_BUSINESS_UUID, "").orEmpty()
        val root = rColumn()
        root.addView(rText("Panel de mi negocio", 25f, UnamadPalette.navy, true))
        root.addView(rText("Administra tus servicios, horarios, equipo y citas desde un solo lugar.", 13f, UnamadPalette.muted))
        status = rText("Conectando…", 12f, UnamadPalette.muted)
        root.addView(status)
        val actionScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val actionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun addPanelAction(label: String, action: () -> Unit) {
            val button = rButton(label, action).apply {
                minWidth = rdp(148)
                minHeight = rdp(46)
            }
            dashboardActionButtons += button
            actionRow.addView(button, LinearLayout.LayoutParams(-2, rdp(46)).apply { marginEnd = rdp(7) })
        }
        addPanelAction("Actualizar panel") { cargarPanel() }
        addPanelAction("Cambiar negocio") { seleccionarNegocio() }
        addPanelAction("Nueva publicación") { mostrarNuevaPublicacion() }
        addPanelAction("Configurar horarios") { configurarHorario() }
        addPanelAction("Agregar trabajador") { agregarTrabajador() }
        addPanelAction("Agregar recurso") { agregarRecurso() }
        actionScroll.addView(actionRow)
        root.addView(actionScroll, LinearLayout.LayoutParams(-1, rdp(54)).apply { topMargin = rdp(6); bottomMargin = rdp(4) })
        val scroll = ScrollView(this)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(rButton("Cerrar sesión en este dispositivo") {
            panelViewModel.cerrarSesion()
            getSharedPreferences(REMOTE_PREFS, MODE_PRIVATE).edit().remove(ACTIVE_BUSINESS_UUID).remove(ACTIVE_USER_ID).apply()
            startActivity(Intent(this, AccesoRemotoActivity::class.java)); finish()
        }, LinearLayout.LayoutParams(-1, rdp(48)))
        agregarNavegacionPrincipal(root, "BUSINESS")
        setContentView(root)

        observarEstadoPanel()

        if (!panelViewModel.haySesion()) {
            startActivity(Intent(this, AccesoRemotoActivity::class.java).putExtra("AFTER_LOGIN", "BUSINESS"))
            finish()
        } else if (businessId.isBlank()) seleccionarNegocio() else cargarPanel()
    }

    private fun observarEstadoPanel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    panelViewModel.uiState.collect { state ->
                        dashboardActionButtons.forEach { button ->
                            button.isEnabled = !state.isLoadingPanel && !state.isSelectingBusiness
                        }
                        when {
                            state.isSelectingBusiness -> status.text = "Consultando tus negocios…"
                            state.isLoadingPanel -> status.text = "Sincronizando servicios, horarios y reservas…"
                            state.dashboardData != null -> {
                                renderizarDashboard(state.dashboardData)
                                status.text = "Información actualizada desde el servidor."
                            }
                            state.errorMessage != null -> {
                                status.text = "No pudimos actualizar el panel. Pulsa ‘Actualizar panel’ para reintentar."
                            }
                        }
                    }
                }
                launch {
                    panelViewModel.events.collect { event ->
                        when (event) {
                            is BusinessPanelEvent.BusinessListLoaded -> mostrarSelectorNegocio(event.businesses)
                            is BusinessPanelEvent.OperationSuccess -> {
                                event.operationId?.let { id ->
                                    reservationChangesInFlight.remove(id)
                                    reservationStatusButtons.remove(id)
                                }
                                pendingMutationDialog?.dismiss()
                                pendingMutationDialog = null
                                pendingMutationButton = null
                                Toast.makeText(this@PanelNegocioRemotoActivity, event.message, Toast.LENGTH_SHORT).show()
                            }
                            is BusinessPanelEvent.OperationFailed -> {
                                event.operationId?.let { id ->
                                    reservationChangesInFlight.remove(id)
                                    reservationStatusButtons.remove(id)?.apply {
                                        isEnabled = true
                                        text = "Reintentar"
                                    }
                                }
                                pendingMutationButton?.apply {
                                    isEnabled = true
                                    text = "Reintentar"
                                }
                                pendingMutationDialog?.setCancelable(true)
                                showRemoteError(this@PanelNegocioRemotoActivity, event.cause)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun mostrarSelectorNegocio(businesses: JSONArray) {
        if (businesses.length() == 0) {
            status.text = "Esta cuenta todavía no tiene negocios asociados."
            showRemoteError(this, IllegalStateException("Esta cuenta todavía no tiene negocios asociados."))
            return
        }
        val validBusinesses = buildList {
            for (index in 0 until businesses.length()) {
                val business = businesses.optJSONObject(index) ?: continue
                val id = business.optString("id").trim()
                val name = business.optString("name").trim()
                if (id.isNotBlank() && id != "null" && name.isNotBlank() && name != "null") add(business)
            }
        }
        if (validBusinesses.isEmpty()) {
            status.text = "No hay negocios con datos válidos para seleccionar."
            return
        }
        val labels = validBusinesses.map { business ->
            "${business.optString("name", "Negocio")} · ${business.optString("district")}"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Selecciona el negocio activo")
            .setItems(labels) { _, which ->
                val selectedBusinessId = validBusinesses.getOrNull(which)?.optString("id").orEmpty()
                if (selectedBusinessId.isBlank()) {
                    showRemoteError(this, IllegalStateException("El negocio seleccionado no tiene un identificador válido."))
                    return@setItems
                }
                if (selectedBusinessId != businessId) content.removeAllViews()
                businessId = selectedBusinessId
                getSharedPreferences(REMOTE_PREFS, MODE_PRIVATE).edit()
                    .putString(ACTIVE_BUSINESS_UUID, businessId).apply()
                cargarPanel()
            }
            .show()
    }

    private fun seleccionarNegocio() {
        if (panelViewModel.uiState.value.isSelectingBusiness) return
        dashboardActionButtons.forEach { it.isEnabled = false }
        status.text = "Consultando tus negocios…"
        panelViewModel.seleccionarNegocio()
    }

    private fun cargarPanel() {
        if (businessId.isBlank()) {
            seleccionarNegocio()
            return
        }
        status.text = "Sincronizando servicios, horarios y reservas…"
        panelViewModel.cargarPanel(businessId)
    }

    private fun renderizarDashboard(data: JSONObject) {
        val business = data.optJSONObject("business")
        val businessIdFromServer = business?.optString("id")?.trim().orEmpty()
        val businessNameFromServer = business?.optString("name")?.trim().orEmpty()
        if (business == null || businessIdFromServer.isBlank() || businessIdFromServer == "null" || businessNameFromServer.isBlank()) {
            content.removeAllViews()
            content.addView(rText("El servidor devolvió un perfil de negocio incompleto. No se mostrarán datos parciales.", 14f, UnamadPalette.danger))
            content.addView(rButton("Volver a intentar") { cargarPanel() })
            return
        }
        val listings = data.optJSONArray("listings") ?: JSONArray()
        val reservations = data.optJSONArray("reservations") ?: JSONArray()
        val hours = data.optJSONArray("hours") ?: JSONArray()
        val staff = data.optJSONArray("staff") ?: JSONArray()
        val resources = data.optJSONArray("resources") ?: JSONArray()
        allowedBusinessModes = buildSet {
            val configured = business.optJSONArray("modes") ?: JSONArray()
            for (modeIndex in 0 until configured.length()) {
                configured.optString(modeIndex).takeIf { it.isNotBlank() && it != "null" }?.let(::add)
            }
        }
        content.removeAllViews()
        content.addView(rText(business.optString("name"), 20f, UnamadPalette.navy, true))
        content.addView(rText(listOf(business.optString("district"), business.optString("province"), business.optString("subscriptionStatus"))
            .filter { it.isNotBlank() && it != "null" }.joinToString(" · ").ifBlank { "Datos del establecimiento" }, 12f))
        val validDashboardListings = buildList {
            for (i in 0 until listings.length()) {
                val item = listings.optJSONObject(i) ?: continue
                val id = item.optString("id").trim()
                val title = item.optString("title").trim()
                if (id.isNotBlank() && id != "null" && title.isNotBlank()) add(item)
            }
        }
        content.addView(rText("Publicaciones (${validDashboardListings.size})", 17f, UnamadPalette.navy, true).apply { setPadding(0, rdp(14), 0, rdp(6)) })
        for (item in validDashboardListings) {
            val card = rCard(); val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(rdp(12), rdp(10), rdp(12), rdp(10)) }
            box.addView(rText(item.optString("title"), 15f, UnamadPalette.navy, true))
            box.addView(rText("${if (item.optString("kind") == "PRODUCTO") "Producto" else "Servicio"} · ${modalidadAmigable(item.optString("mode"))} · S/ ${item.optString("price")}", 12f))
            card.addView(box); content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = rdp(6) })
        }
        content.addView(rText("Horario semanal (${hours.length()} bloques)", 17f, UnamadPalette.navy, true).apply { setPadding(0, rdp(14), 0, rdp(6)) })
        if (hours.length() == 0) content.addView(rText("Aún no has configurado horarios de atención.", 12f))
        for (i in 0 until hours.length()) {
            val h = hours.optJSONObject(i) ?: continue
            content.addView(rText("${diaSemanaAmigable(h.optInt("day_of_week"))}: ${h.optString("opens_at")} – ${h.optString("closes_at")} ${if (h.optBoolean("active")) "" else "(inactivo)"}", 12f))
        }
        content.addView(rText("Trabajadores (${staff.length()})", 17f, UnamadPalette.navy, true).apply { setPadding(0, rdp(14), 0, rdp(6)) })
        for (i in 0 until staff.length()) {
            val person = staff.optJSONObject(i) ?: continue
            content.addView(rText("${person.optString("name")} · ${person.optString("specialty")}", 12f))
        }
        content.addView(rText("Puestos y recursos (${resources.length()})", 17f, UnamadPalette.navy, true).apply { setPadding(0, rdp(14), 0, rdp(6)) })
        for (i in 0 until resources.length()) {
            val resource = resources.optJSONObject(i) ?: continue
            content.addView(rText("${resource.optString("name")} · ${resource.optString("kind")}", 12f))
        }
        val validDashboardReservations = buildList {
            for (i in 0 until reservations.length()) {
                val item = reservations.optJSONObject(i) ?: continue
                val id = item.optString("id").trim()
                val statusValue = item.optString("status")
                if (id.isNotBlank() && id != "null" && statusValue in setOf("SOLICITADA", "CONFIRMADA", "LISTA", "COMPLETADA", "CANCELADA", "NO_SHOW")) add(item)
            }
        }
        content.addView(rText("Reservas recibidas (${validDashboardReservations.size})", 17f, UnamadPalette.navy, true).apply { setPadding(0, rdp(14), 0, rdp(6)) })
        if (validDashboardReservations.isEmpty()) content.addView(rText("No hay reservas válidas para mostrar.", 12f))
        for (reservation in validDashboardReservations) {
            val card = rCard(); val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(rdp(14), rdp(12), rdp(14), rdp(12)) }
            box.addView(rText(reservation.optString("code").ifBlank { "Reserva" }, 14f, UnamadPalette.navy, true))
            box.addView(rStatusChip(reservation.optString("status")), LinearLayout.LayoutParams(-2, -2).apply { topMargin = rdp(4); bottomMargin = rdp(6) })
            box.addView(rText("${reservation.optString("customer_name")} · ${reservation.optString("customer_phone")}", 12f))
            box.addView(rText("${reservation.optString("listing_title")} · ${modalidadAmigable(reservation.optString("mode"))} · ${fechaHoraAmigable(reservation.optString("scheduled_at"))}", 12f))
            if (reservation.optString("assignee_name").isNotBlank()) {
                box.addView(rText("Asignado a: ${reservation.optString("assignee_name")} (${reservation.optString("assignee_type")})", 12f))
            }
            box.addView(rText("Cantidad: ${reservation.optInt("quantity")} · Total: S/ ${reservation.optString("total")}", 12f))
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
            val reservationId = reservation.optString("id").takeIf { it.isNotBlank() && it != "null" }
            fun addStateAction(label: String, nextState: String) {
                if (reservationId == null) return
                lateinit var actionButton: MaterialButton
                actionButton = rButton(label) { cambiarEstado(reservationId, nextState, actionButton) }
                actions.addView(actionButton)
            }
            when (reservation.optString("status")) {
                "SOLICITADA" -> addStateAction("Confirmar", "CONFIRMADA")
                "CONFIRMADA" -> addStateAction(
                    if (reservation.optString("mode") == "RECOJO") "Lista" else "Completar",
                    if (reservation.optString("mode") == "RECOJO") "LISTA" else "COMPLETADA"
                )
                "LISTA" -> addStateAction("Entregada", "COMPLETADA")
            }
            if (actions.childCount > 0) box.addView(actions)
            card.addView(box); content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = rdp(7) })
        }
    }

    private fun agregarTrabajador() {
        if (businessId.isBlank()) { seleccionarNegocio(); return }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(rdp(8), rdp(4), rdp(8), rdp(4)) }
        val name = rField("Nombre del trabajador")
        val specialty = rField("Especialidad (opcional)")
        box.rAddField(name, this); box.rAddField(specialty, this)
        val dialog = AlertDialog.Builder(this).setTitle("Agregar trabajador")
            .setMessage("El trabajador aparecerá como opción real en la disponibilidad de citas.")
            .setView(box).setNegativeButton("Cancelar", null).setPositiveButton("Guardar", null).create()
        dialog.setOnShowListener {
            val submit = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            submit.setOnClickListener {
                if (!name.text.toString().trim().isNotEmpty()) { name.error = "Ingresa el nombre"; return@setOnClickListener }
                val workerName = name.text.toString().trim()
                val workerSpecialty = specialty.text.toString().trim()
                val targetBusinessId = businessId
                submit.isEnabled = false
                submit.text = "Guardando…"
                dialog.setCancelable(false)
                pendingMutationDialog = dialog
                pendingMutationButton = submit
                panelViewModel.agregarTrabajador(targetBusinessId, workerName, workerSpecialty)
            }
        }
        dialog.show()
    }

    private fun agregarRecurso() {
        if (businessId.isBlank()) { seleccionarNegocio(); return }
        val name = rField("Nombre del puesto o recurso")
        val dialog = AlertDialog.Builder(this).setTitle("Agregar capacidad de atención")
            .setMessage("Ejemplos: Silla 2, Sala 1 o Equipo de diagnóstico 1. Cada recurso permite una cita simultánea independiente.")
            .setView(name).setNegativeButton("Cancelar", null).setPositiveButton("Guardar", null).create()
        dialog.setOnShowListener {
            val submit = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            submit.setOnClickListener {
                if (name.text.isBlank()) { name.error = "Ingresa un nombre"; return@setOnClickListener }
                val resourceName = name.text.toString().trim()
                val targetBusinessId = businessId
                submit.isEnabled = false
                submit.text = "Guardando…"
                dialog.setCancelable(false)
                pendingMutationDialog = dialog
                pendingMutationButton = submit
                panelViewModel.agregarRecurso(targetBusinessId, resourceName)
            }
        }
        dialog.show()
    }

    private fun cambiarEstado(reservationId: String, state: String, button: MaterialButton) {
        if (!reservationChangesInFlight.add(reservationId)) return
        button.isEnabled = false
        button.text = "Actualizando…"
        reservationStatusButtons[reservationId] = button
        panelViewModel.cambiarEstadoReserva(businessId, reservationId, state)
    }

    private fun mostrarNuevaPublicacion() {
        if (businessId.isBlank()) { seleccionarNegocio(); return }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(rdp(8), rdp(4), rdp(8), rdp(4)) }
        val title = rField("Nombre del servicio o producto").rLimit(140)
        val description = rMultilineField("Describe el servicio o producto", 3).rLimit(4000)
        val price = rField("Precio en soles", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL).rLimit(14)
        val compare = rField("Precio anterior (opcional)", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL).rLimit(14)
        val kind = Spinner(this).apply { adapter = ArrayAdapter(this@PanelNegocioRemotoActivity, android.R.layout.simple_spinner_item, listOf("SERVICIO", "PRODUCTO")) }
        val mode = Spinner(this)
        fun updateModes() {
            val choices = if (kind.selectedItemPosition == 1) {
                if ("RECOJO" in allowedBusinessModes) listOf("RECOJO") else listOf("RECOGIDA NO HABILITADA")
            } else {
                buildList {
                    if ("CITA" in allowedBusinessModes) add("CITA")
                    if ("RECOJO" in allowedBusinessModes) add("RECOJO")
                    if ("CITA" in allowedBusinessModes && "RECOJO" in allowedBusinessModes) add("AMBOS")
                }.ifEmpty { listOf("SIN MODALIDAD HABILITADA") }
            }
            mode.adapter = ArrayAdapter(this@PanelNegocioRemotoActivity, android.R.layout.simple_spinner_item, choices).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }
        kind.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) = updateModes()
        }
        updateModes()
        val duration = rField("Duración en minutos (5–1440, servicios)", InputType.TYPE_CLASS_NUMBER).rLimit(4)
        val stock = rField("Stock (productos, opcional)", InputType.TYPE_CLASS_NUMBER).rLimit(9)
        box.addView(rText("Tipo")); box.addView(kind); box.addView(rText("Modalidad")); box.addView(mode)
        listOf(title, description, price, compare, duration, stock).forEach { box.rAddField(it, this) }
        val dialog = AlertDialog.Builder(this).setTitle("Nueva publicación").setView(box).setNegativeButton("Cancelar", null).setPositiveButton("Guardar", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val amount = price.text.toString().replace(',', '.').toDoubleOrNull()
                val old = compare.text.toString().replace(',', '.').toDoubleOrNull()
                if (title.text.isBlank() || amount == null || !amount.isFinite() || amount < 0 ||
                    (old != null && (!old.isFinite() || old < amount)) ||
                    (compare.text.isNotBlank() && old == null)) {
                    Toast.makeText(this, "Ingresa título y precio válido. El precio anterior debe ser igual o mayor al actual.", Toast.LENGTH_LONG).show(); return@setOnClickListener
                }
                val selectedKind = kind.selectedItem.toString()
                val durationValue = duration.text.toString().takeIf { it.isNotBlank() }?.toIntOrNull()
                if (selectedKind == "SERVICIO" && durationValue != null && durationValue !in 5..1440) {
                    duration.error = "Usa entre 5 y 1440 minutos"; return@setOnClickListener
                }
                val stockValue = stock.text.toString().takeIf { it.isNotBlank() }?.toIntOrNull()
                if (selectedKind == "PRODUCTO" && stock.text.isNotBlank() && (stockValue == null || stockValue < 0)) {
                    stock.error = "El stock debe ser cero o mayor"; return@setOnClickListener
                }
                val selectedMode = mode.selectedItem?.toString().orEmpty()
                if (selectedMode !in listOf("CITA", "RECOJO", "AMBOS")) {
                    Toast.makeText(this, "Habilita una modalidad válida para este negocio antes de publicar.", Toast.LENGTH_LONG).show(); return@setOnClickListener
                }
                val payload = JSONObject().put("title", title.text.toString().trim()).put("description", description.text.toString().trim())
                    .put("kind", selectedKind).put("mode", selectedMode).put("price", amount)
                if (old != null) payload.put("compareAtPrice", old)
                if (selectedKind == "SERVICIO") durationValue?.let { payload.put("durationMinutes", it) }
                if (selectedKind == "PRODUCTO") stockValue?.let { payload.put("stock", it) }
                val targetBusinessId = businessId
                val submit = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                submit.isEnabled = false
                submit.text = "Guardando…"
                dialog.setCancelable(false)
                pendingMutationDialog = dialog
                pendingMutationButton = submit
                panelViewModel.publicar(targetBusinessId, payload)
            }
        }
        dialog.show()
    }

    private fun configurarHorario() {
        if (businessId.isBlank()) { seleccionarNegocio(); return }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(rdp(8), rdp(4), rdp(8), rdp(4)) }
        box.addView(rText("Se aplicará el mismo bloque de atención de lunes a sábado. Puedes ajustarlo después por día desde el panel web.", 12f))
        val open = rField("Apertura (HH:mm)").apply { setText("09:00") }
        val close = rField("Cierre (HH:mm)").apply { setText("18:00") }
        box.rAddField(open, this); box.rAddField(close, this)
        val dialog = AlertDialog.Builder(this).setTitle("Horario semanal").setView(box).setNegativeButton("Cancelar", null).setPositiveButton("Guardar", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val pattern = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
                if (!pattern.matches(open.text) || !pattern.matches(close.text) || open.text.toString() >= close.text.toString()) {
                    Toast.makeText(this, "Usa horas HH:mm y una hora de cierre posterior.", Toast.LENGTH_LONG).show(); return@setOnClickListener
                }
                val hours = JSONArray()
                val opensAt = open.text.toString()
                val closesAt = close.text.toString()
                (1..6).forEach { day -> hours.put(JSONObject().put("dayOfWeek", day).put("opensAt", opensAt).put("closesAt", closesAt).put("active", true)) }
                val targetBusinessId = businessId
                val submit = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                submit.isEnabled = false
                submit.text = "Guardando…"
                dialog.setCancelable(false)
                pendingMutationDialog = dialog
                pendingMutationButton = submit
                panelViewModel.configurarHorarios(targetBusinessId, hours)
            }
        }
        dialog.show()
    }

    override fun onDestroy() {
        pendingMutationDialog?.setOnDismissListener(null)
        pendingMutationDialog?.dismiss()
        pendingMutationDialog = null
        pendingMutationButton = null
        reservationStatusButtons.clear()
        dashboardActionButtons.clear()
        super.onDestroy()
    }
}
