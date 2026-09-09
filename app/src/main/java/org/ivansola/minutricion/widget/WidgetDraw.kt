package org.ivansola.minutricion.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Utilidades compartidas por los widgets: colores, tamaño en px y tarjeta con degradado. */
internal object WidgetDraw {
    const val TEXT = 0xFFF5F5F7.toInt()
    const val SUB = 0xFF8E8E93.toInt()
    const val TRACK = 0xFF4C4C55.toInt()
    const val YELLOW = 0xFFFFC61A.toInt()
    const val GREEN = 0xFF30D158.toInt()
    const val RED = 0xFFE5484D.toInt()
    private const val GRAD_TOP = 0xFF2B2B31.toInt()
    private const val GRAD_BOT = 0xFF0A0A0C.toInt()

    // Geometría del anillo: hueco de 90° ABAJO (centrado en el sur), el relleno crece en
    // sentido horario desde abajo-izquierda. Ajustado a petición: antes lo tenía arriba.
    const val ARC_START = 135f
    const val ARC_SWEEP = 270f

    fun miles(n: Int) = "%,d".format(n).replace(",", ".")

    /** Banda aceptable 90–110 % del objetivo (igual que `Logic.kcalOk`). Sólo dentro de esta
     * banda el anillo va en verde y aparece la insignia con el check; fuera (por encima o por
     * debajo) va en amarillo y sin insignia. */
    fun inBand(ratio: Float) = ratio in 0.9f..1.1f

    fun bandColor(ratio: Float, inBand: Boolean = inBand(ratio)) = if (inBand) GREEN else YELLOW

    /** Posición en el recorrido del anillo (0–1), portado tal cual de `CalorieGauge.tof` en
     * Components.kt: NO es lineal — la banda 90–110 % ocupa t=0.35–0.65, así que al valor
     * exacto del objetivo le corresponde t=0.5 (el centro/arriba del anillo), no el final. */
    fun tOf(consumed: Double, target: Int): Double {
        if (target <= 0 || consumed <= 0.0) return 0.0
        val lo = target * 0.9; val hi = target * 1.1; val topV = target * 2.0
        val tLo = 0.35; val tHi = 0.65
        return when {
            consumed <= lo -> consumed / lo * tLo
            consumed <= hi -> tLo + (consumed - lo) / (hi - lo) * (tHi - tLo)
            else -> minOf(tHi + (consumed - hi) / (topV - hi) * (1.0 - tHi), 1.0)
        }
    }

