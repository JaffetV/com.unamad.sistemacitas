package com.example.comunamadsistemacitas.domain.validation

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Parsing estricto de fechas de API. Los timestamps sin offset legado se interpretan en zona local. */
object IsoDateTimeParser {
    /** El backend UNAMAD guarda la zona horaria de los negocios como America/Lima. */
    private val businessTimeZone: TimeZone = TimeZone.getTimeZone("America/Lima")
    private val dateTimePatterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSX", "yyyy-MM-dd'T'HH:mm:ssX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS", "yyyy-MM-dd'T'HH:mm:ss"
    )

    fun parseDateTime(value: String): Date? {
        if (value.isBlank()) return null
        for (pattern in dateTimePatterns) {
            val parser = SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = false
                timeZone = businessTimeZone
            }
            val position = ParsePosition(0)
            val parsed = parser.parse(value, position)
            if (parsed != null && position.index == value.length && position.errorIndex < 0) return parsed
        }
        return null
    }

    /** Valida un día civil YYYY-MM-DD sin aceptar fechas truncadas ni normalizaciones. */
    fun parseLocalDate(value: String): Date? {
        if (!value.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) return null
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            isLenient = false
            timeZone = businessTimeZone
        }
        val position = ParsePosition(0)
        val parsed = parser.parse(value, position)
        return parsed?.takeIf { position.index == value.length && position.errorIndex < 0 }
    }
}
