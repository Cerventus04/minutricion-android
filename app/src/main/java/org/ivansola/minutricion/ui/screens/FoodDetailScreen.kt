package org.ivansola.minutricion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ivansola.minutricion.data.Db
import org.ivansola.minutricion.data.Entry
import org.ivansola.minutricion.data.Food
import org.ivansola.minutricion.data.FoodComponent
import org.ivansola.minutricion.data.Logic
import org.ivansola.minutricion.data.Nutriest
import org.ivansola.minutricion.data.Off
import org.ivansola.minutricion.data.Targets
import org.ivansola.minutricion.ui.components.MealBadge
import org.ivansola.minutricion.ui.components.isDrink
import org.ivansola.minutricion.ui.theme.MicroColor
import org.ivansola.minutricion.ui.components.raisedYellow
import org.ivansola.minutricion.ui.theme.Pal
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Ficha de alimento (estilo Fitia, portada de FoodDetailScreen en Kivy): icono+sello, 4 tiles
 * por 100 g, distribución de macros, "Información Nutricional" y "Micronutrientes" plegables,
 * y pie fijo con cantidad + total + CTA (Añadir/Actualizar con selector de comida).
 */
@Composable
fun FoodDetailScreen(
    food: Food,
    meals: List<String>,
    initialMeal: String,
    day: LocalDate,
    entry: Entry?,
    onConfirm: (Food, Double, String) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val current by remember(food.name) { mutableStateOf(food) }
    FoodDetailContent(
        food = current, meals = meals, initialMeal = initialMeal, day = day, entry = entry,
        onConfirm = onConfirm, onDelete = onDelete, onDismiss = onDismiss,
    )
}

/** Normaliza para comparar nombres (minúsculas, sin acentos). */
private fun normName(s: String?): String {
    val n = java.text.Normalizer.normalize((s ?: "").lowercase(), java.text.Normalizer.Form.NFD)
    return n.filter { it.category != CharCategory.NON_SPACING_MARK }.trim()
}

/**
 * Re-descarga los datos del alimento desde Open Food Facts (por código de barras o por nombre con
 * coincidencia estricta) y los fusiona sin tocar los macros. Persiste en la biblioteca. Se ejecuta
 * en un hilo IO. Devuelve el Food actualizado o null si no hubo coincidencia/red.
 */
/**
 * Vuelve a pedir el alimento a Open Food Facts. Era lo que hacía el botón "Actualizar datos" de la
 * barra superior, que se quitó a petición: se conserva para poder volver a engancharlo (a un menú,
 * por ejemplo) sin reescribirlo.
 */
@Suppress("unused")
private fun refetchFood(f: Food): Food? {
    val prod = if (!f.barcode.isNullOrBlank()) {
        Off.byBarcode(f.barcode)
    } else {
        val q = f.name.substringBefore(" (").trim()
        if (q.length < 3) null else {
            val country = Db.getSetting("food_country", "es") ?: "es"
            val match = Off.search(q, country, 10)
                .firstOrNull { normName(it.label) == normName(f.name) || normName(it.name) == normName(q) }
            if (match != null && match.code.isNotBlank()) Off.byBarcode(match.code) ?: match else match
        }
    } ?: return null

    val enriched = f.copy(
        serving = prod.servingG ?: f.serving,
        nutrients = if (prod.nutrients.isNotEmpty()) prod.nutrients else f.nutrients,
        ingredients = prod.ingredients.ifBlank { f.ingredients },
        allergens = prod.allergens.ifEmpty { f.allergens },
        categories = prod.categories.ifEmpty { f.categories },
    )
    Db.upsertFood(enriched)
    Db.setFoodBackfilled(f.name)
    return enriched
}

