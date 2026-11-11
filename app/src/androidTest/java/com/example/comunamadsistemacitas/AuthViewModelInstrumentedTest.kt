package com.example.comunamadsistemacitas

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.comunamadsistemacitas.data.repository.AuthRepositoryContract
import com.example.comunamadsistemacitas.presentation.auth.AuthEvent
import com.example.comunamadsistemacitas.presentation.auth.AuthViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class AuthViewModelInstrumentedTest {
    private class FakeAuthRepository : AuthRepositoryContract {
        val loginCalls = AtomicInteger(0)
        var failLogin = false

        override suspend fun login(email: String, password: String): JSONObject {
            loginCalls.incrementAndGet()
            if (failLogin) throw IOException("Credenciales inválidas")
            return JSONObject().put("id", "user-test").put("full_name", "Ana Prueba")
        }

        override suspend fun registerCustomer(
            name: String,
            email: String,
            phone: String,
            password: String
        ): JSONObject = JSONObject().put("id", "user-test").put("full_name", name)

        override fun close() = Unit
    }

    @Test
    fun loginValidoEmiteSesionYNoBloqueaLaInterfaz() = runBlocking {
        val repository = FakeAuthRepository()
        val viewModel = AuthViewModel(ApplicationProvider.getApplicationContext<Application>(), repository)
        val event = async { withTimeout(5_000) { viewModel.events.first() } }

        viewModel.login("ana@unamad.edu.pe", "ClaveSegura123")
        val result = event.await() as AuthEvent.Authenticated

        assertEquals("user-test", result.user.getString("id"))
        assertEquals(AuthEvent.Source.LOGIN, result.source)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(1, repository.loginCalls.get())
    }

    @Test
    fun loginInvalidoSeExponeComoEstadoDeErrorSinLlamarAlServidor() = runBlocking {
        val repository = FakeAuthRepository()
        val viewModel = AuthViewModel(ApplicationProvider.getApplicationContext<Application>(), repository)

        viewModel.login("correo-invalido", "ClaveSegura123")
        val state = withTimeout(1_000) { viewModel.uiState.first { it.errorMessage != null } }

        assertEquals("Escribe un correo válido.", state.errorMessage)
        assertEquals(0, repository.loginCalls.get())
    }

    @Test
    fun falloDeRedDevuelveEstadoDeErrorYPermiteReintento() = runBlocking {
        val repository = FakeAuthRepository().apply { failLogin = true }
        val viewModel = AuthViewModel(ApplicationProvider.getApplicationContext<Application>(), repository)
        val failedEvent = async { withTimeout(5_000) { viewModel.events.first() } }

        viewModel.login("ana@unamad.edu.pe", "ClaveSegura123")
        assertTrue(failedEvent.await() is AuthEvent.Failed)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("Credenciales inválidas", viewModel.uiState.value.errorMessage)

        repository.failLogin = false
        val successEvent = async { withTimeout(5_000) { viewModel.events.first() } }
        viewModel.login("ana@unamad.edu.pe", "ClaveSegura123")
        assert(successEvent.await() is AuthEvent.Authenticated)
        assertEquals(2, repository.loginCalls.get())
    }
    @Test
    fun registroDeClienteEmiteEventoConOrigenCorrecto() = runBlocking {
        val viewModel = AuthViewModel(
            ApplicationProvider.getApplicationContext<Application>(),
            FakeAuthRepository()
        )
        val event = async { withTimeout(5_000) { viewModel.events.first() } }

        viewModel.registerCustomer("Ana Prueba", "ana@unamad.edu.pe", "987654321", "ClaveSegura123")
        val result = event.await() as AuthEvent.Authenticated

        assertEquals(AuthEvent.Source.CUSTOMER_REGISTRATION, result.source)
        assertEquals("Ana Prueba", result.user.getString("full_name"))
        assertFalse(viewModel.uiState.value.isLoading)
    }

}
