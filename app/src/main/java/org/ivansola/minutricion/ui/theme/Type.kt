package org.ivansola.minutricion.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.ivansola.minutricion.R

/** Nunito (redondeada), igual que en la app original. */
val Nunito = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_bold, FontWeight.SemiBold),
)

private val base = TextStyle(fontFamily = Nunito)

val AppTypography = Typography(
    displayLarge = base, displayMedium = base, displaySmall = base,
    headlineLarge = base, headlineMedium = base, headlineSmall = base,
    titleLarge = base, titleMedium = base, titleSmall = base,
    bodyLarge = base, bodyMedium = base, bodySmall = base,
    labelLarge = base, labelMedium = base, labelSmall = base,
)
