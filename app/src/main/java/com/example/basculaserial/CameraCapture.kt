package com.example.basculaserial

import android.content.Context
import android.graphics.Bitmap
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import android.view.TextureView
import com.jiangdg.ausbc.MultiCameraClient
import com.jiangdg.ausbc.callback.ICameraStateCallBack
import com.jiangdg.ausbc.callback.IDeviceConnectCallBack
import com.jiangdg.ausbc.camera.bean.CameraRequest
import com.jiangdg.ausbc.widget.IAspectRatio
import com.serenegiant.usb.USBMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.ref.WeakReference

/**
 * Captura de fotos desde la Rapoo C500 (USB UVC) usando libausbc 3.2.7.
 *
 * Captura de imagen: usa TextureView.getBitmap() directamente sobre el
 * stream renderizado — más simple y confiable que captureImage() de la librería.
 */
class CameraCapture(private val context: Context) {

    companion object {
        private const val TAG = "BASCULA_CAM"
    }

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private var multiCameraClient: MultiCameraClient? = null
    private var camera: MultiCameraClient.Camera? = null
    private var textureViewRef: WeakReference<TextureView>? = null

    // ── Estado ────────────────────────────────────────────────────────────────

    fun hayCaramaUsb(): Boolean =
        usbManager.deviceList.values.any { esUvc(it) }

    /** Hay cámara disponible si el objeto Camera fue creado (permiso USB concedido) */
    fun hayCamara(): Boolean = camera != null

    // ── Preview en tiempo real ────────────────────────────────────────────────

    fun startPreview(view: IAspectRatio) {
        if (multiCameraClient != null) return

        // Guardar referencia al TextureView para capturar bitmap directo
        if (view is TextureView) {
            textureViewRef = WeakReference(view)
            Log.i(TAG, "TextureView guardado para captura de bitmap")
        }

        // Listar dispositivos USB disponibles
        val allDevices = usbManager.deviceList
        Log.i(TAG, "=== Dispositivos USB: ${allDevices.size} ===")
        allDevices.values.forEach { dev ->
            Log.i(TAG, "  ${dev.productName} VID=0x${dev.vendorId.toString(16)} " +
                    "class=${dev.deviceClass} esUvc=${esUvc(dev)} permiso=${usbManager.hasPermission(dev)}")
        }

        val client = MultiCameraClient(context, object : IDeviceConnectCallBack {

            override fun onAttachDev(device: UsbDevice?) {
                Log.i(TAG, "onAttachDev: ${device?.productName} class=${device?.deviceClass}")
                device ?: return
                if (esUvc(device)) {
                    Log.i(TAG, "  → UVC detectado, pidiendo permiso")
                    multiCameraClient?.requestPermission(device)
                }
            }

            override fun onDetachDec(device: UsbDevice?) {
                Log.i(TAG, "onDetachDec: ${device?.productName}")
                camera?.closeCamera()
                camera = null
            }

            override fun onConnectDev(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
                Log.i(TAG, "onConnectDev: ${device?.productName} ctrlBlock=${ctrlBlock != null}")
                device ?: return

                val cam = MultiCameraClient.Camera(context, device)
                camera = cam

                if (ctrlBlock != null) {
                    cam.setUsbControlBlock(ctrlBlock)
                    Log.i(TAG, "  → UsbControlBlock asignado")
                } else {
                    Log.w(TAG, "  ⚠ ctrlBlock es null")
                }

                cam.setCameraStateCallBack(object : ICameraStateCallBack {
                    override fun onCameraState(
                        self: MultiCameraClient.Camera,
                        code: ICameraStateCallBack.State,
                        msg: String?
                    ) {
                        Log.i(TAG, "onCameraState: $code msg=$msg")
                    }
                })

                val request = CameraRequest.Builder()
                    .setPreviewWidth(1280)
                    .setPreviewHeight(720)
                    .create()

                Log.i(TAG, "  → openCamera(1280x720)")
                cam.openCamera(view, request)
            }

            override fun onDisConnectDec(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
                Log.i(TAG, "onDisConnectDec: ${device?.productName}")
                camera = null
            }

            override fun onCancelDev(device: UsbDevice?) {
                Log.w(TAG, "onCancelDev: permiso denegado para ${device?.productName}")
            }
        })

        multiCameraClient = client
        client.register()
        Log.i(TAG, "MultiCameraClient registrado")

        // Si ya tiene permiso, abrir inmediatamente
        val connected = usbManager.deviceList.values.firstOrNull { esUvc(it) }
        if (connected != null) {
            Log.i(TAG, "Dispositivo UVC ya presente: ${connected.productName}")
            client.requestPermission(connected)
        }
    }

    fun stopPreview() {
        try { camera?.closeCamera()           } catch (_: Exception) {}
        try { multiCameraClient?.unRegister() } catch (_: Exception) {}
        camera = null
        multiCameraClient = null
        textureViewRef = null
    }

    fun release() = stopPreview()

    // ── Captura de foto ───────────────────────────────────────────────────────

    /**
     * Captura el frame actual del TextureView como JPEG.
     * Más confiable que captureImage() de la librería.
     */
    suspend fun captureStill(nombreArchivo: String): String? {
        Log.i(TAG, "captureStill: hayCamara=${hayCamara()} texView=${textureViewRef?.get() != null}")
        val tv = textureViewRef?.get() ?: run {
            Log.e(TAG, "captureStill: textureView es null"); return null
        }

        val dir  = File(context.filesDir, "fotos").also { it.mkdirs() }
        val file = File(dir, "$nombreArchivo.jpg")

        return try {
            // getBitmap() debe llamarse en hilo principal
            val bitmap: Bitmap? = withContext(Dispatchers.Main) { tv.getBitmap() }
            if (bitmap == null) {
                Log.e(TAG, "captureStill: getBitmap() retornó null"); return null
            }
            Log.i(TAG, "captureStill: bitmap ${bitmap.width}x${bitmap.height}")

            withContext(Dispatchers.IO) {
                file.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
                bitmap.recycle()
                Log.i(TAG, "captureStill: guardado en ${file.absolutePath}")
                file.absolutePath
            }
        } catch (e: Exception) {
            Log.e(TAG, "captureStill error: ${e.message}")
            null
        }
    }

    // ── Identificación UVC ────────────────────────────────────────────────────

    private fun esUvc(device: UsbDevice): Boolean {
        if (device.deviceClass == 14 || device.deviceClass == 239) return true
        for (i in 0 until device.interfaceCount)
            if (device.getInterface(i).interfaceClass == 14) return true
        return false
    }
}
