package org.ivansola.minutricion.ui.components

import org.ivansola.minutricion.R

/**
 * Qué icono del pack (res/drawable/food_*, generado desde assets/icons/food_line) le toca a cada
 * alimento. Se decide en este orden:
 *
 *  1. la CATEGORÍA elegida por el usuario, si la tiene: es lo más fiable. Solo cede ante un icono
 *     más concreto de su misma familia ([REFINE]), y "Otros" no cuenta;
 *  2. evidencia fuerte de SUPLEMENTO: una dosis ("500mg") o un nombre que EMPIEZA por el nutriente
 *     ("Magnesio citrato", "Proteína de guisante"). Va antes que todo lo demás porque en estos
 *     productos las demás palabras son sabores: "Vitamina C 1000mg Naranja" no es una fruta;
 *  3. PALABRAS CLAVE del nombre ([keyword]), para desempatar lo que el emoji mezcla o no conoce;
 *  4. el EMOJI que ya deducía la app por el nombre, traducido a su icono del pack;
 *  5. pistas débiles ([lateKeywords]): palabras como "proteína" o "vitaminas" que solo significan
 *     "suplemento" si nada más encaja — "Pechuga alta en proteínas" es pollo;
 *  6. un comodín: bebida genérica u "otros".
 *
 * Todas las palabras se buscan como PALABRA con [TextMatch] (tildes y plurales incluidos), nunca
 * como subcadena. El orden de las tablas importa: lo específico va antes que lo general.
 * El reparto se revisó pasando ~10.000 nombres reales por aquí (IconAuditTest).
 */
object FoodIcons {

    private val byCategory: Map<String, Int> = mapOf(
        "Untables" to R.drawable.food_untables, "Mariscos" to R.drawable.food_mariscos,
        "Hamburguesas" to R.drawable.food_hamburguesas, "Carne de caza" to R.drawable.food_caza,
        "Helados" to R.drawable.food_helados, "Productos de soya" to R.drawable.food_soja,
        "Otros" to R.drawable.food_otros, "Res" to R.drawable.food_res,
        "Legumbres" to R.drawable.food_legumbres, "Pavo" to R.drawable.food_pavo,
        "Panes" to R.drawable.food_panes, "Huevo" to R.drawable.food_huevo,
        "Panadería" to R.drawable.food_panaderia, "Platos preparados" to R.drawable.food_preparados,
        "Suplementos" to R.drawable.food_suplementos, "Especias y hierbas" to R.drawable.food_especias,
        "Snacks" to R.drawable.food_snacks, "Carnes vegetales" to R.drawable.food_carne_vegetal,
        "Pasta" to R.drawable.food_pasta, "Café e Infusiones" to R.drawable.food_cafe,
        "Cereales" to R.drawable.food_cereales, "Dulces" to R.drawable.food_dulces,
        "Yogurt" to R.drawable.food_yogurt, "Pescado" to R.drawable.food_pescado,
        "Salsas" to R.drawable.food_salsas, "Verduras" to R.drawable.food_verduras,
        "Quesos" to R.drawable.food_quesos, "Fiambres" to R.drawable.food_fiambres,
        "Harinas" to R.drawable.food_harinas, "Comida instantánea" to R.drawable.food_instantanea,
        "Bebidas alcohólicas" to R.drawable.food_alcohol, "Granos" to R.drawable.food_granos,
        "Restaurantes" to R.drawable.food_restaurantes, "Bebidas" to R.drawable.food_bebidas,
        "Tubérculos" to R.drawable.food_tuberculos, "Mezclas instantáneas" to R.drawable.food_mezclas,
        "Pollo" to R.drawable.food_pollo, "Bebidas vegetales" to R.drawable.food_veg_drinks,
        "Aceites" to R.drawable.food_aceites, "Cerdo" to R.drawable.food_cerdo,
        "Frutas" to R.drawable.food_frutas, "Chocolates" to R.drawable.food_chocolates,
        "Leche" to R.drawable.food_leche, "Semillas" to R.drawable.food_semillas,
        "Frutos secos" to R.drawable.food_frutos_secos,
    )

    /** Dosis en el nombre ("500mg", "5000 mcg", "1000 UI"): casi siempre un suplemento. */
    private val DOSE = Regex("""\d+\s?(?:mg|mcg|µg|ug|ui|iu)(?![a-z])""")

    /** Cantidades al principio del nombre ("100% ", "2 x 4 x "); "226ERS" no lo es. */
    private val LEADING_AMOUNT = Regex("""^(?:\d+(?:[.,]\d+)?\s*[%x]\s*)+""")

    /** Si el nombre EMPIEZA por uno de estos, es suplemento o proteína en polvo. */
    private val supplementStart: List<Pair<String, Int>> = listOf(
        "proteina*" to R.drawable.food_proteina, "whey" to R.drawable.food_proteina,
        "creatina" to R.drawable.food_proteina, "caseina" to R.drawable.food_proteina,
        "vitamina*" to R.drawable.food_suplementos, "multivitamin*" to R.drawable.food_suplementos,
        "magnesio" to R.drawable.food_suplementos, "zinc" to R.drawable.food_suplementos,
        "calcio" to R.drawable.food_suplementos, "hierro" to R.drawable.food_suplementos,
        "omega" to R.drawable.food_suplementos, "colageno" to R.drawable.food_suplementos,
        "biotina" to R.drawable.food_suplementos, "melatonina" to R.drawable.food_suplementos,
        "extracto" to R.drawable.food_suplementos, "acido" to R.drawable.food_suplementos,
        "l-casei*" to R.drawable.food_yogurt, "l-*" to R.drawable.food_suplementos, "carbohidratos" to R.drawable.food_proteina,
    )

