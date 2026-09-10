package com.example.basculaserial

import android.Manifest
import android.os.Build
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.UsbOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.basculaserial.data.RegistroPeso
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Paleta profesional clara ─────────────────────────────────────────────────
private val Azul        = Color(0xFF1565C0)
private val AzulClaro   = Color(0xFF1E88E5)
private val Fondo       = Color(0xFFF0F4F8)
private val Superficie  = Color(0xFFFFFFFF)
private val TextoOscuro = Color(0xFF111928)
private val TextoMedio  = Color(0xFF546E7A)
private val TextoSuave  = Color(0xFF90A4AE)
private val Borde       = Color(0xFFCFD8DC)
private val Rojo        = Color(0xFFC62828)
private val Ambar       = Color(0xFFF59E0B)
private val Verde       = Color(0xFF1B5E20)
private val VerdeClaro  = Color(0xFF2E7D32)

private data class EstadoBadge(val color: Color, val texto: String, val icono: ImageVector)
private val fmtFecha = SimpleDateFormat("dd/MM/yyyy  HH:mm:ss", Locale.getDefault())

// ── Pantalla principal ───────────────────────────────────────────────────────
@Composable
fun BasculaScreen(viewModel: BasculaViewModel = viewModel()) {
    val uiState    by viewModel.uiState.collectAsState()
    val registros  by viewModel.registros.collectAsState()
    val impresoras by viewModel.impresoras.collectAsState()

    var mostrarAjustes by rememberSaveable { mutableStateOf(false) }

    // ── Permiso de cámara ────────────────────────────────────────────────────
    val camaraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { otorgado ->
        viewModel.actualizarPermisoCarama(otorgado)
    }

    // Solicitar permiso al arrancar si hay cámara USB conectada
    LaunchedEffect(uiState.hayCaramaUsb) {
        if (uiState.hayCaramaUsb) {
            camaraPermLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // ── Permiso Bluetooth (Android 12+) ──────────────────────────────────
    val btPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> viewModel.cargarImpresoras() }

    // Cargar lista de impresoras BT al abrir ajustes (pidiendo permiso si falta)
    LaunchedEffect(mostrarAjustes) {
        if (mostrarAjustes) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                btPermLauncher.launch(arrayOf(
                    android.Manifest.permission.BLUETOOTH_CONNECT,
                    android.Manifest.permission.BLUETOOTH_SCAN
                ))
            } else {
                viewModel.cargarImpresoras()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Fondo)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── HEADER ──────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Azul),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Scale, null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(uiState.productName, color = TextoOscuro, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Báscula Serial", color = TextoMedio, fontSize = 12.sp)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Badge cámara
                        if (uiState.hayCaramaUsb) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        if (uiState.camaraPermiso) VerdeClaro.copy(0.1f)
                                        else Ambar.copy(0.1f)
                                    )
                                    .border(1.dp,
                                        if (uiState.camaraPermiso) VerdeClaro.copy(0.3f)
                                        else Ambar.copy(0.3f),
                                        RoundedCornerShape(20.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .clickable {
                                        if (!uiState.camaraPermiso)
                                            camaraPermLauncher.launch(Manifest.permission.CAMERA)
                                    }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CameraAlt, null,
                                        tint = if (uiState.camaraPermiso) VerdeClaro else Ambar,
                                        modifier = Modifier.size(12.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        if (uiState.camaraPermiso) "Cámara" else "Cámara ⚠",
                                        color = if (uiState.camaraPermiso) VerdeClaro else Ambar,
                                        fontSize = 10.sp, fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                        EstadoBadgeChip(uiState)
                        IconButton(onClick = { mostrarAjustes = true }) {
                            Icon(Icons.Default.Settings, "Ajustes", tint = TextoMedio, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }

            // ── DISPLAY PESO ─────────────────────────────────────────────
            item { DisplayPeso(uiState = uiState, onLimpiarTara = { viewModel.limpiarTara() }) }

            // ── FEEDBACK ESCANER ─────────────────────────────────────
            item {
                AnimatedVisibility(
                    visible = uiState.codigoEscaneado.isNotEmpty(),
                    enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { -40 },
                    exit  = fadeOut(tween(300))
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A237E)),
                        shape  = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.QrCodeScanner, null,
                                tint = Color.White, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("📷 Código escaneado — procesando...",
                                    color = Color(0xFFB3C8FF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Text(uiState.codigoEscaneado,
                                    color = Color.White, fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }

            // ── PREVIEW CÁMARA EN TIEMPO REAL ─────────────────────
            item {
                AnimatedVisibility(visible = uiState.camaraPermiso) {
                    CameraPreviewCard(cameraCapture = viewModel.cameraCapture)
                }
            }


            // ── FEEDBACK GUARDADO ────────────────────────────────────────
            item {
                AnimatedVisibility(visible = uiState.ultimoGuardado.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = VerdeClaro, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Guardado: ${uiState.ultimoGuardado}", color = Verde, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // ── FEEDBACK CÁMARA CAPTURANDO ───────────────────────────────
            item {
                AnimatedVisibility(visible = uiState.camaraCapturando) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            val inf = rememberInfiniteTransition(label = "camPulse")
                            val al by inf.animateFloat(0.3f, 1f,
                                infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse), label = "al")
                            Icon(Icons.Default.CameraAlt, null,
                                tint = Color(0xFF7B1FA2).copy(alpha = al),
                                modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("📸 Capturando foto...", color = Color(0xFF7B1FA2), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // ── FEEDBACK IMPRESIÓN ───────────────────────────────────────
            item {
                AnimatedVisibility(visible = uiState.printResult.isNotEmpty()) {
                    val esOk = uiState.printResult == "ok"
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (esOk) Color(0xFFE3F2FD) else Color(0xFFFFEBEE)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (esOk) Icons.Default.Print else Icons.Default.Error,
                                null,
                                tint = if (esOk) Azul else Rojo,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (esOk) "✓ Etiqueta impresa correctamente"
                                else "Error al imprimir: ${uiState.printResult.removePrefix("error:")}",
                                color = if (esOk) Azul else Rojo,
                                fontSize = 13.sp, fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // ── ERROR ────────────────────────────────────────────────────
            item {
                AnimatedVisibility(visible = uiState.mensajeError.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "⚠ ${uiState.mensajeError}",
                            color = Rojo,
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // ── BOTONES ──────────────────────────────────────────────────
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BotonGuardar(
                        modifier          = Modifier.weight(1f),
                        habilitado        = uiState.conexion == ConexionEstado.CONECTADO &&
                                uiState.peso != "---" && !uiState.guardando &&
                                !uiState.printing && !uiState.camaraCapturando,
                        guardando         = uiState.guardando,
                        printing          = uiState.printing,
                        camaraCapturando  = uiState.camaraCapturando,
                        tienePrinter      = uiState.selectedPrinterAddress.isNotEmpty(),
                        tieneCarama       = uiState.hayCaramaUsb && uiState.camaraPermiso,
                        onClick           = { viewModel.guardarPeso() }
                    )
                    // Botón TARA
                    Button(
                        onClick = { viewModel.setTara() },
                        enabled = uiState.conexion == ConexionEstado.CONECTADO && uiState.peso != "---",
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (uiState.taraActiva) Ambar else Color(0xFF795548)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(56.dp),
                        elevation = ButtonDefaults.buttonElevation(2.dp)
                    ) {
                        Text(
                            if (uiState.taraActiva) "TARA\n${uiState.taraStr}" else "TARA",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            lineHeight = 13.sp
                        )
                    }
                    if (uiState.conexion == ConexionEstado.DESCONECTADO || uiState.conexion == ConexionEstado.ERROR) {
                        Button(
                            onClick = { viewModel.buscarYConectar() },
                            colors = ButtonDefaults.buttonColors(containerColor = Superficie),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(56.dp),
                            elevation = ButtonDefaults.buttonElevation(2.dp)
                        ) {
                            Icon(Icons.Default.Refresh, null, tint = TextoMedio, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            // ── HISTORIAL ────────────────────────────────────────────────
            if (registros.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "HISTORIAL  (${registros.size})",
                            color = TextoMedio, fontSize = 11.sp,
                            letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold
                        )
                        IconButton(onClick = { viewModel.eliminarTodo() }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.DeleteSweep, "Borrar todo", tint = Rojo.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                        }
                    }
                    HorizontalDivider(color = Borde, thickness = 1.dp)
                }
                items(items = registros, key = { it.id }) { registro ->
                    FilaRegistro(registro = registro, onEliminar = { viewModel.eliminarRegistro(registro.id) })
                }
                item { Spacer(Modifier.height(16.dp)) }
            } else {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Toca GUARDAR para registrar el peso",
                        color = TextoSuave, fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    if (mostrarAjustes) {
        AjustesDialog(
            uiState           = uiState,
            impresoras        = impresoras,
            onDismiss         = { mostrarAjustes = false },
            onSaveName        = { viewModel.actualizarNombreProducto(it) },
            onSelectPrinter   = { address, name -> viewModel.seleccionarImpresora(address, name) },
            onClearPrinter    = { viewModel.limpiarSeleccionImpresora() },
            onRefreshPrinters = { viewModel.cargarImpresoras() },
            onTestPrinter     = { viewModel.probarImpresora() },
            onRefreshCarama   = { viewModel.verificarCaramaUsb() }
        )
    }
}

// ── Badge de estado USB ──────────────────────────────────────────────────────
@Composable
private fun EstadoBadgeChip(uiState: BasculaUiState) {
    val badge = when (uiState.conexion) {
        ConexionEstado.CONECTADO           -> EstadoBadge(VerdeClaro, "Conectado", Icons.Default.Usb)
        ConexionEstado.DESCONECTADO        -> EstadoBadge(TextoMedio, "Desconectado", Icons.Default.UsbOff)
        ConexionEstado.SOLICITANDO_PERMISO -> EstadoBadge(Ambar, "Permiso...", Icons.Default.Usb)
        ConexionEstado.ERROR               -> EstadoBadge(Rojo, "Error", Icons.Default.UsbOff)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(badge.color.copy(alpha = 0.1f))
            .border(1.dp, badge.color.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        if (uiState.conexion == ConexionEstado.CONECTADO) {
            val inf = rememberInfiniteTransition(label = "pulse")
            val pa by inf.animateFloat(0.4f, 1f,
                infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse), label = "pa")
            Box(Modifier.size(7.dp).alpha(pa).clip(CircleShape).background(badge.color))
        } else {
            Icon(badge.icono, null, tint = badge.color, modifier = Modifier.size(12.dp))
        }
        Spacer(Modifier.width(5.dp))
        Text(badge.texto, color = badge.color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ── Display de peso ──────────────────────────────────────────────────────────
@Composable
private fun DisplayPeso(uiState: BasculaUiState, onLimpiarTara: () -> Unit) {
    val colorPeso by animateColorAsState(
        targetValue = when {
            uiState.peso == "---" -> TextoSuave
            uiState.estable       -> Verde
            else                  -> TextoOscuro
        } as Color,
        animationSpec = tween(500), label = "colorPeso"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, if (uiState.estable) VerdeClaro.copy(alpha = 0.6f) else Borde, RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = Superficie),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (uiState.taraActiva) "PESO EN VIVO (BRUTO)" else "PESO EN VIVO",
                    color = TextoMedio, fontSize = 11.sp, letterSpacing = 3.sp, fontWeight = FontWeight.SemiBold
                )
                AnimatedVisibility(visible = uiState.peso != "---") {
                    if (uiState.estable) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFFE8F5E9))
                                .border(1.dp, VerdeClaro.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(VerdeClaro))
                            Spacer(Modifier.width(4.dp))
                            Text("Estable", color = VerdeClaro, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        val it2 = rememberInfiniteTransition(label = "blink")
                        val ba by it2.animateFloat(0.3f, 1f,
                            infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse), label = "ba")
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFFFFF8E1))
                                .border(1.dp, Ambar.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Box(Modifier.size(6.dp).alpha(ba).clip(CircleShape).background(Ambar))
                            Spacer(Modifier.width(4.dp))
                            Text("Estabilizando...", color = Ambar, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Peso bruto (grande cuando no hay tara, más pequeño cuando hay tara)
            AnimatedContent(
                targetState = uiState.peso,
                transitionSpec = {
                    (fadeIn(tween(200)) + slideInVertically(tween(200)) { -20 }) togetherWith fadeOut(tween(150))
                }, label = "pesoAnim"
            ) { pesoActual ->
                Text(
                    text = pesoActual,
                    color = if (uiState.taraActiva) TextoMedio else colorPeso,
                    fontSize = if (uiState.taraActiva) 38.sp
                               else if (pesoActual.length > 7) 56.sp else 76.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
            }

            if (uiState.unidad.isNotEmpty() && uiState.peso != "---" && !uiState.taraActiva) {
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (uiState.estable) VerdeClaro else Azul)
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Text(uiState.unidad.uppercase(), color = Color.White, fontSize = 14.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }
            }

            // ── Panel de TARA (visible solo cuando hay tara activa) ──────
            AnimatedVisibility(visible = uiState.taraActiva) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(8.dp))

                    // Fila tara con boton limpiar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFFFF8E1))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "TARA:",
                            color = Ambar, fontSize = 12.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.sp
                        )
                        Text(
                            "${uiState.taraStr} ${uiState.unidad}",
                            color = Ambar, fontSize = 18.sp,
                            fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace
                        )
                        TextButton(
                            onClick = onLimpiarTara,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("✕ LIMPIAR", color = Rojo, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider(color = Borde)
                    Spacer(Modifier.height(8.dp))

                    // Peso NETO — protagonista
                    Text("PESO NETO", color = TextoMedio, fontSize = 11.sp, letterSpacing = 3.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    AnimatedContent(
                        targetState = uiState.pesoNeto,
                        transitionSpec = {
                            (fadeIn(tween(200)) + slideInVertically(tween(200)) { -20 }) togetherWith fadeOut(tween(150))
                        }, label = "netoAnim"
                    ) { neto ->
                        Text(
                            text = neto,
                            color = Verde,
                            fontSize = if (neto.length > 7) 52.sp else 68.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(VerdeClaro)
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        Text(uiState.unidad.uppercase(), color = Color.White, fontSize = 14.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("(lo que se imprime y guarda)", color = TextoSuave, fontSize = 10.sp)
                }
            }

            if (uiState.dispositivoNombre.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("📡 ${uiState.dispositivoNombre}", color = TextoSuave, fontSize = 11.sp)
            }
        }
    }
}

// ── Botón GUARDAR ────────────────────────────────────────────────────────────
@Composable
private fun BotonGuardar(
    modifier: Modifier = Modifier,
    habilitado: Boolean,
    guardando: Boolean,
    printing: Boolean,
    camaraCapturando: Boolean,
    tienePrinter: Boolean,
    tieneCarama: Boolean,
    onClick: () -> Unit
) {
    val gradient = if (habilitado)
        Brush.horizontalGradient(listOf(Azul, AzulClaro))
    else
        Brush.horizontalGradient(listOf(Color(0xFFE0E0E0), Color(0xFFBDBDBD)))

    Box(
        modifier = modifier.height(56.dp).clip(RoundedCornerShape(14.dp)).background(gradient),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = onClick,
            enabled = habilitado,
            modifier = Modifier.fillMaxSize(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent
            ),
            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp)
        ) {
            val ic = if (habilitado) Color.White else Color(0xFF9E9E9E)
            val icono = when {
                camaraCapturando -> Icons.Default.CameraAlt
                printing         -> Icons.Default.Print
                guardando        -> Icons.Default.Save
                tieneCarama      -> Icons.Default.CameraAlt
                tienePrinter     -> Icons.Default.Print
                else             -> Icons.Default.Save
            }
            Icon(icono, null, tint = ic, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    camaraCapturando -> "FOTOGRAFIANDO..."
                    printing         -> "IMPRIMIENDO..."
                    guardando        -> "GUARDANDO..."
                    tieneCarama && tienePrinter -> "GUARDAR + 📷 + 🖨"
                    tieneCarama      -> "GUARDAR + FOTO"
                    tienePrinter     -> "GUARDAR + IMPRIMIR"
                    else             -> "GUARDAR PESO"
                },
                color = ic, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp
            )
        }
    }
}

// ── Fila del historial ───────────────────────────────────────────────────────
@Composable
private fun FilaRegistro(registro: RegistroPeso, onEliminar: () -> Unit) {
    Card(
        colors    = CardDefaults.cardColors(containerColor = Superficie),
        shape     = RoundedCornerShape(12.dp),
        modifier  = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(registro.peso, color = TextoOscuro, fontSize = 24.sp,
                        fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.width(4.dp))
                    Text(registro.unidad.uppercase(), color = Azul, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 3.dp))
                    // Badge "NETO" si hay desglose de tara
                    if (registro.pesoBruto != null) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(VerdeClaro)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("NETO", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(fmtFecha.format(Date(registro.timestamp)),
                        color = TextoMedio, fontSize = 11.sp, textAlign = TextAlign.End)
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = onEliminar, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, "Eliminar", tint = Rojo.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Desglose BRUTO / TARA / NETO (solo cuando hay tara)
            if (registro.pesoBruto != null && registro.tara != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    HorizontalDivider(color = Borde)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Bruto:", color = TextoMedio, fontSize = 11.sp)
                        Text("${registro.pesoBruto} ${registro.unidad}",
                            color = TextoMedio, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Tara:", color = Ambar, fontSize = 11.sp)
                        Text("${registro.tara} ${registro.unidad}",
                            color = Ambar, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Neto:", color = Verde, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("${registro.peso} ${registro.unidad}",
                            color = Verde, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Código de barras escaneado (si existe)
            if (registro.codigoBarras != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.QrCodeScanner, null,
                        tint = TextoMedio, modifier = Modifier.size(13.dp))
                    Text(
                        registro.codigoBarras,
                        color = TextoMedio,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Miniatura de foto si existe
            if (registro.fotoRuta != null) {
                val fotoFile = File(registro.fotoRuta)
                if (fotoFile.exists()) {
                    val ctx = LocalContext.current
                    AsyncImage(
                        model = ImageRequest.Builder(ctx)
                            .data(fotoFile)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Foto del registro",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                    )
                }
            }
        }
    }
}


// ── Diálogo de Ajustes ───────────────────────────────────────────────────────
@Composable
private fun AjustesDialog(
    uiState: BasculaUiState,
    impresoras: List<BtImpresoraInfo>,
    onDismiss: () -> Unit,
    onSaveName: (String) -> Unit,
    onSelectPrinter: (String, String) -> Unit,   // (address, nombre)
    onClearPrinter: () -> Unit,
    onRefreshPrinters: () -> Unit,
    onTestPrinter: () -> Unit,
    onRefreshCarama: () -> Unit
) {
    var nombre by rememberSaveable { mutableStateOf(uiState.productName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Superficie,
        shape            = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, null, tint = Azul, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Ajustes", color = TextoOscuro, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // ── Nombre del producto ──────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Nombre en etiqueta", color = TextoMedio, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = nombre,
                        onValueChange = { nombre = it },
                        placeholder = { Text("Ej: MI EMPRESA") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = Azul,
                            unfocusedBorderColor = Borde,
                            focusedTextColor     = TextoOscuro,
                            unfocusedTextColor   = TextoOscuro
                        )
                    )
                }

                HorizontalDivider(color = Borde)

                // ── Cámara USB ───────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CameraAlt, null, tint = Azul, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Cámara USB", color = TextoMedio, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    IconButton(onClick = onRefreshCarama, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Refresh, "Actualizar", tint = Azul, modifier = Modifier.size(18.dp))
                    }
                }

                if (uiState.hayCaramaUsb) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (uiState.camaraPermiso) Color(0xFFE8F5E9) else Color(0xFFFFF8E1)
                            )
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CameraAlt, null,
                            tint = if (uiState.camaraPermiso) VerdeClaro else Ambar,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (uiState.camaraPermiso)
                                "✓ Cámara USB detectada — tomará foto al guardar"
                            else
                                "Cámara USB detectada — toca aquí para dar permiso",
                            color = if (uiState.camaraPermiso) VerdeClaro else Ambar,
                            fontSize = 12.sp, lineHeight = 17.sp
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF5F5F5))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.UsbOff, null, tint = TextoSuave, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Conecta la Rapoo C500 por USB y toca ↻",
                            color = TextoSuave, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                }

                HorizontalDivider(color = Borde)

                // ── Selección de impresora Bluetooth ───────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Print, null, tint = Azul, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Impresora Bluetooth", color = TextoMedio, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    IconButton(onClick = onRefreshPrinters, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Refresh, "Actualizar lista", tint = Azul, modifier = Modifier.size(18.dp))
                    }
                }

                // Aviso de emparejamiento
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFE3F2FD))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Usb, null, tint = Azul, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Empareja la impresora en Ajustes → Bluetooth del celular antes de seleccionarla aquí.",
                        color = Azul, fontSize = 11.sp, lineHeight = 15.sp
                    )
                }

                if (impresoras.isEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFFF8E1))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Print, null, tint = Ambar, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("No hay dispositivos Bluetooth emparejados. Ve a Ajustes → Bluetooth y empareja la impresora, luego toca ↻",
                            color = Ambar, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Opción: sin impresora
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onClearPrinter() }.padding(6.dp)
                        ) {
                            RadioButton(
                                selected = uiState.selectedPrinterAddress.isEmpty(),
                                onClick = { onClearPrinter() },
                                colors = RadioButtonDefaults.colors(selectedColor = Azul)
                            )
                            Text("Sin impresora", color = TextoMedio, fontSize = 13.sp)
                        }
                        // Lista de dispositivos Bluetooth emparejados
                        impresoras.forEach { dispositivo ->
                            val sel = uiState.selectedPrinterAddress == dispositivo.address
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (sel) Azul.copy(alpha = 0.07f) else Color.Transparent)
                                    .clickable { onSelectPrinter(dispositivo.address, dispositivo.nombre) }
                                    .padding(6.dp)
                            ) {
                                RadioButton(
                                    selected = sel,
                                    onClick = { onSelectPrinter(dispositivo.address, dispositivo.nombre) },
                                    colors = RadioButtonDefaults.colors(selectedColor = Azul)
                                )
                                Column {
                                    Text(dispositivo.nombre, color = TextoOscuro, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Text(dispositivo.address, color = TextoSuave, fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }

                if (uiState.selectedPrinterName.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFE8F5E9)).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Print, null, tint = VerdeClaro, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text("✓ Impresora seleccionada:",
                                color = VerdeClaro, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text(uiState.selectedPrinterName,
                                color = VerdeClaro, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    // Botón PROBAR
                    Button(
                        onClick  = onTestPrinter,
                        enabled  = !uiState.printing,
                        colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                        shape    = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Print, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (uiState.printing) "Probando..." else "PROBAR IMPRESORA",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Resultado de la prueba
                    if (uiState.printResult.isNotEmpty()) {
                        Text(
                            uiState.printResult,
                            color    = if (uiState.printResult.startsWith("✓")) VerdeClaro else Color(0xFFD32F2F),
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF5F5F5))
                                .padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveName(nombre.trim().ifEmpty { "MI EMPRESA" }); onDismiss() },
                colors = ButtonDefaults.buttonColors(containerColor = Azul),
                shape  = RoundedCornerShape(10.dp)
            ) { Text("Guardar", color = Color.White, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = TextoMedio) }
        }
    )
}

// ── Preview de cámara en tiempo real ─────────────────────────────────────────
@Composable
private fun CameraPreviewCard(cameraCapture: CameraCapture) {
    Card(
        colors    = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)),
        shape     = RoundedCornerShape(16.dp),
        modifier  = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {

            // AspectRatioTextureView de libausbc implementa IAspectRatio
            // que requiere Camera.openCamera() para el stream UVC
            AndroidView(
                factory = { ctx ->
                    com.jiangdg.ausbc.widget.AspectRatioTextureView(ctx).also { view ->
                        cameraCapture.startPreview(view)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
            )

            // Etiqueta "EN VIVO"
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xCC1565C0))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val inf = rememberInfiniteTransition(label = "livePulse")
                    val al by inf.animateFloat(0.3f, 1f,
                        infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
                        label = "liveAl")
                    Box(Modifier.size(6.dp).alpha(al).clip(CircleShape).background(Color.Red))
                    Spacer(Modifier.width(5.dp))
                    Text("EN VIVO", color = Color.White, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }

            // Icono de cámara en esquina inferior derecha
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x881565C0))
                    .padding(6.dp)
            ) {
                Icon(Icons.Default.CameraAlt, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { cameraCapture.stopPreview() }
    }
}
