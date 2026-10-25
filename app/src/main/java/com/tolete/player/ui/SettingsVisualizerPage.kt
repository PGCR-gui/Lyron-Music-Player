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
internal fun VisualizerPage(
    presetCount: Int,
    intervalSec: Int,
    onPickFiles: () -> Unit,
    onClear: () -> Unit,
    onIntervalChange: (Int) -> Unit,
    changeSongWithInterval: Boolean = false,
    onChangeSongWithIntervalChange: (Boolean) -> Unit = {},
    changePresetOnSongChange: Boolean = true,
    onChangePresetOnSongChange: (Boolean) -> Unit = {},
    onManagePresets: () -> Unit = {},
    onOpenRecommendedPresets: () -> Unit = {}
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            "Na tela de reprodução, toque na capa para ligar o visualizador e toque nele para desligar. " +
                "A animação aparece só dentro da área da capa.",
            fontSize = 14.sp,
            color = Color.White.copy(0.6f),
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(0.08f))
                .clickable(onClick = onPickFiles)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Importar presets", fontSize = 16.sp)
                Text(
                    if (presetCount > 0) "$presetCount presets disponíveis (embutidos + importados)" else "Carregando presets embutidos…",
                    fontSize = 12.sp,
                    color = Color.White.copy(0.5f)
                )
            }
        }
        Text(
            "Presets .milk já vêm embutidos no app. Você ainda pode importar extras: arquivos .milk, " +
                "texturas (.jpg, .png, .tga) ou um .zip. Vários de uma vez são aceitos.",
            fontSize = 12.sp,
            color = Color.White.copy(0.4f),
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
        )
        if (presetCount > 0) {
            Text(
                "Remover presets importados (os embutidos voltam na próxima abertura)",
                modifier = Modifier
                    .clickable(onClick = onClear)
                    .padding(vertical = 8.dp),
                fontSize = 14.sp,
                color = Color(0xFFFF8A80)
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(0.08f))
                .clickable(onClick = onManagePresets)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Gerenciar presets", fontSize = 16.sp)
                Text(
                    "Buscar, favoritar ou excluir presets individuais",
                    fontSize = 12.sp,
                    color = Color.White.copy(0.5f)
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(0.45f)
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(0.08f))
                .clickable(onClick = onOpenRecommendedPresets)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Presets recomendados", fontSize = 16.sp)
                Text(
                    "Cream of the Crop no GitHub — coleção de alta qualidade",
                    fontSize = 12.sp,
                    color = Color.White.copy(0.5f)
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(0.45f)
            )
        }

        Spacer(Modifier.height(20.dp))
        Text(
            if (intervalSec == 0) "Trocar de preset: nunca"
            else "Trocar de preset a cada $intervalSec s",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        androidx.compose.material3.Slider(
            value = intervalSec.toFloat(),
            onValueChange = { onIntervalChange((it / 5f).toInt() * 5) },
            valueRange = 0f..120f,
            steps = 23
        )
        Text(
            "A troca de preset é aleatória e com transição suave. Com apenas 1 preset ela não acontece.",
            fontSize = 12.sp,
            color = Color.White.copy(0.4f)
        )

        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Mudar de música aleatoriamente no intervalo", fontSize = 15.sp)
                Text(
                    "Quando o timer do preset dispara, também troca para uma música aleatória da fila/biblioteca.",
                    fontSize = 12.sp,
                    color = Color.White.copy(0.45f)
                )
            }
            Switch(
                checked = changeSongWithInterval,
                onCheckedChange = onChangeSongWithIntervalChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF69F0AE)
                )
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Trocar preset ao mudar de música", fontSize = 15.sp)
                Text(
                    "Quando a faixa atual muda, escolhe um preset aleatório automaticamente.",
                    fontSize = 12.sp,
                    color = Color.White.copy(0.45f)
                )
            }
            Switch(
                checked = changePresetOnSongChange,
                onCheckedChange = onChangePresetOnSongChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF69F0AE)
                )
            )
        }
    }
}

