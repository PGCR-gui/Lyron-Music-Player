package com.tolete.player.data

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.supervisorScope
import java.net.HttpURLConnection
import java.net.URL
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLException

/** Fontes disponíveis na busca manual de letras. */
enum class LyricsSource(val label: String) {
    LYRON_REPOSITORY("Lyron · GitHub"),
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

/** Variante linguística de uma letra, quando o provedor a distingue. */
enum class LyricsVariant(val label: String) {
    ORIGINAL("Original"),
    ROMANIZED("Romanizada")
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
    val lrcText: String,
    /** Quem sincronizou/enviou (ex.: repositório Lyron no GitHub). */
    val syncedBy: String? = null,
    /** Variante oferecida explicitamente pela fonte (ex.: original/romanizada da NetEase). */
    val variant: LyricsVariant? = null
) {
    /** Efeitos/segmentos ELRC+ são adicionais ao tipo simples, sincronizado ou por palavra. */
    val hasElrcEffects: Boolean
        get() = LyricsFormat.hasElrcEffects(lines, lrcText)
}

/**
 * Diagnóstico da última busca: por fonte, quantos resultados vieram ou por que falhou
 * (HTTP 503, sem conexão, tempo esgotado…). Serve para a tela distinguir "a fonte está fora
 * do ar" de "a música realmente não existe lá".
 */
internal object LyricsDiagnostics {
    private val counts = ConcurrentHashMap<String, Int>()
    private val errors = ConcurrentHashMap<String, String>()

    fun reset() {
        counts.clear()
        errors.clear()
    }

    fun count(source: String, n: Int) {
        counts[source] = n
    }

    /** Guarda só o primeiro erro de cada fonte (o mais provável de ser a causa). */
    fun error(source: String, message: String) {
        errors.putIfAbsent(source, message)
    }

    fun summary(order: List<String>): String = order.joinToString(" · ") { name ->
        val n = counts[name] ?: 0
        val err = errors[name]
        when {
            n > 0 -> "$name: $n"
            err != null -> "$name: $err"
            else -> "$name: 0"
        }
    }

    fun labelFor(url: String): String {
        val host = try { URL(url).host.lowercase(Locale.ROOT) } catch (_: Exception) { "" }
        return when {
            host.contains("lrclib") -> "LRCLIB"
            host.contains("163.com") -> "NetEase"
            host.contains("qq.com") -> "QQ Music"
            host.contains("kugou") -> "KuGou"
            host.contains("githubusercontent") || host.contains("github") -> "Lyron"
            else -> host.ifBlank { "rede" }
        }
    }

    fun describe(e: Throwable): String = when (e) {
        is UnknownHostException -> "sem conexão/DNS"
        is SocketTimeoutException -> "tempo esgotado"
        is SSLException -> "erro TLS (confira data/hora)"
        else -> e.javaClass.simpleName.ifBlank { "erro" }
    }
}

/**
 * Busca letras em todas as fontes ao mesmo tempo, incluindo o repositório do Lyron.
 * Uma fonte que falhe ou fique offline simplesmente não contribui com resultados.
 */
object LyricsSearch {

    /** Quando true, a variante romanizada da NetEase sobe antes da original quando ambas existem. */
    @Volatile
    var preferRomanization: Boolean = false

    /** Resumo por fonte da última busca (ex.: "LRCLIB: HTTP 503 · NetEase: 2"). */
    @Volatile
    var lastReport: String = ""
        private set

    private val SOURCE_ORDER = listOf("Lyron", "LRCLIB", "QQ Music", "NetEase", "KuGou")

    private const val LRCLIB_MAX = 8
    private const val OTHER_MAX = 5

