package com.example.basculaserial.data

import android.content.Context

/** Almacenamiento persistente de preferencias de la app */
class PrefsManager(context: Context) {
    private val prefs = context.getSharedPreferences("bascula_prefs", Context.MODE_PRIVATE)

    var productName: String
        get() = prefs.getString("product_name", "MI EMPRESA") ?: "MI EMPRESA"
        set(value) = prefs.edit().putString("product_name", value).apply()

    // Impresora Bluetooth identificada por dirección MAC
    var printerAddress: String
        get() = prefs.getString("printer_address", "") ?: ""
        set(value) = prefs.edit().putString("printer_address", value).apply()

    var printerName: String
        get() = prefs.getString("printer_name", "") ?: ""
        set(value) = prefs.edit().putString("printer_name", value).apply()
}
