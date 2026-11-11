package com.example.comunamadsistemacitas.view

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.comunamadsistemacitas.data.remote.MarketplaceSessionStore
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

private fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

private fun crearTexto(context: Context, value: String, size: Float = 14f, color: Int = UnamadPalette.ink, bold: Boolean = false): TextView {
    return TextView(context).apply {
        text = value
        textSize = size
        setTextColor(color)
        includeFontPadding = false
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setLineSpacing(context.dp(2).toFloat(), 1f)
        setPadding(0, context.dp(4), 0, context.dp(4))
    }
}

private fun crearCampo(context: Context, hintText: String, inputType: Int = android.text.InputType.TYPE_CLASS_TEXT): EditText {
    return EditText(context).apply {
        hint = hintText
        textSize = 15f
        this.inputType = inputType
        isSingleLine = true
        imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        minHeight = context.dp(50)
        setPadding(context.dp(13), context.dp(8), context.dp(13), context.dp(8))
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(UnamadPalette.surface)
            cornerRadius = context.dp(12).toFloat()
            setStroke(context.dp(1), UnamadPalette.border)
        }
        setTextColor(UnamadPalette.ink)
        setHintTextColor(UnamadPalette.muted)
    }
}

private fun crearBoton(context: Context, texto: String, onClick: () -> Unit): MaterialButton {
    return MaterialButton(context).apply {
        text = texto
        isAllCaps = false
        minHeight = context.dp(48)
        insetTop = 0
        insetBottom = 0
        cornerRadius = context.dp(13)
        val states = arrayOf(intArrayOf(android.R.attr.state_enabled), intArrayOf())
        setTextColor(android.content.res.ColorStateList(states, intArrayOf(Color.WHITE, Color.rgb(91, 104, 116))))
        backgroundTintList = android.content.res.ColorStateList(states, intArrayOf(UnamadPalette.navy, Color.rgb(197, 207, 216)))
        rippleColor = android.content.res.ColorStateList.valueOf(UnamadPalette.mint)
        setOnClickListener { onClick() }
    }
}

