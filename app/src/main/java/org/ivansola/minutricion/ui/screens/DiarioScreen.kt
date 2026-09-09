package org.ivansola.minutricion.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ivansola.minutricion.ui.components.MdiIcon
import org.ivansola.minutricion.data.Db
import org.ivansola.minutricion.data.Entry
import org.ivansola.minutricion.data.Food
import org.ivansola.minutricion.data.Logic
import org.ivansola.minutricion.data.Macros
import org.ivansola.minutricion.data.Off
import org.ivansola.minutricion.data.Targets
import org.ivansola.minutricion.ui.components.AppCard
import org.ivansola.minutricion.ui.components.CalorieGauge
import org.ivansola.minutricion.ui.components.DayMark
import org.ivansola.minutricion.ui.components.MacroColumn
import org.ivansola.minutricion.ui.components.MacroTrack
import org.ivansola.minutricion.ui.components.WeekStrip
import org.ivansola.minutricion.ui.components.raised
import org.ivansola.minutricion.ui.components.BtnDarkTop
import org.ivansola.minutricion.ui.components.BtnDarkBottom
import org.ivansola.minutricion.ui.components.raisedDark
import org.ivansola.minutricion.ui.theme.Pal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.roundToInt

private val MESES = listOf("ene", "feb", "mar", "abr", "may", "jun",
    "jul", "ago", "sep", "oct", "nov", "dic")

private fun dateLabel(d: LocalDate, today: LocalDate): String = when (d) {
    today -> "Hoy"
    today.minusDays(1) -> "Ayer"
    today.plusDays(1) -> "Mañana"
    else -> "${d.dayOfMonth} ${MESES[d.monthValue - 1]}"
}

/** Formatea un entero con punto como separador de miles (1500 -> "1.500"). */
private fun miles(n: Int): String =
    "%,d".format(n).replace(",", ".")

/** Estado que necesita la parte visual del Diario (sin dependencia de la BD). */
@Immutable
data class DiarioState(
    val selected: LocalDate,
    val today: LocalDate,
    val meals: List<String>,
    val entries: List<Entry>,
    val targets: Targets?,
    val summary: Macros,
    val streak: Int = 0,
    val streakHot: Boolean = false,
    val weekMarks: Map<LocalDate, DayMark> = emptyMap(),
    val consumedMode: Boolean = false,   // false = mostrar RESTANTE; true = CONSUMIDO/objetivo
    val hasClipboard: Boolean = false,   // hay comida/día copiado para pegar
    val nutrients: Map<String, Double> = emptyMap(),   // totales del día (para "Otros Nutrientes")
)

/** Acciones del Diario (menús ⋯ / lápiz). Todas con no-op por defecto -> renderizable sin BD. */
@Immutable
data class DiarioActions(
    val onPickDay: (LocalDate) -> Unit = {},
    val onOpenPicker: () -> Unit = {},
    val onAddFood: (String) -> Unit = {},
    val onToggle: (Entry) -> Unit = {},
    val onEntryClick: (Entry) -> Unit = {},
    /** Categoría (para el icono) de un alimento por su nombre, según la biblioteca. */
    val catOf: (String) -> String? = { null },
    val onOpenStreaks: () -> Unit = {},
    val onOpenScore: () -> Unit = {},
    val onOpenEditView: () -> Unit = {},
    // menú del lápiz (resumen)
    val onToggleConsumed: () -> Unit = {},
    val onConfigCalories: () -> Unit = {},
    val onConfigMacros: () -> Unit = {},
    // menú ⋯ del día (resumen)
    val onCopyDay: () -> Unit = {},
    val onPasteDay: () -> Unit = {},
    val onGoToday: () -> Unit = {},
    val onClearDay: () -> Unit = {},
    // menú ⋯ de cada comida
    val onClearMeal: (String) -> Unit = {},
    val onCopyMeal: (String) -> Unit = {},
    val onPasteMeal: (String) -> Unit = {},
    val onRepeatMeal: (String) -> Unit = {},
    val onAdjustPortions: (String) -> Unit = {},
)

/**
 * Alimentos de la biblioteca que podrían ser el que se acaba de escanear, para que el usuario elija
 * (o diga que no es ninguno). `scanned` es lo que devolvió Open Food Facts para ese código, o null
 * si los candidatos vienen de que el propio código ya estaba en varias fichas.
 */
private data class PickFoods(
    val options: List<Food>, val code: String, val meal: String, val scanned: Food?,
)

