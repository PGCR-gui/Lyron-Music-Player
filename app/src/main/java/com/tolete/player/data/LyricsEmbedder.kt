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
     *  - tradução leva o prefixo {tr} logo após o timestamp da linha;
     *  - só palavras COM tempo recebem <mm:ss.xx> (nunca grava <00:00.00> para palavra sem tempo);
     *  - sílaba: <mm:ss.xx>{sil}texto (a ordem que o parser espera);
     *  - linha sem nenhuma palavra com tempo vira LRC simples.
     */
    fun linesToLrcText(lines: List<LyricLine>): String = buildString {
        lines.forEach { line ->
            val tr = if (line.isTranslation) "{tr}" else ""
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
                    append('[').append(fmtTs(start)).append(']').append(tr)
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
                line.timeMs < 0 -> appendLine(tr + textWithMarkers)
                else -> appendLine("[${fmtTs(line.timeMs)}]$tr$textWithMarkers")
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
        return when (target) {
            Target.LRC_FILE -> embedLrc(context, track, text)
            Target.EMBEDDED -> embedInTags(context, track, text)
        }
    }
}
