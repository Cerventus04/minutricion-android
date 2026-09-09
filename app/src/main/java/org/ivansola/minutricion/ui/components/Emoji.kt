package org.ivansola.minutricion.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.EmojiSupportMatch
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ivansola.minutricion.R
import org.ivansola.minutricion.data.Nutriest
import org.ivansola.minutricion.ui.theme.Pal

/** Fuente de emojis a COLOR (Noto Color Emoji subseteada) para renderizar todos con un mismo estilo. */
private val EmojiFont = FontFamily(Font(R.font.noto_emoji))

// ---- carga de PNG desde assets (con caché) --------------------------------

private val assetCache = HashMap<String, ImageBitmap?>()

private fun loadAsset(ctx: Context, path: String): ImageBitmap? =
    assetCache.getOrPut(path) {
        try {
            ctx.assets.open(path).use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
        } catch (e: Exception) {
            null
        }
    }

/**
 * Icono de emoji A COLOR renderizado con la fuente Noto Color Emoji (una sola fuente para TODOS,
 * consistente y ligera). `emojiSupportMatch = None` evita que Android sustituya por la fuente del
 * sistema, así se usa siempre la nuestra.
 */
@Composable
fun EmojiIcon(emoji: String, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    // fontSize algo menor que la caja y SIN forzar lineHeight: así el alto natural de la línea deja
    // holgura y el glifo (p. ej. 💊) no se recorta por arriba/abajo.
    val sp = with(LocalDensity.current) { (size * 0.82f).toSp() }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Text(
            emoji,
            fontFamily = EmojiFont,
            color = Color.Unspecified,
            fontSize = sp,
            textAlign = TextAlign.Center,
            style = TextStyle(platformStyle = PlatformTextStyle(emojiSupportMatch = EmojiSupportMatch.None)),
            // El glifo queda ALTO dentro de su caja: la línea de texto reserva sitio para el
            // descendente (el rabito de la "p") y el emoji no lo usa. Medido sobre el render, el
            // desvío es ~4 % del alto, y se corrige bajando el glifo esa misma fracción. Un
            // lineHeight/lineHeightStyle NO lo arregla (probado: la desviación no se movía).
            modifier = Modifier.offset(y = size * 0.045f),
        )
    }
}

/**
 * Icono desde un PNG propio de assets/icons/ (los que usaba Kivy).
 *  - `tint`: recolorea TODO el glifo con ese color (usa su alfa).
 *  - `recolor`: recolorea SOLO el cuerpo (píxeles claros) y CONSERVA los oscuros (rayas negras),
 *    igual que el modo `recolor` de Kivy (p. ej. la papelera roja con sus 2 rectas negras).
 */
@Composable
fun AssetIcon(name: String, size: Dp = 20.dp, tint: Color? = Pal.Text, recolor: Color? = null,
              modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val bmp = remember(name, recolor) {
        if (recolor != null) recoloredAsset(ctx, "icons/$name.png", recolor.toArgb())
        else loadAsset(ctx, "icons/$name.png")
    }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        if (bmp != null) {
            Image(
                bmp, null, modifier = Modifier.size(size * 0.82f),
                colorFilter = if (recolor == null) tint?.let { ColorFilter.tint(it) } else null,
            )
        }
    }
}

