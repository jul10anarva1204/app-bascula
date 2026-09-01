package com.example.basculaserial.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Paleta profesional clara — diseñada para uso diurno en planta/bodega
private val LightColorScheme = lightColorScheme(
    primary          = Color(0xFF1565C0),   // Azul industrial
    onPrimary        = Color.White,
    primaryContainer = Color(0xFFBBDEFB),
    onPrimaryContainer = Color(0xFF001D36),
    secondary        = Color(0xFF00695C),   // Teal
    onSecondary      = Color.White,
    secondaryContainer = Color(0xFFB2DFDB),
    background       = Color(0xFFF0F4F8),   // Gris-azulado muy claro
    onBackground     = Color(0xFF111928),
    surface          = Color(0xFFFFFFFF),   // Blanco puro para tarjetas
    onSurface        = Color(0xFF111928),
    surfaceVariant   = Color(0xFFECEFF1),
    onSurfaceVariant = Color(0xFF546E7A),
    error            = Color(0xFFC62828),
    onError          = Color.White,
    outline          = Color(0xFFCFD8DC)
)

@Composable
fun BasculaSerialTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography  = Typography,
        content     = content
    )
}
