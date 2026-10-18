package com.tolete.player.ui

import androidx.compose.material3.RadioButton

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.foundation.lazy.grid.items as lazyGridItems
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.tolete.player.data.Album
import com.tolete.player.data.Artist
import com.tolete.player.data.Genre
import com.tolete.player.data.Playlist
import com.tolete.player.data.Track
import java.io.File

/** Fonte padrão da interface (não afeta letras). Null = sistema. */
val LocalUiFontFamily = staticCompositionLocalOf<FontFamily?> { null }

private data class GroupItem(
    val title: String,
    val subtitle: String,
    val key: String,
    val artPath: String? = null,
    val round: Boolean = false,
    val isFavorites: Boolean = false
)

private fun faixas(n: Int) = if (n == 1) "1 faixa" else "$n faixas"

/** Fundo padrão das telas do app (fallback estático). */
internal val AppGradient = Brush.verticalGradient(listOf(Color(0xFF241440), Color(0xFF0B0B14)))

/** Faz a tela consumir todos os toques, para não "vazarem" para a tela de baixo. */
internal fun Modifier.pointerBlock(): Modifier = pointerInput(Unit) {}

internal fun formatDuration(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}

private fun tabTitle(tab: MusicViewModel.Tab) = when (tab) {
    MusicViewModel.Tab.ALL -> "Todas as músicas"
    MusicViewModel.Tab.ARTISTS -> "Artistas"
    MusicViewModel.Tab.ALBUMS -> "Álbuns"
    MusicViewModel.Tab.GENRES -> "Gêneros"
    MusicViewModel.Tab.PLAYLISTS -> "Playlists"
}

