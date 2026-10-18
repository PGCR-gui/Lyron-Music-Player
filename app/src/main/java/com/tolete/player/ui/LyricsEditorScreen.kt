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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
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
    onSave: (List<LyricLine>) -> Unit,
    onClose: () -> Unit,
    appColors: AppColors
) {
    val lines = remember {
        mutableStateListOf<EditableLine>().apply {
            if (initialLines.isEmpty()) {
                add(EditableLine(timeMs = -1L, text = ""))
            } else {
                initialLines.forEach { add(EditableLine(it.timeMs, it.text)) }
            }
        }
    }
    var position by remember { mutableLongStateOf(getPosition()) }
    // false = Sincronizado, true = Simples
    var simpleMode by remember {
        mutableStateOf(initialLines.isEmpty() || initialLines.all { it.timeMs < 0 })
    }
    var simpleText by remember {
        mutableStateOf(
            if (initialLines.isEmpty()) ""
            else initialLines.joinToString("\n") { it.text }
        )
    }
    val duration = track.duration.coerceAtLeast(1L)

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

    fun switchToSynced() {
        // Cada linha não vazia do texto simples vira uma caixa
        val parts = simpleText
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lines()
            .map { it.trimEnd() }
        lines.clear()
        if (parts.all { it.isBlank() }) {
            lines.add(EditableLine(timeMs = -1L, text = ""))
        } else {
            parts.forEach { line ->
                // Mantém linhas em branco como separadores visuais opcionais? Não — só não vazias
                if (line.isNotBlank()) {
                    lines.add(EditableLine(timeMs = -1L, text = line))
                }
            }
            if (lines.isEmpty()) lines.add(EditableLine(timeMs = -1L, text = ""))
        }
        simpleMode = false
    }

    fun switchToSimple() {
        simpleText = lines.joinToString("\n") { it.text }
        simpleMode = true
    }

    fun seekBy(deltaMs: Long) {
        val next = (getPosition() + deltaMs).coerceIn(0L, duration)
        onSeek(next)
        position = next
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
                        label = "Simples",
                        selected = simpleMode,
                        onClick = { if (!simpleMode) switchToSimple() },
                        modifier = Modifier.weight(1f)
                    )
                    ModeTab(
                        label = "Sincronizado",
                        selected = !simpleMode,
                        onClick = { if (simpleMode) switchToSynced() },
                        modifier = Modifier.weight(1f),
                        accent = appColors.accent
                    )
                }
                Spacer(Modifier.width(12.dp))
                IconButton(
                    onClick = {
                        val out = if (simpleMode) {
                            simpleText
                                .replace("\r\n", "\n")
                                .replace('\r', '\n')
                                .lines()
                                .map { it.trimEnd() }
                                .filter { it.isNotBlank() }
                                .map { LyricLine(-1L, it) }
                        } else {
                            lines
                                .filter { it.text.isNotBlank() }
                                .map { LyricLine(it.timeMs, it.text.trim()) }
                        }
                        onSave(out)
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

            // Offset global de todas as letras (apenas modo sincronizado)
            if (!simpleMode) {
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
                                val t = lines[i].timeMs
                                if (t >= 0) {
                                    lines[i] = lines[i].copy(timeMs = (t + delta).coerceAtLeast(0L))
                                }
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
                        "Aplica a todas as linhas sincronizadas",
                        color = Color.White.copy(0.4f),
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (simpleMode) {
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
                                    "Cole ou escreva a letra aqui…\nCada linha (Enter) vira uma caixa no modo sincronizado.",
                                    color = Color.White.copy(0.35f),
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp
                                )
                            }
                            inner()
                        }
                    )
                }
            } else {
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
                            onTextChange = { lines[index] = line.copy(text = it) },
                            onSync = {
                                val t = getPosition()
                                lines[index] = line.copy(timeMs = t)
                            },
                            onDelete = {
                                if (lines.size > 1) lines.removeAt(index)
                                else lines[index] = line.copy(timeMs = -1L, text = "")
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
                    IconButton(onClick = { seekBy(-10_000L) }) {
                        Icon(
                            Icons.Rounded.Replay10,
                            contentDescription = "-10 segundos",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
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
                    Spacer(Modifier.width(12.dp))
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
            fontSize = 14.sp
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

private data class EditableLine(
    val timeMs: Long,
    val text: String,
    val id: Long = System.nanoTime()
)

/** Converte linhas em texto LRC (ou texto simples se sem timestamps). */
fun lyricLinesToTagText(lines: List<LyricLine>): String {
    if (lines.isEmpty()) return ""
    val allUnsynced = lines.all { it.timeMs < 0 }
    if (allUnsynced) return lines.joinToString("\n") { it.text }
    return buildString {
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
    }.trimEnd()
}
