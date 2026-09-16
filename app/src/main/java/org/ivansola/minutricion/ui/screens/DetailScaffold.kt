package org.ivansola.minutricion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.ivansola.minutricion.ui.theme.Pal
import java.time.LocalDate

/** Barra superior con flecha atrás + título (mismo estilo en todas las pantallas de detalle). */
@Composable
fun DetailTopBar(title: String, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(Pal.Card2).clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.ArrowBackIosNew, null, tint = Pal.Text, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(title, color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1)
    }
}

/**
 * Pantalla de detalle a pantalla completa (Dialog) con barra superior y contenido scrollable.
 * `swipe` (opcional): deslizar lateralmente llama con +1 (derecha) o -1 (izquierda).
 */
@Composable
fun DetailScreen(
    title: String,
    onClose: () -> Unit,
    swipe: ((Int) -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var mod = Modifier.fillMaxSize().background(Pal.Bg)
        if (swipe != null) {
            mod = mod.pointerInput(Unit) {
                val threshold = 56.dp.toPx(); var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total > threshold) swipe(1) else if (total < -threshold) swipe(-1) },
                    onHorizontalDrag = { _, dx -> total += dx },
                )
            }
        }
        Column(mod) {
            DetailTopBar(title, onClose)
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}

private val MESES = listOf("ene", "feb", "mar", "abr", "may", "jun",
    "jul", "ago", "sep", "oct", "nov", "dic")
private val MESES_FULL_C = listOf("Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio",
    "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre")
private val DIAS_C = listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")

fun fechaDm(d: LocalDate) = "${d.dayOfMonth} ${MESES[d.monthValue - 1]}"
fun fechaLarga(d: LocalDate) = "${DIAS_C[d.dayOfWeek.value - 1]} ${d.dayOfMonth} ${MESES[d.monthValue - 1]}"
fun diaCorto(d: LocalDate) = "${DIAS_C[d.dayOfWeek.value - 1]} ${d.dayOfMonth}"
fun mesFull(month: Int) = MESES_FULL_C[month - 1]

/** Peso con hasta 2 decimales y sin ceros sobrantes: 67.85, 67.9, 68. Antes redondeaba a 1 decimal
 *  y 67,85 se veía (y se volvía a guardar desde el diálogo) como 67,9. */
fun fmtKg(x: Double): String =
    java.math.BigDecimal.valueOf(x).setScale(2, java.math.RoundingMode.HALF_UP)
        .stripTrailingZeros().toPlainString()
