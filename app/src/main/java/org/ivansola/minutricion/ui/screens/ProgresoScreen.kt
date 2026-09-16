package org.ivansola.minutricion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.core.graphics.toColorInt
import org.ivansola.minutricion.data.Db
import org.ivansola.minutricion.data.Logic
import org.ivansola.minutricion.data.Macros
import org.ivansola.minutricion.data.Targets
import org.ivansola.minutricion.ui.components.AppCard
import org.ivansola.minutricion.ui.components.MacroRowBar
import org.ivansola.minutricion.ui.components.ScoreGauge
import org.ivansola.minutricion.ui.components.WeekBars
import org.ivansola.minutricion.ui.components.WeightChart
import org.ivansola.minutricion.ui.components.raised
import org.ivansola.minutricion.ui.components.BtnYellowTop
import org.ivansola.minutricion.ui.components.BtnYellowBottom
import org.ivansola.minutricion.ui.components.BtnDarkTop
import org.ivansola.minutricion.ui.components.BtnDarkBottom
import org.ivansola.minutricion.ui.theme.Pal
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

private fun miles(n: Int): String = "%,d".format(n).replace(",", ".")
// fmtKg vive en DetailScaffold.kt (compartido).

/** Estado renderizable de la pantalla de Progreso (sin dependencia de la BD). */
@Immutable
data class ProgresoState(
    val score: Int,
    val scoreColorHex: String,
    val scoreLabel: String,
    val goal: String?,
    val curWeight: Double?,
    val startWeight: Double?,
    val targetWeight: Double?,
    val weeks: Int?,
    val weeklyAvgKcal: Int,
    val weekKcal: List<Int>,
    val todayIndex: Int,
    val kcalLo: Int,
    val kcalHi: Int,
    val maxKcal: Int,
    val weightSeries: List<Double>,
    val weightEma: List<Double>,
    val weightDates: List<String>,
    val macroAvg: Macros,
    val targets: Targets?,
    /** Si hoy hay algún alimento registrado (para el score; si no, "Información no disponible"). */
    val scoreHasData: Boolean = false,
)

@Composable
fun ProgresoScreen(contentPadding: PaddingValues) {
    var refresh by remember { mutableIntStateOf(0) }
    var showWeight by remember { mutableStateOf(false) }
    var showScoreDetail by remember { mutableStateOf(false) }
    var showCaloriesDetail by remember { mutableStateOf(false) }
    var showWeightDetail by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now() }
    val state = remember(refresh) {
        val meals = Db.getMeals()
        val targets = Logic.targets()
        val totals = Db.dailyTotals(today.minusDays(90).toString(), today.toString(), meals)
        val todayTot = totals[today.toString()] ?: Macros()
        val nut = Logic.dayNutrientTotals(today.toString(), meals)
        val score = Logic.nutriScore(todayTot, targets, nut)
        val (colHex, label) = Logic.nutriStyle(score)

        val profile = Db.getProfile()
        val weights = Db.getWeights()
        val curW = weights.lastOrNull()?.second ?: profile?.weight
        val startW = weights.firstOrNull()?.second ?: profile?.weight
        val targetW = profile?.targetWeight
        val weeks = Logic.weeksEstimate(curW, targetW, profile?.goal)

        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val weekKcal = (0..6).map { (totals[monday.plusDays(it.toLong()).toString()]?.kcal ?: 0.0).roundToInt() }
        val logged = weekKcal.filter { it > 0 }
        val weeklyAvg = if (logged.isEmpty()) 0 else (logged.sum().toDouble() / logged.size).roundToInt()
        val lo = targets?.let { (it.kcal * 0.9).roundToInt() } ?: 0
        val hi = targets?.let { (it.kcal * 1.1).roundToInt() } ?: 0
        val maxV = (weekKcal + listOf(targets?.kcal ?: 0, 1)).max()

        val last7 = (0..6).map { today.minusDays(it.toLong()) }
        val loggedDays = last7.filter { (totals[it.toString()]?.kcal ?: 0.0) > 0 }
        val nn = loggedDays.size.coerceAtLeast(1)
        fun avg(sel: (Macros) -> Double) = loggedDays.sumOf { sel(totals[it.toString()] ?: Macros()) } / nn
        val macroAvg = Macros(0.0, avg { it.protein }, avg { it.carbs }, avg { it.fat })

        val last12 = weights.takeLast(12)
        val series = last12.map { it.second }
        val wdates = last12.map { it.first }
        val ema = if (series.size >= 2) Logic.ema(series) else emptyList()

        ProgresoState(
            score, colHex, label, profile?.goal, curW, startW, targetW, weeks,
            weeklyAvg, weekKcal, today.dayOfWeek.value - 1, lo, hi, maxV,
            series, ema, wdates, macroAvg, targets,
            scoreHasData = todayTot.kcal > 0,
        )
    }
    ProgresoContent(contentPadding, state,
        onUpdateWeight = { showWeight = true },
        onOpenScore = { showScoreDetail = true },
        onOpenCalories = { showCaloriesDetail = true },
        onOpenWeight = { showWeightDetail = true },
    )

    if (showWeight) {
        WeightSheet(
            current = state.curWeight,
            lastDay = remember(refresh) { Db.latestWeight()?.first },
            onSave = { kg -> Db.setWeight(today.toString(), kg); refresh++; showWeight = false },
            onDismiss = { showWeight = false },
        )
    }
    if (showScoreDetail) {
        ScoreDetailScreen(onClose = { showScoreDetail = false; refresh++ })
    }
    if (showCaloriesDetail) {
        CaloriesDetailScreen(onClose = { showCaloriesDetail = false })
    }
    if (showWeightDetail) {
        WeightDetailScreen(onClose = { showWeightDetail = false; refresh++ })
    }
}