@Composable
private fun FoodDetailContent(
    food: Food,
    meals: List<String>,
    initialMeal: String,
    day: LocalDate,
    entry: Entry?,
    onConfirm: (Food, Double, String) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    // Detección de bebida por nombre O por categoría de OFF (más fiable) → unidad ml + icono bebida.
    val drink = isDrink(food.name) ||
        org.ivansola.minutricion.ui.components.isDrinkByCategory(food.categories)
    val unit = if (drink) "ml" else "g"
    // Porciones estilo Fitia: ración (si OFF la trae) + unidad base + 100 unidades.
    val portions = remember(food.serving, unit) {
        buildList {
            food.serving?.let { add(Portion("ración ($it $unit)", it)) }
            // nombre completo de la unidad: "g" a secas no dice si es peso o volumen
            add(Portion(if (unit == "ml") "mililitros" else "gramos", 1.0))
            // Si la ración del alimento YA son 100 g, "ración (100 g)" y "100 g" serían la misma
            // opción escrita de dos formas: se deja solo la ración.
            if (food.serving != 100.0) add(Portion("100 $unit", 100.0))
        }
    }
    val hasServing = food.serving != null
    val hundredIdx = portions.indexOfFirst { it.grams == 100.0 }.coerceAtLeast(0)
    val baseIdx = portions.indexOfFirst { it.grams == 1.0 }.coerceAtLeast(0)
    fun fmtQ(v: Double) = if (v % 1.0 == 0.0) v.roundToInt().toString() else v.toString()
    // Última cantidad usada de este alimento (solo al añadir, no al editar una entrada existente).
    val lastQty = remember(food.name) { if (entry == null) Db.lastQty(food.name) else null }
    // Prioridad del valor por defecto: entrada > última cantidad usada > ración > 100 unidades.
    var portionIdx by remember { mutableIntStateOf(
        when { entry != null -> baseIdx; lastQty != null -> baseIdx; hasServing -> 0; else -> hundredIdx }) }
    var qty by remember { mutableStateOf(
        when { entry != null -> fmtQ(entry.grams); lastQty != null -> fmtQ(lastQty); else -> "1" }) }
    val portion = portions[portionIdx]
    val g = (qty.replace(",", ".").toDoubleOrNull() ?: 0.0) * portion.grams
    // Datos de referencia (tiles): por ración si OFF la trae, si no por 100 unidades.
    val tileBasis = food.serving ?: 100.0
    val tileLabel = if (hasServing) "Datos por ración (${Logic.fmtNum(food.serving!!)} $unit)"
        else "Datos por 100 $unit"
    val tf = tileBasis / 100.0
    var selMeal by remember { mutableStateOf(initialMeal) }
    var mealMenu by remember { mutableStateOf(false) }
    var infoOpen by remember { mutableStateOf(false) }
    var microOpen by remember { mutableStateOf(false) }
    var fav by remember { mutableStateOf(Db.isFavorite(food.name)) }
    val verified = remember(food.name) { food.name !in Db.userCreatedNames() }
    val targets = remember { Logic.targets() }
    // Surtido: selector de componente. La ficha (tiles/tabla/registro) muestra el componente
    // elegido con su nombre; por defecto, el primero. Un alimento normal no tiene componentes.
    val comps = food.components
    var selComp by remember(food.name) { mutableIntStateOf(if (comps.isNotEmpty()) 0 else -1) }
    val view = if (selComp in comps.indices) {
        val c = comps[selComp]
        food.copy(name = "${food.name} — ${c.name}", kcal = c.kcal, protein = c.protein,
            carbs = c.carbs, fat = c.fat, nutrients = c.nutrients, components = emptyList())
    } else food

    // Solo datos reales de la base de datos: si un nutriente no está, se deja vacío (no se estima).
    val est = view.nutrients
    fun per100(key: String): Double? = when (key) {
        "kcal" -> view.kcal; "protein" -> view.protein; "carbs" -> view.carbs; "fat" -> view.fat
        else -> est[key]
    }

    val headerTint = org.ivansola.minutricion.ui.components.rememberFoodColor(
        food.name, drink, food.category)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(Pal.Bg)
                // Banda superior teñida con el color del alimento, que se funde con el fondo hacia
                // abajo: da ambiente a la ficha sin tocar la legibilidad del contenido.
                .drawBehind {
                    drawRect(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            0f to headerTint.copy(alpha = 0.42f),
                            0.55f to headerTint.copy(alpha = 0.16f),
                            1f to Color.Transparent,
                            endY = size.height * 0.34f,
                        ),
                        size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.34f),
                    )
                }
        ) {
            TopBar(food.name, onDismiss, fav) { fav = Db.toggleFavorite(food.name) }
            Box(Modifier.weight(1f)) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            IconBadge(food.name, verified, drink, food.category)
                            if (comps.isNotEmpty()) {
                                ComponentSelector(comps, selComp) { selComp = it }
                            }
                            Text(tileLabel, color = Pal.Sub, fontSize = 11.sp,
                                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Tile("${(view.kcal * tf).roundToInt()}", "kcal", Modifier.weight(1f))
                                Tile(Logic.fmtNum(view.protein * tf) + " g", "proteínas", Modifier.weight(1f))
                                Tile(Logic.fmtNum(view.carbs * tf) + " g", "carbs", Modifier.weight(1f))
                                Tile(Logic.fmtNum(view.fat * tf) + " g", "grasas", Modifier.weight(1f))
                            }
                            MacroDistribution(view)
                            InfoSection(infoOpen, { infoOpen = !infoOpen }, g, targets, ::per100)
                            MicroSection(microOpen, { microOpen = !microOpen }, g, ::per100)
                            IngredientsSection(food)
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
            Footer(
                qty = qty, onQtyChange = { qty = it }, unit = unit,
                portions = portions, portionIdx = portionIdx,
                // Al cambiar a "ración" o "100 g" (porciones cuya unidad no es 1), la cantidad vuelve a 1.
                onPortion = { portionIdx = it; if (portions[it].grams != 1.0) qty = "1" },
                food = view, g = g,
                entry = entry, meals = meals, selMeal = selMeal,
                mealMenu = mealMenu, onMealMenuChange = { mealMenu = it },
                onSelMeal = { selMeal = it },
                onDelete = onDelete,
                // registra el componente elegido (nombre "Surtido X — Componente" y sus macros).
                onConfirm = { if (g > 0) onConfirm(view, g, selMeal) },
            )
        }
    }
}

