package org.ivansola.minutricion.ui.theme

import androidx.compose.ui.graphics.Color

/** Paleta idéntica a la app de escritorio / Kivy (ver theme.py). */
object Pal {
    val Bg = Color(0xFF050506)
    val Card = Color(0xFF0F0F11)
    val Card2 = Color(0xFF19191C)
    val Border = Color(0xFF3A3A42)
    val Track = Color(0xFF3A3A42)

    val Yellow = Color(0xFFFFC61A)
    val YellowH = Color(0xFFE0AA10)
    val Green = Color(0xFF30D158)
    val Red = Color(0xFFE5484D)

    val Text = Color(0xFFF5F5F7)
    val Sub = Color(0xFF8E8E93)
    val Orange = Color(0xFFF0873E)
    val Avg = Color(0xFF4AA3DF)

    // Macros
    val Protein = Color(0xFFE8863C)
    val Carbs = Color(0xFFF0AE4E)
    val Fat = Color(0xFFF3D08A)
}

/** Colores por micronutriente (mismos hex que _MICRO_COLOR en main.py). */
val MicroColor: Map<String, Color> = mapOf(
    "vit_a" to Color(0xFF4AA3DF), "vit_c" to Color(0xFFF0873E), "vit_d" to Color(0xFFF0C64E),
    "vit_e" to Color(0xFF8BC98B), "vit_k" to Color(0xFF4CAF50), "b1" to Color(0xFFC77DD8),
    "b2" to Color(0xFF5AC8C8), "b6" to Color(0xFFE8A24C), "b12" to Color(0xFF9C8CE0),
    "folate" to Color(0xFF6FBF73), "calcium" to Color(0xFFF0C64E), "iron" to Color(0xFFD9704E),
    "magnesium" to Color(0xFF6FBF73), "phosphorus" to Color(0xFFE8D07A), "potassium" to Color(0xFFE8A24C),
    "zinc" to Color(0xFF4AA3DF), "selenium" to Color(0xFF8BC98B), "copper" to Color(0xFFE84E9C),
    "manganese" to Color(0xFFB77DE0),
    "b3" to Color(0xFFE8A24C), "b5" to Color(0xFFD8A24C), "biotin" to Color(0xFF9C8CE0),
    "iodine" to Color(0xFF5A9CE0), "chromium" to Color(0xFF8BC98B), "molybdenum" to Color(0xFFB77DE0),
    "fluoride" to Color(0xFF4AA3DF), "chloride" to Color(0xFF6FBF73),
)
