package com.tolete.player.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Cliente da API pública https://lrclib.net
 * Endpoints usados: GET /api/get e GET /api/search
 */
object LrcLibClient {

    private const val BASE = "https://lrclib.net"
    private const val USER_AGENT = "LyronMusicPlayer/1.0 (https://linktr.ee/pgcrgui)"

    data class SearchResult(
        val id: Int,
        val trackName: String,
        val artistName: String,
        val albumName: String,
        val durationSec: Int,
        val hasSynced: Boolean,
        val hasPlain: Boolean,
        val syncedLyrics: String?,
        val plainLyrics: String?
    ) {
        fun toLyricLines(): List<LyricLine> {
            if (!syncedLyrics.isNullOrBlank()) {
                val lines = LyricsExtractor.parseLyricsContent(syncedLyrics)
                if (lines.isNotEmpty()) return lines
            }
            if (!plainLyrics.isNullOrBlank()) {
                return LyricsExtractor.parseLyricsContent(plainLyrics)
            }
            return emptyList()
        }

        fun toLrcText(): String? {
            if (!syncedLyrics.isNullOrBlank()) return syncedLyrics
            if (!plainLyrics.isNullOrBlank()) {
                return plainLyrics.lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .joinToString("\n") { "[00:00.00]$it" }
            }
            return null
        }
    }