@Composable
private fun TopBar(title: String, onClose: () -> Unit, fav: Boolean, onFav: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Sin disco de fondo: así se ve la banda del color del alimento por detrás de la flecha.
        Box(
            Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.ArrowBackIosNew, null, tint = Pal.Text, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(title, color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1,
            modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        HeartToggle(fav, onFav)
    }
}

@Composable
private fun IconBadge(name: String, verified: Boolean, drink: Boolean, category: String?) {
    Box(Modifier.fillMaxWidth().height(84.dp), contentAlignment = Alignment.TopCenter) {
        // El fondo se tiñe con el color predominante del propio icono (a baja opacidad, para no
        // competir con él): así cada alimento tiene su ambiente en vez de un gris igual para todos.
        // El icono va sobre un cuadro OSCURO: el color del alimento lo pone la banda del fondo,
        // y repetirlo aquí dejaría el emoji sin contraste contra su propio color.
        Box(
            Modifier.size(66.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF1C1C1E)),
            contentAlignment = Alignment.Center,
        ) {
            org.ivansola.minutricion.ui.components.FoodEmoji(name, size = 38.dp, drink = drink, category = category)
        }
        if (verified) {
            Box(
                Modifier.padding(top = 56.dp).size(22.dp).clip(CircleShape).background(Pal.Bg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Verified, null, tint = Pal.Yellow, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun Tile(value: String, label: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).border(0.7.dp, Color(0xFF3A3A40), RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(label, color = Pal.Sub, fontSize = 11.sp)
    }
}

/** Selector de componente de un surtido: una fila de chips con el nombre de cada tabla. */
@Composable
private fun ComponentSelector(comps: List<FoodComponent>, sel: Int, onSel: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("Surtido · elige el producto", color = Pal.Sub, fontSize = 11.sp)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            comps.forEachIndexed { i, c ->
                val on = i == sel
                Text(
                    c.name, color = if (on) Pal.Bg else Pal.Text, fontSize = 12.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp))
                        .background(if (on) Pal.Yellow else Pal.Card2)
                        .clickable { onSel(i) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun MacroDistribution(food: Food) {
    val pk = food.protein * 4; val ck = food.carbs * 4; val fk = food.fat * 9
    val tot = (pk + ck + fk).let { if (it <= 0) 1.0 else it }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().height(10.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            listOf(pk to Pal.Protein, ck to Pal.Carbs, fk to Pal.Fat).forEach { (v, col) ->
                val frac = (v / tot).toFloat().coerceIn(0f, 1f)
                if (frac > 0f) Box(
                    Modifier.weight(frac.coerceAtLeast(0.001f)).fillMaxHeight()
                        .clip(RoundedCornerShape(5.dp)).background(col)
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf(Triple("Proteínas", pk, Pal.Protein), Triple("Carbs", ck, Pal.Carbs),
                Triple("Grasas", fk, Pal.Fat)).forEach { (label, v, col) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(col))
                    Spacer(Modifier.width(5.dp))
                    Text("$label ${(v / tot * 100).roundToInt()}%", color = Pal.Text, fontSize = 12.sp)
                }
            }
        }
    }
}

private fun targetGoal(key: String, targets: Targets?): Double? = targets?.let {
    when (key) {
        "kcal" -> it.kcal.toDouble(); "fat" -> it.fat.toDouble()
        "carbs" -> it.carbs.toDouble(); "protein" -> it.protein.toDouble()
        else -> null
    }
}

@Composable
private fun ExpandCard(
    title: String, open: Boolean, onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Pal.Card)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                modifier = Modifier.weight(1f))
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = Pal.Sub)
        }
        if (open) Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp).padding(bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), content = { content() })
    }
}

