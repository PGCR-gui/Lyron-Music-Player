package com.tolete.player.ui

import android.graphics.RenderEffect as PlatformRenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Constraints
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Nível dos efeitos de vidro (Configurações > Avançado).
 * - [FULL]: lente com distorção + desfoque (distorção só no Android 13+).
 * - [NO_DISTORTION]: só desfoque do fundo, sem o shader de lente.
 * - [LIGHT]: sem desfoque nem distorção; fundo translúcido simples (o mais leve).
 */
enum class GlassQuality {
    FULL, NO_DISTORTION, LIGHT;

    companion object {
        fun fromName(name: String?): GlassQuality =
            entries.firstOrNull { it.name == name } ?: FULL
    }
}

/** Estado global lido pelo vidro; o MusicViewModel atualiza isto ao carregar/alterar a opção. */
object GlassConfig {
    var quality by mutableStateOf(GlassQuality.FULL)
}

/**
 * Parâmetros visuais do vidro.
 *
 * - [blur]: desfoque do fundo.
 * - [refraction]: quanto (em dp) o conteúdo atrás é "entortado" na borda — o efeito de lente.
 * - [edgeWidth]: largura (em dp) da faixa de borda onde a refração acontece.
 * - [magnification]: aumento global do conteúdo (0 = nenhum, 0.1 ≈ 1,1x). Lente convexa.
 * - [dispersion]: separação cromática (RGB) na borda, como um vidro real.
 * - [saturation]: vibração das cores do fundo (1 = neutro).
 * - [veilTop]/[veilBottom]: opacidade do véu branco no topo/base.
 * - [rim]: intensidade da borda com brilho especular.
 * - [shadow]: sombra projetada em volta.
 *
 * Distorção (refração/dispersão/ampliação) exige Android 13+ (AGSL). No Android 12 vira
 * só desfoque; abaixo disso, véu escuro translúcido.
 */
@Immutable
data class GlassStyle(
    val blur: Dp = 10.dp,
    val refraction: Dp = 10.dp,
    val edgeWidth: Dp = 18.dp,
    val magnification: Float = 0f,
    val dispersion: Float = 0.22f,
    val saturation: Float = 1.25f,
    val veilTop: Float = 0.20f,
    val veilBottom: Float = 0.06f,
    val backdropTint: Float = 0.12f,
    val rim: Float = 0.55f,
    val shadow: Dp = 8.dp
) {
    companion object {
        /** Painéis grandes (menus, diálogos). */
        val Panel = GlassStyle(
            blur = 14.dp,
            veilTop = 0.10f,
            veilBottom = 0.04f,
            backdropTint = 0.12f
        )

        /** Barras flutuantes (navegação, mini player). */
        val Bar = GlassStyle(
            blur = 6.dp,
            refraction = 14.dp,
            edgeWidth = 22.dp,
            magnification = 0.02f,
            dispersion = 0.25f,
            saturation = 1.3f,
            veilTop = 0.09f,
            veilBottom = 0.02f,
            backdropTint = 0.02f,
            shadow = 10.dp
        )

        /** Lente pequena e transparente (pílula arrastável, botões circulares). */
        val Lens = GlassStyle(
            blur = 1.dp,
            refraction = 20.dp,
            edgeWidth = 24.dp,
            magnification = 0.14f,
            dispersion = 0.32f,
            saturation = 1.4f,
            veilTop = 0.16f,
            veilBottom = 0.04f,
            backdropTint = 0.025f,
            rim = 0.75f,
            shadow = 6.dp
        )

        /**
         * Pílula vertical dos menus. Ela amplia TEXTO, então a refração é bem mais suave que a
         * da [Lens] (senão as letras viram borrão colorido) e o centro da pílula fica nítido.
         */
        val MenuLens = GlassStyle(
            blur = 1.dp,
            refraction = 9.dp,
            edgeWidth = 16.dp,
            magnification = 0.10f,
            dispersion = 0.12f,
            saturation = 1.15f,
            veilTop = 0.14f,
            veilBottom = 0.05f,
            backdropTint = 0f,
            rim = 0.80f,
            shadow = 4.dp
        )
    }
}

