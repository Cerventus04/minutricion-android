package org.ivansola.minutricion.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import kotlinx.coroutines.delay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.util.Rational
import android.view.Surface
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ivansola.minutricion.data.Gemini
import org.ivansola.minutricion.data.GeminiFoodResult
import org.ivansola.minutricion.ui.components.raised
import org.ivansola.minutricion.ui.components.BtnYellowTop
import org.ivansola.minutricion.ui.components.BtnYellowBottom
import org.ivansola.minutricion.ui.components.BtnDarkTop
import org.ivansola.minutricion.ui.components.BtnDarkBottom
import org.ivansola.minutricion.ui.theme.Pal
import java.io.ByteArrayOutputStream
import android.graphics.BitmapFactory
import android.graphics.Matrix

/**
 * Recuadro de encuadre, en fracciones de la pantalla (0..1). Es a la vez la guía que se dibuja y
 * lo ÚNICO que se envía: todo lo de fuera se recorta, así que la foto pesa bastante menos y el
 * modelo no se distrae con el fondo.
 */
private data class Frame(val left: Float, val top: Float, val right: Float, val bottom: Float)

private data class Step(
    val title: String,
    val help: String,
    val frame: Frame,
    val shot: Gemini.Shot,
)

/** Los tres pasos guiados, en el orden en que se piden. */
private val STEPS = listOf(
    // el código de barras es una franja ancha y baja; la tabla y el frente, rectángulos altos
    Step("Código de barras", "Enfoca el código de barras del envase",
        Frame(0.07f, 0.38f, 0.93f, 0.62f), Gemini.Shot.EAN),
    Step("Información nutricional", "La tabla nutricional o, en complementos, la lista de nutrientes",
        Frame(0.07f, 0.20f, 0.93f, 0.80f), Gemini.Shot.NUTRITION),
    Step("Frente del envase", "La cara delantera, donde se ve el nombre y la marca",
        Frame(0.10f, 0.18f, 0.90f, 0.82f), Gemini.Shot.FRONT),
)

/**
 * "Rellenar con IA": guía al usuario para tomar las fotos en orden (EAN → tabla → frente). Cada una
 * se manda a Gemini NADA MÁS TOMARLA, así que se procesan mientras el usuario encuadra la
 * siguiente y al final casi no hay espera; los resultados se funden en un solo alimento.
 * `onResult` recibe lo que haya podido leer; los pasos se pueden saltar (no todos los productos
 * tienen código o tabla a la vista).
 *
 * `knownBarcode`: si ya se conoce el código (p. ej. se llegó aquí tras escanearlo y no encontrarlo),
 * el primer paso sobra y se quita — sería pedir una foto de algo que ya sabemos, gastando una
 * petición y el tiempo del usuario.
 */
