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
 *  - arquivo .lrc ao lado da faixa
 *  - tags embutidas (ID3v2 USLT/SYLT em MP3, comentários Vorbis em FLAC/OGG/Opus)
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
        // 1) .lrc externo
        externalLrc(track)?.let { if (it.isNotEmpty()) return it }

        // 1b) cache do app (quando embutimos sem permissão de escrita na pasta da música)
        LyricsEmbedder.loadFromCache(context, track)?.let { if (it.isNotEmpty()) return it }

        // 2) tags embutidas, lendo só o cabeçalho via content resolver
        try {
            context.contentResolver.openInputStream(track.uri)?.use { stream ->
                readEmbedded(stream)?.let { if (it.isNotEmpty()) return it }
            }
        } catch (_: Exception) { }

        // 3) caminho absoluto legado
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

        // 4) tentativa via MediaMetadataRetriever (alguns builds expõem)
        try {
            val r = MediaMetadataRetriever()
            r.setDataSource(context, track.uri)
            // Chaves não oficiais / OEM às vezes usam "lyrics" no extrator
            @Suppress("DEPRECATION")
            val raw = try {
                r.extractMetadata(1000) // alguns firmwares
            } catch (_: Exception) { null }
            r.release()
            if (!raw.isNullOrBlank()) {
                val lrc = parseLrcText(raw)
                if (lrc.isNotEmpty()) return lrc
                return listOf(LyricLine(-1, raw.trim()))
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

    private fun externalLrc(track: Track): List<LyricLine>? {
        val candidates = mutableListOf<String>()
        if (track.path.startsWith("/")) {
            val base = track.path.substringBeforeLast('.')
            // eLRC / TTML first (syllable sync), then plain LRC
            candidates += "$base.elrc"
            candidates += "$base.ELRC"
            candidates += "$base.ttml"
            candidates += "$base.TTML"
            candidates += "$base.lrc"
            candidates += "$base.LRC"
        }
        for (p in candidates) {
            val f = File(p)
            if (f.exists()) {
                return try {
                    val raw = f.readText()
                    when {
                        p.endsWith(".ttml", true) -> parseTtml(raw)
                        else -> parseLrcText(raw) // also handles eLRC word tags
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
        for (raw in text.lines()) {
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("[ti:") || trimmed.startsWith("[ar:") ||
                trimmed.startsWith("[al:") || trimmed.startsWith("[by:") || trimmed.startsWith("[offset:")) continue
            val m = lineRegex.find(trimmed) ?: continue
            val time = parseTimestamp(m.groupValues[1], m.groupValues[2], m.groupValues[3])
            val lyricPart = m.groupValues[4]
            val words = mutableListOf<LyricWord>()
            if (wordRegex.containsMatchIn(lyricPart)) {
                val parts = wordRegex.split(lyricPart)
                val tags = wordRegex.findAll(lyricPart).toList()
                // parts[0] = text before first tag; parts[i+1] = text after tags[i]
                if (parts.isNotEmpty() && parts[0].isNotEmpty()) {
                    words.add(LyricWord(time, parts[0]))
                }
                for (i in tags.indices) {
                    val wTime = parseTimestamp(tags[i].groupValues[1], tags[i].groupValues[2], tags[i].groupValues[3])
                    val wText = if (i + 1 < parts.size) parts[i + 1] else ""
                    if (wText.isNotEmpty()) words.add(LyricWord(wTime, wText))
                }
            }
            val plainText = lyricPart.replace(wordRegex, "").trim()
            if (plainText.isNotEmpty() || words.isNotEmpty()) {
                lines.add(
                    LyricLine(
                        time,
                        if (plainText.isNotEmpty()) plainText else words.joinToString("") { it.text },
                        words
                    )
                )
            }
        }
        return lines.sortedBy { it.timeMs }
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
     * Parser simplificado de TTML (Timed Text Markup Language) usado por alguns players
     * para letras com sincronização silábica / por palavra.
     */
    fun parseTtml(text: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        // <p begin="10.5s" end="12.0s"> or begin="00:00:10.500"
        val pRegex = Regex(
            """<p\s+[^>]*begin\s*=\s*["']([^"']+)["'][^>]*>(.*?)</p>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val spanRegex = Regex(
            """<span\s+[^>]*begin\s*=\s*["']([^"']+)["'][^>]*>(.*?)</span>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        for (m in pRegex.findAll(text)) {
            val begin = parseTtmlTime(m.groupValues[1])
            val body = m.groupValues[2]
            val words = mutableListOf<LyricWord>()
            for (sm in spanRegex.findAll(body)) {
                val wBegin = parseTtmlTime(sm.groupValues[1])
                val wText = sm.groupValues[2]
                    .replace(Regex("<[^>]+>"), "")
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&quot;", "\"")
                    .trim()
                if (wText.isNotEmpty()) words.add(LyricWord(wBegin, wText))
            }
            val plain = body
                .replace(Regex("<[^>]+>"), "")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .trim()
            if (plain.isNotEmpty() || words.isNotEmpty()) {
                lines.add(LyricLine(begin, if (plain.isNotEmpty()) plain else words.joinToString(" ") { it.text }, words))
            }
        }
        return lines.sortedBy { it.timeMs }
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
            val asLrc = parseLrcText(text)
            if (asLrc.isNotEmpty()) return asLrc
            // quebra em linhas sem tempo
            return text.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { LyricLine(-1, it) }
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
                val lrc = parseLrcText(body)
                if (lrc.isNotEmpty()) return lrc
                if (plain == null) plain = body
            }
        }
        return plain?.let { toPlainLines(it) }
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
            val lrc = parseLrcText(body)
            if (lrc.isNotEmpty()) return lrc
            return toPlainLines(body)
        }
        return null
    }
}
