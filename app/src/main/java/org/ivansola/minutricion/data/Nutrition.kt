package org.ivansola.minutricion.data

/** Cálculo del objetivo de calorías (Mifflin-St Jeor). Portado de nutrition.py. */
object Nutrition {
    val ACTIVITY = linkedMapOf(
        "Sedentario (poco o nada de ejercicio)" to 1.2,
        "Ligero (1-3 días/semana)" to 1.375,
        "Moderado (3-5 días/semana)" to 1.55,
        "Alto (6-7 días/semana)" to 1.725,
        "Muy alto (trabajo físico / 2x día)" to 1.9,
    )
    val GOALS = linkedMapOf(
        "Perder grasa (-500 kcal)" to -500,
        "Déficit ligero (-250 kcal)" to -250,
        "Mantenimiento" to 0,
        "Volumen ligero (+250 kcal)" to 250,
        "Volumen (+500 kcal)" to 500,
    )

    fun bmrMifflin(sex: String?, weight: Double, height: Double, age: Int): Double {
        val base = 10 * weight + 6.25 * height - 5 * age
        return base + if (sex == "Hombre") 5 else -161
    }

    fun tdee(sex: String?, weight: Double, height: Double, age: Int, af: Double) =
        bmrMifflin(sex, weight, height, age) * af

    fun targetKcal(sex: String?, weight: Double, height: Double, age: Int, af: Double, adjust: Int) =
        tdee(sex, weight, height, age, af) + adjust

    /** Reparte kcal en gramos de macros (proteína, carbos, grasa). */
    fun macrosFor(kcal: Double, p: Double = 0.30, c: Double = 0.50, f: Double = 0.20) =
        Triple(kcal * p / 4.0, kcal * c / 4.0, kcal * f / 9.0)
}