    /*
     * Palabras que desempatan lo que el emoji mezcla o no conoce. Válidas para comida y bebida.
     * Van en niveles y se consultan en este orden:
     *
     *  0. INFANTIL: manda sobre todo ("Tarrito de merluza", "Galletas desde 6 meses"); después,
     *     helados, barritas y nutrición deportiva ([strongKeywords]).
     *  A. QUÉ ES el producto (helado, barrita, pan, galleta, salsa, untable, caldo…). Aquí gana la
     *     palabra que aparece ANTES en el nombre, porque en español el nombre empieza por el tipo de
     *     producto: "Yogur con galleta" es yogur y "Galleta con yogur" es galleta; "Bizcocho con
     *     pepitas de chocolate", bollería; "Aceitunas rellenas de anchoa", aperitivo. A igual
     *     posición decide el orden de la lista ("tortitas americanas" antes que "tortita*").
     *  B. su INGREDIENTE PRINCIPAL (embutido, pescado, ave, carne, verdura…), por orden de lista:
     *     los embutidos antes que las aves ("Salchichón de pavo") y los pescados antes que "lomo"
     *     ("Lomo de bacalao").
     *  C. SABORES y aderezos (especias, chocolate, azúcar…): lo más débil, por eso lo último.
     */
    private val infantKeywords: List<Pair<String, Int>> = listOf(
        "alimento infantil" to R.drawable.food_infantil, "leche infantil" to R.drawable.food_infantil,
        "papilla*" to R.drawable.food_infantil, "tarrito*" to R.drawable.food_infantil,
        "potito*" to R.drawable.food_infantil, "gerber" to R.drawable.food_infantil,
        "hero baby" to R.drawable.food_infantil, "hero solo" to R.drawable.food_infantil,
        "nutriben" to R.drawable.food_infantil, "blemil" to R.drawable.food_infantil,
        "nidina" to R.drawable.food_infantil, "yogolino" to R.drawable.food_infantil,
        "leche de continuacion" to R.drawable.food_infantil, "leche de crecimiento" to R.drawable.food_infantil,
        "desde 4 meses" to R.drawable.food_infantil, "desde 6 meses" to R.drawable.food_infantil,
        "desde 8 meses" to R.drawable.food_infantil, "desde 10 meses" to R.drawable.food_infantil,
        "desde 12 meses" to R.drawable.food_infantil, "leche de formula" to R.drawable.food_infantil,
        "almiron" to R.drawable.food_infantil, "anti-regurgitacion" to R.drawable.food_infantil,
    )

    /**
     * Formatos que mandan esté donde esté la palabra, por orden de lista: la barrita ("Race Day Bar
     * BCAA - Barrita energética") y la nutrición deportiva, cuyo nombre suele empezar por la golosina
     * o el sabor ("Gominolas de Ashwagandha"). El helado solo manda si el nombre EMPIEZA por él
     * ("Helado barrita TWIX"); al final suele ser un sabor ("Whey Isolate Helado de Vainilla").
     * Igual el queso ("Queso rallado especial pasta" sí, "Hamburguesa con queso" no) y el vinagre
     * ("Vinagre balsámico edición dorada" sí, "Alcaparras en vinagre" no).
     */
    private val startKeywords: List<Pair<String, Int>> = listOf(
        "helado" to R.drawable.food_helados, "ice cream" to R.drawable.food_helados,
        "queso" to R.drawable.food_quesos, "vinagre" to R.drawable.food_salsas,
        "corteza*" to R.drawable.food_snacks,
    )

    /** Igual que [startKeywords], pero después de las barritas: "Barra grande" sí, "Barra de cereales" no. */
    private val startAfterStrongKeywords: List<Pair<String, Int>> = listOf(
        "barra" to R.drawable.food_panes,
    )

    private val strongKeywords: List<Pair<String, Int>> = listOf(
        "barrita*" to R.drawable.food_barrita, "bar" to R.drawable.food_barrita, "barra de cereal*" to R.drawable.food_barrita,
        "barras de cereal*" to R.drawable.food_barrita, "barra proteica" to R.drawable.food_barrita,
        "barras proteicas" to R.drawable.food_barrita, "protein bar*" to R.drawable.food_barrita,
        // nutrición deportiva y suplementos
        "whey" to R.drawable.food_proteina, "isolate" to R.drawable.food_proteina,
        "casein" to R.drawable.food_proteina, "caseina" to R.drawable.food_proteina,
        "vegetable protein" to R.drawable.food_proteina, "vegan protein" to R.drawable.food_proteina,
        "protein powder" to R.drawable.food_proteina, "gainer" to R.drawable.food_proteina,
        "pre-entreno" to R.drawable.food_proteina, "preentreno" to R.drawable.food_proteina,
        "pre entreno" to R.drawable.food_proteina, "creatina" to R.drawable.food_proteina,
        "pre training" to R.drawable.food_proteina, "pre-workout" to R.drawable.food_proteina,
        "preworkout" to R.drawable.food_proteina, "intra training" to R.drawable.food_proteina,
        "intra-workout" to R.drawable.food_proteina, "hmb" to R.drawable.food_suplementos,
        "carbo mix" to R.drawable.food_proteina,
        "capsula*" to R.drawable.food_suplementos, "comprimido*" to R.drawable.food_suplementos,
        "softgel*" to R.drawable.food_suplementos, "tabs" to R.drawable.food_suplementos,
        "gummies" to R.drawable.food_suplementos, "vial*" to R.drawable.food_suplementos,
        "bcaa" to R.drawable.food_suplementos, "eaa" to R.drawable.food_suplementos,
        "glutamina" to R.drawable.food_suplementos, "leucina" to R.drawable.food_suplementos,
        "carnitina" to R.drawable.food_suplementos, "melatonina" to R.drawable.food_suplementos,
        "ashwagandha" to R.drawable.food_suplementos, "probiotico*" to R.drawable.food_suplementos,
        "multivitamin*" to R.drawable.food_suplementos, "bisglicinato" to R.drawable.food_suplementos,
        "electrolito*" to R.drawable.food_suplementos,
    )