@Composable
private fun InfoSection(
    open: Boolean, onToggle: () -> Unit, grams: Double, targets: Targets?, per100: (String) -> Double?,
) {
    ExpandCard("Información Nutricional", open, onToggle) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Porción", color = Pal.Text, fontSize = 13.sp)
            Text("${grams.roundToInt()} g", color = Pal.Sub, fontSize = 13.sp)
        }
        NutrientBar("Calorías", "kcal", "target", "kcal", Pal.Yellow, grams, targets, per100, bold = true)
        NutrientBar("Grasas Totales", "fat", "target", "g", Pal.Fat, grams, targets, per100, bold = true)
        NutrientBar("Grasas Saturadas", "sat_fat", "nrv", "g", Pal.Fat, grams, targets, per100, indent = 14.dp)
        NutrientBar("Grasas Trans", "trans_fat", "nrv", "g", Pal.Fat, grams, targets, per100, indent = 14.dp)
        NutrientBar("Colesterol", "cholesterol", "nrv", "mg", Pal.Fat, grams, targets, per100, indent = 14.dp)
        NutrientBar("Carbohidratos Totales", "carbs", "target", "g", Pal.Carbs, grams, targets, per100, bold = true)
        NutrientBar("Azúcares", "sugars", "nrv", "g", Pal.Carbs, grams, targets, per100, indent = 14.dp)
        // Solo si OFF los trae (no se muestran con "—" para no ensuciar).
        if (per100("added_sugars") != null)
            NutrientBar("Azúcares añadidos", "added_sugars", "nrv", "g", Pal.Carbs, grams, targets, per100, indent = 28.dp)
        NutrientBar("Fibra", "fiber", "nrv", "g", Pal.Carbs, grams, targets, per100, indent = 14.dp)
        NutrientBar("Proteínas", "protein", "target", "g", Pal.Protein, grams, targets, per100, bold = true)
        NutrientBar("Sal", "salt", "nrv", "g", Pal.Protein, grams, targets, per100, indent = 14.dp)
        if (per100("sodium") != null)
            NutrientBar("Sodio", "sodium", "nrv", "mg", Pal.Protein, grams, targets, per100, indent = 28.dp)
        if (per100("caffeine") != null)
            NutrientBar("Cafeína", "caffeine", "nrv", "mg", Pal.Sub, grams, targets, per100)
    }
}

