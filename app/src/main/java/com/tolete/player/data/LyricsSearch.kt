package com.tolete.player.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** Fontes disponíveis na busca manual de letras. */
enum class LyricsSource(val label: String) {
    LRCLIB("LRCLIB"),
    QQ_MUSIC("QQ Music"),
    NETEASE("NetEase"),
    KUGOU("KuGou")
}

/** Tipo da letra encontrada. */
enum class LyricsType(val label: String) {
    PLAIN("Simples"),
    SYNCED("Sincronizada"),
    WORD("Palavra por palavra")
}

/**
 * Resultado unificado da busca de letras (qualquer fonte).
 * [lines] já vem parseado; [lrcText] é o texto pronto para gravar em .lrc / tags
 * (eLRC com <mm:ss.xx> por palavra quando o tipo é [LyricsType.WORD]).
 */
data class LyricsSearchResult(
    val id: String,
    val source: LyricsSource,
    val trackName: String,
    val artistName: String,
    val albumName: String,
    val durationSec: Int,
    val type: LyricsType,
    val lines: List<LyricLine>,
    val lrcText: String
)

/**
 * Busca letras em todas as fontes ao mesmo tempo (LRCLIB, QQ Music, NetEase, KuGou).
 * Uma fonte que falhe ou fique offline simplesmente não contribui com resultados.
 */
object LyricsSearch {

    private const val LRCLIB_MAX = 8
    private const val OTHER_MAX = 5

    suspend fun search(trackName: String, artistName: String): List<LyricsSearchResult> =
        coroutineScope {
            val t = trackName.trim()
            val a = artistName.trim()
            if (t.isBlank()) return@coroutineScope emptyList()

            val tasks: List<Deferred<List<LyricsSearchResult>>> = listOf(
                async(Dispatchers.IO) { safe { LrcLibClient.search(t, a).take(LRCLIB_MAX).mapNotNull { it.toUnified() } } },
                async(Dispatchers.IO) { safe { QqMusicLyricsClient.search(t, a, OTHER_MAX) } },
                async(Dispatchers.IO) { safe { NeteaseLyricsClient.search(t, a, OTHER_MAX) } },
                async(Dispatchers.IO) { safe { KugouLyricsClient.search(t, a, OTHER_MAX) } }
            )
            rank(tasks.awaitAll().flatten().distinctBy { it.id }, t, a)
        }

    private inline fun safe(block: () -> List<LyricsSearchResult>): List<LyricsSearchResult> =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }

    /** Resultados com título/artista iguais ao pedido sobem; dentro do mesmo grau mantém a ordem das fontes. */
    private fun rank(all: List<LyricsSearchResult>, title: String, artist: String): List<LyricsSearchResult> {
        val nt = norm(title)
        val na = norm(artist)
        fun score(r: LyricsSearchResult): Int {
            val rt = norm(r.trackName)
            val ra = norm(r.artistName)
            val titleRank = when {
                rt == nt -> 0
                rt.isNotEmpty() && nt.isNotEmpty() && (rt.contains(nt) || nt.contains(rt)) -> 1
                else -> 2
            }
            val artistRank = when {
                na.isEmpty() -> 0
                ra.contains(na) || (ra.isNotEmpty() && na.contains(ra)) -> 0
                else -> 1
            }
            return titleRank * 2 + artistRank
        }
        return all.withIndex()
            .sortedWith(compareBy({ score(it.value) }, { it.index }))
            .map { it.value }
    }

    private fun norm(s: String): String = s.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
}

/** LRCLIB → modelo unificado. Retorna null se não houver letra utilizável. */
internal fun LrcLibClient.SearchResult.toUnified(): LyricsSearchResult? {
    val lines = toLyricLines()
    val raw = toLrcText()
    if (lines.isEmpty() || raw.isNullOrBlank()) return null
    return LyricsSearchResult(
        id = "lrclib:$id",
        source = LyricsSource.LRCLIB,
        trackName = trackName,
        artistName = artistName,
        albumName = albumName,
        durationSec = durationSec,
        type = LyricsFormat.classify(lines),
        lines = lines,
        lrcText = raw
    )
}

/** Utilidades de formato compartilhadas pelos clientes de letras. */
internal object LyricsFormat {