    /** A. Qué es el producto. Gana la palabra que aparece antes en el nombre. */
    private val productKeywords: List<Pair<String, Int>> = listOf(
        "helado" to R.drawable.food_helados, "ice cream" to R.drawable.food_helados,
        "polo" to R.drawable.food_helados, "sorbete" to R.drawable.food_helados,
        // tortitas: tres productos con el mismo nombre. Las americanas y las proteicas son masa de
        // sartén; el resto, tortitas infladas de arroz o maíz ("Tortitas Milho")
        "tortita americana" to R.drawable.food_panaderia, "tortitas americanas" to R.drawable.food_panaderia,
        "tortita proteica" to R.drawable.food_panaderia, "tortitas proteicas" to R.drawable.food_panaderia,
        "pancake*" to R.drawable.food_panaderia, "crep*" to R.drawable.food_panaderia,
        "tortita*" to R.drawable.food_tortitas, "arroz inflado" to R.drawable.food_tortitas,
        "maiz inflado" to R.drawable.food_tortitas,
        // platos preparados y masas
        "pizza" to R.drawable.food_pizza, "calzone" to R.drawable.food_pizza, "croqueta*" to R.drawable.food_preparados,
        // NO "empanado": "Filetes de pollo empanado" siguen siendo pollo
        "empanada" to R.drawable.food_preparados, "empanadilla*" to R.drawable.food_preparados,
        "crema de queso" to R.drawable.food_quesos, "dados de queso" to R.drawable.food_quesos,
        "mug cake" to R.drawable.food_panaderia, "cake" to R.drawable.food_panaderia,
        "masa de pizza" to R.drawable.food_panaderia,
        "hummus" to R.drawable.food_legumbres, "masa quebrada" to R.drawable.food_panaderia,
        "hojaldre" to R.drawable.food_panaderia,
        // "pasta de" que no es pasta; van antes que "pasta" para ganar el empate
        "pasta de datil*" to R.drawable.food_untables, "pasta de almendra*" to R.drawable.food_untables,
        "pasta de avellana*" to R.drawable.food_untables, "pasta de sesamo" to R.drawable.food_untables,
        "pasta de cacao" to R.drawable.food_chocolates, "pasta de tomate" to R.drawable.food_salsas,
        "pasta de curry" to R.drawable.food_salsas, "nata para cocinar" to R.drawable.food_leche,
        "pastas de te" to R.drawable.food_galletas,
        // fideos instantáneos: el cuenco, no el plato de pasta
        "fideos orientales" to R.drawable.food_instantanea, "fideos sabor" to R.drawable.food_instantanea,
        "yatekomo" to R.drawable.food_instantanea, "yakisoba" to R.drawable.food_instantanea,
        "ramen" to R.drawable.food_instantanea, "noodles" to R.drawable.food_instantanea,
        "sopinstant" to R.drawable.food_instantanea,
        // pasta, también la rellena y con salsa ("Lasaña de atún", "Macarrones con atún en salsa")
        "pasta" to R.drawable.food_pasta, "macarr*" to R.drawable.food_pasta,
        "espagueti*" to R.drawable.food_pasta, "spaghetti*" to R.drawable.food_pasta,
        "tallarin*" to R.drawable.food_pasta, "fideo*" to R.drawable.food_pasta,
        "helice*" to R.drawable.food_pasta, "espiral*" to R.drawable.food_pasta,
        "penne" to R.drawable.food_pasta, "fusilli" to R.drawable.food_pasta,
        "tagliatelle*" to R.drawable.food_pasta, "linguine" to R.drawable.food_pasta,
        "farfalle" to R.drawable.food_pasta, "rigatoni" to R.drawable.food_pasta,
        "gnocchi*" to R.drawable.food_pasta, "ñoqui*" to R.drawable.food_pasta,
        "ñordo*" to R.drawable.food_pasta,
        "canelon*" to R.drawable.food_pasta, "lasaña*" to R.drawable.food_pasta,
        "raviol*" to R.drawable.food_pasta, "tortellini*" to R.drawable.food_pasta,
        "fideua" to R.drawable.food_pasta,
        // pan y bollería
        "pan de molde" to R.drawable.food_panes, "pan de hamburguesa" to R.drawable.food_panes,
        "pan de perrito" to R.drawable.food_panes, "panecillo*" to R.drawable.food_panes,
        "bizcocho*" to R.drawable.food_panaderia, "magdalena*" to R.drawable.food_panaderia,
        "donut*" to R.drawable.food_panaderia, "brownie*" to R.drawable.food_panaderia,
        "croissant*" to R.drawable.food_panaderia, "cruasan*" to R.drawable.food_panaderia,
        "sobao*" to R.drawable.food_panaderia, "palmerita*" to R.drawable.food_panaderia,
        "berlina*" to R.drawable.food_panaderia, "ensaimada*" to R.drawable.food_panaderia,
        "napolitana*" to R.drawable.food_panaderia, "palmera*" to R.drawable.food_panaderia,
        "rosquilla*" to R.drawable.food_panaderia, "churro*" to R.drawable.food_panaderia,
        "muffin*" to R.drawable.food_panaderia, "bolleria" to R.drawable.food_panaderia,
        "cracker*" to R.drawable.food_snacks, "pan de gambas" to R.drawable.food_snacks, "pan rallado" to R.drawable.food_panes,
        "palito salado" to R.drawable.food_snacks, "palitos salados" to R.drawable.food_snacks,
        // galletas, golosinas y postres (en singular: "natilla" también casa con "natillas")
        "galleta*" to R.drawable.food_galletas, "chocogalleta*" to R.drawable.food_galletas,
        "cookie*" to R.drawable.food_galletas, "oreo" to R.drawable.food_galletas,
        "chips ahoy" to R.drawable.food_galletas, "digestive" to R.drawable.food_galletas,
        "caramelo*" to R.drawable.food_dulces, "regaliz" to R.drawable.food_dulces,
        "chicle*" to R.drawable.food_dulces, "gominola*" to R.drawable.food_dulces,
        "chuche*" to R.drawable.food_dulces, "cabello de angel" to R.drawable.food_dulces,
        "golosina*" to R.drawable.food_dulces, "praline*" to R.drawable.food_chocolates,
        "nachos" to R.drawable.food_snacks, "danonino" to R.drawable.food_yogurt,
        "natilla" to R.drawable.food_postres, "flan" to R.drawable.food_postres,
        "copa" to R.drawable.food_postres, "dalky" to R.drawable.food_postres,
        "tarta helada" to R.drawable.food_helados, "tarta" to R.drawable.food_panaderia, "tartaleta*" to R.drawable.food_panaderia,
        "crema de chocolate" to R.drawable.food_postres, "pudding" to R.drawable.food_postres,
        "mousse" to R.drawable.food_postres, "gelatina" to R.drawable.food_postres,
        "arroz con leche" to R.drawable.food_postres, "crema catalana" to R.drawable.food_postres,
        "tiramisu" to R.drawable.food_postres, "panna cotta" to R.drawable.food_postres,
        // lácteos: 🥛 junta leche, yogur y postres con nata
        "postre lacteo" to R.drawable.food_yogurt, "postre" to R.drawable.food_postres, "petit suisse" to R.drawable.food_yogurt,
        "petit de" to R.drawable.food_yogurt, "yogur*" to R.drawable.food_yogurt,
        "yog" to R.drawable.food_yogurt, "kefir" to R.drawable.food_yogurt,
        "cuajada" to R.drawable.food_yogurt, "leche fermentada" to R.drawable.food_yogurt,
        "actimel" to R.drawable.food_yogurt, "activia" to R.drawable.food_yogurt,
        "bifidus" to R.drawable.food_yogurt,
        // bebidas vegetales ("Bebida de soja" no es tofu)
        "bebida de soja" to R.drawable.food_veg_drinks, "bebida de avena" to R.drawable.food_veg_drinks,
        "bebida de almendra*" to R.drawable.food_veg_drinks, "bebida de arroz" to R.drawable.food_veg_drinks,
        "bebida de coco" to R.drawable.food_veg_drinks, "bebida vegetal" to R.drawable.food_veg_drinks,
        "leche de almendra*" to R.drawable.food_veg_drinks, "leche de avena" to R.drawable.food_veg_drinks,
        "leche de soja" to R.drawable.food_veg_drinks, "leche de coco" to R.drawable.food_veg_drinks,
        // salsas ("salsa de soja", "tomate frito")
        "salsa" to R.drawable.food_salsas, "tomate frito" to R.drawable.food_salsas,
        "ketchup" to R.drawable.food_salsas, "mayonesa" to R.drawable.food_salsas,
        "crema balsamica" to R.drawable.food_salsas, "balsamic*" to R.drawable.food_salsas,
        "crema de vinagre" to R.drawable.food_salsas, "cremas de vinagre" to R.drawable.food_salsas,
        "crema vinagre" to R.drawable.food_salsas, "banderilla*" to R.drawable.food_aceitunas,
        // untables ("Paté de atún")
        "crema de cacao" to R.drawable.food_untables, "crema de anacardo*" to R.drawable.food_untables,
        "membrillo" to R.drawable.food_untables, "sazonador*" to R.drawable.food_especias, "crema de arroz" to R.drawable.food_cereales,
        "crema de cacahuete" to R.drawable.food_untables, "crema de almendra*" to R.drawable.food_untables,
        "crema de avellana*" to R.drawable.food_untables, "crema de pistacho*" to R.drawable.food_untables,
        "pate" to R.drawable.food_untables, "foie" to R.drawable.food_untables,
        "mermelada" to R.drawable.food_untables, "mantequilla" to R.drawable.food_untables,
        "margarina" to R.drawable.food_untables, "miel" to R.drawable.food_untables,
        "sirope" to R.drawable.food_untables, "nocilla" to R.drawable.food_untables,
        "nutella" to R.drawable.food_untables,
        // caldos y sopas (también en litros, cuando cuentan como bebida) y purés de fruta
        "caldo" to R.drawable.food_instantanea, "sopa" to R.drawable.food_instantanea,
        "gazpacho" to R.drawable.food_instantanea, "salmorejo" to R.drawable.food_instantanea,
        "pure de manzana" to R.drawable.food_frutas, "pure de fruta*" to R.drawable.food_frutas,
        // aperitivos
        "snack*" to R.drawable.food_snacks, "chips" to R.drawable.food_snacks,
        "aperitivo*" to R.drawable.food_snacks, "pringles" to R.drawable.food_snacks,
        "gilda*" to R.drawable.food_aceitunas, "aceituna" to R.drawable.food_aceitunas,
        "pepinillo*" to R.drawable.food_aceitunas, "encurtido*" to R.drawable.food_aceitunas,
        "cebollita*" to R.drawable.food_aceitunas, "torera*" to R.drawable.food_aceitunas,
        "guindilla*" to R.drawable.food_aceitunas,
        // cereales y harinas: el desayuno no es sopa
        "porridge" to R.drawable.food_cereales, "avena" to R.drawable.food_cereales,
        "copos" to R.drawable.food_cereales,
        "muesli" to R.drawable.food_cereales, "granola" to R.drawable.food_cereales,
        "cereal" to R.drawable.food_cereales, "harina*" to R.drawable.food_harinas,
        // infusiones (no siempre dicen "infusión")
        "valeriana" to R.drawable.food_te, "manzanilla" to R.drawable.food_te,
        "poleo" to R.drawable.food_cafe, "tila" to R.drawable.food_cafe,
        "rooibos" to R.drawable.food_te, "bolsitas" to R.drawable.food_te,
        "matcha" to R.drawable.food_te, "tisana" to R.drawable.food_te,
        "achicoria" to R.drawable.food_cafe,
        "burger" to R.drawable.food_hamburguesas, "hamburguesa*" to R.drawable.food_hamburguesas,
        "frutos secos" to R.drawable.food_frutos_secos,
        // el chocolate cuenta como producto solo cuando va delante: "Chocolate para postres" es
        // chocolate, pero "Bizcocho con pepitas de chocolate" es bollería
        "chocolate*" to R.drawable.food_chocolates, "xocolata" to R.drawable.food_chocolates,
        // "pan" suelto, lo último: las frases ("pan de molde", "pan rallado") ganan el empate
        "pan" to R.drawable.food_panes,
    )

