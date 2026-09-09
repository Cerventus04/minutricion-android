package org.ivansola.minutricion.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.BakeryDining
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Fastfood
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.Grass
import androidx.compose.material.icons.rounded.LunchDining
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.toColorInt
import org.ivansola.minutricion.data.Db
import org.ivansola.minutricion.data.Logic
import org.ivansola.minutricion.data.Nutriest
import org.ivansola.minutricion.data.Source
import org.ivansola.minutricion.ui.components.AppCard
import org.ivansola.minutricion.ui.components.ScoreGauge
import org.ivansola.minutricion.ui.components.SegBar
import org.ivansola.minutricion.ui.theme.Pal
import java.time.LocalDate
import kotlin.math.roundToInt

private val MESES = listOf("ene", "feb", "mar", "abr", "may", "jun",
    "jul", "ago", "sep", "oct", "nov", "dic")
private val MESES_FULL = listOf("Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio",
    "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre")

private fun periodLabel(period: String, ref: LocalDate): String = when (period) {
    "Mes" -> "${MESES_FULL[ref.monthValue - 1]} ${ref.year}"
    "Semana" -> {
        val mon = ref.minusDays((ref.dayOfWeek.value - 1).toLong())
        val sun = mon.plusDays(6)
        if (mon.monthValue == sun.monthValue) "${mon.dayOfMonth}–${sun.dayOfMonth} ${MESES[mon.monthValue - 1]}"
        else "${mon.dayOfMonth} ${MESES[mon.monthValue - 1]} – ${sun.dayOfMonth} ${MESES[sun.monthValue - 1]}"
    }
    else -> {
        val t = LocalDate.now()
        when (ref) {
            t -> "Hoy"
            t.minusDays(1) -> "Ayer"
            else -> "${ref.dayOfMonth} ${MESES[ref.monthValue - 1]} ${ref.year}"
        }
    }
}

/** Fila de nutriente: (clave, nombre, unidad, valor, objetivo, tipo). */
private data class StatRow(
    val key: String, val name: String, val unit: String,
    val value: Double, val goal: Double?, val kind: String,
)

// Emoji por nutriente (mismos que Kivy _STAT_EMOJI).
private val STAT_EMOJI: Map<String, String> = mapOf(
    "kcal" to "⚡", "protein" to "🥩", "carbs" to "🥔", "fat" to "🥑", "fiber" to "🌾",
    "sat_fat" to "🧈", "trans_fat" to "🍟", "sugars" to "🧊", "salt" to "🧂", "sodium" to "🧂",
)

