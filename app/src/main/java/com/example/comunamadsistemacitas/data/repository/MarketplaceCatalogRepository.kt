package com.example.comunamadsistemacitas.data.repository

import android.content.Context
import com.example.comunamadsistemacitas.data.remote.MarketplaceApiConfig
import com.example.comunamadsistemacitas.data.remote.MarketplaceBusinessPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fuente única de datos del catálogo. La Activity no conoce HTTP ni cursores;
 * esta clase puede reutilizarse desde otra interfaz (por ejemplo, una futura web/KMP API).
 */
class MarketplaceCatalogRepository(context: Context) {
    private val api = MarketplaceApiConfig.crearCliente(context.applicationContext)

    suspend fun explorar(
        query: String,
        cursor: String?,
        limit: Int
    ): MarketplaceBusinessPage = withContext(Dispatchers.IO) {
        api.explorarNegociosPagina(
            texto = query,
            cursor = cursor,
            limit = limit
        )
    }

    fun liberar() = api.liberar()
}
