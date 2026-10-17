package com.tolete.player.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.tolete.player.ui.MusicViewModel
import com.tolete.player.ui.MusicViewModel.AppThemeId
import com.tolete.player.ui.MusicViewModel.SkinId
import com.tolete.player.ui.MusicViewModel.ShuffleType
import java.io.File

enum class SettingsPage { NONE, MENU, LIBRARY, FOLDERS, SKINS, APPEARANCE, LYRICS_VIEWER, ABOUT, CHANGELOG, LICENSES, PLAYER, AUDIO_OUTPUT, BACKUP, VISUALIZER }

private data class SettingsEntry(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit
)

@Composable
fun SettingsScreen(
    page: SettingsPage,
    folders: List<SourceFolder>,
    totalTracks: Int,
    isScanning: Boolean,
    scanCount: Int,
    themeId: AppThemeId,
    skinId: SkinId = SkinId.LYS_PLACE,
    onSkinSelected: (SkinId) -> Unit = {},
    lyricsFontPath: String?,
    uiFontPath: String?,
    librarySettings: LibrarySettings,
    appColors: AppColors,
    onNavigate: (SettingsPage) -> Unit,
    onBack: () -> Unit,
    onAddFolder: () -> Unit,
    onRemoveFolder: (String) -> Unit,
    onRefresh: () -> Unit,
    onThemeSelected: (AppThemeId) -> Unit,
    onPickFont: () -> Unit,
    onClearFont: () -> Unit,
    onPickUiFont: () -> Unit,
    onClearUiFont: () -> Unit,
    onSelectUiFont: (String?) -> Unit = {},
    onSelectLyricsFont: (String?) -> Unit = {},
    onSplitArtistsChange: (Boolean) -> Unit,
    onArtistSeparatorsChange: (String) -> Unit,
    onArtistExceptionsChange: (String) -> Unit,
    onSplitGenresChange: (Boolean) -> Unit,
    onGenreSeparatorsChange: (String) -> Unit,
    allFilesMode: Boolean = false,
    onAllFilesModeChange: (Boolean) -> Unit = {},
    onRequestAllFilesPermission: () -> Unit = {},
    autoFetchLyrics: Boolean = false,
    onAutoFetchLyricsChange: (Boolean) -> Unit = {},
    shuffleType: ShuffleType = ShuffleType.ABSOLUTE,
    onShuffleTypeChange: (ShuffleType) -> Unit = {},
    bitPerfect: Boolean = false,
    onBitPerfectChange: (Boolean) -> Unit = {},
    soundfontPath: String? = null,
    onPickSoundfont: () -> Unit = {},
    onClearSoundfont: () -> Unit = {},
    audioOutput: MusicViewModel.AudioOutputBackend = MusicViewModel.AudioOutputBackend.DEFAULT,
    onAudioOutputChange: (MusicViewModel.AudioOutputBackend) -> Unit = {},
    forceMaxSampleRate: Boolean = false,
    onForceMaxSampleRateChange: (Boolean) -> Unit = {},
    lyricsFontSize: Float = 22f,
    onLyricsFontSizeChange: (Float) -> Unit = {},
    lyricsPrimaryColor: Int = 0xFFFFFFFF.toInt(),
    onLyricsPrimaryColorChange: (Int) -> Unit = {},
    lyricsSecondaryColor: Int = 0x66FFFFFF.toInt(),
    onLyricsSecondaryColorChange: (Int) -> Unit = {},
    lyricsBorderColor: Int = 0xFF000000.toInt(),
    onLyricsBorderColorChange: (Int) -> Unit = {},
    lyricsBorderWidth: Float = 0f,
    onLyricsBorderWidthChange: (Float) -> Unit = {},
    onExportBackup: () -> Unit = {},
    onImportBackup: () -> Unit = {},
    splashEnabled: Boolean = true,
    onSplashEnabledChange: (Boolean) -> Unit = {},
    splashSoundEnabled: Boolean = true,
    onSplashSoundChange: (Boolean) -> Unit = {},
    fadeMs: Int = 300,
    onFadeMsChange: (Int) -> Unit = {},
    milkPresetCount: Int = 0,
    milkIntervalSec: Int = 30,
    onPickMilkFiles: () -> Unit = {},
    onClearMilkPresets: () -> Unit = {},
    onMilkIntervalChange: (Int) -> Unit = {}
) {
    val title = when (page) {
        SettingsPage.LIBRARY -> "Biblioteca"
        SettingsPage.FOLDERS -> "Pastas de música"
        SettingsPage.SKINS -> "Skins"
        SettingsPage.APPEARANCE -> "Aparência"
        SettingsPage.LYRICS_VIEWER -> "Visualizador de legendas"
        SettingsPage.ABOUT -> "Sobre"
        SettingsPage.CHANGELOG -> "Logs de atualização"
        SettingsPage.LICENSES -> "Licenças dos serviços"
        SettingsPage.PLAYER -> "Player"
        SettingsPage.AUDIO_OUTPUT -> "Saída de áudio"
        SettingsPage.BACKUP -> "Backup"
        SettingsPage.VISUALIZER -> "Visualizador (.milk)"
        else -> "Configurações"
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(rememberFloatingGradient(appColors))
            .pointerBlock()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar")
            }
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val forward = targetState.ordinal >= initialState.ordinal
                if (forward) {
                    (slideInHorizontally(tween(280)) { it / 3 } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(240)) { -it / 3 } + fadeOut(tween(180)))
                } else {
                    (slideInHorizontally(tween(280)) { -it / 3 } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(240)) { it / 3 } + fadeOut(tween(180)))
                }
            },
            label = "settingsPage",
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) { currentPage ->
        when (currentPage) {
            SettingsPage.LIBRARY -> LibraryPage(
                folders = folders,
                totalTracks = totalTracks,
                settings = librarySettings,
                allFilesMode = allFilesMode,
                onOpenFolders = { onNavigate(SettingsPage.FOLDERS) },
                onSplitArtistsChange = onSplitArtistsChange,
                onArtistSeparatorsChange = onArtistSeparatorsChange,
                onArtistExceptionsChange = onArtistExceptionsChange,
                onSplitGenresChange = onSplitGenresChange,
                onGenreSeparatorsChange = onGenreSeparatorsChange,
                onAllFilesModeChange = onAllFilesModeChange,
                onRequestAllFilesPermission = onRequestAllFilesPermission
            )
            SettingsPage.PLAYER -> PlayerPage(
                autoFetchLyrics = autoFetchLyrics,
                onAutoFetchLyricsChange = onAutoFetchLyricsChange,
                shuffleType = shuffleType,
                onShuffleTypeChange = onShuffleTypeChange,
                bitPerfect = bitPerfect,
                onBitPerfectChange = onBitPerfectChange,
                fadeMs = fadeMs,
                onFadeMsChange = onFadeMsChange,
                soundfontPath = soundfontPath,
                onPickSoundfont = onPickSoundfont,
                onClearSoundfont = onClearSoundfont,
                onOpenAudioOutput = { onNavigate(SettingsPage.AUDIO_OUTPUT) }
            )
            SettingsPage.AUDIO_OUTPUT -> AudioOutputPage(
                audioOutput = audioOutput,
                onAudioOutputChange = onAudioOutputChange,
                forceMaxSampleRate = forceMaxSampleRate,
                onForceMaxSampleRateChange = onForceMaxSampleRateChange
            )
            SettingsPage.FOLDERS -> FoldersPage(
                folders, totalTracks, isScanning, scanCount, onAddFolder, onRemoveFolder, onRefresh
            )
            SettingsPage.SKINS -> SkinsPage(
                skinId = skinId,
                onSkinSelected = onSkinSelected
            )
            SettingsPage.APPEARANCE -> AppearancePage(
                themeId = themeId,
                uiFontPath = uiFontPath,
                onThemeSelected = onThemeSelected,
                onPickUiFont = onPickUiFont,
                onClearUiFont = onClearUiFont,
                onSelectUiFont = onSelectUiFont,
                onOpenLyricsViewer = { onNavigate(SettingsPage.LYRICS_VIEWER) },
                splashEnabled = splashEnabled,
                onSplashEnabledChange = onSplashEnabledChange,
                splashSoundEnabled = splashSoundEnabled,
                onSplashSoundChange = onSplashSoundChange
            )
            SettingsPage.LYRICS_VIEWER -> LyricsViewerPage(
                lyricsFontPath = lyricsFontPath,
                lyricsFontSize = lyricsFontSize,
                lyricsPrimaryColor = lyricsPrimaryColor,
                lyricsSecondaryColor = lyricsSecondaryColor,
                lyricsBorderColor = lyricsBorderColor,
                lyricsBorderWidth = lyricsBorderWidth,
                onPickFont = onPickFont,
                onClearFont = onClearFont,
                onSelectFont = onSelectLyricsFont,
                onFontSizeChange = onLyricsFontSizeChange,
                onPrimaryColorChange = onLyricsPrimaryColorChange,
                onSecondaryColorChange = onLyricsSecondaryColorChange,
                onBorderColorChange = onLyricsBorderColorChange,
                onBorderWidthChange = onLyricsBorderWidthChange
            )
            SettingsPage.VISUALIZER -> VisualizerPage(
                presetCount = milkPresetCount,
                intervalSec = milkIntervalSec,
                onPickFiles = onPickMilkFiles,
                onClear = onClearMilkPresets,
                onIntervalChange = onMilkIntervalChange
            )
            SettingsPage.BACKUP -> BackupPage(
                onExport = onExportBackup,
                onImport = onImportBackup
            )
            SettingsPage.ABOUT -> AboutPage(
                onOpenChangelog = { onNavigate(SettingsPage.CHANGELOG) },
                onOpenLicenses = { onNavigate(SettingsPage.LICENSES) }
            )
            SettingsPage.CHANGELOG -> ChangelogPage()
            SettingsPage.LICENSES -> LicensesPage()
            else -> {
                val entries = listOf(
                    SettingsEntry(
                        Icons.Rounded.LibraryMusic,
                        "Biblioteca",
                        "Pastas, todos os arquivos, tags"
                    ) { onNavigate(SettingsPage.LIBRARY) },
                    SettingsEntry(
                        Icons.Rounded.PlayArrow,
                        "Player",
                        if (autoFetchLyrics) "Busca automática de letras: ligada" else "Busca automática de letras: desligada"
                    ) { onNavigate(SettingsPage.PLAYER) },
                    SettingsEntry(
                        Icons.Rounded.GraphicEq,
                        "Visualizador",
                        if (milkPresetCount > 0) "$milkPresetCount presets .milk" else "Importe presets .milk"
                    ) { onNavigate(SettingsPage.VISUALIZER) },
                    SettingsEntry(
                        Icons.Rounded.Style,
                        "Skins",
                        skinId.label
                    ) { onNavigate(SettingsPage.SKINS) },
                    SettingsEntry(
                        Icons.Rounded.Palette,
                        "Aparência",
                        themeLabel(themeId)
                    ) { onNavigate(SettingsPage.APPEARANCE) },
                    SettingsEntry(
                        Icons.Rounded.Folder,
                        "Backup",
                        "Exportar ou importar dados"
                    ) { onNavigate(SettingsPage.BACKUP) },
                    SettingsEntry(
                        Icons.Rounded.Info,
                        "Sobre",
                        "Créditos e informações"
                    ) { onNavigate(SettingsPage.ABOUT) }
                )
                LazyColumn {
                    items(entries) { e -> EntryRow(e) }
                }
            }
        }
        } // AnimatedContent
    }
}


