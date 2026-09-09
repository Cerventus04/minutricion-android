package org.ivansola.minutricion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ivansola.minutricion.data.Db
import org.ivansola.minutricion.data.Food
import org.ivansola.minutricion.data.Off
import org.ivansola.minutricion.ui.components.isDrink
import org.ivansola.minutricion.ui.components.inset
import org.ivansola.minutricion.ui.components.TrackTop
import org.ivansola.minutricion.ui.components.TrackBottom
import org.ivansola.minutricion.ui.components.raisedDark
import org.ivansola.minutricion.ui.components.raisedYellow
import org.ivansola.minutricion.ui.theme.Pal
import java.time.LocalDate
import kotlin.math.roundToInt

private data class Disp(val food: Food, val grams: Double, val verified: Boolean)

/**
 * Deduplica por nombre (ignorando mayúsculas/espacios) conservando el ORDEN de la primera
 * aparición, pero quedándose con la ficha MÁS completa (más nutrientes) de cada duplicado. Así el
 * mismo producto local (parcial) + OFF (completo), o duplicados de OFF, aparecen una sola vez.
 */
private fun dedupByName(list: List<Disp>): List<Disp> {
    val index = HashMap<String, Int>()
    val out = ArrayList<Disp>()
    for (d in list) {
        val key = d.food.name.lowercase().trim().replace(Regex("\\s+"), " ")
        val at = index[key]
        if (at == null) {
            index[key] = out.size
            out.add(d)
        } else {
            // Fusiona ambas fichas quedándose con el valor MÁS completo de cada campo, en la misma
            // posición: así ingredientes/ración/categorías de una copia rellenan lo que falte en la otra.
            val cur = out[at]
            out[at] = Disp(
                food = mergeFood(cur.food, d.food),
                grams = cur.grams,
                verified = cur.verified || d.verified,
            )
        }
    }
    return out
}

/** Combina dos fichas del mismo alimento tomando el dato más completo de cada campo. */
private fun mergeFood(a: Food, b: Food): Food = a.copy(
    nutrients = if (b.nutrients.size > a.nutrients.size) b.nutrients else a.nutrients,
    serving = a.serving ?: b.serving,
    ingredients = a.ingredients.ifBlank { b.ingredients },
    allergens = a.allergens.ifEmpty { b.allergens },
    categories = a.categories.ifEmpty { b.categories },
    category = a.category ?: b.category,
    barcode = a.barcode ?: b.barcode,
)

private fun flagOf(code: String) = when (code) { "es" -> "🇪🇸"; "us" -> "🇺🇸"; else -> "🌍" }

