package org.ivansola.minutricion.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BakeryDining
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Egg
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.LunchDining
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.SetMeal
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ivansola.minutricion.data.Logic
import org.ivansola.minutricion.ui.theme.Pal
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.sin

/**
 * Tarjeta oscura en relieve, como si sobresaliera del fondo. Sobre un fondo casi negro una sombra
 * sola no se ve, así que el relieve se consigue imitando una luz cenital con tres capas:
 *  - un degradado de arriba (más claro) a abajo (más oscuro), que da el volumen;
 *  - un borde también degradado: brillo en el canto superior y sombra en el inferior (bisel);
 *  - una sombra proyectada suave, que separa la tarjeta del fondo.
 */
private val CardTop = Color(0xFF1B1B20)
private val CardBottom = Color(0xFF0B0B0D)

/**
 * Da a cualquier superficie el aspecto de relieve de las tarjetas: degradado vertical (la cara de
 * arriba recibe la luz), borde biselado (canto superior con brillo, inferior en sombra) y una
 * sombra suave. `highlight`/`shade` se suben en superficies claras, donde un brillo del 11 % no se
 * distingue del propio color.
 */
fun Modifier.raised(
    shape: Shape,
    top: Color,
    bottom: Color,
    highlight: Float = 0.12f,
    shade: Float = 0.45f,
    elevation: Dp = 6.dp,
    /** Borde de un solo color (el del brillo superior) en todo el contorno, en vez del bisel. */
    uniformBorder: Boolean = false,
    borderWidth: Dp = 1.dp,
): Modifier = this
    .shadow(elevation, shape, clip = false)
    .clip(shape)
    .background(Brush.verticalGradient(listOf(top, bottom)))
    .border(
        borderWidth,
        if (uniformBorder) SolidColor(Color.White.copy(alpha = highlight))
        else Brush.verticalGradient(
            0f to Color.White.copy(alpha = highlight),
            0.55f to Color.White.copy(alpha = highlight * 0.25f),
            1f to Color.Black.copy(alpha = shade),
        ),
        shape,
    )

/**
 * Lo contrario de [raised]: la superficie parece HUNDIDA. Misma luz cenital, pero al revés — la
 * pared de arriba queda en sombra y la de abajo iluminada. Es lo que se usa en un selector como
 * el de Buscar/Escanear: el carril hundido y la opción activa sobresaliendo de él.
 */
fun Modifier.inset(
    shape: Shape,
    top: Color,
    bottom: Color,
    shade: Float = 0.55f,
    highlight: Float = 0.06f,
): Modifier = this
    .clip(shape)
    .background(Brush.verticalGradient(listOf(top, bottom)))
    .border(
        1.dp,
        Brush.verticalGradient(
            0f to Color.Black.copy(alpha = shade),
            0.5f to Color.Black.copy(alpha = shade * 0.35f),
            1f to Color.White.copy(alpha = highlight),
        ),
        shape,
    )

/** Tonos del carril de un selector (más oscuro que la pantalla, como un surco). */
val TrackTop = Color(0xFF141416)
val TrackBottom = Color(0xFF232327)

/** Tonos del botón amarillo en relieve (el plano era Pal.Yellow a secas). */
val BtnYellowTop = Color(0xFFFFD24E)
val BtnYellowBottom = Color(0xFFEDAC0C)
/** Tonos de un botón oscuro dentro de una tarjeta (más claros que ella, para que sobresalga). */
val BtnDarkTop = Color(0xFF26262C)
val BtnDarkBottom = Color(0xFF141417)

/** Relieve de un botón amarillo. El brillo del bisel sube mucho: sobre un color claro, un blanco
 *  al 11 % (el de las tarjetas) no se distingue del propio amarillo. */
fun Modifier.raisedYellow(shape: Shape, elevation: Dp = 6.dp) =
    raised(shape, BtnYellowTop, BtnYellowBottom, highlight = 0.40f, shade = 0.22f, elevation)

