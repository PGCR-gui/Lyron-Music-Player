package com.tolete.player.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.tolete.player.data.LrcLibClient
import com.tolete.player.data.LyricLine
import com.tolete.player.data.Playlist
import com.tolete.player.data.Track
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.sin

// =====================================================================
//  Skin "NyxT" — preto + azul elétrico, abas no topo, Home com estatísticas
// =====================================================================

internal val NyxTBlack = Color(0xFF000000)
internal val NyxTAccent = Color(0xFF4F5BFF)
internal val NyxTAccentDeep = Color(0xFF2F3CE8)
private val NyxTAccentLight = Color(0xFF6C7AFF)
private val NyxTMuted = Color(0xFF8E8E93)
private val NyxTTrack = Color(0xFF1F1F22)

private val NyxTTwoPi: Float = (2.0 * PI).toFloat()

private val NyxTBlueBrush = Brush.verticalGradient(listOf(NyxTAccentLight, NyxTAccentDeep))

/** Paleta usada por telas compartilhadas (Configurações etc.) quando a NyxT está ativa. */
internal val NyxTAppColors = AppColors(
    gradientTop = NyxTBlack,
    gradientBottom = NyxTBlack,
    accent = NyxTAccent,
    surface = Color(0xFF111114)
)

private enum class NyxTab(val label: String, val vmTab: MusicViewModel.Tab?) {
    HOME("Início", null),
    SONGS("Músicas", MusicViewModel.Tab.ALL),
    ALBUMS("Álbum", MusicViewModel.Tab.ALBUMS),
    ARTISTS("Artista", MusicViewModel.Tab.ARTISTS),
    GENRES("Gênero", MusicViewModel.Tab.GENRES),
    PLAYLISTS("Playlists", MusicViewModel.Tab.PLAYLISTS)
}

private fun nyxFaixas(n: Int) = if (n == 1) "1 faixa" else "$n faixas"

private fun daysAgoLabel(lastModified: Long): String {
    val ms = if (lastModified < 100_000_000_000L) lastModified * 1000 else lastModified
    val days = ((System.currentTimeMillis() - ms) / 86_400_000L).coerceAtLeast(0)
    return when (days) {
        0L -> "hoje"
        1L -> "1 dia\natrás"
        else -> "$days dias\natrás"
    }
}

