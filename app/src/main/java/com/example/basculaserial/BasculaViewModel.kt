package com.example.basculaserial

import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.basculaserial.data.BasculaDatabase
import com.example.basculaserial.data.PrefsManager
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "BasculaVM"
private const val ACTION_USB_PERMISSION         = "com.example.basculaserial.USB_PERMISSION"
private const val ACTION_PRINTER_PERMISSION     = "com.example.basculaserial.PRINTER_PERMISSION"
private const val INTERVALO_LECTURA_MS          = 500L
private const val LECTURAS_PARA_ESTABILIZAR     = 4
private const val TOLERANCIA_KG                 = 0.002

enum class ConexionEstado { DESCONECTADO, SOLICITANDO_PERMISO, CONECTADO, ERROR }

data class BasculaUiState(
    // Báscula
    val conexion: ConexionEstado = ConexionEstado.DESCONECTADO,
    val peso: String = "---",
    val unidad: String = "kg",
    val estable: Boolean = false,
    val mensajeError: String = "",
    val dispositivoNombre: String = "",
    // Tara
    val tara: Double = 0.0,
    val taraActiva: Boolean = false,
    // Guardado
    val guardando: Boolean = false,
    val ultimoGuardado: String = "",
    // Impresora
    val printing: Boolean = false,
    val printResult: String = "",
    val selectedPrinterVendorId: Int = -1,
    val selectedPrinterProductId: Int = -1,
    val selectedPrinterName: String = "",
    // Cámara
    val hayCaramaUsb: Boolean = false,
    val camaraCapturando: Boolean = false,
    val camaraPermiso: Boolean = false,
    // Configuración
    val productName: String = "MI EMPRESA",
    // Escaner de código de barras
    val codigoEscaneado: String = ""   // último código leído (se limpia automáticamente)
) {
    /** Peso neto = bruto − tara (lo que se guarda e imprime) */
    val pesoNeto: String get() {
        if (!taraActiva || peso == "---") return peso
        val bruto = peso.replace(",", ".").toDoubleOrNull() ?: return peso
        val neto  = bruto - tara
        return String.format("%.3f", neto)
    }
    val taraStr: String get() = String.format("%.3f", tara)
}

class BasculaViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(BasculaUiState())
    val uiState: StateFlow<BasculaUiState> = _uiState.asStateFlow()

    private val bufferLecturas = ArrayDeque<Double>(LECTURAS_PARA_ESTABILIZAR)

    private val db             = BasculaDatabase.getInstance(application)
    private val prefs          = PrefsManager(application)
    private val printerManager = PrinterManager(application)
    val cameraCapture  = CameraCapture(application)  // public para preview en UI

    val registros = db.registros

    private val _impresoras = MutableStateFlow<List<UsbImpresoraInfo>>(emptyList())
    val impresoras: StateFlow<List<UsbImpresoraInfo>> = _impresoras.asStateFlow()

    private val usbManager = application.getSystemService(Context.USB_SERVICE) as UsbManager
    private var serialPort: UsbSerialPort? = null
    private var scalePendingIntent: PendingIntent? = null
    private var printerPendingIntent: PendingIntent? = null
    private var autoLecturaJob: Job? = null

    // ── Receiver: permiso báscula ────────────────────────────────────────────
    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION != intent.action) return
            val device: UsbDevice? = getDevice(intent)
            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) && device != null)
                conectarDispositivo(device)
            else
                _uiState.update { it.copy(conexion = ConexionEstado.ERROR, mensajeError = "Permiso USB denegado") }
        }
    }

    // ── Receiver: permiso impresora ──────────────────────────────────────────
    private val printerPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_PRINTER_PERMISSION != intent.action) return
            val device: UsbDevice? = getDevice(intent)
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            if (granted && device != null) {
                prefs.printerVendorId  = device.vendorId
                prefs.printerProductId = device.productId
                _uiState.update { it.copy(
                    selectedPrinterVendorId  = device.vendorId,
                    selectedPrinterProductId = device.productId
                )}
            }
        }
    }

    // ── Receiver: desconexión USB ────────────────────────────────────────────
    private val usbDetachReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (UsbManager.ACTION_USB_DEVICE_DETACHED != intent.action) return
            // Actualizar disponibilidad de cámara USB
            _uiState.update { it.copy(hayCaramaUsb = cameraCapture.hayCaramaUsb()) }
            // Si era la báscula, desconectar
            val device: UsbDevice? = getDevice(intent)
            if (device != null &&
                device.vendorId != _uiState.value.selectedPrinterVendorId &&
                device.productId != _uiState.value.selectedPrinterProductId) {
                detenerAutoLectura()
                cerrarPuerto()
                bufferLecturas.clear()
                _uiState.update {
                    it.copy(conexion = ConexionEstado.DESCONECTADO, peso = "---",
                        estable = false, mensajeError = "", dispositivoNombre = "")
                }
            }
        }
    }

    // ── Receiver: conexión USB nueva ─────────────────────────────────────────
    private val usbAttachReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED != intent.action) return
            // Re-verificar si la cámara fue conectada
            _uiState.update { it.copy(hayCaramaUsb = cameraCapture.hayCaramaUsb()) }
        }
    }

    init {
        _uiState.update {
            it.copy(
                productName              = prefs.productName,
                selectedPrinterVendorId  = prefs.printerVendorId,
                selectedPrinterProductId = prefs.printerProductId,
                selectedPrinterName      = prefs.printerName,
                hayCaramaUsb             = cameraCapture.hayCaramaUsb()
            )
        }
        registrarReceivers()
        buscarYConectar()
    }

    private fun registrarReceivers() {
        val app   = getApplication<Application>()
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT

        scalePendingIntent   = PendingIntent.getBroadcast(app, 0, Intent(ACTION_USB_PERMISSION), flags)
        printerPendingIntent = PendingIntent.getBroadcast(app, 1, Intent(ACTION_PRINTER_PERMISSION), flags)

        fun register(receiver: BroadcastReceiver, filter: IntentFilter) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            else app.registerReceiver(receiver, filter)
        }
        register(usbPermissionReceiver,    IntentFilter(ACTION_USB_PERMISSION))
        register(printerPermissionReceiver, IntentFilter(ACTION_PRINTER_PERMISSION))
        register(usbDetachReceiver,        IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED))
        register(usbAttachReceiver,        IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED))
    }

    // ── Báscula USB ──────────────────────────────────────────────────────────
    fun buscarYConectar() {
        viewModelScope.launch(Dispatchers.IO) {
            val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
            if (drivers.isEmpty()) {
                _uiState.update { it.copy(conexion = ConexionEstado.DESCONECTADO,
                    mensajeError = "No se encontró dispositivo USB serial") }
                return@launch
            }
            val device = drivers.first().device
            if (usbManager.hasPermission(device)) conectarDispositivo(device)
            else {
                _uiState.update { it.copy(conexion = ConexionEstado.SOLICITANDO_PERMISO) }
                usbManager.requestPermission(device, scalePendingIntent)
            }
        }
    }

    private fun conectarDispositivo(device: UsbDevice) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val drivers    = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
                val driver     = drivers.find { it.device == device } ?: return@launch
                val connection = usbManager.openDevice(device) ?: run {
                    _uiState.update { it.copy(conexion = ConexionEstado.ERROR,
                        mensajeError = "No se pudo abrir conexión USB") }
                    return@launch
                }
                val port = driver.ports.first()
                port.open(connection)
                port.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                serialPort = port
                val nombre = "${device.manufacturerName ?: "USB Serial"} ${device.productName ?: ""}".trim()
                _uiState.update { it.copy(conexion = ConexionEstado.CONECTADO,
                    mensajeError = "", dispositivoNombre = nombre) }
                iniciarAutoLectura()
            } catch (e: Exception) {
                Log.e(TAG, "Error al conectar", e)
                _uiState.update { it.copy(conexion = ConexionEstado.ERROR,
                    mensajeError = "Error: ${e.message}") }
            }
        }
    }

    private fun iniciarAutoLectura() {
        detenerAutoLectura()
        autoLecturaJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val port = serialPort ?: break
                if (_uiState.value.conexion != ConexionEstado.CONECTADO) break
                try {
                    port.write("P\r\n".toByteArray(Charsets.US_ASCII), 1000)
                    val buffer = ByteArray(64)
                    val sb     = StringBuilder()
                    val t0     = System.currentTimeMillis()
                    while (System.currentTimeMillis() - t0 < 1000) {
                        val n = port.read(buffer, 300)
                        if (n > 0) { sb.append(String(buffer, 0, n, Charsets.US_ASCII)); break }
                    }
                    val resp = sb.toString().trim()
                    if (resp.isNotEmpty()) {
                        val (peso, unidad) = parsearPeso(resp)
                        val estable = verificarEstabilidad(peso)
                        _uiState.update { it.copy(peso = peso, unidad = unidad,
                            estable = estable, mensajeError = "") }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error lectura", e)
                    _uiState.update { it.copy(mensajeError = "Error de lectura: ${e.message}") }
                    delay(3000)
                }
                delay(INTERVALO_LECTURA_MS)
            }
        }
    }

    private fun detenerAutoLectura() { autoLecturaJob?.cancel(); autoLecturaJob = null }

    // ── Guardar + Imprimir + Fotografiar ─────────────────────────────
    fun guardarPeso(codigoBarras: String? = null) {
        val estado = _uiState.value
        if (estado.peso == "---" || estado.peso.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(guardando = true) }
            val ts = System.currentTimeMillis()

            // Peso neto = bruto − tara (si tara activa)
            val pesoNeto = estado.pesoNeto

            // 1. Capturar foto si hay cámara USB activa
            var fotoRuta: String? = null
            if (cameraCapture.hayCamara()) {
                _uiState.update { it.copy(camaraCapturando = true) }
                fotoRuta = cameraCapture.captureStill("foto_$ts")
                _uiState.update { it.copy(camaraCapturando = false) }
            }

            // 2. Guardar en base de datos con desglose de tara
            val pesoBrutoStr = if (estado.taraActiva) estado.peso else null
            val taraStr      = if (estado.taraActiva) estado.taraStr else null
            withContext(Dispatchers.IO) {
                db.insertar(
                    pesoNeto     = pesoNeto,
                    unidad       = estado.unidad,
                    pesoBruto    = pesoBrutoStr,
                    tara         = taraStr,
                    fotoRuta     = fotoRuta,
                    codigoBarras = codigoBarras
                )
            }


            _uiState.update { it.copy(guardando = false,
                ultimoGuardado = "$pesoNeto ${estado.unidad}") }

            // 3. Imprimir etiqueta con peso NETO
            if (estado.selectedPrinterVendorId != -1) {
                imprimir(pesoNeto, estado.unidad, estado.productName,
                    estado.selectedPrinterVendorId, estado.selectedPrinterProductId, ts)
            }

            delay(3000)
            _uiState.update { it.copy(ultimoGuardado = "") }
        }
    }

    private fun imprimir(peso: String, unidad: String, nombre: String,
                         vid: Int, pid: Int, timestamp: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(printing = true, printResult = "") }
            val result = printerManager.imprimirEtiqueta(vid, pid, nombre, peso, unidad, timestamp)
            _uiState.update { it.copy(printing = false,
                printResult = if (result.isSuccess) "ok"
                else "error:${result.exceptionOrNull()?.message}") }
            delay(4000)
            _uiState.update { it.copy(printResult = "") }
        }
    }

    // ── Escaner código de barras ────────────────────────────
    /**
     * Llamado cuando el lector USB HID (Steren COM-595) envía un código.
     * Muestra el código en UI y dispara el flujo completo: foto + guardar + imprimir.
     */
    fun onCodigoEscaneado(codigo: String) {
        _uiState.update { it.copy(codigoEscaneado = codigo) }
        guardarPeso(codigoBarras = codigo)  // foto + BD (con código) + impresora
        viewModelScope.launch {
            delay(4000)
            _uiState.update { it.copy(codigoEscaneado = "") }
        }
    }

    // ── Tara ──────────────────────────────────────────────────────
    /** Establece el peso actual como tara */
    fun setTara() {
        val estado = _uiState.value
        val pesoActual = estado.peso.replace(",", ".").toDoubleOrNull() ?: return
        _uiState.update { it.copy(tara = pesoActual, taraActiva = true) }
    }

    /** Limpia la tara (vuelve a peso bruto) */
    fun limpiarTara() {
        _uiState.update { it.copy(tara = 0.0, taraActiva = false) }
    }

    // ── Cámara ─────────────────────────────────────────────────
    /** Llamar cuando el usuario otorga/niega el permiso de cámara */
    fun actualizarPermisoCarama(otorgado: Boolean) {
        _uiState.update { it.copy(camaraPermiso = otorgado) }
    }

    /** Re-verificar si la cámara USB sigue conectada */
    fun verificarCaramaUsb() {
        _uiState.update { it.copy(hayCaramaUsb = cameraCapture.hayCaramaUsb()) }
    }

    // ── Impresora USB ────────────────────────────────────────────────────────
    fun cargarImpresoras() {
        _impresoras.value = printerManager.getDispositivosConectados()
    }

    fun seleccionarImpresora(vendorId: Int, productId: Int, nombre: String) {
        prefs.printerVendorId  = vendorId
        prefs.printerProductId = productId
        prefs.printerName      = nombre
        _uiState.update { it.copy(
            selectedPrinterVendorId  = vendorId,
            selectedPrinterProductId = productId,
            selectedPrinterName      = nombre
        )}
        val device = printerManager.encontrarDispositivo(vendorId, productId)
        if (device != null && !printerManager.tienePermiso(device)) {
            usbManager.requestPermission(device, printerPendingIntent)
        }
    }

    fun limpiarSeleccionImpresora() {
        prefs.printerVendorId  = -1
        prefs.printerProductId = -1
        prefs.printerName      = ""
        _uiState.update { it.copy(selectedPrinterVendorId = -1,
            selectedPrinterProductId = -1, selectedPrinterName = "") }
    }

    // ── Configuración ────────────────────────────────────────────────────────
    fun actualizarNombreProducto(nombre: String) {
        prefs.productName = nombre
        _uiState.update { it.copy(productName = nombre) }
    }

    // ── DB ───────────────────────────────────────────────────────────────────
    fun eliminarRegistro(id: Long) = viewModelScope.launch(Dispatchers.IO) { db.eliminarPorId(id) }
    fun eliminarTodo()              = viewModelScope.launch(Dispatchers.IO) { db.eliminarTodos() }

    // ── Helpers ──────────────────────────────────────────────────────────────
    private fun verificarEstabilidad(pesoTexto: String): Boolean {
        val valor = pesoTexto.toDoubleOrNull() ?: return false
        if (bufferLecturas.size >= LECTURAS_PARA_ESTABILIZAR) bufferLecturas.removeFirst()
        bufferLecturas.addLast(valor)
        if (bufferLecturas.size < LECTURAS_PARA_ESTABILIZAR) return false
        return (bufferLecturas.max() - bufferLecturas.min()) <= TOLERANCIA_KG
    }

    private fun parsearPeso(respuesta: String): Pair<String, String> {
        val limpio = respuesta.replace(Regex("[\\r\\n\\t]"), " ").trim()
        val regex  = Regex("([+-]?\\d+[.,]\\d+|[+-]?\\d+)\\s*(kg|g|lb|oz)?", RegexOption.IGNORE_CASE)
        val match  = regex.find(limpio) ?: return Pair(limpio.take(10), "")
        val valor  = match.groupValues[1].replace(",", ".")
        val unidad = match.groupValues[2].lowercase().ifEmpty { "kg" }
        val numero = valor.toDoubleOrNull()
        return if (numero != null)
            Pair(if (unidad == "g") "%.0f".format(numero) else "%.3f".format(numero), unidad)
        else Pair(valor, unidad)
    }

    private fun getDevice(intent: Intent): UsbDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)

    private fun cerrarPuerto() {
        try { serialPort?.close() } catch (e: Exception) { Log.e(TAG, "Error cerrando puerto", e) }
        serialPort = null
    }

    override fun onCleared() {
        super.onCleared()
        detenerAutoLectura()
        cerrarPuerto()
        cameraCapture.release()
        listOf(usbPermissionReceiver, printerPermissionReceiver,
               usbDetachReceiver, usbAttachReceiver).forEach {
            try { getApplication<Application>().unregisterReceiver(it) }
            catch (e: Exception) { Log.e(TAG, "Error desregistrando receiver", e) }
        }
    }
}
