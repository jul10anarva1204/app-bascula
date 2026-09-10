package com.example.basculaserial

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Colores (mismos que BasculaScreen) ──────────────────────────────────────
private val CB_Azul       = Color(0xFF1565C0)
private val CB_AzulClaro  = Color(0xFF1E88E5)
private val CB_Fondo      = Color(0xFFF0F4F8)
private val CB_Superficie = Color(0xFFFFFFFF)
private val CB_TextoOscuro= Color(0xFF111928)
private val CB_TextoMedio = Color(0xFF546E7A)
private val CB_TextoSuave = Color(0xFF90A4AE)
private val CB_Borde      = Color(0xFFCFD8DC)
private val CB_Verde      = Color(0xFF2E7D32)
private val CB_Rojo       = Color(0xFFC62828)

@Composable
fun CodigoBarrasScreen(
    viewModel: BasculaViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    var descripcion by rememberSaveable { mutableStateOf("") }
    var codigo      by rememberSaveable { mutableStateOf("") }

    val tienePrinter = uiState.selectedPrinterAddress.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CB_Fondo)
    ) {
        // ── TOP BAR ─────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CB_Azul)
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Regresar", tint = Color.White)
            }
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Códigos de Barras",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text("Code 128", color = Color.White.copy(0.8f), fontSize = 12.sp)
            }
        }

        // ── CONTENIDO ───────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Aviso si no hay impresora
            if (!tienePrinter) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "⚠ Sin impresora seleccionada. Ve a Ajustes para configurar la impresora Bluetooth.",
                        modifier = Modifier.padding(12.dp),
                        color = Color(0xFFE65100),
                        fontSize = 13.sp
                    )
                }
            }

            // ── FORMULARIO ──────────────────────────────────────────────────
            Card(
                colors = CardDefaults.cardColors(containerColor = CB_Superficie),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        "Datos de la etiqueta",
                        fontWeight = FontWeight.SemiBold,
                        color = CB_TextoOscuro,
                        fontSize = 15.sp
                    )

                    // Descripción / nombre del producto
                    OutlinedTextField(
                        value = descripcion,
                        onValueChange = { if (it.length <= 14) descripcion = it },
                        label = { Text("Descripción (máx. 14 chars)") },
                        placeholder = { Text("ej. PRODUCTO A") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters
                        ),
                        supportingText = { Text("${descripcion.length}/14", color = CB_TextoSuave) }
                    )

                    // Código de barras
                    OutlinedTextField(
                        value = codigo,
                        onValueChange = { if (it.length <= 60) codigo = it.filter { c -> c.code in 32..126 } },
                        label = { Text("Código de barras (Code 128)") },
                        placeholder = { Text("ej. 1234567890123") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        supportingText = { Text("Solo caracteres ASCII — ${codigo.length}/60 chars", color = CB_TextoSuave) }
                    )
                }
            }

            // ── VISTA PREVIA ─────────────────────────────────────────────────
            Card(
                colors = CardDefaults.cardColors(containerColor = CB_Superficie),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Vista previa — etiqueta 40×30mm",
                        fontWeight = FontWeight.SemiBold,
                        color = CB_TextoOscuro,
                        fontSize = 15.sp
                    )

                    // Simulación visual de la etiqueta
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White)
                            .border(1.5.dp, CB_Borde, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Nombre
                            Text(
                                text = descripcion.ifBlank { "NOMBRE PRODUCTO" },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (descripcion.isBlank()) CB_TextoSuave else CB_TextoOscuro
                            )

                            // Representación visual del código de barras
                            if (codigo.isNotBlank()) {
                                BarrasVisuales(modifier = Modifier.fillMaxWidth().height(60.dp))
                                Text(
                                    text = codigo,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = CB_TextoOscuro,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(70.dp)
                                        .border(1.dp, CB_Borde, RoundedCornerShape(4.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("[ Código de barras Code 128 ]", color = CB_TextoSuave, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // ── RESULTADO ────────────────────────────────────────────────────
            AnimatedVisibility(
                visible = uiState.barcodeResult.isNotEmpty(),
                enter = fadeIn(), exit = fadeOut()
            ) {
                val esError = uiState.barcodeResult.startsWith("✗")
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (esError) CB_Rojo.copy(0.1f) else CB_Verde.copy(0.1f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = uiState.barcodeResult,
                        modifier = Modifier.padding(14.dp),
                        color = if (esError) CB_Rojo else CB_Verde,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }

            // ── BOTÓN IMPRIMIR ───────────────────────────────────────────────
            Button(
                onClick = { viewModel.imprimirCodigoBarras(descripcion, codigo) },
                enabled = !uiState.barcodePrinting && codigo.isNotBlank() && tienePrinter,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CB_Azul)
            ) {
                if (uiState.barcodePrinting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Imprimiendo...", color = Color.White, fontSize = 15.sp)
                } else {
                    Icon(Icons.Default.Print, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Imprimir Código de Barras", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Dibuja barras verticales aleatorias simulando un código de barras */
@Composable
private fun BarrasVisuales(modifier: Modifier = Modifier) {
    val widths = remember {
        buildList {
            repeat(40) { add(if (Math.random() > 0.5) 2 else 4) }
        }
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        widths.forEachIndexed { i, w ->
            Box(
                modifier = Modifier
                    .width(w.dp)
                    .fillMaxHeight()
                    .background(if (i % 2 == 0) Color.Black else Color.White)
            )
        }
    }
}
