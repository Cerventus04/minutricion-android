package org.ivansola.minutricion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import org.ivansola.minutricion.ui.components.AppCard
import org.ivansola.minutricion.ui.components.CtaButton
import org.ivansola.minutricion.ui.screens.AddButton
import org.ivansola.minutricion.ui.screens.EditButton
import org.ivansola.minutricion.ui.screens.Pill
import org.ivansola.minutricion.ui.screens.VerMas
import org.ivansola.minutricion.ui.theme.MiNutricionTheme
import org.ivansola.minutricion.ui.theme.Pal
import org.junit.Rule
import org.junit.Test

/**
 * Catálogo de los botones con relieve, para revisar el aspecto de un vistazo sin instalar el APK.
 *   ./gradlew :app:recordPaparazziDebug
 *
 * Se renderizan los componentes REALES de la app (por eso son `internal` y no `private`), no copias
 * hechas para la foto: si alguno se queda sin el relieve, aquí se ve.
 */
class ButtonsTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT),
        theme = "android:Theme.Material.NoActionBar",
    )

    @Test
    fun botones() {
        paparazzi.snapshot {
            MiNutricionTheme {
                Column(
                    Modifier.fillMaxSize().background(Pal.Bg).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Label("CtaButton — Crear Alimento / Añadir / Usar texto")
                    CtaButton("Crear Alimento", {})
                    Label("CtaButton deshabilitado (faltan campos)")
                    CtaButton("Crear Alimento", {}, enabled = false)
                    Label("Pill amarilla — Escáner / IA")
                    Pill("Introducir código manualmente") {}
                    Label("Dentro de una tarjeta:")
                    AppCard(padding = 14.dp) {
                        Label("AddButton (+) de cada comida")
                        AddButton {}
                        Label("EditButton")
                        EditButton {}
                        Label("VER MÁS")
                        VerMas {}
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Label(t: String) {
        Text(t, color = Pal.Sub, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
    }

    /** Mide el alto de las dos páginas del resumen para poder igualarlas. */
    @Test
    fun paneles() {
        val targets = org.ivansola.minutricion.data.Targets(1670, 125, 209, 37)
        val state = org.ivansola.minutricion.ui.screens.DiarioState(
            selected = java.time.LocalDate.of(2026, 8, 22),
            today = java.time.LocalDate.of(2026, 8, 22),
            meals = listOf("Desayuno", "Comida"),
            entries = emptyList(),
            targets = targets,
            summary = org.ivansola.minutricion.data.Macros(),
        )
        val actions = org.ivansola.minutricion.ui.screens.DiarioActions()
        paparazzi.snapshot {
            MiNutricionTheme {
                Column(Modifier.fillMaxSize().background(Pal.Bg).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    org.ivansola.minutricion.ui.screens.SummaryCard(state, actions)
                    org.ivansola.minutricion.ui.screens.OtherNutrientsCard(state, actions)
                }
            }
        }
    }
}
