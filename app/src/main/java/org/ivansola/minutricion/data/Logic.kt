package org.ivansola.minutricion.data

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** Un alimento que aporta a un nutriente/macro, promediado por día del periodo. */
data class Source(val name: String, val amount: Double)

/** Resultado de agregar el diario sobre un periodo (Día/Semana/Mes). */
data class AggregateResult(
    val summary: Macros,
    val nutrients: Map<String, Double>,
    val days: Int,
    val sources: Map<String, List<Source>>,
)

/** Rachas de registro (días con algo apuntado) y de días perfectos (kcal en rango). */
data class Streaks(val regCur: Int, val regBest: Int, val perfCur: Int, val perfBest: Int)

/** Cálculos derivados (objetivos, resumen del día, score). Portado de logic.py. */
object Logic {
    const val FIBER_GOAL = 25.0
    val LIMIT_REF = mapOf("sat_fat" to 20.0, "trans_fat" to 2.0, "sugars" to 50.0,
        "salt" to 6.0, "sodium" to 2400.0)

    const val GREEN = "#30d158"
    const val YELLOW = "#FFC61A"
    const val RED = "#e5484d"
    const val GRAY = "#8e8e93"

    /** Objetivo de kcal calculado (Mifflin + actividad + meta). null si faltan datos. */
    fun autoKcal(): Int? {
        val p = Db.getProfile() ?: return null
        if (p.sex == null || p.age == null || p.height == null || p.weight == null ||
            p.activity == null || p.goal == null) return null
        val af = Nutrition.ACTIVITY[p.activity] ?: return null
        val adj = Nutrition.GOALS[p.goal] ?: return null
        return Nutrition.targetKcal(p.sex, p.weight, p.height, p.age.toInt(), af, adj).roundToInt()
    }

    /** (p, c, f) fracciones que suman ~1. Personalizables. */
    fun macroRatios(): Triple<Double, Double, Double> {
        val p = Db.getSetting("macro_ratio_p")?.toDoubleOrNull() ?: 0.30
        val c = Db.getSetting("macro_ratio_c")?.toDoubleOrNull() ?: 0.50
        val f = Db.getSetting("macro_ratio_f")?.toDoubleOrNull() ?: 0.20
        val s = p + c + f
        return if (s <= 0) Triple(0.30, 0.50, 0.20) else Triple(p / s, c / s, f / s)
    }

    /** Guarda el reparto de macros (fracciones normalizadas). */
    fun setMacroRatios(p: Double, c: Double, f: Double) {
        val s = (p + c + f).let { if (it <= 0) 1.0 else it }
        Db.setSetting("macro_ratio_p", (p / s).toString())
        Db.setSetting("macro_ratio_c", (c / s).toString())
        Db.setSetting("macro_ratio_f", (f / s).toString())
    }

    /** Objetivo diario {kcal, protein, carbs, fat} o null si no hay perfil válido. */
    fun targets(): Targets? {
        val p = Db.getProfile() ?: return null
        val kcal: Int = if ((p.mode ?: "auto") == "manual") {
            val mk = p.manualKcal
            if (mk == null || mk <= 0) return null
            mk.roundToInt()
        } else {
            autoKcal() ?: return null
        }
        val (pr, ca, fa) = macroRatios()
        val (gp, gc, gf) = Nutrition.macrosFor(kcal.toDouble(), pr, ca, fa)
        return Targets(kcal, gp.roundToInt(), gc.roundToInt(), gf.roundToInt())
    }

    /** Calorías dentro de la banda aceptable (90%–110% del objetivo). */
    fun kcalOk(value: Double, target: Double) =
        target > 0 && 0.9 * target <= value && value <= 1.1 * target

    /** {kcal, protein, carbs, fat} consumidos ese día (solo comidas visibles/incluidas). */
    fun daySummary(day: String, meals: List<String> = Db.getMeals()): Macros {
        var k = 0.0; var p = 0.0; var c = 0.0; var f = 0.0
        for (e in Db.entriesForDay(day)) {
            if (!e.included || e.meal !in meals) continue
            k += e.totalKcal; p += e.totalProtein; c += e.totalCarbs; f += e.totalFat
        }
        return Macros(k, p, c, f)
    }

    /** Totales del día de TODOS los nutrientes (reales o estimados), escalados a gramos. */
    fun dayNutrientTotals(day: String, meals: List<String> = Db.getMeals()): Map<String, Double> {
        val totals = HashMap<String, Double>()
        for (e in Db.entriesForDay(day)) {
            if (!e.included || e.meal !in meals) continue
            val g = e.grams / 100.0
            val est = Nutriest.estimate(e.name, e.kcal, e.protein, e.carbs, e.fat).toMutableMap()
            est.putAll(e.nutrients)
            for ((kk, v) in est) totals[kk] = (totals[kk] ?: 0.0) + v * g
        }
        return totals
    }