/** Ingredientes y alérgenos REALES de OFF (solo si el producto los trae). */
@Composable
private fun IngredientsSection(food: Food) {
    if (food.ingredients.isBlank() && food.allergens.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    ExpandCard("Ingredientes", open, { open = !open }) {
        if (food.ingredients.isNotBlank()) {
            Text(food.ingredients, color = Pal.Sub, fontSize = 12.sp, lineHeight = 17.sp)
        }
        if (food.allergens.isNotEmpty()) {
            Text("Alérgenos", color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(food.allergens.joinToString(", "), color = Pal.Red, fontSize = 12.sp)
        }
    }
}

@Composable
private fun MicroSection(open: Boolean, onToggle: () -> Unit, grams: Double, per100: (String) -> Double?) {
    // Solo se muestran los micronutrientes que la fuente (OFF) realmente aporta.
    val vits = Nutriest.VIT_INFO.filter { per100(it.first) != null }
    val mins = Nutriest.MIN_INFO.filter { per100(it.first) != null }
    ExpandCard("Micronutrientes", open, onToggle) {
        if (vits.isEmpty() && mins.isEmpty()) {
            Text("Sin datos de micronutrientes", color = Pal.Sub, fontSize = 12.sp)
            return@ExpandCard
        }
        if (vits.isNotEmpty()) {
            Text("Vitaminas", color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            vits.forEach { (k, n, u) ->
                NutrientBar(n, k, "nrv", u, MicroColor[k] ?: Pal.Green, grams, null, per100)
            }
        }
        if (mins.isNotEmpty()) {
            Text("Minerales", color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            mins.forEach { (k, n, u) ->
                NutrientBar(n, k, "nrv", u, MicroColor[k] ?: Color(0xFF3AA0E5), grams, null, per100)
            }
        }
    }
}

@Composable
private fun NutrientBar(
    name: String, key: String, kind: String, unit: String, color: Color,
    grams: Double, targets: Targets?, per100: (String) -> Double?,
    indent: Dp = 0.dp, bold: Boolean = false,
) {
    val raw = per100(key)
    // Sin dato real en la base de datos: se muestra "—" y sin barra (no se inventa el valor).
    if (raw == null) {
        Column(Modifier.fillMaxWidth().padding(start = indent)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(name, color = if (indent > 0.dp) Color(0xFFC9C9CF) else Pal.Text,
                    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp)
                Text("—", color = Pal.Sub, fontSize = 11.sp)
            }
        }
        return
    }
    val v = raw * grams / 100.0
    val (text, frac) = if (kind == "target") {
        val goal = targetGoal(key, targets)
        if (goal != null && goal > 0) {
            "${Logic.fmtNum(v)} / ${Logic.fmtNum(goal)} $unit   ·   ${(v / goal * 100).roundToInt()}%" to
                (v / goal).coerceIn(0.0, 1.0).toFloat()
        } else "${Logic.fmtNum(v)} $unit" to 0f
    } else {
        val nrv = Nutriest.NRV[key]
        if (nrv != null && nrv > 0) {
            "${Logic.fmtNum(v)} $unit   ·   ${(v / nrv * 100).roundToInt()}%" to (v / nrv).coerceIn(0.0, 1.0).toFloat()
        } else "${Logic.fmtNum(v)} $unit" to 0f
    }
    Column(Modifier.fillMaxWidth().padding(start = indent)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, color = if (indent > 0.dp) Color(0xFFC9C9CF) else Pal.Text,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp)
            Text(text, color = Pal.Sub, fontSize = 11.sp)
        }
        Spacer(Modifier.height(3.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Pal.Track)) {
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(CircleShape).background(color))
        }
    }
}

/** Una porción seleccionable en la ficha: etiqueta visible y su equivalencia en g/ml. */
private data class Portion(val label: String, val grams: Double)

