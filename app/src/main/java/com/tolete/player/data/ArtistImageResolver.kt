package com.tolete.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Resolve foto (e opcionalmente bio) de artista com fallback em cadeia:
 * 1. MusicBrainz → MBID
 * 2. Fanart.tv (thumb / logo) via MBID
 * 3. TheAudioDB (por nome)
 * 4. Deezer (por nome, picture_xl)
 * 5. Last.fm
 * 6. Wikidata / Commons
 *
 * Fanart.tv exige API key gratuita: https://fanart.tv/get-an-api-key/
 * Se [FANART_API_KEY] estiver em branco, esse passo é ignorado.
 */
object ArtistImageResolver {

    private const val USER_AGENT = "LyronMusicPlayer/0.45 (https://linktr.ee/pgcrgui)"
    /** Chave gratuita do Fanart.tv — substitua pela sua se quiser priorizar Fanart. */
    private const val FANART_API_KEY = ""
    /** Chave de demonstração pública do TheAudioDB (uso leve). */
    private const val AUDIODB_KEY = "2"

    data class Result(
        val imageUrl: String?,
        val bio: String? = null,
        val source: String = "unknown",
        val mbid: String? = null
    )

    suspend fun resolve(artistName: String): Result = withContext(Dispatchers.IO) {
        if (artistName.isBlank()) return@withContext Result(null)

        val mbid = musicBrainzArtistId(artistName)

        // 1–2) Fanart.tv via MBID
        if (!mbid.isNullOrBlank() && FANART_API_KEY.isNotBlank()) {
            fanartImage(mbid)?.let {
                return@withContext Result(imageUrl = it, source = "fanart.tv", mbid = mbid)
            }
        }

        // 3) TheAudioDB
        audioDbArtist(artistName)?.let { (img, bio) ->
            if (!img.isNullOrBlank()) {
                return@withContext Result(imageUrl = img, bio = bio, source = "theaudiodb", mbid = mbid)
            }
        }

        // 4) Deezer
        deezerArtistImage(artistName)?.let {
            return@withContext Result(imageUrl = it, source = "deezer", mbid = mbid)
        }

        // 5) Last.fm
        try {
            val lfm = LastFmClient.getArtistInfo(artistName)
            if (!lfm?.imageUrl.isNullOrBlank()) {
                return@withContext Result(
                    imageUrl = lfm?.imageUrl,
                    bio = lfm?.bio,
                    source = "lastfm",
                    mbid = mbid
                )
            }
            // guarda bio mesmo sem imagem para enriquecer o card
            if (!lfm?.bio.isNullOrBlank()) {
                // continua para tentar imagem no Wikidata, mas devolve bio do Last.fm no fim
                val wiki = WikidataClient.searchArtist(artistName)
                if (!wiki?.imageUrl.isNullOrBlank()) {
                    return@withContext Result(
                        imageUrl = wiki?.imageUrl,
                        bio = lfm?.bio ?: wiki?.extract ?: wiki?.description,
                        source = "wikidata+lastfm",
                        mbid = mbid
                    )
                }
                return@withContext Result(
                    imageUrl = null,
                    bio = lfm?.bio,
                    source = "lastfm",
                    mbid = mbid
                )
            }
        } catch (_: Exception) { }

        // 6) Wikidata
        try {
            val wiki = WikidataClient.searchArtist(artistName)
            if (wiki != null) {
                return@withContext Result(
                    imageUrl = wiki.imageUrl,
                    bio = wiki.extract ?: wiki.description,
                    source = "wikidata",
                    mbid = mbid
                )
            }
        } catch (_: Exception) { }

        Result(imageUrl = null, mbid = mbid)
    }

    // ---------- MusicBrainz ----------

