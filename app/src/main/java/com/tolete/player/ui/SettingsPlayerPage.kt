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
import com.tolete.player.PlaybackService
import com.tolete.player.data.AudioOutputInfo
import com.tolete.player.data.LibrarySettings
import com.tolete.player.data.OutputSummary
import com.tolete.player.data.SourceFolder
import com.tolete.player.ui.MusicViewModel.AppThemeId
import com.tolete.player.ui.MusicViewModel.AudioOutputBackend
import com.tolete.player.ui.MusicViewModel.ShuffleType
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
internal fun PlayerPage(
    autoFetchLyrics: Boolean,
    onAutoFetchLyricsChange: (Boolean) -> Unit,
    shuffleType: ShuffleType,
    onShuffleTypeChange: (ShuffleType) -> Unit,
    fadeMs: Int = 300,
    onFadeMsChange: (Int) -> Unit = {},
    crossfadeMs: Int = 0,
    onCrossfadeMsChange: (Int) -> Unit = {},
    playCountThreshold: Int = 70,
    onPlayCountThresholdChange: (Int) -> Unit = {},
    playCountMinutes: Int = 0,
    onPlayCountMinutesChange: (Int) -> Unit = {},
    seekBarStyle: SeekBarStyle = SeekBarStyle.Standard,
    onSeekBarStyleChange: (SeekBarStyle) -> Unit = {},
    immersiveNowPlaying: Boolean = true,
    onImmersiveNowPlayingChange: (Boolean) -> Unit = {},
    audioOutput: AudioOutputBackend = AudioOutputBackend.DEFAULT,
    onAudioOutputChange: (AudioOutputBackend) -> Unit = {},
    bitPerfect: Boolean = false,
    onBitPerfectChange: (Boolean) -> Unit = {}
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
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
            "Saída de áudio",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            "Escolha como o áudio é enviado ao DAC USB ou ao fone com fio. A troca vale na hora (a fila e a posição são mantidas).",
            fontSize = 13.sp,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 10.dp)
        )
        ShuffleTypeOption(
            title = "Padrão do sistema",
            subtitle = "O Android escolhe a saída e o formato",
            selected = audioOutput == AudioOutputBackend.DEFAULT,
            onClick = { onAudioOutputChange(AudioOutputBackend.DEFAULT) }
        )
        Spacer(Modifier.height(8.dp))
        ShuffleTypeOption(
            title = "Fidelidade de Som",
            subtitle = "Prioriza DAC USB/fone com fio, conserva a taxa da faixa (44,1 kHz é convertido para 48 kHz por compatibilidade) e oferece Bit-perfect quando suportado",
            selected = audioOutput == AudioOutputBackend.FIDELITY,
            onClick = { onAudioOutputChange(AudioOutputBackend.FIDELITY) }
        )
        if (audioOutput == AudioOutputBackend.FIDELITY) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Bit-perfect (USB)", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "Envia o áudio sem alteração ao DAC USB, usando o driver de áudio USB do próprio Android 14+ (não precisa de driver extra). Só engata quando o DAC publica bit-perfect no mesmo formato da faixa; o volume do Android, o fade e o crossfade deixam de agir (use o volume do DAC). Veja o resultado em \"Saída em uso\".",
                        fontSize = 13.sp,
                        color = Color.White.copy(0.55f)
                    )
                }
                Switch(checked = bitPerfect, onCheckedChange = onBitPerfectChange)
            }
        }
        Spacer(Modifier.height(12.dp))
        AudioOutputInfoCard(mode = audioOutput)

        Spacer(Modifier.height(24.dp))
        Text(
            "Áudio",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
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

        Spacer(Modifier.height(16.dp))
        Text("Fade ao trocar de faixa", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text(
            if (crossfadeMs == 0) "Desligado"
            else "Duração: ${crossfadeMs} ms de sobreposição. Em saída exclusiva/bit-perfect, usa fade-in sem sobrepor para evitar conflito no DAC.",
            fontSize = 13.sp,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        androidx.compose.material3.Slider(
            value = crossfadeMs.toFloat(),
            onValueChange = { onCrossfadeMsChange(it.toInt()) },
            valueRange = 0f..5000f,
            steps = 49
        )

        Spacer(Modifier.height(24.dp))
        Text(
            "Tela Tocando agora",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Modo imersivo", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(
                    "Esconde a barra de status e a de navegação enquanto a tela Tocando agora estiver aberta. Deslize nas bordas para revelá-las temporariamente.",
                    fontSize = 13.sp,
                    color = Color.White.copy(0.55f)
                )
            }
            Switch(checked = immersiveNowPlaying, onCheckedChange = onImmersiveNowPlayingChange)
        }

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

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Contar também por tempo", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(
                    if (playCountMinutes > 0)
                        "O play conta ao atingir ${playCountThreshold}% ou $playCountMinutes min tocados — o que vier primeiro"
                    else
                        "Desligado: conta só pela porcentagem",
                    fontSize = 13.sp,
                    color = Color.White.copy(0.55f)
                )
            }
            Switch(
                checked = playCountMinutes > 0,
                onCheckedChange = { on -> onPlayCountMinutesChange(if (on) 3 else 0) }
            )
        }
        if (playCountMinutes > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("1 min", fontSize = 12.sp, color = Color.White.copy(0.5f))
                androidx.compose.material3.Slider(
                    value = playCountMinutes.toFloat(),
                    onValueChange = { onPlayCountMinutesChange(it.toInt().coerceIn(1, 6)) },
                    valueRange = 1f..6f,
                    steps = 4,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                Text("6 min", fontSize = 12.sp, color = Color.White.copy(0.5f))
            }
            Text(
                "Atual: $playCountMinutes min",
                fontSize = 13.sp,
                color = Color.White.copy(0.7f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

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


/** Abre o card "Saída em uso" num diálogo (usado pelo menu de três pontos do Now Playing). */
@Composable
internal fun AudioOutputInfoDialog(mode: AudioOutputBackend, onDismiss: () -> Unit) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Informações da saída de áudio", fontWeight = FontWeight.SemiBold) },
        text = { AudioOutputInfoCard(mode = mode) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Fechar") }
        }
    )
}

/** Campo de informação: mostra a saída que está sendo usada e o formato, atualizado ao vivo. */
@Composable
internal fun AudioOutputInfoCard(mode: AudioOutputBackend) {
    val ctx = LocalContext.current
    var info by remember { mutableStateOf<OutputSummary?>(null) }
    LaunchedEffect(mode) {
        // Releitura periódica: pega o DAC sendo plugado/desplugado e o formato aplicado à faixa atual.
        while (true) {
            info = withContext(Dispatchers.Default) {
                val modeLabel = when (mode) {
                    AudioOutputBackend.DEFAULT -> "Padrão do sistema"
                    AudioOutputBackend.FIDELITY -> "Fidelidade de Som"
                }
                AudioOutputInfo.summary(
                    ctx,
                    modeLabel,
                    mode != AudioOutputBackend.DEFAULT,
                    PlaybackService.appliedFloatOutput,
                    PlaybackService.appliedConversionInfo
                )
            }
            delay(1500)
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(0.06f))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Info,
                contentDescription = null,
                tint = Color.White.copy(0.7f),
                modifier = Modifier.size(18.dp)
            )
            Text(
                "Saída em uso",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        val i = info
        if (i == null) {
            Text(
                "Lendo a saída de áudio…",
                fontSize = 13.sp,
                color = Color.White.copy(0.55f),
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
            Spacer(Modifier.height(8.dp))
            OutputInfoRow("Dispositivo", i.deviceName)
            OutputInfoRow("Tipo", i.deviceType)
            OutputInfoRow("Formato", i.format)
            if (i.route.isNotBlank()) OutputInfoRow("Origem", i.route)
            i.notes.forEach { n ->
                Text(
                    n,
                    fontSize = 12.sp,
                    color = Color.White.copy(0.55f),
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun OutputInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, fontSize = 13.sp, color = Color.White.copy(0.55f), modifier = Modifier.width(92.dp))
        Text(value, fontSize = 13.sp, color = Color.White.copy(0.9f), modifier = Modifier.weight(1f))
    }
}
