package com.example.comunamadsistemacitas.domain.validation

/** Validaciones puras para reutilizar en Android y en futuras interfaces. */
object AuthValidation {
    private val emailRegex = Regex("""^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$""")
    private fun passwordError(password: String): String? = when {
        password.isBlank() || password.length < 10 -> "La contraseña debe tener al menos 10 caracteres."
        password.length > 128 -> "La contraseña no puede superar 128 caracteres."
        else -> null
    }

    fun validateLogin(email: String, password: String): String? = when {
        email.trim().length > 254 -> "El correo no puede superar 254 caracteres."
        !emailRegex.matches(email.trim()) -> "Escribe un correo válido."
        else -> passwordError(password)
    }

    fun validateCustomer(name: String, email: String, phone: String, password: String): String? = when {
        name.trim().isBlank() -> "Ingresa tu nombre y apellidos."
        name.trim().length > 140 -> "El nombre no puede superar 140 caracteres."
        email.trim().length > 254 -> "El correo no puede superar 254 caracteres."
        !emailRegex.matches(email.trim()) -> "Escribe un correo válido."
        phone.trim().length > 30 -> "El teléfono no puede superar 30 caracteres."
        phone.filter(Char::isDigit).length < 7 -> "Ingresa un teléfono válido."
        else -> passwordError(password)
    }
}
