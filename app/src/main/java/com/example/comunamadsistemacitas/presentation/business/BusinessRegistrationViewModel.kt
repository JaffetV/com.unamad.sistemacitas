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
import org.json.JSONObject

/** ViewModel para el alta de negocios en PostgreSQL. */
class BusinessRegistrationViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: BusinessRepositoryContract = BusinessRepository(application)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(BusinessRegistrationUiState())
    val uiState: StateFlow<BusinessRegistrationUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<BusinessRegistrationEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    fun haySesion(): Boolean = repository.haySesion()

    fun cargarCategorias() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCategories = true, errorMessage = null) }
            suspendResult { repository.obtenerCategorias() }
                .onSuccess { json ->
                    _uiState.update { it.copy(isLoadingCategories = false, categories = json, errorMessage = null) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoadingCategories = false, errorMessage = error.message) }
                }
        }
    }

    fun cargarPerfil() {
        if (!repository.haySesion()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingProfile = true) }
            suspendResult { repository.obtenerMiPerfil() }
                .onSuccess { profile ->
                    _uiState.update { it.copy(isLoadingProfile = false, userProfile = profile) }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoadingProfile = false) }
                }
        }
    }

    fun registrarNegocio(
        existingSession: Boolean,
        payload: JSONObject,
        business: JSONObject,
        ownerFullName: String
    ) {
        if (_uiState.value.isRegistering) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRegistering = true, errorMessage = null) }
            suspendResult {
                if (existingSession) repository.crearOtroNegocio(business)
                else repository.registrarNegocio(payload)
            }.onSuccess { registered ->
                _uiState.update { it.copy(isRegistering = false) }
                eventChannel.send(BusinessRegistrationEvent.Registered(registered, ownerFullName.trim()))
            }.onFailure { error ->
                _uiState.update { it.copy(isRegistering = false, errorMessage = error.message) }
                eventChannel.send(BusinessRegistrationEvent.Failed(error))
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        repository.close()
    }
}