/** Pantalla con estado: lee de la BD y delega la UI en DiarioContent. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiarioScreen(contentPadding: PaddingValues) {
    val today = remember { LocalDate.now() }
    var refresh by remember { mutableIntStateOf(0) }
    var addMeal by remember { mutableStateOf<String?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    var detailEntry by remember { mutableStateOf<Entry?>(null) }
    var scanMeal by remember { mutableStateOf<String?>(null) }
    var scanLoading by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var pendingScan by remember { mutableStateOf<Pair<Food, String>?>(null) }
    // Código escaneado que no existe (código, comida): ofrece crear el alimento con el código puesto.
    var scanNotFound by remember { mutableStateOf<Pair<String, String>?>(null) }
    var createScan by remember { mutableStateOf<Pair<String, String>?>(null) }
    // código escaneado que no está en ningún sitio: se asociará al alimento que el usuario elija
    // buscando por nombre (ver "Buscar por nombre" en el diálogo de no encontrado).
    var attachCode by remember { mutableStateOf<String?>(null) }
    // candidatos de la biblioteca para el código escaneado: los elige el usuario.
    var pickFoods by remember { mutableStateOf<PickFoods?>(null) }
    var showStreaks by remember { mutableStateOf(false) }
    var showScore by remember { mutableStateOf(false) }
    var showEditView by remember { mutableStateOf(false) }
    var showConfigCal by remember { mutableStateOf(false) }
    var showConfigMacros by remember { mutableStateOf(false) }
    var adjustMeal by remember { mutableStateOf<String?>(null) }
    var clipboard by remember { mutableStateOf<List<Entry>?>(null) }
    var consumedMode by remember { mutableStateOf(Db.getSetting("summary_consumed") == "1") }
    val scope = rememberCoroutineScope()
    var toastMsg by remember { mutableStateOf<String?>(null) }
    var toastKey by remember { mutableIntStateOf(0) }
    fun toast(msg: String) { toastMsg = msg; toastKey++ }

    // Dos pagers que SIGUEN EL DEDO (como Fitia): días (contenido) y semanas (tira), sincronizados.
    val dayBase = 100_000
    val weekBase = 100_000
    val dayPager = rememberPagerState(initialPage = dayBase) { 200_000 }
    val weekPager = rememberPagerState(initialPage = weekBase) { 200_000 }
    val todayMonday = remember { mondayOf(today) }
    fun dateFor(p: Int): LocalDate = today.plusDays((p - dayBase).toLong())
    fun dayPageFor(d: LocalDate): Int = dayBase + java.time.temporal.ChronoUnit.DAYS.between(today, d).toInt()
    fun weekPageFor(d: LocalDate): Int = weekBase + java.time.temporal.ChronoUnit.WEEKS.between(todayMonday, mondayOf(d)).toInt()
    fun mondayForWeekPage(p: Int): LocalDate = todayMonday.plusWeeks((p - weekBase).toLong())
    val selected: LocalDate = dateFor(dayPager.currentPage)
    fun goToDay(d: LocalDate) { scope.launch { dayPager.animateScrollToPage(dayPageFor(d)) } }
    // Al asentarse un pager, alinea el otro (guarda `syncing` para no entrar en bucle).
    val syncing = remember { mutableStateOf(false) }
    LaunchedEffect(dayPager.settledPage) {
        if (syncing.value) return@LaunchedEffect
        val wp = weekPageFor(dateFor(dayPager.settledPage))
        if (weekPager.currentPage != wp) { syncing.value = true; weekPager.scrollToPage(wp); syncing.value = false }
    }
    LaunchedEffect(weekPager.settledPage) {
        if (syncing.value) return@LaunchedEffect
        val monday = mondayForWeekPage(weekPager.settledPage)
        val target = monday.plusDays((dateFor(dayPager.currentPage).dayOfWeek.value - 1).toLong())
        val tp = dayPageFor(target)
        if (dayPager.currentPage != tp) { syncing.value = true; dayPager.animateScrollToPage(tp); syncing.value = false }
    }

    val meals = remember(refresh) { Db.getMeals() }
    val entries = remember(selected, refresh) { Db.entriesForDay(selected.toString()) }
    val targets = remember(refresh) { Logic.targets() }
    // Categoría por nombre (para que el icono en la lista coincida con el de la ficha).
    val foodCats = remember(refresh) { Db.listFoods().associate { it.name to it.category } }

    // Atajo lanzado desde el widget "Registro Rápido": abre buscar/escanear en la primera comida.
    LaunchedEffect(org.ivansola.minutricion.widget.WidgetNav.pending.value) {
        val a = org.ivansola.minutricion.widget.WidgetNav.pending.value ?: return@LaunchedEffect
        val m = meals.firstOrNull() ?: "Comida"
        if (a == "scan") scanMeal = m else addMeal = m
        org.ivansola.minutricion.widget.WidgetNav.pending.value = null
    }

    // Copia/pega registros (portapapeles en memoria). Al pegar, se re-insertan en la comida/día.
    fun copyEntries(list: List<Entry>) {
        if (list.isEmpty()) { toast("No hay alimentos que copiar"); return }
        clipboard = list
        toast("${list.size} alimento(s) copiado(s)")
    }
    fun pasteInto(targetMeal: String?) {
        val cb = clipboard
        if (cb.isNullOrEmpty()) { toast("No hay nada copiado"); return }
        cb.forEach { e ->
            Db.addEntry(e.copy(id = 0, day = selected.toString(), meal = targetMeal ?: e.meal))
        }
        refresh++
        toast("${cb.size} alimento(s) pegado(s)")
    }

    val streaksData = remember(refresh) { Logic.streaks() }

    val actions = DiarioActions(
        onPickDay = { goToDay(it) },
        onOpenPicker = { showPicker = true },
        onAddFood = { addMeal = it },
        catOf = { foodCats[it] },
        onToggle = { e -> Db.setEntryIncluded(e.id, !e.included); refresh++ },
        onEntryClick = { detailEntry = it },
        onOpenStreaks = { showStreaks = true },
        onOpenScore = { showScore = true },
        onOpenEditView = { showEditView = true },
        onToggleConsumed = {
            consumedMode = !consumedMode
            Db.setSetting("summary_consumed", if (consumedMode) "1" else "0")
        },
        onConfigCalories = { showConfigCal = true },
        onConfigMacros = { showConfigMacros = true },
        onCopyDay = { copyEntries(entries) },
        onPasteDay = { pasteInto(null) },
        onGoToday = { goToDay(today) },
        onClearDay = {
            val n = entries.size
            meals.forEach { Db.deleteEntriesForMeal(selected.toString(), it) }; refresh++
            toast(if (n > 0) "Día vaciado" else "El día ya estaba vacío")
        },
        onClearMeal = { m ->
            val n = entries.count { it.meal == m }
            Db.deleteEntriesForMeal(selected.toString(), m); refresh++
            toast(if (n > 0) "«$m» vaciado" else "«$m» ya estaba vacío")
        },
        onCopyMeal = { m -> copyEntries(entries.filter { it.meal == m }) },
        onPasteMeal = { m -> pasteInto(m) },
        onRepeatMeal = { m -> pasteInto(m) },
        onAdjustPortions = { m -> adjustMeal = m },
    )

    // Cabecera: la fila "Hoy" se COLAPSA al hacer scroll (como Fitia) y la tira de semana se fija
    // arriba. `headerCollapse` (0..1) lo aporta la página visible del pager de días.
    val headerState = DiarioState(selected, today, meals, emptyList(), targets, Macros(),
        streak = streaksData.regCur, streakHot = streaksData.perfCur > 0,
        consumedMode = consumedMode, hasClipboard = clipboard != null)
    var headerCollapse by remember { mutableStateOf(0f) }
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        // Fila "Hoy 🔥": encoge su altura y se desvanece con el scroll; al llegar a 0 la tira de
        // semana queda pegada arriba del todo (comportamiento de Fitia).
        Box(
            Modifier.fillMaxWidth()
                .height(androidx.compose.ui.unit.lerp(DAY_HEADER_H, 0.dp, headerCollapse))
                .graphicsLayer { alpha = 1f - headerCollapse; clip = true },
        ) {
            DayHeader(headerState, actions)
        }
        HorizontalPager(
            state = weekPager,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            beyondViewportPageCount = 1,
        ) { wpage ->
            val monday = mondayForWeekPage(wpage)
            val marks = rememberWeekMarks(monday, meals, targets, refresh)
            WeekStrip(selected, today, marks, { goToDay(it) }, weekMonday = monday,
                collapse = headerCollapse)
        }
        Spacer(Modifier.height(4.dp))
        HorizontalPager(
            state = dayPager,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            beyondViewportPageCount = 1,
        ) { page ->
            DayPage(dateFor(page), today, meals, targets, refresh,
                consumedMode, clipboard != null, actions, contentPadding,
                isCurrent = page == dayPager.currentPage,
                onCollapse = { headerCollapse = it })
        }
    }
        toastMsg?.let { msg ->
            ToastBar(msg, toastKey, contentPadding.calculateBottomPadding()) { toastMsg = null }
        }
    }

    if (showStreaks) StreaksScreen(
        onClose = { showStreaks = false },
        onPickDay = { goToDay(it); showStreaks = false },
    )
    if (showScore) ScoreDetailScreen(onClose = { showScore = false })
    if (showEditView) EditViewScreen(onClose = { showEditView = false }, onChanged = { refresh++ })
    if (showConfigCal) ConfigCaloriesDialog(onDone = { refresh++; showConfigCal = false }, onDismiss = { showConfigCal = false })
    if (showConfigMacros) ConfigMacrosDialog(onDone = { refresh++; showConfigMacros = false }, onDismiss = { showConfigMacros = false })
    adjustMeal?.let { m ->
        AdjustPortionsDialog(
            onApply = { factor ->
                entries.filter { it.meal == m }.forEach { Db.updateEntryGrams(it.id, it.grams * factor) }
                refresh++; adjustMeal = null
            },
            onDismiss = { adjustMeal = null },
        )
    }

    // --- Selector de fecha (al pulsar "Hoy ▾")
    if (showPicker) {
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = selected.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let {
                        goToDay(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showPicker = false
                }) { Text("Aceptar", color = Pal.Yellow) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancelar", color = Pal.Sub) }
            },
            colors = DatePickerDefaults.colors(containerColor = Pal.Card),
        ) {
            DatePicker(
                state = dpState, showModeToggle = false,
                colors = DatePickerDefaults.colors(
                    containerColor = Pal.Card,
                    selectedDayContainerColor = Pal.Yellow,
                    selectedDayContentColor = Pal.Bg,
                    todayDateBorderColor = Pal.Yellow,
                    todayContentColor = Pal.Yellow,
                ),
            )
        }
    }

    // --- Hoja de "Añadir alimento"
    addMeal?.let { meal ->
        val foods = remember { Db.listFoods() }
        val country = remember { Db.getSetting("food_country", "world") ?: "world" }
        AddFoodSheet(
            meal = meal,
            meals = meals,
            day = selected,
            foods = foods,
            country = country,
            onAdd = { food, grams, chosenMeal ->
                if (food.id == 0L) Db.upsertFood(food)   // guarda en la biblioteca (online/creado)
                // Venimos de un código escaneado que no estaba en ningún sitio: el usuario ha
                // buscado el alimento a mano, así que le asociamos el código para la próxima vez.
                attachCode?.let { c ->
                    val target = if (food.id > 0) food else Db.foodByName(food.name)
                    if (target != null) { Db.attachBarcode(target, c); toast("Código asociado") }
                    attachCode = null
                }
                Db.addEntry(Entry(
                    id = 0, day = selected.toString(), meal = chosenMeal, name = food.name,
                    grams = grams, kcal = food.kcal, protein = food.protein,
                    carbs = food.carbs, fat = food.fat, included = true, nutrients = food.nutrients,
                ))
                refresh++; toast("Añadido")
            },
            onAddMeal = { ents ->
                ents.forEach { e ->
                    Db.addEntry(e.copy(id = 0, day = selected.toString(), meal = meal))
                }
                refresh++; addMeal = null; toast("Comida añadida")
            },
            onScan = { scanMeal = meal; addMeal = null },
            onDismiss = { addMeal = null; attachCode = null },   // si no elige nada, no se asocia
        )
    }

    // Consulta OFF por el código y decide: si hay alimentos parecidos en la biblioteca (guardados
    // sin EAN), se ofrecen para que el usuario elija; si no, se registra como producto nuevo.
    // `suggest = false` cuando el usuario ya ha dicho que no era ninguno de los propuestos.
    fun lookupByCode(code: String, meal: String, suggest: Boolean = true) {
        scanLoading = true
        scope.launch {
            val prod = withContext(Dispatchers.IO) { Off.byBarcode(code) }
            scanLoading = false
            if (prod == null) { scanNotFound = code to meal; return@launch }
            val f = prod.toFood()
            val similar = if (suggest)
                Db.findSimilarFoods(f.name, f.kcal, f.protein, f.carbs, f.fat) else emptyList()
            if (similar.isNotEmpty()) pickFoods = PickFoods(similar, code, meal, f)
            else pendingScan = f.copy(barcode = code) to meal
        }
    }

    // --- Escáner de código de barras → biblioteca local → Open Food Facts → cantidad
    scanMeal?.let { meal ->
        ScanScreen(
            onResult = { code ->
                scanMeal = null
                // Primero la biblioteca LOCAL. Si el código está en VARIAS fichas (duplicados que
                // arrastra la biblioteca), se pregunta cuál es en vez de elegir una al azar.
                val locals = Db.foodsByBarcode(code)
                when {
                    locals.size == 1 -> pendingScan = locals[0] to meal
                    locals.size > 1 -> pickFoods = PickFoods(locals, code, meal, null)
                    else -> lookupByCode(code, meal)
                }
            },
            onClose = { scanMeal = null },
        )
    }

    if (scanLoading) {
        AlertDialog(
            onDismissRequest = { },
            containerColor = Pal.Card2,
            title = { Text("Buscando producto…", color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = { Text("Consultando Open Food Facts", color = Pal.Sub, fontSize = 13.sp) },
            confirmButton = { },
        )
    }

    pendingScan?.let { (food, meal) ->
        FoodDetailScreen(
            food = food, meals = meals, initialMeal = meal, day = selected, entry = null,
            onConfirm = { f, grams, chosenMeal ->
                Db.upsertFood(f)   // guarda el producto escaneado en la biblioteca
                Db.addEntry(Entry(
                    id = 0, day = selected.toString(), meal = chosenMeal, name = f.name,
                    grams = grams, kcal = f.kcal, protein = f.protein,
                    carbs = f.carbs, fat = f.fat, included = true, nutrients = f.nutrients,
                ))
                refresh++; pendingScan = null
            },
            onDismiss = { pendingScan = null },
        )
    }

    scanError?.let { msg ->
        AlertDialog(
            onDismissRequest = { scanError = null },
            containerColor = Pal.Card2,
            title = { Text("No encontrado", color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = { Text(msg, color = Pal.Sub, fontSize = 13.sp) },
            confirmButton = { TextButton(onClick = { scanError = null }) { Text("Aceptar", color = Pal.Yellow) } },
        )
    }

    // Varios alimentos de la biblioteca podrían ser el escaneado: decide el usuario. Al elegir uno
    // se le asocia el código (si no lo tenía), así que el siguiente escaneo va directo y sin dudas.
    pickFoods?.let { p ->
        AlertDialog(
            onDismissRequest = { pickFoods = null },
            containerColor = Pal.Card2,
            title = { Text("¿Cuál es?", color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        if (p.scanned != null)
                            "Has escaneado «${p.scanned.name}». En tu biblioteca hay alimentos que " +
                                "podrían ser el mismo:"
                        else "Ese código está en más de un alimento de tu biblioteca:",
                        color = Pal.Sub, fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    p.options.forEach { f ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    Db.attachBarcode(f, p.code)
                                    pendingScan = f.copy(barcode = f.barcode ?: p.code) to p.meal
                                    pickFoods = null
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(f.name, color = Pal.Text, fontSize = 14.sp)
                                Text("${f.kcal.toInt()} kcal · P ${f.protein} · C ${f.carbs} · G ${f.fat}",
                                    color = Pal.Sub, fontSize = 11.sp)
                            }
                            MdiIcon("chevron-right", size = 18.dp, color = Pal.Sub)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val p2 = p
                    pickFoods = null
                    // "Ninguno": si veníamos de OFF ya tenemos el producto -> se registra como nuevo;
                    // si veníamos de un código duplicado en la biblioteca, se consulta OFF sin volver
                    // a proponer los mismos.
                    if (p2.scanned != null) pendingScan = p2.scanned.copy(barcode = p2.code) to p2.meal
                    else lookupByCode(p2.code, p2.meal, suggest = false)
                }) { Text("Ninguno, es otro", color = Pal.Yellow, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { pickFoods = null }) { Text("Cancelar", color = Pal.Sub) }
            },
        )
    }

    // Código escaneado no registrado → ofrecer crear el alimento (con el código ya puesto).
    scanNotFound?.let { (code, meal) ->
        AlertDialog(
            onDismissRequest = { scanNotFound = null },
            containerColor = Pal.Card2,
            title = { Text("Alimento no registrado", color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Text("El código $code no está en la base de datos. ¿Quieres añadirlo?",
                    color = Pal.Sub, fontSize = 13.sp)
            },
            confirmButton = {
                TextButton(onClick = { createScan = code to meal; scanNotFound = null }) {
                    Text("Añadir", color = Pal.Yellow, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                // El alimento puede estar YA en la biblioteca pero guardado sin código (muy común:
                // varias fuentes del scraper no publican EAN). Se busca a mano y, al elegirlo, se le
                // asocia este código para que el siguiente escaneo lo encuentre solo.
                TextButton(onClick = { attachCode = code; addMeal = meal; scanNotFound = null }) {
                    Text("Buscar por nombre", color = Pal.Sub)
                }
            },
        )
    }

    // Crear Alimento con el código escaneado ya rellenado; al crearlo, abre su ficha para añadirlo.
    createScan?.let { (code, meal) ->
        CrearAlimentoScreen(
            initialBarcode = code,
            onCreated = { food ->
                Db.upsertFood(food, userCreated = true)
                createScan = null
                pendingScan = food to meal
            },
            // ya estaba en la biblioteca: se abre su ficha sin volver a guardarlo
            onPickExisting = { food -> createScan = null; pendingScan = food to meal },
            onDismiss = { createScan = null },
        )
    }

    // --- Detalle de una entrada (ficha completa: editar gramos/comida o eliminar)
    detailEntry?.let { e ->
        val food = remember(e.id) {
            val base = Food(0, e.name, e.kcal, e.protein, e.carbs, e.fat, e.nutrients)
            // Fusiona la info enriquecida de la biblioteca (ingredientes, ración, categoría...)
            // manteniendo los macros tal como se registraron en el diario.
            val lib = Db.foodByName(e.name)
            if (lib == null) base else base.copy(
                nutrients = if (base.nutrients.isEmpty()) lib.nutrients else base.nutrients,
                serving = lib.serving,
                category = lib.category,
                barcode = lib.barcode,
                ingredients = lib.ingredients,
                allergens = lib.allergens,
                categories = lib.categories,
            )
        }
        FoodDetailScreen(
            food = food, meals = meals, initialMeal = e.meal, day = selected, entry = e,
            onConfirm = { _, grams, chosenMeal ->
                Db.updateEntryMealGrams(e.id, chosenMeal, grams); refresh++; detailEntry = null
                toast("Actualizado")
            },
            onDelete = { Db.deleteEntry(e.id); refresh++; detailEntry = null; toast("Eliminado") },
            onDismiss = { detailEntry = null },
        )
    }
}

private fun mondayOf(d: LocalDate) = d.minusDays((d.dayOfWeek.value - 1).toLong())

/** Altura de la fila "Hoy 🔥" (se colapsa a 0 al hacer scroll). Cabe el texto + rayo sin cortarse. */
private val DAY_HEADER_H = 54.dp

