package com.tolete.player.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import com.tolete.player.ui.MusicViewModel.AppThemeId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sin

@Immutable
data class AppColors(
    val gradientTop: Color,
    val gradientBottom: Color,
    val accent: Color,
    val surface: Color,
    val isLight: Boolean = false
) {
    val gradient: Brush get() = Brush.verticalGradient(listOf(gradientTop, gradientBottom))
    /** Segunda cor do tema (par com accent) — usada em barras, bordas e destaques. */
    val secondary: Color get() = gradientTop
}

/** Gradiente com deriva lenta (estilo floating) a partir das cores base do tema. */
@Composable
fun rememberFloatingGradient(colors: AppColors): Brush {
    val infinite = rememberInfiniteTransition(label = "themeFloat")
    val t by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatPhase"
    )
    val wave = (sin(t * Math.PI.toFloat()) * 0.5f + 0.5f)
    val shift = 0.06f + wave * 0.08f
    val topA = lerp(colors.gradientTop, colors.accent.copy(alpha = colors.gradientTop.alpha), shift * 0.45f)
    val topB = lerp(colors.gradientTop, colors.surface, shift)
    val botA = lerp(colors.gradientBottom, colors.surface, shift * 0.5f)
    val botB = lerp(colors.gradientBottom, colors.accent.copy(alpha = colors.gradientBottom.alpha), shift * 0.35f)
    return Brush.verticalGradient(
        colors = listOf(
            lerp(topA, topB, t * 0.5f),
            lerp(botA, botB, (1f - t) * 0.5f)
        )
    )
}

val DefaultAppColors = AppColors(
    gradientTop = Color(0xFF241440),
    gradientBottom = Color(0xFF0B0B14),
    accent = Color(0xFFBB86FC),
    surface = Color(0xFF1A1528)
)

val AquaColors = AppColors(
    gradientTop = Color(0xFF0A3D62),
    gradientBottom = Color(0xFF051B2C),
    accent = Color(0xFF00D2FF),
    surface = Color(0xFF0D2B3E)
)

val SunsetColors = AppColors(
    gradientTop = Color(0xFF4A1C2F),
    gradientBottom = Color(0xFF1A0A12),
    accent = Color(0xFFFF6B4A),
    surface = Color(0xFF2E1520)
)

val ForestColors = AppColors(
    gradientTop = Color(0xFF0F2E1C),
    gradientBottom = Color(0xFF06140C),
    accent = Color(0xFF4ADE80),
    surface = Color(0xFF143D28)
)

val MidnightColors = AppColors(
    gradientTop = Color(0xFF0B1220),
    gradientBottom = Color(0xFF02060E),
    accent = Color(0xFF60A5FA),
    surface = Color(0xFF121A2C)
)

val RoseColors = AppColors(
    gradientTop = Color(0xFF3B1228),
    gradientBottom = Color(0xFF160812),
    accent = Color(0xFFF472B6),
    surface = Color(0xFF2A1522)
)

val EmberColors = AppColors(
    gradientTop = Color(0xFF3D1A0A),
    gradientBottom = Color(0xFF140806),
    accent = Color(0xFFFBBF24),
    surface = Color(0xFF2A1408)
)

fun staticTheme(id: AppThemeId): AppColors = when (id) {
    AppThemeId.AQUA -> AquaColors
    AppThemeId.SUNSET -> SunsetColors
    AppThemeId.FOREST -> ForestColors
    AppThemeId.MIDNIGHT -> MidnightColors
    AppThemeId.ROSE -> RoseColors
    AppThemeId.EMBER -> EmberColors
    AppThemeId.DEFAULT, AppThemeId.DYNAMIC -> DefaultAppColors
}

/**
 * Extrai duas cores dominantes da capa (topo + base) + accent vibrante.
 */
fun colorsFromArt(artPath: String?, fallback: AppColors = DefaultAppColors): AppColors {
    if (artPath.isNullOrBlank()) return fallback
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(artPath, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 128 && bounds.outHeight / (sample * 2) >= 128) sample *= 2
        val bmp = BitmapFactory.decodeFile(
            artPath,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return fallback
        val palette = Palette.from(bmp).generate()
        bmp.recycle()

        val dominant = palette.getDominantColor(fallback.gradientBottom.toArgb())
        val darkVibrant = palette.getDarkVibrantColor(
            palette.getDarkMutedColor(dominant)
        )
        val vibrant = palette.getVibrantColor(
            palette.getMutedColor(fallback.accent.toArgb())
        )
        val lightVibrant = palette.getLightVibrantColor(vibrant)

        // Duas cores principais derivadas da capa
        fun darken(argb: Int, maxV: Float): Color {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(argb, hsv)
            hsv[2] = hsv[2].coerceAtMost(maxV)
            return Color(android.graphics.Color.HSVToColor(hsv))
        }

        val top = darken(darkVibrant, 0.35f)
        val bottom = darken(dominant, 0.16f)
        val accentColor = Color(if (vibrant != dominant) vibrant else lightVibrant)

        AppColors(
            gradientTop = top,
            gradientBottom = bottom,
            accent = accentColor,
            surface = top.copy(alpha = 0.92f)
        )
    } catch (_: Exception) {
        fallback
    } catch (_: OutOfMemoryError) {
        fallback
    }
}

@Composable
fun rememberAppColors(
    themeId: AppThemeId,
    artPath: String?,
    isPlaying: Boolean
): AppColors {
    val fixed = remember(themeId) {
        if (themeId == AppThemeId.DYNAMIC) DefaultAppColors else staticTheme(themeId)
    }
    val useArt = themeId == AppThemeId.DYNAMIC && isPlaying
    val fromArt by produceState(initialValue = DefaultAppColors, artPath, useArt) {
        if (useArt) {
            value = withContext(Dispatchers.Default) { colorsFromArt(artPath, DefaultAppColors) }
        }
    }
    val target = if (useArt) fromArt else fixed
    val top by animateColorAsState(target.gradientTop, tween(550), label = "gradTop")
    val bottom by animateColorAsState(target.gradientBottom, tween(550), label = "gradBottom")
    val accent by animateColorAsState(target.accent, tween(550), label = "accent")
    val surface by animateColorAsState(target.surface, tween(550), label = "surface")
    return AppColors(
        gradientTop = top,
        gradientBottom = bottom,
        accent = accent,
        surface = surface,
        isLight = target.isLight
    )
}
