package com.example.comunamadsistemacitas

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.comunamadsistemacitas.view.PortalMarketplaceActivity

/**
 * Entrada de compatibilidad para instalaciones/enlaces antiguos.
 * El flujo vigente de citas y reservas se opera desde el backend centralizado.
 * La base SQLite histórica no se borra ni se usa para registrar nuevas reservas.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, PortalMarketplaceActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
        finish()
    }
}
