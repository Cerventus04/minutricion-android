package org.ivansola.minutricion.data

/** Categoría de alimento estilo Fitia: etiqueta visible, emoji del icono y si es bebida (unidad ml). */
data class FoodCat(val label: String, val emoji: String, val drink: Boolean = false)

/** Catálogo de categorías (mismas que Fitia) con su emoji. El orden es el de la hoja de selección. */
object FoodCategories {
    val ALL: List<FoodCat> = listOf(
        FoodCat("Untables", "🍯"),            // miel/untable
        FoodCat("Mariscos", "🦐"),            // gamba
        FoodCat("Hamburguesas", "🍔"),
        FoodCat("Carne de caza", "🦌"),       // ciervo (caza)
        FoodCat("Helados", "🍦"),
        FoodCat("Productos de soya", "🫛"),   // vaina/edamame
        FoodCat("Otros", "🍽️"),
        FoodCat("Res", "🥩"),                 // filete rojo
        FoodCat("Legumbres", "🫘"),           // alubias
        FoodCat("Pavo", "🦃"),
        FoodCat("Panes", "🍞"),
        FoodCat("Huevo", "🥚"),
        FoodCat("Panadería", "🥐"),           // cruasán
        FoodCat("Platos preparados", "🥫"),   // conserva
        FoodCat("Suplementos", "💊"),
        FoodCat("Especias y hierbas", "🌿"),
        FoodCat("Snacks", "🍿"),
        FoodCat("Carnes vegetales", "🧆"),    // falafel (proteína vegetal)
        FoodCat("Pasta", "🍝"),
        FoodCat("Café e Infusiones", "☕", drink = true),
        FoodCat("Cereales", "🥣"),            // bol de cereales
        FoodCat("Dulces", "🍬"),
        FoodCat("Yogurt", "🍨"),              // tarrina con cuchara
        FoodCat("Pescado", "🐟"),
        FoodCat("Salsas", "🫙"),              // tarro de salsa
        FoodCat("Verduras", "🥦"),
        FoodCat("Quesos", "🧀"),
        FoodCat("Fiambres", "🥓"),            // fiambre/loncha
        FoodCat("Harinas", "🌾"),
        FoodCat("Comida instantánea", "🍜"),  // fideos instantáneos
        FoodCat("Bebidas alcohólicas", "🍷", drink = true),
        FoodCat("Granos", "🍚"),              // arroz/grano
        FoodCat("Restaurantes", "🏪"),
        FoodCat("Bebidas", "🥤", drink = true),
        FoodCat("Tubérculos", "🥔"),
        FoodCat("Mezclas instantáneas", "🍲"),
        FoodCat("Pollo", "🍗"),
        FoodCat("Bebidas vegetales", "🥥", drink = true),  // leche de coco/vegetal
        FoodCat("Aceites", "🫒"),             // aceite de oliva
        FoodCat("Cerdo", "🍖"),               // pierna/jamón
        FoodCat("Frutas", "🍎"),
        FoodCat("Chocolates", "🍫"),
        FoodCat("Leche", "🥛", drink = true),
        FoodCat("Semillas", "🌰"),
        FoodCat("Frutos secos", "🥜"),
    )

    private val byLabel = ALL.associateBy { it.label }

    fun emojiOf(label: String?): String? = label?.let { byLabel[it]?.emoji }
    fun isDrink(label: String?): Boolean = label?.let { byLabel[it]?.drink } ?: false
}