@Composable
private fun FontOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
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
private fun EntryRow(e: SettingsEntry) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = e.onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(e.icon, contentDescription = null, tint = Color.White)
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(e.title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(e.subtitle, fontSize = 13.sp, color = Color.White.copy(0.55f))
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.White.copy(0.4f)
        )
    }
}

@Composable
private fun LibraryPage(
    folders: List<SourceFolder>,
    totalTracks: Int,
    settings: LibrarySettings,
    allFilesMode: Boolean,
    onOpenFolders: () -> Unit,
    onSplitArtistsChange: (Boolean) -> Unit,
    onArtistSeparatorsChange: (String) -> Unit,
    onArtistExceptionsChange: (String) -> Unit,
    onSplitGenresChange: (Boolean) -> Unit,
    onGenreSeparatorsChange: (String) -> Unit,
    onAllFilesModeChange: (Boolean) -> Unit,
    onRequestAllFilesPermission: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
    ) {
        // Acesso a todos os arquivos (método principal — sem SAF)
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Gerenciar todos os arquivos", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "Modo principal: lê e apaga músicas em todo o armazenamento (requer permissão \"Acesso a todos os arquivos\")",
                        fontSize = 13.sp,
                        color = Color.White.copy(0.55f)
                    )
                }
                Switch(
                    checked = allFilesMode,
                    onCheckedChange = { enabled ->
                        if (enabled) onRequestAllFilesPermission()
                        else onAllFilesModeChange(false)
                    }
                )
            }
            if (allFilesMode) {
                Text(
                    "Modo ativo · acesso total ao armazenamento",
                    fontSize = 12.sp,
                    color = Color(0xFF81C784),
                    modifier = Modifier.padding(top = 6.dp)
                )
            } else {
                Text(
                    "Recomendado: ative para o app funcionar sem pastas SAF",
                    fontSize = 12.sp,
                    color = Color(0xFFFFB74D),
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        EntryRow(
            SettingsEntry(
                Icons.Rounded.Folder,
                "Pastas extras (opcional)",
                when (folders.size) {
                    0 -> if (allFilesMode) "Usando todos os arquivos" else "Nenhuma pasta · ative o acesso total"
                    1 -> "1 pasta · $totalTracks músicas"
                    else -> "${folders.size} pastas · $totalTracks músicas"
                },
                onOpenFolders
            )
        )

        Text(
            "Tags",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp)
        )

        SeparatorOption(
            title = "Separar artistas",
            description = "Trata os separadores da tag de artista como vários artistas",
            enabled = settings.splitArtists,
            separators = settings.artistSeparators,
            onEnabledChange = onSplitArtistsChange,
            onSeparatorsChange = onArtistSeparatorsChange
        )

        if (settings.splitArtists) {
            ArtistExceptionsField(
                value = settings.artistExceptions,
                onValueChange = onArtistExceptionsChange
            )
        }

        SeparatorOption(
            title = "Separar gêneros",
            description = "Trata os separadores da tag de gênero como vários gêneros",
            enabled = settings.splitGenres,
            separators = settings.genreSeparators,
            onEnabledChange = onSplitGenresChange,
            onSeparatorsChange = onGenreSeparatorsChange
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ArtistExceptionsField(
    value: String,
    onValueChange: (String) -> Unit
) {
    var text by remember { mutableStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
        Text(
            "Exceções em artistas",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            "Nomes que não serão separados (um por linha, ou separados por vírgula). Ex.: AC/DC, Crosby, Stills & Nash",
            fontSize = 12.sp,
            color = Color.White.copy(0.5f),
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
        )
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                onValueChange(it)
            },
            placeholder = { Text("AC/DC\nCrosby, Stills & Nash") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 5
        )
    }
}

@Composable
private fun SeparatorOption(
    title: String,
    description: String,
    enabled: Boolean,
    separators: String,
    onEnabledChange: (Boolean) -> Unit,
    onSeparatorsChange: (String) -> Unit
) {
    // texto local: evita o cursor "pular" ao digitar (o valor vai para o app a cada tecla)
    var text by remember { mutableStateOf(separators) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(description, fontSize = 13.sp, color = Color.White.copy(0.55f))
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
        if (enabled) {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    onSeparatorsChange(it)
                },
                label = { Text("Separadores (separados por espaço)") },
                placeholder = { Text("; / feat.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
        }
    }
}

private fun themeLabel(id: AppThemeId) = when (id) {
    AppThemeId.DYNAMIC -> "Dinâmico (capa do álbum)"
    AppThemeId.DEFAULT -> "Padrão"
    AppThemeId.AQUA -> "Aqua"
    AppThemeId.SUNSET -> "Sunset"
    AppThemeId.FOREST -> "Forest"
    AppThemeId.MIDNIGHT -> "Midnight"
    AppThemeId.ROSE -> "Rose"
    AppThemeId.EMBER -> "Ember"
}

/** Cores de pré-visualização de cada skin (independentes do tema ativo). */
private data class SkinPreview(
    val id: SkinId,
    val description: String,
    val bg: Color,
    val card: Color,
    val accent: Color
)

private val skinPreviews = listOf(
    SkinPreview(
        SkinId.LYS_PLACE,
        "Visual original: fundo em degradê, barras de vidro e navegação flutuante.",
        bg = Color(0xFF241440), card = Color.White.copy(0.14f), accent = Color(0xFFBB86FC)
    ),
    SkinPreview(
        SkinId.NYXT,
        "Fundo preto, azul elétrico, abas no topo, Home com estatísticas e player com capa grande.",
        bg = Color(0xFF000000), card = Color(0xFF4F5BFF), accent = Color(0xFF4F5BFF)
    )
)

@Composable
private fun SkinsPage(
    skinId: SkinId,
    onSkinSelected: (SkinId) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            "Escolha a aparência do app. A troca é aplicada na hora e fica salva.",
            fontSize = 13.sp,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 14.dp)
        )
        skinPreviews.forEach { sp ->
            val selected = skinId == sp.id
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (selected) Color.White.copy(0.14f) else Color.White.copy(0.06f))
                    .border(
                        if (selected) 2.dp else 1.dp,
                        if (selected) sp.accent else Color.White.copy(0.12f),
                        RoundedCornerShape(18.dp)
                    )
                    .clickable { onSkinSelected(sp.id) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // miniatura da skin
                Box(
                    Modifier
                        .size(width = 64.dp, height = 96.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(sp.bg)
                        .border(1.dp, Color.White.copy(0.2f), RoundedCornerShape(12.dp))
                        .padding(7.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(0.55f)))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.weight(1f).height(22.dp).clip(RoundedCornerShape(5.dp)).background(sp.card))
                            Box(Modifier.weight(1f).height(22.dp).clip(RoundedCornerShape(5.dp)).background(sp.card))
                        }
                        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(0.25f)))
                        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(0.25f)))
                    }
                }
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(sp.id.label, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        sp.description,
                        fontSize = 12.sp,
                        color = Color.White.copy(0.6f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (selected) {
                    Text("✓", color = sp.accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(
            "Na NyxT, o tema de cores da página Aparência não se aplica: ela usa a paleta própria (preto + azul).",
            fontSize = 12.sp,
            color = Color.White.copy(0.4f),
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
        )
    }
}

@Composable
private fun AppearancePage(
    themeId: AppThemeId,
    uiFontPath: String?,
    onThemeSelected: (AppThemeId) -> Unit,
    onPickUiFont: () -> Unit,
    onClearUiFont: () -> Unit,
    onSelectUiFont: (String?) -> Unit = {},
    onOpenLyricsViewer: () -> Unit,
    splashEnabled: Boolean = true,
    onSplashEnabledChange: (Boolean) -> Unit = {},
    splashSoundEnabled: Boolean = true,
    onSplashSoundChange: (Boolean) -> Unit = {}
) {
    val themes = listOf(
        AppThemeId.DYNAMIC to "Dinâmico",
        AppThemeId.DEFAULT to "Padrão",
        AppThemeId.AQUA to "Aqua",
        AppThemeId.SUNSET to "Sunset",
        AppThemeId.FOREST to "Forest",
        AppThemeId.MIDNIGHT to "Midnight",
        AppThemeId.ROSE to "Rose",
        AppThemeId.EMBER to "Ember"
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
        item {
            Spacer(Modifier.height(20.dp))
            Text(
                "Fonte do player",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(0.55f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                "Selecionada: ${BuiltInFonts.displayLabel(uiFontPath)}",
                fontSize = 12.sp,
                color = Color.White.copy(0.5f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            // Sistema
            FontOptionRow(
                label = "Padrão do sistema",
                selected = uiFontPath.isNullOrBlank() || uiFontPath == "system",
                onClick = onClearUiFont
            )
            BuiltInFonts.ALL.forEach { font ->
                FontOptionRow(
                    label = font.displayName,
                    selected = uiFontPath == font.storageKey,
                    onClick = { onSelectUiFont(font.storageKey) }
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
            Text(
                "Afeta títulos, listas e interface — não altera a fonte das letras.",
                fontSize = 12.sp,
                color = Color.White.copy(0.4f),
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
            )

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
private fun LyricsViewerPage(
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
        FontOptionRow(
            label = "Padrão do sistema",
            selected = lyricsFontPath.isNullOrBlank() || lyricsFontPath == "system",
            onClick = onClearFont
        )
        BuiltInFonts.ALL.forEach { font ->
            FontOptionRow(
                label = font.displayName,
                selected = lyricsFontPath == font.storageKey,
                onClick = { onSelectFont(font.storageKey) }
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
private fun VisualizerPage(
    presetCount: Int,
    intervalSec: Int,
    onPickFiles: () -> Unit,
    onClear: () -> Unit,
    onIntervalChange: (Int) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            "Na tela de reprodução, toque na capa para ligar o visualizador e toque nele para desligar. " +
                "A animação aparece só dentro da área da capa.",
            fontSize = 14.sp,
            color = Color.White.copy(0.6f),
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(0.08f))
                .clickable(onClick = onPickFiles)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Importar presets", fontSize = 16.sp)
                Text(
                    if (presetCount > 0) "$presetCount presets importados" else "Nenhum preset importado",
                    fontSize = 12.sp,
                    color = Color.White.copy(0.5f)
                )
            }
        }
        Text(
            "Escolha arquivos .milk, texturas usadas por eles (.jpg, .png, .tga) ou um .zip com tudo. " +
                "Você pode selecionar vários de uma vez.",
            fontSize = 12.sp,
            color = Color.White.copy(0.4f),
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
        )
        if (presetCount > 0) {
            Text(
                "Remover todos os presets",
                modifier = Modifier
                    .clickable(onClick = onClear)
                    .padding(vertical = 8.dp),
                fontSize = 14.sp,
                color = Color(0xFFFF8A80)
            )
        }

        Spacer(Modifier.height(20.dp))
        Text(
            if (intervalSec == 0) "Trocar de preset: nunca"
            else "Trocar de preset a cada $intervalSec s",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        androidx.compose.material3.Slider(
            value = intervalSec.toFloat(),
            onValueChange = { onIntervalChange((it / 5f).toInt() * 5) },
            valueRange = 0f..120f,
            steps = 23
        )
        Text(
            "A troca é aleatória e com transição suave. Com apenas 1 preset ela não acontece.",
            fontSize = 12.sp,
            color = Color.White.copy(0.4f)
        )
    }
}

@Composable
private fun BackupPage(
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            "Salve ou restaure preferências, tema, pastas e favoritos.",
            fontSize = 14.sp,
            color = Color.White.copy(0.6f),
            modifier = Modifier.padding(bottom = 20.dp)
        )
        OutlinedButton(
            onClick = onExport,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Exportar backup")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onImport,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Importar backup")
        }
    }
}

@Composable
private fun AboutPage(onOpenChangelog: () -> Unit = {}, onOpenLicenses: () -> Unit = {}) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Text(
            "Lyron Music Player",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            "Versão 0.20 (170)",
            fontSize = 13.sp,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        CreditLine(
            label = "Realizador do projeto",
            value = "PGCR Gui",
            isLink = true,
            onLinkClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://linktr.ee/pgcrgui"))
                context.startActivity(intent)
            }
        )
        CreditLine(
            label = "Idealizador do projeto original",
            value = "Matheus França",
            isLink = true,
            onLinkClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/matheusflm4?vrfl=MTRyaDhwNDByNzhycA=="))
                context.startActivity(intent)
            }
        )
        Column(Modifier.padding(vertical = 8.dp)) {
            Text("Beta testers", fontSize = 13.sp, color = Color.White.copy(0.55f))
            Row(Modifier.padding(top = 2.dp)) {
                Text(
                    "Gean Carlos, ",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF82B1FF),
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://www.instagram.com/cgean0276?srtk=dG4zbDNuNm1vdXBy")
                        )
                        context.startActivity(intent)
                    }
                )
                Text(
                    "Ariel Rodrigues Pires",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF82B1FF),
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://www.instagram.com/ariel_rodrigues147?utm_source=qr&dlrf=MTJ1ejhoOWZna2didA==")
                        )
                        context.startActivity(intent)
                    }
                )
            }
        }
        CreditLine(label = "Desenhista do icon", value = "Taci")

        Spacer(Modifier.height(16.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(0.08f))
                .clickable(onClick = onOpenChangelog)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Info, contentDescription = null, tint = Color.White)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Logs de atualização", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text("Mudanças por versão", fontSize = 12.sp, color = Color.White.copy(0.5f))
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(0.4f)
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(0.08f))
                .clickable(onClick = onOpenLicenses)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Description, contentDescription = null, tint = Color.White)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Licenças dos serviços", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text("Bibliotecas e serviços usados", fontSize = 12.sp, color = Color.White.copy(0.5f))
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(0.4f)
            )
        }

        Spacer(Modifier.height(16.dp))
        CreditLine(
            label = "Página extra do realizador do app",
            value = "Archive of Our Own",
            isLink = true,
            onLinkClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://archiveofourown.org/users/PGCR_gui/works"))
                context.startActivity(intent)
            }
        )
        CreditLine(
            label = "Outras versões do app",
            value = "MediaFire",
            isLink = true,
            onLinkClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.mediafire.com/folder/o020wt1jub0sl/Lyrion_Player"))
                context.startActivity(intent)
            }
        )

        Spacer(Modifier.height(12.dp))
        Column(Modifier.padding(vertical = 8.dp)) {
            Text("Splash Screen", fontSize = 13.sp, color = Color.White.copy(0.55f))
            Text(
                "Foto tirada por PGCR Gui e música extraída de Diamond Rush",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Spacer(Modifier.height(20.dp))
        Row {
            Text(
                "Feito com Grok, Claude.Ai e ",
                fontSize = 14.sp,
                color = Color.White.copy(0.7f)
            )
            Text(
                "Code on the Go",
                fontSize = 14.sp,
                color = Color(0xFF82B1FF),
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://appdevforall.org/code-on-the-go/")
                    )
                    context.startActivity(intent)
                }
            )
        }
    }
}


