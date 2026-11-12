package com.tolete.player.data

import android.content.Context
import android.media.MediaMetadataRetriever
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.charset.Charset

/**
 * Extrai letras de:
 *  - arquivo .lrc / .elrc / .ttml ao lado da faixa (prioridade na reprodução)
 *  - tags embutidas (ID3v2 USLT/SYLT em MP3, comentários Vorbis em FLAC/OGG/Opus)
 *
 * Sem cache do app. Na reprodução o .lrc externo vence se existir; senão usa tags.
 * O editor permite escolher explicitamente a fonte.
 *
 * IMPORTANTE: nunca carrega o arquivo de áudio inteiro na memória. Um FLAC 24 bit/96 kHz
 * passa fácil de 100 MB, e ler tudo (+ cópia em String + regex) estourava o heap com
 * OutOfMemoryError, derrubando o app. Agora só o início do arquivo (onde ficam as tags) é lido.
 */
object LyricsExtractor {

    /** Teto para uma tag ID3 / bloco Vorbis mantido na memória (capas grandes ficam dentro da tag). */
    private const val MAX_TAG_BYTES = 16 * 1024 * 1024

    /** Quanto do início de um .ogg/.opus examinamos atrás dos comentários. */
    private const val OGG_SCAN_BYTES = 512 * 1024

    fun load(context: Context, track: Track): List<LyricLine> = try {
        loadInternal(context, track)
    } catch (_: OutOfMemoryError) {
        // OOM é Error, não Exception: sem isto o app fecha. Letra é opcional.
        emptyList()
    }

    private fun loadInternal(context: Context, track: Track): List<LyricLine> {
        // Reprodução: .lrc externo tem prioridade (fonte explícita do usuário).
        // Sem .lrc, usa tags embutidas. Sem cache.

        externalLrc(track)?.let { if (it.isNotEmpty()) return it }

        loadEmbedded(context, track).let { if (it.isNotEmpty()) return it }

        return emptyList()
    }

    /** Só tags embutidas (para o editor quando o usuário escolhe essa fonte). */
    fun loadEmbedded(context: Context, track: Track): List<LyricLine> {
        try {
            context.contentResolver.openInputStream(track.uri)?.use { stream ->
                readEmbedded(stream)?.let { if (it.isNotEmpty()) return it }
            }
        } catch (_: Exception) { }

        if (track.path.startsWith("/")) {
            try {
                val f = File(track.path)
                if (f.exists()) {
                    FileInputStream(f).use { stream ->
                        readEmbedded(stream)?.let { if (it.isNotEmpty()) return it }
                    }
                }
            } catch (_: Exception) { }
        }

        try {
            val r = MediaMetadataRetriever()
            r.setDataSource(context, track.uri)
            @Suppress("DEPRECATION")
            val raw = try {
                r.extractMetadata(1000)
            } catch (_: Exception) { null }
            r.release()
            if (!raw.isNullOrBlank()) {
                val parsed = parseLyricsContent(raw)
                if (parsed.isNotEmpty()) return parsed
            }
        } catch (_: Exception) { }

        return emptyList()
    }

    // ---------- leitura limitada do cabeçalho ----------

    private fun readEmbedded(raw: InputStream): List<LyricLine>? {
        val input = BufferedInputStream(raw, 64 * 1024)
        input.mark(16)
        val head = ByteArray(10)
        val n = readFully(input, head, 0, head.size)
        input.reset()
        if (n < 4) return null

        return when {
            n >= 10 && startsWith(head, n, "ID3") -> readId3(input)
            startsWith(head, n, "fLaC") -> readFlac(input)
            startsWith(head, n, "OggS") -> readOgg(input)
            else -> null
        }
    }

    private fun readId3(input: InputStream): List<LyricLine>? {
        val header = ByteArray(10)
        if (readFully(input, header, 0, 10) < 10) return null
        val tagSize = synchsafe(header, 6)
        if (tagSize <= 0) return null
        val toRead = tagSize.coerceAtMost(MAX_TAG_BYTES)
        val buf = ByteArray(10 + toRead)
        System.arraycopy(header, 0, buf, 0, 10)
        val got = readFully(input, buf, 10, toRead)
        val bytes = if (got < toRead) buf.copyOf(10 + got) else buf
        return parseId3(bytes)
    }

