package com.example.basculaserial

import android.view.KeyEvent

/**
 * Detecta entrada de lector de código de barras USB HID (ej. Steren COM-595).
 *
 * Los lectores HID envían todos los caracteres en ráfagas muy rápidas (< 50 ms entre teclas)
 * seguidas de un ENTER. El teclado humano normal es más lento (> 150 ms).
 *
 * Cuando detecta un código completo, llama [onCodigo].
 */
class BarcodeInputHandler(
    private val onCodigo: (codigo: String) -> Unit
) {
    private val buffer = StringBuilder()
    private var ultimaTeclaMs = 0L

    /** Máximo tiempo entre caracteres de un mismo código (ms) */
    private val TIMEOUT_MS = 120L

    /**
     * Procesar un KeyEvent de la Activity.
     * @return true si el evento fue consumido (no pasar a la vista)
     */
    fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false

        val ahora = System.currentTimeMillis()

        // Si pasó mucho tiempo desde el último carácter, reset del buffer
        if (ahora - ultimaTeclaMs > TIMEOUT_MS && buffer.isNotEmpty()) {
            buffer.clear()
        }
        ultimaTeclaMs = ahora

        return when (event.keyCode) {
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                val codigo = buffer.toString().trim()
                buffer.clear()
                if (codigo.isNotEmpty()) {
                    onCodigo(codigo)
                    true // consumido
                } else false
            }
            KeyEvent.KEYCODE_DEL -> {
                if (buffer.isNotEmpty()) buffer.deleteCharAt(buffer.length - 1)
                false
            }
            else -> {
                val char = event.unicodeChar
                if (char != 0) {
                    buffer.append(char.toChar())
                }
                false // no consumimos — dejar pasar por si hay un EditText con foco
            }
        }
    }
}
