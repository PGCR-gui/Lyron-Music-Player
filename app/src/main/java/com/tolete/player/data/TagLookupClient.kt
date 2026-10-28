package com.tolete.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Busca metadados e capa online usando APIs já usadas no app (Deezer + Last.fm).
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
                val merged = linkedMapOf<String, Result>()
                fun keyOf(r: Result) =
                    "${r.title.trim().lowercase()}|${r.artist.trim().lowercase()}|${r.album.trim().lowercase()}"
                (deezer.await() + lastfm.await()).forEach { r ->
                    val k = keyOf(r)
                    val prev = merged[k]
                    if (prev == null) merged[k] = r
                    else if (prev.coverUrl.isNullOrBlank() && !r.coverUrl.isNullOrBlank()) merged[k] = r
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