@Composable
fun TolereApp(
    vm: MusicViewModel,
    onPickFolder: () -> Unit,
    onExportM3u: (playlistName: String, content: String) -> Unit = { _, _ -> },
    onImportM3u: () -> Unit = {},
    onPickFont: () -> Unit = {},
    onPickUiFont: () -> Unit = {},
    onPickMilkFiles: () -> Unit = {},
    onDeleteTracks: (List<Track>) -> Unit = {},
    onRequestAllFilesPermission: () -> Unit = {},
    onExportBackup: () -> Unit = {},
    onImportBackup: () -> Unit = {}
) {
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val isScanning by vm.isScanning.collectAsStateWithLifecycle()
    val tab by vm.currentTab.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val visible by vm.visibleTracks.collectAsStateWithLifecycle()
    val nowPlaying by vm.nowPlaying.collectAsStateWithLifecycle()
    val isPlaying by vm.isPlaying.collectAsStateWithLifecycle()
    val artists by vm.artists.collectAsStateWithLifecycle()
    val albums by vm.albums.collectAsStateWithLifecycle()
    val genres by vm.genres.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val sourceFolders by vm.sourceFolders.collectAsStateWithLifecycle()
    val scanCount by vm.scanCount.collectAsStateWithLifecycle()
    val queue by vm.queue.collectAsStateWithLifecycle()
    val shuffle by vm.shuffle.collectAsStateWithLifecycle()
    val isSearching by vm.isSearching.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val searchResults by vm.searchResults.collectAsStateWithLifecycle()
    val searchArtists by vm.searchArtists.collectAsStateWithLifecycle()
    val searchAlbums by vm.searchAlbums.collectAsStateWithLifecycle()
    val searchGenres by vm.searchGenres.collectAsStateWithLifecycle()
    val themeId by vm.themeId.collectAsStateWithLifecycle()
    val skinId by vm.skinId.collectAsStateWithLifecycle()
    val repeatMode by vm.repeatMode.collectAsStateWithLifecycle()
    val isNyxT = skinId == MusicViewModel.SkinId.NYXT
    val lyricsFontPath by vm.lyricsFontPath.collectAsStateWithLifecycle()
    val uiFontPath by vm.uiFontPath.collectAsStateWithLifecycle()
    val librarySettings by vm.librarySettings.collectAsStateWithLifecycle()
    val allFilesMode by vm.allFilesMode.collectAsStateWithLifecycle()
    val autoFetchLyrics by vm.autoFetchLyrics.collectAsStateWithLifecycle()
    val shuffleType by vm.shuffleType.collectAsStateWithLifecycle()
    val bitPerfect by vm.bitPerfect.collectAsStateWithLifecycle()
    val fadeMs by vm.fadeMs.collectAsStateWithLifecycle()
    val milkPresets by vm.milkPresets.collectAsStateWithLifecycle()
    val milkIntervalSec by vm.milkIntervalSec.collectAsStateWithLifecycle()
    val audioOutput by vm.audioOutput.collectAsStateWithLifecycle()
    val forceMaxSampleRate by vm.forceMaxSampleRate.collectAsStateWithLifecycle()
    val lyricsFontSize by vm.lyricsFontSize.collectAsStateWithLifecycle()
    val lyricsPrimaryColor by vm.lyricsPrimaryColor.collectAsStateWithLifecycle()
    val lyricsSecondaryColor by vm.lyricsSecondaryColor.collectAsStateWithLifecycle()
    val lyricsBorderColor by vm.lyricsBorderColor.collectAsStateWithLifecycle()
    val lyricsBorderWidth by vm.lyricsBorderWidth.collectAsStateWithLifecycle()
    val splashEnabled by vm.splashEnabled.collectAsStateWithLifecycle()
    val splashSoundEnabled by vm.splashSoundEnabled.collectAsStateWithLifecycle()
    val groupSort by vm.groupSort.collectAsStateWithLifecycle()
    val groupOrder by vm.groupOrder.collectAsStateWithLifecycle()
    val trackSort by vm.trackSort.collectAsStateWithLifecycle()
    val trackOrder by vm.trackOrder.collectAsStateWithLifecycle()

    val themeColors = rememberAppColors(themeId, nowPlaying?.artPath, isPlaying)
    val appColors = if (isNyxT) NyxTAppColors else themeColors

    val uiFontFamily = remember(uiFontPath) {
        BuiltInFonts.resolveFamily(uiFontPath)
    }

    var showNowPlaying by rememberSaveable { mutableStateOf(false) }
    var settingsPage by rememberSaveable { mutableStateOf(SettingsPage.NONE) }
    var showHelp by rememberSaveable { mutableStateOf(false) }

    // Multi-seleção / add-to-playlist state
    var selectedTrackIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showSelectionActions by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var lyricsEditTrack by remember { mutableStateOf<Track?>(null) }
    var tagEditTrack by remember { mutableStateOf<Track?>(null) }
    var showBatchTagEdit by remember { mutableStateOf(false) }
    var batchTagTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var showBatchRename by remember { mutableStateOf(false) }
    var batchRenameTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var addToPlaylistTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var createPlaylistInitialTrack by remember { mutableStateOf<Track?>(null) }
    val selectionMode = selectedTrackIds.isNotEmpty()

    val backdrop = rememberGraphicsLayer()
    val bottomPadding = if (nowPlaying != null) 190.dp else 110.dp

    BackHandler(enabled = selectionMode) { selectedTrackIds = emptySet() }
    BackHandler(enabled = isSearching) { vm.closeSearch() }
    BackHandler(enabled = filter != null && !isSearching) { vm.clearFilter() }
    BackHandler(enabled = settingsPage != SettingsPage.NONE) {
        settingsPage = when (settingsPage) {
            SettingsPage.FOLDERS -> SettingsPage.LIBRARY
            SettingsPage.AUDIO_OUTPUT -> SettingsPage.PLAYER
            SettingsPage.LYRICS_VIEWER -> SettingsPage.APPEARANCE
            SettingsPage.LIBRARY, SettingsPage.SKINS, SettingsPage.APPEARANCE, SettingsPage.ABOUT, SettingsPage.PLAYER, SettingsPage.BACKUP -> SettingsPage.MENU
            SettingsPage.CHANGELOG -> SettingsPage.ABOUT
            SettingsPage.LYRICS_VIEWER -> SettingsPage.APPEARANCE
            else -> SettingsPage.NONE
        }
    }
    BackHandler(enabled = tagEditTrack != null) { tagEditTrack = null }
    BackHandler(enabled = lyricsEditTrack != null) { lyricsEditTrack = null }
    BackHandler(enabled = showHelp) { showHelp = false }
    BackHandler(enabled = showNowPlaying) { showNowPlaying = false }

    fun openAddToPlaylist(tracks: List<Track>) {
        addToPlaylistTracks = tracks
        showAddToPlaylist = true
    }

    CompositionLocalProvider(LocalUiFontFamily provides uiFontFamily) {
    val uiTypography = remember(uiFontFamily) {
        if (uiFontFamily != null) {
            val base = Typography()
            Typography(
                displayLarge = base.displayLarge.copy(fontFamily = uiFontFamily),
                displayMedium = base.displayMedium.copy(fontFamily = uiFontFamily),
                displaySmall = base.displaySmall.copy(fontFamily = uiFontFamily),
                headlineLarge = base.headlineLarge.copy(fontFamily = uiFontFamily),
                headlineMedium = base.headlineMedium.copy(fontFamily = uiFontFamily),
                headlineSmall = base.headlineSmall.copy(fontFamily = uiFontFamily),
                titleLarge = base.titleLarge.copy(fontFamily = uiFontFamily),
                titleMedium = base.titleMedium.copy(fontFamily = uiFontFamily),
                titleSmall = base.titleSmall.copy(fontFamily = uiFontFamily),
                bodyLarge = base.bodyLarge.copy(fontFamily = uiFontFamily),
                bodyMedium = base.bodyMedium.copy(fontFamily = uiFontFamily),
                bodySmall = base.bodySmall.copy(fontFamily = uiFontFamily),
                labelLarge = base.labelLarge.copy(fontFamily = uiFontFamily),
                labelMedium = base.labelMedium.copy(fontFamily = uiFontFamily),
                labelSmall = base.labelSmall.copy(fontFamily = uiFontFamily),
            )
        } else Typography()
    }
    MaterialTheme(
        colorScheme = if (isNyxT) lightColorScheme(primary = NyxTAccent) else lightColorScheme(),
        typography = uiTypography
    ) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent,
        contentColor = Color.White
    ) {
        Box(Modifier.fillMaxSize()) {

            // ---------- Conteúdo ----------
            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        backdrop.record { this@drawWithContent.drawContent() }
                        drawLayer(backdrop)
                    }
            ) {
                if (isNyxT) {
                    NyxTMain(
                        vm = vm,
                        selectedIds = selectedTrackIds,
                        selectionMode = selectionMode,
                        onToggleSelect = { track ->
                            selectedTrackIds = if (track.id in selectedTrackIds)
                                selectedTrackIds - track.id else selectedTrackIds + track.id
                        },
                        onLongSelect = { track -> selectedTrackIds = selectedTrackIds + track.id },
                        onOpenSettings = { settingsPage = SettingsPage.MENU },
                        onOpenNowPlaying = { showNowPlaying = true },
                        onPickFolder = onPickFolder,
                        onCreatePlaylist = {
                            createPlaylistInitialTrack = null
                            newPlaylistName = ""
                            showCreatePlaylist = true
                        },
                        onImportM3u = onImportM3u,
                        onExportM3u = { pl ->
                            val content = vm.exportPlaylistM3u(pl.id)
                            if (content != null) onExportM3u(pl.name, content)
                        }
                    )
                } else
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(rememberFloatingGradient(appColors))
                ) {
                    Column(Modifier.fillMaxSize().statusBarsPadding()) {
                        if (isSearching) {
                            SearchHeader(
                                query = searchQuery,
                                onQueryChange = vm::setSearchQuery,
                                onClose = vm::closeSearch
                            )
                        } else {
                            Header(
                                title = when {
                                    filter != null && tab == MusicViewModel.Tab.PLAYLISTS ->
                                        playlists.firstOrNull { it.id == filter!!.key }?.name ?: filter!!.key
                                    filter != null -> filter!!.key
                                    else -> tabTitle(tab)
                                },
                                showBack = filter != null,
                                onBack = vm::clearFilter,
                                onOpenSearch = vm::openSearch,
                                onOpenHelp = { showHelp = true },
                                onOpenSettings = { settingsPage = SettingsPage.MENU }
                            )
                        }

                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .pointerInput(tab, filter, isSearching, selectionMode) {
                                    if (selectionMode || isSearching || filter != null) return@pointerInput
                                    val tabs = listOf(
                                        MusicViewModel.Tab.ALL,
                                        MusicViewModel.Tab.ARTISTS,
                                        MusicViewModel.Tab.ALBUMS,
                                        MusicViewModel.Tab.GENRES,
                                        MusicViewModel.Tab.PLAYLISTS
                                    )
                                    var totalDrag = 0f
                                    detectHorizontalDragGestures(
                                        onDragEnd = {
                                            val threshold = 80f
                                            val idx = tabs.indexOf(tab)
                                            if (totalDrag < -threshold && idx < tabs.lastIndex) {
                                                vm.setTab(tabs[idx + 1])
                                            } else if (totalDrag > threshold && idx > 0) {
                                                vm.setTab(tabs[idx - 1])
                                            }
                                            totalDrag = 0f
                                        },
                                        onHorizontalDrag = { _, dragAmount ->
                                            totalDrag += dragAmount
                                        }
                                    )
                                }
                        ) {
                            val contentKey = when {
                                isSearching -> "search"
                                isScanning && tracks.isEmpty() -> "scanning"
                                tracks.isEmpty() -> "empty"
                                filter != null -> "filter:${filter!!.tab}:${filter!!.key}"
                                else -> "tab:${tab.name}"
                            }
                            val tabOrder = listOf(
                                MusicViewModel.Tab.ALL,
                                MusicViewModel.Tab.ARTISTS,
                                MusicViewModel.Tab.ALBUMS,
                                MusicViewModel.Tab.GENRES,
                                MusicViewModel.Tab.PLAYLISTS
                            )
                            AnimatedContent(
                                targetState = contentKey,
                                transitionSpec = {
                                    val initialTab = initialState.removePrefix("tab:").let { name ->
                                        tabOrder.firstOrNull { it.name == name }
                                    }
                                    val targetTab = targetState.removePrefix("tab:").let { name ->
                                        tabOrder.firstOrNull { it.name == name }
                                    }
                                    val bothTabs = initialTab != null && targetTab != null
                                    val forward = when {
                                        bothTabs -> tabOrder.indexOf(targetTab) >= tabOrder.indexOf(initialTab)
                                        targetState.startsWith("filter:") && !initialState.startsWith("filter:") -> true
                                        initialState.startsWith("filter:") && !targetState.startsWith("filter:") -> false
                                        targetState == "search" -> true
                                        initialState == "search" -> false
                                        else -> true
                                    }
                                    if (bothTabs || targetState.startsWith("filter:") || initialState.startsWith("filter:")) {
                                        if (forward) {
                                            (slideInHorizontally(tween(280)) { it / 4 } + fadeIn(tween(220))) togetherWith
                                                (slideOutHorizontally(tween(240)) { -it / 4 } + fadeOut(tween(180)))
                                        } else {
                                            (slideInHorizontally(tween(280)) { -it / 4 } + fadeIn(tween(220))) togetherWith
                                                (slideOutHorizontally(tween(240)) { it / 4 } + fadeOut(tween(180)))
                                        }
                                    } else {
                                        (fadeIn(tween(250)) + slideInVertically(tween(280)) { it / 8 }) togetherWith
                                            (fadeOut(tween(180)) + slideOutVertically(tween(200)) { -it / 8 })
                                    }.using(SizeTransform(clip = false))
                                },
                                label = "mainContent"
                            ) { key ->
                            when {
                                key == "search" || isSearching -> SearchResultsPanel(
                                    tracks = searchResults,
                                    artists = searchArtists,
                                    albums = searchAlbums,
                                    genres = searchGenres,
                                    nowPlayingId = nowPlaying?.id,
                                    bottomPadding = bottomPadding,
                                    selectedIds = selectedTrackIds,
                                    selectionMode = selectionMode,
                                    onTrackClick = { index, track ->
                                        if (selectionMode) {
                                            selectedTrackIds = if (track.id in selectedTrackIds)
                                                selectedTrackIds - track.id else selectedTrackIds + track.id
                                        } else {
                                            vm.playTracks(searchResults, index)
                                        }
                                    },
                                    onTrackLongClick = { track ->
                                        selectedTrackIds = selectedTrackIds + track.id
                                    },
                                    onOpenArtist = { name ->
                                        vm.openArtist(name)
                                    },
                                    onOpenAlbum = { name ->
                                        vm.openAlbum(name)
                                    },
                                    onOpenGenre = { name ->
                                        vm.setTab(MusicViewModel.Tab.GENRES)
                                        vm.openGroup(name)
                                    }
                                )

                                isScanning && tracks.isEmpty() -> Column(
                                    Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator()
                                    Text("Lendo sua biblioteca… $scanCount", modifier = Modifier.padding(top = 16.dp))
                                }

                                tracks.isEmpty() -> Column(
                                    Modifier.fillMaxSize().padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text("Nenhuma música ainda", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (allFilesMode) "Nenhuma faixa encontrada no armazenamento."
                                        else "Ative o acesso a todos os arquivos para escanear suas músicas.",
                                        modifier = Modifier.padding(vertical = 12.dp),
                                        color = Color.White.copy(alpha = 0.7f),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    if (!allFilesMode) {
                                        Button(onClick = onRequestAllFilesPermission) {
                                            Text("Permitir acesso total")
                                        }
                                    } else {
                                        Button(onClick = { vm.refreshLibrary() }) {
                                            Text("Escanear novamente")
                                        }
                                    }
                                }

                                filter != null && filter!!.tab == MusicViewModel.Tab.ARTISTS -> {
                                    val artistName = filter!!.key
                                    val artistTracks = visible
                                    val albumMap = artistTracks
                                        .groupBy { it.album.ifBlank { "Álbum desconhecido" } }
                                        .map { (name, ts) ->
                                            val art = ts.firstOrNull { !it.artPath.isNullOrBlank() }?.artPath
                                            val total = ts.sumOf { it.duration }
                                            Triple(name, ts.size, art to total)
                                        }
                                        .sortedBy { it.first.lowercase() }
                                    ArtistDetailScreen(
                                        artistName = artistName,
                                        trackCount = artistTracks.size,
                                        totalDurationMs = artistTracks.sumOf { it.duration },
                                        albums = albumMap.map { (n, c, at) -> ArtistAlbumItem(n, c, at.first, at.second) },
                                        bottomPadding = bottomPadding,
                                        onOpenAlbum = { vm.openAlbum(it) }
                                    )
                                }

                                filter != null && filter!!.tab == MusicViewModel.Tab.ALBUMS -> {
                                    val albumName = filter!!.key
                                    val albumTracks = visible
                                    val art = albumTracks.firstOrNull { !it.artPath.isNullOrBlank() }?.artPath
                                    val artists = albumTracks
                                        .flatMap { vm.artistNamesOf(it) }
                                        .distinct()
                                    AlbumDetailScreen(
                                        albumName = albumName,
                                        artPath = art,
                                        artists = artists,
                                        tracks = albumTracks,
                                        totalDurationMs = albumTracks.sumOf { it.duration },
                                        nowPlayingId = nowPlaying?.id,
                                        bottomPadding = bottomPadding,
                                        accent = appColors.accent,
                                        selectedIds = selectedTrackIds,
                                        selectionMode = selectionMode,
                                        onPlayAll = { vm.playTracks(albumTracks, 0) },
                                        onShuffle = {
                                            vm.playTracks(albumTracks.shuffled(), 0)
                                        },
                                        onOpenArtist = { name -> vm.openArtist(name) },
                                        onTrackClick = { index, track ->
                                            if (selectionMode) {
                                                selectedTrackIds = if (track.id in selectedTrackIds)
                                                    selectedTrackIds - track.id else selectedTrackIds + track.id
                                            } else {
                                                vm.playTracks(albumTracks, index)
                                            }
                                        },
                                        onTrackLongClick = { track ->
                                            selectedTrackIds = selectedTrackIds + track.id
                                        }
                                    )
                                }

                                filter != null || tab == MusicViewModel.Tab.ALL -> {
                                    val listTracks = if (filter == null && tab == MusicViewModel.Tab.ALL) {
                                        vm.sortedTracks(visible)
                                    } else visible
                                    Column(Modifier.fillMaxSize()) {
                                        if (filter == null && tab == MusicViewModel.Tab.ALL) {
                                            SortBar(
                                                isGroup = false,
                                                groupSort = groupSort,
                                                groupOrder = groupOrder,
                                                trackSort = trackSort,
                                                trackOrder = trackOrder,
                                                onGroupSort = { s, o -> vm.setGroupSort(s, o) },
                                                onTrackSort = { s, o -> vm.setTrackSort(s, o) }
                                            )
                                        }
                                        TrackList(
                                            tracks = listTracks,
                                            nowPlayingId = nowPlaying?.id,
                                            bottomPadding = bottomPadding,
                                            showTrackNumbers = filter != null && tab == MusicViewModel.Tab.ALBUMS,
                                            selectedIds = selectedTrackIds,
                                            selectionMode = selectionMode,
                                            onClick = { index, track ->
                                                if (selectionMode) {
                                                    selectedTrackIds = if (track.id in selectedTrackIds)
                                                        selectedTrackIds - track.id else selectedTrackIds + track.id
                                                } else {
                                                    vm.playTracks(listTracks, index)
                                                }
                                            },
                                            onLongClick = { track ->
                                                selectedTrackIds = selectedTrackIds + track.id
                                            }
                                        )
                                    }
                                }

                                tab == MusicViewModel.Tab.PLAYLISTS -> {
                                    PlaylistList(
                                        playlists = playlists,
                                        trackCount = { id ->
                                            val pl = playlists.firstOrNull { it.id == id }
                                            pl?.trackIds?.size ?: 0
                                        },
                                        bottomPadding = bottomPadding,
                                        onOpen = { vm.openGroup(it) },
                                        onCreate = {
                                            createPlaylistInitialTrack = null
                                            newPlaylistName = ""
                                            showCreatePlaylist = true
                                        },
                                        onImport = onImportM3u,
                                        onExport = { pl ->
                                            val content = vm.exportPlaylistM3u(pl.id)
                                            if (content != null) onExportM3u(pl.name, content)
                                        }
                                    )
                                }

                                else -> {
                                    val groups = when (tab) {
                                        MusicViewModel.Tab.ARTISTS -> vm.sortedArtists(artists).map {
                                            GroupItem(it.name, faixas(it.trackCount), it.name, it.artPath, round = true)
                                        }
                                        MusicViewModel.Tab.ALBUMS -> vm.sortedAlbums(albums).map {
                                            GroupItem(it.name, "${it.artist} · ${faixas(it.trackCount)}", it.name, it.artPath)
                                        }
                                        MusicViewModel.Tab.GENRES -> vm.sortedGenres(genres).map {
                                            GroupItem(it.name, faixas(it.trackCount), it.name, it.artPath)
                                        }
                                        else -> emptyList()
                                    }
                                    Column(Modifier.fillMaxSize()) {
                                        SortBar(
                                            isGroup = true,
                                            groupSort = groupSort,
                                            groupOrder = groupOrder,
                                            trackSort = trackSort,
                                            trackOrder = trackOrder,
                                            onGroupSort = { s, o -> vm.setGroupSort(s, o) },
                                            onTrackSort = { s, o -> vm.setTrackSort(s, o) }
                                        )
                                        GroupList(groups, bottomPadding, onClick = vm::openGroup)
                                    }
                                }
                            }
                            } // AnimatedContent
                            if (isScanning && tracks.isNotEmpty()) {
                                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
                            }
                        }
                    }
                }
            }

            // ---------- Barras de vidro flutuantes (só na skin Ly's Place) ----------
            if (!isNyxT) Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                nowPlaying?.let {
                    MiniPlayer(
                        track = it,
                        isPlaying = isPlaying,
                        backdrop = backdrop,
                        onClick = { showNowPlaying = true },
                        onPrevious = vm::previous,
                        onToggle = vm::togglePlayPause,
                        onNext = vm::next
                    )
                }

                LiquidGlassNavigation(
                    currentTab = tab,
                    onTabSelected = vm::setTab,
                    backdrop = backdrop
                )
            }

            // ---------- Configurações ----------
            AnimatedVisibility(
                visible = settingsPage != SettingsPage.NONE,
                enter = slideInHorizontally(tween(300)) { it } + fadeIn(tween(220)),
                exit = slideOutHorizontally(tween(260)) { it } + fadeOut(tween(180))
            ) {
                SettingsScreen(
                    page = settingsPage,
                    folders = sourceFolders,
                    totalTracks = tracks.size,
                    isScanning = isScanning,
                    scanCount = scanCount,
                    themeId = themeId,
                    skinId = skinId,
                    onSkinSelected = vm::setSkinId,
                    lyricsFontPath = lyricsFontPath,
                    uiFontPath = uiFontPath,
                    librarySettings = librarySettings,
                    appColors = appColors,
                    onNavigate = { settingsPage = it },
                    onBack = {
                        settingsPage = when (settingsPage) {
                            SettingsPage.FOLDERS -> SettingsPage.LIBRARY
                            SettingsPage.AUDIO_OUTPUT -> SettingsPage.PLAYER
                            SettingsPage.LYRICS_VIEWER -> SettingsPage.APPEARANCE
                            SettingsPage.CHANGELOG, SettingsPage.LICENSES -> SettingsPage.ABOUT
                            SettingsPage.LIBRARY, SettingsPage.SKINS, SettingsPage.APPEARANCE,
                            SettingsPage.ABOUT, SettingsPage.PLAYER, SettingsPage.BACKUP -> SettingsPage.MENU
                            else -> SettingsPage.NONE
                        }
                    },
                    onAddFolder = onPickFolder,
                    onRemoveFolder = vm::removeFolder,
                    onRefresh = vm::refreshLibrary,
                    onThemeSelected = vm::setThemeId,
                    onPickFont = onPickFont,
                    onClearFont = { vm.setLyricsFontPath(null) },
                    onPickUiFont = onPickUiFont,
                    onClearUiFont = { vm.setUiFontPath(null) },
                    onSelectUiFont = { key -> vm.setUiFontPath(key) },
                    onSelectLyricsFont = { key -> vm.setLyricsFontPath(key) },
                    onSplitArtistsChange = vm::setSplitArtists,
                    onArtistSeparatorsChange = vm::setArtistSeparators,
                    onArtistExceptionsChange = vm::setArtistExceptions,
                    onSplitGenresChange = vm::setSplitGenres,
                    onGenreSeparatorsChange = vm::setGenreSeparators,
                    allFilesMode = allFilesMode,
                    onAllFilesModeChange = vm::setAllFilesMode,
                    onRequestAllFilesPermission = onRequestAllFilesPermission,
                    autoFetchLyrics = autoFetchLyrics,
                    onAutoFetchLyricsChange = vm::setAutoFetchLyrics,
                    shuffleType = shuffleType,
                    onShuffleTypeChange = vm::setShuffleType,
                    bitPerfect = bitPerfect,
                    onBitPerfectChange = vm::setBitPerfect,
                    fadeMs = fadeMs,
                    onFadeMsChange = vm::setFadeMs,
                    milkPresetCount = milkPresets.size,
                    milkIntervalSec = milkIntervalSec,
                    onPickMilkFiles = onPickMilkFiles,
                    onClearMilkPresets = vm::clearMilkPresets,
                    onMilkIntervalChange = vm::setMilkIntervalSec,
                    audioOutput = audioOutput,
                    onAudioOutputChange = vm::setAudioOutput,
                    forceMaxSampleRate = forceMaxSampleRate,
                    onForceMaxSampleRateChange = vm::setForceMaxSampleRate,
                    lyricsFontSize = lyricsFontSize,
                    onLyricsFontSizeChange = vm::setLyricsFontSize,
                    lyricsPrimaryColor = lyricsPrimaryColor,
                    onLyricsPrimaryColorChange = vm::setLyricsPrimaryColor,
                    lyricsSecondaryColor = lyricsSecondaryColor,
                    onLyricsSecondaryColorChange = vm::setLyricsSecondaryColor,
                    lyricsBorderColor = lyricsBorderColor,
                    onLyricsBorderColorChange = vm::setLyricsBorderColor,
                    lyricsBorderWidth = lyricsBorderWidth,
                    onLyricsBorderWidthChange = vm::setLyricsBorderWidth,
                    onExportBackup = onExportBackup,
                    onImportBackup = onImportBackup,
                    splashEnabled = splashEnabled,
                    onSplashEnabledChange = vm::setSplashEnabled,
                    splashSoundEnabled = splashSoundEnabled,
                    onSplashSoundChange = vm::setSplashSoundEnabled
                )
            }

            AnimatedVisibility(
                visible = tagEditTrack != null,
                enter = slideInVertically(tween(300)) { it } + fadeIn(tween(220)),
                exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(180))
            ) {
                tagEditTrack?.let { track ->
                    val editCtx = LocalContext.current
                    TagEditorScreen(
                        track = track,
                        appColors = appColors,
                        onClose = { tagEditTrack = null },
                        onSave = { edits ->
                            vm.updateTrackTags(
                                trackId = track.id,
                                fields = edits.toWriterFields()
                            ) { ok, msg ->
                                android.widget.Toast.makeText(editCtx, msg, android.widget.Toast.LENGTH_SHORT).show()
                                if (ok) tagEditTrack = null
                            }
                        },
                        isPlaying = isPlaying,
                        getPosition = vm::currentPosition,
                        onSeek = vm::seekTo,
                        onToggle = vm::togglePlayPause
                    )
                }
            }

            if (showHelp) {
                HelpDialog(onDismiss = { showHelp = false })
            }

            // ---------- Tocando agora ----------
            AnimatedVisibility(
                visible = showNowPlaying && nowPlaying != null,
                enter = slideInVertically(tween(320)) { it } + fadeIn(tween(240)),
                exit = slideOutVertically(tween(280)) { it } + fadeOut(tween(200))
            ) {
                nowPlaying?.let { current ->
                    // Deriva de playlists (StateFlow) para recompor imediatamente ao favoritar
                    val isFavorite = playlists
                        .firstOrNull { it.isFavorites }
                        ?.trackIds
                        ?.contains(current.id) == true
                    if (isNyxT) {
                        NyxTNowPlayingScreen(
                            track = current,
                            isPlaying = isPlaying,
                            shuffle = shuffle,
                            repeatMode = repeatMode,
                            queue = queue,
                            isFavorite = isFavorite,
                            lyricsFontPath = lyricsFontPath,
                            getPosition = vm::currentPosition,
                            onClose = { showNowPlaying = false },
                            onPrevious = vm::previous,
                            onToggle = vm::togglePlayPause,
                            onNext = vm::next,
                            onSeek = vm::seekTo,
                            onToggleShuffle = vm::toggleShuffle,
                            onCycleRepeat = vm::cycleRepeatMode,
                            onSkipToQueueItem = vm::skipToQueueItem,
                            onMoveQueueItem = vm::moveQueueItem,
                            onToggleFavorite = { vm.toggleFavorite(current.id) },
                            onAddToPlaylist = { openAddToPlaylist(listOf(current)) },
                            onEditTags = {
                                tagEditTrack = current
                                showNowPlaying = false
                            },
                            artistNames = remember(current.artist, librarySettings) { vm.artistNamesOf(current) },
                            onOpenArtist = { name ->
                                showNowPlaying = false
                                vm.openArtist(name)
                            },
                            onOpenAlbum = {
                                showNowPlaying = false
                                vm.openAlbum(current.album)
                            },
                            loadLyrics = { vm.loadLyrics(current) },
                            searchLyrics = { t, a -> vm.searchLyrics(t, a) },
                            embedLyricsResult = { r -> vm.embedLyricsResult(current, r) },
                            lyricsFontSize = lyricsFontSize,
                            lyricsPrimaryColor = lyricsPrimaryColor,
                            lyricsSecondaryColor = lyricsSecondaryColor,
                            lyricsBorderColor = lyricsBorderColor,
                            lyricsBorderWidth = lyricsBorderWidth
                        )
                    } else
                    NowPlayingScreen(
                        track = current,
                        isPlaying = isPlaying,
                        shuffle = shuffle,
                        repeatMode = repeatMode,
                        queue = queue,
                        isFavorite = isFavorite,
                        lyricsFontPath = lyricsFontPath,
                        getPosition = vm::currentPosition,
                        onClose = { showNowPlaying = false },
                        onPrevious = vm::previous,
                        onToggle = vm::togglePlayPause,
                        onNext = vm::next,
                        onSeek = vm::seekTo,
                        onToggleShuffle = vm::toggleShuffle,
                        onCycleRepeat = vm::cycleRepeatMode,
                        onSkipToQueueItem = vm::skipToQueueItem,
                        onMoveQueueItem = vm::moveQueueItem,
                        onRemoveFromQueue = vm::removeFromQueue,
                        onToggleFavoriteTrack = { t -> vm.toggleFavorite(t.id) },
                        onDeleteTrack = { t -> onDeleteTracks(listOf(t)) },
                        onToggleFavorite = { vm.toggleFavorite(current.id) },
                        onAddToPlaylist = { openAddToPlaylist(listOf(current)) },
                        onEditTags = {
                            tagEditTrack = current
                            showNowPlaying = false
                        },
                        artistNames = remember(current.artist, librarySettings) { vm.artistNamesOf(current) },
                        onOpenArtist = { name ->
                            showNowPlaying = false
                            vm.openArtist(name)
                        },
                        onOpenAlbum = {
                            showNowPlaying = false
                            vm.openAlbum(current.album)
                        },
                        loadLyrics = { vm.loadLyrics(current) },
                        searchLyrics = { t, a -> vm.searchLyrics(t, a) },
                        embedLyricsResult = { r -> vm.embedLyricsResult(current, r) },
                        appColors = appColors,
                        milkPresets = milkPresets,
                        milkIntervalSec = milkIntervalSec,
                        lyricsFontSize = lyricsFontSize,
                        lyricsPrimaryColor = lyricsPrimaryColor,
                        lyricsSecondaryColor = lyricsSecondaryColor,
                        lyricsBorderColor = lyricsBorderColor,
                        lyricsBorderWidth = lyricsBorderWidth
                    )
                }
            }

            // ---------- FAB de multi-seleção ----------
            if (selectionMode) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = (if (isNyxT) 100.dp else bottomPadding) + 16.dp, end = 20.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    GlassSurface(
                        backdrop = backdrop,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(56.dp)
                            .clickable { showSelectionActions = true }
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "${selectedTrackIds.size}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            if (showSelectionActions && selectionMode) {
                val pool = if (isSearching) searchResults else visible
                val selectedTracks = remember(selectedTrackIds, pool) {
                    pool.filter { it.id in selectedTrackIds }
                }
                val shareCtx = LocalContext.current
                SelectionActionsMenu(
                    count = selectedTracks.size,
                    backdrop = backdrop,
                    onDismiss = { showSelectionActions = false },
                    onPlayNext = {
                        selectedTracks.forEach { vm.playNext(it) }
                        showSelectionActions = false
                        selectedTrackIds = emptySet()
                    },
                    onPlayLast = {
                        selectedTracks.forEach { vm.playLast(it) }
                        showSelectionActions = false
                        selectedTrackIds = emptySet()
                    },
                    onAddToPlaylist = {
                        showSelectionActions = false
                        openAddToPlaylist(selectedTracks)
                    },
                    onFavorite = {
                        selectedTracks.forEach { vm.toggleFavorite(it.id) }
                        showSelectionActions = false
                        selectedTrackIds = emptySet()
                    },
                    onShare = {
                        try {
                            val uris = ArrayList<android.net.Uri>()
                            selectedTracks.forEach { uris.add(it.uri) }
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
                                type = "audio/*"
                                putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, uris)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            shareCtx.startActivity(android.content.Intent.createChooser(intent, "Compartilhar"))
                        } catch (_: Exception) {}
                        showSelectionActions = false
                    },
                    onEditTags = {
                        showSelectionActions = false
                        if (selectedTracks.size == 1) {
                            tagEditTrack = selectedTracks.first()
                            selectedTrackIds = emptySet()
                        } else {
                            batchTagTracks = selectedTracks
                            showBatchTagEdit = true
                        }
                    },
                    onRenameBatch = {
                        showSelectionActions = false
                        batchRenameTracks = selectedTracks
                        showBatchRename = true
                    },
                    onDelete = {
                        showSelectionActions = false
                        showDeleteConfirm = true
                    }
                )
            }

            if (showBatchTagEdit && batchTagTracks.isNotEmpty()) {
                val batchCtx = LocalContext.current
                BatchTagEditDialog(
                    count = batchTagTracks.size,
                    onDismiss = {
                        showBatchTagEdit = false
                        batchTagTracks = emptyList()
                    },
                    onConfirm = { artist, album, albumArtist ->
                        val ids = batchTagTracks.map { it.id }
                        vm.batchUpdateTags(ids, artist, album, albumArtist) { ok, fail ->
                            android.widget.Toast.makeText(
                                batchCtx,
                                if (fail == 0) "Tags atualizadas em $ok faixas"
                                else "Atualizadas: $ok · falhas: $fail",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                        showBatchTagEdit = false
                        batchTagTracks = emptyList()
                        selectedTrackIds = emptySet()
                    }
                )
            }

            if (showBatchRename && batchRenameTracks.isNotEmpty()) {
                BatchRenameDialog(
                    tracks = batchRenameTracks,
                    onDismiss = {
                        showBatchRename = false
                        batchRenameTracks = emptyList()
                    },
                    onConfirm = { pattern ->
                        // Renomeia arquivos com base no padrão de tags
                        batchRenameTracks.forEach { t ->
                            val newName = applyRenamePattern(pattern, t)
                            // Tenta renomear via ViewModel se disponível
                            try {
                                vm.renameTrackFile(t, newName)
                            } catch (_: Exception) {}
                        }
                        showBatchRename = false
                        batchRenameTracks = emptyList()
                        selectedTrackIds = emptySet()
                    }
                )
            }


            if (showDeleteConfirm && selectedTrackIds.isNotEmpty()) {
                val pool = if (isSearching) searchResults else visible
                val tracksToDelete = remember(selectedTrackIds, pool) {
                    pool.filter { it.id in selectedTrackIds }
                }
                val count = selectedTrackIds.size
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = { Text(if (count == 1) "Excluir permanentemente?" else "Excluir $count faixas?") },
                    text = {
                        Text(
                            if (count == 1) "O arquivo será apagado do armazenamento. Esta ação não pode ser desfeita."
                            else "Os arquivos serão apagados do armazenamento. Esta ação não pode ser desfeita."
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                onDeleteTracks(tracksToDelete)
                                showDeleteConfirm = false
                                selectedTrackIds = emptySet()
                            }
                        ) { Text("Excluir", color = Color(0xFFE53935)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirm = false }) {
                            Text("Cancelar")
                        }
                    }
                )
            }

            // ---------- Adicionar a playlist ----------
            if (showAddToPlaylist && addToPlaylistTracks.isNotEmpty()) {
                AddToPlaylistDialog(
                    playlists = playlists,
                    onDismiss = {
                        showAddToPlaylist = false
                        addToPlaylistTracks = emptyList()
                    },
                    onSelect = { plId ->
                        addToPlaylistTracks.forEach { vm.addToPlaylist(plId, it.id) }
                        showAddToPlaylist = false
                        addToPlaylistTracks = emptyList()
                        selectedTrackIds = emptySet()
                    },
                    onCreateNew = {
                        createPlaylistInitialTrack = addToPlaylistTracks.firstOrNull()
                        newPlaylistName = ""
                        showAddToPlaylist = false
                        showCreatePlaylist = true
                    }
                )
            }

            // ---------- Criar playlist ----------
            if (showCreatePlaylist) {
                AlertDialog(
                    onDismissRequest = { showCreatePlaylist = false },
                    title = { Text("Nova playlist") },
                    text = {
                        OutlinedTextField(
                            value = newPlaylistName,
                            onValueChange = { newPlaylistName = it },
                            singleLine = true,
                            label = { Text("Nome") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val id = vm.createPlaylist(
                                newPlaylistName,
                                createPlaylistInitialTrack?.let { listOf(it.id) } ?: emptyList()
                            )
                            showCreatePlaylist = false
                            createPlaylistInitialTrack = null
                        }) { Text("Criar") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCreatePlaylist = false }) { Text("Cancelar") }
                    }
                )
            }
        }
    }
    } // MaterialTheme
    } // CompositionLocalProvider
}

