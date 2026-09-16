package org.ivansola.minutricion.ui.components

/**
 * Busca palabras clave en nombres de alimentos como PALABRAS, no como trozos de texto.
 *
 * Con `keyword in name` la app casaba cosas absurdas: "nata" dentro de "preNATAl" (un suplemento
 * salía como leche), "coco" dentro de "COCOa", "sal" dentro de "SALami", "especia" dentro de
 * "ESPECIAl", "leche" dentro de "LECHEra"… Aquí una clave solo casa si empieza donde empieza una
 * palabra y termina donde termina, admitiendo:
 *  - los plurales españoles: "salmón" → "salmones", "yogur" → "yogures", "nuez" → "nueces";
 *  - tildes indistintas: "maiz" = "maíz", "tallarín" → "tallarines" (el plural pierde la tilde);
 *  - raíces: una clave terminada en `*` casa con cualquier final ("macarr*" → "macarrones").
 * Las claves de varias palabras ("tomate frito", "crema de") valen igual.
 */
object TextMatch {
    private const val LETTER = "[a-z0-9ñç]"

    /** Minúsculas y sin tildes (la ñ se conserva: "año" y "ano" no son lo mismo). */
    fun normalize(s: String): String = buildString(s.length) {
        for (ch in s.lowercase()) append(
            when (ch) {
                'á', 'à', 'ä', 'â' -> 'a'; 'é', 'è', 'ë', 'ê' -> 'e'; 'í', 'ì', 'ï', 'î' -> 'i'
                'ó', 'ò', 'ö', 'ô' -> 'o'; 'ú', 'ù', 'ü', 'û' -> 'u'; else -> ch
            }
        )
    }

    private val cache = HashMap<String, Regex>()

    private fun regexFor(keyword: String): Regex = cache.getOrPut(keyword) {
        val stem = keyword.endsWith("*")
        val k = normalize(keyword.removeSuffix("*"))
        val start = "(?<!$LETTER)"
        val end = "(?!$LETTER)"
        val pattern = when {
            stem -> start + Regex.escape(k)
            // "nuez" -> "nueces", "maíz" -> "maíces"
            k.endsWith("z") -> start + "(?:" + Regex.escape(k) + "(?:es|s)?|" +
                Regex.escape(k.dropLast(1)) + "ces)" + end
            else -> start + Regex.escape(k) + "(?:es|s)?" + end
        }
        Regex(pattern)
    }

    /** true si `keyword` aparece como palabra en `normalizedName` (ya pasado por [normalize]). */
    fun contains(normalizedName: String, keyword: String): Boolean =
        regexFor(keyword).containsMatchIn(normalizedName)

    /** Posiciones donde `keyword` aparece como palabra en `normalizedName`, de izquierda a derecha. */
    fun indicesOf(normalizedName: String, keyword: String): Sequence<Int> =
        regexFor(keyword).findAll(normalizedName).map { it.range.first }

    /** Primera entrada de la tabla cuya clave aparece en el nombre (el ORDEN de la tabla manda). */
    fun <V> firstMatch(name: String, table: Iterable<Pair<String, V>>): V? {
        val n = normalize(name)
        return table.firstOrNull { contains(n, it.first) }?.second
    }
}
