package com.example.comunamadsistemacitas

import com.example.comunamadsistemacitas.domain.validation.AuthValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthValidationTest {
    @Test fun loginRejectsMalformedEmail() {
        assertEquals("Escribe un correo válido.", AuthValidation.validateLogin("correo-invalido", "ClaveSegura123"))
    }

    @Test fun loginRequiresMinimumPasswordLength() {
        assertEquals("La contraseña debe tener al menos 10 caracteres.", AuthValidation.validateLogin("cliente@unamad.edu.pe", "12345"))
    }

    @Test fun loginRejectsBlankPasswordAndTooLongPassword() {
        assertEquals("La contraseña debe tener al menos 10 caracteres.", AuthValidation.validateLogin("cliente@unamad.edu.pe", "          "))
        assertEquals("La contraseña no puede superar 128 caracteres.", AuthValidation.validateLogin("cliente@unamad.edu.pe", "x".repeat(129)))
    }

    @Test fun loginAcceptsValidInput() {
        assertNull(AuthValidation.validateLogin("cliente@unamad.edu.pe", "ClaveSegura123"))
    }

    @Test fun customerRegistrationValidatesAllRequiredFields() {
        assertEquals("Ingresa tu nombre y apellidos.", AuthValidation.validateCustomer(" ", "cliente@unamad.edu.pe", "987654321", "ClaveSegura123"))
        assertEquals("Ingresa un teléfono válido.", AuthValidation.validateCustomer("Ana", "cliente@unamad.edu.pe", "123", "ClaveSegura123"))
        assertNull(AuthValidation.validateCustomer("Ana", "cliente@unamad.edu.pe", "987654321", "ClaveSegura123"))
    }
}
