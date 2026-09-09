package org.ivansola.minutricion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ivansola.minutricion.data.Db
import org.ivansola.minutricion.data.Logic
import org.ivansola.minutricion.data.Nutrition
import org.ivansola.minutricion.ui.components.EmojiIcon
import org.ivansola.minutricion.ui.theme.Pal
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

private fun milesM(n: Int) = "%,d".format(n).replace(",", ".")

// ---------------------------------------------------------------------------
// Presets de macros (fracciones de las calorías, como Fitia). El % se conserva
// aunque cambie el objetivo de kcal. Keto usa "Carbs Netos".
// ---------------------------------------------------------------------------
private data class MacroPreset(
    val label: String, val emoji: String,
    val p: Double, val c: Double, val f: Double, val keto: Boolean = false,
)

private val MACRO_PRESETS = listOf(
    MacroPreset("Recomendada", "✨", 0.29, 0.41, 0.30),
    MacroPreset("Baja en grasas", "🥚", 0.29, 0.51, 0.20),
    MacroPreset("Alta en Proteínas", "🍗", 0.35, 0.40, 0.25),
    MacroPreset("Baja en carbs", "🥑", 0.29, 0.25, 0.46),
    MacroPreset("Keto", "🧴", 0.27, 0.10, 0.63, keto = true),
    MacroPreset("Personalizado", "✏️", 0.0, 0.0, 0.0),
)