/** Relieve de un botón oscuro. */
fun Modifier.raisedDark(shape: Shape, elevation: Dp = 6.dp) =
    raised(shape, BtnDarkTop, BtnDarkBottom, highlight = 0.13f, elevation = elevation)

/**
 * Botón de acción principal (amarillo, texto centrado). Estaba repetido casi igual en cinco
 * pantallas; unificarlo evita que el relieve quede aplicado en unas sí y en otras no.
 */
@Composable
fun CtaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 54.dp,
    enabled: Boolean = true,
    fontSize: androidx.compose.ui.unit.TextUnit = 16.sp,
) {
    val shape = RoundedCornerShape(height / 2)
    Box(
        modifier.fillMaxWidth().height(height)
            .alpha(if (enabled) 1f else 0.45f)
            .raisedYellow(shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Pal.Bg, fontWeight = FontWeight.Bold, fontSize = fontSize)
    }
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    padding: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            // borde fino y del mismo tono en todo el contorno; el volumen lo sigue dando el
            // degradado del fondo, no el bisel.
            .raised(shape, CardTop, CardBottom, highlight = 0.11f, elevation = 10.dp,
                uniformBorder = true, borderWidth = 0.6.dp)
            .padding(padding),
        content = content,
    )
}

/** DropdownMenu con el mismo borde/fondo que las tarjetas (para usar en toda la app). */
@Composable
fun AppDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    androidx.compose.material3.DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = Pal.Card,
        border = androidx.compose.foundation.BorderStroke(0.7.dp, Pal.Border),
        content = content,
    )
}

// ---------------------------------------------------------------- comidas

/** Color de acento por comida (idéntico a MEAL_COLOR de Kivy). */
fun mealColor(meal: String): Color = when (meal.lowercase()) {
    "desayuno" -> Color(0xFF7C5CFF)
    "comida", "almuerzo" -> Color(0xFF8E8E93)
    "merienda" -> Color(0xFFE5484D)
    "cena" -> Color(0xFF5B6EE5)
    else -> Pal.Sub
}

// Palabras que identifican una bebida (→ unidad ml + icono 🥤/☕/…). Genéricas + marcas comunes.
// OJO: nada de fragmentos como "te " (rompería "corte fino"/"tomate frito") ni "ron" ("macarrones").
private val DRINK_WORDS = listOf(
    // genéricas / tipos (subcadenas seguras)
    // OJO: "agua" NO va aquí (marcaba "aguacate" como bebida); está en DRINK_TOKENS, que compara
    // la palabra entera.
    "leche", "zumo", "jugo", "refresco", "bebida", "batido", "smoothie",
    "café", "cafe", "cortado", "capuchino", "cappuccino", "latte", "colacao", "cola cao",
    "matcha", "infusión", "infusion", "cerveza", "vino", "sidra",
    "champán", "champan", "licor", "vodka", "ginebra", "whisky", "mojito",
    "gaseosa", "tónica", "tonica", "energética", "energetica", "energético", "energetico",
    "energy", "isotónic", "isotonic", "kombucha", "horchata", "mosto", "granizado", "chupito",
    // marcas
    "monster", "red bull", "redbull", "powerking", "power king", "reign", "rockstar", "burn",
    "fanta", "coca-cola", "cocacola", "pepsi", "sprite", "aquarius", "nestea", "trina",
    "seven up", "7up", "gatorade", "powerade", "nesquik", "aquabona", "font vella", "bezoya",
)

// Palabras que se comparan como TOKEN completo (evita falsos positivos por subcadena).
private val DRINK_TOKENS = setOf("té", "te", "tea", "cola", "kas", "ron", "cava", "sprite", "agua")

// Volumen típico de bebida: "33cl", "500 ml", "1 l", "1,5 litros"…
private val DRINK_VOLUME = Regex("""\d+[.,]?\d*\s?(?:cl|ml|litros?|lt|l)\b""")

/** true si el nombre parece una bebida (unidad ml vs g). */
fun isDrink(name: String): Boolean {
    val n = name.lowercase()
    if (DRINK_WORDS.any { it in n }) return true
    val tokens = n.split(' ', ',', '.', '-', '(', ')', '/').filter { it.isNotBlank() }
    if (tokens.any { it in DRINK_TOKENS }) return true
    return DRINK_VOLUME.containsMatchIn(n)
}

