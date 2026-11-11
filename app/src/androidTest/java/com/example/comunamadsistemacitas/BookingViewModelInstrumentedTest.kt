package com.example.comunamadsistemacitas

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.comunamadsistemacitas.data.repository.BookingRepositoryContract
import com.example.comunamadsistemacitas.presentation.booking.BookingEvent
import com.example.comunamadsistemacitas.presentation.booking.BookingViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class BookingViewModelInstrumentedTest {
    private class FakeBookingRepository : BookingRepositoryContract {
        var online = false
        val createCalls = AtomicInteger(0)

        override suspend fun availability(businessId: String, listingId: String, date: String): JSONArray {
            if (!online) throw IOException("Sin conexión de prueba")
            return JSONArray().put(
                JSONObject()
                    .put("starts_at", "2030-01-10T10:00:00-05:00")
                    .put("assignee_type", "STAFF")
                    .put("assignee_id", "staff-test")
                    .put("assignee_name", "Profesional de prueba")
            )
        }

        override suspend fun createReservation(payload: JSONObject): JSONObject {
            createCalls.incrementAndGet()
            return JSONObject().put("id", "reservation-test").put("code", "RSV-TEST")
                .put("status", "SOLICITADA")
        }

        override fun close() = Unit
    }

    @Test
    fun disponibilidadSeRecuperaCuandoVuelveLaConexion() = runBlocking {
        val repository = FakeBookingRepository()
        val viewModel = BookingViewModel(ApplicationProvider.getApplicationContext<Application>(), repository)
        viewModel.reset("business-test", "listing-test")

        viewModel.consultarDisponibilidad("business-test", "listing-test", "2030-01-10")
        val failed = withTimeout(5_000) {
            viewModel.uiState.first { !it.isLoadingAvailability && it.errorMessage != null }
        }
        assertNotNull(failed.errorMessage)

        repository.online = true
        viewModel.consultarDisponibilidad("business-test", "listing-test", "2030-01-10")
        val recovered = withTimeout(5_000) {
            viewModel.uiState.first { !it.isLoadingAvailability && it.slots.isNotEmpty() }
        }
        assertEquals(1, recovered.slots.size)
        assertEquals("business-test", recovered.businessId)
    }

    @Test
    fun flujoCompletoConsultaSeleccionaYConfirmaUnaReserva() = runBlocking {
        val repository = FakeBookingRepository().apply { online = true }
        val viewModel = BookingViewModel(ApplicationProvider.getApplicationContext<Application>(), repository)
        viewModel.reset("business-test", "listing-test")

        viewModel.consultarDisponibilidad("business-test", "listing-test", "2030-01-10")
        val availability = withTimeout(5_000) {
            viewModel.uiState.first { !it.isLoadingAvailability && it.slots.isNotEmpty() }
        }
        val chosenSlot = availability.slots.single()
        viewModel.seleccionarTurno(chosenSlot)
        assertEquals("staff-test", viewModel.uiState.value.selectedSlot?.getString("assignee_id"))

        val event = async { withTimeout(5_000) { viewModel.events.first() } }
        val payload = JSONObject()
            .put("businessId", "business-test")
            .put("listingId", "listing-test")
            .put("mode", "CITA")
            .put("dateTime", chosenSlot.getString("starts_at"))
            .put("staffId", chosenSlot.getString("assignee_id"))
        viewModel.crearReserva(payload)

        val created = event.await() as BookingEvent.Created
        assertEquals("RSV-TEST", created.reservation.getString("code"))
        assertEquals(1, repository.createCalls.get())
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun dobleConfirmacionSoloEjecutaUnaCreacionEnElViewModel() = runBlocking {
        val repository = FakeBookingRepository().apply { online = true }
        val viewModel = BookingViewModel(ApplicationProvider.getApplicationContext<Application>(), repository)
        val event = async { withTimeout(5_000) { viewModel.events.first() } }
        val payload = JSONObject().put("businessId", "business-test").put("listingId", "listing-test")
        viewModel.crearReserva(payload)
        viewModel.crearReserva(payload)

        val result = event.await() as BookingEvent.Created
        assertEquals("RSV-TEST", result.reservation.getString("code"))
        assertEquals(1, repository.createCalls.get())
        assertFalse(viewModel.uiState.value.isSubmitting)
    }
}