    /** Dibuja el anillo (track gris + relleno de color) con la geometría del widget, más un
     * resplandor difuminado DEL PROPIO TRAZO relleno (no del check) — igual que el "glow" verde
     * que rodea la barra de color en el vídeo, no un halo pegado a la insignia. */
    fun ring(cv: Canvas, rect: RectF, strokeW: Float, frac: Float, fillColor: Int) {
        val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = strokeW; strokeCap = Paint.Cap.ROUND
        }
        arc.color = TRACK
        cv.drawArc(rect, ARC_START, ARC_SWEEP, false, arc)
        if (frac > 0f) {
            val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = strokeW * 1.15f; strokeCap = Paint.Cap.ROUND
                color = fillColor; alpha = 110
                maskFilter = android.graphics.BlurMaskFilter(strokeW * 0.5f, android.graphics.BlurMaskFilter.Blur.NORMAL)
            }
            cv.drawArc(rect, ARC_START, ARC_SWEEP * frac, false, glow)
            arc.color = fillColor
            cv.drawArc(rect, ARC_START, ARC_SWEEP * frac, false, arc)
        }
    }

    /** Dos marcas como LÍNEA RADIAL (perpendicular al anillo, cruzando su grosor) — los extremos
     * redondeados quedan arriba/abajo (a lo largo del radio) y los lados quedan rectos, no al
     * revés. Posición t=0.35/0.65 del recorrido (banda 90–110 %, igual que `CalorieGauge`).
     * Cada marca es gris por defecto y se vuelve blanca en cuanto el relleno del anillo ha
     * superado ese nivel (`t` del progreso actual >= 0.35/0.65). */
    fun ringLimitTicks(cv: Canvas, rect: RectF, strokeW: Float, t: Float) {
        val cx = (rect.left + rect.right) / 2f
        val cy = (rect.top + rect.bottom) / 2f
        val radius = rect.width() / 2f
        val tickW = strokeW * 0.30f
        // margen del borde a cada lado (izq. y der.) = casi el ancho de la propia marca, un poco
        // más ajustado que antes para que el borde en sí sea algo más pequeño.
        val sideMargin = tickW * 0.68f
        val cutW = tickW + sideMargin * 2f
        val cut = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CARD_MID; style = Paint.Style.FILL }
        // el borde se dibuja como un rectángulo redondeado (no una línea con capuchón) para poder
        // controlar el largo exacto y darle a sus esquinas una curva pequeña en vez de
        // completamente rectas. Un poco más largo que el grosor del anillo (en vez de exacto) para
        // que cubra el resplandor curvo por igual a ambos lados del radio, no solo por uno.
        val cutLen = strokeW * 1.12f
        val cutCorner = cutW * 0.16f
        val tickHalf = (strokeW - tickW) * 0.5f
        for (markT in listOf(0.35f, 0.65f)) {
            val angleDeg = (ARC_START + ARC_SWEEP * markT)
            val angleRad = Math.toRadians(angleDeg.toDouble())
            val dx = cos(angleRad).toFloat(); val dy = sin(angleRad).toFloat()
            val px = cx + dx * radius; val py = cy + dy * radius
            cv.save()
            cv.translate(px, py)
            cv.rotate(angleDeg)
            cv.drawRoundRect(-cutLen / 2f, -cutW / 2f, cutLen / 2f, cutW / 2f, cutCorner, cutCorner, cut)
            cv.restore()
            val tickColor = if (t >= markT) 0xFFFFFFFF.toInt() else SUB
            val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = tickColor; style = Paint.Style.STROKE
                strokeWidth = tickW; strokeCap = Paint.Cap.ROUND
            }
            cv.drawLine(cx + dx * (radius - tickHalf), cy + dy * (radius - tickHalf),
                        cx + dx * (radius + tickHalf), cy + dy * (radius + tickHalf), tick)
        }
    }

    private const val CARD_MID = 0xFF1C1C20.toInt()   // aprox. del degradado, para "cortar" el anillo
    private const val CHECK_DARK = 0xFF3A3A3D.toInt()  // color exacto del tick en CalorieGauge (Components.kt)

    /** Insignia (círculo + check), calcada de `CalorieGauge`: el CÍRCULO va del mismo color que
     * el relleno del anillo (verde en banda), con un borde oscuro tipo tarjeta — y es el TICK
     * (el símbolo del check) el que va en gris oscuro, no blanco. Sólo se debe llamar cuando
     * `t` cae dentro de la banda aceptable (`inBand`); se coloca en el punto real del recorrido
     * (t=0.5 cuando consumido == objetivo, el centro/arriba del anillo, no el final). */
    fun ringBadge(cv: Canvas, rect: RectF, t: Float, color: Int) {
        val cx = (rect.left + rect.right) / 2f
        val cy = (rect.top + rect.bottom) / 2f
        val radius = (rect.width() / 2f)
        val angleRad = Math.toRadians((ARC_START + ARC_SWEEP * t).toDouble())
        val bx = cx + radius * cos(angleRad).toFloat()
        val by = cy + radius * sin(angleRad).toFloat()
        // `br` es el radio del CÍRCULO (más grande a petición); `bs` es una referencia de tamaño
        // fija para el grosor del borde y el check, que NO deben crecer con el círculo.
        val br = rect.width() * 0.075f
        val bs = rect.width() * 0.058f
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        cv.drawCircle(bx, by, br, bg)
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = CARD_MID; style = Paint.Style.STROKE; strokeWidth = bs * 0.30f
        }
        cv.drawCircle(bx, by, br - border.strokeWidth / 2f, border)
        val check = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = CHECK_DARK; style = Paint.Style.STROKE
            strokeWidth = bs * 0.22f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        val path = android.graphics.Path().apply {
            moveTo(bx - bs * 0.32f, by)
            lineTo(bx - bs * 0.08f, by + bs * 0.24f)
            lineTo(bx + bs * 0.34f, by - bs * 0.26f)
        }
        cv.drawPath(path, check)
    }

    /** Tamaño del bitmap en px a partir de las opciones (dp) del widget, con límites sensatos. */
    fun sizePx(context: Context, mgr: AppWidgetManager, id: Int, minW: Int, minH: Int,
               maxW: Int, maxH: Int): Pair<Int, Int> {
        val opts = mgr.getAppWidgetOptions(id)
        val d = context.resources.displayMetrics.density
        val wdp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 150)
        val hdp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 150)
        val w = ((if (wdp > 0) wdp else 150) * d).roundToInt().coerceIn(minW, maxW)
        val h = ((if (hdp > 0) hdp else 150) * d).roundToInt().coerceIn(minH, maxH)
        return w to h
    }

    fun newCard(w: Int, h: Int): Pair<Bitmap, Canvas> {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), GRAD_TOP, GRAD_BOT, Shader.TileMode.CLAMP)
        cv.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), w * 0.11f, w * 0.11f, p)
        return bmp to cv
    }
}