    /**
     * Busca a melhor letra para a faixa.
     * Prefere /api/get (assinatura exata); se falhar, cai em /api/search.
     */
    suspend fun fetchLyrics(track: Track): List<LyricLine> = withContext(Dispatchers.IO) {
        try {
            getBySignature(track)?.let { if (it.isNotEmpty()) return@withContext it }
            searchAndPick(track)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Busca automática + texto LRC bruto (para embutir/salvar).
     */
    suspend fun fetchLyricsWithRaw(track: Track): Pair<List<LyricLine>, String?> = withContext(Dispatchers.IO) {
        try {
            getBySignatureRaw(track)?.let { (lines, raw) ->
                if (lines.isNotEmpty()) return@withContext lines to raw
            }
            val results = searchInternal(track.title, track.artist)
            val best = pickBest(results, track)
            if (best != null) {
                best.toLyricLines() to best.toLrcText()
            } else emptyList<LyricLine>() to null
        } catch (_: Exception) {
            emptyList<LyricLine>() to null
        }
    }

    /**
     * Busca manual: retorna lista de resultados para o usuário escolher.
     */
    suspend fun search(trackName: String, artistName: String = ""): List<SearchResult> =
        withContext(Dispatchers.IO) { searchInternal(trackName, artistName) }

    private fun searchInternal(trackName: String, artistName: String = ""): List<SearchResult> {
        val structured = buildString {
            append("track_name=").append(enc(trackName))
            if (artistName.isNotBlank()) append("&artist_name=").append(enc(artistName))
        }
        val primary = runSearch(structured)
        // null = o servidor falhou (502/503, sem rede…): uma segunda consulta não ajudaria.
        if (primary == null) return emptyList()
        if (primary.isNotEmpty()) return primary
        // Sem resultado na busca estruturada: tenta busca livre (título + artista).
        val free = "$trackName $artistName".trim()
        return runSearch("q=" + enc(free)).orEmpty()
    }

    /** null quando a requisição falhou; lista (possivelmente vazia) quando o servidor respondeu. */
    private fun runSearch(query: String): List<SearchResult>? {
        return try {
            val body = httpGet("$BASE/api/search?$query") ?: return null
            val arr = JSONArray(body)
            val out = mutableListOf<SearchResult>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val synced = o.strOrNull("syncedLyrics")
                val plain = o.strOrNull("plainLyrics")
                if (synced == null && plain == null) continue
                out += SearchResult(
                    id = o.optInt("id", i),
                    trackName = o.strOrNull("trackName").orEmpty(),
                    artistName = o.strOrNull("artistName").orEmpty(),
                    albumName = o.strOrNull("albumName").orEmpty(),
                    durationSec = o.optInt("duration", 0),
                    hasSynced = synced != null,
                    hasPlain = plain != null,
                    syncedLyrics = synced,
                    plainLyrics = plain
                )
            }
            out
        } catch (e: Exception) {
            Log.w("LrcLibClient", "Resposta inválida do LRCLIB", e)
            LyricsDiagnostics.error("LRCLIB", "resposta inválida")
            null
        }
    }

    /**
     * No Android, JSONObject.optString devolve o texto "null" quando o campo é JSON null
     * (o LRCLIB manda syncedLyrics/plainLyrics nulos para faixas sem letra), o que criava
     * "letras" com a palavra null. Esta função trata o null de verdade.
     */
    private fun JSONObject.strOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key, "").takeIf { it.isNotBlank() }
    }

    private fun getBySignature(track: Track): List<LyricLine>? {
        val (lines, _) = getBySignatureRaw(track) ?: return null
        return lines
    }

    private fun getBySignatureRaw(track: Track): Pair<List<LyricLine>, String?>? {
        val durationSec = (track.duration / 1000).toInt().coerceAtLeast(1)
        val q = buildString {
            append("artist_name=").append(enc(track.artist))
            append("&track_name=").append(enc(track.title))
            if (track.album.isNotBlank()) append("&album_name=").append(enc(track.album))
            append("&duration=").append(durationSec)
        }
        val body = httpGet("$BASE/api/get?$q") ?: return null
        if (body.isBlank() || body.startsWith("[")) return null
        val o = JSONObject(body)
        val synced = o.strOrNull("syncedLyrics")
        val plain = o.strOrNull("plainLyrics")
        val lines = parseRecord(o)
        if (lines.isEmpty()) return null
        val raw = synced ?: plain?.let { text ->
            text.lines().map { it.trim() }.filter { it.isNotEmpty() }
                .joinToString("\n") { "[00:00.00]$it" }
        }
        return lines to raw
    }

    private fun searchAndPick(track: Track): List<LyricLine> {
        val results = searchInternal(track.title, track.artist)
        return pickBest(results, track)?.toLyricLines() ?: emptyList()
    }

    private fun pickBest(results: List<SearchResult>, track: Track): SearchResult? {
        if (results.isEmpty()) return null
        val targetSec = (track.duration / 1000).toInt()
        var best: SearchResult? = null
        var bestDiff = Int.MAX_VALUE
        for (r in results) {
            val diff = if (r.durationSec > 0) kotlin.math.abs(r.durationSec - targetSec) else 999
            if (diff < bestDiff || (diff == bestDiff && r.hasSynced && best?.hasSynced != true)) {
                bestDiff = diff
                best = r
            }
        }
        return best
    }

    private fun parseRecord(o: JSONObject): List<LyricLine> {
        val synced = o.strOrNull("syncedLyrics")
        if (synced != null) {
            val lines = LyricsExtractor.parseLyricsContent(synced)
            if (lines.isNotEmpty()) return lines
        }
        val plain = o.strOrNull("plainLyrics")
        if (plain != null) {
            return LyricsExtractor.parseLyricsContent(plain)
        }
        return emptyList()
    }

    private fun enc(s: String): String =
        URLEncoder.encode(s.trim(), Charsets.UTF_8.name())

    /**
     * GET com até 2 tentativas em falhas transitórias (5xx, timeout, queda de rede).
     * 404 é resposta válida ("não existe") e não é repetido.
     */
    private fun httpGet(urlStr: String): String? {
        var attempt = 0
        while (attempt < 2) {
            attempt++
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12_000
                readTimeout = 12_000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }
            var retry = false
            try {
                val code = conn.responseCode
                if (code in 200..299) {
                    return conn.inputStream.bufferedReader().use { it.readText() }
                }
                Log.w("LrcLibClient", "HTTP $code em $urlStr (tentativa $attempt)")
                if (code != 404) {
                    LyricsDiagnostics.error("LRCLIB", if (code >= 500) "fora do ar (HTTP $code)" else "HTTP $code")
                }
                retry = code >= 500
            } catch (e: Exception) {
                Log.w("LrcLibClient", "Falha em $urlStr (tentativa $attempt)", e)
                LyricsDiagnostics.error("LRCLIB", LyricsDiagnostics.describe(e))
                retry = true
            } finally {
                conn.disconnect()
            }
            if (!retry) return null
            if (attempt < 2) {
                try { Thread.sleep(600) } catch (_: InterruptedException) { return null }
            }
        }
        return null
    }
}
