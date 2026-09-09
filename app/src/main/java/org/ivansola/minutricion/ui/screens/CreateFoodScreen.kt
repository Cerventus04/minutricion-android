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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.ivansola.minutricion.data.Db
import org.ivansola.minutricion.data.Food
import org.ivansola.minutricion.data.Gemini
import org.ivansola.minutricion.data.GeminiFoodResult
import org.ivansola.minutricion.data.Off
import org.ivansola.minutricion.data.OffProduct
import org.ivansola.minutricion.ui.components.AppDropdownMenu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ivansola.minutricion.ui.components.FoodEmoji
import org.ivansola.minutricion.ui.components.MdiIcon
import org.ivansola.minutricion.ui.components.MealBadge
import org.ivansola.minutricion.ui.components.CtaButton
import org.ivansola.minutricion.ui.components.raisedYellow
import org.ivansola.minutricion.ui.components.raisedDark
import org.ivansola.minutricion.ui.theme.Pal

// ---------------------------------------------------------------- utilidades

/**
 * Qué fotos pide cada botón de "Rellenar". Cada apartado del formulario se rellena con la foto que
 * le corresponde, para no hacerle al usuario fotos que no va a usar (ni gastar peticiones en ellas).
 */
private val ALL_SHOTS = Gemini.Shot.entries.toSet()
/** Detalles = nombre, marca, categoría y código: eso está en el frente y en el código de barras. */
private val DETAIL_SHOTS = setOf(Gemini.Shot.FRONT, Gemini.Shot.EAN)
/** Información nutricional (y de paso micronutrientes): solo la tabla. */
private val NUTRITION_SHOTS = setOf(Gemini.Shot.NUTRITION)

/** Filtra a dígitos + separador decimal. */
internal fun numFilter(s: String) = s.filter { it.isDigit() || it == '.' || it == ',' }
internal fun toNum(s: String) = s.replace(",", ".").toDoubleOrNull()

/** Double -> texto para un campo del formulario, sin el ".0" de los enteros ("150.0" -> "150"). */
internal fun num(v: Double): String =
    if (v == Math.floor(v) && !v.isInfinite()) v.toLong().toString() else v.toString()

/**
 * Campo estilo Fitia: caja redondeada con borde fino; la etiqueta actúa de placeholder cuando está
 * vacío. `trailing` permite añadir un selector de unidad o un icono a la derecha.
 */
@Composable
internal fun FField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    number: Boolean = true,
    indent: Dp = 0.dp,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.padding(start = indent).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(14.dp))
                .border(0.7.dp, Pal.Border, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) Text(label, color = Pal.Sub, fontSize = 15.sp)
            BasicTextField(
                value = value,
                onValueChange = { onChange(if (number) numFilter(it) else it) },
                singleLine = true,
                textStyle = TextStyle(color = Pal.Text, fontSize = 15.sp),
                cursorBrush = SolidColor(Pal.Yellow),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        trailing?.invoke()
    }
}

// ------------------------------------------------------------- Ingreso Manual

