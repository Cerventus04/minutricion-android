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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ivansola.minutricion.data.Db
import org.ivansola.minutricion.data.Logic
import org.ivansola.minutricion.ui.components.AppCard
import org.ivansola.minutricion.ui.components.CalorieBars
import org.ivansola.minutricion.ui.theme.Pal
import java.time.LocalDate
import kotlin.math.roundToInt

private fun miles(n: Int) = "%,d".format(n).replace(",", ".")

/** Detalle de Calorías: navegación por semana, barras con valores/eje y lista por día. */
@Composable
fun CaloriesDetailScreen(onClose: () -> Unit) {
    var weekOffset by remember { mutableIntStateOf(0) }
    val today = remember { LocalDate.now() }
    val meals = remember { Db.getMeals() }
    val targets = remember { Logic.targets() }

    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong()).plusWeeks(weekOffset.toLong())
    val week = (0..6).map { monday.plusDays(it.toLong()) }
    val totals = remember(weekOffset) { Db.dailyTotals(week.first().toString(), week.last().toString(), meals) }
    val kvals = week.map { (totals[it.toString()]?.kcal ?: 0.0).roundToInt() }
    val logged = kvals.filter { it > 0 }
    val avg = if (logged.isEmpty()) 0 else (logged.sum().toDouble() / logged.size).roundToInt()
    val lo = targets?.let { (it.kcal * 0.9).roundToInt() } ?: 0
    val hi = targets?.let { (it.kcal * 1.1).roundToInt() } ?: 0
    val hiIdx = if (weekOffset == 0) today.dayOfWeek.value - 1 else -1

    DetailScreen("Calorías", onClose, swipe = { dir ->
        if (dir > 0) weekOffset-- else if (weekOffset < 0) weekOffset++
    }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NavArrowC(Icons.Rounded.ChevronLeft, tint = Pal.Text) { weekOffset-- }
            Text("${fechaDm(week.first())} – ${fechaDm(week.last())}", color = Pal.Text,
                fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f))
            NavArrowC(Icons.Rounded.ChevronRight, tint = if (weekOffset >= 0) Pal.Sub else Pal.Text) {
                if (weekOffset < 0) weekOffset++
            }
        }
        AppCard(padding = 16.dp) {
            Text("Promedio Semanal", color = Pal.Sub, fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Text("${miles(avg)} kcal", color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 24.sp,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            if (targets != null && avg > 0) {
                val (st, sc) = when {
                    avg in lo..hi -> "En objetivo" to Pal.Green
                    avg > hi -> "Por encima" to Pal.Red
                    else -> "Por debajo" to Pal.Yellow
                }
                Text(st, color = sc, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Text("Objetivo: ${miles(lo)} - ${miles(hi)} kcal", color = Pal.Sub, fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(6.dp))
            CalorieBars(kvals, lo, hi, hiIdx, Modifier.fillMaxWidth().height(220.dp))
        }
        Text("Por día", color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        week.forEachIndexed { i, d ->
            val v = kvals[i]
            val col = when {
                v > 0 && lo > 0 && hi > 0 -> if (v in lo..hi) Pal.Green else if (v > hi) Pal.Red else Pal.Yellow
                else -> Pal.Text
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Pal.Card)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(diaCorto(d), color = Pal.Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(if (v > 0) "$v kcal" else "—", color = col, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun NavArrowC(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
    }
}
