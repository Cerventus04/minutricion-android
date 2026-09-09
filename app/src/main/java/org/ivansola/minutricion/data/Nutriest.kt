package org.ivansola.minutricion.data

import java.text.Normalizer
import kotlin.math.min

/**
 * Estimación de sub-nutrientes y micronutrientes por 100 g a partir de macros +
 * categoría deducida del nombre. Portado de nutriest.py.
 */
object Nutriest {
    // Valores de Referencia de Nutrientes (para el % diario).
    val NRV = mapOf(
        "sat_fat" to 20.0, "trans_fat" to 2.0, "cholesterol" to 300.0,
        "sugars" to 90.0, "fiber" to 25.0, "salt" to 6.0, "sodium" to 2400.0,
        "vit_a" to 800.0, "vit_c" to 80.0, "vit_d" to 5.0, "vit_e" to 12.0, "vit_k" to 75.0,
        "b1" to 1.1, "b2" to 1.4, "b3" to 16.0, "b5" to 6.0, "b6" to 1.4, "b12" to 2.5,
        "folate" to 200.0, "biotin" to 50.0,
        "calcium" to 800.0, "iron" to 14.0, "magnesium" to 375.0, "phosphorus" to 700.0,
        "potassium" to 2000.0, "zinc" to 10.0, "selenium" to 55.0, "copper" to 1.0,
        "manganese" to 2.0, "iodine" to 150.0, "chromium" to 40.0, "molybdenum" to 50.0,
        "fluoride" to 3.5, "chloride" to 800.0,
    )

    val MK = listOf(
        "vit_a", "vit_c", "vit_d", "vit_e", "vit_k", "b1", "b2", "b6", "b12",
        "folate", "calcium", "iron", "magnesium", "phosphorus", "potassium",
        "zinc", "selenium", "copper", "manganese",
    )

    /** (clave, nombre, unidad). Mismo orden que la app de escritorio/Kivy. */
    val VIT_INFO = listOf(
        Triple("vit_a", "Vitamina A", "µg"), Triple("vit_c", "Vitamina C", "mg"),
        Triple("vit_d", "Vitamina D", "µg"), Triple("vit_e", "Vitamina E", "mg"),
        Triple("vit_k", "Vitamina K", "µg"), Triple("b1", "Vitamina B1", "mg"),
        Triple("b2", "Vitamina B2", "mg"), Triple("b3", "Vitamina B3 (Niacina)", "mg"),
        Triple("b5", "Vitamina B5", "mg"), Triple("b6", "Vitamina B6", "mg"),
        Triple("b12", "Vitamina B12", "µg"), Triple("folate", "Folato", "µg"),
        Triple("biotin", "Biotina (B8)", "µg"),
    )
    val MIN_INFO = listOf(
        Triple("calcium", "Calcio", "mg"), Triple("iron", "Hierro", "mg"),
        Triple("magnesium", "Magnesio", "mg"), Triple("phosphorus", "Fósforo", "mg"),
        Triple("potassium", "Potasio", "mg"), Triple("zinc", "Zinc", "mg"),
        Triple("copper", "Cobre", "mg"), Triple("manganese", "Manganeso", "mg"),
        Triple("selenium", "Selenio", "µg"), Triple("iodine", "Yodo", "µg"),
        Triple("chromium", "Cromo", "µg"), Triple("molybdenum", "Molibdeno", "µg"),
        Triple("fluoride", "Flúor", "mg"), Triple("chloride", "Cloro", "mg"),
    )
    /** Nutrientes "a limitar" del Score: (clave, nombre, unidad). */
    val LIMIT_INFO = listOf(
        Triple("sat_fat", "Grasas Saturadas", "g"), Triple("trans_fat", "Grasas Trans", "g"),
        Triple("sugars", "Azúcares", "g"), Triple("salt", "Sal", "g"),
        Triple("sodium", "Sodio", "mg"),
    )

    private class Cat(val m: List<Double>, val sat: Double, val sug: Double,
                      val fib: Double, val na: Double, val chol: Double)