/** Mensaje flotante (toast) con una barra inferior que DISMINUYE mostrando el tiempo restante. */
@Composable
private fun ToastBar(msg: String, key: Int, bottomInset: Dp, onDone: () -> Unit) {
    val progress = remember { androidx.compose.animation.core.Animatable(1f) }
    // Duración proporcional a la longitud del texto: mensajes largos se muestran más tiempo.
    val durationMs = (1400 + msg.length * 55).coerceIn(1800, 4500)
    androidx.compose.runtime.LaunchedEffect(key) {
        progress.snapTo(1f)
        progress.animateTo(0f, tween(durationMs, easing = androidx.compose.animation.core.LinearEasing))
        onDone()
    }
    Box(
        Modifier.fillMaxSize().padding(bottom = bottomInset + 18.dp, start = 24.dp, end = 24.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // Píldora oscura tipo cápsula (Fitia): mide SOLO lo que ocupa el texto (wrap-content).
        // La barra inferior ocupa TODO el ancho de la cápsula y se encoge; no la agranda.
        val pill = RoundedCornerShape(percent = 50)
        Column(
            Modifier.width(IntrinsicSize.Max)
                .clip(pill).background(Color(0xFF2A2A2E)).border(0.7.dp, Pal.Border, pill)
                .padding(start = 22.dp, end = 22.dp, top = 11.dp, bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(msg, color = Pal.Text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth(progress.value).height(3.dp)
                .clip(CircleShape).background(Color.White))
        }
    }
}

/** Transición de deslizamiento horizontal según la dirección del cambio de fecha. */
private fun daySlide(): AnimatedContentTransitionScope<DiarioState>.() -> ContentTransform = {
    val fwd = targetState.selected.isAfter(initialState.selected)
    val d = if (fwd) 1 else -1
    (slideInHorizontally(tween(260)) { d * it } + fadeIn(tween(260))) togetherWith
        (slideOutHorizontally(tween(260)) { -d * it } + fadeOut(tween(260)))
}

/** Parte puramente visual del Diario (renderizable con datos de ejemplo). */
@Composable
fun DiarioContent(
    contentPadding: PaddingValues,
    state: DiarioState,
    actions: DiarioActions = DiarioActions(),
) {
    val cur = rememberUpdatedState(state.selected)
    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        DayHeader(state, actions)
        // --- Tira de semana: DESLIZAR lateralmente cambia de semana (con animación) ---
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                .pointerInput(Unit) {
                    val threshold = 56.dp.toPx()
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            if (total < -threshold) actions.onPickDay(cur.value.plusWeeks(1))
                            else if (total > threshold) actions.onPickDay(cur.value.minusWeeks(1))
                        },
                        onHorizontalDrag = { _, dx -> total += dx },
                    )
                },
        ) {
            AnimatedContent(
                targetState = state, contentKey = { mondayOf(it.selected) },
                transitionSpec = daySlide(), label = "week",
            ) { s ->
                WeekStrip(s.selected, s.today, s.weekMarks, actions.onPickDay,
                    weekMonday = mondayOf(s.selected))
            }
        }
        Spacer(Modifier.height(4.dp))
        // --- Contenido del día: DESLIZAR lateralmente cambia de día (con animación) ---
        Box(
            Modifier.weight(1f).fillMaxWidth().clipToBounds()
                .pointerInput(Unit) {
                    val threshold = 56.dp.toPx()
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            if (total < -threshold) actions.onPickDay(cur.value.plusDays(1))
                            else if (total > threshold) actions.onPickDay(cur.value.minusDays(1))
                        },
                        onHorizontalDrag = { _, dx -> total += dx },
                    )
                },
        ) {
            AnimatedContent(
                targetState = state, contentKey = { it.selected },
                transitionSpec = daySlide(), label = "day",
            ) { s ->
                DayScroll(s, actions, contentPadding)
            }
        }
    }
}

