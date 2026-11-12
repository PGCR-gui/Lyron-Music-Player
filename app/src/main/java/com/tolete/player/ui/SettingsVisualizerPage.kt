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
    switchMode: String = "TIME",
    onSwitchModeChange: (String) -> Unit = {},
    presetOrder: String = "RANDOM",
    onPresetOrderChange: (String) -> Unit = {},
    favoritesOnly: Boolean = false,
    onFavoritesOnlyChange: (Boolean) -> Unit = {},
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
                    "Cream of the Crop no GitHub — coleção completa de presets",
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
        Text("Modo de troca de preset", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        listOf(
            "TIME" to ("Trocar no tempo definido" to "Usa o intervalo abaixo enquanto a música toca."),
            "TRACK" to ("Trocar por música" to "Ignora o intervalo e escolhe outro preset ao mudar de faixa.")
        ).forEach { (mode, copy) ->
            Row(
                Modifier.fillMaxWidth().clickable { onSwitchModeChange(mode) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = switchMode == mode,
                    onClick = { onSwitchModeChange(mode) },
                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF69F0AE))
                )
                Column(Modifier.weight(1f)) {
                    Text(copy.first, fontSize = 14.sp)
                    Text(copy.second, fontSize = 12.sp, color = Color.White.copy(0.45f))
                }
            }
        }

        if (switchMode == "TIME") {
            Spacer(Modifier.height(8.dp))
            Text(
                if (intervalSec == 0) "Intervalo: desligado"
                else "Trocar de preset a cada $intervalSec s",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            androidx.compose.material3.Slider(
                value = intervalSec.toFloat(),
                onValueChange = { onIntervalChange((it / 5f).toInt() * 5) },
                valueRange = 0f..120f,
                steps = 23
            )
        }

        Spacer(Modifier.height(12.dp))
        Text("Ordem dos presets", fontSize = 15.sp, fontWeight = FontWeight.Medium)
        listOf("RANDOM" to "Aleatória", "SEQUENTIAL" to "Sequencial").forEach { (order, title) ->
            Row(
                Modifier.fillMaxWidth().clickable { onPresetOrderChange(order) }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = presetOrder == order,
                    onClick = { onPresetOrderChange(order) },
                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF69F0AE))
                )
                Text(title, fontSize = 14.sp)
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Tocar só presets favoritos", fontSize = 15.sp)
                Text(
                    "Limita as trocas automáticas aos presets marcados como favoritos.",
                    fontSize = 12.sp,
                    color = Color.White.copy(0.45f)
                )
            }
            Switch(
                checked = favoritesOnly,
                onCheckedChange = onFavoritesOnlyChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF69F0AE)
                )
            )
        }
    }
}
