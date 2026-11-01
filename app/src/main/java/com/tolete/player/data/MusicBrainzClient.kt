package com.tolete.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Cliente MusicBrainz (metadados / MBID).
 * Respeita ~1 req/s; User-Agent obrigatório.
 */
object MusicBrainzClient {

    private const val BASE = "https://musicbrainz.org/ws/2"
    private const val USER_AGENT = "LyronMusicPlayer/0.50 (https://linktr.ee/pgcrgui)"
    private var lastRequestMs = 0L

    data class ArtistHit(
        val mbid: String,
        val name: String,
        val type: String?,
        val country: String?
    )

    data class ReleaseHit(
        val mbid: String,
        val title: String,
        val date: String?,
        val year: Int?,
        val artistCredit: String?
    )

    data class RecordingHit(
        val mbid: String,
        val title: String,
        val artistCredit: String?,
        val lengthMs: Long?,
        val firstReleaseDate: String?
    )

    data class TagSuggestion(
        val title: String?,
        val artist: String?,
        val album: String?,
        val year: Int?,
        val trackNumber: Int?,
        val mbid: String?
    )

    suspend fun searchArtist(name: String, limit: Int = 5): List<ArtistHit> = withContext(Dispatchers.IO) {
        if (name.isBlank()) return@withContext emptyList()
        try {
            val q = "artist:${quote(name)}"
            val root = getJson("$BASE/artist/?query=${enc(q)}&fmt=json&limit=$limit") ?: return@withContext emptyList()
            val arr = root.optJSONArray("artists") ?: return@withContext emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val a = arr.getJSONObject(i)
                    val id = a.optString("id").ifBlank { return@buildList }
                    add(
                        ArtistHit(
                            mbid = id,
                            name = a.optString("name"),
                            type = a.optString("type").takeIf { it.isNotBlank() },
                            country = a.optString("country").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun searchRelease(album: String, artist: String?, limit: Int = 5): List<ReleaseHit> =
        withContext(Dispatchers.IO) {
            if (album.isBlank()) return@withContext emptyList()
            try {
                val q = buildString {
                    append("release:${quote(album)}")
                    if (!artist.isNullOrBlank()) append(" AND artist:${quote(artist)}")
                }
                val root = getJson("$BASE/release/?query=${enc(q)}&fmt=json&limit=$limit")
                    ?: return@withContext emptyList()
                val arr = root.optJSONArray("releases") ?: return@withContext emptyList()
                buildList {
                    for (i in 0 until arr.length()) {
                        val r = arr.getJSONObject(i)
                        val date = r.optString("date").takeIf { it.isNotBlank() }
                        val year = date?.take(4)?.toIntOrNull()
                        val credit = r.optJSONObject("artist-credit")
                            ?.optJSONArray("artist-credit")
                            ?.optJSONObject(0)
                            ?.optJSONObject("artist")
                            ?.optString("name")
                            ?: r.optJSONArray("artist-credit")?.optJSONObject(0)
                                ?.optJSONObject("artist")?.optString("name")
                        add(
                            ReleaseHit(
                                mbid = r.optString("id"),
                                title = r.optString("title"),
                                date = date,
                                year = year,
                                artistCredit = credit
                            )
                        )
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

    suspend fun searchRecording(title: String, artist: String?, limit: Int = 5): List<RecordingHit> =
        withContext(Dispatchers.IO) {
            if (title.isBlank()) return@withContext emptyList()
            try {
                val q = buildString {
                    append("recording:${quote(title)}")
                    if (!artist.isNullOrBlank()) append(" AND artist:${quote(artist)}")
                }
                val root = getJson("$BASE/recording/?query=${enc(q)}&fmt=json&limit=$limit")
                    ?: return@withContext emptyList()
                val arr = root.optJSONArray("recordings") ?: return@withContext emptyList()
                buildList {
                    for (i in 0 until arr.length()) {
                        val r = arr.getJSONObject(i)
                        val credit = r.optJSONArray("artist-credit")
                            ?.optJSONObject(0)
                            ?.optJSONObject("artist")
                            ?.optString("name")
                        add(
                            RecordingHit(
                                mbid = r.optString("id"),
                                title = r.optString("title"),
                                artistCredit = credit,
                                lengthMs = r.optLong("length").takeIf { it > 0 },
                                firstReleaseDate = r.optString("first-release-date").takeIf { it.isNotBlank() }
                            )
                        )
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

    /** Sugere tags a partir de título + artista (melhor match). */
    suspend fun suggestTags(title: String, artist: String?): TagSuggestion? = withContext(Dispatchers.IO) {
        val rec = searchRecording(title, artist, limit = 3).firstOrNull() ?: return@withContext null
        val year = rec.firstReleaseDate?.take(4)?.toIntOrNull()
        val album = try {
            searchRelease(rec.title, rec.artistCredit ?: artist, limit = 1).firstOrNull()?.title
        } catch (_: Exception) {
            null
        }
        TagSuggestion(
            title = rec.title,
            artist = rec.artistCredit ?: artist,
            album = album,
            year = year,
            trackNumber = null,
            mbid = rec.mbid
        )
    }

    suspend fun releaseYear(album: String, artist: String?): Int? {
        return searchRelease(album, artist, limit = 3).firstOrNull()?.year
    }

    private fun quote(s: String) = "\"${s.replace("\"", "\\\"")}\""

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private suspend fun throttle() {
        val now = System.currentTimeMillis()
        val wait = 1100 - (now - lastRequestMs)
        if (wait > 0) delay(wait)
        lastRequestMs = System.currentTimeMillis()
    }

    private suspend fun getJson(urlStr: String): JSONObject? {
        throttle()
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