/** Puntos de estado (verde/amarillo/gris) de los 7 días de una semana (lee la BD de esa semana). */
@Composable
private fun rememberWeekMarks(monday: LocalDate, meals: List<String>, targets: Targets?, refresh: Int):
    Map<LocalDate, DayMark> = remember(monday, refresh, targets, meals) {
    val sunday = monday.plusDays(6)
    val totals = Db.dailyTotals(monday.toString(), sunday.toString(), meals)
    val logged = Db.loggedDays(monday.toString(), sunday.toString())
    (0..6).associate { i ->
        val d = monday.plusDays(i.toLong())
        val t = totals[d.toString()]
        d to when {
            targets != null && t != null && Logic.kcalOk(t.kcal, targets.kcal.toDouble()) -> DayMark.PERFECT
            d.toString() in logged -> DayMark.LOGGED
            else -> DayMark.NONE
        }
    }
}

/** Una página del pager = el contenido (resumen + comidas) de un día concreto (lee su BD). */
@Composable
private fun DayPage(
    date: LocalDate, today: LocalDate, meals: List<String>, targets: Targets?, refresh: Int,
    consumedMode: Boolean, hasClipboard: Boolean, actions: DiarioActions, contentPadding: PaddingValues,
    isCurrent: Boolean = true, onCollapse: (Float) -> Unit = {},
) {
    val entries = remember(date, refresh) { Db.entriesForDay(date.toString()) }
    val summary = remember(date, refresh) { Logic.daySummary(date.toString(), meals) }
    val nutrients = remember(date, refresh) { Logic.dayNutrientTotals(date.toString(), meals) }
    val state = DiarioState(date, today, meals, entries, targets, summary,
        weekMarks = emptyMap(), consumedMode = consumedMode, hasClipboard = hasClipboard,
        nutrients = nutrients)
    DayScroll(state, actions, contentPadding, isCurrent, onCollapse)
}

