package com.example.comunamadsistemacitas.view

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.comunamadsistemacitas.MainActivity
import com.example.comunamadsistemacitas.R
import com.example.comunamadsistemacitas.data.repository.CitaRepository
import com.example.comunamadsistemacitas.view.adapter.CitaAdapter
import com.google.android.material.button.MaterialButton

/**
 * ListarCitasActivity: Muestra la lista de citas almacenadas en SQLite con tarjetas interactivas.
 * Incluye gestión de estado vacío y animaciones fluidas de navegación.
 */
class ListarCitasActivity : AppCompatActivity() {

    private lateinit var rvCitas: RecyclerView
    private lateinit var containerEmptyState: LinearLayout
    private lateinit var adapter: CitaAdapter
    private lateinit var repository: CitaRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_listar_citas)

        repository = CitaRepository(this)

        val btnAtras = findViewById<MaterialButton>(R.id.btnAtras)
        val btnInicio = findViewById<MaterialButton>(R.id.btnInicio)
        val btnAgendarPrimeraCita = findViewById<MaterialButton>(R.id.btnAgendarPrimeraCita)

        containerEmptyState = findViewById(R.id.containerEmptyState)
        rvCitas = findViewById(R.id.rvCitas)
        rvCitas.layoutManager = LinearLayoutManager(this)

        // Botón Atrás
        btnAtras?.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        // Botón Inicio
        btnInicio?.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        btnAgendarPrimeraCita?.setOnClickListener {
            startActivity(Intent(this, RegistrarCitaActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        val listaCitas = repository.obtenerTodasLasCitas()
        adapter = CitaAdapter(listaCitas) { cita ->
            val intent = Intent(this, DetalleCitaActivity::class.java)
            intent.putExtra("CITA_ID", cita.id)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        rvCitas.adapter = adapter
        actualizarVisibilidadLista(listaCitas.size)
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) {
            val lista = repository.obtenerTodasLasCitas()
            adapter.actualizarLista(lista)
            actualizarVisibilidadLista(lista.size)
        }
    }

    private fun actualizarVisibilidadLista(cantidad: Int) {
        if (cantidad == 0) {
            containerEmptyState.visibility = View.VISIBLE
            rvCitas.visibility = View.GONE
        } else {
            containerEmptyState.visibility = View.GONE
            rvCitas.visibility = View.VISIBLE
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
