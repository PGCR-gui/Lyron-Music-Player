package com.tolete.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tolete.player.data.Track
import com.tolete.player.video.RetrospectiveVideoGenerator
import com.tolete.player.video.RetrospectiveVideoGenerator.AudioKind
import com.tolete.player.video.RetrospectiveVideoGenerator.CardAudioOptions
import kotlin.math.roundToInt

/** Opções antes de gerar os vídeos: duração, música por card e recorte manual. */
@Composable
internal fun RetrospectiveVideoOptionsDialog(
    cards: List<CardAudioOptions>,
    initial: RetrospectiveVideoGenerator.VideoOptions,
    onDismiss: () -> Unit,
    onGenerate: (RetrospectiveVideoGenerator.VideoOptions) -> Unit
) {
    var duration by remember { mutableFloatStateOf(initial.durationSec.toFloat()) }
    var chosen by remember { mutableStateOf(initial.audioByCard) }
    var segmentStarts by remember { mutableStateOf(initial.audioStartMsByCard) }
    var fps by remember { mutableStateOf(initial.fps) }
    var picking by remember { mutableStateOf<CardAudioOptions?>(null) }
    val seconds = duration.toInt()

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Opções dos vídeos", color = Color.White, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Duração de cada vídeo: $seconds s", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
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
                    "Em “Automática”, o app escolhe entre as músicas listadas no card. Artista e álbum mais ouvidos ficam limitados ao artista/álbum exibido. Ao escolher manualmente, ajuste também o início do trecho.",
                    color = Color.White.copy(0.7f),
                    fontSize = 13.sp
                )
                cards.forEach { card ->
                    val current = chosen[card.cardId]
                    val enabled = card.kind == AudioKind.FREE ||
                        (card.kind == AudioKind.RESTRICTED && card.candidates.isNotEmpty())
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
                                        card.kind == AudioKind.LOCKED -> "Usa a própria música (não editável)"
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
                        if (current != null) {
                            val maxStartMs = (current.duration - seconds * 1000L).coerceAtLeast(0L)
                            val startMs = (segmentStarts[card.cardId] ?: 0L).coerceIn(0L, maxStartMs)
                            Text(
                                "Trecho: ${formatTime(startMs)} – ${formatTime((startMs + seconds * 1000L).coerceAtMost(current.duration))}",
                                color = Color.White.copy(0.82f),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(start = 10.dp, top = 2.dp)
                            )
                            Slider(
                                value = startMs.toFloat(),
                                onValueChange = { value ->
                                    segmentStarts = segmentStarts +
                                        (card.cardId to (value / 1000f).roundToInt().toLong() * 1000L)
                                },
                                valueRange = 0f..maxStartMs.toFloat().coerceAtLeast(1f),
                                enabled = maxStartMs > 0L,
                                colors = SliderDefaults.colors(
                                    thumbColor = dialogAccent,
                                    activeTrackColor = dialogAccent,
                                    inactiveTrackColor = Color.White.copy(0.25f)
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onGenerate(RetrospectiveVideoGenerator.VideoOptions(seconds, chosen, segmentStarts, fps))
            }) { Text("Gerar", color = dialogAccent, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Color.White.copy(0.8f)) }
        }
    )

    picking?.let { card ->
        TrackPickerDialog(
            title = card.label,
            tracks = card.candidates,
            selected = chosen[card.cardId],
            onPick = { track ->
                if (track == null) {
                    chosen = chosen - card.cardId
                    segmentStarts = segmentStarts - card.cardId
                } else {
                    chosen = chosen + (card.cardId to track)
                    segmentStarts = segmentStarts + (card.cardId to 0L)
                }
                picking = null
            },
            onDismiss = { picking = null }
        )
    }
}

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
    onDismiss: () -> Unit
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
                    item {
                        PickerRow("Automática", "O app escolhe a música", selected == null) { onPick(null) }
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
