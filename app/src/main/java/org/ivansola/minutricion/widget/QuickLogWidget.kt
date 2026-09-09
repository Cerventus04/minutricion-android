package org.ivansola.minutricion.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import org.ivansola.minutricion.MainActivity
import org.ivansola.minutricion.R

/** Widget "Registro Rápido" (estilo Fitia): 4 atajos que abren la app en la acción elegida. 2x2. */
class QuickLogWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) render(context, mgr, id)
    }
    override fun onAppWidgetOptionsChanged(context: Context, mgr: AppWidgetManager, id: Int, o: Bundle) {
        render(context, mgr, id)
    }

    companion object {
        private fun action(context: Context, req: Int, action: String): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("widget_action", action)
                // data única para que cada atajo tenga su propio PendingIntent
                setData(android.net.Uri.parse("minutricion://widget/$action"))
            }
            return PendingIntent.getActivity(
                context, req, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun render(context: Context, mgr: AppWidgetManager, id: Int) {
            // igual que en Fitia: ancho -> barra de búsqueda + 3 iconos; estrecho -> rejilla 2x2.
            val opts = mgr.getAppWidgetOptions(id)
            val wdp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110)
            val wide = wdp >= 180
            val layout = if (wide) R.layout.widget_quicklog_wide else R.layout.widget_quicklog
            val views = RemoteViews(context.packageName, layout)
            views.setOnClickPendingIntent(R.id.w_list, action(context, 10, "list"))
            views.setOnClickPendingIntent(R.id.w_search, action(context, 11, "search"))
            views.setOnClickPendingIntent(R.id.w_camera, action(context, 12, "scan"))
            views.setOnClickPendingIntent(R.id.w_mic, action(context, 13, "voice"))
            mgr.updateAppWidget(id, views)
        }
    }
}
