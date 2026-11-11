package com.example.comunamadsistemacitas

import com.example.comunamadsistemacitas.domain.validation.BusinessValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BusinessValidationTest {
    @Test
    fun registroDeNegocioExigeDatosDeResponsableParaCuentaNueva() {
        val result = BusinessValidation.validateBusinessRegistration(
            ownerName = "",
            ownerEmail = "responsable@unamad.pe",
            ownerPhone = "987654321",
            ownerPassword = "ClaveSegura123",
            businessName = "Barbería UNAMAD",
            department = "Madre de Dios",
            province = "Tambopata",
            district = "Puerto Maldonado",
            address = "Av. Universitaria 123",
            hasModes = true,
            existingSession = false
        )
        assertEquals("Ingresa el nombre del responsable.", result)
    }

    @Test
    fun registroDeNegocioRechazaTelefonoConEspaciosSinDigitosSuficientes() {
        val result = BusinessValidation.validateBusinessRegistration(
            ownerName = "", ownerEmail = "", ownerPhone = "       ", ownerPassword = "",
            businessName = "Barbería UNAMAD", department = "Madre de Dios", province = "Tambopata",
            district = "Puerto Maldonado", address = "Av. Universitaria 123", hasModes = true, existingSession = true
        )
        assertEquals("Ingresa un teléfono válido para el negocio.", result)
    }

    @Test
    fun registroDeNegocioAceptaDatosValidosConSesionExistente() {
        val result = BusinessValidation.validateBusinessRegistration(
            ownerName = "",
            ownerEmail = "",
            ownerPhone = "987654321",
            ownerPassword = "",
            businessName = "Barbería UNAMAD",
            department = "Madre de Dios",
            province = "Tambopata",
            district = "Puerto Maldonado",
            address = "Av. Universitaria 123",
            hasModes = true,
            existingSession = true
        )
        assertNull(result)
    }
}