/**
 * Superfície de vidro: desenha uma cópia do que está atrás dela (a camada [backdrop]
 * gravada em TolereApp), desfocada e refratada como uma lente, recortada no [shape],
 * mais véu translúcido, brilho especular e borda luminosa.
 */
@Composable
fun GlassSurface(
    backdrop: GraphicsLayer,
    shape: Shape,
    modifier: Modifier = Modifier,
    blurRadius: Dp? = null,
    style: GlassStyle = GlassStyle.Panel,
    sourceOrigin: Offset? = null,
    originOverride: (() -> Offset)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val blurLayer = rememberGraphicsLayer()
    var origin by remember { mutableStateOf(Offset.Zero) }
    val blurPx = with(LocalDensity.current) { (blurRadius ?: style.blur).toPx() }

    val lens = remember { if (Build.VERSION.SDK_INT >= 33) GlassLens() else null }
    val quality = GlassConfig.quality

    // Efeito aplicado à camada desfocada, conforme o nível escolhido:
    // - FULL no Android 13+: o shader de lente cuida disso (reset força a reaplicação).
    // - NO_DISTORTION, ou Android 12: só desfoque, sem shader.
    // - LIGHT: nada é gravado nem desfocado.
    remember(blurPx, quality) {
        if (Build.VERSION.SDK_INT >= 31) {
            if (quality == GlassQuality.FULL && Build.VERSION.SDK_INT >= 33) {
                lens?.reset()
            } else if (quality != GlassQuality.LIGHT) {
                blurLayer.renderEffect = BlurEffect(blurPx, blurPx, TileMode.Clamp)
            }
        }
        Unit
    }

    Box(
        modifier = modifier
            .then(
                if (style.shadow > 0.dp && quality != GlassQuality.LIGHT) {
                    Modifier.shadow(
                        elevation = style.shadow,
                        shape = shape,
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.25f),
                        spotColor = Color.Black.copy(alpha = 0.35f)
                    )
                } else Modifier
            )
            .onGloballyPositioned { coords ->
                val next = sourceOrigin?.let { coords.positionOnScreen() - it }
                    ?: coords.positionInRoot()
                if (next != origin) origin = next
            }
            .clip(shape)
            .drawWithContent {
                if (Build.VERSION.SDK_INT >= 31 && quality != GlassQuality.LIGHT) {
                    if (Build.VERSION.SDK_INT >= 33 && lens != null && quality == GlassQuality.FULL) {
                        val radiusPx = (shape as? CornerBasedShape)?.topStart?.toPx(size, this) ?: 0f
                        lens.update(
                            layer = blurLayer,
                            w = size.width,
                            h = size.height,
                            radius = radiusPx,
                            edge = style.edgeWidth.toPx(),
                            refraction = style.refraction.toPx(),
                            magnify = style.magnification,
                            dispersion = style.dispersion,
                            saturation = style.saturation,
                            blur = blurPx
                        )
                    }
                    // originOverride: posição exata deste frame (sem esperar o onGloballyPositioned),
                    // usada pela pílula que se move e precisa ficar colada no conteúdo.
                    val o = originOverride?.invoke() ?: origin
                    blurLayer.record {
                        translate(-o.x, -o.y) { drawLayer(backdrop) }
                    }
                    drawLayer(blurLayer)

                    // Véu fosco: reduz a leitura dos controles da tela atrás do menu sem apagar o vidro.
                    if (style.backdropTint > 0f) {
                        drawRect(Color(0xFF111319).copy(alpha = style.backdropTint.coerceIn(0f, 1f)))
                    }

                    // Véu: gradiente vertical + reflexo diagonal (luz vindo do canto superior esquerdo)
                    drawRect(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = style.veilTop),
                                Color.White.copy(alpha = style.veilBottom),
                                Color.Black.copy(alpha = 0.03f)
                            )
                        )
                    )
                    drawRect(
                        Brush.linearGradient(
                            0f to Color.White.copy(alpha = 0.10f),
                            0.40f to Color.Transparent,
                            0.75f to Color.Transparent,
                            1f to Color.White.copy(alpha = 0.04f)
                        )
                    )
                } else {
                    // Modo leve (ou Android < 12): véu escuro translúcido, sem ler o fundo
                    val alpha = if (quality == GlassQuality.LIGHT) 0.50f else 0.78f
                    drawRect(Color(0xFF1C1C1E).copy(alpha = alpha))
                    if (quality == GlassQuality.LIGHT) {
                        drawRect(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = style.veilTop * 0.6f),
                                    Color.Transparent
                                )
                            )
                        )
                    }
                }

                // Brilho interno na borda (espessura do vidro). A metade de fora é cortada pelo clip.
                val outline = shape.createOutline(size, layoutDirection, this)
                drawOutline(
                    outline = outline,
                    brush = Brush.linearGradient(
                        0f to Color.White.copy(alpha = 0.35f),
                        0.5f to Color.White.copy(alpha = 0.04f),
                        1f to Color.White.copy(alpha = 0.18f)
                    ),
                    style = Stroke(width = 3.dp.toPx())
                )

                drawContent()
            }
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    0f to Color.White.copy(alpha = style.rim),
                    0.35f to Color.White.copy(alpha = style.rim * 0.18f),
                    0.65f to Color.White.copy(alpha = style.rim * 0.08f),
                    1f to Color.White.copy(alpha = style.rim * 0.5f)
                ),
                shape = shape
            ),
        content = content
    )
}

