package com.tolete.player.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties

/**
 * Cores atuais do app (tema fixo ou capa da música no modo dinâmico).
 * Fornecido em [TolereApp]; menus e diálogos leem daqui.
 */
val LocalAppColors = compositionLocalOf { LiveThemeHolder.colors ?: DefaultAppColors }

enum class MenuVisualStyle {
    COLORFUL, SIMPLE, GLASS;

    companion object {
        fun fromName(name: String?): MenuVisualStyle =
            entries.firstOrNull { it.name == name } ?: COLORFUL
    }
}

/** Shared fallback for windows outside the main composition, such as scrobble edit activities. */
object MenuVisualConfig {
    var style by mutableStateOf(MenuVisualStyle.COLORFUL)
}

val LocalMenuVisualStyle = compositionLocalOf { MenuVisualConfig.style }
val LocalGlassBackdrop = compositionLocalOf<GraphicsLayer?> { null }
val LocalGlassBackdropOrigin = compositionLocalOf { Offset.Zero }

/**
 * Último tema visível no app. Usado por telas fora da árvore principal
 * (ex.: ScrobbleEditActivity), que não têm acesso ao CompositionLocal.
 */
object LiveThemeHolder {
    @Volatile
    var colors: AppColors? = null
}

/** Opacidade do fundo de menus e diálogos (deixa um pouco do app aparecer por trás). */
private const val MENU_ALPHA = 0.84f

/**
 * Paleta de menus/diálogos derivada do tema:
 * - [container]: cor do tema com transparência;
 * - [content]/[muted]: texto claro ou escuro, escolhido pelo contraste com o fundo;
 * - [accent]: cor de destaque ajustada para ter contraste com o fundo do menu.
 */
@Immutable
data class MenuPalette(
    val container: Color,
    val content: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val danger: Color,
    val isLight: Boolean,
    val scheme: ColorScheme
)

private val DarkText = Color(0xFF14141A)
private val LightText = Color(0xFFF4F4F8)

private fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    val hi = maxOf(la, lb)
    val lo = minOf(la, lb)
    return (hi + 0.05f) / (lo + 0.05f)
}

/** Escolhe texto claro ou escuro, o que tiver maior contraste com [bg]. */
fun readableOn(bg: Color): Color =
    if (contrastRatio(LightText, bg) >= contrastRatio(DarkText, bg)) LightText else DarkText

private fun ensureContrast(fg: Color, bg: Color, target: Float, toward: Color): Color {
    var c = fg.copy(alpha = 1f)
    var steps = 0
    while (contrastRatio(c, bg) < target && steps < 10) {
        c = lerp(c, toward, 0.15f)
        steps++
    }
    return c
}