/** Hoja "Ajustar Macros" (estilo Fitia): presets + 3 sliders con celdas g/kcal/%. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigMacrosDialog(onDone: () -> Unit, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val target = remember { Logic.targets()?.kcal ?: Logic.autoKcal() ?: 2000 }
    val (p0, c0, f0) = remember { Logic.macroRatios() }

    // porcentajes (suman ~100). El slider mueve uno y reequilibra los otros dos.
    var pp by remember { mutableStateOf((p0 * 100)) }
    var cc by remember { mutableStateOf((c0 * 100)) }
    var ff by remember { mutableStateOf((f0 * 100)) }
    var keto by remember { mutableStateOf(false) }

    fun matches(pr: MacroPreset) =
        !pr.label.startsWith("Person") &&
            abs(pp - pr.p * 100) < 0.6 && abs(cc - pr.c * 100) < 0.6 && abs(ff - pr.f * 100) < 0.6
    val selected = MACRO_PRESETS.firstOrNull { matches(it) }?.label ?: "Personalizado"

    /** Reequilibra: fija `which` a `value` y reparte el resto entre los otros dos manteniendo su ratio. */
    fun setMacro(which: Int, value: Double) {
        val v = value.coerceIn(0.0, 100.0)
        val rest = 100.0 - v
        val (a, b) = when (which) { 0 -> cc to ff; 1 -> pp to ff; else -> pp to cc }
        val s = a + b
        val na: Double; val nb: Double
        if (s <= 0.0) { na = rest / 2; nb = rest / 2 } else { na = rest * (a / s); nb = rest * (b / s) }
        when (which) {
            0 -> { pp = v; cc = na; ff = nb }
            1 -> { cc = v; pp = na; ff = nb }
            else -> { ff = v; pp = na; cc = nb }
        }
    }

    fun applyPreset(pr: MacroPreset) {
        if (pr.label.startsWith("Person")) return
        pp = pr.p * 100; cc = pr.c * 100; ff = pr.f * 100; keto = pr.keto
    }

    val sumPct = (pp + cc + ff).roundToInt()
    val valid = sumPct == 100

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = Pal.Bg,
        dragHandle = null,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
            SheetHeader("Ajustar Macros", onDismiss)
            Spacer(Modifier.height(14.dp))

            // rejilla de presets (2 columnas)
            MACRO_PRESETS.chunked(2).forEach { rowP ->
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowP.forEach { pr ->
                        PresetChip(pr.emoji, pr.label, pr.label == selected) { applyPreset(pr) }
                    }
                    if (rowP.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(6.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(Pal.Card).padding(16.dp),
            ) {
                MacroSection("Proteínas", pp, target, false) { setMacro(0, it) }
                Spacer(Modifier.height(16.dp))
                MacroSection(if (keto) "Carbs Netos" else "Carbohidratos", cc, target, false) { setMacro(1, it) }
                Spacer(Modifier.height(16.dp))
                MacroSection("Grasas", ff, target, true) { setMacro(2, it) }

                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Objetivo", color = Pal.Sub, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text("${milesM(target)} kcal", color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.width(18.dp))
                    Text("$sumPct %", color = if (valid) Pal.Green else Pal.Red,
                        fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            Spacer(Modifier.height(18.dp))
            YellowSaveButton(enabled = valid) {
                Logic.setMacroRatios(pp, cc, ff)
                onDone()
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun MacroSection(title: String, pct: Double, target: Int, isFat: Boolean, onPct: (Double) -> Unit) {
    val kcal = target * pct / 100.0
    val g = if (isFat) kcal / 9.0 else kcal / 4.0
    Column(Modifier.fillMaxWidth()) {
        Text(title, color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Slider(
            value = pct.toFloat(),
            onValueChange = { onPct(it.toDouble()) },
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
                thumbColor = Pal.Green,
                activeTrackColor = Pal.Green,
                inactiveTrackColor = Pal.Track,
            ),
            modifier = Modifier.fillMaxWidth().height(28.dp),
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ValueCell("${g.roundToInt()} g")
            ValueCell("${kcal.roundToInt()} kcal")
            ValueCell("${pct.roundToInt()} %")
        }
    }
}

@Composable
private fun RowScope.ValueCell(text: String) {
    Box(
        Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(14.dp))
            .border(1.dp, Pal.Border, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Pal.Text, fontSize = 14.sp)
    }
}

@Composable
private fun RowScope.PresetChip(emoji: String, label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp))
            .background(if (active) Pal.Yellow.copy(alpha = 0.22f) else Pal.Card2)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp)) {
            EmojiIcon(emoji, size = 18.dp)
            Spacer(Modifier.width(8.dp))
            Text(label, color = Pal.Text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------------------
// Hoja "Ajustar Calorías"
// ---------------------------------------------------------------------------
/** Hoja "Ajustar Calorías" (estilo Fitia): slider de kcal, ETA de peso y toggles. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigCaloriesDialog(onDone: () -> Unit, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val auto = remember { Logic.autoKcal() }
    val prof = remember { Db.getProfile() }
    val cur0 = remember { Logic.targets()?.kcal ?: auto ?: 2000 }

    val center = auto ?: cur0
    val lo = (center * 0.65).roundToInt()
    val hi = (center * 1.35).roundToInt()

    var kcal by remember { mutableStateOf(cur0.coerceIn(lo, hi)) }
    var autoAdj by remember { mutableStateOf(Db.getSetting("cal_auto_adjust") == "1") }
    var perDay by remember { mutableStateOf(Db.getSetting("cal_per_day") == "1") }

    val initialAuto = remember { autoAdj }
    val initialPerDay = remember { perDay }
    val changed = kcal != cur0 || autoAdj != initialAuto || perDay != initialPerDay

    // ETA de peso: usa la TDEE de mantenimiento y el déficit/superávit elegido.
    val etaText: String? = remember(kcal) {
        val af = prof?.activity?.let { Nutrition.ACTIVITY[it] }
        val w = prof?.weight; val h = prof?.height; val age = prof?.age; val tw = prof?.targetWeight
        if (af == null || w == null || h == null || age == null || tw == null) return@remember null
        val tdee = Nutrition.tdee(prof.sex, w, h, age.toInt(), af)
        val kgPerWeek = abs(kcal - tdee) * 7 / 7700.0
        if (kgPerWeek < 1e-4 || abs(w - tw) < 0.1) return@remember null
        val weeks = maxOf(1, ceil(abs(w - tw) / kgPerWeek).toInt())
        val twS = if (tw % 1.0 == 0.0) tw.toInt().toString() else tw.toString()
        "$twS|$weeks"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = Pal.Bg,
        dragHandle = null,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
            SheetHeader("Ajustar Calorías", onDismiss)
            Spacer(Modifier.height(16.dp))

            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(Pal.Card).padding(18.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center) {
                    Text("${milesM(kcal)} kcal", color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.ChevronRight, null, tint = Pal.Sub, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = kcal.toFloat(),
                    onValueChange = { kcal = (it / 10f).roundToInt() * 10 },
                    valueRange = lo.toFloat()..hi.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = Pal.Green,
                        activeTrackColor = Pal.Green,
                        inactiveTrackColor = Pal.Track,
                    ),
                    modifier = Modifier.fillMaxWidth().height(28.dp),
                )
                Row(Modifier.fillMaxWidth()) {
                    Text("Reducir", color = Pal.Sub, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("Recomendado", color = Pal.Sub, fontSize = 12.sp)
                    Text("Aumentar", color = Pal.Sub, fontSize = 12.sp, modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }

                if (etaText != null) {
                    val (twS, weeks) = etaText.split("|")
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        EmojiIcon("📅", size = 18.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Llegarás a $twS kg en", color = Pal.Text, fontSize = 14.sp,
                            modifier = Modifier.weight(1f))
                        Text("$weeks semanas", color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }

                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Pal.Card2))
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Ajuste automático", color = Pal.Text, fontSize = 14.sp)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.Info, null, tint = Pal.Sub, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.weight(1f))
                    GreenSwitch(autoAdj) { autoAdj = it }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(Pal.Card).padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Personalizar por Día", color = Pal.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                GreenSwitch(perDay) { perDay = it }
            }

            Spacer(Modifier.height(18.dp))
            YellowSaveButton(enabled = changed) {
                Db.setSetting("cal_auto_adjust", if (autoAdj) "1" else "0")
                Db.setSetting("cal_per_day", if (perDay) "1" else "0")
                if (autoAdj) {
                    Db.setSetting("profile_mode", "auto")
                } else {
                    Db.setSetting("profile_mode", "manual")
                    Db.setSetting("profile_manual_kcal", kcal.toString())
                }
                onDone()
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun GreenSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = Pal.Green,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = Pal.Card2,
            uncheckedBorderColor = Pal.Border,
        ),
    )
}

// ---- comunes --------------------------------------------------------------

@Composable
private fun SheetHeader(title: String, onClose: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
        Text(title, color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Box(
            Modifier.align(Alignment.CenterEnd).size(32.dp).clip(CircleShape)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Close, null, tint = Pal.Text, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun YellowSaveButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp))
            .background(if (enabled) Pal.Yellow else Pal.Card2)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("Guardar cambios", color = if (enabled) Pal.Bg else Pal.Sub,
            fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}
