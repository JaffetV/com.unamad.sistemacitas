package com.example.comunamadsistemacitas.presentation.booking

import org.json.JSONObject

sealed interface BookingEvent {
    data class Created(val reservation: JSONObject) : BookingEvent
    data class Failed(val cause: Throwable) : BookingEvent
}