@Composable
private fun ChangelogPage() {
    val entries = listOf(
    ChangelogEntry(
            "0.25",
            "177",
            listOf(
                "Implementação experimental do projectM",
                )
            ),
        ChangelogEntry(
            "0.20",
            "170",
            listOf(
                "Transições ao trocar de tela (Tava um tolete antes kkkkkkk)",
                "Scan de arquivos mais rápido + scan rápido em segundo plano apenas por arquivos novos",
                "Carregamento da biblioteca em segundo plano durante a splash",
                "Opção para desativar completamente a tela de splash",
                "Fade in/out ao reproduzir/pausar (duração configurável em ms)",
                "Navegação por gestos (deslizar esquerda/direita) para trocar abas na tela principal",
                "BitDepth nas informações técnicas do Now Playing",
                "Ícones de codec (MP3, FLAC, AAC, etc.) no lugar dos nomes de texto",
                "Fonte KOMIKABB removida (estava quebrada)",
                "Edição de tags em lote (Artista, Álbum, Artista do Álbum) no menu de multi-seleção",
                "Submenu de licenças dos serviços na tela de créditos",
                "Links extras e seção Splash Screen na tela de créditos",
                "Otimizações: contador de scan menos agressivo, álbuns calculados em background, keys nas listas Lazy, fade cancelado ao pular faixa, escrita parcial de tags em lote"
            )
        ),
        ChangelogEntry(
            "New Glass!",
            "157",
            listOf(
                "Barra de navegação da skin Ly's Place com indicador deslizável de vidro (liquid glass)",
                "Efeitos liquid glass do app refinados",
                "Indicador animado com spring bounce ao trocar de aba"
            )
        ),
        ChangelogEntry(
            "0.10",
            "150",
            listOf(
                "Sincronizador de letras movido para o editor de tags (edita letras embutidas no arquivo)",
                "Modo simples do sincronizador: uma única caixa de texto; linhas viram caixas no modo sincronizado",
                "Miniplayer do sincronizador: botões −10s e +10s no lugar de anterior/próxima",
                "Página de artista (Ly's Place): grade de álbuns, contagem de músicas e duração total",
                "Página de álbum (Ly's Place): capa grande com fade, Reproduzir/Shuffle, artistas clicáveis e lista de faixas"
            )
        ),
        ChangelogEntry(
            "0.9",
            "145",
            listOf(
                "Animação da capa no Now Playing (Ly's Place): próxima rola da direita para a esquerda; anterior da esquerda para a direita",
                "Pacote de fontes embutidas para player e legendas",
                "Splash screen com cor e fonte aleatórias a cada abertura",
                "Submenu de logs de atualização em Sobre"
            )
        ),
        ChangelogEntry(
            "0.8",
            "142",
            listOf(
                "Base da versão anterior (skins, letras, tags, biblioteca)"
            )
        )
    )
    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        items(entries) { e ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(0.08f))
                    .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text(
                    "v${e.version}  ·  code ${e.code}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Spacer(Modifier.height(8.dp))
                e.changes.forEach { change ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text("•  ", color = Color.White.copy(0.5f), fontSize = 14.sp)
                        Text(change, color = Color.White.copy(0.85f), fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

private data class ChangelogEntry(val version: String, val code: String, val changes: List<String>)

@Composable
private fun LicensesPage() {
    val licenses = listOf(
        "AndroidX / Jetpack Compose" to "Apache License 2.0",
        "Media3 (ExoPlayer)" to "Apache License 2.0",
        "Coil" to "Apache License 2.0",
        "jaudiotagger" to "LGPL 2.1 / Apache 2.0",
        "Kotlin Coroutines" to "Apache License 2.0",
        "Material Icons" to "Apache License 2.0",
        "Palette" to "Apache License 2.0"
    )
    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        items(licenses) { (name, license) ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(0.08f))
                    .padding(16.dp)
            ) {
                Text(name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(license, fontSize = 13.sp, color = Color.White.copy(0.55f), modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun CreditLine(
    label: String,
    value: String,
    isLink: Boolean = false,
    onLinkClick: (() -> Unit)? = null
) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(label, fontSize = 13.sp, color = Color.White.copy(0.55f))
        if (isLink && onLinkClick != null) {
            Text(
                value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF82B1FF),
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clickable(onClick = onLinkClick)
            )
        } else {
            Text(
                value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}


@Composable
private fun PlayerPage(
    autoFetchLyrics: Boolean,
    onAutoFetchLyricsChange: (Boolean) -> Unit,
    shuffleType: ShuffleType,
    onShuffleTypeChange: (ShuffleType) -> Unit,
    bitPerfect: Boolean,
    onBitPerfectChange: (Boolean) -> Unit,
    fadeMs: Int = 300,
    onFadeMsChange: (Int) -> Unit = {},
    soundfontPath: String? = null,
    onPickSoundfont: () -> Unit = {},
    onClearSoundfont: () -> Unit = {},
    onOpenAudioOutput: () -> Unit = {}
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            "Letras",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Busca automática de letras", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(
                    "Quando a faixa não tiver letra nas tags nem arquivo .lrc, busca em lrclib.net",
                    fontSize = 13.sp,
                    color = Color.White.copy(0.55f)
                )
            }
            Switch(checked = autoFetchLyrics, onCheckedChange = onAutoFetchLyricsChange)
        }
        Text(
            "Fonte: lrclib.net · requer conexão com a internet",
            fontSize = 12.sp,
            color = Color.White.copy(0.4f),
            modifier = Modifier.padding(top = 12.dp, bottom = 20.dp)
        )

        Text(
            "Aleatório",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text("Tipo de aleatório", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text(
            "Define como o botão Aleatório da tela de reprodução funciona",
            fontSize = 13.sp,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 10.dp)
        )
        ShuffleTypeOption(
            title = "Absoluto",
            subtitle = "Embaralha a ordem de reprodução dinamicamente (modo padrão)",
            selected = shuffleType == ShuffleType.ABSOLUTE,
            onClick = { onShuffleTypeChange(ShuffleType.ABSOLUTE) }
        )
        Spacer(Modifier.height(8.dp))
        ShuffleTypeOption(
            title = "Fila aleatória",
            subtitle = "Gera uma fila fixa embaralhada a partir da música atual",
            selected = shuffleType == ShuffleType.QUEUE,
            onClick = { onShuffleTypeChange(ShuffleType.QUEUE) }
        )

        Spacer(Modifier.height(24.dp))
        Text(
            "Áudio",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Bit-perfect (DAC)", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(
                    "Tenta reduzir processamento de áudio para saída mais fiel em DACs USB/externos. Pode exigir reiniciar a reprodução.",
                    fontSize = 13.sp,
                    color = Color.White.copy(0.55f)
                )
            }
            Switch(checked = bitPerfect, onCheckedChange = onBitPerfectChange)
        }

        Spacer(Modifier.height(20.dp))
        Text("Fade play/pause", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text(
            if (fadeMs == 0) "Desligado (troca instantânea)"
            else "Duração: ${fadeMs} ms",
            fontSize = 13.sp,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        androidx.compose.material3.Slider(
            value = fadeMs.toFloat(),
            onValueChange = { onFadeMsChange(it.toInt()) },
            valueRange = 0f..2000f,
            steps = 39
        )

        Spacer(Modifier.height(24.dp))
        Text(
            "MIDI",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            "Arquivos .mid / .midi entram na biblioteca. A reprodução usa o sintetizador do sistema.",
            fontSize = 13.sp,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(0.08f))
                .clickable(onClick = onPickSoundfont)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Importar soundfont (.sf2)", fontSize = 16.sp)
                Text(
                    soundfontPath?.let { java.io.File(it).name } ?: "Nenhum soundfont importado",
                    fontSize = 12.sp,
                    color = Color.White.copy(0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (soundfontPath != null) {
            Text(
                "Remover soundfont",
                modifier = Modifier
                    .padding(top = 10.dp)
                    .clickable(onClick = onClearSoundfont)
                    .padding(vertical = 6.dp),
                fontSize = 14.sp,
                color = Color(0xFFFF8A80)
            )
        }
        Text(
            "O soundfont fica salvo no app. Em dispositivos com suporte, pode ser usado na síntese MIDI.",
            fontSize = 12.sp,
            color = Color.White.copy(0.4f),
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
        )

        Spacer(Modifier.height(20.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(0.08f))
                .clickable(onClick = onOpenAudioOutput)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Saída de áudio", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(
                    "Backend (AudioTrack, AAudio…) e taxa de amostragem máxima",
                    fontSize = 13.sp,
                    color = Color.White.copy(0.55f)
                )
            }
            Text("›", fontSize = 22.sp, color = Color.White.copy(0.4f))
        }
    }
}

@Composable
private fun AudioOutputPage(
    audioOutput: MusicViewModel.AudioOutputBackend,
    onAudioOutputChange: (MusicViewModel.AudioOutputBackend) -> Unit,
    forceMaxSampleRate: Boolean,
    onForceMaxSampleRateChange: (Boolean) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            "Backend de saída",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            "Escolha o pipeline de áudio. A mudança é salva e aplicada na próxima sessão de reprodução (reinicie a faixa se necessário).",
            fontSize = 13.sp,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 12.dp)
        )
        val options = listOf(
            MusicViewModel.AudioOutputBackend.DEFAULT to ("Padrão do sistema" to "Deixa o ExoPlayer/Media3 escolher"),
            MusicViewModel.AudioOutputBackend.AUDIOTRACK to ("AudioTrack" to "API clássica do Android"),
            MusicViewModel.AudioOutputBackend.AAUDIO to ("AAudio" to "Baixa latência (API 26+)"),
            MusicViewModel.AudioOutputBackend.OPENSLES to ("OpenSL ES" to "Legado, ampla compatibilidade")
        )
        options.forEach { (id, labels) ->
            val (title, sub) = labels
            Spacer(Modifier.height(6.dp))
            ShuffleTypeOption(
                title = title,
                subtitle = sub,
                selected = audioOutput == id,
                onClick = { onAudioOutputChange(id) }
            )
        }

        Spacer(Modifier.height(28.dp))
        Text(
            "Amostragem",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Forçar maior amostragem", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(
                    "Tenta usar a maior taxa de amostragem permitida pela saída atual (útil em DACs USB). Pode falhar em alguns dispositivos.",
                    fontSize = 13.sp,
                    color = Color.White.copy(0.55f)
                )
            }
            Switch(checked = forceMaxSampleRate, onCheckedChange = onForceMaxSampleRateChange)
        }
        Text(
            "Nota: o ExoPlayer aplica o backend na criação do player. Pare e toque de novo após alterar.",
            fontSize = 12.sp,
            color = Color.White.copy(0.4f),
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

@Composable
private fun ShuffleTypeOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color.White.copy(0.14f) else Color.White.copy(0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 12.sp, color = Color.White.copy(0.55f))
        }
        if (selected) {
            Text("✓", fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun FoldersPage(
    folders: List<SourceFolder>,
    totalTracks: Int,
    isScanning: Boolean,
    scanCount: Int,
    onAddFolder: () -> Unit,
    onRemoveFolder: (String) -> Unit,
    onRefresh: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        if (isScanning) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
            Text("Lendo… $scanCount", fontSize = 13.sp, color = Color.White.copy(0.6f))
        }
        if (folders.isEmpty()) {
            Text(
                "Nenhuma pasta na biblioteca.",
                modifier = Modifier.padding(vertical = 24.dp),
                color = Color.White.copy(0.6f)
            )
        }
        LazyColumn(Modifier.weight(1f)) {
            items(folders, key = { it.uri }) { f ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(f.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(f.path, fontSize = 12.sp, color = Color.White.copy(0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (f.trackCount == 1) "1 faixa" else "${f.trackCount} faixas",
                            fontSize = 12.sp,
                            color = Color.White.copy(0.45f)
                        )
                    }
                    IconButton(onClick = { onRemoveFolder(f.uri) }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Remover", tint = Color.White.copy(0.7f))
                    }
                }
            }
        }
        OutlinedButton(
            onClick = onAddFolder,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Adicionar pasta")
        }
        OutlinedButton(
            onClick = onRefresh,
            enabled = folders.isNotEmpty() && !isScanning,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Atualizar biblioteca")
        }
    }
}
