package com.tolete.player.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tolete.player.data.LibrarySettings
import com.tolete.player.data.SourceFolder
import com.tolete.player.ui.MusicViewModel.AppThemeId
import com.tolete.player.ui.MusicViewModel.ShuffleType
import java.io.File

@Composable
internal fun LibraryPage(
    folders: List<SourceFolder>,
    totalTracks: Int,
    settings: LibrarySettings,
    allFilesMode: Boolean,
    onOpenFolders: () -> Unit,
    onSplitArtistsChange: (Boolean) -> Unit,
    onArtistSeparatorsChange: (String) -> Unit,
    onArtistExceptionsChange: (String) -> Unit,
    onSplitGenresChange: (Boolean) -> Unit,
    onGenreSeparatorsChange: (String) -> Unit,
    onAllFilesModeChange: (Boolean) -> Unit,
    onRequestAllFilesPermission: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
    ) {
        // Acesso a todos os arquivos (método principal — sem SAF)
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Gerenciar todos os arquivos", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "Modo principal: lê e apaga músicas em todo o armazenamento (requer permissão \"Acesso a todos os arquivos\")",
                        fontSize = 13.sp,
                        color = Color.White.copy(0.55f)
                    )
                }
                Switch(
                    checked = allFilesMode,
                    onCheckedChange = { enabled ->
                        if (enabled) onRequestAllFilesPermission()
                        else onAllFilesModeChange(false)
                    }
                )
            }
            if (allFilesMode) {
                Text(
                    "Modo ativo · acesso total ao armazenamento",
                    fontSize = 12.sp,
                    color = Color(0xFF81C784),
                    modifier = Modifier.padding(top = 6.dp)
                )
            } else {
                Text(
                    "Recomendado: ative para o app funcionar sem pastas SAF",
                    fontSize = 12.sp,
                    color = Color(0xFFFFB74D),
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        EntryRow(
            SettingsEntry(
                Icons.Rounded.Folder,
                "Pastas extras (opcional)",
                when (folders.size) {
                    0 -> if (allFilesMode) "Usando todos os arquivos" else "Nenhuma pasta · ative o acesso total"
                    1 -> "1 pasta · $totalTracks músicas"
                    else -> "${folders.size} pastas · $totalTracks músicas"
                },
                onOpenFolders
            )
        )

        Text(
            "Tags",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp)
        )

        SeparatorOption(
            title = "Separar artistas",
            description = "Trata os separadores da tag de artista como vários artistas",
            enabled = settings.splitArtists,
            separators = settings.artistSeparators,
            onEnabledChange = onSplitArtistsChange,
            onSeparatorsChange = onArtistSeparatorsChange
        )

        if (settings.splitArtists) {
            ArtistExceptionsField(
                value = settings.artistExceptions,
                onValueChange = onArtistExceptionsChange
            )
        }

        SeparatorOption(
            title = "Separar gêneros",
            description = "Trata os separadores da tag de gênero como vários gêneros",
            enabled = settings.splitGenres,
            separators = settings.genreSeparators,
            onEnabledChange = onSplitGenresChange,
            onSeparatorsChange = onGenreSeparatorsChange
        )
        Spacer(Modifier.height(24.dp))
    }
}


@Composable
internal fun ArtistExceptionsField(
    value: String,
    onValueChange: (String) -> Unit
) {
    var text by remember { mutableStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
        Text(
            "Exceções em artistas",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            "Nomes que não serão separados (um por linha, ou separados por vírgula). Ex.: AC/DC, Crosby, Stills & Nash",
            fontSize = 12.sp,
            color = Color.White.copy(0.5f),
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
        )
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                onValueChange(it)
            },
            placeholder = { Text("AC/DC\nCrosby, Stills & Nash", color = Color.White.copy(0.35f)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 5,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White.copy(0.9f),
                focusedBorderColor = Color(0xFFBB86FC),
                unfocusedBorderColor = Color.White.copy(0.35f),
                cursorColor = Color(0xFFBB86FC),
                focusedContainerColor = Color.White.copy(0.06f),
                unfocusedContainerColor = Color.White.copy(0.04f)
            )
        )
    }
}

@Composable
internal fun SeparatorOption(
    title: String,
    description: String,
    enabled: Boolean,
    separators: String,
    onEnabledChange: (Boolean) -> Unit,
    onSeparatorsChange: (String) -> Unit
) {
    // texto local: evita o cursor "pular" ao digitar (o valor vai para o app a cada tecla)
    var text by remember { mutableStateOf(separators) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(description, fontSize = 13.sp, color = Color.White.copy(0.55f))
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
        if (enabled) {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    onSeparatorsChange(it)
                },
                label = { Text("Separadores (separados por espaço)") },
                placeholder = { Text("; / feat.", color = Color.White.copy(0.35f)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White.copy(0.9f),
                    focusedBorderColor = Color(0xFFBB86FC),
                    unfocusedBorderColor = Color.White.copy(0.35f),
                    cursorColor = Color(0xFFBB86FC),
                    focusedLabelColor = Color(0xFFBB86FC),
                    unfocusedLabelColor = Color.White.copy(0.55f),
                    focusedContainerColor = Color.White.copy(0.06f),
                    unfocusedContainerColor = Color.White.copy(0.04f)
                )
            )
        }
    }
}


@Composable
internal fun FoldersPage(
    folders: List<SourceFolder>,
    totalTracks: Int,
    isScanning: Boolean,
    scanCount: Int,
    onAddFolder: () -> Unit,
    onRemoveFolder: (String) -> Unit,
    onRefresh: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        if (isScanning) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
            Text("Lendo… $scanCount", fontSize = 13.sp, color = Color.White.copy(0.6f))
        }
        if (folders.isEmpty()) {
            Text(
                "Nenhuma pasta na biblioteca.",
                modifier = Modifier.padding(vertical = 24.dp),
                color = Color.White.copy(0.6f)
            )
        }
        LazyColumn(Modifier.weight(1f)) {
            items(folders, key = { it.uri }) { f ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(f.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(f.path, fontSize = 12.sp, color = Color.White.copy(0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (f.trackCount == 1) "1 faixa" else "${f.trackCount} faixas",
                            fontSize = 12.sp,
                            color = Color.White.copy(0.45f)
                        )
                    }
                    IconButton(onClick = { onRemoveFolder(f.uri) }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Remover", tint = Color.White.copy(0.7f))
                    }
                }
            }
        }
        OutlinedButton(
            onClick = onAddFolder,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Adicionar pasta")
        }
        OutlinedButton(
            onClick = onRefresh,
            enabled = folders.isNotEmpty() && !isScanning,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Atualizar biblioteca")
        }
    }
}