// Categorías de Open Food Facts que indican bebida (más fiable que el nombre).
private val DRINK_CATEGORY_WORDS = listOf(
    "beverage", "drink", "soda", "water", "juice", "coffee", "tea", "milk", "smoothie",
    "cocktail", "lemonade", "energy", "kombucha", "cider", "beer", "wine", "spirit", "bebida",
)

/** true si alguna categoría de OFF indica que es una bebida.
 *  Ignora los paraguas genéricos "...foods-and-beverages" (contienen "food"): si no, un tomate
 *  frito ("plant-based-foods-and-beverages") se detectaría como bebida por error. */
fun isDrinkByCategory(categories: List<String>): Boolean =
    categories.any { c ->
        val l = c.lowercase()
        if ("food" in l) return@any false
        DRINK_CATEGORY_WORDS.any { it in l }
    }

/** Insignia de comida: círculo de color LISO con el icono MDI en blanco (como Kivy meal_badge). */
@Composable
fun MealBadge(meal: String, size: Dp = 32.dp) {
    // Barra vertical del color de la comida, sin pictograma: mantiene la señal de color —que es lo
    // que deja localizar una comida en una lista sin leerla— pero sin dibujar ningún objeto.
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(width = 4.dp, height = size * 0.72f)
                .clip(RoundedCornerShape(2.dp))
                .background(mealColor(meal))
        )
    }
}

/**
 * Icono de un alimento sobre su disco en relieve. Vive aquí y no en cada pantalla porque lo usan
 * tanto el diario como la lista de "Añadir alimento", y tienen que verse idénticos.
 */
