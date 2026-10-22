package com.tolete.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tolete.player.data.EqualizerManager

/**
 * Equalizador simples — refeito do zero.
 *
 * - Toggle liga/desliga
 * - 10 bandas com barras verticais arrastáveis
 * - Presets embutidos
 * - Botão Reset (Flat)
 * Visual leve estilo liquid glass.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val enabled by EqualizerManager.enabled.collectAsState()
    val bands by EqualizerManager.bands.collectAsState()
    val presetName by EqualizerManager.presetName.collectAsState()

    val accent = Color(0xFF69F0AE)
    val glassBg = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = 0.14f),
            Color.White.copy(alpha = 0.05f),
            Color.Black.copy(alpha = 0.20f)
        )
    )
    val glassBorder = Brush.verticalGradient(
        listOf(Color.White.copy(0.45f), Color.White.copy(0.10f), Color.White.copy(0.03f))
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0A0A0F).copy(alpha = 0.97f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(0.35f))
            )
        }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Cabeçalho
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(26.dp)
                )
                Text(
                    "Equalizador",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(start = 10.dp).weight(1f)
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = { EqualizerManager.setEnabled(it, context) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = accent,
                        uncheckedThumbColor = Color.White.copy(0.7f),
                        uncheckedTrackColor = Color.White.copy(0.2f)
                    )
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Fechar", tint = Color.White.copy(0.7f))
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                if (enabled) "Preset: $presetName" else "Desligado",
                fontSize = 13.sp,
                color = Color.White.copy(0.55f)
            )

            Spacer(Modifier.height(16.dp))

            // Presets
            Text(
                "Presets",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(0.55f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EqualizerManager.BUILTIN_PRESETS.keys.forEach { name ->
                    val selected = presetName == name && enabled
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selected) accent.copy(0.25f) else Color.White.copy(0.08f))
                            .border(
                                width = 1.dp,
                                color = if (selected) accent else Color.White.copy(0.12f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable { EqualizerManager.applyPreset(name, context) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            name,
                            fontSize = 13.sp,
                            color = if (selected) accent else Color.White.copy(0.85f),
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Bandas
            Text(
                "Bandas",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(0.55f),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(glassBg)
                    .border(1.dp, glassBorder, RoundedCornerShape(20.dp))
                    .padding(horizontal = 6.dp, vertical = 16.dp)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    bands.take(10).forEachIndexed { index, band ->
                        val minDb = band.minLevel / 100f
                        val maxDb = band.maxLevel / 100f
                        val currentDb = band.level / 100f
                        val fraction = if (maxDb > minDb) {
                            ((currentDb - minDb) / (maxDb - minDb)).coerceIn(0f, 1f)
                        } else 0.5f

                        BandBar(
                            fraction = fraction,
                            labelDb = currentDb,
                            freqLabel = formatFreq(EqualizerManager.BAND_FREQS.getOrElse(index) { 0 }),
                            enabled = enabled,
                            accent = accent,
                            onFractionChange = { f ->
                                val db = minDb + f * (maxDb - minDb)
                                EqualizerManager.setBandLevel(index, db, context)
                            },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            TextButton(
                onClick = { EqualizerManager.applyPreset("Flat", context) },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Resetar (Flat)", color = Color.White.copy(0.75f))
            }
        }
    }
}

@Composable
private fun BandBar(
    fraction: Float,
    labelDb: Float,
    freqLabel: String,
    enabled: Boolean,
    accent: Color,
    onFractionChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    // Sempre chama a versão mais recente do callback (evita lambda/valor "velho" dentro do pointerInput)
    val currentOnChange by rememberUpdatedState(onFractionChange)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(horizontal = 2.dp)
    ) {
        Text(
            String.format("%+.0f", labelDb),
            fontSize = 10.sp,
            color = if (enabled) accent else Color.White.copy(0.4f),
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .width(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(0.10f))
                .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(12.dp))
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    // Posição ABSOLUTA do dedo: o slider acompanha o toque/arrasto sem acumular erro
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        fun update(y: Float) = currentOnChange((1f - y / h).coerceIn(0f, 1f))
                        update(down.position.y)
                        down.consume()
                        drag(down.id) { change ->
                            update(change.position.y)
                            change.consume()
                        }
                    }
                }
        ) {
            // fill from bottom — the visual “level”
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(fraction.coerceIn(0.03f, 1f))
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (enabled) {
                            Brush.verticalGradient(
                                listOf(accent.copy(0.95f), accent.copy(0.45f))
                            )
                        } else {
                            Brush.verticalGradient(
                                listOf(Color.White.copy(0.22f), Color.White.copy(0.08f))
                            )
                        }
                    )
            )
        }

        Spacer(Modifier.height(6.dp))
        Text(
            freqLabel,
            fontSize = 10.sp,
            color = Color.White.copy(0.5f),
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

private fun formatFreq(hz: Int): String = when {
    hz >= 1000 -> "${hz / 1000}k"
    else -> "$hz"
}
