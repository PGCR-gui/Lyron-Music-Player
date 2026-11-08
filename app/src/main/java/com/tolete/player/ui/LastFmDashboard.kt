package com.tolete.player.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.tolete.player.data.ArtistImageResolver
import com.tolete.player.data.ArtistMetaStore
import com.tolete.player.data.LastFmClient
import com.tolete.player.data.LastFmClient.ChartItem
import com.tolete.player.data.LastFmClient.ChartKind
import com.tolete.player.data.LastFmClient.ScrobbleItem
import com.tolete.player.data.LastFmScrobbler
import com.tolete.player.data.ScrobbleEdits
import com.tolete.player.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private enum class LfTab { SCROBBLES, LOVED, DATA, PROFILE }

private enum class LfRange(val label: String) {
    WEEK("Semanal"), MONTH("Mensal"), YEAR("Anual")
}

private val ptBr = Locale("pt", "BR")

private val lfTabs = listOf(
    GlassTab(LfTab.SCROBBLES, Icons.Rounded.History, "Scrobbles"),
    GlassTab(LfTab.LOVED, Icons.Rounded.Favorite, "Favoritos"),
    GlassTab(LfTab.DATA, Icons.Rounded.BarChart, "Dados"),
    GlassTab(LfTab.PROFILE, Icons.Rounded.Person, "Perfil")
)

// ---------------------------------------------------------------------------------------------
// Nomes originais (caminho inverso das edições de scrobble)
// ---------------------------------------------------------------------------------------------

/**
 * O Last.fm guarda os nomes DEPOIS das edições (manuais salvas + regras automáticas). Aqui o
 * caminho é invertido: aplicamos as mesmas regras a cada faixa da biblioteca e indexamos o
 * resultado; assim, o nome que está no Last.fm leva de volta à faixa local, e mostramos as
 * tags originais (título/artista/álbum do arquivo).
 */
private class OriginalResolver(tracks: List<Track>) {
    private val trackByKey = HashMap<String, Track>(tracks.size * 2)
    private val artistByKey = HashMap<String, String>()
    private val albumByKey = HashMap<String, Pair<String, String>>()
    private val albumArtByKey = HashMap<String, String>()
    private val artistCoverByKey = HashMap<String, String>()

    init {
        // 1) como o Last.fm recebeu (edições + regras automáticas)
        for (t in tracks) {
            val m = LastFmScrobbler.metaFor(t)
            trackByKey.putIfAbsent(ScrobbleEdits.key(m.artist, m.track), t)
            artistByKey.putIfAbsent(ScrobbleEdits.key(m.artist, ""), t.artist)
            t.artPath?.takeIf { it.isNotBlank() }?.let { p ->
                artistCoverByKey.putIfAbsent(ScrobbleEdits.key(m.artist, ""), p)
                if (m.album.isNotBlank()) {
                    albumArtByKey.putIfAbsent(ScrobbleEdits.key(m.artist, m.album), p)
                    if (m.albumArtist.isNotBlank()) albumArtByKey.putIfAbsent(ScrobbleEdits.key(m.albumArtist, m.album), p)
                }
            }
            if (m.album.isNotBlank()) {
                val shown = t.albumArtist.ifBlank { t.artist } to t.album
                albumByKey.putIfAbsent(ScrobbleEdits.key(m.artist, m.album), shown)
                if (m.albumArtist.isNotBlank()) {
                    albumByKey.putIfAbsent(ScrobbleEdits.key(m.albumArtist, m.album), shown)
                }
            }
        }
        // 2) tags cruas (scrobbles antigos, feitos antes de qualquer edição)
        for (t in tracks) {
            trackByKey.putIfAbsent(ScrobbleEdits.key(t.artist, t.title), t)
            artistByKey.putIfAbsent(ScrobbleEdits.key(t.artist, ""), t.artist)
            t.artPath?.takeIf { it.isNotBlank() }?.let { p ->
                artistCoverByKey.putIfAbsent(ScrobbleEdits.key(t.artist, ""), p)
                if (t.album.isNotBlank()) albumArtByKey.putIfAbsent(ScrobbleEdits.key(t.artist, t.album), p)
            }
            if (t.album.isNotBlank()) {
                albumByKey.putIfAbsent(
                    ScrobbleEdits.key(t.artist, t.album),
                    t.albumArtist.ifBlank { t.artist } to t.album
                )
            }
        }
    }

