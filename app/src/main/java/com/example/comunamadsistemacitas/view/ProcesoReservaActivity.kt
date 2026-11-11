package com.example.comunamadsistemacitas.view

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Puente de compatibilidad para enlaces antiguos.
 * La reserva real se realiza únicamente desde el catálogo remoto, consultando horarios,
 * trabajadores/recursos y disponibilidad en PostgreSQL. Esta pantalla ya no contiene
 * trabajadores, servicios ni horarios de demostración ni guarda citas en SQLite.
 */
class ProcesoReservaActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val next = Intent(this, MarketplaceRemotoActivity::class.java)
        val businessId = intent.getStringExtra("OPEN_BUSINESS_ID")
            ?: intent.getStringExtra("BUSINESS_ID")
            ?: intent.getStringExtra("NEGOCIO_UUID")
        if (!businessId.isNullOrBlank()) next.putExtra("OPEN_BUSINESS_ID", businessId)
        startActivity(next)
        finish()
    }
}