/**
 * Linhas de um menu que a pílula vertical reconhece. Cada item marcado com [lensRow] se
 * registra aqui com a posição (na tela) e a ação de clique.
 */
@Stable
internal class GlassLensRegistry {
    class Row {
        var screenTop = 0f
        var height = 0f
        var onClick: (() -> Unit)? = null
    }

    val rows = ArrayList<Row>()
    var version by mutableIntStateOf(0)
}

internal val LocalGlassLensRegistry = compositionLocalOf<GlassLensRegistry?> { null }

/**
 * Marca este item como uma linha da pílula vertical ([GlassLensContent]). Fora de uma lente
 * não faz nada. [onClick] é chamado quando o usuário arrasta a pílula até a linha e solta.
 */
@Composable
internal fun Modifier.lensRow(onClick: (() -> Unit)? = null): Modifier {
    val registry = LocalGlassLensRegistry.current ?: return this
    val row = remember(registry) { GlassLensRegistry.Row() }
    SideEffect { row.onClick = onClick }
    DisposableEffect(registry, row) {
        registry.rows.add(row)
        onDispose {
            registry.rows.remove(row)
            registry.version = registry.version + 1
        }
    }
    return this.onGloballyPositioned { coords ->
        val top = coords.positionOnScreen().y
        val h = coords.size.height.toFloat()
        if (abs(top - row.screenTop) > 0.5f || abs(h - row.height) > 0.5f) {
            row.screenTop = top
            row.height = h
            registry.version = registry.version + 1
        }
    }
}

/**
 * Conteúdo de menu com uma pílula de vidro VERTICAL, irmã da pílula da barra de navegação:
 * - é uma lente de verdade: amplia e refrata o texto da linha em que está;
 * - ocupa a largura do menu e a altura de uma linha, e encaixa na linha mais próxima;
 * - segure nela e arraste para cima/baixo: ela cresce, estica com a velocidade e dá um
 *   toque de vibração a cada linha; ao soltar, encaixa (considerando a velocidade) e a ação
 *   da linha é executada, como a aba na barra de navegação;
 * - tocar numa linha faz a pílula deslizar até ela (o clique normal continua funcionando);
 * - arrastar FORA da pílula continua rolando a lista normalmente.
 *
 * As linhas precisam usar [lensRow] (ou [LensMenuItem]) para a pílula saber onde elas estão.
 * O texto original some sob a pílula (recorte) para não aparecer duplicado.
 */
