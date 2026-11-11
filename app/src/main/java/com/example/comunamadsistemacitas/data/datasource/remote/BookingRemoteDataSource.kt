package com.example.comunamadsistemacitas.data.datasource.remote

import android.content.Context
import com.example.comunamadsistemacitas.data.remote.MarketplaceApiClient
import com.example.comunamadsistemacitas.data.remote.MarketplaceApiConfig
import org.json.JSONArray
import org.json.JSONObject

/** Acceso HTTP puro del módulo de reservas. Las reglas de flujo viven en BookingViewModel. */
class BookingRemoteDataSource(context: Context) {
    private val api: MarketplaceApiClient = MarketplaceApiConfig.crearCliente(context.applicationContext)

    fun availability(businessId: String, listingId: String, localDate: String): JSONArray =
        api.obtenerDisponibilidad(businessId, listingId, localDate)

    fun createReservation(payload: JSONObject): JSONObject = api.crearReserva(payload)

    fun close() = api.liberar()
}