    suspend fun search(
        trackName: String,
        artistName: String,
        preferRomanization: Boolean = LyricsSearch.preferRomanization,
        sourceOrder: List<String>? = null,
        typeOrder: List<String>? = null
    ): List<LyricsSearchResult> =
        supervisorScope {
            val t = trackName.trim()
            val a = artistName.trim()
            if (t.isBlank()) return@supervisorScope emptyList()
            LyricsDiagnostics.reset()
            val searchJob = coroutineContext[Job] ?: return@supervisorScope emptyList()

            val tasks: List<Deferred<List<LyricsSearchResult>>> = listOf(
                async(Dispatchers.IO) { safe(searchJob, "Lyron") { LyronLyricsRepository.search(t, a) } },
                async(Dispatchers.IO) { safe(searchJob, "LRCLIB") { LrcLibClient.search(t, a).take(LRCLIB_MAX).mapNotNull { it.toUnified() } } },
                async(Dispatchers.IO) { safe(searchJob, "QQ Music") { QqMusicLyricsClient.search(t, a, OTHER_MAX) } },
                async(Dispatchers.IO) { safe(searchJob, "NetEase") { NeteaseLyricsClient.search(t, a, OTHER_MAX, preferRomanization) } },
                async(Dispatchers.IO) { safe(searchJob, "KuGou") { KugouLyricsClient.search(t, a, OTHER_MAX) } }
            )
            val ranked = rank(
                tasks.awaitAll().flatten().distinctBy { it.id },
                t, a,
                sourceOrder = sourceOrder,
                typeOrder = typeOrder
            ).map { result ->
                result.copy(lines = LyricsFormat.normalizeBackgroundLines(result.lines))
            }
            lastReport = LyricsDiagnostics.summary(SOURCE_ORDER)
            Log.i("LyricsSearch", "\"$t\" / \"$a\" -> $lastReport")
            ranked
        }

    private suspend fun safe(
        parentJob: Job,
        source: String,
        block: suspend () -> List<LyricsSearchResult>
    ): List<LyricsSearchResult> =
        try {
            block().also { LyricsDiagnostics.count(source, it.size) }
        } catch (e: CancellationException) {
            // Mantém o cancelamento solicitado pelo usuário, mas trata cancelamento isolado
            // de um provedor como falha local para preservar resultados das outras fontes.
            parentJob.ensureActive()
            Log.w("LyricsSearch", "$source search canceled", e)
            LyricsDiagnostics.error(source, "cancelada")
            emptyList()
        } catch (e: Exception) {
            Log.w("LyricsSearch", "$source search failed", e)
            LyricsDiagnostics.error(source, LyricsDiagnostics.describe(e))
            emptyList()
        }

    /**
     * Ordena resultados da busca manual (e também da automática quando chamada com as listas).
     * Prioridade: match de título/artista → ordem de tipo configurada → ordem de fonte configurada
     * → ordem em que chegaram. Se [sourceOrder]/[typeOrder] forem nulos, mantém o comportamento antigo
     * (Lyron primeiro).
     */
    private fun rank(
        all: List<LyricsSearchResult>,
        title: String,
        artist: String,
        sourceOrder: List<String>? = null,
        typeOrder: List<String>? = null
    ): List<LyricsSearchResult> {
        val nt = norm(title)
        val na = norm(artist)
        val sourceRank = sourceOrder
            ?.mapIndexed { i, name -> name to i }
            ?.toMap()
            .orEmpty()
        val typeRank = typeOrder
            ?.mapIndexed { i, name -> name to i }
            ?.toMap()
            .orEmpty()
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
            .sortedWith(
                compareBy<IndexedValue<LyricsSearchResult>> { score(it.value) }
                    .thenBy {
                        typeRank[it.value.type.name] ?: 99
                    }
                    .thenBy {
                        if (sourceRank.isNotEmpty()) {
                            sourceRank[it.value.source.name] ?: 99
                        } else {
                            // Fallback legado: Lyron primeiro
                            if (it.value.source == LyricsSource.LYRON_REPOSITORY) 0 else 1
                        }
                    }
                    .thenBy { it.index }
            )
            .map { it.value }
    }

