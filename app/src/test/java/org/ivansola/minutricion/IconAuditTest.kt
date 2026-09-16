package org.ivansola.minutricion

import org.ivansola.minutricion.ui.components.FoodIcons
import org.ivansola.minutricion.ui.components.isDrink
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Auditoría (no es un test de regresión): pasa ~10.000 nombres reales de productos por la
 * asignación de iconos y escribe un informe para revisar dónde falla. Solo corre si existe la
 * lista exportada de la base del scraper (tools/auditoria_iconos/nombres.txt).
 */
class IconAuditTest {

    private val dir = File("/mnt/c/Users/Ivan Sola/Desktop/MiNutricionAndroid/tools/auditoria_iconos")

    @Test
    fun auditoria() {
        val input = File(dir, "nombres.txt")
        assumeTrue("sin lista de nombres", input.exists())
        val iconName = R.drawable::class.java.fields
            .filter { it.name.startsWith("food_") }
            .associate { it.getInt(null) to it.name.removePrefix("food_") }

        val rows = input.readLines().filter { it.isNotBlank() }.map { name ->
            val drink = isDrink(name)
            Triple(name, drink, iconName[FoodIcons.resFor(name, drink)] ?: "?")
        }
        File(dir, "asignacion.tsv").writeText(
            rows.joinToString("\n") { (n, d, i) -> "$i\t${if (d) "bebida" else ""}\t$n" })

        val counts = rows.groupingBy { it.third }.eachCount().toList().sortedByDescending { it.second }
        File(dir, "resumen.txt").writeText(buildString {
            appendLine("productos: ${rows.size}")
            appendLine("bebidas detectadas: ${rows.count { it.second }}")
            counts.forEach { (icon, c) -> appendLine("%-15s %5d  %5.1f%%".format(icon, c, 100.0 * c / rows.size)) }
        })
    }
}
