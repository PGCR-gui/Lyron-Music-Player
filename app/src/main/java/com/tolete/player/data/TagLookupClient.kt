package com.tolete.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Busca metadados e capa online usando APIs já usadas no app (Deezer, Last.fm, NetEase,
 * KuGou e QQ Music).
 * Resultados com preview antes de aplicar no editor de tags.
 */
object TagLookupClient {

    private const val USER_AGENT = "LyronMusicPlayer/0.60 (https://linktr.ee/pgcrgui)"

    data class Result(
        val title: String,
        val artist: String,
        val album: String,
        val year: String? = null,
        val genre: String? = null,
        val trackNumber: String? = null,
        val coverUrl: String? = null,
        val source: String
    )

    suspend fun search(title: String, artist: String, album: String = ""): List<Result> =
        withContext(Dispatchers.IO) {
            coroutineScope {
                val deezer = async { searchDeezer(title, artist, album) }
                val lastfm = async { searchLastFm(title, artist, album) }
                // Fontes chinesas podem ser lentas fora da China: timeout próprio para cada uma.
                val netease = async { withTimeoutOrNull(CN_SOURCE_TIMEOUT_MS) { searchNetease(title, artist, album) }.orEmpty() }
                val kugou = async { withTimeoutOrNull(CN_SOURCE_TIMEOUT_MS) { searchKugou(title, artist, album) }.orEmpty() }
                val qq = async { withTimeoutOrNull(CN_SOURCE_TIMEOUT_MS) { searchQq(title, artist, album) }.orEmpty() }
                val merged = linkedMapOf<String, Result>()
                fun keyOf(r: Result) =
                    "${r.title.trim().lowercase()}|${r.artist.trim().lowercase()}|${r.album.trim().lowercase()}"
                // Ordem = prioridade na lista: Deezer e Last.fm primeiro, depois as fontes chinesas.
                (deezer.await() + lastfm.await() + netease.await() + kugou.await() + qq.await()).forEach { r ->
                    val k = keyOf(r)
                    val prev = merged[k]
                    if (prev == null) merged[k] = r
                    else if (prev.coverUrl.isNullOrBlank() && !r.coverUrl.isNullOrBlank()) merged[k] = r
                    else if (prev.year.isNullOrBlank() && !r.year.isNullOrBlank()) {
                        // mantém a capa/fonte já escolhida, só completa o ano
                        merged[k] = prev.copy(year = r.year, trackNumber = prev.trackNumber ?: r.trackNumber)
                    }
                }
                merged.values.toList()
            }
        }

