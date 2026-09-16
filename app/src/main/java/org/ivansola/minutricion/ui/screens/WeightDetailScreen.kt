package org.ivansola.minutricion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import org.ivansola.minutricion.data.Db
import org.ivansola.minutricion.data.Logic
import org.ivansola.minutricion.ui.components.WeightChart
import org.ivansola.minutricion.ui.components.raised
import org.ivansola.minutricion.ui.components.BtnYellowTop
import org.ivansola.minutricion.ui.components.BtnYellowBottom
import org.ivansola.minutricion.ui.components.BtnDarkTop
import org.ivansola.minutricion.ui.components.BtnDarkBottom
import org.ivansola.minutricion.ui.theme.Pal
import java.time.LocalDate

/** Detalle de Peso: periodos 3M/12M/Máx, peso balanza/promedio, gráfica e historial con borrar. */
@Composable
fun WeightDetailScreen(onClose: () -> Unit) {
    var refresh by remember { mutableIntStateOf(0) }
    var period by remember { mutableStateOf("3M") }
    var showDialog by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<String?>(null) }
    val today = remember { LocalDate.now() }

    val allWeights = remember(refresh) { Db.getWeights() }
    val profile = remember { Db.getProfile() }
    val curW = allWeights.lastOrNull()?.second ?: profile?.weight
    val curDay = allWeights.lastOrNull()?.first

    val cutoff = when (period) {
        "3M" -> today.minusDays(90).toString()
        "12M" -> today.minusDays(365).toString()
        else -> null
    }
    var filtered = allWeights.filter { cutoff == null || it.first >= cutoff }
    if (filtered.size < 2 && allWeights.size >= 2) filtered = allWeights.takeLast(2)
    val series = filtered.map { it.second }
    val ema = if (series.size >= 2) Logic.ema(series) else emptyList()

    DetailScreen("Peso", onClose) {
        Segmented3(listOf("3M", "12M", "Máx"), period) { period = it }
        if (curDay != null) {
            Text(fechaLarga(LocalDate.parse(curDay)), color = Pal.Sub, fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        Row(Modifier.fillMaxWidth().height(52.dp)) {
            StatCol(curW, "Peso Balanza", Pal.Yellow, Modifier.weight(1f))
            StatCol(ema.lastOrNull() ?: curW, "Peso Promedio", Pal.Avg, Modifier.weight(1f))
        }
        if (series.size >= 2) {
            WeightChart(series, ema, Modifier.fillMaxWidth().height(220.dp),
                dates = filtered.map { it.first })
        }
        PillButtonW("Actualizar Progreso") { showDialog = true }
        Text("Historial", color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        allWeights.reversed().forEach { (day, kg) ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Pal.Card)
                    .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(fechaLarga(LocalDate.parse(day)), color = Pal.Text, fontSize = 13.sp,
                    modifier = Modifier.weight(1f))
                Text("${fmtKg(kg)} kg", color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Icon(Icons.Rounded.DeleteOutline, null, tint = Pal.Sub,
                    modifier = Modifier.padding(start = 10.dp).size(22.dp).clip(CircleShape)
                        .clickable { deleting = day })
            }
        }
    }

    if (showDialog) {
        WeightSheet(curW, curDay,
            onSave = { kg -> Db.setWeight(today.toString(), kg); refresh++; showDialog = false },
            onDismiss = { showDialog = false })
    }
    deleting?.let { day ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = Pal.Card2,
            title = { Text("Eliminar registro", color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = { Text("¿Eliminar el peso de ${fechaLarga(LocalDate.parse(day))}?", color = Pal.Sub, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { Db.deleteWeight(day); refresh++; deleting = null }) {
                    Text("Eliminar", color = Pal.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar", color = Pal.Sub) } },
        )
    }
}

@Composable
private fun StatCol(value: Double?, label: String, dotColor: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(if (value != null) "${fmtKg(value)} kg" else "— kg", color = Pal.Text,
            fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
            Spacer(Modifier.width(4.dp))
            Text(label, color = Pal.Sub, fontSize = 11.sp)
        }
    }
}

@Composable
private fun Segmented3(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp))
        .background(Pal.Card2).padding(3.dp)) {
        options.forEach { opt ->
            val active = opt == selected
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp))
                    .background(if (active) Pal.Yellow else Color.Transparent)
                    .clickable { onSelect(opt) },
                contentAlignment = Alignment.Center,
            ) {
                Text(opt, color = if (active) Pal.Bg else Pal.Sub, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun PillButtonW(text: String, onClick: () -> Unit) {
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

