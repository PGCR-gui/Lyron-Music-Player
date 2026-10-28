package com.tolete.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/**
 * Letras da QQ Music (y.qq.com).
 * Fornece letra sincronizada por linha (LRC) — ou simples, quando a faixa não tem tempos.
 * O QRC (palavra por palavra) da QQ usa criptografia proprietária e não é suportado aqui.
 */
internal object QqMusicLyricsClient {

    private val headers = mapOf(
        "Referer" to "https://y.qq.com/portal/player.html",
        "Origin" to "https://y.qq.com"
    )

    private data class Song(
        val mid: String,
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
        // 1) endpoint "musicu" (usado pelo cliente desktop)
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
                                .put("num_per_page", limit)
                                .put("page_num", 1)
                                .put("query", keyword)
                                .put("search_type", 0)
                        )
                )
            val resp = LyricsHttp.postJson("https://u.y.qq.com/cgi-bin/musicu.fcg", body.toString(), headers)
            val list = resp?.let {
                JSONObject(it).optJSONObject("req")?.optJSONObject("data")
                    ?.optJSONObject("body")?.optJSONObject("song")?.optJSONArray("list")
            }
            val parsed = list?.let { parseSongs(it) }.orEmpty()
            if (parsed.isNotEmpty()) return parsed
        } catch (_: Exception) { }

        // 2) fallback: endpoint antigo de busca
        return try {
            val url = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp" +
                "?format=json&new_json=1&cr=1&p=1&n=$limit&w=${LyricsFormat.enc(keyword)}"
            val resp = LyricsHttp.get(url, headers) ?: return emptyList()
            val list = JSONObject(resp).optJSONObject("data")?.optJSONObject("song")?.optJSONArray("list")
            list?.let { parseSongs(it) }.orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseSongs(arr: JSONArray): List<Song> {
        val out = mutableListOf<Song>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val mid = o.optString("mid")
            if (mid.isBlank()) continue
            val singers = o.optJSONArray("singer")
            val names = mutableListOf<String>()
            if (singers != null) {
                for (j in 0 until singers.length()) {
                    singers.optJSONObject(j)?.optString("name")?.takeIf { it.isNotBlank() }?.let { names += it }
                }
            }
            out += Song(
                mid = mid,
                name = o.optString("title").ifBlank { o.optString("name") },
                artist = names.joinToString(", "),
                album = o.optJSONObject("album")?.optString("name").orEmpty(),
                durationSec = o.optInt("interval", 0)
            )
        }
        return out
    }

    // ---------- Letra ----------

    private fun fetch(song: Song): LyricsSearchResult? {
        val text = fetchLyricText(song.mid) ?: return null
        val lines = LyricsExtractor.parseLyricsContent(text)
        if (lines.isEmpty()) return null
        return LyricsFormat.build(
            source = LyricsSource.QQ_MUSIC,
            id = song.mid,
            trackName = song.name,
            artistName = song.artist,
            albumName = song.album,
            durationSec = song.durationSec,
            rawLines = lines
        )
    }

    private fun fetchLyricText(mid: String): String? {
        return try {
            val url = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg" +
                "?songmid=$mid&g_tk=5381&format=json&inCharset=utf8&outCharset=utf-8&platform=yqq"
            var resp = LyricsHttp.get(url, headers) ?: return null
            // Se vier embrulhado em callback JSONP, extrai só o objeto JSON.
            if (!resp.trimStart().startsWith("{")) {
                val a = resp.indexOf('{')
                val b = resp.lastIndexOf('}')
                if (a < 0 || b <= a) return null
                resp = resp.substring(a, b + 1)
            }
            val raw = JSONObject(resp).optString("lyric")
            if (raw.isBlank()) return null
            // Normalmente vem em base64; se já vier como LRC, usa direto.
            val decoded = if (raw.contains('[')) raw else {
                String(Base64.getMimeDecoder().decode(raw), Charsets.UTF_8)
            }
            LyricsFormat.unescapeHtml(decoded).takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }
}