/** Recolorea el CUERPO (píxeles claros) al color dado conservando los oscuros (rayas/contorno). */
private fun recoloredAsset(ctx: Context, path: String, argb: Int): ImageBitmap? =
    assetCache.getOrPut("$path#$argb") {
        try {
            val src = ctx.assets.open(path).use { BitmapFactory.decodeStream(it) } ?: return@getOrPut null
            val bmp = src.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
            val w = bmp.width; val h = bmp.height
            val px = IntArray(w * h)
            bmp.getPixels(px, 0, w, 0, 0, w, h)
            val nr = (argb shr 16) and 0xFF
            val ng = (argb shr 8) and 0xFF
            val nb = argb and 0xFF
            for (i in px.indices) {
                val p = px[i]
                val a = (p ushr 24) and 0xFF
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                if (a > 10 && maxOf(r, g, b) >= 100) {
                    px[i] = (a shl 24) or (nr shl 16) or (ng shl 8) or nb
                }
            }
            bmp.setPixels(px, 0, w, 0, 0, w, h)
            bmp.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

// ---- mapas de emoji por alimento/comida (mismos que Kivy) -----------------

private val MEAL_EMOJI = linkedMapOf(
    "desayuno" to "🍳", "almuerzo" to "🥪", "comida" to "🍽️", "merienda" to "🍎",
    "cena" to "🌙", "snack" to "🍿", "postre" to "🍰",
)

// El orden IMPORTA: match() devuelve el PRIMER clave que sea SUBCADENA del nombre, así que los
// términos específicos/compuestos van ANTES que los genéricos (p. ej. "tomate frito" antes que
// "tomate", "panceta" antes que "pan", "salchicha"/"salmón" antes que "sal").
private val FOOD_EMOJI = linkedMapOf(
    // --- platos compuestos / específicos primero ---
    "tomate frito" to "🍅", "ensaladilla" to "🥗", "ensalada" to "🥗", "patatas fritas" to "🍟",
    "salchicha" to "🌭", "perrito" to "🌭", "hamburguesa" to "🍔",
    "bocadillo" to "🥪", "bocata" to "🥪", "sándwich" to "🥪", "sandwich" to "🥪", "montadito" to "🥪",
    "kebab" to "🥙", "durum" to "🥙", "wrap" to "🌯", "burrito" to "🌯", "taco" to "🌮", "nacho" to "🌮",
    "empanadilla" to "🥟", "empanada" to "🥟", "gyoza" to "🥟", "dumpling" to "🥟",
    "sushi" to "🍣", "sashimi" to "🍣", "maki" to "🍣", "onigiri" to "🍙", "ramen" to "🍜",
    "fideos" to "🍜", "fideo" to "🍝", "curry" to "🍛", "paella" to "🥘", "guiso" to "🥘", "estofado" to "🥘",
    "gazpacho" to "🥣", "salmorejo" to "🥣", "crema de" to "🥣", "puré" to "🥣", "sopa" to "🥣", "caldo" to "🥣",
    "quiche" to "🥧", "tortilla" to "🍳", "revuelto" to "🍳", "huevo" to "🥚",

    // --- carnes / aves / embutidos ---
    "pechuga" to "🍗", "pollo" to "🍗", "pavo" to "🍗", "nugget" to "🍗",
    "solomillo" to "🥩", "entrecot" to "🥩", "filete" to "🥩", "lomo" to "🥩", "chuleta" to "🍖",
    "costilla" to "🍖", "cerdo" to "🥩", "ternera" to "🥩", "buey" to "🥩", "cordero" to "🍖",
    "conejo" to "🍖", "albóndiga" to "🍖", "bacon" to "🥓", "panceta" to "🥓", "tocino" to "🥓",
    "jamón" to "🍖", "chorizo" to "🍖", "salchichón" to "🍖", "fuet" to "🍖", "embutido" to "🍖",
    "morcilla" to "🍖", "carne" to "🥩",

    // --- pescados / mariscos ---
    "salmón" to "🐟", "atún" to "🐟", "merluza" to "🐟", "bacalao" to "🐟", "lubina" to "🐟",
    "dorada" to "🐟", "sardina" to "🐟", "boquerón" to "🐟", "caballa" to "🐟", "trucha" to "🐟",
    "pescado" to "🐟", "gamba" to "🦐", "langostino" to "🦐", "camarón" to "🦐", "cigala" to "🦐",
    "cangrejo" to "🦀", "mejillón" to "🦪", "almeja" to "🦪", "ostra" to "🦪", "calamar" to "🦑",
    "sepia" to "🦑", "pulpo" to "🐙", "marisco" to "🦐",

    // --- lácteos ---
    "leche" to "🥛", "yogur" to "🥛", "nata" to "🥛", "cuajada" to "🥛", "kéfir" to "🥛",
    "mozzarella" to "🧀", "parmesano" to "🧀", "cheddar" to "🧀", "requesón" to "🧀", "ricotta" to "🧀",
    "feta" to "🧀", "queso" to "🧀", "mantequilla" to "🧈", "margarina" to "🧈", "flan" to "🍮",
    "natillas" to "🍮", "helado" to "🍦",

    // --- panadería / cereales ---
    "cruasán" to "🥐", "croissant" to "🥐", "baguette" to "🥖", "barra de pan" to "🥖", "bagel" to "🥯",
    "tostada" to "🍞", "biscote" to "🍞", "pan" to "🍞", "arroz" to "🍚", "quinoa" to "🍚",
    "cuscús" to "🍚", "couscous" to "🍚", "bulgur" to "🍚", "mijo" to "🍚",
    "espagueti" to "🍝", "tallarín" to "🍝", "macarr" to "🍝", "raviol" to "🍝", "tortellini" to "🍝",
    "lasaña" to "🍝", "canelón" to "🍝", "ñoqui" to "🍝", "gnocchi" to "🍝", "pasta" to "🍝",
    "avena" to "🥣", "muesli" to "🥣", "cereal" to "🥣", "harina" to "🌾", "trigo" to "🌾", "salvado" to "🌾",
    "gofre" to "🧇", "waffle" to "🧇", "tortita" to "🥞", "crep" to "🥞", "pancake" to "🥞",

    // --- frutas ---
    "manzana" to "🍎", "plátano" to "🍌", "banana" to "🍌", "mandarina" to "🍊", "naranja" to "🍊",
    "fresa" to "🍓", "frambuesa" to "🫐", "arándano" to "🫐", "mora" to "🫐", "uva" to "🍇",
    "melocotón" to "🍑", "durazno" to "🍑", "ciruela" to "🍑", "cereza" to "🍒", "kiwi" to "🥝",
    "pera" to "🍐", "sandía" to "🍉", "melón" to "🍈", "piña" to "🍍", "mango" to "🥭", "coco" to "🥥",
    "limón" to "🍋", "lima" to "🍋", "higo" to "🍑", "granada" to "🍎", "fruta" to "🍎",

    // --- verduras / hortalizas ---
    "aguacate" to "🥑", "brócoli" to "🥦", "coliflor" to "🥦", "lechuga" to "🥬", "espinaca" to "🥬",
    "acelga" to "🥬", "rúcula" to "🥬", "repollo" to "🥬", "zanahoria" to "🥕",
    "pimiento" to "🫑", "pepinillo" to "🥒", "pepino" to "🥒", "calabacín" to "🥒", "berenjena" to "🍆",
    "cebolla" to "🧅", "ajo" to "🧄", "calabaza" to "🎃", "maíz" to "🌽", "choclo" to "🌽",
    "champiñón" to "🍄", "seta" to "🍄", "tomate" to "🍅", "patata" to "🥔", "boniato" to "🥔",
    "espárrago" to "🥬", "verdura" to "🥗", "hortaliza" to "🥗",

    // --- legumbres / frutos secos ---
    "lenteja" to "🫘", "garbanzo" to "🫘", "alubia" to "🫘", "judía" to "🫘", "frijol" to "🫘",
    "soja" to "🫘", "edamame" to "🫘", "haba" to "🫘", "guisante" to "🫛",
    "almendra" to "🥜", "nuez" to "🥜", "cacahuete" to "🥜", "anacardo" to "🥜", "pistacho" to "🥜",
    "avellana" to "🥜", "pipas" to "🥜", "semilla" to "🥜", "chía" to "🥜", "tofu" to "🧊",

    // --- dulces / snacks / grasas / condimentos ---
    "chocolate" to "🍫", "cacao" to "🍫",
    // "col" va DESPUÉS de "chocolate": si no, cho-COL-ate salía como verdura.
    "col" to "🥬", "bombón" to "🍫", "turrón" to "🍫", "brownie" to "🍫",
    "galleta" to "🍪", "donut" to "🍩", "dónut" to "🍩", "churro" to "🍩", "magdalena" to "🧁",
    "muffin" to "🧁", "cupcake" to "🧁", "tarta" to "🍰", "bizcocho" to "🍰", "pastel" to "🍰",
    "cheesecake" to "🍰", "tiramisú" to "🍰", "caramelo" to "🍬", "gominola" to "🍬", "chuche" to "🍬",
    "miel" to "🍯", "sirope" to "🍯", "mermelada" to "🍓", "azúcar" to "🧁", "chips" to "🍟",
    "palomitas" to "🍿", "aceituna" to "🫒", "aceite" to "🫒", "sal" to "🧂", "pimienta" to "🧂",
    "especia" to "🧂", "ketchup" to "🍅", "café" to "☕", "cerveza" to "🍺", "vino" to "🍷",
)

private val DRINK_EMOJI = linkedMapOf(
    "café" to "☕", "latte" to "☕", "cortado" to "☕", "capuchino" to "☕", "cappuccino" to "☕",
    "té" to "🍵", "infusión" to "🍵", "cerveza" to "🍺", "vino" to "🍷", "agua" to "💧",
    "zumo" to "🧃", "batido" to "🥤", "leche" to "🥛", "yogur" to "🥛",
    "monster" to "🥤", "red bull" to "🥤", "redbull" to "🥤", "energét" to "🥤", "energet" to "🥤",
    "fanta" to "🥤", "coca" to "🥤", "cola" to "🥤", "pepsi" to "🥤", "sprite" to "🥤",
    "aquarius" to "🥤", "nestea" to "🥤", "refresco" to "🥤", "gaseosa" to "🥤", "tónica" to "🥤",
)

private fun match(name: String, table: Map<String, String>): String? {
    val n = name.lowercase()
    for ((k, v) in table) if (k in n) return v
    return null
}

/** Emoji del alimento (mismo mapeo que Kivy food_emoji). Si hay categoría elegida, manda ella. */
fun foodEmoji(name: String, drink: Boolean = isDrink(name), category: String? = null): String {
    org.ivansola.minutricion.data.FoodCategories.emojiOf(category)?.let { return it }
    val n = name.lowercase()
    if ("tortita" in n && ("arroz" in n || "maíz" in n || "maiz" in n)) return "🍘"
    if (drink) return match(name, DRINK_EMOJI) ?: "🥤"
    return match(name, FOOD_EMOJI) ?: "🍽️"
}

fun mealEmoji(meal: String): String = match(meal, MEAL_EMOJI) ?: "🍽️"

/** Icono de un alimento como emoji a color (Twemoji), como en Kivy.
 *  Para la categoría "Suplementos" se usa una ilustración propia (bote de vitaminas). */
@Composable
fun FoodEmoji(name: String, size: Dp = 22.dp, modifier: Modifier = Modifier,
              drink: Boolean = isDrink(name), category: String? = null) {
    if (category == "Suplementos") {
        Box(modifier.size(size), contentAlignment = Alignment.Center) {
            Image(
                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_supplement),
                contentDescription = null,
                modifier = Modifier.size(size),
            )
        }
        return
    }
    EmojiIcon(foodEmoji(name, drink, category), size, modifier)
}

/** Separa "Nombre (Marca)" en (nombre, marca); si no hay marca devuelve (nombre, ""). */
fun splitName(name: String): Pair<String, String> {
    if (name.endsWith(")") && " (" in name) {
        val idx = name.lastIndexOf(" (")
        return name.substring(0, idx).trim() to name.substring(idx + 2, name.length - 1).trim()
    }
    return name to ""
}

// ---- color dominante de un emoji ------------------------------------------

private val emojiColorCache = HashMap<String, Color>()

/**
 * Color predominante del emoji, para poder teñir el fondo que va DETRÁS del icono. Se obtiene
 * dibujando el glifo en un bitmap pequeño y quedándose con el tono más repetido, ponderando por
 * saturación: si no, en un emoji con mucho blanco o gris (un vaso de leche) ganaría el gris y el
 * fondo no diría nada del alimento. Se cachea porque cada consulta pinta un bitmap.
 */
fun emojiDominantColor(ctx: Context, emoji: String): Color = emojiColorCache.getOrPut(emoji) {
    try {
        val n = 24
        val bmp = android.graphics.Bitmap.createBitmap(n, n, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = n * 0.8f
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = androidx.core.content.res.ResourcesCompat.getFont(ctx, R.font.noto_emoji)
        }
        canvas.drawText(emoji, n / 2f, n * 0.82f, paint)
        val buckets = HashMap<Int, FloatArray>()   // tono cuantizado -> [peso, r, g, b]
        val hsv = FloatArray(3)
        for (y in 0 until n) for (x in 0 until n) {
            val p = bmp.getPixel(x, y)
            if (android.graphics.Color.alpha(p) < 128) continue
            val r = android.graphics.Color.red(p)
            val g = android.graphics.Color.green(p)
            val b = android.graphics.Color.blue(p)
            android.graphics.Color.RGBToHSV(r, g, b, hsv)
            // se descarta lo casi blanco/negro/gris: no sirve para teñir nada
            if (hsv[1] < 0.25f || hsv[2] < 0.2f) continue
            val key = (hsv[0] / 30f).toInt()       // 12 sectores de tono
            val acc = buckets.getOrPut(key) { FloatArray(4) }
            val w = hsv[1] * hsv[2]
            acc[0] += w; acc[1] += r * w; acc[2] += g * w; acc[3] += b * w
        }
        bmp.recycle()
        val best = buckets.values.maxByOrNull { it[0] } ?: return@getOrPut Pal.Sub
        Color(
            (best[1] / best[0]).toInt().coerceIn(0, 255),
            (best[2] / best[0]).toInt().coerceIn(0, 255),
            (best[3] / best[0]).toInt().coerceIn(0, 255),
        )
    } catch (e: Exception) {
        Pal.Sub   // en previsualizaciones o si la fuente no carga, sin tinte
    }
}

/** Color dominante del icono que le tocaría a este alimento. */
@Composable
fun rememberFoodColor(name: String, drink: Boolean = isDrink(name), category: String? = null): Color {
    val ctx = LocalContext.current
    val emoji = foodEmoji(name, drink, category)
    return remember(emoji) { emojiDominantColor(ctx, emoji) }
}
