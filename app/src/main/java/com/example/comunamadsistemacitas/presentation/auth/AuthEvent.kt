package com.example.comunamadsistemacitas.presentation.auth

import org.json.JSONObject

sealed interface AuthEvent {
    data class Authenticated(val user: JSONObject, val source: Source) : AuthEvent
    data class Failed(val cause: Throwable) : AuthEvent
    enum class Source { LOGIN, CUSTOMER_REGISTRATION }
}