/** Portal principal del marketplace con identidad visual UNAMAD. */
class PortalMarketplaceActivity : UnamadBaseActivity() {
    private val navy = UnamadPalette.navy
    private val green = UnamadPalette.teal
    private val ink = UnamadPalette.ink
    private val muted = UnamadPalette.muted

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(UnamadPalette.background)
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        // Encabezado institucional.
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(20))
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(5, 28, 48), navy, UnamadPalette.tealDark)
            ).apply { cornerRadii = floatArrayOf(0f, 0f, this@PortalMarketplaceActivity.dp(20).toFloat(), this@PortalMarketplaceActivity.dp(20).toFloat(), this@PortalMarketplaceActivity.dp(20).toFloat(), this@PortalMarketplaceActivity.dp(20).toFloat(), 0f, 0f) }
        }
        val logo = android.widget.ImageView(this).apply {
            setImageResource(com.example.comunamadsistemacitas.R.drawable.logo_citas)
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(58), dp(58)).apply { marginEnd = dp(12) })
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(crearTexto(this@PortalMarketplaceActivity, getString(com.example.comunamadsistemacitas.R.string.app_name), 21f, Color.WHITE, true))
        brand.addView(crearTexto(this@PortalMarketplaceActivity, "Encuentra servicios y reserva tu horario", 14f, UnamadPalette.mint, true))
        brand.addView(crearTexto(this@PortalMarketplaceActivity, getString(com.example.comunamadsistemacitas.R.string.portal_brand_subtitle), 12f, Color.rgb(230, 240, 244)))
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        body.addView(header)

        val hero = MaterialCardView(this).apply {
            radius = resources.getDimension(com.example.comunamadsistemacitas.R.dimen.shape_card)
            cardElevation = resources.getDimension(com.example.comunamadsistemacitas.R.dimen.elevation_card)
            setCardBackgroundColor(navy)
        }
        val heroContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(18))
        }
        val heroImage = android.widget.ImageView(this).apply {
            setImageResource(com.example.comunamadsistemacitas.R.drawable.hero_reservas)
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            contentDescription = "Ilustración de un cliente que elige un horario y confirma una cita con un negocio."
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(14).toFloat()
            }
            clipToOutline = true
        }
        heroContent.addView(heroImage, LinearLayout.LayoutParams(-1, dp(150)).apply { bottomMargin = dp(14) })
        heroContent.addView(crearTexto(this@PortalMarketplaceActivity, getString(com.example.comunamadsistemacitas.R.string.portal_hero_title), 24f, Color.WHITE, true))
        heroContent.addView(crearTexto(this@PortalMarketplaceActivity, getString(com.example.comunamadsistemacitas.R.string.portal_hero_description), 14f, Color.rgb(230, 240, 244)).apply {
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(0, dp(6), 0, dp(14))
        })
        val btnReservar = MaterialButton(this).apply {
            text = "${getString(com.example.comunamadsistemacitas.R.string.portal_explore_action)}  →"
            isAllCaps = false
            minHeight = dp(52)
            cornerRadius = dp(14)
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(UnamadPalette.tealDark)
            setOnClickListener { abrirFlujoReserva() }
        }
        heroContent.addView(btnReservar, LinearLayout.LayoutParams(-1, dp(52)))
        hero.addView(heroContent)
        body.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(14), dp(14), dp(14), dp(16)) })

        // La portada ya no ofrece una búsqueda separada: guía al usuario directamente a reservar.
        val shortcuts = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        shortcuts.addView(atajo(com.example.comunamadsistemacitas.R.drawable.ic_nav_calendar_check, "Mis reservas", "Consulta tus citas", UnamadPalette.mint) {
            abrirMisReservas()
        }, LinearLayout.LayoutParams(0, dp(108), 1f).apply { marginEnd = dp(6) })
        shortcuts.addView(atajo(com.example.comunamadsistemacitas.R.drawable.ic_nav_store, "Soy negocio", "Administra tu negocio", UnamadPalette.background) {
            abrirMiNegocio()
        }, LinearLayout.LayoutParams(0, dp(108), 1f).apply { marginStart = dp(6) })
        body.addView(shortcuts, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(14), 0, dp(14), dp(18)) })

        body.addView(crearTexto(this@PortalMarketplaceActivity, "Categorías populares", 18f, ink, true).apply { setPadding(dp(17), 0, dp(17), dp(10)) })
        val horizontal = android.widget.HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val categoryRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(
            "Belleza y estética" to com.example.comunamadsistemacitas.R.drawable.ic_category_beauty,
            "Servicios técnicos" to com.example.comunamadsistemacitas.R.drawable.ic_category_tools,
            "Salud y bienestar" to com.example.comunamadsistemacitas.R.drawable.ic_category_health,
            "Compras y retail" to com.example.comunamadsistemacitas.R.drawable.ic_category_retail
        ).forEach { (name, iconRes) ->
            val c = MaterialCardView(this).apply { radius = dp(14).toFloat(); cardElevation = dp(1).toFloat(); setCardBackgroundColor(Color.WHITE); strokeColor = UnamadPalette.border; strokeWidth = dp(1); isClickable = true; isFocusable = true }
            val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(14), dp(12), dp(14), dp(12)) }
            inner.addView(android.widget.ImageView(this).apply {
                setImageResource(iconRes)
                setColorFilter(green)
                contentDescription = null
            }, LinearLayout.LayoutParams(dp(28), dp(28)).apply { bottomMargin = dp(5) })
            inner.addView(crearTexto(this@PortalMarketplaceActivity, name, 12f, ink, true))
            c.addView(inner)
            c.contentDescription = "Categoría $name. Ver negocios disponibles."
            c.setOnClickListener { abrirCatalogo(name) }
            categoryRow.addView(c, LinearLayout.LayoutParams(dp(132), dp(86)).apply { setMargins(dp(5), 0, dp(5), 0) })
        }
        horizontal.addView(categoryRow)
        body.addView(horizontal, LinearLayout.LayoutParams(-1, dp(94)).apply { bottomMargin = dp(16) })

        val offersTitle = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        offersTitle.addView(crearTexto(this@PortalMarketplaceActivity, "◆  Ofertas y promociones", 18f, ink, true), LinearLayout.LayoutParams(0, -2, 1f))
        offersTitle.addView(crearTexto(this@PortalMarketplaceActivity, "Ver servicios  →", 13f, UnamadPalette.tealDark, true).apply {
            setOnClickListener { abrirCatalogo() }
        })
        body.addView(offersTitle, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(17), 0, dp(17), dp(8)) })
        val promo = MaterialCardView(this).apply { radius = dp(16).toFloat(); cardElevation = dp(2).toFloat(); setCardBackgroundColor(Color.WHITE); strokeColor = UnamadPalette.border; strokeWidth = dp(1) }
        val promoBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(15), dp(12), dp(15), dp(12)) }
        promoBox.addView(crearTexto(this@PortalMarketplaceActivity, "SERVICIOS Y PROMOCIONES", 11f, UnamadPalette.tealDark, true))
        promoBox.addView(crearTexto(this@PortalMarketplaceActivity, "Encuentra un servicio, revisa sus detalles y continúa con tu reserva.", 15f, ink, true))
        promoBox.addView(crearTexto(this@PortalMarketplaceActivity, "Los horarios se consultan en tiempo real antes de confirmar tu cita.", 12f, muted))
        promoBox.addView(crearBoton(this@PortalMarketplaceActivity, "Ver servicios disponibles") { abrirFlujoReserva() })
        promo.addView(promoBox)
        body.addView(promo, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(16), 0, dp(16), dp(18)) })

        agregarNavegacionPrincipal(root, "HOME")
        setContentView(root)
    }

    private fun abrirCatalogo(query: String? = null) {
        val destination = Intent(this, MarketplaceRemotoActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("BOOKING_FLOW", true)
        if (!query.isNullOrBlank()) destination.putExtra("SEARCH_QUERY", query)
        startActivity(destination)
    }

    private fun abrirMisReservas() {
        val hasSession = MarketplaceSessionStore(this).hayTokenGuardado()
        if (!hasSession) {
            startActivity(Intent(this, AccesoRemotoActivity::class.java).putExtra("AFTER_LOGIN", "RESERVATIONS"))
        } else {
            startActivity(Intent(this, MarketplaceRemotoActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("SHOW_RESERVATIONS", true))
        }
    }

    private fun abrirMiNegocio() {
        val id = getSharedPreferences("marketplace_local_session", MODE_PRIVATE)
            .getString("active_business_uuid", null)
        if (id.isNullOrBlank()) {
            startActivity(Intent(this, AccesoRemotoActivity::class.java).putExtra("AFTER_LOGIN", "BUSINESS"))
        } else {
            startActivity(Intent(this, PanelNegocioRemotoActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
    }

    private fun abrirFlujoReserva() = abrirCatalogo()

    private fun atajo(iconoRes: Int, titulo: String, subtitulo: String, fondo: Int, accion: () -> Unit): View {
        val card = MaterialCardView(this).apply {
            radius = dp(15).toFloat(); cardElevation = dp(1).toFloat(); setCardBackgroundColor(fondo)
            strokeColor = UnamadPalette.border; strokeWidth = dp(1); isClickable = true; isFocusable = true
        }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(5), dp(8), dp(5), dp(8)) }
        content.addView(android.widget.ImageView(this@PortalMarketplaceActivity).apply {
            setImageResource(iconoRes)
            setColorFilter(navy)
            contentDescription = null
        }, LinearLayout.LayoutParams(dp(27), dp(27)).apply { bottomMargin = dp(3) })
        content.addView(crearTexto(this@PortalMarketplaceActivity, titulo, 13f, ink, true).apply { gravity = Gravity.CENTER })
        content.addView(crearTexto(this@PortalMarketplaceActivity, subtitulo, 11f, muted).apply { gravity = Gravity.CENTER })
        card.addView(content)
        card.contentDescription = "$titulo. $subtitulo"
        card.setOnClickListener { accion() }
        return card
    }
}

/** Puente de compatibilidad: el registro de negocios vigente usa la API central. */
class RegistroNegocioActivity : UnamadBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, RegistroNegocioRemotoActivity::class.java))
        finish()
    }
}

/** Puente de compatibilidad: el acceso y registro de clientes se realizan contra la API. */
class RegistroClienteActivity : UnamadBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, AccesoRemotoActivity::class.java))
        finish()
    }
}

/** Puente de compatibilidad: el catálogo vigente consulta PostgreSQL mediante la API. */
class MarketplaceActivity : UnamadBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, MarketplaceRemotoActivity::class.java))
        finish()
    }
}

/** Puente de compatibilidad: la administración vigente usa el panel conectado al servidor. */
class PanelNegocioActivity : UnamadBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, PanelNegocioRemotoActivity::class.java))
        finish()
    }
}
