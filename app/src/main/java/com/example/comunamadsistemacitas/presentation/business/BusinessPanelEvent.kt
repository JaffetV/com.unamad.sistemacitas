package com.example.comunamadsistemacitas.presentation.business

import org.json.JSONArray

/** Eventos únicos emitidos por el panel administrativo del negocio. */
sealed interface BusinessPanelEvent {
    data class BusinessListLoaded(val businesses: JSONArray) : BusinessPanelEvent
    data class OperationSuccess(val message: String, val operationId: String? = null) : BusinessPanelEvent
    data class OperationFailed(val cause: Throwable, val operationId: String? = null) : BusinessPanelEvent
}
