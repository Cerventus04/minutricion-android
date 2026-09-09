package org.ivansola.minutricion.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ivansola.minutricion.ui.theme.Pal

/**
 * Medidor circular (arco 270°) del Score Nutricional. El arco lleva el color (verde/amarillo/rojo);
 * el NÚMERO va en blanco y la ETIQUETA en gris, como en la app de escritorio/Fitia.
 */
@Composable
fun ScoreGauge(score: Int, color: Color, label: String, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 16.dp.toPx()
            val d = size.minDimension - stroke
            val tl = Offset((size.width - d) / 2f, (size.height - d) / 2f)
            val sz = Size(d, d)
            val start = 130f
            val full = 280f
            // mismo carril que el resto de indicadores (Card2 no se distinguía de la tarjeta)
            drawArc(Pal.Track, start, full, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
            val frac = (score / 100f).coerceIn(0f, 1f)
            if (frac > 0f) drawArc(color, start, full * frac, false, tl, sz,
                style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score", color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 46.sp)
            Text(label, color = Pal.Sub, fontSize = 13.sp)
        }
    }
}

private val WEEKDAYS_L = listOf("L", "M", "M", "J", "V", "S", "D")

/**
 * Barras de kcal de la semana estilo escritorio: barra fina por día con color por adherencia
 * (verde en banda lo–hi, amarillo por debajo, rojo por encima), un punto gris en los días sin
 * registro, y la letra del día debajo (resaltada la de hoy).
 */
