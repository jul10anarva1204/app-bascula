package com.example.basculaserial

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Info de una impresora USB detectada */
data class UsbImpresoraInfo(
    val nombre: String,
    val deviceName: String,
    val vendorId: Int,
    val productId: Int
)

/**
 * Maneja la conexión USB con la impresora térmica 365B (Xprinter XP-365B)
 * y genera comandos TSPL para etiquetas de 38×25 mm.
 *
 * ─ Investigación de drivers ──────────────────────────────────────────────
 *  La XP-365B usa comunicación USB bulk directa (no serial/COM):
 *  • VID conocidos de Xprinter: 0x1FC9 (NXP), 0x0483 (STM), 0x28E9 (GD32),
 *                                0x04B8 (Epson compat.), 0x0FE6 (ICS)
 *  • Protocolo: TSPL (TSC Printer Language) — comandos ASCII + bulk OUT
 *  • Clase USB: generalmente 0x07 (Printer) o 0xFF (Vendor-specific)
 *  • Endpoint: Bulk OUT (interface 0 ó 1, endpoint tipo XFER_BULK DIR_OUT)
 *  • NO requiere usb-serial-for-android (no es un puerto serial)
 *  • NO requiere el SDK de Xprinter — TSPL puro funciona directamente
 *
 *  Para identificar VID/PID del tuyo: conecta al PC → Administrador de
 *  dispositivos → Propiedades → Detalles → IDs de hardware
 * ─────────────────────────────────────────────────────────────────────────
 */
class PrinterManager(context: Context) {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    companion object {
        /**
         * VIDs conocidos de impresoras térmicas Xprinter / similares.
         * La app mostrará TODOS los dispositivos USB, pero estos se muestran
         * primero con un indicador de "impresora conocida".
         */
        val XPRINTER_VIDS = setOf(
            0x1FC9,  // NXP Semiconductors — chip más común en XP-365B
            0x0483,  // STMicroelectronics
            0x28E9,  // GigaDevice (GD32) — chip chino común
            0x04B8,  // Seiko Epson (compatibles)
            0x0FE6,  // ICS Advent
            0x0416,  // Winbond Electronics
            0x067B,  // Prolific Technology (PL2303 en modo bulk)
            0x1A86,  // QinHeng Electronics (CH340 en modo bulk)
        )

        /** Clase USB de impresora estándar */
        const val USB_CLASS_PRINTER = 7
    }

    // ── Detección de dispositivos ────────────────────────────────────────────

    /**
     * Lista todos los dispositivos USB que son probablemente impresoras.
     * Prioridad: (1) clase USB=Printer, (2) VID conocido de Xprinter,
     *            (3) cualquier dispositivo con endpoint bulk OUT.
     * Excluye dispositivos serial/COM (usados por la báscula) cuando
     * son identificados por usb-serial-for-android.
     */
    fun getDispositivosConectados(): List<UsbImpresoraInfo> {
        val dispositivos = usbManager.deviceList.values.filter { device ->
            esPosibleImpresora(device)
        }

        // Ordenar: primero los más probables (clase printer o VID conocido)
        return dispositivos.sortedByDescending { device ->
            when {
                tieneClaseImpresora(device)         -> 2
                device.vendorId in XPRINTER_VIDS    -> 1
                else                                -> 0
            }
        }.map { device ->
            val esConocida = device.vendorId in XPRINTER_VIDS || tieneClaseImpresora(device)
            UsbImpresoraInfo(
                nombre     = buildNombre(device, esConocida),
                deviceName = device.deviceName,
                vendorId   = device.vendorId,
                productId  = device.productId
            )
        }
    }

    private fun buildNombre(device: UsbDevice, esConocida: Boolean): String {
        val base = device.productName?.takeIf { it.isNotBlank() }
            ?: "USB ${device.vendorId.toString(16).uppercase()}:${device.productId.toString(16).uppercase()}"
        return if (esConocida) "🖨 $base" else base
    }

    private fun esPosibleImpresora(device: UsbDevice): Boolean =
        tieneClaseImpresora(device) ||
                device.vendorId in XPRINTER_VIDS ||
                tieneBulkOut(device)

    private fun tieneClaseImpresora(device: UsbDevice): Boolean {
        if (device.deviceClass == USB_CLASS_PRINTER) return true
        for (i in 0 until device.interfaceCount) {
            if (device.getInterface(i).interfaceClass == USB_CLASS_PRINTER) return true
        }
        return false
    }

    /** Busca un dispositivo conectado por VID+PID */
    fun encontrarDispositivo(vendorId: Int, productId: Int): UsbDevice? =
        usbManager.deviceList.values.find {
            it.vendorId == vendorId && it.productId == productId
        }

    fun tienePermiso(device: UsbDevice) = usbManager.hasPermission(device)

    // ── Impresión ────────────────────────────────────────────────────────────

