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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tolete.player.data.LyricLine
import com.tolete.player.data.LyricsEmbedder
import com.tolete.player.data.Track
import com.tolete.player.video.LyricsVideoGenerator
import com.tolete.player.video.LyricsVideoGenerator.Aspect
import com.tolete.player.visualizer.ProjectMNative
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val Accent = Color(0xFFB388FF)
private const val MIN_CLIP_MS = 1000f

/** Como o tempo do vídeo é escolhido. */
private enum class ClipMode { WHOLE, MANUAL, LINES }

/** Se a última linha escolhida não tem fim conhecido, limita a duração dela (pausas instrumentais). */
private const val LAST_LINE_MAX_MS = 10_000L

/** Fim da linha [idx]: fim da própria linha (se houver), senão o começo da próxima. */
private fun lineEndMs(lines: List<LyricLine>, idx: Int, durationMs: Long): Long {
    val l = lines[idx]
    val lastWordEnd = l.words.lastOrNull()?.endMs ?: -1L
    val explicit = when {
        l.endMs > l.timeMs -> l.endMs
        lastWordEnd > l.timeMs -> lastWordEnd
        else -> -1L
    }
    if (explicit > 0L) return explicit.coerceAtMost(durationMs)
    val next = lines.getOrNull(idx + 1)?.timeMs ?: durationMs
    return minOf(next, l.timeMs + LAST_LINE_MAX_MS, durationMs)
}

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
    milkPresets: List<String> = emptyList(),
    initialMilkPreset: String? = null,
    /** True se existe .lrc ao lado da música (habilita a opção de fonte). */
    hasExternalLrc: Boolean = false,
    /** Carrega as linhas da fonte escolhida (chamado fora da main thread). Usado no tempo "Por linhas". */
    loadLyrics: (LyricsEmbedder.Target) -> List<LyricLine> = { emptyList() },
    /** [useLyrics] = false gera o vídeo sem letra (capa/visualizador, título, artista e progresso). */
    onGenerate: (
        startMs: Long,
        endMs: Long,
        shortSide: Int,
        aspect: Aspect,
        fps: Int,
        milkPresetPath: String?,
        lyricsSource: LyricsEmbedder.Target,
        useLyrics: Boolean
    ) -> Unit
) {
    val duration = track.duration.coerceAtLeast(2000L)
    val durF = duration.toFloat()

    var clipMode by remember { mutableStateOf(ClipMode.WHOLE) }
    var range by remember { mutableStateOf(0f..durF) }
    var aspect by remember { mutableStateOf(Aspect.V_9_16) }
    var fps by remember { mutableIntStateOf(30) }
    var chosenSide by remember { mutableIntStateOf(1080) }
    // Fonte da letra no vídeo: .lrc externo ou tags embutidas
    var lyricsSource by remember {
        mutableStateOf(
            if (hasExternalLrc) LyricsEmbedder.Target.LRC_FILE
            else LyricsEmbedder.Target.EMBEDDED
        )
    }

    // Vídeo sem letra (músicas sem letra ou quando o usuário prefere só a capa/visualizador)
    var noLyrics by remember { mutableStateOf(false) }

    // Linhas principais sincronizadas da fonte escolhida: base do tempo "Por linhas"
    var syncedMain by remember { mutableStateOf<List<LyricLine>>(emptyList()) }
    var loadingLines by remember { mutableStateOf(true) }
    LaunchedEffect(lyricsSource, noLyrics) {
        if (noLyrics) {
            syncedMain = emptyList()
            loadingLines = false
            if (clipMode == ClipMode.LINES) clipMode = ClipMode.WHOLE
            return@LaunchedEffect
        }
        loadingLines = true
        val loaded = withContext(Dispatchers.IO) {
            try {
                loadLyrics(lyricsSource)
            } catch (_: Exception) {
                emptyList()
            }
        }
        syncedMain = loaded
            .filter { it.timeMs >= 0 && it.text.isNotBlank() && !it.isTranslation && !it.isBackground }
            .sortedBy { it.timeMs }
        loadingLines = false
        if (syncedMain.isEmpty() && clipMode == ClipMode.LINES) clipMode = ClipMode.WHOLE
    }
    val n = syncedMain.size
    val linesAvailable = n > 0
    var lineRange by remember(syncedMain) {
        mutableStateOf(0f..(n - 1).coerceAtLeast(0).toFloat())
    }
    val lastIdx = (n - 1).coerceAtLeast(0)
    val i0 = lineRange.start.roundToInt().coerceIn(0, lastIdx)
    val i1 = lineRange.endInclusive.roundToInt().coerceIn(i0, lastIdx)

    // Fundo: capa borrada (padrão) ou preset milk escolhido pelo usuário
    val milkAvailable = remember { ProjectMNative.isAvailable } && milkPresets.isNotEmpty()
    var useMilk by remember { mutableStateOf(false) }
    var milkPreset by remember {
        mutableStateOf(initialMilkPreset?.takeIf { it in milkPresets } ?: milkPresets.firstOrNull())
    }
    var showPicker by remember { mutableStateOf(false) }
    val milkPath = if (useMilk && milkAvailable) milkPreset else null

    val sides = remember(aspect, fps) { LyricsVideoGenerator.supportedShortSides(aspect, fps) }
    val side = when {
        chosenSide in sides -> chosenSide
        1080 in sides -> 1080
        else -> sides.last()
    }

    val mode = if (clipMode == ClipMode.LINES && !linesAvailable) ClipMode.WHOLE else clipMode
    val startMs = when (mode) {
        ClipMode.WHOLE -> 0L
        ClipMode.MANUAL -> range.start.toLong()
        ClipMode.LINES -> syncedMain[i0].timeMs.coerceIn(0L, duration)
    }
    val endMs = when (mode) {
        ClipMode.WHOLE -> duration
        ClipMode.MANUAL -> range.endInclusive.toLong()
        ClipMode.LINES -> lineEndMs(syncedMain, i1, duration)
            .coerceAtLeast(startMs + MIN_CLIP_MS.toLong())
            .coerceAtMost(duration)
    }
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

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gerar vídeo", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Fonte da letra", color = MaterialTheme.colorScheme.onSurface.copy(0.6f), fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !noLyrics && lyricsSource == LyricsEmbedder.Target.LRC_FILE,
                        onClick = {
                            noLyrics = false
                            lyricsSource = LyricsEmbedder.Target.LRC_FILE
                        },
                        enabled = hasExternalLrc,
                        label = { Text("Arquivo .lrc") },
                        colors = chipColors
                    )
                    FilterChip(
                        selected = !noLyrics && lyricsSource == LyricsEmbedder.Target.EMBEDDED,
                        onClick = {
                            noLyrics = false
                            lyricsSource = LyricsEmbedder.Target.EMBEDDED
                        },
                        label = { Text("Tags embutidas") },
                        colors = chipColors
                    )
                    FilterChip(
                        selected = noLyrics,
                        onClick = { noLyrics = true },
                        label = { Text("Sem letra") },
                        colors = chipColors
                    )
                }
                if (noLyrics) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Sem letra: o vídeo mostra a capa (ou o preset milk) com título, artista e barra de progresso.",
                        color = MaterialTheme.colorScheme.onSurface.copy(0.45f),
                        fontSize = 12.sp
                    )
                } else if (!loadingLines && !linesAvailable) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Nenhuma letra sincronizada nesta fonte — o vídeo será gerado sem letra.",
                        color = MaterialTheme.colorScheme.onSurface.copy(0.45f),
                        fontSize = 12.sp
                    )
                }
                if (!hasExternalLrc) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Não há .lrc ao lado desta música — usando tags embutidas.",
                        color = MaterialTheme.colorScheme.onSurface.copy(0.45f),
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.height(14.dp))

                Text("Trecho", color = MaterialTheme.colorScheme.onSurface.copy(0.6f), fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = mode == ClipMode.WHOLE,
                        onClick = { clipMode = ClipMode.WHOLE },
                        label = { Text("Música inteira") },
                        colors = chipColors
                    )
                    FilterChip(
                        selected = mode == ClipMode.MANUAL,
                        onClick = { clipMode = ClipMode.MANUAL },
                        label = { Text("Escolher trecho") },
                        colors = chipColors
                    )
                    FilterChip(
                        selected = mode == ClipMode.LINES,
                        onClick = { clipMode = ClipMode.LINES },
                        enabled = linesAvailable,
                        label = { Text("Por linhas da letra") },
                        colors = chipColors
                    )
                }

                if (mode == ClipMode.LINES) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Do começo da linha inicial até o fim da linha final (letra sincronizada).",
                        color = MaterialTheme.colorScheme.onSurface.copy(0.45f),
                        fontSize = 12.sp
                    )
                    if (n > 1) {
                        RangeSlider(
                            value = lineRange,
                            onValueChange = { lineRange = it },
                            valueRange = 0f..(n - 1).toFloat(),
                            steps = (n - 2).coerceAtLeast(0),
                            colors = SliderDefaults.colors(
                                thumbColor = Accent,
                                activeTrackColor = Accent,
                                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            )
                        )
                    }
                    LineInfoRow(label = "Início", line = syncedMain[i0], number = i0 + 1)
                    LineInfoRow(label = "Fim", line = syncedMain[i1], number = i1 + 1)
                } else if (loadingLines && !noLyrics) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Carregando linhas da letra…",
                        color = MaterialTheme.colorScheme.onSurface.copy(0.45f),
                        fontSize = 12.sp
                    )
                }

                if (mode == ClipMode.MANUAL) {
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
                            inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
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
                Text("Fundo", color = MaterialTheme.colorScheme.onSurface.copy(0.6f), fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !useMilk,
                        onClick = { useMilk = false },
                        label = { Text("Capa borrada") },
                        colors = chipColors
                    )
                    FilterChip(
                        selected = useMilk && milkAvailable,
                        onClick = { useMilk = true },
                        enabled = milkAvailable,
                        label = { Text("Preset milk") },
                        colors = chipColors
                    )
                }
                if (useMilk && milkAvailable) {
                    TextButton(onClick = { showPicker = true }) {
                        Text(
                            milkPreset?.let { java.io.File(it).nameWithoutExtension } ?: "Escolher preset",
                            color = Accent,
                            maxLines = 1
                        )
                    }
                    Text(
                        "O preset reage à música do trecho. Gerar com esse fundo demora bem mais.",
                        color = MaterialTheme.colorScheme.onSurface.copy(0.45f),
                        fontSize = 12.sp
                    )
                } else if (!milkAvailable) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Preset milk indisponível: importe presets em Configurações > Visualizador.",
                        color = MaterialTheme.colorScheme.onSurface.copy(0.45f),
                        fontSize = 12.sp
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text("Proporção", color = MaterialTheme.colorScheme.onSurface.copy(0.6f), fontSize = 12.sp)
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
                Text("Taxa de quadros", color = MaterialTheme.colorScheme.onSurface.copy(0.6f), fontSize = 12.sp)
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
                Text("Resolução", color = MaterialTheme.colorScheme.onSurface.copy(0.6f), fontSize = 12.sp)
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
                    color = MaterialTheme.colorScheme.onSurface.copy(0.7f),
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Resoluções maiores e 60 fps demoram mais para gerar. O progresso aparece na notificação. A letra usa a fonte e as cores do Tocando agora.",
                    color = MaterialTheme.colorScheme.onSurface.copy(0.45f),
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onGenerate(startMs, endMs, side, aspect, fps, milkPath, lyricsSource, !noLyrics)
            }) {
                Text("Gerar", color = Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = MaterialTheme.colorScheme.onSurface.copy(0.7f))
            }
        }
    )

    if (showPicker) {
        MilkPresetPickerDialog(
            presets = milkPresets,
            currentPath = milkPreset,
            onDismiss = { showPicker = false },
            onSelect = { path ->
                milkPreset = path
                showPicker = false
            }
        )
    }
}

@Composable
private fun LineInfoRow(label: String, line: LyricLine, number: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White.copy(0.85f), fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp))
        Text(fmtTime(line.timeMs), color = Accent, fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp))
        Text(
            "#$number · ${line.text}",
            color = Color.White.copy(0.75f),
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
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
    AppAlertDialog(
        onDismissRequest = { /* só fecha pelos botões */ },
        title = { Text("Gerando vídeo…", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column {
                Text(running.trackTitle, color = MaterialTheme.colorScheme.onSurface.copy(0.85f), fontSize = 14.sp, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Text(running.stage, color = MaterialTheme.colorScheme.onSurface.copy(0.6f), fontSize = 13.sp)
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { running.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = Accent,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${(running.progress * 100).toInt()}%",
                    color = MaterialTheme.colorScheme.onSurface.copy(0.7f),
                    fontSize = 13.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onHide) { Text("Segundo plano", color = Accent) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancelar", color = MaterialTheme.colorScheme.onSurface.copy(0.7f)) }
        }
    )
}
