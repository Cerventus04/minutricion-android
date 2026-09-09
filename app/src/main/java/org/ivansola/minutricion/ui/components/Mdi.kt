package org.ivansola.minutricion.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.ivansola.minutricion.R
import org.ivansola.minutricion.data.Nutriest
import org.ivansola.minutricion.ui.theme.Pal

/** Fuente Material Design Icons (misma que usaba Kivy / KivyMD). */
val MdiFont = FontFamily(Font(R.font.mdi))

/** Codepoints MDI (Plano 15 PUA, >0xFFFF -> requieren pares subrogados) de los iconos usados. */
private val CP: Map<String, Int> = mapOf(
    "dots-horizontal" to 0xf01d8, "dots-vertical" to 0xf01d9,
    "chevron-up" to 0xf0143, "chevron-down" to 0xf0140,
    "chevron-left" to 0xf0141, "chevron-right" to 0xf0142,
    "arrow-left" to 0xf004d, "plus" to 0xf0415, "close" to 0xf0156,
    "trash-can-outline" to 0xf0a7a, "heart" to 0xf02d1, "heart-outline" to 0xf02d5,
    "pencil" to 0xf03eb, "pencil-outline" to 0xf0cb6, "flash" to 0xf0241, "flash-off" to 0xf0243,
    "magnify" to 0xf0349, "barcode-scan" to 0xf0072, "fire" to 0xf0238,
    "database" to 0xf01bc, "brush" to 0xf00e3, "silverware-fork-knife" to 0xf0a70,
    "book-open-variant" to 0xf14f7, "chart-line" to 0xf012a, "target" to 0xf04fe,
    "scale-bathroom" to 0xf0473, "lightning-bolt" to 0xf140b, "bowl-mix" to 0xf0617,
    "calendar-blank-outline" to 0xf0b66, "calendar-today" to 0xf00f6,
    "check-bold" to 0xf0e1e, "check-decagram" to 0xf0791, "menu-down" to 0xf035d,
    "trophy-outline" to 0xf053a, "weather-night" to 0xf0594, "egg-fried" to 0xf184a,
    "food-apple" to 0xf025b, "cup" to 0xf01aa, "cup-water" to 0xf01ab,
    "content-copy" to 0xf018f, "content-paste" to 0xf0192, "infinity" to 0xf06e4,
    "eye-outline" to 0xf06d0, "chart-pie" to 0xf012b, "chart-donut" to 0xf07af,
    "cog" to 0xf0493, "cog-outline" to 0xf08bb, "tune-variant" to 0xf1542,
    "leaf" to 0xf032a, "fish" to 0xf023a, "food-drumstick" to 0xf141f, "food-steak" to 0xf146a,
    "egg-outline" to 0xf13f2, "cheese" to 0xf12b9, "bread-slice" to 0xf0cee, "seed" to 0xf0e62,
    "peanut" to 0xf0ffc, "bottle-tonic-outline" to 0xf112f, "candy" to 0xf1970,
    "nutrition" to 0xf03c2, "water" to 0xf058c, "flag" to 0xf023b, "help-circle" to 0xf02d7,
    "circle" to 0xf0765, "circle-medium" to 0xf09de, "settings" to 0xf0493,
    "glass-mug-variant" to 0xf1116,
)

private fun glyph(name: String): String =
    String(Character.toChars(CP[name] ?: CP["help-circle"]!!))

/** Icono MDI por nombre (idéntico a los que usaba Kivy). */
@Composable
fun MdiIcon(name: String, size: Dp = 20.dp, color: Color = Pal.Text, modifier: Modifier = Modifier) {
    val sp = with(LocalDensity.current) { size.toSp() }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Text(
            text = glyph(name),
            color = color,
            fontFamily = MdiFont,
            fontSize = sp,
            lineHeight = sp,
            textAlign = TextAlign.Center,
            style = androidx.compose.ui.text.TextStyle(
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
            ),
        )
    }
}

// ---- nombres MDI por comida / alimento (mismos mapeos que Kivy) ----

fun mealMdiName(meal: String): String = when (meal.lowercase()) {
    "desayuno" -> "egg-fried"
    "comida", "almuerzo" -> "silverware-fork-knife"
    "merienda" -> "food-apple"
    "cena" -> "weather-night"
    else -> "silverware-fork-knife"
}

private val CAT_ICON = mapOf(
    "fruit" to "food-apple", "vegetable" to "leaf", "leafy" to "leaf", "fish" to "fish",
    "poultry" to "food-drumstick", "meat" to "food-steak", "egg" to "egg-outline",
    "dairy" to "cup", "cheese" to "cheese", "grain" to "bread-slice", "legume" to "seed",
    "nut" to "peanut", "oil" to "bottle-tonic-outline", "beverage" to "cup",
    "sweet" to "candy", "default" to "silverware-fork-knife",
)

/** Nombre MDI orientativo según el tipo de alimento (por categoría del nombre). */
fun foodMdiName(name: String, drink: Boolean = isDrink(name)): String {
    if (drink) return "cup"
    return CAT_ICON[Nutriest.classify(name)] ?: "silverware-fork-knife"
}

/** Icono MDI de un alimento (usa su categoría / bebida). */
@Composable
fun FoodIcon(name: String, size: Dp = 20.dp, color: Color = Pal.Sub, modifier: Modifier = Modifier) {
    MdiIcon(foodMdiName(name), size, color, modifier)
}