/**
 * "Ingreso Manual" (hoja inferior, como Fitia): Nombre (opcional), Calorías (obligatorio),
 * Proteínas/Carbohidratos/Grasas (opcional) y "Agregar a [comida]". Los valores son el TOTAL del
 * alimento (se registra con 100 g -> total = valores tal cual).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngresoManualSheet(
    meals: List<String>,
    initialMeal: String,
    onAdd: (Food, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var p by remember { mutableStateOf("") }
    var c by remember { mutableStateOf("") }
    var f by remember { mutableStateOf("") }
    var meal by remember { mutableStateOf(initialMeal) }
    var mealMenu by remember { mutableStateOf(false) }
    val valid = (toNum(kcal) ?: 0.0) > 0.0

    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Pal.Bg) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Ingreso Manual", color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            ManualRow("Nombre") { FField("(opcional)", name, { name = it }, number = false) }
            ManualRow("Calorías (kcal)") { FField("(obligatorio)", kcal, { kcal = it }) }
            ManualRow("Proteínas (g)") { FField("(opcional)", p, { p = it }) }
            ManualRow("Carbohidratos (g)") { FField("(opcional)", c, { c = it }) }
            ManualRow("Grasas (g)") { FField("(opcional)", f, { f = it }) }
            ManualRow("Agregar a", bold = true) {
                Box {
                    Row(
                        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(14.dp))
                            .border(0.7.dp, Pal.Border, RoundedCornerShape(14.dp))
                            .clickable { mealMenu = true }.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(meal, color = Pal.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        MealBadge(meal, size = 24.dp)
                    }
                    AppDropdownMenu(mealMenu, { mealMenu = false }) {
                        meals.forEach { m ->
                            DropdownMenuItem(
                                leadingIcon = { MealBadge(m, size = 22.dp) },
                                text = { Text(m, color = Pal.Text, modifier = Modifier.width(160.dp)) },
                                onClick = { meal = m; mealMenu = false },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp))
                    .background(Pal.Yellow)
                    .clickable(enabled = valid) {
                        val food = Food(
                            id = 0, name = name.ifBlank { "Alimento" }.trim(),
                            kcal = toNum(kcal) ?: 0.0, protein = toNum(p) ?: 0.0,
                            carbs = toNum(c) ?: 0.0, fat = toNum(f) ?: 0.0,
                        )
                        onAdd(food, meal)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("Agregar a $meal", color = Pal.Bg,
                    fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun ManualRow(label: String, bold: Boolean = false, field: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Pal.Text, fontSize = 14.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.width(150.dp))
        Box(Modifier.weight(1f)) { field() }
    }
}

// ------------------------------------------------------------ Crear Alimento

// (etiqueta, clave interna) de vitaminas y minerales del formulario (unidades como Fitia).
private val VIT_FIELDS = listOf(
    "Vitamina A (mcg)" to "vit_a", "Vitamina C (mg)" to "vit_c", "Vitamina D (mcg)" to "vit_d",
    "Vitamina E (mg)" to "vit_e", "Vitamina K (mcg)" to "vit_k", "Vitamina B1 (mg)" to "b1",
    "Vitamina B2 (mg)" to "b2", "Vitamina B3 (mg)" to "b3", "Vitamina B5 (mg)" to "b5",
    "Vitamina B6 (mg)" to "b6", "Vitamina B12 (mcg)" to "b12", "Folato (mcg)" to "folate",
)
private val MIN_FIELDS = listOf(
    "Calcio (mg)" to "calcium", "Hierro (mg)" to "iron", "Magnesio (mg)" to "magnesium",
    "Fósforo (mg)" to "phosphorus", "Potasio (mg)" to "potassium", "Zinc (mg)" to "zinc",
    "Selenio (mcg)" to "selenium", "Cobre (mcg)" to "copper", "Manganeso (mg)" to "manganese",
)

/**
 * "Crear Alimento" a pantalla completa (como Fitia): Detalles del Alimento, Información Nutricional
 * (con sub-nutrientes) y Micronutrientes. Los valores se introducen POR PORCIÓN; se convierten a
 * por 100 g al construir el alimento. `onCreated` recibe el Food resultante (para abrir su ficha).
 *
 * `onPickExisting` se usa cuando, al leer con IA, el alimento resulta ser uno que YA está en la
 * biblioteca: no se ha creado nada, así que quien nos abrió debería enseñar su ficha en vez de
 * mandar a "Creados". Si no se pasa, se cae en `onCreated`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrearAlimentoScreen(
    onCreated: (Food) -> Unit,
    onDismiss: () -> Unit,
    initialBarcode: String = "",
    onPickExisting: ((Food) -> Unit)? = null,
) {
    var brand by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    var portionName by remember { mutableStateOf("") }
    var portionWeight by remember { mutableStateOf("") }
    var portionUnit by remember { mutableStateOf("g") }
    var calUnit by remember { mutableStateOf("kcal") }
    var saltUnit by remember { mutableStateOf("Sal") }
    var sinMarca by remember { mutableStateOf(false) }
    var barcode by remember { mutableStateOf(initialBarcode) }
    var scanBarcode by remember { mutableStateOf(false) }
    // nutrientes principales (por porción)
    var kcal by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var satFat by remember { mutableStateOf("") }
    var transFat by remember { mutableStateOf("") }
    var cholesterol by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var sugars by remember { mutableStateOf("") }
    var addedSugars by remember { mutableStateOf("") }
    var fiber by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var salt by remember { mutableStateOf("") }
    val micros = remember { mutableStateMapOf<String, String>() }

    var detailsOpen by remember { mutableStateOf(true) }
    var infoOpen by remember { mutableStateOf(true) }
    var microOpen by remember { mutableStateOf(false) }
    var catPicker by remember { mutableStateOf(false) }
    var rellenar by remember { mutableStateOf(false) }
    var iaFoto by remember { mutableStateOf(false) }
    // alimentos ya existentes que podrían ser el leído con IA, y la lectura pendiente de aplicar si
    // el usuario decide que no es ninguno y quiere crear uno nuevo.
    var duplicates by remember { mutableStateOf<List<Food>>(emptyList()) }
    var pendingAi by remember { mutableStateOf<GeminiFoodResult?>(null) }
    // producto de Open Food Facts que coincide con el EAN leído (datos ya verificados, mejores que
    // una lectura de fotos), y el aviso mientras se consulta.
    var offMatch by remember { mutableStateOf<OffProduct?>(null) }
    var checkingOff by remember { mutableStateOf(false) }
    // si OFF ya se consultó para la lectura actual, para no repetir la llamada (ni volver a
    // ofrecer lo mismo) cuando el usuario dice que no es ninguno de los candidatos.
    var offChecked by remember { mutableStateOf(false) }
    // qué fotos se van a pedir en la sesión de IA que se abra (depende del apartado desde el que
    // se pulse "Rellenar"); el banner de arriba las pide todas.
    var iaShots by remember { mutableStateOf(ALL_SHOTS) }
    val scope = rememberCoroutineScope()

    // Aplica el texto reconocido (parseo aproximado) a los campos que encuentre.
    fun applyOcr(text: String) {
        val r = parseNutrition(text)
        r.kcal?.let { kcal = it }; r.protein?.let { protein = it }; r.carbs?.let { carbs = it }
        r.fat?.let { fat = it }; r.sat?.let { satFat = it }; r.trans?.let { transFat = it }
        r.sugars?.let { sugars = it }; r.added?.let { addedSugars = it }; r.fiber?.let { fiber = it }
        r.salt?.let { salt = it }; r.cholesterol?.let { cholesterol = it }
    }

    // Aplica lo que Gemini haya leído: a diferencia del OCR offline, también nombre/categoría/EAN
    // y micronutrientes (vitaminas/minerales de los complementos).
    //
    // `shots` = qué se le pidió fotografiar. Solo se rellenan los campos del apartado desde el que
    // se pulsó "Rellenar": si pides la tabla nutricional no tiene sentido que además te cambie el
    // nombre —quizá lo habías escrito tú a mano— a partir de lo que se intuya en esa foto.
    fun applyAi(r: GeminiFoodResult, shots: Set<Gemini.Shot> = ALL_SHOTS) {
        if (!r.found) return
        val details = Gemini.Shot.FRONT in shots || Gemini.Shot.EAN in shots
        val nutrition = Gemini.Shot.NUTRITION in shots
        if (details) {
            if (r.name.isNotBlank()) name = r.name
            r.category?.let { category = it }
            r.ean?.let { barcode = it }
        }
        if (!nutrition) return
        // Los valores se meten en los campos TAL CUAL vienen, y la "porción" se ajusta a lo que
        // representan, que es justo lo que `build()` usa para pasarlos a por 100 g:
        //  - etiqueta normal (por 100 g)  -> porción = 100 g
        //  - complemento (por cápsula/dosis) -> porción = el peso de esa dosis, si la etiqueta lo dice
        if (r.perServing && r.servingG != null) {
            portionWeight = num(r.servingG); portionUnit = "g"
        } else if (!r.perServing) {
            portionWeight = "100"; portionUnit = "g"
        }   // por ración pero sin peso conocido (típico de cápsulas): se deja como esté y los
            // valores quedan tal cual, que es lo único honesto sin saber cuánto pesa la dosis.
        r.servingName?.let { if (portionName.isBlank()) portionName = it }
        calUnit = "kcal"
        if (r.kcal > 0) kcal = num(r.kcal)
        if (r.protein > 0) protein = num(r.protein)
        if (r.carbs > 0) carbs = num(r.carbs)
        if (r.fat > 0) fat = num(r.fat)
        r.nutrients["sat_fat"]?.let { satFat = num(it) }
        r.nutrients["trans_fat"]?.let { transFat = num(it) }
        r.nutrients["sugars"]?.let { sugars = num(it) }
        r.nutrients["added_sugars"]?.let { addedSugars = num(it) }
        r.nutrients["fiber"]?.let { fiber = num(it) }
        r.nutrients["cholesterol"]?.let { cholesterol = num(it) }
        // el formulario tiene UN campo para sal/sodio con selector de unidad: se usa el que venga
        // (si vienen los dos, la sal, que es lo que suele imprimir la etiqueta europea).
        val gSalt = r.nutrients["salt"]
        val mgSodium = r.nutrients["sodium"]
        if (gSalt != null) { salt = num(gSalt); saltUnit = "Sal" }
        else if (mgSodium != null) { salt = num(mgSodium); saltUnit = "Sodio" }
        // vitaminas y minerales: en un complemento son LO ÚNICO que aporta la etiqueta, así que si
        // vienen se abre la sección de micronutrientes para que se vean sin tener que buscarla.
        (VIT_FIELDS + MIN_FIELDS).forEach { (_, key) ->
            r.nutrients[key]?.let { micros[key] = num(it) }
        }
        if ((VIT_FIELDS + MIN_FIELDS).any { r.nutrients.containsKey(it.second) }) microOpen = true
    }

    val valid = name.isNotBlank() && (toNum(kcal) ?: 0.0) > 0.0

    fun build(): Food {
        // Peso de la porción a gramos según la unidad (oz/fl oz ≈ 28,35 / 29,57).
        val unitG = when (portionUnit) { "oz" -> 28.3495; "fl oz" -> 29.5735; else -> 1.0 }
        val w = (toNum(portionWeight) ?: 0.0) * unitG
        val factor = if (w > 0) 100.0 / w else 1.0     // por porción -> por 100
        fun v(s: String) = (toNum(s) ?: 0.0) * factor
        val nutr = HashMap<String, Double>()
        fun put(key: String, s: String) { if (toNum(s) != null) nutr[key] = v(s) }
        put("sat_fat", satFat); put("trans_fat", transFat); put("cholesterol", cholesterol)
        put("sugars", sugars); put("added_sugars", addedSugars); put("fiber", fiber)
        if (saltUnit == "Sodio") put("sodium", salt) else put("salt", salt)
        (VIT_FIELDS + MIN_FIELDS).forEach { (_, k) -> micros[k]?.let { put(k, it) } }
        val full = if (sinMarca || brand.isBlank()) name.trim() else "${name.trim()} (${brand.trim()})"
        val kcalP = v(kcal).let { if (calUnit == "kJ") it / 4.184 else it }   // kJ -> kcal
        return Food(
            id = 0, name = full, kcal = kcalP, protein = v(protein), carbs = v(carbs),
            fat = v(fat), nutrients = nutr, userCreated = true,
            serving = if (w > 0) w else null, category = category,
            barcode = barcode.ifBlank { null },
        )
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxHeight().fillMaxWidth().background(Pal.Bg)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(50)).background(Pal.Card2)
                    .clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                    MdiIcon("chevron-down", size = 20.dp, color = Pal.Text)
                }
                Spacer(Modifier.width(12.dp))
                Text("Crear Alimento", color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Banner "Ahorra tiempo": con IA (Gemini) rellena TODO (nombre, categoría, EAN y
                // tabla); si no hay clave configurada, solo queda el reconocimiento de texto
                // offline (macros básicos, como antes).
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(Pal.Yellow.copy(alpha = 0.18f)).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Ahorra tiempo", color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            if (Gemini.available) "Toma fotos y la IA rellena nombre, EAN y tabla"
                            else "Toma foto y leemos el texto de la etiqueta",
                            color = Pal.Sub, fontSize = 13.sp,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Row(Modifier.raisedYellow(RoundedCornerShape(22.dp))
                        .clickable { iaShots = ALL_SHOTS; if (Gemini.available) iaFoto = true else rellenar = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        org.ivansola.minutricion.ui.components.EmojiIcon(
                            if (Gemini.available) "🤖" else "📷", size = 16.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(if (Gemini.available) "Rellenar con IA" else "Tomar foto",
                            color = Pal.Bg, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
                // Detalles del Alimento
                SectionCard("Detalles del Alimento", detailsOpen, { detailsOpen = !detailsOpen },
                    onFill = { iaShots = DETAIL_SHOTS; if (Gemini.available) iaFoto = true else rellenar = true }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FField("Marca", brand, { brand = it; if (it.isNotEmpty()) sinMarca = false },
                            number = false, modifier = Modifier.weight(1f))
                        Box(Modifier.height(56.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (sinMarca) Pal.Yellow.copy(alpha = 0.22f) else Pal.Card2)
                            .clickable { sinMarca = true; brand = "" }.padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center) {
                            Text("Sin marca", color = if (sinMarca) Pal.Yellow else Pal.Text, fontSize = 14.sp)
                        }
                    }
                    FField("Nombre*", name, { name = it }, number = false)
                    Row(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(14.dp))
                        .border(0.7.dp, Pal.Border, RoundedCornerShape(14.dp))
                        .clickable { catPicker = true }.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(category ?: "Categoría", color = if (category == null) Pal.Sub else Pal.Text,
                            fontSize = 15.sp, modifier = Modifier.weight(1f))
                        MdiIcon("chevron-down", size = 20.dp, color = Pal.Sub)
                    }
                    // Código de barras: al escanear se ASOCIA al alimento (registro local); un futuro
                    // escaneo del mismo código lo encontrará en tu biblioteca.
                    Row(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(14.dp))
                        .border(0.7.dp, Pal.Border, RoundedCornerShape(14.dp))
                        .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            if (barcode.isEmpty()) Text("Código de Barras", color = Pal.Sub, fontSize = 15.sp)
                            BasicTextField(
                                value = barcode, onValueChange = { barcode = it.filter { ch -> ch.isDigit() } },
                                singleLine = true, textStyle = TextStyle(color = Pal.Text, fontSize = 15.sp),
                                cursorBrush = SolidColor(Pal.Yellow), modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Box(Modifier.size(28.dp).clickable { scanBarcode = true },
                            contentAlignment = Alignment.Center) {
                            org.ivansola.minutricion.ui.components.EmojiIcon("📷", size = 18.dp)
                        }
                    }
                }
                // Información Nutricional
                SectionCard("Información Nutricional", infoOpen, { infoOpen = !infoOpen },
                    onFill = { iaShots = NUTRITION_SHOTS; if (Gemini.available) iaFoto = true else rellenar = true }) {
                    FField("Nombre de la porción*", portionName, { portionName = it }, number = false)
                    FField("Peso de la porción", portionWeight, { portionWeight = it }, trailing = {
                        UnitChip(portionUnit, listOf("g", "oz", "ml", "fl oz")) { portionUnit = it }
                    })
                    FField("Calorías", kcal, { kcal = it }, trailing = {
                        UnitChip(calUnit, listOf("kcal", "kJ")) { calUnit = it }
                    })
                    FField("Grasas (g)", fat, { fat = it })
                    FField("G.Saturada (g)", satFat, { satFat = it }, indent = 20.dp)
                    FField("G. Trans (g)", transFat, { transFat = it }, indent = 20.dp)
                    FField("Colesterol (mg)", cholesterol, { cholesterol = it })
                    FField("Carbohidratos Totales (g)", carbs, { carbs = it })
                    FField("Azúcares (g)", sugars, { sugars = it }, indent = 20.dp)
                    FField("Azúcares añadidos (g)", addedSugars, { addedSugars = it }, indent = 40.dp)
                    FField("Fibra (g)", fiber, { fiber = it }, indent = 20.dp)
                    FField("Proteínas (g)", protein, { protein = it })
                    FField("Sal (g)", salt, { salt = it }, trailing = {
                        UnitChip(saltUnit, listOf("Sal", "Sodio")) { saltUnit = it }
                    })
                }
                // Micronutrientes
                SectionCard("Micronutrientes", microOpen, { microOpen = !microOpen }) {
                    Text("Vitaminas", color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    VIT_FIELDS.forEach { (label, key) ->
                        FField(label, micros[key] ?: "", { micros[key] = it })
                    }
                    Text("Minerales", color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    MIN_FIELDS.forEach { (label, key) ->
                        FField(label, micros[key] ?: "", { micros[key] = it })
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Box(Modifier.fillMaxWidth().padding(16.dp)) {
                CtaButton("Crear Alimento", { onCreated(build()) }, enabled = valid)
            }
        }
    }

    if (catPicker) CategoryPickerSheet(onPick = { category = it; catPicker = false },
        onDismiss = { catPicker = false })
    if (rellenar) RellenarScreen(onText = { applyOcr(it); rellenar = false },
        onClose = { rellenar = false })
    // Al leer con IA se comprueba, POR ESTE ORDEN, si el alimento ya existe: 1) en tu biblioteca
    // (por EAN, o por nombre+macros si la ficha no tenía código) y 2) en Open Food Facts, por el
    // EAN leído. Lo segundo importa: la IA lee bien la etiqueta, pero si el producto ya está
    // catalogado sus datos vienen verificados y con ingredientes y alérgenos que en la foto no
    // salen, así que no tiene sentido crear una ficha nueva a mano.
    /** Último recurso: buscar por parecido de nombre+macros en la biblioteca, o crear nuevo. */
    fun similarThenApply(r: GeminiFoodResult) {
        offChecked = true   // llegar aquí significa que OFF ya se miró (o no había código)
        val similar = if (r.found) {
            Db.findSimilarFoods(r.name, r.kcal, r.protein, r.carbs, r.fat)
        } else emptyList()
        if (similar.isNotEmpty()) { pendingAi = r; duplicates = similar } else applyAi(r, iaShots)
    }

    fun checkOffThenApply(r: GeminiFoodResult) {
        val code = r.ean
        if (code == null) { similarThenApply(r); return }  // sin código no se puede buscar en OFF
        checkingOff = true
        scope.launch {
            val prod = withContext(Dispatchers.IO) { Off.byBarcode(code) }
            checkingOff = false
            if (prod != null) { pendingAi = r; offMatch = prod } else similarThenApply(r)
        }
    }

    if (iaFoto) IaFotoScreen(
        // si el formulario ya trae código (se llegó aquí desde un escaneo), no se pide su foto
        knownBarcode = barcode.trim(),
        shots = iaShots,
        onResult = { rRaw ->
            // el código que ya había manda si la IA no leyó ninguno: es el que el escáner
            // descodificó, y sin esto las búsquedas de duplicados se quedarían sin clave.
            val r = if (rRaw.ean.isNullOrBlank() && barcode.isNotBlank()) {
                rRaw.copy(ean = barcode.trim())
            } else rRaw
            iaFoto = false
            offChecked = false
            // Se va a Open Food Facts CUANTO ANTES: tiene muchísimos más productos que tu
            // biblioteca, así que es donde más probable es acertar a la primera. Lo único que se
            // mira antes es si ya tienes ese MISMO código guardado, porque es una consulta local
            // instantánea (no cuesta tiempo) y evita crear un duplicado de algo que ya es tuyo.
            // La búsqueda por parecido de nombre, que es la que hace perder tiempo con diálogos
            // dudosos, queda al final: solo si OFF no lo conoce.
            // Si solo se pidió la tabla nutricional no se busca si el alimento existe: el
            // usuario está rellenando un campo de SU ficha, no registrando un producto nuevo, y
            // preguntarle "¿es este?" en mitad de eso no viene a cuento.
            if (iaShots == NUTRITION_SHOTS) {
                applyAi(r, iaShots)
            } else {
                val mine = r.ean?.let { Db.foodsByBarcode(it) }.orEmpty()
                if (mine.isNotEmpty()) { pendingAi = r; duplicates = mine } else checkOffThenApply(r)
            }
        },
        onClose = { iaFoto = false },
    )

    if (checkingOff) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = Pal.Card2,
            title = { Text("Comprobando…", color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Pal.Yellow, strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Buscando si este alimento ya existe", color = Pal.Sub, fontSize = 13.sp)
                }
            },
            confirmButton = {},
        )
    }

    // Hay un alimento catalogado con ese código. Se presenta igual que los de la biblioteca —una
    // fila que se toca para abrir su ficha y añadirlo—: de dónde salen los datos es un detalle de
    // implementación que al usuario no le aporta nada para decidir si es su producto o no.
    offMatch?.let { prod ->
        val found = prod.toFood().copy(barcode = prod.code)
        AlertDialog(
            onDismissRequest = { offMatch = null; pendingAi = null },
            containerColor = Pal.Card2,
            title = { Text("¿Es este?", color = Pal.Text,
                fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("He encontrado un alimento que podría ser el que estás registrando. " +
                        "Tócalo para abrirlo y añadirlo:", color = Pal.Sub, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .clickable {
                                offMatch = null; pendingAi = null
                                if (onPickExisting != null) onPickExisting(found) else onCreated(found)
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FoodEmoji(found.name, size = 26.dp, category = found.category)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(found.name, color = Pal.Text, fontSize = 14.sp)
                            Text("${num(prod.kcal)} kcal · P ${num(prod.protein)} · " +
                                "C ${num(prod.carbs)} · G ${num(prod.fat)}",
                                color = Pal.Sub, fontSize = 11.sp)
                        }
                        MdiIcon("chevron-right", size = 18.dp, color = Pal.Sub)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingAi?.let { applyAi(it, iaShots) }
                    offMatch = null; pendingAi = null
                }) { Text("No es este, crearlo", color = Pal.Yellow, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { offMatch = null; pendingAi = null }) {
                    Text("Cancelar", color = Pal.Sub)
                }
            },
        )
    }

    // Lo leído por la IA podría ser algo que ya tienes: se muestran los candidatos con sus macros
    // (para poder distinguir formatos/sabores parecidos) y decide el usuario.
    if (duplicates.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { duplicates = emptyList(); pendingAi = null },
            containerColor = Pal.Card2,
            title = { Text("¿Ya lo tienes?", color = Pal.Text,
                fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Esto ya podría estar en tu biblioteca. Toca el que sea para usarlo:",
                        color = Pal.Sub, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    duplicates.forEach { ex ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    // si esa ficha no tenía código, se le asocia el leído: el
                                    // próximo escaneo la encontrará ya por EAN.
                                    val code = pendingAi?.ean
                                    if (code != null) Db.attachBarcode(ex, code)
                                    duplicates = emptyList(); pendingAi = null
                                    val food = if (code != null && ex.barcode.isNullOrBlank())
                                        ex.copy(barcode = code) else ex
                                    // el alimento YA existe: lo suyo es abrir su ficha para
                                    // añadirlo, no tratarlo como recién creado (que llevaba a la
                                    // lista de "Creados"). Si quien nos abrió no sabe mostrar
                                    // fichas, se queda el comportamiento de antes.
                                    if (onPickExisting != null) onPickExisting(food)
                                    else onCreated(food)
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FoodEmoji(ex.name, size = 26.dp, category = ex.category)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(ex.name, color = Pal.Text, fontSize = 14.sp)
                                Text("${ex.kcal.toInt()} kcal · P ${ex.protein} · C ${ex.carbs} · G ${ex.fat}",
                                    color = Pal.Sub, fontSize = 11.sp)
                            }
                            MdiIcon("chevron-right", size = 18.dp, color = Pal.Sub)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    // "es nuevo" solo quiere decir que no está en TU biblioteca: si aún no se ha
                    // mirado OFF (caso de coincidencia por código), puede estar catalogado allí.
                    val r = pendingAi
                    duplicates = emptyList(); pendingAi = null
                    r?.let { if (offChecked) applyAi(it, iaShots) else checkOffThenApply(it) }
                }) { Text("Ninguno, es nuevo", color = Pal.Yellow, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { duplicates = emptyList(); pendingAi = null }) {
                    Text("Cancelar", color = Pal.Sub)
                }
            },
        )
    }
    if (scanBarcode) ScanScreen(onResult = { barcode = it; scanBarcode = false },
        onClose = { scanBarcode = false })
}

