package org.ivansola.minutricion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import org.ivansola.minutricion.ui.screens.WeightSheetContent
import org.ivansola.minutricion.ui.theme.MiNutricionTheme
import org.ivansola.minutricion.ui.theme.Pal
import org.junit.Rule
import org.junit.Test

/** La hoja "Actualizar peso" tal como sale sobre la pantalla (la hoja modal no se pinta en la JVM). */
class WeightSheetTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT),
        theme = "android:Theme.Material.NoActionBar",
    )

    @Test
    fun hojaPeso() {
        paparazzi.snapshot {
            MiNutricionTheme {
                Box(Modifier.fillMaxSize().background(Color(0xFF050506)), contentAlignment = Alignment.BottomCenter) {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                            .background(Pal.Bg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // asa de la hoja, como la dibuja ModalBottomSheet
                        Box(Modifier.padding(vertical = 14.dp).size(width = 32.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp)).background(Pal.Sub.copy(alpha = 0.4f)))
                        WeightSheetContent(current = 67.85, lastDay = "2026-09-15", onSave = {})
                    }
                }
            }
        }
    }
}