@Composable
fun WeekBars(
    values: List<Int>,
    maxV: Int,
    todayIndex: Int,
    lo: Int,
    hi: Int,
    modifier: Modifier = Modifier,
) {
    val labelPaint = remember {
        android.graphics.Paint().apply {
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
    }
    Canvas(modifier) {
        val n = values.size
        if (n == 0 || maxV <= 0) return@Canvas
        val lblH = size.height * 0.20f
        val chartH = size.height - lblH
        val slot = size.width / n
        val bw = slot * 0.30f
        val r = bw / 2f
        labelPaint.textSize = size.height * 0.14f
        for (i in 0 until n) {
            val cx = slot * (i + 0.5f)
            val v = values[i]
            if (v > 0) {
                val col = when {
                    lo > 0 && hi > 0 && v > hi -> Pal.Red
                    lo > 0 && v < lo -> Pal.Yellow
                    lo > 0 && hi > 0 -> Pal.Green
                    i == todayIndex -> Pal.Yellow
                    else -> Pal.Sub
                }
                val bh = ((v.toFloat() / maxV).coerceIn(0f, 1f) * (chartH - r)) + r
                drawRoundRect(col, Offset(cx - bw / 2, chartH - bh), Size(bw, bh),
                    CornerRadius(r, r))
            } else {
                drawCircle(Pal.Card2, r * 0.5f, Offset(cx, chartH - r * 0.5f))
            }
            labelPaint.color = (if (i == todayIndex) Pal.Text else Pal.Sub).toArgb()
            drawContext.canvas.nativeCanvas.drawText(
                WEEKDAYS_L[i % 7], cx, size.height - lblH * 0.30f, labelPaint,
            )
        }
    }
}

/**
 * Barras de calorías del detalle: valor encima de cada barra, rejilla/eje Y a la derecha y letra
 * del día debajo. Color por adherencia (lo–hi). Usada en CaloriesDetailScreen.
 */
@Composable
fun CalorieBars(
    values: List<Int>,
    lo: Int,
    hi: Int,
    todayIndex: Int,
    modifier: Modifier = Modifier,
) {
    val axisPaint = remember {
        android.graphics.Paint().apply { textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true }
    }
    val valPaint = remember {
        android.graphics.Paint().apply {
            textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true; isFakeBoldText = true
        }
    }
    val dayPaint = remember {
        android.graphics.Paint().apply { textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true }
    }
    Canvas(modifier) {
        val n = values.size
        if (n == 0) return@Canvas
        val rightM = size.width * 0.13f
        val topM = size.height * 0.11f
        val lblH = size.height * 0.10f
        val plotL = size.width * 0.01f
        val plotR = size.width - rightM
        val chartTop = topM
        val chartBot = size.height - lblH
        val ch = chartBot - chartTop
        val rawMax = maxOf((values.maxOrNull() ?: 0), hi, 1)
        val nm = (Math.ceil(rawMax / 500.0) * 500).toInt().coerceAtLeast(500)
        axisPaint.color = Pal.Sub.toArgb(); axisPaint.textSize = size.height * 0.058f
        for (k in 0..4) {
            val frac = k / 4f
            val gy = chartBot - ch * frac
            drawLine(Color(0xFF26262C), Offset(plotL, gy), Offset(plotR, gy), 1f)
            drawContext.canvas.nativeCanvas.drawText(
                "%,d".format((nm * frac).toInt()).replace(",", "."),
                plotR + rightM * 0.52f, gy + axisPaint.textSize * 0.35f, axisPaint,
            )
        }
        val slot = (plotR - plotL) / n
        val bw = slot * 0.34f
        val r = bw / 2f
        valPaint.textSize = size.height * 0.062f
        dayPaint.color = Pal.Sub.toArgb(); dayPaint.textSize = size.height * 0.07f
        for (i in 0 until n) {
            val cx = plotL + slot * (i + 0.5f)
            val v = values[i]
            if (v > 0) {
                val col = when {
                    lo > 0 && hi > 0 && v > hi -> Pal.Red
                    lo > 0 && v >= lo -> Pal.Green
                    lo > 0 -> Pal.Yellow
                    else -> Pal.Yellow
                }
                val bh = (v.toFloat() / nm).coerceIn(0f, 1f) * ch
                val top = chartBot - bh
                // Barra con TOP redondeado y BASE PLANA (como Kivy: corners top sí, bottom no).
                val x = cx - bw / 2
                val rr = minOf(r, bh / 2f)
                val bar = Path().apply {
                    moveTo(x, chartBot)
                    lineTo(x, top + rr)
                    quadraticBezierTo(x, top, x + rr, top)
                    lineTo(x + bw - rr, top)
                    quadraticBezierTo(x + bw, top, x + bw, top + rr)
                    lineTo(x + bw, chartBot)
                    close()
                }
                drawPath(bar, col)
                // número encima de la barra en BLANCO (como Kivy).
                valPaint.color = Pal.Text.toArgb()
                drawContext.canvas.nativeCanvas.drawText(
                    "%,d".format(v).replace(",", "."), cx, top - valPaint.textSize * 0.4f, valPaint,
                )
            }
            dayPaint.color = (if (i == todayIndex) Pal.Text else Pal.Sub).toArgb()
            drawContext.canvas.nativeCanvas.drawText(
                WEEKDAYS_L[i % 7], cx, size.height - lblH * 0.15f, dayPaint,
            )
        }
    }
}

/**
 * Gráfica de evolución del peso estilo Fitia: serie real (línea AMARILLA con relleno degradado
 * hacia abajo), media móvil (línea AZUL discontinua), eje Y a la derecha y fechas (día del mes)
 * en el eje X inferior. Se pasan las fechas para etiquetar el eje X.
 */
@Composable
fun WeightChart(
    series: List<Double>,
    ema: List<Double>,
    modifier: Modifier = Modifier,
    dates: List<String> = emptyList(),
) {
    val axisPaint = remember {
        android.graphics.Paint().apply { isAntiAlias = true }
    }
    Canvas(modifier) {
        if (series.size < 2) return@Canvas
        val all = series + ema
        val minV = all.min()
        val maxV = all.max()
        val range = (maxV - minV).coerceAtLeast(0.1)
        val n = series.size
        val rightM = size.width * 0.12f
        val padY = 10.dp.toPx()
        val lblH = if (dates.isNotEmpty()) 16.dp.toPx() else 0f
        val plotR = size.width - rightM
        val chartTop = padY
        val chartBot = size.height - padY - lblH
        val ch = chartBot - chartTop
        fun pt(i: Int, v: Double): Offset {
            val x = plotR * i / (n - 1)
            val y = chartTop + ch * (1f - ((v - minV) / range).toFloat())
            return Offset(x, y)
        }

        // Rejilla + etiquetas del eje Y (derecha): 4 tramos con valores redondeados.
        axisPaint.color = Pal.Sub.toArgb()
        axisPaint.textSize = size.height * 0.075f
        axisPaint.textAlign = android.graphics.Paint.Align.LEFT
        for (k in 0..3) {
            val frac = k / 3f
            val gy = chartBot - ch * frac
            drawLine(Color(0xFF202026), Offset(0f, gy), Offset(plotR, gy), 1f)
            val label = "%.1f".format(minV + range * frac).replace(".", ",")
            drawContext.canvas.nativeCanvas.drawText(
                label, plotR + rightM * 0.16f, gy + axisPaint.textSize * 0.35f, axisPaint,
            )
        }

        // Relleno degradado bajo la línea real (amarillo → transparente).
        val fill = Path()
        series.forEachIndexed { i, v ->
            val o = pt(i, v)
            if (i == 0) fill.moveTo(o.x, o.y) else fill.lineTo(o.x, o.y)
        }
        fill.lineTo(pt(n - 1, series.last()).x, chartBot)
        fill.lineTo(pt(0, series.first()).x, chartBot)
        fill.close()
        drawPath(fill, brush = androidx.compose.ui.graphics.Brush.verticalGradient(
            0f to Pal.Yellow.copy(alpha = 0.35f),
            1f to Pal.Yellow.copy(alpha = 0f),
            startY = chartTop, endY = chartBot,
        ))

        // Línea real (amarilla sólida).
        val real = Path()
        series.forEachIndexed { i, v ->
            val o = pt(i, v)
            if (i == 0) real.moveTo(o.x, o.y) else real.lineTo(o.x, o.y)
        }
        drawPath(real, Pal.Yellow, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Media móvil (azul discontinua).
        if (ema.size >= 2) {
            val avg = Path()
            ema.forEachIndexed { i, v ->
                val o = pt(i, v)
                if (i == 0) avg.moveTo(o.x, o.y) else avg.lineTo(o.x, o.y)
            }
            drawPath(avg, Pal.Avg, style = Stroke(
                2.dp.toPx(), cap = StrokeCap.Round,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                    floatArrayOf(10f, 8f), 0f),
            ))
        }

        // Punto final de la serie real.
        val lastPt = pt(n - 1, series.last())
        drawCircle(Pal.Yellow, 4.dp.toPx(), lastPt)
        drawCircle(Pal.Bg, 2.dp.toPx(), lastPt)

        // Fechas (día del mes) en el eje X inferior.
        if (dates.isNotEmpty()) {
            axisPaint.textAlign = android.graphics.Paint.Align.CENTER
            val step = (n / 4).coerceAtLeast(1)
            var i = 0
            while (i < n) {
                val d = dates[i].substringAfterLast('-').trimStart('0')
                val x = (plotR * i / (n - 1)).coerceIn(axisPaint.textSize, plotR - axisPaint.textSize * 0.5f)
                drawContext.canvas.nativeCanvas.drawText(d, x, size.height - lblH * 0.15f, axisPaint)
                i += step
            }
        }
    }
}

/**
 * Barra de tramos (detalle de nutriente): N segmentos redondeados, TODOS en gris salvo el
 * segmento activo, que lleva su color. Sin marcador (el propio tramo coloreado indica la zona).
 */
@Composable
fun SegBar(colors: List<Color>, activeIndex: Int, modifier: Modifier = Modifier) {
    val gray = Color(0xFF3A3A42)
    Canvas(modifier.fillMaxWidth().height(12.dp)) {
        val n = colors.size
        if (n == 0 || size.width < 4) return@Canvas
        val gap = 3.dp.toPx()
        val segW = (size.width - gap * (n - 1)) / n
        val h = size.height
        val r = CornerRadius(h / 2f, h / 2f)
        var x = 0f
        for (i in 0 until n) {
            val col = if (i == activeIndex) colors[i] else gray
            drawRoundRect(col, Offset(x, 0f), Size(segW, h), r)
            x += segW + gap
        }
    }
}

/** Fila de macro (Progreso): nombre · valor a la derecha · barra fina con su color. */
@Composable
fun MacroRowBar(name: String, value: String, frac: Float, color: Color) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, color = Pal.Text, fontSize = 12.sp)
            Text(value, color = Pal.Sub, fontSize = 12.sp)
        }
        Spacer(Modifier.height(5.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Pal.Track)) {
            Box(
                Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).fillMaxHeight()
                    .clip(CircleShape).background(color)
            )
        }
    }
}
