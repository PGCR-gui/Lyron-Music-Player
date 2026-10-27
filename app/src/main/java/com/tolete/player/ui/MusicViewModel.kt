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
import com.tolete.player.data.FavoritesBridge
import com.tolete.player.data.FavoritesFile
import com.tolete.player.data.Genre
import com.tolete.player.data.LibraryCache
import com.tolete.player.data.LyricLine
import com.tolete.player.data.LyricsExtractor
import com.tolete.player.data.LyricsEmbedder
import com.tolete.player.data.LibrarySettings
import com.tolete.player.data.LrcLibClient
import com.tolete.player.data.Playlist
import com.tolete.player.data.SourceFolder
import com.tolete.player.data.MusicScanner
import com.tolete.player.data.TagSplitter
import com.tolete.player.data.TagWriter
import com.tolete.player.data.Track
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
    private val playlistsFile = File(application.filesDir, "playlists.json")

    /** Biblioteca crua (pode ter a mesma faixa duas vezes se pastas se sobrepõem). */
    private val _library = MutableStateFlow<List<Track>>(emptyList())

    /** Biblioteca sem duplicatas: é o que o resto do app enxerga. */
    val tracks: StateFlow<List<Track>> = _library
        .map { list -> list.distinctBy { it.id } }
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
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _currentTab = MutableStateFlow(Tab.HOME)
    val currentTab: StateFlow<Tab> = _currentTab.asStateFlow()

    private val _filter = MutableStateFlow<Filter?>(null)
    val filter: StateFlow<Filter?> = _filter.asStateFlow()

    // ---------- Busca ----------
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    fun setSearchQuery(q: String) {
        _searchQuery.value = q
    }

    fun openSearch() {
        _isSearching.value = true
        _searchQuery.value = ""
    }

    fun closeSearch() {
        _isSearching.value = false
        _searchQuery.value = ""
    }

    val searchResults: StateFlow<List<Track>> = combine(tracks, _searchQuery) { list, q ->
        if (q.isBlank()) emptyList()
        else {
            val lower = q.lowercase()
            list.filter {
                it.title.lowercase().contains(lower) ||
                    it.artist.lowercase().contains(lower) ||
                    it.album.lowercase().contains(lower)
            }.sortedBy { it.title.lowercase() }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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
    fun importPlaylistM3u(name: String, content: String): M3uImportResult {
        val lib = tracks.value
        val entries = parseM3u(content)
        if (entries.isEmpty()) return M3uImportResult(null, 0, 0)
        if (lib.isEmpty()) return M3uImportResult(null, 0, entries.size)

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
        if (ids.isEmpty()) return M3uImportResult(null, 0, entries.size)
        val id = createPlaylist(name.ifBlank { "Importada" }, ids.toList())
        return M3uImportResult(id, matched, entries.size)
    }

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
                val json = JSONObject().put("v", 1).put("playlists", arr).toString()
                val tmp = File(playlistsFile.parentFile, "playlists.json.tmp")
                tmp.writeText(json)
                if (playlistsFile.exists()) {
                    try { playlistsFile.copyTo(File(playlistsFile.parentFile, "playlists.json.bak"), overwrite = true) } catch (_: Exception) {}
                }
                if (!tmp.renameTo(playlistsFile)) {
                    playlistsFile.writeText(json) // nunca apaga antes de ter o novo conteúdo salvo
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
        if (loaded != null) {
            _playlists.value = loaded
            FavoritesBridge.notifyChanged()
        }
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
            minDurationSec = prefs.getInt(KEY_MIN_DURATION_SEC, 0).coerceIn(0, 50)
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

    /** Aplica blacklist de pastas e duração mínima sobre a lista escaneada. */
    private fun filterLibrary(tracks: List<Track>): List<Track> {
        val st = _librarySettings.value
        val ignored = st.ignoredFolders
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val minMs = if (st.minDurationSec > 0) st.minDurationSec * 1000L else 0L
        return tracks.filter { t ->
            if (minMs > 0 && t.duration > 0 && t.duration < minMs) return@filter false
            if (ignored.isNotEmpty()) {
                val path = t.path
                if (ignored.any { ign -> path.contains(ign, ignoreCase = true) }) return@filter false
            }
            true
        }
    }

    // ---------- Acesso a todos os arquivos (MediaStore) ----------
    private val _allFilesMode = MutableStateFlow(prefs.getBoolean(KEY_ALL_FILES, false))
    val allFilesMode: StateFlow<Boolean> = _allFilesMode.asStateFlow()

    fun setAllFilesMode(enabled: Boolean) {
        _allFilesMode.value = enabled
        prefs.edit().putBoolean(KEY_ALL_FILES, enabled).apply()
        if (enabled) {
            refreshAllFiles()
        } else {
            _library.value = _library.value.filter { it.sourceTree != "all_files" }
            viewModelScope.launch { scanMutex.withLock { cache.save(_library.value) } }
            if (_treeUris.value.isNotEmpty()) refreshLibrary()
        }
    }

    fun refreshAllFiles() {
        launchScan { onTrack ->
            val existing = _library.value.associateBy { it.id }
            val found = scanner.scanAllMedia(onTrack, existing)
            val fromFolders = _library.value.filter { it.sourceTree != "all_files" }
            _library.value = fromFolders + found
        }
    }

    // ---------- Player: busca automática de letras (lrclib.net) ----------
    private val _autoFetchLyrics = MutableStateFlow(prefs.getBoolean(KEY_AUTO_LYRICS, false))
    val autoFetchLyrics: StateFlow<Boolean> = _autoFetchLyrics.asStateFlow()

    fun setAutoFetchLyrics(enabled: Boolean) {
        _autoFetchLyrics.value = enabled
        prefs.edit().putBoolean(KEY_AUTO_LYRICS, enabled).apply()
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

    fun setBitPerfect(enabled: Boolean) {
        _bitPerfect.value = enabled
        prefs.edit().putBoolean(KEY_BIT_PERFECT, enabled).apply()
        // avisa o serviço via SharedPreferences; o player precisa ser recriado na prática
        // para aplicar de forma completa — guardamos a preferência para a próxima sessão
        // e tentamos sinalizar via sessão se possível
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

    /** Quando o timer de preset dispara, também troca a música aleatoriamente. */
    private val _changeSongWithMilkInterval = MutableStateFlow(prefs.getBoolean(KEY_MILK_CHANGE_SONG, false))
    val changeSongWithMilkInterval: StateFlow<Boolean> = _changeSongWithMilkInterval.asStateFlow()
    fun setChangeSongWithMilkInterval(enabled: Boolean) {
        _changeSongWithMilkInterval.value = enabled
        prefs.edit().putBoolean(KEY_MILK_CHANGE_SONG, enabled).apply()
    }

    /** Ao mudar de faixa, escolhe um preset aleatório. */
    private val _changePresetOnSongChange = MutableStateFlow(prefs.getBoolean(KEY_MILK_PRESET_ON_SONG, true))
    val changePresetOnSongChange: StateFlow<Boolean> = _changePresetOnSongChange.asStateFlow()
    fun setChangePresetOnSongChange(enabled: Boolean) {
        _changePresetOnSongChange.value = enabled
        prefs.edit().putBoolean(KEY_MILK_PRESET_ON_SONG, enabled).apply()
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


    /** Saída de áudio preferida (UI + preferência; aplicação plena exige reinício da sessão). */
    enum class AudioOutputBackend { DEFAULT, AUDIOTRACK, AAUDIO, OPENSLES }

    private val _audioOutput = MutableStateFlow(
        try {
            AudioOutputBackend.valueOf(prefs.getString(KEY_AUDIO_OUTPUT, AudioOutputBackend.DEFAULT.name)!!)
        } catch (_: Exception) {
            AudioOutputBackend.DEFAULT
        }
    )
    val audioOutput: StateFlow<AudioOutputBackend> = _audioOutput.asStateFlow()

    fun setAudioOutput(backend: AudioOutputBackend) {
        _audioOutput.value = backend
        prefs.edit().putString(KEY_AUDIO_OUTPUT, backend.name).apply()
    }

    private val _forceMaxSampleRate = MutableStateFlow(prefs.getBoolean(KEY_FORCE_MAX_SR, false))
    val forceMaxSampleRate: StateFlow<Boolean> = _forceMaxSampleRate.asStateFlow()

    fun setForceMaxSampleRate(enabled: Boolean) {
        _forceMaxSampleRate.value = enabled
        prefs.edit().putBoolean(KEY_FORCE_MAX_SR, enabled).apply()
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
            .sortedBy { it.name.lowercase() }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val albums: StateFlow<List<Album>> = tracks.map { list ->
        // Mescla álbuns com mesmo nome e mesmo artista do álbum (case-insensitive + trim)
        // para evitar entradas duplicadas quando tags diferem só em maiúsculas/espaços.
        list.groupBy { t ->
            val albumName = t.album.trim()
            val aa = (t.albumArtist.ifBlank { t.artist }).trim()
            albumName.lowercase() to aa.lowercase()
        }
            .map { (_, l) ->
                val first = l.first()
                val displayAlbum = first.album.trim().ifBlank { first.album }
                val displayArtist = (first.albumArtist.ifBlank { first.artist }).trim()
                Album(
                    displayAlbum,
                    displayArtist,
                    l.size,
                    l.mapNotNull { it.year }.maxOrNull() ?: first.year,
                    l.firstNotNullOfOrNull { it.artPath },
                    l.sumOf { it.duration }
                )
            }
            .sortedBy { it.name.lowercase() }
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
            .sortedBy { it.name.lowercase() }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val searchArtists: StateFlow<List<Artist>> = combine(artists, _searchQuery) { list, q ->
        if (q.isBlank()) emptyList()
        else {
            val lower = q.lowercase()
            list.filter { it.name.lowercase().contains(lower) }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val searchAlbums: StateFlow<List<Album>> = combine(albums, _searchQuery) { list, q ->
        if (q.isBlank()) emptyList()
        else {
            val lower = q.lowercase()
            list.filter {
                it.name.lowercase().contains(lower) ||
                    it.artist.lowercase().contains(lower)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val searchGenres: StateFlow<List<Genre>> = combine(genres, _searchQuery) { list, q ->
        if (q.isBlank()) emptyList()
        else {
            val lower = q.lowercase()
            list.filter { it.name.lowercase().contains(lower) }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Faixas do grupo aberto, ordenadas por álbum > nº da faixa > título. */
    val visibleTracks: StateFlow<List<Track>> = combine(tracks, filter, _playlists, _librarySettings) { list, f, pls, st ->
        if (f == null) {
            // Aba "Todas": ordem alfabética por título
            return@combine list.sortedBy { it.title.lowercase() }
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
                        Tab.ARTISTS -> f.key in artistSplitter.split(it.artist)
                        Tab.ALBUMS -> it.album.trim().equals(f.key.trim(), ignoreCase = true)
                        Tab.GENRES -> f.key in genreSplitter.split(it.genre)
                        Tab.PLAYLISTS -> false
                    }
                }
                base.sortedWith(
                    compareBy<Track>({ it.album.lowercase() }, { it.trackNumber ?: Int.MAX_VALUE }, { it.title.lowercase() })
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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
                }
            }
        }
    }

    /** Adiciona uma pasta e lê só ela (as outras continuam do cache). */
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
                    result += scanner.scanAllMedia(onTrack, existing)
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
                    result += scanner.scanAllMedia(onTrack, existing)
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
            persistPlaybackState(paused = !isPlaying)
            if (isPlaying) startPositionTicker()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _nowPlaying.value = _queue.value.firstOrNull { it.id == mediaItem?.mediaId }
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
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                controller = future.get().also { it.addListener(playerListener) }
                syncFromController()
                // Anexa o equalizador nativo à sessão de áudio atual
                val sessionId = com.tolete.player.PlaybackService.currentAudioSessionId
                if (sessionId != 0) {
                    com.tolete.player.data.EqualizerManager.attach(sessionId, app)
                }
            } catch (e: Exception) {
                controller = null
            }
        }, ContextCompat.getMainExecutor(app))
    }

    fun playTracks(list: List<Track>, startIndex: Int) {
        val c = controller ?: return
        if (startIndex !in list.indices) return
        _queue.value = list
        c.setMediaItems(list.map { it.toMediaItem() }, startIndex, 0L)
        c.prepare()
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
    }

    private var fadeJob: Job? = null

    fun togglePlayPause() {
        val c = controller ?: return
        val ms = _fadeMs.value
        fadeJob?.cancel()
        if (ms <= 0) {
            c.volume = 1f
            if (c.isPlaying) c.pause() else c.play()
            return
        }
        if (c.isPlaying) {
            // fade out then pause
            fadeJob = viewModelScope.launch {
                val steps = (ms / 30).coerceIn(4, 40)
                val startVol = c.volume.coerceIn(0.01f, 1f)
                for (i in 0..steps) {
                    if (!isActive) return@launch
                    c.volume = startVol * (1f - i.toFloat() / steps)
                    delay(ms.toLong() / steps)
                }
                c.pause()
                c.volume = 1f
            }
        } else {
            // fade in from silence
            c.volume = 0f
            c.play()
            fadeJob = viewModelScope.launch {
                val steps = (ms / 30).coerceIn(4, 40)
                for (i in 0..steps) {
                    if (!isActive) return@launch
                    c.volume = i.toFloat() / steps
                    delay(ms.toLong() / steps)
                }
                c.volume = 1f
            }
        }
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
    private fun startPositionTicker() {
        positionTicker?.cancel()
        positionTicker = viewModelScope.launch {
            while (isActive) {
                val c = controller
                if (c != null) {
                    _positionMs.value = c.currentPosition
                    val d = c.duration
                    if (d > 0) _durationMs.value = d
                }
                if (!_isPlaying.value) break
                delay(250)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        _positionMs.value = positionMs
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
    }

    /** Reordena a fila atual (arrastar na lista de reprodução). */
    fun moveQueueItem(from: Int, to: Int) {
        val list = _queue.value
        if (from !in list.indices || to !in list.indices || from == to) return
        controller?.moveMediaItem(from, to)
        _queue.value = list.toMutableList().apply { add(to, removeAt(from)) }
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
        if (_shuffleType.value != ShuffleType.QUEUE) {
            _shuffle.value = c.shuffleModeEnabled
        }
        if (c.mediaItemCount == 0 || _queue.value.isNotEmpty()) return
        val byId = _library.value.associateBy { it.id }
        if (byId.isEmpty()) return
        val q = (0 until c.mediaItemCount).mapNotNull { byId[c.getMediaItemAt(it).mediaId] }
        if (q.size != c.mediaItemCount) return
        _queue.value = q
        _nowPlaying.value = byId[c.currentMediaItem?.mediaId]
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

    /** Salva linhas LRC (arquivo .lrc ou tags embutidas). */
    fun saveSyncedLyrics(
        track: Track,
        lines: List<LyricLine>,
        target: LyricsEmbedder.Target = LyricsEmbedder.Target.LRC_FILE
    ): Boolean {
        return LyricsEmbedder.save(getApplication(), track, lines, target)
    }

    // ---------- Letras (LRC + tags embutidas) ----------
    suspend fun loadLyrics(track: Track): List<LyricLine> = withContext(Dispatchers.IO) {
        val local = LyricsExtractor.load(getApplication(), track)
        if (local.isNotEmpty()) return@withContext local
        if (_autoFetchLyrics.value) {
            try {
                val (lines, raw) = LrcLibClient.fetchLyricsWithRaw(track)
                if (lines.isNotEmpty() && !raw.isNullOrBlank()) {
                    // Embute a letra sincronizada no arquivo (sidecar .lrc ou cache)
                    LyricsEmbedder.embedLrc(getApplication(), track, raw)
                }
                lines
            } catch (_: Exception) {
                emptyList()
            }
        } else emptyList()
    }

    suspend fun searchLyrics(trackName: String, artistName: String): List<LrcLibClient.SearchResult> =
        LrcLibClient.search(trackName, artistName)

    suspend fun embedLyricsResult(
        track: Track,
        result: LrcLibClient.SearchResult,
        target: LyricsEmbedder.Target = LyricsEmbedder.Target.LRC_FILE
    ): List<LyricLine> =
        withContext(Dispatchers.IO) {
            val lines = result.toLyricLines()
            val raw = result.toLrcText()
            if (lines.isNotEmpty() && !raw.isNullOrBlank()) {
                when (target) {
                    LyricsEmbedder.Target.LRC_FILE -> LyricsEmbedder.embedLrc(getApplication(), track, raw)
                    LyricsEmbedder.Target.EMBEDDED -> LyricsEmbedder.embedInTags(getApplication(), track, raw)
                }
            }
            lines
        }

    // ---------- Aparência ----------
    enum class AppThemeId { DYNAMIC, DEFAULT, AQUA, SUNSET, FOREST, MIDNIGHT, ROSE, EMBER, AMOLED }

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

    /** Cor padrão do tema dinâmico quando nenhuma música está tocando (ARGB). */
    private val _dynamicDefaultColor = MutableStateFlow(
        prefs.getInt(KEY_DYNAMIC_DEFAULT_COLOR, 0xFFBB86FC.toInt())
    )
    val dynamicDefaultColor: StateFlow<Int> = _dynamicDefaultColor.asStateFlow()

    fun setDynamicDefaultColor(argb: Int) {
        _dynamicDefaultColor.value = argb
        prefs.edit().putInt(KEY_DYNAMIC_DEFAULT_COLOR, argb).apply()
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

    /** IDs já contados nesta sessão de play (evita recontagem até trocar de faixa). */
    private val countedThisSession = mutableSetOf<String>()

    private fun recordPlay(id: String) {
        if (id in countedThisSession) return
        countedThisSession.add(id)
        val updated = (listOf(id) + _historyIds.value.filter { it != id }).take(HISTORY_MAX)
        _historyIds.value = updated
        prefs.edit().putString(KEY_HISTORY, updated.joinToString("\n")).apply()
        val counts = _playCounts.value.toMutableMap()
        counts[id] = (counts[id] ?: 0) + 1
        _playCounts.value = counts
        val lasts = _lastPlayedAt.value.toMutableMap()
        lasts[id] = System.currentTimeMillis()
        _lastPlayedAt.value = lasts
        prefs.edit()
            .putString(KEY_PLAY_COUNTS, counts.entries.joinToString("\n") { "${it.key}=${it.value}" })
            .putString(KEY_LAST_PLAYED, lasts.entries.joinToString("\n") { "${it.key}=${it.value}" })
            .apply()
    }

    init {
        // Atualiza histórico de ordem quando a faixa atual muda (sem contar play ainda)
        viewModelScope.launch {
            _nowPlaying.collect { t ->
                val id = t?.id
                // Nova faixa: libera contagem novamente
                if (id != null) {
                    countedThisSession.removeAll { it != id }
                }
            }
        }
        // Conta play só ao atingir o percentual configurado da duração
        viewModelScope.launch {
            combine(_nowPlaying, _positionMs, _playCountThreshold) { track, pos, threshold ->
                Triple(track, pos, threshold)
            }.collect { (track, pos, threshold) ->
                val t = track ?: return@collect
                val dur = t.duration.coerceAtLeast(1L)
                val pct = ((pos * 100L) / dur).toInt()
                if (pct >= threshold) {
                    recordPlay(t.id)
                }
            }
        }
    }

    /** Caminho absoluto do .ttf personalizado para letras (null = fonte padrão). */
    private val _lyricsFontPath = MutableStateFlow(prefs.getString(KEY_LYRICS_FONT, null))
    val lyricsFontPath: StateFlow<String?> = _lyricsFontPath.asStateFlow()

    fun setLyricsFontPath(path: String?) {
        _lyricsFontPath.value = path
        prefs.edit().putString(KEY_LYRICS_FONT, path).apply()
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
    }

    private val _lyricsBorderWidth = MutableStateFlow(prefs.getFloat(KEY_LYRICS_BORDER_W, 0f))
    val lyricsBorderWidth: StateFlow<Float> = _lyricsBorderWidth.asStateFlow()
    fun setLyricsBorderWidth(w: Float) {
        val v = w.coerceIn(0f, 8f)
        _lyricsBorderWidth.value = v
        prefs.edit().putFloat(KEY_LYRICS_BORDER_W, v).apply()
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

    // Cor ARGB dos menus claros (pop-ups brancos). Default ~ branco translúcido.
    private val _menuBgColor = MutableStateFlow(
        prefs.getInt(KEY_MENU_BG_COLOR, 0xE8F2F2F7.toInt())
    )
    val menuBgColor: StateFlow<Int> = _menuBgColor.asStateFlow()

    fun setMenuBgColor(argb: Int) {
        _menuBgColor.value = argb
        prefs.edit().putInt(KEY_MENU_BG_COLOR, argb).apply()
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
    fun setTrackSort(sort: TrackSort, order: SortOrder) {
        _trackSort.value = sort
        _trackOrder.value = order
        prefs.edit().putString(KEY_TRACK_SORT, sort.name).putString(KEY_TRACK_ORDER, order.name).apply()
    }

    fun sortedTracks(list: List<Track>): List<Track> {
        val asc = _trackOrder.value == SortOrder.ASC
        val sorted = when (_trackSort.value) {
            TrackSort.NAME -> list.sortedBy { it.title.lowercase() }
            TrackSort.FILE_SIZE -> list.sortedBy { it.fileSize }
            TrackSort.DURATION -> list.sortedBy { it.duration }
            TrackSort.LAST_MODIFIED -> list.sortedBy { it.lastModified }
        }
        return if (asc) sorted else sorted.asReversed()
    }

    fun sortedArtists(list: List<Artist>): List<Artist> {
        val asc = _groupOrder.value == SortOrder.ASC
        val sorted = when (_groupSort.value) {
            GroupSort.NAME -> list.sortedBy { it.name.lowercase() }
            GroupSort.TRACK_COUNT -> list.sortedBy { it.trackCount }
            GroupSort.TOTAL_DURATION -> list.sortedBy { it.totalDuration }
        }
        return if (asc) sorted else sorted.asReversed()
    }

    fun sortedAlbums(list: List<Album>): List<Album> {
        val asc = _groupOrder.value == SortOrder.ASC
        val sorted = when (_groupSort.value) {
            GroupSort.NAME -> list.sortedBy { it.name.lowercase() }
            GroupSort.TRACK_COUNT -> list.sortedBy { it.trackCount }
            GroupSort.TOTAL_DURATION -> list.sortedBy { it.totalDuration }
        }
        return if (asc) sorted else sorted.asReversed()
    }

    fun sortedGenres(list: List<Genre>): List<Genre> {
        val asc = _groupOrder.value == SortOrder.ASC
        val sorted = when (_groupSort.value) {
            GroupSort.NAME -> list.sortedBy { it.name.lowercase() }
            GroupSort.TRACK_COUNT -> list.sortedBy { it.trackCount }
            GroupSort.TOTAL_DURATION -> list.sortedBy { it.totalDuration }
        }
        return if (asc) sorted else sorted.asReversed()
    }

    // ---------- Persistência da fila ----------
    private fun persistPlaybackState(paused: Boolean) {
        try {
            val ids = org.json.JSONArray()
            _queue.value.forEach { ids.put(it.id) }
            val idx = _nowPlaying.value?.let { np -> _queue.value.indexOfFirst { it.id == np.id } } ?: -1
            val pos = controller?.currentPosition ?: 0L
            prefs.edit()
                .putString(KEY_QUEUE_IDS, ids.toString())
                .putInt(KEY_QUEUE_INDEX, idx)
                .putLong(KEY_QUEUE_POS, pos)
                .putBoolean(KEY_QUEUE_PAUSED, paused)
                .apply()
        } catch (_: Exception) {}
    }

    private fun restorePlaybackState() {
        viewModelScope.launch {
            try {
                val idsRaw = prefs.getString(KEY_QUEUE_IDS, null) ?: return@launch
                val arr = org.json.JSONArray(idsRaw)
                if (arr.length() == 0) return@launch
                // espera biblioteca carregar
                var tries = 0
                while (_library.value.isEmpty() && tries < 40) {
                    kotlinx.coroutines.delay(150)
                    tries++
                }
                val byId = _library.value.associateBy { it.id }
                val q = (0 until arr.length()).mapNotNull { byId[arr.getString(it)] }
                if (q.isEmpty()) return@launch
                val idx = prefs.getInt(KEY_QUEUE_INDEX, 0).coerceIn(0, q.lastIndex)
                val pos = prefs.getLong(KEY_QUEUE_POS, 0L)
                val c = controller
                if (c == null) {
                    // espera controller
                    tries = 0
                    while (controller == null && tries < 30) {
                        kotlinx.coroutines.delay(100)
                        tries++
                    }
                }
                val ctrl = controller ?: return@launch
                _queue.value = q
                ctrl.setMediaItems(q.map { it.toMediaItem() }, idx, pos)
                ctrl.prepare()
                ctrl.pause() // sempre pausado ao restaurar
                _nowPlaying.value = q[idx]
                _isPlaying.value = false
            } catch (_: Exception) {}
        }
    }

    // ---------- Backup ----------
    fun exportBackup(): String {
        val obj = org.json.JSONObject()
        obj.put("version", 2)
        obj.put("theme", _themeId.value.name)
        obj.put("lyricsFont", _lyricsFontPath.value)
        obj.put("uiFont", _uiFontPath.value)
        obj.put("lyricsFontSize", _lyricsFontSize.value.toDouble())
        obj.put("homeButtonFont", _homeButtonFontPath.value)
        obj.put("homeButtonColor", _homeButtonColor.value)
        obj.put("shareFont", _shareFontPath.value)
        obj.put("shareTextColor", _shareTextColor.value)
        obj.put("shareBrandColor", _shareBrandColor.value)
        obj.put("lyricsPrimary", _lyricsPrimaryColor.value)
        obj.put("lyricsSecondary", _lyricsSecondaryColor.value)
        obj.put("lyricsBorder", _lyricsBorderColor.value)
        obj.put("lyricsBorderWidth", _lyricsBorderWidth.value.toDouble())
        obj.put("autoFetchLyrics", _autoFetchLyrics.value)
        obj.put("shuffleType", _shuffleType.value.name)
        obj.put("bitPerfect", _bitPerfect.value)
        obj.put("audioOutput", _audioOutput.value.name)
        obj.put("allFilesMode", _allFilesMode.value)
        obj.put("splitArtists", _librarySettings.value.splitArtists)
        obj.put("artistSeparators", _librarySettings.value.artistSeparators)
        obj.put("artistExceptions", _librarySettings.value.artistExceptions)
        obj.put("splitGenres", _librarySettings.value.splitGenres)
        obj.put("genreSeparators", _librarySettings.value.genreSeparators)
        obj.put("fadeMs", _fadeMs.value)
        obj.put("milkIntervalSec", _milkIntervalSec.value)
        obj.put("changeSongWithMilkInterval", _changeSongWithMilkInterval.value)
        obj.put("changePresetOnSongChange", _changePresetOnSongChange.value)
        obj.put("playCountThreshold", _playCountThreshold.value)
        obj.put("splashEnabled", _splashEnabled.value)
        obj.put("splashSoundEnabled", _splashSoundEnabled.value)
        obj.put("dynamicDefaultColor", _dynamicDefaultColor.value)
        // Histórico e contagem de plays
        obj.put("history", _historyIds.value.joinToString("\n"))
        val counts = org.json.JSONObject()
        _playCounts.value.forEach { (k, v) -> counts.put(k, v) }
        obj.put("playCounts", counts)
        val folders = org.json.JSONArray()
        _treeUris.value.forEach { folders.put(it) }
        obj.put("folders", folders)
        val favs = org.json.JSONArray()
        try {
            _playlists.value.find { it.isFavorites }?.trackIds?.forEach { favs.put(it) }
        } catch (_: Exception) {}
        obj.put("favorites", favs)
        // Metadados de artistas (texto/foto URL) — fotos locais vão no ZIP separado
        try {
            obj.put("artistMeta", org.json.JSONObject(ArtistMetaStore.exportAll(getApplication())))
        } catch (_: Exception) {}
        return obj.toString(2)
    }

    /** Gera um ZIP com preferences.json + fotos de artistas em artist_photos/. */
    fun exportBackupZip(destFile: java.io.File): Boolean {
        return try {
            val json = exportBackup()
            java.util.zip.ZipOutputStream(java.io.FileOutputStream(destFile)).use { zos ->
                zos.putNextEntry(java.util.zip.ZipEntry("preferences.json"))
                zos.write(json.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
                val photoDir = java.io.File(getApplication<android.app.Application>().filesDir, "artist_photos")
                if (photoDir.isDirectory) {
                    photoDir.listFiles()?.forEach { f ->
                        if (f.isFile) {
                            zos.putNextEntry(java.util.zip.ZipEntry("artist_photos/${f.name}"))
                            f.inputStream().use { it.copyTo(zos) }
                            zos.closeEntry()
                        }
                    }
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun importBackupZip(bytes: ByteArray): Boolean {
        return try {
            var json: String? = null
            val app = getApplication<android.app.Application>()
            val photoDir = java.io.File(app.filesDir, "artist_photos").also { it.mkdirs() }
            val restoredFiles = mutableSetOf<String>() // filenames only
            java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name.replace("\\", "/")
                    if (name == "preferences.json" || name.endsWith("/preferences.json")) {
                        json = zis.readBytes().toString(Charsets.UTF_8)
                    } else if ((name.contains("artist_photos/") || name.startsWith("artist_photos/")) && !entry.isDirectory) {
                        val base = name.substringAfterLast('/')
                        if (base.isNotBlank()) {
                            val dest = java.io.File(photoDir, base)
                            dest.outputStream().use { out -> zis.copyTo(out) }
                            restoredFiles.add(base)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            val ok = if (json != null) importBackup(json!!) else restoredFiles.isNotEmpty()
            // Religa caminhos absolutos antigos de localImagePath para o diretório local atual
            if (restoredFiles.isNotEmpty()) {
                try {
                    val prefs = app.getSharedPreferences("entity_meta", android.content.Context.MODE_PRIVATE)
                    val ed = prefs.edit()
                    prefs.all.forEach { (key, value) ->
                        if (value !is String) return@forEach
                        try {
                            val o = org.json.JSONObject(value)
                            val oldPath = o.optString("localImagePath")
                            if (oldPath.isNullOrBlank()) return@forEach
                            val base = java.io.File(oldPath).name
                            if (base in restoredFiles || java.io.File(photoDir, base).exists()) {
                                o.put("localImagePath", java.io.File(photoDir, base).absolutePath)
                                ed.putString(key, o.toString())
                            }
                        } catch (_: Exception) {}
                    }
                    ed.apply()
                } catch (_: Exception) {}
            }
            ok
        } catch (_: Exception) {
            false
        }
    }

    fun importBackup(json: String): Boolean {

        return try {
            val obj = org.json.JSONObject(json)
            obj.optString("theme").takeIf { it.isNotBlank() }?.let {
                try { setThemeId(AppThemeId.valueOf(it)) } catch (_: Exception) {}
            }
            if (obj.has("lyricsFont")) setLyricsFontPath(obj.optString("lyricsFont").ifBlank { null })
            if (obj.has("uiFont")) setUiFontPath(obj.optString("uiFont").ifBlank { null })
            if (obj.has("lyricsFontSize")) setLyricsFontSize(obj.getDouble("lyricsFontSize").toFloat())
            if (obj.has("homeButtonFont")) setHomeButtonFontPath(obj.optString("homeButtonFont").ifBlank { null })
            if (obj.has("homeButtonColor")) setHomeButtonColor(obj.getInt("homeButtonColor"))
            if (obj.has("shareFont")) setShareFontPath(obj.optString("shareFont").ifBlank { null })
            if (obj.has("shareTextColor")) setShareTextColor(obj.getInt("shareTextColor"))
            if (obj.has("shareBrandColor")) setShareBrandColor(obj.getInt("shareBrandColor"))
            if (obj.has("lyricsPrimary")) setLyricsPrimaryColor(obj.getInt("lyricsPrimary"))
            if (obj.has("lyricsSecondary")) setLyricsSecondaryColor(obj.getInt("lyricsSecondary"))
            if (obj.has("lyricsBorder")) setLyricsBorderColor(obj.getInt("lyricsBorder"))
            if (obj.has("lyricsBorderWidth")) setLyricsBorderWidth(obj.getDouble("lyricsBorderWidth").toFloat())
            if (obj.has("autoFetchLyrics")) setAutoFetchLyrics(obj.getBoolean("autoFetchLyrics"))
            if (obj.has("history")) {
                _historyIds.value = obj.optString("history").split('\n').filter { it.isNotBlank() }
                prefs.edit().putString(KEY_HISTORY, _historyIds.value.joinToString("\n")).apply()
            }
            if (obj.has("playCounts")) {
                val c = obj.getJSONObject("playCounts")
                val map = mutableMapOf<String, Int>()
                c.keys().forEach { k -> map[k] = c.optInt(k) }
                _playCounts.value = map
                prefs.edit().putString(KEY_PLAY_COUNTS, map.entries.joinToString("\n") { "${it.key}=${it.value}" }).apply()
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
        connectController()
        loadPlaylists()

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
                _library.value = cached.filter {
                    it.sourceTree in valid || (allFiles && it.sourceTree == "all_files")
                }
                syncFromController()
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
        }
    }

    override fun onCleared() {
        persistPlaybackState(paused = true)
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
        const val KEY_HISTORY = "play_history"
        const val KEY_PLAY_COUNTS = "play_counts"
        const val KEY_LAST_PLAYED = "last_played_at"
        const val KEY_PLAY_COUNT_THRESHOLD = "play_count_threshold"
        const val HISTORY_MAX = 50
        const val KEY_LYRICS_FONT = "lyrics_font_path"
        const val KEY_UI_FONT = "ui_font_path"
        const val KEY_SPLIT_ARTISTS = "split_artists"
        const val KEY_ARTIST_SEPARATORS = "artist_separators"
        const val KEY_ARTIST_EXCEPTIONS = "artist_exceptions"
        const val KEY_SPLIT_GENRES = "split_genres"
        const val KEY_GENRE_SEPARATORS = "genre_separators"
        const val KEY_IGNORED_FOLDERS = "ignored_folders"
        const val KEY_MIN_DURATION_SEC = "min_duration_sec"
        const val KEY_ALL_FILES = "all_files_mode"
        const val KEY_AUTO_LYRICS = "auto_fetch_lyrics"
        const val KEY_SHUFFLE_TYPE = "shuffle_type"
        const val KEY_BIT_PERFECT = "bit_perfect"
        const val KEY_MILK_INTERVAL = "milk_interval_sec"
        const val KEY_MILK_CHANGE_SONG = "milk_change_song_with_interval"
        const val KEY_MILK_PRESET_ON_SONG = "milk_preset_on_song_change"
        const val KEY_AUDIO_OUTPUT = "audio_output_backend"
        const val KEY_FORCE_MAX_SR = "force_max_sample_rate"
        const val KEY_LYRICS_FONT_SIZE = "lyrics_font_size"
        const val KEY_LYRICS_PRIMARY = "lyrics_primary_color"
        const val KEY_LYRICS_SECONDARY = "lyrics_secondary_color"
        const val KEY_LYRICS_BORDER = "lyrics_border_color"
        const val KEY_LYRICS_BORDER_W = "lyrics_border_width"
        const val KEY_SPLASH_ENABLED = "splash_enabled"
        const val KEY_SPLASH_SOUND = "splash_sound_enabled"
        const val KEY_FLOATING_ICONS = "floating_icons_enabled"
        const val KEY_MENU_BG_COLOR = "menu_bg_color"
        const val KEY_HOME_BUTTON_FONT = "home_button_font_path"
        const val KEY_HOME_BUTTON_COLOR = "home_button_color"
        const val KEY_SHARE_FONT = "share_font_path"
        const val KEY_SHARE_TEXT_COLOR = "share_text_color"
        const val KEY_SHARE_BRAND_COLOR = "share_brand_color"
        const val KEY_FADE_MS = "fade_ms"
        const val KEY_SEEK_BAR_STYLE = "seek_bar_style"
        const val KEY_GROUP_SORT = "group_sort"
        const val KEY_GROUP_ORDER = "group_order"
        const val KEY_TRACK_SORT = "track_sort"
        const val KEY_TRACK_ORDER = "track_order"
        const val KEY_QUEUE_IDS = "queue_ids"
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


    fun playExternalUri(uri: Uri, title: String) {
        viewModelScope.launch {
            // Aguarda o MediaController ficar pronto
            var tries = 0
            while (controller == null && tries < 40) {
                kotlinx.coroutines.delay(100)
                tries++
            }
            val c = controller ?: return@launch
            val item = MediaItem.Builder()
                .setUri(uri)
                .setMediaMetadata(
                    MediaMetadata.Builder().setTitle(title).build()
                )
                .build()
            c.setMediaItem(item)
            c.prepare()
            c.play()
            startPositionTicker()
        }
    }

}




