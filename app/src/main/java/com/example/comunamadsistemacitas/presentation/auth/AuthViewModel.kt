package com.example.comunamadsistemacitas.presentation.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.comunamadsistemacitas.data.repository.AuthRepository
import com.example.comunamadsistemacitas.data.repository.AuthRepositoryContract
import com.example.comunamadsistemacitas.domain.validation.AuthValidation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Estado de autenticación separado de las Activities; sobrevive a recreaciones de UI. */
class AuthViewModel(
    application: Application,
    private val repository: AuthRepositoryContract
) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, AuthRepository(application))
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState = _uiState.asStateFlow()
    // Channel conserva eventos si la Activity se recrea durante una operación.
    private val eventChannel = Channel<AuthEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    fun login(email: String, password: String) {
        AuthValidation.validateLogin(email, password)?.let { message ->
            _uiState.value = AuthUiState(errorMessage = message, statusMessage = message)
            return
        }
        execute(AuthEvent.Source.LOGIN) { repository.login(email.trim(), password) }
    }

    fun registerCustomer(name: String, email: String, phone: String, password: String) {
        AuthValidation.validateCustomer(name, email, phone, password)?.let { message ->
            _uiState.value = AuthUiState(errorMessage = message, statusMessage = message)
            return
        }
        execute(AuthEvent.Source.CUSTOMER_REGISTRATION) {
            repository.registerCustomer(name.trim(), email.trim(), phone.trim(), password)
        }
    }

    private fun execute(source: AuthEvent.Source, operation: suspend () -> JSONObject) {
        if (_uiState.value.isLoading) return
        _uiState.value = AuthUiState(isLoading = true, statusMessage = "Conectando con tu cuenta…")
        viewModelScope.launch {
            try {
                val user = operation()
                _uiState.value = AuthUiState(statusMessage = "Sesión iniciada correctamente.")
                eventChannel.send(AuthEvent.Authenticated(user, source))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.value = AuthUiState(
                    statusMessage = error.message ?: "No se pudo completar la autenticación.",
                    errorMessage = error.message ?: "No se pudo completar la autenticación."
                )
                eventChannel.send(AuthEvent.Failed(error))
            }
        }
    }

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }
}