@Composable
private fun SectionCard(title: String, open: Boolean, onToggle: () -> Unit,
                        onFill: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Pal.Card)
        .padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            if (onFill != null) {
                Spacer(Modifier.width(10.dp))
                Row(Modifier.clip(RoundedCornerShape(16.dp)).background(Pal.Card2)
                    .clickable(onClick = onFill).padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    org.ivansola.minutricion.ui.components.EmojiIcon("📷", size = 15.dp)
                    Spacer(Modifier.width(5.dp))
                    Text("Rellenar", color = Pal.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.clickable(onClick = onToggle)) {
                MdiIcon(if (open) "chevron-up" else "chevron-down", size = 22.dp, color = Pal.Sub)
            }
        }
        if (open) content()
    }
}

@Composable
private fun UnitChip(selected: String, options: List<String>, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.height(56.dp).clip(RoundedCornerShape(14.dp)).background(Pal.Card2)
            .clickable { open = true }.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(selected, color = Pal.Text, fontSize = 14.sp)
            Spacer(Modifier.width(4.dp))
            MdiIcon("chevron-down", size = 16.dp, color = Pal.Sub)
        }
        AppDropdownMenu(open, { open = false }) {
            options.forEach { o ->
                DropdownMenuItem(text = { Text(o, color = Pal.Text, modifier = Modifier.width(90.dp)) },
                    onClick = { onSelect(o); open = false })
            }
        }
    }
}

