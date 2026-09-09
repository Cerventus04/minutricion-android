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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.ivansola.minutricion.ui.components.CtaButton
import org.ivansola.minutricion.ui.theme.Pal
import java.util.concurrent.atomic.AtomicReference

/**
 * "Rellenar": cámara que reconoce TEXTO (ML Kit) del empaque/etiqueta. Muestra el texto detectado
 * en vivo; al pulsar "Usar" lo devuelve por `onText` para que el formulario intente rellenarse.
 * El parseo es APROXIMADO (ver parseNutrition).
 */
@Composable
fun RellenarScreen(onText: (String) -> Unit, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val context = LocalContext.current
        var granted by remember {
            mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED)
        }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
        LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
        val latest = remember { AtomicReference("") }
        var preview by remember { mutableStateOf("") }

        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (granted) {
                TextCamera(latest) { preview = it }
                Box(Modifier.align(Alignment.Center).fillMaxWidth().padding(24.dp)
                    .size(width = 300.dp, height = 200.dp)
                    .border(2.dp, Pal.Yellow, RoundedCornerShape(14.dp)))
            } else {
                Column(Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Necesito permiso de cámara", color = Pal.Text, fontSize = 15.sp)
                }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Enfoca el nombre o la etiqueta", color = Pal.Text,
                    fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(50)).background(Color(0x55000000))
                    .clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                    Text("✕", color = Pal.Text, fontSize = 18.sp)
                }
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(20.dp)) {
                if (preview.isNotBlank()) {
                    Text(preview.take(120).replace("\n", " · "), color = Pal.Sub, fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp))
                }
                CtaButton("Usar texto detectado", { onText(latest.get()) },
                    height = 52.dp, fontSize = 15.sp)
            }
        }
    }
}

/** Campos que el parser aproximado puede extraer de una etiqueta nutricional (valores como texto). */
data class ParsedNutrition(
    val kcal: String? = null, val protein: String? = null, val carbs: String? = null,
    val fat: String? = null, val sat: String? = null, val trans: String? = null,
    val sugars: String? = null, val added: String? = null, val fiber: String? = null,
    val salt: String? = null, val cholesterol: String? = null,
)

private val NUM = Regex("""(\d+[.,]?\d*)""")

private fun firstNum(line: String): String? =
    NUM.find(line)?.groupValues?.get(1)?.replace(',', '.')

/**
 * Parseo APROXIMADO de la etiqueta: por cada línea busca una palabra clave y el primer número.
 * El orden importa (saturadas/trans/añadidos antes que sus generales).
 */
fun parseNutrition(text: String): ParsedNutrition {
    var kcal: String? = null; var protein: String? = null; var carbs: String? = null
    var fat: String? = null; var sat: String? = null; var trans: String? = null
    var sugars: String? = null; var added: String? = null; var fiber: String? = null
    var salt: String? = null; var chol: String? = null
    for (raw in text.split('\n')) {
        val l = raw.lowercase()
        val n = firstNum(l) ?: continue
        when {
            "satur" in l -> sat = sat ?: n
            "trans" in l -> trans = trans ?: n
            "colest" in l || "cholest" in l -> chol = chol ?: n
            ("grasa" in l || "fat" in l) && "hidr" !in l -> fat = fat ?: n
            "añad" in l || "anad" in l || "added" in l -> added = added ?: n
            "azúc" in l || "azuc" in l || "sugar" in l -> sugars = sugars ?: n
            "fibra" in l || "fiber" in l -> fiber = fiber ?: n
            "hidrat" in l || "carbo" in l -> carbs = carbs ?: n
            "prote" in l -> protein = protein ?: n
            "sal" in l || "salt" in l -> salt = salt ?: n
            "kcal" in l || "calor" in l || "energ" in l -> kcal = kcal ?: n
        }
    }
    return ParsedNutrition(kcal, protein, carbs, fat, sat, trans, sugars, added, fiber, salt, chol)
}

@Composable
private fun TextCamera(latest: AtomicReference<String>, onPreview: (String) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val providerHolder = remember { arrayOfNulls<ProcessCameraProvider>(1) }
    AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
        val view = PreviewView(ctx).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE }
        val future = ProcessCameraProvider.getInstance(ctx)
        future.addListener({
            val provider = future.get(); providerHolder[0] = provider
            val prev = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { proxy ->
                val media = proxy.image
                if (media != null) {
                    val img = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                    recognizer.process(img)
                        .addOnSuccessListener { t ->
                            if (t.text.isNotBlank()) { latest.set(t.text); onPreview(t.text) }
                        }
                        .addOnCompleteListener { proxy.close() }
                } else proxy.close()
            }
            try {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, prev, analysis)
            } catch (_: Exception) {}
        }, ContextCompat.getMainExecutor(ctx))
        view
    })
    DisposableEffect(Unit) {
        onDispose { providerHolder[0]?.unbindAll(); recognizer.close() }
    }
}