@Composable
fun ProgresoContent(
    contentPadding: PaddingValues,
    state: ProgresoState,
    onUpdateWeight: () -> Unit = {},
    onOpenScore: () -> Unit = {},
    onOpenCalories: () -> Unit = {},
    onOpenWeight: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 12.dp, end = 12.dp,
                top = contentPadding.calculateTopPadding() + 10.dp,
                bottom = contentPadding.calculateBottomPadding() + 90.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Progreso", color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 22.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))

        Section("Score Nutricional")
        ScoreCard(state, onOpenScore)

        Section("Fase")
        FaseCard(state, onUpdateWeight)

        Section("Estadísticas")
        CaloriasCard(state, onOpenCalories)

        PesoCard(state, onOpenWeight)
        MacrosCard(state)
    }
}

@Composable
private fun Section(title: String) {
    Text(title, color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 17.sp,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp))
}

@Composable
private fun ScoreCard(state: ProgresoState, onClick: () -> Unit) {
    val color = Color(state.scoreColorHex.toColorInt())
    AppCard(padding = 16.dp, modifier = Modifier.clickable(onClick = onClick)) {
        Text("Hoy", color = Pal.Sub, fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(12.dp))   // el arco casi tocaba la palabra "Hoy"
        // Sin registros del día el medidor se enseña igual, a 0: así la tarjeta no cambia de forma
        // según la hora del día. Va en gris y con "Sin datos" en vez del color y la etiqueta del
        // score, porque un 0 rojo con su "Muy mejorable" diría que has comido mal, cuando lo que
        // pasa es que aún no has apuntado nada.
        ScoreGauge(
            score = if (state.scoreHasData) state.score else 0,
            color = if (state.scoreHasData) color else Pal.Track,
            label = if (state.scoreHasData) state.scoreLabel else "Sin datos",
            modifier = Modifier.fillMaxWidth().height(180.dp),
        )
    }
}

