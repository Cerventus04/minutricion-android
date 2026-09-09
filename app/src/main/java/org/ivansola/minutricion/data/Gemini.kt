package org.ivansola.minutricion.data

import android.util.Base64
import androidx.compose.runtime.Immutable
import org.ivansola.minutricion.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Resultado de que Gemini lea 1-3 fotos de un producto (etiqueta, tabla nutricional, código de
 * barras). Valores POR 100 g, igual que [Food]/[OffProduct]. `found = false` si Gemini no pudo
 * identificar el producto en ninguna foto (fotos borrosas, sin tabla nutricional visible…).
 */
@Immutable
data class GeminiFoodResult(
    val found: Boolean,
    val name: String = "",
    val category: String? = null,
    val ean: String? = null,
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
    val nutrients: Map<String, Double> = emptyMap(),
    /** true si los valores son POR RACIÓN/DOSIS (complementos) en vez de por 100 g/ml. */
    val perServing: Boolean = false,
    /** Peso de esa ración en g/ml, si la etiqueta lo indica. */
    val servingG: Double? = null,
    /** Cómo llama la etiqueta a la ración ("1 cápsula"…). */
    val servingName: String? = null,
) {
    fun toFood() = Food(
        id = 0, name = name.ifBlank { "Alimento" }, kcal = kcal, protein = protein,
        carbs = carbs, fat = fat, nutrients = nutrients, userCreated = true,
        category = category, barcode = ean,
    )
}

/**
 * Cliente de la API de Gemini (gratis dentro de la cuota, https://aistudio.google.com/apikey):
 * manda las fotos que el usuario tomó de un producto y pide que rellene nombre, categoría, EAN y
 * tabla nutricional por 100 g en un único JSON. Pensado para productos que NO están ni en la
 * biblioteca local ni en Open Food Facts (si estuvieran, mejor usar esos datos ya verificados).
 */
object Gemini {
    // Google retira modelos con el tiempo: si un día devuelve 404 ("no longer available"), el propio
    // mensaje de error indica cuál es el sustituto.
    /**
     * Modelos por orden de preferencia. El plan gratuito limita a 20 peticiones AL DÍA por modelo
     * (lo dice la propia API: quotaId GenerateRequestsPerDayPerProjectPerModel-FreeTier), y como
     * cada análisis gasta una por foto, es fácil agotarlo. Si el principal responde 429 se pasa al
     * siguiente en vez de dejar al usuario sin poder registrar nada.
     */
    private val MODELS = listOf("gemini-3.6-flash", "gemini-3.5-flash")

    /**
     * Claves disponibles. La segunda es de otro proyecto de Google, así que tiene su PROPIA cuota
     * diaria: cuando la primera se agota en todos los modelos, la app sigue funcionando con ella.
     */
    private val KEYS: List<String>
        get() = listOf(BuildConfig.GEMINI_API_KEY, BuildConfig.GEMINI_API_KEY_2)
            .filter { it.isNotBlank() }

    /** Combinaciones a probar, en orden: los modelos de la clave 1 y luego los de la clave 2. */
    private val CHAIN: List<Pair<String, String>>
        get() = KEYS.flatMap { key -> MODELS.map { model -> key to model } }

    /**
     * Primera combinación con cuota, recordada durante la sesión: lo agotado seguirá estándolo el
     * resto del día, y sin esto cada foto malgastaría llamadas en volver a descubrirlo.
     */
    @Volatile private var attemptFrom = 0
    private const val CONNECT_TIMEOUT = 15000
    /** Leer la respuesta puede tardar: son 3 fotos y el modelo razona sobre ellas. */
    private const val READ_TIMEOUT = 90000
    /** Intentos totales ante fallo de red o error temporal del servidor. */
    private const val ATTEMPTS = 3

