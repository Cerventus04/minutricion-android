package org.ivansola.minutricion

import org.ivansola.minutricion.ui.components.FoodIcons
import org.junit.Assert.assertEquals
import org.junit.Test

/** Nombres reales que salían con el icono equivocado, y el que les toca. */
class IconRulesTest {

    private fun check(expected: Int, vararg names: String) = names.forEach { name ->
        assertEquals(name, expected, FoodIcons.resFor(name))
    }

    @Test
    fun loQueSeVeEnLaLista() {
        check(R.drawable.food_tortitas, "Tortitas Milho", "Tortitas de maíz con chocolate")
        check(R.drawable.food_bebidas, "Bebida de canela y limón")
        check(R.drawable.food_quesos, "Lonchas de queso (Milbona)")
    }

    @Test
    fun ganaLoQueEsNoLoQueLleva() {
        check(R.drawable.food_postres, "Natilla con sorpresa de chocolate")
        check(R.drawable.food_galletas, "Galleta Cookie&Cream Nocilla", "Chocogalleta MILKA", "Galletas de avena")
        check(R.drawable.food_panaderia, "Bizcocho con pepitas de chocolate", "Donuts sabor fresa",
            "Masa de hojaldre")
        check(R.drawable.food_panes, "Pan de molde sésamo y lino")
        check(R.drawable.food_untables, "Paté de atún", "Crema de avellanas con cookies")
        check(R.drawable.food_yogurt, "Yogur griego con miel", "Yogur sabor galleta")
        check(R.drawable.food_helados, "Sorbete de limón tarrina 1 litro", "Helado barrita TWIX")
        check(R.drawable.food_preparados, "Croquetas de hongos", "Croquetas de jamón")
        check(R.drawable.food_aceitunas, "Aceitunas rellenas de anchoa")
        check(R.drawable.food_cereales, "Porridge de Arroz Proteico - Speculoos")
    }

    @Test
    fun ingredienteYSabor() {
        check(R.drawable.food_embutidos, "Salchichón de pavo")
        check(R.drawable.food_pescado, "Lomo de bacalao")
        check(R.drawable.food_salsas, "Crema balsámica de Módena")
        check(R.drawable.food_cafe, "Achicoria soluble")
        check(R.drawable.food_granos, "Espelta Grano Bio (Drasanvi)")
        check(R.drawable.food_infantil, "Galletas infantiles desde 6 meses", "Tarrito de merluza con verduras")
        check(R.drawable.food_suplementos, "Gominolas de Ashwagandha (60 gominolas)")
        check(R.drawable.food_barrita, "BOX 6 RACE DAY BAR BCAA - Barrita Energética Vegana",
            "Zero Choclite - Chocolate con Leche y Crema de Avellanas - Barrita Zero Choclite")
        check(R.drawable.food_pasta, "Macarrones con atún en salsa de tomate Carretilla",
            "Pasta con salmón salvaje y salsa de hierbas aromáticas")
        check(R.drawable.food_untables, "Pasta de almendra Hacendado")
        check(R.drawable.food_preparados, "Empanada hojaldre de carne")
        check(R.drawable.food_pollo, "Filetes de pollo empanado El Mercado")
        check(R.drawable.food_quesos, "Queso rallado filatto especial pasta El Caserío 80 g.")
        check(R.drawable.food_proteina, "Impact Whey Isolate Milkshake Helado de Vainilla")
        check(R.drawable.food_instantanea, "Fideos orientales clásicos Yatekomo Gallina Blanca")
        check(R.drawable.food_galletas, "Pastas de té Dulcesol")
        check(R.drawable.food_chocolates, "Chocolate negro para postres sin azúcares añadidos")
        check(R.drawable.food_hamburguesas, "Hamburguesa de vacuno con cecina El Mercado 300 g")
        check(R.drawable.food_semillas, "Pipas sabor bacon Baconeras Grefusa 165 g.")
        check(R.drawable.food_salsas, "Vinagre balsámico de Módena edición dorada BORGES, botella 25 cl")
        check(R.drawable.food_panaderia, "Sobao pasiego grande EROSKI")
        check(R.drawable.food_pasta, "Hélices vegetales Dia Al Diante 1 Kg")
        check(R.drawable.food_aceitunas, "Alcaparras en vinagre FRAGATA, frasco 99 g")
        check(R.drawable.food_proteina, "100% Proteína de soja - Chocolate (Prozis)",
            "Proteína de soja aislada 2.0 Soy Protein Isolate Chai Tea Latte Kg (Essential Series)")
        check(R.drawable.food_mariscos, "Trozos de calamar en salsa marinera Classic Carrefour")
        check(R.drawable.food_panes, "Barra grande EROSKI, 330 g")
        check(R.drawable.food_barrita, "Barra de cereales con chocolate")
        check(R.drawable.food_untables, "NaturGreen Paté Nature Bio 125 g")
        check(R.drawable.food_panes, "Pan blanco sin corteza EROSKI, paquete 700 g")
        check(R.drawable.food_snacks, "Corteza de jamón natural 110 g")
        check(R.drawable.food_te, "Hinojo en bolsitas Pompadour 20 ud.")
        check(R.drawable.food_chocolates, "Maíz frito barbacoa bañado en chocolate negro Mr. Corn")
    }

