package com.example.comunamadsistemacitas.data.repository

import android.content.Context
import com.example.comunamadsistemacitas.data.datasource.remote.BookingRemoteDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

interface BookingRepositoryContract {
    suspend fun availability(businessId: String, listingId: String, date: String): JSONArray
    suspend fun createReservation(payload: JSONObject): JSONObject
    fun close()
}

class BookingRepository(context: Context) : BookingRepositoryContract {
    private val remote = BookingRemoteDataSource(context.applicationContext)

    override suspend fun availability(businessId: String, listingId: String, date: String): JSONArray =
        withContext(Dispatchers.IO) { remote.availability(businessId, listingId, date) }

    override suspend fun createReservation(payload: JSONObject): JSONObject =
        withContext(Dispatchers.IO) { remote.createReservation(payload) }

    override fun close() = remote.close()
}
