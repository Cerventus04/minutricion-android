package org.ivansola.minutricion.ui

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.ivansola.minutricion.R
import org.ivansola.minutricion.ui.components.MdiIcon
import org.ivansola.minutricion.ui.screens.AjustesScreen
import org.ivansola.minutricion.ui.screens.DiarioScreen
import org.ivansola.minutricion.ui.screens.ProgresoScreen
import org.ivansola.minutricion.ui.theme.Pal

private enum class Tab(val label: String, val icon: String) {
    Diario("Diario", "book-open-variant"),
    Progreso("Progreso", "chart-line"),
    Ajustes("Ajustes", "cog"),
}

/** Indication vacía: elimina el "ripple" de TODOS los `clickable` de la app. */
private object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        object : Modifier.Node(), DrawModifierNode {
            override fun ContentDrawScope.draw() = drawContent()
        }
    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = -1
}

@Composable
fun App() {
    var tab by remember { mutableStateOf(Tab.Diario) }

    CompositionLocalProvider(LocalIndication provides NoIndication) {
        Scaffold(
            containerColor = Pal.Bg,
            bottomBar = { BottomBar(tab) { tab = it } },
        ) { inner ->
            Box(Modifier.fillMaxSize().background(Pal.Bg)) {
                when (tab) {
                    Tab.Diario -> DiarioScreen(inner)
                    Tab.Progreso -> ProgresoScreen(inner)
                    Tab.Ajustes -> AjustesScreen(inner, onSaved = { tab = Tab.Diario })
                }
            }
        }
    }
}

@Composable
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Pal.Card)
            .navigationBarsPadding(),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Pal.Border))
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Tab.entries.forEach { t ->
                NavItem(t, t == current, Modifier.weight(1f)) { onSelect(t) }
            }
        }
    }
}

@Composable
private fun NavItem(tab: Tab, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color = if (active) Pal.Yellow else Pal.Sub
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(18.dp))
                .background(if (active) Pal.Yellow.copy(alpha = 0.16f) else Color.Transparent)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MdiIcon(tab.icon, size = 20.dp, color = color)
            Spacer(Modifier.width(6.dp))
            Text(tab.label, color = color, fontSize = 12.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
        }
    }
}