    /** B. Ingrediente principal. Manda el orden de la lista. */
    private val ingredientKeywords: List<Pair<String, Int>> = listOf(
        // embutidos antes que aves: "Salchichón de pavo", "Jamón de pavo"
        // lo cocido y loncheado es fiambre; el jamón y la paleta curados, la pata de jamón
        "fiambre*" to R.drawable.food_fiambres, "jamon cocido" to R.drawable.food_fiambres,
        "jamon york" to R.drawable.food_fiambres, "jamon de pavo" to R.drawable.food_fiambres,
        "jamon de pato" to R.drawable.food_fiambres, "paleta cocida" to R.drawable.food_fiambres,
        "paleta asada" to R.drawable.food_fiambres,
        "jamon" to R.drawable.food_cerdo, "paleta" to R.drawable.food_cerdo,
        "embutido*" to R.drawable.food_embutidos,
        "mortadela" to R.drawable.food_fiambres,
        "chorizo" to R.drawable.food_embutidos, "salchichon" to R.drawable.food_embutidos,
        "salami" to R.drawable.food_embutidos, "fuet" to R.drawable.food_embutidos,
        "lomo embuchado" to R.drawable.food_embutidos, "chistorra" to R.drawable.food_embutidos,
        "salchicha*" to R.drawable.food_embutidos, "frankfurt*" to R.drawable.food_embutidos,
        "pepperoni" to R.drawable.food_embutidos,
        "chopped" to R.drawable.food_fiambres, "guanciale" to R.drawable.food_fiambres,
        // NO "bacon" ni "cecina": suelen ser sabor o relleno ("Pipas sabor bacon", "Hamburguesa con
        // cecina"); el bacon solo ya lo resuelve el emoji y la cecina va en las pistas débiles
        "panceta" to R.drawable.food_fiambres, "sobrasada" to R.drawable.food_embutidos,
        "lacon" to R.drawable.food_fiambres, "longaniza*" to R.drawable.food_embutidos,
        "morcilla*" to R.drawable.food_embutidos, "butifarra*" to R.drawable.food_embutidos,
        // NO "lonchas": "queso en lonchas" no es fiambre
        // pescados antes que "lomo": "Lomo de bacalao" es pescado, no cerdo
        "bacalao" to R.drawable.food_pescado, "salmon" to R.drawable.food_pescado,
        "atun" to R.drawable.food_pescado, "merluza" to R.drawable.food_pescado,
        "rape" to R.drawable.food_pescado, "lubina" to R.drawable.food_pescado,
        "dorada" to R.drawable.food_pescado, "bonito" to R.drawable.food_pescado,
        "ventresca" to R.drawable.food_pescado, "anchoa*" to R.drawable.food_pescado,
        "escabeche" to R.drawable.food_pescado, "caballa" to R.drawable.food_pescado,
        "sardina*" to R.drawable.food_pescado, "poton" to R.drawable.food_mariscos,
        "datil*" to R.drawable.food_frutas, "arenque*" to R.drawable.food_pescado,
        "tinta" to R.drawable.food_mariscos, "zamburiña*" to R.drawable.food_mariscos,
        "volandeira*" to R.drawable.food_mariscos, "pota" to R.drawable.food_mariscos,
        "pavo" to R.drawable.food_pavo,
        "pato" to R.drawable.food_pollo, "magret" to R.drawable.food_pollo, "confit" to R.drawable.food_pollo,
        "conejo" to R.drawable.food_caza, "ciervo" to R.drawable.food_caza,
        "jabali" to R.drawable.food_caza, "venado" to R.drawable.food_caza,
        "codorniz" to R.drawable.food_caza, "perdiz" to R.drawable.food_caza,
        "cerdo" to R.drawable.food_cerdo, "lomo" to R.drawable.food_cerdo,
        "costilla" to R.drawable.food_cerdo, "codillo" to R.drawable.food_cerdo,
        "secreto" to R.drawable.food_cerdo, "chuleta" to R.drawable.food_cerdo,
        "carrillera*" to R.drawable.food_cerdo,
        "vacuno" to R.drawable.food_res,
        "burrata*" to R.drawable.food_quesos, "burratina*" to R.drawable.food_quesos,
        // conservas "en aceite": el aceite es el líquido, no el producto
        "boletus" to R.drawable.food_verduras, "alcaparra*" to R.drawable.food_aceitunas,
        "menestra" to R.drawable.food_verduras,
        "alcachofa*" to R.drawable.food_verduras,
        // sin raíz: "palmito*" casaba con "palmitoiletanolamida", un suplemento
        "palmito" to R.drawable.food_verduras, "pocha*" to R.drawable.food_legumbres,
        "seitan" to R.drawable.food_carne_vegetal,
        "tofu" to R.drawable.food_soja, "edamame" to R.drawable.food_soja, "soja" to R.drawable.food_soja,
        "semilla" to R.drawable.food_semillas, "chia" to R.drawable.food_semillas,
        "pipa" to R.drawable.food_semillas, "sesamo" to R.drawable.food_semillas,
    )