@Composable
internal fun ArtImage(
    path: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp)
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center
    ) {
        if (path != null) {
            AsyncImage(
                model = File(path),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(size * 0.45f)
            )
        }
    }
}

@Composable
private fun Header(
    title: String,
    showBack: Boolean,
    onBack: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showBack) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Voltar")
            }
        }
        Text(
            text = title,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = if (showBack) 0.dp else 12.dp)
        )
        // Caixa de pesquisa pequena e opaca ao lado do botão de ajuda
        Box(
            Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.12f))
                .clickable(onClick = onOpenSearch)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = "Buscar",
                    modifier = Modifier.size(18.dp),
                    tint = Color.White.copy(0.85f)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Buscar",
                    fontSize = 13.sp,
                    color = Color.White.copy(0.7f)
                )
            }
        }
        IconButton(onClick = onOpenHelp) {
            Icon(Icons.Rounded.HelpOutline, contentDescription = "Ajuda")
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Rounded.Settings, contentDescription = "Configurações")
        }
    }
}

@Composable
private fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajuda — Lyron Music Player") },
        text = {
            Column {
                Text(
                    "• Toque em uma música para reproduzir.\n" +
                        "• Pressione e segure uma faixa para opções (tocar a seguir, adicionar à playlist…).\n" +
                        "• Use as abas para navegar por artistas, álbuns, gêneros e playlists.\n" +
                        "• Em Configurações → Biblioteca você pode adicionar pastas e controlar a separação de artistas/gêneros.\n" +
                        "• Em Aparência escolha temas e fontes do player e das letras.\n" +
                        "• A busca está na caixa ao lado do botão de ajuda.",
                    fontSize = 14.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Entendi") }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchResultsPanel(
    tracks: List<Track>,
    artists: List<Artist>,
    albums: List<Album>,
    genres: List<Genre>,
    nowPlayingId: String?,
    bottomPadding: Dp,
    selectedIds: Set<String>,
    selectionMode: Boolean,
    onTrackClick: (Int, Track) -> Unit,
    onTrackLongClick: (Track) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenGenre: (String) -> Unit
) {
    val hasGroups = artists.isNotEmpty() || albums.isNotEmpty() || genres.isNotEmpty()
    if (!hasGroups) {
        TrackList(
            tracks = tracks,
            nowPlayingId = nowPlayingId,
            bottomPadding = bottomPadding,
            showTrackNumbers = false,
            selectedIds = selectedIds,
            selectionMode = selectionMode,
            onClick = onTrackClick,
            onLongClick = onTrackLongClick,
            showAlphabetScroller = false
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding)) {
        if (artists.isNotEmpty()) {
            item {
                Text(
                    "Artistas",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(0.55f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
            items(artists.take(20)) { a ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenArtist(a.name) }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ArtImage(path = a.artPath, size = 48.dp, shape = CircleShape)
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(a.name, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            faixas(a.trackCount),
                            fontSize = 13.sp,
                            color = Color.White.copy(0.6f)
                        )
                    }
                }
            }
        }
        if (albums.isNotEmpty()) {
            item {
                Text(
                    "Álbuns",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(0.55f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
            items(albums.take(20)) { al ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenAlbum(al.name) }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ArtImage(path = al.artPath, size = 48.dp, shape = RoundedCornerShape(10.dp))
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(al.name, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${al.artist} · ${faixas(al.trackCount)}",
                            fontSize = 13.sp,
                            color = Color.White.copy(0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        if (genres.isNotEmpty()) {
            item {
                Text(
                    "Gêneros",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(0.55f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
            items(genres.take(20)) { g ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenGenre(g.name) }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ArtImage(path = g.artPath, size = 48.dp, shape = RoundedCornerShape(10.dp))
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(g.name, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            faixas(g.trackCount),
                            fontSize = 13.sp,
                            color = Color.White.copy(0.6f)
                        )
                    }
                }
            }
        }
        if (tracks.isNotEmpty()) {
            item {
                Text(
                    "Faixas",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(0.55f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
            itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
                val active = track.id == nowPlayingId
                val selected = track.id in selectedIds
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (selected) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                        .combinedClickable(
                            onClick = { onTrackClick(index, track) },
                            onLongClick = { onTrackLongClick(track) }
                        )
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectionMode) {
                        Box(
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Text("✓", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                    }
                    ArtImage(path = track.artPath, size = 48.dp)
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(
                            track.title,
                            fontSize = 16.sp,
                            color = if (active) MaterialTheme.colorScheme.primary else Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${track.artist} · ${track.album}",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        formatDuration(track.duration),
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun SearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Rounded.ArrowBack, contentDescription = "Fechar busca")
        }
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
            cursorBrush = SolidColor(Color.White),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) {
                        Text("Buscar músicas…", color = Color.White.copy(0.45f), fontSize = 18.sp)
                    }
                    inner()
                }
            }
        )
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }) {
                Icon(Icons.Rounded.Close, contentDescription = "Limpar")
            }
        }
    }
}

@Composable
private fun GroupList(items: List<GroupItem>, bottomPadding: Dp, onClick: (String) -> Unit) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val letterIndex = remember(items) {
        buildLetterIndex(items) { it.title }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding, end = 20.dp)
        ) {
            items(items) { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onClick(item.key) }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ArtImage(
                        path = item.artPath,
                        size = 56.dp,
                        shape = if (item.round) CircleShape else RoundedCornerShape(10.dp)
                    )
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(item.title, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            item.subtitle,
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        AlphabetFastScroller(
            letterIndex = letterIndex,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(vertical = 12.dp, horizontal = 2.dp),
            onLetterSelected = { letter ->
                letterIndex[letter]?.let { idx ->
                    scope.launch { listState.scrollToItem(idx.coerceIn(0, items.lastIndex.coerceAtLeast(0))) }
                }
            }
        )
    }
}

/** Índice da primeira ocorrência de cada letra (A–Z / #). */
private fun <T> buildLetterIndex(items: List<T>, label: (T) -> String): Map<Char, Int> {
    val map = linkedMapOf<Char, Int>()
    items.forEachIndexed { index, item ->
        val c = label(item).firstOrNull()?.uppercaseChar()
        val key = if (c != null && c in 'A'..'Z') c else '#'
        if (key !in map) map[key] = index
    }
    return map
}

private val AlphabetLetters = listOf('#') + ('A'..'Z').toList()

@Composable
private fun AlphabetFastScroller(
    letterIndex: Map<Char, Int>,
    modifier: Modifier = Modifier,
    onLetterSelected: (Char) -> Unit
) {
    if (letterIndex.isEmpty()) return
    var activeLetter by remember { mutableStateOf<Char?>(null) }
    Column(
        modifier = modifier
            .width(22.dp)
            .pointerInput(letterIndex) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        val letter = letterAtY(offset.y, size.height.toFloat())
                        activeLetter = letter
                        onLetterSelected(letter)
                    },
                    onDragEnd = { activeLetter = null },
                    onDragCancel = { activeLetter = null },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        val letter = letterAtY(change.position.y, size.height.toFloat())
                        if (letter != activeLetter) {
                            activeLetter = letter
                            onLetterSelected(letter)
                        }
                    }
                )
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AlphabetLetters.forEach { letter ->
            val present = letter in letterIndex
            Text(
                text = letter.toString(),
                fontSize = 10.sp,
                fontWeight = if (activeLetter == letter) FontWeight.Bold else FontWeight.Medium,
                color = when {
                    activeLetter == letter -> Color.White
                    present -> Color.White.copy(0.75f)
                    else -> Color.White.copy(0.28f)
                },
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = present) {
                        activeLetter = letter
                        onLetterSelected(letter)
                    }
            )
        }
    }
}