    /** (artista, título) originais. */
    fun track(artist: String, title: String): Pair<String, String> {
        trackByKey[ScrobbleEdits.key(artist, title)]?.let { return it.artist to it.title }
        ScrobbleEdits.reverseManual(artist, title)?.let { return it.artist to it.track }
        return artist to title
    }

    fun artist(name: String): String = artistByKey[ScrobbleEdits.key(name, "")] ?: name

    // ----- capas locais (arquivos do app) -----

    /** Capa local da faixa (aceita o nome do Last.fm ou o original). */
    fun trackArt(artist: String, title: String): String? =
        trackByKey[ScrobbleEdits.key(artist, title)]?.artPath?.takeIf { it.isNotBlank() }

    fun albumArt(artist: String, album: String): String? =
        albumArtByKey[ScrobbleEdits.key(artist, album)]

    /** Foto salva do artista; se não houver, a capa de uma faixa dele. */
    fun artistArt(name: String): String? =
        ArtistMetaStore.cachedPhotoPath(artist(name)) ?: artistCoverByKey[ScrobbleEdits.key(name, "")]

    /** (artista, álbum) originais. */
    fun album(artist: String, album: String): Pair<String, String> =
        albumByKey[ScrobbleEdits.key(artist, album)] ?: (artist to album)
}

private fun mergeTracks(items: List<ChartItem>, r: OriginalResolver?): List<ChartItem> {
    if (r == null) return items
    val map = LinkedHashMap<String, ChartItem>()
    for (i in items) {
        val (a, t) = r.track(i.artist, i.name)
        val k = ScrobbleEdits.key(a, t)
        val cur = map[k]
        map[k] = if (cur == null) ChartItem(t, a, i.plays) else cur.copy(plays = cur.plays + i.plays)
    }
    return map.values.sortedByDescending { it.plays }
}

private fun mergeArtists(items: List<ChartItem>, r: OriginalResolver?): List<ChartItem> {
    if (r == null) return items
    val map = LinkedHashMap<String, ChartItem>()
    for (i in items) {
        val a = r.artist(i.name)
        val k = ScrobbleEdits.key(a, "")
        val cur = map[k]
        map[k] = if (cur == null) ChartItem(a, "", i.plays) else cur.copy(plays = cur.plays + i.plays)
    }
    return map.values.sortedByDescending { it.plays }
}

private fun mergeAlbums(items: List<ChartItem>, r: OriginalResolver?): List<ChartItem> {
    if (r == null) return items
    val map = LinkedHashMap<String, ChartItem>()
    for (i in items) {
        val (a, al) = r.album(i.artist, i.name)
        val k = ScrobbleEdits.key(a, al)
        val cur = map[k]
        map[k] = if (cur == null) ChartItem(al, a, i.plays) else cur.copy(plays = cur.plays + i.plays)
    }
    return map.values.sortedByDescending { it.plays }
}

// ---------------------------------------------------------------------------------------------
// Períodos e buckets do gráfico
// ---------------------------------------------------------------------------------------------

private data class BucketSpec(val label: String, val fromSec: Long, val toSec: Long, val current: Boolean)

private data class RangeSpec(val title: String, val buckets: List<BucketSpec>) {
    val fromSec: Long get() = buckets.first().fromSec
    val toSec: Long get() = buckets.last().toSec
}

private data class RawData(
    val counts: List<Int>,
    val tracks: List<ChartItem>,
    val artists: List<ChartItem>,
    val albums: List<ChartItem>
)

private val weekdayNames = arrayOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb")
private val monthNames = arrayOf("Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez")

private fun startOfDay(c: Calendar) {
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
}

