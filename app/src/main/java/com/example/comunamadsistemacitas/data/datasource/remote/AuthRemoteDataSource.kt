package com.example.comunamadsistemacitas.data.datasource.remote

import android.content.Context
import com.example.comunamadsistemacitas.data.remote.MarketplaceApiClient
import com.example.comunamadsistemacitas.data.remote.MarketplaceApiConfig
import org.json.JSONObject

/** Fuente remota de autenticación. No contiene reglas de interfaz ni navegación. */
class AuthRemoteDataSource(context: Context) {
    private val api: MarketplaceApiClient = MarketplaceApiConfig.crearCliente(context.applicationContext)

    fun login(email: String, password: String): JSONObject = api.iniciarSesion(email, password)

    fun registerCustomer(name: String, email: String, phone: String, password: String): JSONObject =
        api.registrarCliente(name, email, phone, password)

    fun close() = api.liberar()
}
