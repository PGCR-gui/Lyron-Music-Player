package com.tolete.player.data

import android.content.Context
import java.io.File

/**
 * Gravação de letras:
 * - Arquivo sidecar .lrc ao lado do áudio (preferido quando a pasta for gravável)
 * - Tags embutidas (FieldKey.LYRICS) via JAudioTagger
 * - Fallback no cache do app
 *
 * O LyricsExtractor prioriza sidecar / cache e depois tags embutidas.
 */
object LyricsEmbedder {

    enum class Target { LRC_FILE, EMBEDDED }

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
        return f.exists() && f.length() > 0
    }

    fun canWriteExternalLrc(track: Track): Boolean {
        val f = externalLrcPath(track) ?: return false
        return try {
            val parent = f.parentFile
            parent != null && (parent.canWrite() || (!f.exists() && parent.exists() && parent.canWrite()) || f.canWrite())
        } catch (_: Exception) {
            false
        }
    }

    fun loadExternalLrc(track: Track): List<LyricLine>? {
        val f = externalLrcPath(track) ?: return null
        if (!f.exists()) return null
        return try {
            LyricsExtractor.parseLrcText(f.readText(Charsets.UTF_8))
        } catch (_: Exception) {
            null
        }
    }

    fun linesToLrcText(lines: List<LyricLine>): String = buildString {
        lines.forEach { line ->
            if (line.timeMs < 0) {
                appendLine(line.text)
            } else {
                val m = line.timeMs / 60_000
                val s = (line.timeMs % 60_000) / 1000
                val cs = (line.timeMs % 1000) / 10
                appendLine("[%02d:%02d.%02d]%s".format(m, s, cs, line.text))
            }
        }
    }

    fun embedLrc(context: Context, track: Track, lrcText: String): Boolean {
        if (lrcText.isBlank()) return false
        // 1) caminho absoluto legível/gravável
        if (track.path.startsWith("/")) {
            try {
                val audio = File(track.path)
                if (audio.exists() && audio.parentFile?.canWrite() == true) {
                    val base = track.path.substringBeforeLast('.')
                    val lrcFile = File("$base.lrc")
                    lrcFile.writeText(lrcText, Charsets.UTF_8)
                    return lrcFile.exists() && lrcFile.length() > 0
                }
            } catch (_: Exception) { }
        }
        // 2) tentativa via content resolver (algumas URIs file://)
        try {
            if (track.uri.scheme == "file") {
                val path = track.uri.path
                if (!path.isNullOrBlank()) {
                    val audio = File(path)
                    if (audio.exists() && audio.parentFile?.canWrite() == true) {
                        val base = path.substringBeforeLast('.')
                        val lrcFile = File("$base.lrc")
                        lrcFile.writeText(lrcText, Charsets.UTF_8)
                        return lrcFile.exists() && lrcFile.length() > 0
                    }
                }
            }
        } catch (_: Exception) { }
        // Fallback: salva no cache do app (não ideal, mas preserva a letra para a sessão)
        return writeCache(context, track, lrcText)
    }

    fun writeCache(context: Context, track: Track, lrcText: String): Boolean {
        return try {
            val dir = File(context.cacheDir, "lyrics")
            dir.mkdirs()
            val safeId = track.id.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80)
            val f = File(dir, "$safeId.lrc")
            f.writeText(lrcText, Charsets.UTF_8)
            f.exists()
        } catch (_: Exception) {
            false
        }
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
            Target.LRC_FILE -> {
                if (embedLrc(context, track, text)) true
                else writeCache(context, track, text)
            }
            Target.EMBEDDED -> embedInTags(context, track, text)
        }
    }

    /** Tenta carregar .lrc do cache do app (fallback do embed). */
    fun loadFromCache(context: Context, track: Track): List<LyricLine>? {
        return try {
            val dir = File(context.cacheDir, "lyrics")
            val safeId = track.id.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80)
            val f = File(dir, "$safeId.lrc")
            if (f.exists()) LyricsExtractor.parseLrcText(f.readText()) else null
        } catch (_: Exception) {
            null
        }
    }
}
