package com.example.comunamadsistemacitas.view

import android.graphics.Color
import android.os.Bundle
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.example.comunamadsistemacitas.data.remote.MarketplaceSessionStore
import com.google.android.material.button.MaterialButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

/** Tokens de color compartidos por todas las pantallas activas de UNAMAD Citas. */
internal object UnamadPalette {
    // Paleta comercial institucional: azul confianza, verde acción y ámbar pendiente.
    val navy = Color.rgb(15, 76, 129)             // #0F4C81
    val navyDeep = Color.rgb(8, 45, 77)
    val teal = Color.rgb(0, 168, 107)             // #00A86B
    val tealDark = Color.rgb(0, 122, 78)
    val mint = Color.rgb(221, 246, 233)
    val background = Color.rgb(248, 249, 250)     // #F8F9FA
    val surface = Color.WHITE
    val ink = Color.rgb(23, 36, 52)
    val muted = Color.rgb(82, 98, 115)
    val border = Color.rgb(224, 231, 237)
    val danger = Color.rgb(127, 29, 29)
    val warning = Color.rgb(120, 59, 18)          // Pendiente: #E59866
    val warningAccent = Color.rgb(229, 152, 102)
    val warningBackground = Color.rgb(255, 240, 228)
    val success = Color.rgb(0, 91, 57)
    val successBackground = Color.rgb(221, 246, 233)
}

/**
 * Base visual para las pantallas del flujo vigente.
 * Mantiene barras del sistema legibles y coherentes con la identidad visual,
 * incluso cuando el dispositivo cambia entre modo claro y oscuro.
 */
abstract class UnamadBaseActivity : AppCompatActivity() {
    private var navigationBar: LinearLayout? = null
    private var currentNavigationDestination: String = ""
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        aplicarBarrasDelSistema()
    }

    override fun onResume() {
        super.onResume()
        aplicarBarrasDelSistema()
    }

    /** Barra inferior única para los cuatro destinos principales de la app. */
    protected fun agregarNavegacionPrincipal(root: LinearLayout, destinoSeleccionado: String) {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(dpNav(5), dpNav(5), dpNav(5), dpNav(5))
            setBackgroundColor(UnamadPalette.surface)
            elevation = dpNav(8).toFloat()
        }
        currentNavigationDestination = destinoSeleccionado
        val destinations = listOf(
            Triple("HOME", "Inicio", com.example.comunamadsistemacitas.R.drawable.ic_nav_home),
            Triple("BOOK", "Reservar", com.example.comunamadsistemacitas.R.drawable.ic_nav_calendar_plus),
            Triple("RESERVATIONS", "Mis citas", com.example.comunamadsistemacitas.R.drawable.ic_nav_calendar_check),
            Triple("BUSINESS", "Negocio", com.example.comunamadsistemacitas.R.drawable.ic_nav_store)
        )
        destinations.forEach { (key, label, iconRes) ->
            val active = key == destinoSeleccionado
            val button = MaterialButton(this).apply {
                text = label
                icon = androidx.appcompat.content.res.AppCompatResources.getDrawable(this@UnamadBaseActivity, iconRes)
                iconTint = android.content.res.ColorStateList.valueOf(if (active) UnamadPalette.navy else UnamadPalette.muted)
                iconSize = dpNav(19)
                iconPadding = dpNav(2)
                iconGravity = MaterialButton.ICON_GRAVITY_TOP
                isAllCaps = false
                textSize = 10.5f
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                gravity = Gravity.CENTER
                minHeight = dpNav(52)
                minWidth = 0
                insetTop = 0
                insetBottom = 0
                cornerRadius = dpNav(12)
                tag = key
                setTextColor(if (active) UnamadPalette.navy else UnamadPalette.muted)
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    if (active) android.graphics.Color.rgb(224, 239, 250) else UnamadPalette.surface
                )
                contentDescription = label
                setOnClickListener { navegarDestinoPrincipal(key) }
            }
            bar.addView(button, LinearLayout.LayoutParams(0, dpNav(54), 1f).apply {
                marginStart = dpNav(2); marginEnd = dpNav(2)
            })
        }
        navigationBar = bar
        root.addView(bar, LinearLayout.LayoutParams(-1, dpNav(64)))
    }

    protected fun seleccionarDestinoNavegacion(destinoSeleccionado: String) {
        currentNavigationDestination = destinoSeleccionado
        val bar = navigationBar ?: return
        for (index in 0 until bar.childCount) {
            val button = bar.getChildAt(index) as? MaterialButton ?: continue
            val active = button.tag == destinoSeleccionado
            button.setTextColor(if (active) UnamadPalette.navy else UnamadPalette.muted)
            button.backgroundTintList = android.content.res.ColorStateList.valueOf(
                if (active) android.graphics.Color.rgb(224, 239, 250) else UnamadPalette.surface
            )
        }
    }

    private fun navegarDestinoPrincipal(destino: String) {
        if (destino == currentNavigationDestination) return
        val intent = when (destino) {
            "HOME" -> Intent(this, PortalMarketplaceActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            "BOOK" -> Intent(this, MarketplaceRemotoActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("BOOKING_FLOW", true)
            "RESERVATIONS" -> {
                val hasSession = MarketplaceSessionStore(this).hayTokenGuardado()
                if (!hasSession) Intent(this, AccesoRemotoActivity::class.java).putExtra("AFTER_LOGIN", "RESERVATIONS")
                else Intent(this, MarketplaceRemotoActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra("SHOW_RESERVATIONS", true)
            }
            "BUSINESS" -> {
                val businessId = getSharedPreferences("marketplace_local_session", MODE_PRIVATE)
                    .getString("active_business_uuid", null)
                if (businessId.isNullOrBlank()) Intent(this, AccesoRemotoActivity::class.java)
                    .putExtra("AFTER_LOGIN", "BUSINESS")
                else Intent(this, PanelNegocioRemotoActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            else -> return
        }
        startActivity(intent)
    }

    private fun dpNav(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun aplicarBarrasDelSistema() {
        window.statusBarColor = UnamadPalette.background
        window.navigationBarColor = UnamadPalette.surface
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }
}

internal fun estadoReservaAmigable(status: String): String = when (status.uppercase()) {
    "SOLICITADA", "PENDIENTE" -> "Pendiente de confirmación"
    "CONFIRMADA" -> "Confirmada"
    "CANCELADA" -> "Cancelada"
    "COMPLETADA" -> "Completada"
    "NO_ASISTIO" -> "No asistió"
    "LISTA" -> "Lista para recoger"
    "RECHAZADA" -> "Rechazada"
    else -> status.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}

internal fun modalidadAmigable(mode: String): String = when (mode.uppercase()) {
    "CITA" -> "Cita"
    "RECOJO" -> "Recojo"
    "AMBOS" -> "Cita y recojo"
    else -> mode.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}

internal fun diaSemanaAmigable(day: Int): String = when (day) {
    1 -> "Lunes"
    2 -> "Martes"
    3 -> "Miércoles"
    4 -> "Jueves"
    5 -> "Viernes"
    6 -> "Sábado"
    7 -> "Domingo"
    else -> "Día $day"
}
