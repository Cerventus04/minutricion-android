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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.ivansola.minutricion.data.Db
import org.ivansola.minutricion.data.Entry
import org.ivansola.minutricion.data.Food
import org.ivansola.minutricion.ui.theme.Pal
import java.time.LocalDate
import kotlin.math.roundToInt

/** Biblioteca de alimentos del usuario: buscar, crear, editar, eliminar y añadir al día de hoy. */
@Composable
fun AlimentosScreen(onClose: () -> Unit) {
    var refresh by remember { mutableIntStateOf(0) }
    val all = remember(refresh) { Db.listFoods() }
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Food?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Food?>(null) }
    var detail by remember { mutableStateOf<Food?>(null) }
    val meals = remember { Db.getMeals() }
    val today = remember { LocalDate.now() }

    val filtered = remember(all, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) all else all.filter { it.name.lowercase().contains(q) }
    }

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
                Text("Mis alimentos", color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                    modifier = Modifier.weight(1f))
                Icon(
                    Icons.Rounded.Add, null, tint = Pal.Yellow,
                    modifier = Modifier.size(26.dp).clickable { creating = true },
                )
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("Buscar en tu biblioteca…", color = Pal.Sub) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Pal.Sub) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = fieldColors(),
            )
            Spacer(Modifier.height(6.dp))
            Text("${filtered.size} alimentos", color = Pal.Sub, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp))
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                items(filtered, key = { it.id }) { food ->
                    FoodLibraryRow(
                        food,
                        onClick = { detail = food },
                        onEdit = { editing = food },
                        onDelete = { deleting = food },
                    )
                }
            }
        }
    }

    detail?.let { food ->
        FoodDetailScreen(
            food = food, meals = meals, initialMeal = meals.first(), day = today, entry = null,
            onConfirm = { f, grams, meal ->
                Db.addEntry(Entry(0, today.toString(), meal, f.name, grams, f.kcal, f.protein,
                    f.carbs, f.fat, true, f.nutrients))
                detail = null
            },
            onDismiss = { detail = null },
        )
    }
    if (creating) {
        FoodEditDialog(
            initial = null,
            onSave = { f -> Db.upsertFood(f, userCreated = true); refresh++; creating = false },
            onDismiss = { creating = false },
        )
    }
    editing?.let { food ->
        FoodEditDialog(
            initial = food,
            onSave = { f -> Db.upsertFood(f); refresh++; editing = null },
            onDismiss = { editing = null },
        )
    }
    deleting?.let { food ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = Pal.Card2,
            title = { Text("Eliminar alimento", color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = { Text("¿Eliminar «${food.name}» de tu biblioteca?", color = Pal.Sub, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { Db.deleteFood(food.id); refresh++; deleting = null }) {
                    Text("Eliminar", color = Pal.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar", color = Pal.Sub) } },
        )
    }
}

@Composable
private fun FoodLibraryRow(food: Food, onClick: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
            org.ivansola.minutricion.ui.components.FoodEmoji(food.name, size = 24.dp)
        }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(food.name, color = Pal.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("${food.kcal.roundToInt()} kcal · 100 g", color = Pal.Sub, fontSize = 11.sp)
        }
        Box {
            Icon(Icons.Rounded.MoreVert, null, tint = Pal.Sub,
                modifier = Modifier.size(22.dp).clip(CircleShape).clickable { menu = true })
            org.ivansola.minutricion.ui.components.AppDropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Editar", color = Pal.Text, modifier = Modifier.width(120.dp)) },
                    trailingIcon = { Icon(Icons.Rounded.Edit, null, tint = Pal.Text) },
                    onClick = { menu = false; onEdit() },
                )
                DropdownMenuItem(
                    text = { Text("Eliminar", color = Pal.Red, modifier = Modifier.width(120.dp)) },
                    trailingIcon = { Icon(Icons.Rounded.Delete, null, tint = Pal.Red) },
                    onClick = { menu = false; onDelete() },
                )
            }
        }
    }
}

/** Crear o editar un alimento propio (valores por 100 g). Al editar, el nombre no se toca. */
@Composable
private fun FoodEditDialog(initial: Food?, onSave: (Food) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var kcal by remember { mutableStateOf(initial?.kcal?.roundToInt()?.toString() ?: "") }
    var p by remember { mutableStateOf(initial?.protein?.roundToInt()?.toString() ?: "") }
    var c by remember { mutableStateOf(initial?.carbs?.roundToInt()?.toString() ?: "") }
    var f by remember { mutableStateOf(initial?.fat?.roundToInt()?.toString() ?: "") }
    fun d(s: String) = s.replace(",", ".").toDoubleOrNull() ?: 0.0
    val valid = name.isNotBlank() && d(kcal) > 0
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pal.Card2,
        title = {
            Text(if (initial != null) "Editar alimento" else "Crear alimento",
                color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column {
                EditField("Nombre", name, KeyboardType.Text, enabled = initial == null) { name = it }
                Spacer(Modifier.height(6.dp))
                EditField("Calorías / 100 g", kcal, KeyboardType.Number) { kcal = it }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.weight(1f)) { EditField("Prot", p, KeyboardType.Number) { p = it } }
                    Box(Modifier.weight(1f)) { EditField("Carbs", c, KeyboardType.Number) { c = it } }
                    Box(Modifier.weight(1f)) { EditField("Grasa", f, KeyboardType.Number) { f = it } }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(Food(initial?.id ?: 0, name.trim(), d(kcal), d(p), d(c), d(f), initial?.nutrients ?: emptyMap()))
            }) { Text(if (initial != null) "Guardar" else "Crear", color = if (valid) Pal.Yellow else Pal.Sub, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Pal.Sub) } },
    )
}

@Composable
private fun EditField(label: String, value: String, keyboard: KeyboardType, enabled: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { s ->
            onChange(if (keyboard == KeyboardType.Number) s.filter { it.isDigit() || it == '.' || it == ',' } else s)
        },
        label = { Text(label, color = Pal.Sub) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
        colors = fieldColors(),
    )
}
