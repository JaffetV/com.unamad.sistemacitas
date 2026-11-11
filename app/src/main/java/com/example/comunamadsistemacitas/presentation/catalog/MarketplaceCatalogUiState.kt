package com.example.comunamadsistemacitas.presentation.catalog

import org.json.JSONObject

/** Estado inmutable que representa todos los estados visibles del catálogo. */
data class MarketplaceCatalogUiState(
    val businesses: List<JSONObject> = emptyList(),
    val query: String = "",
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    val reachedDisplayLimit: Boolean = false
)
