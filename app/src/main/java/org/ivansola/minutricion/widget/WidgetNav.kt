package org.ivansola.minutricion.widget

import androidx.compose.runtime.mutableStateOf

/** Acción pendiente lanzada desde el widget de Registro Rápido ("search"|"scan"|"list"|"voice"). */
object WidgetNav {
    val pending = mutableStateOf<String?>(null)
}