/** Pantalla de Score Nutricional completa: pestañas Día/Semana/Mes + secciones plegables. */
@Composable
fun ScoreDetailScreen(onClose: () -> Unit) {
    var period by remember { mutableStateOf("Día") }
    var ref by remember { mutableStateOf(LocalDate.now()) }
    val meals = remember { Db.getMeals() }
    val targets = remember { Logic.targets() }

    var detail by remember { mutableStateOf<StatRow?>(null) }
    var detailSources by remember { mutableStateOf<List<Source>>(emptyList()) }
    var openVit by remember { mutableStateOf(false) }
    var openMin by remember { mutableStateOf(false) }

    fun shift(delta: Long) {
        ref = when (period) {
            "Mes" -> ref.withDayOfMonth(1).plusMonths(delta)
            "Semana" -> ref.plusWeeks(delta)
            else -> ref.plusDays(delta)
        }
    }
    val shiftState = rememberUpdatedState<(Long) -> Unit> { shift(it) }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Pal.Bg)
                .pointerInput(Unit) {
                    val threshold = 56.dp.toPx(); var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            if (total < -threshold) shiftState.value(1)
                            else if (total > threshold) shiftState.value(-1)
                        },
                        onHorizontalDrag = { _, dx -> total += dx },
                    )
                },
        ) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    TopBar("Score Nutricional", onClose)
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PeriodTabs(period) { period = it }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center) {
                            NavArrow(Icons.Rounded.ChevronLeft) { shift(-1) }
                            Text(periodLabel(period, ref), color = Pal.Text,
                                fontWeight = FontWeight.Bold, fontSize = 15.sp,
                                modifier = Modifier.padding(horizontal = 10.dp))
                            NavArrow(Icons.Rounded.ChevronRight) { shift(1) }
                        }
                    }
                }
                item {
                    AnimatedContent(
                        targetState = period to ref, contentKey = { it },
                        transitionSpec = {
                            val fwd = targetState.second.isAfter(initialState.second)
                            val d = if (fwd) 1 else -1
                            (slideInHorizontally(tween(260)) { d * it } + fadeIn(tween(260))) togetherWith
                                (slideOutHorizontally(tween(260)) { -d * it } + fadeOut(tween(260)))
                        },
                        label = "score",
                    ) { (p, r) ->
                        ScoreBody(
                            p, r, meals, targets, openVit, openMin,
                            onToggleVit = { openVit = !openVit }, onToggleMin = { openMin = !openMin },
                            onDetail = { row, src -> detail = row; detailSources = src },
                        )
                    }
                }
            }
        }
    }

    detail?.let { r ->
        NutrientDetailSheet(
            name = r.name, unit = r.unit, consumed = r.value, goal = r.goal, kind = r.kind,
            sources = detailSources, periodLabel = periodLabel(period, ref),
            isDay = period == "Día",
            onDismiss = { detail = null },
        )
    }
}

