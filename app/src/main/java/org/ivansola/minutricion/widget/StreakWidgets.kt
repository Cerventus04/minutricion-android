package org.ivansola.minutricion.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
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

/** Dibuja un widget de racha: cuadro con degradado del color, llama (emoji) + número + etiqueta. */
private fun drawStreak(context: Context, w: Int, h: Int, value: Int, label: String,
                       emoji: String, tint: Int): Bitmap {
    val bold = runCatching { ResourcesCompat.getFont(context, R.font.nunito_bold) }.getOrNull()
        ?: Typeface.DEFAULT_BOLD
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val cv = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    // degradado: color tenue (oscurecido, OPACO) arriba -> casi negro abajo
    fun darken(c: Int, f: Float): Int {
        val r = ((c shr 16 and 0xFF) * f).toInt()
        val g = ((c shr 8 and 0xFF) * f).toInt()
        val b = ((c and 0xFF) * f).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
    p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), darken(tint, 0.42f), 0xFF0A0A0C.toInt(), Shader.TileMode.CLAMP)
    cv.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), w * 0.11f, w * 0.11f, p)
    p.shader = null

    val cx = w / 2f
    // fila llama + número
    val numP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = WidgetDraw.TEXT; typeface = bold; textAlign = Paint.Align.LEFT; textSize = h * 0.26f
    }
    val emoP = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.LEFT; textSize = h * 0.24f }
    val numStr = value.toString()
    val numW = numP.measureText(numStr)
    val emW = emoP.measureText(emoji)
    val gap = h * 0.04f
    val totalW = emW + gap + numW
    val startX = cx - totalW / 2f
    val rowY = h * 0.50f
    cv.drawText(emoji, startX, rowY, emoP)
    cv.drawText(numStr, startX + emW + gap, rowY, numP)
    // etiqueta
    val labP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = WidgetDraw.TEXT; typeface = bold; textAlign = Paint.Align.CENTER; textSize = h * 0.10f
    }
    cv.drawText(label, cx, h * 0.68f, labP)
    return bmp
}

private fun pending(context: Context, req: Int) = PendingIntent.getActivity(
    context, req,
    Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    },
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

/** Widget "Racha de Días Registrados" (llama amarilla). 2x2. */
class StreakLoggedWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) { ids.forEach { render(context, mgr, it) } }
    override fun onAppWidgetOptionsChanged(context: Context, mgr: AppWidgetManager, id: Int, o: Bundle) = render(context, mgr, id)
    companion object {
        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context) ?: return
            mgr.getAppWidgetIds(ComponentName(context, StreakLoggedWidget::class.java)).forEach { render(context, mgr, it) }
        }
        fun render(context: Context, mgr: AppWidgetManager, id: Int) {
            Db.init(context.applicationContext)
            val (w, h) = WidgetDraw.sizePx(context, mgr, id, 220, 220, 600, 600)
            val v = Logic.streaks().regCur
            val views = RemoteViews(context.packageName, R.layout.widget_cal_macros)
            views.setImageViewBitmap(R.id.widget_img, drawStreak(context, w, h, v, "Día de Racha", "🔥", WidgetDraw.YELLOW))
            views.setOnClickPendingIntent(R.id.widget_img, pending(context, 4))
            mgr.updateAppWidget(id, views)
        }
    }
}

/** Widget "Racha de Días Perfectos" (llama verde). 2x2. */
class StreakPerfectWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) { ids.forEach { render(context, mgr, it) } }
    override fun onAppWidgetOptionsChanged(context: Context, mgr: AppWidgetManager, id: Int, o: Bundle) = render(context, mgr, id)
    companion object {
        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context) ?: return
            mgr.getAppWidgetIds(ComponentName(context, StreakPerfectWidget::class.java)).forEach { render(context, mgr, it) }
        }
        fun render(context: Context, mgr: AppWidgetManager, id: Int) {
            Db.init(context.applicationContext)
            val (w, h) = WidgetDraw.sizePx(context, mgr, id, 220, 220, 600, 600)
            val v = Logic.streaks().perfCur
            val views = RemoteViews(context.packageName, R.layout.widget_cal_macros)
            views.setImageViewBitmap(R.id.widget_img, drawStreak(context, w, h, v, "Racha Perfecta", "🔥", WidgetDraw.GREEN))
            views.setOnClickPendingIntent(R.id.widget_img, pending(context, 5))
            mgr.updateAppWidget(id, views)
        }
    }
}
