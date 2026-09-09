package org.ivansola.minutricion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.ivansola.minutricion.data.Db
import org.ivansola.minutricion.ui.components.MealBadge
import org.ivansola.minutricion.ui.components.raised
import org.ivansola.minutricion.ui.components.BtnYellowTop
import org.ivansola.minutricion.ui.components.BtnYellowBottom
import org.ivansola.minutricion.ui.components.BtnDarkTop
import org.ivansola.minutricion.ui.components.BtnDarkBottom
import org.ivansola.minutricion.ui.theme.Pal

/** Añadir, renombrar y eliminar las comidas configurables del Diario. */
@Composable
fun MealsConfigScreen(onClose: () -> Unit, onChanged: () -> Unit) {
    var refresh by remember { mutableIntStateOf(0) }
    val meals = remember(refresh) { Db.getMeals() }
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }

    fun reload() { refresh++; onChanged() }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Pal.Bg)) {
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
                Text("Comidas", color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                meals.forEach { meal ->
                    MealRow(meal, onRename = { renaming = meal }, onDelete = { deleting = meal })
                }
                PillButton("Añadir comida") { adding = true }
            }
        }
    }

    if (adding) {
        NamePrompt("Nueva comida", "", "Nombre de la comida",
            onConfirm = { v -> Db.setMeals(meals + v); reload(); adding = false },
            onDismiss = { adding = false })
    }
    renaming?.let { meal ->
        NamePrompt("Renombrar comida", meal, "Nombre de la comida",
            onConfirm = { v ->
                if (v != meal) {
                    Db.setMeals(meals.map { if (it == meal) v else it })
                    Db.renameMeal(meal, v)
                    reload()
                }
                renaming = null
            },
            onDismiss = { renaming = null })
    }
    deleting?.let { meal ->
        val n = remember(meal) { Db.countMealEntries(meal) }
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = Pal.Card2,
            title = { Text("Eliminar comida", color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Text(
                    if (n > 0) "«$meal» tiene $n registro(s); quedarán ocultos." else "¿Quitar «$meal»?",
                    color = Pal.Sub, fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (meals.size > 1) {
                        Db.setMeals(meals.filter { it != meal })
                        reload()
                    }
                    deleting = null
                }) { Text("Quitar", color = Pal.Red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar", color = Pal.Sub) } },
        )
    }
}

@Composable
private fun MealRow(meal: String, onRename: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Pal.Card)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MealBadge(meal, size = 28.dp)
        Spacer(Modifier.width(10.dp))
        Text(meal, color = Pal.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.Edit, null, tint = Pal.Sub,
            modifier = Modifier.size(20.dp).clip(CircleShape).clickable(onClick = onRename))
        Spacer(Modifier.width(14.dp))
        Icon(Icons.Rounded.Delete, null, tint = Pal.Red,
            modifier = Modifier.size(20.dp).clip(CircleShape).clickable(onClick = onDelete))
    }
}

@Composable
private fun PillButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(46.dp)
            .raised(RoundedCornerShape(23.dp), BtnDarkTop, BtnDarkBottom, highlight = 0.13f)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Add, null, tint = Pal.Text, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun NamePrompt(title: String, initial: String, hint: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pal.Card2,
        title = { Text(title, color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            OutlinedTextField(
                value = value, onValueChange = { value = it },
                placeholder = { Text(hint, color = Pal.Sub) },
                singleLine = true,
                colors = fieldColors(),
            )
        },
        confirmButton = {
            TextButton(enabled = value.isNotBlank(), onClick = { onConfirm(value.trim()) }) {
                Text("Guardar", color = Pal.Yellow, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Pal.Sub) } },
    )
}
