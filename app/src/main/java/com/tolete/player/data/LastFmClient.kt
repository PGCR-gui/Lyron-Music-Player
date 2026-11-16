package com.tolete.player.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Cliente da API Last.fm (métodos públicos, sem autenticação).
 * Fallback quando Wikidata não retorna foto/bio.
 */
object LastFmClient {

    const val DASHBOARD_CACHE_TTL_MS = 4 * 60 * 1000L
    private const val READ_CACHE_TTL_MS = DASHBOARD_CACHE_TTL_MS
    private data class CachedBody(val savedAt: Long, val body: String)
    private data class CachedTrackDates(val savedAt: Long, val dates: TrackPlayDates)
    private val readCache = ConcurrentHashMap<String, CachedBody>()
    private val trackDatesCache = ConcurrentHashMap<String, CachedTrackDates>()
    @Volatile private var cacheDirectory: File? = null
    @Volatile private var cacheContext: Context? = null
    @Volatile private var invalidatedBeforeMs: Long = 0L
    @Volatile private var cacheGeneration: Long = 0L

    private fun cacheBody(url: String, cached: CachedBody) {
        readCache[url] = cached
        if (readCache.size > 250) {
            readCache.entries.sortedBy { it.value.savedAt }
                .take(readCache.size - 250)
                .forEach { readCache.remove(it.key, it.value) }
        }
    }

    fun initialize(context: Context) {
        val app = context.applicationContext
        cacheContext = app
        cacheDirectory = File(app.cacheDir, "lastfm_read_cache")
    }

    /** Inicia uma visita ao dashboard e invalida respostas se ele não era acessado há 4 minutos. */
    fun beginDashboardSession(user: String): Boolean {
        val context = cacheContext ?: return false
        val prefs = context.getSharedPreferences("tolete", Context.MODE_PRIVATE)
        val key = "lastfm_dashboard_last_access_${user.trim().lowercase()}"
        val now = System.currentTimeMillis()
        val previous = prefs.getLong(key, 0L)
        val expired = previous > 0L && now - previous >= DASHBOARD_CACHE_TTL_MS
        prefs.edit().putLong(key, now).apply()
        if (expired) clearReadCache()
        return expired
    }

    fun touchDashboardAccess(user: String) {
        val context = cacheContext ?: return
        val key = "lastfm_dashboard_last_access_${user.trim().lowercase()}"
        context.getSharedPreferences("tolete", Context.MODE_PRIVATE).edit()
            .putLong(key, System.currentTimeMillis())
            .apply()
    }

    /** Invalida em memória sem executar trabalho de disco no thread da UI. */
    fun clearReadCache() {
        invalidatedBeforeMs = System.currentTimeMillis() + 1L
        cacheGeneration++
        readCache.clear()
        trackDatesCache.clear()
    }

    internal const val DEFAULT_API_KEY = "752ee48ef50aca94c9b2ec0dbb4c4881"
    /** Pode ser trocada nas configurações; precisa ser do mesmo cadastro do API Secret. */
    @Volatile internal var API_KEY: String = DEFAULT_API_KEY
    internal const val BASE = "https://ws.audioscrobbler.com/2.0/"
    internal const val USER_AGENT = "LyronMusicPlayer/0.40 (https://linktr.ee/pgcrgui)"

    data class ArtistInfo(
        val name: String,
        val bio: String?,
        val imageUrl: String?
    )

    data class AlbumInfo(
        val name: String,
        val artist: String?,
        val summary: String?,
        val imageUrl: String?
    )

