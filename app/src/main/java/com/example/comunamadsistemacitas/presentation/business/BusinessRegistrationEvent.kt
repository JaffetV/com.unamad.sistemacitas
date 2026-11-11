package com.example.comunamadsistemacitas.presentation.business

import org.json.JSONObject

/** Eventos únicos emitidos durante el alta de negocio. */
sealed interface BusinessRegistrationEvent {
    data class Registered(
        val registeredBusiness: JSONObject,
        val ownerFullName: String
    ) : BusinessRegistrationEvent

    data class Failed(val cause: Throwable) : BusinessRegistrationEvent
}
