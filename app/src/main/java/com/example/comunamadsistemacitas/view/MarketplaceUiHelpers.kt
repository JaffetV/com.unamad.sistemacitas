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

internal const val REMOTE_PREFS = "marketplace_local_session"
internal const val ACTIVE_BUSINESS_UUID = "active_business_uuid"
internal const val ACTIVE_USER_ID = "remote_user_id"
internal const val ACTIVE_USER_NAME = "remote_user_name"
internal val UNAMAD_TIME_ZONE: TimeZone = TimeZone.getTimeZone("America/Lima")
internal fun formatoLima(pattern: String, locale: Locale = Locale.getDefault()): SimpleDateFormat =
    SimpleDateFormat(pattern, locale).apply { timeZone = UNAMAD_TIME_ZONE }

internal fun Context.rdp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
internal fun Context.rText(value: String, size: Float = 14f, color: Int = UnamadPalette.ink, bold: Boolean = false): TextView =
    TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        includeFontPadding = false
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setLineSpacing(rdp(2).toFloat(), 1f)
        setPadding(0, rdp(4), 0, rdp(4))
    }
internal fun Context.rField(hintText: String, type: Int = InputType.TYPE_CLASS_TEXT): EditText = EditText(this).apply {
    hint = hintText
    textSize = 15f
    inputType = type
    isSingleLine = true
    minHeight = rdp(50)
    imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_NEXT
    setPadding(rdp(13), rdp(8), rdp(13), rdp(8))
    background = GradientDrawable().apply {
        setColor(UnamadPalette.surface)
        cornerRadius = resources.getDimension(com.example.comunamadsistemacitas.R.dimen.shape_input)
        setStroke(rdp(1), UnamadPalette.border)
    }
    setTextColor(UnamadPalette.ink)
    setHintTextColor(UnamadPalette.muted)
}
internal fun EditText.rLimit(maxCharacters: Int): EditText = apply {
    filters = filters + android.text.InputFilter.LengthFilter(maxCharacters)
}

internal fun Context.rMultilineField(hintText: String, minLines: Int = 3): EditText = EditText(this).apply {
    hint = hintText
    textSize = 15f
    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
    isSingleLine = false
    setSingleLine(false)
    setMinLines(minLines)
    gravity = Gravity.TOP or Gravity.START
    setPadding(rdp(13), rdp(10), rdp(13), rdp(10))
    background = GradientDrawable().apply {
        setColor(UnamadPalette.surface)
        cornerRadius = resources.getDimension(com.example.comunamadsistemacitas.R.dimen.shape_input)
        setStroke(rdp(1), UnamadPalette.border)
    }
    setTextColor(UnamadPalette.ink)
    setHintTextColor(UnamadPalette.muted)
}
internal fun Context.rButton(label: String, action: () -> Unit): MaterialButton = MaterialButton(this).apply {
    text = label
    isAllCaps = false
    minHeight = resources.getDimensionPixelSize(com.example.comunamadsistemacitas.R.dimen.control_min_height)
    insetTop = 0
    insetBottom = 0
    cornerRadius = resources.getDimensionPixelSize(com.example.comunamadsistemacitas.R.dimen.shape_button)
    setTextColor(Color.WHITE)
    val states = arrayOf(intArrayOf(android.R.attr.state_enabled), intArrayOf())
    backgroundTintList = android.content.res.ColorStateList(states, intArrayOf(UnamadPalette.navy, Color.rgb(197, 207, 216)))
    setTextColor(android.content.res.ColorStateList(states, intArrayOf(Color.WHITE, Color.rgb(91, 104, 116))))
    rippleColor = android.content.res.ColorStateList.valueOf(UnamadPalette.mint)
    elevation = rdp(1).toFloat()
    setOnClickListener { action() }
}
internal fun Context.rColumn(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(rdp(20), rdp(18), rdp(20), rdp(24))
    setBackgroundColor(UnamadPalette.background)
}
internal fun Context.rCard(): MaterialCardView = MaterialCardView(this).apply {
    radius = resources.getDimension(com.example.comunamadsistemacitas.R.dimen.shape_card)
    cardElevation = resources.getDimension(com.example.comunamadsistemacitas.R.dimen.elevation_card)
    maxCardElevation = rdp(3).toFloat()
    useCompatPadding = true
    setCardBackgroundColor(UnamadPalette.surface)
    strokeColor = UnamadPalette.border
    strokeWidth = rdp(1)
}
internal fun EditText.rSetValidationError(message: String?) {
    val inputLayout = parent as? TextInputLayout
    if (inputLayout != null) {
        inputLayout.error = message
        inputLayout.isErrorEnabled = !message.isNullOrBlank()
    } else {
        error = message
    }
}

