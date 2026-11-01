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
import androidx.compose.material.icons.rounded.Edit
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
    onRequestAllFilesPermission: () -> Unit,
    onIgnoredFoldersChange: (String) -> Unit = {},
    onPickIgnoredFolder: () -> Unit = {},
    onMinDurationSecChange: (Int) -> Unit = {},
    lastFmUsername: String? = null,
    onLastFmUsernameChange: (String?) -> Unit = {},
    lastFmSyncMessage: String? = null,
    onSyncLastFm: () -> Unit = {},
    onClearPlaybackHistory: () -> Unit = {},
    onClearFavorites: () -> Unit = {}
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
                    "Devido a funções avançadas do app, ele precisa dessa permissão para funcionar",
                    fontSize = 12.sp,
                    color = Color(0xFFFFB74D),
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        EntryRow(
            SettingsEntry(
                Icons.Rounded.Folder,
                "Organizar biblioteca",
                "Pasta principal, renomear e mover por tags",
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

        Text(
            "Filtros de biblioteca",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp)
        )

        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("Pastas a ignorar (blacklist)", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(
                "Escolha pastas pelo seletor do sistema. Faixas cujo caminho contenha o nome da pasta serão excluídas da biblioteca.",
                fontSize = 12.sp,
                color = Color.White.copy(0.5f),
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
            val ignoredList = settings.ignoredFolders
                .split("\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            ignoredList.forEach { folder ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(0.08f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Folder, contentDescription = null, tint = Color.White.copy(0.7f), modifier = Modifier.size(20.dp))
                    Text(folder, Modifier.weight(1f).padding(horizontal = 10.dp), fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = {
                        val next = ignoredList.filter { it != folder }.joinToString("\n")
                        onIgnoredFoldersChange(next)
                    }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Remover", tint = Color(0xFFE57373), modifier = Modifier.size(18.dp))
                    }
                }
            }
            OutlinedButton(
                onClick = onPickIgnoredFolder,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Adicionar pasta (gerenciador interno)")
            }
        Spacer(Modifier.height(20.dp))
        Text("Histórico e plays", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text(
            "O histórico e as Mais tocadas usam o arquivo playback history.csv em Lyron/.",
            fontSize = 13.sp,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        OutlinedButton(
            onClick = onClearPlaybackHistory,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Limpar histórico de plays")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onClearFavorites,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Limpar favoritas")
        }


            Spacer(Modifier.height(16.dp))
            val minSec = settings.minDurationSec
            Text(
                if (minSec <= 0) "Duração mínima: desligada"
                else "Duração mínima: $minSec s (faixas menores são ignoradas)",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                "Arraste entre 0-50 segundos. Em 0 (extrema esquerda) o filtro fica desligado.",
                fontSize = 12.sp,
                color = Color.White.copy(0.5f),
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )
            Slider(
                value = minSec.toFloat(),
                onValueChange = { onMinDurationSecChange(it.toInt()) },
                valueRange = 0f..50f,
                steps = 49,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White.copy(0.85f),
                    inactiveTrackColor = Color.White.copy(0.25f)
                )
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0 (off)", fontSize = 11.sp, color = Color.White.copy(0.45f))
                Text("2 s", fontSize = 11.sp, color = Color.White.copy(0.45f))
                Text("50 s", fontSize = 11.sp, color = Color.White.copy(0.45f))
            }
        }

        // Last.fm (somente leitura: histórico e loved — sem scrobbling)
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
            Text(
                "Last.fm",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(0.55f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                "Usuário (público)",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                "Importa histórico recente e faixas amadas para a biblioteca local. Sem scrobbling por enquanto.",
                fontSize = 13.sp,
                color = Color.White.copy(0.55f),
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
            )
            var userText by remember(lastFmUsername) { mutableStateOf(lastFmUsername.orEmpty()) }
            OutlinedTextField(
                value = userText,
                onValueChange = {
                    userText = it
                    onLastFmUsernameChange(it.trim().ifBlank { null })
                },
                label = { Text("Username Last.fm") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
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
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onSyncLastFm,
                    enabled = !userText.isBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Sincronizar")
                }
                if (!lastFmUsername.isNullOrBlank()) {
                    TextButton(onClick = {
                        userText = ""
                        onLastFmUsernameChange(null)
                    }) {
                        Text("Sair", color = Color(0xFFE57373))
                    }
                }
            }
            if (!lastFmSyncMessage.isNullOrBlank()) {
                Text(
                    lastFmSyncMessage!!,
                    fontSize = 12.sp,
                    color = Color.White.copy(0.65f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

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
            "Nomes que não serão separados (um por linha). Ex.: AC/DC",
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
            placeholder = { Text("AC/DC\nJoão Mineiro & Marciano", color = Color.White.copy(0.35f)) },
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
                placeholder = { Text("; / feat. & ft.", color = Color.White.copy(0.35f)) },
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


@Composable
internal fun OrganizeLibraryPage(
    libraryRootPath: String?,
    onPickLibraryRoot: () -> Unit,
    onBulkRenameByTags: () -> Unit,
    onMoveToLibraryRoot: () -> Unit,
    organizeStatus: String? = null
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            "Pasta principal",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            "Usada como destino ao mover músicas na função mover para pasta principal.",
            fontSize = 13.sp,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 10.dp)
        )
        Text(
            libraryRootPath?.ifBlank { null } ?: "Nenhuma pasta definida",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = if (libraryRootPath.isNullOrBlank()) Color.White.copy(0.45f) else Color.White
        )
        OutlinedButton(
            onClick = onPickLibraryRoot,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 20.dp)
        ) {
            Icon(Icons.Rounded.Folder, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (libraryRootPath.isNullOrBlank()) "Escolher pasta principal" else "Alterar pasta principal")
        }

        Text(
            "Renomear em massa por tags",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            "Escolha uma pasta: todas as músicas nela e nas subpastas serão renomeadas pelo padrão de tags.",
            fontSize = 13.sp,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 10.dp)
        )
        OutlinedButton(
            onClick = onBulkRenameByTags,
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
        ) {
            Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Escolher pasta e renomear")
        }

        Text(
            "Mover para pasta principal",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            "Escolha uma pasta de origem. As músicas dessa pasta (e das subpastas) vão para PastaPrincipal/Artista do Álbum/Álbum/. Se a tag de artista do álbum estiver vazia, usa o artista.",
            fontSize = 13.sp,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 10.dp)
        )
        OutlinedButton(
            onClick = onMoveToLibraryRoot,
            enabled = !libraryRootPath.isNullOrBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.Folder, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Escolher pasta e mover")
        }
        if (!organizeStatus.isNullOrBlank()) {
            Text(
                organizeStatus!!,
                fontSize = 12.sp,
                color = Color.White.copy(0.65f),
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