private fun buildRange(range: LfRange, offset: Int, nowMs: Long): RangeSpec {
    val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
    startOfDay(cal)
    fun sec(c: Calendar) = c.timeInMillis / 1000L
    fun isNow(fromSec: Long, toSec: Long) = nowMs / 1000L in fromSec until toSec

    return when (range) {
        // Plays por dia da semana
        LfRange.WEEK -> {
            cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
            cal.add(Calendar.WEEK_OF_YEAR, offset)
            val fmt = SimpleDateFormat("dd/MM", ptBr)
            val startLabel = fmt.format(cal.time)
            val buckets = ArrayList<BucketSpec>(7)
            repeat(7) {
                val from = sec(cal)
                val label = weekdayNames[cal.get(Calendar.DAY_OF_WEEK) - 1]
                cal.add(Calendar.DAY_OF_YEAR, 1)
                val to = sec(cal)
                buckets.add(BucketSpec(label, from, to, isNow(from, to)))
            }
            val endLabel = fmt.format(java.util.Date((buckets.last().toSec - 1) * 1000L))
            val title = when (offset) {
                0 -> "Esta semana ($startLabel – $endLabel)"
                -1 -> "Semana passada ($startLabel – $endLabel)"
                else -> "$startLabel – $endLabel"
            }
            RangeSpec(title, buckets)
        }
        // Plays por semana do mês (blocos de 7 dias a partir do dia 1)
        LfRange.MONTH -> {
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.add(Calendar.MONTH, offset)
            val daysIn = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            val buckets = ArrayList<BucketSpec>(5)
            var d = 1
            while (d <= daysIn) {
                val endDay = minOf(d + 6, daysIn)
                val a = cal.clone() as Calendar
                a.set(Calendar.DAY_OF_MONTH, d)
                val b = cal.clone() as Calendar
                b.set(Calendar.DAY_OF_MONTH, endDay)
                b.add(Calendar.DAY_OF_MONTH, 1)
                val from = sec(a)
                val to = sec(b)
                buckets.add(BucketSpec("$d–$endDay", from, to, isNow(from, to)))
                d += 7
            }
            val title = SimpleDateFormat("MMMM 'de' yyyy", ptBr).format(cal.time)
                .replaceFirstChar { it.titlecase(ptBr) }
            RangeSpec(title, buckets)
        }
        // Plays por mês do ano
        LfRange.YEAR -> {
            cal.set(Calendar.DAY_OF_YEAR, 1)
            cal.add(Calendar.YEAR, offset)
            val year = cal.get(Calendar.YEAR)
            val buckets = ArrayList<BucketSpec>(12)
            for (m in 0 until 12) {
                val a = cal.clone() as Calendar
                a.set(Calendar.MONTH, m)
                a.set(Calendar.DAY_OF_MONTH, 1)
                val b = a.clone() as Calendar
                b.add(Calendar.MONTH, 1)
                val from = sec(a)
                val to = sec(b)
                buckets.add(BucketSpec(monthNames[m], from, to, isNow(from, to)))
            }
            RangeSpec(year.toString(), buckets)
        }
    }
}

/** Carrega contagens por bucket + rankings do intervalo. Poucas chamadas em paralelo (limite do Last.fm). */
private suspend fun loadRaw(user: String, spec: RangeSpec, sem: Semaphore): RawData? = coroutineScope {
    val nowSec = System.currentTimeMillis() / 1000L
    val countJobs = spec.buckets.map { b ->
        async {
            if (b.fromSec > nowSec) 0
            else sem.withPermit { LastFmClient.getScrobbleCount(user, b.fromSec, b.toSec) }
        }
    }
    val tracksJob = async { sem.withPermit { LastFmClient.getChart(user, ChartKind.TRACKS, spec.fromSec, spec.toSec) } }
    val artistsJob = async { sem.withPermit { LastFmClient.getChart(user, ChartKind.ARTISTS, spec.fromSec, spec.toSec) } }
    val albumsJob = async { sem.withPermit { LastFmClient.getChart(user, ChartKind.ALBUMS, spec.fromSec, spec.toSec) } }
    val counts = countJobs.awaitAll()
    val tracks = tracksJob.await()
    val artists = artistsJob.await()
    val albums = albumsJob.await()
    if (counts.any { it == null } || tracks == null || artists == null || albums == null) {
        null
    } else {
        RawData(counts.map { it ?: 0 }, tracks, artists, albums)
    }
}

