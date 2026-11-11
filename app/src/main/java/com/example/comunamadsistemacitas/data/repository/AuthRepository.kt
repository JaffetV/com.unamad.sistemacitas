package com.example.comunamadsistemacitas.data.repository

import android.content.Context
import com.example.comunamadsistemacitas.data.datasource.remote.AuthRemoteDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

interface AuthRepositoryContract {
    suspend fun login(email: String, password: String): JSONObject
    suspend fun registerCustomer(name: String, email: String, phone: String, password: String): JSONObject
    fun close()
}

/** Repositorio reutilizable: la interfaz Android y una futura interfaz web consumen el mismo contrato REST. */
class AuthRepository(context: Context) : AuthRepositoryContract {
    private val remote = AuthRemoteDataSource(context.applicationContext)

    override suspend fun login(email: String, password: String): JSONObject = withContext(Dispatchers.IO) {
        remote.login(email, password)
    }

    override suspend fun registerCustomer(name: String, email: String, phone: String, password: String): JSONObject =
        withContext(Dispatchers.IO) { remote.registerCustomer(name, email, phone, password) }

    override fun close() = remote.close()
}
