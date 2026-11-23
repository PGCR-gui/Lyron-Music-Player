package com.tolete.player.ui

import android.content.Intent
import android.content.Context
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
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Sync
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
import androidx.compose.runtime.produceState
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
import com.tolete.player.data.LyricsEmbedder
import com.tolete.player.data.SourceFolder
import com.tolete.player.ui.MusicViewModel
import com.tolete.player.ui.MusicViewModel.AppThemeId
import com.tolete.player.ui.MusicViewModel.ShuffleType
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class SettingsPage { NONE, MENU, LIBRARY, FOLDERS, ORGANIZE, APPEARANCE, LYRICS_VIEWER, ABOUT, FONT_CREDITS, CHANGELOG, LICENSES, PLAYER, BACKUP, VISUALIZER, ADVANCED, SCROBBLE, LYRICS_CONTRIBUTIONS }

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
    lyricsTranslationFontPath: String? = null,
    lyricsBackingFontPath: String? = null,
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
    dynamicFloatColors: List<Int> = emptyList(),
    onDynamicFloatColorsChange: (List<Int>) -> Unit = {},
    onPickFont: () -> Unit,
    onClearFont: () -> Unit,
    onPickUiFont: () -> Unit,
    onClearUiFont: () -> Unit,
    onSelectUiFont: (String?) -> Unit = {},
    onSelectLyricsFont: (String?) -> Unit = {},
    onPickTranslationFont: () -> Unit = {},
    onClearTranslationFont: () -> Unit = {},
    onSelectTranslationFont: (String?) -> Unit = {},
    onPickBackingFont: () -> Unit = {},
    onClearBackingFont: () -> Unit = {},
    onSelectBackingFont: (String?) -> Unit = {},
    onSplitArtistsChange: (Boolean) -> Unit,
    onArtistSeparatorsChange: (String) -> Unit,
    onArtistExceptionsChange: (String) -> Unit,
    onSplitGenresChange: (Boolean) -> Unit,
    onGenreSeparatorsChange: (String) -> Unit,
    onIgnoredFoldersChange: (String) -> Unit = {},
    onPickIgnoredFolder: () -> Unit = {},
    onMinDurationSecChange: (Int) -> Unit = {},
    onFirstLetterOrderChange: (Boolean) -> Unit = {},
    onArtistAutoFetchModeChange: (String) -> Unit = {},
    lastFmUsername: String? = null,
    onLastFmUsernameChange: (String?) -> Unit = {},
    lastFmSyncMessage: String? = null,
    onSyncLastFm: () -> Unit = {},
    onClearPlaybackHistory: () -> Unit = {},
    onClearFavorites: () -> Unit = {},
    libraryRootPath: String? = null,
    onPickLibraryRoot: () -> Unit = {},
    onBulkRenameByTags: () -> Unit = {},
    onMoveToLibraryRoot: () -> Unit = {},
    organizeStatus: String? = null,
    allFilesMode: Boolean = false,
    onAllFilesModeChange: (Boolean) -> Unit = {},
    onRequestAllFilesPermission: () -> Unit = {},
    autoFetchLyrics: Boolean = false,
    onAutoFetchLyricsChange: (Boolean) -> Unit = {},
    autoLyricsSaveTarget: LyricsEmbedder.Target = LyricsEmbedder.Target.LRC_FILE,
    onAutoLyricsSaveTargetChange: (LyricsEmbedder.Target) -> Unit = {},
    preferNeteaseRomanization: Boolean = false,
    onPreferNeteaseRomanizationChange: (Boolean) -> Unit = {},
    lyricsPopupEnabled: Boolean = false,
    onLyricsPopupEnabledChange: (Boolean) -> Unit = {},
    searchStaggerMs: Int = 50,
    onSearchStaggerMsChange: (Int) -> Unit = {},
    lyricsTypeOrder: List<String> = listOf("WORD", "SYNCED", "PLAIN"),
    onMoveLyricsType: (Int, Int) -> Unit = { _, _ -> },
    lyricsSourceOrder: List<String> = listOf("LYRON_REPOSITORY", "LRCLIB", "QQ_MUSIC", "NETEASE", "KUGOU"),
    onMoveLyricsSource: (Int, Int) -> Unit = { _, _ -> },
    shuffleType: ShuffleType = ShuffleType.ABSOLUTE,
    onShuffleTypeChange: (ShuffleType) -> Unit = {},
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
    lyricsBackingBorderColor: Int = lyricsBorderColor,
    onLyricsBackingBorderColorChange: (Int) -> Unit = {},
    lyricsBackingBorderWidth: Float = lyricsBorderWidth,
    onLyricsBackingBorderWidthChange: (Float) -> Unit = {},
    lyricsTranslationBorderColor: Int = lyricsBorderColor,
    onLyricsTranslationBorderColorChange: (Int) -> Unit = {},
    lyricsTranslationBorderWidth: Float = lyricsBorderWidth,
    onLyricsTranslationBorderWidthChange: (Float) -> Unit = {},
    lyricsTranslationColor: Int = 0xFFB0BEC5.toInt(),
    onLyricsTranslationColorChange: (Int) -> Unit = {},
    lyricsBackingColor: Int = 0xFFCE93D8.toInt(),
    onLyricsBackingColorChange: (Int) -> Unit = {},
    lyricsBackingSecondaryColor: Int = 0x99CE93D8.toInt(),
    onLyricsBackingSecondaryColorChange: (Int) -> Unit = {},
    onExportBackup: (MusicViewModel.BackupOptions) -> Unit = {},
    onImportBackup: (MusicViewModel.BackupOptions) -> Unit = {},
    splashEnabled: Boolean = true,
    onSplashEnabledChange: (Boolean) -> Unit = {},
    splashSoundEnabled: Boolean = true,
    onSplashSoundChange: (Boolean) -> Unit = {},
    floatingIconsEnabled: Boolean = true,
    onFloatingIconsChange: (Boolean) -> Unit = {},
    glassQuality: GlassQuality = GlassQuality.FULL,
    onGlassQualityChange: (GlassQuality) -> Unit = {},
    homeButtonFontPath: String? = null,
    onHomeButtonFontSelect: (String?) -> Unit = {},
    homeButtonColor: Int = 0xFFFFFFFF.toInt(),
    onHomeButtonColorChange: (Int) -> Unit = {},
    shareFontPath: String? = null,
    onShareFontSelect: (String?) -> Unit = {},
    shareTextColor: Int = 0xFFF5F5FA.toInt(),
    onShareTextColorChange: (Int) -> Unit = {},
    shareBgColor: Int = 0xFF121212.toInt(),
    onShareBgColorChange: (Int) -> Unit = {},
    shareBrandColor: Int = 0xFFFFFFFF.toInt(),
    onShareBrandColorChange: (Int) -> Unit = {},
    fadeMs: Int = 300,
    onFadeMsChange: (Int) -> Unit = {},
    crossfadeMs: Int = 0,
    onCrossfadeMsChange: (Int) -> Unit = {},
    seekBarStyle: SeekBarStyle = SeekBarStyle.Standard,
    onSeekBarStyleChange: (SeekBarStyle) -> Unit = {},
    seekBarWaveformTheme: WaveformTheme = WaveformTheme.BARS,
    onSeekBarWaveformThemeChange: (WaveformTheme) -> Unit = {},
    liveWaveformEnabled: Boolean = false,
    onLiveWaveformEnabledChange: (Boolean) -> Unit = {},
    liveWaveformFps: Int = 30,
    onLiveWaveformFpsChange: (Int) -> Unit = {},
    immersiveNowPlaying: Boolean = true,
    onImmersiveNowPlayingChange: (Boolean) -> Unit = {},
    audioOutput: MusicViewModel.AudioOutputBackend = MusicViewModel.AudioOutputBackend.DEFAULT,
    onAudioOutputChange: (MusicViewModel.AudioOutputBackend) -> Unit = {},
    bitPerfect: Boolean = false,
    onBitPerfectChange: (Boolean) -> Unit = {},
    playCountThreshold: Int = 70,
    onPlayCountThresholdChange: (Int) -> Unit = {},
    playCountMinutes: Int = 0,
    onPlayCountMinutesChange: (Int) -> Unit = {},
    milkPresetCount: Int = 0,
    milkIntervalSec: Int = 30,
    onPickMilkFiles: () -> Unit = {},
    onClearMilkPresets: () -> Unit = {},
    onMilkIntervalChange: (Int) -> Unit = {},
    milkSwitchMode: String = "TIME",
    onMilkSwitchModeChange: (String) -> Unit = {},
    milkPresetOrder: String = "RANDOM",
    onMilkPresetOrderChange: (String) -> Unit = {},
    milkFavoritesOnly: Boolean = false,
    onMilkFavoritesOnlyChange: (Boolean) -> Unit = {},
    onManageMilkPresets: () -> Unit = {},
    onOpenRecommendedPresets: () -> Unit = {},
    onPickDataFolder: () -> Unit = {},
    lyricsEmphasisFontPath: String? = null,
    onSelectLyricsEmphasisFont: (String?) -> Unit = {},
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
    val title = when (page) {
        SettingsPage.LIBRARY -> "Biblioteca"
        SettingsPage.FOLDERS -> "Pastas de música"
        SettingsPage.ORGANIZE -> "Organizar biblioteca"
        SettingsPage.APPEARANCE -> "Aparência"
        SettingsPage.LYRICS_VIEWER -> "Visualizador de legendas"
        SettingsPage.ABOUT -> "Sobre"
        SettingsPage.FONT_CREDITS -> "Créditos das fontes"
        SettingsPage.CHANGELOG -> "Logs de atualização"
        SettingsPage.LICENSES -> "Licenças dos serviços"
        SettingsPage.PLAYER -> "Player"
        SettingsPage.BACKUP -> "Backup"
        SettingsPage.VISUALIZER -> "Visualizador (.milk)"
        SettingsPage.ADVANCED -> "Avançado"
        SettingsPage.LYRICS_CONTRIBUTIONS -> "Contribuições de letras"
        SettingsPage.SCROBBLE -> "Scrobbling (Last.fm)"
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
                onOpenFolders = { onNavigate(SettingsPage.ORGANIZE) },
                onSplitArtistsChange = onSplitArtistsChange,
                onArtistSeparatorsChange = onArtistSeparatorsChange,
                onArtistExceptionsChange = onArtistExceptionsChange,
                onSplitGenresChange = onSplitGenresChange,
                onGenreSeparatorsChange = onGenreSeparatorsChange,
                onIgnoredFoldersChange = onIgnoredFoldersChange,
                onPickIgnoredFolder = onPickIgnoredFolder,
                onMinDurationSecChange = onMinDurationSecChange,
                onFirstLetterOrderChange = onFirstLetterOrderChange,
                onArtistAutoFetchModeChange = onArtistAutoFetchModeChange,
                onAllFilesModeChange = onAllFilesModeChange,
                onRequestAllFilesPermission = onRequestAllFilesPermission,
                lastFmUsername = lastFmUsername,
                onLastFmUsernameChange = onLastFmUsernameChange,
                lastFmSyncMessage = lastFmSyncMessage,
                onSyncLastFm = onSyncLastFm,
                onClearPlaybackHistory = onClearPlaybackHistory,
                onClearFavorites = onClearFavorites
            )
            SettingsPage.PLAYER -> PlayerPage(
                autoFetchLyrics = autoFetchLyrics,
                onAutoFetchLyricsChange = onAutoFetchLyricsChange,
                autoLyricsSaveTarget = autoLyricsSaveTarget,
                onAutoLyricsSaveTargetChange = onAutoLyricsSaveTargetChange,
                preferNeteaseRomanization = preferNeteaseRomanization,
                onPreferNeteaseRomanizationChange = onPreferNeteaseRomanizationChange,
                lyricsPopupEnabled = lyricsPopupEnabled,
                onLyricsPopupEnabledChange = onLyricsPopupEnabledChange,
                searchStaggerMs = searchStaggerMs,
                onSearchStaggerMsChange = onSearchStaggerMsChange,
                lyricsTypeOrder = lyricsTypeOrder,
                onMoveLyricsType = onMoveLyricsType,
                lyricsSourceOrder = lyricsSourceOrder,
                onMoveLyricsSource = onMoveLyricsSource,
                shuffleType = shuffleType,
                onShuffleTypeChange = onShuffleTypeChange,
                fadeMs = fadeMs,
                onFadeMsChange = onFadeMsChange,
                crossfadeMs = crossfadeMs,
                onCrossfadeMsChange = onCrossfadeMsChange,
                playCountThreshold = playCountThreshold,
                onPlayCountThresholdChange = onPlayCountThresholdChange,
                playCountMinutes = playCountMinutes,
                onPlayCountMinutesChange = onPlayCountMinutesChange,
                seekBarStyle = seekBarStyle,
                onSeekBarStyleChange = onSeekBarStyleChange,
                seekBarWaveformTheme = seekBarWaveformTheme,
                onSeekBarWaveformThemeChange = onSeekBarWaveformThemeChange,
                liveWaveformEnabled = liveWaveformEnabled,
                onLiveWaveformEnabledChange = onLiveWaveformEnabledChange,
                liveWaveformFps = liveWaveformFps,
                onLiveWaveformFpsChange = onLiveWaveformFpsChange,
                immersiveNowPlaying = immersiveNowPlaying,
                onImmersiveNowPlayingChange = onImmersiveNowPlayingChange,
                audioOutput = audioOutput,
                onAudioOutputChange = onAudioOutputChange,
                bitPerfect = bitPerfect,
                onBitPerfectChange = onBitPerfectChange
            )
            SettingsPage.ORGANIZE -> OrganizeLibraryPage(
                libraryRootPath = libraryRootPath,
                onPickLibraryRoot = onPickLibraryRoot,
                onBulkRenameByTags = onBulkRenameByTags,
                onMoveToLibraryRoot = onMoveToLibraryRoot,
                organizeStatus = organizeStatus
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
                dynamicFloatColors = dynamicFloatColors,
                onDynamicFloatColorsChange = onDynamicFloatColorsChange,
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
                shareBgColor = shareBgColor,
                onShareBgColorChange = onShareBgColorChange,
                shareBrandColor = shareBrandColor,
                onShareBrandColorChange = onShareBrandColorChange,
                menuVisualStyle = menuVisualStyle,
                onMenuVisualStyleChange = onMenuVisualStyleChange,
                photoThemePath = photoThemePath,
                onPickPhotoTheme = onPickPhotoTheme,
                onClearPhotoTheme = onClearPhotoTheme,
                photoThemeBlur = photoThemeBlur,
                onPhotoThemeBlurChange = onPhotoThemeBlurChange,
                dynamicAlbumBlur = dynamicAlbumBlur,
                onDynamicAlbumBlurChange = onDynamicAlbumBlurChange,
                alphabetScrollerStyle = alphabetScrollerStyle,
                onAlphabetScrollerStyleChange = onAlphabetScrollerStyleChange,
                waveformTheme = waveformTheme,
                onWaveformThemeChange = onWaveformThemeChange
            )
            SettingsPage.LYRICS_VIEWER -> LyricsViewerPage(
                lyricsFontPath = lyricsFontPath,
                lyricsTranslationFontPath = lyricsTranslationFontPath,
                lyricsBackingFontPath = lyricsBackingFontPath,
                lyricsEmphasisFontPath = lyricsEmphasisFontPath,
                lyricsFontSize = lyricsFontSize,
                lyricsPrimaryColor = lyricsPrimaryColor,
                lyricsSecondaryColor = lyricsSecondaryColor,
                lyricsBorderColor = lyricsBorderColor,
                lyricsBorderWidth = lyricsBorderWidth,
                lyricsBackingBorderColor = lyricsBackingBorderColor,
                lyricsBackingBorderWidth = lyricsBackingBorderWidth,
                lyricsTranslationBorderColor = lyricsTranslationBorderColor,
                lyricsTranslationBorderWidth = lyricsTranslationBorderWidth,
                lyricsTranslationColor = lyricsTranslationColor,
                lyricsBackingColor = lyricsBackingColor,
                lyricsBackingSecondaryColor = lyricsBackingSecondaryColor,
                onPickFont = onPickFont,
                onClearFont = onClearFont,
                onSelectFont = onSelectLyricsFont,
                onPickTranslationFont = onPickTranslationFont,
                onClearTranslationFont = onClearTranslationFont,
                onSelectTranslationFont = onSelectTranslationFont,
                onPickBackingFont = onPickBackingFont,
                onClearBackingFont = onClearBackingFont,
                onSelectBackingFont = onSelectBackingFont,
                onSelectLyricsEmphasisFont = onSelectLyricsEmphasisFont,
                onFontSizeChange = onLyricsFontSizeChange,
                onPrimaryColorChange = onLyricsPrimaryColorChange,
                onSecondaryColorChange = onLyricsSecondaryColorChange,
                onBorderColorChange = onLyricsBorderColorChange,
                onBorderWidthChange = onLyricsBorderWidthChange,
                onBackingBorderColorChange = onLyricsBackingBorderColorChange,
                onBackingBorderWidthChange = onLyricsBackingBorderWidthChange,
                onTranslationBorderColorChange = onLyricsTranslationBorderColorChange,
                onTranslationBorderWidthChange = onLyricsTranslationBorderWidthChange,
                onTranslationColorChange = onLyricsTranslationColorChange,
                onBackingColorChange = onLyricsBackingColorChange,
                onBackingSecondaryColorChange = onLyricsBackingSecondaryColorChange
            )
            SettingsPage.VISUALIZER -> VisualizerPage(
                presetCount = milkPresetCount,
                intervalSec = milkIntervalSec,
                onPickFiles = onPickMilkFiles,
                onClear = onClearMilkPresets,
                onIntervalChange = onMilkIntervalChange,
                switchMode = milkSwitchMode,
                onSwitchModeChange = onMilkSwitchModeChange,
                presetOrder = milkPresetOrder,
                onPresetOrderChange = onMilkPresetOrderChange,
                favoritesOnly = milkFavoritesOnly,
                onFavoritesOnlyChange = onMilkFavoritesOnlyChange,
                onManagePresets = onManageMilkPresets,
                onOpenRecommendedPresets = onOpenRecommendedPresets
            )
            SettingsPage.BACKUP -> BackupPage(
                onExport = onExportBackup,
                onImport = onImportBackup
            )
            SettingsPage.ADVANCED -> AdvancedPage(
                onPickCustomFolder = onPickDataFolder,
                glassQuality = glassQuality,
                onGlassQualityChange = onGlassQualityChange
            )
            SettingsPage.LYRICS_CONTRIBUTIONS -> LyricsContributionsPage(appColors)
            SettingsPage.SCROBBLE -> ScrobblePage()
            SettingsPage.ABOUT -> AboutPage(
                onOpenChangelog = { onNavigate(SettingsPage.CHANGELOG) },
                onOpenLicenses = { onNavigate(SettingsPage.LICENSES) },
                onOpenFontCredits = { onNavigate(SettingsPage.FONT_CREDITS) }
            )
            SettingsPage.FONT_CREDITS -> FontCreditsPage()
            SettingsPage.CHANGELOG -> ChangelogPage()
            SettingsPage.LICENSES -> LicensesPage()
            else -> {
                val settingsCtx = LocalContext.current
                val entries = listOf(
                    SettingsEntry(
                        Icons.Rounded.LibraryMusic,
                        "Biblioteca",
                        "Pastas, acesso amplo e tags"
                    ) { onNavigate(SettingsPage.LIBRARY) },
                    SettingsEntry(
                        Icons.Rounded.PlayArrow,
                        "Player",
                        "Letras automáticas, seekbar, Letras Pop-Up"
                    ) { onNavigate(SettingsPage.PLAYER) },
                    SettingsEntry(
                        Icons.Rounded.Sync,
                        "Scrobbling (Last.fm)",
                        "Entrar na conta e enviar o que você ouve"
                    ) { onNavigate(SettingsPage.SCROBBLE) },
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
                        Icons.Rounded.Share,
                        "Contribuições de letras",
                        "Reenviar arquivos LRC e instruções para o index.json do GitHub"
                    ) { onNavigate(SettingsPage.LYRICS_CONTRIBUTIONS) },
                    SettingsEntry(
                        Icons.Rounded.Settings,
                        "Avançado",
                        "Pasta de dados, qualidade dos efeitos e gerenciador de espaço"
                    ) { onNavigate(SettingsPage.ADVANCED) },
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


@Composable
private fun AdvancedPage(
    onPickCustomFolder: () -> Unit,
    glassQuality: GlassQuality,
    onGlassQualityChange: (GlassQuality) -> Unit
) {
    val context = LocalContext.current
    var mode by remember {
        mutableStateOf(com.tolete.player.data.LyronStorage.getMode())
    }
    val customPath = com.tolete.player.data.LyronStorage.getCustomRootPath()

    fun applyMode(m: com.tolete.player.data.LyronStorage.Mode, path: String? = null) {
        com.tolete.player.data.LyronStorage.setMode(context, m, path)
        mode = m
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            "Pasta de dados",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            "Escolha onde o app salva playlists, favoritos, histórico, fotos de artistas e infos.",
            fontSize = 13.sp,
            color = Color.White.copy(0.6f),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        DataFolderOption(
            selected = mode == com.tolete.player.data.LyronStorage.Mode.DEFAULT,
            title = "Padrão",
            subtitle = "emulated/0/Lyron",
            onClick = { applyMode(com.tolete.player.data.LyronStorage.Mode.DEFAULT) }
        )
        DataFolderOption(
            selected = mode == com.tolete.player.data.LyronStorage.Mode.INTERNAL,
            title = "Interno",
            subtitle = "Salva internamente no app. Trechos de letra exportados continuam sendo salvos em Lyron/Lyrics Cards e vídeos em Lyron/Lyrics Videos; Cards de retrospectivas e do Last.fm também continuam em suas próprias pastas.",
            onClick = { applyMode(com.tolete.player.data.LyronStorage.Mode.INTERNAL) }
        )
        DataFolderOption(
            selected = mode == com.tolete.player.data.LyronStorage.Mode.CUSTOM,
            title = "Personalizado",
            subtitle = customPath ?: "Toque para escolher uma pasta",
            onClick = {
                onPickCustomFolder()
            }
        )

        Spacer(Modifier.height(24.dp))
        Text(
            "Ao mudar a pasta, dados existentes na pasta anterior não são movidos automaticamente. Use Backup se precisar transferir.",
            fontSize = 12.sp,
            color = Color.White.copy(0.45f)
        )

        Spacer(Modifier.height(28.dp))
        Text(
            "Contribuição de letras",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            "Este nome será usado em sync_by no trecho de index.json. Se deixar vazio, o Lyron perguntará antes de gerar os arquivos para compartilhar.",
            fontSize = 13.sp,
            color = Color.White.copy(0.6f),
            modifier = Modifier.padding(bottom = 12.dp)
        )
        val prefs = remember(context) {
            context.getSharedPreferences("tolete", Context.MODE_PRIVATE)
        }
        var lyricsSyncBy by remember {
            mutableStateOf(
                prefs.getString(LyricsContributionShareHelper.SYNC_BY_PREFERENCE_KEY, "").orEmpty()
            )
        }
        OutlinedTextField(
            value = lyricsSyncBy,
            onValueChange = { value ->
                lyricsSyncBy = value
                prefs.edit().putString(LyricsContributionShareHelper.SYNC_BY_PREFERENCE_KEY, value).apply()
            },
            label = { Text("Nome para sync_by") },
            placeholder = { Text("Usuário do GitHub") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(32.dp))
        Text(
            "Efeitos de vidro",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            "As barras e o mini player usam vidro; os menus também usam esses efeitos quando o estilo Vidro está selecionado. Os efeitos mais pesados gastam GPU e bateria; reduza se o app travar ou esquentar.",
            fontSize = 13.sp,
            color = Color.White.copy(0.6f),
            modifier = Modifier.padding(bottom = 16.dp)
        )
        DataFolderOption(
            selected = glassQuality == GlassQuality.FULL,
            title = "Completo",
            subtitle = "Lente com distorção, desfoque e brilho. A distorção só funciona no Android 13+.",
            onClick = { onGlassQualityChange(GlassQuality.FULL) }
        )
        DataFolderOption(
            selected = glassQuality == GlassQuality.NO_DISTORTION,
            title = "Sem distorção",
            subtitle = "Mantém o desfoque do fundo, sem o efeito de lente.",
            onClick = { onGlassQualityChange(GlassQuality.NO_DISTORTION) }
        )
        DataFolderOption(
            selected = glassQuality == GlassQuality.LIGHT,
            title = "Leve",
            subtitle = "Sem desfoque nem distorção: fundo translúcido simples. O mais leve para o aparelho.",
            onClick = { onGlassQualityChange(GlassQuality.LIGHT) }
        )

        Spacer(Modifier.height(32.dp))
        Text(
            "Armazenamento",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            "Veja quanto espaço o app usa e limpe o cache, cada item separadamente ou todos os dados.",
            fontSize = 13.sp,
            color = Color.White.copy(0.6f),
            modifier = Modifier.padding(bottom = 16.dp)
        )
        val usedBytes by produceState<Long?>(initialValue = null) {
            value = withContext(Dispatchers.IO) {
                try { com.tolete.player.data.StorageManager.computeUsage(context).totalBytes } catch (_: Exception) { null }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(0.05f))
                .clickable {
                    context.startActivity(Intent(context, com.tolete.player.ManageSpaceActivity::class.java))
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Storage, contentDescription = null, tint = Color.White.copy(0.85f))
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text("Gerenciar espaço", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White)
                Text(
                    usedBytes?.let { "Dados do app: " + com.tolete.player.data.StorageManager.formatSize(context, it) }
                        ?: "Dados do app, cache e limpeza",
                    fontSize = 12.sp,
                    color = Color.White.copy(0.55f)
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(0.4f)
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DataFolderOption(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color.White.copy(0.12f) else Color.White.copy(0.05f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) Color(0xFF7C5CFF) else Color.White.copy(0.2f)),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White)
            Text(subtitle, fontSize = 12.sp, color = Color.White.copy(0.55f))
        }
    }
    Spacer(Modifier.height(8.dp))
}
