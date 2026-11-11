package com.example.comunamadsistemacitas.data.datasource.remote

import android.content.Context
import com.example.comunamadsistemacitas.data.remote.MarketplaceApiClient
import com.example.comunamadsistemacitas.data.remote.MarketplaceApiConfig
import org.json.JSONArray
import org.json.JSONObject

/** Fuente remota de gestión de negocios y administración de panel. */
class BusinessRemoteDataSource(context: Context) {
    private val api: MarketplaceApiClient = MarketplaceApiConfig.crearCliente(context.applicationContext)

    fun haySesion(): Boolean = api.haySesion()
    fun cerrarSesion() = api.cerrarSesion()
    fun obtenerMiPerfil(): JSONObject = api.obtenerMiPerfil()
    fun obtenerCategorias(): JSONArray = api.obtenerCategorias()
    fun registrarNegocio(datos: JSONObject): JSONObject = api.registrarNegocio(datos)
    fun crearOtroNegocio(datos: JSONObject): JSONObject = api.crearOtroNegocio(datos)
    fun obtenerMisNegocios(): JSONArray = api.obtenerMisNegocios()
    fun obtenerMisReservas(): JSONArray = api.obtenerMisReservas()
    fun cancelarMiReserva(reservaId: String): JSONObject = api.cancelarMiReserva(reservaId)
    fun obtenerNegocio(negocioId: String): JSONObject = api.obtenerNegocio(negocioId)
    fun obtenerPublicaciones(negocioId: String): JSONArray = api.obtenerPublicaciones(negocioId)
    fun obtenerReservasNegocio(negocioId: String): JSONArray = api.obtenerReservasNegocio(negocioId)
    fun obtenerHorarios(negocioId: String): JSONArray = api.obtenerHorarios(negocioId)
    fun obtenerTrabajadores(negocioId: String): JSONArray = api.obtenerTrabajadores(negocioId)
    fun obtenerRecursos(negocioId: String): JSONArray = api.obtenerRecursos(negocioId)
    fun crearTrabajador(negocioId: String, nombre: String, especialidad: String): JSONObject =
        api.crearTrabajador(negocioId, nombre, especialidad)
    fun crearRecurso(negocioId: String, nombre: String, tipo: String = "CAPACITY"): JSONObject =
        api.crearRecurso(negocioId, nombre, tipo)
    fun cambiarEstadoReserva(negocioId: String, reservaId: String, estado: String): JSONObject =
        api.cambiarEstadoReserva(negocioId, reservaId, estado)
    fun publicar(negocioId: String, publicacion: JSONObject): JSONObject =
        api.publicar(negocioId, publicacion)
    fun configurarHorarios(negocioId: String, horarios: JSONArray): JSONObject =
        api.configurarHorarios(negocioId, horarios)
    fun close() = api.liberar()
}
