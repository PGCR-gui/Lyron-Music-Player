package com.tolete.player.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
}


@Composable
internal fun AppearancePage(
    themeId: AppThemeId,
    uiFontPath: String?,
    onThemeSelected: (AppThemeId) -> Unit,
    dynamicDefaultColor: Int = 0xFFBB86FC.toInt(),
    onDynamicDefaultColorChange: (Int) -> Unit = {},
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
    menuBgColor: Int = 0xE8F2F2F7.toInt(),
    onMenuBgColorChange: (Int) -> Unit = {},
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
    shareBrandColor: Int = 0xFFFFFFFF.toInt(),
    onShareBrandColorChange: (Int) -> Unit = {}
) {
    var fontsExpanded by remember { mutableStateOf(false) }

    val themes = listOf(
        AppThemeId.DYNAMIC to "Dinâmico",
        AppThemeId.DEFAULT to "Padrão",
        AppThemeId.AQUA to "Aqua",
        AppThemeId.SUNSET to "Sunset",
        AppThemeId.FOREST to "Forest",
        AppThemeId.MIDNIGHT to "Midnight",
        AppThemeId.ROSE to "Rose",
        AppThemeId.EMBER to "Ember",
        AppThemeId.AMOLED to "AMOLED"
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
                    if (id == AppThemeId.DYNAMIC) {
                        Text(
                            "Duas cores da capa enquanto toca",
                            fontSize = 12.sp,
                            color = Color.White.copy(0.5f)
                        )
                    }
                }
                if (selected) {
                    Text("✓", color = preview.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
                val seedColors = listOf(
                    0xFFBB86FC.toInt(),
                    0xFF00D2FF.toInt(),
                    0xFFFF6B4A.toInt(),
                    0xFF4ADE80.toInt(),
                    0xFF60A5FA.toInt(),
                    0xFFF472B6.toInt(),
                    0xFFFBBF24.toInt(),
                    0xFFE53935.toInt(),
                    0xFF26A69A.toInt(),
                    0xFFAB47BC.toInt()
                )
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    seedColors.forEach { argb ->
                        val sel = dynamicDefaultColor == argb
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .border(
                                    if (sel) 3.dp else 1.dp,
                                    if (sel) Color.White else Color.White.copy(0.3f),
                                    CircleShape
                                )
                                .clickable { onDynamicDefaultColorChange(argb) }
                        )
                    }
                }
                Text(
                    "Usada quando o tema dinâmico está ativo e nenhuma faixa está tocando.",
                    fontSize = 12.sp,
                    color = Color.White.copy(0.45f)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Cor personalizada (hex)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(0.55f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                HexColorPicker(
                    selected = dynamicDefaultColor,
                    onColorChange = onDynamicDefaultColorChange,
                    modifier = Modifier.fillMaxWidth()
                )
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
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(0.08f))
                    .clickable { fontsExpanded = !fontsExpanded }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.FontDownload, contentDescription = null)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Fonte do player", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "Selecionada: ${BuiltInFonts.displayLabel(uiFontPath)}",
                        fontSize = 12.sp,
                        color = Color.White.copy(0.5f)
                    )
                }
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color.White.copy(0.5f),
                    modifier = Modifier
                )
            }
            if (fontsExpanded) {
                val ctx = LocalContext.current
                val userFonts = remember { BuiltInFonts.listUserFonts(ctx) }
                Spacer(Modifier.height(8.dp))
                FontOptionRow(
                    label = "Padrão do sistema",
                    selected = uiFontPath.isNullOrBlank() || uiFontPath == "system" || uiFontPath?.startsWith("builtin:") == true,
                    onClick = onClearUiFont
                )
                userFonts.forEach { file ->
                    FontOptionRow(
                        label = file.name,
                        selected = uiFontPath == file.absolutePath,
                        onClick = { onSelectUiFont(file.absolutePath) }
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(0.08f))
                        .clickable(onClick = onPickUiFont)
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.FontDownload, contentDescription = null)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("Carregar fonte personalizada", fontSize = 16.sp)
                        Text(
                            if (uiFontPath != null && !uiFontPath.startsWith("builtin:") && uiFontPath != "system")
                                File(uiFontPath).name else "Arquivo .ttf / .otf",
                            fontSize = 12.sp,
                            color = Color.White.copy(0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (uiFontPath != null && !uiFontPath.startsWith("builtin:") && uiFontPath != "system") {
                    Text(
                        "Remover fonte personalizada",
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .clickable(onClick = onClearUiFont)
                            .padding(vertical = 6.dp),
                        fontSize = 14.sp,
                        color = Color(0xFFFF8A80)
                    )
                }
                // Recomendação DaFont
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF1A73E8).copy(0.22f))
                        .clickable {
                            try {
                                ctx.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.dafont.com"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } catch (_: Exception) {}
                        }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.FontDownload, contentDescription = null, tint = Color(0xFF8AB4F8))
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("Baixar fontes no DaFont", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "dafont.com — muitas fontes gratuitas para uso pessoal",
                            fontSize = 12.sp,
                            color = Color.White.copy(0.55f)
                        )
                    }
                }
                Text(
                    "Afeta títulos, listas e interface — não altera a fonte das letras.\n" +
                        "A rotação de fontes no splash usa as fontes que você carregar aqui.",
                    fontSize = 12.sp,
                    color = Color.White.copy(0.4f),
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )
            }
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
                "Menus claros (pop-ups)",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(0.55f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            val menuColor = Color(menuBgColor)
            val menuAlpha = ((menuBgColor ushr 24) and 0xFF) / 255f
            Text(
                "Cor e transparência dos menus brancos (Now Playing, seleção, etc.)",
                fontSize = 12.sp,
                color = Color.White.copy(0.5f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(menuColor)
                    .border(1.dp, Color.White.copy(0.25f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("Prévia do menu", color = Color(0xFF1A1A1A), fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(10.dp))
            Text("Transparência: ${(menuAlpha * 100).toInt()}%", fontSize = 14.sp, color = Color.White.copy(0.85f))
            Slider(
                value = menuAlpha.coerceIn(0.15f, 1f),
                onValueChange = { a ->
                    val rgb = menuBgColor and 0x00FFFFFF
                    val alphaByte = (a.coerceIn(0.15f, 1f) * 255).toInt().coerceIn(38, 255)
                    onMenuBgColorChange((alphaByte shl 24) or rgb)
                },
                valueRange = 0.15f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White.copy(0.85f),
                    inactiveTrackColor = Color.White.copy(0.25f)
                )
            )
            Text("Tom (claro → acinzentado)", fontSize = 14.sp, color = Color.White.copy(0.85f))
            val gray = ((menuBgColor shr 16) and 0xFF)
            Slider(
                value = gray.toFloat(),
                onValueChange = { g ->
                    val gv = g.toInt().coerceIn(180, 255)
                    val alphaByte = (menuBgColor ushr 24) and 0xFF
                    onMenuBgColorChange((alphaByte shl 24) or (gv shl 16) or (gv shl 8) or gv)
                },
                valueRange = 180f..255f,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White.copy(0.85f),
                    inactiveTrackColor = Color.White.copy(0.25f)
                )
            )
            TextButton(onClick = { onMenuBgColorChange(0xE8F2F2F7.toInt()) }) {
                Text("Restaurar padrão", color = Color(0xFF82B1FF))
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
            val homeUserFonts = remember { BuiltInFonts.listUserFonts(homeFontsCtx) }
            FontOptionRow(
                label = "Padrão do sistema",
                selected = homeButtonFontPath.isNullOrBlank() || homeButtonFontPath == "system",
                onClick = { onHomeButtonFontSelect(null) }
            )
            homeUserFonts.forEach { file ->
                FontOptionRow(
                    label = file.name,
                    selected = homeButtonFontPath == file.absolutePath,
                    onClick = { onHomeButtonFontSelect(file.absolutePath) }
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(0.08f))
                    .clickable(onClick = onPickHomeButtonFont)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.FontDownload, contentDescription = null)
                Text(
                    "Carregar fonte para os botões",
                    fontSize = 15.sp,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text("Cor do texto", fontSize = 13.sp, color = Color.White.copy(0.7f), modifier = Modifier.padding(bottom = 6.dp))
            val homeColorPresets = listOf(
                0xFFFFFFFF.toInt(), 0xFFBB86FC.toInt(), 0xFF00D2FF.toInt(),
                0xFFFF6B4A.toInt(), 0xFF4ADE80.toInt(), 0xFFFBBF24.toInt(),
                0xFFF472B6.toInt(), 0xFF60A5FA.toInt()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                homeColorPresets.forEach { c ->
                    val selected = homeButtonColor == c
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .border(if (selected) 3.dp else 1.dp, if (selected) Color.White else Color.White.copy(0.3f), CircleShape)
                            .clickable { onHomeButtonColorChange(c) }
                    )
                }
            }

            // --- Foto gerada (trecho de letra) ---
            Spacer(Modifier.height(24.dp))
            Text(
                "Foto gerada (trecho de letra)",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(0.55f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                "Fonte da letra e cor do texto / logo na imagem compartilhada",
                fontSize = 12.sp,
                color = Color.White.copy(0.5f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            val shareFontsCtx = LocalContext.current
            val shareUserFonts = remember { BuiltInFonts.listUserFonts(shareFontsCtx) }
            FontOptionRow(
                label = "Padrão do sistema",
                selected = shareFontPath.isNullOrBlank() || shareFontPath == "system",
                onClick = { onShareFontSelect(null) }
            )
            shareUserFonts.forEach { file ->
                FontOptionRow(
                    label = file.name,
                    selected = shareFontPath == file.absolutePath,
                    onClick = { onShareFontSelect(file.absolutePath) }
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(0.08f))
                    .clickable(onClick = onPickShareFont)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.FontDownload, contentDescription = null)
                Text(
                    "Carregar fonte para a foto",
                    fontSize = 15.sp,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            // Cores mais escuras / saturadas para legibilidade na foto
            val shareColorPresets = listOf(
                0xFFE8E8F0.toInt(), // off-white suave
                0xFFC2D1CB.toInt(), // sage
                0xFF9BB8D4.toInt(), // azul acinzentado
                0xFFB8A9C9.toInt(), // lavanda escura
                0xFFD4A574.toInt(), // ocre
                0xFF8FBC8F.toInt(), // verde musgo
                0xFFCD8B8B.toInt(), // rosa terroso
                0xFF7A9E9F.toInt()  // teal escuro
            )
            Text("Cor do texto (letra)", fontSize = 13.sp, color = Color.White.copy(0.7f), modifier = Modifier.padding(bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                shareColorPresets.forEach { c ->
                    val selected = shareTextColor == c
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .border(if (selected) 3.dp else 1.dp, if (selected) Color.White else Color.White.copy(0.3f), CircleShape)
                            .clickable { onShareTextColorChange(c) }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Cor do logo / marca", fontSize = 13.sp, color = Color.White.copy(0.7f), modifier = Modifier.padding(bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                shareColorPresets.forEach { c ->
                    val selected = shareBrandColor == c
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .border(if (selected) 3.dp else 1.dp, if (selected) Color.White else Color.White.copy(0.3f), CircleShape)
                            .clickable { onShareBrandColorChange(c) }
                    )
                }
            }

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
    lyricsFontSize: Float,
    lyricsPrimaryColor: Int,
    lyricsSecondaryColor: Int,
    lyricsBorderColor: Int,
    lyricsBorderWidth: Float,
    onPickFont: () -> Unit,
    onClearFont: () -> Unit,
    onSelectFont: (String?) -> Unit = {},
    onFontSizeChange: (Float) -> Unit,
    onPrimaryColorChange: (Int) -> Unit,
    onSecondaryColorChange: (Int) -> Unit,
    onBorderColorChange: (Int) -> Unit,
    onBorderWidthChange: (Float) -> Unit
) {
    val primaryPresets = listOf(
        0xFFFFFFFF.toInt(),
        0xFFBB86FC.toInt(),
        0xFF00D2FF.toInt(),
        0xFFFF6B4A.toInt(),
        0xFF4ADE80.toInt(),
        0xFFF472B6.toInt(),
        0xFFFBBF24.toInt(),
        0xFF60A5FA.toInt(),
        0xFFFFEB3B.toInt(),
        0xFFE91E63.toInt(),
        0xFF00BCD4.toInt(),
        0xFFFF9800.toInt()
    )
    val secondaryPresets = listOf(
        0x66FFFFFF.toInt(),
        0x99FFFFFF.toInt(),
        0x44FFFFFF.toInt(),
        0xFF888888.toInt(),
        0xFFB0BEC5.toInt(),
        0xFF90CAF9.toInt(),
        0xFFCE93D8.toInt(),
        0xFFA5D6A7.toInt()
    )
    val borderPresets = listOf(
        0xFF000000.toInt() to "Preto",
        0xFFFFFFFF.toInt() to "Branco",
        0xFF000000.toInt() to "Preto",
        0x88000000.toInt() to "Preto 50%"
    )
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Text(
            "Fonte das letras",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
        )
        Text(
            "Selecionada: ${BuiltInFonts.displayLabel(lyricsFontPath)}",
            fontSize = 12.sp,
            color = Color.White.copy(0.5f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        val lyricsCtx = LocalContext.current
        val lyricsUserFonts = remember { BuiltInFonts.listUserFonts(lyricsCtx) }
        FontOptionRow(
            label = "Padrão do sistema",
            selected = lyricsFontPath.isNullOrBlank() || lyricsFontPath == "system" || lyricsFontPath?.startsWith("builtin:") == true,
            onClick = onClearFont
        )
        lyricsUserFonts.forEach { file ->
            FontOptionRow(
                label = file.name,
                selected = lyricsFontPath == file.absolutePath,
                onClick = { onSelectFont(file.absolutePath) }
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(0.08f))
                .clickable(onClick = onPickFont)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.FontDownload, contentDescription = null)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Carregar fonte personalizada", fontSize = 16.sp)
                Text(
                    if (lyricsFontPath != null && !lyricsFontPath.startsWith("builtin:") && lyricsFontPath != "system")
                        File(lyricsFontPath).name else "Arquivo .ttf / .otf",
                    fontSize = 12.sp,
                    color = Color.White.copy(0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (lyricsFontPath != null && !lyricsFontPath.startsWith("builtin:") && lyricsFontPath != "system") {
            Text(
                "Remover fonte personalizada",
                modifier = Modifier
                    .padding(top = 10.dp)
                    .clickable(onClick = onClearFont)
                    .padding(vertical = 6.dp),
                fontSize = 14.sp,
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

        Spacer(Modifier.height(16.dp))
        Text(
            "Cor primária (ativa / sílaba atual — eLRC/TTML)",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            primaryPresets.forEach { color ->
                val selected = lyricsPrimaryColor == color
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(color))
                        .border(if (selected) 3.dp else 1.dp, if (selected) Color.White else Color.White.copy(0.3f), CircleShape)
                        .clickable { onPrimaryColorChange(color) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Cor secundária (inativa / sílabas restantes)",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            secondaryPresets.forEach { color ->
                val selected = lyricsSecondaryColor == color
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(color))
                        .border(if (selected) 3.dp else 1.dp, if (selected) Color.White else Color.White.copy(0.3f), CircleShape)
                        .clickable { onSecondaryColorChange(color) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Cor da borda",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0x88000000.toInt(), 0x00000000).forEach { color ->
                val selected = lyricsBorderColor == color
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (color == 0) Color.Transparent else Color(color))
                        .border(if (selected) 3.dp else 1.dp, Color.White.copy(0.5f), CircleShape)
                        .clickable { onBorderColorChange(color) }
                )
            }
        }

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


@Composable
internal fun HexColorPicker(
    selected: Int,
    onColorChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    fun toHex(argb: Int): String {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return String.format("#%02X%02X%02X", r, g, b)
    }
    fun parseHex(raw: String): Int? {
        var s = raw.trim().removePrefix("#")
        if (s.length == 3) {
            s = s.map { "$it$it" }.joinToString("")
        }
        if (s.length != 6) return null
        return try {
            (0xFF shl 24) or s.toInt(16)
        } catch (_: Exception) {
            null
        }
    }

    var text by remember(selected) { mutableStateOf(toHex(selected)) }
    var isError by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(selected))
                .border(1.dp, Color.White.copy(0.35f), RoundedCornerShape(12.dp))
        )
        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                // permite só # e hex
                val filtered = input.filter { it in "#0123456789abcdefABCDEF" }.take(7)
                text = filtered
                val parsed = parseHex(filtered)
                isError = parsed == null && filtered.length >= 6
                if (parsed != null) onColorChange(parsed)
            },
            singleLine = true,
            isError = isError,
            label = { Text("Hex (#RRGGBB)") },
            placeholder = { Text("#BB86FC") },
            modifier = Modifier.weight(1f),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF69F0AE),
                unfocusedBorderColor = Color.White.copy(0.3f),
                focusedLabelColor = Color.White.copy(0.7f),
                unfocusedLabelColor = Color.White.copy(0.5f),
                cursorColor = Color(0xFF69F0AE),
                errorBorderColor = Color(0xFFFF8A80)
            )
        )
    }
    if (isError) {
        Text(
            "Use o formato #RRGGBB (ex.: #FF5722)",
            fontSize = 11.sp,
            color = Color(0xFFFF8A80),
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
