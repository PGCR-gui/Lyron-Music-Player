package com.tolete.player.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.Player
import com.tolete.player.data.LyricLine
import com.tolete.player.data.LyricsEmbedder
import com.tolete.player.data.Track
import com.tolete.player.video.RetrospectiveVideoGenerator
import com.tolete.player.video.WaveformExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Editor de trecho dos vídeos (Retrospectiva e letra em vídeo): player próprio (sem fila/notificação),
 * forma de onda com zoom, régua de tempo e ajuste fino. Usa as cores do tema do app e o estilo de
 * waveform escolhido em Configurações → Aparência.
 *
 * O trecho não tem limite de duração: início e fim são livres (a duração do vídeo é o tamanho do
 * trecho) e cada extremidade pode ser arrastada de forma independente, na faixa inteira ou na
 * forma de onda ampliada. Também dá para definir o trecho pelas linhas da letra sincronizada.
 */
@Composable
internal fun RetrospectiveClipEditorDialog(
    track: Track,
    initialStartMs: Long,
    initialEndMs: Long,
    hasExternalLrc: Boolean,
    loadLyrics: (LyricsEmbedder.Target) -> List<LyricLine>,
    onLyricsSourceChange: (LyricsEmbedder.Target) -> Unit = {},
    onDismiss: () -> Unit,
    onConfirm: (startMs: Long, endMs: Long) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        ClipEditorBody(
            track, initialStartMs, initialEndMs, hasExternalLrc, loadLyrics,
            onLyricsSourceChange, onDismiss, onConfirm
        )
    }
}

// Tipos de gesto nas formas de onda
private const val KIND_NONE = 0
private const val KIND_START = 1 // alça de início
private const val KIND_END = 2   // alça de fim
private const val KIND_MOVE = 3  // move o trecho inteiro (duração mantida)
private const val KIND_PAN = 4   // rola a visão (detalhe) / salta (visão geral)

private const val MAX_ZOOM = 6

