package org.ivansola.minutricion.data

import androidx.compose.runtime.Immutable
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Producto de Open Food Facts (valores por 100 g). Portado de off.py. */
@Immutable
data class OffProduct(
    val code: String,
    val name: String,
    val brand: String,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    /** Gramos/ml por ración si OFF lo indica (serving_size / serving_quantity). */
    val servingG: Double? = null,
    /** Nutrientes extra REALES de OFF (saturadas, azúcares, sal, fibra, vitaminas…) por 100 g. */
    val nutrients: Map<String, Double> = emptyMap(),
    /** Lista de ingredientes (texto), alérgenos y categorías de OFF. */
    val ingredients: String = "",
    val allergens: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
) {
    /** Nombre con la marca entre paréntesis, como en la app Kivy (sin duplicar si nombre == marca). */
    val label: String get() = when {
        brand.isNotBlank() && !name.equals(brand, ignoreCase = true) -> "$name ($brand)"
        else -> name
    }

    /** Convierte a un Food (valores por 100 g) para poder registrarlo. */
    fun toFood() = Food(id = 0, name = label, kcal = kcal, protein = protein, carbs = carbs,
        fat = fat, serving = servingG, nutrients = nutrients,
        ingredients = ingredients, allergens = allergens, categories = categories)
}

/** Cliente de Open Food Facts (búsqueda por código de barras y por nombre). */
object Off {
    private const val BASE = "https://world.openfoodfacts.org"
    private const val FIELDS = "code,product_name,generic_name,brands,nutriments,serving_size," +
        "serving_quantity,ingredients_text_es,ingredients_text,allergens_tags,categories_tags"
    private val SERVING_NUM = Regex("""(\d+[.,]?\d*)\s*(g|ml|gr)""", RegexOption.IGNORE_CASE)
    private const val TIMEOUT = 12000

    /** Busca un producto por código de barras. Devuelve null si no existe o hay error de red. */
    fun byBarcode(code: String): OffProduct? {
        val c = code.trim()
        if (c.isEmpty()) return null
        val url = "$BASE/api/v2/product/${URLEncoder.encode(c, "UTF-8")}.json?fields=$FIELDS"
        val json = get(url) ?: return null
        if (json.optInt("status") != 1) return null
        val product = json.optJSONObject("product") ?: return null
        return parse(product)
    }