    /** C. Sabores y aderezos: lo más débil. Manda el orden de la lista. */
    private val flavorKeywords: List<Pair<String, Int>> = listOf(
        // NO "aceite": "Atún en aceite" es atún; el aceite puro ya lo resuelve el emoji
        "especia" to R.drawable.food_especias, "pimienta" to R.drawable.food_especias,
        "pimenton" to R.drawable.food_especias, "oregano" to R.drawable.food_especias,
        "canela" to R.drawable.food_especias, "comino" to R.drawable.food_especias,
        // NO "azúcar" suelto: casi siempre aparece como "sin azúcar"
        "azucar moreno" to R.drawable.food_dulces, "azucar blanco" to R.drawable.food_dulces,
        "azucar glas" to R.drawable.food_dulces, "azucar de caña" to R.drawable.food_dulces,
        "edulcorante" to R.drawable.food_dulces, "speculoos" to R.drawable.food_galletas,
        "choco" to R.drawable.food_chocolates, "tableta" to R.drawable.food_chocolates,
    )

    /** Bebidas alcohólicas inequívocas: van antes que todo en una bebida ("Vino … D.O. Manzanilla"). */
    private val alcoholFirst: List<Pair<String, Int>> = listOf(
        "cerveza" to R.drawable.food_cerveza, "beer" to R.drawable.food_cerveza,
        "radler" to R.drawable.food_cerveza, "lager" to R.drawable.food_cerveza,
        "vino" to R.drawable.food_alcohol, "cava" to R.drawable.food_alcohol,
        "champan" to R.drawable.food_alcohol, "sidra" to R.drawable.food_alcohol,
        "licor" to R.drawable.food_alcohol, "vermut" to R.drawable.food_alcohol,
        "vermouth" to R.drawable.food_alcohol, "ginebra" to R.drawable.food_alcohol,
        "vodka" to R.drawable.food_alcohol, "whisky" to R.drawable.food_alcohol,
        "tequila" to R.drawable.food_alcohol, "brandy" to R.drawable.food_alcohol,
        "orujo" to R.drawable.food_alcohol, "jerez" to R.drawable.food_alcohol,
        "moscatel" to R.drawable.food_alcohol, "moscato" to R.drawable.food_alcohol,
        "frizzante" to R.drawable.food_alcohol, "espirituosa" to R.drawable.food_alcohol,
    )

