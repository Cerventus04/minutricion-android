package org.ivansola.minutricion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ivansola.minutricion.data.Db
import org.ivansola.minutricion.ui.components.MealBadge
import org.ivansola.minutricion.ui.theme.Pal

private val EXTRAS = listOf("Desayuno", "Comida", "Merienda", "Cena", "Snack 1", "Snack 2")

/** Editar Vista: interruptores para mostrar/ocultar cada comida del diario (no borra registros). */
@Composable
fun EditViewScreen(onClose: () -> Unit, onChanged: () -> Unit) {
    var refresh by remember { mutableIntStateOf(0) }
    val meals = remember(refresh) { Db.getMeals() }
    val catalog = remember(refresh) {
        val out = meals.toMutableList()
        EXTRAS.forEach { if (it !in out) out.add(it) }
        out
    }

    fun toggle(meal: String, on: Boolean) {
        val cur = Db.getMeals()
        val next = when {
            on && meal !in cur -> catalog.filter { it in cur || it == meal }
            !on && meal in cur -> if (cur.size <= 1) cur else cur.filter { it != meal }
            else -> cur
        }
        Db.setMeals(next)
        refresh++
        onChanged()
    }

    DetailScreen("Editar Vista", onClose) {
        Text("Comidas", color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp,
            modifier = Modifier.padding(start = 4.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Pal.Card)
                .padding(vertical = 6.dp),
        ) {
            catalog.forEach { meal ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Switch(
                        checked = meal in meals,
                        onCheckedChange = { toggle(meal, it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Pal.Bg,
                            checkedTrackColor = Pal.Yellow,
                            uncheckedThumbColor = Pal.Sub,
                            uncheckedTrackColor = Pal.Card2,
                            uncheckedBorderColor = Pal.Border,
                        ),
                    )
                    Spacer(Modifier.width(10.dp))
                    MealBadge(meal, size = 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(meal, color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
        Text(
            "Al ocultar una comida sus registros no se borran: se muestran de nuevo al reactivarla.",
            color = Pal.Sub, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