@Composable
private fun Footer(
    qty: String, onQtyChange: (String) -> Unit, unit: String,
    portions: List<Portion>, portionIdx: Int, onPortion: (Int) -> Unit,
    food: Food, g: Double,
    entry: Entry?, meals: List<String>, selMeal: String,
    mealMenu: Boolean, onMealMenuChange: (Boolean) -> Unit, onSelMeal: (String) -> Unit,
    onDelete: (() -> Unit)?,
    onConfirm: () -> Unit,
) {
    var portionMenu by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().background(Pal.Bg).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Campos estilo Fitia: "Cantidad" (número) + "Porción" (selector), en cajas redondeadas.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(0.42f)) {
                Text("Cantidad", color = Pal.Sub, fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
                Row(
                    Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp))
                        .background(Pal.Card2).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = qty,
                        onValueChange = { s -> onQtyChange(s.filter { it.isDigit() || it == '.' || it == ',' }) },
                        singleLine = true,
                        textStyle = TextStyle(color = Pal.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                        cursorBrush = SolidColor(Pal.Yellow),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Column(Modifier.weight(0.58f)) {
                Text("Porción", color = Pal.Sub, fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
                Box {
                    Row(
                        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp))
                            .background(Pal.Card2).clickable { portionMenu = true }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(portions[portionIdx].label, color = Pal.Text, fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.KeyboardArrowDown, null, tint = Pal.Sub, modifier = Modifier.size(22.dp))
                    }
                    org.ivansola.minutricion.ui.components.AppDropdownMenu(portionMenu, { portionMenu = false }) {
                        portions.forEachIndexed { i, p ->
                            DropdownMenuItem(
                                text = {
                                    Text(p.label, color = Pal.Text, textAlign = TextAlign.Center,
                                        modifier = Modifier.width(150.dp))
                                },
                                onClick = { onPortion(i); portionMenu = false },
                            )
                        }
                    }
                }
            }
        }
        val f = g / 100.0
        Text(
            "Total para ${g.roundToInt()} $unit:   ${(food.kcal * f).roundToInt()} kcal  ·  " +
                "${(food.protein * f).roundToInt()} P  ·  ${(food.carbs * f).roundToInt()} C  ·  " +
                "${(food.fat * f).roundToInt()} G",
            color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 12.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onDelete != null) {
                Box(
                    Modifier.size(54.dp).clip(CircleShape).background(Color(0xFF5A181C))
                        .clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center,
                ) {
                    org.ivansola.minutricion.ui.components.AssetIcon("pepelera_alimento", size = 28.dp, recolor = Color(0xFFFF453A))
                }
            }
            Row(
                Modifier.weight(1f).height(54.dp).raisedYellow(RoundedCornerShape(27.dp)),
            ) {
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable(onClick = onConfirm),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (entry != null) "Actualizar" else "Añadir a $selMeal",
                        color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    )
                }
                Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 10.dp).background(Color(0x29000000)))
                Box(
                    Modifier.width(46.dp).fillMaxHeight().clickable { onMealMenuChange(true) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.KeyboardArrowDown, null, tint = Color.Black, modifier = Modifier.size(22.dp))
                    org.ivansola.minutricion.ui.components.AppDropdownMenu(mealMenu, { onMealMenuChange(false) }) {
                        meals.forEach { m ->
                            DropdownMenuItem(
                                leadingIcon = { MealBadge(m, size = 22.dp) },
                                text = { Text(m, color = Pal.Text, modifier = Modifier.width(190.dp)) },
                                onClick = { onSelMeal(m); onMealMenuChange(false) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Corazón de favorito que LATE: al marcarlo da dos pulsos (grande-pequeño-grande), como un
 * latido, en vez de cambiar de icono de golpe. Al desmarcarlo no late — el gesto de quitar algo
 * no merece celebración.
 */
@Composable
private fun HeartToggle(fav: Boolean, onToggle: () -> Unit) {
    val scale = remember { androidx.compose.animation.core.Animatable(1f) }
    var beats by remember { mutableStateOf(0) }
    LaunchedEffect(beats) {
        if (beats == 0) return@LaunchedEffect
        // dos latidos seguidos, el segundo algo más flojo
        for (peak in listOf(1.45f, 1.22f)) {
            scale.animateTo(peak, androidx.compose.animation.core.tween(120))
            scale.animateTo(1f, androidx.compose.animation.core.tween(160))
        }
    }
    val mod = Modifier
        .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
        .clickable { onToggle(); beats++ }
    if (fav) {
        org.ivansola.minutricion.ui.components.MdiIcon("heart", size = 24.dp, color = Pal.Red, modifier = mod)
    } else {
        org.ivansola.minutricion.ui.components.MdiIcon("heart-outline", size = 24.dp, color = Pal.Sub, modifier = mod)
    }
}