    /**
     * Claves de nutriente que puede rellenar, con la unidad en que se le piden. Las unidades son
     * las MISMAS que muestran los campos del formulario de "Crear Alimento", porque los valores se
     * vuelcan ahí tal cual. Incluye vitaminas y minerales: los complementos alimenticios (omega 3,
     * multivitamínicos…) no traen una tabla nutricional clásica, sino una de micronutrientes por
     * cápsula/dosis, y sin estas claves no habría dónde guardar lo único que aportan.
     */
    private val NUTRIENT_KEYS = listOf(
        "sat_fat" to "g", "trans_fat" to "g", "sugars" to "g", "added_sugars" to "g",
        "fiber" to "g", "salt" to "g", "sodium" to "mg", "cholesterol" to "mg",
        "vit_a" to "mcg", "vit_c" to "mg", "vit_d" to "mcg", "vit_e" to "mg", "vit_k" to "mcg",
        "b1" to "mg", "b2" to "mg", "b3" to "mg", "b5" to "mg", "b6" to "mg", "b12" to "mcg",
        "folate" to "mcg", "biotin" to "mcg",
        "calcium" to "mg", "iron" to "mg", "magnesium" to "mg", "phosphorus" to "mg",
        "potassium" to "mg", "zinc" to "mg", "selenium" to "mcg", "copper" to "mcg",
        "manganese" to "mg", "iodine" to "mcg",
    )

    /** Basta con que haya UNA clave configurada (la principal puede faltar y quedar solo la 2ª). */
    val available: Boolean get() = KEYS.isNotEmpty()

    /** Resultado de la consulta: o los datos leídos, o el motivo del fallo (para poder enseñarlo). */
    sealed class Outcome {
        data class Ok(val food: GeminiFoodResult) : Outcome()
        data class Error(val message: String) : Outcome()
        /** Cuota agotada en ESE modelo: interno, para poder pasar al siguiente. */
        internal data class Quota(val message: String) : Outcome()
    }

    /**
     * Qué se le pidió al usuario en cada foto. Como las fotos se toman de una en una y en un orden
     * conocido, se le puede decir al modelo qué está mirando: mejora la lectura y, sobre todo,
     * permite mandar cada foto EN CUANTO se hace, sin esperar a tener las tres.
     */
    enum class Shot(val hint: String) {
        EAN("Esta foto es del CÓDIGO DE BARRAS del envase: lo que importa es el número EAN."),
        NUTRITION("Esta foto es de la INFORMACIÓN NUTRICIONAL: la tabla de valores o, en un " +
            "complemento alimenticio, la lista de nutrientes por dosis con su %VRN."),
        FRONT("Esta foto es del FRENTE del envase: lo que importa es el nombre del producto, la " +
            "marca y de qué tipo de alimento se trata."),
    }

    /** Analiza UNA foto. Se llama nada más tomarla, mientras el usuario hace la siguiente. */
    fun analyzeImage(image: ByteArray, shot: Shot): Outcome {
        if (!available) return Outcome.Error("No hay clave de Gemini configurada")
        val attempts = CHAIN
        var last: Outcome = Outcome.Error("Sin modelos disponibles")
        for (i in attemptFrom until attempts.size) {
            val (key, model) = attempts[i]
            val res = analyzeWith(key, model, image, shot)
            if (res !is Outcome.Quota) return res
            if (i + 1 > attemptFrom) attemptFrom = i + 1   // esa combinación no tiene cuota hoy
            last = Outcome.Error(
                if (i == attempts.lastIndex) "Se ha agotado la cuota diaria de todas las claves"
                else res.message
            )
        }
        return last
    }

    private fun analyzeWith(key: String, model: String, image: ByteArray, shot: Shot): Outcome {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent" +
            "?key=$key"
        val parts = JSONArray().apply {
            put(JSONObject().put("text", prompt(shot)))
            put(JSONObject().put(
                "inline_data",
                JSONObject()
                    .put("mime_type", "image/jpeg")
                    .put("data", Base64.encodeToString(image, Base64.NO_WRAP)),
            ))
        }
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", parts)))
            .put("generationConfig", JSONObject()
                .put("response_mime_type", "application/json")
                // leer una etiqueta no es tarea creativa: sin esto va a 1.0 y puede "rellenar
                // huecos" con lo que le parezca plausible, que es justo lo que no queremos.
                .put("temperature", 0))
        val res = post(url, body)
        if (res is PostResult.Fail) {
            return if (res.code == 429) Outcome.Quota(res.message) else Outcome.Error(res.message)
        }
        val json = (res as PostResult.Body).json
        return parse(json)?.let { Outcome.Ok(it) }
            ?: Outcome.Error("Respuesta de Gemini no reconocible")
    }

