package com.tolete.player.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Aba da barra de vidro: [key] identifica a aba, [icon]/[label] aparecem na barra. */
data class GlassTab<T>(
    val key: T,
    val icon: ImageVector,
    val label: String
)

private val navItems = listOf(
    GlassTab(MusicViewModel.Tab.HOME, Icons.Rounded.Home, "Início"),
    GlassTab(MusicViewModel.Tab.ALL, Icons.Rounded.LibraryMusic, "Todas"),
    GlassTab(MusicViewModel.Tab.ARTISTS, Icons.Rounded.Person, "Artistas"),
    GlassTab(MusicViewModel.Tab.ALBUMS, Icons.Rounded.Album, "Álbuns"),
    GlassTab(MusicViewModel.Tab.GENRES, Icons.Rounded.Category, "Gêneros")
)

/**
 * Barra de navegação "liquid glass".
 *
 * A pílula de vidro é uma lente de verdade (refração, dispersão e ampliação) que pode ser
 * ARRASTADA: segure nela e deslize, ou arraste em qualquer ponto da barra. Ao soltar ela
 * encaixa na aba mais próxima (considerando a velocidade do gesto) e a aba é selecionada.
 * Enquanto está sendo segurada ela cresce, estica com a velocidade e amplia os ícones.
 */
@Composable
fun LiquidGlassNavigation(
    currentTab: MusicViewModel.Tab,
    onTabSelected: (MusicViewModel.Tab) -> Unit,
    backdrop: GraphicsLayer,
    modifier: Modifier = Modifier
) {
    LiquidGlassTabBar(
        items = navItems,
        selected = currentTab,
        onSelected = onTabSelected,
        backdrop = backdrop,
        modifier = modifier
    )
}

/**
 * Versão genérica da barra "liquid glass": usada pela navegação principal e por outras telas
 * (ex.: dashboard do Last.fm) com qualquer conjunto de abas.
 */