fun menuPalette(colors: AppColors, style: MenuVisualStyle = MenuVisualStyle.COLORFUL): MenuPalette {
    if (style != MenuVisualStyle.COLORFUL) {
        val container = when (style) {
            MenuVisualStyle.SIMPLE -> Color.Black
            MenuVisualStyle.GLASS -> Color(0xB317171D)
            MenuVisualStyle.COLORFUL -> Color.Black
        }
        val content = Color.White
        val muted = Color.White.copy(alpha = 0.72f)
        val accent = ensureContrast(colors.accent, container, 3f, content)
        val onAccent = readableOn(accent)
        val danger = Color(0xFFFF6B6B)
        val chip = lerp(container, accent, 0.35f)
        val scheme = darkColorScheme(
            primary = accent, onPrimary = onAccent,
            secondary = accent, onSecondary = onAccent,
            tertiary = accent, onTertiary = onAccent,
            surface = container, onSurface = content,
            surfaceVariant = container, onSurfaceVariant = muted,
            primaryContainer = chip, onPrimaryContainer = readableOn(chip),
            secondaryContainer = chip, onSecondaryContainer = readableOn(chip),
            tertiaryContainer = chip, onTertiaryContainer = readableOn(chip),
            error = danger, onError = Color.Black,
            surfaceTint = Color.Transparent,
            surfaceContainerLowest = container, surfaceContainerLow = container,
            surfaceContainer = container, surfaceContainerHigh = container,
            surfaceContainerHighest = container,
            background = container, onBackground = content,
            outline = content.copy(alpha = 0.55f),
            outlineVariant = content.copy(alpha = 0.25f)
        )
        return MenuPalette(
            container = container,
            content = content,
            muted = muted,
            accent = accent,
            onAccent = onAccent,
            danger = danger,
            isLight = false,
            scheme = scheme
        )
    }
    // Mistura as cores do tema; um toque de branco separa o menu do fundo (que é escuro).
    val base = lerp(lerp(colors.gradientTop, colors.surface, 0.5f), colors.accent, 0.16f)
        .copy(alpha = 1f)
    val container = lerp(base, Color.White, 0.06f)
    // Cor que o usuário realmente vê (menu translúcido sobre o fundo do app).
    val effective = container.copy(alpha = MENU_ALPHA)
        .compositeOver(colors.gradientBottom.copy(alpha = 1f))
    val content = readableOn(effective)
    val isLight = content == DarkText
    val muted = content.copy(alpha = 0.70f)
    val accent = ensureContrast(colors.accent, effective, 3f, content)
    val chip = lerp(effective, accent, 0.35f)
    val onChip = readableOn(chip)
    val danger = if (isLight) Color(0xFFC62828) else Color(0xFFFF6B6B)
    val onAccent = readableOn(accent)
    val translucent = container.copy(alpha = MENU_ALPHA)

    val scheme = if (isLight) {
        lightColorScheme(
            primary = accent, onPrimary = onAccent,
            secondary = accent, onSecondary = onAccent,
            tertiary = accent, onTertiary = onAccent,
            surface = translucent, onSurface = content,
            surfaceVariant = translucent, onSurfaceVariant = muted,
            primaryContainer = chip, onPrimaryContainer = onChip,
            secondaryContainer = chip, onSecondaryContainer = onChip,
            tertiaryContainer = chip, onTertiaryContainer = onChip,
            error = danger, onError = readableOn(danger),
            surfaceTint = Color.Transparent,
            surfaceContainerLowest = translucent, surfaceContainerLow = translucent,
            surfaceContainer = translucent, surfaceContainerHigh = translucent,
            surfaceContainerHighest = translucent,
            background = translucent, onBackground = content,
            outline = content.copy(alpha = 0.55f),
            outlineVariant = content.copy(alpha = 0.25f)
        )
    } else {
        darkColorScheme(
            primary = accent, onPrimary = onAccent,
            secondary = accent, onSecondary = onAccent,
            tertiary = accent, onTertiary = onAccent,
            surface = translucent, onSurface = content,
            surfaceVariant = translucent, onSurfaceVariant = muted,
            primaryContainer = chip, onPrimaryContainer = onChip,
            secondaryContainer = chip, onSecondaryContainer = onChip,
            tertiaryContainer = chip, onTertiaryContainer = onChip,
            error = danger, onError = readableOn(danger),
            surfaceTint = Color.Transparent,
            surfaceContainerLowest = translucent, surfaceContainerLow = translucent,
            surfaceContainer = translucent, surfaceContainerHigh = translucent,
            surfaceContainerHighest = translucent,
            background = translucent, onBackground = content,
            outline = content.copy(alpha = 0.55f),
            outlineVariant = content.copy(alpha = 0.25f)
        )
    }
    return MenuPalette(
        container = translucent,
        content = content,
        muted = muted,
        accent = accent,
        onAccent = onAccent,
        danger = danger,
        isLight = isLight,
        scheme = scheme
    )
}

val LocalMenuPalette = compositionLocalOf { menuPalette(DefaultAppColors) }

/** Aplica a paleta de menus (cores do Material + [LocalMenuPalette]) ao conteúdo. */
@Composable
fun AppMenuTheme(colors: AppColors = LocalAppColors.current, content: @Composable () -> Unit) {
    val style = LocalMenuVisualStyle.current
    val palette = remember(
        colors.gradientTop, colors.gradientBottom, colors.accent, colors.surface, style
    ) { menuPalette(colors, style) }
    MaterialTheme(
        colorScheme = palette.scheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes
    ) {
        CompositionLocalProvider(LocalMenuPalette provides palette, content = content)
    }
}