    private val CATS = mapOf(
        "fruit" to Cat(listOf(5.0,40.0,0.0,.3,2.0,.05,.04,.08,0.0,20.0,12.0,.3,10.0,15.0,190.0,.1,.5,.05,.1), .10,.90,2.4,2.0,0.0),
        "vegetable" to Cat(listOf(150.0,25.0,0.0,.5,15.0,.06,.05,.12,0.0,35.0,25.0,.6,14.0,32.0,260.0,.25,.6,.06,.18), .15,.45,2.2,25.0,0.0),
        "leafy" to Cat(listOf(400.0,28.0,0.0,2.0,300.0,.08,.19,.2,0.0,150.0,99.0,2.7,79.0,49.0,558.0,.5,1.0,.13,.9), .15,.35,2.2,30.0,0.0),
        "dairy" to Cat(listOf(46.0,1.0,.1,.05,.2,.04,.18,.05,.5,5.0,120.0,.05,11.0,95.0,150.0,.4,2.0,.01,.004), .62,.90,0.0,50.0,10.0),
        "cheese" to Cat(listOf(260.0,0.0,.6,.3,2.5,.03,.4,.07,1.5,20.0,700.0,.7,28.0,500.0,100.0,3.1,15.0,.03,.03), .63,.60,0.0,700.0,90.0),
        "meat" to Cat(listOf(5.0,0.0,.5,.4,1.5,.5,.2,.4,2.0,6.0,12.0,1.8,22.0,200.0,320.0,4.0,20.0,.08,.01), .40,0.0,0.0,65.0,70.0),
        "poultry" to Cat(listOf(12.0,0.0,.1,.3,1.5,.07,.12,.5,.3,6.0,12.0,.7,25.0,200.0,250.0,1.0,22.0,.05,.02), .28,0.0,0.0,70.0,75.0),
        "fish" to Cat(listOf(30.0,0.0,5.0,1.5,.1,.1,.15,.4,3.0,12.0,15.0,.5,30.0,220.0,380.0,.5,30.0,.05,.02), .22,0.0,0.0,60.0,55.0),
        "egg" to Cat(listOf(160.0,0.0,2.0,1.0,.3,.04,.46,.17,1.1,47.0,56.0,1.8,12.0,198.0,138.0,1.3,30.0,.07,.03), .31,0.0,0.0,140.0,370.0),
        "grain" to Cat(listOf(0.0,0.0,0.0,.3,1.0,.2,.1,.1,0.0,30.0,25.0,1.5,40.0,100.0,120.0,1.0,20.0,.15,.8), .20,.08,3.0,250.0,0.0),
        "legume" to Cat(listOf(1.0,1.5,0.0,.5,5.0,.17,.07,.18,0.0,180.0,40.0,3.3,45.0,150.0,400.0,1.5,3.0,.25,.5), .13,.05,7.0,15.0,0.0),
        "nut" to Cat(listOf(0.0,0.0,0.0,15.0,0.0,.2,.8,.1,0.0,45.0,130.0,3.7,220.0,480.0,700.0,3.0,4.0,1.0,2.0), .09,.25,8.0,5.0,0.0),
        "oil" to Cat(listOf(20.0,0.0,.3,14.0,20.0,0.0,0.0,0.0,0.0,0.0,2.0,0.0,0.0,2.0,5.0,0.0,0.0,0.0,0.0), .20,0.0,0.0,10.0,5.0),
        "sweet" to Cat(listOf(2.0,0.0,0.0,.5,2.0,.03,.05,.02,.1,5.0,30.0,2.0,40.0,60.0,200.0,.8,3.0,.4,.3), .60,.95,1.5,40.0,10.0),
        "beverage" to Cat(listOf(0.0,0.0,0.0,0.0,0.0,0.0,.02,0.0,0.0,2.0,5.0,.05,8.0,15.0,40.0,.03,.5,.01,.05), .20,.90,0.0,10.0,0.0),
        "default" to Cat(listOf(10.0,3.0,0.0,.4,3.0,.08,.08,.1,.2,20.0,30.0,1.0,25.0,80.0,150.0,.7,5.0,.1,.2), .30,.30,1.5,50.0,5.0),
    )

