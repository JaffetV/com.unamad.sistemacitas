package com.example.comunamadsistemacitas.presentation.business

import org.json.JSONArray
import org.json.JSONObject

/** Estado inmutable de la pantalla de registro de negocio. */
data class BusinessRegistrationUiState(
    val isLoadingCategories: Boolean = false,
    val categories: JSONArray = JSONArray(),
    val isRegistering: Boolean = false,
    val isLoadingProfile: Boolean = false,
    val userProfile: JSONObject? = null,
    val errorMessage: String? = null
)