@Composable
private fun DayScroll(
    state: DiarioState, actions: DiarioActions, contentPadding: PaddingValues,
    isCurrent: Boolean = true, onCollapse: (Float) -> Unit = {},
) {
    val byMeal = remember(state.entries) { state.entries.groupBy { it.meal } }
    val listState = rememberLazyListState()
    // La barra COMPACTA (kcal + macros) empieza a aparecer cuando los macros del resumen dejan de
    // verse (~80% de la tarjeta desplazada) y se completa despacio; como Fitia.
    val compactProgress by remember {
        derivedStateOf {
            val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }
                ?: return@derivedStateOf 1f
            val h = info.size.toFloat().coerceAtLeast(1f)
            val scrolled = -info.offset.toFloat()
            ((scrolled - h * 0.78f) / (h * 0.22f)).coerceIn(0f, 1f)
        }
    }
    // Colapso de "Hoy" + números de los días: empieza al iniciar el scroll y termina PRONTO
    // (~0,45 de la tarjeta), de modo que los números están ya totalmente ocultos ANTES de que
    // empiece a aparecer la barra de macros (que arranca en 0,78). La tira nunca se toca.
    val collapseProgress by remember {
        derivedStateOf {
            val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }
                ?: return@derivedStateOf 1f
            val h = info.size.toFloat().coerceAtLeast(1f)
            val scrolled = -info.offset.toFloat()
            ((scrolled - h * 0.10f) / (h * 0.35f)).coerceIn(0f, 1f)
        }
    }
    if (isCurrent) LaunchedEffect(collapseProgress) { onCollapse(collapseProgress) }
    // clipToBounds: la barra compacta entra deslizándose desde arriba (translationY negativo); sin
    // recorte se dibujaría sobre la tira de semana y tapaba los números de los días.
    Box(Modifier.fillMaxSize().clipToBounds()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp,
                bottom = contentPadding.calculateBottomPadding() + 90.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SummaryPager(state, actions) }
            items(state.meals, key = { it }, contentType = { "meal" }) { meal ->
                MealCard(meal, byMeal[meal].orEmpty(), state.hasClipboard, actions)
            }
            item { EditButton(actions.onOpenEditView) }
        }
        if (compactProgress > 0.01f) {
            var chHeight by remember { mutableIntStateOf(0) }
            CompactHeader(
                state,
                Modifier.align(Alignment.TopCenter)
                    .onSizeChanged { chHeight = it.height }
                    .graphicsLayer { translationY = -chHeight * (1f - compactProgress) },
            )
        }
    }
}

