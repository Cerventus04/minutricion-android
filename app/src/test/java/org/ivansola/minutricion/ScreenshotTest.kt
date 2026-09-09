package org.ivansola.minutricion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import org.ivansola.minutricion.data.Entry
import org.ivansola.minutricion.data.Logic
import org.ivansola.minutricion.data.Macros
import org.ivansola.minutricion.data.Targets
import org.ivansola.minutricion.ui.components.DayMark
import org.ivansola.minutricion.ui.screens.DiarioContent
import org.ivansola.minutricion.ui.screens.DiarioState
import org.ivansola.minutricion.ui.screens.ProgresoContent
import org.ivansola.minutricion.ui.screens.ProgresoState
import org.ivansola.minutricion.ui.theme.MiNutricionTheme
import org.ivansola.minutricion.ui.theme.Pal
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/**
 * Renderiza pantallas a PNG en la JVM (sin emulador) para revisar la estética.
 *   ./gradlew :app:recordPaparazziDebug   -> genera los PNG en app/src/test/snapshots/images/
 *
 * Nota: solo se pueden previsualizar así las pantallas partidas en Screen (estado) + Content
 * (vista pura, sin BD) — p. ej. Diario/Progreso. FoodDetailScreen/ScoreDetailScreen/AlimentosScreen
 * llaman a Db.* directamente y Db.init() necesita `context.getDatabasePath(...)`, que el contexto
 * de Paparazzi no soporta (siempre null) -> hay que probarlas con el APK en el móvil.
 */
class ScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT),
        theme = "android:Theme.Material.NoActionBar",
    )

    private fun e(meal: String, name: String, grams: Double, kcal: Double, p: Double, c: Double, f: Double) =
        Entry(0, "2026-07-21", meal, name, grams, kcal, p, c, f, true)

    @Test
    fun diario() {
        val today = LocalDate.of(2026, 7, 21)
        val meals = listOf("Desayuno", "Comida", "Merienda", "Cena")
        val entries = listOf(
            e("Desayuno", "Avena", 60.0, 389.0, 16.9, 66.3, 6.9),
            e("Desayuno", "Leche semidesnatada", 200.0, 46.0, 3.3, 4.8, 1.6),
            e("Comida", "Arroz blanco cocido", 200.0, 130.0, 2.7, 28.0, 0.3),
            e("Comida", "Pechuga de pollo a la plancha", 150.0, 165.0, 31.0, 0.0, 3.6),
        )
        val targets = Targets(2200, 165, 275, 49)
        val summary = Macros(kcal = 700.0, protein = 60.0, carbs = 190.0, fat = 20.0)
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val marks = mapOf(
            monday to DayMark.PERFECT,
            monday.plusDays(1) to DayMark.PERFECT,
            monday.plusDays(2) to DayMark.LOGGED,
        )

        paparazzi.snapshot {
            MiNutricionTheme {
                Box(Modifier.fillMaxSize().background(Pal.Bg)) {
                    DiarioContent(
                        contentPadding = PaddingValues(0.dp),
                        state = DiarioState(
                            today, today, meals, entries, targets, summary,
                            streak = 5, streakHot = true, weekMarks = marks,
                        ),
                    )
                }
            }
        }
    }

    /** Progreso recién abierto el día: sin nada apuntado, el medidor debe salir igual pero a 0. */
    @Test
    fun progreso_sin_datos() {
        val targets = Targets(2200, 165, 275, 49)
        val series = listOf(82.0, 81.4, 81.0, 80.3, 79.8, 79.1, 78.5)
        val state = ProgresoState(
            score = 0, scoreColorHex = "#E5484D", scoreLabel = "Muy mejorable",
            goal = "Perder grasa (-500 kcal)",
            curWeight = 78.5, startWeight = 82.0, targetWeight = 75.0, weeks = 7,
            weeklyAvgKcal = 2050,
            weekKcal = listOf(2100, 1950, 2200, 0, 0, 0, 0),
            todayIndex = 4, kcalLo = 1980, kcalHi = 2420, maxKcal = 2420,
            weightSeries = series, weightEma = Logic.ema(series),
            weightDates = List(series.size) { "2026-07-0${it + 1}" },
            macroAvg = Macros(0.0, 150.0, 230.0, 60.0), targets = targets,
            scoreHasData = false,
        )
        paparazzi.snapshot {
            MiNutricionTheme {
                Box(Modifier.fillMaxSize().background(Pal.Bg)) {
                    ProgresoContent(contentPadding = PaddingValues(0.dp), state = state)
                }
            }
        }
    }

    @Test
    fun progreso() {
        val targets = Targets(2200, 165, 275, 49)
        val series = listOf(82.0, 81.4, 81.0, 80.3, 79.8, 79.1, 78.5)
        val state = ProgresoState(
            score = 72, scoreColorHex = "#FFC61A", scoreLabel = "Medio",
            goal = "Perder grasa (-500 kcal)",
            curWeight = 78.5, startWeight = 82.0, targetWeight = 75.0, weeks = 7,
            weeklyAvgKcal = 2050,
            weekKcal = listOf(2100, 1950, 2200, 0, 2050, 1800, 0),
            todayIndex = 4, kcalLo = 1980, kcalHi = 2420, maxKcal = 2420,
            weightSeries = series, weightEma = Logic.ema(series),
            weightDates = List(series.size) { "2026-07-0${it + 1}" },
            macroAvg = Macros(0.0, 150.0, 230.0, 60.0), targets = targets,
        )
        paparazzi.snapshot {
            MiNutricionTheme {
                Box(Modifier.fillMaxSize().background(Pal.Bg)) {
                    ProgresoContent(contentPadding = PaddingValues(0.dp), state = state)
                }
            }
        }
    }
}
