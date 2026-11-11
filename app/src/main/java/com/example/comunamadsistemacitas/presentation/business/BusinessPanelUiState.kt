package com.example.comunamadsistemacitas.presentation.business

import org.json.JSONArray
import org.json.JSONObject

/** Estado inmutable de la pantalla de administración del negocio. */
data class BusinessPanelUiState(
    val isSelectingBusiness: Boolean = false,
    val businesses: JSONArray = JSONArray(),
    val isLoadingPanel: Boolean = false,
    val businessId: String = "",
    val dashboardData: JSONObject? = null,
    val errorMessage: String? = null
)