private fun letterAtY(y: Float, height: Float): Char {
    if (height <= 0f) return '#'
    val fraction = (y / height).coerceIn(0f, 0.999f)
    val idx = (fraction * AlphabetLetters.size).toInt().coerceIn(0, AlphabetLetters.lastIndex)
    return AlphabetLetters[idx]
}

@Composable
internal fun PlaylistList(
    playlists: List<Playlist>,
    trackCount: (String) -> Int,
    bottomPadding: Dp,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
    onImport: () -> Unit,
    onExport: (Playlist) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding)) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(onClick = onCreate) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Nova")
                }
                TextButton(onClick = onImport) {
                    Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Importar .m3u")
                }
            }
        }
        items(playlists) { pl ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(pl.id) }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Faixa vermelha para Favoritos
                Box(
                    Modifier
                        .width(4.dp)
                        .height(48.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (pl.isFavorites) Color(0xFFE53935) else Color.Transparent)
                )
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (pl.isFavorites) Color(0xFFE53935).copy(alpha = 0.25f)
                            else Color.White.copy(alpha = 0.08f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (pl.isFavorites) "♥" else "♪",
                        fontSize = 22.sp,
                        color = if (pl.isFavorites) Color(0xFFE53935) else Color.White.copy(0.7f)
                    )
                }
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(pl.name, fontSize = 16.sp, fontWeight = if (pl.isFavorites) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(faixas(trackCount(pl.id)), fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
                }
                if (!pl.isFavorites) {
                    IconButton(onClick = { onExport(pl) }) {
                        Icon(Icons.Rounded.FileUpload, contentDescription = "Exportar .m3u", tint = Color.White.copy(0.7f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackList(
    tracks: List<Track>,
    nowPlayingId: String?,
    bottomPadding: Dp,
    showTrackNumbers: Boolean = false,
    selectedIds: Set<String> = emptySet(),
    selectionMode: Boolean = false,
    onClick: (Int, Track) -> Unit,
    onLongClick: (Track) -> Unit,
    showAlphabetScroller: Boolean = true
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val letterIndex = remember(tracks) {
        if (showAlphabetScroller) buildLetterIndex(tracks) { it.title } else emptyMap()
    }
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding, end = if (showAlphabetScroller) 20.dp else 0.dp)
    ) {
        itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
            val active = track.id == nowPlayingId
            val selected = track.id in selectedIds
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (selected) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                    .combinedClickable(
                        onClick = { onClick(index, track) },
                        onLongClick = { onLongClick(track) }
                    )
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectionMode) {
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Text("✓", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                } else if (showTrackNumbers) {
                    Text(
                        track.trackNumber?.toString() ?: "–",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(0.7f),
                        modifier = Modifier.width(28.dp)
                    )
                }
                ArtImage(path = track.artPath, size = 48.dp)
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(
                        track.title,
                        fontSize = 16.sp,
                        color = if (active) MaterialTheme.colorScheme.primary else Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${track.artist} · ${track.album}",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    formatDuration(track.duration),
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }
    }
    if (showAlphabetScroller && letterIndex.isNotEmpty()) {
        AlphabetFastScroller(
            letterIndex = letterIndex,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(vertical = 12.dp, horizontal = 2.dp),
            onLetterSelected = { letter ->
                letterIndex[letter]?.let { idx ->
                    scope.launch { listState.scrollToItem(idx.coerceIn(0, tracks.lastIndex.coerceAtLeast(0))) }
                }
            }
        )
    }
    } // Box
}

@Composable
private fun MiniPlayer(
    track: Track,
    isPlaying: Boolean,
    backdrop: GraphicsLayer,
    onClick: () -> Unit,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit
) {
    GlassSurface(
        backdrop = backdrop,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArtImage(path = track.artPath, size = 44.dp, shape = RoundedCornerShape(12.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(track.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onPrevious) { Icon(Icons.Rounded.SkipPrevious, contentDescription = "Anterior") }
            IconButton(onClick = onToggle) {
                Icon(
                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pausar" else "Tocar"
                )
            }
            IconButton(onClick = onNext) { Icon(Icons.Rounded.SkipNext, contentDescription = "Próxima") }
        }
    }
}

/** Menu de ações para multi-seleção. */
@Composable
private fun SelectionActionsMenu(
    count: Int,
    backdrop: GraphicsLayer,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayLast: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onFavorite: () -> Unit,
    onShare: () -> Unit = {},
    onEditTags: (() -> Unit)? = null,
    onRenameBatch: (() -> Unit)? = null,
    onDelete: () -> Unit
) {
    // onEditTags: 1 faixa = editor completo; várias = edição em lote (Artista/Álbum/Artista do Álbum)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 72.dp) // um pouco mais alto que o fundo da tela
                    .navigationBarsPadding()
                    .clickable(enabled = false) {}
            ) {
                GlassSurface(
                    backdrop = backdrop,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                ) {
                    Column(
                        Modifier
                            .padding(vertical = 8.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            if (count == 1) "1 faixa selecionada" else "$count faixas selecionadas",
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1A1A1A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        MenuRow("Reproduzir a seguir", onPlayNext)
                        MenuRow("Reproduzir no final", onPlayLast)
                        MenuRow("Adicionar a uma Playlist", onAddToPlaylist)
                        MenuRow("Favoritar", onFavorite)
                        MenuRow("Compartilhar", onShare)
                        if (onEditTags != null) {
                            MenuRow(
                                if (count == 1) "Editar tags" else "Editar tags em lote",
                                onEditTags
                            )
                        }
                        if (onRenameBatch != null) {
                            MenuRow("Renomear por tags", onRenameBatch)
                        }
                        MenuRow("Excluir", onDelete, textColor = Color(0xFFC62828))
                        MenuRow("Fechar menu", onDismiss)
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(label: String, onClick: () -> Unit, textColor: Color = Color(0xFF1A1A1A)) {
    Text(
        text = label,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        fontSize = 16.sp,
        color = textColor
    )
}

@Composable
private fun AddToPlaylistDialog(
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onCreateNew: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar a playlist") },
        text = {
            Column {
                playlists.forEach { pl ->
                    Text(
                        pl.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(pl.id) }
                            .padding(vertical = 12.dp),
                        fontSize = 16.sp
                    )
                }
                Text(
                    "+ Criar nova playlist",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onCreateNew)
                        .padding(vertical = 12.dp),
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}


@Composable
private fun SortBar(
    isGroup: Boolean,
    groupSort: MusicViewModel.GroupSort,
    groupOrder: MusicViewModel.SortOrder,
    trackSort: MusicViewModel.TrackSort,
    trackOrder: MusicViewModel.SortOrder,
    onGroupSort: (MusicViewModel.GroupSort, MusicViewModel.SortOrder) -> Unit,
    onTrackSort: (MusicViewModel.TrackSort, MusicViewModel.SortOrder) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val label = if (isGroup) {
        val s = when (groupSort) {
            MusicViewModel.GroupSort.NAME -> "Nome"
            MusicViewModel.GroupSort.TRACK_COUNT -> "Qtd. músicas"
            MusicViewModel.GroupSort.TOTAL_DURATION -> "Duração total"
        }
        val o = if (groupOrder == MusicViewModel.SortOrder.ASC) "↑" else "↓"
        "$s $o"
    } else {
        val s = when (trackSort) {
            MusicViewModel.TrackSort.NAME -> "Nome"
            MusicViewModel.TrackSort.FILE_SIZE -> "Tamanho"
            MusicViewModel.TrackSort.DURATION -> "Duração"
            MusicViewModel.TrackSort.LAST_MODIFIED -> "Atualização"
        }
        val o = if (trackOrder == MusicViewModel.SortOrder.ASC) "↑" else "↓"
        "$s $o"
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(0.10f))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Ordenar: $label", fontSize = 13.sp, color = Color.White.copy(0.85f))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (isGroup) {
                listOf(
                    MusicViewModel.GroupSort.NAME to "Nome",
                    MusicViewModel.GroupSort.TRACK_COUNT to "Quantidade de músicas",
                    MusicViewModel.GroupSort.TOTAL_DURATION to "Duração total"
                ).forEach { (sort, name) ->
                    DropdownMenuItem(
                        text = { Text("$name ↑") },
                        onClick = {
                            onGroupSort(sort, MusicViewModel.SortOrder.ASC)
                            expanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("$name ↓") },
                        onClick = {
                            onGroupSort(sort, MusicViewModel.SortOrder.DESC)
                            expanded = false
                        }
                    )
                }
            } else {
                listOf(
                    MusicViewModel.TrackSort.NAME to "Nome",
                    MusicViewModel.TrackSort.FILE_SIZE to "Tamanho do arquivo",
                    MusicViewModel.TrackSort.DURATION to "Duração da faixa",
                    MusicViewModel.TrackSort.LAST_MODIFIED to "Última atualização"
                ).forEach { (sort, name) ->
                    DropdownMenuItem(
                        text = { Text("$name ↑") },
                        onClick = {
                            onTrackSort(sort, MusicViewModel.SortOrder.ASC)
                            expanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("$name ↓") },
                        onClick = {
                            onTrackSort(sort, MusicViewModel.SortOrder.DESC)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}


private data class ArtistAlbumItem(
    val name: String,
    val trackCount: Int,
    val artPath: String?,
    val totalDurationMs: Long
)

private fun formatTotalDuration(ms: Long): String {
    val totalMin = (ms / 60_000L).toInt()
    return if (totalMin < 60) "$totalMin min"
    else {
        val h = totalMin / 60
        val m = totalMin % 60
        if (m == 0) "${h} h" else "${h} h ${m} min"
    }
}

@Composable
private fun ArtistDetailScreen(
    artistName: String,
    trackCount: Int,
    totalDurationMs: Long,
    albums: List<ArtistAlbumItem>,
    bottomPadding: Dp,
    onOpenAlbum: (String) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(
                artistName,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${faixas(trackCount)}  ·  ${formatTotalDuration(totalDurationMs)}",
                fontSize = 14.sp,
                color = Color.White.copy(0.65f)
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            lazyGridItems(albums, key = { it.name }) { album ->
                Column(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onOpenAlbum(album.name) }
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (album.artPath != null) {
                            AsyncImage(
                                model = File(album.artPath),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        album.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White
                    )
                    Text(
                        "${faixas(album.trackCount)} · ${formatTotalDuration(album.totalDurationMs)}",
                        fontSize = 12.sp,
                        color = Color.White.copy(0.55f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumDetailScreen(
    albumName: String,
    artPath: String?,
    artists: List<String>,
    tracks: List<Track>,
    totalDurationMs: Long,
    nowPlayingId: String?,
    bottomPadding: Dp,
    accent: Color,
    selectedIds: Set<String>,
    selectionMode: Boolean,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onTrackClick: (Int, Track) -> Unit,
    onTrackLongClick: (Track) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = bottomPadding),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(320.dp)
            ) {
                if (artPath != null) {
                    AsyncImage(
                        model = File(artPath),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(Color(0xFF2A2A32)))
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.Black.copy(0.15f),
                                    Color.Black.copy(0.55f),
                                    Color.Black.copy(0.92f)
                                )
                            )
                        )
                )
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(8.dp))
                Text(
                    albumName,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2
                )
                Spacer(Modifier.height(6.dp))
                if (artists.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        artists.forEachIndexed { idx, name ->
                            if (idx > 0) {
                                Text(", ", color = Color.White.copy(0.7f), fontSize = 15.sp)
                            }
                            Text(
                                name,
                                color = Color(0xFF82B1FF),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable { onOpenArtist(name) }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "${faixas(tracks.size)}  ·  ${formatTotalDuration(totalDurationMs)}",
                    fontSize = 13.sp,
                    color = Color.White.copy(0.6f)
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onPlayAll,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Reproduzir", fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = onShuffle,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(0.18f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Shuffle", fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
            val active = track.id == nowPlayingId
            val selected = track.id in selectedIds
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (selected) Color.White.copy(0.12f) else Color.Transparent)
                        .combinedClickable(
                            onClick = { onTrackClick(index, track) },
                            onLongClick = { onTrackLongClick(track) }
                        )
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        track.trackNumber?.toString() ?: "${index + 1}",
                        fontSize = 14.sp,
                        color = Color.White.copy(0.45f),
                        modifier = Modifier.width(28.dp)
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            track.title,
                            fontSize = 16.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (active) accent else Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            track.artist,
                            fontSize = 13.sp,
                            color = Color.White.copy(0.55f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color.White.copy(0.5f),
                        modifier = Modifier.size(22.dp)
                    )
                }
                HorizontalDivider(
                    color = Color.White.copy(0.08f),
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }
    }
}

@Composable
private fun BatchTagEditDialog(
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: (artist: String?, album: String?, albumArtist: String?) -> Unit
) {
    var artist by remember { mutableStateOf("") }
    var album by remember { mutableStateOf("") }
    var albumArtist by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar tags em lote ($count faixas)") },
        text = {
            Column {
                Text(
                    "Deixe em branco os campos que não quiser alterar.",
                    fontSize = 13.sp,
                    color = Color.White.copy(0.6f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artista") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = album,
                    onValueChange = { album = it },
                    label = { Text("Álbum") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = albumArtist,
                    onValueChange = { albumArtist = it },
                    label = { Text("Artista do Álbum") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val canApply = artist.isNotBlank() || album.isNotBlank() || albumArtist.isNotBlank()
            TextButton(
                onClick = {
                    onConfirm(
                        artist.takeIf { it.isNotBlank() },
                        album.takeIf { it.isNotBlank() },
                        albumArtist.takeIf { it.isNotBlank() }
                    )
                },
                enabled = canApply
            ) { Text("Aplicar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}


private val DEFAULT_RENAME_PATTERNS = listOf(
    "%ARTIST% - %TITLE%",
    "%TITLE% - %ARTIST%",
    "%TITLE% - %ALBUM%",
    "%TITLE% - %ARTIST% (%ALBUM%)",
    "%TRACK%. %TITLE%",
    "%TRACK%. %TITLE% - %ALBUM%",
    "%TRACK%. %TITLE% - %ARTIST%"
)

fun applyRenamePattern(pattern: String, track: Track): String {
    val trackNum = track.trackNumber?.toString()?.padStart(2, '0') ?: "00"
    return pattern
        .replace("%ARTIST%", track.artist)
        .replace("%TITLE%", track.title)
        .replace("%ALBUM%", track.album)
        .replace("%ALBUM_ARTIST%", track.albumArtist.ifBlank { track.artist })
        .replace("%TRACK%", trackNum)
        .replace("%YEAR%", track.year?.toString() ?: "")
        .replace("%GENRE%", track.genre)
        .replace("%COMPOSER%", "")
        .replace("%COMMENT%", "")
        .replace("%DISC_NUMBER%", "")
        .replace("%RECORD_LABEL%", "")
        .replace("%REMIXER%", "")
        .trim()
        .ifBlank { track.title }
}

@Composable
private fun BatchRenameDialog(
    tracks: List<Track>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var patterns by remember { mutableStateOf(DEFAULT_RENAME_PATTERNS.toMutableList()) }
    var selected by remember { mutableStateOf(patterns.first()) }
    var showAdd by remember { mutableStateOf(false) }
    var newPattern by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renomear por tags (${tracks.size})") },
        text = {
            Column {
                patterns.forEach { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { selected = p }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected == p, onClick = { selected = p })
                        Text(p, Modifier.padding(start = 8.dp), color = Color(0xFF1A1A1A))
                    }
                }
                TextButton(onClick = { showAdd = true }) {
                    Text("+ Adicionar padrão")
                }
                if (showAdd) {
                    OutlinedTextField(
                        value = newPattern,
                        onValueChange = { newPattern = it },
                        label = { Text("Padrão") },
                        placeholder = { Text("%ARTIST% - %TITLE%") }
                    )
                    Text(
                        "Códigos: %ARTIST% %TITLE% %ALBUM% %ALBUM_ARTIST% %TRACK% %YEAR% %GENRE%",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    TextButton(onClick = {
                        if (newPattern.isNotBlank()) {
                            patterns = (patterns + newPattern).toMutableList()
                            selected = newPattern
                            newPattern = ""
                            showAdd = false
                        }
                    }) { Text("Concluído") }
                }
                // Preview
                tracks.firstOrNull()?.let { t ->
                    Text(
                        "Ex.: ${applyRenamePattern(selected, t)}",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) { Text("Renomear") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