    /** Percorre os blocos de metadados do FLAC, pulando (sem ler) tudo menos o VORBIS_COMMENT. */
    private fun readFlac(input: InputStream): List<LyricLine>? {
        if (!skipFully(input, 4)) return null // "fLaC"
        val hdr = ByteArray(4)
        while (true) {
            if (readFully(input, hdr, 0, 4) < 4) return null
            val isLast = (hdr[0].toInt() and 0x80) != 0
            val type = hdr[0].toInt() and 0x7F
            val len = ((hdr[1].toInt() and 0xFF) shl 16) or
                ((hdr[2].toInt() and 0xFF) shl 8) or
                (hdr[3].toInt() and 0xFF)
            if (type == 4) { // VORBIS_COMMENT
                if (len <= 0 || len > MAX_TAG_BYTES) return null
                val block = ByteArray(len)
                if (readFully(input, block, 0, len) < len) return null
                return parseVorbisComments(block, 0, len)
            }
            // STREAMINFO, SEEKTABLE, PICTURE (capa, vários MB)... são pulados
            if (isLast || !skipFully(input, len.toLong())) return null
        }
    }

    /** OGG/Opus: os comentários ficam nas primeiras páginas; examina só o início. */
    private fun readOgg(input: InputStream): List<LyricLine>? {
        val buf = ByteArray(OGG_SCAN_BYTES)
        val n = readFully(input, buf, 0, buf.size)
        if (n <= 0) return null
        for (marker in listOf("OpusTags", "\u0003vorbis")) {
            val idx = indexOf(buf, n, marker)
            if (idx >= 0) {
                parseVorbisComments(buf, idx + marker.length, n)?.let { return it }
            }
        }
        return parseVorbisLoose(buf, n)
    }

    private fun startsWith(b: ByteArray, n: Int, tag: String): Boolean {
        if (n < tag.length) return false
        for (i in tag.indices) if (b[i] != tag[i].code.toByte()) return false
        return true
    }

    private fun indexOf(b: ByteArray, end: Int, s: String): Int {
        val pat = s.toByteArray(Charsets.ISO_8859_1)
        outer@ for (i in 0..(end - pat.size)) {
            for (j in pat.indices) if (b[i + j] != pat[j]) continue@outer
            return i
        }
        return -1
    }

    private fun readFully(input: InputStream, buf: ByteArray, off: Int, len: Int): Int {
        var total = 0
        while (total < len) {
            val r = input.read(buf, off + total, len - total)
            if (r < 0) break
            total += r
        }
        return total
    }

    private fun skipFully(input: InputStream, count: Long): Boolean {
        var left = count
        while (left > 0) {
            val s = input.skip(left)
            if (s > 0) {
                left -= s
            } else {
                if (input.read() < 0) return false // fim do arquivo
                left--
            }
        }
        return true
    }

    private fun toPlainLines(text: String): List<LyricLine> =
        text.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { LyricLine(-1, it) }

    /** Detecta TTML (Apple Music, etc.) pelo conteúdo, independente da extensão. */
    private fun looksLikeTtml(text: String): Boolean {
        val t = text.trimStart()
        if (t.startsWith("<?xml", ignoreCase = true) || t.startsWith("<tt", ignoreCase = true)) return true
        if (t.contains("<tt ", ignoreCase = true) || t.contains("<tt\n", ignoreCase = true)) return true
        // corpo típico: <p begin="00:00:…"> ou begin="12.5s"
        return Regex(
            """<\s*p\s+[^>]*begin\s*=\s*["'][^"']+["']""",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(t) && t.contains("</p>", ignoreCase = true)
    }

    /**
     * Tenta TTML → LRC/eLRC → texto plano.
     * Usado para tags embutidas e arquivos externos sem extensão confiável.
     */
    fun parseLyricsContent(text: String): List<LyricLine> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (looksLikeTtml(trimmed)) {
            val ttml = parseTtml(trimmed)
            if (ttml.isNotEmpty()) return ttml
        }
        val lrc = parseLrcText(trimmed)
        if (lrc.isNotEmpty()) return lrc
        return toPlainLines(trimmed)
    }

