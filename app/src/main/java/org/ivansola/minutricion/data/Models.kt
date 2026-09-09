package org.ivansola.minutricion.data

import androidx.compose.runtime.Immutable

/** Un registro del diario (una comida de un día). Valores nutricionales POR 100 g. */
@Immutable
data class Entry(
    val id: Long,
    val day: String,
    val meal: String,
    val name: String,
    val grams: Double,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val included: Boolean,
    val nutrients: Map<String, Double> = emptyMap(),
) {
    val f: Double get() = grams / 100.0
    val totalKcal: Double get() = kcal * f
    val totalProtein: Double get() = protein * f
    val totalCarbs: Double get() = carbs * f
    val totalFat: Double get() = fat * f
}

/** Un componente de un surtido (una de las varias tablas nutricionales). Valores POR 100 g. */
@Immutable
data class FoodComponent(
    val name: String,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val nutrients: Map<String, Double> = emptyMap(),
)

/** Alimento de la biblioteca. Valores POR 100 g. */
@Immutable
data class Food(
    val id: Long,
    val name: String,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val nutrients: Map<String, Double> = emptyMap(),
    /** Surtido: varias tablas (una por componente). Vacío = alimento normal. Los macros de arriba
     *  son la MEDIA de los componentes; al registrar se elige uno. */
    val components: List<FoodComponent> = emptyList(),
    val userCreated: Boolean = false,
    /** Tamaño de una ración en g/ml, si la fuente (Open Food Facts) lo indica. */
    val serving: Double? = null,
    /** Metadatos de Open Food Facts (solo en la ficha; no se persisten). */
    val ingredients: String = "",
    val allergens: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    /** Categoría elegida por el usuario (Fitia): determina el icono y se PERSISTE. */
    val category: String? = null,
    /** Código de barras asociado (se persiste): al escanearlo luego, se encuentra en local. */
    val barcode: String? = null,
    /** TODOS los códigos de la misma ficha (p. ej. distintos pesos del mismo sabor comparten
     *  perfil): al escanear cualquiera de ellos se encuentra esta ficha. */
    val barcodes: List<String> = emptyList(),
)

/** Perfil del usuario (para calcular objetivos). */
@Immutable
data class Profile(
    val sex: String? = null,
    val age: Double? = null,
    val height: Double? = null,
    val weight: Double? = null,
    val activity: String? = null,
    val goal: String? = null,
    val mode: String? = null,
    val manualKcal: Double? = null,
    val targetWeight: Double? = null,
)

/** Objetivo diario de calorías y macros (gramos). */
@Immutable
data class Targets(
    val kcal: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int,
)

/** Totales consumidos de un día. */
@Immutable
data class Macros(
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
)
