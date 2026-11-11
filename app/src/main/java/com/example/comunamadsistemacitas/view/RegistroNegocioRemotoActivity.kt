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


class RegistroNegocioRemotoActivity : UnamadBaseActivity() {
    private val registrationViewModel: BusinessRegistrationViewModel by viewModels()
    private lateinit var categories: JSONArray
    private lateinit var categorySpinner: Spinner
    private lateinit var ownerName: EditText
    private lateinit var ownerEmail: EditText
    private lateinit var ownerPhone: EditText
    private lateinit var ownerPassword: EditText
    private lateinit var businessName: EditText
    private lateinit var description: EditText
    private lateinit var department: EditText
    private lateinit var province: EditText
    private lateinit var district: EditText
    private lateinit var address: EditText
    private lateinit var modeCita: CheckBox
    private lateinit var modeRecojo: CheckBox
    private lateinit var status: TextView
    private lateinit var registerButton: MaterialButton
    private lateinit var retryCategoriesButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        categories = JSONArray()
        val root = rColumn()
        val scroll = ScrollView(this)
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(form); root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        form.addView(rText("Registra tu negocio", 25f, UnamadPalette.navy, true))
        form.addView(rText("El servidor crea tu cuenta de propietario y activa una prueba comercial de 14 días.", 13f))
        ownerName = rField("Nombre del responsable").rLimit(140)
        ownerEmail = rField("Correo electrónico", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS).rLimit(254)
        ownerPhone = rField("Teléfono del responsable", InputType.TYPE_CLASS_PHONE).rLimit(30)
        ownerPassword = rField("Contraseña (10–128 caracteres)", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD).rLimit(128)
        businessName = rField("Nombre comercial").rLimit(140)
        description = rMultilineField("Describe los servicios que ofrece tu negocio", 3).rLimit(4000)
        department = rField("Departamento (ej. Madre de Dios)").rLimit(100)
        province = rField("Provincia (ej. Tambopata)").rLimit(100)
        district = rField("Distrito (ej. Puerto Maldonado)").rLimit(100)
        address = rField("Dirección del establecimiento").rLimit(220)
        listOf(ownerName, ownerEmail, ownerPhone, ownerPassword, businessName, description, department, province, district, address).forEach { form.rAddField(it, this) }
        form.addView(rText("Categoría", 13f, UnamadPalette.muted, true))
        categorySpinner = Spinner(this); form.addView(categorySpinner, LinearLayout.LayoutParams(-1, rdp(50)))
        modeCita = CheckBox(this).apply { text = "Atención mediante citas"; isChecked = true }
        modeRecojo = CheckBox(this).apply { text = "Pedidos para recoger" }
        form.addView(modeCita); form.addView(modeRecojo)
        status = rText("Cargando categorías del servidor…", 12f)
        form.addView(status)
        registerButton = rButton("Registrar negocio") { registrar() }.apply { isEnabled = false }
        root.addView(registerButton, LinearLayout.LayoutParams(-1, rdp(52)))
        retryCategoriesButton = rButton("Reintentar cargar categorías") { cargarCategorias() }.apply { visibility = View.GONE }
        root.addView(retryCategoriesButton, LinearLayout.LayoutParams(-1, rdp(48)))
        root.addView(rButton("Volver") { finish() }, LinearLayout.LayoutParams(-1, rdp(48)))
        setContentView(root)

        observarEstadoRegistro()