@Composable
fun IaFotoScreen(
    onResult: (GeminiFoodResult) -> Unit,
    onClose: () -> Unit,
    knownBarcode: String = "",
    shots: Set<Gemini.Shot> = Gemini.Shot.entries.toSet(),
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val steps = remember(knownBarcode, shots) {
            STEPS.filter {
                // solo los pasos que pide el apartado, y sin el del código si ya se conoce
                it.shot in shots && !(it.shot == Gemini.Shot.EAN && knownBarcode.isNotBlank())
            }
        }
        // no debería pasar, pero si no queda ningún paso que pedir no tiene sentido abrir la cámara
        if (steps.isEmpty()) { LaunchedEffect(Unit) { onClose() }; return@Dialog }
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var granted by remember {
            mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED)
        }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
        LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

        /** Una foto tomada y su envío a Gemini, ya en marcha. */
        class Job(val shot: Gemini.Shot, val bytes: ByteArray, val task: Deferred<Gemini.Outcome>)

        val jobs = remember { mutableStateListOf<Job>() }
        val ready = remember { mutableStateMapOf<Int, Boolean>() }   // índice -> ya respondió
        var step by remember { mutableStateOf(0) }
        var capture by remember { mutableStateOf<ImageCapture?>(null) }
        var taking by remember { mutableStateOf(false) }
        var analyzing by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        // destello blanco al disparar: sin él no hay ninguna señal de que la foto se haya hecho y
        // no queda claro que toca hacer la siguiente.
        var flash by remember { mutableStateOf(false) }
        val flashAlpha by animateFloatAsState(
            targetValue = if (flash) 0.85f else 0f,
            animationSpec = tween(durationMillis = if (flash) 40 else 260),
            label = "flash",
        )
        LaunchedEffect(flash) { if (flash) { delay(90); flash = false } }

        /**
         * Manda la foto AL MOMENTO, sin esperar a las demás: mientras el usuario encuadra la
         * siguiente, esta ya se está procesando, así que al hacer la última solo queda por esperar
         * lo de esa (o nada).
         */
        fun send(shot: Gemini.Shot, bytes: ByteArray) {
            val idx = jobs.size
            jobs.add(Job(shot, bytes, scope.async(Dispatchers.IO) {
                // el try no es de adorno: en `async` una excepción tumbaría también al resto de
                // envíos y a la propia pantalla, en vez de quedarse en "esta foto no salió".
                val out = try {
                    Gemini.analyzeImage(bytes, shot)
                } catch (e: Exception) {
                    Gemini.Outcome.Error(e.message ?: "fallo analizando la foto")
                }
                withContext(Dispatchers.Main) { ready[idx] = true }
                out
            }))
        }

        fun analyze() {
            if (jobs.isEmpty()) {
                error = "No has hecho ninguna foto"
                step = 0
                return
            }
            analyzing = true
            error = null
            scope.launch {
                val outs = jobs.map { it.task.await() }
                analyzing = false
                val food = Gemini.merge(outs.filterIsInstance<Gemini.Outcome.Ok>().map { it.food })
                when {
                    food.found -> onResult(food)
                    // si NINGUNA foto sirvió, enseña el motivo real de la primera que falló
                    else -> error = outs.filterIsInstance<Gemini.Outcome.Error>()
                        .firstOrNull()?.message
                        ?: "No he reconocido ningún producto en las fotos. " +
                        "Prueba a repetirlas más cerca y bien enfocadas."
                }
            }
        }

        /** Reenvía las fotos ya hechas (para "Reintentar" sin volver a fotografiar). */
        fun retry() {
            val old = jobs.toList()
            jobs.clear(); ready.clear()
            old.forEach { send(it.shot, it.bytes) }
            analyze()
        }

        /** Avanza de paso; al terminar el último se analiza solo, sin botón. */
        fun advance() {
            if (step < steps.lastIndex) step++ else analyze()
        }

        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (granted) {
                CaptureCamera(onCaptureReady = { capture = it })
                FrameOverlay(steps[step].frame)
            } else {
                Column(Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Necesito permiso de cámara", color = Pal.Text, fontSize = 15.sp)
                }
            }

            // Cabecera: paso actual y qué hay que fotografiar
            Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Paso ${step + 1} de ${steps.size} · ${steps[step].title}",
                            color = Pal.Yellow, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(steps[step].help, color = Pal.Text, fontSize = 12.sp)
                    }
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(50)).background(Color(0x55000000))
                        .clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                        Text("✕", color = Pal.Text, fontSize = 18.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                // puntos de progreso
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    steps.indices.forEach { i ->
                        Box(Modifier.height(4.dp).width(46.dp).clip(RoundedCornerShape(2.dp))
                            .background(if (i <= step) Pal.Yellow else Color(0x55FFFFFF)))
                    }
                }
            }

            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                if (jobs.isNotEmpty()) {
                    // cada miniatura enseña si su foto ya está procesada (✓) o aún en camino
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        jobs.forEachIndexed { i, job ->
                            val bmp = remember(job.bytes) {
                                BitmapFactory.decodeByteArray(job.bytes, 0, job.bytes.size)
                            }
                            Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))
                                .border(1.dp, Pal.Yellow, RoundedCornerShape(10.dp))) {
                                if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop)
                                Box(Modifier.fillMaxSize().background(Color(0x66000000)),
                                    contentAlignment = Alignment.Center) {
                                    if (ready[i] == true) Text("✓", color = Pal.Yellow,
                                        fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                    else CircularProgressIndicator(Modifier.size(18.dp),
                                        color = Pal.Yellow, strokeWidth = 2.dp)
                                }
                            }
                        }
                    }
                }
                error?.let {
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(Color(0xCC3A1212)).padding(12.dp)) {
                        Text(it, color = Color(0xFFFF8A8A), fontSize = 12.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Pill("Reintentar", Pal.Yellow) { retry() }
                        Pill("Repetir fotos", Pal.Card2) {
                            jobs.clear(); ready.clear(); step = 0; error = null
                        }
                    }
                }
                if (!analyzing && error == null) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text("Saltar", color = Pal.Sub, fontSize = 14.sp,
                            modifier = Modifier.clickable { advance() })
                        // disparador
                        Box(
                            Modifier.size(72.dp).clip(RoundedCornerShape(50)).background(Pal.Yellow)
                                .clickable(enabled = !taking) {
                                    val cap = capture ?: return@clickable
                                    taking = true
                                    val s = steps[step]
                                    takePhoto(context, cap, s.frame) { bytes ->
                                        taking = false
                                        if (bytes != null) {
                                            flash = true
                                            send(s.shot, bytes); advance()
                                        } else error = "No se pudo tomar la foto"
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (taking) CircularProgressIndicator(Modifier.size(24.dp),
                                color = Pal.Bg, strokeWidth = 2.dp)
                            // icono vectorial en vez del emoji 📷: el glifo del emoji trae sus
                            // propias métricas y queda desplazado dentro del círculo.
                            else Icon(Icons.Rounded.PhotoCamera, "Tomar foto",
                                Modifier.size(32.dp), tint = Pal.Bg)
                        }
                        Text(if (step == steps.lastIndex) "Última" else "Siguiente",
                            color = Color.Transparent, fontSize = 14.sp)   // equilibra la fila
                    }
                }
            }

            if (flashAlpha > 0f) {
                Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flashAlpha)))
            }

            if (analyzing) {
                Box(Modifier.fillMaxSize().background(Color(0xAA000000)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Pal.Yellow)
                        Spacer(Modifier.height(14.dp))
                        Text("Analizando las fotos…", color = Pal.Text,
                            fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        val faltan = jobs.indices.count { ready[it] != true }
                        Text(
                            if (faltan > 0) "Queda ${if (faltan == 1) "1 foto" else "$faltan fotos"} por terminar"
                            else "Juntando nombre, código y nutrición",
                            color = Pal.Sub, fontSize = 12.sp, textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Oscurece TODO menos el recuadro de encuadre, para que quede claro qué se va a capturar (lo de
 * fuera se descarta de verdad, no es solo decoración).
 */
@Composable
private fun FrameOverlay(frame: Frame) {
    Canvas(Modifier.fillMaxSize()) {
        val l = frame.left * size.width
        val t = frame.top * size.height
        val r = frame.right * size.width
        val b = frame.bottom * size.height
        val veil = Color(0xE6000000)
        drawRect(veil, size = androidx.compose.ui.geometry.Size(size.width, t))
        drawRect(veil, topLeft = Offset(0f, b),
            size = androidx.compose.ui.geometry.Size(size.width, size.height - b))
        drawRect(veil, topLeft = Offset(0f, t), size = androidx.compose.ui.geometry.Size(l, b - t))
        drawRect(veil, topLeft = Offset(r, t),
            size = androidx.compose.ui.geometry.Size(size.width - r, b - t))
        drawRoundRect(
            Pal.Yellow, topLeft = Offset(l, t),
            size = androidx.compose.ui.geometry.Size(r - l, b - t),
            cornerRadius = CornerRadius(14.dp.toPx()),
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}

@Composable
private fun Pill(text: String, bg: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .raised(RoundedCornerShape(22.dp),
                if (bg == Pal.Yellow) BtnYellowTop else BtnDarkTop,
                if (bg == Pal.Yellow) BtnYellowBottom else BtnDarkBottom,
                highlight = if (bg == Pal.Yellow) 0.40f else 0.13f,
                shade = if (bg == Pal.Yellow) 0.22f else 0.45f)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (bg == Pal.Yellow) Pal.Bg else Pal.Text,
            fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

private fun takePhoto(
    context: android.content.Context,
    capture: ImageCapture,
    frame: Frame,
    onDone: (ByteArray?) -> Unit,
) {
    capture.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
        override fun onCaptureSuccess(image: ImageProxy) {
            val bytes = imageProxyToJpeg(image, frame)
            image.close()
            onDone(bytes)
        }
        override fun onError(exc: ImageCaptureException) {
            onDone(null)
        }
    })
}

/** Lado mayor al que se reduce cada foto antes de mandarla: de sobra para leer una etiqueta. */
private const val MAX_SIDE = 1600

/**
 * JPEG orientado y REDUCIDO. Lo segundo no es cosmético: la cámara entrega la foto a resolución
 * nativa (48-64 MP en móviles actuales) y tres de ellas, ya en base64 dentro del JSON, se van a
 * 48-64 MB — muy por encima del límite de 20 MB por petición de Gemini, que rechazaba la llamada.
 * A 1600 px cada foto queda en pocos cientos de KB y la etiqueta se sigue leyendo perfectamente.
 */
/**
 * Margen que se deja ALREDEDOR del recuadro al recortar: encuadrar justo al borde es fácil que
 * corte la última cifra de una tabla o una barra del código, y eso arruina la lectura.
 */
private const val CROP_PAD = 0.03f

private fun imageProxyToJpeg(image: ImageProxy, frame: Frame?): ByteArray {
    val buffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
    var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes

    // 1) el ViewPort deja en `cropRect` justo el trozo que se ve en la vista previa: sin esto, las
    //    fracciones del recuadro no coincidirían con lo que el usuario tenía delante.
    val cr = image.cropRect
    if (cr.width() in 1 until bmp.width || cr.height() in 1 until bmp.height) {
        bmp = android.graphics.Bitmap.createBitmap(
            bmp, cr.left.coerceIn(0, bmp.width - 1), cr.top.coerceIn(0, bmp.height - 1),
            cr.width().coerceAtMost(bmp.width - cr.left), cr.height().coerceAtMost(bmp.height - cr.top),
        )
    }
    // 2) enderezar antes de recortar, porque el recuadro está en coordenadas de PANTALLA
    val rotation = image.imageInfo.rotationDegrees
    if (rotation != 0) {
        bmp = android.graphics.Bitmap.createBitmap(
            bmp, 0, 0, bmp.width, bmp.height,
            Matrix().apply { postRotate(rotation.toFloat()) }, true,
        )
    }
    // 3) quedarse solo con el recuadro amarillo (+ margen)
    if (frame != null) {
        val l = ((frame.left - CROP_PAD).coerceAtLeast(0f) * bmp.width).toInt()
        val t = ((frame.top - CROP_PAD).coerceAtLeast(0f) * bmp.height).toInt()
        val r = ((frame.right + CROP_PAD).coerceAtMost(1f) * bmp.width).toInt()
        val b = ((frame.bottom + CROP_PAD).coerceAtMost(1f) * bmp.height).toInt()
        if (r - l > 0 && b - t > 0) bmp = android.graphics.Bitmap.createBitmap(bmp, l, t, r - l, b - t)
    }
    // 4) reducir: la cámara entrega resolución nativa (48-64 MP) y tres fotos así, ya en base64,
    //    superaban el límite de 20 MB por petición de Gemini, que rechazaba la llamada.
    val scale = MAX_SIDE.toFloat() / maxOf(bmp.width, bmp.height)
    if (scale < 1f) {
        bmp = android.graphics.Bitmap.createBitmap(
            bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postScale(scale, scale) }, true,
        )
    }
    return ByteArrayOutputStream().also {
        bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it)
    }.toByteArray()
}

@Composable
private fun CaptureCamera(onCaptureReady: (ImageCapture) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val providerHolder = remember { arrayOfNulls<ProcessCameraProvider>(1) }
    AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
        val view = PreviewView(ctx).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE }
        val future = ProcessCameraProvider.getInstance(ctx)
        // doOnLayout: hasta que la vista no está medida no hay ni tamaño ni display, y ambos hacen
        // falta para el ViewPort de abajo.
        future.addListener({ view.doOnLayout {
            val provider = future.get(); providerHolder[0] = provider
            val prev = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
            val imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
            try {
                provider.unbindAll()
                // ViewPort = "captura lo mismo que se ve en pantalla". Sin él la foto abarca más
                // campo que la vista previa (FILL_CENTER recorta) y el recuadro amarillo no
                // correspondería con la zona que luego recortamos.
                val vp = if (view.width > 0 && view.height > 0) {
                    ViewPort.Builder(Rational(view.width, view.height),
                        view.display?.rotation ?: Surface.ROTATION_0)
                        .setScaleType(ViewPort.FILL_CENTER).build()
                } else null
                val group = UseCaseGroup.Builder().addUseCase(prev).addUseCase(imageCapture)
                    .apply { vp?.let { setViewPort(it) } }.build()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, group)
                onCaptureReady(imageCapture)
            } catch (_: Exception) {}
        } }, ContextCompat.getMainExecutor(ctx))
        view
    })
    DisposableEffect(Unit) { onDispose { providerHolder[0]?.unbindAll() } }
}
