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
            Text(
                "Cor e transparência dos menus (Now Playing, seleção, etc.)",
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
            ColorPickerButton(
                color = menuBgColor,
                onColorChange = onMenuBgColorChange,
                showAlpha = true,
                label = "Cor do menu",
                modifier = Modifier.fillMaxWidth()
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
            ColorPickerButton(
                color = homeButtonColor,
                onColorChange = onHomeButtonColorChange,
                showAlpha = false,
                label = "Cor do texto",
                modifier = Modifier.fillMaxWidth()
            )

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
            ColorPickerButton(
                color = shareTextColor,
                onColorChange = onShareTextColorChange,
                showAlpha = false,
                label = "Cor do texto (letra)",
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