    @Test
    fun iconosNuevos() {
        check(R.drawable.food_cerveza, "Cerveza Mahou 5 Estrellas 50 cl", "Cerveza tostada 0,0% alcohol Dia Ramblers 33 cl")
        check(R.drawable.food_alcohol, "Vino tinto joven D.O. Rioja 75 cl")
        check(R.drawable.food_zumo, "Zumo de naranja recién exprimido con pulpa botella 1 l.")
        check(R.drawable.food_te, "Infusión con tomillo y menta en bolsitas", "Té verde matcha 20 bolsitas")
        check(R.drawable.food_cafe, "Café molido natural BONKA, paquete 250 g")
        check(R.drawable.food_pizza, "Pizza Ristorante pollo DR.OETKER, caja 355 g")
        check(R.drawable.food_postres, "Flan de huevo", "Postre de trufa LA LECHERA, pack 2x125 g")
        check(R.drawable.food_embutidos, "Chorizo picante artesano IRURA", "Salchichas Frankfurt PICKEN")
        check(R.drawable.food_cerdo, "Jamón curado gran reserva en lonchas Nico 90 g")
        check(R.drawable.food_fiambres, "Jamón cocido extra Campofrío", "Jamón de pavo mini EROSKI",
            "Mortadela con Aceitunas al Corte (Campofrío)")
        check(R.drawable.food_mariscos, "Mejillones cocidos en su jugo Aguinamar 1 Kg")
        check(R.drawable.food_alcohol, "Vino generoso Cortijo Ceret D.O. Manzanilla-Sanlúcar de Barrameda 75 cl.")
        check(R.drawable.food_te, "Infusión de anís estrellado 20 bolsitas")
    }

    @Test
    fun laCategoriaCedeAnteAlgoMasConcreto() {
        fun cat(expected: Int, name: String, category: String) =
            assertEquals("$name [$category]", expected, FoodIcons.resFor(name, category = category))
        cat(R.drawable.food_proteina, "100% Real Whey Protein - Vainilla", "Suplementos")
        cat(R.drawable.food_cerveza, "Cerveza Mahou clásica lata 50 cl.", "Bebidas alcohólicas")
        cat(R.drawable.food_galletas, "Galletas Digestive Avena", "Dulces")
        cat(R.drawable.food_tortitas, "Tortitas de arroz", "Cereales")
        // de otra familia: manda la categoría que eligió el usuario
        cat(R.drawable.food_suplementos, "Chocolate negro 85%", "Suplementos")
        cat(R.drawable.food_leche, "Galletas María", "Leche")
        // "Otros" no dice nada
        cat(R.drawable.food_pizza, "Pizza barbacoa", "Otros")
    }
}