@Composable
internal fun GlassLensContent(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    horizontalInset: Dp = 6.dp,
    fallbackPillHeight: Dp = 44.dp,
    content: @Composable () -> Unit
) {
    val registry = remember { GlassLensRegistry() }
    val contentLayer = rememberGraphicsLayer()
    var sourceOrigin by remember { mutableStateOf(Offset.Zero) }
    var panelSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // Distorção real (shader) só no Android 13+ com o vidro no nível máximo; senão a pílula
    // vira um simples destaque translúcido, sem ampliar nem recortar o texto.
    val distort = Build.VERSION.SDK_INT >= 33 && GlassConfig.quality == GlassQuality.FULL

    val insetX = with(density) { horizontalInset.toPx() }
    val insetY = with(density) { 3.dp.toPx() }
    val edgeMin = with(density) { 2.dp.toPx() }
    val growW = with(density) { 8.dp.toPx() }
    val growH = with(density) { 6.dp.toPx() }
    val stretchMax = with(density) { 12.dp.toPx() }
    val fallbackH = with(density) { fallbackPillHeight.toPx() }

    val centerAnim = remember { Animatable(0f) }
    var ready by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var dragCenter by remember { mutableFloatStateOf(0f) }
    var pressed by remember { mutableStateOf(false) }
    var dragVelocity by remember { mutableFloatStateOf(0f) }
    var autoScrollDirection by remember { mutableIntStateOf(0) }

    val pressAnim = animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 450f),
        label = "menuPillPress"
    )
    val stretchAnim = animateFloatAsState(
        targetValue = if (dragging) (abs(dragVelocity) / 3500f).coerceIn(0f, 1f) else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "menuPillStretch"
    )

    fun rowCenter(r: GlassLensRegistry.Row) = r.screenTop - sourceOrigin.y + r.height / 2f

    fun visibleRows(): List<GlassLensRegistry.Row> = registry.rows.filter { row ->
        row.height > 0f && rowCenter(row) in 0f..panelSize.height.toFloat()
    }

    fun nearestRow(y: Float): GlassLensRegistry.Row? {
        val candidates = visibleRows().ifEmpty { registry.rows }
        var best: GlassLensRegistry.Row? = null
        var bestDist = Float.MAX_VALUE
        for (r in candidates) {
            if (r.height <= 0f) continue
            val d = abs(rowCenter(r) - y)
            if (d < bestDist) {
                bestDist = d
                best = r
            }
        }
        return best
    }

    /** Mantém o centro da pílula entre a primeira e a última linha do menu. */
    fun clampToRows(y: Float): Float {
        val candidates = visibleRows().ifEmpty { registry.rows }
        var lo = Float.MAX_VALUE
        var hi = -Float.MAX_VALUE
        for (r in candidates) {
            if (r.height <= 0f) continue
            val c = rowCenter(r)
            if (c < lo) lo = c
            if (c > hi) hi = c
        }
        return if (lo > hi) y else y.coerceIn(lo, hi)
    }

    /** Retorna a direção quando a pílula está junto à primeira/última linha visível. */
    fun edgeDirectionFor(y: Float): Int {
        if (panelSize.height <= 0) return 0
        val visible = visibleRows()
        val first = visible.minByOrNull { rowCenter(it) } ?: return 0
        val last = visible.maxByOrNull { rowCenter(it) } ?: return 0
        if (first === last) return if (y < panelSize.height / 2f) -1 else 1

        val topDistance = abs(y - rowCenter(first))
        val bottomDistance = abs(y - rowCenter(last))
        val nearTop = topDistance <= first.height * 0.55f
        val nearBottom = bottomDistance <= last.height * 0.55f
        return when {
            nearTop && nearBottom -> if (topDistance <= bottomDistance) -1 else 1
            nearTop -> -1
            nearBottom -> 1
            else -> 0
        }
    }

    // A animação percorre o conteúdo continuamente, em velocidade proporcional à distância.
    // Isso evita o micro-passo por frame que fazia o menu parecer travado sob a pill.
    LaunchedEffect(scrollState, dragging, autoScrollDirection) {
        val direction = autoScrollDirection
        if (!dragging || direction == 0) return@LaunchedEffect

        delay(400)
        while (dragging && autoScrollDirection == direction &&
            (scrollState.viewportSize <= 0 || scrollState.maxValue <= 0 ||
                scrollState.maxValue == Int.MAX_VALUE)
        ) {
            delay(16)
        }
        if (!dragging || autoScrollDirection != direction || scrollState.maxValue <= 0 ||
            scrollState.maxValue == Int.MAX_VALUE
        ) return@LaunchedEffect

        val target = if (direction < 0) 0 else scrollState.maxValue
        val distancePx = kotlin.math.abs(target - scrollState.value)
        if (distancePx <= 0) return@LaunchedEffect
        val speedPxPerSecond = with(density) { 560.dp.toPx() }
        val durationMs = (distancePx / speedPxPerSecond * 1_000f)
            .toInt().coerceAtLeast(180)
        scrollState.animateScrollTo(
            value = target,
            animationSpec = tween(durationMillis = durationMs, easing = LinearEasing)
        )
    }

    // Lambdas lidas só nas fases de layout/draw (não recompõem a cada frame)
    val centerY = { if (dragging) dragCenter else centerAnim.value }
    val baseH = {
        val r = nearestRow(centerY())
        ((r?.height ?: fallbackH) - insetY * 2f).coerceAtLeast(fallbackH * 0.6f)
    }
    val pillW = {
        ((panelSize.width - insetX * 2f) + pressAnim.value * growW)
            .coerceIn(1f, panelSize.width.toFloat().coerceAtLeast(1f))
    }
    val pillH = { baseH() + pressAnim.value * growH + stretchAnim.value * stretchMax }
    val pillLeft = { (panelSize.width - pillW()) / 2f }
    val pillTop = {
        val h = pillH()
        (centerY() - h / 2f).coerceIn(edgeMin, (panelSize.height - h - edgeMin).coerceAtLeast(edgeMin))
    }

    // Posição inicial: primeira linha, assim que as linhas forem medidas
    LaunchedEffect(registry) {
        snapshotFlow { registry.version + panelSize.height }.collect {
            if (!ready && panelSize.height > 0) {
                val first = registry.rows.filter { it.height > 0f }.minByOrNull { it.screenTop }
                if (first != null) {
                    centerAnim.snapTo(rowCenter(first))
                    ready = true
                }
            }
        }
    }

    val placePill = Modifier
        .offset { IntOffset(pillLeft().roundToInt(), pillTop().roundToInt()) }
        .layout { measurable, _ ->
            val w = pillW().roundToInt().coerceAtLeast(1)
            val h = pillH().roundToInt().coerceAtLeast(1)
            val placeable = measurable.measure(Constraints.fixed(w, h))
            layout(w, h) { placeable.place(0, 0) }
        }

    CompositionLocalProvider(LocalGlassLensRegistry provides registry) {
        Box(
            modifier = modifier
                .onSizeChanged { panelSize = it }
                .onGloballyPositioned { sourceOrigin = it.positionOnScreen() }
                .pointerInput(registry) {
                    val slop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial
                        )
                        if (!ready) return@awaitEachGesture
                        val startCenter = centerY()
                        val left = pillLeft()
                        val onPill = abs(down.position.y - startCenter) <= pillH() / 2f + slop &&
                            down.position.x >= left - slop &&
                            down.position.x <= left + pillW() + slop

                        if (!onPill) {
                            // Toque simples fora da pílula: ela desliza até a linha tocada.
                            // Não consumimos nada, então o clique e a rolagem seguem normais.
                            var moved = false
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: return@awaitEachGesture
                                if (!change.pressed) {
                                    if (!moved) {
                                        val row = nearestRow(down.position.y)
                                        if (row != null) {
                                            scope.launch {
                                                centerAnim.animateTo(
                                                    targetValue = rowCenter(row),
                                                    animationSpec = spring(
                                                        dampingRatio = 0.68f,
                                                        stiffness = 380f
                                                    )
                                                )
                                            }
                                        }
                                    }
                                    return@awaitEachGesture
                                }
                                if (abs(change.position.y - down.position.y) > slop ||
                                    abs(change.position.x - down.position.x) > slop
                                ) moved = true
                            }
                        }

                        // Começou em cima da pílula: segura e arrasta na vertical
                        val grab = startCenter - down.position.y
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        pressed = true

                        var started = false
                        var cancelled = false
                        var lastRow: GlassLensRegistry.Row? = null

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null) { cancelled = true; break }
                            if (!change.pressed) break

                            tracker.addPosition(change.uptimeMillis, change.position)
                            val finger = change.position.y

                            if (!started && abs(finger - down.position.y) > slop) {
                                started = true
                                dragCenter = startCenter
                                dragging = true
                                scope.launch { centerAnim.stop() }
                            }
                            if (started) {
                                change.consume()
                                dragCenter = clampToRows(finger + grab)
                                autoScrollDirection = edgeDirectionFor(dragCenter)
                                dragVelocity = tracker.calculateVelocity().y

                                val near = nearestRow(dragCenter)
                                if (near !== lastRow) {
                                    if (lastRow != null) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    lastRow = near
                                }
                            }
                        }

                        pressed = false
                        autoScrollDirection = 0
                        if (started) {
                            val vy = if (cancelled) 0f else tracker.calculateVelocity().y
                            val end = dragCenter
                            val target = nearestRow(clampToRows(end + vy * 0.10f))
                            scope.launch {
                                centerAnim.snapTo(end)
                                dragging = false
                                dragVelocity = 0f
                                if (target != null) {
                                    // Executa a ação da linha logo depois de a pílula assentar
                                    if (!cancelled) {
                                        launch {
                                            delay(160)
                                            target.onClick?.invoke()
                                        }
                                    }
                                    centerAnim.animateTo(
                                        targetValue = rowCenter(target),
                                        animationSpec = spring(dampingRatio = 0.68f, stiffness = 380f),
                                        initialVelocity = vy
                                    )
                                }
                            }
                        }
                    }
                }
        ) {
            // Conteúdo do menu. Gravado numa camada (que a lente lê) e redesenhado com um
            // "buraco" no formato da pílula, para o texto não aparecer duplicado sob a lente.
            Box(
                Modifier.drawWithContent {
                    contentLayer.record { this@drawWithContent.drawContent() }
                    if (ready && distort) {
                        val wi = pillW().roundToInt().toFloat()
                        val hi = pillH().roundToInt().toFloat()
                        val l = pillLeft().roundToInt().toFloat()
                        val t = pillTop().roundToInt().toFloat()
                        val hole = Path().apply {
                            addRoundRect(RoundRect(l, t, l + wi, t + hi, CornerRadius(hi / 2f)))
                        }
                        clipPath(hole, ClipOp.Difference) { drawLayer(contentLayer) }
                    } else {
                        drawLayer(contentLayer)
                    }
                }
            ) { content() }

            if (ready) {
                if (distort) {
                    GlassSurface(
                        backdrop = contentLayer,
                        shape = RoundedCornerShape(percent = 50),
                        style = GlassStyle.MenuLens,
                        originOverride = {
                            Offset(
                                pillLeft().roundToInt().toFloat(),
                                pillTop().roundToInt().toFloat()
                            )
                        },
                        modifier = placePill
                    ) { }
                } else {
                    Box(
                        placePill
                            .clip(RoundedCornerShape(percent = 50))
                            .background(Color.White.copy(alpha = 0.14f))
                            .border(
                                1.dp,
                                Color.White.copy(alpha = 0.35f),
                                RoundedCornerShape(percent = 50)
                            )
                    )
                }
            }
        }
    }
}