    /** Palabras SOLO para bebidas ("cola" también es "cola" de gominola; aquí ya se sabe que se bebe). */
    private val byDrinkKeyword: List<Pair<String, Int>> = listOf(
        // cacao soluble: "Cola Cao" no es un refresco de cola
        "cola cao" to R.drawable.food_bebidas, "colacao" to R.drawable.food_bebidas,
        "cacao soluble" to R.drawable.food_bebidas, "nesquik" to R.drawable.food_bebidas,
        "infusion" to R.drawable.food_te, "tisana" to R.drawable.food_te,
        "cerveza" to R.drawable.food_cerveza, "beer" to R.drawable.food_cerveza,
        "radler" to R.drawable.food_cerveza, "lager" to R.drawable.food_cerveza,
        "moscatel" to R.drawable.food_alcohol,
        "jerez" to R.drawable.food_alcohol, "oporto" to R.drawable.food_alcohol,
        "pacharan" to R.drawable.food_alcohol, "anis" to R.drawable.food_alcohol,
        // lo que se vende en lata
        "monster" to R.drawable.food_bebidas_lata, "red bull" to R.drawable.food_bebidas_lata,
        "redbull" to R.drawable.food_bebidas_lata, "energet*" to R.drawable.food_bebidas_lata,
        "fanta" to R.drawable.food_bebidas_lata, "coca" to R.drawable.food_bebidas_lata,
        "cola" to R.drawable.food_bebidas_lata, "pepsi" to R.drawable.food_bebidas_lata,
        "sprite" to R.drawable.food_bebidas_lata, "aquarius" to R.drawable.food_bebidas_lata,
        "nestea" to R.drawable.food_bebidas_lata, "refresco" to R.drawable.food_bebidas_lata,
        "gaseosa" to R.drawable.food_bebidas_lata, "tonica" to R.drawable.food_bebidas_lata,
        "schweppes" to R.drawable.food_bebidas_lata, "7up" to R.drawable.food_bebidas_lata,
        "kas" to R.drawable.food_bebidas_lata,
        "lata" to R.drawable.food_bebidas_lata,
        "vino" to R.drawable.food_alcohol,
        "sidra" to R.drawable.food_alcohol, "licor" to R.drawable.food_alcohol,
        "cava" to R.drawable.food_alcohol, "champan" to R.drawable.food_alcohol,
        "txakoli" to R.drawable.food_alcohol,
        "vermut" to R.drawable.food_alcohol, "vermouth" to R.drawable.food_alcohol,
        "espirituosa" to R.drawable.food_alcohol, "fino" to R.drawable.food_alcohol,
        "ron" to R.drawable.food_alcohol,
        "ginebra" to R.drawable.food_alcohol, "vodka" to R.drawable.food_alcohol,
        "whisky" to R.drawable.food_alcohol, "tequila" to R.drawable.food_alcohol,
        "brandy" to R.drawable.food_alcohol, "orujo" to R.drawable.food_alcohol,
        "mojito" to R.drawable.food_alcohol, "sangria" to R.drawable.food_alcohol,
        "tinto de verano" to R.drawable.food_alcohol,
        "zumo" to R.drawable.food_zumo, "nectar" to R.drawable.food_zumo,
        "jugo" to R.drawable.food_zumo, "smoothie" to R.drawable.food_zumo,
        "cafe" to R.drawable.food_cafe, "infusion" to R.drawable.food_te,
        "te" to R.drawable.food_te, "tea" to R.drawable.food_te, "chai" to R.drawable.food_te,
        "leche" to R.drawable.food_leche,
    )

    /** Pistas débiles: solo si ni las palabras clave ni el emoji han encontrado nada. */
    private val lateKeywords: List<Pair<String, Int>> = listOf(
        "proteina*" to R.drawable.food_proteina, "protein" to R.drawable.food_proteina,
        "vitamina*" to R.drawable.food_suplementos, "vitamin*" to R.drawable.food_suplementos,
        "magnesio" to R.drawable.food_suplementos, "zinc" to R.drawable.food_suplementos,
        "calcio" to R.drawable.food_suplementos, "hierro" to R.drawable.food_suplementos,
        "omega" to R.drawable.food_suplementos, "colageno" to R.drawable.food_suplementos,
        "biotina" to R.drawable.food_suplementos, "coenzima" to R.drawable.food_suplementos,
        "q10" to R.drawable.food_suplementos, "ginseng" to R.drawable.food_suplementos,
        "maca" to R.drawable.food_suplementos, "extracto" to R.drawable.food_suplementos,
        "extract" to R.drawable.food_suplementos, "prenatal" to R.drawable.food_suplementos,
        "articular" to R.drawable.food_suplementos, "quemador*" to R.drawable.food_suplementos,
        "termogenic*" to R.drawable.food_suplementos, "cafeina" to R.drawable.food_suplementos,
        "electrolito*" to R.drawable.food_suplementos, "complex" to R.drawable.food_suplementos,
        "collagen" to R.drawable.food_suplementos, "potasio" to R.drawable.food_suplementos,
        "now foods" to R.drawable.food_suplementos, "cecina" to R.drawable.food_embutidos,
        // grano suelto; aquí y no antes para que "Pan de espelta" siga siendo pan (lo da el emoji)
        "espelta" to R.drawable.food_granos,
        // marcas que solo venden nutrición deportiva: si nada más ha encajado, es un suplemento
        "essential series" to R.drawable.food_suplementos, "vitobest" to R.drawable.food_suplementos,
        "drasanvi" to R.drawable.food_suplementos, "quamtrax" to R.drawable.food_suplementos,
        "amix" to R.drawable.food_suplementos, "bigsuplements" to R.drawable.food_suplementos,
        "226ers" to R.drawable.food_suplementos, "musclecore" to R.drawable.food_suplementos,
        "myvitamins" to R.drawable.food_suplementos, "swanson" to R.drawable.food_suplementos,
        "myprotein" to R.drawable.food_suplementos, "prozis" to R.drawable.food_suplementos,
        "sport series" to R.drawable.food_suplementos, "hsn" to R.drawable.food_suplementos,
        "evobomb" to R.drawable.food_suplementos, "evordx" to R.drawable.food_suplementos,
        // otras marcas de un solo tipo de producto
        "dulcesol" to R.drawable.food_panaderia, "torras" to R.drawable.food_chocolates,
        "haribo" to R.drawable.food_dulces, "be essential" to R.drawable.food_suplementos,
        "raw series" to R.drawable.food_suplementos, "granini" to R.drawable.food_zumo,
        "vegecampo" to R.drawable.food_carne_vegetal, "garden gourmet" to R.drawable.food_carne_vegetal,
    )

