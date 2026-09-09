package org.ivansola.minutricion.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import org.ivansola.minutricion.ui.components.raisedYellow
import org.ivansola.minutricion.ui.theme.Pal
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Escáner de código de barras a pantalla completa (CameraX + ML Kit, reconocimiento offline).
 * `onResult` se dispara con el primer código detectado (o el introducido a mano); `onClose` cancela.
 */
@Composable
fun ScanScreen(onResult: (String) -> Unit, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val context = LocalContext.current
        var granted by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
            )
        }
        var manual by remember { mutableStateOf(false) }
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted = it }
        LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (granted) {
                CameraPreview(onResult)
                // marco de escaneo (estilo Fitia/Kivy: recuadro amarillo)
                Box(
                    Modifier.align(Alignment.Center).size(width = 280.dp, height = 170.dp)
                        .border(2.dp, Pal.Yellow, RoundedCornerShape(14.dp))
                )
            } else {
                Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("Necesito permiso de cámara para escanear",
                        color = Pal.Text, fontSize = 15.sp)
                    Pill("Dar permiso") { launcher.launch(Manifest.permission.CAMERA) }
                }
            }

            // Barra superior: título + cerrar
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Escanea el código de barras", color = Pal.Text,
                    fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShapeCircle())
                        .background(Color(0x55000000)).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Close, null, tint = Pal.Text, modifier = Modifier.size(22.dp))
                }
            }

            // Introducir código a mano (respaldo si la cámara no lee)
            Pill(
                "Introducir código manualmente",
                Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
            ) { manual = true }
        }

        if (manual) {
            ManualDialog(
                onSubmit = { code -> manual = false; onResult(code) },
                onDismiss = { manual = false },
            )
        }
    }
}

/**
 * Comprueba el dígito de control de un EAN-13 / UPC-A. Una lectura corrupta casi nunca lo cumple,
 * así que es la forma barata de descartar códigos mal leídos antes de darlos por buenos. Solo se
 * valida en esas dos longitudes: UPC-E usa otro esquema y rechazarlo sería un falso negativo.
 */
private fun checksumOk(code: String): Boolean {
    if (code.length != 13 && code.length != 12) return true   // otras longitudes: sin veredicto
    if (!code.all(Char::isDigit)) return false
    val digits = code.map { it - '0' }
    var sum = 0
    for (i in digits.size - 2 downTo 0) {
        // pesos 3 y 1 alternos empezando por la derecha (sin contar el dígito de control)
        sum += digits[i] * if ((digits.size - 2 - i) % 2 == 0) 3 else 1
    }
    return (10 - sum % 10) % 10 == digits.last()
}

/** Un EAN/UPC vale más que un Code 128: ese suele ser el código de lote o logística del envase. */
private fun isProductCode(format: Int) = format == Barcode.FORMAT_EAN_13 ||
    format == Barcode.FORMAT_EAN_8 || format == Barcode.FORMAT_UPC_A || format == Barcode.FORMAT_UPC_E

@Composable
private fun CameraPreview(onBarcode: (String) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val fired = remember { AtomicBoolean(false) }
    // último código visto: no se acepta hasta que dos fotogramas seguidos coincidan, para que un
    // cuadro borroso o a medio enfocar no cuele una lectura suelta.
    val previous = remember { arrayOfNulls<String>(1) }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(
                Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E, Barcode.FORMAT_CODE_128,
            ).build()
        )
    }
    val providerHolder = remember { arrayOfNulls<ProcessCameraProvider>(1) }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                val provider = future.get()
                providerHolder[0] = provider
                val preview = Preview.Builder().build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { proxy ->
                    val media = proxy.image
                    if (media != null && !fired.get()) {
                        val img = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                        scanner.process(img)
                            .addOnSuccessListener { list ->
                                // si en el cuadro hay varios códigos (habitual: EAN + lote), manda
                                // el del producto; el Code 128 solo se usa si no hay otra cosa.
                                val valid = list.filter { !it.rawValue.isNullOrBlank() && checksumOk(it.rawValue!!) }
                                val code = (valid.firstOrNull { isProductCode(it.format) }
                                    ?: valid.firstOrNull())?.rawValue
                                if (code != null) {
                                    if (previous[0] == code && fired.compareAndSet(false, true)) {
                                        onBarcode(code)
                                    }
                                    previous[0] = code
                                }
                            }
                            .addOnCompleteListener { proxy.close() }
                    } else {
                        proxy.close()
                    }
                }
                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
                    )
                } catch (_: Exception) {
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
    )

    DisposableEffect(Unit) {
        onDispose {
            providerHolder[0]?.unbindAll()
            scanner.close()
        }
    }
}

@Composable
private fun ManualDialog(onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pal.Card2,
        title = { Text("Código de barras", color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            OutlinedTextField(
                value = code,
                onValueChange = { s -> code = s.filter { it.isDigit() } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = fieldColors(),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (code.isNotBlank()) onSubmit(code) }) {
                Text("Buscar", color = Pal.Yellow, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Pal.Sub) } },
    )
}

@Composable
internal fun Pill(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.raisedYellow(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Pal.Bg, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

private fun RoundedCornerShapeCircle() = RoundedCornerShape(50)