/** Capa que preenche o espaço disponível. */
@Composable
private fun NyxArt(path: String?, shape: Shape, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
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
                tint = Color.White.copy(alpha = 0.25f),
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------
//  Tela principal
// ---------------------------------------------------------------------

@Composable
internal fun NyxTMain(
    vm: MusicViewModel,
    selectedIds: Set<String>,
    selectionMode: Boolean,
    onToggleSelect: (Track) -> Unit,
    onLongSelect: (Track) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onPickFolder: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onImportM3u: () -> Unit,
    onExportM3u: (Playlist) -> Unit
) {
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val tab by vm.currentTab.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val visible by vm.visibleTracks.collectAsStateWithLifecycle()
    val nowPlaying by vm.nowPlaying.collectAsStateWithLifecycle()
    val isPlaying by vm.isPlaying.collectAsStateWithLifecycle()
    val artists by vm.artists.collectAsStateWithLifecycle()
    val albums by vm.albums.collectAsStateWithLifecycle()
    val genres by vm.genres.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val isScanning by vm.isScanning.collectAsStateWithLifecycle()
    val scanCount by vm.scanCount.collectAsStateWithLifecycle()
    val isSearching by vm.isSearching.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val searchResults by vm.searchResults.collectAsStateWithLifecycle()
    val groupSort by vm.groupSort.collectAsStateWithLifecycle()
    val groupOrder by vm.groupOrder.collectAsStateWithLifecycle()
    val trackSort by vm.trackSort.collectAsStateWithLifecycle()
    val trackOrder by vm.trackOrder.collectAsStateWithLifecycle()

    var home by rememberSaveable { mutableStateOf(true) }
    var gridMode by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(filter) { if (filter != null) home = false }

    val current = if (home) NyxTab.HOME else when (tab) {
        MusicViewModel.Tab.ALL -> NyxTab.SONGS
        MusicViewModel.Tab.ALBUMS -> NyxTab.ALBUMS
        MusicViewModel.Tab.ARTISTS -> NyxTab.ARTISTS
        MusicViewModel.Tab.GENRES -> NyxTab.GENRES
        MusicViewModel.Tab.PLAYLISTS -> NyxTab.PLAYLISTS
    }

    fun select(t: NyxTab) {
        if (t == NyxTab.HOME) {
            home = true
            vm.clearFilter()
            vm.closeSearch()
        } else {
            home = false
            vm.setTab(t.vmTab!!)
        }
    }

    fun playFrom(list: List<Track>, index: Int, track: Track) {
        if (selectionMode) onToggleSelect(track) else vm.playTracks(list, index)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(NyxTBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // ----- Cabeçalho -----
        if (isSearching) {
            SearchHeader(query = searchQuery, onQueryChange = vm::setSearchQuery, onClose = vm::closeSearch)
        } else if (filter != null) {
            Row(
                Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = vm::clearFilter) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar")
                }
                Text(
                    text = if (tab == MusicViewModel.Tab.PLAYLISTS)
                        playlists.firstOrNull { it.id == filter!!.key }?.name ?: filter!!.key
                    else filter!!.key,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            NyxTTabs(current = current, onSelect = ::select, onSearch = vm::openSearch, onSettings = onOpenSettings)
        }

        // ----- Conteúdo -----
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                isSearching -> NyxTTrackList(
                    tracks = searchResults, nowPlayingId = nowPlaying?.id,
                    selectedIds = selectedIds, selectionMode = selectionMode,
                    onClick = { i, t -> playFrom(searchResults, i, t) }, onLongClick = onLongSelect
                )

                isScanning && tracks.isEmpty() -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = NyxTAccent)
                    Text("Lendo sua biblioteca… $scanCount", modifier = Modifier.padding(top = 16.dp))
                }

                tracks.isEmpty() -> Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Nenhuma música ainda", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Escolha a pasta onde estão suas músicas.",
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = NyxTMuted
                    )
                    TextButton(onClick = onPickFolder) { Text("Escolher pasta", color = NyxTAccentLight) }
                }

                filter != null -> NyxTTrackList(
                    tracks = visible, nowPlayingId = nowPlaying?.id,
                    selectedIds = selectedIds, selectionMode = selectionMode,
                    onClick = { i, t -> playFrom(visible, i, t) }, onLongClick = onLongSelect
                )

                current == NyxTab.HOME -> NyxTHome(
                    tracks = tracks,
                    albumCount = albums.size,
                    artistCount = artists.size,
                    genreCount = genres.size,
                    playlistCount = playlists.size,
                    history = history,
                    onOpenTab = ::select,
                    onPlay = { list, i -> vm.playTracks(list, i) }
                )

                current == NyxTab.SONGS -> {
                    val sorted = vm.sortedTracks(visible)
                    Column(Modifier.fillMaxSize()) {
                        NyxTSortRow(
                            label = when (trackSort) {
                                MusicViewModel.TrackSort.NAME -> "Título"
                                MusicViewModel.TrackSort.FILE_SIZE -> "Tamanho"
                                MusicViewModel.TrackSort.DURATION -> "Duração"
                                MusicViewModel.TrackSort.LAST_MODIFIED -> "Atualização"
                            },
                            descending = trackOrder == MusicViewModel.SortOrder.DESC,
                            options = listOf(
                                "Título" to MusicViewModel.TrackSort.NAME,
                                "Tamanho" to MusicViewModel.TrackSort.FILE_SIZE,
                                "Duração" to MusicViewModel.TrackSort.DURATION,
                                "Atualização" to MusicViewModel.TrackSort.LAST_MODIFIED
                            ).map { (n, s) ->
                                Triple(n, { vm.setTrackSort(s, MusicViewModel.SortOrder.ASC) },
                                    { vm.setTrackSort(s, MusicViewModel.SortOrder.DESC) })
                            },
                            trailing = null
                        )
                        NyxTTrackList(
                            tracks = sorted, nowPlayingId = nowPlaying?.id,
                            selectedIds = selectedIds, selectionMode = selectionMode,
                            onClick = { i, t -> playFrom(sorted, i, t) }, onLongClick = onLongSelect
                        )
                    }
                }

                current == NyxTab.PLAYLISTS -> PlaylistList(
                    playlists = playlists,
                    trackCount = { id -> playlists.firstOrNull { it.id == id }?.trackIds?.size ?: 0 },
                    bottomPadding = 16.dp,
                    onOpen = { vm.openGroup(it) },
                    onCreate = onCreatePlaylist,
                    onImport = onImportM3u,
                    onExport = onExportM3u
                )

                else -> {
                    val groups = when (current) {
                        NyxTab.ALBUMS -> vm.sortedAlbums(albums).map {
                            NyxGroup(it.name, it.artist.ifBlank { nyxFaixas(it.trackCount) }, it.artPath, false)
                        }
                        NyxTab.ARTISTS -> vm.sortedArtists(artists).map {
                            NyxGroup(it.name, nyxFaixas(it.trackCount), it.artPath, true)
                        }
                        else -> vm.sortedGenres(genres).map {
                            NyxGroup(it.name, nyxFaixas(it.trackCount), it.artPath, false)
                        }
                    }
                    Column(Modifier.fillMaxSize()) {
                        NyxTSortRow(
                            label = when (groupSort) {
                                MusicViewModel.GroupSort.NAME -> "Título"
                                MusicViewModel.GroupSort.TRACK_COUNT -> "Faixas"
                                MusicViewModel.GroupSort.TOTAL_DURATION -> "Duração"
                            },
                            descending = groupOrder == MusicViewModel.SortOrder.DESC,
                            options = listOf(
                                "Título" to MusicViewModel.GroupSort.NAME,
                                "Faixas" to MusicViewModel.GroupSort.TRACK_COUNT,
                                "Duração" to MusicViewModel.GroupSort.TOTAL_DURATION
                            ).map { (n, s) ->
                                Triple(n, { vm.setGroupSort(s, MusicViewModel.SortOrder.ASC) },
                                    { vm.setGroupSort(s, MusicViewModel.SortOrder.DESC) })
                            },
                            trailing = {
                                IconButton(onClick = { gridMode = !gridMode }) {
                                    Icon(
                                        if (gridMode) Icons.Rounded.ViewAgenda else Icons.Rounded.ViewModule,
                                        contentDescription = "Alternar visualização",
                                        tint = NyxTMuted
                                    )
                                }
                            }
                        )
                        if (gridMode) NyxTGroupGrid(groups, onClick = vm::openGroup)
                        else NyxTGroupList(groups, onClick = vm::openGroup)
                    }
                }
            }
            if (isScanning && tracks.isNotEmpty()) {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    color = NyxTAccent,
                    trackColor = Color.Transparent
                )
            }
        }

        // ----- Mini player -----
        nowPlaying?.let { np ->
            NyxTMiniPlayer(track = np, isPlaying = isPlaying, onClick = onOpenNowPlaying, onToggle = vm::togglePlayPause)
        }
    }
}

