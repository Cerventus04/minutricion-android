package org.ivansola.minutricion.data

import java.text.Normalizer

/**
 * Rellena en segundo plano los alimentos ANTIGUOS de la biblioteca con la información
 * que ahora captura Open Food Facts (ración, nutrientes extra, ingredientes, alérgenos,
 * categorías). Se ejecuta una sola vez por alimento: al terminar lo marca `backfilled=1`,
 * así en arranques posteriores no vuelve a consultar la red.
 */
object Backfill {
    @Volatile private var running = false

    /** Lanza el backfill en un hilo aparte (idempotente dentro de la sesión). */
    fun runAsync() {
        if (running) return
        running = true
        Thread({
            try {
                run()
            } catch (e: Exception) {
                // silencioso: es una mejora en segundo plano, nunca debe romper la app
            } finally {
                running = false
            }
        }, "off-backfill").start()
    }

    private fun run() {
        val country = Db.getSetting("food_country", "es") ?: "es"
        val pending = Db.foodsNeedingBackfill()
        for (f in pending) {
            // Los creados por el usuario no están en OFF: se marcan sin consultar.
            if (f.userCreated) { Db.setFoodBackfilled(f.name); continue }

            val (prod, exactName) = fetch(f, country) ?: (null to false)
            if (prod != null) {
                val enriched = f.copy(
                    serving = f.serving ?: prod.servingG,
                    nutrients = if (f.nutrients.isEmpty()) prod.nutrients else f.nutrients,
                    ingredients = f.ingredients.ifBlank { prod.ingredients },
                    allergens = f.allergens.ifEmpty { prod.allergens },
                    categories = f.categories.ifEmpty { prod.categories },
                )
                Db.upsertFood(enriched, userCreated = f.userCreated)
                // Si se ha encontrado por nombre y marca EXACTOS, es ese producto: se guarda su
                // código. Sin él, un escaneo posterior no lo reconoce y, si OFF le ha cambiado el
                // nombre entretanto, crea un duplicado (pasó con "Gnocchi" -> "ñordos").
                if (exactName && f.barcode.isNullOrBlank() && prod.code.isNotBlank()) {
                    Db.attachBarcode(f, prod.code)
                }
            }
            Db.setFoodBackfilled(f.name)
            Thread.sleep(350)   // amable con la API pública de OFF
        }
    }

    /**
     * Busca el producto en OFF por código de barras o, si no, por nombre (coincidencia estricta).
     * El segundo valor dice si el nombre CON marca coincide exactamente (entonces su código es fiable).
     */
    private fun fetch(f: Food, country: String): Pair<OffProduct, Boolean>? {
        if (!f.barcode.isNullOrBlank()) {
            Off.byBarcode(f.barcode)?.let { return it to false }
        }
        val query = f.name.substringBefore(" (").trim()
        if (query.length < 3) return null
        val results = Off.search(query, country, pageSize = 10)
        val target = norm(f.name)
        val targetShort = norm(query)
        // "Gnocchi" a secas casa con los de cualquier marca: solo nombre+marca identifica el producto
        val exact = results.firstOrNull { norm(it.label) == target }
        val match = exact ?: results.firstOrNull { norm(it.name) == targetShort } ?: return null
        // El endpoint de búsqueda no trae ingredientes fiables: re-consulta el producto completo
        // por su código para obtener ingredientes/alérgenos.
        if (match.code.isNotBlank()) Off.byBarcode(match.code)?.let { return it to (exact != null) }
        return match to (exact != null)
    }

    private fun norm(s: String?): String {
        val n = Normalizer.normalize((s ?: "").lowercase(), Normalizer.Form.NFD)
        return n.filter { it.category != CharCategory.NON_SPACING_MARK }.trim()
    }
}