@Composable
fun FoodIconDisc(
    name: String,
    size: Dp = 32.dp,
    emojiSize: Dp = 20.dp,
    category: String? = null,
    drink: Boolean = isDrink(name),
) {
    Box(
        Modifier.size(size).raised(CircleShape, DiscTop, DiscBottom, highlight = 0.13f, elevation = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        FoodEmoji(name, size = emojiSize, drink = drink, category = category)
    }
}

/** Tonos del disco del icono de alimento (algo más claros que la tarjeta que lo contiene). */
private val DiscTop = Color(0xFF2B2B31)
private val DiscBottom = Color(0xFF171719)

// ---------------------------------------------------------------- velocímetro

/**
 * Velocímetro semicircular de calorías (dome tipo Fitia), portado de `pilgauge.py`:
 * arco ±26° desde arriba, banda 90-110% (t 0.35–0.65) con marcadores lo/hi, y un
 * marcador circular con check en el valor actual.
 */
@Composable
fun CalorieGauge(consumed: Double, target: Int, modifier: Modifier = Modifier) {
    val hasTarget = target > 0
    val spread = 26.0
    val lo = target * 0.9
    val hi = target * 1.1
    val topV = target * 2.0
    val tLo = 0.35; val tHi = 0.65

    fun tof(v: Double): Double {
        if (v <= 0.0 || !hasTarget) return 0.0
        if (v <= lo) return v / lo * tLo
        if (v <= hi) return tLo + (v - lo) / (hi - lo) * (tHi - tLo)
        return minOf(tHi + (v - hi) / (topV - hi) * (1.0 - tHi), 1.0)
    }

    val curTarget = tof(consumed).toFloat()
    // Verde solo en rango (90–110%); por debajo o POR ENCIMA, amarillo (como Fitia).
    val color = when {
        !hasTarget -> Pal.Yellow
        Logic.kcalOk(consumed, target.toDouble()) -> Pal.Green
        else -> Pal.Yellow
    }
    // El progreso del velocímetro se ANIMA (crece) al aparecer o al cambiar de día.
    val animT = remember { Animatable(0f) }
    LaunchedEffect(curTarget) { animT.animateTo(curTarget, tween(500)) }

    Canvas(modifier) {
        val curT = animT.value
        val w = size.width
        val pad = 20.dp.toPx()
        val ytop = 27.dp.toPx()   // única diferencia: la cúpula va centrada, no pegada arriba   // cúpula centrada en el hueco que queda bajo el número
        val sinSpread = sin(Math.toRadians(spread)).toFloat()
        val r = (w / 2f - pad) / sinSpread
        val cx = w / 2f
        val cy = ytop + r
        val lw = 7.dp.toPx()
        val a0 = (270.0 - spread).toFloat()
        val sweepFull = (2 * spread).toFloat()
        val topLeft = Offset(cx - r, cy - r)
        val arcSize = Size(2 * r, 2 * r)

        // pista + progreso (extremos redondeados)
        // Pal.Track y no Pal.Card2: este arco vive DENTRO de una tarjeta cuyo fondo empieza en
        // #1B1B20, y Card2 (#19191C) queda a 1,02:1 de contraste — invisible. Pal.Track es el
        // mismo carril que usan las barras de macro, así que además unifica.
        drawArc(Pal.Track, a0, sweepFull, false, topLeft, arcSize,
            style = Stroke(lw, cap = StrokeCap.Round))
        if (curT > 0f) drawArc(color, a0, sweepFull * curT, false, topLeft, arcSize,
            style = Stroke(lw, cap = StrokeCap.Round))

        fun pt(t: Double): Offset {
            val phi = Math.toRadians(-spread + t * 2 * spread)
            return Offset((cx + r * sin(phi)).toFloat(), (ytop + r * (1 - cos(phi))).toFloat())
        }

        // marcadores de la banda aceptable (lo/hi) + su valor debajo (como Kivy)
        if (hasTarget) {
            val hh = 7.dp.toPx()
            val labelPaint = android.graphics.Paint().also {
                it.color = Pal.Sub.toArgb()
                it.textAlign = android.graphics.Paint.Align.CENTER
                it.textSize = 11.dp.toPx()
                it.isAntiAlias = true
            }
            // Si te has colado del límite (por encima de la banda), las marcas rectas van en BLANCO.
            val over = consumed > hi
            for (v in listOf(lo, hi)) {
                val t = tof(v)
                val p = pt(t)
                val capCol = if (over) Color.White else if (curT >= t.toFloat()) color else Pal.Sub
                drawLine(Pal.Card, Offset(p.x, p.y - hh - 3.dp.toPx()),
                    Offset(p.x, p.y + hh + 3.dp.toPx()), strokeWidth = 9.dp.toPx())
                drawLine(capCol, Offset(p.x, p.y - hh), Offset(p.x, p.y + hh),
                    strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                drawContext.canvas.nativeCanvas.drawText(
                    String.format(java.util.Locale.US, "%,d", v.toInt()),
                    p.x, p.y + 20.dp.toPx(), labelPaint,
                )
            }
        }

        // marcador del valor actual: círculo + tick (check)
        val p = pt(curT.toDouble())
        val mr = 10.dp.toPx()
        drawCircle(color, mr, p)
        drawCircle(Pal.Card, mr, p, style = Stroke(2.dp.toPx()))
        val u = 1.dp.toPx()
        val check = Path().apply {
            moveTo(p.x - 2.8f * u, p.y + 0.3f * u)
            lineTo(p.x - 0.9f * u, p.y + 2.4f * u)
            lineTo(p.x + 2.9f * u, p.y - 2.8f * u)
        }
        drawPath(check, Color(0xFF3A3A3D),
            style = Stroke(2.1f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Columna de macro del resumen: nombre · valor · barra fina (amarilla; verde al alcanzar). */
@Composable
fun MacroColumn(
    name: String,
    value: String,
    frac: Float,
    reached: Boolean,
    modifier: Modifier = Modifier,
    /** Color propio del macro; si es null se usa el amarillo de siempre. */
    color: Color? = null,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(name, color = Pal.Text, fontSize = 12.sp)
        Spacer(Modifier.height(3.dp))
        Text(value, color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(5.dp))
        MacroTrack(frac, reached, 7.dp, color)
    }
}

/** Barra fina de macro con un punto al inicio incluso a 0. El relleno se ANIMA (crece) al aparecer
 *  o cambiar de valor (p. ej. al pasar de día). */
@Composable
fun MacroTrack(frac: Float, reached: Boolean, height: Dp, color: Color? = null) {
    val target = frac.coerceIn(0f, 1f)
    val anim = remember { Animatable(0f) }
    LaunchedEffect(target) { anim.animateTo(target, tween(450)) }
    // El verde de "objetivo cumplido" manda sobre el color del macro: informa, no decora.
    val col = if (reached) Pal.Green else color ?: Pal.Yellow
    Box(Modifier.fillMaxWidth().height(height).clip(CircleShape).background(Pal.Track)) {
        // Punto de color SIEMPRE visible al inicio (incluso a 0), como Kivy.
        Box(Modifier.align(Alignment.CenterStart).size(height).clip(CircleShape).background(col))
        // Relleno animado.
        Box(Modifier.fillMaxWidth(anim.value).fillMaxHeight().clip(CircleShape).background(col))
        // Tenue tinte del color hasta el objetivo: con la barra a 0 el carril gris no dice nada,
        // y así se ve cuánto falta sin añadir números.
        Box(Modifier.fillMaxWidth().fillMaxHeight().clip(CircleShape)
            .background(col.copy(alpha = 0.14f)))
    }
}

// ---------------------------------------------------------------- tira de semana

/** Estado de un día en la tira de la semana (color del punto inferior). */
enum class DayMark { NONE, LOGGED, PERFECT }

/**
 * Tira de días de la semana estilo Kivy/Fitia: círculo de selección sobre la LETRA (blanco,
 * letra negra), número debajo (naranja si es hoy) y un punto de estado (verde/amarillo/gris)
 * al pie. Sin flechas: para cambiar de semana se DESLIZA lateralmente (ver DiarioContent).
 * `weekMonday` fija qué semana se muestra (permite deslizar aunque `selected` sea de otra).
 */
@Composable
fun WeekStrip(
    selected: LocalDate,
    today: LocalDate,
    marks: Map<LocalDate, DayMark>,
    onPick: (LocalDate) -> Unit,
    weekMonday: LocalDate = selected.minusDays((selected.dayOfWeek.value - 1).toLong()),
    collapse: Float = 0f,   // 0 = fila de número visible; 1 = oculta (al hacer scroll, como Fitia)
) {
    val letters = listOf("L", "M", "M", "J", "V", "S", "D")   // como Kivy (martes/miércoles = M)
    val monday = weekMonday
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0..6) {
            val d = monday.plusDays(i.toLong())
            val isSel = d == selected
            val isToday = d == today
            val mark = marks[d] ?: DayMark.NONE
            Column(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onPick(d) }
                    .padding(vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(30.dp)
                        // el día activo sobresale como los botones; el resto, plano
                        .then(if (isSel) Modifier.raisedYellow(CircleShape, elevation = 4.dp)
                              else Modifier.clip(CircleShape)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        letters[i],
                        color = if (isSel) Pal.Bg else Pal.Sub,
                        fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    )
                }
                // Fila del número: se encoge (altura) y se desvanece (alfa) con el scroll; al
                // colapsar deja la tira solo con letra + punto, como Fitia.
                Box(
                    Modifier
                        .height(androidx.compose.ui.unit.lerp(22.dp, 0.dp, collapse))
                        .alpha(1f - collapse)
                        .clipToBounds(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${d.dayOfMonth}",
                            color = if (isToday) Pal.Orange else Pal.Text,
                            fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        )
                    }
                }
                // Hueco constante letra/número → punto (así, colapsado, el punto no toca el círculo).
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier.size(9.dp).clip(CircleShape).background(
                        when (mark) {
                            DayMark.PERFECT -> Pal.Green
                            DayMark.LOGGED -> Pal.Yellow
                            DayMark.NONE -> Color(0xFF3A3A3D)
                        }
                    )
                )
            }
        }
    }
}
