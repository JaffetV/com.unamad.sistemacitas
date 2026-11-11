package com.example.comunamadsistemacitas.presentation.booking

import org.json.JSONObject

data class BookingUiState(
    val businessId: String = "",
    val listingId: String = "",
    val availabilityDate: String? = null,
    val slots: List<JSONObject> = emptyList(),
    val selectedSlot: JSONObject? = null,
    val availabilityRevision: Long = 0L,
    val isLoadingAvailability: Boolean = false,
    val isSubmitting: Boolean = false,
    val availabilityErrorMessage: String? = null,
    val errorMessage: String? = null
)
