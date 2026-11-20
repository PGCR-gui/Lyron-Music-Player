package com.tolete.player.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.tolete.player.PlaybackService
import com.tolete.player.data.Album
import com.tolete.player.data.Artist
import com.tolete.player.data.ArtistMetaStore
import com.tolete.player.data.AlphaSort
import com.tolete.player.data.FavoritesBridge
import com.tolete.player.data.FavoritesFile
import com.tolete.player.data.Genre
import com.tolete.player.data.LibraryCache
import com.tolete.player.data.LastFmClient
import com.tolete.player.data.LastFmScrobbler
import com.tolete.player.data.LyricLine
import com.tolete.player.data.LyricsExtractor
import com.tolete.player.data.LyricsEmbedder
import com.tolete.player.data.LibrarySettings
import com.tolete.player.data.LrcLibClient
import com.tolete.player.data.LyricsSearch
import com.tolete.player.data.LyricsSearchResult
import com.tolete.player.data.Playlist
import com.tolete.player.data.SourceFolder
import com.tolete.player.data.MusicScanner
import com.tolete.player.data.TagSplitter
import com.tolete.player.data.TagWriter
import com.tolete.player.data.Track
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    // ---------- Abas e filtro ----------
    enum class Tab { HOME, ALL, ARTISTS, ALBUMS, GENRES, PLAYLISTS }

    /** Grupo aberto (ex.: um artista específico). null = mostrando a lista de grupos. */
    data class Filter(val tab: Tab, val key: String)

    private val scanner = MusicScanner(application)
    private val cache = LibraryCache(application)
    private val prefs = application.getSharedPreferences("tolete", Context.MODE_PRIVATE)
    private val playlistsFile = File(com.tolete.player.data.LyronStorage.playlistsDir, "playlists.json")
    private val favoritesFile = com.tolete.player.data.LyronStorage.favoritesFile

    /** Biblioteca crua (pode ter a mesma faixa duas vezes se pastas se sobrepõem). */
    private val _library = MutableStateFlow<List<Track>>(emptyList())

    /** Biblioteca sem duplicatas: é o que o resto do app enxerga. */
    val tracks: StateFlow<List<Track>> = _library
        .map { list -> list.distinctBy { it.id } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanCount = MutableStateFlow(0)
    val scanCount: StateFlow<Int> = _scanCount.asStateFlow()

    // ---------- Pastas da biblioteca ----------
    private val _treeUris = MutableStateFlow<List<String>>(emptyList())

    val sourceFolders: StateFlow<List<SourceFolder>> = combine(_treeUris, _library) { uris, lib ->
        val counts = lib.groupingBy { it.sourceTree }.eachCount()
        uris.map { describeTree(it, counts[it] ?: 0) }
            .sortedBy { it.path.lowercase() }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _currentTab = MutableStateFlow(Tab.HOME)
    val currentTab: StateFlow<Tab> = _currentTab.asStateFlow()

    private val _filter = MutableStateFlow<Filter?>(null)
    val filter: StateFlow<Filter?> = _filter.asStateFlow()

    // ---------- Busca ----------
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    private val _activeSearchQuery = MutableStateFlow("")
    private var searchDebounceJob: Job? = null
    private val _searchPending = MutableStateFlow(false)
    val searchPending: StateFlow<Boolean> = _searchPending.asStateFlow()
    private val _searchHistory = MutableStateFlow(
        runCatching {
            val arr = JSONArray(prefs.getString(KEY_SEARCH_HISTORY, "[]"))
            List(arr.length()) { arr.optString(it) }.filter { it.isNotBlank() }.take(20)
        }.getOrDefault(emptyList())
    )
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    fun setSearchQuery(q: String) {
        _searchQuery.value = q
        searchDebounceJob?.cancel()
        val term = q.trim()
        if (term.isBlank()) {
            _activeSearchQuery.value = ""
            _searchPending.value = false
            return
        }
        _activeSearchQuery.value = ""
        _searchPending.value = true
        searchDebounceJob = viewModelScope.launch {
            delay(1_000)
            if (_searchQuery.value.trim() == term && _isSearching.value) {
                // Só ativa a pesquisa. O termo NÃO vai para o histórico aqui:
                // ele só é salvo quando o usuário toca em um resultado (commitSearchToHistory).
                _activeSearchQuery.value = term
            }
            _searchPending.value = false
        }
    }

    /**
     * Salva a pesquisa atual no histórico. Chamado apenas quando o usuário toca em um
     * resultado (faixa, artista, álbum ou gênero), não a cada pesquisa digitada.
     */
    fun commitSearchToHistory() {
        val term = _activeSearchQuery.value.trim().ifBlank { _searchQuery.value.trim() }
        if (term.isNotBlank()) addSearchHistory(term)
    }

    private fun addSearchHistory(term: String) {
        val updated = (listOf(term) + _searchHistory.value.filterNot { it.equals(term, ignoreCase = true) })
            .take(20)
        _searchHistory.value = updated
        val json = JSONArray().apply { updated.forEach { put(it) } }.toString()
        prefs.edit().putString(KEY_SEARCH_HISTORY, json).apply()
    }

    fun clearSearchHistory() {
        _searchHistory.value = emptyList()
        prefs.edit().remove(KEY_SEARCH_HISTORY).apply()
    }

    fun removeSearchHistoryItem(term: String) {
        val updated = _searchHistory.value.filterNot { it.equals(term, ignoreCase = true) }
        if (updated.size == _searchHistory.value.size) return
        _searchHistory.value = updated
        if (updated.isEmpty()) {
            prefs.edit().remove(KEY_SEARCH_HISTORY).apply()
        } else {
            val json = JSONArray().apply { updated.forEach { put(it) } }.toString()
            prefs.edit().putString(KEY_SEARCH_HISTORY, json).apply()
        }
    }

    fun openSearch() {
        searchDebounceJob?.cancel()
        _isSearching.value = true
        _searchQuery.value = ""
        _activeSearchQuery.value = ""
        _searchPending.value = false
    }

    fun closeSearch() {
        searchDebounceJob?.cancel()
        _isSearching.value = false
        _searchQuery.value = ""
        _activeSearchQuery.value = ""
        _searchPending.value = false
    }

    val searchResults: StateFlow<List<Track>> = combine(tracks, _activeSearchQuery) { list, q ->
        if (q.isBlank()) emptyList()
        else {
            val lower = q.lowercase()
            list.filter {
                it.title.lowercase().contains(lower) ||
                    it.artist.lowercase().contains(lower) ||
                    it.album.lowercase().contains(lower)
            }.sortedBy { it.title.lowercase() }
        }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setTab(tab: Tab) {
        _currentTab.value = tab
        _filter.value = null
        closeSearch()
    }

    fun openGroup(key: String) {
        _filter.value = Filter(_currentTab.value, key)
    }

    fun clearFilter() {
        _filter.value = null
    }

    /** Abre o filtro de artista a partir da tela de reprodução. */
    fun openArtist(name: String) {
        _currentTab.value = Tab.ARTISTS
        _filter.value = Filter(Tab.ARTISTS, name)
        closeSearch()
    }

    /** Abre o filtro de álbum a partir da tela de reprodução. */
    fun openAlbum(name: String) {
        _currentTab.value = Tab.ALBUMS
        _filter.value = Filter(Tab.ALBUMS, name)
        closeSearch()
    }

    // ---------- Playlists ----------
    private val _playlists = MutableStateFlow<List<Playlist>>(listOf(Playlist.favorites()))
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    fun isFavorite(trackId: String): Boolean =
        _playlists.value.firstOrNull { it.isFavorites }?.trackIds?.contains(trackId) == true

    fun toggleFavorite(trackId: String) {
        val list = _playlists.value.toMutableList()
        val idx = list.indexOfFirst { it.isFavorites }
        if (idx < 0) return
        val fav = list[idx]
        val ids = fav.trackIds.toMutableList()
        if (trackId in ids) ids.remove(trackId) else ids.add(0, trackId)
        list[idx] = fav.copy(trackIds = ids)
        _playlists.value = list
        persistPlaylists()
        FavoritesBridge.notifyChanged() // atualiza o ícone da notificação
    }

    fun createPlaylist(name: String, initialTrackIds: List<String> = emptyList()): String {
        val id = UUID.randomUUID().toString()
        val pl = Playlist(id, name.trim().ifBlank { "Nova playlist" }, initialTrackIds)
        _playlists.value = _playlists.value + pl
        persistPlaylists()
        return id
    }

    fun addToPlaylist(playlistId: String, trackId: String) {
        val list = _playlists.value.toMutableList()
        val idx = list.indexOfFirst { it.id == playlistId }
        if (idx < 0) return
        val pl = list[idx]
        if (trackId in pl.trackIds) return
        list[idx] = pl.copy(trackIds = pl.trackIds + trackId)
        _playlists.value = list
        persistPlaylists()
    }

    fun reorderPlaylistTracks(playlistId: String, orderedTrackIds: List<String>) {
        _playlists.value = _playlists.value.map { pl ->
            if (pl.id != playlistId) pl
            else pl.copy(trackIds = orderedTrackIds)
        }
        persistPlaylists()
    }

    fun removeFromPlaylist(playlistId: String, trackId: String) {
        val list = _playlists.value.toMutableList()
        val idx = list.indexOfFirst { it.id == playlistId }
        if (idx < 0) return
        val pl = list[idx]
        list[idx] = pl.copy(trackIds = pl.trackIds - trackId)
        _playlists.value = list
        persistPlaylists()
    }

    /** Reordena as playlists (Favoritos fica sempre primeiro). [orderedIds] = ids das demais, na nova ordem. */
    fun reorderPlaylists(orderedIds: List<String>) {
        val current = _playlists.value
        val fav = current.filter { it.isFavorites }
        val byId = current.filter { !it.isFavorites }.associateBy { it.id }
        val ordered = orderedIds.mapNotNull { byId[it] }
        val missing = byId.values.filter { it.id !in orderedIds.toSet() }
        _playlists.value = fav + ordered + missing
        persistPlaylists()
    }

    fun deletePlaylist(playlistId: String) {
        if (playlistId == Playlist.FAVORITES_ID) return
        _playlists.value = _playlists.value.filter { it.id != playlistId }
        persistPlaylists()
        if (_filter.value?.tab == Tab.PLAYLISTS && _filter.value?.key == playlistId) {
            _filter.value = null
        }
    }

    /**
     * Remove faixas da biblioteca e tenta apagar os arquivos do armazenamento.
     * Funciona melhor com caminhos absolutos legados; URIs SAF podem falhar sem
     * permissão de escrita persistente.
     */
    /**
     * Remove faixas só da biblioteca/playlists (após exclusão física confirmada).
     */
    fun removeTracksFromLibrary(ids: Set<String>) {
        if (ids.isEmpty()) return
        _library.value = _library.value.filter { it.id !in ids }
        _playlists.value = _playlists.value.map { pl ->
            pl.copy(trackIds = pl.trackIds.filter { it !in ids })
        }
        persistPlaylists()
        viewModelScope.launch(Dispatchers.IO) {
            scanMutex.withLock { cache.save(_library.value) }
        }
    }

    /**
     * Tenta apagar permanentemente os arquivos.
     * Com MANAGE_EXTERNAL_STORAGE (acesso a todos os arquivos), apaga via File no caminho
     * real e, em seguida, limpa a entrada do MediaStore — sem diálogo do sistema.
     * Retorna URIs que ainda precisam de confirmação do sistema (fallback raro).
     */
    fun deleteTracksPermanent(tracks: List<Track>): List<android.net.Uri> {
        if (tracks.isEmpty()) return emptyList()
        val app = getApplication<Application>()
        val cr = app.contentResolver
        val needConfirm = mutableListOf<android.net.Uri>()
        val deletedIds = mutableSetOf<String>()
        val hasAllFiles = try {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.os.Environment.isExternalStorageManager()
            } else {
                true
            }
        } catch (_: Exception) { false }

        for (t in tracks) {
            var gone = false
            val realPath = t.path.takeIf { it.startsWith("/") }
                ?: t.uri.path?.takeIf { it.startsWith("/") }

            // 1) Com acesso total: apaga pelo caminho de arquivo (preferido)
            if (!gone && hasAllFiles && !realPath.isNullOrBlank()) {
                try {
                    val f = java.io.File(realPath)
                    if (f.exists()) {
                        gone = f.delete()
                    }
                    val base = realPath.substringBeforeLast('.')
                    java.io.File("$base.lrc").takeIf { it.exists() }?.delete()
                    java.io.File("$base.ttml").takeIf { it.exists() }?.delete()
                } catch (_: Exception) { }
            }

            // 2) file:// explícito
            if (!gone) {
                try {
                    val path = t.uri.path
                    if (t.uri.scheme == "file" && path != null) {
                        val f = java.io.File(path)
                        if (f.exists()) gone = f.delete()
                    }
                } catch (_: Exception) { }
            }

            // 3) DocumentsContract (legado SAF) — só se ainda houver URI de documento
            if (!gone) {
                try {
                    if (android.provider.DocumentsContract.isDocumentUri(app, t.uri)) {
                        gone = android.provider.DocumentsContract.deleteDocument(cr, t.uri)
                    }
                } catch (_: Exception) { }
            }

            // 4) ContentResolver / MediaStore
            if (!gone) {
                try {
                    val n = cr.delete(t.uri, null, null)
                    gone = n > 0
                } catch (e: SecurityException) {
                    if (!hasAllFiles) needConfirm.add(t.uri)
                } catch (_: Exception) { }
            }

            // Com all-files, se o arquivo sumiu do disco mas a URI MediaStore ficou, tenta limpar
            if (gone && hasAllFiles && t.uri.authority?.contains("media") == true) {
                try { cr.delete(t.uri, null, null) } catch (_: Exception) { }
            }

            if (!gone && !hasAllFiles && t.uri.authority?.contains("media") == true) {
                needConfirm.add(t.uri)
            }

            // Se all-files e o arquivo não existe mais no disco, considera excluído
            if (!gone && hasAllFiles && !realPath.isNullOrBlank()) {
                val f = java.io.File(realPath)
                if (!f.exists()) gone = true
            }

            if (gone) deletedIds.add(t.id)
        }

        if (deletedIds.isNotEmpty()) {
            removeTracksFromLibrary(deletedIds)
        }
        return needConfirm.distinct()
    }

    /** Compat: exclusão permanente (sem UI de confirmação — preferir fluxo da Activity). */
    fun deleteTracks(tracks: List<Track>) {
        deleteTracksPermanent(tracks)
    }

    fun renamePlaylist(playlistId: String, newName: String) {
        if (playlistId == Playlist.FAVORITES_ID) return
        val list = _playlists.value.toMutableList()
        val idx = list.indexOfFirst { it.id == playlistId }
        if (idx < 0) return
        list[idx] = list[idx].copy(name = newName.trim().ifBlank { list[idx].name })
        _playlists.value = list
        persistPlaylists()
    }

    /** Exporta playlist como texto .m3u (caminhos absolutos quando disponíveis). */
    fun exportPlaylistM3u(playlistId: String): String? {
        val pl = _playlists.value.firstOrNull { it.id == playlistId } ?: return null
        val byId = tracks.value.associateBy { it.id }
        val sb = StringBuilder("#EXTM3U\n")
        for (id in pl.trackIds) {
            val t = byId[id] ?: continue
            sb.append("#EXTINF:").append(t.duration / 1000).append(",").append(t.artist).append(" - ").append(t.title).append('\n')
            // Prefer path if it looks like a real filesystem path; otherwise use content URI
            if (t.path.startsWith("/")) sb.append(t.path) else sb.append(t.uri.toString())
            sb.append('\n')
        }
        return sb.toString()
    }

    /** Resultado da importação de .m3u: [id] é null quando nenhuma faixa casou. */
    data class M3uImportResult(val id: String?, val matched: Int, val total: Int)

    /**
     * Importa .m3u/.m3u8. Aceita caminhos absolutos, RELATIVOS (ex.: "Músicas/Alternative/x.m4a"),
     * file://, content:// (com %XX), barras invertidas do Windows, acentos em NFC/NFD e maiúsc./minúsc.
     * O casamento com a biblioteca é feito em etapas, da mais precisa à mais flexível:
     *  1) fim do caminho/URI da faixa == caminho da linha (mais segmentos em comum = melhor)
     *  2) mesmo nome de arquivo
     *  3) mesmo nome de arquivo ignorando a extensão (ex.: .flac na lista, .m4a na biblioteca)
     *  4) "Artista - Título" da linha #EXTINF contra as tags da faixa
     */
    suspend fun importPlaylistM3u(name: String, content: String): M3uImportResult {
        val lib = tracks.value
        val match = withContext(Dispatchers.Default) {
            val entries = parseM3u(content)
            if (entries.isEmpty()) return@withContext M3uMatchResult(emptyList(), 0, 0)
            if (lib.isEmpty()) return@withContext M3uMatchResult(emptyList(), 0, entries.size)

            class Key(val t: Track, val locs: List<String>, val base: String, val baseNoExt: String)

            fun locationsOf(t: Track): List<String> {
                val out = ArrayList<String>(3)
                if (t.path.isNotBlank()) out += normM3uPath(t.path)
                // URIs SAF/MediaStore guardam o caminho relativo codificado: ".../document/primary%3AMúsicas%2FX.m4a"
                val u = try { Uri.decode(t.uri.toString()) } catch (_: Exception) { t.uri.toString() }
                out += normM3uPath(u.substringAfter("/document/", u).substringAfter("primary:"))
                out += normM3uPath(u)
                return out.filter { it.isNotEmpty() }.distinct()
            }

            val keys = lib.map { t ->
                val locs = locationsOf(t)
                val base = locs.firstOrNull { it.contains('/') }?.substringAfterLast('/')
                    ?: locs.firstOrNull()?.substringAfterLast('/') ?: ""
                val fileBase = normM3uPath(t.path).substringAfterLast('/').ifBlank { base }
                Key(t, locs, fileBase, fileBase.substringBeforeLast('.', fileBase))
            }
            val byBase = keys.groupBy { it.base }
            val byBaseNoExt = keys.groupBy { it.baseNoExt }
            val byArtistTitle = keys.groupBy { normM3uText(it.t.artist) + "|" + normM3uText(it.t.title) }
            val byTitleOnly = keys.groupBy { normM3uText(it.t.title) }

            fun commonSuffixSegments(a: String, b: String): Int {
                val x = a.split('/').filter { it.isNotEmpty() }.asReversed()
                val y = b.split('/').filter { it.isNotEmpty() }.asReversed()
                var n = 0
                while (n < x.size && n < y.size && x[n] == y[n]) n++
                return n
            }

            fun best(cands: List<Key>, wanted: String): Key? {
                if (cands.isEmpty()) return null
                if (cands.size == 1) return cands[0]
                return cands.maxByOrNull { k -> k.locs.maxOf { commonSuffixSegments(it, wanted) } }
            }

            val ids = LinkedHashSet<String>()
            var matched = 0
            for (e in entries) {
                val wanted = normM3uPath(e.path)
                val wantedBase = wanted.substringAfterLast('/')
                val wantedNoExt = wantedBase.substringBeforeLast('.', wantedBase)

                var hit: Key? = null
                // 1) caminho completo/sufixo
                if (wanted.contains('/')) {
                    hit = keys.filter { k -> k.locs.any { it == wanted || it.endsWith("/$wanted") || wanted.endsWith("/$it") } }
                        .let { best(it, wanted) }
                }
                // 2) nome do arquivo
                if (hit == null) hit = best(byBase[wantedBase].orEmpty(), wanted)
                // 3) nome sem extensão
                if (hit == null) hit = best(byBaseNoExt[wantedNoExt].orEmpty(), wanted)
                // 4) Artista - Título do #EXTINF (ou do nome do arquivo "Artista - Título.ext")
                if (hit == null) {
                    val label = e.label.ifBlank { wantedNoExt }
                    val parts = label.split(" - ", limit = 2)
                    if (parts.size == 2) {
                        hit = byArtistTitle[normM3uText(parts[0]) + "|" + normM3uText(parts[1])]?.firstOrNull()
                            ?: byTitleOnly[normM3uText(parts[1])]?.takeIf { it.size == 1 }?.firstOrNull()
                    } else {
                        hit = byTitleOnly[normM3uText(label)]?.takeIf { it.size == 1 }?.firstOrNull()
                    }
                }
                if (hit != null) {
                    matched++
                    ids.add(hit.t.id)
                }
            }
            M3uMatchResult(ids.toList(), matched, entries.size)
        }
        if (match.total == 0 || match.ids.isEmpty()) {
            return M3uImportResult(null, 0, match.total)
        }
        val id = createPlaylist(name.ifBlank { "Importada" }, match.ids)
        return M3uImportResult(id, match.matched, match.total)
    }

    private data class M3uMatchResult(val ids: List<String>, val matched: Int, val total: Int)

    private data class M3uEntry(val path: String, val label: String)

    private fun parseM3u(raw: String): List<M3uEntry> {
        val out = ArrayList<M3uEntry>()
        var label = ""
        for (l in raw.removePrefix("\uFEFF").lineSequence()) {
            val line = l.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#")) {
                if (line.startsWith("#EXTINF", ignoreCase = true)) {
                    label = line.substringAfter(',', "").trim()
                }
                continue
            }
            out += M3uEntry(line, label)
            label = ""
        }
        return out
    }

    /** Normaliza caminho: decodifica %XX, tira file://, barras, ./, minúsculas e Unicode NFC. */
    private fun normM3uPath(p: String): String {
        var s = p.trim()
        if (s.contains('%')) s = try { Uri.decode(s) } catch (_: Exception) { s }
        s = s.removePrefix("file://").replace('\\', '/')
        s = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFC).lowercase()
        while (s.startsWith("./")) s = s.removePrefix("./")
        return s.trimStart('/').replace(Regex("/+"), "/")
    }

    private fun normM3uText(t: String): String =
        java.text.Normalizer.normalize(t, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()

    // Salvamos sempre o ESTADO MAIS RECENTE, uma gravação por vez. Antes, várias gravações simultâneas
    // (ex.: adicionar várias faixas) disputavam o mesmo arquivo temporário e podiam apagar playlists.json.
    private fun persistPlaylists() {
        viewModelScope.launch(Dispatchers.IO) { writePlaylistsNow() }
    }

    private fun writePlaylistsNow() {
        synchronized(FavoritesFile.lock) {
            try {
                val arr = JSONArray()
                for (pl in _playlists.value) {
                    arr.put(JSONObject().apply {
                        put("id", pl.id)
                        put("name", pl.name)
                        put("isFavorites", pl.isFavorites)
                        put("trackIds", JSONArray(pl.trackIds))
                    })
                }
                // Favoritos em arquivo próprio
                val favIds = _playlists.value.firstOrNull { it.isFavorites }?.trackIds.orEmpty()
                val favJson = JSONObject().put("v", 1).put("trackIds", JSONArray(favIds)).toString()
                val favTmp = File(favoritesFile.parentFile, "favorites.json.tmp")
                favTmp.writeText(favJson)
                if (!favTmp.renameTo(favoritesFile)) {
                    favoritesFile.writeText(favJson)
                    favTmp.delete()
                }
                // Demais playlists (sem duplicar favoritos track list como única fonte)
                val json = JSONObject().put("v", 1).put("playlists", arr).toString()
                val tmp = File(playlistsFile.parentFile, "playlists.json.tmp")
                tmp.writeText(json)
                if (playlistsFile.exists()) {
                    try { playlistsFile.copyTo(File(playlistsFile.parentFile, "playlists.json.bak"), overwrite = true) } catch (_: Exception) {}
                }
                if (!tmp.renameTo(playlistsFile)) {
                    playlistsFile.writeText(json)
                    tmp.delete()
                }
            } catch (_: Exception) { }
        }
    }

    private fun parsePlaylistsFile(f: File): List<Playlist> {
        val root = JSONObject(f.readText())
        val arr = root.getJSONArray("playlists")
        val loaded = mutableListOf<Playlist>()
        var hasFav = false
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val idsArr = o.getJSONArray("trackIds")
            val ids = List(idsArr.length()) { idsArr.getString(it) }
            val isFav = o.optBoolean("isFavorites") || o.getString("id") == Playlist.FAVORITES_ID
            if (isFav) hasFav = true
            loaded.add(
                Playlist(
                    id = o.getString("id"),
                    name = if (isFav) "Favoritos" else o.getString("name"),
                    trackIds = ids,
                    isFavorites = isFav
                )
            )
        }
        if (!hasFav) loaded.add(0, Playlist.favorites())
        loaded.sortBy { if (it.isFavorites) 0 else 1 }
        return loaded
    }

    /** Carrega na hora (arquivo pequeno) para nenhuma gravação ocorrer antes e sobrescrever o que existe. */
    private fun loadPlaylists() {
        val bak = File(playlistsFile.parentFile, "playlists.json.bak")
        val loaded: List<Playlist>? = synchronized(FavoritesFile.lock) {
            listOf(playlistsFile, bak).firstNotNullOfOrNull { f ->
                if (!f.exists()) null else try { parsePlaylistsFile(f) } catch (_: Exception) { null }
            }
        }
        var list = loaded?.toMutableList() ?: _playlists.value.toMutableList()
        try {
            if (favoritesFile.exists()) {
                val o = org.json.JSONObject(favoritesFile.readText())
                val arr = o.optJSONArray("trackIds")
                if (arr != null) {
                    val ids = (0 until arr.length()).map { arr.getString(it) }
                    val idx = list.indexOfFirst { it.isFavorites }
                    val fav = Playlist.favorites(ids)
                    if (idx >= 0) list[idx] = fav else list.add(0, fav)
                }
            }
        } catch (_: Exception) {}
        if (list.none { it.isFavorites }) list.add(0, Playlist.favorites())
        _playlists.value = list
        FavoritesBridge.notifyChanged()
    }

    // ---------- Biblioteca: separar artistas / gêneros ----------
    private val _librarySettings = MutableStateFlow(
        LibrarySettings(
            splitArtists = prefs.getBoolean(KEY_SPLIT_ARTISTS, false),
            artistSeparators = prefs.getString(KEY_ARTIST_SEPARATORS, null) ?: "; /",
            artistExceptions = prefs.getString(KEY_ARTIST_EXCEPTIONS, null) ?: "",
            splitGenres = prefs.getBoolean(KEY_SPLIT_GENRES, false),
            genreSeparators = prefs.getString(KEY_GENRE_SEPARATORS, null) ?: "; /",
            ignoredFolders = prefs.getString(KEY_IGNORED_FOLDERS, null) ?: "",
            minDurationSec = prefs.getInt(KEY_MIN_DURATION_SEC, 0).coerceIn(0, 50),
            firstLetterOrder = prefs.getBoolean(KEY_FIRST_LETTER_ORDER, false),
            artistAutoFetchMode = prefs.getString(KEY_ARTIST_AUTO_FETCH_MODE, "ON")
                ?.takeIf { it in setOf("ON", "WIFI_ONLY", "OFF") } ?: "ON"
        )
    )
    val librarySettings: StateFlow<LibrarySettings> = _librarySettings.asStateFlow()

    fun setSplitArtists(enabled: Boolean) {
        _librarySettings.update { it.copy(splitArtists = enabled) }
        prefs.edit().putBoolean(KEY_SPLIT_ARTISTS, enabled).apply()
    }

    fun setArtistSeparators(value: String) {
        _librarySettings.update { it.copy(artistSeparators = value) }
        prefs.edit().putString(KEY_ARTIST_SEPARATORS, value).apply()
    }

    fun setArtistExceptions(value: String) {
        _librarySettings.update { it.copy(artistExceptions = value) }
        prefs.edit().putString(KEY_ARTIST_EXCEPTIONS, value).apply()
    }

    fun setSplitGenres(enabled: Boolean) {
        _librarySettings.update { it.copy(splitGenres = enabled) }
        prefs.edit().putBoolean(KEY_SPLIT_GENRES, enabled).apply()
    }

    fun setGenreSeparators(value: String) {
        _librarySettings.update { it.copy(genreSeparators = value) }
        prefs.edit().putString(KEY_GENRE_SEPARATORS, value).apply()
    }

    fun setIgnoredFolders(value: String) {
        _librarySettings.update { it.copy(ignoredFolders = value) }
        prefs.edit().putString(KEY_IGNORED_FOLDERS, value).apply()
    }

    fun setMinDurationSec(sec: Int) {
        val v = sec.coerceIn(0, 50)
        _librarySettings.update { it.copy(minDurationSec = v) }
        prefs.edit().putInt(KEY_MIN_DURATION_SEC, v).apply()
    }
    /**
     * Ordem alfabética pela primeira letra (Configurações › Biblioteca). Afeta as listas de
     * músicas, artistas, álbuns e gêneros e o índice lateral A–Z.
     */
    fun setFirstLetterOrder(enabled: Boolean) {
        _librarySettings.update { it.copy(firstLetterOrder = enabled) }
        prefs.edit().putBoolean(KEY_FIRST_LETTER_ORDER, enabled).apply()
    }

    fun setArtistAutoFetchMode(mode: String) {
        val safeMode = mode.takeIf { it in setOf("ON", "WIFI_ONLY", "OFF") } ?: "ON"
        _librarySettings.update { it.copy(artistAutoFetchMode = safeMode) }
        prefs.edit().putString(KEY_ARTIST_AUTO_FETCH_MODE, safeMode).apply()
    }

    /** Aplica blacklist de pastas e duração mínima sobre a lista escaneada. */
    private fun filterLibrary(tracks: List<Track>): List<Track> {
        val st = _librarySettings.value
        val ignored = st.ignoredFolders
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val minMs = if (st.minDurationSec > 0) st.minDurationSec * 1000L else 0L
        val trackBl = _trackBlacklist.value
        return tracks.filter { t ->
            if (minMs > 0 && t.duration > 0 && t.duration < minMs) return@filter false
            if (ignored.isNotEmpty()) {
                val path = t.path
                if (ignored.any { ign -> path.contains(ign, ignoreCase = true) }) return@filter false
            }
            if (trackBl.isNotEmpty() && (t.id in trackBl || t.path in trackBl)) return@filter false
            true
        }
    }

    // ---------- Acesso amplo à biblioteca ----------
    private val _allFilesMode = MutableStateFlow(prefs.getBoolean(KEY_ALL_FILES, false))
    val allFilesMode: StateFlow<Boolean> = _allFilesMode.asStateFlow()

    fun setAllFilesMode(enabled: Boolean) {
        _allFilesMode.value = enabled
        prefs.edit().putBoolean(KEY_ALL_FILES, enabled).apply()
        if (enabled) {
            _library.value = _library.value.filter { it.sourceTree != "media_store" }
            refreshAllFiles()
        } else {
            _library.value = _library.value.filter { it.sourceTree != "all_files" && it.sourceTree != "media_store" }
            viewModelScope.launch { scanMutex.withLock { cache.save(_library.value) } }
            if (_treeUris.value.isNotEmpty()) refreshLibrary()
        }
    }

    fun refreshAllFiles() {
        if (!_allFilesMode.value) return
        launchScan { onTrack ->
            val existing = _library.value.associateBy { it.id }
            val found = scanner.scanAllMedia(
                onTrack = onTrack,
                existingById = existing,
                sourceTree = "all_files",
                scanUnindexedDsf = true
            )
            val fromFolders = _library.value.filter { it.sourceTree != "all_files" }
            _library.value = fromFolders + found
        }
    }

    // ---------- Player: busca automática de letras ----------
    private val _autoFetchLyrics = MutableStateFlow(prefs.getBoolean(KEY_AUTO_LYRICS, false))
    val autoFetchLyrics: StateFlow<Boolean> = _autoFetchLyrics.asStateFlow()

    fun setAutoFetchLyrics(enabled: Boolean) {
        _autoFetchLyrics.value = enabled
        prefs.edit().putBoolean(KEY_AUTO_LYRICS, enabled).apply()
    }

    private val _autoLyricsSaveTarget = MutableStateFlow(
        runCatching { LyricsEmbedder.Target.valueOf(prefs.getString(KEY_AUTO_LYRICS_TARGET, null).orEmpty()) }
            .getOrDefault(LyricsEmbedder.Target.LRC_FILE)
    )
    val autoLyricsSaveTarget: StateFlow<LyricsEmbedder.Target> = _autoLyricsSaveTarget.asStateFlow()
    fun setAutoLyricsSaveTarget(target: LyricsEmbedder.Target) {
        _autoLyricsSaveTarget.value = target
        prefs.edit().putString(KEY_AUTO_LYRICS_TARGET, target.name).apply()
    }

    private val _preferNeteaseRomanization = MutableStateFlow(
        prefs.getBoolean(KEY_PREFER_NETEASE_ROMANIZATION, false)
    )
    val preferNeteaseRomanization: StateFlow<Boolean> = _preferNeteaseRomanization.asStateFlow()
    fun setPreferNeteaseRomanization(enabled: Boolean) {
        _preferNeteaseRomanization.value = enabled
        prefs.edit().putBoolean(KEY_PREFER_NETEASE_ROMANIZATION, enabled).apply()
    }

    /** Ordem de prioridade de tipo de letra para auto-fetch (ids de LyricsType). */
    private val defaultLyricsTypeOrder = listOf("WORD", "SYNCED", "PLAIN")
    private val _lyricsTypeOrder = MutableStateFlow(
        prefs.getString(KEY_LYRICS_TYPE_ORDER, null)
            ?.split('|')
            ?.filter { it.isNotBlank() }
            ?.ifEmpty { null }
            ?: defaultLyricsTypeOrder
    )
    val lyricsTypeOrder: StateFlow<List<String>> = _lyricsTypeOrder.asStateFlow()
    fun setLyricsTypeOrder(order: List<String>) {
        val cleaned = order.filter { it in defaultLyricsTypeOrder }.ifEmpty { defaultLyricsTypeOrder }
        _lyricsTypeOrder.value = cleaned
        prefs.edit().putString(KEY_LYRICS_TYPE_ORDER, cleaned.joinToString("|")).apply()
    }
    fun moveLyricsType(from: Int, to: Int) {
        val list = _lyricsTypeOrder.value.toMutableList()
        if (from !in list.indices || to !in list.indices) return
        val item = list.removeAt(from)
        list.add(to, item)
        setLyricsTypeOrder(list)
    }

    /** Ordem de servidores/fontes para auto-fetch (ids de LyricsSource). */
    private val defaultLyricsSourceOrder = listOf(
        "LYRON_REPOSITORY", "LRCLIB", "QQ_MUSIC", "NETEASE", "KUGOU"
    )
    private val _lyricsSourceOrder = MutableStateFlow(
        prefs.getString(KEY_LYRICS_SOURCE_ORDER, null)
            ?.split('|')
            ?.filter { it.isNotBlank() }
            ?.ifEmpty { null }
            ?: defaultLyricsSourceOrder
    )
    val lyricsSourceOrder: StateFlow<List<String>> = _lyricsSourceOrder.asStateFlow()
    fun setLyricsSourceOrder(order: List<String>) {
        val cleaned = order.filter { it in defaultLyricsSourceOrder }.ifEmpty { defaultLyricsSourceOrder }
        _lyricsSourceOrder.value = cleaned
        prefs.edit().putString(KEY_LYRICS_SOURCE_ORDER, cleaned.joinToString("|")).apply()
    }
    fun moveLyricsSource(from: Int, to: Int) {
        val list = _lyricsSourceOrder.value.toMutableList()
        if (from !in list.indices || to !in list.indices) return
        val item = list.removeAt(from)
        list.add(to, item)
        setLyricsSourceOrder(list)
    }

    /** Atraso (ms) entre aparições dos itens nos resultados da pesquisa. */
    private val _searchStaggerMs = MutableStateFlow(
        prefs.getInt(KEY_SEARCH_STAGGER_MS, 50).coerceIn(0, 200)
    )
    val searchStaggerMs: StateFlow<Int> = _searchStaggerMs.asStateFlow()
    fun setSearchStaggerMs(ms: Int) {
        val v = ms.coerceIn(0, 200)
        _searchStaggerMs.value = v
        prefs.edit().putInt(KEY_SEARCH_STAGGER_MS, v).apply()
    }

    private val _lyricsPopupEnabled = MutableStateFlow(prefs.getBoolean(KEY_LYRICS_POPUP, false))
    val lyricsPopupEnabled: StateFlow<Boolean> = _lyricsPopupEnabled.asStateFlow()

    private val popupPrefsListener =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_LYRICS_POPUP) {
                val enabled = prefs.getBoolean(KEY_LYRICS_POPUP, false)
                if (_lyricsPopupEnabled.value != enabled) {
                    _lyricsPopupEnabled.value = enabled
                }
            }
        }

    fun setLyricsPopupEnabled(enabled: Boolean) {
        _lyricsPopupEnabled.value = enabled
        prefs.edit().putBoolean(KEY_LYRICS_POPUP, enabled).apply()
        // A notificação de controle é mantida pelo PlaybackService enquanto o popup estiver ativo.
        // Aqui só garantimos limpeza imediata ao desligar pela UI.
        if (!enabled) {
            try {
                com.tolete.player.LyricsPopupOverlay.hide()
                com.tolete.player.LyricsPopupNotifier.cancel(getApplication())
            } catch (_: Throwable) { }
        }
    }

    /** Modo de aleatório: ABSOLUTE (shuffle do player) ou QUEUE (fila embaralhada fixa). */
    enum class ShuffleType { ABSOLUTE, QUEUE }

    private val _shuffleType = MutableStateFlow(
        try {
            ShuffleType.valueOf(prefs.getString(KEY_SHUFFLE_TYPE, ShuffleType.ABSOLUTE.name)!!)
        } catch (_: Exception) { ShuffleType.ABSOLUTE }
    )
    val shuffleType: StateFlow<ShuffleType> = _shuffleType.asStateFlow()

    fun setShuffleType(type: ShuffleType) {
        _shuffleType.value = type
        prefs.edit().putString(KEY_SHUFFLE_TYPE, type.name).apply()
        // se o aleatório estiver ligado, reaplica o modo novo
        if (_shuffle.value) {
            applyShuffleMode(true)
        }
    }

    private val _bitPerfect = MutableStateFlow(prefs.getBoolean(KEY_BIT_PERFECT, false))
    val bitPerfect: StateFlow<Boolean> = _bitPerfect.asStateFlow()

    /** Liga/desliga o bit-perfect (USB, Android 14+). O serviço lê a preferência ao ser criado. */
    fun setBitPerfect(enabled: Boolean, applyNow: Boolean = false) {
        val changed = _bitPerfect.value != enabled
        _bitPerfect.value = enabled
        prefs.edit().putBoolean(KEY_BIT_PERFECT, enabled).apply()
        // Só faz diferença em Fidelidade de Som, que oferece Bit-perfect.
        if (applyNow && changed && _audioOutput.value == AudioOutputBackend.FIDELITY) {
            applyAudioSettingsNow()
        }
    }

    /** Modo imersivo na tela Now Playing (esconde barras de status e navegação). */
    private val _immersiveNowPlaying = MutableStateFlow(prefs.getBoolean(KEY_IMMERSIVE_NOW_PLAYING, true))
    val immersiveNowPlaying: StateFlow<Boolean> = _immersiveNowPlaying.asStateFlow()

    fun setImmersiveNowPlaying(enabled: Boolean) {
        _immersiveNowPlaying.value = enabled
        prefs.edit().putBoolean(KEY_IMMERSIVE_NOW_PLAYING, enabled).apply()
    }

    // ---------- Visualizador .milk (presets do usuário) ----------
    private val _milkPresets = MutableStateFlow(
        com.tolete.player.visualizer.MilkPresetStore.list(application)
    )
    val milkPresets: StateFlow<List<String>> = _milkPresets.asStateFlow()

    private val _milkIntervalSec = MutableStateFlow(prefs.getInt(KEY_MILK_INTERVAL, 30))
    /** Segundos entre trocas automáticas de preset (0 = não trocar). */
    val milkIntervalSec: StateFlow<Int> = _milkIntervalSec.asStateFlow()
    fun setMilkIntervalSec(sec: Int) {
        _milkIntervalSec.value = sec
        prefs.edit().putInt(KEY_MILK_INTERVAL, sec).apply()
    }

    /** TIME respeita o intervalo; TRACK troca somente quando muda a música. */
    private val _milkSwitchMode = MutableStateFlow(
        prefs.getString(KEY_MILK_SWITCH_MODE, null)?.uppercase()?.takeIf { it == "TIME" || it == "TRACK" }
            ?: if (prefs.getBoolean(KEY_MILK_PRESET_ON_SONG, true)) "TRACK" else "TIME"
    )
    val milkSwitchMode: StateFlow<String> = _milkSwitchMode.asStateFlow()
    fun setMilkSwitchMode(mode: String) {
        val safe = if (mode.equals("TRACK", ignoreCase = true)) "TRACK" else "TIME"
        _milkSwitchMode.value = safe
        prefs.edit().putString(KEY_MILK_SWITCH_MODE, safe).apply()
    }

    /** Ordem aplicada quando a política selecionada pede uma troca automática. */
    private val _milkPresetOrder = MutableStateFlow(
        prefs.getString(KEY_MILK_PRESET_ORDER, "RANDOM")?.uppercase()?.takeIf { it == "RANDOM" || it == "SEQUENTIAL" }
            ?: "RANDOM"
    )
    val milkPresetOrder: StateFlow<String> = _milkPresetOrder.asStateFlow()
    fun setMilkPresetOrder(order: String) {
        val safe = if (order.equals("SEQUENTIAL", ignoreCase = true)) "SEQUENTIAL" else "RANDOM"
        _milkPresetOrder.value = safe
        prefs.edit().putString(KEY_MILK_PRESET_ORDER, safe).apply()
    }

    private val _milkFavoritesOnly = MutableStateFlow(prefs.getBoolean(KEY_MILK_FAVORITES_ONLY, false))
    val milkFavoritesOnly: StateFlow<Boolean> = _milkFavoritesOnly.asStateFlow()
    fun setMilkFavoritesOnly(enabled: Boolean) {
        _milkFavoritesOnly.value = enabled
        prefs.edit().putBoolean(KEY_MILK_FAVORITES_ONLY, enabled).apply()
    }

    /** Copia .milk / texturas / .zip escolhidos pelo usuário para a pasta do app. */
    fun importMilkFiles(uris: List<Uri>, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val added = withContext(Dispatchers.IO) {
                com.tolete.player.visualizer.MilkPresetStore.import(ctx, uris)
            }
            _milkPresets.value = withContext(Dispatchers.IO) {
                com.tolete.player.visualizer.MilkPresetStore.list(ctx)
            }
            onDone(added)
        }
    }

    fun clearMilkPresets() {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            withContext(Dispatchers.IO) { com.tolete.player.visualizer.MilkPresetStore.clear(ctx) }
            _milkPresets.value = emptyList()
        }
    }

    fun deleteMilkPreset(path: String) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                com.tolete.player.visualizer.MilkPresetStore.delete(path)
            }
            if (ok) {
                val ctx = getApplication<android.app.Application>()
                _milkPresets.value = withContext(Dispatchers.IO) {
                    com.tolete.player.visualizer.MilkPresetStore.list(ctx)
                }
            }
        }
    }


    /** DEFAULT = Android decide; FIDELITY preserva a fonte com conversão de compatibilidade. */
    enum class AudioOutputBackend { DEFAULT, FIDELITY }

    private fun audioOutputFromPrefs(): AudioOutputBackend {
        val saved = prefs.getString(KEY_AUDIO_OUTPUT, com.tolete.player.data.AudioOutputPrefs.DEFAULT)
        val normalized = com.tolete.player.data.AudioOutputPrefs.normalize(saved)
        if (saved != normalized) prefs.edit().putString(KEY_AUDIO_OUTPUT, normalized).apply()
        return if (normalized == com.tolete.player.data.AudioOutputPrefs.FIDELITY) {
            AudioOutputBackend.FIDELITY
        } else AudioOutputBackend.DEFAULT
    }

    private val _audioOutput = MutableStateFlow(audioOutputFromPrefs())
    val audioOutput: StateFlow<AudioOutputBackend> = _audioOutput.asStateFlow()

    /** Salva a escolha e recria o player para ela valer já (a fila e a posição são restauradas). */
    fun setAudioOutput(backend: AudioOutputBackend, applyNow: Boolean = false) {
        val changed = _audioOutput.value != backend
        _audioOutput.value = backend
        prefs.edit().putString(KEY_AUDIO_OUTPUT, backend.name).apply()
        if (applyNow && changed) applyAudioSettingsNow()
    }

    /**
     * Taxa de amostragem preferida da saída (Hz).
     * 0 = automática (sistema). Pedido ao pipeline; o Android ainda pode reamostrar.
     */
    private val _preferredSampleRate = MutableStateFlow(
        prefs.getInt(KEY_PREFERRED_SAMPLE_RATE, 0).let { if (it < 0) 0 else it }
    )
    val preferredSampleRate: StateFlow<Int> = _preferredSampleRate.asStateFlow()

    fun setPreferredSampleRate(hz: Int) {
        val v = if (hz <= 0) 0 else hz
        _preferredSampleRate.value = v
        prefs.edit().putInt(KEY_PREFERRED_SAMPLE_RATE, v).apply()
    }

    /**
     * Recria o PlaybackService/ExoPlayer para aplicar backend, taxa e rota USB agora.
     * Restaura fila, posição e play/pause.
     */
    fun applyAudioSettingsNow() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val c = controller
            val items = try {
                if (c != null && c.mediaItemCount > 0) {
                    (0 until c.mediaItemCount).map { c.getMediaItemAt(it) }
                } else emptyList()
            } catch (_: Exception) { emptyList() }
            val idx = try { c?.currentMediaItemIndex ?: 0 } catch (_: Exception) { 0 }
            val pos = try { c?.currentPosition ?: 0L } catch (_: Exception) { 0L }
            val shouldPlay = try { c?.playWhenReady == true } catch (_: Exception) { false }
            try { c?.pause() } catch (_: Exception) {}
            try { c?.stop() } catch (_: Exception) {}
            try {
                c?.release()
            } catch (_: Exception) {}
            controller = null
            try {
                controllerFuture?.cancel(true)
            } catch (_: Exception) {}
            controllerFuture = null
            try {
                app.stopService(android.content.Intent(app, com.tolete.player.PlaybackService::class.java))
            } catch (_: Exception) {}
            kotlinx.coroutines.delay(450)
            connectController()
            var waits = 0
            while (controller == null && waits < 25) {
                kotlinx.coroutines.delay(120)
                waits++
            }
            val c2 = controller
            if (c2 != null && items.isNotEmpty()) {
                try {
                    c2.setMediaItems(items, idx.coerceAtLeast(0), pos.coerceAtLeast(0L))
                    c2.prepare()
                    if (shouldPlay) c2.play() else c2.pause()
                    syncFromController()
                } catch (e: Exception) {
                    android.util.Log.e("MusicVM", "applyAudioSettingsNow restore failed", e)
                }
            }
            val out = com.tolete.player.data.AudioOutputInfo.findPreferredOutput(app)
            val modeLabel = when (_audioOutput.value) {
                AudioOutputBackend.FIDELITY -> "Fidelidade de Som"
                AudioOutputBackend.DEFAULT -> "padrão do sistema"
            }
            val msg = when {
                _audioOutput.value == AudioOutputBackend.DEFAULT -> "Saída de áudio: $modeLabel"
                out == null -> "Saída de áudio: $modeLabel · nenhum DAC USB ou fone com fio detectado"
                else -> "Saída de áudio: $modeLabel · ${com.tolete.player.data.AudioOutputInfo.describeDevice(out)}"
            }
            android.widget.Toast.makeText(app, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun artistSplitterOf(st: LibrarySettings) =
        TagSplitter(
            if (st.splitArtists) st.artistSeparators else "",
            if (st.splitArtists) st.artistExceptions else ""
        )

    /** Nomes de artista da faixa (vários se "separar artistas" estiver ligado e a tag tiver separadores). */
    fun artistNamesOf(track: Track): List<String> {
        return artistSplitterOf(_librarySettings.value).split(track.artist)
    }

    // ---------- Agrupamentos (derivados de tracks) ----------
    val artists: StateFlow<List<Artist>> = combine(tracks, _librarySettings) { list, st ->
        val splitter = artistSplitterOf(st)
        list.flatMap { t -> splitter.split(t.artist).map { name -> name to t } }
            .groupBy({ it.first }, { it.second })
            .map { (name, l) -> Artist(name, l.size, l.firstNotNullOfOrNull { it.artPath }, l.sumOf { it.duration }) }
            .sortedBy { AlphaSort.key(it.name, st.firstLetterOrder) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Compositores: mesma lógica de divisão de tags dos artistas (separadores/exceções). */
    val composers: StateFlow<List<Artist>> = combine(tracks, _librarySettings) { list, st ->
        val splitter = artistSplitterOf(st)
        list.flatMap { t ->
            splitter.split(t.composer)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { name -> name to t }
        }
            .groupBy({ it.first }, { it.second })
            .map { (name, l) -> Artist(name, l.size, l.firstNotNullOfOrNull { it.artPath }, l.sumOf { it.duration }) }
            .sortedBy { AlphaSort.key(it.name, st.firstLetterOrder) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun openComposer(name: String) {
        _currentTab.value = Tab.ARTISTS
        _filter.value = Filter(Tab.ARTISTS, "composer::$name")
    }

    private fun albumIdentity(track: Track): String {
        val name = track.album.trim().lowercase()
        val albumArtist = track.albumArtist.trim().lowercase()
        if (track.albumKey.startsWith("media:")) return "${track.albumKey}|$name"
        if (albumArtist.isNotBlank()) return "tag:$name|$albumArtist"
        if (track.albumKey.isNotBlank()) return "${track.albumKey}|$name"
        val parent = track.path.substringBeforeLast('/', "").trim().lowercase()
        return "folder:$parent|$name"
    }

    val albums: StateFlow<List<Album>> = combine(tracks, _librarySettings) { list, st ->
        // Não usar o artista da faixa como artista do álbum: em coletâneas cada música
        // pode ter um artista diferente, embora pertença ao mesmo álbum/pasta.
        list.groupBy(::albumIdentity)
            .map { (key, l) ->
                val first = l.first()
                val displayAlbum = first.album.trim().ifBlank { first.album }
                val albumArtists = l.map { it.albumArtist.trim() }
                    .filter { it.isNotBlank() }
                    .distinctBy { it.lowercase() }
                val trackArtists = l.map { it.artist.trim() }
                    .filter { it.isNotBlank() }
                    .distinctBy { it.lowercase() }
                val displayArtist = when {
                    albumArtists.size == 1 -> albumArtists.first()
                    albumArtists.size > 1 || trackArtists.size > 1 -> "Vários artistas"
                    else -> trackArtists.firstOrNull().orEmpty()
                }
                Album(
                    name = displayAlbum,
                    artist = displayArtist,
                    trackCount = l.size,
                    year = l.mapNotNull { it.year }.maxOrNull() ?: first.year,
                    artPath = l.firstNotNullOfOrNull { it.artPath },
                    totalDuration = l.sumOf { it.duration },
                    key = key
                )
            }
            .sortedBy { AlphaSort.key(it.name, st.firstLetterOrder) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val genres: StateFlow<List<Genre>> = combine(tracks, _librarySettings) { list, st ->
        val splitter = TagSplitter(if (st.splitGenres) st.genreSeparators else "")
        list.flatMap { t -> splitter.split(t.genre).map { name -> name to t } }
            .groupBy({ it.first }, { it.second })
            .map { (name, l) ->
                // Capa do gênero = arte da faixa mais recente (maior ano, depois primeira com capa)
                val sorted = l.sortedWith(
                    compareByDescending<Track> { it.year ?: 0 }
                        .thenByDescending { it.trackNumber ?: 0 }
                )
                val art = sorted.firstNotNullOfOrNull { it.artPath }
                Genre(name, l.size, art, l.sumOf { it.duration })
            }
            .sortedBy { AlphaSort.key(it.name, st.firstLetterOrder) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val searchArtists: StateFlow<List<Artist>> = combine(artists, _activeSearchQuery) { list, q ->
        if (q.isBlank()) emptyList()
        else {
            val lower = q.lowercase()
            list.filter { it.name.lowercase().contains(lower) }
        }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val searchAlbums: StateFlow<List<Album>> = combine(albums, _activeSearchQuery) { list, q ->
        if (q.isBlank()) emptyList()
        else {
            val lower = q.lowercase()
            list.filter {
                it.name.lowercase().contains(lower) ||
                    it.artist.lowercase().contains(lower)
            }
        }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val searchGenres: StateFlow<List<Genre>> = combine(genres, _activeSearchQuery) { list, q ->
        if (q.isBlank()) emptyList()
        else {
            val lower = q.lowercase()
            list.filter { it.name.lowercase().contains(lower) }
        }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Faixas do grupo aberto, ordenadas por álbum > nº da faixa > título. */
    val visibleTracks: StateFlow<List<Track>> = combine(tracks, filter, _playlists, _librarySettings) { list, f, pls, st ->
        if (f == null) {
            // A aba "Todas" aplica a ordenação escolhida por sortedAllTracks, fora da UI.
            return@combine list
        }
        when (f.tab) {
            Tab.PLAYLISTS -> {
                val pl = pls.firstOrNull { it.id == f.key }
                val byId = list.associateBy { it.id }
                pl?.trackIds?.mapNotNull { byId[it] } ?: emptyList()
            }
            else -> {
                val artistSplitter = artistSplitterOf(st)
                val genreSplitter = TagSplitter(if (st.splitGenres) st.genreSeparators else "")
                val base = list.filter {
                    when (f.tab) {
                        Tab.HOME, Tab.ALL -> true
                        Tab.ARTISTS -> {
                            val key = f.key
                            if (key.startsWith("composer::")) {
                                val c = key.removePrefix("composer::")
                                c in artistSplitter.split(it.composer) ||
                                    it.composer.equals(c, ignoreCase = true)
                            } else {
                                key in artistSplitter.split(it.artist) || it.artist.equals(key, ignoreCase = true)
                            }
                        }
                        Tab.ALBUMS -> if (f.key.startsWith("album-key::")) {
                            albumIdentity(it) == f.key.removePrefix("album-key::")
                        } else {
                            it.album.trim().equals(f.key.trim(), ignoreCase = true)
                        }
                        Tab.GENRES -> f.key in genreSplitter.split(it.genre)
                        Tab.PLAYLISTS -> false
                    }
                }
                base.sortedWith(
                    compareBy<Track>(
                        { AlphaSort.key(it.album, st.firstLetterOrder) },
                        { it.trackNumber ?: Int.MAX_VALUE },
                        { AlphaSort.key(it.title, st.firstLetterOrder) }
                    )
                )
            }
        }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ---------- Scan ----------
    private val scanMutex = Mutex()

    /** Roda [block] com a flag de scan ligada e grava o cache ao terminar. Scans rodam um por vez. */
    private fun launchScan(block: suspend (onTrack: () -> Unit) -> Unit) {
        viewModelScope.launch {
            scanMutex.withLock {
                _isScanning.value = true
                _scanCount.value = 0
                var n = 0
                // Atualiza o contador a cada 8 faixas para reduzir recomposições na UI
                try {
                    block {
                        n++
                        if (n % 8 == 0 || n == 1) _scanCount.value = n
                    }
                    _scanCount.value = n
                    cache.save(_library.value)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // falha de scan não derruba o app; a biblioteca anterior continua valendo
                } finally {
                    _isScanning.value = false
                    if (!playbackRestoreCompleted) restorePlaybackState()
                }
            }
        }
    }

    /** Adiciona uma pasta e lê só ela (as outras continuam do cache). */
    fun addFolderPath(absolutePath: String) {
        val path = absolutePath.trim().trimEnd('/')
        if (path.isBlank()) return
        val key = if (path.startsWith("file:")) path else "file://$path"
        if (key in _treeUris.value || path in _treeUris.value) return
        _treeUris.value = _treeUris.value + key
        saveFolders()
        // Com all-files mode a biblioteca já cobre o disco; ainda assim força refresh
        refreshLibrary()
    }

    fun addFolder(treeUri: Uri) {
        val key = treeUri.toString()
        if (key in _treeUris.value) return
        _treeUris.value = _treeUris.value + key
        saveFolders()
        launchScan { onTrack ->
            val found = scanner.scanTree(treeUri, key, onTrack)
            _library.value = _library.value + found
        }
    }

    fun removeFolder(uriString: String) {
        _treeUris.value = _treeUris.value - uriString
        saveFolders()
        try {
            getApplication<Application>().contentResolver.releasePersistableUriPermission(
                Uri.parse(uriString), Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) { /* já estava sem permissão */ }
        _library.value = _library.value.filter { it.sourceTree != uriString }
        viewModelScope.launch { scanMutex.withLock { cache.save(_library.value) } }
    }

    /** Relê todas as pastas (reaproveita metadados de arquivos não alterados). */
    /**
     * Limpa o aproveitamento de cache dessas faixas, re-lê tags no disco e atualiza a biblioteca.
     */
    fun forceReloadTracks(ids: Collection<String>, onDone: (() -> Unit)? = null) {
        if (ids.isEmpty()) {
            onDone?.invoke()
            return
        }
        val idSet = ids.toSet()
        viewModelScope.launch {
            val updated = withContext(Dispatchers.IO) {
                val lib = _library.value
                val next = lib.map { t ->
                    if (t.id in idSet) scanner.reextract(t) ?: t else t
                }
                try {
                    LibraryCache(getApplication()).save(filterLibrary(next))
                } catch (_: Exception) { }
                filterLibrary(next)
            }
            _library.value = updated
            onDone?.invoke()
        }
    }

    fun forceReloadArtist(artistName: String, onDone: (() -> Unit)? = null) {
        val ids = _library.value
            .filter { artistNamesOf(it).any { n -> n.equals(artistName, ignoreCase = true) } ||
                it.artist.equals(artistName, ignoreCase = true) }
            .map { it.id }
        forceReloadTracks(ids, onDone)
    }

    fun forceReloadAlbum(albumName: String, onDone: (() -> Unit)? = null) {
        val ids = _library.value
            .filter { it.album.equals(albumName, ignoreCase = true) }
            .map { it.id }
        forceReloadTracks(ids, onDone)
    }

    fun refreshLibrary() {
        val folders = _treeUris.value
        val allFiles = _allFilesMode.value
        if (folders.isEmpty() && !allFiles) return
        launchScan { onTrack ->
            val existing = _library.value.associateBy { it.id }
            val result = mutableListOf<Track>()
            if (allFiles) {
                try {
                    result += scanner.scanAllMedia(
                        onTrack = onTrack,
                        existingById = existing,
                        sourceTree = "all_files",
                        scanUnindexedDsf = true
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) { }
            }
            for (f in folders) {
                try {
                    result += scanner.scanTree(Uri.parse(f), f, onTrack, existing)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // uma pasta com problema não impede as outras
                }
            }
            _library.value = filterLibrary(result)
        }
    }

    /**
     * Scan rápido em segundo plano: só reescaneia se já há biblioteca em cache.
     * Arquivos inalterados são reaproveitados; só novos/modificados passam pelo extrator.
     */
    fun quickRescanInBackground() {
        val folders = _treeUris.value
        val allFiles = _allFilesMode.value
        if (folders.isEmpty() && !allFiles) return
        if (_library.value.isEmpty()) {
            refreshLibrary()
            return
        }
        launchScan { onTrack ->
            val existing = _library.value.associateBy { it.id }
            val result = mutableListOf<Track>()
            if (allFiles) {
                try {
                    result += scanner.scanAllMedia(
                        onTrack = onTrack,
                        existingById = existing,
                        sourceTree = "all_files",
                        scanUnindexedDsf = true
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) { }
            }
            for (f in folders) {
                try {
                    result += scanner.scanTree(Uri.parse(f), f, onTrack, existing)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) { }
            }
            if (result.isNotEmpty()) {
                _library.value = filterLibrary(result)
            }
        }
    }

    private fun saveFolders() {
        prefs.edit().putStringSet(KEY_TREE_URIS, _treeUris.value.toSet()).apply()
    }

    /** "primary:Music/Aimer" -> nome "Aimer", caminho "Armazenamento interno/Music/Aimer". */
    private fun describeTree(uriString: String, count: Int): SourceFolder {
        val docId = try {
            DocumentsContract.getTreeDocumentId(Uri.parse(uriString))
        } catch (e: Exception) {
            uriString
        }
        val volume = docId.substringBefore(':')
        val rel = docId.substringAfter(':', "").trim('/')
        val volumeLabel = if (volume.equals("primary", ignoreCase = true)) "Armazenamento interno" else "Cartão SD"
        val path = if (rel.isEmpty()) volumeLabel else "$volumeLabel/$rel"
        val name = if (rel.isEmpty()) volumeLabel else rel.substringAfterLast('/')
        return SourceFolder(uriString, name, path, count)
    }

    // ---------- Player (MediaController -> PlaybackService) ----------
    private val _nowPlaying = MutableStateFlow<Track?>(null)
    val nowPlaying: StateFlow<Track?> = _nowPlaying.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    private val _themeRetainTrackColors = MutableStateFlow(false)
    /** Mantém a paleta da faixa durante o fade de pausa; não altera estado/controles do player. */
    val themeRetainTrackColors: StateFlow<Boolean> = _themeRetainTrackColors.asStateFlow()
    private val _playbackButtonIsPlaying = MutableStateFlow(false)
    val playbackButtonIsPlaying: StateFlow<Boolean> = _playbackButtonIsPlaying.asStateFlow()
    private var pendingPlaybackButtonState: Boolean? = null
    private var playbackButtonSettleJob: Job? = null

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()
    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()

    /** Repetição: 0 = desligada, 1 = repetir tudo, 2 = repetir faixa (valores de Player.REPEAT_MODE_*). */
    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    fun cycleRepeatMode() {
        // Ordem: desligado → música atual → fila inteira → desligado
        val next = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        controller?.repeatMode = next
        _repeatMode.value = next
    }

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (isPlaying) _themeRetainTrackColors.value = false
            val pendingButtonState = pendingPlaybackButtonState
            if (pendingButtonState == null) {
                _playbackButtonIsPlaying.value = isPlaying
            } else if (pendingButtonState && isPlaying) {
                pendingPlaybackButtonState = null
                playbackButtonSettleJob?.cancel()
                playbackButtonSettleJob = null
                _playbackButtonIsPlaying.value = true
            }
            persistPlaybackState(paused = !isPlaying, immediate = !isPlaying)
            if (isPlaying) {
                startPositionTicker()
                _nowPlaying.value?.let { LastFmScrobbler.nowPlaying(it) }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // Zera posição imediatamente para não reutilizar o progresso da faixa anterior
            // no cálculo de play count (race com o combine de _positionMs).
            _positionMs.value = 0L
            _durationMs.value = 0L
            val queuedTrack = _queue.value.firstOrNull { it.id == mediaItem?.mediaId }
            val activeTrack = queuedTrack ?: mediaItem?.let(::externalTrackFromMediaItem)
            if (queuedTrack == null && activeTrack != null) _queue.value = listOf(activeTrack)
            _nowPlaying.value = activeTrack
            if (_isPlaying.value) _nowPlaying.value?.let { LastFmScrobbler.nowPlaying(it) }
            persistPlaybackState(paused = !(_isPlaying.value))
            // Re-anexa EQ caso a sessão de áudio mude
            val sessionId = com.tolete.player.PlaybackService.currentAudioSessionId
            if (sessionId != 0) {
                com.tolete.player.data.EqualizerManager.attach(sessionId, getApplication())
            }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _repeatMode.value = repeatMode
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            // No modo Fila Aleatória o shuffle nativo fica sempre off; _shuffle é controlado por nós
            if (_shuffleType.value != ShuffleType.QUEUE) {
                _shuffle.value = shuffleModeEnabled
            }
        }
    }

    private fun connectController() {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            // A leitura da fila e das edições do scrobble pode envolver JSON/arquivos.
            LastFmScrobbler.init(app)
            withContext(Dispatchers.Main.immediate) {
                val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
                val future = MediaController.Builder(app, token).buildAsync()
                controllerFuture = future
                future.addListener({
                    try {
                        controller = future.get().also { it.addListener(playerListener) }
                        syncFromController()
                        if (_library.value.isNotEmpty()) restorePlaybackState()
                        val sessionId = com.tolete.player.PlaybackService.currentAudioSessionId
                        if (sessionId != 0) {
                            com.tolete.player.data.EqualizerManager.attach(sessionId, app)
                        }
                    } catch (_: Exception) {
                        controller = null
                    }
                }, ContextCompat.getMainExecutor(app))
            }
        }
    }

    fun playTracks(list: List<Track>, startIndex: Int) {
        val c = controller ?: return
        if (startIndex !in list.indices) return
        _queue.value = list
        _pinnedQueueIds.value = emptySet()
        c.setMediaItems(list.map { it.toMediaItem() }, startIndex, 0L)
        c.prepare()
        requestPlaybackButtonState(playing = true)
        c.play()
        _nowPlaying.value = list[startIndex]
        persistPlaybackState(paused = false)
    }

    /** Adiciona a faixa logo após a atual na fila. Se já estiver na fila, apenas reorganiza. */
    fun playNext(track: Track) {
        val c = controller ?: return
        val q = _queue.value.toMutableList()
        val currentId = _nowPlaying.value?.id
        val existingIdx = q.indexOfFirst { it.id == track.id }
        val insertAt = if (currentId != null) {
            val i = q.indexOfFirst { it.id == currentId }
            if (i >= 0) i + 1 else q.size
        } else q.size

        if (existingIdx >= 0) {
            // Já está na fila: remove da posição antiga e recoloca após a atual
            if (existingIdx == insertAt || (currentId == track.id && existingIdx == q.indexOfFirst { it.id == currentId })) {
                return // já está na posição desejada (ou é a atual)
            }
            q.removeAt(existingIdx)
            val newInsert = when {
                existingIdx < insertAt -> (insertAt - 1).coerceAtLeast(0)
                else -> insertAt.coerceAtMost(q.size)
            }
            // Não mover a faixa que está tocando para "play next" de si mesma de forma inválida
            val currentIdx = q.indexOfFirst { it.id == currentId }
            val finalInsert = if (currentIdx >= 0) (currentIdx + 1).coerceAtMost(q.size) else newInsert
            q.add(finalInsert, track)
            _queue.value = q
            try {
                c.moveMediaItem(existingIdx, finalInsert.coerceAtMost(c.mediaItemCount))
            } catch (_: Exception) {
                // fallback: reconstrói a fila no player
                val pos = c.currentPosition
                val wasPlaying = c.isPlaying
                c.setMediaItems(q.map { it.toMediaItem() }, q.indexOfFirst { it.id == currentId }.coerceAtLeast(0), pos)
                if (wasPlaying) c.play()
            }
            persistPlaybackState(paused = !_isPlaying.value)
            return
        }

        q.add(insertAt, track)
        _queue.value = q
        if (c.mediaItemCount == 0) {
            playTracks(listOf(track), 0)
        } else {
            try {
                c.addMediaItem(insertAt, track.toMediaItem())
            } catch (_: Exception) {
                // evita crash se o índice estiver inconsistente
                c.addMediaItem(track.toMediaItem())
            }
        }
        persistPlaybackState(paused = !_isPlaying.value)
    }

    /** Várias faixas logo após a atual, mantendo a ordem recebida (1ª da lista toca primeiro). */
    fun playNextAll(list: List<Track>) {
        // Inserir de trás para frente: cada uma entra logo após a atual, então a 1ª termina na frente.
        if ((controller?.mediaItemCount ?: 0) == 0) { playTracks(list, 0); return }
        list.asReversed().forEach { playNext(it) }
    }

    /** Várias faixas no fim da fila, na ordem recebida. */
    fun playLastAll(list: List<Track>) {
        list.forEach { playLast(it) }
    }

    /** Abre uma playlist (a partir da Início) mostrando suas faixas. */
    fun openPlaylist(id: String) {
        _filter.value = Filter(Tab.PLAYLISTS, id)
    }

    /** Adiciona a faixa no final da fila. */
    fun playLast(track: Track) {
        val c = controller ?: return
        val q = _queue.value.toMutableList()
        q.add(track)
        _queue.value = q
        if (c.mediaItemCount == 0) {
            playTracks(listOf(track), 0)
        } else {
            c.addMediaItem(track.toMediaItem())
        }
        persistPlaybackState(paused = !_isPlaying.value)
    }

    private var fadeJob: Job? = null

    private fun requestPlaybackButtonState(playing: Boolean) {
        playbackButtonSettleJob?.cancel()
        pendingPlaybackButtonState = playing
        _playbackButtonIsPlaying.value = playing
        _themeRetainTrackColors.value = !playing && _fadeMs.value > 0
        val settleAfterMs = if (playing) 1_200L else _fadeMs.value.toLong() + 200L
        playbackButtonSettleJob = viewModelScope.launch {
            delay(settleAfterMs)
            if (pendingPlaybackButtonState == playing) {
                pendingPlaybackButtonState = null
                playbackButtonSettleJob = null
                _playbackButtonIsPlaying.value = _isPlaying.value
                _themeRetainTrackColors.value = false
            }
        }
    }

    fun togglePlayPause() {
        if (controller == null) return
        // Fade é aplicado no PlaybackService (vale para notificação, Bluetooth e UI)
        fadeJob?.cancel()
        if (_playbackButtonIsPlaying.value) pausePlayback() else startPlayback()
    }

    fun startPlayback() {
        val c = controller ?: return
        fadeJob?.cancel()
        requestPlaybackButtonState(playing = true)
        c.volume = 1f
        c.play()
        persistPlaybackState(paused = false)
    }

    fun pausePlayback() {
        val c = controller ?: return
        fadeJob?.cancel()
        requestPlaybackButtonState(playing = false)
        c.pause()
        persistPlaybackState(paused = true, immediate = true)
    }

    fun next() {
        fadeJob?.cancel()
        controller?.volume = 1f
        controller?.seekToNextMediaItem()
        persistPlaybackState(paused = !(_isPlaying.value))
    }

    fun previous() {
        fadeJob?.cancel()
        controller?.volume = 1f
        controller?.seekToPreviousMediaItem()
        persistPlaybackState(paused = !(_isPlaying.value))
    }

    fun currentPosition(): Long = controller?.currentPosition ?: 0L
    fun currentDuration(): Long = controller?.duration?.takeIf { it > 0 } ?: 0L

    private var positionTicker: Job? = null
    private var lastPositionCheckpointMs = 0L
    private fun startPositionTicker() {
        positionTicker?.cancel()
        lastPositionCheckpointMs = android.os.SystemClock.elapsedRealtime()
        positionTicker = viewModelScope.launch {
            while (isActive) {
                val c = controller
                if (c != null) {
                    _positionMs.value = c.currentPosition
                    val d = c.duration
                    if (d > 0) _durationMs.value = d
                }
                if (!_isPlaying.value) break
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastPositionCheckpointMs >= 5_000L) {
                    lastPositionCheckpointMs = now
                    persistPlaybackState(paused = false)
                }
                delay(250)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        _positionMs.value = positionMs
        persistPlaybackState(paused = !_isPlaying.value)
    }

    /** Velocidade de reprodução (1f = normal). Usado no sincronizador de letras. */
    fun setPlaybackSpeed(speed: Float) {
        val s = speed.coerceIn(0.25f, 2f)
        android.util.Log.i("MusicVM", "setPlaybackSpeed($s) player=${com.tolete.player.PlaybackService.exoPlayerRef != null} ctrl=${controller != null}")

        // 1) Referência direta ao ExoPlayer
        com.tolete.player.PlaybackService.setSpeed(s)

        // 2) MediaController
        try {
            controller?.setPlaybackSpeed(s)
        } catch (e: Exception) {
            android.util.Log.w("MusicVM", "controller.setPlaybackSpeed failed", e)
        }
        try {
            controller?.playbackParameters =
                androidx.media3.common.PlaybackParameters(s, /* pitch= */ 1f)
        } catch (_: Exception) { }

        // 3) Comando customizado da sessão
        try {
            val args = android.os.Bundle().apply {
                putFloat(com.tolete.player.PlaybackService.EXTRA_SPEED, s)
            }
            val cmd = androidx.media3.session.SessionCommand(
                com.tolete.player.PlaybackService.ACTION_SET_SPEED,
                android.os.Bundle.EMPTY
            )
            controller?.sendCustomCommand(cmd, args)
        } catch (e: Exception) {
            android.util.Log.w("MusicVM", "sendCustomCommand SET_SPEED failed", e)
        }
    }

    fun currentPlaybackSpeed(): Float {
        val direct = com.tolete.player.PlaybackService.currentSpeed()
        if (direct > 0f) return direct
        return controller?.playbackParameters?.speed?.takeIf { it > 0f } ?: 1f
    }

    fun toggleShuffle() {
        applyShuffleMode(!_shuffle.value)
    }

    private fun applyShuffleMode(enable: Boolean) {
        val c = controller ?: return
        when (_shuffleType.value) {
            ShuffleType.ABSOLUTE -> {
                c.shuffleModeEnabled = enable
                _shuffle.value = enable
            }
            ShuffleType.QUEUE -> {
                // Shuffle nativo off; fila fixa embaralhada a partir da faixa atual
                c.shuffleModeEnabled = false
                if (enable) {
                    val current = _nowPlaying.value ?: run {
                        _shuffle.value = false
                        return
                    }
                    val q = _queue.value
                    val rest = q.filter { it.id != current.id }.shuffled()
                    val newQueue = listOf(current) + rest
                    val pos = c.currentPosition
                    _queue.value = newQueue
                    c.setMediaItems(newQueue.map { it.toMediaItem() }, /* startIndex = */ 0, pos)
                    c.prepare()
                    if (_isPlaying.value) c.play()
                    _shuffle.value = true
                    persistPlaybackState(paused = !_isPlaying.value)
                } else {
                    _shuffle.value = false
                }
            }
        }
    }

    /** Toca o item [index] da fila atual. */
    fun skipToQueueItem(index: Int) {
        val c = controller ?: return
        if (index !in _queue.value.indices) return
        c.seekToDefaultPosition(index)
        c.play()
        _nowPlaying.value = _queue.value[index]
        persistPlaybackState(paused = false)
    }

    /** Faixas fixadas na fila: ficam agrupadas logo após a faixa atual. */
    private val _pinnedQueueIds = MutableStateFlow<Set<String>>(emptySet())
    val pinnedQueueIds: StateFlow<Set<String>> = _pinnedQueueIds.asStateFlow()

    /**
     * Fixa/desafixa uma faixa da fila. Ao fixar, a faixa vai para logo depois da atual (e depois
     * das outras já fixadas, na ordem em que foram fixadas) e ganha um alfinete na lista.
     * Desafixar só remove a marca; a posição fica como está.
     */
    fun togglePinQueueItem(track: Track) {
        val pinned = _pinnedQueueIds.value
        if (track.id in pinned) {
            _pinnedQueueIds.value = pinned - track.id
            return
        }
        _pinnedQueueIds.value = pinned + track.id
        val q = _queue.value
        val from = q.indexOfFirst { it.id == track.id }
        if (from < 0) return
        val currentId = _nowPlaying.value?.id
        if (track.id == currentId) return // a que está tocando só ganha a marca
        val rest = q.toMutableList().also { it.removeAt(from) }
        val curIdx = rest.indexOfFirst { it.id == currentId }
        var anchor = curIdx
        var i = curIdx + 1
        while (i < rest.size && rest[i].id in pinned) {
            anchor = i
            i++
        }
        val to = (anchor + 1).coerceIn(0, rest.size)
        if (to != from) moveQueueItem(from, to)
    }

    /** Reordena a fila atual (arrastar na lista de reprodução). */
    fun moveQueueItem(from: Int, to: Int) {
        val list = _queue.value
        if (from !in list.indices || to !in list.indices || from == to) return
        controller?.moveMediaItem(from, to)
        _queue.value = list.toMutableList().apply { add(to, removeAt(from)) }
        persistPlaybackState(paused = !_isPlaying.value)
    }

    /** Remove um item da fila de reprodução (não apaga o arquivo). */
    fun removeFromQueue(index: Int) {
        val c = controller ?: return
        if (index !in _queue.value.indices) return
        val q = _queue.value.toMutableList()
        q.removeAt(index)
        _queue.value = q
        try {
            c.removeMediaItem(index)
        } catch (_: Exception) { }
        if (q.isEmpty()) {
            _nowPlaying.value = null
            try { c.stop() } catch (_: Exception) { }
        } else if (_nowPlaying.value?.id !in q.map { it.id }.toSet()) {
            val newIndex = index.coerceAtMost(q.lastIndex)
            _nowPlaying.value = q[newIndex]
            try {
                c.seekToDefaultPosition(newIndex)
            } catch (_: Exception) { }
        }
        persistPlaybackState(paused = !(_isPlaying.value))
    }

    /**
     * Se o serviço já estava tocando quando o app foi reaberto, reconstrói fila e
     * "tocando agora" a partir dele. Só age quando controller e biblioteca estão prontos.
     */
    private fun syncFromController() {
        val c = controller ?: return
        _isPlaying.value = c.isPlaying
        if (pendingPlaybackButtonState == null) _playbackButtonIsPlaying.value = c.isPlaying
        if (_shuffleType.value != ShuffleType.QUEUE) {
            _shuffle.value = c.shuffleModeEnabled
        }
        if (c.mediaItemCount == 0) return
        val currentItem = c.currentMediaItem
        val queuedCurrent = _queue.value.firstOrNull { it.id == currentItem?.mediaId }
        if (queuedCurrent != null) {
            _nowPlaying.value = queuedCurrent
            return
        }
        val byId = _library.value.associateBy { it.id }
        val q = (0 until c.mediaItemCount).mapNotNull { byId[c.getMediaItemAt(it).mediaId] }
        if (q.size == c.mediaItemCount) {
            _queue.value = q
            _nowPlaying.value = byId[currentItem?.mediaId]
            return
        }
        val externalTrack = currentItem?.let(::externalTrackFromMediaItem) ?: return
        _queue.value = listOf(externalTrack)
        _nowPlaying.value = externalTrack
    }

    private fun externalTrackFromMediaItem(item: MediaItem): Track? {
        if (!item.mediaId.startsWith("external_")) return null
        val uri = item.localConfiguration?.uri ?: return null
        val metadata = item.mediaMetadata
        val title = metadata.title?.toString()?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')?.substringBeforeLast('.')
            ?: "Música"
        val artPath = metadata.artworkUri
            ?.takeIf { it.scheme == "file" }
            ?.path
        return Track(
            id = item.mediaId,
            title = title,
            artist = metadata.artist?.toString().orEmpty(),
            album = metadata.albumTitle?.toString().orEmpty(),
            albumArtist = "",
            genre = "",
            duration = controller?.duration?.takeIf { it > 0L } ?: 0L,
            uri = uri,
            path = uri.toString(),
            folder = "Arquivo externo",
            artPath = artPath
        )
    }

    private fun Track.toMediaItem(): MediaItem {
        val builder = MediaItem.Builder()
            .setMediaId(id)
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .apply { artPath?.let { setArtworkUri(Uri.fromFile(File(it))) } }
                    .build()
            )
        return builder.build()
    }

    // ---------- Tags e letras editáveis ----------

    /**
     * Atualiza metadados na biblioteca (memória + cache) e tenta gravar no arquivo
     * com jaudiotagger. [onFileResult] recebe (sucesso, mensagem) na Main.
     */
    fun updateTrackTags(
        trackId: String,
        title: String,
        artist: String,
        album: String,
        genre: String,
        year: Int?,
        trackNumber: Int?,
        onFileResult: ((Boolean, String) -> Unit)? = null
    ) {
        updateTrackTags(
            trackId = trackId,
            fields = TagWriter.Fields(
                title = title,
                artist = artist,
                album = album,
                genre = genre,
                year = year?.takeIf { it > 0 }?.toString().orEmpty(),
                track = trackNumber?.takeIf { it > 0 }?.toString().orEmpty()
            ),
            onFileResult = onFileResult
        )
    }

    /** Editor experimental completo — grava todos os campos via JAudioTagger. */
    fun updateTrackTags(
        trackId: String,
        fields: TagWriter.Fields,
        onFileResult: ((Boolean, String) -> Unit)? = null
    ) {
        val yearInt = fields.year.trim().take(4).toIntOrNull()?.takeIf { it in 1000..9999 }
        val trackInt = fields.track.trim().substringBefore('/').toIntOrNull()?.takeIf { it > 0 }
        val newArtPath = fields.cover?.let {
            try { TagWriter.saveCoverPreview(getApplication(), it, "image/jpeg") } catch (_: Exception) { null }
        }
        fun patch(t: Track): Track = if (t.id != trackId) t else t.copy(
            title = fields.title.ifBlank { t.title },
            artist = fields.artist.ifBlank { t.artist },
            album = fields.album.ifBlank { t.album },
            genre = fields.genre.ifBlank { t.genre },
            year = yearInt ?: t.year,
            trackNumber = trackInt ?: t.trackNumber,
            artPath = newArtPath ?: t.artPath
        )
        _library.value = _library.value.map(::patch)
        _queue.value = _queue.value.map(::patch)
        _nowPlaying.value = _nowPlaying.value?.let(::patch)
        viewModelScope.launch {
            scanMutex.withLock { cache.save(_library.value) }
            val track = _library.value.firstOrNull { it.id == trackId } ?: return@launch
            val result = withContext(Dispatchers.IO) {
                TagWriter.write(getApplication(), track, fields)
            }
            onFileResult?.invoke(result.ok, result.message)
        }
    }

    /**
     * Edição em lote: aplica Artista, Álbum e/ou Artista do Álbum nas faixas selecionadas.
     * Campos em branco não alteram o valor existente.
     */
    fun batchUpdateTags(
        trackIds: List<String>,
        artist: String?,
        album: String?,
        albumArtist: String?,
        genre: String? = null,
        year: String? = null,
        trackTotal: String? = null,
        discTotal: String? = null,
        onDone: ((Int, Int) -> Unit)? = null
    ) {
        val idSet = trackIds.toSet()
        val hasArtist = !artist.isNullOrBlank()
        val hasAlbum = !album.isNullOrBlank()
        val hasAlbumArtist = !albumArtist.isNullOrBlank()
        val hasGenre = !genre.isNullOrBlank()
        val hasYear = !year.isNullOrBlank()
        val hasTrackTotal = !trackTotal.isNullOrBlank()
        val hasDiscTotal = !discTotal.isNullOrBlank()
        if (idSet.isEmpty() || (!hasArtist && !hasAlbum && !hasAlbumArtist && !hasGenre && !hasYear && !hasTrackTotal && !hasDiscTotal)) {
            onDone?.invoke(0, 0)
            return
        }
        fun patch(t: Track): Track {
            if (t.id !in idSet) return t
            return t.copy(
                artist = if (hasArtist) artist!! else t.artist,
                album = if (hasAlbum) album!! else t.album,
                genre = if (hasGenre) genre!! else t.genre,
                year = if (hasYear) year!!.take(4).toIntOrNull() ?: t.year else t.year
            )
        }
        _library.value = _library.value.map(::patch)
        _queue.value = _queue.value.map(::patch)
        _nowPlaying.value = _nowPlaying.value?.let(::patch)
        viewModelScope.launch {
            scanMutex.withLock { cache.save(_library.value) }
            var ok = 0
            var fail = 0
            withContext(Dispatchers.IO) {
                for (id in trackIds) {
                    val track = _library.value.firstOrNull { it.id == id } ?: continue
                    val result = TagWriter.writePartial(
                        context = getApplication(),
                        track = track,
                        artist = if (hasArtist) artist else null,
                        album = if (hasAlbum) album else null,
                        albumArtist = if (hasAlbumArtist) albumArtist else null,
                        genre = if (hasGenre) genre else null,
                        year = if (hasYear) year else null,
                        trackTotal = if (hasTrackTotal) trackTotal else null,
                        discTotal = if (hasDiscTotal) discTotal else null
                    )
                    if (result.ok) ok++ else fail++
                }
            }
            onDone?.invoke(ok, fail)
        }
    }

    /**
     * Edição em lote completa (editor em tela cheia): aplica só os campos não nulos de [fields]
     * nas faixas [trackIds] e, se houver, a mesma capa em todas. "" apaga a tag no arquivo.
     */
    fun batchUpdateTagsFull(
        trackIds: List<String>,
        fields: TagWriter.BatchFields,
        onDone: ((Int, Int) -> Unit)? = null
    ) {
        val idSet = trackIds.toSet()
        if (idSet.isEmpty() || !fields.hasChanges) {
            onDone?.invoke(0, 0)
            return
        }
        val newArtPath = fields.cover?.let {
            try { TagWriter.saveCoverPreview(getApplication(), it, "image/jpeg") } catch (_: Exception) { null }
        }
        fun patch(t: Track): Track {
            if (t.id !in idSet) return t
            return t.copy(
                artist = fields.artist?.takeIf { it.isNotBlank() } ?: t.artist,
                album = fields.album?.takeIf { it.isNotBlank() } ?: t.album,
                albumArtist = fields.albumArtist ?: t.albumArtist,
                genre = fields.genre?.takeIf { it.isNotBlank() } ?: t.genre,
                year = fields.year?.let { y -> y.trim().take(4).toIntOrNull()?.takeIf { it in 1000..9999 } } ?: t.year,
                discNumber = fields.disc?.let { d -> d.trim().substringBefore('/').toIntOrNull()?.takeIf { it > 0 } } ?: t.discNumber,
                composer = fields.composer ?: t.composer,
                artPath = newArtPath ?: t.artPath
            )
        }
        _library.value = _library.value.map(::patch)
        _queue.value = _queue.value.map(::patch)
        _nowPlaying.value = _nowPlaying.value?.let(::patch)
        viewModelScope.launch {
            scanMutex.withLock { cache.save(_library.value) }
            var ok = 0
            var fail = 0
            withContext(Dispatchers.IO) {
                for (id in trackIds) {
                    val track = _library.value.firstOrNull { it.id == id } ?: continue
                    val result = TagWriter.writeBatch(getApplication(), track, fields)
                    if (result.ok) ok++ else fail++
                }
            }
            onDone?.invoke(ok, fail)
        }
    }

    /** Salva linhas LRC no .lrc ou nas tags, conforme o target. */
    fun saveSyncedLyrics(
        track: Track,
        lines: List<LyricLine>,
        target: LyricsEmbedder.Target
    ): Boolean {
        return LyricsEmbedder.save(getApplication(), track, lines, target)
    }

    // ---------- Letras (.lrc + tags embutidas, sem cache) ----------
    suspend fun loadLyrics(
        track: Track,
        allowNetwork: Boolean = true
    ): List<LyricLine> = withContext(Dispatchers.IO) {
        val local = LyricsExtractor.load(getApplication(), track)
        if (local.isNotEmpty()) return@withContext local
        // Só baixa da rede se a opção estiver ligada e o chamador permitir
        // (Now Playing / letras popup). Nunca em scans de biblioteca.
        if (_autoFetchLyrics.value && allowNetwork) {
            try {
                val results = LyricsSearch.search(track.title, track.artist, _preferNeteaseRomanization.value)
                if (results.isEmpty()) return@withContext emptyList()
                // Busca automática rígida: título e artista normalizados devem ser iguais
                fun norm(s: String) = java.text.Normalizer
                    .normalize(s.trim().replace(Regex("\\s+"), " "), java.text.Normalizer.Form.NFC)
                    .lowercase(java.util.Locale.ROOT)
                    .filter { it.isLetterOrDigit() || it.isWhitespace() }
                    .replace(Regex("\\s+"), " ")
                    .trim()
                val wantTitle = norm(track.title)
                val wantArtist = norm(track.artist)
                val exact = results.filter {
                    norm(it.trackName) == wantTitle && norm(it.artistName) == wantArtist
                }
                if (exact.isEmpty()) return@withContext emptyList()
                val typeRank = _lyricsTypeOrder.value.withIndex().associate { it.value to it.index }
                val sourceRank = _lyricsSourceOrder.value.withIndex().associate { it.value to it.index }
                val preferRoman = _preferNeteaseRomanization.value
                val best = exact.minWithOrNull(
                    compareBy<com.tolete.player.data.LyricsSearchResult> {
                        if (it.source != com.tolete.player.data.LyricsSource.NETEASE || it.variant == null) 0
                        else if ((it.variant == com.tolete.player.data.LyricsVariant.ROMANIZED) == preferRoman) 0 else 1
                    }.thenBy {
                        typeRank[it.type.name] ?: 99
                    }.thenBy {
                        sourceRank[it.source.name] ?: 99
                    }
                ) ?: return@withContext emptyList()
                val lines = best.lines
                if (lines.isNotEmpty()) {
                    // Outra rotina pode ter gravado enquanto a rede respondia; não sobrescrever.
                    val stored = LyricsEmbedder.hasExternalLrc(track) ||
                        LyricsExtractor.loadEmbedded(getApplication(), track).isNotEmpty()
                    if (stored) return@withContext LyricsExtractor.load(getApplication(), track)
                    val target = _autoLyricsSaveTarget.value
                    val saved = LyricsEmbedder.save(getApplication(), track, lines, target)
                    // Mantém o fallback histórico para .lrc quando a pasta não permite escrita.
                    if (!saved && target == LyricsEmbedder.Target.LRC_FILE) {
                        LyricsEmbedder.save(getApplication(), track, lines, LyricsEmbedder.Target.EMBEDDED)
                    }
                }
                lines
            } catch (_: Exception) {
                emptyList()
            }
        } else emptyList()
    }

    /** Só tags embutidas (para o editor). */
    suspend fun loadEmbeddedLyrics(track: Track): List<LyricLine> = withContext(Dispatchers.IO) {
        LyricsExtractor.loadEmbedded(getApplication(), track)
    }

    /** Só arquivo .lrc externo (para o editor). */
    suspend fun loadExternalLyrics(track: Track): List<LyricLine> = withContext(Dispatchers.IO) {
        LyricsEmbedder.loadExternalLrc(track)
    }

    /** Busca manual: LRCLIB + QQ Music + NetEase + KuGou, todas ao mesmo tempo. */
    suspend fun searchLyrics(trackName: String, artistName: String): List<LyricsSearchResult> =
        LyricsSearch.search(trackName, artistName, _preferNeteaseRomanization.value)

    suspend fun embedLyricsResult(
        track: Track,
        result: LyricsSearchResult,
        target: LyricsEmbedder.Target
    ): List<LyricLine> =
        withContext(Dispatchers.IO) {
            val lines = result.lines
            if (lines.isNotEmpty() && LyricsEmbedder.save(getApplication(), track, lines, target)) lines else emptyList()
        }

    // ---------- Aparência ----------
    enum class AppThemeId { DYNAMIC, DEFAULT, AQUA, SUNSET, FOREST, MIDNIGHT, ROSE, EMBER, AMOLED, TWILIGHT, PHOTO }

    /** Estilo do índice alfabético: glass (atual), classic (faixa de letras) ou slim (barra fina com balão). */
    enum class AlphabetScrollerStyle { GLASS, CLASSIC, SLIM }

    private val _themeId = MutableStateFlow(
        try {
            val raw = prefs.getString(KEY_THEME, AppThemeId.DYNAMIC.name)!!
            // migra tema removido Aqua Light → Aqua
            if (raw == "AQUA_LIGHT") AppThemeId.AQUA else AppThemeId.valueOf(raw)
        } catch (_: Exception) { AppThemeId.DYNAMIC }
    )
    val themeId: StateFlow<AppThemeId> = _themeId.asStateFlow()

    fun setThemeId(id: AppThemeId) {
        _themeId.value = id
        prefs.edit().putString(KEY_THEME, id.name).apply()
    }

    /** Caminho da foto de fundo do tema PHOTO (arquivo local). */
    private val _photoThemePath = MutableStateFlow(prefs.getString(KEY_PHOTO_THEME_PATH, null))
    val photoThemePath: StateFlow<String?> = _photoThemePath.asStateFlow()
    fun setPhotoThemePath(path: String?) {
        _photoThemePath.value = path
        prefs.edit().putString(KEY_PHOTO_THEME_PATH, path).apply()
    }

    /** Intensidade do blur do fundo por foto (0f…1f). */
    private val _photoThemeBlur = MutableStateFlow(prefs.getFloat(KEY_PHOTO_THEME_BLUR, 0.55f).coerceIn(0f, 1f))
    val photoThemeBlur: StateFlow<Float> = _photoThemeBlur.asStateFlow()
    fun setPhotoThemeBlur(v: Float) {
        val c = v.coerceIn(0f, 1f)
        _photoThemeBlur.value = c
        prefs.edit().putFloat(KEY_PHOTO_THEME_BLUR, c).apply()
    }

    /**
     * Quando true e tema = DYNAMIC, usa a capa do álbum como fundo borrado
     * (além das cores extraídas). Opcional.
     */
    private val _dynamicAlbumBlur = MutableStateFlow(prefs.getBoolean(KEY_DYNAMIC_ALBUM_BLUR, false))
    val dynamicAlbumBlur: StateFlow<Boolean> = _dynamicAlbumBlur.asStateFlow()
    fun setDynamicAlbumBlur(enabled: Boolean) {
        _dynamicAlbumBlur.value = enabled
        prefs.edit().putBoolean(KEY_DYNAMIC_ALBUM_BLUR, enabled).apply()
    }

    private val _alphabetScrollerStyle = MutableStateFlow(
        try {
            AlphabetScrollerStyle.valueOf(
                prefs.getString(KEY_ALPHA_SCROLLER_STYLE, AlphabetScrollerStyle.GLASS.name)!!
            )
        } catch (_: Exception) { AlphabetScrollerStyle.GLASS }
    )
    val alphabetScrollerStyle: StateFlow<AlphabetScrollerStyle> = _alphabetScrollerStyle.asStateFlow()
    fun setAlphabetScrollerStyle(style: AlphabetScrollerStyle) {
        _alphabetScrollerStyle.value = style
        prefs.edit().putString(KEY_ALPHA_SCROLLER_STYLE, style.name).apply()
    }

    /** Estilo da forma de onda dos cortadores de áudio (editor de trecho dos vídeos). */
    private val _waveformTheme = MutableStateFlow(
        WaveformTheme.fromName(prefs.getString(KEY_WAVEFORM_THEME, null))
    )
    val waveformTheme: StateFlow<WaveformTheme> = _waveformTheme.asStateFlow()
    fun setWaveformTheme(theme: WaveformTheme) {
        _waveformTheme.value = theme
        prefs.edit().putString(KEY_WAVEFORM_THEME, theme.name).apply()
    }

    private val _menuVisualStyle = MutableStateFlow(
        MenuVisualStyle.fromName(prefs.getString(KEY_MENU_VISUAL_STYLE, null)).also {
            MenuVisualConfig.style = it
        }
    )
    val menuVisualStyle: StateFlow<MenuVisualStyle> = _menuVisualStyle.asStateFlow()

    fun setMenuVisualStyle(style: MenuVisualStyle) {
        _menuVisualStyle.value = style
        MenuVisualConfig.style = style
        prefs.edit().putString(KEY_MENU_VISUAL_STYLE, style.name).apply()
    }

    /** Cor padrão do tema dinâmico quando nenhuma música está tocando (ARGB). */
    private val _dynamicDefaultColor = MutableStateFlow(
        prefs.getInt(KEY_DYNAMIC_DEFAULT_COLOR, 0xFFBB86FC.toInt())
    )
    val dynamicDefaultColor: StateFlow<Int> = _dynamicDefaultColor.asStateFlow()

    fun setDynamicDefaultColor(argb: Int) {
        _dynamicDefaultColor.value = argb
        prefs.edit().putInt(KEY_DYNAMIC_DEFAULT_COLOR, argb).apply()
    }

    /**
     * Até 5 cores (ARGB) que "flutuam" no gradiente do tema dinâmico.
     * Vazio = usa as cores extraídas automaticamente da capa.
     */
    private val _dynamicFloatColors = MutableStateFlow(
        prefs.getString(KEY_DYNAMIC_FLOAT_COLORS, null)
            ?.split(',')
            ?.mapNotNull { it.toIntOrNull() }
            ?.take(5)
            ?: emptyList()
    )
    val dynamicFloatColors: StateFlow<List<Int>> = _dynamicFloatColors.asStateFlow()

    fun setDynamicFloatColors(colors: List<Int>) {
        val clipped = colors.take(5)
        _dynamicFloatColors.value = clipped
        prefs.edit()
            .putString(KEY_DYNAMIC_FLOAT_COLORS, clipped.joinToString(","))
            .apply()
    }

    // ---------- Histórico de reprodução + contagem de plays ----------
    private val _historyIds = MutableStateFlow(
        prefs.getString(KEY_HISTORY, null)
            ?.split('\n')?.filter { it.isNotBlank() }
            ?: emptyList()
    )

    /** Contagem de vezes que cada faixa foi reproduzida (id -> count). */
    private val _playCounts = MutableStateFlow(
        prefs.getString(KEY_PLAY_COUNTS, null)
            ?.lineSequence()
            ?.mapNotNull { line ->
                val parts = line.split('=', limit = 2)
                if (parts.size == 2) parts[0] to (parts[1].toIntOrNull() ?: 0) else null
            }
            ?.toMap()
            ?: emptyMap()
    )

    /** Timestamp (epoch ms) da última reprodução contada por faixa. */
    private val _lastPlayedAt = MutableStateFlow(
        prefs.getString(KEY_LAST_PLAYED, null)
            ?.lineSequence()
            ?.mapNotNull { line ->
                val parts = line.split('=', limit = 2)
                if (parts.size == 2) parts[0] to (parts[1].toLongOrNull() ?: 0L) else null
            }
            ?.toMap()
            ?: emptyMap()
    )
private val _firstPlayedAt = MutableStateFlow(
        prefs.getString(KEY_FIRST_PLAYED, null)
            ?.lineSequence()
            ?.mapNotNull { line ->
                val parts = line.split("=", limit = 2)
                if (parts.size == 2) parts[0] to (parts[1].toLongOrNull() ?: 0L) else null
            }
            ?.toMap()
            ?: emptyMap()
    )

    /**
     * Log de plays com timestamp (id\tts por linha), para estatísticas por período.
     * Limitado a PLAY_EVENTS_MAX entradas (mais recentes).
     */
    private val _playEvents = MutableStateFlow(
        prefs.getString(KEY_PLAY_EVENTS, null)
            ?.lineSequence()
            ?.mapNotNull { line ->
                val parts = line.split('\t', limit = 2)
                if (parts.size == 2) {
                    val ts = parts[1].toLongOrNull() ?: return@mapNotNull null
                    parts[0] to ts
                } else null
            }
            ?.toList()
            ?: emptyList()
    )

    enum class StatsPeriod {
        ALL_TIME, THIS_WEEK, LAST_WEEK, THIS_MONTH, LAST_MONTH, THIS_YEAR, LAST_YEAR
    }

    enum class MostPlayedKind { TRACKS, ARTISTS, ALBUMS }

    data class RankedItem(
        val key: String,
        val title: String,
        val subtitle: String,
        val count: Int,
        val trackIds: List<String> = emptyList(),
        val artPath: String? = null
    )

    /** Intervalo [start, end) em epoch ms para o período. */
    fun periodRange(period: StatsPeriod, now: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
        fun startOfDay(c: java.util.Calendar) {
            c.set(java.util.Calendar.HOUR_OF_DAY, 0)
            c.set(java.util.Calendar.MINUTE, 0)
            c.set(java.util.Calendar.SECOND, 0)
            c.set(java.util.Calendar.MILLISECOND, 0)
        }
        return when (period) {
            StatsPeriod.ALL_TIME -> 0L to Long.MAX_VALUE
            StatsPeriod.THIS_WEEK -> {
                startOfDay(cal)
                cal.set(java.util.Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                val start = cal.timeInMillis
                cal.add(java.util.Calendar.WEEK_OF_YEAR, 1)
                start to cal.timeInMillis
            }
            StatsPeriod.LAST_WEEK -> {
                startOfDay(cal)
                cal.set(java.util.Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                val thisStart = cal.timeInMillis
                cal.add(java.util.Calendar.WEEK_OF_YEAR, -1)
                cal.timeInMillis to thisStart
            }
            StatsPeriod.THIS_MONTH -> {
                startOfDay(cal)
                cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
                val start = cal.timeInMillis
                cal.add(java.util.Calendar.MONTH, 1)
                start to cal.timeInMillis
            }
            StatsPeriod.LAST_MONTH -> {
                startOfDay(cal)
                cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
                val thisStart = cal.timeInMillis
                cal.add(java.util.Calendar.MONTH, -1)
                cal.timeInMillis to thisStart
            }
            StatsPeriod.THIS_YEAR -> {
                startOfDay(cal)
                cal.set(java.util.Calendar.DAY_OF_YEAR, 1)
                val start = cal.timeInMillis
                cal.add(java.util.Calendar.YEAR, 1)
                start to cal.timeInMillis
            }
            StatsPeriod.LAST_YEAR -> {
                startOfDay(cal)
                cal.set(java.util.Calendar.DAY_OF_YEAR, 1)
                val thisStart = cal.timeInMillis
                cal.add(java.util.Calendar.YEAR, -1)
                cal.timeInMillis to thisStart
            }
        }
    }

    private fun countsInPeriod(period: StatsPeriod): Map<String, Int> {
        if (period == StatsPeriod.ALL_TIME) return _playCounts.value
        val (start, end) = periodRange(period)
        val map = mutableMapOf<String, Int>()
        for ((id, ts) in _playEvents.value) {
            if (ts in start until end) map[id] = (map[id] ?: 0) + 1
        }
        // Fallback: se o log de eventos não cobre o período (CSV antigo / prefs dessincronizado),
        // usa lastPlayedAt — pelo menos 1 play se a última vez foi no período.
        if (map.isEmpty()) {
            for ((id, ts) in _lastPlayedAt.value) {
                if (ts in start until end) {
                    map[id] = _playCounts.value[id] ?: 1
                }
            }
        }
        return map
    }

    /** Total de plays do período, sem o limite aplicado às listas exibidas. */
    fun playCountTotal(period: StatsPeriod): Int = countsInPeriod(period).values.sum()

    fun mostPlayedRanked(kind: MostPlayedKind, period: StatsPeriod, limit: Int = HISTORY_MAX): List<RankedItem> {
        val counts = countsInPeriod(period)
        if (counts.isEmpty()) return emptyList()
        val lib = _library.value
        val byId = lib.associateBy { it.id }
        return when (kind) {
            MostPlayedKind.TRACKS -> {
                counts.entries
                    .sortedByDescending { it.value }
                    .mapNotNull { (id, c) ->
                        val t = byId[id] ?: return@mapNotNull null
                        RankedItem(
                            key = id,
                            title = t.title,
                            subtitle = t.artist,
                            count = c,
                            trackIds = listOf(id),
                            artPath = t.artPath
                        )
                    }
                    .take(limit)
            }
            MostPlayedKind.ARTISTS -> {
                val agg = mutableMapOf<String, Pair<Int, MutableList<String>>>()
                for ((id, c) in counts) {
                    val t = byId[id] ?: continue
                    val names = artistNamesOf(t).ifEmpty { listOf(t.artist.ifBlank { "Desconhecido" }) }
                    for (name in names) {
                        val cur = agg[name] ?: (0 to mutableListOf())
                        agg[name] = (cur.first + c) to cur.second.apply { add(id) }
                    }
                }
                val app = getApplication<android.app.Application>()
                agg.entries
                    .sortedByDescending { it.value.first }
                    .map { (name, pair) ->
                        val firstTrack = pair.second.firstOrNull()?.let { byId[it] }
                        // Mesma foto da lista de artistas: meta local → URL → capa de faixa
                        val art = com.tolete.player.data.ArtistMetaStore.cachedPhotoPath(name)
                            ?: firstTrack?.artPath
                        RankedItem(
                            key = name,
                            title = name,
                            subtitle = if (pair.first == 1) "1 play" else "${pair.first} plays",
                            count = pair.first,
                            trackIds = pair.second.distinct(),
                            artPath = art
                        )
                    }
                    .take(limit)
            }
            MostPlayedKind.ALBUMS -> {
                val agg = mutableMapOf<String, Pair<Int, MutableList<String>>>()
                for ((id, c) in counts) {
                    val t = byId[id] ?: continue
                    val album = t.album.ifBlank { "Álbum desconhecido" }
                    val cur = agg[album] ?: (0 to mutableListOf())
                    agg[album] = (cur.first + c) to cur.second.apply { add(id) }
                }
                val app = getApplication<android.app.Application>()
                agg.entries
                    .sortedByDescending { it.value.first }
                    .map { (album, pair) ->
                        val tracks = pair.second.mapNotNull { byId[it] }
                        val firstTrack = tracks.firstOrNull()
                        val art = tracks.firstOrNull { !it.artPath.isNullOrBlank() }?.artPath
                        RankedItem(
                            key = album,
                            title = album,
                            subtitle = firstTrack?.artist.orEmpty(),
                            count = pair.first,
                            trackIds = pair.second.distinct(),
                            artPath = art
                        )
                    }
                    .take(limit)
            }
        }
    }

    fun historyInPeriod(period: StatsPeriod): List<Track> {
        val byId = _library.value.associateBy { it.id }
        if (period == StatsPeriod.ALL_TIME) {
            return _historyIds.value.mapNotNull { byId[it] }
        }
        val (start, end) = periodRange(period)
        // Preferir ordem dos eventos no período (mais recente primeiro)
        val seen = linkedSetOf<String>()
        val fromEvents = mutableListOf<Track>()
        for ((id, ts) in _playEvents.value) {
            if (ts !in start until end) continue
            if (id in seen) continue
            val t = byId[id] ?: continue
            seen.add(id)
            fromEvents.add(t)
        }
        if (fromEvents.isNotEmpty()) return fromEvents
        // Fallback: histórico + lastPlayedAt
        val lasts = _lastPlayedAt.value
        return _historyIds.value.mapNotNull { id ->
            val ts = lasts[id] ?: return@mapNotNull null
            if (ts in start until end) byId[id] else null
        }
    }

    /** Últimas faixas tocadas (mais recente primeiro), já resolvidas na biblioteca. */
    val history: StateFlow<List<Track>> = combine(_historyIds, tracks) { ids, lib ->
        val byId = lib.associateBy { it.id }
        ids.mapNotNull { byId[it] }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Faixas mais tocadas (ordenadas por contagem desc), já resolvidas. */
    val mostPlayed: StateFlow<List<Track>> = combine(_playCounts, tracks) { counts, lib ->
        val byId = lib.associateBy { it.id }
        counts.entries
            .sortedByDescending { it.value }
            .mapNotNull { (id, _) -> byId[id] }
            .take(HISTORY_MAX)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun playCountOf(trackId: String): Int = _playCounts.value[trackId] ?: 0
    fun firstPlayedAtOf(trackId: String): Long = _firstPlayedAt.value[trackId] ?: 0L
    fun lastPlayedAtOf(trackId: String): Long = _lastPlayedAt.value[trackId] ?: 0L

    fun lastPlayedAt(trackId: String): Long = _lastPlayedAt.value[trackId] ?: 0L

    /** Percentual mínimo da faixa (70–100) para contar como "tocada". */
    private val _playCountThreshold = MutableStateFlow(
        prefs.getInt(KEY_PLAY_COUNT_THRESHOLD, 70).coerceIn(70, 100)
    )
    val playCountThreshold: StateFlow<Int> = _playCountThreshold.asStateFlow()

    fun setPlayCountThreshold(percent: Int) {
        val v = percent.coerceIn(70, 100)
        _playCountThreshold.value = v
        prefs.edit().putInt(KEY_PLAY_COUNT_THRESHOLD, v).apply()
    }

    /**
     * Minutos de reprodução (1–6) que também contam o play; 0 = desligado.
     * Vale o que for atingido primeiro: o percentual acima OU este tempo.
     */
    private val _playCountMinutes = MutableStateFlow(
        prefs.getInt(KEY_PLAY_COUNT_MINUTES, 0).coerceIn(0, 6)
    )
    val playCountMinutes: StateFlow<Int> = _playCountMinutes.asStateFlow()

    fun setPlayCountMinutes(minutes: Int) {
        val v = minutes.coerceIn(0, 6)
        _playCountMinutes.value = v
        prefs.edit().putInt(KEY_PLAY_COUNT_MINUTES, v).apply()
    }

    /** Username público do Last.fm (leitura de histórico/loved). O scrobbling usa [LastFmScrobbler]. */
    private val _lastFmUsername = MutableStateFlow(prefs.getString(KEY_LASTFM_USER, null)?.takeIf { it.isNotBlank() })
    val lastFmUsername: StateFlow<String?> = _lastFmUsername.asStateFlow()

    private val _lastFmSyncMessage = MutableStateFlow<String?>(null)
    val lastFmSyncMessage: StateFlow<String?> = _lastFmSyncMessage.asStateFlow()

    fun setLastFmUsername(user: String?) {
        val v = user?.trim()?.takeIf { it.isNotBlank() }
        _lastFmUsername.value = v
        prefs.edit().putString(KEY_LASTFM_USER, v).apply()
        if (v == null) _lastFmSyncMessage.value = null
    }


    private val _libraryRootPath = MutableStateFlow(prefs.getString(KEY_LIBRARY_ROOT, null)?.takeIf { it.isNotBlank() })
    val libraryRootPath: StateFlow<String?> = _libraryRootPath.asStateFlow()

    private val _organizeStatus = MutableStateFlow<String?>(null)
    val organizeStatus: StateFlow<String?> = _organizeStatus.asStateFlow()

    fun setLibraryRootPath(path: String?) {
        val v = path?.trim()?.takeIf { it.isNotBlank() }
        _libraryRootPath.value = v
        prefs.edit().putString(KEY_LIBRARY_ROOT, v).apply()
    }

    private fun sanitizeFilePart(s: String): String =
        s.replace(Regex("[\\/:*?\"<>|]"), "_").trim().ifBlank { "Desconhecido" }

    private fun collectAudioFiles(root: File): List<File> {
        val out = mutableListOf<File>()
        fun walk(d: File) {
            val kids = d.listFiles() ?: return
            for (f in kids) {
                if (f.isDirectory) walk(f)
                else if (f.extension.lowercase() in setOf("mp3","flac","wav","m4a","aac","ogg","opus","alac","aiff","aif","wma","dsf","dff")) {
                    out.add(f)
                }
            }
        }
        if (root.isDirectory) walk(root) else if (root.isFile) out.add(root)
        return out
    }

    fun bulkRenameFolderByPattern(folderPath: String, pattern: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _organizeStatus.value = "Renomeando…"
            try {
                val files = collectAudioFiles(File(folderPath))
                var ok = 0
                for (src in files) {
                    val tags = com.tolete.player.data.TagReader.read(src)
                    val trackNum = tags?.track?.filter { it.isDigit() }?.take(2)?.padStart(2, '0') ?: "00"
                    val base = pattern
                        .replace("%ARTIST%", tags?.artist.orEmpty())
                        .replace("%TITLE%", tags?.title ?: src.nameWithoutExtension)
                        .replace("%ALBUM%", tags?.album.orEmpty())
                        .replace("%ALBUM_ARTIST%", tags?.albumArtist ?: tags?.artist.orEmpty())
                        .replace("%TRACK%", trackNum)
                        .replace("%YEAR%", tags?.year.orEmpty())
                        .replace("%GENRE%", tags?.genre.orEmpty())
                        .replace("%COMPOSER%", tags?.composer.orEmpty())
                        .trim()
                        .ifBlank { src.nameWithoutExtension }
                    val safe = sanitizeFilePart(base)
                    val dest = File(src.parentFile, "$safe.${src.extension}")
                    if (dest.absolutePath == src.absolutePath) { ok++; continue }
                    if (dest.exists()) continue
                    if (src.renameTo(dest)) ok++
                }
                _organizeStatus.value = "Renomeadas $ok de ${files.size} faixas"
                refreshLibrary()
            } catch (e: Exception) {
                _organizeStatus.value = "Falha: ${e.message}"
            }
        }
    }

    fun moveFolderToLibraryRoot(sourceFolder: String) {
        val rootPath = _libraryRootPath.value ?: run {
            _organizeStatus.value = "Defina a pasta principal antes"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val channelId = "lyron_organize"
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(
                    android.app.NotificationChannel(channelId, "Organizar biblioteca", android.app.NotificationManager.IMPORTANCE_LOW)
                )
            }
            val files = collectAudioFiles(File(sourceFolder))
            val total = files.size.coerceAtLeast(1)
            _organizeStatus.value = "Movendo 0/$total…"
            var done = 0
            var moved = 0
            fun notify(progress: Int, text: String, ongoing: Boolean = true) {
                val builder = if (android.os.Build.VERSION.SDK_INT >= 26) {
                    android.app.Notification.Builder(app, channelId)
                } else {
                    @Suppress("DEPRECATION")
                    android.app.Notification.Builder(app)
                }
                val n = builder
                    .setContentTitle("Organizar biblioteca")
                    .setContentText(text)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setOngoing(ongoing)
                    .setProgress(total, progress, false)
                    .build()
                nm.notify(4242, n)
            }
            notify(0, "Preparando…")
            val root = File(rootPath)
            root.mkdirs()
            for (src in files) {
                try {
                    val tags = com.tolete.player.data.TagReader.read(src)
                    // Hierarquia: Artista do Álbum → Artista → "Artista desconhecido"
                    val albumArtistRaw = tags?.albumArtist?.trim().orEmpty()
                    val artistRaw = tags?.artist?.trim().orEmpty()
                    val folderArtist = sanitizeFilePart(
                        when {
                            albumArtistRaw.isNotBlank() -> albumArtistRaw
                            artistRaw.isNotBlank() -> artistRaw
                            else -> "Artista desconhecido"
                        }
                    )
                    val album = sanitizeFilePart(
                        tags?.album?.trim()?.takeIf { it.isNotBlank() } ?: "Álbum desconhecido"
                    )
                    val destDir = File(root, "$folderArtist/$album")
                    destDir.mkdirs()
                    var dest = File(destDir, src.name)
                    if (dest.exists() && dest.absolutePath != src.absolutePath) {
                        var i = 1
                        val base = src.nameWithoutExtension
                        val ext = src.extension
                        while (dest.exists()) {
                            dest = File(destDir, "$base ($i).$ext")
                            i++
                        }
                    }
                    if (dest.absolutePath != src.absolutePath) {
                        if (!src.renameTo(dest)) {
                            try {
                                src.copyTo(dest, overwrite = false)
                                src.delete()
                            } catch (_: Exception) {}
                        }
                        if (dest.exists()) moved++
                    }
                } catch (_: Exception) {}
                done++
                if (done % 3 == 0 || done == files.size) {
                    notify(done, "$done de ${files.size}")
                    _organizeStatus.value = "Movendo $done/${files.size}…"
                }
            }
            notify(files.size, "Concluído: $moved movida(s)", ongoing = false)
            _organizeStatus.value = "Movidas $moved de ${files.size} faixas"
            refreshLibrary()
        }
    }

    private fun normMatch(s: String): String =
        s.lowercase().trim().replace(Regex("\\s+"), " ")

    private fun matchLocal(title: String, artist: String): Track? {
        val nt = normMatch(title)
        val na = normMatch(artist)
        if (nt.isEmpty()) return null
        val lib = tracks.value
        // 1) título + artista
        lib.firstOrNull { normMatch(it.title) == nt && (na.isEmpty() || normMatch(it.artist) == na) }
            ?.let { return it }
        // 2) só título
        return lib.firstOrNull { normMatch(it.title) == nt }
    }

    /**
     * Importa histórico recente e faixas amadas do Last.fm (conta pública).
     * Casa com a biblioteca local por título/artista. Não faz scrobbling.
     */
    fun syncFromLastFm() {
        val user = _lastFmUsername.value ?: run {
            _lastFmSyncMessage.value = "Informe o usuário do Last.fm"
            return
        }
        viewModelScope.launch {
            _lastFmSyncMessage.value = "Importando favoritos…"
            try {
                val loved = LastFmClient.getLovedTracks(user, limit = 200)
                var lovedMatched = 0
                if (loved.isNotEmpty()) {
                    val list = _playlists.value.toMutableList()
                    val favIdx = list.indexOfFirst { it.isFavorites }
                    val current = if (favIdx >= 0) list[favIdx].trackIds.toMutableList() else mutableListOf()
                    for (ut in loved) {
                        val t = matchLocal(ut.title, ut.artist) ?: continue
                        if (t.id !in current) {
                            current.add(0, t.id)
                            lovedMatched++
                        }
                    }
                    val fav = Playlist.favorites(current)
                    if (favIdx >= 0) list[favIdx] = fav else list.add(0, fav)
                    _playlists.value = list
                    persistPlaylists()
                }
                _lastFmSyncMessage.value =
                    if (lovedMatched > 0) "Favoritos: $lovedMatched nova(s) faixa(s)"
                    else "Nenhum favorito novo encontrado na biblioteca"
            } catch (e: Exception) {
                _lastFmSyncMessage.value = "Erro: ${e.message}"
            }
        }
    }

    /** IDs já contados nesta sessão de play (evita recontagem até trocar de faixa). */
    private val countedThisSession = mutableSetOf<String>()

    /**
     * Tempo realmente ouvido na faixa atual (ms), acumulado só enquanto [isPlaying].
     * Não sobe com seek — avançar para o fim da música não dispara scrobble/play count.
     */
    private val _listenedMs = MutableStateFlow(0L)
    private var listenTrackId: String? = null
    private var lastListenWallMs: Long = 0L

    private fun recordPlay(id: String) {
        if (id in countedThisSession) return
        countedThisSession.add(id)
        val now = System.currentTimeMillis()
        val updated = (listOf(id) + _historyIds.value.filter { it != id }).take(HISTORY_MAX)
        _historyIds.value = updated
        prefs.edit().putString(KEY_HISTORY, updated.joinToString("\n")).apply()
        val counts = _playCounts.value.toMutableMap()
        val prev = counts[id] ?: 0
        counts[id] = prev + 1
        _playCounts.value = counts
        val lasts = _lastPlayedAt.value.toMutableMap()
        lasts[id] = now
        _lastPlayedAt.value = lasts
        if (prev == 0) {
            val firsts = _firstPlayedAt.value.toMutableMap()
            firsts[id] = now
            _firstPlayedAt.value = firsts
            val firstLine = firsts.entries.joinToString(separator = "\n") { e -> e.key + "=" + e.value }
            prefs.edit().putString(KEY_FIRST_PLAYED, firstLine).apply()
        }
        val events = (listOf(id to now) + _playEvents.value).take(PLAY_EVENTS_MAX)
        _playEvents.value = events
        prefs.edit()
            .putString(KEY_PLAY_COUNTS, counts.entries.joinToString("\n") { "${it.key}=${it.value}" })
            .putString(KEY_LAST_PLAYED, lasts.entries.joinToString("\n") { "${it.key}=${it.value}" })
            .putString(KEY_PLAY_EVENTS, events.joinToString("\n") { "${it.first}\t${it.second}" })
            .apply()
        val track = tracks.value.firstOrNull { it.id == id }
        if (track != null) {
            viewModelScope.launch(Dispatchers.IO) {
                appendPlaybackHistoryCsv(track, now)
            }
            // Timestamp = início estimado pelo tempo REALMENTE ouvido (não pela posição do seek).
            val listened = _listenedMs.value.coerceAtLeast(0L)
            LastFmScrobbler.scrobble(track, now - listened)
        }
    }

    private val playbackHistoryCsvLock = Any()

    private fun csvEscape(value: String): String {
        val needsQuotes = value.contains(',') || value.contains('"') ||
            value.contains('\n') || value.contains('\r')
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }

    private fun detectFileFormat(track: Track): String {
        val fromCodec = track.codec?.trim().orEmpty()
        if (fromCodec.isNotEmpty()) {
            val c = fromCodec.uppercase()
            return when {
                "FLAC" in c -> "Flac"
                "MPEG" in c || "MP3" in c || "LAYER" in c -> "Mp3"
                "WAV" in c || "PCM" in c -> "Wav"
                "AAC" in c || "M4A" in c || "MP4" in c -> "M4a"
                "OGG" in c || "VORBIS" in c || "OPUS" in c -> "Ogg"
                "ALAC" in c -> "Alac"
                "AIFF" in c -> "Aiff"
                else -> fromCodec.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                }
            }
        }
        val ext = track.path.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "flac" -> "Flac"
            "mp3" -> "Mp3"
            "wav" -> "Wav"
            "m4a", "aac", "mp4" -> "M4a"
            "ogg", "opus" -> "Ogg"
            "alac" -> "Alac"
            "aiff", "aif" -> "Aiff"
            "wma" -> "Wma"
            else -> ext.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }.ifBlank { "Unknown" }
        }
    }

    private fun formatPlayTimeHuman(epochMs: Long): String {
        val sdf = SimpleDateFormat("d 'de' MMMM 'de' yyyy 'às' HH:mm z", Locale("pt", "BR"))
        sdf.timeZone = TimeZone.getTimeZone("America/Sao_Paulo")
        return sdf.format(Date(epochMs))
    }

    /** Caminho do CSV de plays (fonte de verdade do histórico e mais tocadas). */
    private fun playbackHistoryCsvFile(): File {
        val dir = File("/storage/emulated/0/Lyron")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "playback history.csv")
    }

    private val CSV_HEADER =
        "timeHuman,epochMs,artist,track,album,albumArtist,fileFormat,isFavorite,path,durationMs,year,genre,trackId,codec,sampleRate,bitrate,playCountAfter"

    /** Grava cada play datada em /storage/emulated/0/Lyron/playback history.csv */
    private fun appendPlaybackHistoryCsv(track: Track, epochMs: Long) {
        try {
            val file = playbackHistoryCsvFile()
            val timeHuman = formatPlayTimeHuman(epochMs)
            val albumArtist = track.albumArtist.ifBlank { track.artist }
            val format = detectFileFormat(track)
            val fav = if (isFavorite(track.id)) "Yes" else "No"
            val afterCount = (_playCounts.value[track.id] ?: 0)
            val line = listOf(
                timeHuman,
                epochMs.toString(),
                track.artist,
                track.title,
                track.album,
                albumArtist,
                format,
                fav,
                track.path,
                track.duration.toString(),
                track.year?.toString().orEmpty(),
                track.genre,
                track.id,
                track.codec.orEmpty(),
                if (track.sampleRate > 0) track.sampleRate.toString() else "",
                if (track.bitrate > 0) track.bitrate.toString() else "",
                afterCount.toString()
            ).joinToString(",") { csvEscape(it) }
            synchronized(playbackHistoryCsvLock) {
                val needsHeader = !file.exists() || file.length() == 0L
                val sb = StringBuilder()
                if (needsHeader) sb.append(CSV_HEADER).append('\n')
                sb.append(line).append('\n')
                file.appendText(sb.toString(), Charsets.UTF_8)
            }
            // Mantém memória alinhada ao CSV
            reloadStatsFromCsv()
        } catch (e: Exception) {
            android.util.Log.w("MusicVM", "Falha ao gravar playback history.csv", e)
        }
    }

    /**
     * Lê o CSV e reconstrói histórico, contagens, eventos por período e primeira/última reprodução.
     * Aceita o cabeçalho antigo (sem epochMs/path/…) e o novo.
     * Fonte de verdade: playback history.csv
     */
    fun reloadStatsFromCsv() {
        try {
            val file = playbackHistoryCsvFile()
            if (!file.exists() || file.length() == 0L) return
            val lines = file.readLines(Charsets.UTF_8).filter { it.isNotBlank() }
            if (lines.isEmpty()) return
            val headerCells = parseCsvLine(lines.first()).map { it.lowercase() }
            val hasEpoch = "epochms" in headerCells
            val hasPath = "path" in headerCells
            val hasTrackId = "trackid" in headerCells
            val data = if (headerCells.firstOrNull()?.contains("timehuman") == true) lines.drop(1) else lines

            val counts = linkedMapOf<String, Int>()
            val first = mutableMapOf<String, Long>()
            val last = mutableMapOf<String, Long>()
            val recentOrder = mutableListOf<String>() // ordem de primeira aparição (mais antigo primeiro)
            val rawEvents = mutableListOf<Pair<String, Long>>() // key to epoch

            for (raw in data) {
                val cols = parseCsvLine(raw)
                if (cols.size < 4) continue
                val epoch = if (hasEpoch && cols.size > 1) cols[1].toLongOrNull() ?: 0L else 0L
                val artist = if (hasEpoch) cols.getOrNull(2).orEmpty() else cols.getOrNull(1).orEmpty()
                val title = if (hasEpoch) cols.getOrNull(3).orEmpty() else cols.getOrNull(2).orEmpty()
                val path = if (hasPath) {
                    val idx = headerCells.indexOf("path")
                    if (idx >= 0) cols.getOrNull(idx).orEmpty() else ""
                } else ""
                val trackId = if (hasTrackId) {
                    val idx = headerCells.indexOf("trackid")
                    if (idx >= 0) cols.getOrNull(idx).orEmpty() else ""
                } else ""
                val key = when {
                    trackId.isNotBlank() -> trackId
                    path.isNotBlank() -> path
                    else -> "$artist\t$title"
                }
                if (key.isBlank()) continue
                counts[key] = (counts[key] ?: 0) + 1
                if (epoch > 0L) {
                    val f = first[key]
                    if (f == null || epoch < f) first[key] = epoch
                    val l = last[key]
                    if (l == null || epoch > l) last[key] = epoch
                    rawEvents.add(key to epoch)
                }
                if (key !in recentOrder) recentOrder.add(key)
            }
            // CSV appenda no fim → mais recente por último; inverte para histórico
            val ordered = recentOrder.asReversed().distinct()

            // Mapear keys para ids da biblioteca quando possível
            val lib = tracks.value.ifEmpty { _library.value }
            val byId = lib.associateBy { it.id }
            val byPath = lib.associateBy { it.path }
            fun resolveId(key: String): String {
                byId[key]?.id?.let { return it }
                byPath[key]?.id?.let { return it }
                // path normalizado / sufixo
                if (key.startsWith("/")) {
                    lib.firstOrNull { it.path.equals(key, true) }?.id?.let { return it }
                    lib.firstOrNull { it.path.endsWith(key.substringAfterLast('/'), true) }?.id?.let { return it }
                }
                if (key.contains('\t')) {
                    val parts = key.split('\t', limit = 2)
                    if (parts.size == 2) {
                        lib.firstOrNull {
                            it.title.equals(parts[1], true) && it.artist.equals(parts[0], true)
                        }?.id?.let { return it }
                    }
                }
                // artista - título em path
                return key
            }

            val countById = mutableMapOf<String, Int>()
            counts.forEach { (k, v) ->
                val id = resolveId(k)
                countById[id] = (countById[id] ?: 0) + v
            }
            val firstById = mutableMapOf<String, Long>()
            first.forEach { (k, v) ->
                val id = resolveId(k)
                val prev = firstById[id]
                if (prev == null || v < prev) firstById[id] = v
            }
            val lastById = mutableMapOf<String, Long>()
            last.forEach { (k, v) ->
                val id = resolveId(k)
                val prev = lastById[id]
                if (prev == null || v > prev) lastById[id] = v
            }
            val histIds = ordered.map { resolveId(it) }.distinct().take(HISTORY_MAX)

            // Eventos por play (mais recente primeiro) — necessário para "esta semana" etc.
            val eventsById = rawEvents
                .asReversed()
                .map { (k, ts) -> resolveId(k) to ts }
                .filter { it.second > 0L }
                .take(PLAY_EVENTS_MAX)

            _playCounts.value = countById
            _firstPlayedAt.value = firstById
            _lastPlayedAt.value = lastById
            _historyIds.value = histIds
            _playEvents.value = eventsById
            prefs.edit()
                .putString(KEY_PLAY_COUNTS, countById.entries.joinToString("\n") { "${it.key}=${it.value}" })
                .putString(KEY_FIRST_PLAYED, firstById.entries.joinToString("\n") { "${it.key}=${it.value}" })
                .putString(KEY_LAST_PLAYED, lastById.entries.joinToString("\n") { "${it.key}=${it.value}" })
                .putString(KEY_HISTORY, histIds.joinToString("\n"))
                .putString(KEY_PLAY_EVENTS, eventsById.joinToString("\n") { "${it.first}\t${it.second}" })
                .apply()
        } catch (e: Exception) {
            android.util.Log.w("MusicVM", "Falha ao ler playback history.csv", e)
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"'); i++
                    } else inQuotes = !inQuotes
                }
                c == ',' && !inQuotes -> {
                    out.add(sb.toString()); sb.clear()
                }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString())
        return out
    }

    fun clearFavorites() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = _playlists.value.toMutableList()
            val idx = list.indexOfFirst { it.isFavorites }
            if (idx >= 0) {
                list[idx] = Playlist.favorites(emptyList())
            } else {
                list.add(0, Playlist.favorites(emptyList()))
            }
            _playlists.value = list
            writePlaylistsNow()
            try { com.tolete.player.data.FavoritesFile.clearAll(getApplication()) } catch (_: Exception) {}
            _organizeStatus.value = "Favoritos limpos"
        }
    }

    fun clearPlaybackHistory() {

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val f = playbackHistoryCsvFile()
                if (f.exists()) f.writeText(CSV_HEADER + "\n", Charsets.UTF_8)
            } catch (_: Exception) {}
            _historyIds.value = emptyList()
            _playCounts.value = emptyMap()
            _firstPlayedAt.value = emptyMap()
            _lastPlayedAt.value = emptyMap()
            _playEvents.value = emptyList()
            prefs.edit()
                .remove(KEY_HISTORY)
                .remove(KEY_PLAY_COUNTS)
                .remove(KEY_FIRST_PLAYED)
                .remove(KEY_LAST_PLAYED)
                .remove(KEY_PLAY_EVENTS)
                .apply()
            _organizeStatus.value = "Histórico limpo"
        }
    }


    private val _trackBlacklist = MutableStateFlow(
        prefs.getString(KEY_TRACK_BLACKLIST, null)
            ?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()
    )
    val trackBlacklist: StateFlow<Set<String>> = _trackBlacklist.asStateFlow()

    fun isTrackBlacklisted(trackId: String, path: String = ""): Boolean {
        val set = _trackBlacklist.value
        return trackId in set || (path.isNotBlank() && path in set)
    }

    fun addToTrackBlacklist(track: Track) {
        val set = _trackBlacklist.value.toMutableSet()
        set.add(track.id)
        if (track.path.isNotBlank()) set.add(track.path)
        _trackBlacklist.value = set
        prefs.edit().putString(KEY_TRACK_BLACKLIST, set.joinToString("\n")).apply()
        // Remove from library view immediately
        _library.value = _library.value.filter { it.id != track.id && it.path != track.path }
    }

    fun removeFromTrackBlacklist(idOrPath: String) {
        val set = _trackBlacklist.value.toMutableSet()
        set.remove(idOrPath)
        _trackBlacklist.value = set
        prefs.edit().putString(KEY_TRACK_BLACKLIST, set.joinToString("\n")).apply()
    }


    init {
        viewModelScope.launch(Dispatchers.IO) {
            // Aguarda um pouco a biblioteca e carrega stats do CSV
            kotlinx.coroutines.delay(1500)
            reloadStatsFromCsv()
        }
        // Quando a biblioteca carrega/atualiza, re-lê o CSV para resolver trackIds → Track
        // (o reload precoce pode ter rodado com lib vazia e IDs ficarem "soltos").
        viewModelScope.launch {
            var hadLib = false
            tracks.collect { lib ->
                if (lib.isNotEmpty()) {
                    if (!hadLib) {
                        hadLib = true
                        withContext(Dispatchers.IO) { reloadStatsFromCsv() }
                    }
                }
            }
        }
        // Nova faixa: zera sessão e tempo realmente ouvido
        viewModelScope.launch {
            _nowPlaying.collect { t ->
                countedThisSession.clear()
                listenTrackId = t?.id
                _listenedMs.value = 0L
                lastListenWallMs = 0L
            }
        }
        // Acumula tempo REAL de reprodução (só enquanto isPlaying) — ignores seek
        viewModelScope.launch {
            while (true) {
                delay(500)
                val t = _nowPlaying.value
                if (t == null) {
                    lastListenWallMs = 0L
                    continue
                }
                if (listenTrackId != t.id) {
                    listenTrackId = t.id
                    _listenedMs.value = 0L
                    lastListenWallMs = 0L
                }
                val now = System.currentTimeMillis()
                if (_isPlaying.value) {
                    if (lastListenWallMs > 0L) {
                        val delta = (now - lastListenWallMs).coerceIn(0L, 2000L)
                        if (delta > 0L) _listenedMs.value = _listenedMs.value + delta
                    }
                    lastListenWallMs = now
                } else {
                    lastListenWallMs = 0L
                }
            }
        }
        // Conta play/scrobble pelo tempo REALMENTE ouvido (não pela posição do seek).
        viewModelScope.launch {
            combine(_nowPlaying, _listenedMs, _durationMs, _playCountThreshold, _playCountMinutes) { track, listened, playerDur, threshold, minutes ->
                arrayOf<Any?>(track, listened, playerDur, threshold, minutes)
            }.collect { arr ->
                val track = arr[0] as Track?
                val listened = arr[1] as Long
                val playerDur = arr[2] as Long
                val threshold = arr[3] as Int
                val minutes = arr[4] as Int
                val t = track ?: return@collect
                if (listened <= 0L) return@collect
                val dur = when {
                    playerDur > 0L -> playerDur
                    t.duration > 0L -> t.duration
                    else -> 0L
                }
                val reachedPercent = dur > 0L && ((listened * 100L) / dur).toInt() >= threshold
                val reachedTime = minutes > 0 && listened >= minutes * 60_000L
                if (reachedPercent || reachedTime) {
                    recordPlay(t.id)
                }
            }
        }
    }

    /** Caminho absoluto do .ttf personalizado para letras (null = fonte padrão). */
    private val _lyricsFontPath = MutableStateFlow(prefs.getString(KEY_LYRICS_FONT, null))
    val lyricsFontPath: StateFlow<String?> = _lyricsFontPath.asStateFlow()

    /** Destino do seletor externo de fontes. */
    private var fontPickTarget: String = "lyrics"
    fun beginFontPickForTranslation() { fontPickTarget = "translation" }
    fun beginFontPickForBacking() { fontPickTarget = "backing" }
    fun beginFontPickForLyrics() { fontPickTarget = "lyrics" }

    fun setLyricsFontPath(path: String?) {
        val target = fontPickTarget
        fontPickTarget = "lyrics"
        when (target) {
            "translation" -> setLyricsTranslationFontPath(path)
            "backing" -> setLyricsBackingFontPath(path)
            else -> setMainLyricsFontPath(path)
        }
    }

    fun setMainLyricsFontPath(path: String?) {
        fontPickTarget = "lyrics"
        _lyricsFontPath.value = path
        prefs.edit().putString(KEY_LYRICS_FONT, path).apply()
    }

    private val _lyricsTranslationFontPath = MutableStateFlow(prefs.getString(KEY_LYRICS_TRANSLATION_FONT, null))
    val lyricsTranslationFontPath: StateFlow<String?> = _lyricsTranslationFontPath.asStateFlow()
    fun setLyricsTranslationFontPath(path: String?) {
        fontPickTarget = "lyrics"
        _lyricsTranslationFontPath.value = path
        prefs.edit().putString(KEY_LYRICS_TRANSLATION_FONT, path).apply()
    }

    /** Fonte opcional e independente das linhas de backing vocal. */
    private val _lyricsBackingFontPath = MutableStateFlow(prefs.getString(KEY_LYRICS_BACKING_FONT, null))
    val lyricsBackingFontPath: StateFlow<String?> = _lyricsBackingFontPath.asStateFlow()
    fun setLyricsBackingFontPath(path: String?) {
        fontPickTarget = "lyrics"
        _lyricsBackingFontPath.value = path
        prefs.edit().putString(KEY_LYRICS_BACKING_FONT, path).apply()
    }

    /** Fonte opcional para palavras/sílabas marcadas {des}; null usa a fonte padrão da letra/sistema. */
    private val _lyricsEmphasisFontPath = MutableStateFlow(prefs.getString(KEY_LYRICS_EMPHASIS_FONT, null))
    val lyricsEmphasisFontPath: StateFlow<String?> = _lyricsEmphasisFontPath.asStateFlow()
    fun setLyricsEmphasisFontPath(path: String?) {
        _lyricsEmphasisFontPath.value = path
        prefs.edit().putString(KEY_LYRICS_EMPHASIS_FONT, path).apply()
    }

    /** Fonte padrão do player (UI), independente da fonte das letras. */
    private val _uiFontPath = MutableStateFlow(prefs.getString(KEY_UI_FONT, null))
    val uiFontPath: StateFlow<String?> = _uiFontPath.asStateFlow()

    fun setUiFontPath(path: String?) {
        _uiFontPath.value = path
        prefs.edit().putString(KEY_UI_FONT, path).apply()
    }

    // ---------- Visualizador de legendas ----------
    private val _lyricsFontSize = MutableStateFlow(prefs.getFloat(KEY_LYRICS_FONT_SIZE, 22f))
    val lyricsFontSize: StateFlow<Float> = _lyricsFontSize.asStateFlow()
    fun setLyricsFontSize(size: Float) {
        val v = size.coerceIn(12f, 48f)
        _lyricsFontSize.value = v
        prefs.edit().putFloat(KEY_LYRICS_FONT_SIZE, v).apply()
    }

    private val _lyricsPrimaryColor = MutableStateFlow(prefs.getInt(KEY_LYRICS_PRIMARY, 0xFFFFFFFF.toInt()))
    val lyricsPrimaryColor: StateFlow<Int> = _lyricsPrimaryColor.asStateFlow()
    fun setLyricsPrimaryColor(argb: Int) {
        _lyricsPrimaryColor.value = argb
        prefs.edit().putInt(KEY_LYRICS_PRIMARY, argb).apply()
    }

    private val _lyricsSecondaryColor = MutableStateFlow(prefs.getInt(KEY_LYRICS_SECONDARY, 0x66FFFFFF))
    val lyricsSecondaryColor: StateFlow<Int> = _lyricsSecondaryColor.asStateFlow()
    fun setLyricsSecondaryColor(argb: Int) {
        _lyricsSecondaryColor.value = argb
        prefs.edit().putInt(KEY_LYRICS_SECONDARY, argb).apply()
    }

    private val _lyricsBorderColor = MutableStateFlow(prefs.getInt(KEY_LYRICS_BORDER, 0xFF000000.toInt()))
    val lyricsBorderColor: StateFlow<Int> = _lyricsBorderColor.asStateFlow()
    fun setLyricsBorderColor(argb: Int) {
        _lyricsBorderColor.value = argb
        prefs.edit().putInt(KEY_LYRICS_BORDER, argb).apply()
        if (!prefs.contains(KEY_LYRICS_BACKING_BORDER)) _lyricsBackingBorderColor.value = argb
        if (!prefs.contains(KEY_LYRICS_TRANSLATION_BORDER)) _lyricsTranslationBorderColor.value = argb
    }

    private val _lyricsBorderWidth = MutableStateFlow(prefs.getFloat(KEY_LYRICS_BORDER_W, 0f))
    val lyricsBorderWidth: StateFlow<Float> = _lyricsBorderWidth.asStateFlow()
    fun setLyricsBorderWidth(w: Float) {
        val v = w.coerceIn(0f, 8f)
        _lyricsBorderWidth.value = v
        prefs.edit().putFloat(KEY_LYRICS_BORDER_W, v).apply()
        if (!prefs.contains(KEY_LYRICS_BACKING_BORDER_W)) _lyricsBackingBorderWidth.value = v
        if (!prefs.contains(KEY_LYRICS_TRANSLATION_BORDER_W)) _lyricsTranslationBorderWidth.value = v
    }

    // Contorno próprio do backing vocal e da tradução. Enquanto o usuário não personaliza,
    // seguem o contorno da letra principal (valor salvo só ao alterar).
    private val _lyricsBackingBorderColor = MutableStateFlow(
        prefs.getInt(KEY_LYRICS_BACKING_BORDER, prefs.getInt(KEY_LYRICS_BORDER, 0xFF000000.toInt()))
    )
    val lyricsBackingBorderColor: StateFlow<Int> = _lyricsBackingBorderColor.asStateFlow()
    fun setLyricsBackingBorderColor(argb: Int) {
        _lyricsBackingBorderColor.value = argb
        prefs.edit().putInt(KEY_LYRICS_BACKING_BORDER, argb).apply()
    }

    private val _lyricsBackingBorderWidth = MutableStateFlow(
        prefs.getFloat(KEY_LYRICS_BACKING_BORDER_W, prefs.getFloat(KEY_LYRICS_BORDER_W, 0f))
    )
    val lyricsBackingBorderWidth: StateFlow<Float> = _lyricsBackingBorderWidth.asStateFlow()
    fun setLyricsBackingBorderWidth(w: Float) {
        val v = w.coerceIn(0f, 8f)
        _lyricsBackingBorderWidth.value = v
        prefs.edit().putFloat(KEY_LYRICS_BACKING_BORDER_W, v).apply()
    }

    private val _lyricsTranslationBorderColor = MutableStateFlow(
        prefs.getInt(KEY_LYRICS_TRANSLATION_BORDER, prefs.getInt(KEY_LYRICS_BORDER, 0xFF000000.toInt()))
    )
    val lyricsTranslationBorderColor: StateFlow<Int> = _lyricsTranslationBorderColor.asStateFlow()
    fun setLyricsTranslationBorderColor(argb: Int) {
        _lyricsTranslationBorderColor.value = argb
        prefs.edit().putInt(KEY_LYRICS_TRANSLATION_BORDER, argb).apply()
    }

    private val _lyricsTranslationBorderWidth = MutableStateFlow(
        prefs.getFloat(KEY_LYRICS_TRANSLATION_BORDER_W, prefs.getFloat(KEY_LYRICS_BORDER_W, 0f))
    )
    val lyricsTranslationBorderWidth: StateFlow<Float> = _lyricsTranslationBorderWidth.asStateFlow()
    fun setLyricsTranslationBorderWidth(w: Float) {
        val v = w.coerceIn(0f, 8f)
        _lyricsTranslationBorderWidth.value = v
        prefs.edit().putFloat(KEY_LYRICS_TRANSLATION_BORDER_W, v).apply()
    }

    private val _lyricsTranslationColor = MutableStateFlow(
        prefs.getInt(KEY_LYRICS_TRANSLATION, 0xFFB0BEC5.toInt())
    )
    val lyricsTranslationColor: StateFlow<Int> = _lyricsTranslationColor.asStateFlow()
    fun setLyricsTranslationColor(argb: Int) {
        _lyricsTranslationColor.value = argb
        prefs.edit().putInt(KEY_LYRICS_TRANSLATION, argb).apply()
    }

    private val _lyricsBackingColor = MutableStateFlow(
        prefs.getInt(KEY_LYRICS_BACKING, 0xFFCE93D8.toInt())
    )
    val lyricsBackingColor: StateFlow<Int> = _lyricsBackingColor.asStateFlow()
    fun setLyricsBackingColor(argb: Int) {
        _lyricsBackingColor.value = argb
        prefs.edit().putInt(KEY_LYRICS_BACKING, argb).apply()
    }

    private val _lyricsBackingSecondaryColor = MutableStateFlow(
        prefs.getInt(KEY_LYRICS_BACKING_SECONDARY, 0x99CE93D8.toInt())
    )
    val lyricsBackingSecondaryColor: StateFlow<Int> = _lyricsBackingSecondaryColor.asStateFlow()
    fun setLyricsBackingSecondaryColor(argb: Int) {
        _lyricsBackingSecondaryColor.value = argb
        prefs.edit().putInt(KEY_LYRICS_BACKING_SECONDARY, argb).apply()
    }

    // ---------- Splash ----------
    private val _splashEnabled = MutableStateFlow(prefs.getBoolean(KEY_SPLASH_ENABLED, true))
    val splashEnabled: StateFlow<Boolean> = _splashEnabled.asStateFlow()
    fun setSplashEnabled(enabled: Boolean) {
        _splashEnabled.value = enabled
        prefs.edit().putBoolean(KEY_SPLASH_ENABLED, enabled).apply()
    }

    private val _splashSoundEnabled = MutableStateFlow(prefs.getBoolean(KEY_SPLASH_SOUND, true))
    val splashSoundEnabled: StateFlow<Boolean> = _splashSoundEnabled.asStateFlow()
    fun setSplashSoundEnabled(enabled: Boolean) {
        _splashSoundEnabled.value = enabled
        prefs.edit().putBoolean(KEY_SPLASH_SOUND, enabled).apply()
    }

    // ---------- Ícones flutuantes no fundo ----------
    private val _floatingIconsEnabled = MutableStateFlow(prefs.getBoolean(KEY_FLOATING_ICONS, true))
    val floatingIconsEnabled: StateFlow<Boolean> = _floatingIconsEnabled.asStateFlow()
    fun setFloatingIconsEnabled(enabled: Boolean) {
        _floatingIconsEnabled.value = enabled
        prefs.edit().putBoolean(KEY_FLOATING_ICONS, enabled).apply()
    }

    // ---------- Nível dos efeitos de vidro ----------
    private val _glassQuality = MutableStateFlow(
        GlassQuality.fromName(prefs.getString(KEY_GLASS_QUALITY, null)).also { GlassConfig.quality = it }
    )
    val glassQuality: StateFlow<GlassQuality> = _glassQuality.asStateFlow()
    fun setGlassQuality(quality: GlassQuality) {
        _glassQuality.value = quality
        GlassConfig.quality = quality
        prefs.edit().putString(KEY_GLASS_QUALITY, quality.name).apply()
    }

    // Fonte e cor dos textos dos botões da tela Início
    private val _homeButtonFontPath = MutableStateFlow(prefs.getString(KEY_HOME_BUTTON_FONT, null))
    val homeButtonFontPath: StateFlow<String?> = _homeButtonFontPath.asStateFlow()
    fun setHomeButtonFontPath(path: String?) {
        _homeButtonFontPath.value = path
        prefs.edit().putString(KEY_HOME_BUTTON_FONT, path).apply()
    }

    private val _homeButtonColor = MutableStateFlow(
        prefs.getInt(KEY_HOME_BUTTON_COLOR, 0xFFFFFFFF.toInt())
    )
    val homeButtonColor: StateFlow<Int> = _homeButtonColor.asStateFlow()
    fun setHomeButtonColor(argb: Int) {
        _homeButtonColor.value = argb
        prefs.edit().putInt(KEY_HOME_BUTTON_COLOR, argb).apply()
    }

    // Fonte e cores da foto gerada (trecho de letra)
    private val _shareFontPath = MutableStateFlow(prefs.getString(KEY_SHARE_FONT, null))
    val shareFontPath: StateFlow<String?> = _shareFontPath.asStateFlow()
    fun setShareFontPath(path: String?) {
        _shareFontPath.value = path
        prefs.edit().putString(KEY_SHARE_FONT, path).apply()
    }

    private val _shareBgColor = MutableStateFlow(
        prefs.getInt(KEY_SHARE_BG_COLOR, 0xFF121212.toInt())
    )
    val shareBgColor: StateFlow<Int> = _shareBgColor.asStateFlow()
    fun setShareBgColor(argb: Int) {
        _shareBgColor.value = argb
        prefs.edit().putInt(KEY_SHARE_BG_COLOR, argb).apply()
    }
    private val _shareTextColor = MutableStateFlow(
        prefs.getInt(KEY_SHARE_TEXT_COLOR, 0xFFF5F5FA.toInt())
    )
    val shareTextColor: StateFlow<Int> = _shareTextColor.asStateFlow()
    fun setShareTextColor(argb: Int) {
        _shareTextColor.value = argb
        prefs.edit().putInt(KEY_SHARE_TEXT_COLOR, argb).apply()
    }

    private val _shareBrandColor = MutableStateFlow(
        prefs.getInt(KEY_SHARE_BRAND_COLOR, 0xFFFFFFFF.toInt())
    )
    val shareBrandColor: StateFlow<Int> = _shareBrandColor.asStateFlow()
    fun setShareBrandColor(argb: Int) {
        _shareBrandColor.value = argb
        prefs.edit().putInt(KEY_SHARE_BRAND_COLOR, argb).apply()
    }

    /** Duração do fade in/out ao play/pause em milissegundos (0 = instantâneo). */
    private val _fadeMs = MutableStateFlow(prefs.getInt(KEY_FADE_MS, 300).coerceIn(0, 3000))
    val fadeMs: StateFlow<Int> = _fadeMs.asStateFlow()
    fun setFadeMs(ms: Int) {
        val v = ms.coerceIn(0, 3000)
        _fadeMs.value = v
        prefs.edit().putInt(KEY_FADE_MS, v).apply()
    }

    /** Crossfade entre faixas (ms). 0 = desligado. */
    private val _crossfadeMs = MutableStateFlow(prefs.getInt(KEY_CROSSFADE_MS, 0).coerceIn(0, 5000))
    val crossfadeMs: StateFlow<Int> = _crossfadeMs.asStateFlow()
    fun setCrossfadeMs(ms: Int) {
        val v = ms.coerceIn(0, 5000)
        _crossfadeMs.value = v
        prefs.edit().putInt(KEY_CROSSFADE_MS, v).apply()
    }

    /** Estilo visual da barra de progresso no Now Playing. */
    private val _seekBarStyle = MutableStateFlow(
        try {
            SeekBarStyle.valueOf(prefs.getString(KEY_SEEK_BAR_STYLE, SeekBarStyle.Standard.name)!!)
        } catch (_: Exception) {
            SeekBarStyle.Standard
        }
    )
    val seekBarStyle: StateFlow<SeekBarStyle> = _seekBarStyle.asStateFlow()
    fun setSeekBarStyle(style: SeekBarStyle) {
        _seekBarStyle.value = style
        prefs.edit().putString(KEY_SEEK_BAR_STYLE, style.name).apply()
    }

    /** Waveform usada quando a seekbar está no estilo Waveform (independente da do editor de trecho). */
    private val _seekBarWaveformTheme = MutableStateFlow(
        WaveformTheme.fromName(prefs.getString(KEY_SEEK_WAVEFORM_THEME, null))
    )
    val seekBarWaveformTheme: StateFlow<WaveformTheme> = _seekBarWaveformTheme.asStateFlow()
    fun setSeekBarWaveformTheme(theme: WaveformTheme) {
        _seekBarWaveformTheme.value = theme
        prefs.edit().putString(KEY_SEEK_WAVEFORM_THEME, theme.name).apply()
    }

    // ---------- Ordenação ----------
    enum class GroupSort { NAME, TRACK_COUNT, TOTAL_DURATION }
    enum class TrackSort { NAME, FILE_SIZE, DURATION, LAST_MODIFIED }
    enum class SortOrder { ASC, DESC }

    private val _groupSort = MutableStateFlow(
        try { GroupSort.valueOf(prefs.getString(KEY_GROUP_SORT, GroupSort.NAME.name)!!) }
        catch (_: Exception) { GroupSort.NAME }
    )
    val groupSort: StateFlow<GroupSort> = _groupSort.asStateFlow()
    private val _groupOrder = MutableStateFlow(
        try { SortOrder.valueOf(prefs.getString(KEY_GROUP_ORDER, SortOrder.ASC.name)!!) }
        catch (_: Exception) { SortOrder.ASC }
    )
    val groupOrder: StateFlow<SortOrder> = _groupOrder.asStateFlow()
    fun setGroupSort(sort: GroupSort, order: SortOrder) {
        _groupSort.value = sort
        _groupOrder.value = order
        prefs.edit().putString(KEY_GROUP_SORT, sort.name).putString(KEY_GROUP_ORDER, order.name).apply()
    }

    private val _trackSort = MutableStateFlow(
        try { TrackSort.valueOf(prefs.getString(KEY_TRACK_SORT, TrackSort.NAME.name)!!) }
        catch (_: Exception) { TrackSort.NAME }
    )
    val trackSort: StateFlow<TrackSort> = _trackSort.asStateFlow()
    private val _trackOrder = MutableStateFlow(
        try { SortOrder.valueOf(prefs.getString(KEY_TRACK_ORDER, SortOrder.ASC.name)!!) }
        catch (_: Exception) { SortOrder.ASC }
    )
    val trackOrder: StateFlow<SortOrder> = _trackOrder.asStateFlow()
    /** Lista da aba "Todas" ordenada fora da thread principal, sem ordenar duas vezes. */
    val sortedAllTracks: StateFlow<List<Track>> = combine(
        tracks, _trackSort, _trackOrder, _librarySettings
    ) { list, sort, order, settings ->
        sortTracks(list, sort, order, settings.firstLetterOrder)
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setTrackSort(sort: TrackSort, order: SortOrder) {
        _trackSort.value = sort
        _trackOrder.value = order
        prefs.edit().putString(KEY_TRACK_SORT, sort.name).putString(KEY_TRACK_ORDER, order.name).apply()
    }

    fun sortedTracks(list: List<Track>): List<Track> {
        return sortTracks(list, _trackSort.value, _trackOrder.value, _librarySettings.value.firstLetterOrder)
    }

    private fun sortTracks(
        list: List<Track>,
        sort: TrackSort,
        order: SortOrder,
        firstLetterOrder: Boolean
    ): List<Track> {
        val sorted = when (sort) {
            TrackSort.NAME -> list.map { AlphaSort.key(it.title, firstLetterOrder) to it }
                .sortedBy { it.first }.map { it.second }
            TrackSort.FILE_SIZE -> list.sortedBy { it.fileSize }
            TrackSort.DURATION -> list.sortedBy { it.duration }
            TrackSort.LAST_MODIFIED -> list.sortedBy { it.lastModified }
        }
        return if (order == SortOrder.ASC) sorted else sorted.asReversed()
    }

    fun sortedArtists(list: List<Artist>): List<Artist> {
        val asc = _groupOrder.value == SortOrder.ASC
        val sorted = when (_groupSort.value) {
            GroupSort.NAME -> list.sortedBy { AlphaSort.key(it.name, _librarySettings.value.firstLetterOrder) }
            GroupSort.TRACK_COUNT -> list.sortedBy { it.trackCount }
            GroupSort.TOTAL_DURATION -> list.sortedBy { it.totalDuration }
        }
        return if (asc) sorted else sorted.asReversed()
    }

    fun sortedAlbums(list: List<Album>): List<Album> {
        val asc = _groupOrder.value == SortOrder.ASC
        val sorted = when (_groupSort.value) {
            GroupSort.NAME -> list.sortedBy { AlphaSort.key(it.name, _librarySettings.value.firstLetterOrder) }
            GroupSort.TRACK_COUNT -> list.sortedBy { it.trackCount }
            GroupSort.TOTAL_DURATION -> list.sortedBy { it.totalDuration }
        }
        return if (asc) sorted else sorted.asReversed()
    }

    fun sortedGenres(list: List<Genre>): List<Genre> {
        val asc = _groupOrder.value == SortOrder.ASC
        val sorted = when (_groupSort.value) {
            GroupSort.NAME -> list.sortedBy { AlphaSort.key(it.name, _librarySettings.value.firstLetterOrder) }
            GroupSort.TRACK_COUNT -> list.sortedBy { it.trackCount }
            GroupSort.TOTAL_DURATION -> list.sortedBy { it.totalDuration }
        }
        return if (asc) sorted else sorted.asReversed()
    }

    // ---------- Persistência da fila ----------
    /** Cache do JSON da fila: só é reconstruído quando a lista da fila muda (não a cada troca de faixa). */
    @Volatile private var queueJsonCache: Pair<List<Track>, String>? = null
    private var persistJob: Job? = null
    private var playbackRestoreInProgress = false
    private var playbackRestoreCompleted = false

    /**
     * Salva fila/posição para restaurar ao reabrir o app.
     *
     * Antes isso montava o JSON da fila INTEIRA na thread principal e regravava as preferências
     * 3-4 vezes por troca de faixa (next + onMediaItemTransition + onIsPlayingChanged...). Com a
     * fila grande e várias trocas seguidas, a thread principal engasgava e o app/sistema travava.
     * Agora só tira um instantâneo barato na main e grava (com debounce) fora dela.
     */
    private fun persistPlaybackState(paused: Boolean, immediate: Boolean = false) {
        val queue = _queue.value
        val nowId = _nowPlaying.value?.id
        // Ao fechar o app/serviço o player é limpo (faixa atual = null) mas a fila continua na
        // memória: não sobrescreve o que foi salvo, senão a música atual se perde ao reabrir.
        if (queue.isNotEmpty() && nowId == null) return
        // Antes de a fila salva ser restaurada, não grava uma fila vazia por cima dela.
        if (queue.isEmpty() && !playbackRestoreCompleted) return
        val pos = try {
            controller?.takeIf { it.mediaItemCount > 0 }?.currentPosition ?: _positionMs.value
        } catch (_: Exception) { _positionMs.value }
        if (immediate) {
            persistPlaybackStateNow(queue, nowId, pos, paused)
            return
        }
        persistJob?.cancel()
        persistJob = viewModelScope.launch(Dispatchers.Default) {
            delay(400) // várias trocas rápidas viram uma única gravação
            persistPlaybackStateNow(queue, nowId, pos, paused)
        }
    }

    private fun persistPlaybackStateNow(queue: List<Track>, nowId: String?, pos: Long, paused: Boolean) {
        try {
            val cached = queueJsonCache
            val idsJson = if (cached != null && cached.first === queue) {
                cached.second
            } else {
                val ids = org.json.JSONArray()
                queue.forEach { ids.put(it.id) }
                ids.toString().also { queueJsonCache = queue to it }
            }
            val idx = if (nowId == null) -1 else queue.indexOfFirst { it.id == nowId }
            val externalTracks = org.json.JSONArray()
            queue.filter { it.id.startsWith("external_") }.forEach { track ->
                externalTracks.put(org.json.JSONObject().apply {
                    put("id", track.id)
                    put("title", track.title)
                    put("artist", track.artist)
                    put("album", track.album)
                    put("genre", track.genre)
                    put("duration", track.duration)
                    put("uri", track.uri.toString())
                    put("path", track.path)
                    put("folder", track.folder)
                    put("artPath", track.artPath ?: "")
                })
            }
            prefs.edit()
                .putString(KEY_QUEUE_IDS, idsJson)
                .putString(KEY_QUEUE_EXTERNAL_TRACKS, externalTracks.toString())
                .putString(KEY_QUEUE_CURRENT_ID, nowId)
                .putInt(KEY_QUEUE_INDEX, idx)
                .putLong(KEY_QUEUE_POS, pos)
                .putBoolean(KEY_QUEUE_PAUSED, paused)
                .apply()
        } catch (_: Exception) {}
    }

    private fun restorePlaybackState() {
        if (playbackRestoreCompleted || playbackRestoreInProgress) return
        playbackRestoreInProgress = true
        viewModelScope.launch {
            try {
                val idsRaw = prefs.getString(KEY_QUEUE_IDS, null)
                if (idsRaw == null) {
                    playbackRestoreCompleted = true
                    return@launch
                }
                val arr = org.json.JSONArray(idsRaw)
                if (arr.length() == 0) {
                    playbackRestoreCompleted = true
                    return@launch
                }
                var tries = 0
                while (controller == null && tries < 150) {
                    delay(100)
                    tries++
                }
                val ctrl = controller ?: return@launch
                // Se o serviço ainda está vivo, sua sessão em memória é a fonte mais recente.
                if (ctrl.mediaItemCount > 0) {
                    syncFromController()
                    playbackRestoreCompleted = true
                    return@launch
                }
                // Aguarda um scan em andamento para não restaurar uma fila parcial.
                while (_isScanning.value) delay(150)

                val ids = (0 until arr.length()).mapNotNull { originalIndex ->
                    arr.optString(originalIndex).takeIf { it.isNotBlank() }
                        ?.let { originalIndex to it }
                }
                val externalById = mutableMapOf<String, Track>()
                try {
                    val externalRaw = prefs.getString(KEY_QUEUE_EXTERNAL_TRACKS, null)
                    if (!externalRaw.isNullOrBlank()) {
                        val externalArray = org.json.JSONArray(externalRaw)
                        for (i in 0 until externalArray.length()) {
                            val item = externalArray.optJSONObject(i) ?: continue
                            val id = item.optString("id")
                            val uriText = item.optString("uri")
                            if (id.isBlank() || uriText.isBlank()) continue
                            val uri = Uri.parse(uriText)
                            externalById[id] = Track(
                                id = id,
                                title = item.optString("title", "Música"),
                                artist = item.optString("artist"),
                                album = item.optString("album"),
                                genre = item.optString("genre"),
                                duration = item.optLong("duration"),
                                uri = uri,
                                path = item.optString("path", uriText),
                                folder = item.optString("folder", "Arquivo externo"),
                                artPath = item.optString("artPath").takeIf { it.isNotBlank() }
                            )
                        }
                    }
                } catch (_: Exception) { }
                val byId = _library.value.associateBy { it.id }
                val restored = ids.mapNotNull { (originalIndex, id) ->
                    (byId[id] ?: externalById[id])?.let { originalIndex to it }
                }
                val q = restored.map { it.second }
                if (q.isEmpty()) return@launch
                // Não sobrescreve uma fila que o usuário iniciou enquanto o scan terminava.
                if (ctrl.mediaItemCount > 0) {
                    syncFromController()
                    playbackRestoreCompleted = true
                    return@launch
                }

                val savedIndex = prefs.getInt(KEY_QUEUE_INDEX, 0)
                val savedCurrentId = prefs.getString(KEY_QUEUE_CURRENT_ID, null)
                val currentIndex = savedCurrentId
                    ?.let { id -> q.indexOfFirst { it.id == id }.takeIf { it >= 0 } }
                    ?: restored.indexOfFirst { it.first == savedIndex }.takeIf { it >= 0 }
                    ?: 0
                val pos = prefs.getLong(KEY_QUEUE_POS, 0L).coerceAtLeast(0L)
                // Ao reabrir, a fila volta pronta na música/posição salvas, mas pausada.
                val wasPlaying = false

                _queue.value = q
                _nowPlaying.value = q[currentIndex]
                _positionMs.value = pos
                _durationMs.value = q[currentIndex].duration
                _isPlaying.value = wasPlaying
                _playbackButtonIsPlaying.value = wasPlaying
                ctrl.setMediaItems(q.map { it.toMediaItem() }, currentIndex, pos)
                ctrl.prepare()
                if (wasPlaying) {
                    ctrl.play()
                    startPositionTicker()
                } else {
                    ctrl.pause()
                }
                persistPlaybackStateNow(q, q[currentIndex].id, pos, paused = !wasPlaying)
                playbackRestoreCompleted = true
            } catch (e: Exception) {
                android.util.Log.w("MusicVM", "Falha ao restaurar fila de reprodução", e)
            } finally {
                playbackRestoreInProgress = false
            }
        }
    }

    // ---------- Backup ----------
    data class BackupOptions(
        val preferences: Boolean = true,
        val playlistsFavorites: Boolean = true,
        val playHistory: Boolean = true,
        val playCounts: Boolean = true,
        val librarySettings: Boolean = true,
        val appearance: Boolean = true,
        val artistPhotos: Boolean = true,
        val trackBlacklist: Boolean = true
    )

    @Volatile
    var pendingBackupOptions: BackupOptions = BackupOptions()

    fun exportBackup(options: BackupOptions = BackupOptions()): String {

        val obj = org.json.JSONObject()
        obj.put("version", 3)
        obj.put("backupOptions", org.json.JSONObject().apply {
            put("preferences", options.preferences)
            put("playlistsFavorites", options.playlistsFavorites)
            put("playHistory", options.playHistory)
            put("playCounts", options.playCounts)
            put("librarySettings", options.librarySettings)
            put("appearance", options.appearance)
            put("artistPhotos", options.artistPhotos)
            put("trackBlacklist", options.trackBlacklist)
        })
        obj.put("theme", _themeId.value.name)
        obj.put("waveformTheme", _waveformTheme.value.name)
        obj.put("lyricsFont", _lyricsFontPath.value)
        obj.put("lyricsEmphasisFont", _lyricsEmphasisFontPath.value)
        obj.put("uiFont", _uiFontPath.value)
        obj.put("lyricsFontSize", _lyricsFontSize.value.toDouble())
        obj.put("homeButtonFont", _homeButtonFontPath.value)
        obj.put("homeButtonColor", _homeButtonColor.value)
        obj.put("shareFont", _shareFontPath.value)
        obj.put("shareTextColor", _shareTextColor.value)
        obj.put("shareBrandColor", _shareBrandColor.value)
        obj.put("lyricsPrimary", _lyricsPrimaryColor.value)
        obj.put("lyricsSecondary", _lyricsSecondaryColor.value)
        obj.put("lyricsBackingFont", _lyricsBackingFontPath.value)
        obj.put("lyricsBackingPrimary", _lyricsBackingColor.value)
        obj.put("lyricsBackingSecondary", _lyricsBackingSecondaryColor.value)
        obj.put("lyricsBorder", _lyricsBorderColor.value)
        obj.put("lyricsBorderWidth", _lyricsBorderWidth.value.toDouble())
        obj.put("lyricsBackingBorder", _lyricsBackingBorderColor.value)
        obj.put("lyricsBackingBorderWidth", _lyricsBackingBorderWidth.value.toDouble())
        obj.put("lyricsTranslationBorder", _lyricsTranslationBorderColor.value)
        obj.put("lyricsTranslationBorderWidth", _lyricsTranslationBorderWidth.value.toDouble())
        obj.put("autoFetchLyrics", _autoFetchLyrics.value)
        obj.put("shuffleType", _shuffleType.value.name)
        obj.put("bitPerfect", _bitPerfect.value)
        obj.put("audioOutput", _audioOutput.value.name)
        obj.put("preferredSampleRate", _preferredSampleRate.value)
        obj.put("allFilesMode", _allFilesMode.value)
        obj.put("splitArtists", _librarySettings.value.splitArtists)
        obj.put("artistSeparators", _librarySettings.value.artistSeparators)
        obj.put("artistExceptions", _librarySettings.value.artistExceptions)
        obj.put("splitGenres", _librarySettings.value.splitGenres)
        obj.put("genreSeparators", _librarySettings.value.genreSeparators)
        obj.put("ignoredFolders", _librarySettings.value.ignoredFolders)
        obj.put("minDurationSec", _librarySettings.value.minDurationSec)
        obj.put("fadeMs", _fadeMs.value)
        obj.put("crossfadeMs", _crossfadeMs.value)
        obj.put("seekBarStyle", _seekBarStyle.value.name)
        obj.put("seekBarWaveformTheme", _seekBarWaveformTheme.value.name)
        obj.put("immersiveNowPlaying", _immersiveNowPlaying.value)
        obj.put("milkIntervalSec", _milkIntervalSec.value)
        obj.put("milkSwitchMode", _milkSwitchMode.value)
        obj.put("milkPresetOrder", _milkPresetOrder.value)
        obj.put("milkFavoritesOnly", _milkFavoritesOnly.value)
        obj.put("playCountThreshold", _playCountThreshold.value)
        obj.put("playCountMinutes", _playCountMinutes.value)
        obj.put("splashEnabled", _splashEnabled.value)
        obj.put("splashSoundEnabled", _splashSoundEnabled.value)
        obj.put("floatingIconsEnabled", _floatingIconsEnabled.value)
        obj.put("glassQuality", _glassQuality.value.name)
        obj.put("menuVisualStyle", _menuVisualStyle.value.name)
        obj.put("dynamicDefaultColor", _dynamicDefaultColor.value)
        obj.put("groupSort", prefs.getString(KEY_GROUP_SORT, null))
        obj.put("groupOrder", prefs.getString(KEY_GROUP_ORDER, null))
        obj.put("trackSort", prefs.getString(KEY_TRACK_SORT, null))
        obj.put("trackOrder", prefs.getString(KEY_TRACK_ORDER, null))
        if (options.playHistory || options.playCounts) {
            if (options.playHistory) {
                obj.put("history", _historyIds.value.joinToString("\n"))
                obj.put(
                    "playEvents",
                    _playEvents.value.joinToString("\n") { "${it.first}\t${it.second}" }
                )
                val lasts = org.json.JSONObject()
                _lastPlayedAt.value.forEach { (k, v) -> lasts.put(k, v) }
                obj.put("lastPlayedAt", lasts)
                val firsts = org.json.JSONObject()
                _firstPlayedAt.value.forEach { (k, v) -> firsts.put(k, v) }
                obj.put("firstPlayedAt", firsts)
            }
            if (options.playCounts) {
                val counts = org.json.JSONObject()
                _playCounts.value.forEach { (k, v) -> counts.put(k, v) }
                obj.put("playCounts", counts)
            }
        }
        if (options.trackBlacklist) {
            obj.put("trackBlacklist", _trackBlacklist.value.joinToString("\n"))
        }
        val folders = org.json.JSONArray()
        _treeUris.value.forEach { folders.put(it) }
        obj.put("folders", folders)
        val favs = org.json.JSONArray()
        try {
            _playlists.value.find { it.isFavorites }?.trackIds?.forEach { favs.put(it) }
        } catch (_: Exception) {}
        obj.put("favorites", favs)
        // Playlists (exceto a lista de favoritos embutida, já coberta por favorites)
        try {
            val pls = org.json.JSONArray()
            _playlists.value.filter { !it.isFavorites }.forEach { pl ->
                val o = org.json.JSONObject()
                o.put("id", pl.id)
                o.put("name", pl.name)
                o.put("trackIds", org.json.JSONArray(pl.trackIds))
                pls.put(o)
            }
            obj.put("playlists", pls)
        } catch (_: Exception) {}
        // Favoritos de presets .milk
        try {
            val milkFavs = prefs.getStringSet("milk_preset_favs", emptySet()) ?: emptySet()
            val arr = org.json.JSONArray()
            milkFavs.forEach { arr.put(it) }
            obj.put("milkPresetFavorites", arr)
        } catch (_: Exception) {}
        // Metadados de artistas (texto/foto URL) — fotos locais vão no ZIP separado
        try {
            obj.put("artistMeta", org.json.JSONObject(ArtistMetaStore.exportAll(getApplication())))
        } catch (_: Exception) {}
        return obj.toString(2)
    }

    /** Gera um ZIP com preferences.json + fotos de artistas em artist_photos/. */
    fun exportBackupZip(destFile: java.io.File, options: BackupOptions = pendingBackupOptions): Boolean {
        return try {
            val json = exportBackup(options)
            java.util.zip.ZipOutputStream(java.io.FileOutputStream(destFile)).use { zos ->
                zos.putNextEntry(java.util.zip.ZipEntry("preferences.json"))
                zos.write(json.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
                fun putDir(dir: java.io.File, zipPrefix: String) {
                    if (!dir.isDirectory) return
                    dir.listFiles()?.forEach { f ->
                        if (f.isFile && f.name != ".nomedia") {
                            zos.putNextEntry(java.util.zip.ZipEntry("$zipPrefix/${f.name}"))
                            f.inputStream().use { it.copyTo(zos) }
                            zos.closeEntry()
                        }
                    }
                }
                if (options.artistPhotos) {
                    putDir(com.tolete.player.data.LyronStorage.artistPhotos, "artist_photos")
                    putDir(com.tolete.player.data.LyronStorage.artistInfos, "artist_infos")
                    putDir(com.tolete.player.data.LyronStorage.albumInfos, "album_infos")
                }
                if (options.playlistsFavorites) {
                    putDir(com.tolete.player.data.LyronStorage.playlistsDir, "playlists")
                    val fav = com.tolete.player.data.LyronStorage.favoritesFile
                    if (fav.isFile) {
                        zos.putNextEntry(java.util.zip.ZipEntry("favorites.json"))
                        fav.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
                if (options.playHistory) {
                    val hist = com.tolete.player.data.LyronStorage.playbackHistory
                    if (hist.isFile) {
                        zos.putNextEntry(java.util.zip.ZipEntry("playback history.csv"))
                        hist.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun importBackupZip(bytes: ByteArray, options: BackupOptions = pendingBackupOptions): Boolean {
        return try {
            var json: String? = null
            val restoredFiles = mutableSetOf<String>()
            java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name.replace("\\", "/")
                    if (name == "preferences.json" || name.endsWith("/preferences.json")) {
                        json = zis.readBytes().toString(Charsets.UTF_8)
                    } else if (!entry.isDirectory) {
                        val base = name.substringAfterLast('/')
                        if (base.isNotBlank()) {
                            val dest = when {
                                options.artistPhotos && ("artist_photos/" in name) ->
                                    java.io.File(com.tolete.player.data.LyronStorage.artistPhotos, base)
                                options.artistPhotos && ("artist_infos/" in name) ->
                                    java.io.File(com.tolete.player.data.LyronStorage.artistInfos, base)
                                options.artistPhotos && ("album_infos/" in name) ->
                                    java.io.File(com.tolete.player.data.LyronStorage.albumInfos, base)
                                options.playlistsFavorites && ("playlists/" in name) ->
                                    java.io.File(com.tolete.player.data.LyronStorage.playlistsDir, base)
                                options.playlistsFavorites && base == "favorites.json" ->
                                    com.tolete.player.data.LyronStorage.favoritesFile
                                options.playHistory && base == "playback history.csv" ->
                                    com.tolete.player.data.LyronStorage.playbackHistory
                                else -> null
                            }
                            if (dest != null) {
                                dest.parentFile?.mkdirs()
                                dest.outputStream().use { out -> zis.copyTo(out) }
                                restoredFiles.add(base)
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            val okJson = if (json != null) importBackup(json!!, options) else false
            if (options.artistPhotos) {
                try { com.tolete.player.data.ArtistMetaStore.rebuildPhotoIndex() } catch (_: Exception) {}
            }
            if (options.playHistory) {
                try { reloadStatsFromCsv() } catch (_: Exception) {}
            }
            if (options.playlistsFavorites) {
                try { loadPlaylists() } catch (_: Exception) {}
            }
            okJson || restoredFiles.isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

        fun importBackup(json: String, options: BackupOptions = pendingBackupOptions): Boolean {

        return try {
            val obj = org.json.JSONObject(json)
            obj.optString("theme").takeIf { it.isNotBlank() }?.let {
                try { setThemeId(AppThemeId.valueOf(it)) } catch (_: Exception) {}
            }
            obj.optString("waveformTheme").takeIf { it.isNotBlank() }?.let {
                setWaveformTheme(WaveformTheme.fromName(it))
            }
            if (obj.has("lyricsFont")) setMainLyricsFontPath(obj.optString("lyricsFont").ifBlank { null })
            if (obj.has("lyricsEmphasisFont")) setLyricsEmphasisFontPath(obj.optString("lyricsEmphasisFont").ifBlank { null })
            if (obj.has("lyricsBackingFont")) setLyricsBackingFontPath(obj.optString("lyricsBackingFont").ifBlank { null })
            if (obj.has("uiFont")) setUiFontPath(obj.optString("uiFont").ifBlank { null })
            if (obj.has("lyricsFontSize")) setLyricsFontSize(obj.getDouble("lyricsFontSize").toFloat())
            if (obj.has("homeButtonFont")) setHomeButtonFontPath(obj.optString("homeButtonFont").ifBlank { null })
            if (obj.has("homeButtonColor")) setHomeButtonColor(obj.getInt("homeButtonColor"))
            if (obj.has("shareFont")) setShareFontPath(obj.optString("shareFont").ifBlank { null })
            if (obj.has("shareTextColor")) setShareTextColor(obj.getInt("shareTextColor"))
            if (obj.has("shareBrandColor")) setShareBrandColor(obj.getInt("shareBrandColor"))
            if (obj.has("lyricsPrimary")) setLyricsPrimaryColor(obj.getInt("lyricsPrimary"))
            if (obj.has("lyricsSecondary")) setLyricsSecondaryColor(obj.getInt("lyricsSecondary"))
            if (obj.has("lyricsBackingPrimary")) setLyricsBackingColor(obj.getInt("lyricsBackingPrimary"))
            if (obj.has("lyricsBackingSecondary")) setLyricsBackingSecondaryColor(obj.getInt("lyricsBackingSecondary"))
            if (obj.has("lyricsBorder")) setLyricsBorderColor(obj.getInt("lyricsBorder"))
            if (obj.has("lyricsBorderWidth")) setLyricsBorderWidth(obj.getDouble("lyricsBorderWidth").toFloat())
            if (obj.has("lyricsBackingBorder")) setLyricsBackingBorderColor(obj.getInt("lyricsBackingBorder"))
            if (obj.has("lyricsBackingBorderWidth")) setLyricsBackingBorderWidth(obj.getDouble("lyricsBackingBorderWidth").toFloat())
            if (obj.has("lyricsTranslationBorder")) setLyricsTranslationBorderColor(obj.getInt("lyricsTranslationBorder"))
            if (obj.has("lyricsTranslationBorderWidth")) setLyricsTranslationBorderWidth(obj.getDouble("lyricsTranslationBorderWidth").toFloat())
            if (obj.has("autoFetchLyrics")) setAutoFetchLyrics(obj.getBoolean("autoFetchLyrics"))
            if (obj.has("shuffleType")) {
                try { setShuffleType(ShuffleType.valueOf(obj.getString("shuffleType"))) } catch (_: Exception) {}
            }
            if (obj.has("audioOutput")) {
                try {
                    val backend = when (com.tolete.player.data.AudioOutputPrefs.normalize(obj.getString("audioOutput"))) {
                        com.tolete.player.data.AudioOutputPrefs.FIDELITY -> AudioOutputBackend.FIDELITY
                        else -> AudioOutputBackend.DEFAULT
                    }
                    setAudioOutput(backend)
                } catch (_: Exception) {}
            }
            if (obj.has("preferredSampleRate")) setPreferredSampleRate(obj.getInt("preferredSampleRate"))
            if (obj.has("fadeMs")) setFadeMs(obj.getInt("fadeMs"))
            if (obj.has("crossfadeMs")) setCrossfadeMs(obj.getInt("crossfadeMs"))
            if (obj.has("seekBarStyle")) {
                try { setSeekBarStyle(SeekBarStyle.valueOf(obj.getString("seekBarStyle"))) } catch (_: Exception) {}
            }
            obj.optString("seekBarWaveformTheme").takeIf { it.isNotBlank() }?.let {
                setSeekBarWaveformTheme(WaveformTheme.fromName(it))
            }
            if (obj.has("immersiveNowPlaying")) setImmersiveNowPlaying(obj.getBoolean("immersiveNowPlaying"))
            if (obj.has("milkIntervalSec")) setMilkIntervalSec(obj.getInt("milkIntervalSec"))
            if (obj.has("milkSwitchMode")) setMilkSwitchMode(obj.getString("milkSwitchMode"))
            else if (obj.has("changePresetOnSongChange")) {
                setMilkSwitchMode(if (obj.getBoolean("changePresetOnSongChange")) "TRACK" else "TIME")
            }
            if (obj.has("milkPresetOrder")) setMilkPresetOrder(obj.getString("milkPresetOrder"))
            if (obj.has("milkFavoritesOnly")) setMilkFavoritesOnly(obj.getBoolean("milkFavoritesOnly"))
            if (obj.has("playCountThreshold")) setPlayCountThreshold(obj.getInt("playCountThreshold"))
            if (obj.has("playCountMinutes")) setPlayCountMinutes(obj.getInt("playCountMinutes"))
            if (obj.has("splashEnabled")) setSplashEnabled(obj.getBoolean("splashEnabled"))
            if (obj.has("splashSoundEnabled")) setSplashSoundEnabled(obj.getBoolean("splashSoundEnabled"))
            if (obj.has("floatingIconsEnabled")) setFloatingIconsEnabled(obj.getBoolean("floatingIconsEnabled"))
            if (obj.has("glassQuality")) setGlassQuality(GlassQuality.fromName(obj.getString("glassQuality")))
            if (obj.has("menuVisualStyle")) setMenuVisualStyle(MenuVisualStyle.fromName(obj.getString("menuVisualStyle")))
            if (obj.has("dynamicDefaultColor")) setDynamicDefaultColor(obj.getInt("dynamicDefaultColor"))
            if (obj.has("splitArtists")) setSplitArtists(obj.getBoolean("splitArtists"))
            if (obj.has("artistSeparators")) setArtistSeparators(obj.getString("artistSeparators"))
            if (obj.has("artistExceptions")) setArtistExceptions(obj.getString("artistExceptions"))
            if (obj.has("splitGenres")) setSplitGenres(obj.getBoolean("splitGenres"))
            if (obj.has("genreSeparators")) setGenreSeparators(obj.getString("genreSeparators"))
            if (obj.has("ignoredFolders")) setIgnoredFolders(obj.getString("ignoredFolders"))
            if (obj.has("minDurationSec")) setMinDurationSec(obj.getInt("minDurationSec"))
            if (obj.has("groupSort")) prefs.edit().putString(KEY_GROUP_SORT, obj.optString("groupSort")).apply()
            if (obj.has("groupOrder")) prefs.edit().putString(KEY_GROUP_ORDER, obj.optString("groupOrder")).apply()
            if (obj.has("trackSort")) prefs.edit().putString(KEY_TRACK_SORT, obj.optString("trackSort")).apply()
            if (obj.has("trackOrder")) prefs.edit().putString(KEY_TRACK_ORDER, obj.optString("trackOrder")).apply()
            if (options.playHistory && obj.has("history")) {
                _historyIds.value = obj.optString("history").split('\n').filter { it.isNotBlank() }
                prefs.edit().putString(KEY_HISTORY, _historyIds.value.joinToString("\n")).apply()
            }
            if (options.trackBlacklist && obj.has("trackBlacklist")) {
                val rawBl = obj.optString("trackBlacklist")
                val set = rawBl.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                _trackBlacklist.value = set
                prefs.edit().putString(KEY_TRACK_BLACKLIST, set.joinToString("\n")).apply()
            }
            if (options.playCounts && obj.has("playCounts")) {
                val c = obj.getJSONObject("playCounts")
                val map = mutableMapOf<String, Int>()
                c.keys().forEach { k -> map[k] = c.optInt(k) }
                _playCounts.value = map
                prefs.edit().putString(KEY_PLAY_COUNTS, map.entries.joinToString("\n") { "${it.key}=${it.value}" }).apply()
            }
            if (options.playHistory && obj.has("lastPlayedAt")) {
                val c = obj.getJSONObject("lastPlayedAt")
                val map = mutableMapOf<String, Long>()
                c.keys().forEach { k -> map[k] = c.optLong(k) }
                _lastPlayedAt.value = map
                prefs.edit().putString(KEY_LAST_PLAYED, map.entries.joinToString("\n") { "${it.key}=${it.value}" }).apply()
            }
            if (options.playHistory && obj.has("playEvents")) {
                val events = obj.optString("playEvents").lineSequence().mapNotNull { line ->
                    val parts = line.split('\t', limit = 2)
                    if (parts.size == 2) {
                        val ts = parts[1].toLongOrNull() ?: return@mapNotNull null
                        parts[0] to ts
                    } else null
                }.toList()
                _playEvents.value = events
                prefs.edit().putString(KEY_PLAY_EVENTS, events.joinToString("\n") { "${it.first}\t${it.second}" }).apply()
            }
            if (obj.has("folders")) {
                val arr = obj.getJSONArray("folders")
                val list = (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
                if (list.isNotEmpty()) {
                    _treeUris.value = list
                    prefs.edit().putStringSet(KEY_TREE_URIS, list.toSet()).apply()
                }
            }
            if (obj.has("milkPresetFavorites")) {
                val arr = obj.getJSONArray("milkPresetFavorites")
                val set = (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }.toSet()
                prefs.edit().putStringSet("milk_preset_favs", set).apply()
            }
            if (obj.has("favorites")) {
                try {
                    val arr = obj.getJSONArray("favorites")
                    val ids = (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
                    val list = _playlists.value.toMutableList()
                    val favIdx = list.indexOfFirst { it.isFavorites }
                    if (favIdx >= 0) {
                        list[favIdx] = list[favIdx].copy(trackIds = ids)
                    } else {
                        list.add(0, Playlist.favorites(ids))
                    }
                    _playlists.value = list
                    persistPlaylists()
                } catch (_: Exception) {}
            }
            if (obj.has("playlists")) {
                try {
                    val arr = obj.getJSONArray("playlists")
                    // Mescla playlists salvas (não sobrescreve favoritos)
                    val existing = _playlists.value.toMutableList()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val id = o.optString("id")
                        if (id.isBlank()) continue
                        val name = o.optString("name").ifBlank { "Playlist" }
                        val idsArr = o.optJSONArray("trackIds")
                        val trackIds = if (idsArr != null) {
                            (0 until idsArr.length()).mapNotNull { idsArr.optString(it).takeIf { s -> s.isNotBlank() } }
                        } else emptyList()
                        val idx = existing.indexOfFirst { it.id == id }
                        if (idx >= 0) {
                            existing[idx] = existing[idx].copy(name = name, trackIds = trackIds)
                        } else {
                            existing.add(
                                Playlist(id = id, name = name, trackIds = trackIds, isFavorites = false)
                            )
                        }
                    }
                    _playlists.value = existing
                    persistPlaylists()
                } catch (_: Exception) {}
            }
            if (obj.has("artistMeta")) {
                try { ArtistMetaStore.importAll(getApplication(), obj.getJSONObject("artistMeta").toString()) } catch (_: Exception) {}
            }
            if (obj.has("allFilesMode")) setAllFilesMode(obj.getBoolean("allFilesMode"))
            true
        } catch (_: Exception) {
            false
        }
    }

    private val favoritesOwner = object : FavoritesBridge.Owner {
        override fun isFavorite(trackId: String) = this@MusicViewModel.isFavorite(trackId)
        override fun toggleFavorite(trackId: String) = this@MusicViewModel.toggleFavorite(trackId)
    }

    init {
        FavoritesBridge.owner = favoritesOwner
        // Limpa a preferência do modo de biblioteca removido nesta versão.
        prefs.edit().remove("media_store_mode").apply()
        connectController()
        loadPlaylists()
        // Espelha desligar o popup pela notificação → UI / botão do Now Playing / settings
        prefs.registerOnSharedPreferenceChangeListener(popupPrefsListener)

        viewModelScope.launch {
            // pastas salvas (+ migração da antiga pasta única), só as que ainda têm permissão
            val saved = prefs.getStringSet(KEY_TREE_URIS, null)?.toMutableSet() ?: mutableSetOf()
            prefs.getString(KEY_LEGACY_TREE_URI, null)?.let {
                saved += it
                prefs.edit().remove(KEY_LEGACY_TREE_URI).apply()
            }
            val granted = application.contentResolver.persistedUriPermissions
                .filter { it.isReadPermission }
                .map { it.uri.toString() }
                .toSet()
            val valid = saved.filter { it in granted }
            _treeUris.value = valid
            saveFolders()

            // biblioteca em cache: abre na hora; depois scan rápido só de novos/alterados
            val cached = cache.load()
            val allFiles = _allFilesMode.value
            if (cached != null) {
                _library.value = withContext(Dispatchers.Default) {
                    cached.filter {
                        it.sourceTree in valid ||
                            (allFiles && it.sourceTree == "all_files")
                    }
                }
                syncFromController()
                // Restaura a fila salva assim que a biblioteca em cache estiver pronta
                if (_library.value.isNotEmpty() && controller != null) restorePlaybackState()
                if (allFiles && _library.value.none { it.sourceTree == "all_files" }) {
                    refreshAllFiles()
                } else if (valid.isNotEmpty() || allFiles) {
                    // em segundo plano: procura arquivos novos sem bloquear a UI
                    quickRescanInBackground()
                }
            } else if (allFiles) {
                refreshAllFiles()
            } else if (valid.isNotEmpty()) {
                refreshLibrary()
            }
            restorePlaybackState()
        }
    }

    override fun onCleared() {
        persistJob?.cancel()
        // síncrono: o viewModelScope já foi cancelado quando o onCleared roda
        persistPlaybackState(paused = !_isPlaying.value, immediate = true)
        if (FavoritesBridge.owner === favoritesOwner) FavoritesBridge.owner = null
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        super.onCleared()
    }

    private companion object {
        const val KEY_TREE_URIS = "tree_uris"
        const val KEY_LEGACY_TREE_URI = "tree_uri"
        const val KEY_THEME = "app_theme"
        const val KEY_DYNAMIC_DEFAULT_COLOR = "dynamic_default_color"
        const val KEY_DYNAMIC_FLOAT_COLORS = "dynamic_float_colors"
        const val KEY_PHOTO_THEME_PATH = "photo_theme_path"
        const val KEY_PHOTO_THEME_BLUR = "photo_theme_blur"
        const val KEY_DYNAMIC_ALBUM_BLUR = "dynamic_album_blur"
        const val KEY_ALPHA_SCROLLER_STYLE = "alphabet_scroller_style"
        const val KEY_WAVEFORM_THEME = "waveform_theme"
        const val KEY_HISTORY = "play_history"
        const val KEY_TRACK_BLACKLIST = "track_blacklist"
        const val KEY_PLAY_COUNTS = "play_counts"
        const val KEY_FIRST_PLAYED = "first_played_at"
        const val KEY_LAST_PLAYED = "last_played_at"
        const val KEY_PLAY_COUNT_THRESHOLD = "play_count_threshold"
        const val KEY_PLAY_COUNT_MINUTES = "play_count_minutes"
        const val KEY_LASTFM_USER = "lastfm_username"
        const val KEY_LIBRARY_ROOT = "library_root_path"
        const val HISTORY_MAX = 50
        const val KEY_LYRICS_FONT = "lyrics_font_path"
        const val KEY_LYRICS_TRANSLATION_FONT = "lyrics_translation_font_path"
        const val KEY_LYRICS_BACKING_FONT = "lyrics_backing_font_path"
        const val KEY_LYRICS_EMPHASIS_FONT = "lyrics_emphasis_font_path"
        const val KEY_UI_FONT = "ui_font_path"
        const val KEY_SPLIT_ARTISTS = "split_artists"
        const val KEY_ARTIST_SEPARATORS = "artist_separators"
        const val KEY_ARTIST_EXCEPTIONS = "artist_exceptions"
        const val KEY_SPLIT_GENRES = "split_genres"
        const val KEY_GENRE_SEPARATORS = "genre_separators"
        const val KEY_IGNORED_FOLDERS = "ignored_folders"
        const val KEY_MIN_DURATION_SEC = "min_duration_sec"
        const val KEY_FIRST_LETTER_ORDER = "first_letter_order"
        const val KEY_SEARCH_HISTORY = "library_search_history"
        const val KEY_ARTIST_AUTO_FETCH_MODE = "artist_auto_fetch_mode"
        const val KEY_ALL_FILES = "all_files_mode"
        const val KEY_AUTO_LYRICS = "auto_fetch_lyrics"
        const val KEY_AUTO_LYRICS_TARGET = "auto_fetch_lyrics_target"
        const val KEY_PREFER_NETEASE_ROMANIZATION = "prefer_netease_romanization"
        const val KEY_LYRICS_POPUP = "lyrics_popup_enabled"
        const val KEY_SEARCH_STAGGER_MS = "search_stagger_ms"
        const val KEY_LYRICS_TYPE_ORDER = "lyrics_type_order"
        const val KEY_LYRICS_SOURCE_ORDER = "lyrics_source_order"
        const val KEY_SHUFFLE_TYPE = "shuffle_type"
        const val KEY_QUEUE_CURRENT_ID = "queue_current_id"
        const val KEY_BIT_PERFECT = "bit_perfect"
        const val KEY_IMMERSIVE_NOW_PLAYING = "immersive_now_playing"
        const val KEY_MILK_INTERVAL = "milk_interval_sec"
        const val KEY_MILK_SWITCH_MODE = "milk_switch_mode"
        const val KEY_MILK_PRESET_ORDER = "milk_preset_order"
        const val KEY_MILK_FAVORITES_ONLY = "milk_favorites_only"
        // Chaves legadas preservadas apenas para migrar a instalação para o modo equivalente.
        const val KEY_MILK_CHANGE_SONG = "milk_change_song_with_interval"
        const val KEY_MILK_PRESET_ON_SONG = "milk_preset_on_song_change"
        const val KEY_AUDIO_OUTPUT = com.tolete.player.data.AudioOutputPrefs.KEY
        const val KEY_FORCE_MAX_SR = "force_max_sample_rate" // legado
        const val KEY_PREFERRED_SAMPLE_RATE = "preferred_sample_rate"
        const val KEY_PLAY_EVENTS = "play_events_log"
        private const val PLAY_EVENTS_MAX = 8000
        const val KEY_LYRICS_FONT_SIZE = "lyrics_font_size"
        const val KEY_LYRICS_PRIMARY = "lyrics_primary_color"
        const val KEY_LYRICS_SECONDARY = "lyrics_secondary_color"
        const val KEY_LYRICS_BORDER = "lyrics_border_color"
        const val KEY_LYRICS_BORDER_W = "lyrics_border_width"
        const val KEY_LYRICS_BACKING_BORDER = "lyrics_backing_border_color"
        const val KEY_LYRICS_BACKING_BORDER_W = "lyrics_backing_border_width"
        const val KEY_LYRICS_TRANSLATION_BORDER = "lyrics_translation_border_color"
        const val KEY_LYRICS_TRANSLATION_BORDER_W = "lyrics_translation_border_width"
        const val KEY_LYRICS_TRANSLATION = "lyrics_translation_color"
        const val KEY_LYRICS_BACKING = "lyrics_backing_color"
        const val KEY_LYRICS_BACKING_SECONDARY = "lyrics_backing_secondary_color"
        const val KEY_SPLASH_ENABLED = "splash_enabled"
        const val KEY_SPLASH_SOUND = "splash_sound_enabled"
        const val KEY_FLOATING_ICONS = "floating_icons_enabled"
        const val KEY_GLASS_QUALITY = "glass_quality"
        const val KEY_MENU_VISUAL_STYLE = "menu_visual_style"
        const val KEY_HOME_BUTTON_FONT = "home_button_font_path"
        const val KEY_HOME_BUTTON_COLOR = "home_button_color"
        const val KEY_SHARE_FONT = "share_font_path"
        const val KEY_SHARE_TEXT_COLOR = "share_text_color"
        const val KEY_SHARE_BG_COLOR = "share_bg_color"
        const val KEY_SHARE_BRAND_COLOR = "share_brand_color"
        const val KEY_FADE_MS = "fade_ms"
        const val KEY_CROSSFADE_MS = "crossfade_ms"
        const val KEY_SEEK_BAR_STYLE = "seek_bar_style"
        const val KEY_SEEK_WAVEFORM_THEME = "seek_bar_waveform_theme"
        const val KEY_GROUP_SORT = "group_sort"
        const val KEY_GROUP_ORDER = "group_order"
        const val KEY_TRACK_SORT = "track_sort"
        const val KEY_TRACK_ORDER = "track_order"
        const val KEY_QUEUE_IDS = "queue_ids"
        const val KEY_QUEUE_EXTERNAL_TRACKS = "queue_external_tracks"
        const val KEY_QUEUE_INDEX = "queue_index"
        const val KEY_QUEUE_POS = "queue_pos"
        const val KEY_QUEUE_PAUSED = "queue_paused"
    }

    /** Renomeia o arquivo da faixa no disco (mantém extensão) e atualiza a biblioteca. */
    fun renameTrackFile(track: Track, newBaseName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val src = File(track.path)
                if (!src.isFile) return@launch
                val safe = newBaseName.replace(Regex("[\\/:*?\"<>|]"), "_").trim()
                if (safe.isBlank()) return@launch
                val dest = File(src.parentFile, safe + "." + src.extension)
                if (dest.exists() && dest.absolutePath != src.absolutePath) return@launch
                if (src.renameTo(dest)) {
                    val updated = track.copy(
                        title = track.title, // tags intactas
                        path = dest.absolutePath,
                        uri = Uri.fromFile(dest)
                    )
                    // Atualiza lista em memória
                    _library.value = _library.value.map { if (it.id == track.id) updated else it }
                    try {
                        LibraryCache(getApplication()).save(_library.value)
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
    }


    suspend fun playExternalUri(track: Track): Boolean {
        // Aguarda o MediaController ficar pronto antes de permitir o salto para Now Playing.
        var tries = 0
        while (controller == null && tries < 40) {
            kotlinx.coroutines.delay(100)
            tries++
        }
        val c = controller ?: return false
        return try {
            _queue.value = listOf(track)
            _nowPlaying.value = track
            c.setMediaItem(track.toMediaItem())
            c.prepare()
            requestPlaybackButtonState(playing = true)
            c.play()
            startPositionTicker()
            persistPlaybackState(paused = false)
            true
        } catch (_: Exception) {
            false
        }
    }

}
