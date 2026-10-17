package com.tolete.player.ui

import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Superfície de vidro de verdade: desenha uma CÓPIA BORRADA do que está atrás dela
 * (a camada [backdrop] gravada em TolereApp), recortada no [shape], mais um véu
 * translúcido e uma borda com brilho.
 *
 * Borrar o fundo usa RenderEffect (Android 12+ / API 31). Em versões menores
 * cai num véu escuro translúcido, sem blur.
 */
@Composable
fun GlassSurface(
    backdrop: GraphicsLayer,
    shape: Shape,
    modifier: Modifier = Modifier,
    blurRadius: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val blurLayer = rememberGraphicsLayer()
    var origin by remember { mutableStateOf(Offset.Zero) }
    // renderEffect só depende do raio; não recria a cada frame
    val blurPx = with(androidx.compose.ui.platform.LocalDensity.current) { blurRadius.toPx() }
    remember(blurPx) {
        if (Build.VERSION.SDK_INT >= 31) {
            blurLayer.renderEffect = BlurEffect(blurPx, blurPx, TileMode.Clamp)
        }
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                val next = coords.positionInRoot()
                // Evita recomposição/draw extra se a posição não mudou
                if (next != origin) origin = next
            }
            .clip(shape)
            .drawWithContent {
                if (Build.VERSION.SDK_INT >= 31) {
                    blurLayer.record {
                        translate(-origin.x, -origin.y) { drawLayer(backdrop) }
                    }
                    drawLayer(blurLayer)
                    // Liquid glass: véu translúcido com leve brilho superior (estilo iOS / video)
                    drawRect(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.22f),
                                Color.White.copy(alpha = 0.08f),
                                Color.Black.copy(alpha = 0.06f)
                            )
                        )
                    )
                } else {
                    drawRect(Color(0xFF1C1C1E).copy(alpha = 0.88f))
                }
                drawContent()
            }
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.50f),
                        Color.White.copy(alpha = 0.12f),
                        Color.White.copy(alpha = 0.04f)
                    )
                ),
                shape = shape
            ),
        content = content
    )
}
