package com.tolete.player.ui

import android.graphics.RenderEffect as PlatformRenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
    val rim: Float = 0.55f,
    val shadow: Dp = 8.dp
) {
    companion object {
        /** Painéis grandes (menus, diálogos). */
        val Panel = GlassStyle()

        /** Barras flutuantes (navegação, mini player). */
        val Bar = GlassStyle(
            blur = 8.dp,
            refraction = 14.dp,
            edgeWidth = 22.dp,
            magnification = 0.02f,
            dispersion = 0.25f,
            saturation = 1.3f,
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
            rim = 0.75f,
            shadow = 6.dp
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
                val next = coords.positionInRoot()
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
                    blurLayer.record {
                        translate(-origin.x, -origin.y) { drawLayer(backdrop) }
                    }
                    drawLayer(blurLayer)

                    // Véu: gradiente vertical + reflexo diagonal (luz vindo do canto superior esquerdo)
                    drawRect(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = style.veilTop),
                                Color.White.copy(alpha = style.veilBottom),
                                Color.Black.copy(alpha = 0.06f)
                            )
                        )
                    )
                    drawRect(
                        Brush.linearGradient(
                            0f to Color.White.copy(alpha = 0.14f),
                            0.40f to Color.Transparent,
                            0.75f to Color.Transparent,
                            1f to Color.White.copy(alpha = 0.06f)
                        )
                    )
                } else {
                    // Modo leve (ou Android < 12): véu escuro translúcido, sem ler o fundo
                    val alpha = if (quality == GlassQuality.LIGHT) 0.68f else 0.88f
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
