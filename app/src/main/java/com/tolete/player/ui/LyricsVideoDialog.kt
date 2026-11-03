package com.tolete.player.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tolete.player.data.Track
import com.tolete.player.video.LyricsVideoGenerator
import com.tolete.player.video.LyricsVideoGenerator.Aspect

private val Accent = Color(0xFFB388FF)
private const val MIN_CLIP_MS = 1000f

private fun fmtTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LyricsVideoDialog(
    track: Track,
    currentPositionMs: Long,
    onDismiss: () -> Unit,
    onGenerate: (startMs: Long, endMs: Long, shortSide: Int, aspect: Aspect, fps: Int) -> Unit
) {
    val duration = track.duration.coerceAtLeast(2000L)
    val durF = duration.toFloat()

    var whole by remember { mutableStateOf(true) }
    var range by remember { mutableStateOf(0f..durF) }
    var aspect by remember { mutableStateOf(Aspect.V_9_16) }
    var fps by remember { mutableIntStateOf(30) }
    var chosenSide by remember { mutableIntStateOf(1080) }

    val sides = remember(aspect, fps) { LyricsVideoGenerator.supportedShortSides(aspect, fps) }
    val side = when {
        chosenSide in sides -> chosenSide
        1080 in sides -> 1080
        else -> sides.last()
    }

    val startMs = if (whole) 0L else range.start.toLong()
    val endMs = if (whole) duration else range.endInclusive.toLong()
    val (vw, vh) = LyricsVideoGenerator.dimsFor(side, aspect)

    fun setStart(ms: Long) {
        val maxStart = (range.endInclusive - MIN_CLIP_MS).coerceAtLeast(0f)
        range = ms.toFloat().coerceIn(0f, maxStart)..range.endInclusive
    }

    fun setEnd(ms: Long) {
        val minEnd = (range.start + MIN_CLIP_MS).coerceAtMost(durF)
        range = range.start..ms.toFloat().coerceIn(minEnd, durF)
    }

    val chipColors = FilterChipDefaults.filterChipColors(
        containerColor = Color.White.copy(alpha = 0.06f),
        labelColor = Color.White.copy(alpha = 0.85f),
        selectedContainerColor = Accent.copy(alpha = 0.35f),
        selectedLabelColor = Color.White
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A24),
        title = { Text("Gerar vídeo com letra", color = Color.White) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Trecho", color = Color.White.copy(0.6f), fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = whole,
                        onClick = { whole = true },
                        label = { Text("Música inteira") },
                        colors = chipColors
                    )
                    FilterChip(
                        selected = !whole,
                        onClick = { whole = false },
                        label = { Text("Escolher trecho") },
                        colors = chipColors
                    )
                }

                if (!whole) {
                    Spacer(Modifier.height(8.dp))
                    RangeSlider(
                        value = range,
                        onValueChange = { r ->
                            val s = r.start
                            val e = r.endInclusive
                            range = when {
                                e - s >= MIN_CLIP_MS -> s..e
                                s != range.start -> (e - MIN_CLIP_MS).coerceAtLeast(0f)..e
                                else -> s..(s + MIN_CLIP_MS).coerceAtMost(durF)
                            }
                        },
                        valueRange = 0f..durF,
                        colors = SliderDefaults.colors(
                            thumbColor = Accent,
                            activeTrackColor = Accent,
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                        )
                    )
                    TimeAdjustRow(
                        label = "Início",
                        valueMs = range.start.toLong(),
                        onChange = ::setStart,
                        onNow = { setStart(currentPositionMs) }
                    )
                    TimeAdjustRow(
                        label = "Fim",
                        valueMs = range.endInclusive.toLong(),
                        onChange = ::setEnd,
                        onNow = { setEnd(currentPositionMs) }
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text("Proporção", color = Color.White.copy(0.6f), fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Aspect.values().forEach { a ->
                        FilterChip(
                            selected = aspect == a,
                            onClick = { aspect = a },
                            label = { Text(a.label) },
                            colors = chipColors
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text("Taxa de quadros", color = Color.White.copy(0.6f), fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LyricsVideoGenerator.FPS_OPTIONS.forEach { f ->
                        FilterChip(
                            selected = fps == f,
                            onClick = { fps = f },
                            label = { Text("$f fps") },
                            colors = chipColors
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text("Resolução", color = Color.White.copy(0.6f), fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sides.forEach { s ->
                        FilterChip(
                            selected = s == side,
                            onClick = { chosenSide = s },
                            label = { Text(if (s == 1440) "1440p (2K)" else "${s}p") },
                            colors = chipColors
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    "Saída: ${vw}×${vh} · $fps fps · MP4 (H.264 + AAC) · " +
                        fmtTime(endMs - startMs),
                    color = Color.White.copy(0.7f),
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Resoluções maiores e 60 fps demoram mais para gerar. O progresso aparece na notificação. A letra usa a fonte e as cores do Tocando agora.",
                    color = Color.White.copy(0.45f),
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onGenerate(startMs, endMs, side, aspect, fps) }) {
                Text("Gerar", color = Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color.White.copy(0.7f))
            }
        }
    )
}

@Composable
private fun TimeAdjustRow(
    label: String,
    valueMs: Long,
    onChange: (Long) -> Unit,
    onNow: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White.copy(0.85f), fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp))
        Text(fmtTime(valueMs), color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = { onChange(valueMs - 1000L) }) { Text("−1s", color = Accent) }
        TextButton(onClick = { onChange(valueMs + 1000L) }) { Text("+1s", color = Accent) }
        TextButton(onClick = onNow) { Text("Agora", color = Accent) }
    }
}

@Composable
internal fun LyricsVideoProgressDialog(
    running: LyricsVideoGenerator.State.Running,
    onCancel: () -> Unit,
    onHide: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* só fecha pelos botões */ },
        containerColor = Color(0xFF1A1A24),
        title = { Text("Gerando vídeo…", color = Color.White) },
        text = {
            Column {
                Text(running.trackTitle, color = Color.White.copy(0.85f), fontSize = 14.sp, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Text(running.stage, color = Color.White.copy(0.6f), fontSize = 13.sp)
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { running.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = Accent,
                    trackColor = Color.White.copy(alpha = 0.15f)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${(running.progress * 100).toInt()}%",
                    color = Color.White.copy(0.7f),
                    fontSize = 13.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onHide) { Text("Segundo plano", color = Accent) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancelar", color = Color.White.copy(0.7f)) }
        }
    )
}
