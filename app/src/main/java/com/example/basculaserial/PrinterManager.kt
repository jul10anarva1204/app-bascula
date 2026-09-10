package com.example.basculaserial

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Info de una impresora Bluetooth emparejada */
data class BtImpresoraInfo(
    val nombre: String,
    val address: String
)

/**
 * Imprime tickets térmicos por Bluetooth (ESC/POS) usando raster bitmap.
 * Compatible con: Ofichido POS-5820, Xprinter, GOOJPRT y similares de 58mm.
 */
class PrinterManager(context: Context) {

    private val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    companion object {
        private const val TAG = "BASCULA_BT"
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val PRINT_WIDTH = 384
        private val ESC_INIT = byteArrayOf(0x1B, 0x40)
        private val ESC_FEED = byteArrayOf(0x0A)
        private val ESC_CUT  = byteArrayOf(0x1D, 0x56, 0x42, 0x00)
    }

    // ── Detección ──────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun getDispositivosConectados(): List<BtImpresoraInfo> {
        return try {
            val adapter: BluetoothAdapter = btManager.adapter ?: return emptyList()
            adapter.bondedDevices.map { device ->
                BtImpresoraInfo(nombre = device.name ?: "Bluetooth", address = device.address)
            }
        } catch (_: Exception) { emptyList() }
    }

    // ── Impresión ──────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    suspend fun imprimirEtiqueta(
        address: String,
        nombre: String,
        peso: String,
        unidad: String,
        timestamp: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var socket: BluetoothSocket? = null
        return@withContext try {
            Log.d(TAG, "=== IMPRESION TSPL === address=$address")
            val adapter = btManager.adapter
                ?: return@withContext Result.failure(Exception("Bluetooth no disponible"))

            val device = adapter.getRemoteDevice(address)
            adapter.cancelDiscovery()

            socket = try {
                device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
            } catch (e: Exception) {
                device.createRfcommSocketToServiceRecord(SPP_UUID)
            }
            socket.connect()
            Log.d(TAG, "Conectado ✓")
            Thread.sleep(500)

            val fecha = SimpleDateFormat("dd/MM/yy  HH:mm", Locale.getDefault()).format(Date(timestamp))
            // Sanitizar nombre para TSPL (quitar comillas dobles)
            val nombreSafe = nombre.replace("\"", "'").take(18)
            val pesoSafe   = "$peso $unidad"
            val qrData     = "$nombreSafe|$peso|$unidad|$fecha"

            // ── Etiqueta TSPL 40×30 mm ────────────────────────────────────────
            val nombreCorto = nombreSafe.take(14)
            val tspl = buildString {
                append("SIZE 40 mm,30 mm\n")
                append("GAP 2 mm,0 mm\n")
                append("DIRECTION 0\n")
                append("CLS\n")
                // Título izquierda
                append("TEXT 5,5,\"4\",0,1,1,\"$nombreCorto\"\n")
                // Peso
                append("TEXT 5,35,\"3\",0,2,1,\"$peso\"\n")
                // Unidad
                append("TEXT 5,60,\"3\",0,1,1,\"$unidad\"\n")
                // QR centrado
                append("QRCODE 107,72,H,5,M,0,\"$peso $unidad\"\n")
                // Fecha más abajo (y=215)
                append("TEXT 5,215,\"3\",0,1,1,\"$fecha\"\n")
                append("PRINT 1,1\n")
            }

            val out = socket.outputStream
            out.write(tspl.toByteArray(Charsets.US_ASCII))
            out.flush()
            Log.d(TAG, "TSPL enviado: ${tspl.length} chars ✓")

            Thread.sleep(3000)
            Log.d(TAG, "=== FIN OK ===")
            Result.success(Unit)
        } catch (e: IOException) {
            Log.e(TAG, "IOException: ${e.message}", e)
            Result.failure(Exception("Error BT: ${e.message}"))
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: ${e.message}", e)
            Result.failure(Exception("Sin permiso Bluetooth"))
        } catch (e: Exception) {
            Log.e(TAG, "Exception: ${e.message}", e)
            Result.failure(Exception("Error: ${e.message}"))
        } finally {
            try { socket?.close(); Log.d(TAG, "Socket cerrado") } catch (_: Exception) {}
        }
    }

    // ── Código de Barras Code 128 ─────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    suspend fun imprimirCodigoBarras(
        address: String,
        nombre: String,
        codigo: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var socket: BluetoothSocket? = null
        return@withContext try {
            val adapter = btManager.adapter
                ?: return@withContext Result.failure(Exception("BT no disponible"))
            val device = adapter.getRemoteDevice(address)
            adapter.cancelDiscovery()

            socket = try {
                device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
            } catch (e: Exception) {
                device.createRfcommSocketToServiceRecord(SPP_UUID)
            }
            socket.connect()
            Log.d(TAG, "Barcode: conectado ✓")
            Thread.sleep(500)

            val nombreSafe = nombre.replace("\"", "'").take(14)
            val codigoSafe = codigo.replace("\"", "").replace("\\", "").take(60)

            val tspl = buildString {
                append("SIZE 40 mm,30 mm\n")
                append("GAP 2 mm,0 mm\n")
                append("DIRECTION 0\n")
                append("CLS\n")
                // Nombre del producto arriba
                append("TEXT 5,5,\"4\",0,1,1,\"$nombreSafe\"\n")
            }

            // ── Generar Code 128 directo al tamaño final ───────────────────
            // ZXing escala los módulos y agrega las quiet zones automáticamente
            val bmpW = 240          // dots ancho (~30mm)
            val bmpH = 60           // dots alto (~7mm)
            val hints = mapOf(
                EncodeHintType.MARGIN to 10,            // 10 dots quiet zone cada lado
                EncodeHintType.CHARACTER_SET to "ISO-8859-1"
            )
            val matrix = MultiFormatWriter().encode(
                codigoSafe, BarcodeFormat.CODE_128, bmpW, bmpH, hints
            )
            Log.d(TAG, "Barcode matrix: ${matrix.width}×${matrix.height}")

            // ── Convertir BitMatrix → bytes TSPL BITMAP (POLARIDAD INVERTIDA) ─
            // En esta impresora bit=0=negro, bit=1=blanco → invertir la convención
            val widthBytes = (matrix.width + 7) / 8
            val bitmapData = ByteArray(widthBytes * matrix.height) { 0xFF.toByte() } // inicio: todo blanco
            for (y in 0 until matrix.height) {
                for (x in 0 until matrix.width) {
                    if (matrix.get(x, y)) {          // negro en ZXing → limpiar bit (0=negro)
                        val idx = y * widthBytes + x / 8
                        bitmapData[idx] = (bitmapData[idx].toInt() and (0x80 shr (x % 8)).inv()).toByte()
                    }
                }
            }

            // ── Centrar en etiqueta de 320 dots ────────────────────────────
            val barcodeX = (320 - matrix.width) / 2    // ~10 dots cada lado
            val barcodeY = 70
            val textX    = maxOf(5, (320 - codigoSafe.length * 12) / 2)

            // ── Enviar TSPL ─────────────────────────────────────────────────
            val bitmapCmd = "BITMAP $barcodeX,$barcodeY,$widthBytes,${matrix.height},0,".toByteArray(Charsets.US_ASCII)
            val footer    = "\r\nTEXT $textX,${barcodeY + matrix.height + 5},\"3\",0,1,1,\"$codigoSafe\"\r\nPRINT 1,1\r\n"
                                .toByteArray(Charsets.US_ASCII)

            val out = socket.outputStream
            out.write(tspl.toByteArray(Charsets.US_ASCII))
            out.write(bitmapCmd)
            out.write(bitmapData)
            out.write(footer)
            out.flush()
            Log.d(TAG, "Barcode enviado: widthBytes=$widthBytes h=${matrix.height} x=$barcodeX y=$barcodeY ✓")
            Thread.sleep(3000)
            Result.success(Unit)
        } catch (e: IOException) {
            Log.e(TAG, "Barcode IOException: ${e.message}", e)
            Result.failure(Exception("Error BT: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "Barcode Exception: ${e.message}", e)
            Result.failure(Exception("Error: ${e.message}"))
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    /** Prueba TSPL — protocolo de impresoras de etiquetas ZJiang */
    @SuppressLint("MissingPermission")
    suspend fun pruebaTextoSimple(address: String): Result<String> = withContext(Dispatchers.IO) {
        var socket: BluetoothSocket? = null
        val log = StringBuilder()
        return@withContext try {
            val adapter = btManager.adapter
                ?: return@withContext Result.failure(Exception("BT no disponible"))
            val device = adapter.getRemoteDevice(address)
            adapter.cancelDiscovery()

            log.append("1. Creando socket...\n")
            socket = try {
                device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
            } catch (e: Exception) {
                log.append("   inseguro falló → usando seguro\n")
                device.createRfcommSocketToServiceRecord(SPP_UUID)
            }

            log.append("2. Conectando...\n")
            socket.connect()
            log.append("3. CONECTADO ✓\n")

            Thread.sleep(1500)
            val out = socket.outputStream

            // ── TSPL (protocolo de etiquetas ZJiang) ─────────────────────────
            log.append("4. Enviando TSPL...\n")
            val tspl = buildString {
                append("SIZE 48 mm,30 mm\n")
                append("GAP 2 mm,0 mm\n")
                append("DIRECTION 1\n")
                append("CLS\n")
                append("TEXT 5,10,\"3\",0,1,1,\"PRUEBA IMPRESION\"\n")
                append("TEXT 5,60,\"3\",0,1,1,\"Bascula App OK\"\n")
                append("PRINT 1,1\n")
            }
            val tsplBytes = tspl.toByteArray(Charsets.US_ASCII)
            out.write(tsplBytes)
            out.flush()
            log.append("5. TSPL enviado (${tsplBytes.size} bytes) ✓\n")

            Thread.sleep(5000)

            // ── ESC/POS texto (fallback) ──────────────────────────────────────
            log.append("6. Enviando ESC/POS texto...\n")
            val buf = java.io.ByteArrayOutputStream()
            buf.write(byteArrayOf(0x1B, 0x40))          // ESC @ init
            buf.write(byteArrayOf(0x0A))                // LF
            buf.write("PRUEBA ESCPOS\n".toByteArray())
            buf.write(byteArrayOf(0x0A, 0x0A, 0x0A))    // 3 LF
            buf.write(byteArrayOf(0x1B, 0x4A, 80.toByte())) // ESC J 80
            out.write(buf.toByteArray())
            out.flush()
            log.append("7. ESC/POS enviado ✓\n")

            Thread.sleep(5000)
            log.append("8. Listo — revisa si imprimio algo\n")
            Result.success(log.toString())
        } catch (e: Exception) {
            log.append("ERROR: ${e.javaClass.simpleName}: ${e.message}\n")
            Log.e(TAG, "Prueba error", e)
            Result.failure(Exception(log.toString()))
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    // ── Renderizado del ticket ─────────────────────────────────────────────────

    /**
     * Dibuja el ticket en un Bitmap usando Canvas de Android.
     * Ancho = PRINT_WIDTH px (384 para 58mm).
     */
    private fun renderTicket(
        nombre: String,
        peso: String,
        unidad: String,
        timestamp: Long
    ): Bitmap {
        val fecha = SimpleDateFormat("dd/MM/yyyy  HH:mm", Locale.getDefault()).format(Date(timestamp))
        val w     = PRINT_WIDTH
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Medir altura total primero
        var y     = 20f
        val pad   = 10f

        // Crear bitmap temporal grande
        val tmp = Bitmap.createBitmap(w, w * 4, Bitmap.Config.ARGB_8888)
        val c   = Canvas(tmp)
        c.drawColor(Color.WHITE)

        // ── Nombre empresa (centrado, negrita) ────────────────────────────
        paint.typeface  = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        paint.textSize  = 32f
        paint.color     = Color.BLACK
        paint.textAlign = Paint.Align.CENTER
        c.drawText(nombre.take(24).uppercase(), w / 2f, y + paint.textSize, paint)
        y += paint.textSize + pad

        // ── Separador ─────────────────────────────────────────────────────
        y += 6f
        paint.typeface  = Typeface.MONOSPACE
        paint.textSize  = 22f
        paint.textAlign = Paint.Align.CENTER
        c.drawText("─".repeat(24), w / 2f, y + paint.textSize, paint)
        y += paint.textSize + 6f

        // ── Peso (grande) ─────────────────────────────────────────────────
        paint.typeface  = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        paint.textSize  = 90f
        paint.textAlign = Paint.Align.CENTER
        c.drawText(peso, w / 2f, y + paint.textSize, paint)
        y += paint.textSize + 4f

        // ── Unidad ────────────────────────────────────────────────────────
        paint.typeface  = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        paint.textSize  = 36f
        paint.textAlign = Paint.Align.CENTER
        c.drawText(unidad.uppercase(), w / 2f, y + paint.textSize, paint)
        y += paint.textSize + 10f

        // ── Separador ─────────────────────────────────────────────────────
        paint.typeface  = Typeface.MONOSPACE
        paint.textSize  = 22f
        c.drawText("─".repeat(24), w / 2f, y + paint.textSize, paint)
        y += paint.textSize + 14f

        // ── QR del peso ───────────────────────────────────────────────────
        val qrSize = 200
        try {
            val hints = mapOf(EncodeHintType.MARGIN to 1)
            val matrix = MultiFormatWriter().encode("$peso $unidad", BarcodeFormat.QR_CODE, qrSize, qrSize, hints)
            val qrBmp  = Bitmap.createBitmap(qrSize, qrSize, Bitmap.Config.ARGB_8888)
            for (qx in 0 until qrSize)
                for (qy in 0 until qrSize)
                    qrBmp.setPixel(qx, qy, if (matrix[qx, qy]) Color.BLACK else Color.WHITE)
            val qrX = (w - qrSize) / 2f
            c.drawBitmap(qrBmp, qrX, y, null)
            y += qrSize + 14f
            qrBmp.recycle()
        } catch (_: Exception) { /* Sin QR si falla */ }

        // ── Separador ─────────────────────────────────────────────────────
        paint.typeface  = Typeface.MONOSPACE
        paint.textSize  = 22f
        c.drawText("─".repeat(24), w / 2f, y + paint.textSize, paint)
        y += paint.textSize + 10f

        // ── Fecha y hora ──────────────────────────────────────────────────
        paint.typeface  = Typeface.MONOSPACE
        paint.textSize  = 24f
        paint.textAlign = Paint.Align.CENTER
        c.drawText(fecha, w / 2f, y + paint.textSize, paint)
        y += paint.textSize + 20f

        // Recortar al tamaño real
        val height = y.toInt().coerceAtMost(tmp.height)
        val result = Bitmap.createBitmap(tmp, 0, 0, w, height)
        tmp.recycle()
        return result
    }

    // ── Conversión Bitmap → GS v 0 por filas (= eachLinePixToCmd) ─────────────

    /**
     * Replica exacta de eachLinePixToCmd de la app de referencia.
     * Crea UN comando GS v 0 por fila (yL=1, yH=0).
     * Esto es más compatible con impresoras térmicas baratas.
     */
    private fun bitmapToRasterPerRow(bitmap: Bitmap): ByteArray {
        val w      = bitmap.width
        val h      = bitmap.height
        val wBytes = w / 8            // bytes por fila = 384/8 = 48

        // Por cada fila: 8 bytes cabecera + wBytes datos
        val rowSize = 8 + wBytes
        val result  = ByteArray(h * rowSize)

        for (row in 0 until h) {
            val base = row * rowSize
            // Cabecera GS v 0
            result[base + 0] = 0x1D.toByte()  // GS
            result[base + 1] = 0x76.toByte()  // v
            result[base + 2] = 0x30.toByte()  // 0
            result[base + 3] = 0x00.toByte()  // modo 0
            result[base + 4] = (wBytes and 0xFF).toByte()   // xL
            result[base + 5] = (wBytes shr 8  and 0xFF).toByte() // xH
            result[base + 6] = 0x01.toByte()  // yL = 1 fila
            result[base + 7] = 0x00.toByte()  // yH = 0

            // Datos: 8 píxeles → 1 byte (MSB primero = pixel más a la izquierda)
            for (col in 0 until wBytes) {
                var byte = 0
                for (bit in 0 until 8) {
                    val px  = col * 8 + bit
                    if (px < w) {
                        val pixel = bitmap.getPixel(px, row)
                        val lum   = Color.red(pixel) * 0.299 +
                                    Color.green(pixel) * 0.587 +
                                    Color.blue(pixel) * 0.114
                        if (lum < 128.0) byte = byte or (1 shl (7 - bit))
                    }
                }
                result[base + 8 + col] = byte.toByte()
            }
        }
        return result
    }

    // ── Ticket de texto ESC/POS (sin bitmap) ──────────────────────────────────

    /**
     * Ticket de texto puro — máxima compatibilidad con impresoras ESC/POS baratas.
     * Usa codificación GBK igual que la app de referencia.
     */
    private fun buildTicketTexto(
        out: OutputStream,
        nombre: String,
        peso: String,
        unidad: String,
        timestamp: Long
    ) {
        val fecha = SimpleDateFormat("dd/MM/yyyy  HH:mm", Locale.getDefault()).format(Date(timestamp))

        fun cmd(vararg b: Int) = out.write(b.map { it.toByte() }.toByteArray())
        fun txt(s: String) { out.write(s.toByteArray(Charsets.UTF_8)); out.write(0x0A) }

        // Centrar
        cmd(0x1B, 0x61, 0x01)
        // Negrita ON
        cmd(0x1B, 0x45, 0x01)
        txt(nombre.take(32).uppercase())
        // Negrita OFF
        cmd(0x1B, 0x45, 0x00)

        txt("--------------------------------")

        // Peso en tamaño GRANDE (doble alto + ancho)
        cmd(0x1D, 0x21, 0x11)
        cmd(0x1B, 0x45, 0x01)
        txt("$peso")
        cmd(0x1B, 0x45, 0x00)

        // Unidad tamaño normal
        cmd(0x1D, 0x21, 0x01)
        txt(unidad.uppercase())
        cmd(0x1D, 0x21, 0x00)

        txt("--------------------------------")

        // Fecha
        txt(fecha)
    }
}