/** Diálogo compartilhado que aplica o estilo global Colorido, Simples ou Vidro. */
@Composable
fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    properties: DialogProperties = DialogProperties()
) {
    AppMenuTheme {
        val p = LocalMenuPalette.current
        val style = LocalMenuVisualStyle.current
        val backdrop = LocalGlassBackdrop.current
        if (style == MenuVisualStyle.GLASS) {
            Dialog(onDismissRequest = onDismissRequest, properties = properties) {
                val dialogModifier = modifier.widthIn(min = 280.dp, max = 560.dp)
                val dialogBody: @Composable () -> Unit = {
                    AppDialogBody(
                        palette = p,
                        confirmButton = confirmButton,
                        dismissButton = dismissButton,
                        icon = icon,
                        title = title,
                        text = text
                    )
                }
                if (backdrop != null) {
                    GlassSurface(
                        backdrop = backdrop,
                        sourceOrigin = LocalGlassBackdropOrigin.current,
                        shape = shape,
                        modifier = dialogModifier,
                        style = GlassStyle.Panel.copy(
                            refraction = 6.dp,
                            edgeWidth = 14.dp,
                            magnification = 0.015f,
                            dispersion = 0.18f
                        )
                    ) { dialogBody() }
                } else {
                    Surface(
                        modifier = dialogModifier,
                        shape = shape,
                        color = p.container,
                        tonalElevation = 0.dp
                    ) { dialogBody() }
                }
            }
        } else {
            AlertDialog(
                onDismissRequest = onDismissRequest,
                confirmButton = confirmButton,
                modifier = modifier,
                dismissButton = dismissButton,
                icon = icon,
                title = title,
                text = text,
                shape = shape,
                containerColor = p.container,
                iconContentColor = p.accent,
                titleContentColor = p.content,
                textContentColor = p.muted,
                tonalElevation = 0.dp,
                properties = properties
            )
        }
    }
}

@Composable
private fun AppDialogBody(
    palette: MenuPalette,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)?,
    icon: @Composable (() -> Unit)?,
    title: @Composable (() -> Unit)?,
    text: @Composable (() -> Unit)?
) {
    CompositionLocalProvider(LocalContentColor provides palette.content) {
        Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
            icon?.let {
                Box(
                    Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    contentAlignment = Alignment.Center
                ) { it() }
            }
            title?.let {
                Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    ProvideTextStyle(MaterialTheme.typography.headlineSmall) { it() }
                }
            }
            text?.let {
                Box(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp)) {
                    CompositionLocalProvider(LocalContentColor provides palette.muted) {
                        ProvideTextStyle(MaterialTheme.typography.bodyLarge) { it() }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                dismissButton?.let {
                    it()
                    Spacer(Modifier.width(8.dp))
                }
                confirmButton()
            }
        }
    }
}

/** Menu suspenso compartilhado, com paleta temática ou lente de vidro conforme a preferência. */
@Composable
fun AppDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    scrollState: ScrollState = rememberScrollState(),
    properties: PopupProperties = PopupProperties(focusable = true),
    shape: Shape = MaterialTheme.shapes.extraSmall,
    lensEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    AppMenuTheme {
        val p = LocalMenuPalette.current
        val style = LocalMenuVisualStyle.current
        val backdrop = LocalGlassBackdrop.current
        val glass = style == MenuVisualStyle.GLASS && backdrop != null
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            offset = offset,
            scrollState = scrollState,
            properties = properties,
            shape = shape,
            containerColor = if (glass) Color.Transparent else p.container,
            tonalElevation = 0.dp,
            // No vidro a sombra fica por conta do GlassSurface; a do popup virava um "fantasma"
            shadowElevation = if (glass) 0.dp else 3.dp,
            content = {
                if (glass) {
                    GlassSurface(
                        backdrop = backdrop!!,
                        sourceOrigin = LocalGlassBackdropOrigin.current,
                        shape = shape,
                        modifier = Modifier.widthIn(min = 112.dp),
                        style = GlassStyle.Panel.copy(
                            refraction = 5.dp,
                            edgeWidth = 12.dp,
                            magnification = 0.01f,
                            dispersion = 0.15f
                        )
                    ) {
                        if (lensEnabled && GlassConfig.quality != GlassQuality.LIGHT) {
                            GlassLensContent(scrollState = scrollState) { Column(content = content) }
                        } else {
                            Column(content = content)
                        }
                    }
                } else {
                    content()
                }
            }
        )
    }
}


/**
 * Contorno dos menus suspensos. No estilo Vidro o próprio [GlassSurface] já desenha a borda;
 * a borda extra aplicada no [DropdownMenu] pegava também o espaçamento interno do popup e
 * aparecia como um segundo retângulo deslocado atrás do menu.
 */
@Composable
fun Modifier.appMenuOutline(shape: Shape): Modifier {
    val glass = LocalMenuVisualStyle.current == MenuVisualStyle.GLASS &&
        LocalGlassBackdrop.current != null
    return if (glass) this else this.border(
        1.dp,
        Brush.verticalGradient(listOf(Color.White.copy(0.7f), Color.White.copy(0.25f))),
        shape
    )
}

/**
 * Item de menu que a pílula de vidro vertical reconhece (e executa ao soltar sobre ele).
 * Fora de uma lente se comporta exatamente como um [DropdownMenuItem].
 */
@Composable
fun LensMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true
) {
    DropdownMenuItem(
        text = text,
        onClick = onClick,
        modifier = modifier.lensRow(if (enabled) onClick else null),
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        enabled = enabled
    )
}
