package com.tolete.player.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tolete.player.data.ScrobbleEdits

internal val dialogAccent = Color(0xFFB39DDB)

@Composable
internal fun editFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = dialogAccent,
    unfocusedBorderColor = Color.White.copy(0.45f),
    cursorColor = dialogAccent,
    focusedLabelColor = dialogAccent,
    unfocusedLabelColor = Color.White.copy(0.8f),
    focusedContainerColor = Color.White.copy(0.08f),
    unfocusedContainerColor = Color.White.copy(0.05f)
)

/**
 * Gerenciador das edições de tags de scrobble salvas: lista, busca, edita e apaga.
 * As mesmas edições alimentam o “editor reverso” (dashboard e retrospectiva).
 */
@Composable
internal fun ScrobbleEditsManagerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val version by ScrobbleEdits.version.collectAsState()
    val all = remember(version) { ScrobbleEdits.list() }
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<ScrobbleEdits.Edit?>(null) }
    var deleting by remember { mutableStateOf<ScrobbleEdits.Edit?>(null) }

    val shown = remember(all, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) all
        else all.filter { e ->
            listOf(e.origTrack, e.origArtist, e.origAlbum, e.track, e.artist, e.album, e.albumArtist)
                .any { it?.lowercase()?.contains(q) == true }
        }
    }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Edições salvas (${all.size})", color = Color.White, fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Buscar") },
                    singleLine = true,
                    colors = editFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (shown.isEmpty()) {
                    Text(
                        if (all.isEmpty()) "Nenhuma edição salva." else "Nada encontrado.",
                        color = Color.White.copy(0.75f),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(shown) { e ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(0.08f))
                                    .clickable { editing = e }
                                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "${e.origArtist.orEmpty()} — ${e.origTrack.orEmpty()}",
                                        color = Color.White.copy(0.8f),
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${e.artist.ifBlank { "(igual)" }} — ${e.track.ifBlank { "(igual)" }}",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (e.album.isNotBlank() || e.albumArtist.isNotBlank()) {
                                        Text(
                                            listOf(e.album, e.albumArtist).filter { it.isNotBlank() }.joinToString(" · "),
                                            color = Color.White.copy(0.72f),
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                IconButton(onClick = { editing = e }) {
                                    Icon(Icons.Rounded.Edit, contentDescription = "Editar", tint = Color.White.copy(0.8f), modifier = Modifier.size(20.dp))
                                }
                                IconButton(onClick = { deleting = e }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Apagar", tint = Color(0xFFFF8A80), modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar", color = dialogAccent) } }
    )

    editing?.let { target ->
        EditEditDialog(
            edit = target,
            onDismiss = { editing = null },
            onSave = { updated ->
                val ok = ScrobbleEdits.replace(target, updated)
                Toast.makeText(context, if (ok) "Edição atualizada" else "Não foi possível salvar", Toast.LENGTH_SHORT).show()
                editing = null
            }
        )
    }

    deleting?.let { target ->
        AppAlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Apagar edição?", color = Color.White) },
            text = {
                Text(
                    "“${target.origArtist.orEmpty()} — ${target.origTrack.orEmpty()}” volta a ser enviada " +
                        "ao Last.fm com as tags originais do arquivo.",
                    color = Color.White.copy(0.9f),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val ok = ScrobbleEdits.remove(target)
                    Toast.makeText(context, if (ok) "Edição apagada" else "Não foi possível apagar", Toast.LENGTH_SHORT).show()
                    deleting = null
                }) { Text("Apagar", color = Color(0xFFFF8A80)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar", color = Color.White.copy(0.8f)) } }
        )
    }
}

@Composable
private fun EditEditDialog(
    edit: ScrobbleEdits.Edit,
    onDismiss: () -> Unit,
    onSave: (ScrobbleEdits.Edit) -> Unit
) {
    var track by remember(edit) { mutableStateOf(edit.track) }
    var artist by remember(edit) { mutableStateOf(edit.artist) }
    var album by remember(edit) { mutableStateOf(edit.album) }
    var albumArtist by remember(edit) { mutableStateOf(edit.albumArtist) }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modificar edição", color = Color.White, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Original: ${edit.origArtist.orEmpty()} — ${edit.origTrack.orEmpty()}" +
                        (edit.origAlbum?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""),
                    color = Color.White.copy(0.85f),
                    fontSize = 13.sp
                )
                OutlinedTextField(track, { track = it }, label = { Text("Título") }, singleLine = true, colors = editFieldColors(), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(artist, { artist = it }, label = { Text("Artista") }, singleLine = true, colors = editFieldColors(), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(album, { album = it }, label = { Text("Álbum") }, singleLine = true, colors = editFieldColors(), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(albumArtist, { albumArtist = it }, label = { Text("Artista do álbum") }, singleLine = true, colors = editFieldColors(), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(2.dp))
                Text(
                    "Campo vazio = mantém a tag original.",
                    color = Color.White.copy(0.7f),
                    fontSize = 13.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    edit.copy(
                        track = track.trim(),
                        artist = artist.trim(),
                        album = album.trim(),
                        albumArtist = albumArtist.trim()
                    )
                )
            }) { Text("Salvar", color = dialogAccent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Color.White.copy(0.8f)) } }
    )
}