@Composable
private fun FaseCard(state: ProgresoState, onUpdateWeight: () -> Unit) {
    AppCard(padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.LocalFireDepartment, null, tint = Pal.Orange, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(state.goal?.substringBefore(" (") ?: "Mantenimiento",
                color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(state.curWeight?.let { "${fmtKg(it)} kg" } ?: "— kg",
                color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            val start = state.startWeight
            val cur = state.curWeight
            if (cur != null && start != null && abs(cur - start) >= 0.05) {
                val dl = cur - start
                val good = state.targetWeight != null && ((state.targetWeight < start) == (dl < 0))
                Text("  (${if (dl >= 0) "+" else ""}${fmtKg(dl)} kg)",
                    color = if (good) Pal.Green else Pal.Sub, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            if (state.targetWeight != null) {
                Spacer(Modifier.weight(1f))
                Icon(Icons.Rounded.TrackChanges, null, tint = Pal.Red, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("${fmtKg(state.targetWeight)} kg",
                    color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        val prog = if (state.curWeight != null && state.startWeight != null && state.targetWeight != null &&
            abs(state.targetWeight - state.startWeight) > 1e-6)
            ((state.curWeight - state.startWeight) / (state.targetWeight - state.startWeight))
                .toFloat().coerceIn(0f, 1f)
        else 0f
        ThinBar(prog, Pal.Yellow)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CalendarToday, null, tint = Pal.Sub, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(5.dp))
            Text("Llegarás en", color = Pal.Sub, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text(state.weeks?.let { "$it semanas" } ?: "—",
                color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))
        PillButton("Actualizar Progreso", onUpdateWeight)
    }
}

@Composable
private fun CaloriasCard(state: ProgresoState, onClick: () -> Unit) {
    AppCard(padding = 16.dp, modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Bolt, null, tint = Pal.Yellow, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Calorías", color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text("${miles(state.weeklyAvgKcal)} kcal", color = Pal.Text,
            fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text("Promedio Semanal", color = Pal.Sub, fontSize = 11.sp)
        Spacer(Modifier.height(10.dp))
        WeekBars(state.weekKcal, state.maxKcal, state.todayIndex, state.kcalLo, state.kcalHi,
            Modifier.fillMaxWidth().height(96.dp))
    }
}

@Composable
private fun PesoCard(state: ProgresoState, onClick: () -> Unit) {
    AppCard(padding = 16.dp, modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.MonitorWeight, null, tint = Pal.Yellow, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Peso Balanza", color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(state.curWeight?.let { "${fmtKg(it)} kg" } ?: "— kg",
            color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(8.dp))
        if (state.weightSeries.size >= 2) {
            WeightChart(state.weightSeries, state.weightEma, Modifier.fillMaxWidth().height(140.dp),
                dates = state.weightDates)
        } else {
            Text("Registra tu peso para ver la evolución", color = Pal.Sub, fontSize = 11.sp)
        }
    }
}

@Composable
private fun MacrosCard(state: ProgresoState) {
    AppCard(padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Restaurant, null, tint = Pal.Green, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Macros · media 7 días", color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))
        MacroAvgRow("Proteínas", state.macroAvg.protein, state.targets?.protein ?: 0, Pal.Protein)
        Spacer(Modifier.height(10.dp))
        MacroAvgRow("Carbohidratos", state.macroAvg.carbs, state.targets?.carbs ?: 0, Pal.Carbs)
        Spacer(Modifier.height(10.dp))
        MacroAvgRow("Grasas", state.macroAvg.fat, state.targets?.fat ?: 0, Pal.Fat)
    }
}

@Composable
private fun MacroAvgRow(name: String, avg: Double, goal: Int, color: Color) {
    val value = if (goal > 0) "${avg.roundToInt()} / $goal g" else "${avg.roundToInt()} g"
    val frac = if (goal > 0) (avg / goal).toFloat() else 0f
    MacroRowBar(name, value, frac, color)
}

@Composable
private fun ThinBar(frac: Float, color: Color) {
    Box(Modifier.fillMaxWidth().height(9.dp).clip(CircleShape).background(Pal.Track)) {
        Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(color))
    }
}

@Composable
private fun PillButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(44.dp)
            .raised(RoundedCornerShape(22.dp), BtnYellowTop, BtnYellowBottom,
                highlight = 0.40f, shade = 0.22f)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Pal.Bg, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
