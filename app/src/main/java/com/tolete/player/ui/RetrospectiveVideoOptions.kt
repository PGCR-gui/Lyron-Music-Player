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
import androidx.compose.material3.AlertDialog
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

private val panel = Color(0xFF1A1528)

/**
 * Opções antes de gerar os vídeos: duração (3–15 s) e música de cada card.
 * Escolher música é opcional: card em “Automática” usa a música escolhida pelo app, como antes.
 */
@Composable
internal fun RetrospectiveVideoOptionsDialog(
    cards: List<CardAudioOptions>,
    initial: RetrospectiveVideoGenerator.VideoOptions,
    onDismiss: () -> Unit,
    onGenerate: (RetrospectiveVideoGenerator.VideoOptions) -> Unit
) {
    var duration by remember { mutableFloatStateOf(initial.durationSec.toFloat()) }
    var chosen by remember { mutableStateOf(initial.audioByCard) }
    var picking by remember { mutableStateOf<CardAudioOptions?>(null) }
    val seconds = duration.toInt()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = panel,
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
                    "Música de cada card (opcional)",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    "Deixe em “Automática” para o app escolher. Nos cards de top 5 só dá para escolher entre os itens listados.",
                    color = Color.White.copy(0.7f),
                    fontSize = 13.sp
                )
                cards.forEach { card ->
                    val current = chosen[card.cardId]
                    val enabled = card.kind == AudioKind.FREE || (card.kind == AudioKind.RESTRICTED && card.candidates.isNotEmpty())
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
                                    !enabled -> "Nenhuma música da biblioteca encontrada · Automática"
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
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onGenerate(RetrospectiveVideoGenerator.VideoOptions(seconds, chosen))
            }) { Text("Gerar", color = dialogAccent, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("Cancelar", color = Color.White.copy(0.8f)) }
                TextButton(onClick = {
                    onGenerate(RetrospectiveVideoGenerator.VideoOptions(seconds, emptyMap()))
                }) { Text("Pular músicas", color = Color.White.copy(0.8f)) }
            }
        }
    )

    picking?.let { card ->
        TrackPickerDialog(
            title = card.label,
            tracks = card.candidates,
            selected = chosen[card.cardId],
            onPick = { t ->
                chosen = if (t == null) chosen - card.cardId else chosen + (card.cardId to t)
                picking = null
            },
            onDismiss = { picking = null }
        )
    }
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
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = panel,
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
                    items(shown, key = { it.id }) { t ->
                        PickerRow(t.title, t.artist, selected?.id == t.id) { onPick(t) }
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
