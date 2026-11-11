package com.example.comunamadsistemacitas.presentation.catalog

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.comunamadsistemacitas.data.repository.MarketplaceCatalogRepository
import com.example.comunamadsistemacitas.presentation.suspendResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

/**
 * MVVM del catálogo: controla filtros, paginación, errores y respuestas fuera de orden.
 * El identificador de petición evita que una respuesta antigua reemplace un filtro nuevo.
 */
class MarketplaceCatalogViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MarketplaceCatalogRepository(application)
    private val requestVersion = AtomicLong(0)
    private var pageJob: Job? = null

    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow(MarketplaceCatalogUiState())
    val uiState: kotlinx.coroutines.flow.StateFlow<MarketplaceCatalogUiState> = _uiState

    fun buscar(query: String) {
        val version = requestVersion.incrementAndGet()
        pageJob?.cancel()
        _uiState.value = MarketplaceCatalogUiState(
            query = query.trim(),
            isLoading = true
        )
        pageJob = viewModelScope.launch {
            suspendResult {
                repository.explorar(query.trim(), cursor = null, limit = PAGE_SIZE)
            }.onSuccess { page ->
                if (requestVersion.get() != version) return@onSuccess
                val items = page.items.let { array ->
                    buildList { for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        val id = item.optString("id").trim()
                        val name = item.optString("name").trim()
                        if (id.isNotBlank() && id != "null" && name.isNotBlank() && name != "null") add(item)
                    } }
                }
                _uiState.value = _uiState.value.copy(
                    businesses = items,
                    nextCursor = page.nextCursor,
                    hasMore = page.hasMore,
                    isLoading = false,
                    errorMessage = null,
                    reachedDisplayLimit = items.size >= MAX_RENDERED && page.hasMore
                )
            }.onFailure { error ->
                if (requestVersion.get() != version) return@onFailure
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = error.message ?: "No se pudo cargar el catálogo.")
            }
        }
    }

    fun cargarMas() {
        val current = _uiState.value
        if (current.isLoading || current.isLoadingMore || !current.hasMore || current.reachedDisplayLimit) return
        val version = requestVersion.get()
        val cursor = current.nextCursor ?: return
        _uiState.value = current.copy(isLoadingMore = true, errorMessage = null)
        pageJob = viewModelScope.launch {
            suspendResult {
                val remaining = MAX_RENDERED - _uiState.value.businesses.size
                repository.explorar(current.query, cursor, minOf(PAGE_SIZE, remaining))
            }.onSuccess { page ->
                if (requestVersion.get() != version) return@onSuccess
                val more = page.items.let { array ->
                    buildList { for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        val id = item.optString("id").trim()
                        val name = item.optString("name").trim()
                        if (id.isNotBlank() && id != "null" && name.isNotBlank() && name != "null") add(item)
                    } }
                }
                val all = _uiState.value.businesses + more
                _uiState.value = _uiState.value.copy(
                    businesses = all,
                    nextCursor = page.nextCursor,
                    hasMore = page.hasMore,
                    isLoadingMore = false,
                    reachedDisplayLimit = all.size >= MAX_RENDERED && page.hasMore,
                    errorMessage = null
                )
            }.onFailure { error ->
                if (requestVersion.get() != version) return@onFailure
                _uiState.value = _uiState.value.copy(isLoadingMore = false, errorMessage = error.message ?: "No se pudieron cargar más negocios.")
            }
        }
    }

    override fun onCleared() {
        pageJob?.cancel()
        repository.liberar()
        super.onCleared()
    }

    private companion object {
        const val PAGE_SIZE = 24
        const val MAX_RENDERED = 120
    }
}