    /**
     * Imprime una etiqueta TSPL en la impresora indicada por VID+PID.
     *
     * Estrategia de conexión:
     *  1. Intenta Interface 0 (estándar en la mayoría)
     *  2. Si falla, prueba todas las interfaces en orden
     *  3. Envía TSPL en bloques de 4 KB con reintentos
     */
    suspend fun imprimirEtiqueta(
        vendorId: Int,
        productId: Int,
        nombreProducto: String,
        peso: String,
        unidad: String,
        timestamp: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val device = encontrarDispositivo(vendorId, productId)
            ?: return@withContext Result.failure(Exception(
                "Impresora no conectada. Verifica el cable USB y el hub OTG."))

        if (!usbManager.hasPermission(device)) {
            return@withContext Result.failure(Exception(
                "Sin permiso USB para la impresora. Abre Ajustes → vuelve a seleccionarla."))
        }

        val tspl = buildTsplLabel(nombreProducto, peso, unidad, timestamp)
        val data = tspl.toByteArray(Charsets.US_ASCII)

        return@withContext enviarPorUsb(device, data)
    }

    private suspend fun enviarPorUsb(device: UsbDevice, data: ByteArray): Result<Unit> =
        withContext(Dispatchers.IO) {
            var connection: UsbDeviceConnection? = null
            try {
                connection = usbManager.openDevice(device)
                    ?: return@withContext Result.failure(Exception("No se pudo abrir la impresora USB"))

                // Buscar endpoint bulk OUT en todas las interfaces
                for (ifaceIdx in 0 until device.interfaceCount) {
                    val iface = device.getInterface(ifaceIdx)
                    val bulkOut = (0 until iface.endpointCount)
                        .map { iface.getEndpoint(it) }
                        .firstOrNull {
                            it.type == UsbConstants.USB_ENDPOINT_XFER_BULK &&
                                    it.direction == UsbConstants.USB_DIR_OUT
                        } ?: continue

                    if (!connection.claimInterface(iface, true)) continue

                    // Enviar datos en bloques de 4 KB
                    var offset = 0
                    var ok = true
                    while (offset < data.size) {
                        val chunk = minOf(data.size - offset, 4096)
                        val sent  = connection.bulkTransfer(bulkOut, data, offset, chunk, 5000)
                        if (sent < 0) { ok = false; break }
                        offset += sent
                    }

                    connection.releaseInterface(iface)

                    return@withContext if (ok) Result.success(Unit)
                    else Result.failure(Exception("Error en la transferencia de datos al imprimir"))
                }

                Result.failure(Exception("No se encontró endpoint de impresión en el dispositivo"))
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                try { connection?.close() } catch (_: Exception) { }
            }
        }

    // ── Generador de etiqueta TSPL ───────────────────────────────────────────

    /**
     * Construye comandos TSPL para etiqueta 38×25 mm a 203 DPI.
     *
     *  38 mm = 304 dots de ancho
     *  25 mm = 200 dots de alto
     *
     *  Layout (módulo QR=5 → 21×5=105 dots):
     *   y=4   Nombre del producto  (font "2", ~13 dots/char, alto ~20 dots)
     *   y=27  Código QR centrado   (módulo 5 dots → 105×105 dots)
     *   y=135 Peso + unidad        (font "2")
     *   y=158 Fecha y hora         (font "1", ~9 dots/char)
     *
     *  El QR solo contiene el número (sin "kg") para mayor legibilidad.
     */
    private fun buildTsplLabel(
        nombreProducto: String,
        peso: String,
        unidad: String,
        timestamp: Long
    ): String {
        val sdf       = SimpleDateFormat("dd/MM/yyyy  HH:mm:ss", Locale.getDefault())
        val fechaHora = sdf.format(Date(timestamp))

        // QR solo con el número, sin unidades
        val qrContent = peso

        val W = 304  // ancho de etiqueta en dots

        fun centrarX(chars: Int, dotsPerChar: Int) =
            maxOf(0, (W - chars * dotsPerChar) / 2)

        val nombre  = nombreProducto.take(22).uppercase(Locale.getDefault())
        val nombreX = centrarX(nombre.length, 13)

        // Módulo 5 → QR de 105×105 dots (21 módulos × 5 dots)
        val qrModulo = 5
        val qrSize   = 21 * qrModulo  // = 105 dots
        val qrX      = (W - qrSize) / 2   // centrado: (304-105)/2 = 99

        val pesoTxt = "$peso $unidad"
        val pesoX   = centrarX(pesoTxt.length, 13)
        val fechaX  = centrarX(fechaHora.length, 9)

        return buildString {
            appendLine("SIZE 38 mm,25 mm")
            appendLine("GAP 2 mm,0")
            appendLine("DIRECTION 0,0")
            appendLine("DENSITY 8,A")
            appendLine("SET PEEL OFF")
            appendLine("SET CUTTER OFF")
            appendLine("CLS")
            // Nombre del producto
            appendLine("""TEXT $nombreX,4,"2",0,1,1,"$nombre"""")
            // QR grande (módulo 5) con solo el número
            appendLine("""QRCODE $qrX,27,L,$qrModulo,A,0,"$qrContent"""")
            // Peso + unidad debajo del QR
            appendLine("""TEXT $pesoX,135,"2",0,1,1,"$pesoTxt"""")
            // Fecha y hora al fondo
            appendLine("""TEXT $fechaX,158,"1",0,1,1,"$fechaHora"""")
            appendLine("PRINT 1,1")
        }
    }

    /** Verifica si un dispositivo tiene al menos un endpoint bulk OUT */
    private fun tieneBulkOut(device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            for (j in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(j)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK &&
                    ep.direction == UsbConstants.USB_DIR_OUT) return true
            }
        }
        return false
    }
}
