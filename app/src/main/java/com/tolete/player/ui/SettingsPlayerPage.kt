package com.tolete.player.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.tolete.player.data.LyricsEmbedder
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
    autoLyricsSaveTarget: LyricsEmbedder.Target = LyricsEmbedder.Target.LRC_FILE,
    onAutoLyricsSaveTargetChange: (LyricsEmbedder.Target) -> Unit = {},
    preferNeteaseRomanization: Boolean = false,
    onPreferNeteaseRomanizationChange: (Boolean) -> Unit = {},
    lyricsPopupEnabled: Boolean = false,
    onLyricsPopupEnabledChange: (Boolean) -> Unit = {},
    searchStaggerMs: Int = 50,
    onSearchStaggerMsChange: (Int) -> Unit = {},
    lyricsTypeOrder: List<String> = listOf("WORD", "SYNCED", "PLAIN"),
    onMoveLyricsType: (Int, Int) -> Unit = { _, _ -> },
    lyricsSourceOrder: List<String> = listOf("LYRON_REPOSITORY", "LRCLIB", "QQ_MUSIC", "NETEASE", "KUGOU"),
    onMoveLyricsSource: (Int, Int) -> Unit = { _, _ -> },
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
    seekBarWaveformTheme: WaveformTheme = WaveformTheme.BARS,
    onSeekBarWaveformThemeChange: (WaveformTheme) -> Unit = {},
    immersiveNowPlaying: Boolean = true,
    onImmersiveNowPlayingChange: (Boolean) -> Unit = {},
    audioOutput: AudioOutputBackend = AudioOutputBackend.DEFAULT,
    onAudioOutputChange: (AudioOutputBackend) -> Unit = {},
    bitPerfect: Boolean = false,
    onBitPerfectChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val hasOverlayPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
        onLyricsPopupEnabledChange(granted)
    }
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

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Letras em popup sobre outros apps", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(
                    "Mostra a linha atual da letra durante a reprodução. O Android pedirá permissão de sobreposição.",
                    fontSize = 13.sp,
                    color = Color.White.copy(0.55f)
                )
            }
            Switch(
                checked = lyricsPopupEnabled && hasOverlayPermission,
                onCheckedChange = { enabled ->
                    if (!enabled) {
                        onLyricsPopupEnabledChange(false)
                    } else if (!hasOverlayPermission) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        overlayPermissionLauncher.launch(intent)
                    } else {
                        onLyricsPopupEnabledChange(true)
                    }
                }
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Busca automática de letras", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(
                    "Quando não houver letra local, busca online e salva no destino escolhido abaixo. Usa a ordem de tipo e servidor configurada.",
                    fontSize = 13.sp,
                    color = Color.White.copy(0.55f)
                )
            }
            Switch(checked = autoFetchLyrics, onCheckedChange = onAutoFetchLyricsChange)
        }

        if (autoFetchLyrics) {
            Spacer(Modifier.height(12.dp))
            Text("Salvar automaticamente em", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            ShuffleTypeOption(
                title = "Arquivo .lrc ao lado da música",
                subtitle = "Se a pasta não aceitar escrita, mantém o fallback histórico para as tags do áudio",
                selected = autoLyricsSaveTarget == LyricsEmbedder.Target.LRC_FILE,
                onClick = { onAutoLyricsSaveTargetChange(LyricsEmbedder.Target.LRC_FILE) }
            )
            Spacer(Modifier.height(6.dp))
            ShuffleTypeOption(
                title = "Tags embutidas no arquivo",
                subtitle = "Grava a letra nas tags do áudio",
                selected = autoLyricsSaveTarget == LyricsEmbedder.Target.EMBEDDED,
                onClick = { onAutoLyricsSaveTargetChange(LyricsEmbedder.Target.EMBEDDED) }
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Priorizar romanização no NetEase", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text("Na escolha automática, prefere romalrc quando a fonte o disponibiliza. A busca manual continua mostrando as duas variantes.", fontSize = 12.sp, color = Color.White.copy(0.55f))
                }
                Switch(
                    checked = preferNeteaseRomanization,
                    onCheckedChange = onPreferNeteaseRomanizationChange
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Prioridade do tipo de letra",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(0.75f)
            )
            Text(
                "O primeiro da lista tem preferência na escolha automática",
                fontSize = 12.sp,
                color = Color.White.copy(0.45f),
                modifier = Modifier.padding(bottom = 6.dp)
            )
            PriorityOrderList(
                items = lyricsTypeOrder,
                labelOf = {
                    when (it) {
                        "WORD" -> "Palavra por palavra"
                        "SYNCED" -> "Sincronizada"
                        "PLAIN" -> "Simples"
                        else -> it
                    }
                },
                onMove = onMoveLyricsType
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Prioridade de servidor",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(0.75f)
            )
            Text(
                "Ordem de fallback entre fontes online",
                fontSize = 12.sp,
                color = Color.White.copy(0.45f),
                modifier = Modifier.padding(bottom = 6.dp)
            )
            PriorityOrderList(
                items = lyricsSourceOrder,
                labelOf = {
                    when (it) {
                        "LYRON_REPOSITORY" -> "Lyron · GitHub"
                        "LRCLIB" -> "LRCLIB"
                        "QQ_MUSIC" -> "QQ Music"
                        "NETEASE" -> "NetEase"
                        "KUGOU" -> "KuGou"
                        else -> it
                    }
                },
                onMove = onMoveLyricsSource
            )
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "Pesquisa",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            "Atraso entre itens nos resultados (${searchStaggerMs} ms)",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            "0 = todos juntos · valores maiores = cascata mais lenta",
            fontSize = 13.sp,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Slider(
            value = searchStaggerMs.toFloat(),
            onValueChange = { onSearchStaggerMsChange(it.toInt()) },
            valueRange = 0f..150f,
            steps = 29
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
            SeekBarStyle.Liquid to "Liquid — bolha no progresso",
            SeekBarStyle.WavyLiquid to "Wavy Liquid — onda + bolha líquida",
            SeekBarStyle.Waveform to "Waveform — forma de onda da música",
            SeekBarStyle.Glass to "Glass — vidro fumê + progresso champagne"
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

        if (seekBarStyle == SeekBarStyle.Waveform) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Desenho da waveform da barra",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(0.55f),
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                "Usa a cor de destaque do tema (exceto Multicolor). A forma de onda de cada música é calculada " +
                    "uma vez, em segundo plano, e fica guardada para as próximas vezes.",
                fontSize = 12.sp,
                color = Color.White.copy(0.45f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            val seekWaveAccent = LocalAppColors.current.accent
            WaveformTheme.values().forEach { option ->
                val optionSelected = seekBarWaveformTheme == option
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (optionSelected) Color.White.copy(0.14f) else Color.White.copy(0.06f))
                        .clickable { onSeekBarWaveformThemeChange(option) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = optionSelected,
                        onClick = { onSeekBarWaveformThemeChange(option) }
                    )
                    Text(
                        option.label,
                        fontSize = 15.sp,
                        fontWeight = if (optionSelected) FontWeight.SemiBold else FontWeight.Medium,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp)
                    )
                    WaveformThemePreview(
                        theme = option,
                        accent = seekWaveAccent,
                        modifier = Modifier
                            .width(110.dp)
                            .height(36.dp)
                    )
                }
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

@Composable
private fun PriorityOrderList(
    items: List<String>,
    labelOf: (String) -> String,
    onMove: (Int, Int) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(0.05f))
            .padding(vertical = 4.dp)
    ) {
        items.forEachIndexed { index, id ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${index + 1}.",
                    fontSize = 13.sp,
                    color = Color.White.copy(0.45f),
                    modifier = Modifier.width(28.dp)
                )
                Text(
                    labelOf(id),
                    fontSize = 14.sp,
                    color = Color.White.copy(0.92f),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "▲",
                    fontSize = 14.sp,
                    color = if (index > 0) Color.White.copy(0.85f) else Color.White.copy(0.25f),
                    modifier = Modifier
                        .clickable(enabled = index > 0) { onMove(index, index - 1) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                Text(
                    "▼",
                    fontSize = 14.sp,
                    color = if (index < items.lastIndex) Color.White.copy(0.85f) else Color.White.copy(0.25f),
                    modifier = Modifier
                        .clickable(enabled = index < items.lastIndex) { onMove(index, index + 1) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
