package com.example.comunamadsistemacitas.presentation.booking

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.comunamadsistemacitas.data.repository.BookingRepository
import com.example.comunamadsistemacitas.data.repository.BookingRepositoryContract
import com.example.comunamadsistemacitas.domain.validation.IsoDateTimeParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

/** Coordina disponibilidad, selección y envío idempotente de una reserva central. */
class BookingViewModel(
    application: Application,
    private val repository: BookingRepositoryContract
) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, BookingRepository(application))
    private val requestVersion = AtomicLong(0L)
    private var availabilityJob: Job? = null
    private val _uiState = MutableStateFlow(BookingUiState())
    val uiState = _uiState.asStateFlow()
    // Canal buffered: el resultado no se pierde si Android recrea la Activity.
    private val eventChannel = Channel<BookingEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    fun reset(businessId: String, listingId: String) {
        requestVersion.incrementAndGet()
        availabilityJob?.cancel()
        _uiState.value = BookingUiState(businessId = businessId, listingId = listingId)
    }

    fun consultarDisponibilidad(businessId: String, listingId: String, localDate: String) {
        if (businessId.isBlank() || listingId.isBlank() || IsoDateTimeParser.parseLocalDate(localDate) == null) {
            _uiState.value = _uiState.value.copy(
                businessId = businessId,
                listingId = listingId,
                isLoadingAvailability = false,
                availabilityErrorMessage = "El negocio, servicio o fecha no son válidos. Selecciona nuevamente el día."
            )
            return
        }
        val version = requestVersion.incrementAndGet()
        availabilityJob?.cancel()
        _uiState.value = _uiState.value.copy(
            businessId = businessId,
            listingId = listingId,
            availabilityDate = localDate,
            slots = emptyList(),
            selectedSlot = null,
            availabilityRevision = _uiState.value.availabilityRevision + 1,
            isLoadingAvailability = true,
            availabilityErrorMessage = null,
            errorMessage = null
        )
        availabilityJob = viewModelScope.launch {
            try {
                val result = repository.availability(businessId, listingId, localDate)
                if (requestVersion.get() != version) return@launch
                val slots = buildList {
                    for (index in 0 until result.length()) {
                        val slot = result.optJSONObject(index) ?: continue
                        val startsAt = slot.optString("starts_at")
                        val assigneeId = slot.optString("assignee_id")
                        val assigneeType = slot.optString("assignee_type")
                        if (startsAt.isBlank() || assigneeId.isBlank() || assigneeId == "null") continue
                        if (assigneeType != "STAFF" && assigneeType != "RESOURCE") continue
                        if (IsoDateTimeParser.parseDateTime(startsAt) == null) continue
                        add(slot)
                    }
                }
                _uiState.value = _uiState.value.copy(
                    slots = slots,
                    availabilityRevision = _uiState.value.availabilityRevision + 1,
                    isLoadingAvailability = false,
                    availabilityErrorMessage = null,
                    errorMessage = null
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (requestVersion.get() != version) return@launch
                _uiState.value = _uiState.value.copy(
                    isLoadingAvailability = false,
                    availabilityRevision = _uiState.value.availabilityRevision + 1,
                    availabilityErrorMessage = error.message ?: "No se pudo consultar la disponibilidad."
                )
            }
        }
    }

    /** Fuerza la representación al recrear un diálogo conservado tras rotación. */
    fun refrescarEstadoUi() {
        _uiState.value = _uiState.value.copy(availabilityRevision = _uiState.value.availabilityRevision + 1)
    }

    fun seleccionarTurno(slot: JSONObject) {
        val startsAt = slot.optString("starts_at")
        val assigneeId = slot.optString("assignee_id")
        val assigneeType = slot.optString("assignee_type")
        if (IsoDateTimeParser.parseDateTime(startsAt) == null || assigneeId.isBlank() || assigneeId == "null" ||
            (assigneeType != "STAFF" && assigneeType != "RESOURCE")) {
            _uiState.value = _uiState.value.copy(errorMessage = "El turno recibido está incompleto. Consulta nuevamente la disponibilidad.")
            return
        }
        _uiState.value = _uiState.value.copy(selectedSlot = slot, errorMessage = null, availabilityErrorMessage = null)
    }

    fun crearReserva(payload: JSONObject) {
        val state = _uiState.value
        if (state.isSubmitting) return
        try {
            validarPayloadReserva(payload, state)
        } catch (error: Exception) {
            _uiState.value = state.copy(errorMessage = error.message ?: "Revisa los datos de la reserva.")
            viewModelScope.launch { eventChannel.send(BookingEvent.Failed(error)) }
            return
        }
        _uiState.value = state.copy(isSubmitting = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val result = repository.createReservation(payload)
                _uiState.value = _uiState.value.copy(isSubmitting = false)
                eventChannel.send(BookingEvent.Created(result))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    errorMessage = error.message ?: "No se pudo enviar la reserva."
                )
                eventChannel.send(BookingEvent.Failed(error))
            }
        }
    }

    /** Evita que la Activity envíe una cita distinta del turno visible/seleccionado. */
    private fun validarPayloadReserva(payload: JSONObject, state: BookingUiState) {
        val businessId = payload.optString("businessId")
        val listingId = payload.optString("listingId")
        require(businessId.isNotBlank() && businessId == state.businessId) {
            "El negocio cambió. Vuelve a seleccionar el servicio antes de reservar."
        }
        require(listingId.isNotBlank() && listingId == state.listingId) {
            "El servicio cambió. Vuelve a abrir el formulario de reserva."
        }
        val mode = payload.optString("mode")
        require(mode == "CITA" || mode == "RECOJO") { "La modalidad de reserva no es válida." }
        val quantity = payload.optInt("quantity", 0)
        require(quantity in 1..99) { "La cantidad debe estar entre 1 y 99." }

        val dateTime = payload.optString("dateTime")
        val scheduledAt = IsoDateTimeParser.parseDateTime(dateTime)
        require(scheduledAt != null && scheduledAt.time > System.currentTimeMillis()) {
            "Selecciona una fecha y hora futuras."
        }

        val staffId = payload.optString("staffId").takeIf { it.isNotBlank() && it != "null" }
        val resourceId = payload.optString("resourceId").takeIf { it.isNotBlank() && it != "null" }
        if (mode == "CITA") {
            require(quantity == 1) { "Una cita de atención debe reservar una sola unidad." }
            val selected = state.selectedSlot
                ?: throw IllegalArgumentException("Primero consulta y selecciona un turno disponible.")
            require(dateTime == selected.optString("starts_at")) {
                "El horario seleccionado cambió. Consulta nuevamente la disponibilidad."
            }
            val assigneeId = selected.optString("assignee_id")
            when (selected.optString("assignee_type")) {
                "STAFF" -> require(assigneeId.isNotBlank() && staffId == assigneeId && resourceId == null) {
                    "La asignación del profesional no coincide con el turno seleccionado."
                }
                "RESOURCE" -> require(assigneeId.isNotBlank() && resourceId == assigneeId && staffId == null) {
                    "La asignación del puesto no coincide con el turno seleccionado."
                }
                else -> throw IllegalArgumentException("El turno seleccionado no tiene una asignación válida.")
            }
        } else {
            require(staffId == null && resourceId == null) {
                "La recogida de un producto no puede asignarse a un trabajador o recurso de citas."
            }
        }
    }


    fun cancelarConsulta() {
        requestVersion.incrementAndGet()
        availabilityJob?.cancel()
        _uiState.value = _uiState.value.copy(isLoadingAvailability = false)
    }

    override fun onCleared() {
        availabilityJob?.cancel()
        repository.close()
        super.onCleared()
    }
}
