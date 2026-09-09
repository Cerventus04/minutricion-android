package org.ivansola.minutricion.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Bundle
import android.widget.RemoteViews
import androidx.core.content.res.ResourcesCompat
import org.ivansola.minutricion.MainActivity
import org.ivansola.minutricion.R
import org.ivansola.minutricion.data.Db
import org.ivansola.minutricion.data.Logic
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Widget de pantalla de inicio "Calorías y Macros" (estilo Fitia): barra curva de kcal
 * (consumido/objetivo) + tres barritas P/C/G. Se dibuja como bitmap con Canvas para
 * controlar el aspecto y se coloca en un ImageView. Lee la misma nutricion.db.
 */
class MacroWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) render(context, mgr, id)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle,
    ) {
        render(context, mgr, id)
    }

    companion object {
        private const val BG = 0xFF19191C.toInt()
        private const val CARD = 0xFF0F0F11.toInt()
        private const val GRAD_TOP = 0xFF2B2B31.toInt()
        private const val GRAD_BOT = 0xFF0A0A0C.toInt()
        private const val TEXT = 0xFFF5F5F7.toInt()
        private const val SUB = 0xFF8E8E93.toInt()
        private const val TRACK = 0xFF4C4C55.toInt()
        private const val YELLOW = 0xFFFFC61A.toInt()
        private const val RED = 0xFFE5484D.toInt()

        /** Redibuja TODOS los widgets colocados (llamar tras registrar comida). */
        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context) ?: return
            val ids = mgr.getAppWidgetIds(ComponentName(context, MacroWidget::class.java))
            for (id in ids) render(context, mgr, id)
        }

        private fun miles(n: Int) = "%,d".format(n).replace(",", ".")

        private fun render(context: Context, mgr: AppWidgetManager, id: Int) {
            Db.init(context.applicationContext)

            val opts = mgr.getAppWidgetOptions(id)
            val d = context.resources.displayMetrics.density
            val minWdp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 300)
            val minHdp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 120)
            val w = ((if (minWdp > 0) minWdp else 300) * d).roundToInt().coerceIn(300, 1400)
            val h = ((if (minHdp > 0) minHdp else 120) * d).roundToInt().coerceIn(140, 700)

            val bmp = draw(context, w, h)

            val views = RemoteViews(context.packageName, R.layout.widget_cal_macros)
            views.setImageViewBitmap(R.id.widget_img, bmp)

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_img, pi)
            mgr.updateAppWidget(id, views)
        }

        private fun draw(context: Context, w: Int, h: Int): Bitmap {
            val today = LocalDate.now().toString()
            val tgt = Logic.targets()
            val sum = Logic.daySummary(today)

            val bold = runCatching { ResourcesCompat.getFont(context, R.font.nunito_bold) }.getOrNull()
                ?: Typeface.DEFAULT_BOLD
            val reg = runCatching { ResourcesCompat.getFont(context, R.font.nunito_regular) }.getOrNull()
                ?: Typeface.DEFAULT

            data class Row(val label: String, val cur: Int, val tot: Int, val unit: String)
            val rows = listOf(
                Row("kcal", sum.kcal.roundToInt(), tgt?.kcal ?: 0, ""),
                Row("Proteínas", sum.protein.roundToInt(), tgt?.protein ?: 0, ""),
                Row("Carbs", sum.carbs.roundToInt(), tgt?.carbs ?: 0, ""),
                Row("Grasas", sum.fat.roundToInt(), tgt?.fat ?: 0, ""),
            )

            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val cv = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)

            // tarjeta redondeada con degradado vertical (claro arriba -> oscuro abajo)
            val radius = w * 0.11f
            p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), GRAD_TOP, GRAD_BOT, Shader.TileMode.CLAMP)
            cv.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius, p)
            p.shader = null

            val padX = w * 0.06f
            val padY = h * 0.06f
            val left = padX; val right = w - padX
            val innerW = right - left
            val rowH = (h - 2 * padY) / rows.size

            val labelP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = TEXT; typeface = bold; textAlign = Paint.Align.LEFT; textSize = rowH * 0.30f
            }
            val valP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = TEXT; typeface = bold; textAlign = Paint.Align.RIGHT; textSize = rowH * 0.31f
            }

            rows.forEachIndexed { i, r ->
                val top = padY + i * rowH
                val textY = top + rowH * 0.40f
                cv.drawText(r.label, left, textY, labelP)
                val valTxt = if (r.tot > 0) "${miles(r.cur)} / ${miles(r.tot)}" else miles(r.cur)
                cv.drawText(valTxt, right, textY, valP)

                // barra fina bajo la etiqueta
                val barY = top + rowH * 0.62f
                val barH = rowH * 0.17f
                p.color = TRACK
                cv.drawRoundRect(RectF(left, barY, right, barY + barH), barH, barH, p)
                val frac = if (r.tot > 0) (r.cur.toFloat() / r.tot).coerceIn(0f, 1f) else 0f
                if (frac > 0f) {
                    val over = r.tot > 0 && r.cur > r.tot * 1.1f
                    p.color = if (over) RED else YELLOW
                    cv.drawRoundRect(RectF(left, barY, left + innerW * frac, barY + barH), barH, barH, p)
                }
            }

            return bmp
        }
    }
}