    /** Score nutricional 0-100 (misma fórmula que la app de escritorio). */
    fun nutriScore(t: Macros, tgt: Targets?, nut: Map<String, Double>? = null): Int {
        if (tgt == null || tgt.kcal == 0) return 0
        val kc = 1 - min(abs(t.kcal - tgt.kcal) / tgt.kcal, 1.0)
        val macs = listOf(
            if (tgt.protein != 0) min(t.protein / tgt.protein, 1.0) else 0.0,
            if (tgt.carbs != 0) min(t.carbs / tgt.carbs, 1.0) else 0.0,
            if (tgt.fat != 0) min(t.fat / tgt.fat, 1.0) else 0.0,
        )
        val macro = macs.sum() / 3
        if (nut == null) return ((0.4 * kc + 0.6 * macro) * 100).roundToInt()
        val fiber = min((nut["fiber"] ?: 0.0) / FIBER_GOAL, 1.0)
        val positive = 0.30 * kc + 0.50 * macro + 0.20 * fiber
        fun over(amount: Double, ref: Double) = min(maxOf(0.0, (amount - ref) / ref), 1.0)
        val pen = 0.10 * over(nut["sat_fat"] ?: 0.0, LIMIT_REF["sat_fat"]!!) +
                0.10 * over(nut["salt"] ?: 0.0, LIMIT_REF["salt"]!!) +
                0.08 * over(nut["sugars"] ?: 0.0, LIMIT_REF["sugars"]!!)
        return (maxOf(0.0, positive - pen) * 100).roundToInt()
    }

    /** (color_hex, etiqueta) según el score. */
    fun nutriStyle(s: Int): Pair<String, String> = when {
        s >= 80 -> GREEN to "Alto"
        s >= 60 -> YELLOW to "Medio"
        else -> RED to "Bajo"
    }

    /** Semanas estimadas para alcanzar el peso objetivo (misma fórmula que la de escritorio). */
    fun weeksEstimate(cur: Double?, target: Double?, goal: String?): Int? {
        val delta = (Nutrition.GOALS[goal] ?: 0).toDouble()
        if (cur == null || target == null || delta == 0.0) return null
        val kgPerWeek = abs(delta) * 7 / 7700.0
        if (kgPerWeek <= 0 || abs(cur - target) < 0.1) return null
        return maxOf(1, kotlin.math.ceil(abs(cur - target) / kgPerWeek).toInt())
    }

    /** Media móvil exponencial (línea de peso promedio). */
    fun ema(vals: List<Double>, alpha: Double = 0.35): List<Double> {
        if (vals.isEmpty()) return emptyList()
        val out = ArrayList<Double>(vals.size)
        out.add(vals[0])
        for (i in 1 until vals.size) out.add(alpha * vals[i] + (1 - alpha) * out.last())
        return out
    }

    /** Rachas de registro y de días perfectos (actual y mejor de los últimos ~400 días). */
    fun streaks(): Streaks {
        val today = LocalDate.now()
        val start = today.minusDays(400)
        val logged = Db.loggedDays(start.toString(), today.toString())
        val tgt = targets()
        val totals = Db.dailyTotals(start.toString(), today.toString(), Db.getMeals())
        fun reg(d: LocalDate) = d.toString() in logged
        fun perf(d: LocalDate): Boolean {
            val t = totals[d.toString()] ?: return false
            return tgt != null && kcalOk(t.kcal, tgt.kcal.toDouble())
        }
        fun cur(pred: (LocalDate) -> Boolean): Int {
            var n = 0; var d = today
            while (pred(d)) { n++; d = d.minusDays(1) }
            return n
        }
        fun best(pred: (LocalDate) -> Boolean): Int {
            var b = 0; var c = 0; var d = start
            while (!d.isAfter(today)) { c = if (pred(d)) c + 1 else 0; b = maxOf(b, c); d = d.plusDays(1) }
            return b
        }
        return Streaks(cur(::reg), best(::reg), cur(::perf), best(::perf))
    }

    /** Días que cubre el periodo (Día/Semana/Mes) que contiene a `ref`. */
    fun periodDays(period: String, ref: LocalDate): List<LocalDate> = when (period) {
        "Semana" -> {
            val monday = ref.minusDays((ref.dayOfWeek.value - 1).toLong())
            (0..6).map { monday.plusDays(it.toLong()) }
        }
        "Mes" -> {
            val first = ref.withDayOfMonth(1)
            (0 until ref.lengthOfMonth()).map { first.plusDays(it.toLong()) }
        }
        else -> listOf(ref)
    }