    /**
     * Funde lo leído en cada foto en un único producto. No vale con quedarse con el primero: el
     * EAN suele salir de la foto 1, la nutrición de la 2 y el nombre de la 3.
     *
     * Los valores nutricionales se toman TODOS de la misma foto (la que traiga más datos) en vez de
     * mezclarlos: cada foto dice si sus cifras son por 100 g o por dosis, y combinar cifras con
     * bases distintas daría un alimento sin sentido.
     */
    fun merge(results: List<GeminiFoodResult>): GeminiFoodResult {
        val ok = results.filter { it.found }
        if (ok.isEmpty()) return GeminiFoodResult(found = false)
        // la foto con más datos nutricionales manda en macros/nutrientes/ración
        val best = ok.maxByOrNull { r ->
            r.nutrients.size + listOf(r.kcal, r.protein, r.carbs, r.fat).count { it > 0 }
        }!!
        return best.copy(
            // nombre y categoría: la última foto que los tenga (el frente es la más fiable)
            name = ok.lastOrNull { it.name.isNotBlank() }?.name.orEmpty(),
            category = ok.lastOrNull { it.category != null }?.category,
            // EAN: el primero que aparezca (la foto 1 es justo la del código)
            ean = ok.firstOrNull { it.ean != null }?.ean,
        )
    }

    // NOTA: aquí hubo un `response_schema` (v8.2) y hubo que quitarlo. Sobre el papel garantizaba
    // tipos y categorías válidas, pero medido con ESTE prompt el modelo se limitaba a cumplirlo por
    // lo mínimo: como solo "found" era obligatorio y el resto admitía null, devolvía 48 tokens con
    // el nombre y nada más — ni EAN ni nutrientes. Sin esquema, la misma foto da el EAN y 19
    // nutrientes. Si se reintenta algún día, hay que marcar los campos como obligatorios y
    // comprobarlo con el prompt real, no con uno de prueba.

    private fun prompt(shot: Shot): String {
        val cats = FoodCategories.ALL.joinToString(", ") { it.label }
        val nutr = NUTRIENT_KEYS.joinToString(", ") { "\"${it.first}\": ${it.second}" }
        return """
            Lee esta foto de un producto alimenticio o complemento alimenticio y
            devuelve SOLO un JSON, sin texto adicional ni markdown.
            ${shot.hint} Aun así, rellena TODO lo que se vea en ella, no solo eso.
            Formato:
            {
              "found": true|false,
              "name": "nombre del producto con la marca, o null si no se ve",
              "category": "una de estas categorías EXACTAS: $cats, o null",
              "ean": "código de barras de 8, 12 o 13 dígitos, o null",
              "basis": "100g" si los valores son por 100 g/ml, o "serving" si son por ración/dosis/cápsula,
              "serving_g": peso en gramos (o ml) de esa ración/dosis si la etiqueta lo indica, o null,
              "serving_name": "cómo llama la etiqueta a la ración (p. ej. '1 cápsula', '2 comprimidos'), o null",
              "kcal": numero, o null,
              "protein": numero en gramos, o null,
              "carbs": numero en gramos, o null,
              "fat": numero en gramos, o null,
              "nutrients": {$nutr}
            }
            REGLAS:
            - Pon "found": true si reconoces CUALQUIER dato útil, aunque solo veas la tabla, o solo
              el nombre, o solo el código de barras. Rellena lo que veas y pon null en lo demás.
            - Pon "found": false SOLO si la foto no muestra ningún producto alimenticio.
            - Los COMPLEMENTOS (omega 3, multivitamínicos, minerales…) no traen tabla nutricional
              clásica sino una lista de nutrientes por cápsula/dosis con su %VRN: en ese caso pon
              "basis": "serving", deja kcal/protein/carbs/fat a null si no aparecen, y rellena en
              "nutrients" lo que sí figure (vitamina E, omega 3 no cabe: omítelo).
            - NO conviertas tú los valores: dalos tal como vienen en la etiqueta e indica en
              "basis" a qué se refieren. Ignora la columna de %VRN.
            - En "nutrients" usa EXACTAMENTE las unidades indicadas arriba (convierte si la etiqueta
              usa otra) e incluye solo las claves que veas; omite las demás.
            - No inventes ningún valor que no aparezca en las fotos: usa null.
        """.trimIndent()
    }

