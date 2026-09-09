package org.ivansola.minutricion

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.ivansola.minutricion.data.Db
import org.ivansola.minutricion.ui.App
import org.ivansola.minutricion.ui.theme.MiNutricionTheme
import org.ivansola.minutricion.widget.CaloriesWidget
import org.ivansola.minutricion.widget.MacroWidget
import org.ivansola.minutricion.widget.NutrientsWidget
import org.ivansola.minutricion.widget.RemainingWidget
import org.ivansola.minutricion.widget.StreakLoggedWidget
import org.ivansola.minutricion.widget.StreakPerfectWidget
import org.ivansola.minutricion.widget.WidgetNav

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Db.init(applicationContext)
        org.ivansola.minutricion.data.Backfill.runAsync()
        handleWidgetAction(intent)
        setContent {
            MiNutricionTheme {
                App()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetAction(intent)
    }

    /** Atajo del widget de Registro Rápido: la UI (Diario) lo consume y abre buscar/escanear. */
    private fun handleWidgetAction(intent: Intent?) {
        intent?.getStringExtra("widget_action")?.let { WidgetNav.pending.value = it }
    }

    /** Refresca todos los widgets al salir de la app (tras registrar comida). */
    override fun onStop() {
        super.onStop()
        MacroWidget.updateAll(applicationContext)
        CaloriesWidget.updateAll(applicationContext)
        NutrientsWidget.updateAll(applicationContext)
        RemainingWidget.updateAll(applicationContext)
        StreakLoggedWidget.updateAll(applicationContext)
        StreakPerfectWidget.updateAll(applicationContext)
    }
}
