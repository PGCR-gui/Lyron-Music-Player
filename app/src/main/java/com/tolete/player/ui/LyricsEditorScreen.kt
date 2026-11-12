package com.tolete.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tolete.player.data.LyricLine
import com.tolete.player.data.LyricAnimation
import com.tolete.player.data.LyricWord
import com.tolete.player.data.LyricsEmbedder
import com.tolete.player.data.LyricsExtractor
import com.tolete.player.data.Track
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * Editor de sincronização de letras (tags embutidas):
 * - Modo simples: uma única caixa de texto grande (linhas separadas por Enter)
 * - Modo sincronizado: cada linha em caixa liquid glass + botão de relógio
 * - Ao mudar de simples → sincronizado, cada linha do texto vira uma caixa
 * - Miniplayer com -10s / play / +10s
 */
@OptIn(UnstableApi::class)
@Composable
fun LyricsEditorScreen(
    track: Track,
    initialLines: List<LyricLine>,
    onSave: (List<LyricLine>, LyricsEmbedder.Target) -> Unit,
    onClose: () -> Unit,
    appColors: AppColors,
    /** Target fixo desta sessão: LRC_FILE ou EMBEDDED — o save vai direto para esse destino. */
    initialTarget: LyricsEmbedder.Target = LyricsEmbedder.Target.EMBEDDED,
    sourceWasTtml: Boolean = false
) {
    val editorContext = LocalContext.current
    val editorPlayer = remember(track.id) { createEditorPreviewPlayer(editorContext, track) }
    var previewIsPlaying by remember(track.id) { mutableStateOf(editorPlayer.isPlaying) }
    DisposableEffect(editorPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                previewIsPlaying = isPlaying
            }
        }
        editorPlayer.addListener(listener)
        onDispose {
            editorPlayer.removeListener(listener)
            editorPlayer.release()
        }
    }
    val lines = remember(track.id, initialLines) {
        mutableStateListOf<EditableLine>().apply {
            if (initialLines.isEmpty()) {
                add(EditableLine(timeMs = -1L, text = ""))
            } else {
                // Agrupa traduções: linhas isTranslation ficam como translationText da linha principal anterior
                var i = 0
                while (i < initialLines.size) {
                    val src = initialLines[i]
                    if (src.isTranslation) {
                        // Tradução solta — trata como linha normal marcada
                        val words = src.words.map {
                            EditableWord(timeMs = it.timeMs, text = it.text, isSyllable = it.isSyllable, animation = it.animation, endMs = it.endMs)
                        }
                        add(EditableLine(
                            timeMs = src.timeMs,
                            text = src.text,
                            words = words,
                            isTranslation = true,
                            isBackground = src.isBackground
                        ))
                        i++
                        continue
                    }
                    val words = src.words.map {
                        EditableWord(timeMs = it.timeMs, text = it.text, isSyllable = it.isSyllable, animation = it.animation, endMs = it.endMs)
                    }
                    var trText = ""
                    var trWords = emptyList<EditableWord>()
                    if (i + 1 < initialLines.size && initialLines[i + 1].isTranslation) {
                        trText = initialLines[i + 1].text
                        trWords = initialLines[i + 1].words.map {
                            EditableWord(timeMs = it.timeMs, text = it.text, isSyllable = it.isSyllable, animation = it.animation, endMs = it.endMs)
                        }
                        i++ // consome a tradução
                    }
                    add(EditableLine(
                        timeMs = src.timeMs,
                        text = src.text,
                        words = words,
                        isBackground = src.isBackground,
                        translationText = trText,
                        translationWords = trWords
                    ))
                    i++
                }
            }
        }
    }
    var position by remember(track.id) { mutableLongStateOf(editorPlayer.currentPosition) }
    // Editor manual de tempo (long-press no relógio)
    var manualTimeTarget by remember { mutableStateOf<Pair<Int, Int?>?>(null) } // lineIndex to wordIndex?
    var manualTimeMs by remember { mutableLongStateOf(0L) }
    var animationTarget by remember { mutableStateOf<AnimationTarget?>(null) }
    /** Passo 1 do FX em Linhas/Tradução: escolher a palavra antes do efeito. */
    var fxWordPicker by remember { mutableStateOf<FxWordPicker?>(null) }
    /** Passo extra na tradução: sincronizar tempo com palavra animada da letra. */
    var fxSyncPicker by remember { mutableStateOf<FxSyncPicker?>(null) }
    var simpleTextLayoutForGesture by remember { mutableStateOf<TextLayoutResult?>(null) }
    val wordListState = rememberLazyListState()
    val lineListState = rememberLazyListState()

    // ---- Auto scroll ao sincronizar (linha ou palavra) ----
    // Comportamento: se o item sincronizado está no fim da tela (o próximo item já não cabe
    // inteiro), a lista rola suavemente e leva o item sincronizado para perto do topo, revelando
    // os próximos. Sincronizar itens que estão no topo/meio não move a lista.
    val autoScrollScope = rememberCoroutineScope()
    val autoScrollJob = remember { arrayOfNulls<Job>(1) }
    val wordListCoords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val autoScrollGapPx = with(LocalDensity.current) { 12.dp.toPx() }
    fun autoScrollIfAtBottom(
        state: LazyListState,
        itemTop: Float,
        itemHeight: Float,
        viewportTop: Float,
        viewportBottom: Float
    ) {
        val viewportH = viewportBottom - viewportTop
        if (viewportH <= 0f) return
        // O próximo item (altura parecida) ainda cabe inteiro na tela? Então não rola.
        if (itemTop + itemHeight * 2f + autoScrollGapPx <= viewportBottom) return
        val targetTop = viewportTop + viewportH * 0.22f
        val delta = itemTop - targetTop
        if (delta <= 1f) return
        autoScrollJob[0]?.cancel()
        autoScrollJob[0] = autoScrollScope.launch {
            state.animateScrollBy(delta, tween(durationMillis = 380))
        }
    }
    // Modo Palavras: usa as coordenadas reais do elemento clicado (linhas viram itens altos).
    fun autoScrollWordList(target: LayoutCoordinates?) {
        val list = wordListCoords[0]
        if (target == null || list == null || !target.isAttached || !list.isAttached) return
        val top = list.localPositionOf(target, Offset.Zero).y
        autoScrollIfAtBottom(
            wordListState,
            top,
            target.size.height.toFloat() + autoScrollGapPx,
            0f,
            list.size.height.toFloat()
        )
    }
    var editorMode by remember(track.id, initialLines) {
        mutableStateOf(
            when {
                initialLines.any { it.hasSyllableSync } -> EditorMode.WORD
                initialLines.isNotEmpty() && initialLines.any { it.timeMs >= 0 } -> EditorMode.LINE
                else -> EditorMode.SIMPLE
            }
        )
    }
    var simpleText by remember(track.id, initialLines) {
        mutableStateOf(
            if (initialLines.isEmpty()) ""
            else initialLines.joinToString("\n") {
                (if (it.isBackground) "{bg}" else "") + (if (it.isTranslation) "{tr}" else "") + wordsWithMarkers(
                    it.words.map { w -> EditableWord(w.timeMs, w.text, w.isSyllable, w.animation, w.endMs) },
                    it.text
                )
            }
        )
    }
    fun simpleTextFromLines(): String = lines.flatMap { line ->
        buildList {
            add((if (line.isBackground) "{bg}" else "") + (if (line.isTranslation) "{tr}" else "") +
                wordsWithMarkers(line.words, line.text))
            if (line.translationText.isNotBlank()) {
                add("{tr}" + wordsWithMarkers(line.translationWords, line.translationText))
            }
        }
    }.joinToString("\n")
    val duration = track.duration.coerceAtLeast(1L)
    var playbackSpeed by remember(track.id) { mutableStateOf(1f) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var speedDraft by remember { mutableStateOf(playbackSpeed) }

    LaunchedEffect(editorPlayer, previewIsPlaying) {
        while (true) {
            position = editorPlayer.currentPosition
            if (!previewIsPlaying) break
            delay(50)
        }
    }

        fun parseTimeToMs(raw: String): Long? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        // Aceita m:ss, mm:ss, m:ss.cc, mm:ss.cc, ss.cc
        val re = Regex("""^(?:(\d+):)?(\d{1,2})(?:[.:](\d{1,3}))?$""")
        val m = re.matchEntire(t) ?: return null
        val mins = m.groupValues[1].toLongOrNull() ?: 0L
        val secs = m.groupValues[2].toLongOrNull() ?: return null
        val frac = m.groupValues[3]
        val msPart = when {
            frac.isEmpty() -> 0L
            frac.length == 1 -> frac.toLong() * 100
            frac.length == 2 -> frac.toLong() * 10
            else -> frac.take(3).toLong()
        }
        return mins * 60_000L + secs * 1000L + msPart
    }

    fun formatTime(ms: Long): String {
        if (ms < 0) return "—:——.—"
        val totalSec = ms / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        val frac = (ms % 1000) / 10
        return "%d:%02d.%02d".format(m, s, frac)
    }

    fun decodeTaggedText(text: String): Pair<String, List<EditableWord>> {
        val parsed = LyricsExtractor.parseLyricsContent(text).firstOrNull() ?: return text to emptyList()
        val taggedWords = parsed.words.filter {
            it.isSyllable || it.animation != LyricAnimation.NONE
        }
        if (taggedWords.isEmpty()) return text to emptyList()
        return parsed.text to parsed.words.map {
            EditableWord(it.timeMs, it.text, it.isSyllable, it.animation, it.endMs)
        }
    }

    fun mergeWordAnnotations(
        parsedWords: List<EditableWord>,
        previousWords: List<EditableWord>
    ): List<EditableWord> = parsedWords.mapIndexed { index, parsedWord ->
        val previous = previousWords.getOrNull(index)
        parsedWord.copy(
            timeMs = previous?.timeMs?.takeIf { it >= 0 } ?: parsedWord.timeMs,
            endMs = previous?.endMs?.takeIf { it >= 0 } ?: parsedWord.endMs,
            animation = parsedWord.animation,
            isSyllable = parsedWord.isSyllable
        )
    }

    fun applyAnimation(target: AnimationTarget, animation: LyricAnimation, syncTimeMs: Long? = null) {
        if (target.simpleStart != null && target.simpleEnd != null) {
            if (target.isTranslation && target.lineIndex != null) {
                val li = target.lineIndex
                if (li !in lines.indices) return
                val line = lines[li]
                // Texto exibido (com marcadores) para respeitar o offset do long-press
                val displayed = wordsWithMarkers(line.translationWords, line.translationText)
                val marked = addAnimationMarker(displayed, target.simpleStart, target.simpleEnd, animation)
                val (cleanText, taggedWords) = decodeTaggedText(marked)
                val baseWords = line.translationWords.ifEmpty {
                    line.translationText.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                        .map { EditableWord(text = it) }
                }
                val merged = if (taggedWords.isNotEmpty()) {
                    mergeWordAnnotations(taggedWords, baseWords)
                } else {
                    // Remover animação: limpa efeitos mas preserva timestamps/sílabas
                    baseWords.map { it.copy(animation = LyricAnimation.NONE) }
                }
                lines[li] = line.copy(
                    translationText = cleanText.ifBlank { rebuildTextFromWords(merged) },
                    translationWords = merged
                )
                return
            }
            simpleText = addAnimationMarker(simpleText, target.simpleStart, target.simpleEnd, animation)
            return
        }
        val lineIndex = target.lineIndex ?: return
        if (lineIndex !in lines.indices) return
        val line = lines[lineIndex]
        val wordIndex = target.wordIndex ?: return
        if (target.isTranslation) {
            val words = line.translationWords.ifEmpty {
                line.translationText.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                    .map { EditableWord(text = it) }
            }.toMutableList()
            if (wordIndex !in words.indices) return
            val syncedTime = syncTimeMs?.takeIf { it >= 0L } ?: words[wordIndex].timeMs
            words[wordIndex] = words[wordIndex].copy(animation = animation, timeMs = syncedTime)
            lines[lineIndex] = line.copy(
                translationWords = words,
                translationText = rebuildTextFromWords(words)
            )
        } else {
            val words = line.ensureWords().words.toMutableList()
            if (wordIndex !in words.indices) return
            words[wordIndex] = words[wordIndex].copy(animation = animation)
            lines[lineIndex] = line.copy(words = words).rebuildTextFromWords()
        }
    }

    fun openWordAnimationFromText(lineIndex: Int, text: String, offset: Int, isTranslation: Boolean = false) {
        val range = wordRangeAt(text, offset) ?: return
        if (editorMode == EditorMode.SIMPLE) {
            animationTarget = AnimationTarget(simpleStart = range.first, simpleEnd = range.second)
            return
        }
        // Tradução: mesmo fluxo do texto simples — insere marcador no texto ({glo}/{pop}/{des}/{ded})
        if (isTranslation) {
            if (lineIndex !in lines.indices) return
            animationTarget = AnimationTarget(
                lineIndex = lineIndex,
                isTranslation = true,
                simpleStart = range.first,
                simpleEnd = range.second
            )
            return
        }
        if (lineIndex !in lines.indices) return
        val line = lines[lineIndex]
        val words = expandEditableWords(line.ensureWords().words)
        lines[lineIndex] = line.copy(words = words)
        val index = editableWordIndexAt(words, offset)
        if (index >= 0) {
            animationTarget = AnimationTarget(
                lineIndex = lineIndex,
                wordIndex = index,
                isTranslation = false
            )
        }
    }

    /** Aplica o texto simples em `lines` preservando timestamps quando o texto da linha não muda. */
    fun applySimpleTextToLines(text: String) {
        val old = lines.toList()
        val parts = text
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lines()
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }
        // Selecionar/substituir texto pode produzir um valor vazio intermediário.
        // Não o trate como uma instrução para apagar todas as letras existentes.
        if (parts.isEmpty()) {
            if (old.none { it.text.isNotBlank() || it.words.any { word -> word.text.isNotBlank() } || it.translationText.isNotBlank() }) {
                lines.clear()
                lines.add(EditableLine(timeMs = -1L, text = ""))
            }
            return
        }
        val oldMain = old.filterNot { it.isTranslation }
        val rebuilt = mutableListOf<EditableLine>()
        var mainIndex = 0
        parts.forEach { rawPart ->
            var sourcePart = rawPart.trimStart()
            var startsWithTranslation = false
            var startsWithBackground = false
            val lineMarker = Regex("^\\{(tr|bg)\\}", RegexOption.IGNORE_CASE)
            while (true) {
                val marker = lineMarker.find(sourcePart) ?: break
                when (marker.groupValues[1].lowercase()) {
                    "tr" -> startsWithTranslation = true
                    "bg" -> startsWithBackground = true
                }
                sourcePart = sourcePart.substring(marker.range.last + 1).trimStart()
            }
            val (part, parsedWords) = decodeTaggedText(sourcePart)
            if (startsWithTranslation) {
                val previous = rebuilt.lastOrNull()
                if (previous != null && !previous.isTranslation) {
                    val words = if (parsedWords.isNotEmpty()) mergeWordAnnotations(parsedWords, previous.translationWords)
                    else previous.translationWords.map { it.copy(animation = LyricAnimation.NONE) }
                    rebuilt[rebuilt.lastIndex] = previous.copy(
                        translationText = part.trim(),
                        translationWords = words
                    )
                } else {
                    rebuilt.add(
                        EditableLine(
                            timeMs = -1L,
                            text = part.trim(),
                            words = parsedWords,
                            isTranslation = true,
                            isBackground = startsWithBackground
                        )
                    )
                }
                return@forEach
            }

            val prev = oldMain.getOrNull(mainIndex)
            val next = when {
                prev != null && prev.text == part -> prev.copy(
                    words = when {
                        parsedWords.isNotEmpty() -> mergeWordAnnotations(parsedWords, prev.words)
                        prev.words.any { it.animation != LyricAnimation.NONE } -> prev.words.map { it.copy(animation = LyricAnimation.NONE) }
                        else -> prev.words
                    }
                )
                prev != null && prev.text.trim() == part.trim() -> prev.copy(text = part)
                else -> oldMain.firstOrNull { it.text == part }?.copy(id = System.nanoTime())
                    ?.let { found ->
                        found.copy(words = if (parsedWords.isNotEmpty()) mergeWordAnnotations(parsedWords, found.words) else found.words)
                    }
                    ?: EditableLine(timeMs = -1L, text = part, words = parsedWords)
            }
            rebuilt.add(next.copy(isTranslation = false, isBackground = startsWithBackground))
            mainIndex++
        }
        lines.clear()
        if (rebuilt.isEmpty()) {
            lines.add(EditableLine(timeMs = -1L, text = ""))
        } else {
            lines.addAll(rebuilt)
        }
    }

    /** Quebra Enter/colagens em entradas independentes sem deixar newline dentro do texto. */
    fun splitLineInput(index: Int, rawInput: String) {
        val original = lines.getOrNull(index) ?: return
        val normalized = rawInput.replace("\r\n", "\n").replace('\r', '\n')
        val chunks = normalized.split('\n', ignoreCase = false, limit = Int.MAX_VALUE)
        if (chunks.size <= 1) return
        val oldWords = original.words
        val consumed = BooleanArray(oldWords.size)
        val replacements = chunks.mapIndexed { chunkIndex, rawChunk ->
            val parsed = LyricsExtractor.parseLyricsContent(rawChunk).firstOrNull()
            val cleanText = parsed?.text ?: rawChunk
            val parsedWords = parsed?.words.orEmpty()
            val wordSources = if (parsedWords.isNotEmpty()) parsedWords.map { it.text } else cleanText.trim()
                .split(Regex("\\s+" )).filter { it.isNotEmpty() }
            val editableWords = wordSources.map { wordText ->
                val matchIndex = oldWords.indices.firstOrNull { wi ->
                    !consumed[wi] && oldWords[wi].text.trim() == wordText.trim()
                }
                if (matchIndex != null) {
                    consumed[matchIndex] = true
                    val prior = oldWords[matchIndex]
                    val fromParsed = parsedWords.firstOrNull { it.text.trim() == wordText.trim() }
                    EditableWord(
                        timeMs = fromParsed?.timeMs?.takeIf { it >= 0 } ?: prior.timeMs,
                        text = wordText,
                        isSyllable = fromParsed?.isSyllable ?: prior.isSyllable,
                        animation = fromParsed?.animation?.takeIf { it != LyricAnimation.NONE } ?: prior.animation,
                        endMs = fromParsed?.endMs?.takeIf { it >= 0 } ?: prior.endMs
                    )
                } else {
                    val fromParsed = parsedWords.firstOrNull { it.text.trim() == wordText.trim() }
                    EditableWord(
                        timeMs = fromParsed?.timeMs ?: -1L,
                        text = wordText,
                        isSyllable = fromParsed?.isSyllable ?: false,
                        animation = fromParsed?.animation ?: LyricAnimation.NONE,
                        endMs = fromParsed?.endMs ?: -1L
                    )
                }
            }
            val explicitBg = rawChunk.trimStart().startsWith("{bg}", ignoreCase = true) || parsed?.isBackground == true
            val explicitTranslation = rawChunk.trimStart().startsWith("{tr}", ignoreCase = true) || parsed?.isTranslation == true
            original.copy(
                timeMs = if (chunkIndex == 0) original.timeMs else -1L,
                text = cleanText.replace(Regex("[\\r\\n]+"), " ").trim(),
                words = editableWords,
                isBackground = if (chunkIndex == 0) original.isBackground else (explicitBg || original.isBackground),
                isTranslation = if (chunkIndex == 0) original.isTranslation else (explicitTranslation || original.isTranslation),
                translationText = if (chunkIndex == 0) original.translationText else "",
                translationWords = if (chunkIndex == 0) original.translationWords else emptyList(),
                id = if (chunkIndex == 0) original.id else System.nanoTime() + chunkIndex
            )
        }
        lines.removeAt(index)
        lines.addAll(index, replacements)
    }

    fun switchToMode(target: EditorMode) {
        if (target == editorMode) return
        when (editorMode) {
            EditorMode.SIMPLE -> {
                // Texto → estrutura: mescla com timestamps existentes
                applySimpleTextToLines(simpleText)
            }
            EditorMode.LINE, EditorMode.WORD, EditorMode.TRANSLATION -> {
                // Garante que o texto simples reflita as linhas atuais
                simpleText = simpleTextFromLines()
            }
        }
        if (target == EditorMode.WORD) {
            for (i in lines.indices) {
                lines[i] = lines[i].ensureWords()
            }
        }
        if (target == EditorMode.LINE || target == EditorMode.SIMPLE) {
            // Atualiza text a partir das palavras se houver timestamps de palavra
            for (i in lines.indices) {
                if (lines[i].words.isNotEmpty()) {
                    lines[i] = lines[i].rebuildTextFromWords()
                }
            }
            if (target == EditorMode.SIMPLE) {
                simpleText = simpleTextFromLines()
            }
        }
        editorMode = target
    }


    fun seekBy(deltaMs: Long) {
        val next = (editorPlayer.currentPosition + deltaMs).coerceIn(0L, duration)
        editorPlayer.seekTo(next)
        position = next
    }

    fun applySpeed(next: Float) {
        val clamped = next.coerceIn(0.25f, 1f)
        playbackSpeed = clamped
        // A velocidade desta pré-escuta fica isolada do player principal.
        editorPlayer.playbackParameters = PlaybackParameters(clamped, 1f)
    }

    Box(Modifier.fillMaxSize()) {
        val art = track.artPath
        if (!art.isNullOrBlank()) {
            AsyncImage(
                model = File(art),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(28.dp, BlurredEdgeTreatment.Unbounded)
            )
        } else {
            Box(Modifier.fillMaxSize().background(appColors.gradient))
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(0.14f))
                        .border(1.dp, Color.White.copy(0.25f), CircleShape)
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Fechar", tint = Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(0.12f))
                        .border(
                            1.dp,
                            Brush.verticalGradient(
                                listOf(Color.White.copy(0.45f), Color.White.copy(0.12f))
                            ),
                            RoundedCornerShape(14.dp)
                        )
                ) {
                    ModeTab(
                        label = "Texto",
                        selected = editorMode == EditorMode.SIMPLE,
                        onClick = { switchToMode(EditorMode.SIMPLE) },
                        modifier = Modifier.weight(1f),
                        accent = appColors.accent
                    )
                    ModeTab(
                        label = "Linhas",
                        selected = editorMode == EditorMode.LINE,
                        onClick = { switchToMode(EditorMode.LINE) },
                        modifier = Modifier.weight(1f),
                        accent = appColors.accent
                    )
                    ModeTab(
                        label = "Palavras",
                        selected = editorMode == EditorMode.WORD,
                        onClick = { switchToMode(EditorMode.WORD) },
                        modifier = Modifier.weight(1f),
                        accent = appColors.accent
                    )
                    ModeTab(
                        label = "Tradução",
                        selected = editorMode == EditorMode.TRANSLATION,
                        onClick = { switchToMode(EditorMode.TRANSLATION) },
                        modifier = Modifier.weight(1f),
                        accent = appColors.accent
                    )
                }
                Spacer(Modifier.width(12.dp))
                IconButton(
                    onClick = {
                        // Sempre sincroniza o estado a partir do modo atual antes de salvar
                        if (editorMode == EditorMode.SIMPLE) {
                            applySimpleTextToLines(simpleText)
                        }
                        val out = mutableListOf<LyricLine>()
                        for (el in lines) {
                            val hasContent = el.text.isNotBlank() || el.words.any { w -> w.text.isNotBlank() }
                            if (!hasContent && el.translationText.isBlank()) continue
                            // Auto-ajusta tempo da linha no modo palavras
                            val adjusted = if (editorMode == EditorMode.WORD) el.autoAdjustLineTime() else el
                            val words = adjusted.words
                                .filter { it.text.isNotBlank() }
                                .map {
                                    LyricWord(
                                        timeMs = it.timeMs,
                                        text = it.text,
                                        endMs = it.endMs,
                                        isSyllable = it.isSyllable,
                                        animation = it.animation
                                    )
                                }
                            val text = adjusted.text.trim().ifBlank {
                                words.joinToString("") { w -> if (w.isSyllable) w.text else " " + w.text }.trim()
                            }
                            out += LyricLine(
                                timeMs = adjusted.timeMs,
                                text = text,
                                words = words,
                                isTranslation = adjusted.isTranslation,
                                isBackground = adjusted.isBackground
                            )
                            // Linha de tradução associada
                            if (adjusted.translationText.isNotBlank() || adjusted.translationWords.any { it.text.isNotBlank() }) {
                                val translationWords = adjusted.translationWords
                                    .filter { it.text.isNotBlank() }
                                    .map {
                                        LyricWord(
                                            timeMs = it.timeMs,
                                            text = it.text,
                                            endMs = it.endMs,
                                            isSyllable = it.isSyllable,
                                            animation = it.animation
                                        )
                                    }
                                // Texto limpo (sem marcadores); efeitos ficam só em words
                                val trText = if (translationWords.isNotEmpty()) {
                                    rebuildTextFromWords(adjusted.translationWords)
                                } else {
                                    adjusted.translationText
                                        .replace(Regex("\\{(?:sil|glo|pop|des|ded|eco|zou|zin|sli|typ|fla|vib|rnb|frz|shd|wav)\\}", RegexOption.IGNORE_CASE), "")
                                        .trim()
                                }
                                out += LyricLine(
                                    timeMs = adjusted.timeMs,
                                    text = trText,
                                    words = translationWords,
                                    isTranslation = true
                                )
                            }
                        }
                        if (out.isEmpty() && initialLines.any {
                                it.text.isNotBlank() || it.words.any { word -> word.text.isNotBlank() }
                            }
                        ) {
                            android.widget.Toast.makeText(
                                editorContext,
                                "A letra original foi preservada porque não há linhas para salvar.",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            return@IconButton
                        }
                        // Preenche endMs de palavras e linhas — mesmo pós-processamento do parse de LRC baixado,
                        // para o karaokê ter a mesma suavidade nas letras sincronizadas manualmente.
                        onSave(com.tolete.player.data.LyricsExtractor.finalizeLyricsTimings(out), initialTarget)
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(appColors.accent)
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = "Salvar", tint = Color.Black)
                }
            }

            Text(
                track.title,
                color = Color.White.copy(0.9f),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
            Text(
                track.artist,
                color = Color.White.copy(0.55f),
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp)
            )

            if (sourceWasTtml) {
                Text(
                    "Fonte TTML aberta para conversão. O original não será alterado; somente Salvar grava um .lrc.",
                    color = appColors.accent,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 5.dp)
                )
            }

            // Offset global (modos Linhas e Palavras)
            if (editorMode != EditorMode.SIMPLE) {
                var offsetMs by remember { mutableStateOf("0") }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Offset (ms)", color = Color.White.copy(0.7f), fontSize = 13.sp)
                    BasicTextField(
                        value = offsetMs,
                        onValueChange = { v ->
                            if (v.isEmpty() || v == "-" || v.toLongOrNull() != null) offsetMs = v
                        },
                        modifier = Modifier
                            .width(72.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(0.12f))
                            .border(1.dp, Color.White.copy(0.25f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                        singleLine = true,
                        cursorBrush = SolidColor(appColors.accent)
                    )
                    IconButton(
                        onClick = {
                            val delta = offsetMs.toLongOrNull() ?: 0L
                            if (delta == 0L) return@IconButton
                            for (i in lines.indices) {
                                val el = lines[i]
                                val newLineTime = if (el.timeMs >= 0) (el.timeMs + delta).coerceAtLeast(0L) else el.timeMs
                                val newWords = el.words.map { w ->
                                    if (w.timeMs >= 0) w.copy(timeMs = (w.timeMs + delta).coerceAtLeast(0L))
                                    else w
                                }
                                lines[i] = el.copy(timeMs = newLineTime, words = newWords)
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(appColors.accent.copy(alpha = 0.85f))
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = "Aplicar offset", tint = Color.Black, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        if (editorMode == EditorMode.WORD)
                            "Aplica a linhas e palavras"
                        else
                            "Aplica a todas as linhas sincronizadas",
                        color = Color.White.copy(0.4f),
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            when (editorMode) {
                EditorMode.SIMPLE -> {
                // imePadding no Column + Column scrollável com padding generoso no fim
                // para conseguir ver e editar as últimas linhas com o teclado aberto.
                val simpleScroll = rememberScrollState()
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .padding(bottom = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.White.copy(0.20f), Color.White.copy(0.08f))
                            )
                        )
                        .border(
                            1.dp,
                            Brush.verticalGradient(
                                listOf(Color.White.copy(0.50f), Color.White.copy(0.12f))
                            ),
                            RoundedCornerShape(16.dp)
                        )
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(simpleScroll)
                            .padding(horizontal = 14.dp, vertical = 14.dp)
                            .padding(bottom = 280.dp)
                    ) {
                        BasicTextField(
                            value = simpleText,
                            onValueChange = { simpleText = it },
                            modifier = Modifier.fillMaxWidth(),
                            onTextLayout = { simpleTextLayoutForGesture = it },
                            textStyle = TextStyle(color = Color.White, fontSize = 16.sp, lineHeight = 24.sp),
                            cursorBrush = SolidColor(appColors.accent),
                            decorationBox = { inner ->
                                Box(Modifier.fillMaxWidth()) {
                                    if (simpleText.isEmpty()) {
                                        Text(
                                            "Cole ou escreva a letra aqui…\nCada linha (Enter) vira uma caixa nas abas Linhas/Palavras.\nTimestamps são preservados ao trocar de aba.",
                                            color = Color.White.copy(0.35f),
                                            fontSize = 15.sp,
                                            lineHeight = 22.sp
                                        )
                                    }
                                    inner()
                                }
                            }
                        )
                    }
                }
                }

                EditorMode.LINE -> {
                LazyColumn(
                    state = lineListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(lines, key = { _, line -> line.id }) { index, line ->
                        LineBox(
                            line = line,
                            formatTime = ::formatTime,
                            onTextChange = { newText ->
                                if (newText.contains('\n') || newText.contains('\r')) {
                                    splitLineInput(index, newText)
                                    return@LineBox
                                }
                                val (cleanText, taggedWords) = decodeTaggedText(newText)
                                if (taggedWords.isNotEmpty()) {
                                    lines[index] = line.copy(
                                        text = cleanText,
                                        words = mergeWordAnnotations(taggedWords, line.words)
                                    )
                                } else {
                                    // Ao editar texto da linha, invalida palavras se o texto mudou bastante.
                                    val updated = line.copy(text = cleanText)
                                    lines[index] = if (line.words.isNotEmpty() &&
                                        line.words.joinToString(" ") { it.text } != cleanText.trim()
                                    ) updated.copy(words = emptyList()) else updated
                                }
                            },
                            onLongPressWord = { charOffset ->
                                openWordAnimationFromText(index, line.text, charOffset)
                            },
                            onOpenFx = {
                                val ensured = line.ensureWords()
                                lines[index] = ensured
                                val words = ensured.words
                                    .mapIndexed { i, w -> i to w.text }
                                    .filter { it.second.isNotBlank() }
                                if (words.isNotEmpty()) {
                                    fxWordPicker = FxWordPicker(
                                        lineIndex = index,
                                        isTranslation = false,
                                        words = words
                                    )
                                }
                            },
                            onPlayLine = {
                                if (line.timeMs >= 0) {
                                    editorPlayer.seekTo(line.timeMs)
                                    position = line.timeMs
                                    if (!previewIsPlaying) editorPlayer.play()
                                }
                            },
                            onSync = {
                                val t = editorPlayer.currentPosition.coerceAtLeast(0L)
                                lines[index] = line.copy(timeMs = t)
                                val info = lineListState.layoutInfo
                                info.visibleItemsInfo.firstOrNull { it.index == index }?.let { item ->
                                    autoScrollIfAtBottom(
                                        lineListState,
                                        item.offset.toFloat(),
                                        item.size.toFloat() + autoScrollGapPx,
                                        info.viewportStartOffset.toFloat(),
                                        info.viewportEndOffset.toFloat()
                                    )
                                }
                            },
                            onLongSync = {
                                manualTimeTarget = index to null
                                manualTimeMs = if (line.timeMs >= 0) line.timeMs else editorPlayer.currentPosition
                            },
                            onToggleBackground = {
                                lines[index] = line.copy(isBackground = !line.isBackground)
                            },
                            onDelete = {
                                if (lines.size > 1) lines.removeAt(index)
                                else lines[index] = line.copy(
                                    timeMs = -1L,
                                    text = "",
                                    words = emptyList(),
                                    isTranslation = false,
                                    isBackground = false,
                                    translationText = "",
                                    translationWords = emptyList()
                                )
                            },
                            accent = appColors.accent
                        )
                    }
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.White.copy(0.18f), Color.White.copy(0.08f))
                                    )
                                )
                                .border(
                                    1.dp,
                                    Brush.verticalGradient(
                                        listOf(Color.White.copy(0.45f), Color.White.copy(0.12f))
                                    ),
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable {
                                    lines.add(EditableLine(timeMs = -1L, text = ""))
                                }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.White.copy(0.75f))
                            Spacer(Modifier.width(8.dp))
                            Text("Adicionar linha", color = Color.White.copy(0.75f), fontSize = 14.sp)
                        }
                    }
                }
                } // EditorMode.LINE

                EditorMode.WORD -> {
                LazyColumn(
                    state = wordListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onGloballyPositioned { wordListCoords[0] = it },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(lines, key = { _, line -> "w-${line.id}" }) { index, line ->
                        val displayLine = if (line.words.isEmpty() && line.text.isNotBlank()) {
                            line.ensureWords()
                        } else line
                        // Materializa palavras na primeira composição da aba
                        LaunchedEffect(line.id) {
                            if (lines[index].words.isEmpty() && lines[index].text.isNotBlank()) {
                                lines[index] = lines[index].ensureWords()
                            }
                        }
                        WordLineBox(
                            line = displayLine,
                            formatTime = ::formatTime,
                            onPlayLine = {
                                val time = lines[index].timeMs
                                if (time >= 0) {
                                    editorPlayer.seekTo(time)
                                    position = time
                                    if (!previewIsPlaying) editorPlayer.play()
                                }
                            },
                            onLineSync = { coords ->
                                val t = editorPlayer.currentPosition.coerceAtLeast(0L)
                                lines[index] = lines[index].copy(timeMs = t)
                                autoScrollWordList(coords)
                            },
                            onLongLineSync = {
                                manualTimeTarget = index to null
                                manualTimeMs = if (lines[index].timeMs >= 0) lines[index].timeMs else editorPlayer.currentPosition
                            },
                            onWordSync = { wordIndex, coords ->
                                val t = editorPlayer.currentPosition.coerceAtLeast(0L)
                                val el = lines[index].ensureWords()
                                val newWords = el.words.toMutableList()
                                if (wordIndex in newWords.indices) {
                                    newWords[wordIndex] = newWords[wordIndex].copy(timeMs = t)
                                    val adjusted = el.copy(words = newWords).rebuildTextFromWords().autoAdjustLineTime()
                                    lines[index] = adjusted
                                    // Auto-scroll quando a palavra sincronizada está no fim da tela
                                    autoScrollWordList(coords)
                                }
                            },
                            onLongWordSync = { wordIndex ->
                                val el = lines[index].ensureWords()
                                val t = el.words.getOrNull(wordIndex)?.timeMs ?: -1L
                                manualTimeTarget = index to wordIndex
                                manualTimeMs = if (t >= 0) t else editorPlayer.currentPosition
                            },
                            onLastWordEndSync = {
                                val t = editorPlayer.currentPosition.coerceAtLeast(0L)
                                val el = lines[index].ensureWords()
                                val newWords = el.words.toMutableList()
                                if (newWords.isNotEmpty()) {
                                    val last = newWords.lastIndex
                                    // endMs só faz sentido se for depois do início da palavra (ou da linha)
                                    val minStart = newWords[last].timeMs.takeIf { it >= 0 }
                                        ?: el.timeMs.takeIf { it >= 0 }
                                        ?: 0L
                                    newWords[last] = newWords[last].copy(endMs = t.coerceAtLeast(minStart))
                                    lines[index] = el.copy(words = newWords)
                                }
                            },
                            onOpenAnimationMenu = { wordIndex ->
                                animationTarget = AnimationTarget(lineIndex = index, wordIndex = wordIndex)
                            },
                            onWordTextChange = { wordIndex, newText ->
                                val el = lines[index].ensureWords()
                                val newWords = el.words.toMutableList()
                                if (wordIndex in newWords.indices) {
                                    val parts = if (newText.contains(' ') || newText.contains('\n')) {
                                        newText.split(Regex("\\s+")).filter { it.isNotEmpty() }
                                    } else emptyList()
                                    if (parts.size >= 2) {
                                        val base = newWords[wordIndex]
                                        newWords[wordIndex] = base.copy(text = parts[0])
                                        val extras = parts.drop(1).map {
                                            EditableWord(timeMs = -1L, text = it, isSyllable = false)
                                        }
                                        newWords.addAll(wordIndex + 1, extras)
                                    } else {
                                        newWords[wordIndex] = newWords[wordIndex].copy(text = newText)
                                    }
                                    lines[index] = el.copy(words = newWords).rebuildTextFromWords()
                                }
                            },
                            onToggleSyllable = { wordIndex ->
                                val el = lines[index].ensureWords()
                                val newWords = el.words.toMutableList()
                                if (wordIndex in newWords.indices && wordIndex > 0) {
                                    val w = newWords[wordIndex]
                                    newWords[wordIndex] = w.copy(isSyllable = !w.isSyllable)
                                    lines[index] = el.copy(words = newWords).rebuildTextFromWords()
                                }
                            },
                            onToggleBackground = {
                                lines[index] = lines[index].copy(isBackground = !lines[index].isBackground)
                            },
                            onAddWord = {
                                val el = lines[index].ensureWords()
                                lines[index] = el.copy(
                                    words = el.words + EditableWord(text = "")
                                ).rebuildTextFromWords()
                            },
                            onDeleteWord = { wordIndex ->
                                val el = lines[index].ensureWords()
                                if (el.words.size <= 1) {
                                    if (lines.size > 1) lines.removeAt(index)
                                    else lines[index] = EditableLine(timeMs = -1L, text = "")
                                } else {
                                    val newWords = el.words.toMutableList().also { it.removeAt(wordIndex) }
                                    lines[index] = el.copy(words = newWords).rebuildTextFromWords()
                                }
                            },
                            onDeleteLine = {
                                if (lines.size > 1) lines.removeAt(index)
                                else lines[index] = EditableLine(timeMs = -1L, text = "")
                            },
                            accent = appColors.accent
                        )
                    }
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.White.copy(0.18f), Color.White.copy(0.08f))
                                    )
                                )
                                .border(
                                    1.dp,
                                    Brush.verticalGradient(
                                        listOf(Color.White.copy(0.45f), Color.White.copy(0.12f))
                                    ),
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable {
                                    lines.add(EditableLine(timeMs = -1L, text = "nova linha").ensureWords())
                                }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.White.copy(0.75f))
                            Spacer(Modifier.width(8.dp))
                            Text("Adicionar linha", color = Color.White.copy(0.75f), fontSize = 14.sp)
                        }
                    }
                }
                } // EditorMode.WORD

                EditorMode.TRANSLATION -> {
                    // Cada linha principal com caixa de tradução abaixo
                    LazyColumn(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(lines, key = { _, l -> l.id }) { index, line ->
                            var translationLayout by remember(line.id) { mutableStateOf<TextLayoutResult?>(null) }
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.White.copy(0.22f), Color.White.copy(0.08f))
                                        )
                                    )
                                    .border(
                                        1.dp,
                                        Brush.verticalGradient(
                                            listOf(Color.White.copy(0.55f), Color.White.copy(0.12f))
                                        ),
                                        RoundedCornerShape(16.dp)
                                    )
                                    .padding(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        line.text.ifBlank { "(linha vazia)" },
                                        color = Color.White.copy(0.9f),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            if (line.timeMs >= 0) {
                                                editorPlayer.seekTo(line.timeMs)
                                                position = line.timeMs
                                                if (!previewIsPlaying) editorPlayer.play()
                                            }
                                        },
                                        enabled = line.timeMs >= 0,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.PlayArrow,
                                            contentDescription = "Reproduzir a partir desta linha",
                                            tint = if (line.timeMs >= 0) appColors.accent else Color.White.copy(0.25f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "Tradução",
                                        color = appColors.accent.copy(0.85f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "FX",
                                        color = appColors.accent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(appColors.accent.copy(0.18f))
                                            .clickable {
                                                val baseWords = line.translationWords.ifEmpty {
                                                    line.translationText.trim().split(Regex("\\s+"))
                                                        .filter { it.isNotEmpty() }
                                                        .map { EditableWord(text = it) }
                                                }
                                                if (baseWords.isNotEmpty()) {
                                                    lines[index] = line.copy(translationWords = baseWords)
                                                    fxWordPicker = FxWordPicker(
                                                        lineIndex = index,
                                                        isTranslation = true,
                                                        words = baseWords.mapIndexed { i, w -> i to w.text }
                                                            .filter { it.second.isNotBlank() }
                                                    )
                                                }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                BasicTextField(
                                    value = wordsWithMarkers(line.translationWords, line.translationText),
                                    onValueChange = { tr ->
                                        val (cleanText, taggedWords) = decodeTaggedText(tr)
                                        lines[index] = if (taggedWords.isNotEmpty()) {
                                            // Texto limpo + efeitos nas palavras (marcadores só na exibição)
                                            line.copy(
                                                translationText = cleanText,
                                                translationWords = mergeWordAnnotations(taggedWords, line.translationWords)
                                            )
                                        } else line.copy(translationText = tr, translationWords = emptyList())
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White.copy(0.08f))
                                        .border(1.dp, Color.White.copy(0.15f), RoundedCornerShape(12.dp))
                                        .padding(10.dp),
                                    onTextLayout = { translationLayout = it },
                                    textStyle = TextStyle(color = Color.White.copy(0.92f), fontSize = 14.sp),
                                    cursorBrush = SolidColor(appColors.accent),
                                    decorationBox = { inner ->
                                        if (line.translationText.isEmpty()) {
                                            Text(
                                                "Escreva a tradução… (use FX para efeitos)",
                                                color = Color.White.copy(0.35f),
                                                fontSize = 14.sp
                                            )
                                        }
                                        inner()
                                    }
                                )
                            }
                        }
                    }
                } // EditorMode.TRANSLATION
            } // when

            if (showSpeedDialog) {
                AppAlertDialog(
                    onDismissRequest = { showSpeedDialog = false },
                    title = { Text("Velocidade de reprodução") },
                    text = {
                        Column {
                            Text(
                                "${(speedDraft * 100f).toInt()}%",
                                color = appColors.accent,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Slider(
                                value = speedDraft,
                                onValueChange = { speedDraft = it.coerceIn(0.25f, 1f) },
                                valueRange = 0.25f..1f,
                                steps = 14,
                                colors = SliderDefaults.colors(
                                    thumbColor = appColors.accent,
                                    activeTrackColor = appColors.accent
                                )
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            applySpeed(speedDraft)
                            showSpeedDialog = false
                        }) { Text("Aplicar") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSpeedDialog = false }) { Text("Cancelar") }
                    }
                )
            }

            // Diálogo de tempo manual (long-press no relógio)
            if (manualTimeTarget != null) {
                val (lineIdx, wordIdx) = manualTimeTarget!!
                AppAlertDialog(
                    onDismissRequest = { manualTimeTarget = null },
                    title = {
                        Text(
                            if (wordIdx == null) "Tempo da linha" else "Tempo da palavra",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    text = {
                        // Entrada textual mm:ss.cc
                        var timeText by remember(manualTimeMs) {
                            mutableStateOf(formatTime(manualTimeMs))
                        }
                        Column {
                            Text(
                                "Formato: mm:ss.cc  (ex. 0:09.63)",
                                color = MaterialTheme.colorScheme.onSurface.copy(0.55f),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            BasicTextField(
                                value = timeText,
                                onValueChange = { input ->
                                    timeText = input
                                    parseTimeToMs(input)?.let { manualTimeMs = it }
                                },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.onSurface.copy(0.08f))
                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(0.5f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = {
                                    manualTimeMs = editorPlayer.currentPosition
                                    timeText = formatTime(manualTimeMs)
                                }) {
                                    Text("Agora", color = MaterialTheme.colorScheme.primary)
                                }
                                TextButton(onClick = {
                                    manualTimeMs = (manualTimeMs - 100).coerceAtLeast(0L)
                                    timeText = formatTime(manualTimeMs)
                                }) {
                                    Text("-0.1s", color = MaterialTheme.colorScheme.onSurface)
                                }
                                TextButton(onClick = {
                                    manualTimeMs += 100
                                    timeText = formatTime(manualTimeMs)
                                }) {
                                    Text("+0.1s", color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val el = lines[lineIdx]
                            if (wordIdx == null) {
                                lines[lineIdx] = el.copy(timeMs = manualTimeMs)
                            } else {
                                val ensured = el.ensureWords()
                                val nw = ensured.words.toMutableList()
                                if (wordIdx in nw.indices) {
                                    nw[wordIdx] = nw[wordIdx].copy(timeMs = manualTimeMs)
                                    lines[lineIdx] = ensured.copy(words = nw).rebuildTextFromWords().autoAdjustLineTime()
                                }
                            }
                            manualTimeTarget = null
                        }) { Text("Aplicar", color = MaterialTheme.colorScheme.primary) }
                    },
                    dismissButton = {
                        TextButton(onClick = { manualTimeTarget = null }) {
                            Text("Cancelar", color = MaterialTheme.colorScheme.onSurface.copy(0.7f))
                        }
                    })
            }

            // Miniplayer: -10s / play / +10s
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(0.16f), Color.Black.copy(0.55f))
                        )
                    )
                    .border(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(Color.White.copy(0.35f), Color.White.copy(0.06f))
                        ),
                        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Slider(
                    value = (position.toFloat() / duration).coerceIn(0f, 1f),
                    onValueChange = {
                        val target = (it * duration).toLong()
                        editorPlayer.seekTo(target)
                        position = target
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = appColors.accent,
                        activeTrackColor = appColors.accent,
                        inactiveTrackColor = Color.White.copy(0.25f)
                    )
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatTime(position), fontSize = 11.sp, color = Color.White.copy(0.6f))
                    Text(formatTime(duration), fontSize = 11.sp, color = Color.White.copy(0.6f))
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (editorMode != EditorMode.SIMPLE) {
                        TextButton(
                            onClick = {
                                speedDraft = playbackSpeed
                                showSpeedDialog = true
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (playbackSpeed != 1f) appColors.accent.copy(0.18f) else Color.White.copy(0.10f))
                                .border(1.dp, Color.White.copy(0.22f), RoundedCornerShape(50))
                        ) {
                            Text(
                                "${(playbackSpeed * 100f).toInt()}%",
                                color = if (playbackSpeed != 1f) appColors.accent else Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    IconButton(onClick = { seekBy(-10_000L) }) {
                        Icon(
                            Icons.Rounded.Replay10,
                            contentDescription = "-10 segundos",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    IconButton(
                        onClick = {
                            if (previewIsPlaying) editorPlayer.pause() else {
                                if (editorPlayer.playbackState == Player.STATE_ENDED) editorPlayer.seekTo(0L)
                                editorPlayer.play()
                            }
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(appColors.accent)
                    ) {
                        Icon(
                            if (previewIsPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    IconButton(onClick = { seekBy(10_000L) }) {
                        Icon(
                            Icons.Rounded.Forward10,
                            contentDescription = "+10 segundos",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
        // Passo 1: escolher palavra (Linhas / Tradução via botão FX)
        if (fxWordPicker != null) {
            val picker = fxWordPicker!!
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { fxWordPicker = null }
            ) {
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF2A2A32), Color(0xFF16161C))
                            )
                        )
                        .border(
                            1.dp,
                            Brush.verticalGradient(
                                listOf(Color.White.copy(0.28f), Color.White.copy(0.06f))
                            ),
                            RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* consome */ }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .padding(bottom = 10.dp)
                ) {
                    Box(
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(0.28f))
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (picker.isTranslation) "Palavra da tradução" else "Palavra da linha",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Escolha em qual palavra aplicar o efeito",
                        color = Color.White.copy(0.45f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                    )
                    picker.words.chunked(3).forEach { row ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { (wordIndex, wordText) ->
                                Text(
                                    wordText,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(appColors.accent.copy(0.16f))
                                        .border(1.dp, appColors.accent.copy(0.4f), RoundedCornerShape(12.dp))
                                        .clickable {
                                            animationTarget = AnimationTarget(
                                                lineIndex = picker.lineIndex,
                                                wordIndex = wordIndex,
                                                isTranslation = picker.isTranslation
                                            )
                                            fxWordPicker = null
                                        }
                                        .padding(horizontal = 10.dp, vertical = 12.dp)
                                )
                            }
                            repeat(3 - row.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    TextButton(
                        onClick = { fxWordPicker = null },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Cancelar", color = Color.White.copy(0.65f))
                    }
                }
            }
        }

        // Passo 2b (tradução): sincronizar com palavra animada da letra (des1, des2…)
        if (fxSyncPicker != null) {
            val sync = fxSyncPicker!!
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { fxSyncPicker = null }
            ) {
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF2A2A32), Color(0xFF16161C))
                            )
                        )
                        .border(
                            1.dp,
                            Brush.verticalGradient(
                                listOf(Color.White.copy(0.28f), Color.White.copy(0.06f))
                            ),
                            RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .padding(bottom = 10.dp)
                ) {
                    Box(
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(0.28f))
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Sincronizar com a letra",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "A animação da tradução dispara no mesmo tempo da palavra escolhida",
                        color = Color.White.copy(0.45f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                    )
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        sync.anchors.forEach { anchor ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(appColors.accent.copy(0.14f))
                                    .border(1.dp, appColors.accent.copy(0.4f), RoundedCornerShape(12.dp))
                                    .clickable {
                                        applyAnimation(sync.target, sync.animation, syncTimeMs = anchor.timeMs)
                                        fxSyncPicker = null
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    anchor.label,
                                    color = appColors.accent,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(appColors.accent.copy(0.2f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    anchor.wordText,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    if (anchor.timeMs >= 0L) formatTime(anchor.timeMs) else "—",
                                    color = Color.White.copy(0.45f),
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        // Opção sem sync
                        Text(
                            "Sem sincronizar",
                            color = Color.White.copy(0.7f),
                            fontSize = 13.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(0.06f))
                                .clickable {
                                    applyAnimation(sync.target, sync.animation)
                                    fxSyncPicker = null
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        )
                    }
                    TextButton(
                        onClick = { fxSyncPicker = null },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Cancelar", color = Color.White.copy(0.65f))
                    }
                }
            }
        }

        // Passo 2: escolher efeito
        // Painel inferior com chips grandes — mais fácil de tocar do que AlertDialog.
        if (animationTarget != null) {
            val target = animationTarget!!
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { animationTarget = null }
            ) {
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF2A2A32), Color(0xFF16161C))
                            )
                        )
                        .border(
                            1.dp,
                            Brush.verticalGradient(
                                listOf(Color.White.copy(0.28f), Color.White.copy(0.06f))
                            ),
                            RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* consome clique para não fechar */ }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .padding(bottom = 10.dp)
                ) {
                    Box(
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(0.28f))
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Efeito da palavra",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "O marcador é colocado sempre após a palavra/sílaba",
                        color = Color.White.copy(0.45f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                    )
                    val options = listOf(
                        Triple(LyricAnimation.GLO, "Piscar", "{glo}"),
                        Triple(LyricAnimation.POP, "Agitar", "{pop}"),
                        Triple(LyricAnimation.DES, "Destacar", "{des}"),
                        Triple(LyricAnimation.DED, "Sumir", "{ded}"),
                        Triple(LyricAnimation.ECO, "Eco", "{eco}"),
                        Triple(LyricAnimation.ZOU, "Zoom out", "{zou}"),
                        Triple(LyricAnimation.ZIN, "Zoom in", "{zin}"),
                        Triple(LyricAnimation.SLI, "Deslizar", "{sli}"),
                        Triple(LyricAnimation.TYP, "Digitar", "{typ}"),
                        Triple(LyricAnimation.FLA, "Flash", "{fla}"),
                        Triple(LyricAnimation.VIB, "Tremor", "{vib}"),
                        Triple(LyricAnimation.RNB, "Arco-íris", "{rnb}"),
                        Triple(LyricAnimation.FRZ, "Congelar", "{frz}"),
                        Triple(LyricAnimation.SHD, "Sombra", "{shd}"),
                        Triple(LyricAnimation.WAV, "Onda", "{wav}"),
                        Triple(LyricAnimation.NONE, "Remover", "—")
                    )
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        options.chunked(2).forEach { row ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                row.forEach { (anim, title, tag) ->
                                    Column(
                                        Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(
                                                if (anim == LyricAnimation.NONE) Color.White.copy(0.06f)
                                                else appColors.accent.copy(0.16f)
                                            )
                                            .border(
                                                1.dp,
                                                if (anim == LyricAnimation.NONE) Color.White.copy(0.12f)
                                                else appColors.accent.copy(0.45f),
                                                RoundedCornerShape(14.dp)
                                            )
                                            .clickable {
                                                // Tradução: se a letra tem palavras com efeito, pede sincronização de tempo
                                                if (target.isTranslation && target.lineIndex != null) {
                                                    val li = target.lineIndex
                                                    val mainLine = lines.getOrNull(li)
                                                    val anchors = mainLine?.let { mainAnimatedAnchors(it) }.orEmpty()
                                                    if (anchors.isNotEmpty()) {
                                                        fxSyncPicker = FxSyncPicker(
                                                            target = target,
                                                            animation = anim,
                                                            anchors = anchors
                                                        )
                                                        animationTarget = null
                                                        return@clickable
                                                    }
                                                }
                                                applyAnimation(target, anim)
                                                animationTarget = null
                                            }
                                            .padding(horizontal = 12.dp, vertical = 12.dp)
                                    ) {
                                        Text(
                                            title,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            tag,
                                            color = if (anim == LyricAnimation.NONE) Color.White.copy(0.35f)
                                            else appColors.accent,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    TextButton(
                        onClick = { animationTarget = null },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Cancelar", color = Color.White.copy(0.65f))
                    }
                }
            }
        }


    }

}

@Composable
private fun ModeTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Color(0xFF4ADE80)
) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (selected) Modifier
                    .background(Color.Transparent)
                    .border(1.5.dp, accent, RoundedCornerShape(12.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) accent else Color.White.copy(0.45f),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 12.sp
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LineBox(
    line: EditableLine,
    formatTime: (Long) -> String,
    onTextChange: (String) -> Unit,
    onLongPressWord: (Int) -> Unit,
    onOpenFx: () -> Unit,
    onPlayLine: () -> Unit,
    onSync: () -> Unit,
    onLongSync: () -> Unit = {},
    onToggleBackground: () -> Unit,
    onDelete: () -> Unit,
    accent: Color
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(0.22f), Color.White.copy(0.08f))
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(Color.White.copy(0.55f), Color.White.copy(0.12f))
                ),
                RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        var textLayout by remember(line.id) { mutableStateOf<TextLayoutResult?>(null) }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(0.10f))
                .border(1.dp, Color.White.copy(0.22f), RoundedCornerShape(12.dp))
                .combinedClickable(onClick = onSync, onLongClick = onLongSync)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Icon(
                Icons.Rounded.AccessTime,
                contentDescription = "Sincronizar com o player",
                tint = if (line.timeMs >= 0) accent else Color.White.copy(0.55f),
                modifier = Modifier.size(20.dp)
            )
            Text(
                formatTime(line.timeMs),
                fontSize = 11.sp,
                color = if (line.timeMs >= 0) accent else Color.White.copy(0.45f),
                fontWeight = FontWeight.Medium
            )
        }
        IconButton(onClick = onPlayLine, enabled = line.timeMs >= 0, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = "Reproduzir a partir desta linha",
                tint = if (line.timeMs >= 0) accent else Color.White.copy(0.25f),
                modifier = Modifier.size(20.dp)
            )
        }
        Box(
            Modifier
                .padding(horizontal = 8.dp)
                .width(1.dp)
                .height(28.dp)
                .background(Color.White.copy(0.2f))
        )
        BasicTextField(
            value = line.text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            onTextLayout = { textLayout = it },
            textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
            cursorBrush = SolidColor(accent),
            singleLine = false,
            decorationBox = { inner ->
                if (line.text.isEmpty()) {
                    Text("Linha de letra…", color = Color.White.copy(0.35f), fontSize = 15.sp)
                }
                inner()
            }
        )
        Text(
            text = "BG",
            color = if (line.isBackground) Color.Black else Color.White.copy(0.65f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(if (line.isBackground) accent else Color.White.copy(0.10f))
                .clickable(onClick = onToggleBackground)
                .padding(horizontal = 7.dp, vertical = 6.dp)
        )
        Text(
            "FX",
            color = accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(accent.copy(0.18f))
                .clickable(onClick = onOpenFx)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Rounded.Delete,
                contentDescription = "Remover linha",
                tint = Color.White.copy(0.45f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WordLineBox(
    line: EditableLine,
    formatTime: (Long) -> String,
    onPlayLine: () -> Unit,
    onLineSync: (LayoutCoordinates?) -> Unit,
    onLongLineSync: () -> Unit = {},
    onWordSync: (Int, LayoutCoordinates?) -> Unit,
    onLongWordSync: (Int) -> Unit = {},
    /** Sincroniza só o endMs da última palavra da linha (posição atual do player). */
    onLastWordEndSync: () -> Unit = {},
    onWordTextChange: (Int, String) -> Unit,
    onToggleSyllable: (Int) -> Unit = {},
    onToggleBackground: () -> Unit,
    onOpenAnimationMenu: (Int) -> Unit,
    onAddWord: () -> Unit,
    onDeleteWord: (Int) -> Unit,
    onDeleteLine: () -> Unit,
    accent: Color
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(0.22f), Color.White.copy(0.08f))
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(Color.White.copy(0.55f), Color.White.copy(0.12f))
                ),
                RoundedCornerShape(16.dp)
            )
            .padding(10.dp)
    ) {
        // Cabeçalho da linha: tempo da linha + texto resumo + delete
        val headerCoords = remember { arrayOfNulls<LayoutCoordinates>(1) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.onGloballyPositioned { headerCoords[0] = it }
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(0.10f))
                    .border(1.dp, Color.White.copy(0.22f), RoundedCornerShape(12.dp))
                    .combinedClickable(
                        onClick = { onLineSync(headerCoords[0]) },
                        onLongClick = onLongLineSync
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Icon(
                    Icons.Rounded.AccessTime,
                    contentDescription = "Sincronizar linha",
                    tint = if (line.timeMs >= 0) accent else Color.White.copy(0.55f),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    formatTime(line.timeMs),
                    fontSize = 10.sp,
                    color = if (line.timeMs >= 0) accent else Color.White.copy(0.45f),
                    fontWeight = FontWeight.Medium
                )
            }
            IconButton(onClick = onPlayLine, enabled = line.timeMs >= 0, modifier = Modifier.size(30.dp)) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = "Reproduzir a partir desta linha",
                    tint = if (line.timeMs >= 0) accent else Color.White.copy(0.25f),
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                line.text.ifBlank { "Linha" },
                color = Color.White.copy(0.75f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "BG",
                color = if (line.isBackground) Color.Black else Color.White.copy(0.65f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (line.isBackground) accent else Color.White.copy(0.10f))
                    .clickable(onClick = onToggleBackground)
                    .padding(horizontal = 7.dp, vertical = 6.dp)
            )
            IconButton(onClick = onDeleteLine, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "Remover linha",
                    tint = Color.White.copy(0.4f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        // Palavras
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            line.words.forEachIndexed { wi, word ->
                val rowCoords = remember(word.id) { arrayOfNulls<LayoutCoordinates>(1) }
                Row(
                    Modifier
                        .onGloballyPositioned { rowCoords[0] = it }
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(0.08f))
                        .border(1.dp, Color.White.copy(0.15f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(0.08f))
                            .combinedClickable(
                                onClick = { onWordSync(wi, rowCoords[0]) },
                                onLongClick = { onLongWordSync(wi) }
                            )
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Rounded.AccessTime,
                            contentDescription = "Sincronizar palavra",
                            tint = if (word.timeMs >= 0) accent else Color.White.copy(0.45f),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            formatTime(word.timeMs),
                            fontSize = 9.sp,
                            color = if (word.timeMs >= 0) accent else Color.White.copy(0.4f)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = word.text,
                        onValueChange = { onWordTextChange(wi, it) },
                        modifier = Modifier
                            .weight(1f)
                            .pointerInput(word.id) {
                                detectTapGestures(onLongPress = { onOpenAnimationMenu(wi) })
                            },
                        textStyle = TextStyle(
                            color = if (word.isSyllable) accent.copy(alpha = 0.95f) else Color.White,
                            fontSize = 14.sp,
                            fontWeight = if (word.isSyllable) FontWeight.Bold else FontWeight.Normal
                        ),
                        cursorBrush = SolidColor(accent),
                        singleLine = true,
                        decorationBox = { inner ->
                            if (word.text.isEmpty()) {
                                Text("palavra…", color = Color.White.copy(0.3f), fontSize = 14.sp)
                            }
                            inner()
                        }
                    )
                    Text(
                        when (word.animation) {
                            LyricAnimation.GLO -> "glo"
                            LyricAnimation.POP -> "pop"
                            LyricAnimation.DES -> "des"
                            LyricAnimation.DED -> "ded"
                            LyricAnimation.ECO -> "eco"
                            LyricAnimation.ZOU -> "zou"
                            LyricAnimation.ZIN -> "zin"
                            LyricAnimation.SLI -> "sli"
                            LyricAnimation.TYP -> "typ"
                            LyricAnimation.FLA -> "fla"
                            LyricAnimation.VIB -> "vib"
                            LyricAnimation.RNB -> "rnb"
                            LyricAnimation.FRZ -> "frz"
                            LyricAnimation.SHD -> "shd"
                            LyricAnimation.WAV -> "wav"
                            LyricAnimation.NONE -> "FX"
                        },
                        color = if (word.animation == LyricAnimation.NONE) Color.White.copy(0.45f) else accent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(if (word.animation == LyricAnimation.NONE) 0.08f else 0.16f))
                            .clickable { onOpenAnimationMenu(wi) }
                            .padding(horizontal = 6.dp, vertical = 5.dp)
                    )
                    // Toggle sílaba ({sil})
                    Text(
                        if (word.isSyllable) "síl" else "pal",
                        color = if (word.isSyllable) accent else Color.White.copy(0.45f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (word.isSyllable) accent.copy(0.2f) else Color.White.copy(0.08f)
                            )
                            .clickable { onToggleSyllable(wi) }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                    // Só na última palavra: botão para marcar o endMs (fim do fill do karaokê)
                    if (wi == line.words.lastIndex) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (word.endMs > word.timeMs && word.timeMs >= 0)
                                        accent.copy(alpha = 0.18f)
                                    else Color.White.copy(0.08f)
                                )
                                .clickable(onClick = onLastWordEndSync)
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "fim",
                                color = if (word.endMs > word.timeMs && word.timeMs >= 0) accent else Color.White.copy(0.55f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                formatTime(word.endMs),
                                fontSize = 9.sp,
                                color = if (word.endMs > word.timeMs && word.timeMs >= 0) accent else Color.White.copy(0.4f)
                            )
                        }
                    }
                    IconButton(onClick = { onDeleteWord(wi) }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Remover palavra",
                            tint = Color.White.copy(0.35f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
            Text(
                "+ palavra",
                color = accent.copy(alpha = 0.9f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clickable(onClick = onAddWord)
                    .padding(vertical = 4.dp)
            )
        }
    }
}

private enum class EditorMode { SIMPLE, LINE, WORD, TRANSLATION }

private data class AnimationTarget(
    val lineIndex: Int? = null,
    val wordIndex: Int? = null,
    val isTranslation: Boolean = false,
    val simpleStart: Int? = null,
    val simpleEnd: Int? = null
)

/** Passo 1 do botão FX em Linhas/Tradução. */
private data class FxWordPicker(
    val lineIndex: Int,
    val isTranslation: Boolean,
    val words: List<Pair<Int, String>>
)

/** Sincronizar tempo da palavra da tradução com âncora da letra (des1, des2…). */
private data class FxSyncPicker(
    val target: AnimationTarget,
    val animation: LyricAnimation,
    val anchors: List<FxSyncAnchor>
)

private data class FxSyncAnchor(
    val label: String,
    val timeMs: Long,
    val wordText: String
)

/** Lista âncoras da letra principal: des1, des2, glo1… com o texto da palavra. */
private fun mainAnimatedAnchors(line: EditableLine): List<FxSyncAnchor> {
    val counts = mutableMapOf<LyricAnimation, Int>()
    val out = mutableListOf<FxSyncAnchor>()
    line.words.forEach { w ->
        if (w.animation == LyricAnimation.NONE || w.text.isBlank()) return@forEach
        val n = (counts[w.animation] ?: 0) + 1
        counts[w.animation] = n
        val tag = animationMarker(w.animation).removePrefix("{").removeSuffix("}").ifBlank { "fx" }
        val time = if (w.timeMs >= 0L) w.timeMs else line.timeMs
        out += FxSyncAnchor(
            label = "$tag$n",
            timeMs = time,
            wordText = w.text
        )
    }
    return out
}

private fun animationMarker(animation: LyricAnimation): String = when (animation) {
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

private fun wordsWithMarkers(words: List<EditableWord>, fallback: String): String {
    if (words.none { it.isSyllable || it.animation != LyricAnimation.NONE }) return fallback
    return buildString {
        words.forEachIndexed { index, word ->
            if (index > 0 && !word.isSyllable) append(' ')
            if (word.isSyllable) append("{sil}")
            append(word.text)
            append(animationMarker(word.animation))
        }
    }
}

private fun rebuildTextFromWords(words: List<EditableWord>): String = buildString {
    words.forEachIndexed { index, word ->
        if (index > 0 && !word.isSyllable) append(' ')
        append(word.text)
    }
}

private fun wordRangeAt(text: String, offset: Int): Pair<Int, Int>? {
    if (text.isEmpty()) return null
    var index = offset.coerceIn(0, text.lastIndex)
    fun isDelimiter(c: Char) = c.isWhitespace() || c == '{' || c == '}'
    if (isDelimiter(text[index])) return null
    var start = index
    while (start > 0 && !isDelimiter(text[start - 1])) start--
    var end = index + 1
    while (end < text.length && !isDelimiter(text[end])) end++
    // Ignore tag bodies like {glo}; only actual lyric characters open the chooser.
    if (start > 0 && text[start - 1] == '{') return null
    return start to end
}

private fun editableWordIndexAt(words: List<EditableWord>, offset: Int): Int {
    var cursor = 0
    words.forEachIndexed { index, word ->
        if (index > 0 && !word.isSyllable) cursor++
        val end = cursor + word.text.length
        if (offset >= cursor && offset < end) return index
        cursor = end
    }
    return -1
}

private fun expandEditableWords(words: List<EditableWord>): List<EditableWord> = words.flatMap { word ->
    val parts = word.text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (parts.size <= 1) listOf(word)
    else parts.mapIndexed { index, text ->
        word.copy(
            timeMs = if (index == 0) word.timeMs else -1L,
            text = text,
            isSyllable = word.isSyllable && index == 0,
            animation = if (index == parts.lastIndex) word.animation else LyricAnimation.NONE,
            id = if (index == 0) word.id else System.nanoTime() + index
        )
    }
}

private fun addAnimationMarker(text: String, startOffset: Int, endOffset: Int, animation: LyricAnimation): String {
    val start = startOffset.coerceIn(0, text.length)
    val end = endOffset.coerceIn(start, text.length)
    val markerRegex = Regex("\\{(?:glo|pop|des|ded|eco|zou|zin|sli|typ|fla|vib|rnb|frz|shd|wav)\\}", RegexOption.IGNORE_CASE)
    var suffixEnd = end
    while (true) {
        val existing = markerRegex.find(text, suffixEnd)?.takeIf { it.range.first == suffixEnd } ?: break
        suffixEnd = existing.range.last + 1
    }
    return text.substring(0, start) + text.substring(start, end) + animationMarker(animation) + text.substring(suffixEnd)
}

private data class EditableWord(
    val timeMs: Long = -1L,
    val text: String,
    val isSyllable: Boolean = false,
    val animation: LyricAnimation = LyricAnimation.NONE,
    /** Fim explícito da palavra (ms). Usado sobretudo na última palavra da linha. -1 = desconhecido. */
    val endMs: Long = -1L,
    val id: Long = System.nanoTime()
)

private data class EditableLine(
    val timeMs: Long,
    val text: String,
    val words: List<EditableWord> = emptyList(),
    val isTranslation: Boolean = false,
    val isBackground: Boolean = false,
    val translationText: String = "",
    val translationWords: List<EditableWord> = emptyList(),
    val id: Long = System.nanoTime()
) {
    fun ensureWords(): EditableLine {
        if (words.isNotEmpty()) return this
        val parts = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return this
        return copy(words = parts.map { EditableWord(timeMs = -1L, text = it) })
    }

    fun rebuildTextFromWords(): EditableLine {
        if (words.isEmpty()) return this
        // Sílabas colam na palavra anterior sem espaço extra
        val sb = StringBuilder()
        words.forEachIndexed { i, w ->
            if (i > 0 && !w.isSyllable) sb.append(' ')
            sb.append(w.text)
        }
        return copy(text = sb.toString())
    }

    /**
     * Só puxa o tempo da linha para o da 1ª palavra quando a linha está
     * *depois* da 1ª palavra (timeMs da linha > timeMs da palavra).
     * Assim o usuário pode manter um início de linha anterior ao da 1ª palavra
     * (ex.: intro instrumental) sem ser sobrescrito ao sincronizar a palavra.
     */
    fun autoAdjustLineTime(): EditableLine {
        val firstWordTime = words.firstOrNull { it.timeMs >= 0 }?.timeMs ?: return this
        if (timeMs > firstWordTime) {
            return copy(timeMs = firstWordTime)
        }
        return this
    }
}

/** Converte linhas em texto LRC / eLRC (com tags de palavra quando houver). */
fun lyricLinesToTagText(lines: List<LyricLine>): String {
    if (lines.isEmpty()) return ""
    // Mesmo serializador do salvamento direto (atalho do menu), para os dois caminhos
    // gerarem exatamente o mesmo texto ({tr}, sem <00:00.00> espúrio, {sil} na ordem certa).
    return LyricsEmbedder.linesToLrcText(lines).trimEnd()
}