    /** Busca productos por nombre. `country` = subdominio de OFF (es, us, world, …). */
    fun search(query: String, country: String = "world", pageSize: Int = 25): List<OffProduct> {
        val q = URLEncoder.encode(query, "UTF-8")
        val sub = country.ifBlank { "world" }
        val url = "https://$sub.openfoodfacts.org/cgi/search.pl?search_terms=$q" +
            "&search_simple=1&action=process&json=1&page_size=$pageSize&fields=$FIELDS"
        val json = get(url) ?: return emptyList()
        val arr = json.optJSONArray("products") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(::parse) }
            .filter { it.name != "(sin nombre)" }
    }

    private fun get(url: String): JSONObject? = try {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT
            readTimeout = TIMEOUT
            setRequestProperty("User-Agent", "MiNutricion/2.0 (uso personal)")
        }
        conn.inputStream.use { JSONObject(it.bufferedReader().readText()) }
    } catch (e: Exception) {
        null
    }

    private fun round1(x: Double) = Math.round(x * 10) / 10.0

    /**
     * Valor por 100 g de un nutriente. Si OFF no trae `<base>_100g` pero sí `<base>_serving` y se
     * conoce el tamaño de la ración, lo deriva: valor_100g = valor_serving ÷ raciónG × 100.
     * `base` es la clave sin sufijo (p. ej. "proteins", "saturated-fat").
     */
    private fun per100(n: JSONObject, base: String, servingG: Double?): Double? {
        val v = n.opt("${base}_100g")
        if (v is Number) return v.toDouble()
        if (servingG != null && servingG > 0) {
            val vs = n.opt("${base}_serving")
            if (vs is Number) return vs.toDouble() / servingG * 100.0
        }
        return null
    }

    private fun kcal100(n: JSONObject, servingG: Double?): Double {
        val v = n.opt("energy-kcal_100g")
        if (v is Number) return round1(v.toDouble())
        val kj = n.opt("energy_100g")
        if (kj is Number) return round1(kj.toDouble() / 4.184)
        if (servingG != null && servingG > 0) {
            val vs = n.opt("energy-kcal_serving")
            if (vs is Number) return round1(vs.toDouble() / servingG * 100.0)
            val kjs = n.opt("energy_serving")
            if (kjs is Number) return round1(kjs.toDouble() / 4.184 / servingG * 100.0)
        }
        return 0.0
    }

    private fun parse(p: JSONObject): OffProduct {
        val n = p.optJSONObject("nutriments") ?: JSONObject()
        val brand = p.optString("brands").split(",").firstOrNull()?.trim().orEmpty()
        // Respaldo de nombre: product_name → generic_name → marca → "(sin nombre)".
        val name = p.optString("product_name").trim()
            .ifEmpty { p.optString("generic_name").trim() }
            .ifEmpty { brand }
            .ifEmpty { "(sin nombre)" }
        val sg = servingGrams(p)
        return OffProduct(
            code = p.optString("code"), name = name, brand = brand,
            kcal = kcal100(n, sg),
            protein = per100(n, "proteins", sg)?.let(::round1) ?: 0.0,
            carbs = per100(n, "carbohydrates", sg)?.let(::round1) ?: 0.0,
            fat = per100(n, "fat", sg)?.let(::round1) ?: 0.0,
            servingG = sg,
            nutrients = extraNutrients(n, sg),
            ingredients = p.optString("ingredients_text_es").trim()
                .ifEmpty { p.optString("ingredients_text").trim() },
            allergens = tagList(p, "allergens_tags"),
            categories = tagList(p, "categories_tags"),
        )
    }

    /** Nutriente OFF (por 100 g, en gramos) → clave de la app + factor a su unidad (mg=1000, µg=1e6). */
    private class NMap(val off: String, val app: String, val factor: Double)
    private val NUTR = listOf(
        NMap("saturated-fat_100g", "sat_fat", 1.0), NMap("trans-fat_100g", "trans_fat", 1.0),
        NMap("sugars_100g", "sugars", 1.0), NMap("added-sugars_100g", "added_sugars", 1.0),
        NMap("fiber_100g", "fiber", 1.0), NMap("salt_100g", "salt", 1.0),
        NMap("sodium_100g", "sodium", 1000.0), NMap("cholesterol_100g", "cholesterol", 1000.0),
        NMap("caffeine_100g", "caffeine", 1000.0),
        // vitaminas (OFF en gramos → µg o mg)
        NMap("vitamin-a_100g", "vit_a", 1e6), NMap("vitamin-c_100g", "vit_c", 1000.0),
        NMap("vitamin-d_100g", "vit_d", 1e6), NMap("vitamin-e_100g", "vit_e", 1000.0),
        NMap("vitamin-k_100g", "vit_k", 1e6), NMap("vitamin-b1_100g", "b1", 1000.0),
        NMap("vitamin-b2_100g", "b2", 1000.0), NMap("vitamin-b6_100g", "b6", 1000.0),
        NMap("vitamin-b12_100g", "b12", 1e6), NMap("vitamin-b9_100g", "folate", 1e6),
        NMap("folates_100g", "folate", 1e6),
        NMap("vitamin-pp_100g", "b3", 1000.0), NMap("niacin_100g", "b3", 1000.0),
        NMap("pantothenic-acid_100g", "b5", 1000.0), NMap("biotin_100g", "biotin", 1e6),
        // minerales
        NMap("calcium_100g", "calcium", 1000.0), NMap("iron_100g", "iron", 1000.0),
        NMap("magnesium_100g", "magnesium", 1000.0), NMap("phosphorus_100g", "phosphorus", 1000.0),
        NMap("potassium_100g", "potassium", 1000.0), NMap("zinc_100g", "zinc", 1000.0),
        NMap("copper_100g", "copper", 1000.0), NMap("manganese_100g", "manganese", 1000.0),
        NMap("selenium_100g", "selenium", 1e6),
        NMap("iodine_100g", "iodine", 1e6), NMap("chromium_100g", "chromium", 1e6),
        NMap("molybdenum_100g", "molybdenum", 1e6), NMap("fluoride_100g", "fluoride", 1000.0),
        NMap("chloride_100g", "chloride", 1000.0),
    )

    private fun extraNutrients(n: JSONObject, servingG: Double?): Map<String, Double> {
        val out = HashMap<String, Double>()
        for (m in NUTR) {
            // m.off es "<base>_100g"; usa el valor por 100 g o, si falta, deriva del `_serving`.
            val base = m.off.removeSuffix("_100g")
            val v100 = per100(n, base, servingG) ?: continue
            if (!out.containsKey(m.app)) out[m.app] = Math.round(v100 * m.factor * 100.0) / 100.0
        }
        return out
    }

    /** Lee un array de tags OFF ("en:milk") y devuelve etiquetas legibles ("Milk"). */
    private fun tagList(p: JSONObject, key: String): List<String> {
        val arr = p.optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optString(i).substringAfter(':').replace('-', ' ').trim()
                .replaceFirstChar { it.uppercase() }.ifBlank { null }
        }
    }

    /** Gramos por ración: `serving_quantity` (número) o el primer número de `serving_size`. */
    private fun servingGrams(p: JSONObject): Double? {
        (p.opt("serving_quantity") as? Number)?.toDouble()?.let { if (it > 0) return round1(it) }
        val sq = p.optString("serving_quantity").replace(",", ".").toDoubleOrNull()
        if (sq != null && sq > 0) return round1(sq)
        val ss = p.optString("serving_size")
        val m = SERVING_NUM.find(ss) ?: return null
        val v = m.groupValues[1].replace(",", ".").toDoubleOrNull() ?: return null
        return if (v > 0) round1(v) else null
    }
}