/** Cuerpo del Score (gauge + secciones) para un periodo/fecha concretos (recalcula sus datos). */
@Composable
private fun ScoreBody(
    period: String,
    ref: LocalDate,
    meals: List<String>,
    targets: org.ivansola.minutricion.data.Targets?,
    openVit: Boolean,
    openMin: Boolean,
    onToggleVit: () -> Unit,
    onToggleMin: () -> Unit,
    onDetail: (StatRow, List<Source>) -> Unit,
) {
    val agg = remember(period, ref) { Logic.aggregate(period, ref, meals) }
    val score = remember(agg, targets) { Logic.nutriScore(agg.summary, targets, agg.nutrients) }
    val (scoreColorHex, scoreLabel) = Logic.nutriStyle(score)
    val mainRows = listOf(
        StatRow("kcal", "Calorías", "kcal", agg.summary.kcal, targets?.kcal?.toDouble(), "kcal"),
        StatRow("protein", "Proteínas", "g", agg.summary.protein, targets?.protein?.toDouble(), "reach"),
        StatRow("carbs", "Carbohidratos", "g", agg.summary.carbs, targets?.carbs?.toDouble(), "reach"),
        StatRow("fat", "Grasas", "g", agg.summary.fat, targets?.fat?.toDouble(), "reach"),
        StatRow("fiber", "Fibra", "g", agg.nutrients["fiber"] ?: 0.0, Logic.FIBER_GOAL, "reach"),
    )
    val limitRows = Nutriest.LIMIT_INFO.map { (k, nm, u) ->
        StatRow(k, nm, u, agg.nutrients[k] ?: 0.0, Logic.LIMIT_REF[k], "limit")
    }
    val vitRows = Nutriest.VIT_INFO.map { (k, nm, u) -> StatRow(k, nm, u, agg.nutrients[k] ?: 0.0, Nutriest.NRV[k], "reach") }
    val minRows = Nutriest.MIN_INFO.map { (k, nm, u) -> StatRow(k, nm, u, agg.nutrients[k] ?: 0.0, Nutriest.NRV[k], "reach") }
    fun src(key: String) = agg.sources[key].orEmpty()

    val hasData = agg.summary.kcal > 0 || agg.summary.protein > 0 ||
        agg.summary.carbs > 0 || agg.summary.fat > 0
    if (!hasData) {
        Box(Modifier.fillMaxWidth().height(260.dp).padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center) {
            Text("Información no disponible", color = Pal.Sub, fontSize = 15.sp)
        }
        return
    }

    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScoreGauge(score, Color(scoreColorHex.toColorInt()), scoreLabel,
            Modifier.fillMaxWidth().height(200.dp))
        if (period != "Día") {
            Text("Media de ${agg.days} día(s) con registro", color = Pal.Sub,
                fontSize = 11.sp, modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        SecLabel("Nutrientes Principales")
        mainRows.forEach { r -> StatRowView(r) { onDetail(r, src(r.key)) } }
        SecLabel("Nutrientes a limitar")
        limitRows.forEach { r -> StatRowView(r) { onDetail(r, src(r.key)) } }
        SecLabel("Micronutrientes")
        CollapsibleSection("Vitaminas", "🍊", vitRows, openVit, onToggleVit) { r -> onDetail(r, src(r.key)) }
        CollapsibleSection("Minerales", "💎", minRows, openMin, onToggleMin) { r -> onDetail(r, src(r.key)) }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun TopBar(title: String, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(Pal.Card2)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.ArrowBackIosNew, null, tint = Pal.Text, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(title, color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

@Composable
private fun NavArrow(icon: ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(28.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Pal.Sub, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun PeriodTabs(selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp))
        .background(Pal.Card2).padding(3.dp)) {
        listOf("Día", "Semana", "Mes").forEach { opt ->
            val active = opt == selected
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp))
                    .background(if (active) Pal.Yellow else Color.Transparent)
                    .clickable { onSelect(opt) },
                contentAlignment = Alignment.Center,
            ) {
                Text(opt, color = if (active) Pal.Bg else Pal.Sub,
                    fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun SecLabel(text: String) {
    Text(text, color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
}

@Composable
private fun StatRowView(r: StatRow, onClick: () -> Unit) {
    val (txt, colHex) = Logic.status(r.kind, r.value, r.goal)
    val col = Color(colHex.toColorInt())
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Pal.Card)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)
            .height(46.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        org.ivansola.minutricion.ui.components.EmojiIcon(STAT_EMOJI[r.key] ?: "🔹", size = 22.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(r.name, color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(txt, color = col, fontSize = 11.sp)
        }
        if (r.kind == "limit" && r.goal != null) {
            Column(horizontalAlignment = Alignment.End) {
                Text("${Logic.fmtNum(r.value)} ${r.unit}", color = Pal.Text,
                    fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Máx ${Logic.fmtNum(r.goal)} ${r.unit}", color = Pal.Sub, fontSize = 11.sp)
            }
        } else {
            val txt2 = if (r.goal != null) "${Logic.fmtNum(r.value)} / ${Logic.fmtNum(r.goal)} ${r.unit}"
                       else "${Logic.fmtNum(r.value)} ${r.unit}"
            Text(txt2, color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun CollapsibleSection(
    title: String,
    emoji: String,
    rows: List<StatRow>,
    open: Boolean,
    onToggle: () -> Unit,
    onRowClick: (StatRow) -> Unit,
) {
    val reds = rows.count { Logic.rowColor(it.kind, it.value, it.goal) == Logic.RED }
    val subTxt = if (reds > 0) "Muy bajo en $reds nutrientes" else "En objetivo"
    val subCol = if (reds > 0) Pal.Red else Pal.Green
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Pal.Card),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            org.ivansola.minutricion.ui.components.EmojiIcon(emoji, size = 22.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(subTxt, color = subCol, fontSize = 11.sp)
            }
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null,
                tint = Pal.Sub, modifier = Modifier.size(22.dp))
        }
        if (open) {
            Box(Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 8.dp).background(Color(0xFF2A2A2F)))
            Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                rows.forEach { r -> NutriRow(r) { onRowClick(r) } }
            }
        }
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun NutriRow(r: StatRow, onClick: () -> Unit) {
    val colHex = Logic.rowColor(r.kind, r.value, r.goal)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)
            .padding(horizontal = 10.dp).height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(Color(colHex.toColorInt())))
        Spacer(Modifier.width(10.dp))
        Text(r.name, color = Pal.Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
        val txt = if (r.goal != null) "${Logic.fmtNum(r.value)}/${Logic.fmtNum(r.goal)} ${r.unit}" else "${Logic.fmtNum(r.value)} ${r.unit}"
        Text(txt, color = Pal.Sub, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

/** Hoja de detalle de un nutriente: consumido/meta, estado, SegBar de tramos y "Tus Fuentes". */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun NutrientDetailSheet(
    name: String, unit: String, consumed: Double, goal: Double?, kind: String,
    sources: List<Source>, periodLabel: String, isDay: Boolean,
    onDismiss: () -> Unit,
) {
    val isLimit = kind == "limit"
    val (badge, badgeColHex, frac) = if (isLimit) {
        when {
            goal == null || consumed <= goal -> Triple("En rango", Logic.GREEN, if (goal != null) (consumed / goal).coerceAtMost(1.0) else 0.0)
            consumed <= goal * 1.25 -> Triple("Alto", Logic.YELLOW, 1.0)
            else -> Triple("Muy alto", Logic.RED, 1.0)
        }
    } else {
        val f = if (goal != null && goal > 0) (consumed / goal).coerceAtMost(1.0) else 0.0
        when {
            goal != null && goal > 0 && consumed >= goal * 0.95 -> Triple("Objetivo cumplido", Logic.GREEN, f)
            goal != null && goal > 0 && consumed >= goal * 0.5 -> Triple("Vas bien", Logic.YELLOW, f)
            else -> Triple("Muy bajo", Logic.RED, f)
        }
    }
    val metaTxt = if (goal != null) {
        if (isLimit) "Meta: como máximo ${Logic.fmtNum(goal)} $unit" else "Meta: al menos ${Logic.fmtNum(goal)} $unit"
    } else "Sin objetivo"
    val title = (if (isDay) "Consumo Diario" else "Consumo Medio") + " de $name"

    val zones: List<Color>
    val active: Int
    if (isLimit) {
        zones = listOf(Pal.Green, Pal.Yellow, Pal.Red)
        active = if (goal == null || consumed <= goal) 0 else if (consumed <= goal * 1.25) 1 else 2
    } else {
        zones = listOf(Pal.Red, Pal.Yellow, Pal.Green, Pal.Yellow, Pal.Red)
        val r = if (goal != null && goal > 0) consumed / goal else 0.0
        active = when { r < 0.5 -> 0; r < 0.85 -> 1; r < 1.15 -> 2; r < 1.5 -> 3; else -> 4 }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Pal.Bg) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            AppCard(padding = 16.dp) {
                Text("Consumido", color = Pal.Sub, fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text("${Logic.fmtNum(consumed)} $unit", color = Pal.Text, fontWeight = FontWeight.Bold,
                    fontSize = 24.sp, modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text(metaTxt, color = Pal.Sub, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.clip(RoundedCornerShape(12.dp))
                            .background(Color(badgeColHex.toColorInt()).copy(alpha = 0.16f))
                            .padding(horizontal = 14.dp, vertical = 5.dp),
                    ) {
                        Text(badge, color = Color(badgeColHex.toColorInt()),
                            fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                SegBar(zones, active, Modifier.fillMaxWidth())
            }
            if (sources.isNotEmpty()) {
                Text("Tus Fuentes", color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                val total = sources.sumOf { it.amount }.let { if (it <= 0) 1.0 else it }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    sources.take(12).forEach { s ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                                org.ivansola.minutricion.ui.components.FoodEmoji(s.name, size = 22.dp)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(s.name, color = Pal.Text, fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
                            Text("${Logic.fmtNum(s.amount)} $unit  (${(s.amount / total * 100).roundToInt()}%)",
                                color = Pal.Sub, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
