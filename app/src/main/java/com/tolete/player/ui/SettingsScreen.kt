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
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.tolete.player.ui.MusicViewModel.ShuffleType
import java.io.File

enum class SettingsPage { NONE, MENU, LIBRARY, FOLDERS, APPEARANCE, LYRICS_VIEWER, ABOUT, CHANGELOG, LICENSES, PLAYER, AUDIO_OUTPUT, BACKUP, VISUALIZER }

internal data class SettingsEntry(
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
    dynamicDefaultColor: Int = 0xFFBB86FC.toInt(),
    onDynamicDefaultColorChange: (Int) -> Unit = {},
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
    onIgnoredFoldersChange: (String) -> Unit = {},
    onPickIgnoredFolder: () -> Unit = {},
    onMinDurationSecChange: (Int) -> Unit = {},
    allFilesMode: Boolean = false,
    onAllFilesModeChange: (Boolean) -> Unit = {},
    onRequestAllFilesPermission: () -> Unit = {},
    autoFetchLyrics: Boolean = false,
    onAutoFetchLyricsChange: (Boolean) -> Unit = {},
    shuffleType: ShuffleType = ShuffleType.ABSOLUTE,
    onShuffleTypeChange: (ShuffleType) -> Unit = {},
    bitPerfect: Boolean = false,
    onBitPerfectChange: (Boolean) -> Unit = {},
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
    floatingIconsEnabled: Boolean = true,
    onFloatingIconsChange: (Boolean) -> Unit = {},
    menuBgColor: Int = 0xE8F2F2F7.toInt(),
    onMenuBgColorChange: (Int) -> Unit = {},
    homeButtonFontPath: String? = null,
    onHomeButtonFontSelect: (String?) -> Unit = {},
    homeButtonColor: Int = 0xFFFFFFFF.toInt(),
    onHomeButtonColorChange: (Int) -> Unit = {},
    shareFontPath: String? = null,
    onShareFontSelect: (String?) -> Unit = {},
    shareTextColor: Int = 0xFFF5F5FA.toInt(),
    onShareTextColorChange: (Int) -> Unit = {},
    shareBrandColor: Int = 0xFFFFFFFF.toInt(),
    onShareBrandColorChange: (Int) -> Unit = {},
    fadeMs: Int = 300,
    onFadeMsChange: (Int) -> Unit = {},
    seekBarStyle: SeekBarStyle = SeekBarStyle.Standard,
    onSeekBarStyleChange: (SeekBarStyle) -> Unit = {},
    playCountThreshold: Int = 70,
    onPlayCountThresholdChange: (Int) -> Unit = {},
    milkPresetCount: Int = 0,
    milkIntervalSec: Int = 30,
    onPickMilkFiles: () -> Unit = {},
    onClearMilkPresets: () -> Unit = {},
    onMilkIntervalChange: (Int) -> Unit = {},
    changeSongWithInterval: Boolean = false,
    onChangeSongWithIntervalChange: (Boolean) -> Unit = {},
    changePresetOnSongChange: Boolean = true,
    onChangePresetOnSongChange: (Boolean) -> Unit = {},
    onManageMilkPresets: () -> Unit = {},
    onOpenRecommendedPresets: () -> Unit = {}
) {
    val title = when (page) {
        SettingsPage.LIBRARY -> "Biblioteca"
        SettingsPage.FOLDERS -> "Pastas de música"
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

    Box(Modifier.fillMaxSize()) {
        // Fundo com gradiente + ícones flutuantes (mesma lógica das abas principais)
        Box(
            Modifier
                .fillMaxSize()
                .background(rememberFloatingGradient(appColors))
        ) {
            if (floatingIconsEnabled) {
                FloatingIconsBackground(kind = "settings")
            }
        }
        Column(
            Modifier
                .fillMaxSize()
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
                onIgnoredFoldersChange = onIgnoredFoldersChange,
                onPickIgnoredFolder = onPickIgnoredFolder,
                onMinDurationSecChange = onMinDurationSecChange,
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
                playCountThreshold = playCountThreshold,
                onPlayCountThresholdChange = onPlayCountThresholdChange,
                onOpenAudioOutput = { onNavigate(SettingsPage.AUDIO_OUTPUT) },
                seekBarStyle = seekBarStyle,
                onSeekBarStyleChange = onSeekBarStyleChange
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
            SettingsPage.APPEARANCE -> AppearancePage(
                themeId = themeId,
                uiFontPath = uiFontPath,
                onThemeSelected = onThemeSelected,
                dynamicDefaultColor = dynamicDefaultColor,
                onDynamicDefaultColorChange = onDynamicDefaultColorChange,
                onPickUiFont = onPickUiFont,
                onClearUiFont = onClearUiFont,
                onSelectUiFont = onSelectUiFont,
                onOpenLyricsViewer = { onNavigate(SettingsPage.LYRICS_VIEWER) },
                splashEnabled = splashEnabled,
                onSplashEnabledChange = onSplashEnabledChange,
                splashSoundEnabled = splashSoundEnabled,
                onSplashSoundChange = onSplashSoundChange,
                floatingIconsEnabled = floatingIconsEnabled,
                onFloatingIconsChange = onFloatingIconsChange,
                menuBgColor = menuBgColor,
                onMenuBgColorChange = onMenuBgColorChange,
                homeButtonFontPath = homeButtonFontPath,
                onHomeButtonFontSelect = onHomeButtonFontSelect,
                onPickHomeButtonFont = onPickUiFont,
                homeButtonColor = homeButtonColor,
                onHomeButtonColorChange = onHomeButtonColorChange,
                shareFontPath = shareFontPath,
                onShareFontSelect = onShareFontSelect,
                onPickShareFont = onPickUiFont,
                shareTextColor = shareTextColor,
                onShareTextColorChange = onShareTextColorChange,
                shareBrandColor = shareBrandColor,
                onShareBrandColorChange = onShareBrandColorChange
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
                onIntervalChange = onMilkIntervalChange,
                changeSongWithInterval = changeSongWithInterval,
                onChangeSongWithIntervalChange = onChangeSongWithIntervalChange,
                changePresetOnSongChange = changePresetOnSongChange,
                onChangePresetOnSongChange = onChangePresetOnSongChange,
                onManagePresets = onManageMilkPresets,
                onOpenRecommendedPresets = onOpenRecommendedPresets
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
                val settingsCtx = LocalContext.current
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
                    ) { onNavigate(SettingsPage.ABOUT) },
                    SettingsEntry(
                        Icons.Rounded.Description,
                        "Reportar bugs e sugerir melhorias",
                        "Abre o formulário de feedback"
                    ) {
                        try {
                            settingsCtx.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://forms.gle/FTCnrL7DhofQwEV26")
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (_: Exception) {}
                    }
                )
                LazyColumn {
                    items(entries) { e -> EntryRow(e) }
                }
            }
        }
        } // AnimatedContent
        } // Column
    } // outer Box
}


@Composable
internal fun EntryRow(e: SettingsEntry) {
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

