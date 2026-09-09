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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ivansola.minutricion.data.Db
import org.ivansola.minutricion.data.Logic
import org.ivansola.minutricion.ui.theme.Pal
import java.time.LocalDate

/** Mis Rachas: días registrados + días perfectos, y calendario mensual con puntos de estado. */
@Composable
fun StreaksScreen(onClose: () -> Unit, onPickDay: (LocalDate) -> Unit = {}) {
    val streaks = remember { Logic.streaks() }
    val today = remember { LocalDate.now() }
    val targets = remember { Logic.targets() }
    val meals = remember { Db.getMeals() }
    var month by remember { mutableStateOf(today.withDayOfMonth(1)) }

    DetailScreen("Mis Rachas", onClose) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Flame(Pal.Yellow, "Días Registrados", streaks.regCur, streaks.regBest, Modifier.weight(1f))
            Flame(Pal.Green, "Días Perfectos", streaks.perfCur, streaks.perfBest, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center) {
            NavArrowS(Icons.Rounded.ChevronLeft) { month = month.minusMonths(1) }
            Text("${mesFull(month.monthValue)} ${month.year}", color = Pal.Text,
                fontWeight = FontWeight.Bold, fontSize = 15.sp,
                modifier = Modifier.padding(horizontal = 12.dp))
            NavArrowS(Icons.Rounded.ChevronRight) { month = month.plusMonths(1) }
        }
        MonthCalendar(month, today, targets?.kcal, meals, onPickDay)
    }
}

@Composable
private fun Flame(color: Color, title: String, cur: Int, best: Int, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Pal.Card).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(Icons.Rounded.LocalFireDepartment, null, tint = color, modifier = Modifier.size(44.dp))
        Text(title, color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text("$cur", color = color, fontWeight = FontWeight.Bold, fontSize = 26.sp)
        Text("racha actual", color = Pal.Sub, fontSize = 10.sp)
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.EmojiEvents, null, tint = color, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
            Text("Mejor: $best", color = Pal.Sub, fontSize = 11.sp)
        }
    }
}

@Composable
private fun NavArrowS(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(30.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Pal.Sub, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun MonthCalendar(month: LocalDate, today: LocalDate, goalKcal: Int?, meals: List<String>,
                          onPickDay: (LocalDate) -> Unit) {
    val first = month.withDayOfMonth(1)
    val last = month.withDayOfMonth(month.lengthOfMonth())
    val totals = remember(month) { Db.dailyTotals(first.toString(), last.toString(), meals) }
    val logged = remember(month) { Db.loggedDays(first.toString(), last.toString()) }
    // La rejilla empieza en el lunes de la semana que contiene al día 1.
    val gridStart = first.minusDays((first.dayOfWeek.value - 1).toLong())
    val weeks = ((last.toEpochDay() - gridStart.toEpochDay()) / 7 + 1).toInt()

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Pal.Card)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            listOf("L", "M", "M", "J", "V", "S", "D").forEach { wd ->
                Text(wd, color = Pal.Sub, fontSize = 11.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f))
            }
            Text("kcal\nprom.", color = Pal.Sub, fontSize = 9.sp, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f))
        }
        for (w in 0 until weeks) {
            val weekStart = gridStart.plusDays((w * 7).toLong())
            val vals = ArrayList<Double>()
            Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
                for (i in 0..6) {
                    val d = weekStart.plusDays(i.toLong())
                    val inMonth = d.monthValue == month.monthValue
                    val iso = d.toString()
                    if (inMonth) totals[iso]?.let { vals.add(it.kcal) }
                    DayCell(d, inMonth, iso, today, goalKcal, totals, logged, onPickDay, Modifier.weight(1f))
                }
                val avg = if (vals.isNotEmpty()) "%,d".format((vals.sum() / vals.size).toInt()).replace(",", ".") else "—"
                Text(avg, color = if (vals.isNotEmpty()) Pal.Yellow else Color(0xFF3A3A3D),
                    fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DayCell(
    d: LocalDate, inMonth: Boolean, iso: String, today: LocalDate, goalKcal: Int?,
    totals: Map<String, org.ivansola.minutricion.data.Macros>, logged: Set<String>,
    onPickDay: (LocalDate) -> Unit, modifier: Modifier,
) {
    Column(
        modifier.let { if (inMonth) it.clip(RoundedCornerShape(10.dp)).clickable { onPickDay(d) } else it }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (!inMonth) { Spacer(Modifier.height(32.dp)); return@Column }
        val isToday = d == today
        Text("${d.dayOfMonth}", color = if (isToday) Pal.Yellow else Pal.Text,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal, fontSize = 11.sp)
        val t = totals[iso]
        val dotCol = when {
            goalKcal != null && t != null && Logic.kcalOk(t.kcal, goalKcal.toDouble()) -> Pal.Green
            iso in logged -> Pal.Yellow
            else -> Color(0xFF3A3A3D)
        }
        Box(Modifier.size(10.dp).clip(CircleShape).background(dotCol))
    }
}
