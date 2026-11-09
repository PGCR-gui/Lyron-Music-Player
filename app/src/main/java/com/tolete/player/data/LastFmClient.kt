package com.tolete.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Cliente da API Last.fm (métodos públicos, sem autenticação).
 * Fallback quando Wikidata não retorna foto/bio.
 */
object LastFmClient {

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
                JSONObject(body)
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
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

    data class Page<T>(val items: List<T>, val page: Int, val totalPages: Int, val total: Int)

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
        Page(
            items = items,
            page = attr?.optString("page")?.toIntOrNull() ?: page,
            totalPages = attr?.optString("totalPages")?.toIntOrNull() ?: 1,
            total = attr?.optString("total")?.toIntOrNull() ?: items.size
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

    data class TrackStats(
        val title: String,
        val artist: String,
        val album: String?,
        val userPlayCount: Long,
        val listeners: Long,
        val globalPlayCount: Long,
        val url: String?,
        val imageUrl: String?
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
                        ?: root.optJSONArray("image"))
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