    suspend fun getArtistInfo(name: String): ArtistInfo? = withContext(Dispatchers.IO) {
        if (name.isBlank()) return@withContext null
        try {
            val url = "${BASE}?method=artist.getinfo&artist=${enc(name)}&api_key=$API_KEY&format=json&lang=pt"
            val root = getJson(url) ?: return@withContext null
            val artist = root.optJSONObject("artist") ?: return@withContext null
            val bioObj = artist.optJSONObject("bio")
            val bio = bioObj?.optString("summary")
                ?.replace(Regex("<a[^>]*>.*?</a>", RegexOption.IGNORE_CASE), "")
                ?.replace(Regex("<[^>]+>"), "")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            val imageUrl = pickImage(artist.optJSONArray("image"))
            ArtistInfo(
                name = artist.optString("name").ifBlank { name },
                bio = bio,
                imageUrl = imageUrl
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getAlbumInfo(album: String, artist: String?): AlbumInfo? = withContext(Dispatchers.IO) {
        if (album.isBlank()) return@withContext null
        try {
            val params = buildString {
                append("method=album.getinfo&album=${enc(album)}&api_key=$API_KEY&format=json&lang=pt")
                if (!artist.isNullOrBlank()) append("&artist=${enc(artist)}")
            }
            val root = getJson("$BASE?$params") ?: return@withContext null
            val alb = root.optJSONObject("album") ?: return@withContext null
            val wiki = alb.optJSONObject("wiki")
            val summary = wiki?.optString("summary")
                ?.replace(Regex("<a[^>]*>.*?</a>", RegexOption.IGNORE_CASE), "")
                ?.replace(Regex("<[^>]+>"), "")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            val imageUrl = pickImage(alb.optJSONArray("image"))
            AlbumInfo(
                name = alb.optString("name").ifBlank { album },
                artist = alb.optString("artist").takeIf { it.isNotBlank() },
                summary = summary,
                imageUrl = imageUrl
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun pickImage(arr: org.json.JSONArray?): String? {
        if (arr == null || arr.length() == 0) return null
        var best: String? = null
        var bestRank = -1
        val rank = mapOf(
            "mega" to 5, "extralarge" to 4, "large" to 3, "medium" to 2, "small" to 1
        )
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val url = obj.optString("#text").takeIf { it.isNotBlank() && it != "https://" } ?: continue
            val size = obj.optString("size")
            val r = rank[size] ?: 0
            if (r > bestRank) {
                bestRank = r
                best = url
            }
        }
        return best
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun getJson(urlStr: String): JSONObject? {
        val now = System.currentTimeMillis()
        val requestGeneration = cacheGeneration
        readCache[urlStr]?.let { cached ->
            if (cached.savedAt >= invalidatedBeforeMs && now - cached.savedAt < READ_CACHE_TTL_MS) {
                return try { JSONObject(cached.body) } catch (_: Exception) { readCache.remove(urlStr); null }
            }
            readCache.remove(urlStr, cached)
        }
        val storedFile = cacheFile(urlStr)
        if (storedFile?.isFile == true) {
            try {
                val stored = storedFile.readText()
                val split = stored.indexOf('\n')
                val savedAt = if (split >= 0) stored.substring(0, split).toLongOrNull() else null
                val body = if (split >= 0) stored.substring(split + 1) else ""
                if (savedAt != null && savedAt >= invalidatedBeforeMs && now - savedAt < READ_CACHE_TTL_MS && body.isNotBlank()) {
                    cacheBody(urlStr, CachedBody(savedAt, body))
                    return JSONObject(body)
                }
                storedFile.delete()
            } catch (_: Exception) {
                storedFile.delete()
            }
        }
        return try {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 12_000
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
            }
            try {
                if (conn.responseCode !in 200..299) return null
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val saved = System.currentTimeMillis()
                if (requestGeneration == cacheGeneration) {
                    cacheBody(urlStr, CachedBody(saved, body))
                    try {
                        val target = cacheFile(urlStr)
                        if (target != null) {
                            target.parentFile?.mkdirs()
                            val tmp = File(target.parentFile, target.name + ".${Thread.currentThread().id}.tmp")
                            tmp.writeText("$saved\n$body")
                            if (!tmp.renameTo(target)) {
                                target.writeText("$saved\n$body")
                                tmp.delete()
                            }
                            val cacheFiles = target.parentFile?.listFiles()
                                ?.filter { it.isFile && it.extension == "json" }
                                ?.sortedBy { it.lastModified() }
                                .orEmpty()
                            if (cacheFiles.size > 250) cacheFiles.take(cacheFiles.size - 250).forEach { it.delete() }
                        }
                    } catch (_: Exception) { }
                }
                json
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun cacheFile(url: String): File? {
        val directory = cacheDirectory ?: return null
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(directory, "$digest.json")
    }

    data class UserTrack(
        val title: String,
        val artist: String,
        val album: String? = null,
        val playedAt: Long? = null, // epoch ms, se disponível
        val loved: Boolean = false
    )

    /**
     * Faixas recentes do usuário (público; basta o username).
     * Sem scrobbling — só leitura.
     */
    suspend fun getRecentTracks(user: String, limit: Int = 50): List<UserTrack> = withContext(Dispatchers.IO) {
        if (user.isBlank()) return@withContext emptyList()
        try {
            val url = "${BASE}?method=user.getrecenttracks&user=${enc(user)}&api_key=$API_KEY&format=json&limit=${limit.coerceIn(1, 200)}"
            val root = getJson(url) ?: return@withContext emptyList()
            val arr = root.optJSONObject("recenttracks")?.optJSONArray("track") ?: return@withContext emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val title = o.optString("name").trim()
                    val artist = o.optJSONObject("artist")?.optString("#text")
                        ?: o.optString("artist")
                    val album = o.optJSONObject("album")?.optString("#text")?.takeIf { it.isNotBlank() }
                    val dateUts = o.optJSONObject("date")?.optString("uts")?.toLongOrNull()
                    // faixa "now playing" não tem date
                    if (title.isBlank()) continue
                    add(
                        UserTrack(
                            title = title,
                            artist = artist?.trim().orEmpty(),
                            album = album,
                            playedAt = dateUts?.let { it * 1000L }
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Faixas amadas (loved) do usuário (público).
     */
    suspend fun getLovedTracks(user: String, limit: Int = 100): List<UserTrack> = withContext(Dispatchers.IO) {
        if (user.isBlank()) return@withContext emptyList()
        try {
            val url = "${BASE}?method=user.getlovedtracks&user=${enc(user)}&api_key=$API_KEY&format=json&limit=${limit.coerceIn(1, 200)}"
            val root = getJson(url) ?: return@withContext emptyList()
            val arr = root.optJSONObject("lovedtracks")?.optJSONArray("track") ?: return@withContext emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val title = o.optString("name").trim()
                    val artist = o.optJSONObject("artist")?.optString("name")
                        ?: o.optJSONObject("artist")?.optString("#text")
                        ?: o.optString("artist")
                    if (title.isBlank()) continue
                    add(
                        UserTrack(
                            title = title,
                            artist = artist?.trim().orEmpty(),
                            loved = true
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }


    // ------------------------------------------------------------------
    // Dashboard do Last.fm (leitura pública; basta o username)
    // ------------------------------------------------------------------

    data class UserInfo(
        val name: String,
        val realName: String?,
        val url: String?,
        val country: String?,
        val imageUrl: String?,
        val playCount: Long,
        val artistCount: Long,
        val trackCount: Long,
        val albumCount: Long,
        val lovedCount: Long,
        val registeredAtMs: Long?
    )

    data class ScrobbleItem(
        val title: String,
        val artist: String,
        val album: String? = null,
        val dateMs: Long? = null,
        val nowPlaying: Boolean = false
    )

    data class Page<T>(
        val items: List<T>,
        val page: Int,
        val totalPages: Int,
        val total: Int,
        val paginationKnown: Boolean = true
    )

    enum class ChartKind { TRACKS, ARTISTS, ALBUMS }

    data class ChartItem(
        val name: String,
        val artist: String,
        val plays: Int
    )

    /** O JSON do Last.fm devolve objeto em vez de array quando há um único item. */
    private fun jsonItems(o: JSONObject?, key: String): List<JSONObject> {
        val v = o?.opt(key) ?: return emptyList()
        return when (v) {
            is org.json.JSONArray -> (0 until v.length()).mapNotNull { v.optJSONObject(it) }
            is JSONObject -> kotlin.collections.listOf(v)
            else -> emptyList()
        }
    }

    private fun artistOf(o: JSONObject): String {
        val a = o.opt("artist")
        return when (a) {
            is JSONObject -> a.optString("name").ifBlank { a.optString("#text") }
            is String -> a
            else -> ""
        }.trim()
    }

    private fun userUrl(method: String, user: String, extra: String = ""): String =
        "${BASE}?method=$method&user=${enc(user)}&api_key=$API_KEY&format=json$extra"

    suspend fun getUserInfo(user: String): UserInfo? = withContext(Dispatchers.IO) {
        if (user.isBlank()) return@withContext null
        val u = getJson(userUrl("user.getinfo", user))?.optJSONObject("user") ?: return@withContext null
        val lovedTotal = getJson(userUrl("user.getlovedtracks", user, "&limit=1"))
            ?.optJSONObject("lovedtracks")?.optJSONObject("@attr")?.optString("total")
            ?.toLongOrNull() ?: 0L
        UserInfo(
            name = u.optString("name").ifBlank { user },
            realName = u.optString("realname").takeIf { it.isNotBlank() },
            url = u.optString("url").takeIf { it.isNotBlank() },
            country = u.optString("country").takeIf { it.isNotBlank() && it != "None" },
            imageUrl = pickImage(u.optJSONArray("image")),
            playCount = u.optString("playcount").toLongOrNull() ?: 0L,
            artistCount = u.optString("artist_count").toLongOrNull() ?: 0L,
            trackCount = u.optString("track_count").toLongOrNull() ?: 0L,
            albumCount = u.optString("album_count").toLongOrNull() ?: 0L,
            lovedCount = lovedTotal,
            registeredAtMs = u.optJSONObject("registered")?.optString("unixtime")?.toLongOrNull()?.times(1000L)
        )
    }

    /** Uma página de scrobbles (mais recentes primeiro). [fromSec]/[toSec] em epoch segundos. */
    suspend fun getScrobblePage(
        user: String,
        page: Int,
        limit: Int = 50,
        fromSec: Long? = null,
        toSec: Long? = null
    ): Page<ScrobbleItem>? = withContext(Dispatchers.IO) {
        if (user.isBlank()) return@withContext null
        val extra = buildString {
            append("&limit=${limit.coerceIn(1, 200)}&page=${page.coerceAtLeast(1)}")
            if (fromSec != null) append("&from=$fromSec")
            if (toSec != null) append("&to=$toSec")
        }
        val root = getJson(userUrl("user.getrecenttracks", user, extra))
            ?.optJSONObject("recenttracks") ?: return@withContext null
        val attr = root.optJSONObject("@attr")
        val items = jsonItems(root, "track").mapNotNull { o ->
            val title = o.optString("name").trim()
            if (title.isBlank()) return@mapNotNull null
            ScrobbleItem(
                title = title,
                artist = artistOf(o),
                album = o.optJSONObject("album")?.optString("#text")?.takeIf { it.isNotBlank() },
                dateMs = o.optJSONObject("date")?.optString("uts")?.toLongOrNull()?.times(1000L),
                nowPlaying = o.optJSONObject("@attr")?.optString("nowplaying") == "true"
            )
        }
        val totalPages = attr?.optString("totalPages")?.toIntOrNull()
        Page(
            items = items,
            page = attr?.optString("page")?.toIntOrNull() ?: page,
            totalPages = totalPages ?: 1,
            total = attr?.optString("total")?.toIntOrNull() ?: items.size,
            paginationKnown = totalPages != null
        )
    }

    /** Quantidade de scrobbles no intervalo [fromSec, toSec) — 1 chamada por intervalo. */
    suspend fun getScrobbleCount(user: String, fromSec: Long, toSec: Long): Int? =
        getScrobblePage(user, page = 1, limit = 1, fromSec = fromSec, toSec = (toSec - 1).coerceAtLeast(fromSec))
            ?.total

    /** Faixas amadas (mais recentes primeiro), paginadas. */
    suspend fun getLovedPage(user: String, page: Int, limit: Int = 50): Page<ScrobbleItem>? =
        withContext(Dispatchers.IO) {
            if (user.isBlank()) return@withContext null
            val root = getJson(
                userUrl("user.getlovedtracks", user, "&limit=${limit.coerceIn(1, 200)}&page=${page.coerceAtLeast(1)}")
            )?.optJSONObject("lovedtracks") ?: return@withContext null
            val attr = root.optJSONObject("@attr")
            val items = jsonItems(root, "track").mapNotNull { o ->
                val title = o.optString("name").trim()
                if (title.isBlank()) return@mapNotNull null
                ScrobbleItem(
                    title = title,
                    artist = artistOf(o),
                    dateMs = o.optJSONObject("date")?.optString("uts")?.toLongOrNull()?.times(1000L)
                )
            }
            Page(
                items = items,
                page = attr?.optString("page")?.toIntOrNull() ?: page,
                totalPages = attr?.optString("totalPages")?.toIntOrNull() ?: 1,
                total = attr?.optString("total")?.toIntOrNull() ?: items.size
            )
        }


    data class TrackPlayDates(
        val firstPlayedMs: Long?,
        val lastPlayedMs: Long?,
        /** False quando a API falhou antes de cobrir todo o histórico. */
        val historyScanComplete: Boolean = true
    )

    /**
     * Primeira e última reprodução no histórico do Last.fm (página da biblioteca do usuário).
     * Independente dos contadores locais do app.
     */
    suspend fun getUserTrackPlayDates(
        user: String,
        artist: String,
        track: String,
        candidates: List<Pair<String, String>> = emptyList()
    ): TrackPlayDates? = withContext(Dispatchers.IO) {
        if (user.isBlank() || artist.isBlank() || track.isBlank()) return@withContext null
        try {
            fun normalize(value: String): String = java.text.Normalizer
                .normalize(value.trim().replace(Regex("\\s+"), " "), java.text.Normalizer.Form.NFC)
                .lowercase(java.util.Locale.ROOT)
            val namePairs = (listOf(artist to track) + candidates)
                .filter { it.first.isNotBlank() && it.second.isNotBlank() }
                .distinctBy { normalize(it.first) + "\u0000" + normalize(it.second) }
            val cacheKey = user.trim().lowercase(java.util.Locale.ROOT) + "|" +
                namePairs.map { normalize(it.first) + "\u0000" + normalize(it.second) }.sorted().joinToString("|")
            val now = System.currentTimeMillis()
            trackDatesCache[cacheKey]?.takeIf { now - it.savedAt < READ_CACHE_TTL_MS }
                ?.let { return@withContext it.dates }

            // A rota da biblioteca é útil e barata, mas precisa de segmentos URL codificados.
            // Se uma data faltar (mudança de HTML, idioma, faixa rara), completamos via API.
            fun pathSegment(value: String): String = URLEncoder.encode(value.trim(), "UTF-8")
                .replace("+", "%20")
            var first = namePairs.firstNotNullOfOrNull { pair ->
                val url = "https://www.last.fm/user/${pathSegment(user)}/library/music/" +
                    "${pathSegment(pair.first)}/_/${pathSegment(pair.second)}"
                val html = getHtml(url) ?: return@firstNotNullOfOrNull null
                fun parseDate(label: String): Long? {
                    val re = Regex(
                        "(?is)<h[1-6][^>]*>\\s*${Regex.escape(label)}\\s*</h[1-6]>\\s*<p[^>]*>(.*?)</p>"
                    )
                    val rawHtml = re.find(html)?.groupValues?.getOrNull(1) ?: return null
                    val raw = android.text.Html.fromHtml(
                        rawHtml,
                        android.text.Html.FROM_HTML_MODE_LEGACY
                    ).toString().trim().replace(".", "")
                        .replace(Regex("\\s+(am|pm)$", RegexOption.IGNORE_CASE), "\$1")
                    if (raw.isBlank() || raw.equals("Unknown", true)) return null
                    val formats = listOf(
                        "dd MMM yyyy, h:mma", "d MMM yyyy, h:mma",
                        "dd MMM yyyy, H:mm", "d MMM yyyy, H:mm",
                        "MMM dd, yyyy h:mma", "yyyy-MM-dd HH:mm"
                    )
                    for (format in formats) {
                        try {
                            val parser = java.text.SimpleDateFormat(format, java.util.Locale.ENGLISH)
                            parser.isLenient = false
                            parser.parse(raw)?.let { return it.time }
                        } catch (_: Exception) { }
                    }
                    return null
                }
                val htmlFirst = parseDate("First played")
                val htmlLast = parseDate("Last played")
                if (htmlFirst != null || htmlLast != null) htmlFirst to htmlLast else null
            } ?: (null to null)
            var last = first.second
            first = first.first to first.second
            if (first.first != null && last != null) {
                val result = TrackPlayDates(first.first, last)
                trackDatesCache[cacheKey] = CachedTrackDates(System.currentTimeMillis(), result)
                return@withContext result
            }

            // O endpoint recente devolve a biblioteca completa paginada (200 por página). A busca
            // começa pelas pontas certas: página 1 para a ocorrência mais recente e última página
            // para a primeira ocorrência. Nunca usamos a reprodução/histórico local como fallback.
            val firstPage = getScrobblePage(user, page = 1, limit = 200)
                ?: return@withContext if (first.first != null || last != null) {
                    TrackPlayDates(first.first, last, historyScanComplete = false)
                } else null
            if (!firstPage.paginationKnown && firstPage.total > firstPage.items.size) {
                return@withContext TrackPlayDates(first.first, last, historyScanComplete = false)
            }
            val totalPages = firstPage.totalPages.coerceAtLeast(1)
            fun matches(item: ScrobbleItem): Boolean = namePairs.any { (a, t) ->
                normalize(item.title) == normalize(t) && normalize(item.artist) == normalize(a)
            }
            fun matchingDates(page: Page<ScrobbleItem>): List<Long> = page.items
                .asSequence()
                .filterNot { it.nowPlaying }
                .filter(::matches)
                .mapNotNull { it.dateMs }
                .toList()

            var complete = firstPage.paginationKnown || firstPage.total <= firstPage.items.size
            var earliestScanned: Long? = null
            var latestSearchCoveredHistory = false
            // Limite de páginas para não ficar carregando o histórico inteiro (pode ser milhares).
            val maxScanPages = 8
            if (last == null) {
                val endPage = minOf(totalPages, maxScanPages)
                for (pageNo in 1..endPage) {
                    val page = if (pageNo == 1) firstPage else getScrobblePage(user, pageNo, limit = 200)
                    if (page == null || (!page.paginationKnown && pageNo == totalPages && totalPages > 1)) {
                        complete = false
                        break
                    }
                    val dates = matchingDates(page)
                    val pageEarliest = dates.minOrNull()
                    if (pageEarliest != null && (earliestScanned == null || pageEarliest < earliestScanned!!)) {
                        earliestScanned = pageEarliest
                    }
                    last = dates.maxOrNull()
                    if (last != null) break
                    if (pageNo == totalPages) latestSearchCoveredHistory = true
                }
                if (endPage < totalPages && last == null) complete = false
            }
            if (first.first == null && latestSearchCoveredHistory) {
                first = earliestScanned to first.second
            }
            if (first.first == null && !latestSearchCoveredHistory) {
                val startPage = totalPages
                val endPage = maxOf(1, totalPages - maxScanPages + 1)
                for (pageNo in startPage downTo endPage) {
                    val page = if (pageNo == 1) firstPage else getScrobblePage(user, pageNo, limit = 200)
                    if (page == null) {
                        complete = false
                        break
                    }
                    first = matchingDates(page).minOrNull() to first.second
                    if (first.first != null) break
                }
                if (endPage > 1 && first.first == null) complete = false
            }
            // Mesmo incompleto, devolve o que achou para a UI não ficar em loading infinito
            val result = TrackPlayDates(first.first, last, historyScanComplete = complete)
            trackDatesCache[cacheKey] = CachedTrackDates(System.currentTimeMillis(), result)
            result
        } catch (_: Exception) {
            null
        }
    }

    private fun getHtml(url: String): String? {
        return try {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            try {
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.setRequestProperty("User-Agent", "LyronMusicPlayer/1.0")
                conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                conn.instanceFollowRedirects = true
                if (conn.responseCode !in 200..299) return null
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    data class TrackStats(
        val title: String,
        val artist: String,
        val album: String?,
        val userPlayCount: Long,
        val listeners: Long,
        val globalPlayCount: Long,
        val url: String?,
        val imageUrl: String?,
        val userLoved: Boolean = false
    )

    data class EntityStats(
        val name: String,
        val artist: String? = null,
        val userPlayCount: Long,
        val listeners: Long,
        val globalPlayCount: Long,
        val url: String?,
        val imageUrl: String?
    )

    /** Stats públicas de uma faixa (user playcount exige username). */
    suspend fun getTrackStats(artist: String, track: String, user: String? = null): TrackStats? =
        withContext(Dispatchers.IO) {
            if (artist.isBlank() || track.isBlank()) return@withContext null
            try {
                val extra = buildString {
                    append("method=track.getInfo&artist=${enc(artist)}&track=${enc(track)}&api_key=$API_KEY&format=json")
                    if (!user.isNullOrBlank()) append("&username=${enc(user)}")
                }
                val root = getJson("$BASE?$extra")?.optJSONObject("track") ?: return@withContext null
                TrackStats(
                    title = root.optString("name").ifBlank { track },
                    artist = root.optJSONObject("artist")?.optString("name")
                        ?: root.optString("artist").ifBlank { artist },
                    album = root.optJSONObject("album")?.optString("title")?.takeIf { it.isNotBlank() },
                    userPlayCount = root.optString("userplaycount").toLongOrNull() ?: 0L,
                    listeners = root.optString("listeners").toLongOrNull() ?: 0L,
                    globalPlayCount = root.optString("playcount").toLongOrNull() ?: 0L,
                    url = root.optString("url").takeIf { it.isNotBlank() },
                    imageUrl = pickImage(root.optJSONObject("album")?.optJSONArray("image")
                        ?: root.optJSONArray("image")),
                    userLoved = root.optString("userloved") == "1"
                )
            } catch (_: Exception) {
                null
            }
        }

    suspend fun getArtistStats(artist: String, user: String? = null): EntityStats? =
        withContext(Dispatchers.IO) {
            if (artist.isBlank()) return@withContext null
            try {
                val extra = buildString {
                    append("method=artist.getInfo&artist=${enc(artist)}&api_key=$API_KEY&format=json")
                    if (!user.isNullOrBlank()) append("&username=${enc(user)}")
                }
                val root = getJson("$BASE?$extra")?.optJSONObject("artist") ?: return@withContext null
                val stats = root.optJSONObject("stats")
                EntityStats(
                    name = root.optString("name").ifBlank { artist },
                    userPlayCount = stats?.optString("userplaycount")?.toLongOrNull() ?: 0L,
                    listeners = stats?.optString("listeners")?.toLongOrNull() ?: 0L,
                    globalPlayCount = stats?.optString("playcount")?.toLongOrNull() ?: 0L,
                    url = root.optString("url").takeIf { it.isNotBlank() },
                    imageUrl = pickImage(root.optJSONArray("image"))
                )
            } catch (_: Exception) {
                null
            }
        }

    suspend fun getAlbumStats(artist: String, album: String, user: String? = null): EntityStats? =
        withContext(Dispatchers.IO) {
            if (artist.isBlank() || album.isBlank()) return@withContext null
            try {
                val extra = buildString {
                    append("method=album.getInfo&artist=${enc(artist)}&album=${enc(album)}&api_key=$API_KEY&format=json")
                    if (!user.isNullOrBlank()) append("&username=${enc(user)}")
                }
                val root = getJson("$BASE?$extra")?.optJSONObject("album") ?: return@withContext null
                EntityStats(
                    name = root.optString("name").ifBlank { album },
                    artist = root.optString("artist").takeIf { it.isNotBlank() } ?: artist,
                    userPlayCount = root.optString("userplaycount").toLongOrNull() ?: 0L,
                    listeners = root.optString("listeners").toLongOrNull() ?: 0L,
                    globalPlayCount = root.optString("playcount").toLongOrNull() ?: 0L,
                    url = root.optString("url").takeIf { it.isNotBlank() },
                    imageUrl = pickImage(root.optJSONArray("image"))
                )
            } catch (_: Exception) {
                null
            }
        }

    /**
     * Ranking dentro de um intervalo arbitrário (weekly charts aceitam qualquer from/to).
     * [fromSec]/[toSec] em epoch segundos. Se ambos forem null, usa o total (overall).
     */
    suspend fun getChart(
        user: String,
        kind: ChartKind,
        fromSec: Long?,
        toSec: Long?,
        limit: Int = 0
    ): List<ChartItem>? = withContext(Dispatchers.IO) {
        if (user.isBlank()) return@withContext null
        val (method, rootKey, itemKey) = when (kind) {
            ChartKind.TRACKS -> Triple("user.getweeklytrackchart", "weeklytrackchart", "track")
            ChartKind.ARTISTS -> Triple("user.getweeklyartistchart", "weeklyartistchart", "artist")
            ChartKind.ALBUMS -> Triple("user.getweeklyalbumchart", "weeklyalbumchart", "album")
        }
        val range = if (fromSec != null && toSec != null) "&from=$fromSec&to=$toSec" else ""
        val root = if (range.isNotEmpty()) {
            getJson(userUrl(method, user, range))?.optJSONObject(rootKey)
        } else {
            val (m2, k2, i2) = when (kind) {
                ChartKind.TRACKS -> Triple("user.gettoptracks", "toptracks", "track")
                ChartKind.ARTISTS -> Triple("user.gettopartists", "topartists", "artist")
                ChartKind.ALBUMS -> Triple("user.gettopalbums", "topalbums", "album")
            }
            val lim = if (limit > 0) limit else 50
            getJson(userUrl(m2, user, "&period=overall&limit=$lim"))?.optJSONObject(k2)
        } ?: return@withContext null
        val itemName = if (range.isNotEmpty()) itemKey else when (kind) {
            ChartKind.TRACKS -> "track"; ChartKind.ARTISTS -> "artist"; ChartKind.ALBUMS -> "album"
        }
        jsonItems(root, itemName).mapNotNull { o ->
            val name = o.optString("name").trim()
            if (name.isBlank()) return@mapNotNull null
            ChartItem(
                name = name,
                artist = if (kind == ChartKind.ARTISTS) "" else artistOf(o),
                plays = o.optString("playcount").toIntOrNull() ?: 0
            )
        }.sortedByDescending { it.plays }.let { if (limit > 0) it.take(limit) else it }
    }

}
