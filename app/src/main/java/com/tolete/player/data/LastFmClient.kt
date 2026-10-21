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

    private const val API_KEY = "752ee48ef50aca94c9b2ec0dbb4c4881"
    private const val BASE = "https://ws.audioscrobbler.com/2.0/"
    private const val USER_AGENT = "LyronMusicPlayer/0.40 (https://linktr.ee/pgcrgui)"

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
}
