package com.tolete.player.ui

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tolete.player.data.LibrarySettings
import com.tolete.player.data.SourceFolder
import com.tolete.player.ui.MusicViewModel.AppThemeId
import com.tolete.player.ui.MusicViewModel.ShuffleType
import java.io.File

internal fun themeLabel(id: AppThemeId) = when (id) {
    AppThemeId.DYNAMIC -> "Dinâmico (capa do álbum)"
    AppThemeId.DEFAULT -> "Padrão"
    AppThemeId.AQUA -> "Aqua"
    AppThemeId.SUNSET -> "Sunset"
    AppThemeId.FOREST -> "Forest"
    AppThemeId.MIDNIGHT -> "Midnight"
    AppThemeId.ROSE -> "Rose"
    AppThemeId.EMBER -> "Ember"
    AppThemeId.AMOLED -> "AMOLED"
    AppThemeId.TWILIGHT -> "Twilight"
    AppThemeId.PHOTO -> "Por foto"
}


@Composable
internal fun AppearancePage(
    themeId: AppThemeId,
    uiFontPath: String?,
    onThemeSelected: (AppThemeId) -> Unit,
    dynamicDefaultColor: Int = 0xFFBB86FC.toInt(),
    onDynamicDefaultColorChange: (Int) -> Unit = {},
    dynamicFloatColors: List<Int> = emptyList(),
    onDynamicFloatColorsChange: (List<Int>) -> Unit = {},
    onPickUiFont: () -> Unit,
    onClearUiFont: () -> Unit,
    onSelectUiFont: (String?) -> Unit = {},
    onOpenLyricsViewer: () -> Unit,
    splashEnabled: Boolean = true,
    onSplashEnabledChange: (Boolean) -> Unit = {},
    splashSoundEnabled: Boolean = true,
    onSplashSoundChange: (Boolean) -> Unit = {},
    floatingIconsEnabled: Boolean = true,
    onFloatingIconsChange: (Boolean) -> Unit = {},
    homeButtonFontPath: String? = null,
    onHomeButtonFontSelect: (String?) -> Unit = {},
    onPickHomeButtonFont: () -> Unit = {},
    homeButtonColor: Int = 0xFFFFFFFF.toInt(),
    onHomeButtonColorChange: (Int) -> Unit = {},
    shareFontPath: String? = null,
    onShareFontSelect: (String?) -> Unit = {},
    onPickShareFont: () -> Unit = {},
    shareTextColor: Int = 0xFFF5F5FA.toInt(),
    onShareTextColorChange: (Int) -> Unit = {},
    shareBgColor: Int = 0xFF121212.toInt(),
    onShareBgColorChange: (Int) -> Unit = {},
    shareBrandColor: Int = 0xFFFFFFFF.toInt(),
    onShareBrandColorChange: (Int) -> Unit = {},
    menuVisualStyle: MenuVisualStyle = MenuVisualStyle.COLORFUL,
    onMenuVisualStyleChange: (MenuVisualStyle) -> Unit = {},
    photoThemePath: String? = null,
    onPickPhotoTheme: () -> Unit = {},
    onClearPhotoTheme: () -> Unit = {},
    photoThemeBlur: Float = 0.55f,
    onPhotoThemeBlurChange: (Float) -> Unit = {},
    dynamicAlbumBlur: Boolean = false,
    onDynamicAlbumBlurChange: (Boolean) -> Unit = {},
    alphabetScrollerStyle: MusicViewModel.AlphabetScrollerStyle = MusicViewModel.AlphabetScrollerStyle.GLASS,
    onAlphabetScrollerStyleChange: (MusicViewModel.AlphabetScrollerStyle) -> Unit = {},
    waveformTheme: WaveformTheme = WaveformTheme.BARS,
    onWaveformThemeChange: (WaveformTheme) -> Unit = {}
) {
    val themes = listOf(
        AppThemeId.DYNAMIC to "Dinâmico",
        AppThemeId.PHOTO to "Por foto",
        AppThemeId.DEFAULT to "Padrão",
        AppThemeId.AQUA to "Aqua",
        AppThemeId.SUNSET to "Sunset",
        AppThemeId.FOREST to "Forest",
        AppThemeId.MIDNIGHT to "Midnight",
        AppThemeId.ROSE to "Rose",
        AppThemeId.EMBER to "Ember",
        AppThemeId.AMOLED to "AMOLED",
        AppThemeId.TWILIGHT to "Twilight"
    )
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        item {
            Text(
                "Tema",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(0.55f),
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
            )
        }
        items(themes) { (id, label) ->
            val selected = themeId == id
            val preview = staticTheme(id)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) Color.White.copy(0.14f) else Color.Transparent)
                    .clickable { onThemeSelected(id) }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(preview.gradient)
                        .border(1.dp, Color.White.copy(0.2f), RoundedCornerShape(10.dp))
                )
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(label, fontSize = 16.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    when (id) {
                        AppThemeId.DYNAMIC -> Text(
                            "Cores da capa flutuam enquanto toca",
                            fontSize = 12.sp,
                            color = Color.White.copy(0.5f)
                        )
                        AppThemeId.PHOTO -> Text(
                            "Fundo borrado a partir de uma foto sua",
                            fontSize = 12.sp,
                            color = Color.White.copy(0.5f)
                        )
                        else -> {}
                    }
                }
                if (selected) {
                    Text("✓", color = preview.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Spacer(Modifier.height(20.dp))
            Text(
                "Estilo dos menus",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                "Escolha a aparência dos diálogos, menus de ações e seletores do app.",
                fontSize = 13.sp,
                color = Color.White.copy(0.55f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            listOf(
                MenuVisualStyle.COLORFUL to ("Colorido" to "Translúcido, com as cores do tema atual."),
                MenuVisualStyle.SIMPLE to ("Simples" to "Fundo preto e texto branco."),
                MenuVisualStyle.GLASS to ("Vidro" to "Superfície de vidro com os efeitos avançados configurados no app.")
            ).forEach { (style, copy) ->
                val selected = menuVisualStyle == style
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) Color.White.copy(0.14f) else Color.White.copy(0.04f))
                        .clickable { onMenuVisualStyleChange(style) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(copy.first, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, color = Color.White)
                        Text(copy.second, fontSize = 12.sp, color = Color.White.copy(0.58f))
                    }
                    if (selected) Text("✓", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (menuVisualStyle == MenuVisualStyle.GLASS) {
                Text(
                    "Os menus em vidro acompanham o nível de efeitos escolhido em Configurações avançadas.",
                    fontSize = 12.sp,
                    color = Color.White.copy(0.45f),
                    modifier = Modifier.padding(top = 5.dp)
                )
            }
        }
        if (themeId == AppThemeId.PHOTO) {
            item {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Tema por foto",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(0.55f),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Text(
                    "Escolha uma imagem para o fundo borrado. Enquanto toca, as cores da capa dominam o tema.",
                    fontSize = 12.sp,
                    color = Color.White.copy(0.45f),
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(onClick = onPickPhotoTheme) {
                        Text(if (photoThemePath.isNullOrBlank()) "Escolher foto" else "Trocar foto")
                    }
                    if (!photoThemePath.isNullOrBlank()) {
                        TextButton(onClick = onClearPhotoTheme) {
                            Text("Remover")
                        }
                    }
                }
                if (!photoThemePath.isNullOrBlank()) {
                    Text(
                        "Foto salva ✓",
                        fontSize = 12.sp,
                        color = Color.White.copy(0.55f),
                        modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
                    )
                }
                Text(
                    "Nível de blur: ${(photoThemeBlur * 100).toInt()}%",
                    fontSize = 12.sp,
                    color = Color.White.copy(0.55f),
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
                Slider(
                    value = photoThemeBlur,
                    onValueChange = onPhotoThemeBlurChange,
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(0.05f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Usar capa enquanto toca", fontSize = 15.sp, color = Color.White)
                        Text(
                            "Substitui a foto escolhida pela capa do álbum durante a reprodução.",
                            fontSize = 12.sp,
                            color = Color.White.copy(0.5f)
                        )
                    }
                    Switch(
                        checked = dynamicAlbumBlur,
                        onCheckedChange = onDynamicAlbumBlurChange
                    )
                }
            }
        }
        if (themeId == AppThemeId.DYNAMIC) {
            item {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Cor padrão do dinâmico (sem música tocando)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(0.55f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    "Usada quando o tema dinâmico está ativo e nenhuma faixa está tocando.",
                    fontSize = 12.sp,
                    color = Color.White.copy(0.45f),
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                ColorPickerButton(
                    color = dynamicDefaultColor,
                    onColorChange = onDynamicDefaultColorChange,
                    showAlpha = false,
                    label = "Cor padrão do tema dinâmico",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Cores flutuantes (até 5)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(0.55f),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Text(
                    "Estas cores derivam lentamente no fundo do tema dinâmico quando não há música tocando. A partir de 3 cores podem ofuscar os ícones flutuantes.",
                    fontSize = 12.sp,
                    color = Color.White.copy(0.45f),
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    dynamicFloatColors.forEachIndexed { index, argb ->
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .border(1.5.dp, Color.White.copy(0.35f), CircleShape)
                                .clickable {
                                    // Remove ao tocar de novo
                                    onDynamicFloatColorsChange(
                                        dynamicFloatColors.toMutableList().also { it.removeAt(index) }
                                    )
                                }
                        )
                    }
                    if (dynamicFloatColors.size < 5) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(0.08f))
                                .border(1.dp, Color.White.copy(0.25f), CircleShape)
                                .clickable {
                                    // Adiciona a cor padrão atual como ponto de partida
                                    onDynamicFloatColorsChange(
                                        dynamicFloatColors + dynamicDefaultColor
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = "Adicionar cor",
                                tint = Color.White.copy(0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                if (dynamicFloatColors.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    dynamicFloatColors.forEachIndexed { index, argb ->
                        ColorPickerButton(
                            color = argb,
                            onColorChange = { newColor ->
                                onDynamicFloatColorsChange(
                                    dynamicFloatColors.toMutableList().also { it[index] = newColor }
                                )
                            },
                            showAlpha = false,
                            label = "Cor flutuante ${index + 1}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        )
                    }
                    Text(
                        "Toque no círculo para remover uma cor.",
                        fontSize = 11.sp,
                        color = Color.White.copy(0.4f)
                    )
                }
            }
        }
        item {
            Spacer(Modifier.height(20.dp))
            // --- Fontes (submenu) ---
            Text(
                "Fontes",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(0.55f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            val ctx = LocalContext.current
            val userFonts = remember(uiFontPath) { BuiltInFonts.listUserFonts(ctx) }
            FontDropdownSelector(
                title = "Fonte do player",
                selectedPath = uiFontPath,
                systemLabel = "Padrão do sistema",
                userFonts = userFonts,
                onSelectSystem = onClearUiFont,
                onSelectFont = { onSelectUiFont(it) },
                onLoadFont = onPickUiFont,
                loadLabel = "Carregar fonte..."
            )
            if (!uiFontPath.isNullOrBlank() && !uiFontPath.startsWith("builtin:") && uiFontPath != "system") {
                Text(
                    "Remover fonte personalizada",
                    modifier = Modifier.padding(top = 6.dp).clickable(onClick = onClearUiFont).padding(vertical = 6.dp),
                    fontSize = 13.sp,
                    color = Color(0xFFFF8A80)
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1A73E8).copy(0.22f))
                    .clickable {
                        try {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.dafont.com")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (_: Exception) {}
                    }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.FontDownload, contentDescription = null, tint = Color(0xFF8AB4F8))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Baixar fontes no DaFont", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text("dafont.com — muitas fontes gratuitas para uso pessoal", fontSize = 12.sp, color = Color.White.copy(0.55f))
                }
            }
            Text(
                "Afeta títulos, listas e interface — não altera a fonte das letras.\n" +
                    "A rotação de fontes no splash usa as fontes que você carregar aqui.",
                fontSize = 12.sp,
                color = Color.White.copy(0.4f),
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "Ícones flutuantes",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(0.55f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(0.08f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Mostrar ícones flutuantes", fontSize = 16.sp)
                    Text(
                        "No fundo de Início, músicas, artistas, álbuns e gêneros",
                        fontSize = 12.sp,
                        color = Color.White.copy(0.5f)
                    )
                }
                Switch(
                    checked = floatingIconsEnabled,
                    onCheckedChange = onFloatingIconsChange
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "Índice alfabético",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(0.55f),
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Text(
                "Escolha entre o trilho de vidro, a faixa clássica de letras (A–Z) ou a barra fina Slim.",
                fontSize = 12.sp,
                color = Color.White.copy(0.45f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            listOf(
                MusicViewModel.AlphabetScrollerStyle.GLASS to ("Vidro" to "Trilho com língua e detalhe de prefixos."),
                MusicViewModel.AlphabetScrollerStyle.CLASSIC to ("Clássico" to "Faixa vertical simples de letras, como em apps de música."),
                MusicViewModel.AlphabetScrollerStyle.SLIM to ("Slim" to "Barra fina que acompanha a rolagem; ao arrastar, destaca a inicial escolhida.")
            ).forEach { (style, copy) ->
                val selected = alphabetScrollerStyle == style
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) Color.White.copy(0.14f) else Color.White.copy(0.04f))
                        .clickable { onAlphabetScrollerStyleChange(style) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(copy.first, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, color = Color.White)
                        Text(copy.second, fontSize = 12.sp, color = Color.White.copy(0.58f))
                    }
                    if (selected) Text("✓", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }

            // --- Tema da forma de onda (cortadores de áudio) ---
            Spacer(Modifier.height(20.dp))
            Text(
                "Forma de onda dos cortadores de áudio",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(0.55f),
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Text(
                "Estilo da waveform no editor de trecho dos vídeos (Retrospectiva e letra em vídeo). " +
                    "Todos os estilos, exceto Multicolor, usam a cor dominante do tema atual.",
                fontSize = 12.sp,
                color = Color.White.copy(0.45f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            val waveAccent = LocalAppColors.current.accent
            WaveformTheme.values().forEach { option ->
                val selected = waveformTheme == option
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) Color.White.copy(0.14f) else Color.White.copy(0.04f))
                        .clickable { onWaveformThemeChange(option) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            option.label,
                            fontSize = 15.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = Color.White
                        )
                        Text(option.description, fontSize = 12.sp, color = Color.White.copy(0.58f))
                    }
                    WaveformThemePreview(
                        theme = option,
                        accent = waveAccent,
                        modifier = Modifier
                            .padding(start = 10.dp)
                            .width(92.dp)
                            .height(44.dp)
                    )
                    if (selected) {
                        Text(
                            "✓",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 10.dp)
                        )
                    }
                }
            }

            // --- Botões da tela Início ---
            Spacer(Modifier.height(20.dp))
            Text(
                "Botões da tela Início",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(0.55f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                "Fonte e cor dos textos (Mais Tocadas, Favoritas, Playlists, Histórico)",
                fontSize = 12.sp,
                color = Color.White.copy(0.5f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            val homeFontsCtx = LocalContext.current
            val homeUserFonts = remember(homeButtonFontPath) { BuiltInFonts.listUserFonts(homeFontsCtx) }
            FontDropdownSelector(
                title = "Fonte dos botões da tela Início",
                selectedPath = homeButtonFontPath,
                systemLabel = "Padrão do sistema",
                userFonts = homeUserFonts,
                onSelectSystem = { onHomeButtonFontSelect(null) },
                onSelectFont = onHomeButtonFontSelect,
                onLoadFont = onPickHomeButtonFont,
                loadLabel = "Carregar fonte..."
            )
            Spacer(Modifier.height(10.dp))
            ColorPickerButton(
                color = homeButtonColor,
                onColorChange = onHomeButtonColorChange,
                showAlpha = false,
                label = "Cor do texto",
                modifier = Modifier.fillMaxWidth()
            )

            // --- Foto gerada (letra + mais tocadas) ---
            Spacer(Modifier.height(24.dp))
            Text(
                "Fotos geradas (letra e Mais Tocadas)",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(0.55f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                "Fonte, cor do texto/logo e cor de fundo (Mais Tocadas) nas imagens exportadas",
                fontSize = 12.sp,
                color = Color.White.copy(0.5f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            val shareFontsCtx = LocalContext.current
            val shareUserFonts = remember(shareFontPath) { BuiltInFonts.listUserFonts(shareFontsCtx) }
            FontDropdownSelector(
                title = "Fonte da foto gerada",
                selectedPath = shareFontPath,
                systemLabel = "Padrão do sistema",
                userFonts = shareUserFonts,
                onSelectSystem = { onShareFontSelect(null) },
                onSelectFont = onShareFontSelect,
                onLoadFont = onPickShareFont,
                loadLabel = "Carregar fonte..."
            )
            Spacer(Modifier.height(10.dp))
            ColorPickerButton(
                color = shareTextColor,
                onColorChange = onShareTextColorChange,
                showAlpha = false,
                label = "Cor do texto",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            ColorPickerButton(
                color = shareBgColor,
                onColorChange = onShareBgColorChange,
                showAlpha = false,
                label = "Cor de fundo (Mais Tocadas)",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            ColorPickerButton(
                color = shareBrandColor,
                onColorChange = onShareBrandColorChange,
                showAlpha = false,
                label = "Cor do logo / marca",
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(20.dp))
            Text(
                "Splash screen",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(0.55f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(0.08f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Mostrar splash", fontSize = 16.sp)
                    Text(
                        "Desative para abrir o app direto na biblioteca",
                        fontSize = 12.sp,
                        color = Color.White.copy(0.5f)
                    )
                }
                Switch(
                    checked = splashEnabled,
                    onCheckedChange = onSplashEnabledChange
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(0.08f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Som do splash", fontSize = 16.sp)
                    Text(
                        "Reproduz o som ao abrir o app",
                        fontSize = 12.sp,
                        color = Color.White.copy(0.5f)
                    )
                }
                Switch(
                    checked = splashSoundEnabled,
                    onCheckedChange = onSplashSoundChange,
                    enabled = splashEnabled
                )
            }
            Spacer(Modifier.height(16.dp))

            Text(
                "Legendas",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(0.55f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(0.08f))
                    .clickable(onClick = onOpenLyricsViewer)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.FontDownload, contentDescription = null)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Visualizador de legendas", fontSize = 16.sp)
                    Text(
                        "Fonte, tamanho, cores e borda",
                        fontSize = 12.sp,
                        color = Color.White.copy(0.5f)
                    )
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Color.White.copy(0.5f))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}


@Composable
internal fun LyricsViewerPage(
    lyricsFontPath: String?,
    lyricsTranslationFontPath: String? = null,
    lyricsBackingFontPath: String? = null,
    lyricsFontSize: Float,
    lyricsPrimaryColor: Int,
    lyricsSecondaryColor: Int,
    lyricsBorderColor: Int,
    lyricsBorderWidth: Float,
    lyricsTranslationColor: Int = 0xFFB0BEC5.toInt(),
    lyricsBackingColor: Int = 0xFFCE93D8.toInt(),
    lyricsBackingSecondaryColor: Int = 0x99CE93D8.toInt(),
    onPickFont: () -> Unit,
    onClearFont: () -> Unit,
    onSelectFont: (String?) -> Unit = {},
    onPickTranslationFont: () -> Unit = {},
    onClearTranslationFont: () -> Unit = {},
    onSelectTranslationFont: (String?) -> Unit = {},
    onPickBackingFont: () -> Unit = {},
    onClearBackingFont: () -> Unit = {},
    onSelectBackingFont: (String?) -> Unit = {},
    onFontSizeChange: (Float) -> Unit,
    onPrimaryColorChange: (Int) -> Unit,
    onSecondaryColorChange: (Int) -> Unit,
    onBorderColorChange: (Int) -> Unit,
    onBorderWidthChange: (Float) -> Unit,
    onTranslationColorChange: (Int) -> Unit = {},
    onBackingColorChange: (Int) -> Unit = {},
    onBackingSecondaryColorChange: (Int) -> Unit = {},
    lyricsEmphasisFontPath: String? = null,
    onSelectLyricsEmphasisFont: (String?) -> Unit = {}
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        val lyricsCtx = LocalContext.current
        val lyricsUserFonts = remember(lyricsFontPath, lyricsTranslationFontPath, lyricsBackingFontPath, lyricsEmphasisFontPath) { BuiltInFonts.listUserFonts(lyricsCtx) }
        val emphasisFontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                try {
                    val resolver = lyricsCtx.contentResolver
                    val originalName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }.orEmpty()
                    val extension = originalName.substringAfterLast('.', "").lowercase()
                    if (extension == "ttf" || extension == "otf") {
                        val baseName = originalName.substringBeforeLast('.', "font")
                            .replace(Regex("[^A-Za-z0-9._-]"), "_")
                            .take(48)
                        val destination = File(
                            BuiltInFonts.fontsDir(lyricsCtx),
                            "${baseName}_${System.currentTimeMillis()}.$extension"
                        )
                        val input = resolver.openInputStream(uri)
                        if (input != null) {
                            input.use { source -> destination.outputStream().use { source.copyTo(it) } }
                            onSelectLyricsEmphasisFont(destination.absolutePath)
                        }
                    }
                } catch (_: Exception) {
                    // Um arquivo inacessível ou incompatível não altera a fonte atual.
                }
            }
        }
        FontDropdownSelector(
            title = "Fonte principal das letras",
            selectedPath = lyricsFontPath,
            systemLabel = "Padrão do sistema",
            userFonts = lyricsUserFonts,
            onSelectSystem = onClearFont,
            onSelectFont = { onSelectFont(it) },
            onLoadFont = onPickFont,
            loadLabel = "Carregar fonte..."
        )
        if (!lyricsFontPath.isNullOrBlank() && !lyricsFontPath.startsWith("builtin:") && lyricsFontPath != "system") {
            Text(
                "Remover fonte personalizada",
                modifier = Modifier.padding(top = 6.dp).clickable(onClick = onClearFont).padding(vertical = 6.dp),
                fontSize = 13.sp,
                color = Color(0xFFFF8A80)
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Tamanho da fonte: ${lyricsFontSize.toInt()} sp",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("12", fontSize = 12.sp, color = Color.White.copy(0.5f))
            androidx.compose.material3.Slider(
                value = lyricsFontSize,
                onValueChange = onFontSizeChange,
                valueRange = 12f..48f,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            Text("48", fontSize = 12.sp, color = Color.White.copy(0.5f))
        }

        Spacer(Modifier.height(20.dp))
        FontDropdownSelector(
            title = "Fonte da tradução ({tr})",
            selectedPath = lyricsTranslationFontPath,
            systemLabel = "Mesma da letra",
            userFonts = lyricsUserFonts,
            onSelectSystem = onClearTranslationFont,
            onSelectFont = { onSelectTranslationFont(it) },
            onLoadFont = onPickTranslationFont,
            loadLabel = "Carregar fonte..."
        )
        Spacer(Modifier.height(20.dp))
        FontDropdownSelector(
            title = "Fonte do backing vocal ({bg})",
            selectedPath = lyricsBackingFontPath,
            systemLabel = "Mesma da letra",
            userFonts = lyricsUserFonts,
            onSelectSystem = onClearBackingFont,
            onSelectFont = { onSelectBackingFont(it) },
            onLoadFont = onPickBackingFont,
            loadLabel = "Carregar fonte..."
        )
        Spacer(Modifier.height(20.dp))
        FontDropdownSelector(
            title = "Fonte de destaque ({des})",
            selectedPath = lyricsEmphasisFontPath,
            systemLabel = "Mesma da letra / sistema",
            userFonts = lyricsUserFonts,
            onSelectSystem = { onSelectLyricsEmphasisFont(null) },
            onSelectFont = { onSelectLyricsEmphasisFont(it) },
            onLoadFont = { emphasisFontPicker.launch(arrayOf("font/ttf", "font/otf", "application/octet-stream")) },
            loadLabel = "Carregar fonte..."
        )
        Spacer(Modifier.height(16.dp))
        ColorPickerButton(
            color = lyricsPrimaryColor,
            onColorChange = onPrimaryColorChange,
            showAlpha = true,
            label = "Cor primária (ativa / sílaba atual — eLRC/TTML)",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))
        ColorPickerButton(
            color = lyricsSecondaryColor,
            onColorChange = onSecondaryColorChange,
            showAlpha = true,
            label = "Cor secundária (inativa / sílabas restantes)",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))
        ColorPickerButton(
            color = lyricsTranslationColor,
            onColorChange = onTranslationColorChange,
            showAlpha = true,
            label = "Cor da tradução ({tr})",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        ColorPickerButton(
            color = lyricsBackingColor,
            onColorChange = onBackingColorChange,
            showAlpha = true,
            label = "Cor primária do backing vocal ({bg})",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        ColorPickerButton(
            color = lyricsBackingSecondaryColor,
            onColorChange = onBackingSecondaryColorChange,
            showAlpha = true,
            label = "Cor secundária do backing vocal",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))
        ColorPickerButton(
            color = lyricsBorderColor,
            onColorChange = onBorderColorChange,
            showAlpha = true,
            label = "Cor da borda",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))
        Text(
            "Grossura da borda: ${"%.1f".format(lyricsBorderWidth)} px",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("0", fontSize = 12.sp, color = Color.White.copy(0.5f))
            androidx.compose.material3.Slider(
                value = lyricsBorderWidth,
                onValueChange = onBorderWidthChange,
                valueRange = 0f..8f,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            Text("8", fontSize = 12.sp, color = Color.White.copy(0.5f))
        }
        Text(
            "Suporte a sincronização silábica (eLRC e TTML).",
            fontSize = 12.sp,
            color = Color.White.copy(0.4f),
            modifier = Modifier.padding(top = 16.dp, bottom = 24.dp)
        )
    }
}


@Composable
private fun FontDropdownSelector(
    title: String,
    selectedPath: String?,
    systemLabel: String,
    userFonts: List<File>,
    onSelectSystem: () -> Unit,
    onSelectFont: (String) -> Unit,
    onLoadFont: (() -> Unit)?,
    loadLabel: String?
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = if (selectedPath.isNullOrBlank() || selectedPath == "system") {
        systemLabel
    } else {
        BuiltInFonts.displayLabel(selectedPath)
    }
    val glassShape = RoundedCornerShape(50)
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(glassShape)
                .background(Brush.horizontalGradient(listOf(Color.White.copy(0.13f), Color.White.copy(0.055f))))
                .border(1.dp, Color.White.copy(0.18f), glassShape)
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(0.72f))
                Text(selectedLabel, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Escolher fonte", tint = Color.White.copy(0.78f))
        }
        AppDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 250.dp, max = 340.dp),
            scrollState = rememberScrollState(),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(8.dp)) {
                FontMenuOption(
                    label = systemLabel,
                    selected = selectedPath.isNullOrBlank() || selectedPath == "system",
                    onClick = { onSelectSystem(); expanded = false }
                )
                Text("Embutidas", fontSize = 11.sp, color = Color.White.copy(0.48f), modifier = Modifier.padding(start = 12.dp, top = 9.dp, bottom = 3.dp))
                BuiltInFonts.EMBEDDED.forEach { font ->
                    val key = BuiltInFonts.builtinKey(font.id)
                    FontMenuOption(font.label, selectedPath == key, onClick = {
                        onSelectFont(key)
                        expanded = false
                    })
                }
                Text("Personalizadas", fontSize = 11.sp, color = Color.White.copy(0.48f), modifier = Modifier.padding(start = 12.dp, top = 9.dp, bottom = 3.dp))
                if (userFonts.isEmpty()) {
                    Text("Nenhuma fonte personalizada", fontSize = 12.sp, color = Color.White.copy(0.4f), modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                } else {
                    userFonts.forEach { file ->
                        FontMenuOption(file.name, selectedPath == file.absolutePath, onClick = {
                            onSelectFont(file.absolutePath)
                            expanded = false
                        })
                    }
                }
                if (onLoadFont != null && loadLabel != null) {
                    HorizontalDivider(color = Color.White.copy(0.12f), modifier = Modifier.padding(vertical = 5.dp))
                    FontMenuOption(loadLabel, selected = false, onClick = {
                        expanded = false
                        onLoadFont()
                    }, action = true)
                }
            }
        }
    }
}

@Composable
private fun FontMenuOption(label: String, selected: Boolean, onClick: () -> Unit, action: Boolean = false) {
    LensMenuItem(
        text = {
            Text(label, fontSize = 14.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        onClick = onClick,
        leadingIcon = if (action) {
            { Icon(Icons.Rounded.FontDownload, contentDescription = null, tint = Color(0xFFB8C7FF)) }
        } else null,
        trailingIcon = if (selected) {
            { Text("✓", color = Color(0xFFCFB7FF), fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        } else null
    )
}

@Composable
internal fun FontOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color.White.copy(0.14f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
        if (selected) {
            Text("✓", color = Color(0xFFBB86FC), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