    private fun parse(json: JSONObject): GeminiFoodResult? {
        val text = json.optJSONArray("candidates")
            ?.optJSONObject(0)?.optJSONObject("content")
            ?.optJSONArray("parts")?.optJSONObject(0)
            ?.optString("text") ?: return null
        val r = try { JSONObject(text) } catch (e: Exception) { return null }
        if (!r.optBoolean("found", false)) return GeminiFoodResult(found = false)
        val nutrients = HashMap<String, Double>()
        r.optJSONObject("nutrients")?.let { n ->
            for ((key, _) in NUTRIENT_KEYS) {
                val v = n.opt(key)
                if (v is Number) nutrients[key] = v.toDouble()
            }
        }
        // OJO: `optString` de un JSON null devuelve la CADENA "null", no vacío -> hay que filtrarla
        // en todos los campos de texto o acabaríamos con un alimento llamado literalmente "null".
        fun str(key: String) = r.optString(key).trim().takeIf { it.isNotBlank() && it != "null" }
        val ean = str("ean")?.takeIf { it.all(Char::isDigit) }
        // el modelo no siempre respeta la lista de categorías (devuelve p. ej. "Complemento
        // alimenticio"): si no es una de las nuestras se ignora, o el icono saldría vacío.
        val category = str("category")?.takeIf { c -> FoodCategories.ALL.any { it.label == c } }
        return GeminiFoodResult(
            found = true,
            name = str("name").orEmpty(),
            category = category,
            ean = ean,
            kcal = r.optDouble("kcal", 0.0).let { if (it.isNaN()) 0.0 else it },
            protein = r.optDouble("protein", 0.0).let { if (it.isNaN()) 0.0 else it },
            carbs = r.optDouble("carbs", 0.0).let { if (it.isNaN()) 0.0 else it },
            fat = r.optDouble("fat", 0.0).let { if (it.isNaN()) 0.0 else it },
            nutrients = nutrients,
            perServing = str("basis").equals("serving", ignoreCase = true),
            servingG = r.optDouble("serving_g", Double.NaN).takeIf { !it.isNaN() && it > 0 },
            servingName = str("serving_name"),
        )
    }

    private sealed class PostResult {
        data class Body(val json: JSONObject) : PostResult()
        /** `retryable`: fallo pasajero (socket cortado, timeout, 5xx) -> merece reintento. */
        data class Fail(val message: String, val retryable: Boolean = false, val code: Int = 0) : PostResult()
    }

    /**
     * Reintenta los fallos pasajeros. Android reutiliza conexiones del pool y, si el servidor cerró
     * la suya mientras tanto, la primera escritura muere con un error de socket: por eso "volver a
     * darle al botón" funcionaba. Ahora se reintenta solo, con una espera creciente.
     */
    private fun post(urlStr: String, body: JSONObject): PostResult {
        var last: PostResult.Fail = PostResult.Fail("sin respuesta")
        repeat(ATTEMPTS) { i ->
            if (i > 0) Thread.sleep(600L * i)
            when (val r = postOnce(urlStr, body)) {
                is PostResult.Body -> return r
                is PostResult.Fail -> { if (!r.retryable) return r; last = r }
            }
        }
        return PostResult.Fail("${last.message} (tras $ATTEMPTS intentos)")
    }

    private fun postOnce(urlStr: String, body: JSONObject): PostResult = try {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            doOutput = true
            setFixedLengthStreamingMode(0)   // se ajusta abajo con el tamaño real
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        val payload = body.toString().toByteArray(Charsets.UTF_8)
        conn.setFixedLengthStreamingMode(payload.size)   // evita bufferar todo en memoria
        conn.outputStream.use { it.write(payload) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.readText().orEmpty()
        if (code in 200..299) PostResult.Body(JSONObject(text))
        else {
            // el cuerpo de error de Google trae {"error":{"message":"..."}}: se enseña tal cual,
            // que es lo que dice de verdad qué ha fallado (clave, modelo retirado, tamaño…).
            val msg = try { JSONObject(text).optJSONObject("error")?.optString("message") } catch (e: Exception) { null }
            // 5xx = problema del servidor, suele ir bien al repetir. El 429 NO se reintenta: en el
            // plan gratuito es la cuota del día, y repetir solo retrasa el cambio a otro modelo.
            PostResult.Fail("Error $code: ${msg ?: text.take(160).ifBlank { "sin detalle" }}",
                retryable = code >= 500, code = code)
        }
    } catch (e: Exception) {
        PostResult.Fail("${e.javaClass.simpleName}: ${e.message ?: "fallo de red"}", retryable = true)
    }
}