private data class NyxGroup(val title: String, val subtitle: String, val artPath: String?, val round: Boolean)

// ---------------------------------------------------------------------
//  Abas
// ---------------------------------------------------------------------

@Composable
private fun NyxTTabs(
    current: NyxTab,
    onSelect: (NyxTab) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(current) { listState.animateScrollToItem(current.ordinal) }
    Row(
        Modifier.fillMaxWidth().height(72.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(NyxTab.values().toList()) { t ->
                val selected = t == current
                val size by animateFloatAsState(if (selected) 30f else 22f, tween(200), label = "tabSize")
                Text(
                    t.label,
                    fontSize = size.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (selected) Color.White else Color.White.copy(alpha = 0.38f),
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelect(t) }
                )
            }
        }
        IconButton(onClick = onSearch, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.Search, contentDescription = "Buscar", tint = Color.White.copy(0.7f))
        }
        IconButton(onClick = onSettings, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.Settings, contentDescription = "Configurações", tint = Color.White.copy(0.7f))
        }
        Spacer(Modifier.width(4.dp))
    }
}

@Composable
private fun NyxTSortRow(
    label: String,
    descending: Boolean,
    options: List<Triple<String, () -> Unit, () -> Unit>>,
    trailing: (@Composable () -> Unit)?
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Text(
                "$label ${if (descending) "↓" else "↑"}",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = NyxTMuted,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 4.dp, vertical = 6.dp)
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (name, asc, desc) ->
                    DropdownMenuItem(text = { Text("$name ↑") }, onClick = { asc(); expanded = false })
                    DropdownMenuItem(text = { Text("$name ↓") }, onClick = { desc(); expanded = false })
                }
            }
        }
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

