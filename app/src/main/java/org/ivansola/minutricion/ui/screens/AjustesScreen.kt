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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ivansola.minutricion.data.Db
import org.ivansola.minutricion.data.Nutrition
import org.ivansola.minutricion.data.Profile
import org.ivansola.minutricion.ui.components.AppCard
import org.ivansola.minutricion.ui.components.raised
import org.ivansola.minutricion.ui.components.BtnYellowTop
import org.ivansola.minutricion.ui.components.BtnYellowBottom
import org.ivansola.minutricion.ui.components.BtnDarkTop
import org.ivansola.minutricion.ui.components.BtnDarkBottom
import org.ivansola.minutricion.ui.theme.Pal
import kotlin.math.roundToInt

private fun d(x: Double?): String =
    x?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: ""

private fun parse(s: String): Double? = s.replace(",", ".").toDoubleOrNull()

@Composable
fun AjustesScreen(contentPadding: PaddingValues, onSaved: () -> Unit = {}) {
    var showAlimentos by remember { mutableStateOf(false) }
    var showComidas by remember { mutableStateOf(false) }
    val p0 = remember { Db.getProfile() }
    var mode by remember { mutableStateOf(if (p0?.mode == "manual") "Manual" else "Automático") }
    var manualKcal by remember { mutableStateOf(d(p0?.manualKcal)) }
    var sex by remember { mutableStateOf(p0?.sex?.takeIf { it == "Hombre" || it == "Mujer" } ?: "Hombre") }
    var age by remember { mutableStateOf(d(p0?.age)) }
    var height by remember { mutableStateOf(d(p0?.height)) }
    var weight by remember { mutableStateOf(d(p0?.weight)) }
    var activity by remember { mutableStateOf(p0?.activity?.takeIf { it in Nutrition.ACTIVITY } ?: Nutrition.ACTIVITY.keys.first()) }
    var goal by remember { mutableStateOf(p0?.goal?.takeIf { it in Nutrition.GOALS } ?: "Mantenimiento") }
    var targetWeight by remember { mutableStateOf(d(p0?.targetWeight)) }
    val countries = remember { linkedMapOf("España" to "es", "Estados Unidos" to "us", "Mundial" to "world") }
    var country by remember {
        val code = Db.getSetting("food_country", "es") ?: "es"
        mutableStateOf(countries.entries.firstOrNull { it.value == code }?.key ?: "España")
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 16.dp, end = 16.dp,
                top = contentPadding.calculateTopPadding() + 10.dp,
                bottom = contentPadding.calculateBottomPadding() + 90.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Objetivo", color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 22.sp)

        Segmented(listOf("Automático", "Manual"), mode) { mode = it }
        NumField("Calorías objetivo (modo Manual)", manualKcal) { manualKcal = it }

        Divider("o cálculo automático con tus datos")

        Dropdown("Sexo", listOf("Hombre", "Mujer"), sex) { sex = it }
        NumField("Edad", age) { age = it }
        NumField("Altura (cm)", height) { height = it }
        NumField("Peso (kg)", weight) { weight = it }
        Dropdown("Actividad", Nutrition.ACTIVITY.keys.toList(), activity) { activity = it }
        Dropdown("Objetivo", Nutrition.GOALS.keys.toList(), goal) { goal = it }
        NumField("Peso objetivo (kg)", targetWeight) { targetWeight = it }

        TargetCard(mode, manualKcal, sex, age, height, weight, activity, goal)

        PillButton("Guardar perfil") {
            Db.saveProfile(
                Profile(
                    sex = sex,
                    age = parse(age),
                    height = parse(height),
                    weight = parse(weight),
                    activity = activity,
                    goal = goal,
                    mode = if (mode == "Manual") "manual" else "auto",
                    manualKcal = parse(manualKcal),
                    targetWeight = parse(targetWeight),
                )
            )
            onSaved()
        }

        Spacer(Modifier.height(8.dp))
        Text("Ajustes", color = Pal.Sub, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Dropdown("País de búsqueda de alimentos", countries.keys.toList(), country) { name ->
            country = name
            Db.setSetting("food_country", countries[name] ?: "es")
        }

        Spacer(Modifier.height(4.dp))
        Text("Gestión", color = Pal.Sub, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        LinkRow("Mis alimentos") { showAlimentos = true }
        LinkRow("Comidas") { showComidas = true }
    }

    if (showAlimentos) {
        AlimentosScreen(onClose = { showAlimentos = false })
    }
    if (showComidas) {
        MealsConfigScreen(onClose = { showComidas = false }, onChanged = {})
    }
}

@Composable
private fun LinkRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Pal.Card)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Pal.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text("›", color = Pal.Sub, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

/** Vista previa del objetivo calculado con los valores actuales del formulario. */
@Composable
private fun TargetCard(
    mode: String, manualKcal: String, sex: String,
    age: String, height: String, weight: String, activity: String, goal: String,
) {
    val kcal: Int? = if (mode == "Manual") {
        parse(manualKcal)?.takeIf { it > 0 }?.roundToInt()
    } else {
        val a = parse(age); val h = parse(height); val w = parse(weight)
        val af = Nutrition.ACTIVITY[activity]; val adj = Nutrition.GOALS[goal]
        if (a != null && h != null && w != null && af != null && adj != null)
            Nutrition.targetKcal(sex, w, h, a.toInt(), af, adj).roundToInt()
        else null
    }
    AppCard(padding = 16.dp) {
        if (kcal == null || kcal <= 0) {
            Text("Completa tus datos para calcular tu objetivo", color = Pal.Sub, fontSize = 13.sp)
        } else {
            val (p, c, f) = Nutrition.macrosFor(kcal.toDouble())
            Row(verticalAlignment = Alignment.Bottom) {
                Text("%,d".format(kcal).replace(",", "."),
                    color = Pal.Yellow, fontWeight = FontWeight.Bold, fontSize = 30.sp)
                Text(" kcal / día", color = Pal.Sub, fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 6.dp))
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("P ${p.roundToInt()}g", color = Pal.Protein, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("C ${c.roundToInt()}g", color = Pal.Carbs, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("G ${f.roundToInt()}g", color = Pal.Fat, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun Segmented(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(13.dp))
            .background(Pal.Card2).padding(3.dp),
    ) {
        options.forEach { opt ->
            val active = opt == selected
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(11.dp))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dropdown(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, color = Pal.Sub, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selected,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                colors = fieldColors(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt, color = Pal.Text, fontSize = 13.sp) },
                        onClick = { onSelect(opt); expanded = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun NumField(label: String, value: String, onChange: (String) -> Unit) {
    Column {
        Text(label, color = Pal.Sub, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = { s -> onChange(s.filter { it.isDigit() || it == '.' || it == ',' }) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors(),
        )
    }
}

@Composable
private fun Divider(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(Pal.Card2))
        Text(text, color = Pal.Sub, fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 8.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Pal.Card2))
    }
}

@Composable
private fun PillButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(46.dp)
            .raised(RoundedCornerShape(23.dp), BtnYellowTop, BtnYellowBottom,
                highlight = 0.40f, shade = 0.22f)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Pal.Bg, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}