/** Fim da linha [idx]: fim da própria linha (se houver), senão o começo da próxima (máx. 10 s). */
private fun lyricLineEndMs(lines: List<LyricLine>, idx: Int, durationMs: Long): Long {
    val l = lines[idx]
    val lastWordEnd = l.words.lastOrNull()?.endMs ?: -1L
    val explicit = when {
        l.endMs > l.timeMs -> l.endMs
        lastWordEnd > l.timeMs -> lastWordEnd
        else -> -1L
    }
    if (explicit > 0L) return explicit.coerceAtMost(durationMs)
    val next = lines.getOrNull(idx + 1)?.timeMs ?: durationMs
    return minOf(next, l.timeMs + 10_000L, durationMs)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClipEditorBody(
    track: Track,
    initialStartMs: Long,
    initialEndMs: Long,
    hasExternalLrc: Boolean,
    loadLyrics: (LyricsEmbedder.Target) -> List<LyricLine>,
    onLyricsSourceChange: (LyricsEmbedder.Target) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (Long, Long) -> Unit
) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val waveTheme = LocalWaveformTheme.current
    val accent = colors.accent
    val onBg = if (colors.isLight) Color(0xFF1B1B1F) else Color.White
    val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White

    val dur = track.duration.coerceAtLeast(1_000L)
    val minLen = min(RetrospectiveVideoGenerator.MIN_CLIP_SEC * 1000L, dur)

    var startMs by remember(track.id) {
        mutableLongStateOf(initialStartMs.coerceIn(0L, (dur - minLen).coerceAtLeast(0L)))
    }
    var endMs by remember(track.id) {
        mutableLongStateOf(initialEndMs.coerceIn(startMs + minLen, dur))
    }
    val clipMs = endMs - startMs
    var playheadMs by remember(track.id) { mutableLongStateOf(startMs) }
    var playing by remember(track.id) { mutableStateOf(false) }

    // ---- zoom e janela visível do detalhe (independentes do trecho, para as alças ficarem estáveis)
    fun visibleFor(z: Int): Long = max(min(dur, 1_000L), dur shr z)
    var zoom by remember(track.id) {
        // abre mostrando ~2,5x o tamanho do trecho (mín. 20 s)
        val want = max((endMs - startMs) * 5 / 2, 20_000L)
        var z = 0
        while (z < MAX_ZOOM && (dur shr (z + 1)) >= want) z++
        mutableIntStateOf(z)
    }
    val visibleMs = visibleFor(zoom)
    var viewStartMs by remember(track.id) {
        mutableLongStateOf((startMs - (visibleMs * 0.15f).toLong()).coerceIn(0L, (dur - visibleMs).coerceAtLeast(0L)))
    }

    fun clampView(v: Long): Long = v.coerceIn(0L, (dur - visibleMs).coerceAtLeast(0L))

    /** Rola a visão o mínimo necessário para [ms] continuar na tela. */
    fun keepVisible(ms: Long) {
        var vs = viewStartMs
        if (ms < vs) vs = ms else if (ms > vs + visibleMs) vs = ms - visibleMs
        viewStartMs = clampView(vs)
    }

    /** Depois de mudanças vindas de botões/letra: se o início saiu da tela, recentraliza nele. */
    fun ensureSelectionVisible() {
        if (startMs < viewStartMs || startMs > viewStartMs + visibleMs - visibleMs / 20) {
            viewStartMs = clampView(startMs - (visibleMs * 0.15f).toLong())
        }
    }

    fun changeZoom(newZoom: Int) {
        val nz = newZoom.coerceIn(0, MAX_ZOOM)
        if (nz == zoom) return
        val center = viewStartMs + visibleMs / 2
        zoom = nz
        val nv = visibleFor(nz)
        viewStartMs = (center - nv / 2).coerceIn(0L, (dur - nv).coerceAtLeast(0L))
    }

    // ---- player próprio (não toca na fila global nem no scrobbler)
    val player = remember(track.id) {
        createEditorPreviewPlayer(context, track).also { it.seekTo(startMs) }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }

    // pausa ao sair do app
    val owner = remember(context) { context.findClipEditorOwner() }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_PAUSE) playing = false
        }
        owner?.lifecycle?.addObserver(obs)
        onDispose { owner?.lifecycle?.removeObserver(obs) }
    }

    fun seekHead(v: Long) {
        playheadMs = v
        player.seekTo(v)
    }

    /** Arrasta o trecho inteiro (início e fim andam juntos, a duração se mantém). */
    fun moveWindow(value: Long) {
        val len = endMs - startMs
        val s = value.coerceIn(0L, (dur - len).coerceAtLeast(0L))
        startMs = s
        endMs = s + len
        seekHead(s)
    }

    /** Move só o início (o fim fica). Sem duração máxima; o mínimo é [minLen]. */
    fun setStartEdge(value: Long) {
        val hi = (endMs - minLen).coerceAtLeast(0L)
        startMs = value.coerceIn(0L, hi)
        seekHead(startMs)
    }

    /** Move só o fim (o início fica). Sem duração máxima; o mínimo é [minLen]. */
    fun setEndEdge(value: Long) {
        val lo = startMs + minLen
        endMs = value.coerceIn(lo, dur.coerceAtLeast(lo))
    }

    // ---- modo "Por letra": início/fim vêm das linhas da letra sincronizada
    var byLyrics by remember(track.id) { mutableStateOf(false) }
    var lyricsSource by remember(track.id) {
        mutableStateOf(if (hasExternalLrc) LyricsEmbedder.Target.LRC_FILE else LyricsEmbedder.Target.EMBEDDED)
    }
    var lines by remember(track.id) { mutableStateOf<List<LyricLine>>(emptyList()) }
    var loadingLines by remember(track.id) { mutableStateOf(true) }
    LaunchedEffect(track.id, lyricsSource) {
        loadingLines = true
        val loaded = withContext(Dispatchers.IO) {
            try {
                loadLyrics(lyricsSource)
            } catch (_: Exception) {
                emptyList()
            }
        }
        lines = loaded
            .filter { it.timeMs >= 0 && it.text.isNotBlank() && !it.isTranslation && !it.isBackground }
            .sortedBy { it.timeMs }
        loadingLines = false
        if (lines.isEmpty()) byLyrics = false
    }
    val lineCount = lines.size
    val lastLine = (lineCount - 1).coerceAtLeast(0)
    var lineRange by remember(lines) { mutableStateOf(0f..lastLine.toFloat()) }
    val i0 = lineRange.start.roundToInt().coerceIn(0, lastLine)
    val i1 = lineRange.endInclusive.roundToInt().coerceIn(i0, lastLine)

    LaunchedEffect(byLyrics, lines, i0, i1) {
        if (!byLyrics || lines.isEmpty()) return@LaunchedEffect
        val s = lines[i0].timeMs.coerceIn(0L, (dur - minLen).coerceAtLeast(0L))
        val e = lyricLineEndMs(lines, i1, dur)
            .coerceAtLeast(s + minLen)
            .coerceAtMost(dur.coerceAtLeast(s + minLen))
        startMs = s
        endMs = e
        seekHead(s)
        ensureSelectionVisible()
    }

    /** Mexer numa alça enquanto "Por letra" está ativo passa o trecho para o modo manual. */
    fun leaveLyricsMode() {
        if (byLyrics) byLyrics = false
    }

    /** Gesto nas formas de onda: [delta] em ms (já convertido de px). */
    fun applyDrag(kind: Int, delta: Long) {
        when (kind) {
            KIND_START -> {
                leaveLyricsMode()
                setStartEdge(startMs + delta)
                keepVisible(startMs)
            }
            KIND_END -> {
                leaveLyricsMode()
                setEndEdge(endMs + delta)
                keepVisible(endMs)
            }
            KIND_MOVE -> if (!byLyrics) {
                moveWindow(startMs + delta)
                keepVisible(startMs)
                keepVisible(endMs)
            }
            KIND_PAN -> viewStartMs = clampView(viewStartMs + delta)
        }
    }

    // toca só o trecho selecionado, em laço
    LaunchedEffect(playing, player) {
        if (!playing) {
            player.pause()
            return@LaunchedEffect
        }
        if (playheadMs < startMs || playheadMs >= endMs - 30L) player.seekTo(startMs)
        player.play()
        while (true) {
            delay(25)
            val pos = player.currentPosition
            playheadMs = pos
            if (pos >= endMs - 15L || player.playbackState == Player.STATE_ENDED) {
                player.seekTo(startMs)
                if (player.playbackState == Player.STATE_ENDED) player.play()
            }
        }
    }

    // ---- forma de onda
    var peaks by remember(track.id) { mutableStateOf(WaveformExtractor.cached(track.id)) }
    var loadProgress by remember(track.id) { mutableFloatStateOf(0f) }
    var waveFailed by remember(track.id) { mutableStateOf(false) }
    LaunchedEffect(track.id) {
        if (peaks != null) return@LaunchedEffect
        val result = withContext(Dispatchers.IO) {
            WaveformExtractor.extract(
                context = context.applicationContext,
                uri = track.uri,
                key = track.id,
                durationMs = dur,
                cancelled = { !isActive },
                onProgress = { loadProgress = it }
            )
        }
        if (result != null) peaks = result else waveFailed = true
    }

    // ---------------------------------------------------------------- seções da tela

    val header: @Composable () -> Unit = {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, contentDescription = "Fechar", tint = onBg)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    color = onBg,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    track.artist,
                    color = onBg.copy(0.7f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                "Aplicar",
                color = onAccent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(accent)
                    .clickable {
                        playing = false
                        onConfirm(startMs, endMs)
                    }
                    .padding(horizontal = 18.dp, vertical = 9.dp)
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "${fmtClip(startMs)} – ${fmtClip(endMs)}",
                color = accent,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Vídeo de ${String.format(Locale.US, "%.1f", clipMs / 1000.0)} s · " +
                    "faixa de ${fmtClip(dur).substringBefore('.')}",
                color = onBg.copy(0.7f),
                fontSize = 12.sp
            )
        }
    }

    val middle: @Composable () -> Unit = {
        // como escolher o trecho
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ModeChip("Por tempo", !byLyrics, true, accent, onBg, onAccent) { byLyrics = false }
            ModeChip("Por letra", byLyrics, lineCount > 0, accent, onBg, onAccent) { byLyrics = true }
            if (loadingLines) {
                Text("Carregando letra…", color = onBg.copy(0.6f), fontSize = 12.sp)
            } else if (lineCount == 0) {
                Text("Sem letra sincronizada", color = onBg.copy(0.6f), fontSize = 12.sp)
            }
        }

        if (byLyrics && lineCount > 0) {
            if (hasExternalLrc) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeChip(
                        "Arquivo .lrc", lyricsSource == LyricsEmbedder.Target.LRC_FILE, true, accent, onBg, onAccent
                    ) {
                        lyricsSource = LyricsEmbedder.Target.LRC_FILE
                        onLyricsSourceChange(LyricsEmbedder.Target.LRC_FILE)
                    }
                    ModeChip(
                        "Tags embutidas", lyricsSource == LyricsEmbedder.Target.EMBEDDED, true, accent, onBg, onAccent
                    ) {
                        lyricsSource = LyricsEmbedder.Target.EMBEDDED
                        onLyricsSourceChange(LyricsEmbedder.Target.EMBEDDED)
                    }
                }
            }
            Text(
                "Do começo da linha inicial até o fim da linha final (letra sincronizada).",
                color = onBg.copy(0.6f),
                fontSize = 12.sp
            )
            if (lineCount > 1) {
                RangeSlider(
                    value = lineRange,
                    onValueChange = { lineRange = it },
                    valueRange = 0f..lastLine.toFloat(),
                    steps = (lineCount - 2).coerceAtLeast(0),
                    colors = SliderDefaults.colors(
                        thumbColor = accent,
                        activeTrackColor = accent,
                        inactiveTrackColor = onBg.copy(0.2f)
                    )
                )
            }
            LyricLineRow("Início", lines[i0], i0 + 1, accent, onBg)
            LyricLineRow("Fim", lines[i1], i1 + 1, accent, onBg)
        }

        if (peaks == null && !waveFailed) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(
                    progress = { loadProgress },
                    color = accent,
                    trackColor = onBg.copy(0.15f),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Gerando forma de onda… ${(loadProgress * 100).roundToInt()}%",
                    color = onBg.copy(0.65f),
                    fontSize = 12.sp
                )
            }
        } else if (waveFailed) {
            Text(
                "Forma de onda indisponível para este formato. O ajuste por tempo continua funcionando.",
                color = onBg.copy(0.65f),
                fontSize = 12.sp
            )
        }

        // visão geral da faixa inteira
        OverviewWave(
            peaks = peaks,
            dur = dur,
            startMs = startMs,
            clipMs = clipMs,
            playheadMs = playheadMs,
            accent = accent,
            onBg = onBg,
            onAccent = onAccent,
            theme = waveTheme,
            onDrag = { kind, delta -> applyDrag(kind, delta) },
            onSeekCenter = { ms ->
                if (!byLyrics) {
                    moveWindow(ms - clipMs / 2)
                    ensureSelectionVisible()
                }
            },
            onSeekInside = { ms -> seekHead(ms.coerceIn(startMs, (endMs - 30L).coerceAtLeast(startMs))) }
        )

        // detalhe com zoom
        DetailWave(
            peaks = peaks,
            dur = dur,
            viewStart = viewStartMs,
            visibleMs = visibleMs,
            startMs = startMs,
            clipMs = clipMs,
            playheadMs = playheadMs,
            accent = accent,
            onBg = onBg,
            onAccent = onAccent,
            theme = waveTheme,
            onDrag = { kind, delta -> applyDrag(kind, delta) },
            onSeekInside = { ms -> seekHead(ms.coerceIn(startMs, (endMs - 30L).coerceAtLeast(startMs))) }
        )

        Text(
            if (byLyrics) {
                "Arraste uma alça para ajustar o trecho à mão · toque dentro do trecho para ouvir dali · arraste fora para rolar · zoom ${1 shl zoom}×"
            } else {
                "Arraste as alças para ajustar início e fim separadamente · arraste dentro do trecho para movê-lo " +
                    "· toque dentro dele para ouvir dali · fora, para rolar · zoom ${1 shl zoom}×"
            },
            color = onBg.copy(0.6f),
            fontSize = 11.sp
        )
    }

    val bottom: @Composable () -> Unit = {
        // controles
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { changeZoom(zoom + 1) }, enabled = zoom < MAX_ZOOM) {
                Icon(Icons.Rounded.ZoomIn, contentDescription = "Aproximar", tint = onBg.copy(if (zoom < MAX_ZOOM) 1f else 0.35f))
            }
            IconButton(onClick = {
                seekHead(startMs)
                ensureSelectionVisible()
            }) {
                Icon(Icons.Rounded.Replay, contentDescription = "Voltar ao início do trecho", tint = onBg)
            }
            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(accent)
                    .clickable { playing = !playing },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (playing) "Pausar" else "Ouvir trecho",
                    tint = onAccent,
                    modifier = Modifier.size(34.dp)
                )
            }
            IconButton(onClick = { changeZoom(zoom - 1) }, enabled = zoom > 0) {
                Icon(Icons.Rounded.ZoomOut, contentDescription = "Afastar", tint = onBg.copy(if (zoom > 0) 1f else 0.35f))
            }
        }

        // ajuste fino do início e do fim (só no modo por tempo)
        if (!byLyrics) {
            EdgeNudgeRow("Início", startMs, onBg, ::fmtClip) {
                setStartEdge(it)
                ensureSelectionVisible()
            }
            EdgeNudgeRow("Fim", endMs, onBg, ::fmtClip) {
                setEndEdge(it)
                keepVisible(endMs)
            }
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(colors.gradient)
    ) {
        // Tudo (formas de onda, play/zoom e ajuste fino de início/fim) rola junto logo abaixo do
        // miolo. Antes os controles ficavam fixos no rodapé da janela e, neste Dialog, a linha
        // "Fim" ficava atrás da barra de gestos/fora da tela, inacessível. O espaço extra no fim
        // garante que dê para rolar até ela mesmo se o sistema não informar o inset corretamente.
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            header()
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                middle()
                bottom()
                Spacer(Modifier.height(96.dp))
            }
        }
    }
}

