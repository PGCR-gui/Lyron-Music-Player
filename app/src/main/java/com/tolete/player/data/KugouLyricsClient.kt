package com.tolete.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Inflater

/**
 * Letras da KuGou (kugou.com).
 * - Palavra por palavra: formato KRC (XOR + zlib), tempos de cada palavra relativos ao início da linha.
 * - Sincronizada: quando o KRC não traz marcas por palavra (ou cai no fmt=lrc).
 */
internal object KugouLyricsClient {

    /** Chave XOR fixa do formato KRC. */
    private val KRC_KEY = intArrayOf(64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45, 206, 210, 110, 105)

    private data class Song(
        val hash: String,
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
        val enc = LyricsFormat.enc(keyword)

        try {
            val resp = LyricsHttp.get(
                "https://mobileservice.kugou.com/api/v3/search/song" +
                    "?format=json&keyword=$enc&page=1&pagesize=$limit&showtype=1"
            )
            val info = resp?.let { JSONObject(it).optJSONObject("data")?.optJSONArray("info") }
            val parsed = info?.let { parseMobile(it) }.orEmpty()
            if (parsed.isNotEmpty()) return parsed
        } catch (_: Exception) { }

        return try {
            val resp = LyricsHttp.get(
                "https://songsearch.kugou.com/song_search_v2" +
                    "?keyword=$enc&page=1&pagesize=$limit&platform=WebFilter"
            ) ?: return emptyList()
            val lists = JSONObject(resp).optJSONObject("data")?.optJSONArray("lists")
            lists?.let { parseWeb(it) }.orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseMobile(arr: JSONArray): List<Song> {
        val out = mutableListOf<Song>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val hash = o.optString("hash")
            if (hash.isBlank()) continue
            out += Song(
                hash = hash,
                name = o.optString("songname"),
                artist = o.optString("singername"),
                album = o.optString("album_name"),
                durationSec = o.optInt("duration", 0)
            )
        }
        return out
    }

    private fun parseWeb(arr: JSONArray): List<Song> {
        val out = mutableListOf<Song>()
        val tagRe = Regex("<[^>]*>")
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val hash = o.optString("FileHash")
            if (hash.isBlank()) continue
            out += Song(
                hash = hash,
                name = o.optString("SongName").replace(tagRe, ""),
                artist = o.optString("SingerName").replace(tagRe, ""),
                album = o.optString("AlbumName").replace(tagRe, ""),
                durationSec = o.optInt("Duration", 0)
            )
        }
        return out
    }

    // ---------- Letra ----------

    private fun fetch(song: Song): LyricsSearchResult? {
        val kw = LyricsFormat.enc(if (song.artist.isBlank()) song.name else "${song.artist} - ${song.name}")
        val searchUrl = "https://lyrics.kugou.com/search?ver=1&man=yes&client=pc" +
            "&keyword=$kw&duration=${song.durationSec * 1000L}&hash=${song.hash}"
        val candidate = try {
            val resp = LyricsHttp.get(searchUrl) ?: return null
            JSONObject(resp).optJSONArray("candidates")?.optJSONObject(0)
        } catch (_: Exception) {
            null
        } ?: return null

        val id = candidate.optString("id")
        val key = candidate.optString("accesskey")
        if (id.isBlank() || key.isBlank()) return null

        // 1) KRC (palavra por palavra)
        var lines: List<LyricLine> = emptyList()
        download(id, key, "krc")?.let { text ->
            lines = parseKrc(text).ifEmpty { LyricsExtractor.parseLyricsContent(text) }
        }
        // 2) fallback: LRC (por linha)
        if (lines.isEmpty()) {
            download(id, key, "lrc")?.let { lines = LyricsExtractor.parseLyricsContent(it) }
        }
        if (lines.isEmpty()) return null

        return LyricsFormat.build(
            source = LyricsSource.KUGOU,
            id = song.hash,
            trackName = song.name,
            artistName = song.artist,
            albumName = song.album,
            durationSec = song.durationSec,
            rawLines = lines
        )
    }

    private fun download(id: String, accessKey: String, fmt: String): String? {
        return try {
            val resp = LyricsHttp.get(
                "https://lyrics.kugou.com/download?ver=1&client=pc" +
                    "&id=$id&accesskey=$accessKey&fmt=$fmt&charset=utf8"
            ) ?: return null
            val content = JSONObject(resp).optString("content")
            if (content.isBlank()) return null
            val bytes = Base64.getMimeDecoder().decode(content)
            val text = if (bytes.size > 4 && String(bytes, 0, 4, Charsets.ISO_8859_1) == "krc1") {
                decryptKrc(bytes)
            } else {
                String(bytes, Charsets.UTF_8)
            }
            text?.removePrefix("\uFEFF")?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    /** "krc1" + dados: XOR com a chave fixa e depois zlib. */
    private fun decryptKrc(raw: ByteArray): String? {
        val xored = ByteArray(raw.size - 4) { i ->
            (raw[i + 4].toInt() xor KRC_KEY[i % KRC_KEY.size]).toByte()
        }
        val inflater = Inflater()
        return try {
            inflater.setInput(xored)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                out.write(buf, 0, n)
            }
            String(out.toByteArray(), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        } finally {
            inflater.end()
        }
    }

    /**
     * Formato KRC: [inícioLinhaMs,duraçãoMs]<deslocPalavraMs,duraçãoMs,0>palavra<...>palavra
     * O deslocamento de cada palavra é relativo ao início da linha.
     */
    fun parseKrc(text: String): List<LyricLine> {
        val lineRe = Regex("""^\[(\d+),(\d+)](.*)$""")
        val wordRe = Regex("""<(\d+),(\d+),-?\d+>""")
        val out = mutableListOf<LyricLine>()
        for (raw in text.lines()) {
            val l = raw.trim()
            if (l.isEmpty()) continue
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
                val offset = tag.groupValues[1].toLongOrNull() ?: continue
                val wd = tag.groupValues[2].toLongOrNull() ?: 0L
                words += LyricWord(timeMs = start + offset, text = w, endMs = start + offset + wd)
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
}
