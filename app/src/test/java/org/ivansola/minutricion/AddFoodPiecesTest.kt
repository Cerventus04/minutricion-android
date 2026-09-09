package org.ivansola.minutricion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import org.ivansola.minutricion.ui.components.FoodIconDisc
import org.ivansola.minutricion.ui.components.MealBadge
import org.ivansola.minutricion.ui.components.TrackBottom
import org.ivansola.minutricion.ui.components.TrackTop
import org.ivansola.minutricion.ui.components.inset
import org.ivansola.minutricion.ui.screens.SegPill
import org.ivansola.minutricion.ui.theme.MiNutricionTheme
import org.ivansola.minutricion.ui.theme.Pal
import org.junit.Rule
import org.junit.Test

/**
 * Piezas de "Añadir alimento". Esa pantalla lee de la BD, así que Paparazzi no puede dibujarla
 * entera (Db.init necesita un contexto real): se renderizan aquí sus componentes reales.
 */
class AddFoodPiecesTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT),
        theme = "android:Theme.Material.NoActionBar",
    )

    private val meals = listOf("Desayuno", "Comida", "Merienda", "Cena")

    @Test
    fun piezas() {
        paparazzi.snapshot {
            MiNutricionTheme {
                Column(
                    Modifier.fillMaxSize().background(Pal.Bg).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Label("Carril hundido del selector (aplicado)")
                    Row(
                        Modifier.inset(RoundedCornerShape(23.dp), TrackTop, TrackBottom).padding(5.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        SegPill(Icons.Rounded.Search, "Buscar", true) {}
                        SegPill(Icons.Rounded.QrCodeScanner, "Escanear", false) {}
                    }

                    Label("Filas de alimento: el emoji va en disco")
                    FoodRowDemo("Ice Cream Cacao", "Dulcesol", "48 g", "91 kcal")
                    FoodRowDemo("Pizza de atún y bacon", "Hacendado", "415 g", "1091 kcal")

                    Label("Distintivo de comida: barra del color de la comida")
                    meals.forEach { m ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MealBadge(m, size = 28.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("$m (vie 24 jul)", color = Pal.Text,
                                fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun FoodRowDemo(name: String, sub: String, qty: String, kcal: String) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FoodIconDisc(name, size = 32.dp, emojiSize = 20.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = Pal.Text, fontSize = 13.sp)
                Text(sub, color = Pal.Sub, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(qty, color = Pal.Text, fontSize = 12.sp)
                Text(kcal, color = Pal.Sub, fontSize = 11.sp)
            }
        }
    }

    @Composable
    private fun Label(t: String) {
        Text(t, color = Pal.Sub, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
    }

    /** Comprueba que el tinte del icono sale del emoji y no del gris de respaldo. */
    @Test
    fun tintes() {
        val foods = listOf("Fresas", "Plátano", "Leche semidesnatada", "Pizza de atún",
            "Aguacate", "Chocolate negro", "Salmón", "Arroz blanco")
        paparazzi.snapshot {
            MiNutricionTheme {
                Column(Modifier.fillMaxSize().background(Pal.Bg).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    foods.forEach { f ->
                        val tint = org.ivansola.minutricion.ui.components.rememberFoodColor(f)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(56.dp).clip(RoundedCornerShape(16.dp))
                                    .background(tint.copy(alpha = 0.34f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                org.ivansola.minutricion.ui.components.FoodEmoji(f, size = 32.dp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(f, color = Pal.Text, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
