package com.tolete.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tolete.player.data.LyricLine
import com.tolete.player.data.LyricWord
import com.tolete.player.data.LyricsEmbedder
import com.tolete.player.data.Track
import java.io.File
import kotlinx.coroutines.delay

/**
 * Editor de sincronização de letras (tags embutidas):
 * - Modo simples: uma única caixa de texto grande (linhas separadas por Enter)
 * - Modo sincronizado: cada linha em caixa liquid glass + botão de relógio
 * - Ao mudar de simples → sincronizado, cada linha do texto vira uma caixa
 * - Miniplayer com -10s / play / +10s
 */
@Composable
fun LyricsEditorScreen(
    track: Track,
    initialLines: List<LyricLine>,
    isPlaying: Boolean,
    getPosition: () -> Long,
    onSeek: (Long) -> Unit,
    onToggle: () -> Unit,
    onSave: (List<LyricLine>, LyricsEmbedder.Target) -> Unit,
    onClose: () -> Unit,
    appColors: AppColors,
    initialTarget: LyricsEmbedder.Target = LyricsEmbedder.Target.LRC_FILE,
    canWriteLrc: Boolean = true,
    getPlaybackSpeed: () -> Float = { 1f },
    onPlaybackSpeed: (Float) -> Unit = {}
) {
    val lines = remember {
        mutableStateListOf<EditableLine>().apply {
            if (initialLines.isEmpty()) {
                add(EditableLine(timeMs = -1L, text = ""))
            } else {
                initialLines.forEach { src ->
                    val words = src.words.map { EditableWord(timeMs = it.timeMs, text = it.text) }
                    add(EditableLine(timeMs = src.timeMs, text = src.text, words = words))
                }
            }
        }
    }
    var position by remember { mutableLongStateOf(getPosition()) }
    var editorMode by remember {
        mutableStateOf(
            when {
                initialLines.any { it.hasSyllableSync } -> EditorMode.WORD
                initialLines.isNotEmpty() && initialLines.any { it.timeMs >= 0 } -> EditorMode.LINE
                else -> EditorMode.SIMPLE
            }
        )
    }
    var simpleText by remember {
        mutableStateOf(
            if (initialLines.isEmpty()) ""
            else initialLines.joinToString("\n") { it.text }
        )
    }
    var showSaveTarget by remember { mutableStateOf(false) }
    var pendingSaveLines by remember { mutableStateOf<List<LyricLine>?>(null) }
    var preferredTarget by remember { mutableStateOf(initialTarget) }
    val duration = track.duration.coerceAtLeast(1L)
    // Velocidades úteis para sincronizar (cicla no botão)
    val speedSteps = remember { listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f) }
    var playbackSpeed by remember { mutableStateOf(getPlaybackSpeed().let { cur ->
        speedSteps.minByOrNull { kotlin.math.abs(it - cur) } ?: 1f
    }) }

    LaunchedEffect(isPlaying) {
        while (true) {
            position = getPosition()
            if (!isPlaying) break
            delay(50)
        }
    }

    fun formatTime(ms: Long): String {
        if (ms < 0) return "—:——.—"
        val totalSec = ms / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        val frac = (ms % 1000) / 10
        return "%d:%02d.%02d".format(m, s, frac)
    }

    /** Aplica o texto simples em `lines` preservando timestamps quando o texto da linha não muda. */
    fun applySimpleTextToLines(text: String) {
        val parts = text
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lines()
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }
        val old = lines.toList()
        lines.clear()
        if (parts.isEmpty()) {
            lines.add(EditableLine(timeMs = -1L, text = ""))
            return
        }
        parts.forEachIndexed { i, part ->
            val prev = old.getOrNull(i)
            if (prev != null && prev.text == part) {
                // Mesmo texto na mesma posição: mantém timestamps de linha e palavras
                lines.add(prev)
            } else if (prev != null && prev.text.trim() == part.trim()) {
                lines.add(prev.copy(text = part))
            } else {
                // Tenta achar por texto idêntico em outra posição
                val match = old.firstOrNull { it.text == part }
                if (match != null) {
                    lines.add(match.copy(id = System.nanoTime()))
                } else {
                    lines.add(EditableLine(timeMs = -1L, text = part))
                }
            }
        }
    }

    fun switchToMode(target: EditorMode) {
        if (target == editorMode) return
        when (editorMode) {
            EditorMode.SIMPLE -> {
                // Texto → estrutura: mescla com timestamps existentes
                applySimpleTextToLines(simpleText)
            }
            EditorMode.LINE, EditorMode.WORD -> {
                // Garante que o texto simples reflita as linhas atuais
                simpleText = lines.joinToString("\n") { it.text }
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
                simpleText = lines.joinToString("\n") { it.text }
            }
        }
        editorMode = target
    }

    fun seekBy(deltaMs: Long) {
        val next = (getPosition() + deltaMs).coerceIn(0L, duration)
        onSeek(next)
        position = next
    }

    fun applySpeed(next: Float) {
        playbackSpeed = next
        // Callback do ViewModel (MediaController + serviço)
        onPlaybackSpeed(next)
        // Caminho direto no ExoPlayer (mesmo processo) — não depende do wiring
        com.tolete.player.PlaybackService.setSpeed(next)
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
                }
                Spacer(Modifier.width(12.dp))
                IconButton(
                    onClick = {
                        // Sempre sincroniza o estado a partir do modo atual antes de salvar
                        if (editorMode == EditorMode.SIMPLE) {
                            applySimpleTextToLines(simpleText)
                        }
                        val out = lines
                            .filter { it.text.isNotBlank() || it.words.any { w -> w.text.isNotBlank() } }
                            .map { el ->
                                val words = el.words
                                    .filter { it.text.isNotBlank() }
                                    .map { LyricWord(timeMs = it.timeMs, text = it.text) }
                                val text = el.text.trim().ifBlank {
                                    words.joinToString(" ") { it.text }
                                }
                                LyricLine(
                                    timeMs = el.timeMs,
                                    text = text,
                                    words = words
                                )
                            }
                        pendingSaveLines = out
                        showSaveTarget = true
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
                // Uma única caixa grande de texto
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
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
                        .padding(14.dp)
                ) {
                    BasicTextField(
                        value = simpleText,
                        onValueChange = { simpleText = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp, lineHeight = 24.sp),
                        cursorBrush = SolidColor(appColors.accent),
                        decorationBox = { inner ->
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
                    )
                }
                }
                EditorMode.LINE -> {
                LazyColumn(
                    Modifier
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
                                // Ao editar texto da linha, invalida palavras se o texto mudou bastante
                                val updated = line.copy(text = newText)
                                lines[index] = if (line.words.isNotEmpty() &&
                                    line.words.joinToString(" ") { it.text } != newText.trim()
                                ) {
                                    updated.copy(words = emptyList())
                                } else updated
                            },
                            onSync = {
                                val t = getPosition()
                                lines[index] = line.copy(timeMs = t)
                            },
                            onDelete = {
                                if (lines.size > 1) lines.removeAt(index)
                                else lines[index] = line.copy(timeMs = -1L, text = "", words = emptyList())
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
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
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
                            onLineSync = {
                                val t = getPosition()
                                lines[index] = lines[index].copy(timeMs = t)
                            },
                            onWordSync = { wordIndex ->
                                val t = getPosition()
                                val el = lines[index].ensureWords()
                                val newWords = el.words.toMutableList()
                                if (wordIndex in newWords.indices) {
                                    newWords[wordIndex] = newWords[wordIndex].copy(timeMs = t)
                                    val lineTime = if (el.timeMs < 0 && wordIndex == 0) t else el.timeMs
                                    lines[index] = el.copy(timeMs = lineTime, words = newWords)
                                        .rebuildTextFromWords()
                                }
                            },
                            onWordTextChange = { wordIndex, newText ->
                                val el = lines[index].ensureWords()
                                val newWords = el.words.toMutableList()
                                if (wordIndex in newWords.indices) {
                                    newWords[wordIndex] = newWords[wordIndex].copy(text = newText)
                                    lines[index] = el.copy(words = newWords).rebuildTextFromWords()
                                }
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
            } // when

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
                    onValueChange = { onSeek((it * duration).toLong()) },
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
                    // Diminuir velocidade (Linhas / Palavras)
                    if (editorMode != EditorMode.SIMPLE) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(0.12f))
                                .border(1.dp, Color.White.copy(0.22f), CircleShape)
                                .clickable {
                                    val idx = speedSteps.indexOf(playbackSpeed).let {
                                        if (it < 0) speedSteps.indexOf(1f) else it
                                    }
                                    applySpeed(speedSteps[(idx - 1).coerceAtLeast(0)])
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("−", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
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
                        onClick = onToggle,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(appColors.accent)
                    ) {
                        Icon(
                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
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
                    // Aumentar velocidade + indicador (Linhas / Palavras)
                    if (editorMode != EditorMode.SIMPLE) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(0.12f))
                                .border(1.dp, Color.White.copy(0.22f), CircleShape)
                                .clickable {
                                    val idx = speedSteps.indexOf(playbackSpeed).let {
                                        if (it < 0) speedSteps.indexOf(1f) else it
                                    }
                                    applySpeed(speedSteps[(idx + 1).coerceAtMost(speedSteps.lastIndex)])
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = when {
                                playbackSpeed == 1f -> "1x"
                                playbackSpeed == 0.5f -> "0.5x"
                                playbackSpeed == 0.75f -> "0.75x"
                                playbackSpeed == 1.25f -> "1.25x"
                                playbackSpeed == 1.5f -> "1.5x"
                                else -> "${"%.2f".format(playbackSpeed)}x"
                            },
                            color = if (playbackSpeed != 1f) appColors.accent else Color.White.copy(0.75f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (playbackSpeed != 1f) appColors.accent.copy(0.18f)
                                    else Color.White.copy(0.08f)
                                )
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }

    if (showSaveTarget && pendingSaveLines != null) {
        AlertDialog(
            onDismissRequest = {
                showSaveTarget = false
                pendingSaveLines = null
            },
            title = { Text("Salvar letra") },
            text = {
                Column {
                    Text(
                        "Escolha onde gravar a letra:",
                        fontSize = 14.sp,
                        color = Color.White.copy(0.85f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "• Arquivo .lrc — ao lado da música (recomendado para letras sincronizadas)",
                        fontSize = 13.sp,
                        color = Color.White.copy(0.7f)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "• Tags embutidas — dentro do arquivo de áudio",
                        fontSize = 13.sp,
                        color = Color.White.copy(0.7f)
                    )
                    if (!canWriteLrc) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "A pasta da música pode não permitir gravar .lrc; nesse caso use tags embutidas ou o cache do app.",
                            fontSize = 12.sp,
                            color = Color(0xFFFFAB91)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val out = pendingSaveLines ?: return@TextButton
                    preferredTarget = LyricsEmbedder.Target.LRC_FILE
                    showSaveTarget = false
                    pendingSaveLines = null
                    onSave(out, LyricsEmbedder.Target.LRC_FILE)
                }) { Text("Arquivo .lrc") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        val out = pendingSaveLines ?: return@TextButton
                        preferredTarget = LyricsEmbedder.Target.EMBEDDED
                        showSaveTarget = false
                        pendingSaveLines = null
                        onSave(out, LyricsEmbedder.Target.EMBEDDED)
                    }) { Text("Tags embutidas") }
                    TextButton(onClick = {
                        showSaveTarget = false
                        pendingSaveLines = null
                    }) { Text("Cancelar") }
                }
            }
        )
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

@Composable
private fun LineBox(
    line: EditableLine,
    formatTime: (Long) -> String,
    onTextChange: (String) -> Unit,
    onSync: () -> Unit,
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(0.10f))
                .border(1.dp, Color.White.copy(0.22f), RoundedCornerShape(12.dp))
                .clickable(onClick = onSync)
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


@Composable
private fun WordLineBox(
    line: EditableLine,
    formatTime: (Long) -> String,
    onLineSync: () -> Unit,
    onWordSync: (Int) -> Unit,
    onWordTextChange: (Int, String) -> Unit,
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(0.10f))
                    .border(1.dp, Color.White.copy(0.22f), RoundedCornerShape(12.dp))
                    .clickable(onClick = onLineSync)
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
            Spacer(Modifier.width(10.dp))
            Text(
                line.text.ifBlank { "Linha" },
                color = Color.White.copy(0.75f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                modifier = Modifier.weight(1f)
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
                Row(
                    Modifier
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
                            .clickable { onWordSync(wi) }
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
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                        cursorBrush = SolidColor(accent),
                        singleLine = true,
                        decorationBox = { inner ->
                            if (word.text.isEmpty()) {
                                Text("palavra…", color = Color.White.copy(0.3f), fontSize = 14.sp)
                            }
                            inner()
                        }
                    )
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

private enum class EditorMode { SIMPLE, LINE, WORD }

private data class EditableWord(
    val timeMs: Long = -1L,
    val text: String,
    val id: Long = System.nanoTime()
)

private data class EditableLine(
    val timeMs: Long,
    val text: String,
    val words: List<EditableWord> = emptyList(),
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
        return copy(text = words.joinToString(" ") { it.text })
    }
}

/** Converte linhas em texto LRC / eLRC (com tags de palavra quando houver). */
fun lyricLinesToTagText(lines: List<LyricLine>): String {
    if (lines.isEmpty()) return ""
    val allUnsynced = lines.all { it.timeMs < 0 && it.words.all { w -> w.timeMs < 0 } }
    if (allUnsynced) return lines.joinToString("\n") { it.text }
    fun fmt(ms: Long): String {
        val m = ms / 60_000
        val s = (ms % 60_000) / 1000
        val cs = (ms % 1000) / 10
        return "%02d:%02d.%02d".format(m, s, cs)
    }
    return buildString {
        lines.forEach { line ->
            if (line.hasSyllableSync && line.words.any { it.timeMs >= 0 }) {
                val lineStart = if (line.timeMs >= 0) line.timeMs else line.words.first { it.timeMs >= 0 }.timeMs
                append("[%s]".format(fmt(lineStart)))
                line.words.forEach { w ->
                    if (w.timeMs >= 0) append("<%s>".format(fmt(w.timeMs)))
                    append(w.text)
                }
                appendLine()
            } else if (line.timeMs < 0) {
                appendLine(line.text)
            } else {
                appendLine("[%s]%s".format(fmt(line.timeMs), line.text))
            }
        }
    }.trimEnd()
}
