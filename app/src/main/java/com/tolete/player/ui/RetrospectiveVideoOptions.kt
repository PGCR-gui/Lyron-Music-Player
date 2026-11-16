package com.tolete.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tolete.player.data.LyricsEmbedder
import com.tolete.player.data.LyricsExtractor
import com.tolete.player.data.Track
import com.tolete.player.video.RetrospectiveVideoGenerator
import com.tolete.player.video.RetrospectiveVideoGenerator.AudioKind
import com.tolete.player.video.RetrospectiveVideoGenerator.CardAudioOptions
import com.tolete.player.video.RetrospectiveVideoGenerator.CardClip

/** Progresso compartilhado da geração; ocultar o diálogo não interrompe o serviço em primeiro plano. */
@Composable
internal fun RetrospectiveVideoProgressDialog(
    running: RetrospectiveVideoGenerator.State.Running,
    onCancel: () -> Unit,
    onHide: () -> Unit
) {
    val progress = (if (running.batch) running.overallFrac else running.frac).coerceIn(0f, 1f)
    val percent = (progress * 100).toInt()
    AppAlertDialog(
        onDismissRequest = { /* O usuário escolhe cancelar ou deixar em segundo plano. */ },
        title = { Text("Gerando vídeo…", color = Color.White, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text(
                    if (running.batch) "Card ${running.index + 1}/${running.total} · ${running.title}"
                    else running.title,
                    color = Color.White.copy(0.85f),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (running.batch) "${running.stage} · card ${(running.frac * 100).toInt()}%"
                    else running.stage,
                    color = Color.White.copy(0.62f),
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = dialogAccent,
                    trackColor = Color.White.copy(alpha = 0.15f)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (running.batch) "Progresso geral · $percent%" else "$percent%",
                    color = Color.White.copy(0.72f),
                    fontSize = 13.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onHide) { Text("Segundo plano", color = dialogAccent) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancelar", color = Color.White.copy(0.72f)) }
        }
    )
}

/** Opções para um único info card do Last.fm: música local + corte reutilizável da retrospectiva. */
@Composable
internal fun EntityCardVideoOptionsDialog(
    kind: EntityShareCard.Kind,
    entityName: String,
    entitySubtitle: String?,
    libraryTracks: List<Track>,
    libraryLoading: Boolean,
    suggestedTrack: Track?,
    onDismiss: () -> Unit,
    onGenerate: (Track, CardClip) -> Unit
) {
    val context = LocalContext.current
    val candidates = remember(kind, entityName, entitySubtitle, libraryTracks, suggestedTrack) {
        val preferred = libraryTracks.firstOrNull { track ->
            when (kind) {
                EntityShareCard.Kind.TRACK -> track.title.equals(entityName, ignoreCase = true) &&
                    (entitySubtitle.isNullOrBlank() || track.artist.contains(entitySubtitle, ignoreCase = true))
                EntityShareCard.Kind.ARTIST -> track.artist.contains(entityName, ignoreCase = true)
                EntityShareCard.Kind.ALBUM -> track.album.equals(entityName, ignoreCase = true) &&
                    (entitySubtitle.isNullOrBlank() || track.artist.contains(entitySubtitle, ignoreCase = true))
            }
        }
        (listOfNotNull(suggestedTrack, preferred) + libraryTracks).distinctBy { it.id }
    }
    var selected by remember(candidates) { mutableStateOf(candidates.firstOrNull()) }
    var clip by remember(selected?.id) { mutableStateOf<CardClip?>(null) }
    var picking by remember { mutableStateOf(false) }
    var editingClip by remember { mutableStateOf(false) }

    fun defaultClip(track: Track): CardClip {
        val trackMs = track.duration.coerceAtLeast(1_000L)
        val length = minOf(RetrospectiveVideoGenerator.DEFAULT_DURATION_SEC * 1000L, trackMs)
        val start = RetrospectiveVideoGenerator.autoStartMs(trackMs, length)
            .coerceIn(0L, (trackMs - length).coerceAtLeast(0L))
        return CardClip(start, (start + length).coerceAtMost(trackMs))
    }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Vídeo do info card", color = Color.White, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Escolha uma música da biblioteca e ajuste o trecho. O vídeo mantém o visual do card e usa áudio AAC em MP4.",
                    color = Color.White.copy(0.72f),
                    fontSize = 13.sp
                )
                when {
                    libraryLoading -> Text("Carregando músicas da biblioteca…", color = Color.White.copy(0.7f), fontSize = 13.sp)
                    candidates.isEmpty() -> Text("Nenhuma música disponível na biblioteca.", color = Color.White.copy(0.7f), fontSize = 13.sp)
                    else -> {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(0.08f))
                                .clickable { picking = true }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text("Música", color = Color.White.copy(0.6f), fontSize = 11.sp)
                            Text(
                                selected?.let { "${it.title} · ${it.artist}" } ?: "Escolha uma música",
                                color = Color.White,
                                fontSize = 14.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text("Trocar música", color = dialogAccent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        selected?.let { track ->
                            val currentClip = clip ?: defaultClip(track)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Trecho: ${formatTime(currentClip.startMs)} – ${formatTime(currentClip.endMs)} · " +
                                            "${formatSeconds(currentClip.durationMs)} s",
                                        color = Color.White.copy(0.82f),
                                        fontSize = 12.sp
                                    )
                                    Text("Duração do vídeo = duração do trecho", color = Color.White.copy(0.55f), fontSize = 11.sp)
                                }
                                TextButton(onClick = { editingClip = true }) {
                                    Text("Ajustar trecho", color = dialogAccent)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected != null && !libraryLoading,
                onClick = {
                    val track = selected ?: return@TextButton
                    onGenerate(track, clip ?: defaultClip(track))
                }
            ) { Text("Gerar vídeo", color = dialogAccent, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Color.White.copy(0.8f)) }
        }
    )

    if (picking) {
        TrackPickerDialog(
            title = "Música do vídeo",
            tracks = candidates,
            selected = selected,
            onPick = { track ->
                if (track != null) {
                    selected = track
                    clip = null
                }
                picking = false
            },
            onDismiss = { picking = false },
            allowAutomatic = false
        )
    }

    selected?.let { track ->
        if (editingClip) {
            val currentClip = clip ?: defaultClip(track)
            RetrospectiveClipEditorDialog(
                track = track,
                initialStartMs = currentClip.startMs,
                initialEndMs = currentClip.endMs,
                hasExternalLrc = LyricsEmbedder.hasExternalLrc(track),
                loadLyrics = { source ->
                    when (source) {
                        LyricsEmbedder.Target.LRC_FILE -> LyricsEmbedder.loadExternalLrc(track)
                        LyricsEmbedder.Target.EMBEDDED -> LyricsExtractor.loadEmbedded(context, track)
                    }
                },
                onDismiss = { editingClip = false },
                onConfirm = { start, end ->
                    clip = CardClip(start, end)
                    editingClip = false
                }
            )
        }
    }
}

/** Opções antes de gerar os vídeos: duração padrão, música por card e trecho (início/fim) de cada card. */
@Composable
internal fun RetrospectiveVideoOptionsDialog(
    cards: List<CardAudioOptions>,
    initial: RetrospectiveVideoGenerator.VideoOptions,
    onDismiss: () -> Unit,
    onGenerate: (RetrospectiveVideoGenerator.VideoOptions) -> Unit,
    title: String = "Opções dos vídeos"
) {
    var duration by remember { mutableFloatStateOf(initial.durationSec.toFloat()) }
    var chosen by remember { mutableStateOf(initial.audioByCard) }
    var clips by remember { mutableStateOf(initial.clipByCard) }
    val context = LocalContext.current
    var fps by remember { mutableStateOf(initial.fps) }
    var picking by remember { mutableStateOf<CardAudioOptions?>(null) }
    var editingClip by remember { mutableStateOf<CardAudioOptions?>(null) }
    val seconds = duration.toInt()

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Color.White, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Duração padrão: $seconds s", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    "Vale para os cards sem trecho escolhido. Ao ajustar o trecho de um card, a duração do vídeo dele passa a ser o tamanho do trecho.",
                    color = Color.White.copy(0.65f),
                    fontSize = 12.sp
                )
                Slider(
                    value = duration,
                    onValueChange = { duration = it },
                    valueRange = RetrospectiveVideoGenerator.MIN_DURATION_SEC.toFloat()..
                        RetrospectiveVideoGenerator.MAX_DURATION_SEC.toFloat(),
                    steps = RetrospectiveVideoGenerator.MAX_DURATION_SEC - RetrospectiveVideoGenerator.MIN_DURATION_SEC - 1,
                    colors = SliderDefaults.colors(
                        thumbColor = dialogAccent,
                        activeTrackColor = dialogAccent,
                        inactiveTrackColor = Color.White.copy(0.25f)
                    )
                )
                Text(
                    "Quadros por segundo",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(RetrospectiveVideoGenerator.FPS_30, RetrospectiveVideoGenerator.FPS_60).forEach { option ->
                        val selected = fps == option
                        Text(
                            "$option fps",
                            color = if (selected) Color.Black else Color.White,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (selected) dialogAccent else Color.White.copy(0.12f))
                                .clickable { fps = option }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
                Text(
                    "60 fps usa mais processamento e pode aumentar o tamanho do vídeo.",
                    color = Color.White.copy(0.65f),
                    fontSize = 12.sp
                )
                Text(
                    "Música de cada card (opcional)",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    "Em “Automática”, o app escolhe entre as músicas listadas no card. Artista e álbum mais ouvidos ficam limitados ao artista/álbum exibido. Em “Ajustar trecho” você define início e fim (ou escolhe pelas linhas da letra); isso vale também para a música mais ouvida, cuja música não pode ser trocada.",
                    color = Color.White.copy(0.7f),
                    fontSize = 13.sp
                )
                cards.forEach { card ->
                    val current = chosen[card.cardId]
                    val enabled = card.kind == AudioKind.FREE ||
                        (card.kind == AudioKind.RESTRICTED && card.candidates.isNotEmpty())
                    // Música cujo trecho pode ser ajustado: a escolhida ou, no card travado, a própria música.
                    val clipTrack = current ?: card.lockedTrack
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(if (enabled) 0.08f else 0.04f))
                                .clickable(enabled = enabled) { picking = card }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(card.label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    when {
                                        card.kind == AudioKind.LOCKED ->
                                            card.lockedTrack?.let { "${it.title} · ${it.artist} (fixa)" }
                                                ?: "Usa a própria música (não editável)"
                                        !enabled -> "Nenhuma música permitida da biblioteca · Automática"
                                        current != null -> "${current.title} · ${current.artist}"
                                        else -> "Automática"
                                    },
                                    color = if (current != null) dialogAccent else Color.White.copy(0.72f),
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (enabled) Text("Trocar", color = dialogAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        if (clipTrack != null) {
                            val clip = clips[card.cardId]
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 10.dp, top = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    if (clip != null) {
                                        "Trecho: ${formatTime(clip.startMs)} – ${formatTime(clip.endMs)} · ${formatSeconds(clip.durationMs)} s"
                                    } else {
                                        "Trecho automático · $seconds s"
                                    },
                                    color = Color.White.copy(0.82f),
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "Ajustar trecho",
                                    color = dialogAccent,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(dialogAccent.copy(0.16f))
                                        .clickable { editingClip = card }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onGenerate(RetrospectiveVideoGenerator.VideoOptions(seconds, chosen, clips, fps))
            }) { Text("Gerar", color = dialogAccent, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Color.White.copy(0.8f)) }
        }
    )

    editingClip?.let { card ->
        val track = chosen[card.cardId] ?: card.lockedTrack
        if (track == null) {
            editingClip = null
        } else {
            val existing = clips[card.cardId]
            val defaultLenMs = seconds * 1000L
            val autoStart = RetrospectiveVideoGenerator.autoStartMs(track.duration, defaultLenMs)
            RetrospectiveClipEditorDialog(
                track = track,
                initialStartMs = existing?.startMs ?: autoStart,
                initialEndMs = existing?.endMs ?: (autoStart + defaultLenMs),
                hasExternalLrc = LyricsEmbedder.hasExternalLrc(track),
                loadLyrics = { source ->
                    when (source) {
                        LyricsEmbedder.Target.LRC_FILE -> LyricsEmbedder.loadExternalLrc(track)
                        LyricsEmbedder.Target.EMBEDDED -> LyricsExtractor.loadEmbedded(context, track)
                    }
                },
                onDismiss = { editingClip = null },
                onConfirm = { start, end ->
                    clips = clips + (card.cardId to CardClip(start, end))
                    editingClip = null
                }
            )
        }
    }

    picking?.let { card ->
        TrackPickerDialog(
            title = card.label,
            tracks = card.candidates,
            selected = chosen[card.cardId],
            onPick = { track ->
                // Trocar a música descarta o trecho antigo (o tempo era de outra faixa).
                clips = clips - card.cardId
                chosen = if (track == null) chosen - card.cardId else chosen + (card.cardId to track)
                picking = null
            },
            onDismiss = { picking = null }
        )
    }
}

private fun formatSeconds(ms: Long): String =
    java.util.Locale.US.let { String.format(it, "%.1f", ms / 1000.0) }

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L).toInt()
    return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

@Composable
private fun TrackPickerDialog(
    title: String,
    tracks: List<Track>,
    selected: Track?,
    onPick: (Track?) -> Unit,
    onDismiss: () -> Unit,
    allowAutomatic: Boolean = true
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(tracks, query) {
        val q = query.trim().lowercase()
        (if (q.isEmpty()) tracks else tracks.filter {
            it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) || it.album.lowercase().contains(q)
        }).take(200)
    }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Color.White, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (tracks.size > 8) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Buscar") },
                        singleLine = true,
                        colors = editFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (allowAutomatic) {
                        item {
                            PickerRow("Automática", "O app escolhe a música", selected == null) { onPick(null) }
                        }
                    }
                    items(shown, key = { it.id }) { track ->
                        PickerRow(track.title, track.artist, selected?.id == track.id) { onPick(track) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar", color = dialogAccent) } }
    )
}

@Composable
private fun PickerRow(title: String, sub: String, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) dialogAccent.copy(0.28f) else Color.White.copy(0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(title, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (sub.isNotBlank()) {
            Text(sub, color = Color.White.copy(0.7f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
