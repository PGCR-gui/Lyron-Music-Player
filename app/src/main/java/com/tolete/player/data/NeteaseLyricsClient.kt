package com.tolete.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Letras da NetEase Cloud Music (music.163.com).
 * - Palavra por palavra: campo "yrc" (formato [início,dur](início,dur,0)palavra...)
 * - Sincronizada: campo "lrc"
 */
internal object NeteaseLyricsClient {

    private val headers = mapOf(
        "Referer" to "https://music.163.com/",
        "Origin" to "https://music.163.com",
        "Cookie" to "os=pc; appver=2.10.13"
    )

    private data class Song(
        val id: Long,
        val name: String,
        val artist: String,
        val album: String,
        val durationSec: Int
    )

    suspend fun search(title: String, artist: String, limit: Int): List<LyricsSearchResult> = coroutineScope {
        val keyword = if (artist.isBlank()) title else "$title $artist"
        searchSongs(keyword, limit)
            .map { song -> async(Dispatchers.IO) { fetch(song) } }
            .awaitAll()
            .filterNotNull()
    }

    // ---------- Busca ----------

    private fun searchSongs(keyword: String, limit: Int): List<Song> {
        val form = "s=${LyricsFormat.enc(keyword)}&type=1&limit=$limit&offset=0"
        val endpoints = listOf(
            "https://music.163.com/api/search/get",
            "https://music.163.com/api/cloudsearch/pc"
        )
        for (url in endpoints) {
            val body = LyricsHttp.postForm(url, form, headers) ?: continue
            val songs = try {
                JSONObject(body).optJSONObject("result")?.optJSONArray("songs")
            } catch (_: Exception) {
                null
            } ?: continue
            val parsed = parseSongs(songs)
            if (parsed.isNotEmpty()) return parsed
        }
        return emptyList()
    }

    private fun parseSongs(arr: JSONArray): List<Song> {
        val out = mutableListOf<Song>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optLong("id", 0L)
            if (id == 0L) continue
            // /api/search/get usa artists/album/duration; cloudsearch usa ar/al/dt
            val artists = o.optJSONArray("artists") ?: o.optJSONArray("ar")
            val names = mutableListOf<String>()
            if (artists != null) {
                for (j in 0 until artists.length()) {
                    artists.optJSONObject(j)?.optString("name")?.takeIf { it.isNotBlank() }?.let { names += it }
                }
            }
            val album = (o.optJSONObject("album") ?: o.optJSONObject("al"))?.optString("name").orEmpty()
            val durMs = if (o.has("duration")) o.optLong("duration", 0L) else o.optLong("dt", 0L)
            out += Song(
                id = id,
                name = o.optString("name"),
                artist = names.joinToString(", "),
                album = album,
                durationSec = (durMs / 1000).toInt()
            )
        }
        return out
    }

    // ---------- Letra ----------

    private fun fetch(song: Song): LyricsSearchResult? {
        val json = eapiLyric(song.id) ?: classicLyric(song.id) ?: return null
        val yrc = json.optJSONObject("yrc")?.optString("lyric").orEmpty()
        val lrc = json.optJSONObject("lrc")?.optString("lyric").orEmpty()

        var lines: List<LyricLine> = emptyList()
        if (yrc.isNotBlank()) lines = parseYrc(yrc)
        if (lines.isEmpty() && lrc.isNotBlank()) lines = LyricsExtractor.parseLyricsContent(lrc)
        if (lines.isEmpty()) return null

        return LyricsFormat.build(
            source = LyricsSource.NETEASE,
            id = song.id.toString(),
            trackName = song.name,
            artistName = song.artist,
            albumName = song.album,
            durationSec = song.durationSec,
            rawLines = lines
        )
    }

    /** Endpoint usado pelo cliente oficial; é o que entrega o "yrc" (palavra por palavra). */
    private fun eapiLyric(id: Long): JSONObject? {
        return try {
            val path = "/api/song/lyric/v1"
            val text = """{"id":"$id","cp":false,"tv":0,"lv":0,"rv":0,"kv":0,"yv":0,"ytv":0,"yrv":0}"""
            val digest = md5Hex("nobody${path}use${text}md5forencrypt")
            val payload = "$path-36cd479b6b5-$text-36cd479b6b5-$digest"
            val params = aesEcbHex(payload, "e82ckenh8dichen8")
            val resp = LyricsHttp.postForm(
                "https://interface3.music.163.com/eapi/song/lyric/v1",
                "params=$params",
                headers
            ) ?: return null
            val o = JSONObject(resp)
            if (o.optInt("code", 200) != 200) null else o
        } catch (_: Exception) {
            null
        }
    }

    /** Fallback sem criptografia: só traz "lrc" (linha por linha). */
    private fun classicLyric(id: Long): JSONObject? {
        return try {
            val resp = LyricsHttp.get(
                "https://music.163.com/api/song/lyric?id=$id&lv=-1&kv=-1&tv=-1",
                headers
            ) ?: return null
            val o = JSONObject(resp)
            if (o.optInt("code", 200) != 200) null else o
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Formato YRC:
     * [inícioLinhaMs,duraçãoMs](inícioPalavraMs,duraçãoMs,0)palavra(inícioPalavraMs,duraçãoMs,0)palavra...
     * Os tempos das palavras já são absolutos. Linhas JSON de créditos ({"t":0,"c":[...]}) são ignoradas.
     */
    fun parseYrc(text: String): List<LyricLine> {
        val lineRe = Regex("""^\[(\d+),(\d+)](.*)$""")
        val wordRe = Regex("""\((\d+),(\d+),-?\d+\)""")
        val out = mutableListOf<LyricLine>()
        for (raw in text.lines()) {
            val l = raw.trim()
            if (l.isEmpty() || l.startsWith("{")) continue
            val m = lineRe.find(l) ?: continue
            val start = m.groupValues[1].toLongOrNull() ?: continue
            val dur = m.groupValues[2].toLongOrNull() ?: 0L
            val rest = m.groupValues[3]
            val tags = wordRe.findAll(rest).toList()
            if (tags.isEmpty()) {
                val tx = rest.trim()
                if (tx.isNotEmpty()) out += LyricLine(timeMs = start, text = tx, endMs = start + dur)
                continue
            }
            val words = mutableListOf<LyricWord>()
            for (i in tags.indices) {
                val tag = tags[i]
                val from = tag.range.last + 1
                val to = if (i + 1 < tags.size) tags[i + 1].range.first else rest.length
                val w = rest.substring(from, to)
                if (w.isEmpty()) continue
                val ws = tag.groupValues[1].toLongOrNull() ?: continue
                val wd = tag.groupValues[2].toLongOrNull() ?: 0L
                words += LyricWord(timeMs = ws, text = w, endMs = ws + wd)
            }
            if (words.isEmpty()) continue
            out += LyricLine(
                timeMs = start,
                text = words.joinToString("") { it.text }.trim(),
                words = words,
                endMs = start + dur
            )
        }
        return out.sortedBy { it.timeMs }
    }

    private fun md5Hex(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun aesEcbHex(plain: String, key: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"))
        return cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02X".format(it) }
    }
}