    /**
     * Agrega el diario sobre un periodo: totales promediados por día CON registro (salvo "Día",
     * que usa el día elegido tal cual), más las "fuentes" (alimentos) que aportan cada nutriente.
     */
    fun aggregate(period: String, ref: LocalDate, meals: List<String>): AggregateResult {
        val days = periodDays(period, ref)
        val active = if (period == "Día") listOf(ref) else {
            val logged = Db.loggedDays(days.first().toString(), days.last().toString())
            days.filter { it.toString() in logged }
        }
        val nd = maxOf(active.size, 1)
        var k = 0.0; var p = 0.0; var c = 0.0; var f = 0.0
        val nuts = HashMap<String, Double>()
        val acc = HashMap<String, HashMap<String, Double>>()
        fun addSource(key: String, name: String, amt: Double) {
            if (amt <= 0) return
            val m = acc.getOrPut(key) { HashMap() }
            m[name] = (m[name] ?: 0.0) + amt
        }
        for (d in active) {
            for (e in Db.entriesForDay(d.toString())) {
                if (!e.included || e.meal !in meals) continue
                val g = e.grams / 100.0
                k += e.totalKcal; p += e.totalProtein; c += e.totalCarbs; f += e.totalFat
                addSource("kcal", e.name, e.totalKcal)
                addSource("protein", e.name, e.totalProtein)
                addSource("carbs", e.name, e.totalCarbs)
                addSource("fat", e.name, e.totalFat)
                val est = Nutriest.estimate(e.name, e.kcal, e.protein, e.carbs, e.fat).toMutableMap()
                est.putAll(e.nutrients)
                for ((kk, v) in est) {
                    val amt = v * g
                    if (amt <= 0) continue
                    nuts[kk] = (nuts[kk] ?: 0.0) + amt
                    addSource(kk, e.name, amt)
                }
            }
        }
        k /= nd; p /= nd; c /= nd; f /= nd
        for (kk in nuts.keys.toList()) nuts[kk] = nuts[kk]!! / nd
        val sources = acc.mapValues { (_, m) ->
            m.map { (nm, amt) -> Source(nm, amt / nd) }.sortedByDescending { it.amount }
        }
        return AggregateResult(Macros(k, p, c, f), nuts, nd, sources)
    }

    /** Color (hex) de una fila de nutriente según su tipo: "kcal" | "limit" | "reach". */
    fun rowColor(kind: String, v: Double, goal: Double?): String = when (kind) {
        "kcal" -> when {
            goal != null && goal > 0 && kcalOk(v, goal) -> GREEN
            goal != null && goal > 0 && (v > goal * 1.1 || v < goal * 0.5) -> RED
            else -> YELLOW
        }
        "limit" -> limitColor(v, goal)
        else -> reachColor(v, goal)
    }

    private fun reachColor(v: Double, goal: Double?): String = when {
        goal == null || goal <= 0 -> GRAY
        v >= goal * 0.95 -> GREEN
        v >= goal * 0.5 -> YELLOW
        else -> RED
    }

    private fun limitColor(v: Double, ref: Double?): String = when {
        ref == null || ref <= 0 -> GRAY
        v <= ref -> GREEN
        v <= ref * 1.25 -> YELLOW
        else -> RED
    }

    /** Número formateado: decimales finos para valores pequeños, miles con "." para grandes.
     *  Usa Locale.US para el separador decimal '.' (con locale español "%.2f" daba "0," -> bug). */
    fun fmtNum(v: Double): String {
        val a = abs(v)
        return when {
            a < 1 -> trimZeros(String.format(java.util.Locale.US, "%.2f", v))
            a < 10 -> trimZeros(String.format(java.util.Locale.US, "%.1f", v))
            else -> String.format(java.util.Locale.US, "%,d", v.roundToInt()).replace(",", ".")
        }
    }

    private fun trimZeros(s: String): String {
        val t = s.trimEnd('0').trimEnd('.')
        return t.ifEmpty { "0" }
    }

    /** (texto de estado, color) de una fila de nutriente, como Fitia. */
    fun status(kind: String, v: Double, goal: Double?): Pair<String, String> {
        val col = rowColor(kind, v, goal)
        val over = goal != null && goal > 0 && v > goal
        val txt = when (kind) {
            "limit" -> when (col) { GREEN -> "En rango"; YELLOW -> "Alto"; RED -> "Muy alto"; else -> "" }
            "kcal" -> when {
                col == GREEN -> "En rango"
                col == RED -> if (over) "Muy alto" else "Muy bajo"
                else -> if (over) "Alto" else "Bajo"
            }
            else -> when (col) { GREEN -> "En rango"; YELLOW -> "Bajo"; RED -> "Muy bajo"; else -> "" }
        }
        return txt to col
    }
}
