package org.ivansola.minutricion

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import org.ivansola.minutricion.ui.theme.MiNutricionTheme
import org.ivansola.minutricion.ui.theme.Pal
import org.junit.Rule
import org.junit.Test

/**
 * Pinta TODOS los iconos del pack tal como los carga Android (VectorDrawable), para comprobar que
 * la conversión desde SVG no ha roto ningún trazado. Los recursos se enumeran por reflexión sobre
 * R.drawable, así que un icono nuevo aparece aquí sin tocar el test.
 */
class FoodIconsTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT, screenHeight = 3300),
        theme = "android:Theme.Material.NoActionBar",
    )

    @OptIn(ExperimentalLayoutApi::class)
    @Test
    fun pack() {
        val icons = R.drawable::class.java.fields
            .filter { it.name.startsWith("food_") }
            .sortedBy { it.name }
            .map { it.name.removePrefix("food_") to it.getInt(null) }
        paparazzi.snapshot {
            MiNutricionTheme {
                FlowRow(
                    Modifier.fillMaxSize().background(Pal.Bg).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    icons.forEach { (name, res) ->
                        Column(
                            Modifier.width(62.dp).clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF16161A)).padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Image(painterResource(res), null, Modifier.size(56.dp))
                            Text(name, color = Pal.Sub, fontSize = 7.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }

    /** Los iconos añadidos en la segunda auditoría: el que tenían antes, el nuevo y productos reales. */
    @Test
    fun nuevos() {
        data class Nuevo(val nombre: String, val antes: Int, val ahora: Int, val ejemplos: List<String>)
        val nuevos = listOf(
            Nuevo("Galletas", R.drawable.food_dulces, R.drawable.food_galletas,
                listOf("Galletas Digestive Avena (Gullón)", "Galleta María dorada (Artiach)")),
            Nuevo("Cerveza", R.drawable.food_alcohol, R.drawable.food_cerveza,
                listOf("Cerveza Mahou 5 Estrellas 50 cl", "Cerveza 0,0 tostada Mahou 33 cl")),
            Nuevo("Pizza", R.drawable.food_preparados, R.drawable.food_pizza,
                listOf("Pizza Ristorante prosciutto (Dr. Oetker)", "Pizza de 4 quesos (Buitoni)")),
            Nuevo("Zumo", R.drawable.food_bebidas, R.drawable.food_zumo,
                listOf("Zumo de naranja con pulpa 1 l", "Néctar de melocotón (Granini) 1 l")),
            Nuevo("Té e infusiones", R.drawable.food_cafe, R.drawable.food_te,
                listOf("Té verde con jengibre (Pompadour)", "Infusión de manzanilla 20 bolsitas")),
            Nuevo("Aceitunas y encurtidos", R.drawable.food_snacks, R.drawable.food_aceitunas,
                listOf("Aceitunas rellenas de anchoa", "Guindillas de Ibarra (Zubelzu)")),
            Nuevo("Embutidos", R.drawable.food_fiambres, R.drawable.food_embutidos,
                listOf("Chorizo ibérico (ElPozo)", "Salchichas Frankfurt (Campofrío)")),
            Nuevo("Postres", R.drawable.food_dulces, R.drawable.food_postres,
                listOf("Natillas de vainilla (Hacendado)", "Flan de huevo (Danone)")),
        )
        paparazzi.snapshot {
            MiNutricionTheme {
                Column(Modifier.fillMaxSize().background(Pal.Bg).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    nuevos.forEach { n ->
                        androidx.compose.foundation.layout.Row(
                            Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFF16161A))
                                .padding(10.dp).fillMaxWidth().height(96.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(painterResource(n.antes), null, Modifier.size(30.dp).alpha(0.45f))
                                Text("antes", color = Pal.Sub, fontSize = 8.sp)
                            }
                            Text("→", color = Pal.Sub, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 6.dp))
                            Image(painterResource(n.ahora), null, Modifier.size(76.dp))
                            androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(n.nombre, color = Pal.Text, fontSize = 14.sp)
                                n.ejemplos.forEach { e ->
                                    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                                        org.ivansola.minutricion.ui.components.FoodIconDisc(e, size = 22.dp, emojiSize = 14.dp)
                                        androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
                                        Text(e, color = Pal.Sub, fontSize = 9.sp, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Alimentos reales de la app (sacados de capturas) con el icono que les asigna FoodIcons, para
     * revisar el MAPEO, no solo el dibujo. Incluye a propósito los casos trampa: "sin azúcar",
     * "queso en lonchas", "salsa de soja", "chocolate" (contiene "cola") o un helado de cacao.
     */
    @Test
    fun asignacion() {
        val foods = listOf(
            // los de la captura
            "Tortitas Milho Campestre (Hacendado)", "Bebida de canela y limón (Hacendado)",
            "Lonchas de queso (Milbona)", "Gnocchi (Hacendado)", "ñordos (Hacendado)",
            // gana lo que ES, no lo que lleva
            "Natilla con sorpresa de chocolate", "Bizcocho con pepitas de chocolate",
            "Galleta Cookie&Cream Nocilla", "Salchichón de pavo (ElPozo)", "Pan de molde sésamo y lino",
            "Paté de atún", "Sorbete de limón tarrina 1 litro", "Yogur griego con miel",
            "Aceitunas rellenas de anchoa", "Croquetas de jamón ibérico",
            "Macarrones con atún en salsa de tomate", "Calamares en salsa americana (Calvo)",
            "Queso rallado especial pasta", "Hamburguesa con queso", "Chocolate negro para postres",
            // nutrición deportiva e infantil
            "Barrita proteica chocolate (Prozis)", "Impact Whey Isolate Helado de Vainilla",
            "Gominolas de Ashwagandha", "Leche infantil de continuación desde 6 meses",
            // palabras que faltaban
            "Espelta Grano Bio (Drasanvi)", "Achicoria soluble", "Vinagre balsámico de Módena",
            "Fideos orientales Yatekomo", "Masa de hojaldre", "Porridge de arroz proteico",
            "Bífidus con avena", "Sobao pasiego", "Barra de pan", "Hummus clásico",
            "Bebida energética Ultra Zero (Monster)", "Café Latte Light (Hacendado)", "Pechuga de pavo",
        )
        paparazzi.snapshot {
            MiNutricionTheme {
                Column(Modifier.fillMaxSize().background(Pal.Bg).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    foods.forEach { f ->
                        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                            org.ivansola.minutricion.ui.components.FoodIconDisc(f, size = 26.dp, emojiSize = 16.dp)
                            androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                            Text(f, color = Pal.Text, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
