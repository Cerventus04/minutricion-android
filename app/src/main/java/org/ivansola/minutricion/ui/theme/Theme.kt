package org.ivansola.minutricion.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkScheme = darkColorScheme(
    primary = Pal.Yellow,
    background = Pal.Bg,
    surface = Pal.Card,
    onPrimary = Pal.Bg,
    onBackground = Pal.Text,
    onSurface = Pal.Text,
)

@Composable
fun MiNutricionTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val ctx = view.context
    if (!view.isInEditMode && ctx is Activity) {
        SideEffect {
            WindowCompat.getInsetsController(ctx.window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(
        colorScheme = DarkScheme,   // la app es siempre oscura
        typography = AppTypography,
        content = content,
    )
}