        cargarCategorias()
        if (registrationViewModel.haySesion()) {
            ownerPassword.visibility = View.GONE
            ownerName.hint = "Nombre del responsable (cuenta actual)"
            ownerEmail.hint = "Correo electrónico (cuenta actual)"
            registrationViewModel.cargarPerfil()
        }
    }

    private fun observarEstadoRegistro() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    registrationViewModel.uiState.collect { state ->
                        // Conserva la categoría elegida por ID, no por posición: el servidor
                        // puede devolver otra lista con la misma cantidad de elementos.
                        val previousCategoryId = categories
                            .optJSONObject(categorySpinner.selectedItemPosition)
                            ?.optString("id")
                            ?.takeIf { it.isNotBlank() && it != "null" }
                        categories = JSONArray().apply {
                            for (index in 0 until state.categories.length()) {
                                val category = state.categories.optJSONObject(index) ?: continue
                                val id = category.optString("id").trim()
                                val name = category.optString("name").trim()
                                if (id.isNotBlank() && id != "null" && name.isNotBlank()) put(category)
                            }
                        }
                        val json = categories
                        val labels = (0 until json.length()).map { i ->
                            val category = json.optJSONObject(i) ?: JSONObject()
                            "${category.optString("emoji", "🏪")}  ${category.optString("name", "Categoría")}"
                        }
                        // Siempre reemplaza el adapter: comparar solo el count dejaba
                        // nombres antiguos visibles cuando la API cambiaba categorías 1:1.
                        categorySpinner.adapter = ArrayAdapter(
                            this@RegistroNegocioRemotoActivity,
                            android.R.layout.simple_spinner_item,
                            labels
                        ).also {
                            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        }
                        val restoredPosition = (0 until json.length()).firstOrNull { index ->
                            json.optJSONObject(index)?.optString("id") == previousCategoryId
                        } ?: 0
                        if (json.length() > 0) categorySpinner.setSelection(restoredPosition)

                        if (state.userProfile != null && ownerName.text.isBlank()) {
                            ownerName.setText(state.userProfile.optString("full_name"))
                            ownerEmail.setText(state.userProfile.optString("email"))
                            ownerPhone.setText(state.userProfile.optString("phone"))
                        }

                        if (state.isRegistering) {
                            registerButton.isEnabled = false
                            registerButton.text = "Registrando…"
                            status.text = "Registrando negocio…"
                        } else if (state.isLoadingCategories) {
                            registerButton.isEnabled = false
                            retryCategoriesButton.visibility = View.GONE
                            status.text = "Cargando categorías del servidor…"
                        } else if (state.errorMessage != null && categories.length() == 0) {
                            status.text = "No pudimos cargar las categorías. Comprueba la conexión y reintenta."
                            retryCategoriesButton.visibility = View.VISIBLE
                            registerButton.isEnabled = false
                        } else if (state.errorMessage != null) {
                            registerButton.isEnabled = true
                            registerButton.text = "Reintentar registro"
                            status.text = "No se pudo registrar el negocio. Revisa los datos e inténtalo nuevamente."
                        } else {
                            retryCategoriesButton.visibility = View.GONE
                            status.text = if (categories.length() == 0) "No hay categorías disponibles todavía." else "${categories.length()} categorías disponibles."
                            registerButton.isEnabled = categories.length() > 0
                            registerButton.text = "Registrar negocio"
                        }
                    }
                }
                launch {
                    registrationViewModel.events.collect { event ->
                        when (event) {
                            is BusinessRegistrationEvent.Registered -> {
                                val registeredBusinessId = event.registeredBusiness.optString("id")
                                    .takeIf { it.isNotBlank() && it != "null" }
                                if (registeredBusinessId == null) {
                                    showRemoteError(this@RegistroNegocioRemotoActivity, IllegalStateException("El servidor confirmó el registro, pero no devolvió el identificador del negocio. Vuelve a consultar tus negocios."))
                                    return@collect
                                }
                                getSharedPreferences(REMOTE_PREFS, MODE_PRIVATE).edit()
                                    .putString(ACTIVE_USER_ID, event.registeredBusiness.optString("ownerUserId"))
                                    .putString(ACTIVE_USER_NAME, event.ownerFullName)
                                    .putString(ACTIVE_BUSINESS_UUID, registeredBusinessId).apply()
                                Toast.makeText(this@RegistroNegocioRemotoActivity, "Negocio registrado en el servidor.", Toast.LENGTH_LONG).show()
                                startActivity(Intent(this@RegistroNegocioRemotoActivity, PanelNegocioRemotoActivity::class.java))
                                finish()
                            }
                            is BusinessRegistrationEvent.Failed -> {
                                showRemoteError(this@RegistroNegocioRemotoActivity, event.cause)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun cargarCategorias() {
        registrationViewModel.cargarCategorias()
    }

    private fun registrar() {
        if (registrationViewModel.uiState.value.isRegistering) return
        val modes = JSONArray()
        if (modeCita.isChecked) modes.put("CITA")
        if (modeRecojo.isChecked) modes.put("RECOJO")
        if (categories.length() == 0 || categorySpinner.selectedItemPosition !in 0 until categories.length()) {
            Toast.makeText(this, "Selecciona una categoría válida.", Toast.LENGTH_LONG).show(); return
        }
        val existingSession = registrationViewModel.haySesion()
        val validationError = BusinessValidation.validateBusinessRegistration(
            ownerName = ownerName.text.toString(),
            ownerEmail = ownerEmail.text.toString(),
            ownerPhone = ownerPhone.text.toString(),
            ownerPassword = ownerPassword.text.toString(),
            businessName = businessName.text.toString(),
            department = department.text.toString(),
            province = province.text.toString(),
            district = district.text.toString(),
            address = address.text.toString(),
            hasModes = modes.length() > 0,
            existingSession = existingSession
        )
        if (validationError != null) {
            ownerName.rSetValidationError(if (!existingSession && ownerName.text.isBlank()) "Ingresa el nombre del responsable" else if (ownerName.text.length > 140) "Máximo 140 caracteres" else null)
            ownerEmail.rSetValidationError(if (!existingSession && !android.util.Patterns.EMAIL_ADDRESS.matcher(ownerEmail.text.toString().trim()).matches()) "Correo no válido" else if (ownerEmail.text.length > 254) "Máximo 254 caracteres" else null)
            ownerPassword.rSetValidationError(if (!existingSession && ownerPassword.text.length < 10) "Usa al menos 10 caracteres" else if (ownerPassword.text.length > 128) "Máximo 128 caracteres" else null)
            ownerPhone.rSetValidationError(if (ownerPhone.text.toString().filter(Char::isDigit).length < 7) "Teléfono no válido" else if (ownerPhone.text.length > 30) "Máximo 30 caracteres" else null)
            businessName.rSetValidationError(if (businessName.text.isBlank()) "Ingresa el nombre comercial" else null)
            department.rSetValidationError(if (department.text.isBlank()) "Completa el departamento" else null)
            province.rSetValidationError(if (province.text.isBlank()) "Completa la provincia" else null)
            district.rSetValidationError(if (district.text.isBlank()) "Completa el distrito" else null)
            address.rSetValidationError(if (address.text.isBlank()) "Completa la dirección" else null)
            Toast.makeText(this, validationError, Toast.LENGTH_LONG).show()
            return
        }
        listOf(ownerName, ownerEmail, ownerPhone, ownerPassword, businessName, department, province, district, address).forEach { it.rSetValidationError(null) }
        val categoryId = categories.optJSONObject(categorySpinner.selectedItemPosition)?.optString("id")
            ?.takeIf { it.isNotBlank() && it != "null" }
        if (categoryId == null) {
            Toast.makeText(this, "La categoría seleccionada no tiene un identificador válido. Recarga la lista.", Toast.LENGTH_LONG).show()
            return
        }
        val business = JSONObject().put("name", businessName.text.toString().trim())
            .put("categoryId", categoryId).put("description", description.text.toString().trim())
            .put("phone", ownerPhone.text.toString().trim()).put("department", department.text.toString().trim())
            .put("province", province.text.toString().trim()).put("district", district.text.toString().trim())
            .put("address", address.text.toString().trim()).put("modes", modes)
        val ownerFullName = ownerName.text.toString().trim()
        val payload = JSONObject().put("fullName", ownerFullName)
            .put("email", ownerEmail.text.toString().trim()).put("ownerPhone", ownerPhone.text.toString().trim())
            .put("password", ownerPassword.text.toString()).put("business", business)

        registrationViewModel.registrarNegocio(existingSession, payload, business, ownerFullName)
    }

    override fun onDestroy() { super.onDestroy() }
}

/** Exploración remota con identificadores UUID y solicitudes de reserva en servidor. */
