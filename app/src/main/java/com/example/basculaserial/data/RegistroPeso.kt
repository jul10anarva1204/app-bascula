package com.example.basculaserial.data

/** Registro de peso guardado localmente */
data class RegistroPeso(
    val id: Long = 0,
    val peso: String,           // peso neto (lo que se imprime) — columna original
    val unidad: String,
    val timestamp: Long = System.currentTimeMillis(),
    val fotoRuta: String? = null,   // ruta local de la foto
    // v3: desglose tara
    val pesoBruto: String? = null,
    val tara: String? = null,
    val pesoNeto: String? = null,
    // v4: código de barras escaneado
    val codigoBarras: String? = null
)