/**
 * Efeito de lente em AGSL (Android 13+): refração pela borda (SDF do retângulo arredondado),
 * ampliação global, dispersão cromática e saturação. Roda depois do desfoque.
 */
@RequiresApi(33)
private class GlassLens {
    private val shader = RuntimeShader(LENS_AGSL)

    private var applied = false
    private var lastW = -1
    private var lastH = -1
    private var lastRadius = -1f
    private var lastEdge = -1f
    private var lastRefraction = -1f
    private var lastMagnify = -1f
    private var lastDispersion = -1f
    private var lastSaturation = -1f
    private var lastBlur = -1f

    /** Força a próxima chamada de [update] a reaplicar o efeito (ex.: voltando de outro nível). */
    fun reset() {
        applied = false
    }

    fun update(
        layer: GraphicsLayer,
        w: Float,
        h: Float,
        radius: Float,
        edge: Float,
        refraction: Float,
        magnify: Float,
        dispersion: Float,
        saturation: Float,
        blur: Float
    ) {
        val iw = w.roundToInt()
        val ih = h.roundToInt()
        if (applied && iw == lastW && ih == lastH && radius == lastRadius && edge == lastEdge &&
            refraction == lastRefraction && magnify == lastMagnify && dispersion == lastDispersion &&
            saturation == lastSaturation && blur == lastBlur
        ) return

        lastW = iw; lastH = ih; lastRadius = radius; lastEdge = edge
        lastRefraction = refraction; lastMagnify = magnify; lastDispersion = dispersion
        lastSaturation = saturation; lastBlur = blur
        applied = true

        shader.setFloatUniform("uSize", iw.toFloat(), ih.toFloat())
        shader.setFloatUniform("uRadius", radius)
        shader.setFloatUniform("uEdge", edge)
        shader.setFloatUniform("uRefraction", refraction)
        shader.setFloatUniform("uMagnify", magnify)
        shader.setFloatUniform("uDispersion", dispersion)
        shader.setFloatUniform("uSaturation", saturation)

        val lensEffect = PlatformRenderEffect.createRuntimeShaderEffect(shader, "content")
        val effect = if (blur >= 0.5f) {
            // inner (desfoque) é aplicado primeiro; outer (lente) distorce o resultado
            PlatformRenderEffect.createChainEffect(
                lensEffect,
                PlatformRenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP)
            )
        } else lensEffect
        layer.renderEffect = effect.asComposeRenderEffect()
    }
}