@Composable
fun <T> LiquidGlassTabBar(
    items: List<GlassTab<T>>,
    selected: T,
    onSelected: (T) -> Unit,
    backdrop: GraphicsLayer,
    modifier: Modifier = Modifier
) {
    val navItems = items
    val currentTab = selected
    val onTabSelected = onSelected
    val selectedIndex = navItems.indexOfFirst { it.key == currentTab }.coerceAtLeast(0)
    val currentSelected by rememberUpdatedState(selectedIndex)
    val currentOnSelect by rememberUpdatedState(onTabSelected)
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    GlassSurface(
        backdrop = backdrop,
        shape = RoundedCornerShape(32.dp),
        style = GlassStyle.Bar,
        modifier = modifier.fillMaxWidth().height(64.dp)
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val count = navItems.size
            val barW = constraints.maxWidth.toFloat()
            val barH = constraints.maxHeight.toFloat()
            val itemW = barW / count

            val insetX = with(density) { 4.dp.toPx() }
            val insetY = with(density) { 6.dp.toPx() }
            val edgeMin = with(density) { 2.dp.toPx() }
            val growW = with(density) { 12.dp.toPx() }
            val growH = with(density) { 6.dp.toPx() }
            val stretchMax = with(density) { 10.dp.toPx() }
            val baseW = itemW - insetX * 2f
            val baseH = barH - insetY * 2f

            fun centerOf(index: Int) = itemW * (index + 0.5f)

            // Posição (centro X da pílula, em px relativos à barra)
            val centerAnim = remember { Animatable(centerOf(selectedIndex)) }
            var dragging by remember { mutableStateOf(false) }
            var dragCenter by remember { mutableFloatStateOf(0f) }
            var pressed by remember { mutableStateOf(false) }
            var dragVelocity by remember { mutableFloatStateOf(0f) }
            var pendingVelocity by remember { mutableFloatStateOf(0f) }

            // Troca de aba (toque, arrasto ou mudança externa): a pílula desliza até lá
            LaunchedEffect(selectedIndex, itemW) {
                if (!dragging) {
                    val v = pendingVelocity
                    pendingVelocity = 0f
                    centerAnim.animateTo(
                        targetValue = centerOf(selectedIndex),
                        animationSpec = spring(dampingRatio = 0.68f, stiffness = 380f),
                        initialVelocity = v
                    )
                }
            }

            val pressAnim = animateFloatAsState(
                targetValue = if (pressed) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = 450f),
                label = "pillPress"
            )
            val stretchAnim = animateFloatAsState(
                targetValue = if (dragging) (abs(dragVelocity) / 3500f).coerceIn(0f, 1f) else 0f,
                animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
                label = "pillStretch"
            )

            // Lambdas lidas só nas fases de layout/draw (não recompõem a cada frame)
            val centerPx = { if (dragging) dragCenter else centerAnim.value }
            val pillW = { baseW + pressAnim.value * growW + stretchAnim.value * stretchMax }
            val pillH = { baseH + pressAnim.value * growH }
            val pillLeft = {
                val w = pillW()
                (centerPx() - w / 2f).coerceIn(edgeMin, (barW - w - edgeMin).coerceAtLeast(edgeMin))
            }
            val pillTop = { (barH - pillH()) / 2f }

            // Camada base: ícones apagados + captura de gestos
            NavRow(
                navItems = navItems,
                tint = Color.White.copy(alpha = 0.60f),
                bold = false,
                selectedIndex = selectedIndex,
                onSelect = { currentOnSelect(it) },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(itemW, barW) {
                        val slop = viewConfiguration.touchSlop
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val startCenter = centerPx()
                            val onPill = abs(down.position.x - startCenter) <= pillW() / 2f + slop
                            val grab = if (onPill) startCenter - down.position.x else 0f
                            val tracker = VelocityTracker()
                            tracker.addPosition(down.uptimeMillis, down.position)
                            if (onPill) pressed = true

                            var started = false
                            var cancelled = false
                            var lastIndex = -1

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null) { cancelled = true; break }
                                if (!change.pressed) break

                                tracker.addPosition(change.uptimeMillis, change.position)
                                val finger = change.position.x

                                if (!started && abs(finger - down.position.x) > slop) {
                                    started = true
                                    dragCenter = startCenter
                                    dragging = true
                                    pressed = true
                                    scope.launch { centerAnim.stop() }
                                }
                                if (started) {
                                    change.consume()
                                    val desired = finger + grab
                                    // Começou fora da pílula: ela "corre" até o dedo; senão segue 1:1
                                    val next = if (onPill) desired
                                    else dragCenter + (desired - dragCenter) * 0.4f
                                    dragCenter = next.coerceIn(itemW / 2f, barW - itemW / 2f)
                                    dragVelocity = tracker.calculateVelocity().x

                                    val idx = (dragCenter / itemW).toInt().coerceIn(0, count - 1)
                                    if (idx != lastIndex) {
                                        if (lastIndex != -1) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        lastIndex = idx
                                    }
                                }
                            }

                            pressed = false
                            if (started) {
                                val vx = if (cancelled) 0f else tracker.calculateVelocity().x
                                val endCenter = dragCenter
                                val projected = (endCenter + vx * 0.10f)
                                    .coerceIn(itemW / 2f, barW - itemW / 2f)
                                val idx = (projected / itemW).toInt().coerceIn(0, count - 1)
                                scope.launch {
                                    centerAnim.snapTo(endCenter)
                                    dragging = false
                                    dragVelocity = 0f
                                    if (idx != currentSelected) {
                                        pendingVelocity = vx
                                        currentOnSelect(navItems[idx].key)
                                    } else {
                                        centerAnim.animateTo(
                                            targetValue = centerOf(idx),
                                            animationSpec = spring(dampingRatio = 0.68f, stiffness = 380f),
                                            initialVelocity = vx
                                        )
                                    }
                                }
                            } else if (!cancelled && !onPill) {
                                // Toque simples numa aba
                                val idx = (down.position.x / itemW).toInt().coerceIn(0, count - 1)
                                if (idx != currentSelected) currentOnSelect(navItems[idx].key)
                            }
                        }
                    }
            )

            // Pílula-lente arrastável. Mostra uma segunda cópia dos ícones (brancos e
            // ampliados) recortada pela própria pílula, como se a lente os aumentasse.
            GlassSurface(
                backdrop = backdrop,
                shape = RoundedCornerShape(percent = 50),
                style = GlassStyle.Lens,
                modifier = Modifier
                    .offset { IntOffset(pillLeft().roundToInt(), pillTop().roundToInt()) }
                    .layout { measurable, _ ->
                        val w = pillW().roundToInt().coerceAtLeast(1)
                        val h = pillH().roundToInt().coerceAtLeast(1)
                        val placeable = measurable.measure(Constraints.fixed(w, h))
                        layout(w, h) { placeable.place(0, 0) }
                    }
            ) {
                NavRow(
                    navItems = navItems,
                    tint = Color.White,
                    bold = true,
                    selectedIndex = selectedIndex,
                    modifier = Modifier
                        .clearAndSetSemantics { }
                        // Mede com o tamanho da barra inteira, mas REPORTA o tamanho da pílula.
                        // Se reportasse o tamanho da barra (maior que o máximo do pai), o Compose
                        // centralizaria o conteúdo e os ícones ficariam deslocados.
                        .layout { measurable, incoming ->
                            val placeable = measurable.measure(
                                Constraints.fixed(barW.roundToInt(), barH.roundToInt())
                            )
                            layout(incoming.maxWidth, incoming.maxHeight) { placeable.place(0, 0) }
                        }
                        .offset { IntOffset(-pillLeft().roundToInt(), -pillTop().roundToInt()) }
                        .graphicsLayer {
                            val s = 1.04f + 0.20f * pressAnim.value
                            scaleX = s
                            scaleY = s
                            transformOrigin = TransformOrigin((centerPx() / barW).coerceIn(0f, 1f), 0.5f)
                        }
                )
            }
        }
    }
}

/** Linha de abas. Usada duas vezes: apagada (base) e branca (dentro da lente). */
@Composable
private fun <T> NavRow(
    navItems: List<GlassTab<T>>,
    tint: Color,
    bold: Boolean,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    onSelect: ((T) -> Unit)? = null
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        navItems.forEachIndexed { index, item ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(
                        if (onSelect != null) {
                            Modifier.semantics(mergeDescendants = true) {
                                role = Role.Tab
                                selected = index == selectedIndex
                                onClick(label = item.label) { onSelect(item.key); true }
                            }
                        } else Modifier
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.label,
                    color = tint,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}