@Composable
private fun DayHeader(state: DiarioState, actions: DiarioActions) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // El título abre la MISMA vista de Rachas (calendario) que el botón de la racha; desde ahí
        // se puede seleccionar cualquier día para moverse a él.
        Row(
            Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = actions.onOpenStreaks)
                .padding(vertical = 4.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(dateLabel(state.selected, state.today), color = Pal.Text,
                fontWeight = FontWeight.Bold, fontSize = 23.sp)
            MdiIcon("chevron-down", size = 27.dp, color = Pal.Text)
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = actions.onOpenStreaks)
                .padding(vertical = 6.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MdiIcon("fire", size = 26.dp, color = if (state.streakHot) Pal.Green else Pal.Sub)
            Spacer(Modifier.width(3.dp))
            Text("${state.streak}", color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
    }
}

/** Cabecera compacta (Fitia): kcal + 3 macros; solo borde INFERIOR con esquinas curvas (como Kivy). */
@Composable
private fun CompactHeader(state: DiarioState, modifier: Modifier = Modifier) {
    val target = state.targets?.kcal ?: 0
    val consumed = state.summary.kcal
    val borderColor = Pal.Border
    Column(
        modifier.fillMaxWidth()
            // SIN clip: el recorte redondeado cortaba el trazo en las curvas (bezier ≠ arco del clip)
            // y hacía la esquina más fina. Como el fondo es el mismo que el de la página, el clip no
            // aporta nada visual; sin él el trazo se dibuja con grosor UNIFORME en recto y curvas.
            .background(Pal.Bg)
            .drawWithContent {
                drawContent()
                // SOLO el borde inferior + las dos esquinas curvas (nada de laterales ni arriba).
                // Esquinas con arcTo (arco circular real) e inset = sw/2 para que quepa el trazo.
                val r = 16.dp.toPx()
                val sw = 0.7.dp.toPx()   // mismo grosor fino que el borde de las tarjetas
                val i = sw / 2f
                val d = 2f * (r - i)   // diámetro del arco de esquina
                val w = size.width; val h = size.height
                val p = Path().apply {
                    moveTo(i, h - r)
                    // esquina inferior IZQUIERDA (arco circular real de 90°)
                    arcTo(androidx.compose.ui.geometry.Rect(i, h - i - d, i + d, h - i), 180f, -90f, false)
                    lineTo(w - r, h - i)
                    // esquina inferior DERECHA
                    arcTo(androidx.compose.ui.geometry.Rect(w - i - d, h - i - d, w - i, h - i), 90f, -90f, false)
                }
                drawPath(p, borderColor, style = Stroke(sw))
            }
            .padding(bottom = 8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CompactCol("kcal",
                if (target > 0) "${miles(consumed.roundToInt())} / ${miles(target)}"
                else miles(consumed.roundToInt()),
                if (target > 0) (consumed / target).toFloat() else 0f, false, Modifier.weight(1f))
            CompactMacro("Proteínas", state.summary.protein, state.targets?.protein ?: 0, Modifier.weight(1f))
            CompactMacro("Carbs", state.summary.carbs, state.targets?.carbs ?: 0, Modifier.weight(1f))
            CompactMacro("Grasas", state.summary.fat, state.targets?.fat ?: 0, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CompactMacro(name: String, cur: Double, goal: Int, modifier: Modifier) {
    // Verde solo si está en rango (100–110%); si se pasa del límite, amarillo (como Fitia).
    val reached = goal > 0 && cur >= goal && cur <= goal * 1.1
    val value = if (goal > 0) "${cur.roundToInt()} / $goal g" else "${cur.roundToInt()} g"
    CompactCol(name, value, if (goal > 0) (cur / goal).toFloat() else 0f, reached, modifier)
}

@Composable
private fun CompactCol(label: String, value: String, frac: Float, reached: Boolean, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 10.sp, maxLines = 1)
        Text(value, color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, maxLines = 1)
        Spacer(Modifier.height(3.dp))
        MacroTrack(frac, reached, 4.dp)
    }
}

/** Carrusel del resumen: pág. 1 = kcal+macros; pág. 2 = "Otros Nutrientes". Con puntos debajo. */
@Composable
private fun SummaryPager(state: DiarioState, actions: DiarioActions) {
    var page by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().pointerInput(Unit) {
                val threshold = 48.dp.toPx(); var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total < -threshold) page = 1 else if (total > threshold) page = 0 },
                    onHorizontalDrag = { _, dx -> total += dx },
                )
            },
        ) {
            AnimatedContent(
                targetState = page, label = "summary",
                transitionSpec = {
                    val d = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(260)) { d * it } + fadeIn(tween(260))) togetherWith
                        (slideOutHorizontally(tween(260)) { -d * it } + fadeOut(tween(260)))
                },
            ) { p ->
                if (p == 0) SummaryCard(state, actions) else OtherNutrientsCard(state, actions)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(2) { i ->
                Box(
                    Modifier.padding(horizontal = 3.dp).size(if (i == page) 8.dp else 6.dp)
                        .clip(CircleShape).background(if (i == page) Pal.Text else Pal.Border),
                )
            }
        }
    }
}