private const val LENS_AGSL = """
uniform shader content;
uniform float2 uSize;
uniform float uRadius;
uniform float uEdge;
uniform float uRefraction;
uniform float uMagnify;
uniform float uDispersion;
uniform float uSaturation;

float sdRoundRect(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + float2(r);
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0))) - r;
}

half4 main(float2 coord) {
    float2 hs = uSize * 0.5;
    float2 p = coord - hs;
    float r = min(uRadius, min(hs.x, hs.y));

    // distância à borda (negativa dentro) -> 0 na borda, 1 a uEdge de profundidade
    float d = sdRoundRect(p, hs, r);
    float t = clamp(-d / max(uEdge, 1.0), 0.0, 1.0);
    float bend = (1.0 - t) * (1.0 - t) * (1.0 - t);

    // normal (para fora) da forma
    float2 ex = float2(0.75, 0.0);
    float2 ey = float2(0.0, 0.75);
    float2 n = float2(
        sdRoundRect(p + ex, hs, r) - sdRoundRect(p - ex, hs, r),
        sdRoundRect(p + ey, hs, r) - sdRoundRect(p - ey, hs, r)
    );
    float nl = length(n);
    n = nl > 0.0001 ? n / nl : float2(0.0);

    // lente convexa: amostra mais perto do centro (amplia) + curva forte perto da borda
    float2 base = hs + p * (1.0 - uMagnify);
    float2 disp = -n * bend * uRefraction;

    float2 lo = float2(0.5);
    float2 hi = uSize - float2(0.5);
    float2 uvG = clamp(base + disp, lo, hi);
    float2 uvR = clamp(base + disp * (1.0 + uDispersion), lo, hi);
    float2 uvB = clamp(base + disp * (1.0 - uDispersion), lo, hi);

    float4 g = content.eval(uvG);
    float rr = content.eval(uvR).r;
    float bb = content.eval(uvB).b;
    float3 col = float3(rr, g.g, bb);

    float luma = dot(col, float3(0.2126, 0.7152, 0.0722));
    col = mix(float3(luma), col, uSaturation);
    col += float3(bend * 0.06);
    col = clamp(col, 0.0, g.a);
    return half4(half3(col), half(g.a));
}
"""
