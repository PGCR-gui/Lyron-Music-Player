package com.tolete.player.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties

/**
 * Cores atuais do app (tema fixo ou capa da música no modo dinâmico).
 * Fornecido em [TolereApp]; menus e diálogos leem daqui.
 */
val LocalAppColors = compositionLocalOf { LiveThemeHolder.colors ?: DefaultAppColors }

/**
 * Último tema visível no app. Usado por telas fora da árvore principal
 * (ex.: ScrobbleEditActivity), que não têm acesso ao CompositionLocal.
 */
object LiveThemeHolder {
    @Volatile
    var colors: AppColors? = null
}

/** Opacidade do fundo de menus e diálogos (deixa um pouco do app aparecer por trás). */
private const val MENU_ALPHA = 0.90f

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

fun menuPalette(colors: AppColors): MenuPalette {
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
    val palette = remember(
        colors.gradientTop, colors.gradientBottom, colors.accent, colors.surface
    ) { menuPalette(colors) }
    MaterialTheme(
        colorScheme = palette.scheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes
    ) {
        CompositionLocalProvider(LocalMenuPalette provides palette, content = content)
    }
}

/** AlertDialog com fundo na cor do tema (translúcido) e texto que se adapta ao fundo. */
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

/** DropdownMenu com fundo na cor do tema (translúcido) e texto que se adapta ao fundo. */
@Composable
fun AppDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    scrollState: ScrollState = rememberScrollState(),
    properties: PopupProperties = PopupProperties(focusable = true),
    shape: Shape = MaterialTheme.shapes.extraSmall,
    content: @Composable ColumnScope.() -> Unit
) {
    AppMenuTheme {
        val p = LocalMenuPalette.current
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            offset = offset,
            scrollState = scrollState,
            properties = properties,
            shape = shape,
            containerColor = p.container,
            tonalElevation = 0.dp,
            content = content
        )
    }
}
