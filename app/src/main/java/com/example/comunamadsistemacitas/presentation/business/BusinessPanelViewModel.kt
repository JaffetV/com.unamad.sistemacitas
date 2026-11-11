package com.example.comunamadsistemacitas.presentation.business

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.comunamadsistemacitas.data.repository.BusinessRepository
import com.example.comunamadsistemacitas.data.repository.BusinessRepositoryContract
import com.example.comunamadsistemacitas.presentation.suspendResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

/** ViewModel para la gestión del panel administrativo del negocio. */
class BusinessPanelViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: BusinessRepositoryContract = BusinessRepository(application)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(BusinessPanelUiState())
    val uiState: StateFlow<BusinessPanelUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<BusinessPanelEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private var dashboardRequestId = AtomicInteger(0)
    private var dashboardRequestedBusinessId = ""
    private val reservationChangesInFlight = mutableSetOf<String>()

    fun haySesion(): Boolean = repository.haySesion()

    fun cerrarSesion() {
        repository.cerrarSesion()
    }

    fun seleccionarNegocio() {
        if (_uiState.value.isSelectingBusiness) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSelectingBusiness = true, errorMessage = null) }
            suspendResult { repository.obtenerMisNegocios() }
                .onSuccess { list ->
                    _uiState.update { it.copy(isSelectingBusiness = false, businesses = list) }
                    eventChannel.send(BusinessPanelEvent.BusinessListLoaded(list))
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isSelectingBusiness = false, errorMessage = error.message) }
                    eventChannel.send(BusinessPanelEvent.OperationFailed(error))
                }
        }
    }

    fun cargarPanel(businessId: String) {
        if (businessId.isBlank()) return
        val requestedBusinessId = businessId
        if (_uiState.value.isLoadingPanel && dashboardRequestedBusinessId == requestedBusinessId) return
        dashboardRequestedBusinessId = requestedBusinessId
        val requestId = dashboardRequestId.incrementAndGet()

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingPanel = true, businessId = businessId, errorMessage = null) }
            suspendResult {
                val business = repository.obtenerNegocio(requestedBusinessId)
                val listings = repository.obtenerPublicaciones(requestedBusinessId)
                val reservations = repository.obtenerReservasNegocio(requestedBusinessId)
                val hours = repository.obtenerHorarios(requestedBusinessId)
                val staff = repository.obtenerTrabajadores(requestedBusinessId)
                val resources = repository.obtenerRecursos(requestedBusinessId)
                JSONObject().put("business", business).put("listings", listings).put("reservations", reservations)
                    .put("hours", hours).put("staff", staff).put("resources", resources)
            }.onSuccess { data ->
                if (requestId != dashboardRequestId.get() || requestedBusinessId != _uiState.value.businessId) return@launch
                _uiState.update { it.copy(isLoadingPanel = false, dashboardData = data, errorMessage = null) }
            }.onFailure { error ->
                if (requestId != dashboardRequestId.get()) return@launch
                _uiState.update { it.copy(isLoadingPanel = false, errorMessage = error.message) }
                eventChannel.send(BusinessPanelEvent.OperationFailed(error))
            }
        }
    }

    fun agregarTrabajador(businessId: String, nombre: String, especialidad: String) {
        val safeName = nombre.trim()
        val safeSpecialty = especialidad.trim()
        if (businessId.isBlank() || safeName.isBlank()) {
            viewModelScope.launch { eventChannel.send(BusinessPanelEvent.OperationFailed(IllegalArgumentException("Ingresa un nombre válido para el trabajador."))) }
            return
        }
        viewModelScope.launch {
            suspendResult { repository.crearTrabajador(businessId, safeName, safeSpecialty) }
                .onSuccess {
                    eventChannel.send(BusinessPanelEvent.OperationSuccess("Trabajador registrado."))
                    cargarPanel(businessId)
                }
                .onFailure { error ->
                    eventChannel.send(BusinessPanelEvent.OperationFailed(error))
                }
        }
    }

    fun agregarRecurso(businessId: String, nombre: String) {
        val safeName = nombre.trim()
        if (businessId.isBlank() || safeName.isBlank()) {
            viewModelScope.launch { eventChannel.send(BusinessPanelEvent.OperationFailed(IllegalArgumentException("Ingresa un nombre válido para el recurso."))) }
            return
        }
        viewModelScope.launch {
            suspendResult { repository.crearRecurso(businessId, safeName) }
                .onSuccess {
                    eventChannel.send(BusinessPanelEvent.OperationSuccess("Recurso registrado."))
                    cargarPanel(businessId)
                }
                .onFailure { error ->
                    eventChannel.send(BusinessPanelEvent.OperationFailed(error))
                }
        }
    }

    fun cambiarEstadoReserva(businessId: String, reservaId: String, estado: String) {
        if (!reservationChangesInFlight.add(reservaId)) return
        viewModelScope.launch {
            suspendResult { repository.cambiarEstadoReserva(businessId, reservaId, estado) }
                .onSuccess {
                    reservationChangesInFlight.remove(reservaId)
                    eventChannel.send(BusinessPanelEvent.OperationSuccess("Reserva actualizada.", reservaId))
                    cargarPanel(businessId)
                }
                .onFailure { error ->
                    reservationChangesInFlight.remove(reservaId)
                    eventChannel.send(BusinessPanelEvent.OperationFailed(error, reservaId))
                }
        }
    }

    fun publicar(businessId: String, payload: JSONObject) {
        if (businessId.isBlank() || payload.optString("title").trim().isBlank()) {
            viewModelScope.launch { eventChannel.send(BusinessPanelEvent.OperationFailed(IllegalArgumentException("Ingresa el nombre de la publicación."))) }
            return
        }
        payload.put("title", payload.optString("title").trim())
        payload.put("description", payload.optString("description").trim())
        viewModelScope.launch {
            suspendResult { repository.publicar(businessId, payload) }
                .onSuccess {
                    eventChannel.send(BusinessPanelEvent.OperationSuccess("Publicación guardada."))
                    cargarPanel(businessId)
                }
                .onFailure { error ->
                    eventChannel.send(BusinessPanelEvent.OperationFailed(error))
                }
        }
    }

    fun configurarHorarios(businessId: String, horarios: JSONArray) {
        if (businessId.isBlank() || horarios.length() == 0) {
            viewModelScope.launch { eventChannel.send(BusinessPanelEvent.OperationFailed(IllegalArgumentException("Configura al menos un bloque de horario."))) }
            return
        }
        viewModelScope.launch {
            suspendResult { repository.configurarHorarios(businessId, horarios) }
                .onSuccess {
                    eventChannel.send(BusinessPanelEvent.OperationSuccess("Horario actualizado."))
                    cargarPanel(businessId)
                }
                .onFailure { error ->
                    eventChannel.send(BusinessPanelEvent.OperationFailed(error))
                }
        }
    }

    override fun onCleared() {
        dashboardRequestId.incrementAndGet()
        super.onCleared()
        repository.close()
    }
}