/**
 * Vista "Añadir alimento" estilo Fitia (pantalla completa): píldora de búsqueda con bandera de
 * país, pestañas Base de Datos / Favoritos / Creados, recientes con sello verificado, y píldora
 * flotante Buscar / Escanear. Al elegir un alimento abre su ficha (FoodDetailScreen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodSheet(
    meal: String,
    meals: List<String>,
    day: LocalDate,
    foods: List<Food>,
    country: String,
    onAdd: (Food, Double, String) -> Unit,
    onAddMeal: (List<org.ivansola.minutricion.data.Entry>) -> Unit,
    onScan: () -> Unit,
    onDismiss: () -> Unit,
) {
    var tab by remember { mutableStateOf("Base de Datos") }
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<Food?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var showCrear by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }
    var offResults by remember { mutableStateOf<List<Food>>(emptyList()) }
    var offLoading by remember { mutableStateOf(false) }
    var countryCode by remember { mutableStateOf(country) }
    var countryMenu by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    // Crossfade "VER MÁS" como Kivy: la lista se desvanece, se reconstruye expandida y reaparece.
    val scope = rememberCoroutineScope()
    val listAlpha = remember { androidx.compose.animation.core.Animatable(1f) }
    val expandRecents: () -> Unit = {
        scope.launch {
            listAlpha.animateTo(0f, androidx.compose.animation.core.tween(150))
            expanded = true
            listAlpha.animateTo(1f, androidx.compose.animation.core.tween(320))
        }
    }

    // libRefresh se incrementa al crear un alimento para reconsultar la biblioteca (aparece en Creados).
    var libRefresh by remember { mutableStateOf(0) }
    val foodsLive = remember(libRefresh) { Db.listFoods() }
    val userCreated = remember(libRefresh) { Db.userCreatedNames() }
    val favorites = remember(libRefresh) { Db.getFavorites().toSet() }
    val recents = remember(libRefresh) { Db.recentFoods(25) }
    val recentMeals = remember(libRefresh) { Db.recentMeals(8) }
    // Índice por nombre para enriquecer los recientes con la ficha de biblioteca (ingredientes,
    // ración, categoría…), que no están en la entrada del diario.
    val libByName = remember(libRefresh) { foodsLive.associateBy { it.name } }

    LaunchedEffect(query, tab, countryCode) {
        if (tab != "Base de Datos") { offResults = emptyList(); offLoading = false; return@LaunchedEffect }
        val q = query.trim()
        if (q.length < 3) { offResults = emptyList(); offLoading = false; return@LaunchedEffect }
        offLoading = true
        delay(450)
        offResults = withContext(Dispatchers.IO) { Off.search(q, countryCode) }.map { it.toFood() }
        offLoading = false
    }

    val q = query.trim().lowercase()
    // Lista a mostrar según pestaña + búsqueda.
    val (sectionTitle, items) = when (tab) {
        "Creados" -> "CREADOS POR TI" to foodsLive.filter { it.name in userCreated }
            .filter { q.isEmpty() || it.name.lowercase().contains(q) }
            .map { Disp(it, 100.0, false) }
        "Favoritos" -> "FAVORITOS" to (foodsLive.filter { it.name in favorites } +
            recents.filter { it.name in favorites }.map { Food(0, it.name, it.kcal, it.protein, it.carbs, it.fat, it.nutrients) })
            .distinctBy { it.name }
            .filter { q.isEmpty() || it.name.lowercase().contains(q) }
            .map { Disp(it, 100.0, it.name !in userCreated) }
        else -> if (q.isEmpty()) {
            "INGRESADO RECIENTEMENTE" to recents.let { if (expanded) it else it.take(5) }
                .map { r ->
                    // Si el alimento está en la biblioteca, usa su ficha completa (con ingredientes,
                    // ración, etc.); si no, reconstrúyelo desde la entrada del diario.
                    val food = libByName[r.name] ?: Food(0, r.name, r.kcal, r.protein, r.carbs, r.fat, r.nutrients)
                    Disp(food, r.grams, r.name !in userCreated)
                }
        } else {
            // Local (biblioteca) + OFF, deduplicado por nombre quedándose con la ficha MÁS completa.
            val local = foodsLive.filter { it.name.lowercase().contains(q) }.map { Disp(it, 100.0, it.name !in userCreated) }
            val off = offResults.map { Disp(it, 100.0, true) }
            "RESULTADOS" to dedupByName(local + off)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Pal.Bg)) {
            Column(Modifier.fillMaxSize()) {
                // Cabecera
                Row(
                    Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.ArrowBackIosNew, null, tint = Pal.Text, modifier = Modifier.size(18.dp))
                    }
                    Text("Añadir a $meal", color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
                // Píldora de búsqueda + país + "..."
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(22.dp))
                            .background(Pal.Card2)
                            .border(0.7.dp, Pal.Border, RoundedCornerShape(22.dp))
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Search, null, tint = Pal.Sub, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.weight(1f)) {
                            if (query.isEmpty()) Text("Buscar alimentos", color = Pal.Sub, fontSize = 14.sp)
                            BasicTextField(
                                value = query, onValueChange = { query = it },
                                singleLine = true,
                                textStyle = TextStyle(color = Pal.Text, fontSize = 14.sp),
                                cursorBrush = SolidColor(Pal.Yellow),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Box {
                            Text(flagOf(countryCode), fontSize = 20.sp,
                                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { countryMenu = true })
                            org.ivansola.minutricion.ui.components.AppDropdownMenu(countryMenu, { countryMenu = false }) {
                                listOf("España" to "es", "Estados Unidos" to "us", "Mundial" to "world").forEach { (nm, code) ->
                                    DropdownMenuItem(
                                        text = { Text(nm, color = Pal.Text) },
                                        trailingIcon = { Text(flagOf(code), fontSize = 18.sp, modifier = Modifier.padding(start = 20.dp)) },
                                        onClick = { countryCode = code; Db.setSetting("food_country", code); countryMenu = false },
                                    )
                                }
                            }
                        }
                    }
                    Box {
                        Box(Modifier.size(42.dp).raisedDark(CircleShape).clickable { menuOpen = true },
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.MoreHoriz, null, tint = Pal.Text, modifier = Modifier.size(22.dp))
                        }
                        org.ivansola.minutricion.ui.components.AppDropdownMenu(menuOpen, { menuOpen = false }) {
                            DropdownMenuItem(
                                leadingIcon = { org.ivansola.minutricion.ui.components.EmojiIcon("🍎", size = 20.dp) },
                                text = { Text("Crear Alimento", color = Pal.Text, modifier = Modifier.width(170.dp)) },
                                onClick = { menuOpen = false; showCrear = true },
                            )
                            DropdownMenuItem(
                                leadingIcon = { org.ivansola.minutricion.ui.components.EmojiIcon("⚡", size = 20.dp) },
                                text = { Text("Ingreso Manual", color = Pal.Text, modifier = Modifier.width(170.dp)) },
                                onClick = { menuOpen = false; showManual = true },
                            )
                        }
                    }
                }
                // Pestañas
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    listOf("Base de Datos" to Icons.Rounded.Storage, "Favoritos" to Icons.Rounded.Favorite,
                        "Creados" to Icons.Rounded.Brush).forEach { (name, icon) ->
                        TabCell(name, icon, tab == name) { tab = name; query = ""; expanded = false }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Pal.Card2))

                // Lista
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(
                        Modifier.fillMaxSize()
                            .graphicsLayer { this.alpha = listAlpha.value },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 90.dp),
                    ) {
                        item {
                            Text(sectionTitle, color = Pal.Sub, fontWeight = FontWeight.Bold, fontSize = 10.sp,
                                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
                        }
                        if (tab == "Base de Datos" && q.isNotEmpty() && offLoading) {
                            item {
                                Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = Pal.Yellow)
                                }
                            }
                        }
                        val collapsedRecents = tab == "Base de Datos" && q.isEmpty() && !expanded && recents.size > 5
                        items(items, key = { "${it.food.id}:${it.food.name}" }) { d ->
                            FoodRowRich(d) { picked = d.food }
                        }
                        if (collapsedRecents) {
                            item { VerMas(onClick = expandRecents) }
                        }
                        // Comidas recientes: repetir una comida ENTERA de otro día.
                        if (tab == "Base de Datos" && q.isEmpty() && recentMeals.isNotEmpty()) {
                            item {
                                Text("COMIDAS RECIENTES", color = Pal.Sub, fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp, modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp))
                            }
                            items(recentMeals, key = { "${it.day}:${it.meal}" }) { rm ->
                                RecentMealRow(rm) { onAddMeal(rm.entries) }
                            }
                        }
                        if (items.isEmpty() && !offLoading) {
                            item {
                                Text(
                                    if (tab == "Base de Datos") "Sin resultados. Escribe para buscar en línea."
                                    else "Aún no hay nada aquí.",
                                    color = Pal.Sub, fontSize = 12.sp,
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                        }
                    }
                    // Selector flotante Buscar / Escanear
                    Row(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)
                            .inset(RoundedCornerShape(23.dp), TrackTop, TrackBottom).padding(5.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        SegPill(Icons.Rounded.Search, "Buscar", true) {}
                        SegPill(Icons.Rounded.QrCodeScanner, "Escanear", false, onScan)
                    }
                }
            }
        }
    }

    picked?.let { food ->
        FoodDetailScreen(
            food = food, meals = meals, initialMeal = meal, day = day, entry = null,
            onConfirm = { f, g, m -> onAdd(f, g, m); picked = null },
            // Al cerrar, recarga la biblioteca por si se usó "Actualizar datos" en la ficha.
            onDismiss = { picked = null; libRefresh++ },
        )
    }
    if (showCrear) {
        CrearAlimentoScreen(
            onCreated = { food ->
                Db.upsertFood(food, userCreated = true)   // se guarda en "Creados"
                libRefresh++
                showCrear = false
                tab = "Creados"                            // salta a Creados para verlo
            },
            // el alimento ya existía: se abre su ficha directamente para poder añadirlo
            onPickExisting = { food -> showCrear = false; picked = food },
            onDismiss = { showCrear = false },
        )
    }
    if (showManual) {
        IngresoManualSheet(meals = meals, initialMeal = meal,
            onAdd = { food, m -> showManual = false; onAdd(food, 100.0, m) },
            onDismiss = { showManual = false })
    }
}

@Composable
private fun TabCell(name: String, icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    val col = if (active) Pal.Text else Pal.Sub
    // width(IntrinsicSize.Max): la columna mide SOLO el ancho de su contenido (icono+texto), así el
    // subrayado (fillMaxWidth) no fuerza a la pestaña a ocupar toda la fila (bug que ocultaba Fav/Creados).
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(androidx.compose.foundation.layout.IntrinsicSize.Max)
            .clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
            Icon(icon, null, tint = col, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
            Text(name, color = col, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(if (active) Pal.Text else Color.Transparent))
    }
}

@Composable
private fun FoodRowRich(d: Disp, onClick: () -> Unit) {
    val drink = isDrink(d.food.name) ||
        org.ivansola.minutricion.ui.components.isDrinkByCategory(d.food.categories)
    val (base, brand) = remember(d.food.name) { org.ivansola.minutricion.ui.components.splitName(d.food.name) }
    val unit = if (drink) "ml" else "g"
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            org.ivansola.minutricion.ui.components.FoodIconDisc(
                d.food.name, size = 32.dp, emojiSize = 20.dp,
                category = d.food.category, drink = drink)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(base, color = Pal.Text, fontSize = 13.sp, maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false))
                    if (d.verified) {
                        Spacer(Modifier.width(4.dp))
                        // sello dorado (check-decagram MDI), como Kivy.
                        org.ivansola.minutricion.ui.components.MdiIcon("check-decagram", size = 14.dp, color = Pal.Yellow)
                    }
                }
                Text(
                    if (brand.isNotEmpty()) brand else "${d.food.kcal.roundToInt()} kcal/100$unit",
                    color = Pal.Sub, fontSize = 10.sp, maxLines = 1,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text("${d.grams.roundToInt()} $unit", color = Pal.Text, fontSize = 11.sp)
                Text("${(d.food.kcal * d.grams / 100).roundToInt()} kcal", color = Pal.Sub, fontSize = 10.sp)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1C1C20)))
    }
}

private val DIAS_R = listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")
private val MESES_R = listOf("ene", "feb", "mar", "abr", "may", "jun",
    "jul", "ago", "sep", "oct", "nov", "dic")

@Composable
private fun RecentMealRow(rm: org.ivansola.minutricion.data.Db.RecentMeal, onClick: () -> Unit) {
    val d = remember(rm.day) { LocalDate.parse(rm.day) }
    val when0 = "${DIAS_R[d.dayOfWeek.value - 1]} ${d.dayOfMonth} ${MESES_R[d.monthValue - 1]}"
    val names = rm.entries.joinToString(", ") { it.name }.let { if (it.length > 40) it.take(40).trimEnd(',', ' ') + "…" else it }
    val kcal = rm.entries.filter { it.included }.sumOf { it.totalKcal }.roundToInt()
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            org.ivansola.minutricion.ui.components.MealBadge(rm.meal, size = 30.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("${rm.meal} ($when0)", color = Pal.Text, fontSize = 12.sp, maxLines = 1)
                Text(names, color = Pal.Sub, fontSize = 10.sp, maxLines = 1)
            }
            Text("$kcal kcal", color = Pal.Sub, fontSize = 10.sp)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1C1C20)))
    }
}

@Composable
internal fun VerMas(onClick: () -> Unit) {
    Box(Modifier.padding(start = 12.dp, top = 6.dp)) {
        Box(
            Modifier.raisedDark(RoundedCornerShape(13.dp), elevation = 3.dp)
                .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text("VER MÁS", color = Pal.Sub, fontWeight = FontWeight.Bold, fontSize = 10.sp)
        }
    }
}

@Composable
internal fun SegPill(icon: ImageVector, text: String, active: Boolean, onClick: () -> Unit) {
    val col = if (active) Color.Black else Pal.Sub
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.height(36.dp)
            .then(if (active) Modifier.raisedYellow(shape, elevation = 4.dp)
                  else Modifier.clip(shape))
            .clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = col, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = col, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
internal fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Pal.Yellow,
    unfocusedBorderColor = Pal.Border,
    focusedTextColor = Pal.Text,
    unfocusedTextColor = Pal.Text,
    cursorColor = Pal.Yellow,
    focusedContainerColor = Pal.Card2,
    unfocusedContainerColor = Pal.Card2,
)