// ---------------------------------------------------------------------------------------------
// Tela
// ---------------------------------------------------------------------------------------------

@Composable
fun LastFmDashboardScreen(
    tracks: List<Track>,
    appColors: AppColors,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val user by LastFmScrobbler.username.collectAsStateWithLifecycle()
    val editsVersion by ScrobbleEdits.version.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(LfTab.SCROBBLES) }
    var refreshKey by remember { mutableIntStateOf(0) }

    // Índice dos nomes originais; refeito quando a biblioteca ou as edições mudam
    val resolver by produceState<OriginalResolver?>(null, tracks, editsVersion) {
        value = withContext(Dispatchers.Default) { OriginalResolver(tracks) }
    }

    val backdrop = rememberGraphicsLayer()
    val navPadding = 110.dp

    Box(
        Modifier
            .fillMaxSize()
            // impede toques de "vazar" para a tela de baixo
            .clickable(enabled = true, indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            ) { }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    if (GlassConfig.quality == GlassQuality.LIGHT) {
                        drawContent()
                    } else {
                        backdrop.record { this@drawWithContent.drawContent() }
                        drawLayer(backdrop)
                    }
                }
                .background(rememberFloatingGradient(appColors))
        ) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                // Cabeçalho
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Last.fm", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        user?.let {
                            Text("@$it", fontSize = 12.sp, color = Color.White.copy(0.6f), maxLines = 1)
                        }
                    }
                    IconButton(onClick = { refreshKey++ }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Atualizar", tint = appColors.accent)
                    }
                }

                val u = user
                if (u.isNullOrBlank()) {
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "Entre na sua conta do Last.fm em Configurações → Scrobble para ver seus dados aqui.",
                            color = Color.White.copy(0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
                        when (tab) {
                            LfTab.SCROBBLES -> ScrobbleList(u, false, refreshKey, resolver, appColors, navPadding)
                            LfTab.LOVED -> ScrobbleList(u, true, refreshKey, resolver, appColors, navPadding)
                            LfTab.DATA -> DataTab(u, refreshKey, resolver, appColors, navPadding)
                            LfTab.PROFILE -> ProfileTab(u, refreshKey, resolver, appColors, navPadding)
                        }
                    }
                }
            }
        }

        // Mesma barra de vidro da tela inicial, com as abas do dashboard
        LiquidGlassTabBar(
            items = lfTabs,
            selected = tab,
            onSelected = { tab = it },
            backdrop = backdrop,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Histórico de scrobbles / Favoritos (lista paginada)
// ---------------------------------------------------------------------------------------------

@Composable
private fun ScrobbleList(
    user: String,
    loved: Boolean,
    refreshKey: Int,
    resolver: OriginalResolver?,
    appColors: AppColors,
    bottomPadding: Dp
) {
    val items = remember(user, loved, refreshKey) { mutableStateListOf<ScrobbleItem>() }
    var page by remember(user, loved, refreshKey) { mutableIntStateOf(0) }
    var totalPages by remember(user, loved, refreshKey) { mutableIntStateOf(1) }
    var total by remember(user, loved, refreshKey) { mutableIntStateOf(0) }
    var loading by remember(user, loved, refreshKey) { mutableStateOf(false) }
    var failed by remember(user, loved, refreshKey) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun loadNext() {
        if (loading || page >= totalPages) return
        loading = true
        failed = false
        scope.launch {
            val r = if (loved) LastFmClient.getLovedPage(user, page + 1)
            else LastFmClient.getScrobblePage(user, page + 1)
            if (r == null) {
                failed = true
            } else {
                items.addAll(r.items)
                page = r.page
                totalPages = r.totalPages
                total = r.total
            }
            loading = false
        }
    }

    LaunchedEffect(user, loved, refreshKey) { loadNext() }

    Column(Modifier.fillMaxSize()) {
        if (total > 0) {
            Text(
                if (loved) "$total faixas favoritas" else "$total scrobbles",
                color = Color.White.copy(0.6f),
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
            )
        }
        when {
            items.isEmpty() && loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = appColors.accent)
            }
            items.isEmpty() && failed -> CenterMessage("Não foi possível carregar. Verifique a conexão e toque em atualizar.")
            items.isEmpty() -> CenterMessage(if (loved) "Nenhuma faixa favorita ainda." else "Nenhum scrobble ainda.")
            else -> LazyColumn(
                contentPadding = PaddingValues(bottom = bottomPadding),
                modifier = Modifier.fillMaxSize()
            ) {
                items(items.size) { i ->
                    val it = items[i]
                    val (artist, title) = remember(it, resolver) {
                        resolver?.track(it.artist, it.title) ?: (it.artist to it.title)
                    }
                    val album = remember(it, resolver) {
                        it.album?.let { al -> resolver?.album(it.artist, al)?.second ?: al }
                    }
                    ListRow(
                        title = title,
                        subtitle = listOfNotNull(artist.takeIf { a -> a.isNotBlank() }, album).joinToString(" · "),
                        trailing = when {
                            it.nowPlaying -> "ouvindo agora"
                            it.dateMs != null -> formatWhen(it.dateMs)
                            else -> ""
                        },
                        trailingColor = if (it.nowPlaying) appColors.accent else Color.White.copy(0.5f),
                        art = { TrackArt(resolver, it.artist, it.title, it.album) }
                    )
                }
                if (page < totalPages) {
                    item {
                        LaunchedEffect(page) { loadNext() }
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            if (failed) {
                                Text(
                                    "Falhou — toque para tentar de novo",
                                    color = appColors.accent,
                                    modifier = Modifier.clickable { loadNext() }.padding(8.dp)
                                )
                            } else {
                                CircularProgressIndicator(Modifier.size(24.dp), color = appColors.accent, strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Dados (semanal / mensal / anual) + gráfico de plays
// ---------------------------------------------------------------------------------------------

@Composable
private fun DataTab(
    user: String,
    refreshKey: Int,
    resolver: OriginalResolver?,
    appColors: AppColors,
    bottomPadding: Dp
) {
    var range by remember { mutableStateOf(LfRange.WEEK) }
    var offset by remember { mutableIntStateOf(0) }
    var kind by remember { mutableStateOf(ChartKind.TRACKS) }
    val sem = remember { Semaphore(4) }
    val cache = remember(user) { HashMap<String, RawData>() }

    val spec = remember(range, offset, refreshKey) { buildRange(range, offset, System.currentTimeMillis()) }
    val cacheKey = "${range.name}|$offset|$refreshKey"
    var raw by remember(cacheKey) { mutableStateOf(cache[cacheKey]) }
    var failed by remember(cacheKey) { mutableStateOf(false) }

    LaunchedEffect(cacheKey, user) {
        if (raw == null) {
            failed = false
            val r = loadRaw(user, spec, sem)
            if (r != null) {
                cache[cacheKey] = r
                raw = r
            } else {
                failed = true
            }
        }
    }

    val data = raw
    val shown by remember(data, kind, resolver) {
        derivedStateOf {
            when {
                data == null -> emptyList()
                kind == ChartKind.TRACKS -> mergeTracks(data.tracks, resolver)
                kind == ChartKind.ARTISTS -> mergeArtists(data.artists, resolver)
                else -> mergeAlbums(data.albums, resolver)
            }.take(50)
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = bottomPadding),
        modifier = Modifier.fillMaxSize()
    ) {
        // Semanal / Mensal / Anual
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LfRange.entries.forEach { r ->
                    Chip(r.label, range == r, appColors) { range = r; offset = 0 }
                }
            }
        }
        // Navegação entre períodos
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { offset-- }) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = "Período anterior", tint = Color.White)
                }
                Text(
                    spec.title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { if (offset < 0) offset++ }, enabled = offset < 0) {
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = "Próximo período",
                        tint = if (offset < 0) Color.White else Color.White.copy(0.25f)
                    )
                }
            }
        }
        when {
            data == null && !failed -> item {
                Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = appColors.accent)
                }
            }
            data == null -> item {
                Text(
                    "Não foi possível carregar os dados. Toque em atualizar para tentar de novo.",
                    color = Color.White.copy(0.7f),
                    modifier = Modifier.padding(16.dp)
                )
            }
            else -> {
                // Total + gráfico de plays
                item {
                    val total = data.counts.sum()
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(0.10f))
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("$total", color = appColors.accent, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (total == 1) "play" else "plays",
                                color = Color.White.copy(0.7f),
                                modifier = Modifier.padding(bottom = 5.dp)
                            )
                        }
                        Text(
                            when (range) {
                                LfRange.WEEK -> "por dia da semana"
                                LfRange.MONTH -> "por semana do mês (dias 1–7, 8–14, …)"
                                LfRange.YEAR -> "por mês do ano"
                            },
                            color = Color.White.copy(0.5f),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        PlaysBarChart(spec.buckets, data.counts, appColors)
                    }
                    Spacer(Modifier.height(14.dp))
                }
                // Músicas / Artistas / Álbuns
                item {
                    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            ChartKind.TRACKS to "Músicas",
                            ChartKind.ARTISTS to "Artistas",
                            ChartKind.ALBUMS to "Álbuns"
                        ).forEach { (k, label) -> Chip(label, kind == k, appColors) { kind = k } }
                    }
                }
                if (shown.isEmpty()) {
                    item {
                        Text("Nada tocado neste período.", color = Color.White.copy(0.6f), modifier = Modifier.padding(8.dp))
                    }
                } else {
                    items(shown.size) { i ->
                        val c = shown[i]
                        ListRow(
                            rank = i + 1,
                            title = c.name,
                            subtitle = c.artist,
                            trailing = if (c.plays == 1) "1 play" else "${c.plays} plays",
                            trailingColor = appColors.accent,
                            art = {
                                when (kind) {
                                    ChartKind.TRACKS -> TrackArt(resolver, c.artist, c.name, null)
                                    ChartKind.ARTISTS -> ArtistArt(resolver, c.name)
                                    ChartKind.ALBUMS -> AlbumArt(resolver, c.artist, c.name)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaysBarChart(buckets: List<BucketSpec>, counts: List<Int>, appColors: AppColors) {
    val max = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    val barMax = 120.dp
    val compact = buckets.size > 7
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(barMax + 22.dp), verticalAlignment = Alignment.Bottom) {
            buckets.forEachIndexed { i, b ->
                val count = counts.getOrElse(i) { 0 }
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        "$count",
                        color = if (b.current) appColors.accent else Color.White.copy(0.85f),
                        fontSize = if (compact) 9.sp else 11.sp,
                        fontWeight = if (b.current) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(Modifier.height(3.dp))
                    val h = if (count == 0) 3.dp else (barMax * (count.toFloat() / max)).coerceAtLeast(6.dp)
                    Box(
                        Modifier
                            .width(if (compact) 14.dp else 24.dp)
                            .height(h)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (count == 0) Brush.verticalGradient(listOf(Color.White.copy(0.25f), Color.White.copy(0.25f)))
                                else Brush.verticalGradient(
                                    listOf(
                                        appColors.accent.copy(alpha = if (b.current) 1f else 0.85f),
                                        appColors.accent.copy(alpha = if (b.current) 0.55f else 0.40f)
                                    )
                                )
                            )
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            buckets.forEach { b ->
                Text(
                    b.label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = if (b.current) appColors.accent else Color.White.copy(0.6f),
                    fontSize = if (compact) 10.sp else 11.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Perfil
// ---------------------------------------------------------------------------------------------

@Composable
private fun ProfileTab(
    user: String,
    refreshKey: Int,
    resolver: OriginalResolver?,
    appColors: AppColors,
    bottomPadding: Dp
) {
    val context = LocalContext.current
    var failed by remember(user, refreshKey) { mutableStateOf(false) }
    val info by produceState<LastFmClient.UserInfo?>(null, user, refreshKey) {
        val r = LastFmClient.getUserInfo(user)
        failed = r == null
        value = r
    }
    val topArtists by produceState<List<ChartItem>?>(null, user, refreshKey) {
        value = LastFmClient.getChart(user, ChartKind.ARTISTS, null, null, 5)
    }
    val topTracks by produceState<List<ChartItem>?>(null, user, refreshKey) {
        value = LastFmClient.getChart(user, ChartKind.TRACKS, null, null, 5)
    }

    val i = info
    if (i == null) {
        if (failed) CenterMessage("Não foi possível carregar o perfil.")
        else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = appColors.accent)
        }
        return
    }

    val fmtDate = remember { SimpleDateFormat("dd/MM/yyyy", ptBr) }
    LazyColumn(contentPadding = PaddingValues(bottom = bottomPadding), modifier = Modifier.fillMaxSize()) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(0.10f))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(72.dp).clip(CircleShape).background(appColors.accent.copy(0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (i.imageUrl != null) {
                        AsyncImage(
                            model = i.imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(Icons.Rounded.Person, contentDescription = null, tint = appColors.accent, modifier = Modifier.size(36.dp))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(i.realName ?: i.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("@${i.name}", color = Color.White.copy(0.6f), fontSize = 13.sp)
                    val extra = listOfNotNull(
                        i.country,
                        i.registeredAtMs?.let { "desde ${fmtDate.format(java.util.Date(it))}" }
                    ).joinToString(" · ")
                    if (extra.isNotBlank()) Text(extra, color = Color.White.copy(0.5f), fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("Scrobbles", i.playCount, appColors, Modifier.weight(1f))
                    StatCard("Favoritas", i.lovedCount, appColors, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("Artistas", i.artistCount, appColors, Modifier.weight(1f))
                    StatCard("Álbuns", i.albumCount, appColors, Modifier.weight(1f))
                    StatCard("Músicas", i.trackCount, appColors, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        val ta = topArtists?.let { mergeArtists(it, resolver) }.orEmpty()
        if (ta.isNotEmpty()) {
            item { SectionTitle("Artistas mais ouvidos (geral)") }
            items(ta.size) { n ->
                ListRow(rank = n + 1, title = ta[n].name, subtitle = "", trailing = "${ta[n].plays} plays", trailingColor = appColors.accent,
                    art = { ArtistArt(resolver, ta[n].name) })
            }
        }
        val tt = topTracks?.let { mergeTracks(it, resolver) }.orEmpty()
        if (tt.isNotEmpty()) {
            item { SectionTitle("Músicas mais ouvidas (geral)") }
            items(tt.size) { n ->
                ListRow(rank = n + 1, title = tt[n].name, subtitle = tt[n].artist, trailing = "${tt[n].plays} plays", trailingColor = appColors.accent,
                    art = { TrackArt(resolver, tt[n].artist, tt[n].name, null) })
            }
        }
        i.url?.let { url ->
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Abrir perfil no Last.fm",
                    color = appColors.accent,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(appColors.accent.copy(0.15f))
                        .clickable {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            } catch (_: Exception) { }
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Pedaços reutilizáveis
// ---------------------------------------------------------------------------------------------

@Composable
private fun Chip(label: String, selected: Boolean, appColors: AppColors, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Color.Black else Color.White.copy(0.85f),
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) appColors.accent else Color.White.copy(0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun ListRow(
    title: String,
    subtitle: String,
    trailing: String,
    trailingColor: Color,
    rank: Int? = null,
    art: (@Composable () -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (rank != null) {
            Text("$rank", color = Color.White.copy(0.45f), fontSize = 13.sp, modifier = Modifier.width(28.dp))
        }
        if (art != null) {
            art()
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = Color.White.copy(0.55f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(trailing, color = trailingColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Cache em memória das imagens da web (vazio = já tentou e não achou). */
private object WebArt {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val sem = Semaphore(2)
    // Imagem "estrelinha" padrão do Last.fm (não é uma foto de verdade)
    private const val LASTFM_PLACEHOLDER = "2a96cbd8b46e442fc41c2b86b821562f"

    suspend fun get(key: String, loader: suspend () -> String?): String? {
        cache[key]?.let { return it.ifEmpty { null } }
        val r = sem.withPermit {
            withContext(Dispatchers.IO) { try { loader() } catch (_: Exception) { null } }
        }?.takeUnless { it.contains(LASTFM_PLACEHOLDER) }
        cache[key] = r ?: ""
        return r
    }
}

/** Mostra a imagem local (se existir) e só então busca na web. [webKey] null = não buscar. */
@Composable
private fun LfArt(
    local: String?,
    webKey: String?,
    round: Boolean,
    size: Dp = 44.dp,
    webLoader: suspend () -> String?
) {
    val localOk = remember(local) { !local.isNullOrBlank() && java.io.File(local).exists() }
    val web by produceState<String?>(null, localOk, webKey) {
        value = if (localOk || webKey == null) null else WebArt.get(webKey, webLoader)
    }
    ArtImage(
        path = if (localOk) local else web,
        size = size,
        shape = if (round) CircleShape else RoundedCornerShape(10.dp)
    )
}

@Composable
private fun TrackArt(r: OriginalResolver?, artist: String, title: String, album: String?) {
    val local = remember(r, artist, title, album) {
        r?.trackArt(artist, title) ?: album?.takeIf { it.isNotBlank() }?.let { r?.albumArt(artist, it) }
    }
    val hasAlbum = !album.isNullOrBlank()
    val key = when {
        r == null || artist.isBlank() -> null
        hasAlbum -> "al|$artist|$album"
        else -> "ar|$artist"
    }
    LfArt(local, key, round = false) {
        if (hasAlbum) LastFmClient.getAlbumInfo(album!!, artist)?.imageUrl
        else ArtistImageResolver.resolve(artist).imageUrl
    }
}

@Composable
private fun ArtistArt(r: OriginalResolver?, name: String) {
    val local = remember(r, name) { r?.artistArt(name) }
    LfArt(local, if (r == null || name.isBlank()) null else "ar|$name", round = true) {
        ArtistImageResolver.resolve(name).imageUrl
    }
}

@Composable
private fun AlbumArt(r: OriginalResolver?, artist: String, album: String) {
    val local = remember(r, artist, album) { r?.albumArt(artist, album) }
    LfArt(local, if (r == null || album.isBlank()) null else "al|$artist|$album", round = false) {
        LastFmClient.getAlbumInfo(album, artist)?.imageUrl
    }
}

@Composable
private fun StatCard(label: String, value: Long, appColors: AppColors, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(0.10f))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            java.text.NumberFormat.getIntegerInstance(ptBr).format(value),
            color = appColors.accent,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(label, color = Color.White.copy(0.65f), fontSize = 12.sp)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = Color.White,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp, start = 4.dp)
    )
}

@Composable
private fun CenterMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White.copy(0.65f), textAlign = TextAlign.Center)
    }
}

private fun formatWhen(ms: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - ms
    return when {
        diff < 60_000L -> "agora"
        diff < 3_600_000L -> "há ${diff / 60_000L} min"
        diff < 86_400_000L -> "há ${diff / 3_600_000L} h"
        else -> {
            val sameYear = Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.YEAR) ==
                Calendar.getInstance().get(Calendar.YEAR)
            SimpleDateFormat(if (sameYear) "dd/MM HH:mm" else "dd/MM/yy HH:mm", ptBr).format(java.util.Date(ms))
        }
    }
}
