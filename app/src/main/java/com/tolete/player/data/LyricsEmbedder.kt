package com.tolete.player.data

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Salva letra sincronizada como arquivo .lrc ao lado da faixa (quando o caminho
 * absoluto estiver disponível e for gravável). Isso é o método mais confiável
 * sem dependência de biblioteca de tags e é priorizado pelo LyricsExtractor.
 *
 * Retorna true se o arquivo foi gravado com sucesso.
 */
object LyricsEmbedder {

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
