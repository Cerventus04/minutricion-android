package org.ivansola.minutricion.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.widget.RemoteViews
import androidx.core.content.res.ResourcesCompat
import org.ivansola.minutricion.MainActivity
import org.ivansola.minutricion.R
import org.ivansola.minutricion.data.Db
import org.ivansola.minutricion.data.Logic
import java.time.LocalDate
import kotlin.math.min
import kotlin.math.roundToInt

/** Widget "Calorías Consumidas" (estilo Fitia): anillo con las kcal consumidas del día. 2x2. */
class CaloriesWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) render(context, mgr, id)
    }
    override fun onAppWidgetOptionsChanged(context: Context, mgr: AppWidgetManager, id: Int, o: Bundle) {
        render(context, mgr, id)
    }

    companion object {
        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context) ?: return
            for (id in mgr.getAppWidgetIds(ComponentName(context, CaloriesWidget::class.java)))
                render(context, mgr, id)
        }

        private fun render(context: Context, mgr: AppWidgetManager, id: Int) {
            Db.init(context.applicationContext)
            val (w, h) = WidgetDraw.sizePx(context, mgr, id, 220, 220, 600, 600)
            val bmp = draw(context, w, h)
            val views = RemoteViews(context.packageName, R.layout.widget_cal_macros)
            views.setImageViewBitmap(R.id.widget_img, bmp)
            val pi = PendingIntent.getActivity(
                context, 1,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_img, pi)
            mgr.updateAppWidget(id, views)
        }

        fun draw(context: Context, w: Int, h: Int): Bitmap {
            val tgt = Logic.targets()?.kcal ?: 0
            val consumed = Logic.daySummary(LocalDate.now().toString()).kcal.roundToInt()
            val inBand = tgt > 0 && Logic.kcalOk(consumed.toDouble(), tgt.toDouble())
            val fill = if (inBand) WidgetDraw.GREEN else WidgetDraw.YELLOW
            val t = WidgetDraw.tOf(consumed.toDouble(), tgt).toFloat()

            val bold = runCatching { ResourcesCompat.getFont(context, R.font.nunito_bold) }.getOrNull()
                ?: Typeface.DEFAULT_BOLD
            val reg = runCatching { ResourcesCompat.getFont(context, R.font.nunito_regular) }.getOrNull()
                ?: Typeface.DEFAULT

            val (bmp, cv) = WidgetDraw.newCard(w, h)
            val cx = w / 2f; val cy = h * 0.52f
            val r = min(w, h) * 0.38f
            val sw = r * 0.16f
            val rect = RectF(cx - r, cy - r, cx + r, cy + r)
            WidgetDraw.ring(cv, rect, sw, t, fill)
            WidgetDraw.ringLimitTicks(cv, rect, sw, t)
            if (inBand) WidgetDraw.ringBadge(cv, rect, t, fill)
            // textos centrales (con margen de sobra entre líneas: el tamaño de fuente real de
            // cada móvil varía, y con poco margen se solapaban "kcal" con el número).
            val lab = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = WidgetDraw.TEXT; typeface = reg; textAlign = Paint.Align.CENTER; textSize = r * 0.26f
            }
            cv.drawText("kcal", cx, cy - r * 0.30f, lab)
            val num = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = WidgetDraw.TEXT; typeface = bold; textAlign = Paint.Align.CENTER; textSize = r * 0.48f
            }
            cv.drawText(WidgetDraw.miles(consumed), cx, cy + r * 0.24f, num)
            val emo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER; textSize = r * 0.28f
            }
            cv.drawText("⚡", cx, cy + r * 0.80f, emo)
            return bmp
        }
    }
}
