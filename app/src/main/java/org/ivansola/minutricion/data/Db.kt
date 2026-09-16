package org.ivansola.minutricion.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Capa de datos sobre SQLite (equivalente a db.py). Reutiliza el MISMO fichero
 * `nutricion.db`: se empaqueta como asset y se copia al almacenamiento privado
 * en el primer arranque. Así el esquema y los datos son idénticos a la app original.
 */
object Db {
    private lateinit var db: SQLiteDatabase

    val DEFAULT_MEALS = listOf("Desayuno", "Comida", "Merienda", "Cena")

    fun init(context: Context) {
        if (::db.isInitialized) return
        val target = context.getDatabasePath("nutricion.db")
        if (!target.exists()) {
            target.parentFile?.mkdirs()
            copyAsset(context, "nutricion.db", target)
        }
        db = SQLiteDatabase.openDatabase(
            target.path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY
        )
        ensureSchema()
    }

    /**
     * Copia la base entera a Descargas/MiNutricion (copia de seguridad, o para revisarla en el PC).
     * Antes vuelca al fichero principal lo que siga en el diario de escrituras (WAL), para que la
     * copia esté completa. Devuelve dónde ha quedado.
     */
    fun exportCopy(context: Context): String {
        db.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
        val src = File(db.path)
        val name = "MiNutricion_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".db"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MiNutricion")
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("no se pudo crear el archivo en Descargas")
            resolver.openOutputStream(uri).use { out ->
                requireNotNull(out) { "no se pudo escribir en Descargas" }
                src.inputStream().use { it.copyTo(out) }
            }
            return "Descargas/MiNutricion/$name"
        }
        // Android 9 o anterior: Descargas pide permiso; la carpeta propia de la app no
        val out = File(context.getExternalFilesDir(null) ?: error("sin almacenamiento externo"), name)
        src.copyTo(out, overwrite = true)
        return out.path
    }

    private fun copyAsset(context: Context, name: String, target: File) {
        context.assets.open(name).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
    }

    private fun ensureSchema() {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS foods (
                id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE,
                kcal REAL NOT NULL, protein REAL NOT NULL DEFAULT 0,
                carbs REAL NOT NULL DEFAULT 0, fat REAL NOT NULL DEFAULT 0, nutrients TEXT)"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT, day TEXT NOT NULL, meal TEXT NOT NULL,
                name TEXT NOT NULL, grams REAL NOT NULL, kcal REAL NOT NULL, protein REAL NOT NULL,
                carbs REAL NOT NULL, fat REAL NOT NULL, included INTEGER NOT NULL DEFAULT 1,
                nutrients TEXT)"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_entries_day ON entries(day)")
        db.execSQL("CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS weights (day TEXT PRIMARY KEY, kg REAL NOT NULL)")
        addColumn("entries", "included", "INTEGER NOT NULL DEFAULT 1")
        addColumn("entries", "nutrients", "TEXT")
        addColumn("foods", "nutrients", "TEXT")
        addColumn("foods", "user_created", "INTEGER NOT NULL DEFAULT 0")
        addColumn("foods", "category", "TEXT")
        addColumn("foods", "serving", "REAL")
        addColumn("foods", "barcode", "TEXT")
        addColumn("foods", "barcodes", "TEXT")   // todos los códigos de la ficha (varios pesos)
        addColumn("foods", "ingredients", "TEXT")
        addColumn("foods", "allergens", "TEXT")
        addColumn("foods", "categories", "TEXT")
        addColumn("foods", "backfilled", "INTEGER NOT NULL DEFAULT 0")
        addColumn("foods", "components", "TEXT")
    }

    private fun addColumn(table: String, col: String, decl: String) {
        val cols = mutableSetOf<String>()
        db.rawQuery("PRAGMA table_info($table)", null).use { c ->
            val idx = c.getColumnIndex("name")
            while (c.moveToNext()) cols.add(c.getString(idx))
        }
        if (col !in cols) db.execSQL("ALTER TABLE $table ADD COLUMN $col $decl")
    }

    // ---- helpers ----------------------------------------------------------

    private fun parseNutrients(raw: String?): Map<String, Double> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val o = JSONObject(raw)
            buildMap {
                o.keys().forEach { k ->
                    val v = o.opt(k)
                    if (v is Number) put(k, v.toDouble())
                }
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun dumpNutrients(n: Map<String, Double>?): String? =
        if (n.isNullOrEmpty()) null else JSONObject(n as Map<*, *>).toString()

    private fun parseList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val a = org.json.JSONArray(raw)
            List(a.length()) { a.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun dumpList(l: List<String>?): String? =
        if (l.isNullOrEmpty()) null else org.json.JSONArray(l).toString()

    private fun parseComponents(raw: String?): List<FoodComponent> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val a = org.json.JSONArray(raw)
            List(a.length()) { i ->
                val o = a.getJSONObject(i)
                FoodComponent(
                    name = o.optString("name"),
                    kcal = o.optDouble("kcal", 0.0), protein = o.optDouble("protein", 0.0),
                    carbs = o.optDouble("carbs", 0.0), fat = o.optDouble("fat", 0.0),
                    nutrients = parseNutrients(o.optJSONObject("nutrients")?.toString()),
                )
            }.filter { it.name.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun dumpComponents(cs: List<FoodComponent>?): String? {
        if (cs.isNullOrEmpty()) return null
        val arr = org.json.JSONArray()
        cs.forEach { c ->
            arr.put(JSONObject().apply {
                put("name", c.name); put("kcal", c.kcal); put("protein", c.protein)
                put("carbs", c.carbs); put("fat", c.fat)
                if (c.nutrients.isNotEmpty()) put("nutrients", JSONObject(c.nutrients as Map<*, *>))
            })
        }
        return arr.toString()
    }

    private fun Cursor.d(name: String) = getDouble(getColumnIndexOrThrow(name))
    private fun Cursor.s(name: String): String? {
        val i = getColumnIndex(name); return if (i < 0 || isNull(i)) null else getString(i)
    }

    private fun readEntry(c: Cursor) = Entry(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        day = c.getString(c.getColumnIndexOrThrow("day")),
        meal = c.getString(c.getColumnIndexOrThrow("meal")),
        name = c.getString(c.getColumnIndexOrThrow("name")),
        grams = c.d("grams"), kcal = c.d("kcal"), protein = c.d("protein"),
        carbs = c.d("carbs"), fat = c.d("fat"),
        included = c.getInt(c.getColumnIndexOrThrow("included")) != 0,
        nutrients = parseNutrients(c.s("nutrients")),
    )

    // ---- alimentos --------------------------------------------------------

    private fun readFood(c: Cursor) = Food(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        name = c.getString(c.getColumnIndexOrThrow("name")),
        kcal = c.d("kcal"), protein = c.d("protein"), carbs = c.d("carbs"), fat = c.d("fat"),
        nutrients = parseNutrients(c.s("nutrients")),
        components = parseComponents(c.s("components")),
        userCreated = (c.getColumnIndex("user_created").let { if (it < 0) 0 else c.getInt(it) }) != 0,
        category = c.s("category"),
        serving = c.getColumnIndex("serving").let { if (it < 0 || c.isNull(it)) null else c.getDouble(it) },
        barcode = c.s("barcode"),
        barcodes = parseList(c.s("barcodes")),
        ingredients = c.s("ingredients") ?: "",
        allergens = parseList(c.s("allergens")),
        categories = parseList(c.s("categories")),
    )

    fun listFoods(): List<Food> {
        val out = ArrayList<Food>()
        db.rawQuery("SELECT * FROM foods ORDER BY name COLLATE NOCASE", null).use { c ->
            while (c.moveToNext()) out.add(readFood(c))
        }
        return out
    }

    /** Inserta o actualiza un alimento en la biblioteca (clave: nombre único).
     *  Los metadatos vacíos NO sobrescriben los existentes (COALESCE + NULLIF). */
    fun upsertFood(food: Food, userCreated: Boolean = false) {
        db.execSQL(
            """INSERT INTO foods
                 (name, kcal, protein, carbs, fat, nutrients, user_created, category, serving,
                  barcode, barcodes, ingredients, allergens, categories, components)
               VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
               ON CONFLICT(name) DO UPDATE SET kcal=excluded.kcal, protein=excluded.protein,
                 carbs=excluded.carbs, fat=excluded.fat,
                 nutrients=COALESCE(excluded.nutrients, foods.nutrients),
                 category=COALESCE(excluded.category, foods.category),
                 serving=COALESCE(excluded.serving, foods.serving),
                 barcode=COALESCE(excluded.barcode, foods.barcode),
                 barcodes=COALESCE(excluded.barcodes, foods.barcodes),
                 ingredients=COALESCE(excluded.ingredients, foods.ingredients),
                 allergens=COALESCE(excluded.allergens, foods.allergens),
                 categories=COALESCE(excluded.categories, foods.categories),
                 components=COALESCE(excluded.components, foods.components)""",
            arrayOf(food.name, food.kcal, food.protein, food.carbs, food.fat,
                dumpNutrients(food.nutrients), if (userCreated) 1 else 0, food.category, food.serving,
                food.barcode, dumpList(food.barcodes), food.ingredients.ifBlank { null },
                dumpList(food.allergens), dumpList(food.categories),
                dumpComponents(food.components))
        )
    }

    /** Busca un alimento de la biblioteca por su código de barras (registro local). Casa tanto el
     *  código principal como cualquiera de la lista `barcodes` (varios pesos comparten ficha). */
    fun foodByBarcode(code: String): Food? = foodsByBarcode(code).firstOrNull()

    /**
     * TODOS los alimentos que llevan ese código. Normalmente es uno, pero la biblioteca arrastra
     * casos en que el mismo EAN quedó en dos fichas (el mismo producto guardado por dos fuentes con
     * distinta redacción, o un código mal asignado). Devolverlos todos permite PREGUNTAR al usuario
     * cuál es, en vez de quedarnos con el primero que salga —que dependía del orden interno de
     * SQLite y podía ser el equivocado sin que se notara.
     */
    fun foodsByBarcode(code: String): List<Food> {
        val out = ArrayList<Food>()
        db.rawQuery(
            "SELECT * FROM foods WHERE barcode=? OR barcodes LIKE ? ORDER BY user_created DESC, id",
            arrayOf(code, "%\"$code\"%")
        ).use { c ->
            while (c.moveToNext()) out.add(readFood(c))
        }
        return out
    }

    /** Busca un alimento de la biblioteca por su nombre exacto. */
    fun foodByName(name: String): Food? {
        db.rawQuery("SELECT * FROM foods WHERE name=? LIMIT 1", arrayOf(name)).use { c ->
            if (!c.moveToNext()) return null
            return readFood(c)
        }
    }

    /**
     * Añade `code` a los códigos de un alimento YA guardado (unión, sin duplicar). Sirve para los
     * alimentos de la biblioteca que no traían EAN: al escanearlos una vez, quedan asociados y el
     * siguiente escaneo los encuentra directamente por código.
     */
    fun attachBarcode(food: Food, code: String) {
        val c = code.trim()
        if (c.isEmpty() || food.id <= 0) return
        val codes = (listOfNotNull(food.barcode) + food.barcodes).filter { it.isNotBlank() }.distinct()
        if (c in codes) return
        val union = codes + c
        db.execSQL("UPDATE foods SET barcode=?, barcodes=? WHERE id=?",
            arrayOf<Any?>(union.first(), dumpList(union), food.id))
    }

    // Palabras de envase/formato: NO identifican el producto (dos cosas distintas comparten
    // "bolsa"/"pack"), así que no cuentan como coincidencia de nombre.
    private val PACKAGING = setOf(
        "bolsa", "bolsas", "bandeja", "botella", "brik", "lata", "latas", "pack", "unidades",
        "unidad", "paquete", "tarro", "frasco", "garrafa", "envase", "caja", "vaso", "sobre",
        "sobres", "tubo", "botellin", "sabor", "sabores",
    )

    private fun tokens(name: String): Set<String> =
        java.text.Normalizer.normalize(name.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .split(' ').filter { it.length >= 4 && it !in PACKAGING }.toSet()

    /** Ingredientes comparables: minúsculas, sin tildes ni signos, espacios simples. */
    private fun recipe(ingredients: String): String =
        java.text.Normalizer.normalize(ingredients.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    private fun close(a: Double, b: Double, absTol: Double = 0.5, rel: Double = 0.10): Boolean =
        Math.abs(a - b) <= maxOf(absTol, rel * maxOf(Math.abs(a), Math.abs(b)))

    /**
     * CANDIDATOS ya guardados que podrían ser el mismo alimento aunque el NOMBRE no sea idéntico
     * (p. ej. se escanea un producto que el scraper guardó con otra redacción y sin EAN). Exige
     * macros equivalentes y, además, una de dos:
     *  - >=2 palabras distintivas en común con solape (Jaccard) >= 0.6, o
     *  - la MISMA lista de ingredientes. Esto cubre los productos a los que Open Food Facts les ha
     *    cambiado el nombre: el EAN 8015057003760 (gnocchi Hacendado) pasó a llamarse "ñordos" en
     *    OFF y, al escanearlo, se guardó como alimento nuevo junto al "Gnocchi" que ya existía.
     * Sin energía (0 kcal) no se arriesga. Van ordenados de más a menos parecido.
     *
     * OJO: son solo PISTAS, las tiene que elegir el USUARIO antes de asociarles un código. Ni el
     * nombre ni los macros pueden distinguir dos formatos del mismo producto ("Vodka 35 cl" vs
     * "70 cl", "Whisky 70 cl" vs "1 l"), que tienen EAN DISTINTO: asociarlo automáticamente
     * colgaría el código en la ficha equivocada y el fallo sería silencioso.
     */
    fun findSimilarFoods(
        name: String, kcal: Double, protein: Double, carbs: Double, fat: Double, limit: Int = 4,
        ingredients: String = "",
    ): List<Food> {
        if (kcal <= 0) return emptyList()
        val mine = tokens(name)
        // una lista corta ("Leche", "Azúcar") no identifica un producto
        val myRecipe = recipe(ingredients).takeIf { it.length >= 30 }
        if (mine.size < 2 && myRecipe == null) return emptyList()
        val scored = ArrayList<Pair<Double, Food>>()
        db.rawQuery("SELECT * FROM foods WHERE kcal BETWEEN ? AND ?",
            arrayOf((kcal * 0.9).toString(), (kcal * 1.1).toString())).use { c ->
            while (c.moveToNext()) {
                val f = readFood(c)
                val theirs = tokens(f.name)
                val common = mine intersect theirs
                val union = mine union theirs
                val jaccard = if (union.isEmpty()) 0.0 else common.size.toDouble() / union.size
                val sameRecipe = myRecipe != null && recipe(f.ingredients) == myRecipe
                val sameName = mine.size >= 2 && common.size >= 2 && jaccard >= 0.6
                if (!sameName && !sameRecipe) continue
                if (!close(protein, f.protein) || !close(carbs, f.carbs) || !close(fat, f.fat)) continue
                scored.add((if (sameRecipe) maxOf(jaccard, 0.99) else jaccard) to f)
            }
        }
        return scored.sortedByDescending { it.first }.take(limit).map { it.second }
    }

    /** Alimentos aún no procesados por el backfill (para enriquecerlos desde OFF). */
    fun foodsNeedingBackfill(): List<Food> {
        val out = ArrayList<Food>()
        db.rawQuery("SELECT * FROM foods WHERE COALESCE(backfilled,0)=0 ORDER BY id", null).use { c ->
            while (c.moveToNext()) out.add(readFood(c))
        }
        return out
    }

    /** Marca un alimento como ya intentado por el backfill (no reintentar cada arranque). */
    fun setFoodBackfilled(name: String) =
        db.execSQL("UPDATE foods SET backfilled=1 WHERE name=?", arrayOf(name))

    fun userCreatedNames(): Set<String> {
        val out = HashSet<String>()
        db.rawQuery("SELECT name FROM foods WHERE user_created=1", null).use { c ->
            while (c.moveToNext()) out.add(c.getString(0))
        }
        return out
    }

    fun deleteFood(id: Long) = db.execSQL("DELETE FROM foods WHERE id=?", arrayOf(id))

    /**
     * Guarda los cambios de un alimento que se ha editado, RENOMBRÁNDOLO si su nombre cambió.
     *
     * No basta con `upsertFood`: su clave es el nombre, así que con un nombre nuevo creaba una
     * ficha aparte y dejaba la antigua (y el diario seguía apuntando al nombre viejo). Eso es lo
     * que dejaba, por ejemplo, "ñordos" y "Gnocchi" como dos entradas del mismo producto.
     *
     * Todo lo que referencia un alimento POR NOMBRE se arrastra al nuevo, en una transacción:
     *  - la propia ficha;
     *  - los registros del diario, incluidos los de componente de un surtido ("X — Componente");
     *  - favoritos y la última cantidad usada.
     *
     * Si el nombre nuevo YA es de otro alimento, los dos se FUSIONAN en esa ficha (se conservan sus
     * códigos de barras sumados): es justo lo que hace falta para juntar un duplicado.
     */
    fun saveEditedFood(oldName: String, food: Food) {
        val newName = food.name
        if (oldName == newName) { upsertFood(food); return }
        db.beginTransaction()
        try {
            val old = foodByName(oldName)
            val target = foodByName(newName)
            if (target == null) {
                db.execSQL("UPDATE foods SET name=? WHERE name=?", arrayOf(newName, oldName))
            } else {
                // fusión: la ficha destino recibe los códigos de la antigua, y la antigua se borra
                old?.let { o ->
                    // se relee la ficha en cada código: attachBarcode une con la copia que recibe,
                    // y con una copia fija cada código borraría el añadido justo antes
                    (listOfNotNull(o.barcode) + o.barcodes).forEach { code ->
                        foodByName(newName)?.let { attachBarcode(it, code) }
                    }
                    if (o.userCreated) {
                        db.execSQL("UPDATE foods SET user_created=1 WHERE id=?", arrayOf(target.id))
                    }
                }
                db.execSQL("DELETE FROM foods WHERE name=?", arrayOf(oldName))
            }
            upsertFood(food)   // aplica el resto de campos editados sobre la ficha ya renombrada

            db.execSQL("UPDATE entries SET name=? WHERE name=?", arrayOf(newName, oldName))
            // registros de componente: "Viejo — Componente" -> "Nuevo — Componente". Se compara
            // por prefijo con substr y no con LIKE, porque un nombre puede contener % o _.
            val oldPrefix = "$oldName — "
            val oldLen = oldPrefix.codePointCount(0, oldPrefix.length)   // substr cuenta caracteres
            db.execSQL(
                "UPDATE entries SET name = ? || substr(name, ?) WHERE substr(name, 1, ?) = ?",
                arrayOf<Any>("$newName — ", oldLen + 1, oldLen, oldPrefix),
            )

            val favs = getFavorites()
            if (oldName in favs) {
                val updated = (favs - oldName).let { if (newName in it) it else it + newName }
                setSetting("favorites", org.json.JSONArray(updated).toString())
            }
            getSetting("lastqty:$oldName")?.let { qty ->
                if (getSetting("lastqty:$newName") == null) setSetting("lastqty:$newName", qty)
                db.execSQL("DELETE FROM settings WHERE key=?", arrayOf("lastqty:$oldName"))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ---- favoritos (lista de nombres, guardada como ajuste JSON) ----------

    fun getFavorites(): List<String> {
        val raw = getSetting("favorites") ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(raw)
            List(arr.length()) { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun isFavorite(name: String) = name in getFavorites()

    /** Alterna favorito; devuelve el nuevo estado. */
    fun toggleFavorite(name: String): Boolean {
        val favs = getFavorites().toMutableList()
        val now: Boolean
        if (name in favs) { favs.remove(name); now = false } else { favs.add(name); now = true }
        setSetting("favorites", org.json.JSONArray(favs).toString())
        return now
    }

    // ---- registros del diario --------------------------------------------

    fun entriesForDay(day: String): List<Entry> {
        val out = ArrayList<Entry>()
        db.rawQuery("SELECT * FROM entries WHERE day=? ORDER BY id", arrayOf(day)).use { c ->
            while (c.moveToNext()) out.add(readEntry(c))
        }
        return out
    }

    /** Comida registrada (un día + una comida) con todos sus alimentos, para repetirla entera. */
    data class RecentMeal(val day: String, val meal: String, val entries: List<Entry>)

    /** Comidas recientes (cada par día+comida) con sus alimentos, la más reciente primero. */
    fun recentMeals(limit: Int = 8): List<RecentMeal> {
        val keys = ArrayList<Pair<String, String>>()
        db.rawQuery(
            "SELECT day, meal, MAX(id) AS mid FROM entries GROUP BY day, meal ORDER BY mid DESC LIMIT ?",
            arrayOf(limit.toString()),
        ).use { c -> while (c.moveToNext()) keys.add(c.getString(0) to c.getString(1)) }
        return keys.map { (day, meal) ->
            val ents = ArrayList<Entry>()
            db.rawQuery("SELECT * FROM entries WHERE day=? AND meal=? ORDER BY id", arrayOf(day, meal))
                .use { c -> while (c.moveToNext()) ents.add(readEntry(c)) }
            RecentMeal(day, meal, ents)
        }
    }

    /** Alimentos usados recientemente (distintos por nombre) con su última cantidad. */
    fun recentFoods(limit: Int = 25): List<Entry> {
        val out = ArrayList<Entry>()
        db.rawQuery(
            """SELECT *, MAX(id) AS mid FROM entries GROUP BY name ORDER BY mid DESC LIMIT ?""",
            arrayOf(limit.toString()),
        ).use { c -> while (c.moveToNext()) out.add(readEntry(c)) }
        return out
    }

    fun addEntry(e: Entry) {
        db.execSQL(
            "INSERT INTO entries (day, meal, name, grams, kcal, protein, carbs, fat, nutrients) VALUES (?,?,?,?,?,?,?,?,?)",
            arrayOf(e.day, e.meal, e.name, e.grams, e.kcal, e.protein, e.carbs, e.fat, dumpNutrients(e.nutrients))
        )
        // Recuerda la última cantidad usada de este alimento (para prerrellenarla la próxima vez).
        setSetting("lastqty:${e.name}", e.grams.toString())
    }

    /** Última cantidad (g/ml) con la que se registró este alimento, o null si nunca. */
    fun lastQty(name: String): Double? = getSetting("lastqty:$name")?.toDoubleOrNull()?.takeIf { it > 0 }

    fun deleteEntry(id: Long) = db.execSQL("DELETE FROM entries WHERE id=?", arrayOf(id))

    fun deleteEntriesForMeal(day: String, meal: String) =
        db.execSQL("DELETE FROM entries WHERE day=? AND meal=?", arrayOf(day, meal))

    fun setEntryIncluded(id: Long, value: Boolean) =
        db.execSQL("UPDATE entries SET included=? WHERE id=?", arrayOf(if (value) 1 else 0, id))

    fun updateEntryGrams(id: Long, grams: Double) =
        db.execSQL("UPDATE entries SET grams=? WHERE id=?", arrayOf(grams, id))

    fun updateEntryMealGrams(id: Long, meal: String, grams: Double) =
        db.execSQL("UPDATE entries SET meal=?, grams=? WHERE id=?", arrayOf(meal, grams, id))

    /** {día -> Macros} entre start y end (incl.), solo included=1 y comidas visibles. */
    fun dailyTotals(start: String, end: String, meals: List<String>): Map<String, Macros> {
        if (meals.isEmpty()) return emptyMap()
        val ph = meals.joinToString(",") { "?" }
        val sql = """SELECT day,
              SUM(kcal*grams/100.0) k, SUM(protein*grams/100.0) p,
              SUM(carbs*grams/100.0) c, SUM(fat*grams/100.0) f
            FROM entries WHERE day BETWEEN ? AND ? AND included=1 AND meal IN ($ph) GROUP BY day"""
        val args = (listOf(start, end) + meals).toTypedArray()
        val out = HashMap<String, Macros>()
        db.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) out[c.getString(0)] =
                Macros(c.getDouble(1), c.getDouble(2), c.getDouble(3), c.getDouble(4))
        }
        return out
    }

    fun loggedDays(start: String, end: String): Set<String> {
        val out = HashSet<String>()
        db.rawQuery("SELECT DISTINCT day FROM entries WHERE day BETWEEN ? AND ?", arrayOf(start, end)).use { c ->
            while (c.moveToNext()) out.add(c.getString(0))
        }
        return out
    }

    // ---- ajustes / perfil -------------------------------------------------

    fun getSetting(key: String, default: String? = null): String? {
        db.rawQuery("SELECT value FROM settings WHERE key=?", arrayOf(key)).use { c ->
            return if (c.moveToNext()) c.getString(0) else default
        }
    }

    fun setSetting(key: String, value: String) = db.execSQL(
        "INSERT INTO settings (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value=excluded.value",
        arrayOf(key, value)
    )

    fun getMeals(): List<String> {
        val raw = getSetting("meals") ?: return DEFAULT_MEALS
        return try {
            val arr = org.json.JSONArray(raw)
            if (arr.length() == 0) DEFAULT_MEALS
            else List(arr.length()) { arr.getString(it) }
        } catch (e: Exception) {
            DEFAULT_MEALS
        }
    }

    fun setMeals(meals: List<String>) = setSetting("meals", org.json.JSONArray(meals).toString())

    /** Renombra una comida y actualiza los registros existentes que la usan. */
    fun renameMeal(old: String, new: String) =
        db.execSQL("UPDATE entries SET meal=? WHERE meal=?", arrayOf(new, old))

    fun countMealEntries(meal: String): Int {
        db.rawQuery("SELECT COUNT(*) FROM entries WHERE meal=?", arrayOf(meal)).use { c ->
            return if (c.moveToNext()) c.getInt(0) else 0
        }
    }

    fun getProfile(): Profile? {
        val rows = HashMap<String, String>()
        db.rawQuery("SELECT key, value FROM settings WHERE key LIKE 'profile_%'", null).use { c ->
            while (c.moveToNext()) rows[c.getString(0)] = c.getString(1)
        }
        if (rows.isEmpty()) return null
        fun num(x: String?): Double? = x?.toDoubleOrNull()
        return Profile(
            sex = rows["profile_sex"], age = num(rows["profile_age"]),
            height = num(rows["profile_height"]), weight = num(rows["profile_weight"]),
            activity = rows["profile_activity"], goal = rows["profile_goal"],
            mode = rows["profile_mode"], manualKcal = num(rows["profile_manual_kcal"]),
            targetWeight = num(rows["profile_target_weight"]),
        )
    }

    /** Guarda el perfil como ajustes `profile_*` (solo escribe los campos no nulos). */
    fun saveProfile(p: Profile) {
        p.sex?.let { setSetting("profile_sex", it) }
        p.age?.let { setSetting("profile_age", it.toString()) }
        p.height?.let { setSetting("profile_height", it.toString()) }
        p.weight?.let { setSetting("profile_weight", it.toString()) }
        p.activity?.let { setSetting("profile_activity", it) }
        p.goal?.let { setSetting("profile_goal", it) }
        p.mode?.let { setSetting("profile_mode", it) }
        p.manualKcal?.let { setSetting("profile_manual_kcal", it.toString()) }
        p.targetWeight?.let { setSetting("profile_target_weight", it.toString()) }
    }

    // ---- peso -------------------------------------------------------------

    fun getWeights(start: String? = null, end: String? = null): List<Pair<String, Double>> {
        val out = ArrayList<Pair<String, Double>>()
        val (sql, args) = if (start != null && end != null)
            "SELECT day, kg FROM weights WHERE day BETWEEN ? AND ? ORDER BY day" to arrayOf(start, end)
        else "SELECT day, kg FROM weights ORDER BY day" to emptyArray()
        db.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) out.add(c.getString(0) to c.getDouble(1))
        }
        return out
    }

    fun latestWeight(): Pair<String, Double>? {
        db.rawQuery("SELECT day, kg FROM weights ORDER BY day DESC LIMIT 1", null).use { c ->
            return if (c.moveToNext()) c.getString(0) to c.getDouble(1) else null
        }
    }

    fun setWeight(day: String, kg: Double) = db.execSQL(
        "INSERT INTO weights (day, kg) VALUES (?, ?) ON CONFLICT(day) DO UPDATE SET kg=excluded.kg",
        arrayOf(day, kg)
    )

    fun deleteWeight(day: String) = db.execSQL("DELETE FROM weights WHERE day=?", arrayOf(day))
}
