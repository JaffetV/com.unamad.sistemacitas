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


class AccesoRemotoActivity : UnamadBaseActivity() {
    private val authViewModel: AuthViewModel by viewModels()
    private val marketplaceViewModel: MarketplaceBrowseViewModel by viewModels()
    private lateinit var email: EditText
    private lateinit var password: EditText
    private lateinit var status: TextView
    private lateinit var loginButton: MaterialButton
    private lateinit var registerButton: MaterialButton
    private var authenticationInProgress = false
    // Evita iniciar dos veces la navegación si el token y el evento llegan durante una recreación.
    private var accountRoutingStarted = false
    private var accountRequestKey = 0
    private var registrationDialog: AlertDialog? = null
    private var registrationSubmitButton: android.widget.Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = rColumn()
        root.addView(rText("Tu cuenta UNAMAD", 25f, UnamadPalette.navy, true))
        root.addView(rText("Accede para reservar tus citas o administrar los servicios de tu negocio.", 14f, UnamadPalette.muted))
        val form = rCard()
        val formContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(rdp(14), rdp(12), rdp(14), rdp(14))
        }
        email = rField("Correo electrónico", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS).rLimit(254).apply {
            id = com.example.comunamadsistemacitas.R.id.login_email
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_NEXT
        }
        password = rField("Contraseña", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD).rLimit(128).apply {
            id = com.example.comunamadsistemacitas.R.id.login_password
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        }
        formContent.addView(rText("Iniciar sesión", 17f, UnamadPalette.navy, true))
        formContent.rAddField(email, this)
        formContent.rAddField(password, this)
        status = rText("Tu sesión se guarda de forma cifrada en este dispositivo.", 12f, UnamadPalette.muted)
        formContent.addView(status)
        loginButton = rButton("Iniciar sesión") { iniciarSesion() }.apply { id = com.example.comunamadsistemacitas.R.id.login_submit }
        formContent.addView(loginButton, LinearLayout.LayoutParams(-1, rdp(50)))
        registerButton = rButton("Crear cuenta gratuita de cliente") { mostrarRegistroCliente() }.apply {
            backgroundTintList = android.content.res.ColorStateList.valueOf(UnamadPalette.navy)
        }
        formContent.addView(registerButton, LinearLayout.LayoutParams(-1, rdp(50)).apply { topMargin = rdp(8) })
        form.addView(formContent)
        root.addView(form, LinearLayout.LayoutParams(-1, -2).apply { topMargin = rdp(14) })
        root.addView(rButton("Volver al inicio") {
            val intent = Intent(this, PortalMarketplaceActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }, LinearLayout.LayoutParams(-1, rdp(48)).apply { topMargin = rdp(12) })
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(root)
            clipToPadding = false
            setPadding(0, 0, 0, rdp(12))
        })
        observarEstadoAutenticacion()
        if (marketplaceViewModel.haySesion() && !authViewModel.uiState.value.isLoading) cargarNegociosDeCuenta()
    }

    private fun observarEstadoAutenticacion() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    authViewModel.uiState.collect { state ->
                        authenticationInProgress = state.isLoading
                        if (::loginButton.isInitialized) {
                            loginButton.isEnabled = !state.isLoading
                            loginButton.text = if (state.isLoading) "Verificando…" else "Iniciar sesión"
                        }
                        if (::registerButton.isInitialized) registerButton.isEnabled = !state.isLoading
                        registrationSubmitButton?.let { button ->
                            button.isEnabled = !state.isLoading
                            button.text = if (state.isLoading) "Creando cuenta…" else "Crear cuenta"
                        }
                        if (state.statusMessage.isNotBlank()) status.text = state.statusMessage
                    }
                }
                launch {
                    authViewModel.events.collect { event ->
                        when (event) {
                            is AuthEvent.Authenticated -> {
                                getSharedPreferences(REMOTE_PREFS, MODE_PRIVATE).edit()
                                    .putString(ACTIVE_USER_ID, event.user.optString("id"))
                                    .putString(ACTIVE_USER_NAME, event.user.optString("full_name"))
                                    .apply()
                                if (event.source == AuthEvent.Source.CUSTOMER_REGISTRATION) registrationDialog?.dismiss()
                                cargarNegociosDeCuenta()
                            }
                            is AuthEvent.Failed -> showRemoteError(this@AccesoRemotoActivity, event.cause)
                        }
                    }
                }
                launch {
                    marketplaceViewModel.events.collect { event ->
                        when (event) {
                            is MarketplaceBrowseEvent.BusinessesLoaded -> {
                                if (event.requestKey == accountRequestKey && !isFinishing && !isDestroyed) {
                                    mostrarSeleccionNegocio(event.items)
                                }
                            }
                            is MarketplaceBrowseEvent.Failed -> {
                                if (event.operation == MarketplaceBrowseEvent.Operation.BUSINESSES &&
                                    event.requestKey == accountRequestKey && !isFinishing && !isDestroyed) {
                                    accountRoutingStarted = false
                                    status.text = "No pudimos cargar tus negocios."
                                    showRemoteError(this@AccesoRemotoActivity, event.cause)
                                }
                            }
                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    private fun iniciarSesion() {
        if (authenticationInProgress) return
        val mail = email.text.toString().trim()
        val pass = password.text.toString()
        val validationError = AuthValidation.validateLogin(mail, pass)
        if (validationError != null) {
            email.rSetValidationError(if (!android.util.Patterns.EMAIL_ADDRESS.matcher(mail).matches()) "Escribe un correo válido" else null)
            password.rSetValidationError(if (pass.isBlank() || pass.length < 10) "La contraseña debe tener al menos 10 caracteres" else null)
            Toast.makeText(this, validationError, Toast.LENGTH_LONG).show()
            return
        }
        authViewModel.login(mail, pass)
    }

    private fun intentMarketplaceDespuesDeAcceso(): Intent {
        val next = Intent(this, MarketplaceRemotoActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        when (intent.getStringExtra("AFTER_LOGIN")) {
            "RESERVATIONS" -> next.putExtra("SHOW_RESERVATIONS", true)
            "BOOKING" -> {
                next.putExtra("OPEN_BUSINESS_ID", intent.getStringExtra("OPEN_BUSINESS_ID"))
                next.putExtra("RESERVE_LISTING", intent.getStringExtra("RESERVE_LISTING"))
            }
        }
        return next
    }

    private fun cargarNegociosDeCuenta() {
        if (accountRoutingStarted || isFinishing || isDestroyed) return
        accountRoutingStarted = true
        if (intent.getStringExtra("AFTER_LOGIN") != "BUSINESS") {
            startActivity(intentMarketplaceDespuesDeAcceso())
            finish()
            return
        }
        status.text = "Consultando los negocios de tu cuenta…"
        accountRequestKey = marketplaceViewModel.nuevaClaveSolicitud()
        marketplaceViewModel.cargarMisNegocios(accountRequestKey)
    }

    private fun mostrarSeleccionNegocio(businesses: JSONArray) {
        if (businesses.length() == 0) {
            getSharedPreferences(REMOTE_PREFS, MODE_PRIVATE).edit().remove(ACTIVE_BUSINESS_UUID).apply()
            startActivity(Intent(this, RegistroNegocioRemotoActivity::class.java))
            finish()
            return
        }
        val validBusinesses = buildList {
            for (index in 0 until businesses.length()) {
                val item = businesses.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()
                val name = item.optString("name").trim()
                if (id.isNotBlank() && id != "null" && name.isNotBlank() && name != "null") add(item)
            }
        }
        if (validBusinesses.isEmpty()) {
            accountRoutingStarted = false
            showRemoteError(this, IllegalStateException("El servidor no devolvió negocios con identificadores válidos."))
            return
        }
        val labels = validBusinesses.map { item ->
            "${item.optString("name", "Negocio")} · ${item.optString("district")}".trimEnd(' ', '·')
        }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Selecciona tu negocio")
            .setItems(labels) { _, which ->
                val chosen = validBusinesses.getOrNull(which)
                val chosenId = chosen?.optString("id")?.takeIf { it.isNotBlank() && it != "null" }
                if (chosenId == null) {
                    accountRoutingStarted = false
                    showRemoteError(this, IllegalStateException("El negocio seleccionado no tiene un identificador válido."))
                    return@setItems
                }
                getSharedPreferences(REMOTE_PREFS, MODE_PRIVATE).edit()
                    .putString(ACTIVE_BUSINESS_UUID, chosenId).apply()
                startActivity(Intent(this, PanelNegocioRemotoActivity::class.java))
                finish()
            }.setOnCancelListener {
                accountRoutingStarted = false
                status.text = "Sesión iniciada. Selecciona un negocio para continuar."
            }.show()
    }

    private fun mostrarRegistroCliente() {
        if (authenticationInProgress) return
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(rdp(8), rdp(4), rdp(8), rdp(4)) }
        val name = rField("Nombre y apellidos").rLimit(140)
        val mail = rField("Correo electrónico", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS).rLimit(254)
        val phone = rField("Teléfono", InputType.TYPE_CLASS_PHONE).rLimit(30)
        val pass = rField("Contraseña (10–128 caracteres)", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD).rLimit(128)
        listOf(name, mail, phone, pass).forEach { box.rAddField(it, this) }
        val dialog = AlertDialog.Builder(this).setTitle("Crear cuenta de cliente")
            .setView(box).setNegativeButton("Cancelar", null).setPositiveButton("Crear cuenta", null).create()
        registrationDialog = dialog
        dialog.setOnDismissListener { registrationDialog = null; registrationSubmitButton = null }
        dialog.setOnShowListener {
            val submit = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            submit.setOnClickListener {
                if (authenticationInProgress) return@setOnClickListener
                val validationError = AuthValidation.validateCustomer(
                    name.text.toString(), mail.text.toString(), phone.text.toString(), pass.text.toString()
                )
                if (validationError != null) {
                    name.rSetValidationError(if (name.text.toString().isBlank()) "Ingresa tu nombre" else null)
                    mail.rSetValidationError(if (!android.util.Patterns.EMAIL_ADDRESS.matcher(mail.text.toString().trim()).matches()) "Correo no válido" else null)
                    phone.rSetValidationError(if (phone.text.toString().filter(Char::isDigit).length < 7) "Teléfono no válido" else null)
                    pass.rSetValidationError(if (pass.text.isBlank() || pass.text.length < 10) "Usa al menos 10 caracteres" else null)
                    Toast.makeText(this@AccesoRemotoActivity, validationError, Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                listOf(name, mail, phone, pass).forEach { it.rSetValidationError(null) }
                val customerName = name.text.toString().trim()
                val customerEmail = mail.text.toString().trim()
                val customerPhone = phone.text.toString().trim()
                val customerPassword = pass.text.toString()
                registrationSubmitButton = submit
                authViewModel.registerCustomer(customerName, customerEmail, customerPhone, customerPassword)
            }
        }
        dialog.show()
    }

    override fun onDestroy() {
        registrationDialog?.setOnDismissListener(null)
        registrationDialog?.dismiss()
        registrationDialog = null
        registrationSubmitButton = null
        super.onDestroy()
    }
}

/** Alta de negocio directamente en PostgreSQL; el UUID devuelto se usa en el panel. */