// ----------------------------------------------------- selector de categoría

/**
 * "Elige una categoría" a pantalla completa (Dialog) con buscador + lista con emoji. Se usa Dialog
 * en vez de hoja inferior para que al SCROLLEAR la lista no se cierre por el gesto de arrastre.
 */
@Composable
fun CategoryPickerSheet(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()
    val list = remember(q) {
        org.ivansola.minutricion.data.FoodCategories.ALL
            .filter { q.isEmpty() || it.label.lowercase().contains(q) }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxHeight().fillMaxWidth().background(Pal.Bg).padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Elige una categoría", color = Pal.Text, fontWeight = FontWeight.Bold,
                    fontSize = 22.sp, modifier = Modifier.weight(1f))
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(50)).background(Pal.Card2)
                    .clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                    Text("✕", color = Pal.Text, fontSize = 16.sp)
                }
            }
            Row(
                Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(23.dp))
                    .background(Pal.Card2).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MdiIcon("magnify", size = 20.dp, color = Pal.Sub)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Buscar", color = Pal.Sub, fontSize = 15.sp)
                    BasicTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        textStyle = TextStyle(color = Pal.Text, fontSize = 15.sp),
                        cursorBrush = SolidColor(Pal.Yellow), modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(list, key = { it.label }) { cat ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(cat.label) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        org.ivansola.minutricion.ui.components.FoodEmoji(
                            cat.label, size = 26.dp, category = cat.label)
                        Spacer(Modifier.width(14.dp))
                        Text(cat.label, color = Pal.Text, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