/** Segunda página del resumen: nutrientes "a limitar" + fibra, con su máximo/objetivo. */
@Composable
internal fun OtherNutrientsCard(state: DiarioState, actions: DiarioActions, modifier: Modifier = Modifier) {
    data class NRow(val key: String, val name: String, val emoji: String, val max: Double, val limit: Boolean)
    val rows = listOf(
        NRow("sugars", "Azúcares", "🧊", Logic.LIMIT_REF["sugars"] ?: 50.0, true),
        NRow("fiber", "Fibra", "🌾", Logic.FIBER_GOAL, false),
        NRow("salt", "Sal", "🧂", Logic.LIMIT_REF["salt"] ?: 6.0, true),
        NRow("sat_fat", "Gras. Saturadas", "🧈", Logic.LIMIT_REF["sat_fat"] ?: 20.0, true),
        NRow("trans_fat", "Grasas Trans", "🍟", Logic.LIMIT_REF["trans_fat"] ?: 2.0, true),
    )
    AppCard(padding = 16.dp, modifier = modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Otros Nutrientes", color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                modifier = Modifier.weight(1f))
            Box(Modifier.size(30.dp).clip(CircleShape).clickable { actions.onOpenScore() },
                contentAlignment = Alignment.Center) {
                org.ivansola.minutricion.ui.components.AssetIcon("editpencil", size = 18.dp, tint = Pal.Sub)
            }
        }
        Spacer(Modifier.height(4.dp))
        rows.forEachIndexed { i, r ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Pal.Card2))
            val v = state.nutrients[r.key] ?: 0.0
            val colHex = Logic.rowColor(if (r.limit) "limit" else "reach", v, r.max)
            Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                org.ivansola.minutricion.ui.components.EmojiIcon(r.emoji, size = 22.dp)
                Spacer(Modifier.width(10.dp))
                Text(r.name, color = Pal.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    if (r.limit) {
                        Text("${Logic.fmtNum(v)} g", color = Color(android.graphics.Color.parseColor(colHex)),
                            fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Max. ${Logic.fmtNum(r.max)} g", color = Pal.Sub, fontSize = 11.sp)
                    } else {
                        Text("${Logic.fmtNum(v)}/${Logic.fmtNum(r.max)}g", color = Color(android.graphics.Color.parseColor(colHex)),
                            fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
internal fun SummaryCard(state: DiarioState, actions: DiarioActions, modifier: Modifier = Modifier) {
    val target = state.targets?.kcal ?: 0
    val consumed = state.summary.kcal
    val (numberText, subText) = when {
        target <= 0 -> miles(consumed.roundToInt()) to "kcal"
        state.consumedMode -> "${miles(consumed.roundToInt())} / ${miles(target)}" to "kcal"
        else -> miles((target - consumed).roundToInt()) to "kcal restantes"
    }
    var pencilMenu by remember { mutableStateOf(false) }
    var dayMenu by remember { mutableStateOf(false) }
    AppCard(padding = 16.dp, modifier = modifier) {
        // fila superior: lápiz (menú config) · número centrado · "⋯" (menú del día)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box {
                Box(
                    Modifier.size(30.dp).clip(CircleShape).clickable { pencilMenu = true },
                    contentAlignment = Alignment.Center,
                ) {
                    org.ivansola.minutricion.ui.components.AssetIcon("editpencil", size = 19.dp, tint = Pal.Text)
                }
                MenuCard(expanded = pencilMenu, onDismiss = { pencilMenu = false }) {
                    PngMenuItem("ojo", if (state.consumedMode) "Ver restante" else "Ver consumido", iconLeft = true) {
                        pencilMenu = false; actions.onToggleConsumed()
                    }
                    PngMenuItem("conf_cal", "Configurar Calorías", iconSize = 24.dp, iconLeft = true) { pencilMenu = false; actions.onConfigCalories() }
                    PngMenuItem("conf_macros", "Configurar Macros", iconSize = 24.dp, iconLeft = true) { pencilMenu = false; actions.onConfigMacros() }
                }
            }
            Text(
                numberText,
                color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 32.sp,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
            )
            Box {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable { dayMenu = true },
                    contentAlignment = Alignment.Center,
                ) { MdiIcon("dots-horizontal", size = 26.dp, color = Pal.Text) }
                MenuCard(expanded = dayMenu, onDismiss = { dayMenu = false }) {
                    PngMenuItem("copiar", "Copiar día") { dayMenu = false; actions.onCopyDay() }
                    PngMenuItem("pegar", "Pegar día", enabled = state.hasClipboard) { dayMenu = false; actions.onPasteDay() }
                    MdiMenuItem("calendar-blank-outline", "Ir a fecha") { dayMenu = false; actions.onOpenPicker() }
                    MdiMenuItem("calendar-today", "Hoy") { dayMenu = false; actions.onGoToday() }
                    PngMenuItem("pepelera", "Vaciar día", color = Pal.Red) { dayMenu = false; actions.onClearDay() }
                }
            }
        }
        Text(
            subText, color = Pal.Sub, fontSize = 12.sp,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        CalorieGauge(consumed, target, Modifier.fillMaxWidth().height(104.dp))
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MacroCol("Proteínas", state.summary.protein, state.targets?.protein ?: 0, state.consumedMode, Modifier.weight(1f), Pal.Protein)
            MacroCol("Carbs", state.summary.carbs, state.targets?.carbs ?: 0, state.consumedMode, Modifier.weight(1f), Pal.Carbs)
            MacroCol("Grasas", state.summary.fat, state.targets?.fat ?: 0, state.consumedMode, Modifier.weight(1f), Pal.Fat)
        }
    }
}

@Composable
private fun MacroCol(name: String, cur: Double, goal: Int, consumedMode: Boolean, modifier: Modifier,
                     color: Color? = null) {
    // Verde solo en rango (100–110%); si se pasa del límite, amarillo (como Fitia).
    val reached = goal > 0 && cur >= goal && cur <= goal * 1.1
    val value = when {
        goal <= 0 -> "${cur.roundToInt()} g"
        consumedMode -> "${cur.roundToInt()} / $goal g"
        else -> "${(goal - cur).roundToInt()} g"
    }
    val frac = if (goal > 0) (cur / goal).toFloat() else 0f
    MacroColumn(name, value, frac, reached, modifier, color)
}

@Composable
private fun MealCard(meal: String, entries: List<Entry>, hasClipboard: Boolean, actions: DiarioActions) {
    val inc = entries.filter { it.included }
    val kcal = inc.sumOf { it.totalKcal }.roundToInt()
    val p = inc.sumOf { it.totalProtein }.roundToInt()
    val c = inc.sumOf { it.totalCarbs }.roundToInt()
    val f = inc.sumOf { it.totalFat }.roundToInt()
    var menuOpen by remember { mutableStateOf(false) }
    AppCard(padding = 14.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // sin distintivo: el nombre de la comida arranca en el margen de la tarjeta
            Text(meal, color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                modifier = Modifier.weight(1f))
            Box {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable { menuOpen = true },
                    contentAlignment = Alignment.Center,
                ) { MdiIcon("dots-horizontal", size = 26.dp, color = Pal.Text) }
                MenuCard(expanded = menuOpen, onDismiss = { menuOpen = false }) {
                    PngMenuItem("copiar", "Copiar") { menuOpen = false; actions.onCopyMeal(meal) }
                    PngMenuItem("pegar", "Pegar", enabled = hasClipboard) { menuOpen = false; actions.onPasteMeal(meal) }
                    PngMenuItem("infinito", "Repetir comida") { menuOpen = false; actions.onRepeatMeal(meal) }
                    MdiMenuItem("chart-pie", "Ajustar porciones") { menuOpen = false; actions.onAdjustPortions(meal) }
                    PngMenuItem("pepelera", "Vaciar comida", color = Pal.Red) { menuOpen = false; actions.onClearMeal(meal) }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MdiIcon("fire", size = 15.dp, color = Pal.Text)
            Spacer(Modifier.width(4.dp))
            Text("$kcal kcal", color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.width(6.dp))
            Text("·   $p P | $c C | $f G", color = Pal.Sub, fontSize = 11.sp)
        }
        if (entries.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            entries.forEach { FoodRow(it, actions.catOf(it.name), actions.onToggle, actions.onEntryClick) }
        }
        Spacer(Modifier.height(8.dp))
        AddButton { actions.onAddFood(meal) }
    }
}

/** DropdownMenu con el mismo borde/fondo que las tarjetas (esquinas redondeadas + borde fino). */
@Composable
private fun MenuCard(expanded: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = Pal.Card,
        border = androidx.compose.foundation.BorderStroke(0.7.dp, Pal.Border),
        content = content,
    )
}

