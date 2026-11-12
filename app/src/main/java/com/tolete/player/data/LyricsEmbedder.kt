package com.tolete.player.data

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File
import java.util.Locale

/**
 * Gravação de letras:
 * - Arquivo sidecar .lrc ao lado do áudio (requer escrita na pasta / “Acesso a todos os arquivos”)
 * - Tags embutidas (FieldKey.LYRICS) via JAudioTagger
 *
 * Sem cache do app — a letra só vive no .lrc ou nas tags do arquivo.
 */
object LyricsEmbedder {

    enum class Target { LRC_FILE, EMBEDDED }

    /**
     * Apaga o cache legado de letras (pasta cache/lyrics), se ainda existir.
     * Mantido para o MainActivity não quebrar em builds que chamam isto no onCreate.
     * O app não grava mais letras em cache.
     */
    fun clearLegacyCache(context: Context) {
        try {
            val dir = File(context.cacheDir, "lyrics")
            if (dir.exists()) {
                dir.listFiles()?.forEach { it.delete() }
                dir.delete()
            }
        } catch (_: Exception) { }
    }

    /** Caminho do .lrc ao lado do arquivo de áudio, se resolvível. */
    fun externalLrcPath(track: Track): File? {
        if (track.path.startsWith("/")) {
            val base = track.path.substringBeforeLast('.')
            return File("$base.lrc")
        }
        if (track.uri.scheme == "file") {
            val path = track.uri.path
            if (!path.isNullOrBlank()) {
                val base = path.substringBeforeLast('.')
                return File("$base.lrc")
            }
        }
        return null
    }

    fun hasExternalLrc(track: Track): Boolean {
        val f = externalLrcPath(track) ?: return false
        return try {
            f.exists() && f.length() > 0
        } catch (_: Exception) {
            false
        }
    }

    /** Caminho do sidecar TTML, para conversão explícita no editor (nunca grava ao ler). */
    fun externalTtmlPath(track: Track): File? {
        val base = when {
            track.path.startsWith("/") -> track.path.substringBeforeLast('.')
            track.uri.scheme == "file" && !track.uri.path.isNullOrBlank() -> track.uri.path!!.substringBeforeLast('.')
            else -> return null
        }
        val lower = File("$base.ttml")
        return if (lower.exists()) lower else File("$base.TTML")
    }

    fun hasExternalTtml(track: Track): Boolean {
        val f = externalTtmlPath(track) ?: return false
        return try { f.exists() && f.length() > 0 } catch (_: Exception) { false }
    }

    /** Lê e interpreta apenas o sidecar TTML; não cria nem altera qualquer arquivo. */
    fun loadExternalTtml(track: Track): List<LyricLine> {
        val f = externalTtmlPath(track) ?: return emptyList()
        return try {
            if (!f.isFile || f.length() <= 0L) emptyList()
            else LyricsExtractor.parseTtml(f.readText(Charsets.UTF_8))
        } catch (_: Exception) { emptyList() }
    }

    private fun hasAllFilesAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager() else true

    fun canWriteExternalLrc(track: Track): Boolean {
        val f = externalLrcPath(track) ?: return false
        return try {
            val parent = f.parentFile ?: return false
            if (f.canWrite()) return true
            if (parent.canWrite()) return true
            hasAllFilesAccess() && parent.exists()
        } catch (_: Exception) {
            false
        }
    }

