package com.example.comunamadsistemacitas.presentation.catalog

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.comunamadsistemacitas.data.repository.BusinessRepository
import com.example.comunamadsistemacitas.data.repository.BusinessRepositoryContract
import com.example.comunamadsistemacitas.presentation.suspendResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.util.concurrent.atomic.AtomicInteger

/**
 * Orquesta las consultas del marketplace que no pertenecen al catálogo paginado.
 * La Activity solo renderiza eventos y ya no conoce MarketplaceApiClient.
 */
class MarketplaceBrowseViewModel(
    application: Application,
    private val repository: BusinessRepositoryContract
) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, BusinessRepository(application))

    private val eventChannel = Channel<MarketplaceBrowseEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    private val cancellationsInFlight = mutableSetOf<String>()
    private val requestSequence = AtomicInteger(0)
    private val operationJobs = mutableMapOf<MarketplaceBrowseEvent.Operation, Job>()

    fun nuevaClaveSolicitud(): Int = requestSequence.incrementAndGet()

    fun haySesion(): Boolean = repository.haySesion()

    fun cargarMisNegocios(requestKey: Int) = execute(requestKey, MarketplaceBrowseEvent.Operation.BUSINESSES) {
        MarketplaceBrowseEvent.BusinessesLoaded(requestKey, repository.obtenerMisNegocios())
    }

    fun cargarNegocio(businessId: String, requestKey: Int) {
        if (businessId.isBlank()) {
            emitirError(requestKey, MarketplaceBrowseEvent.Operation.BUSINESS, IllegalArgumentException("El negocio no es válido."))
            return
        }
        execute(requestKey, MarketplaceBrowseEvent.Operation.BUSINESS) {
            val business = repository.obtenerNegocio(businessId)
            val id = business.optString("id").trim()
            val name = business.optString("name").trim()
            require(id == businessId && name.isNotBlank() && name != "null") {
                "El servidor devolvió un negocio incompleto o diferente al solicitado."
            }
            MarketplaceBrowseEvent.BusinessLoaded(requestKey, business)
        }
    }

    fun cargarPublicaciones(businessId: String, requestKey: Int) {
        if (businessId.isBlank()) {
            emitirError(requestKey, MarketplaceBrowseEvent.Operation.LISTINGS, IllegalArgumentException("El negocio no es válido."))
            return
        }
        execute(requestKey, MarketplaceBrowseEvent.Operation.LISTINGS) {
            MarketplaceBrowseEvent.ListingsLoaded(requestKey, businessId, repository.obtenerPublicaciones(businessId))
        }
    }

    fun cargarMisReservas(requestKey: Int) = execute(requestKey, MarketplaceBrowseEvent.Operation.RESERVATIONS) {
        MarketplaceBrowseEvent.ReservationsLoaded(requestKey, repository.obtenerMisReservas())
    }

    fun cancelarReserva(reservationId: String, requestKey: Int) {
        val safeId = reservationId.trim()
        if (safeId.isBlank() || safeId == "null") {
            emitirError(requestKey, MarketplaceBrowseEvent.Operation.CANCEL_RESERVATION,
                IllegalArgumentException("El identificador de la reserva no es válido."), safeId)
            return
        }
        if (!cancellationsInFlight.add(safeId)) return
        viewModelScope.launch {
            suspendResult { repository.cancelarMiReserva(safeId) }
                .onSuccess {
                    cancellationsInFlight.remove(safeId)
                    eventChannel.send(MarketplaceBrowseEvent.ReservationCancelled(requestKey, safeId))
                }
                .onFailure { error ->
                    cancellationsInFlight.remove(safeId)
                    eventChannel.send(MarketplaceBrowseEvent.Failed(
                        requestKey, MarketplaceBrowseEvent.Operation.CANCEL_RESERVATION, error, safeId
                    ))
                }
        }
    }

    private fun execute(
        requestKey: Int,
        operation: MarketplaceBrowseEvent.Operation,
        block: suspend () -> MarketplaceBrowseEvent
    ) {
        val previous = synchronized(operationJobs) { operationJobs.remove(operation) }
        previous?.cancel()

        val job = viewModelScope.launch {
            suspendResult(block)
                .onSuccess { eventChannel.send(it) }
                .onFailure { error -> eventChannel.send(MarketplaceBrowseEvent.Failed(requestKey, operation, error)) }
        }
        synchronized(operationJobs) { operationJobs[operation] = job }
        job.invokeOnCompletion {
            synchronized(operationJobs) {
                if (operationJobs[operation] === job) operationJobs.remove(operation)
            }
        }
    }

    private fun emitirError(
        requestKey: Int,
        operation: MarketplaceBrowseEvent.Operation,
        error: Throwable,
        reservationId: String? = null
    ) {
        viewModelScope.launch {
            eventChannel.send(MarketplaceBrowseEvent.Failed(requestKey, operation, error, reservationId))
        }
    }

    override fun onCleared() {
        super.onCleared()
        repository.close()
    }
}
