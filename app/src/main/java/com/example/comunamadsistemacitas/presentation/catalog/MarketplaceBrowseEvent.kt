package com.example.comunamadsistemacitas.presentation.catalog

import org.json.JSONArray
import org.json.JSONObject

/** Eventos de una operación de navegación/consulta del marketplace. */
sealed class MarketplaceBrowseEvent {
    data class BusinessesLoaded(val requestKey: Int, val items: JSONArray) : MarketplaceBrowseEvent()
    data class BusinessLoaded(val requestKey: Int, val business: JSONObject) : MarketplaceBrowseEvent()
    data class ListingsLoaded(val requestKey: Int, val businessId: String, val items: JSONArray) : MarketplaceBrowseEvent()
    data class ReservationsLoaded(val requestKey: Int, val items: JSONArray) : MarketplaceBrowseEvent()
    data class ReservationCancelled(val requestKey: Int, val reservationId: String) : MarketplaceBrowseEvent()
    data class Failed(
        val requestKey: Int,
        val operation: Operation,
        val cause: Throwable,
        val reservationId: String? = null
    ) : MarketplaceBrowseEvent()

    enum class Operation { BUSINESSES, BUSINESS, LISTINGS, RESERVATIONS, CANCEL_RESERVATION }
}