internal fun LinearLayout.rAddField(field: EditText, context: Context) {
    val multiline = (field.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
    val hint = field.hint?.toString().orEmpty()
    val wrapper = TextInputLayout(context).apply {
        hint.takeIf { it.isNotBlank() }?.let { this.hint = it }
        boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        setBoxCornerRadii(context.resources.getDimension(com.example.comunamadsistemacitas.R.dimen.shape_input), context.resources.getDimension(com.example.comunamadsistemacitas.R.dimen.shape_input), context.resources.getDimension(com.example.comunamadsistemacitas.R.dimen.shape_input), context.resources.getDimension(com.example.comunamadsistemacitas.R.dimen.shape_input))
        boxStrokeColor = UnamadPalette.navy
        boxStrokeWidth = context.rdp(1)
        boxStrokeWidthFocused = context.rdp(2)
        setBoxBackgroundColor(UnamadPalette.surface)
        if ((field.inputType and InputType.TYPE_TEXT_VARIATION_PASSWORD) == InputType.TYPE_TEXT_VARIATION_PASSWORD) {
            endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        }
    }
    field.hint = null
    field.background = null
    field.setPadding(context.rdp(12), context.rdp(12), context.rdp(12), context.rdp(10))
    wrapper.addView(field, LinearLayout.LayoutParams(-1, if (multiline) context.rdp(96) else context.rdp(52)))
    addView(wrapper, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = context.rdp(10) })
}

internal fun Context.rStatusChip(status: String): TextView {
    val normalized = status.uppercase(Locale.ROOT)
    val (label, foreground, chipBackgroundColor) = when (normalized) {
        "CONFIRMADA", "COMPLETADA", "LISTA" -> Triple(estadoReservaAmigable(normalized), UnamadPalette.success, UnamadPalette.successBackground)
        "SOLICITADA", "PENDIENTE" -> Triple(estadoReservaAmigable(normalized), UnamadPalette.warning, UnamadPalette.warningBackground)
        "CANCELADA", "RECHAZADA", "NO_ASISTIO", "NO_SHOW" -> Triple(estadoReservaAmigable(normalized), UnamadPalette.danger, Color.rgb(252, 232, 230))
        else -> Triple(estadoReservaAmigable(normalized.ifBlank { "PENDIENTE" }), UnamadPalette.muted, Color.rgb(238, 242, 246))
    }
    return TextView(this).apply {
        text = label
        textSize = 11f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(foreground)
        setPadding(rdp(11), rdp(6), rdp(11), rdp(6))
        background = GradientDrawable().apply {
            setColor(chipBackgroundColor)
            cornerRadius = rdp(999).toFloat()
        }
        contentDescription = "Estado: $label"
    }
}
internal fun mensajeErrorAmigable(error: Throwable): String {
    val causes = generateSequence(error) { it.cause }.take(8).toList()
    return when {
        causes.any { it is ConnectException || it is UnknownHostException } ||
            causes.any { it.message.orEmpty().contains("Failed to connect", ignoreCase = true) } ->
            "No se pudo contactar con el servidor de citas.\n\nEn el emulador, 10.0.2.2 apunta a tu PC. Inicia Docker y la API desde la carpeta backend con `docker compose up --build -d` y comprueba `http://localhost:8080/health`.\n\nSi usas un teléfono físico, configura la IP local de tu PC en la dirección de la API."
        causes.any { it is SocketTimeoutException } ->
            "El servidor está tardando demasiado en responder. Comprueba que PostgreSQL y la API estén activos y vuelve a intentarlo."
        error is com.example.comunamadsistemacitas.data.remote.MarketplaceApiException -> error.message
        else -> error.message?.takeIf { it.isNotBlank() } ?: "No se pudo completar la operación. Inténtalo nuevamente."
    }
}

internal fun slotButtonTint(button: MaterialButton, selected: Boolean) {
    button.backgroundTintList = android.content.res.ColorStateList.valueOf(
        if (selected) UnamadPalette.tealDark else UnamadPalette.navy
    )
}

internal fun showRemoteError(context: Context, error: Throwable) {
    val connectivityProblem = generateSequence(error) { it.cause }.take(8).any {
        it is ConnectException || it is UnknownHostException || it is SocketTimeoutException ||
            it.message.orEmpty().contains("Failed to connect", ignoreCase = true)
    }
    AlertDialog.Builder(context)
        .setTitle(if (connectivityProblem) "Servidor no disponible" else "No se pudo completar")
        .setMessage(mensajeErrorAmigable(error))
        .setPositiveButton("Entendido", null)
        .show()
}

internal fun parseIsoDateStrict(value: String): java.util.Date? = IsoDateTimeParser.parseDateTime(value)

internal fun fechaHoraAmigable(value: String): String {
    val parsed = parseIsoDateStrict(value) ?: return value.takeIf { it.isNotBlank() } ?: "Fecha por confirmar"
    return formatoLima("EEE, dd MMM yyyy · HH:mm", Locale("es", "PE")).format(parsed)
}

internal fun horaLocalDesdeIso(value: String): String {
    val parsed = parseIsoDateStrict(value) ?: return "Hora no disponible"
    return formatoLima("HH:mm", Locale("es", "PE")).format(parsed)
}

/** Inicio de sesión y alta de cliente contra el backend REST. */