    private val creditRegex = Regex(
        """^\s*(作词|作詞|作曲|编曲|編曲|制作人|制作|監製|监制|混音|母带|录音|词|曲|歌手|歌名|专辑|OP|SP|""" +
            """Lyrics?\s*by|Music\s*by|Composed\s*by|Written\s*by|Arranged\s*by|Produced\s*by|""" +
            """Lyricist|Composer|Arranger|Producer)\s*[:：]""",
        RegexOption.IGNORE_CASE
    )

    private val instrumentalRegex = Regex("纯音乐|純音樂|暂无歌词|暫無歌詞|没有填词|沒有填詞")

    fun classify(lines: List<LyricLine>): LyricsType = when {
        lines.any { it.words.isNotEmpty() } -> LyricsType.WORD
        lines.any { it.timeMs >= 0 } -> LyricsType.SYNCED
        else -> LyricsType.PLAIN
    }

    fun fmtTs(ms: Long): String {
        val t = ms.coerceAtLeast(0L)
        return String.format(Locale.US, "%02d:%02d.%02d", t / 60_000, (t % 60_000) / 1000, (t % 1000) / 10)
    }

    /** Gera LRC (linha) ou eLRC (palavra) conforme o conteúdo. Letra simples segue o padrão já usado pelo app. */
    fun toLrcText(lines: List<LyricLine>, type: LyricsType): String = buildString {
        for (line in lines) {
            when {
                type == LyricsType.PLAIN || line.timeMs < 0 ->
                    appendLine("[00:00.00]${line.text}")
                line.words.isNotEmpty() -> {
                    append('[').append(fmtTs(line.timeMs)).append(']')
                    line.words.forEach { w -> append('<').append(fmtTs(w.timeMs)).append('>').append(w.text) }
                    append('\n')
                }
                else -> appendLine("[${fmtTs(line.timeMs)}]${line.text}")
            }
        }
    }.trimEnd()

    /** Remove linhas de créditos ("作词 : ...") no começo da música e descarta placeholders de instrumental. */
    fun build(
        source: LyricsSource,
        id: String,
        trackName: String,
        artistName: String,
        albumName: String,
        durationSec: Int,
        rawLines: List<LyricLine>
    ): LyricsSearchResult? {
        val lines = rawLines.filterNot {
            it.timeMs in 0..30_000 && creditRegex.containsMatchIn(it.text)
        }
        if (lines.isEmpty()) return null
        if (lines.size <= 3 && instrumentalRegex.containsMatchIn(lines.joinToString(" ") { it.text })) return null
        val type = classify(lines)
        return LyricsSearchResult(
            id = "${source.name.lowercase(Locale.ROOT)}:$id",
            source = source,
            trackName = trackName,
            artistName = artistName,
            albumName = albumName,
            durationSec = durationSec,
            type = type,
            lines = lines,
            lrcText = toLrcText(lines, type)
        )
    }

    /** Entidades HTML que a QQ Music devolve no texto da letra. */
    fun unescapeHtml(s: String): String {
        if (!s.contains('&')) return s
        var out = s.replace("&apos;", "'")
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
        out = Regex("&#(\\d+);").replace(out) { m ->
            try {
                String(Character.toChars(m.groupValues[1].toInt()))
            } catch (_: Exception) {
                m.value
            }
        }
        return out.replace("&amp;", "&")
    }

    fun enc(s: String): String = URLEncoder.encode(s.trim(), "UTF-8")
}

/** HTTP mínimo (HTTPS apenas — o app bloqueia tráfego em texto puro). */
internal object LyricsHttp {

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Safari/537.36"

    fun get(url: String, headers: Map<String, String> = emptyMap()): String? =
        request(url, "GET", null, null, headers)

    fun postForm(url: String, body: String, headers: Map<String, String> = emptyMap()): String? =
        request(url, "POST", body.toByteArray(Charsets.UTF_8), "application/x-www-form-urlencoded", headers)

    fun postJson(url: String, body: String, headers: Map<String, String> = emptyMap()): String? =
        request(url, "POST", body.toByteArray(Charsets.UTF_8), "application/json;charset=UTF-8", headers)

    private fun request(
        url: String,
        method: String,
        body: ByteArray?,
        contentType: String?,
        headers: Map<String, String>
    ): String? {
        val conn = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (_: Exception) {
            return null
        }
        return try {
            conn.requestMethod = method
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", "application/json, text/plain, */*")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.doOutput = true
                if (contentType != null) conn.setRequestProperty("Content-Type", contentType)
                conn.outputStream.use { it.write(body) }
            }
            if (conn.responseCode !in 200..299) null
            else conn.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }
}