    private fun norm(s: String): String = s.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
}

/** LRCLIB → modelo unificado. Retorna null se não houver letra utilizável. */
internal fun LrcLibClient.SearchResult.toUnified(): LyricsSearchResult? {
    val lines = toLyricLines()
    val raw = toLrcText()?.let { LyricsFormat.unescapeHtml(it) }
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

    private val elrcEffectRegex = Regex(
        "\\{(sil|glo|pop|des|ded|eco|zou|zin|sli|typ|fla|vib|rnb|frz|shd|wav)\\}",
        RegexOption.IGNORE_CASE
    )

    fun hasElrcEffects(lines: List<LyricLine>, rawText: String): Boolean =
        elrcEffectRegex.containsMatchIn(rawText) || lines.any { line ->
            line.words.any { it.isSyllable || it.animation != LyricAnimation.NONE }
        }

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

    private val backgroundPrefixRegex = Regex(
        "^\\s*(?:\\{bg\\}|\\[bg\\]|\\(bg\\)|（bg）)\\s*",
        RegexOption.IGNORE_CASE
    )

    /** Marca e remove prefixos legados/externos de backing vocal, sem tocar em tempos ou palavras. */
    fun normalizeBackgroundLines(lines: List<LyricLine>): List<LyricLine> {
        // Chaves/colchetes escapados dos dois lados: o regex (ICU) do Android lança
        // PatternSyntaxException com "}" ou "]" soltos.
        val prefix = backgroundPrefixRegex
        return lines.map { line ->
            val textHadMarker = prefix.containsMatchIn(line.text)
            val cleanText = prefix.replaceFirst(line.text, "")
            val firstWord = line.words.firstOrNull()
            val wordHadMarker = firstWord != null && prefix.containsMatchIn(firstWord.text)
            val words = if (wordHadMarker) line.words.mapIndexed { index, word ->
                if (index == 0) word.copy(text = prefix.replaceFirst(word.text, "")) else word
            } else line.words
            val agentIsBackground = line.agent?.trim()?.lowercase(Locale.ROOT)?.let {
                it in setOf("bg", "background", "backing", "backing vocal", "backing vocals")
            } == true
            line.copy(
                text = cleanText,
                words = words,
                isBackground = line.isBackground || textHadMarker || wordHadMarker || agentIsBackground
            )
        }
    }

    fun fmtTs(ms: Long): String {
        val t = ms.coerceAtLeast(0L)
        return String.format(Locale.US, "%02d:%02d.%02d", t / 60_000, (t % 60_000) / 1000, (t % 1000) / 10)
    }

    /** Gera LRC (linha) ou eLRC (palavra) conforme o conteúdo. Letra simples segue o padrão já usado pelo app. */
    fun toLrcText(lines: List<LyricLine>, type: LyricsType): String = buildString {
        for (line in lines) {
            val markers = (if (line.isBackground) "{bg}" else "") +
                (if (line.isTranslation) "{tr}" else "")
            when {
                type == LyricsType.PLAIN || line.timeMs < 0 ->
                    appendLine("[00:00.00]$markers${line.text}")
                line.words.isNotEmpty() -> {
                    append('[').append(fmtTs(line.timeMs)).append(']').append(markers)
                    line.words.forEach { w -> append('<').append(fmtTs(w.timeMs)).append('>').append(w.text) }
                    append('\n')
                }
                else -> appendLine("[${fmtTs(line.timeMs)}]$markers${line.text}")
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
        rawLines: List<LyricLine>,
        variant: LyricsVariant? = null
    ): LyricsSearchResult? {
        val lines = normalizeBackgroundLines(rawLines).filterNot {
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
            lrcText = toLrcText(lines, type),
            variant = variant
        )
    }

    private val numericEntityRegex = Regex("&#(\\d+);")
    private val hexEntityRegex = Regex("&#[xX]([0-9a-fA-F]+);")

    /**
     * Converte entidades HTML (&apos; &quot; &amp; &#39; &#x27; ...) em caracteres normais.
     * Várias fontes (QQ Music, repositório Lyron, LRCLIB, Kugou...) entregam o texto assim.
     * Repete até estabilizar para cobrir texto codificado em dobro (&amp;apos;).
     */
    fun unescapeHtml(s: String): String {
        if (!s.contains('&')) return s
        var out = s
        repeat(3) {
            if (!out.contains('&')) return out
            val before = out
            out = out
                .replace("&apos;", "'")
                .replace("&#039;", "'")
                .replace("&quot;", "\"")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&nbsp;", " ")
            out = numericEntityRegex.replace(out) { m -> codePointToString(m.groupValues[1].toIntOrNull(), m.value) }
            out = hexEntityRegex.replace(out) { m -> codePointToString(m.groupValues[1].toIntOrNull(16), m.value) }
            out = out.replace("&amp;", "&")
            if (out == before) return out
        }
        return out
    }

    private fun codePointToString(cp: Int?, fallback: String): String =
        try {
            if (cp != null && Character.isValidCodePoint(cp)) String(Character.toChars(cp)) else fallback
        } catch (_: Exception) {
            fallback
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
            val code = conn.responseCode
            if (code !in 200..299) {
                Log.w("LyricsHttp", "HTTP $code em $url")
                LyricsDiagnostics.error(LyricsDiagnostics.labelFor(url), "HTTP $code")
                null
            } else conn.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w("LyricsHttp", "Falha em $url", e)
            LyricsDiagnostics.error(LyricsDiagnostics.labelFor(url), LyricsDiagnostics.describe(e))
            null
        } finally {
            conn.disconnect()
        }
    }
}
