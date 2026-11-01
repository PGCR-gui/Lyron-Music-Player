package com.tolete.player.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tolete.player.ui.MusicViewModel.BackupOptions

@Composable
internal fun BackupPage(
    onExport: (BackupOptions) -> Unit,
    onImport: (BackupOptions) -> Unit
) {
    var preferences by remember { mutableStateOf(true) }
    var playlistsFavorites by remember { mutableStateOf(true) }
    var playHistory by remember { mutableStateOf(true) }
    var playCounts by remember { mutableStateOf(true) }
    var librarySettings by remember { mutableStateOf(true) }
    var appearance by remember { mutableStateOf(true) }
    var artistPhotos by remember { mutableStateOf(true) }
    var trackBlacklist by remember { mutableStateOf(true) }

    fun current() = BackupOptions(
        preferences = preferences,
        playlistsFavorites = playlistsFavorites,
        playHistory = playHistory,
        playCounts = playCounts,
        librarySettings = librarySettings,
        appearance = appearance,
        artistPhotos = artistPhotos,
        trackBlacklist = trackBlacklist
    )

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            "Escolha o que incluir na exportação ou aplicar na importação.",
            fontSize = 14.sp,
            color = Color.White.copy(0.6f),
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Text("Dados", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White.copy(0.5f))
        BackupCheck("Preferências gerais do player", preferences) { preferences = it }
        BackupCheck("Playlists e favoritos", playlistsFavorites) { playlistsFavorites = it }
        BackupCheck("Histórico de reprodução (CSV / ordem)", playHistory) { playHistory = it }
        BackupCheck("Contagens / Mais tocadas", playCounts) { playCounts = it }
        BackupCheck("Configurações de biblioteca", librarySettings) { librarySettings = it }
        BackupCheck("Aparência (tema, fontes, cores)", appearance) { appearance = it }
        BackupCheck("Fotos de artistas", artistPhotos) { artistPhotos = it }
        BackupCheck("Blacklist de faixas", trackBlacklist) { trackBlacklist = it }

        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = { onExport(current()) }, modifier = Modifier.fillMaxWidth()) {
            Text("Exportar backup (ZIP)")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { onImport(current()) }, modifier = Modifier.fillMaxWidth()) {
            Text("Importar backup (ZIP ou JSON)")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun BackupCheck(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, fontSize = 15.sp, modifier = Modifier.padding(start = 4.dp))
    }
}