    /** Traducción del emoji que ya deducía la app. */
    private val byEmoji: Map<String, Int> = buildMap {
        fun put(icon: Int, vararg emojis: String) = emojis.forEach { put(it, icon) }
        put(R.drawable.food_salsas, "🍅")
        put(R.drawable.food_verduras, "🥗", "🥦", "🥬", "🥕", "🫑", "🥒", "🍆", "🧅", "🧄", "🎃",
            "🌽", "🍄")
        put(R.drawable.food_snacks, "🍟", "🍿")
        put(R.drawable.food_tortitas, "🍘")
        put(R.drawable.food_fiambres, "🥪", "🥓")
        put(R.drawable.food_embutidos, "🌭")
        put(R.drawable.food_hamburguesas, "🍔")
        put(R.drawable.food_preparados, "🥙", "🌯", "🌮", "🥟", "🍛", "🥘", "🥧")
        put(R.drawable.food_pescado, "🍣", "🐟")
        put(R.drawable.food_granos, "🍙", "🍚")
        put(R.drawable.food_instantanea, "🍜", "🥣")
        put(R.drawable.food_pasta, "🍝")
        put(R.drawable.food_huevo, "🍳", "🥚")
        put(R.drawable.food_pollo, "🍗")
        put(R.drawable.food_res, "🥩")
        put(R.drawable.food_cerdo, "🍖")
        put(R.drawable.food_mariscos, "🦐", "🦀", "🦪", "🦑", "🐙")
        put(R.drawable.food_leche, "🥛")
        put(R.drawable.food_quesos, "🧀")
        put(R.drawable.food_untables, "🧈", "🍯")
        put(R.drawable.food_dulces, "🍬")
        put(R.drawable.food_galletas, "🍪")
        put(R.drawable.food_postres, "🍮")
        put(R.drawable.food_helados, "🍦")
        put(R.drawable.food_panaderia, "🥐", "🧇", "🥞", "🍩", "🧁", "🍰")
        put(R.drawable.food_panes, "🥖", "🥯", "🍞")
        put(R.drawable.food_harinas, "🌾")
        put(R.drawable.food_frutas, "🍎", "🍌", "🍊", "🍓", "🫐", "🍇", "🍑", "🍒", "🥝", "🍐",
            "🍉", "🍈", "🍍", "🥭", "🥥", "🍋", "🥑")
        put(R.drawable.food_tuberculos, "🥔")
        put(R.drawable.food_legumbres, "🫘", "🫛")
        put(R.drawable.food_frutos_secos, "🥜")
        put(R.drawable.food_soja, "🧊")
        put(R.drawable.food_chocolates, "🍫")
        put(R.drawable.food_aceites, "🫒")
        put(R.drawable.food_especias, "🧂")
        put(R.drawable.food_cafe, "☕")
        put(R.drawable.food_te, "🍵")
        put(R.drawable.food_alcohol, "🍷")
        put(R.drawable.food_cerveza, "🍺")
        put(R.drawable.food_bebidas, "💧", "🥤")
        put(R.drawable.food_zumo, "🧃")
    }

    /** Iconos que puede llevar una bebida. */
    private val DRINK_ICONS = setOf(
        R.drawable.food_veg_drinks, R.drawable.food_leche, R.drawable.food_yogurt,
        R.drawable.food_cafe, R.drawable.food_alcohol, R.drawable.food_bebidas,
        R.drawable.food_bebidas_lata, R.drawable.food_instantanea, R.drawable.food_infantil,
        R.drawable.food_cerveza, R.drawable.food_zumo, R.drawable.food_te,
    )

    /** true si `n` EMPIEZA por la palabra `k` (con plural; `*` final = raíz). */
    private fun startsWithWord(name: String, k: String): Boolean {
        val stem = k.endsWith("*")
        val key = TextMatch.normalize(k.removeSuffix("*"))
        // la cantidad delante no cuenta: "100% Proteína de soja", "2 x 4 x Helado…"
        val n = name.replaceFirst(LEADING_AMOUNT, "")
        if (!n.startsWith(key)) return false
        if (stem) return true
        val rest = n.substring(key.length).removePrefix("es").removePrefix("s")
        return rest.isEmpty() || !rest[0].isLetterOrDigit()
    }

    private fun first(n: String, table: List<Pair<String, Int>>, accept: (Int) -> Boolean = { true }) =
        table.firstOrNull { accept(it.second) && TextMatch.contains(n, it.first) }?.second

    /** true si justo antes de la posición `at` de `n` va la palabra `word` ("sin corteza"). */
    private fun afterWord(n: String, at: Int, word: String): Boolean =
        at > word.length && n.startsWith("$word ", at - word.length - 1) &&
            (at == word.length + 1 || n[at - word.length - 2] == ' ')

