package com.example.basculaserial.data

import android.content.Context

/** Almacenamiento persistente de preferencias de la app */
class PrefsManager(context: Context) {
    private val prefs = context.getSharedPreferences("bascula_prefs", Context.MODE_PRIVATE)

    var productName: String
        get() = prefs.getString("product_name", "MI EMPRESA") ?: "MI EMPRESA"
        set(value) = prefs.edit().putString("product_name", value).apply()

    // Impresora USB identificada por VendorId + ProductId
    var printerVendorId: Int
        get() = prefs.getInt("printer_vendor_id", -1)
        set(value) = prefs.edit().putInt("printer_vendor_id", value).apply()

    var printerProductId: Int
        get() = prefs.getInt("printer_product_id", -1)
        set(value) = prefs.edit().putInt("printer_product_id", value).apply()

    var printerName: String
        get() = prefs.getString("printer_name", "") ?: ""
        set(value) = prefs.edit().putString("printer_name", value).apply()
}