    private fun musicBrainzArtistId(name: String): String? {
        return try {
            val q = URLEncoder.encode("artist:\"$name\"", "UTF-8")
            val url = "https://musicbrainz.org/ws/2/artist/?query=$q&fmt=json&limit=5"
            val root = getJson(url) ?: return null
            val artists = root.optJSONArray("artists") ?: return null
            if (artists.length() == 0) return null
            // Prefer exact-ish name match
            val want = name.trim().lowercase()
            var bestId: String? = null
            var bestScore = -1
            for (i in 0 until artists.length()) {
                val a = artists.getJSONObject(i)
                val n = a.optString("name").lowercase()
                val score = a.optInt("score", 0)
                val exact = n == want
                val s = if (exact) score + 100 else score
                if (s > bestScore) {
                    bestScore = s
                    bestId = a.optString("id").takeIf { it.isNotBlank() }
                }
            }
            bestId
        } catch (_: Exception) {
            null
        }
    }

    // ---------- Fanart.tv ----------

    private fun fanartImage(mbid: String): String? {
        if (FANART_API_KEY.isBlank()) return null
        return try {
            val url = "https://webservice.fanart.tv/v3/music/$mbid?api_key=$FANART_API_KEY"
            val root = getJson(url) ?: return null
            // prioridade: artistthumb > artistbackground > hdmusiclogo
            pickFanart(root.optJSONArray("artistthumb"))
                ?: pickFanart(root.optJSONArray("artistbackground"))
                ?: pickFanart(root.optJSONArray("hdmusiclogo"))
                ?: pickFanart(root.optJSONArray("musiclogo"))
        } catch (_: Exception) {
            null
        }
    }

    private fun pickFanart(arr: org.json.JSONArray?): String? {
        if (arr == null || arr.length() == 0) return null
        // prefer lang empty/en and highest likes if present
        var bestUrl: String? = null
        var bestLikes = -1
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val url = o.optString("url").takeIf { it.startsWith("http") } ?: continue
            val likes = o.optString("likes").toIntOrNull() ?: 0
            val lang = o.optString("lang", "")
            val bonus = if (lang.isEmpty() || lang == "en") 10 else 0
            val score = likes + bonus
            if (score >= bestLikes) {
                bestLikes = score
                bestUrl = url
            }
        }
        return bestUrl
    }

    // ---------- TheAudioDB ----------

    private fun audioDbArtist(name: String): Pair<String?, String?>? {
        return try {
            val url = "https://www.theaudiodb.com/api/v1/json/$AUDIODB_KEY/search.php?s=${enc(name)}"
            val root = getJson(url) ?: return null
            val arr = root.optJSONArray("artists") ?: return null
            if (arr.length() == 0) return null
            val a = arr.getJSONObject(0)
            val img = sequenceOf(
                a.optString("strArtistThumb"),
                a.optString("strArtistFanart"),
                a.optString("strArtistFanart2"),
                a.optString("strArtistBanner")
            ).map { it.trim() }.firstOrNull { it.startsWith("http") }
            val bio = a.optString("strBiographyPT").takeIf { it.isNotBlank() }
                ?: a.optString("strBiographyEN").takeIf { it.isNotBlank() }
            img to bio
        } catch (_: Exception) {
            null
        }
    }

    // ---------- Deezer ----------

    private fun deezerArtistImage(name: String): String? {
        return try {
            val url = "https://api.deezer.com/search/artist?q=${enc(name)}&limit=5"
            val root = getJson(url) ?: return null
            val data = root.optJSONArray("data") ?: return null
            if (data.length() == 0) return null
            val want = name.trim().lowercase()
            var bestUrl: String? = null
            var bestExact = false
            for (i in 0 until data.length()) {
                val a = data.getJSONObject(i)
                val n = a.optString("name").lowercase()
                val exact = n == want
                val img = a.optString("picture_xl").takeIf { it.startsWith("http") }
                    ?: a.optString("picture_big").takeIf { it.startsWith("http") }
                    ?: a.optString("picture_medium").takeIf { it.startsWith("http") }
                if (img == null) continue
                if (exact) return img
                if (!bestExact && bestUrl == null) bestUrl = img
            }
            bestUrl
        } catch (_: Exception) {
            null
        }
    }

    // ---------- HTTP ----------

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun getJson(urlStr: String): JSONObject? {
        return try {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 12_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (conn.responseCode !in 200..299) return null
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                if (body.isBlank()) null else JSONObject(body)
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }
}