@Composable
private fun EdgeNudgeRow(
    label: String,
    valueMs: Long,
    onBg: Color,
    format: (Long) -> String,
    onChange: (Long) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = onBg.copy(0.7f), fontSize = 12.sp, modifier = Modifier.padding(end = 6.dp))
        NudgeChip("−1 s", onBg) { onChange(valueMs - 1000L) }
        NudgeChip("−0,1 s", onBg) { onChange(valueMs - 100L) }
        Text(
            format(valueMs),
            color = onBg,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
        NudgeChip("+0,1 s", onBg) { onChange(valueMs + 100L) }
        NudgeChip("+1 s", onBg) { onChange(valueMs + 1000L) }
    }
}

@Composable
private fun ModeChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    accent: Color,
    onBg: Color,
    onAccent: Color,
    onClick: () -> Unit
) {
    Text(
        label,
        color = if (selected) onAccent else onBg.copy(if (enabled) 1f else 0.4f),
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) accent else onBg.copy(0.12f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun LyricLineRow(label: String, line: LyricLine, number: Int, accent: Color, onBg: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = onBg.copy(0.85f), fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp))
        Text(fmtRuler(line.timeMs, 1_000L), color = accent, fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp))
        Text(
            "#$number · ${line.text}",
            color = onBg.copy(0.75f),
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun NudgeChip(label: String, onBg: Color, onClick: () -> Unit) {
    Text(
        label,
        color = onBg,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(onBg.copy(0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    )
}

/** Qual gesto começa em [x]: alça de início, alça de fim, dentro do trecho (mover) ou fora (rolar). */
private fun hitKind(x: Float, xs: Float, xe: Float, slop: Float): Int {
    val ds = abs(x - xs)
    val de = abs(x - xe)
    if (min(ds, de) <= slop) {
        return when {
            ds < de -> KIND_START
            de < ds -> KIND_END
            else -> if (x < xs) KIND_START else KIND_END // alças sobrepostas: decide pelo lado
        }
    }
    return if (x in xs..xe) KIND_MOVE else KIND_PAN
}

/** Alça arrastável: linha vertical + "pílula" com marcas de pegada. */
private fun DrawScope.drawHandle(x: Float, color: Color, grip: Color, pillH: Float) {
    drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.5.dp.toPx())
    val pw = 14.dp.toPx()
    val top = (size.height - pillH) / 2f
    drawRoundRect(
        color = color,
        topLeft = Offset(x - pw / 2f, top),
        size = Size(pw, pillH),
        cornerRadius = CornerRadius(pw / 2f)
    )
    val g = 2.dp.toPx()
    val w = 1.5.dp.toPx()
    drawLine(grip, Offset(x - g, top + pillH * 0.30f), Offset(x - g, top + pillH * 0.70f), w, StrokeCap.Round)
    drawLine(grip, Offset(x + g, top + pillH * 0.30f), Offset(x + g, top + pillH * 0.70f), w, StrokeCap.Round)
}

/**
 * Faixa inteira, com o trecho escolhido destacado. As alças ajustam início e fim separadamente;
 * arrastar dentro do trecho move tudo; tocar dentro dele adianta o player para aquele ponto;
 * tocar/arrastar fora salta o trecho para lá.
 */
@Composable
private fun OverviewWave(
    peaks: FloatArray?,
    dur: Long,
    startMs: Long,
    clipMs: Long,
    playheadMs: Long,
    accent: Color,
    onBg: Color,
    onAccent: Color,
    theme: WaveformTheme,
    onDrag: (kind: Int, deltaMs: Long) -> Unit,
    onSeekCenter: (Long) -> Unit,
    onSeekInside: (Long) -> Unit
) {
    var widthPx by remember { mutableIntStateOf(0) }
    val currentSeek by rememberUpdatedState(onSeekCenter)
    val currentSeekInside by rememberUpdatedState(onSeekInside)
    val currentDrag by rememberUpdatedState(onDrag)
    val durNow by rememberUpdatedState(dur)
    val startNow by rememberUpdatedState(startMs)
    val endNow by rememberUpdatedState(startMs + clipMs)
    Box(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(onBg.copy(0.07f))
            .onSizeChanged { widthPx = it.width }
            .pointerInput(Unit) {
                val slop = 24.dp.toPx()
                detectTapGestures { o ->
                    val w = widthPx.toFloat()
                    if (w > 0f) {
                        val xs = startNow.toFloat() / durNow * w
                        val xe = endNow.toFloat() / durNow * w
                        val ms = (o.x / w * durNow).toLong()
                        when (hitKind(o.x, xs, xe, slop)) {
                            // toque no meio do trecho: só adianta o player para ali (o trecho não muda)
                            KIND_MOVE -> currentSeekInside(ms)
                            // toque fora do trecho: leva o trecho para lá
                            KIND_PAN -> currentSeek(ms)
                            // toque numa alça: nada (as alças são para arrastar)
                            else -> Unit
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                val slop = 24.dp.toPx()
                var kind = KIND_NONE
                detectDragGestures(
                    onDragStart = { o ->
                        val w = widthPx.toFloat()
                        val xs = startNow.toFloat() / durNow * w
                        val xe = endNow.toFloat() / durNow * w
                        kind = hitKind(o.x, xs, xe, slop)
                        if (kind == KIND_PAN && w > 0f) currentSeek((o.x / w * durNow).toLong())
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        val w = widthPx.toFloat()
                        if (w > 0f) {
                            if (kind == KIND_PAN) {
                                currentSeek((change.position.x / w * durNow).toLong())
                            } else if (kind != KIND_NONE) {
                                currentDrag(kind, (amount.x / w * durNow).roundToLong())
                            }
                        }
                    },
                    onDragEnd = { kind = KIND_NONE },
                    onDragCancel = { kind = KIND_NONE }
                )
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val xs = startMs.toFloat() / dur * size.width
            val xe = (startMs + clipMs).toFloat() / dur * size.width
            drawWave(
                peaks = peaks,
                dur = dur,
                fromMs = 0L,
                spanMs = dur,
                selStart = startMs,
                selEnd = startMs + clipMs,
                accent = accent,
                offColor = onBg.copy(0.28f),
                theme = theme,
                barW = 2.dp.toPx(),
                gap = 1.dp.toPx(),
                minH = 2.dp.toPx()
            )
            drawRect(
                color = accent.copy(0.16f),
                topLeft = Offset(xs, 0f),
                size = Size((xe - xs).coerceAtLeast(2.dp.toPx()), size.height)
            )
            drawRect(
                color = accent,
                topLeft = Offset(xs, 0f),
                size = Size((xe - xs).coerceAtLeast(2.dp.toPx()), size.height),
                style = Stroke(width = 1.5.dp.toPx())
            )
            val xp = playheadMs.toFloat() / dur * size.width
            drawLine(onBg, Offset(xp, 0f), Offset(xp, size.height), strokeWidth = 1.5.dp.toPx())
            val pill = min(size.height, 30.dp.toPx())
            drawHandle(xs.coerceIn(0f, size.width), accent, onAccent, pill)
            drawHandle(xe.coerceIn(0f, size.width), accent, onAccent, pill)
        }
    }
}

/**
 * Forma de onda ampliada com régua de tempo. Cada alça (início/fim) é arrastável de forma
 * independente (segurando perto da borda da tela a visão rola sozinha); arrastar dentro do trecho
 * o move; arrastar fora rola a visão.
 */
@Composable
private fun DetailWave(
    peaks: FloatArray?,
    dur: Long,
    viewStart: Long,
    visibleMs: Long,
    startMs: Long,
    clipMs: Long,
    playheadMs: Long,
    accent: Color,
    onBg: Color,
    onAccent: Color,
    theme: WaveformTheme,
    onDrag: (kind: Int, deltaMs: Long) -> Unit,
    onSeekInside: (Long) -> Unit
) {
    val density = LocalDensity.current
    val currentSeekInside by rememberUpdatedState(onSeekInside)
    var widthPx by remember { mutableIntStateOf(0) }
    val msPerPx = if (widthPx > 0) visibleMs.toFloat() / widthPx else 1f
    val currentMsPerPx by rememberUpdatedState(msPerPx)
    val currentDrag by rememberUpdatedState(onDrag)
    val viewNow by rememberUpdatedState(viewStart)
    val startNow by rememberUpdatedState(startMs)
    val endNow by rememberUpdatedState(startMs + clipMs)

    // alça em uso e direção da rolagem automática (dedo encostado na borda da tela)
    var activeKind by remember { mutableIntStateOf(KIND_NONE) }
    var holdDir by remember { mutableIntStateOf(0) }
    LaunchedEffect(activeKind, holdDir) {
        if (holdDir == 0 || (activeKind != KIND_START && activeKind != KIND_END)) return@LaunchedEffect
        while (isActive) {
            currentDrag(activeKind, (holdDir * currentMsPerPx * 6f).roundToLong())
            delay(16)
        }
    }

    val stepMs = rulerStep(msPerPx, with(density) { 72.dp.toPx() })
    val ticks = remember(viewStart, visibleMs, stepMs) {
        val list = ArrayList<Long>()
        var t = ((viewStart + stepMs - 1) / stepMs) * stepMs
        while (t <= viewStart + visibleMs && list.size < 40) {
            list.add(t)
            t += stepMs
        }
        list
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(onBg.copy(0.07f))
            .onSizeChanged { widthPx = it.width }
            .pointerInput(Unit) {
                // toque no meio do trecho (longe das alças): adianta o player para aquela posição
                val slop = 28.dp.toPx()
                detectTapGestures { o ->
                    val mpp = currentMsPerPx
                    val xs = (startNow - viewNow) / mpp
                    val xe = (endNow - viewNow) / mpp
                    if (hitKind(o.x, xs, xe, slop) == KIND_MOVE) {
                        currentSeekInside((viewNow + o.x * mpp).toLong())
                    }
                }
            }
            .pointerInput(Unit) {
                val slop = 28.dp.toPx()
                val edgeZone = 30.dp.toPx()
                detectDragGestures(
                    onDragStart = { o ->
                        val mpp = currentMsPerPx
                        val xs = (startNow - viewNow) / mpp
                        val xe = (endNow - viewNow) / mpp
                        activeKind = hitKind(o.x, xs, xe, slop)
                        holdDir = 0
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        val mpp = currentMsPerPx
                        when (activeKind) {
                            KIND_START, KIND_END, KIND_MOVE ->
                                currentDrag(activeKind, (amount.x * mpp).roundToLong())
                            // o conteúdo acompanha o dedo: arrastar para a direita volta no tempo
                            KIND_PAN -> currentDrag(KIND_PAN, (-amount.x * mpp).roundToLong())
                        }
                        holdDir = if (activeKind == KIND_START || activeKind == KIND_END) {
                            val x = change.position.x
                            when {
                                x < edgeZone -> -1
                                x > widthPx - edgeZone -> 1
                                else -> 0
                            }
                        } else 0
                    },
                    onDragEnd = {
                        activeKind = KIND_NONE
                        holdDir = 0
                    },
                    onDragCancel = {
                        activeKind = KIND_NONE
                        holdDir = 0
                    }
                )
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val xs = (startMs - viewStart) / msPerPx
            val xe = (startMs + clipMs - viewStart) / msPerPx

            // região selecionada
            val selL = max(xs, 0f)
            val selR = min(xe, w)
            if (selR > selL) {
                drawRect(accent.copy(0.12f), Offset(selL, 0f), Size(selR - selL, h))
            }

            drawWave(
                peaks = peaks,
                dur = dur,
                fromMs = viewStart,
                spanMs = visibleMs,
                selStart = startMs,
                selEnd = startMs + clipMs,
                accent = accent,
                offColor = onBg.copy(0.30f),
                theme = theme,
                barW = 3.dp.toPx(),
                gap = 1.5.dp.toPx(),
                minH = 2.dp.toPx()
            )

            // linha central e régua
            drawLine(onBg.copy(0.14f), Offset(0f, h / 2f), Offset(w, h / 2f), strokeWidth = 1.dp.toPx())
            for (t in ticks) {
                val x = (t - viewStart) / msPerPx
                drawLine(onBg.copy(0.35f), Offset(x, 0f), Offset(x, 10.dp.toPx()), strokeWidth = 1.dp.toPx())
            }

            // cursor de reprodução (por baixo das alças)
            val xp = (playheadMs - viewStart) / msPerPx
            if (xp in 0f..w) {
                drawLine(onBg, Offset(xp, 0f), Offset(xp, h), strokeWidth = 2.dp.toPx())
            }

            // alças de início e fim
            val pill = min(h * 0.5f, 40.dp.toPx())
            if (xs in 0f..w) drawHandle(xs, accent, onAccent, pill)
            if (xe in 0f..w) drawHandle(xe, accent, onAccent, pill)
        }

        // rótulos da régua
        for (t in ticks) {
            val x = ((t - viewStart) / msPerPx).roundToInt()
            Text(
                fmtRuler(t, stepMs),
                color = onBg.copy(0.75f),
                fontSize = 10.sp,
                modifier = Modifier.offset { IntOffset(x + 4, 6) }
            )
        }
    }
}

/**
 * Desenha a forma de onda de [fromMs, fromMs + spanMs] no estilo [theme].
 * Cada coluna usa uma mistura de pico e média das barras que caem nela (menos "estourada" do que
 * só o máximo, principalmente na visão geral, onde muitas barras ficam na mesma coluna).
 */
private fun DrawScope.drawWave(
    peaks: FloatArray?,
    dur: Long,
    fromMs: Long,
    spanMs: Long,
    selStart: Long,
    selEnd: Long,
    accent: Color,
    offColor: Color,
    theme: WaveformTheme,
    barW: Float,
    gap: Float,
    minH: Float
) {
    val colW = barW + gap
    val cols = max(1, (size.width / colW).toInt())
    val msPerCol = spanMs.toFloat() / cols
    val pk = peaks
    val n = pk?.size ?: 0
    val binsPerMs = if (n > 0) n.toFloat() / dur else 0f
    val amps = FloatArray(cols)
    for (i in 0 until cols) {
        val t0 = fromMs + i * msPerCol
        val t1 = t0 + msPerCol
        var p = 0.06f // sem dados ainda: barras baixas só para mostrar a seleção
        if (pk != null && n > 0) {
            val b0 = (t0 * binsPerMs).toInt().coerceIn(0, n - 1)
            val b1 = (t1 * binsPerMs).toInt().coerceIn(b0, n - 1)
            var mx = 0f
            var sum = 0f
            for (b in b0..b1) {
                val v = pk[b]
                if (v > mx) mx = v
                sum += v
            }
            val mean = sum / (b1 - b0 + 1)
            p = max(0.4f * mx + 0.6f * mean, 0.02f)
        }
        amps[i] = p
    }
    val selL = (selStart - fromMs).toFloat() / spanMs * size.width
    val selR = (selEnd - fromMs).toFloat() / spanMs * size.width
    drawWaveAmps(theme, amps, selL, selR, accent, offColor, barW, gap, minH)
}

private val RULER_STEPS_MS = longArrayOf(250L, 500L, 1_000L, 2_000L, 5_000L, 10_000L, 15_000L, 30_000L, 60_000L)

private fun rulerStep(msPerPx: Float, minPx: Float): Long {
    for (s in RULER_STEPS_MS) if (s / msPerPx >= minPx) return s
    return RULER_STEPS_MS.last()
}

private fun fmtRuler(ms: Long, stepMs: Long): String {
    val totalSec = ms / 1000L
    val m = totalSec / 60L
    val s = totalSec % 60L
    return if (stepMs < 1000L) {
        String.format(Locale.US, "%d:%02d.%d", m, s, (ms % 1000L) / 100L)
    } else {
        String.format(Locale.US, "%d:%02d", m, s)
    }
}

private fun fmtClip(ms: Long): String {
    val v = ms.coerceAtLeast(0L)
    val totalSec = v / 1000L
    return String.format(Locale.US, "%02d:%02d.%03d", totalSec / 60L, totalSec % 60L, v % 1000L)
}

private tailrec fun Context.findClipEditorOwner(): LifecycleOwner? = when (this) {
    is LifecycleOwner -> this
    is ContextWrapper -> baseContext.findClipEditorOwner()
    else -> null
}