    // (categoría -> subcadenas del nombre). Se comprueba en orden; lo específico primero.
    private val RULES: List<Pair<String, List<String>>> = listOf(
        "cheese" to listOf("queso"),
        "egg" to listOf("huevo", "clara de huevo", "tortilla francesa"),
        "leafy" to listOf("espinaca","lechuga","acelga","canonig","rucula","kale","berza","escarola","rucola"),
        "vegetable" to listOf("judia verde","tomate","cebolla","zanahoria","pimiento","brocoli","calabacin","berenjena","pepino","champin","champiñ","seta","esparrago","coliflor","verdura","ajo","puerro","alcachofa","remolacha","apio","calabaza","patata","maiz","guisante"),
        "fish" to listOf("salmon","atun","merluza","bacalao","sardina","trucha","lubina","dorada","caballa","pescado","boqueron","anchoa","lenguado","panga","gamba","langostino","mejillon","calamar","pulpo","marisco","almeja","sepia"),
        "poultry" to listOf("pollo","pavo","gallina"),
        "meat" to listOf("ternera","cerdo","lomo","jamon","chuleta","carne","cordero","conejo","salchicha","chorizo","bacon","hamburguesa","filete","buey","vacuno","embutido","morcilla","longaniza","salami","mortadela"),
        "dairy" to listOf("leche","yogur","kefir","cuajada","queso fresco batido","requeson","nata"),
        "legume" to listOf("lenteja","garbanzo","alubia","frijol","soja","haba","tofu","judia","edamame"),
        "nut" to listOf("almendra","nuez","cacahuete","avellana","pistacho","anacardo","semilla","pipas","frutos secos"),
        "oil" to listOf("aceite","mantequilla","margarina","manteca"),
        "sweet" to listOf("azucar","chocolate","miel","cacao","mermelada","caramelo","dulce","bolleria","tarta","pastel","helado","natilla","flan","turron","gominola","nutella","galleta","cereal"),
        "beverage" to listOf("agua","cafe","refresco","cola","cerveza","vino","zumo","bebida","gaseosa","tonica","limonada","batido","infusion"),
        "fruit" to listOf("manzana","naranja","platano","pera","fresa","kiwi","uva","sandia","melon","melocoton","mango","pina","cereza","ciruela","aguacate","arandano","frambuesa","mandarina","limon","higo","granada","fruta","datil","pasa","coco"),
        "grain" to listOf("arroz","pan","pasta","avena","harina","trigo","tostada","quinoa","cuscus","cous","maicena","muesli","espelta","centeno","wrap","fideos","noodle"),
    )

    private fun norm(s: String?): String {
        val n = Normalizer.normalize((s ?: "").lowercase(), Normalizer.Form.NFD)
        return n.filter { it.category != CharCategory.NON_SPACING_MARK }
    }

    fun classify(name: String): String {
        val n = norm(name)
        for ((cat, keys) in RULES) for (k in keys) if (norm(k) in n) return cat
        return "default"
    }

    /** Sub-nutrientes y micros estimados POR 100 g. Incluye "category". */
    fun estimate(name: String, kcal: Double, protein: Double, carbs: Double, fat: Double): Map<String, Double> {
        val cat = CATS[classify(name)] ?: CATS["default"]!!
        val f = maxOf(fat, 0.0)
        val c = maxOf(carbs, 0.0)
        val sat = round1(min(f, f * cat.sat))
        val sugars = round1(min(c, c * cat.sug))
        val fiber = round1(min(c, cat.fib))
        val sodium = cat.na
        val out = HashMap<String, Double>()
        out["sat_fat"] = sat
        out["trans_fat"] = 0.0
        out["cholesterol"] = cat.chol
        out["sugars"] = sugars
        out["fiber"] = fiber
        out["sodium"] = sodium
        out["salt"] = Math.round(sodium * 2.5 / 1000.0 * 100.0) / 100.0
        for (i in MK.indices) out[MK[i]] = cat.m[i]
        return out
    }

    fun categoryOf(name: String) = classify(name)

    private fun round1(x: Double) = Math.round(x * 10.0) / 10.0
}