    fun loadExternalLrc(track: Track): List<LyricLine> {
        val f = externalLrcPath(track) ?: return emptyList()
        if (!f.exists()) return emptyList()
        return try {
            LyricsExtractor.parseLyricsContent(f.readText(Charsets.UTF_8))
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fmtTs(ms: Long): String {
        val t = ms.coerceAtLeast(0L)
        return String.format(Locale.US, "%02d:%02d.%02d", t / 60_000, (t % 60_000) / 1000, (t % 1000) / 10)
    }

    private fun animationTag(animation: LyricAnimation): String = when (animation) {
        LyricAnimation.GLO -> "{glo}"
        LyricAnimation.POP -> "{pop}"
        LyricAnimation.DES -> "{des}"
        LyricAnimation.DED -> "{ded}"
        LyricAnimation.ECO -> "{eco}"
        LyricAnimation.ZOU -> "{zou}"
        LyricAnimation.ZIN -> "{zin}"
        LyricAnimation.SLI -> "{sli}"
        LyricAnimation.TYP -> "{typ}"
        LyricAnimation.FLA -> "{fla}"
        LyricAnimation.VIB -> "{vib}"
        LyricAnimation.RNB -> "{rnb}"
        LyricAnimation.FRZ -> "{frz}"
        LyricAnimation.SHD -> "{shd}"
        LyricAnimation.WAV -> "{wav}"
        LyricAnimation.NONE -> ""
    }

    /**
     * Serializa linhas em LRC ou eLRC (word-by-word). Formato único, também usado pelo editor de tags:
     *  - backing vocal leva {bg} e tradução leva {tr} logo após o timestamp;
     *  - só palavras COM tempo recebem <mm:ss.xx> (nunca grava <00:00.00> para palavra sem tempo);
     *  - sílaba: <mm:ss.xx>{sil}texto (a ordem que o parser espera);
     *  - linha sem nenhuma palavra com tempo vira LRC simples.
     */
    fun linesToLrcText(lines: List<LyricLine>): String = buildString {
        lines.forEach { line ->
            val lineMarkers = (if (line.isBackground) "{bg}" else "") +
                (if (line.isTranslation) "{tr}" else "")
            val hasTimedWords = line.words.any { it.timeMs >= 0 }
            val hasWordMarkers = line.words.any {
                it.isSyllable || it.animation != LyricAnimation.NONE
            }
            val textWithMarkers = if (hasWordMarkers) {
                buildString {
                    line.words.forEachIndexed { index, word ->
                        if (index > 0 && !word.isSyllable) append(' ')
                        if (word.isSyllable) append("{sil}")
                        append(word.text)
                        append(animationTag(word.animation))
                    }
                }
            } else line.text
            when {
                hasTimedWords -> {
                    val start = if (line.timeMs >= 0) line.timeMs
                    else line.words.first { it.timeMs >= 0 }.timeMs
                    append('[').append(fmtTs(start)).append(']').append(lineMarkers)
                    line.words.forEachIndexed { i, w ->
                        if (w.timeMs >= 0) {
                            // Espaço entre palavras (formato eLRC padrão: "<t>You <t>said").
                            // Sem ele, ao reler o texto da linha virava "Yousaid".
                            if (i > 0 && !w.isSyllable) append(' ')
                            append('<').append(fmtTs(w.timeMs)).append('>')
                            if (w.isSyllable) append("{sil}")
                            append(w.text)
                            append(animationTag(w.animation))
                        } else {
                            // Palavra ainda sem tempo: cola no texto anterior, sem tag.
                            if (i > 0 && !w.isSyllable) append(' ')
                            if (w.isSyllable) append("{sil}")
                            append(w.text)
                            append(animationTag(w.animation))
                        }
                    }
                    append('\n')
                }
                line.timeMs < 0 -> appendLine(lineMarkers + textWithMarkers)
                else -> appendLine("[${fmtTs(line.timeMs)}]$lineMarkers$textWithMarkers")
            }
        }
    }.trimEnd() + "\n"

    /**
     * Grava .lrc ao lado do áudio. Usa “Acesso a todos os arquivos” quando disponível.
     * Não usa cache — se não der para gravar, retorna false.
     */
    fun embedLrc(context: Context, track: Track, lrcText: String): Boolean {
        if (lrcText.isBlank()) return false
        val candidates = mutableListOf<File>()
        if (track.path.startsWith("/")) {
            candidates += File(track.path.substringBeforeLast('.') + ".lrc")
        }
        if (track.uri.scheme == "file") {
            track.uri.path?.takeIf { it.startsWith("/") }?.let {
                candidates += File(it.substringBeforeLast('.') + ".lrc")
            }
        }
        // Tenta resolver path via MediaStore quando só temos content URI
        if (candidates.isEmpty() && track.uri.scheme == "content") {
            try {
                context.contentResolver.query(
                    track.uri,
                    arrayOf(android.provider.MediaStore.MediaColumns.DATA),
                    null,
                    null,
                    null
                )?.use { c ->
                    if (c.moveToFirst()) {
                        val idx = c.getColumnIndex(android.provider.MediaStore.MediaColumns.DATA)
                        if (idx >= 0) {
                            val path = c.getString(idx)
                            if (!path.isNullOrBlank() && path.startsWith("/")) {
                                candidates += File(path.substringBeforeLast('.') + ".lrc")
                            }
                        }
                    }
                }
            } catch (_: Exception) { }
        }
        for (lrcFile in candidates.distinctBy { it.absolutePath }) {
            try {
                val parent = lrcFile.parentFile
                val writable = parent != null && (
                    lrcFile.canWrite() ||
                        parent.canWrite() ||
                        (hasAllFilesAccess() && parent.exists())
                    )
                if (writable) {
                    parent?.mkdirs()
                    lrcFile.writeText(lrcText, Charsets.UTF_8)
                    if (lrcFile.exists() && lrcFile.length() > 0) return true
                }
            } catch (_: Exception) { }
        }
        return false
    }

    /** Grava letras embutidas nas tags do arquivo (FieldKey.LYRICS). */
    fun embedInTags(context: Context, track: Track, lrcText: String): Boolean {
        if (lrcText.isBlank()) return false
        return try {
            TagWriter.writeLyricsOnly(context, track, lrcText).ok
        } catch (_: Exception) {
            false
        }
    }

    fun save(
        context: Context,
        track: Track,
        lines: List<LyricLine>,
        target: Target
    ): Boolean {
        val text = linesToLrcText(lines)
        if (text.isBlank()) return false
        val wrote = when (target) {
            Target.LRC_FILE -> embedLrc(context, track, text)
            Target.EMBEDDED -> embedInTags(context, track, text)
        }
        if (!wrote) return false

        // Releitura imediata: alguns formatos/tag handlers podem aceitar commit sem
        // manter o campo LYRICS. Não feche o editor nem indique sucesso se os tempos sumiram.
        val savedText = when (target) {
            Target.LRC_FILE -> externalLrcPath(track)?.let { file ->
                try { if (file.isFile) file.readText(Charsets.UTF_8) else null } catch (_: Exception) { null }
            }
            Target.EMBEDDED -> TagReader.read(context, track)?.lyrics
        } ?: return false
        if (savedText.isBlank()) return false
        val expectedTimes = syncTimes(lines)
        if (expectedTimes.isEmpty()) return true
        val savedTimes = syncTimes(LyricsExtractor.parseLyricsContent(savedText))
        val remaining = savedTimes.toMutableList()
        for (expected in expectedTimes) {
            val match = remaining.indices.minByOrNull { index ->
                kotlin.math.abs(remaining[index] - expected)
            }?.takeIf { kotlin.math.abs(remaining[it] - expected) <= 10L } ?: return false
            remaining.removeAt(match)
        }
        return true
    }

    private fun syncTimes(lines: List<LyricLine>): List<Long> = lines.flatMap { line ->
        val wordTimes = line.words.mapNotNull { it.timeMs.takeIf { time -> time >= 0L } }
        if (wordTimes.isNotEmpty()) wordTimes
        else listOfNotNull(line.timeMs.takeIf { it >= 0L })
    }.sorted()
}