/** Fila de menú de ancho fijo: icono a un lado y texto al otro con separación amplia.
 *  `iconLeft=false` (por defecto) -> texto izquierda, icono derecha (menús ⋯);
 *  `iconLeft=true` -> icono izquierda, texto derecha (menú del lápiz, como estaba). */
@Composable
private fun MenuItemRow(text: String, color: Color, enabled: Boolean, iconLeft: Boolean,
                        icon: @Composable () -> Unit, onClick: () -> Unit) {
    val c = if (enabled) color else Pal.Sub.copy(alpha = 0.4f)
    DropdownMenuItem(
        enabled = enabled,
        text = {
            Row(Modifier.width(210.dp), verticalAlignment = Alignment.CenterVertically) {
                if (iconLeft) {
                    icon(); Spacer(Modifier.width(16.dp)); Text(text, color = c, fontSize = 14.sp)
                } else {
                    Text(text, color = c, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(16.dp)); icon()
                }
            }
        },
        onClick = onClick,
    )
}

@Composable
private fun MdiMenuItem(icon: String, text: String, color: Color = Pal.Text, enabled: Boolean = true,
                        iconLeft: Boolean = false, onClick: () -> Unit) {
    val c = if (enabled) color else Pal.Sub.copy(alpha = 0.4f)
    MenuItemRow(text, color, enabled, iconLeft, { MdiIcon(icon, size = 18.dp, color = c) }, onClick)
}

/** Ítem de menú con uno de los PNG propios de Kivy (assets/icons/), tintado. */
@Composable
private fun PngMenuItem(asset: String, text: String, color: Color = Pal.Text, enabled: Boolean = true,
                        iconSize: Dp = 20.dp, iconLeft: Boolean = false, onClick: () -> Unit) {
    val c = if (enabled) color else Pal.Sub.copy(alpha = 0.4f)
    MenuItemRow(text, color, enabled, iconLeft,
        { org.ivansola.minutricion.ui.components.AssetIcon(asset, size = iconSize, tint = c) }, onClick)
}

@Composable
private fun FoodRow(e: Entry, category: String?, onToggle: (Entry) -> Unit, onClick: (Entry) -> Unit) {
    val on = e.included
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onClick(e) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        org.ivansola.minutricion.ui.components.FoodIconDisc(
            e.name, size = 30.dp, emojiSize = 19.dp, category = category)
        Spacer(Modifier.width(8.dp))
        // Como en la base de datos: nombre arriba y, si existe, la marca debajo en gris.
        val (base, brand) = org.ivansola.minutricion.ui.components.splitName(e.name)
        Column(Modifier.weight(1f)) {
            Text(
                base, color = if (on) Pal.Text else Pal.Sub,
                fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1,
            )
            if (brand.isNotEmpty()) {
                Text(brand, color = Pal.Sub, fontSize = 11.sp, maxLines = 1)
            }
        }
        val unit = if (org.ivansola.minutricion.ui.components.isDrink(e.name)) "ml" else "g"
        Column(horizontalAlignment = Alignment.End) {
            Text("${e.grams.roundToInt()} $unit", color = if (on) Pal.Text else Pal.Sub, fontSize = 11.sp)
            Text("${e.totalKcal.roundToInt()} kcal", color = Pal.Sub, fontSize = 10.sp)
        }
        Spacer(Modifier.width(8.dp))
        ToggleCircle(on) { onToggle(e) }
    }
}

@Composable
private fun ToggleCircle(included: Boolean, onClick: () -> Unit) {
    if (included) {
        Box(
            Modifier.size(22.dp).clip(CircleShape)
                .background(Pal.Green.copy(alpha = 0.16f))
                .border(1.6.dp, Pal.Green, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            MdiIcon("check-bold", size = 12.dp, color = Pal.Green)
        }
    } else {
        Box(
            Modifier.size(22.dp).clip(CircleShape)
                .border(1.6.dp, Color(0xFF5A5A5F), CircleShape).clickable(onClick = onClick)
        )
    }
}

@Composable
internal fun AddButton(onClick: () -> Unit) {
    // Mismo relieve que las tarjetas (luz cenital): degradado claro arriba / oscuro abajo y un
    // borde biselado. Los tonos son más claros que los de la tarjeta porque el botón va DENTRO de
    // ella y tiene que verse por encima, no hundido.
    Box(
        Modifier.fillMaxWidth().height(40.dp)
            .raised(RoundedCornerShape(20.dp), BtnDarkTop, BtnDarkBottom, highlight = 0.13f)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        MdiIcon("plus", size = 22.dp, color = Pal.Text)
    }
}

@Composable
internal fun EditButton(onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(46.dp)
            .raisedDark(RoundedCornerShape(23.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            org.ivansola.minutricion.ui.components.AssetIcon("editpencil", size = 20.dp, tint = Pal.Text)
            Spacer(Modifier.width(8.dp))
            Text("Editar", color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

// ---- diálogos del menú del lápiz / porciones ----

@Composable
private fun AdjustPortionsDialog(onApply: (Double) -> Unit, onDismiss: () -> Unit) {
    var factor by remember { mutableStateOf("1") }
    val v = factor.replace(",", ".").toDoubleOrNull() ?: 0.0
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pal.Card2,
        title = { Text("Ajustar porciones", color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column {
                OutlinedTextField(
                    value = factor,
                    onValueChange = { s -> factor = s.filter { it.isDigit() || it == '.' || it == ',' } },
                    label = { Text("Factor (0.5 = mitad, 2 = doble)", color = Pal.Sub) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = fieldColors(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (v > 0) onApply(v) }) {
                Text("Aplicar", color = Pal.Yellow, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Pal.Sub) } },
    )
}