    private fun externalLrc(track: Track): List<LyricLine>? {
        val candidates = mutableListOf<String>()
        if (track.path.startsWith("/")) {
            val base = track.path.substringBeforeLast('.')
            // eLRC keeps word-level sync; a newly saved plain LRC takes precedence over TTML.
            candidates += "$base.elrc"
            candidates += "$base.ELRC"
            candidates += "$base.lrc"
            candidates += "$base.LRC"
            candidates += "$base.ttml"
            candidates += "$base.TTML"
        }
        for (p in candidates) {
            val f = File(p)
            if (f.exists()) {
                return try {
                    val raw = f.readText()
                    when {
                        p.endsWith(".ttml", true) -> parseTtml(raw).ifEmpty { parseLyricsContent(raw) }
                        else -> parseLyricsContent(raw)
                    }
                } catch (_: Exception) { null }
            }
        }
        return null
    }

    fun parseLrcText(text: String): List<LyricLine> {
        // Line timestamp: [mm:ss.xx] or [mm:ss.xxx]
        val lineRegex = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?](.*)""")
        // Word/syllable timestamp inside line (eLRC): <mm:ss.xx>
        val wordRegex = Regex("""<(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?>""")
        val lines = mutableListOf<LyricLine>()
        val rawLines = text.lines()
        for (raw in rawLines) {
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("[ti:") || trimmed.startsWith("[ar:") ||
                trimmed.startsWith("[al:") || trimmed.startsWith("[by:") || trimmed.startsWith("[offset:")) continue
            val m = lineRegex.find(trimmed)
            val time = m?.let { parseTimestamp(it.groupValues[1], it.groupValues[2], it.groupValues[3]) } ?: -1L
            var lyricPart = m?.groupValues?.get(4) ?: trimmed
            // Marcadores de tipo de linha vêm antes da primeira palavra, logo após o timestamp.
            var isTranslation = false
            var isBackground = false
            val lineTypeMarker = Regex("^\\s*\\{(tr|bg)\\}", RegexOption.IGNORE_CASE)
            while (true) {
                val marker = lineTypeMarker.find(lyricPart) ?: break
                when (marker.groupValues[1].lowercase()) {
                    "tr" -> isTranslation = true
                    "bg" -> isBackground = true
                }
                lyricPart = lyricPart.substring(marker.range.last + 1)
            }
            val words = mutableListOf<LyricWord>()
            if (wordRegex.containsMatchIn(lyricPart)) {
                val parts = wordRegex.split(lyricPart)
                val tags = wordRegex.findAll(lyricPart).toList()
                // parts[0] = text before first tag; parts[i+1] = text after tags[i]
                if (parts.isNotEmpty() && parts[0].isNotEmpty()) {
                    appendWordSegment(words, parts[0], time)
                }
                for (i in tags.indices) {
                    val wTime = parseTimestamp(tags[i].groupValues[1], tags[i].groupValues[2], tags[i].groupValues[3])
                    val rawText = if (i + 1 < parts.size) parts[i + 1] else ""
                    appendWordSegment(words, rawText, wTime)
                }
                // endMs e fim de linha são preenchidos em finalizeLyricsTimings
            } else if (wordMarkerRegex.containsMatchIn(lyricPart)) {
                // Mantém animações/sílabas em linhas simples, sem timestamps de palavra.
                appendWordSegment(words, lyricPart, time)
            }
            val plainText = lyricPart.replace(wordRegex, "").replace(wordMarkerRegex, "").trim()
            if (plainText.isNotEmpty() || words.isNotEmpty()) {
                lines.add(
                    LyricLine(
                        timeMs = time,
                        text = if (plainText.isNotEmpty()) plainText else words.joinToString("") { it.text },
                        words = words,
                        isTranslation = isTranslation,
                        isBackground = isBackground
                    )
                )
            }
        }
        val ordered = if (lines.all { it.timeMs >= 0 }) lines.sortedBy { it.timeMs } else lines
        return finalizeLyricsTimings(ordered)
    }

    /**
     * Preenche endMs de cada palavra (início da próxima com tempo) e o fim de cada linha.
     * Usado no parse de LRC/TTML e ao salvar letras sincronizadas manualmente, para o
     * karaokê ter a mesma suavidade em ambos os casos.
     */
    fun finalizeLyricsTimings(lines: List<LyricLine>): List<LyricLine> {
        if (lines.isEmpty()) return lines
        val withWordEnds = lines.map { line ->
            if (line.words.isEmpty()) return@map line
            val words = line.words.toMutableList()
            for (i in words.indices) {
                val current = words[i]
                if (current.timeMs < 0L) continue
                // Preserva endMs definido manualmente (ex.: fim da última palavra no editor).
                if (current.endMs > current.timeMs) continue
                val nextTimed = words.drop(i + 1).firstOrNull { it.timeMs > current.timeMs }
                val end = nextTimed?.timeMs ?: -1L
                if (end > current.timeMs) {
                    words[i] = current.copy(endMs = end)
                }
            }
            line.copy(words = words)
        }
        return inferLineEnds(withWordEnds)
    }

    /**
     * Prepara fins de exibição para fontes sem duração explícita. BG expira 1,5 s
     * após a última palavra sincronizada (ou o começo da linha como fallback),
     * mas um novo BG encerra o anterior imediatamente.
     */
    private fun inferLineEnds(lines: List<LyricLine>): List<LyricLine> = lines.mapIndexed { index, line ->
        val nextLineTime = lines.drop(index + 1).firstOrNull { it.timeMs > line.timeMs }?.timeMs
        val nextBackgroundTime = if (line.isBackground) {
            lines.drop(index + 1).firstOrNull { it.isBackground && it.timeMs > line.timeMs }?.timeMs
        } else null
        if (line.isBackground) {
            val lastTimedWord = line.words.mapNotNull { it.timeMs.takeIf { time -> time >= 0 } }.maxOrNull()
            val fallbackEnd = ((lastTimedWord ?: line.timeMs).coerceAtLeast(0L)) + 1_500L
            val inferredEnd = nextBackgroundTime?.let { minOf(fallbackEnd, it) } ?: fallbackEnd
            line.copy(endMs = inferredEnd)
        } else if (line.endMs <= line.timeMs && nextLineTime != null) {
            line.copy(endMs = nextLineTime)
        } else line
    }

    private val wordMarkerRegex = Regex("\\{(sil|glo|pop|des|ded|eco|zou|zin|sli|typ|fla|vib|rnb|frz|shd|wav)\\}", RegexOption.IGNORE_CASE)

    /** Converte um trecho e seus marcadores em palavras, sem criar timestamps artificiais. */
    private fun appendWordSegment(words: MutableList<LyricWord>, raw: String, timeMs: Long) {
        val markers = wordMarkerRegex.findAll(raw).toList()
        var cursor = 0
        var firstWord = true
        var nextIsSyllable = false
        fun appendChunk(chunk: String) {
            val trimmed = chunk.trim()
            val pieces = if (markers.isEmpty()) listOf(trimmed).filter { it.isNotEmpty() }
            else trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
            pieces.forEach { piece ->
                words.add(
                    LyricWord(
                        timeMs = if (firstWord) timeMs else -1L,
                        text = piece,
                        isSyllable = nextIsSyllable
                    )
                )
                firstWord = false
                nextIsSyllable = false
            }
        }
        markers.forEach { match ->
            appendChunk(raw.substring(cursor, match.range.first))
            when (match.groupValues[1].lowercase()) {
                "sil" -> nextIsSyllable = true
                "glo" -> if (words.isNotEmpty()) words[words.lastIndex] = words.last().copy(animation = LyricAnimation.GLO)
                "pop" -> if (words.isNotEmpty()) words[words.lastIndex] = words.last().copy(animation = LyricAnimation.POP)
                "des" -> if (words.isNotEmpty()) words[words.lastIndex] = words.last().copy(animation = LyricAnimation.DES)
                "ded" -> if (words.isNotEmpty()) words[words.lastIndex] = words.last().copy(animation = LyricAnimation.DED)
                "eco" -> if (words.isNotEmpty()) words[words.lastIndex] = words.last().copy(animation = LyricAnimation.ECO)
                "zou" -> if (words.isNotEmpty()) words[words.lastIndex] = words.last().copy(animation = LyricAnimation.ZOU)
                "zin" -> if (words.isNotEmpty()) words[words.lastIndex] = words.last().copy(animation = LyricAnimation.ZIN)
                "sli" -> if (words.isNotEmpty()) words[words.lastIndex] = words.last().copy(animation = LyricAnimation.SLI)
                "typ" -> if (words.isNotEmpty()) words[words.lastIndex] = words.last().copy(animation = LyricAnimation.TYP)
                "fla" -> if (words.isNotEmpty()) words[words.lastIndex] = words.last().copy(animation = LyricAnimation.FLA)
                "vib" -> if (words.isNotEmpty()) words[words.lastIndex] = words.last().copy(animation = LyricAnimation.VIB)
                "rnb" -> if (words.isNotEmpty()) words[words.lastIndex] = words.last().copy(animation = LyricAnimation.RNB)
                "frz" -> if (words.isNotEmpty()) words[words.lastIndex] = words.last().copy(animation = LyricAnimation.FRZ)
                "shd" -> if (words.isNotEmpty()) words[words.lastIndex] = words.last().copy(animation = LyricAnimation.SHD)
                "wav" -> if (words.isNotEmpty()) words[words.lastIndex] = words.last().copy(animation = LyricAnimation.WAV)
            }
            cursor = match.range.last + 1
        }
        appendChunk(raw.substring(cursor))
    }

    private fun parseTimestamp(minStr: String, secStr: String, frac: String): Long {
        val min = minStr.toLongOrNull() ?: 0L
        val sec = secStr.toLongOrNull() ?: 0L
        val ms = when {
            frac.isEmpty() -> 0L
            frac.length == 1 -> frac.toLong() * 100
            frac.length == 2 -> frac.toLong() * 10
            else -> frac.take(3).toLong()
        }
        return min * 60_000 + sec * 1000 + ms
    }

    /**
     * Parser de TTML (Timed Text Markup Language), incluindo o formato Apple Music:
     * - &lt;p begin end ttm:agent&gt; com &lt;span begin end&gt; por palavra
     * - multi-vocais via ttm:agent / itunes:agent (v1, v2, bg…)
     * - backing vocals marcados como isBackground
     */
    fun parseTtml(text: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        // Captura a tag <p ...> completa e o corpo até </p>
        val pRegex = Regex(
            """<\s*p\s+([^>]*)>(.*?)</\s*p\s*>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val spanRegex = Regex(
            """<\s*span\s+([^>]*)>(.*?)</\s*span\s*>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val attrBegin = Regex("""begin\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val attrEnd = Regex("""end\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val attrAgent = Regex(
            """(?:ttm:agent|itunes:agent|agent)\s*=\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        val attrRole = Regex(
            """(?:ttm:role|role|itunes:key)\s*=\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )

        // Agente principal = o mais frequente (geralmente v1); demais podem ser backing
        val agentCounts = mutableMapOf<String, Int>()
        for (m in pRegex.findAll(text)) {
            val attrs = m.groupValues[1]
            attrAgent.find(attrs)?.groupValues?.get(1)?.trim()?.lowercase()?.let {
                agentCounts[it] = (agentCounts[it] ?: 0) + 1
            }
        }
        val primaryAgent = agentCounts.maxByOrNull { it.value }?.key

        for (m in pRegex.findAll(text)) {
            val attrs = m.groupValues[1]
            val body = m.groupValues[2]
            val beginRaw = attrBegin.find(attrs)?.groupValues?.get(1) ?: continue
            val begin = parseTtmlTime(beginRaw)
            val endRaw = attrEnd.find(attrs)?.groupValues?.get(1)
            val endMs = endRaw?.let { parseTtmlTime(it) } ?: -1L
            val agent = attrAgent.find(attrs)?.groupValues?.get(1)?.trim()
            val role = attrRole.find(attrs)?.groupValues?.get(1)?.trim()?.lowercase()
            val agentLower = agent?.lowercase()
            val isBg = when {
                role != null && (role.contains("bg") || role.contains("back") || role.contains("harmon") || role == "x-bg") -> true
                agentLower != null && (
                    agentLower.contains("bg") ||
                        agentLower.contains("back") ||
                        agentLower.contains("harmon") ||
                        agentLower.contains("chorus") ||
                        agentLower.contains("choir") ||
                        (primaryAgent != null && agentLower != primaryAgent && agentCounts.size > 1)
                    ) -> true
                else -> false
            }

            val words = mutableListOf<LyricWord>()
            for (sm in spanRegex.findAll(body)) {
                val sAttrs = sm.groupValues[1]
                val wBeginRaw = attrBegin.find(sAttrs)?.groupValues?.get(1) ?: continue
                val wBegin = parseTtmlTime(wBeginRaw)
                val wEndRaw = attrEnd.find(sAttrs)?.groupValues?.get(1)
                val wEnd = wEndRaw?.let { parseTtmlTime(it) } ?: -1L
                val wText = decodeXmlEntities(
                    sm.groupValues[2].replace(Regex("<[^>]+>"), "")
                ).trim()
                // Preserva espaços entre palavras quando o span não traz espaço
                if (wText.isNotEmpty()) {
                    val parsedSpan = mutableListOf<LyricWord>()
                    appendWordSegment(parsedSpan, wText, wBegin)
                    parsedSpan.forEach { parsedWord ->
                        words.add(parsedWord.copy(endMs = wEnd))
                    }
                }
            }
            val rawPlain = decodeXmlEntities(body.replace(Regex("<[^>]+>"), "")).trim()
                .replace(Regex("\\s+"), " ")
            val isTranslation = rawPlain.trimStart().startsWith("{tr}", ignoreCase = true)
            if (words.isEmpty() && wordMarkerRegex.containsMatchIn(rawPlain)) {
                val content = if (isTranslation) rawPlain.trimStart().drop("{tr}".length).trimStart() else rawPlain
                appendWordSegment(words, content, begin)
            }
            // Se spans existem mas o join ficou colado, tenta reinserir espaço entre palavras
            // que terminam em letra e a seguinte começa em letra maiúscula/minúscula sem pontuação.
            val joinedWords = if (words.isNotEmpty()) {
                buildString {
                    words.forEachIndexed { i, w ->
                        if (i > 0) {
                            val prev = words[i - 1].text
                            val cur = w.text
                            val needSpace = prev.isNotEmpty() && cur.isNotEmpty() &&
                                !prev.last().isWhitespace() && !cur.first().isWhitespace() &&
                                !",.;:!?)]}".contains(cur.first()) &&
                                !"([".contains(prev.last())
                            if (needSpace && !prev.endsWith("-") && !prev.endsWith("'")) append(' ')
                        }
                        append(w.text)
                    }
                }
            } else ""

            val plain = rawPlain
                .let { if (isTranslation) it.trimStart().drop("{tr}".length).trimStart() else it }
                .replace(wordMarkerRegex, "")
                .trim()
            val display = when {
                plain.isNotEmpty() -> plain
                joinedWords.isNotEmpty() -> joinedWords
                else -> ""
            }
            if (display.isNotEmpty() || words.isNotEmpty()) {
                lines.add(
                    LyricLine(
                        timeMs = begin,
                        text = display.ifEmpty { joinedWords },
                        words = words,
                        endMs = endMs,
                        agent = agent,
                        isBackground = isBg,
                        isTranslation = isTranslation
                    )
                )
            }
        }
        val ordered = lines.sortedWith(compareBy({ it.timeMs }, { if (it.isBackground) 1 else 0 }))
        return ordered.mapIndexed { index, line ->
            val nextLineTime = ordered.drop(index + 1).firstOrNull { it.timeMs > line.timeMs }?.timeMs
            val nextBackgroundTime = if (line.isBackground) {
                ordered.drop(index + 1).firstOrNull { it.isBackground }?.timeMs
            } else null
            val lastTimedWord = line.words.mapNotNull { it.timeMs.takeIf { time -> time >= 0 } }.maxOrNull()
            val bgFallback = if (line.isBackground) {
                ((lastTimedWord ?: line.timeMs).coerceAtLeast(0L)) + 1_500L
            } else null
            val inferredEnd = when {
                line.isBackground -> {
                    val desiredEnd = bgFallback ?: line.timeMs + 1_500L
                    nextBackgroundTime?.let { minOf(desiredEnd, it) } ?: desiredEnd
                }
                line.endMs > line.timeMs -> line.endMs
                nextLineTime != null -> nextLineTime
                else -> -1L
            }
            line.copy(endMs = inferredEnd)
        }
    }

    private fun decodeXmlEntities(s: String): String =
        s.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace(Regex("""&#(\d+);""")) { mr ->
                mr.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: mr.value
            }
            .replace(Regex("""&#x([0-9a-fA-F]+);""")) { mr ->
                mr.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: mr.value
            }

    private fun parseTtmlTime(raw: String): Long {
        val s = raw.trim()
        // 12.5s or 12.5
        if (s.endsWith("s", ignoreCase = true) || s.matches(Regex("""\d+(\.\d+)?"""))) {
            val num = s.removeSuffix("s").removeSuffix("S").toDoubleOrNull() ?: return 0L
            return (num * 1000).toLong()
        }
        // HH:MM:SS.mmm or MM:SS.mmm
        val parts = s.split(":")
        return try {
            when (parts.size) {
                3 -> {
                    val h = parts[0].toLong()
                    val m = parts[1].toLong()
                    val sec = parts[2].toDouble()
                    ((h * 3600 + m * 60) * 1000 + sec * 1000).toLong()
                }
                2 -> {
                    val m = parts[0].toLong()
                    val sec = parts[1].toDouble()
                    ((m * 60) * 1000 + sec * 1000).toLong()
                }
                else -> 0L
            }
        } catch (_: Exception) { 0L }
    }

    // ---------- ID3v2 USLT / SYLT ----------

    private fun parseId3(bytes: ByteArray): List<LyricLine>? {
        if (bytes.size < 10) return null
        if (bytes[0] != 'I'.code.toByte() || bytes[1] != 'D'.code.toByte() || bytes[2] != '3'.code.toByte()) return null
        val ver = bytes[3].toInt() and 0xFF
        if (ver < 3) return null
        val tagSize = synchsafe(bytes, 6)
        val end = (10 + tagSize).coerceAtMost(bytes.size)
        var pos = 10
        // skip extended header if present
        val flags = bytes[5].toInt() and 0xFF
        if (flags and 0x40 != 0 && pos + 4 <= end) {
            val extSize = if (ver >= 4) synchsafe(bytes, pos) else int32(bytes, pos)
            pos += 4 + extSize
        }
        var unsyncedText: String? = null
        val synced = mutableListOf<LyricLine>()
        while (pos + 10 <= end) {
            val id = String(bytes, pos, 4, Charsets.ISO_8859_1)
            if (id[0] == '\u0000') break
            val frameSize = if (ver >= 4) synchsafe(bytes, pos + 4) else int32(bytes, pos + 4)
            if (frameSize <= 0 || pos + 10 + frameSize > end) break
            val frameStart = pos + 10
            when (id) {
                "USLT" -> {
                    decodeUslt(bytes, frameStart, frameSize)?.let { unsyncedText = it }
                }
                "SYLT" -> {
                    decodeSylt(bytes, frameStart, frameSize)?.let { synced += it }
                }
            }
            pos = frameStart + frameSize
        }
        if (synced.isNotEmpty()) return synced.sortedBy { it.timeMs }
        unsyncedText?.let { text ->
            return parseLyricsContent(text)
        }
        return null
    }

    private fun decodeUslt(bytes: ByteArray, start: Int, size: Int): String? {
        if (size < 5) return null
        val encoding = bytes[start].toInt() and 0xFF
        // language 3 bytes
        var i = start + 4
        val end = start + size
        // content descriptor until null
        i = skipNullTerminated(bytes, i, end, encoding)
        if (i >= end) return null
        return decodeEncoded(bytes, i, end - i, encoding)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun decodeSylt(bytes: ByteArray, start: Int, size: Int): List<LyricLine>? {
        if (size < 6) return null
        val encoding = bytes[start].toInt() and 0xFF
        // lang 3 + timestamp format 1 + content type 1
        var i = start + 6
        val end = start + size
        i = skipNullTerminated(bytes, i, end, encoding) // content descriptor
        val lines = mutableListOf<LyricLine>()
        while (i < end) {
            val textStart = i
            i = skipNullTerminated(bytes, i, end, encoding)
            val text = decodeEncoded(bytes, textStart, i - textStart - nullWidth(encoding), encoding)?.trim().orEmpty()
            if (i + 4 > end) break
            val stamp = ((bytes[i].toInt() and 0xFF) shl 24) or
                ((bytes[i + 1].toInt() and 0xFF) shl 16) or
                ((bytes[i + 2].toInt() and 0xFF) shl 8) or
                (bytes[i + 3].toInt() and 0xFF)
            i += 4
            // timestamp format: 1 = MPEG frames, 2 = ms — assumimos ms quando razoável
            val timeMs = stamp.toLong()
            if (text.isNotEmpty()) lines.add(LyricLine(timeMs, text))
        }
        return lines.ifEmpty { null }
    }

    private fun nullWidth(encoding: Int) = if (encoding == 1 || encoding == 2) 2 else 1

    private fun skipNullTerminated(bytes: ByteArray, start: Int, end: Int, encoding: Int): Int {
        var i = start
        if (encoding == 1 || encoding == 2) {
            while (i + 1 < end && !(bytes[i] == 0.toByte() && bytes[i + 1] == 0.toByte())) i += 2
            return (i + 2).coerceAtMost(end)
        } else {
            while (i < end && bytes[i] != 0.toByte()) i++
            return (i + 1).coerceAtMost(end)
        }
    }

    private fun decodeEncoded(bytes: ByteArray, start: Int, length: Int, encoding: Int): String? {
        if (length <= 0 || start + length > bytes.size) return null
        val cs: Charset = when (encoding) {
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> Charsets.ISO_8859_1
        }
        return try {
            String(bytes, start, length, cs).trim('\u0000').trim()
        } catch (_: Exception) {
            null
        }
    }

    private fun synchsafe(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0x7F) shl 21) or
            ((b[off + 1].toInt() and 0x7F) shl 14) or
            ((b[off + 2].toInt() and 0x7F) shl 7) or
            (b[off + 3].toInt() and 0x7F)

    private fun int32(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 24) or
            ((b[off + 1].toInt() and 0xFF) shl 16) or
            ((b[off + 2].toInt() and 0xFF) shl 8) or
            (b[off + 3].toInt() and 0xFF)

    // ---------- Vorbis comments (FLAC / OGG / Opus) ----------

    /**
     * Lê comentários Vorbis (formato com tamanhos little-endian) entre [start] e [end].
     * Aceita LYRICS, UNSYNCEDLYRICS e SYNCEDLYRICS; prefere a versão com tempo (LRC).
     */
    private fun parseVorbisComments(b: ByteArray, start: Int, end: Int): List<LyricLine>? {
        var p = start
        fun le32(): Int {
            if (p + 4 > end) return -1
            val v = (b[p].toInt() and 0xFF) or
                ((b[p + 1].toInt() and 0xFF) shl 8) or
                ((b[p + 2].toInt() and 0xFF) shl 16) or
                ((b[p + 3].toInt() and 0xFF) shl 24)
            p += 4
            return v
        }

        val vendorLen = le32()
        if (vendorLen < 0 || p.toLong() + vendorLen > end) return null
        p += vendorLen
        val count = le32()
        if (count < 0) return null

        var plain: String? = null
        var i = 0
        while (i < count && i < 10_000) {
            val len = le32()
            if (len < 0 || p.toLong() + len > end) break
            val entry = String(b, p, len, Charsets.UTF_8)
            p += len
            i++
            val eq = entry.indexOf('=')
            if (eq <= 0) continue
            val key = entry.substring(0, eq).uppercase()
            if (key == "LYRICS" || key == "UNSYNCEDLYRICS" || key == "SYNCEDLYRICS") {
                val body = entry.substring(eq + 1).trim()
                if (body.isEmpty()) continue
                val parsed = parseLyricsContent(body)
                if (parsed.isNotEmpty() && parsed.any { it.timeMs >= 0 }) return parsed
                if (plain == null) plain = body
            }
        }
        return plain?.let { parseLyricsContent(it) }
    }

    /** Plano B para OGG cujos comentários cruzam páginas: busca textual só no trecho já lido. */
    private fun parseVorbisLoose(bytes: ByteArray, n: Int): List<LyricLine>? {
        val text = String(bytes, 0, n, Charsets.ISO_8859_1)
        val patterns = listOf(
            Regex("""(?i)LYRICS=([^\u0000]+)"""),
            Regex("""(?i)UNSYNCEDLYRICS=([^\u0000]+)"""),
            Regex("""(?i)SYNCEDLYRICS=([^\u0000]+)""")
        )
        for (p in patterns) {
            val m = p.find(text) ?: continue
            val body = m.groupValues[1].trim()
            if (body.isEmpty()) continue
            return parseLyricsContent(body)
        }
        return null
    }
}