    /**
     * true si la palabra en `at` no dice qué es el producto sino lo que le falta o le acompaña:
     * "Pan SIN corteza", "Mortadela CON aceitunas", "Pepinillos SABOR anchoa", o cómo viene
     * preparado ("Calamar EN salsa americana", "Comida EN gelatina").
     */
    private fun secondary(n: String, at: Int, icon: Int): Boolean =
        afterWord(n, at, "sin") || afterWord(n, at, "sabor") ||
            // "con chocolate" / "con helado" sí dicen qué es: "Pistachos con chocolate", "Conos con helado"
            (afterWord(n, at, "con") && icon !in COATINGS) ||
            (afterWord(n, at, "a") && afterWord(n, at - 2, "sabor")) ||
            (icon in PREPARED_IN && afterWord(n, at, "en"))

    private val PREPARED_IN = setOf(R.drawable.food_salsas, R.drawable.food_postres)
    private val COATINGS = setOf(R.drawable.food_chocolates, R.drawable.food_helados)

    /** Como [first], pero sin contar las apariciones secundarias ([secondary]). */
    private fun firstMain(n: String, table: List<Pair<String, Int>>, accept: (Int) -> Boolean) =
        table.firstOrNull { (k, icon) ->
            accept(icon) && TextMatch.indicesOf(n, k).any { !secondary(n, it, icon) }
        }?.second

    /**
     * La clave que aparece antes en el nombre; a igual posición, la primera de la tabla. No cuenta
     * lo que va tras "sin" ("Pan sin corteza"), ni una salsa tras "en": es cómo viene preparado, no
     * qué es ("Calamar en salsa americana").
     */
    private fun earliest(n: String, table: List<Pair<String, Int>>, accept: (Int) -> Boolean): Int? {
        var best: Int? = null
        var bestAt = Int.MAX_VALUE
        for ((k, icon) in table) {
            if (!accept(icon)) continue
            val at = TextMatch.indicesOf(n, k).firstOrNull { !secondary(n, it, icon) } ?: continue
            if (at < bestAt) { best = icon; bestAt = at }
        }
        return best
    }

    /** Las palabras clave por niveles: infantil, qué es, ingrediente, sabor. */
    private fun keyword(n: String, accept: (Int) -> Boolean = { true }): Int? =
        first(n, infantKeywords, accept)
            ?: startKeywords.firstOrNull { accept(it.second) && startsWithWord(n, it.first) }?.second
            ?: first(n, strongKeywords, accept)
            ?: startAfterStrongKeywords.firstOrNull { accept(it.second) && startsWithWord(n, it.first) }?.second
            ?: earliest(n, productKeywords, accept)
            ?: firstMain(n, ingredientKeywords, accept)
            ?: first(n, flavorKeywords, accept)

    /**
     * Iconos más concretos que caben dentro de cada categoría. La categoría manda, pero si el nombre
     * dice algo más preciso de la MISMA familia se usa eso: "Suplementos" puede ser el bote de
     * proteína o la barrita; "Bebidas alcohólicas", la cerveza; "Dulces", la galleta o el flan.
     * Si el nombre apunta a otra familia, se queda la categoría: la eligió el usuario.
     */
    private val REFINE: Map<Int, Set<Int>> = mapOf(
        R.drawable.food_suplementos to setOf(R.drawable.food_proteina, R.drawable.food_barrita),
        R.drawable.food_chocolates to setOf(R.drawable.food_barrita, R.drawable.food_galletas),
        R.drawable.food_cereales to setOf(R.drawable.food_tortitas, R.drawable.food_barrita, R.drawable.food_galletas),
        R.drawable.food_snacks to setOf(R.drawable.food_tortitas, R.drawable.food_aceitunas,
            R.drawable.food_barrita, R.drawable.food_frutos_secos),
        R.drawable.food_panaderia to setOf(R.drawable.food_galletas),
        R.drawable.food_dulces to setOf(R.drawable.food_galletas, R.drawable.food_postres),
        R.drawable.food_yogurt to setOf(R.drawable.food_postres),
        R.drawable.food_bebidas to setOf(R.drawable.food_bebidas_lata, R.drawable.food_zumo),
        R.drawable.food_alcohol to setOf(R.drawable.food_cerveza),
        R.drawable.food_cafe to setOf(R.drawable.food_te),
        R.drawable.food_leche to setOf(R.drawable.food_infantil),
        R.drawable.food_fiambres to setOf(R.drawable.food_embutidos, R.drawable.food_cerdo),
        R.drawable.food_cerdo to setOf(R.drawable.food_embutidos),
        R.drawable.food_preparados to setOf(R.drawable.food_pizza),
        R.drawable.food_verduras to setOf(R.drawable.food_aceitunas),
    )

    fun resFor(name: String, drink: Boolean = isDrink(name), category: String? = null): Int {
        // "Otros" no dice nada: se decide por el nombre
        category?.let { byCategory[it] }?.takeIf { it != R.drawable.food_otros }?.let { byCat ->
            val byName = resFor(name, drink, null)
            return if (byName in REFINE[byCat].orEmpty()) byName else byCat
        }
        val n = TextMatch.normalize(name).trim()

        if (drink) {
            // Una bebida solo acepta iconos de bebida. Sin esto, "Bebida de canela y limón" casaba
            // con "canela" y salía con la hoja de especias.
            first(n, alcoholFirst)?.let { return it }
            keyword(n) { it in DRINK_ICONS }?.let { return it }
            first(n, byDrinkKeyword)?.let { return it }
            return byEmoji[foodEmoji(name, true, null)] ?: R.drawable.food_bebidas
        }

        if (DOSE.containsMatchIn(n)) return R.drawable.food_suplementos
        supplementStart.firstOrNull { (k, _) -> startsWithWord(n, k) }?.let { return it.second }
        keyword(n)?.let { return it }
        byEmoji[foodEmoji(name, false, null)]?.let { return it }
        first(n, lateKeywords)?.let { return it }
        return R.drawable.food_otros
    }
}