    suspend fun downloadCover(url: String): ByteArray? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 12_000
                setRequestProperty("User-Agent", USER_AGENT)
                instanceFollowRedirects = true
            }
            conn.inputStream.use { it.readBytes() }.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    private fun searchDeezer(title: String, artist: String, album: String): List<Result> {
        if (title.isBlank() && artist.isBlank() && album.isBlank()) return emptyList()
        return try {
            val q = buildString {
                if (title.isNotBlank()) append("track:\"${title.trim()}\" ")
                if (artist.isNotBlank()) append("artist:\"${artist.trim()}\" ")
                if (album.isNotBlank()) append("album:\"${album.trim()}\"")
            }.trim().ifBlank { listOf(title, artist, album).filter { it.isNotBlank() }.joinToString(" ") }
            val url = "https://api.deezer.com/search?q=${enc(q)}&limit=10"
            val root = getJson(url) ?: return emptyList()
            val data = root.optJSONArray("data") ?: return emptyList()
            buildList {
                for (i in 0 until data.length()) {
                    val o = data.optJSONObject(i) ?: continue
                    val t = o.optString("title")
                    if (t.isBlank()) continue
                    val a = o.optJSONObject("artist")?.optString("name").orEmpty()
                    val alb = o.optJSONObject("album")
                    val albumName = alb?.optString("title").orEmpty()
                    val cover = listOf(
                        alb?.optString("cover_xl"),
                        alb?.optString("cover_big"),
                        alb?.optString("cover_medium")
                    ).firstOrNull { !it.isNullOrBlank() }
                    add(
                        Result(
                            title = t,
                            artist = a,
                            album = albumName,
                            coverUrl = cover,
                            source = "Deezer"
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun searchLastFm(title: String, artist: String, album: String): List<Result> {
        val out = mutableListOf<Result>()
        try {
            if (title.isNotBlank()) {
                val params = buildString {
                    append("method=track.search&track=${enc(title)}&api_key=${LastFmClientApiKey}&format=json&limit=8")
                    if (artist.isNotBlank()) append("&artist=${enc(artist)}")
                }
                val root = getJson("https://ws.audioscrobbler.com/2.0/?$params")
                val matches = root
                    ?.optJSONObject("results")
                    ?.optJSONObject("trackmatches")
                    ?.optJSONArray("track")
                if (matches != null) {
                    for (i in 0 until matches.length()) {
                        val o = matches.optJSONObject(i) ?: continue
                        val t = o.optString("name")
                        if (t.isBlank()) continue
                        val a = o.optString("artist")
                        val image = pickLastFmImage(o.optJSONArray("image"))
                        out.add(
                            Result(
                                title = t,
                                artist = a,
                                album = album,
                                coverUrl = image,
                                source = "Last.fm"
                            )
                        )
                    }
                }
            }
            val albName = album.ifBlank { null }
            if (!albName.isNullOrBlank() || title.isNotBlank()) {
                val info = LastFmClient.getAlbumInfo(
                    album = albName ?: title,
                    artist = artist.takeIf { it.isNotBlank() }
                )
                if (info != null) {
                    out.add(
                        0,
                        Result(
                            title = title.ifBlank { info.name },
                            artist = info.artist ?: artist,
                            album = info.name,
                            coverUrl = info.imageUrl,
                            source = "Last.fm"
                        )
                    )
                }
            }
        } catch (_: Exception) { }
        return out
    }

    private const val CN_SOURCE_TIMEOUT_MS = 9_000L
    private const val CN_MAX_RESULTS = 8

    private fun httpsUrl(url: String?): String? =
        url?.trim()?.takeIf { it.isNotBlank() }?.replaceFirst("http://", "https://")

    /** Ano a partir de um timestamp em ms (ignora 0, negativos e o placeholder de 1970). */
    private fun yearFromMillis(ms: Long): String? {
        if (ms <= 0L) return null
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = ms
        val y = cal.get(java.util.Calendar.YEAR)
        return if (y in 1901..2100 && y != 1970) y.toString() else null
    }

    private fun yearFromDate(date: String?): String? =
        date?.trim()?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) && it != "0000" }

    private fun keywordOf(title: String, artist: String, album: String): String =
        listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")
            .ifBlank { album.trim() }

    // ---------------- NetEase Cloud Music ----------------

    private val neteaseHeaders = mapOf(
        "Referer" to "https://music.163.com/",
        "Origin" to "https://music.163.com",
        "Cookie" to "os=pc; appver=2.10.13"
    )

    private suspend fun searchNetease(title: String, artist: String, album: String): List<Result> =
        withContext(Dispatchers.IO) {
            val keyword = keywordOf(title, artist, album)
            if (keyword.isBlank()) return@withContext emptyList()
            try {
                val form = "s=${enc(keyword)}&type=1&limit=$CN_MAX_RESULTS&offset=0"
                // cloudsearch devolve álbum com capa (al.picUrl) e data (publishTime)
                val body = LyricsHttp.postForm("https://music.163.com/api/cloudsearch/pc", form, neteaseHeaders)
                val songs = body?.let {
                    JSONObject(it).optJSONObject("result")?.optJSONArray("songs")
                } ?: return@withContext emptyList()
                parseNeteaseSongs(songs)
            } catch (_: Exception) {
                emptyList()
            }
        }

    private fun parseNeteaseSongs(songs: JSONArray): List<Result> {
        val out = mutableListOf<Result>()
        for (i in 0 until songs.length()) {
            val o = songs.optJSONObject(i) ?: continue
            val name = o.optString("name")
            if (name.isBlank()) continue
            val artists = o.optJSONArray("ar") ?: o.optJSONArray("artists")
            val names = mutableListOf<String>()
            if (artists != null) {
                for (j in 0 until artists.length()) {
                    artists.optJSONObject(j)?.optString("name")?.takeIf { it.isNotBlank() }?.let { names += it }
                }
            }
            val al = o.optJSONObject("al") ?: o.optJSONObject("album")
            val pic = al?.optString("picUrl")?.takeIf { it.isNotBlank() && it != "null" }
            // ?param=WxH pede a capa em alta resolução ao CDN da NetEase
            val cover = httpsUrl(pic)?.let { "$it?param=1000y1000" }
            val publish = o.optLong("publishTime", 0L).takeIf { it > 0L }
                ?: al?.optLong("publishTime", 0L) ?: 0L
            val no = o.optInt("no", 0).takeIf { it > 0 }
            out += Result(
                title = name,
                artist = names.joinToString(", "),
                album = al?.optString("name").orEmpty(),
                year = yearFromMillis(publish),
                trackNumber = no?.toString(),
                coverUrl = cover,
                source = "NetEase"
            )
        }
        return out
    }

    // ---------------- KuGou ----------------

    private suspend fun searchKugou(title: String, artist: String, album: String): List<Result> =
        withContext(Dispatchers.IO) {
            val keyword = keywordOf(title, artist, album)
            if (keyword.isBlank()) return@withContext emptyList()
            try {
                // A busca web já traz a capa (campo Image, com o marcador {size})
                val resp = LyricsHttp.get(
                    "https://songsearch.kugou.com/song_search_v2" +
                        "?keyword=${enc(keyword)}&page=1&pagesize=$CN_MAX_RESULTS&platform=WebFilter"
                ) ?: return@withContext emptyList()
                val lists = JSONObject(resp).optJSONObject("data")?.optJSONArray("lists")
                    ?: return@withContext emptyList()
                val tagRe = Regex("<[^>]*>")
                val out = mutableListOf<Result>()
                for (i in 0 until lists.length()) {
                    val o = lists.optJSONObject(i) ?: continue
                    val name = o.optString("SongName").replace(tagRe, "").trim()
                    if (name.isBlank()) continue
                    val singers = o.optString("SingerName").replace(tagRe, "").trim()
                    val image = o.optString("Image").takeIf { it.isNotBlank() }
                        ?.replace("{size}", "400")
                    out += Result(
                        title = name,
                        artist = singers,
                        album = o.optString("AlbumName").replace(tagRe, "").trim(),
                        coverUrl = httpsUrl(image),
                        source = "KuGou"
                    )
                }
                out
            } catch (_: Exception) {
                emptyList()
            }
        }

    // ---------------- QQ Music ----------------

    private val qqHeaders = mapOf(
        "Referer" to "https://y.qq.com/portal/player.html",
        "Origin" to "https://y.qq.com"
    )

    private suspend fun searchQq(title: String, artist: String, album: String): List<Result> =
        withContext(Dispatchers.IO) {
            val keyword = keywordOf(title, artist, album)
            if (keyword.isBlank()) return@withContext emptyList()
            try {
                val body = JSONObject()
                    .put("comm", JSONObject().put("ct", 19).put("cv", 1859).put("uin", 0))
                    .put(
                        "req", JSONObject()
                            .put("method", "DoSearchForQQMusicDesktop")
                            .put("module", "music.search.SearchCgiService")
                            .put(
                                "param", JSONObject()
                                    .put("grp", 1)
                                    .put("num_per_page", CN_MAX_RESULTS)
                                    .put("page_num", 1)
                                    .put("query", keyword)
                                    .put("search_type", 0)
                            )
                    )
                val resp = LyricsHttp.postJson("https://u.y.qq.com/cgi-bin/musicu.fcg", body.toString(), qqHeaders)
                var list = resp?.let {
                    JSONObject(it).optJSONObject("req")?.optJSONObject("data")
                        ?.optJSONObject("body")?.optJSONObject("song")?.optJSONArray("list")
                }
                if (list == null || list.length() == 0) {
                    // fallback: endpoint antigo de busca
                    val old = LyricsHttp.get(
                        "https://c.y.qq.com/soso/fcgi-bin/client_search_cp" +
                            "?format=json&new_json=1&cr=1&p=1&n=$CN_MAX_RESULTS&w=${enc(keyword)}",
                        qqHeaders
                    )
                    list = old?.let {
                        JSONObject(it).optJSONObject("data")?.optJSONObject("song")?.optJSONArray("list")
                    }
                }
                list?.let { parseQqSongs(it) }.orEmpty()
            } catch (_: Exception) {
                emptyList()
            }
        }

    private fun parseQqSongs(arr: JSONArray): List<Result> {
        val out = mutableListOf<Result>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("title").ifBlank { o.optString("name") }
            if (name.isBlank()) continue
            val singers = o.optJSONArray("singer")
            val names = mutableListOf<String>()
            if (singers != null) {
                for (j in 0 until singers.length()) {
                    singers.optJSONObject(j)?.optString("name")?.takeIf { it.isNotBlank() }?.let { names += it }
                }
            }
            val al = o.optJSONObject("album")
            val albumMid = al?.optString("mid").orEmpty()
            // Capa 800x800 do CDN da QQ Music a partir do mid do álbum
            val cover = if (albumMid.isNotBlank())
                "https://y.gtimg.cn/music/photo_new/T002R800x800M000$albumMid.jpg"
            else null
            val no = o.optInt("index_album", 0).takeIf { it > 0 }
            out += Result(
                title = name,
                artist = names.joinToString(", "),
                album = al?.optString("name").orEmpty(),
                year = yearFromDate(o.optString("time_public")),
                trackNumber = no?.toString(),
                coverUrl = cover,
                source = "QQ Music"
            )
        }
        return out
    }

    // Mesma chave já usada em LastFmClient
    private const val LastFmClientApiKey = "752ee48ef50aca94c9b2ec0dbb4c4881"

    private fun pickLastFmImage(arr: org.json.JSONArray?): String? {
        if (arr == null || arr.length() == 0) return null
        var best: String? = null
        var bestRank = -1
        val rank = mapOf(
            "mega" to 5, "extralarge" to 4, "large" to 3, "medium" to 2, "small" to 1
        )
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val url = obj.optString("#text").takeIf { it.isNotBlank() && it != "https://" } ?: continue
            val r = rank[obj.optString("size")] ?: 0
            if (r > bestRank) {
                bestRank = r
                best = url
            }
        }
        return best
    }

    private fun enc(s: String): String =
        URLEncoder.encode(s, Charsets.UTF_8.name())

    private fun getJson(url: String): JSONObject? {
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", USER_AGENT)
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body)
        } catch (_: Exception) {
            null
        }
    }
}
