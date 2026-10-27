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
internal fun PlayerPage(
    autoFetchLyrics: Boolean,
    onAutoFetchLyricsChange: (Boolean) -> Unit,
    shuffleType: ShuffleType,
    onShuffleTypeChange: (ShuffleType) -> Unit,
    bitPerfect: Boolean,
    onBitPerfectChange: (Boolean) -> Unit,
    fadeMs: Int = 300,
    onFadeMsChange: (Int) -> Unit = {},
    playCountThreshold: Int = 70,
    onPlayCountThresholdChange: (Int) -> Unit = {},
    onOpenAudioOutput: () -> Unit = {},
    seekBarStyle: SeekBarStyle = SeekBarStyle.Standard,
    onSeekBarStyleChange: (SeekBarStyle) -> Unit = {}
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            "Letras",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Busca automática de letras", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(
                    "Quando a faixa não tiver letra nas tags nem arquivo .lrc, busca em lrclib.net",
                    fontSize = 13.sp,
                    color = Color.White.copy(0.55f)
                )
            }
            Switch(checked = autoFetchLyrics, onCheckedChange = onAutoFetchLyricsChange)
        }
        Text(
            "Fonte: lrclib.net · requer conexão com a internet",
            fontSize = 12.sp,
            color = Color.White.copy(0.4f),
            modifier = Modifier.padding(top = 12.dp, bottom = 20.dp)
        )

        Text(
            "Aleatório",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text("Tipo de aleatório", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text(
            "Define como o botão Aleatório da tela de reprodução funciona",
            fontSize = 13.sp,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 10.dp)
        )
        ShuffleTypeOption(
            title = "Absoluto",
            subtitle = "Embaralha a ordem de reprodução dinamicamente (modo padrão)",
            selected = shuffleType == ShuffleType.ABSOLUTE,
            onClick = { onShuffleTypeChange(ShuffleType.ABSOLUTE) }
        )
        Spacer(Modifier.height(8.dp))
        ShuffleTypeOption(
            title = "Fila aleatória",
            subtitle = "Gera uma fila fixa embaralhada a partir da música atual",
            selected = shuffleType == ShuffleType.QUEUE,
            onClick = { onShuffleTypeChange(ShuffleType.QUEUE) }
        )

        Spacer(Modifier.height(24.dp))
        Text(
            "Áudio",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Bit-perfect (DAC)", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(
                    "Tenta reduzir processamento de áudio para saída mais fiel em DACs USB/externos. Pode exigir reiniciar a reprodução.",
                    fontSize = 13.sp,
                    color = Color.White.copy(0.55f)
                )
            }
            Switch(checked = bitPerfect, onCheckedChange = onBitPerfectChange)
        }

        Spacer(Modifier.height(20.dp))
        Text("Fade play/pause", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text(
            if (fadeMs == 0) "Desligado (troca instantânea)"
            else "Duração: ${fadeMs} ms",
            fontSize = 13.sp,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        androidx.compose.material3.Slider(
            value = fadeMs.toFloat(),
            onValueChange = { onFadeMsChange(it.toInt()) },
            valueRange = 0f..2000f,
            steps = 39
        )

        Spacer(Modifier.height(24.dp))
        Text(
            "Barra de progresso (Now Playing)",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            "Estilo visual da seekbar na tela Tocando agora",
            fontSize = 13.sp,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 10.dp)
        )
        val seekStyles = listOf(
            SeekBarStyle.Normal to "Normal — fina e discreta",
            SeekBarStyle.Standard to "Standard — gradiente + thumb",
            SeekBarStyle.Wavy to "Wavy — onda senoidal",
            SeekBarStyle.Thick to "Thick — trilha grossa",
            SeekBarStyle.Slim to "Slim — bem fina",
            SeekBarStyle.Liquid to "Liquid — bolha no progresso"
        )
        seekStyles.forEach { (style, label) ->
            val selected = seekBarStyle == style
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) Color.White.copy(0.14f) else Color.White.copy(0.06f))
                    .clickable { onSeekBarStyleChange(style) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selected,
                    onClick = { onSeekBarStyleChange(style) }
                )
                Text(label, fontSize = 15.sp, modifier = Modifier.padding(start = 4.dp))
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "Histórico / Mais tocadas",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text("Contar play a partir de", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text(
            "A faixa só entra no histórico e em Mais Tocadas após tocar pelo menos ${playCountThreshold}% da duração",
            fontSize = 13.sp,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("70%", fontSize = 12.sp, color = Color.White.copy(0.5f))
            androidx.compose.material3.Slider(
                value = playCountThreshold.toFloat(),
                onValueChange = { onPlayCountThresholdChange(it.toInt()) },
                valueRange = 70f..100f,
                steps = 5,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            Text("100%", fontSize = 12.sp, color = Color.White.copy(0.5f))
        }
        Text(
            "Atual: ${playCountThreshold}%",
            fontSize = 13.sp,
            color = Color.White.copy(0.7f),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(0.08f))
                .clickable(onClick = onOpenAudioOutput)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Saída de áudio", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(
                    "Backend (AudioTrack, AAudio…) e taxa de amostragem máxima",
                    fontSize = 13.sp,
                    color = Color.White.copy(0.55f)
                )
            }
            Text("›", fontSize = 22.sp, color = Color.White.copy(0.4f))
        }
    }
}

@Composable
internal fun AudioOutputPage(
    audioOutput: MusicViewModel.AudioOutputBackend,
    onAudioOutputChange: (MusicViewModel.AudioOutputBackend) -> Unit,
    forceMaxSampleRate: Boolean,
    onForceMaxSampleRateChange: (Boolean) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            "Backend de saída",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            "Escolha o pipeline de áudio. A mudança é salva e aplicada na próxima sessão de reprodução (reinicie a faixa se necessário).",
            fontSize = 13.sp,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 12.dp)
        )
        val options = listOf(
            MusicViewModel.AudioOutputBackend.DEFAULT to ("Padrão do sistema" to "Deixa o ExoPlayer/Media3 escolher"),
            MusicViewModel.AudioOutputBackend.AUDIOTRACK to ("AudioTrack" to "API clássica do Android"),
            MusicViewModel.AudioOutputBackend.AAUDIO to ("AAudio" to "Baixa latência (API 26+)"),
            MusicViewModel.AudioOutputBackend.OPENSLES to ("OpenSL ES" to "Legado, ampla compatibilidade")
        )
        options.forEach { (id, labels) ->
            val (title, sub) = labels
            Spacer(Modifier.height(6.dp))
            ShuffleTypeOption(
                title = title,
                subtitle = sub,
                selected = audioOutput == id,
                onClick = { onAudioOutputChange(id) }
            )
        }

        Spacer(Modifier.height(28.dp))
        Text(
            "Amostragem",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Forçar maior amostragem", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(
                    "Tenta usar a maior taxa de amostragem permitida pela saída atual (útil em DACs USB). Pode falhar em alguns dispositivos.",
                    fontSize = 13.sp,
                    color = Color.White.copy(0.55f)
                )
            }
            Switch(checked = forceMaxSampleRate, onCheckedChange = onForceMaxSampleRateChange)
        }
        Text(
            "Nota: o ExoPlayer aplica o backend na criação do player. Pare e toque de novo após alterar.",
            fontSize = 12.sp,
            color = Color.White.copy(0.4f),
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}


@Composable
internal fun ShuffleTypeOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color.White.copy(0.14f) else Color.White.copy(0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 12.sp, color = Color.White.copy(0.55f))
        }
        if (selected) {
            Text("✓", fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

