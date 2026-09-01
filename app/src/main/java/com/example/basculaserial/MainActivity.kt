package com.example.basculaserial

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.basculaserial.theme.BasculaSerialTheme

class MainActivity : ComponentActivity() {

    private val viewModel: BasculaViewModel by viewModels()

    // Manejador de lectura de código de barras (HID keyboard emulation)
    private val barcodeHandler = BarcodeInputHandler { codigo ->
        runOnUiThread { viewModel.onCodigoEscaneado(codigo) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BasculaSerialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BasculaScreen(viewModel = viewModel)
                }
            }
        }
    }

    /**
     * Intercepta TODOS los eventos de teclado antes de que lleguen a las vistas.
     * El lector Steren COM-595 llega aquí como HID keyboard.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (barcodeHandler.onKeyEvent(event)) return true
        return super.dispatchKeyEvent(event)
    }
}
