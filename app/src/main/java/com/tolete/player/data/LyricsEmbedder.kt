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

    /** Resultado de [saveIfAbsent]. */
    enum class SaveResult { SAVED, FAILED, ALREADY_STORED }

    /**
     * Gravações (escrita) excluem leituras: sem isto, o popup (que relê a letra a cada ~1,5 s) e a
     * tela Tocando agora podiam ler o .lrc/tag NO MEIO de uma gravação (arquivo truncado/vazio),
     * concluir "esta faixa não tem letra" e a busca automática sobrescrevia a letra salva.
     */
    internal val lyricsLock = java.util.concurrent.locks.ReentrantReadWriteLock()

    /** Motivo da última falha de [save] (para o aviso do editor dizer o que realmente deu errado). */
    @Volatile
    var lastError: String? = null

    /** Último .lrc gravado com sucesso por [embedLrc] (a conferência relê exatamente este arquivo). */
    @Volatile
    private var lastWrittenLrc: File? = null

    /**
     * Existe QUALQUER letra salva para a faixa (.lrc/.elrc/.ttml em qualquer caixa, ou embutida)?
     * É conservadora: se não deu para ler com certeza (erro de E/S), responde true, porque
     * sobrescrever uma letra existente é pior do que deixar de baixar uma.
     */
    fun hasStoredLyrics(context: Context, track: Track): Boolean {
        lyricsLock.readLock().lock()
        try {
            if (LyricsExtractor.sidecarFiles(track).any { f ->
                    try { f.isFile && f.length() > 0L } catch (_: Exception) { true }
                }) return true
            return LyricsExtractor.hasEmbeddedOrUncertain(context, track)
        } finally {
            lyricsLock.readLock().unlock()
        }
    }

    /**
     * Grava só se, NO MOMENTO da gravação, ainda não houver letra salva. Verificação e escrita
     * ficam sob o mesmo lock, então duas rotinas (tela + popup) não se atropelam.
     * Usado por todos os fluxos automáticos; só o editor (ação explícita) sobrescreve.
     */
    fun saveIfAbsent(
        context: Context,
        track: Track,
        lines: List<LyricLine>,
        target: Target
    ): SaveResult {
        lyricsLock.writeLock().lock()
        try {
            if (hasStoredLyrics(context, track)) return SaveResult.ALREADY_STORED
            return if (save(context, track, lines, target)) SaveResult.SAVED else SaveResult.FAILED
        } finally {
            lyricsLock.writeLock().unlock()
        }
    }

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
        return String.format(Locale.US, "%02d:%02d.%03d", t / 60_000, (t % 60_000) / 1000, t % 1000)
    }

    /**
     * Fim da palavra só é gravado quando não dá para inferi-lo na leitura: na leitura o fim
     * padrão é o início da próxima palavra com tempo, então fim igual a isso não precisa de tag.
     * Sem isto, o endMs marcado manualmente no sincronizador nunca chegava ao arquivo.
     */
    private fun explicitWordEnd(words: List<LyricWord>, index: Int): Long? {
        val w = words[index]
        if (w.timeMs < 0L || w.endMs <= w.timeMs) return null
        val nextStart = words.drop(index + 1).firstOrNull { it.timeMs > w.timeMs }?.timeMs
        if (nextStart != null && kotlin.math.abs(w.endMs - nextStart) <= 10L) return null
        return w.endMs
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
     *  - só palavras COM tempo recebem <mm:ss.xxx> (nunca grava <00:00.000> para palavra sem tempo);
     *  - sílaba: <mm:ss.xx>{sil}texto (a ordem que o parser espera);
     *  - linha sem nenhuma palavra com tempo vira LRC simples.
     */
    fun linesToLrcText(lines: List<LyricLine>): String = buildString {
        LyricsFormat.normalizeBackgroundLines(lines).forEach { line ->
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
                            // endMs definido à mão (ou vindo da fonte) que a leitura não consegue inferir.
                            explicitWordEnd(line.words, i)?.let { end ->
                                append('<').append(fmtTs(end)).append('>')
                            }
                        } else {
                            // Palavra ainda sem tempo (sincronização parcial): vai marcada com {nt}, senão a leitura
                            // a colava na palavra anterior ou lhe dava um tempo inventado e a sincronização "sumia".
                            if (i > 0 && !w.isSyllable) append(' ')
                            append("{nt}")
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
                    // Atômico: grava num temporário e renomeia. writeText direto trunca o arquivo
                    // antes de escrever, e uma leitura nesse instante via um .lrc vazio.
                    val tmp = File(lrcFile.path + ".tmp")
                    var replaced = false
                    try {
                        tmp.writeText(lrcText, Charsets.UTF_8)
                        replaced = tmp.length() > 0 && tmp.renameTo(lrcFile)
                    } catch (_: Exception) { }
                    if (!replaced) {
                        try { tmp.delete() } catch (_: Exception) { }
                        lrcFile.writeText(lrcText, Charsets.UTF_8)
                    }
                    if (lrcFile.exists() && lrcFile.length() > 0) {
                        lastWrittenLrc = lrcFile
                        return true
                    }
                }
            } catch (_: Exception) { }
        }
        return false
    }

    /** Grava letras embutidas nas tags do arquivo (FieldKey.LYRICS). */
    fun embedInTags(context: Context, track: Track, lrcText: String): Boolean {
        if (lrcText.isBlank()) return false
        return try {
            val result = TagWriter.writeLyricsOnly(context, track, lrcText)
            if (!result.ok) lastError = result.message
            result.ok
        } catch (e: Exception) {
            lastError = e.message?.take(160)
            false
        }
    }

    fun save(
        context: Context,
        track: Track,
        lines: List<LyricLine>,
        target: Target
    ): Boolean {
        lyricsLock.writeLock().lock()
        try {
            return saveLocked(context, track, lines, target)
        } finally {
            lyricsLock.writeLock().unlock()
        }
    }

    private fun saveLocked(
        context: Context,
        track: Track,
        lines: List<LyricLine>,
        target: Target
    ): Boolean {
        lastError = null
        val text = linesToLrcText(lines)
        if (text.isBlank()) {
            lastError = "não há linhas com texto para gravar"
            return false
        }
        lastWrittenLrc = null
        val wrote = when (target) {
            Target.LRC_FILE -> embedLrc(context, track, text)
            Target.EMBEDDED -> embedInTags(context, track, text)
        }
        if (!wrote) {
            if (lastError == null) lastError = "não foi possível gravar (sem permissão na pasta/arquivo)"
            return false
        }

        // Releitura imediata: alguns formatos/tag handlers podem aceitar commit sem
        // manter o campo LYRICS. Não feche o editor nem indique sucesso se os tempos sumiram.
        val savedText: String? = when (target) {
            // Relê o arquivo que acabou de ser gravado (o caminho pode ter vindo do MediaStore, e aí
            // externalLrcPath() não o enxerga e a conferência reprovava uma gravação correta).
            Target.LRC_FILE -> (lastWrittenLrc ?: externalLrcPath(track))?.let { file ->
                try { if (file.isFile) file.readText(Charsets.UTF_8) else null } catch (_: Exception) { null }
            }
            Target.EMBEDDED -> try { TagReader.read(context, track)?.lyrics } catch (_: Exception) { null }
        }
        // Não deu para reler: a gravação em si já foi confirmada, então não reprova por isso.
        if (savedText == null) return true
        if (savedText.isBlank()) {
            lastError = "a letra gravada voltou vazia na releitura"
            return false
        }
        val expectedTimes = syncTimes(lines)
        if (expectedTimes.isEmpty()) return true
        val savedTimes = syncTimes(LyricsExtractor.parseLyricsContent(savedText))
        val remaining = savedTimes.toMutableList()
        var matched = 0
        for (expected in expectedTimes) {
            val match = remaining.indices.minByOrNull { index ->
                kotlin.math.abs(remaining[index] - expected)
            }?.takeIf { kotlin.math.abs(remaining[it] - expected) <= 10L } ?: continue
            remaining.removeAt(match)
            matched++
        }
        // O objetivo da conferência é detectar tempos que SUMIRAM (formato que descarta o timestamp).
        // Antes, uma única palavra fora da tolerância reprovava a letra inteira, mesmo gravada certa.
        if (matched * 2 < expectedTimes.size) {
            lastError = "os tempos não foram preservados na gravação ($matched de ${expectedTimes.size})"
            return false
        }
        return true
    }

    private fun syncTimes(lines: List<LyricLine>): List<Long> = lines
        // O parser descarta linhas sem texto (pausas instrumentais "[01:23.45]"); contá-las aqui
        // fazia a conferência falhar mesmo com a letra gravada corretamente.
        .filter { it.text.isNotBlank() || it.words.isNotEmpty() }
        .flatMap { line ->
        val wordTimes = line.words.mapNotNull { it.timeMs.takeIf { time -> time >= 0L } }
        if (wordTimes.isNotEmpty()) wordTimes
        else listOfNotNull(line.timeMs.takeIf { it >= 0L })
    }.sorted()
}
