package org.ivansola.minutricion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ivansola.minutricion.ui.components.CtaButton
import org.ivansola.minutricion.ui.components.raisedDark
import org.ivansola.minutricion.ui.theme.Pal
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** Paso de los botones − / +: muchas básculas miden de 50 en 50 g (67,85 kg). */
private const val STEP_KG = 0.05

/** Peso con 2 decimales como máximo, sin el error de coma flotante de sumar pasos (67.85000001). */
internal fun roundKg(x: Double): Double =
    BigDecimal.valueOf(x).setScale(2, RoundingMode.HALF_UP).toDouble()

/** Lo que se puede escribir: hasta 3 cifras, un separador (coma o punto) y 2 decimales. */
internal fun kgFilter(s: String): String {
    val out = StringBuilder()
    var sep = false
    var ints = 0
    var decs = 0
    for (ch in s) when {
        ch.isDigit() && !sep && ints < 3 -> { out.append(ch); ints++ }
        ch.isDigit() && sep && decs < 2 -> { out.append(ch); decs++ }
        (ch == '.' || ch == ',') && !sep && ints > 0 -> { out.append('.'); sep = true }
    }
    return out.toString()
}

/** Un peso válido (entre 20 y 400 kg) o null. */
internal fun parseKg(s: String): Double? =
    s.replace(',', '.').toDoubleOrNull()?.takeIf { it in 20.0..400.0 }?.let(::roundKg)

/**
 * Hoja "Actualizar peso". Sigue el lenguaje del resto de hojas de la app (Ingreso Manual, Ajustar
 * Macros): fondo de pantalla, título grande, la cifra en una caja redondeada, botones en relieve y
 * el botón amarillo de acción. La usan Progreso y el detalle de Peso.
 *
 * `current` es el último peso conocido (registro o perfil) y `lastDay` el día de ese registro.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightSheet(
    current: Double?,
    lastDay: String?,
    onSave: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Pal.Bg) {
        WeightSheetContent(current, lastDay, onSave)
    }
}

/** El contenido de [WeightSheet], aparte para poder pintarlo en las capturas de prueba. */
@Composable
fun WeightSheetContent(current: Double?, lastDay: String?, onSave: (Double) -> Unit) {
    var text by remember { mutableStateOf(current?.let { fmtKg(it) } ?: "") }
    val value = parseKg(text)
    fun step(delta: Double) {
        val base = text.replace(',', '.').toDoubleOrNull() ?: current ?: return
        text = fmtKg(roundKg((base + delta).coerceIn(20.0, 400.0)))
    }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Actualizar peso", color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            Text("Registro de hoy, ${fechaLarga(LocalDate.now())}", color = Pal.Sub, fontSize = 13.sp)
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StepButton("−") { step(-STEP_KG) }
            Box(
                Modifier.weight(1f).height(84.dp).clip(RoundedCornerShape(18.dp))
                    .background(Pal.Card2).padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                val big = TextStyle(color = Pal.Text, fontSize = 36.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center)
                if (text.isEmpty()) Text("0.00", style = big.copy(color = Pal.Sub))
                BasicTextField(
                    value = text,
                    onValueChange = { text = kgFilter(it) },
                    singleLine = true,
                    textStyle = big,
                    cursorBrush = SolidColor(Pal.Yellow),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { value?.let(onSave) }),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                )
                Text("kg", color = Pal.Sub, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterEnd))
            }
            StepButton("+") { step(STEP_KG) }
        }

        // referencia: el último registro y cuánto cambia respecto a él
        val ref = when {
            current == null -> null
            lastDay != null -> "Último registro: ${fmtKg(current)} kg · ${fechaLarga(LocalDate.parse(lastDay))}"
            else -> "Peso del perfil: ${fmtKg(current)} kg"
        }
        if (ref != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(ref, color = Pal.Sub, fontSize = 13.sp, modifier = Modifier.weight(1f))
                if (value != null && current != null) {
                    val diff = roundKg(value - current)
                    Text(
                        when {
                            diff > 0 -> "+${fmtKg(diff)} kg"
                            diff < 0 -> "−${fmtKg(-diff)} kg"
                            else -> "sin cambios"
                        },
                        color = Pal.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(Modifier.height(2.dp))
        CtaButton("Guardar", { value?.let(onSave) }, enabled = value != null)
    }
}

@Composable
private fun StepButton(symbol: String, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).raisedDark(CircleShape, elevation = 4.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, color = Pal.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}
