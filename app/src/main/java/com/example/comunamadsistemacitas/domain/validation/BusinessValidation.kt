package com.example.comunamadsistemacitas.domain.validation

/** Validaciones puras de negocio para reutilizar en ViewModels e interfaces. */
object BusinessValidation {
    private val emailRegex = Regex("""^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$""")

    fun validateBusinessRegistration(
        ownerName: String,
        ownerEmail: String,
        ownerPhone: String,
        ownerPassword: String,
        businessName: String,
        department: String,
        province: String,
        district: String,
        address: String,
        hasModes: Boolean,
        existingSession: Boolean
    ): String? = when {
        !existingSession && ownerName.trim().isBlank() -> "Ingresa el nombre del responsable."
        !existingSession && ownerName.trim().length > 140 -> "El nombre del responsable no puede superar 140 caracteres."
        !existingSession && ownerEmail.trim().length > 254 -> "El correo del responsable no puede superar 254 caracteres."
        !existingSession && !emailRegex.matches(ownerEmail.trim()) -> "Ingresa un correo válido del responsable."
        !existingSession && (ownerPassword.isBlank() || ownerPassword.length < 10) -> "La contraseña debe tener al menos 10 caracteres."
        !existingSession && ownerPassword.length > 128 -> "La contraseña no puede superar 128 caracteres."
        ownerPhone.trim().length > 30 -> "El teléfono no puede superar 30 caracteres."
        ownerPhone.filter(Char::isDigit).length < 7 -> "Ingresa un teléfono válido para el negocio."
        businessName.trim().isBlank() -> "Ingresa el nombre comercial."
        businessName.trim().length > 140 -> "El nombre comercial no puede superar 140 caracteres."
        department.trim().isBlank() || province.trim().isBlank() || district.trim().isBlank() || address.trim().isBlank() ->
            "Completa la ubicación del establecimiento."
        department.trim().length > 100 || province.trim().length > 100 || district.trim().length > 100 ->
            "El departamento, provincia y distrito no pueden superar 100 caracteres."
        address.trim().length > 220 -> "La dirección no puede superar 220 caracteres."
        !hasModes -> "Selecciona al menos una modalidad de atención."
        else -> null
    }
}