// ---------------------------------------------------------------------
//  Home
// ---------------------------------------------------------------------

@Composable
private fun NyxTHome(
    tracks: List<Track>,
    albumCount: Int,
    artistCount: Int,
    genreCount: Int,
    playlistCount: Int,
    history: List<Track>,
    onOpenTab: (NyxTab) -> Unit,
    onPlay: (List<Track>, Int) -> Unit
) {
    val lastAdded = remember(tracks) {
        tracks.filter { it.lastModified > 0 }.sortedByDescending { it.lastModified }.take(15)
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            val stats = listOf(
                Triple(tracks.size, "MÚSICAS", NyxTab.SONGS),
                Triple(albumCount, "ÁLBUNS", NyxTab.ALBUMS),
                Triple(artistCount, "ARTISTAS", NyxTab.ARTISTS),
                Triple(genreCount, "GÊNEROS", NyxTab.GENRES),
                Triple(playlistCount, "PLAYLISTS", NyxTab.PLAYLISTS)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(stats) { (n, label, target) ->
                    Column(
                        Modifier
                            .width(110.dp)
                            .height(80.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(NyxTBlueBrush)
                            .clickable { onOpenTab(target) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("$n", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                }
            }
        }

        item { NyxTSectionTitle("History") { onOpenTab(NyxTab.SONGS) } }
        item {
            if (history.isEmpty()) {
                Text(
                    "Suas músicas tocadas recentemente aparecem aqui.",
                    fontSize = 14.sp,
                    color = NyxTMuted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    itemsIndexed(history) { i, t ->
                        Column(
                            Modifier.width(94.dp).clickable { onPlay(history, i) },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            ArtImage(path = t.artPath, size = 94.dp, shape = RoundedCornerShape(20.dp))
                            Text(
                                t.title,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        item { NyxTSectionTitle("Last Added") { onOpenTab(NyxTab.SONGS) } }
        itemsIndexed(lastAdded) { i, t ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPlay(lastAdded, i) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArtImage(path = t.artPath, size = 63.dp, shape = RoundedCornerShape(16.dp))
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(t.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        t.artist, fontSize = 15.sp, fontStyle = FontStyle.Italic,
                        color = NyxTMuted, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    daysAgoLabel(t.lastModified),
                    fontSize = 14.sp,
                    color = NyxTMuted,
                    textAlign = TextAlign.End,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun NyxTSectionTitle(title: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = NyxTMuted, modifier = Modifier.size(30.dp))
    }
}

// ---------------------------------------------------------------------
//  Listas
// ---------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NyxTTrackList(
    tracks: List<Track>,
    nowPlayingId: String?,
    selectedIds: Set<String>,
    selectionMode: Boolean,
    onClick: (Int, Track) -> Unit,
    onLongClick: (Track) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp)) {
        itemsIndexed(tracks) { index, track ->
            val active = track.id == nowPlayingId
            val selected = track.id in selectedIds
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (selected) NyxTAccent.copy(alpha = 0.22f) else Color.Transparent)
                    .combinedClickable(
                        onClick = { onClick(index, track) },
                        onLongClick = { onLongClick(track) }
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectionMode) {
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (selected) NyxTAccent else Color.White.copy(0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) Text("✓", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(Modifier.width(12.dp))
                }
                ArtImage(path = track.artPath, size = 63.dp, shape = RoundedCornerShape(16.dp))
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(
                        track.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (active) NyxTAccentLight else Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        track.artist,
                        fontSize = 16.sp,
                        fontStyle = FontStyle.Italic,
                        color = NyxTMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    formatDuration(track.duration),
                    fontSize = 15.sp,
                    color = NyxTMuted,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun NyxTGroupGrid(items: List<NyxGroup>, onClick: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        items(items) { g ->
            Column(
                Modifier.clickable { onClick(g.title) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NyxArt(
                    g.artPath,
                    if (g.round) CircleShape else RoundedCornerShape(28.dp),
                    Modifier.fillMaxWidth().aspectRatio(1f)
                )
                Text(
                    g.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp)
                )
                Text(
                    g.subtitle,
                    fontSize = 15.sp,
                    fontStyle = FontStyle.Italic,
                    color = NyxTMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun NyxTGroupList(items: List<NyxGroup>, onClick: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp)) {
        items(items) { g ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onClick(g.title) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArtImage(
                    path = g.artPath,
                    size = 63.dp,
                    shape = if (g.round) CircleShape else RoundedCornerShape(16.dp)
                )
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(g.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(g.subtitle, fontSize = 15.sp, fontStyle = FontStyle.Italic, color = NyxTMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------
//  Mini player
// ---------------------------------------------------------------------

@Composable
private fun NyxTMiniPlayer(
    track: Track,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(NyxTBlack)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtImage(path = track.artPath, size = 63.dp, shape = RoundedCornerShape(16.dp))
        Box(
            Modifier
                .padding(start = 14.dp)
                .width(1.5.dp)
                .height(60.dp)
                .background(Color.White.copy(0.18f))
        )
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(track.title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, fontSize = 17.sp, color = NyxTMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onToggle, modifier = Modifier.size(64.dp)) {
            Icon(
                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Pausar" else "Tocar",
                modifier = Modifier.size(52.dp),
                tint = Color.White
            )
        }
    }
}

// ---------------------------------------------------------------------
//  Tocando agora (NyxT)
// ---------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NyxTNowPlayingScreen(
    track: Track,
    isPlaying: Boolean,
    shuffle: Boolean,
    repeatMode: Int,
    queue: List<Track>,
    isFavorite: Boolean,
    lyricsFontPath: String?,
    getPosition: () -> Long,
    onClose: () -> Unit,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onSkipToQueueItem: (Int) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onEditTags: () -> Unit = {},
    artistNames: List<String>,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: () -> Unit,
    loadLyrics: suspend () -> List<LyricLine>,
    searchLyrics: suspend (String, String) -> List<LrcLibClient.SearchResult>,
    embedLyricsResult: suspend (LrcLibClient.SearchResult) -> List<LyricLine>,
    lyricsFontSize: Float,
    lyricsPrimaryColor: Int,
    lyricsSecondaryColor: Int,
    lyricsBorderColor: Int,
    lyricsBorderWidth: Float
) {
    var position by remember { mutableLongStateOf(getPosition()) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    var showQueue by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showTech by remember { mutableStateOf(false) }
    var showArtistMenu by remember(track.id) { mutableStateOf(false) }
    var showLyricsSearch by remember { mutableStateOf(false) }
    var showFullLyrics by remember { mutableStateOf(false) }
    var lyrics by remember { mutableStateOf<List<LyricLine>>(emptyList()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(track.id, isPlaying) {
        position = getPosition()
        while (isPlaying) {
            delay(400)
            val p = getPosition()
            if (kotlin.math.abs(p - position) >= 200L || p < position) {
                position = p
            }
        }
    }
    LaunchedEffect(track.id) { lyrics = loadLyrics() }

    val duration = track.duration.coerceAtLeast(1L)
    val fraction = dragFraction ?: (position.toFloat() / duration).coerceIn(0f, 1f)
    val shownPosition = dragFraction?.let { (it * duration).toLong() } ?: position
    val synced = remember(lyrics) { lyrics.filter { it.timeMs >= 0 } }
    val lyricsFont = remember(lyricsFontPath) {
        lyricsFontPath?.let { path ->
            try {
                val f = File(path)
                if (f.exists()) FontFamily(Font(f)) else null
            } catch (_: Exception) { null }
        }
    }

    val currentIndex = remember(queue, track.id) { queue.indexOfFirst { it.id == track.id } }
    val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        Modifier
            .fillMaxSize()
            .background(NyxTBlack)
            .pointerInput(Unit) {}
    ) {
        // Onda animada no rodapé (tocando) / barra azul lisa (pausado)
        NyxTWave(isPlaying = isPlaying, navBarHeight = navBarHeight, modifier = Modifier.align(Alignment.BottomCenter))

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Barra superior
            Row(
                Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Fechar", modifier = Modifier.size(36.dp))
                }
                Text(
                    "NOW PLAYING",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Mais opções")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Adicionar a uma playlist") }, onClick = { showMenu = false; onAddToPlaylist() })
                        DropdownMenuItem(text = { Text("Editar tags") }, onClick = { showMenu = false; onEditTags() })
                        DropdownMenuItem(text = { Text("Buscar letra") }, onClick = { showMenu = false; showLyricsSearch = true })
                        DropdownMenuItem(text = { Text("Ir para o álbum") }, onClick = { showMenu = false; onOpenAlbum() })
                        DropdownMenuItem(text = { Text("Detalhes técnicos") }, onClick = { showMenu = false; showTech = true })
                    }
                }
            }

            // Carrossel de capas (vizinhas aparecem nas laterais)
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val side = minOf(maxWidth - 88.dp, maxHeight)
                val pad = (maxWidth - side) / 2
                val pageCount = if (currentIndex >= 0) queue.size else 1
                val pagerState = rememberPagerState(initialPage = currentIndex.coerceAtLeast(0)) { pageCount }
                val skip by rememberUpdatedState(onSkipToQueueItem)
                val liveIndex by rememberUpdatedState(currentIndex)
                LaunchedEffect(currentIndex) {
                    if (currentIndex >= 0 && pagerState.currentPage != currentIndex) {
                        pagerState.animateScrollToPage(currentIndex)
                    }
                }
                LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.settledPage }.collect { p ->
                        if (liveIndex >= 0 && p != liveIndex) skip(p)
                    }
                }
                HorizontalPager(
                    state = pagerState,
                    contentPadding = PaddingValues(horizontal = pad),
                    pageSpacing = 28.dp,
                    modifier = Modifier.fillMaxWidth().height(side).align(Alignment.Center)
                ) { page ->
                    val t = if (currentIndex >= 0) queue.getOrNull(page) ?: track else track
                    NyxArt(
                        t.artPath,
                        RoundedCornerShape(30.dp),
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val off = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                                    .absoluteValue.coerceIn(0f, 1f)
                                val s = 1f - 0.26f * off
                                scaleX = s
                                scaleY = s
                            }
                    )
                }
            }

            // Título + artista
            Text(
                track.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp)
                    .basicMarquee(iterations = Int.MAX_VALUE),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                color = Color.White
            )
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    track.artist,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = NyxTMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            if (artistNames.size > 1) showArtistMenu = true
                            else onOpenArtist(artistNames.firstOrNull() ?: track.artist)
                        }
                )
                DropdownMenu(expanded = showArtistMenu, onDismissRequest = { showArtistMenu = false }) {
                    artistNames.forEach { name ->
                        DropdownMenuItem(text = { Text(name) }, onClick = { showArtistMenu = false; onOpenArtist(name) })
                    }
                }
            }

            // Linha de ações (esmaecidas, como no design)
            val dim = Color.White.copy(alpha = 0.20f)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showTech = true }) {
                    Icon(Icons.Rounded.Apps, contentDescription = "Detalhes técnicos", tint = dim, modifier = Modifier.size(34.dp))
                }
                IconButton(onClick = { showQueue = true }) {
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = "Fila", tint = dim, modifier = Modifier.size(34.dp))
                }
                IconButton(onClick = { if (synced.isNotEmpty()) showFullLyrics = true else showLyricsSearch = true }) {
                    Icon(
                        Icons.Rounded.AllInclusive,
                        contentDescription = "Letras",
                        tint = if (synced.isNotEmpty()) NyxTAccentLight else dim,
                        modifier = Modifier.size(40.dp)
                    )
                }
                IconButton(onClick = { showLyricsSearch = true }) {
                    Icon(Icons.Rounded.Tune, contentDescription = "Buscar letra", tint = dim, modifier = Modifier.size(34.dp))
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        Icons.Rounded.Favorite,
                        contentDescription = "Favoritar",
                        tint = if (isFavorite) Color(0xFFE53935) else dim,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            // Barra de progresso
            NyxTSeekBar(
                fraction = fraction,
                onDrag = { dragFraction = it },
                onDragEnd = { f ->
                    val target = (f * duration).toLong()
                    position = target
                    dragFraction = null
                    onSeek(target)
                },
                onDragCancel = { dragFraction = null },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatDuration(shownPosition), fontSize = 18.sp, color = NyxTMuted)
                Text(formatDuration(duration), fontSize = 18.sp, color = NyxTMuted)
            }

            // Controles
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = if (shuffle) "Aleatório ligado" else "Aleatório desligado",
                        tint = if (shuffle) NyxTAccentLight else Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
                IconButton(onClick = onPrevious, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Rounded.FastRewind, contentDescription = "Anterior", tint = Color.White, modifier = Modifier.size(56.dp))
                }
                NyxTPlayPauseButton(isPlaying = isPlaying, onClick = onToggle)
                IconButton(onClick = onNext, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Rounded.FastForward, contentDescription = "Próxima", tint = Color.White, modifier = Modifier.size(56.dp))
                }
                IconButton(onClick = onCycleRepeat) {
                    Icon(
                        if (repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        contentDescription = "Repetir",
                        tint = if (repeatMode == androidx.media3.common.Player.REPEAT_MODE_OFF) Color.White else NyxTAccentLight,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            // Rodapé: codec · posição na fila · bitrate (só quando há dados)
            val codec = track.codec?.uppercase().orEmpty()
            val kbps = if (track.bitrate > 0) "${Math.round(track.bitrate / 1000.0)} Kb/S" else ""
            if (codec.isNotEmpty() || kbps.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(codec, fontSize = 16.sp, color = Color.White.copy(0.35f))
                    Text(
                        if (currentIndex >= 0) "${currentIndex + 1}/${queue.size}" else "",
                        fontSize = 16.sp, color = Color.White.copy(0.35f)
                    )
                    Text(kbps, fontSize = 16.sp, color = Color.White.copy(0.35f))
                }
            } else {
                Spacer(Modifier.height(20.dp))
            }
        }

        if (showQueue) {
            QueueSheet(
                queue = queue,
                currentId = track.id,
                onDismiss = { showQueue = false },
                onSkipTo = onSkipToQueueItem,
                onMove = onMoveQueueItem
            )
        }
        if (showTech) TechDetailsDialog(track = track, onDismiss = { showTech = false })
        if (showFullLyrics && synced.isNotEmpty()) {
            FullscreenLyrics(
                track = track,
                lyrics = synced,
                position = position,
                lyricsFont = lyricsFont,
                fontSizeSp = lyricsFontSize,
                primaryColor = Color(lyricsPrimaryColor),
                secondaryColor = Color(lyricsSecondaryColor),
                borderColor = Color(lyricsBorderColor),
                borderWidth = lyricsBorderWidth,
                onSeek = { ms -> onSeek(ms); position = ms },
                onClose = { showFullLyrics = false }
            )
        }
        if (showLyricsSearch) {
            LyricsSearchDialog(
                initialTitle = track.title,
                initialArtist = track.artist,
                onDismiss = { showLyricsSearch = false },
                onSearch = { t, a -> searchLyrics(t, a) },
                onPick = { result ->
                    scope.launch {
                        val lines = embedLyricsResult(result)
                        if (lines.isNotEmpty()) lyrics = lines
                        showLyricsSearch = false
                    }
                }
            )
        }
    }
}

@Composable
private fun NyxTSeekBar(
    fraction: Float,
    onDrag: (Float) -> Unit,
    onDragEnd: (Float) -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val thumb = 28.dp
    val trackH = 14.dp
    val onDragState by rememberUpdatedState(onDrag)
    val onDragEndState by rememberUpdatedState(onDragEnd)
    val onDragCancelState by rememberUpdatedState(onDragCancel)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(Unit) {
                detectTapGestures { off ->
                    onDragEndState((off.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                var last = 0f
                detectHorizontalDragGestures(
                    onDragStart = { off ->
                        last = (off.x / size.width).coerceIn(0f, 1f)
                        onDragState(last)
                    },
                    onDragEnd = { onDragEndState(last) },
                    onDragCancel = { onDragCancelState() },
                    onHorizontalDrag = { change, _ ->
                        last = (change.position.x / size.width).coerceIn(0f, 1f)
                        onDragState(last)
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val travel = maxWidth - thumb
        val thumbX = travel * fraction.coerceIn(0f, 1f)
        Box(Modifier.fillMaxWidth().height(trackH).clip(CircleShape).background(NyxTTrack))
        Box(
            Modifier
                .width(thumbX + thumb / 2)
                .height(trackH)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(NyxTAccentDeep, NyxTAccentLight)))
        )
        Box(
            Modifier
                .offset(x = thumbX)
                .size(thumb)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

@Composable
private fun NyxTPlayPauseButton(isPlaying: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(104.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isPlaying) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                repeat(2) {
                    Box(
                        Modifier
                            .width(30.dp)
                            .height(86.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(NyxTBlueBrush)
                    )
                }
            }
        } else {
            Canvas(Modifier.size(92.dp)) {
                val w = size.width
                val h = size.height
                val inset = 14f
                val path = Path().apply {
                    moveTo(w * 0.14f + inset, h * 0.06f + inset)
                    lineTo(w * 0.92f - inset, h * 0.5f)
                    lineTo(w * 0.14f + inset, h * 0.94f - inset)
                    close()
                }
                drawPath(path, NyxTBlueBrush, style = Fill)
                drawPath(path, NyxTBlueBrush, style = Stroke(width = inset * 2, join = StrokeJoin.Round))
            }
        }
    }
}

/** Rodapé azul: barra lisa quando pausado, onda animada quando tocando. */
@Composable
private fun NyxTWave(isPlaying: Boolean, navBarHeight: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val rise by animateFloatAsState(if (isPlaying) 1f else 0f, tween(600), label = "waveRise")
    // Só anima a fase enquanto toca — parado não gasta frames em infiniteTransition.
    val phase = if (isPlaying) {
        val transition = rememberInfiniteTransition(label = "wave")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = NyxTTwoPi,
            animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
            label = "wavePhase"
        ).value
    } else {
        0f
    }
    val waveMax = 110.dp
    Canvas(
        modifier
            .fillMaxWidth()
            .height(navBarHeight + waveMax)
    ) {
        val w = size.width
        val h = size.height
        val barH = navBarHeight.toPx()
        val baseY = h - barH - rise * (waveMax.toPx() * 0.55f)
        val amp1 = rise * 22.dp.toPx()
        val amp2 = rise * 12.dp.toPx()
        val path = Path().apply {
            moveTo(0f, h)
            lineTo(0f, baseY)
            // Passo maior (12px) = menos vértices por frame, visual quase igual.
            var x = 0f
            while (x <= w) {
                val t = x / w
                val a1: Float = sin(t * NyxTTwoPi * 1.1f + phase)
                val a2: Float = sin(t * NyxTTwoPi * 2.3f - phase * 1.4f)
                val y: Float = baseY + amp1 * a1 + amp2 * a2
                lineTo(x, y)
                x += 12f
            }
            lineTo(w, h)
            close()
        }
        drawPath(path, Brush.verticalGradient(listOf(NyxTAccentLight, NyxTAccentDeep), startY = baseY - amp1, endY = h))
    }
}
