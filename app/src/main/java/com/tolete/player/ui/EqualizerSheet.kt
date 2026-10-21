package com.tolete.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tolete.player.data.EqualizerManager
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Equalizador liquid glass inspirado na referência do vídeo:
 * Página 1: 10 bandas + seletor de bandas/preset
 * Página 2: Bass boost, Surround, Preamp, Volume, Stereo/Mono + balance
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun EqualizerSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val enabled by EqualizerManager.enabled.collectAsState()
    val bands by EqualizerManager.bands.collectAsState()
    val presetName by EqualizerManager.presetName.collectAsState()

    var bassBoost by remember { mutableFloatStateOf(0f) }      // 0..100 %
    var surround by remember { mutableFloatStateOf(0f) }       // 0..100 %
    var preampDb by remember { mutableFloatStateOf(0f) }       // -6..+2
    var volume by remember { mutableFloatStateOf(0.7f) }       // 0..1
    var isStereo by remember { mutableStateOf(true) }
    var balance by remember { mutableFloatStateOf(0.5f) }      // 0=left, 1=right
    var showBandCount by remember { mutableStateOf(false) }
    var showPresets by remember { mutableStateOf(false) }
    var selectedBands by remember { mutableIntStateOf(10) }

    val glassBg = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = 0.18f),
            Color.White.copy(alpha = 0.06f),
            Color.Black.copy(alpha = 0.25f)
        )
    )
    val glassBorder = Brush.verticalGradient(
        listOf(Color.White.copy(0.55f), Color.White.copy(0.12f), Color.White.copy(0.04f))
    )
    val accent = Color(0xFF69F0AE) // mint like the video

    val pagerState = rememberPagerState(pageCount = { 2 })

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0A0A0F).copy(alpha = 0.96f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
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
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.GraphicEq, null, tint = accent, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "Equalizador",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
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
                    Icon(Icons.Rounded.Close, "Fechar", tint = Color.White.copy(0.7f))
                }
            }

            // Device row (visual only)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(0.06f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📱", fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Alto-falantes embutidos",
                    color = accent,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = { EqualizerManager.setEnabled(it, context) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = accent
                    )
                )
            }

            Spacer(Modifier.height(12.dp))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 480.dp)
            ) { page ->
                when (page) {
                    0 -> {
                        // === PAGE 1: Bands ===
                        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                            // Band count + preset chips
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Color.White.copy(0.08f))
                                        .border(1.dp, accent.copy(0.5f), RoundedCornerShape(20.dp))
                                        .clickable { showBandCount = true }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text("〰 $selectedBands", color = accent, fontSize = 14.sp)
                                }
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Color.White.copy(0.08f))
                                        .border(1.dp, Color.White.copy(0.2f), RoundedCornerShape(20.dp))
                                        .clickable { showPresets = true }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text(presetName, color = Color.White, fontSize = 14.sp)
                                }
                                Spacer(Modifier.weight(1f))
                                IconButton(onClick = { /* save custom preset future */ }) {
                                    Icon(Icons.Rounded.Save, "Salvar", tint = Color.White.copy(0.6f))
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            // Freq labels + dB
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                EqualizerManager.BAND_FREQS.take(10).forEachIndexed { i, hz ->
                                    val db = bands.getOrNull(i)?.let { it.level / 100f } ?: 0f
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                        Text(
                                            formatFreq(hz),
                                            color = Color.White.copy(0.55f),
                                            fontSize = 9.sp,
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            "%+.1f".format(db),
                                            color = Color.White.copy(0.75f),
                                            fontSize = 9.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            // Vertical liquid-glass sliders
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(240.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(glassBg)
                                    .border(1.dp, glassBorder, RoundedCornerShape(20.dp))
                                    .padding(horizontal = 6.dp, vertical = 16.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                bands.take(10).forEachIndexed { index, band ->
                                    val minDb = band.minLevel / 100f
                                    val maxDb = band.maxLevel / 100f
                                    val value = (band.level / 100f).coerceIn(minDb, maxDb)
                                    VerticalLiquidSlider(
                                        value = value,
                                        valueRange = minDb..maxDb,
                                        enabled = enabled,
                                        accent = accent,
                                        onValueChange = { EqualizerManager.setBandLevel(index, it, context) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                    )
                                }
                            }
                        }
                    }
                    1 -> {
                        // === PAGE 2: Effects ===
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(bottom = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                LiquidKnob(
                                    label = "Bass boost",
                                    value = bassBoost,
                                    valueRange = 0f..100f,
                                    display = "${bassBoost.toInt()}%",
                                    accent = accent,
                                    onValueChange = {
                                        bassBoost = it
                                        // Map to low bands roughly
                                        EqualizerManager.setBassTreble(it / 12f, 0f, context)
                                    },
                                    enabled = enabled
                                )
                                LiquidKnob(
                                    label = "Surround Sound",
                                    value = surround,
                                    valueRange = 0f..100f,
                                    display = "${surround.toInt()}%",
                                    accent = accent,
                                    onValueChange = { surround = it },
                                    enabled = enabled
                                )
                            }

                            Spacer(Modifier.height(20.dp))

                            Text("Preamp", color = Color.White.copy(0.7f), fontSize = 13.sp)
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf(-6f to "Headroom\n-6 dB", 0f to "Desligado\n0 dB", 2f to "Impulsionar\n+2 dB").forEach { (v, label) ->
                                    val selected = preampDb == v
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (selected) accent.copy(0.25f) else Color.Transparent)
                                            .border(1.dp, if (selected) accent else Color.White.copy(0.15f), RoundedCornerShape(12.dp))
                                            .clickable { preampDb = v }
                                            .padding(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            label,
                                            color = if (selected) accent else Color.White.copy(0.6f),
                                            fontSize = 11.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                            Slider(
                                value = preampDb,
                                onValueChange = { preampDb = it },
                                valueRange = -6f..2f,
                                enabled = enabled,
                                colors = SliderDefaults.colors(
                                    thumbColor = accent,
                                    activeTrackColor = accent,
                                    inactiveTrackColor = Color.White.copy(0.15f)
                                )
                            )

                            Spacer(Modifier.height(12.dp))
                            Text("Volume", color = Color.White.copy(0.7f), fontSize = 13.sp)
                            Slider(
                                value = volume,
                                onValueChange = { volume = it },
                                valueRange = 0f..1f,
                                enabled = enabled,
                                colors = SliderDefaults.colors(
                                    thumbColor = accent,
                                    activeTrackColor = accent,
                                    inactiveTrackColor = Color.White.copy(0.15f)
                                )
                            )

                            Spacer(Modifier.height(16.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Stereo / Mono
                                Column(
                                    Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color.White.copy(0.08f))
                                        .border(1.dp, glassBorder, RoundedCornerShape(14.dp))
                                        .padding(4.dp)
                                ) {
                                    listOf(true to "Estéreo", false to "Mono").forEach { (stereo, label) ->
                                        Text(
                                            label,
                                            color = if (isStereo == stereo) accent else Color.White.copy(0.5f),
                                            fontSize = 14.sp,
                                            fontWeight = if (isStereo == stereo) FontWeight.SemiBold else FontWeight.Normal,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (isStereo == stereo) accent.copy(0.2f) else Color.Transparent)
                                                .clickable { isStereo = stereo }
                                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                        )
                                    }
                                }

                                // Balance knob
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    LiquidKnob(
                                        label = "",
                                        value = balance * 100f,
                                        valueRange = 0f..100f,
                                        display = "",
                                        accent = accent,
                                        onValueChange = { balance = it / 100f },
                                        enabled = enabled,
                                        knobSize = 90.dp
                                    )
                                    Text(
                                        "Balanço do canal de áudio",
                                        color = Color.White.copy(0.5f),
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        "Esquerdo:${(100 - balance * 100).toInt()}, Direito:${(balance * 100).toInt()}",
                                        color = Color.White.copy(0.7f),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Page indicator
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(2) { i ->
                    Box(
                        Modifier
                            .padding(4.dp)
                            .size(if (pagerState.currentPage == i) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (pagerState.currentPage == i) accent else Color.White.copy(0.3f)
                            )
                    )
                }
            }
        }
    }

    if (showBandCount) {
        AlertDialog(
            onDismissRequest = { showBandCount = false },
            containerColor = Color(0xFF1A1A24),
            title = { Text("Número de bandas de frequência", color = Color.White) },
            text = {
                Column {
                    listOf(3, 5, 10, 15, 31).forEach { n ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedBands = n
                                    showBandCount = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedBands == n,
                                onClick = {
                                    selectedBands = n
                                    showBandCount = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = accent)
                            )
                            Text("$n", color = Color.White, fontSize = 16.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBandCount = false }) {
                    Text("Fechar", color = Color.White.copy(0.7f))
                }
            }
        )
    }

    if (showPresets) {
        AlertDialog(
            onDismissRequest = { showPresets = false },
            containerColor = Color(0xFF1A1A24),
            title = { Text("Predefinições", color = Color.White) },
            text = {
                Column {
                    EqualizerManager.BUILTIN_PRESETS.keys.forEach { name ->
                        Text(
                            name,
                            color = if (name == presetName) accent else Color.White.copy(0.85f),
                            fontSize = 16.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    EqualizerManager.applyPreset(name, context)
                                    showPresets = false
                                }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPresets = false }) {
                    Text("Fechar", color = Color.White.copy(0.7f))
                }
            }
        )
    }
}

@Composable
private fun VerticalLiquidSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    accent: Color,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures { change, _ ->
                    change.consume()
                    val h = size.height.toFloat()
                    val y = change.position.y.coerceIn(0f, h)
                    val fraction = 1f - (y / h)
                    val newVal = valueRange.start + fraction * (valueRange.endInclusive - valueRange.start)
                    onValueChange(newVal)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val h = constraints.maxHeight.toFloat()
        val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
        Canvas(Modifier.fillMaxSize()) {
            val trackW = 4.dp.toPx()
            val cx = size.width / 2
            // track
            drawLine(
                color = Color.White.copy(0.15f),
                start = Offset(cx, 0f),
                end = Offset(cx, size.height),
                strokeWidth = trackW,
                cap = StrokeCap.Round
            )
            // active
            val activeTop = size.height * (1f - fraction)
            drawLine(
                color = accent,
                start = Offset(cx, activeTop),
                end = Offset(cx, size.height),
                strokeWidth = trackW,
                cap = StrokeCap.Round
            )
        }
        // thumb
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = with(density) { ((1f - fraction) * h).toDp() - 14.dp })
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(0.9f), Color.White.copy(0.55f))
                    )
                )
                .border(1.dp, Color.White.copy(0.4f), RoundedCornerShape(8.dp))
        ) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .width(12.dp)
                    .height(2.dp)
                    .background(Color.Black.copy(0.35f))
            )
        }
    }
}

@Composable
private fun LiquidKnob(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    display: String,
    accent: Color,
    onValueChange: (Float) -> Unit,
    enabled: Boolean,
    knobSize: androidx.compose.ui.unit.Dp = 110.dp
) {
    val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (label.isNotEmpty()) {
            Text(label, color = Color.White.copy(0.65f), fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
        }
        Box(
            Modifier
                .size(knobSize)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectDragGestures { change, _ ->
                        change.consume()
                        val center = Offset(this.size.width / 2f, this.size.height / 2f)
                        val pos = change.position
                        val angle = atan2(pos.y - center.y, pos.x - center.x)
                        // map -PI..PI to 0..1 (bottom is start)
                        var f = ((angle + Math.PI) / (2 * Math.PI)).toFloat()
                        f = (f + 0.25f) % 1f // rotate so bottom is 0
                        val newVal = valueRange.start + f * (valueRange.endInclusive - valueRange.start)
                        onValueChange(newVal)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 6.dp.toPx()
                val canvasW = this.size.width
                val canvasH = this.size.height
                val radius = min(canvasW, canvasH) / 2f - stroke
                // background arc
                drawArc(
                    color = Color.White.copy(0.12f),
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = Offset(canvasW / 2 - radius, canvasH / 2 - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                // active arc
                drawArc(
                    color = accent,
                    startAngle = 135f,
                    sweepAngle = 270f * fraction,
                    useCenter = false,
                    topLeft = Offset(canvasW / 2 - radius, canvasH / 2 - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                // indicator
                val ang = Math.toRadians((135 + 270 * fraction).toDouble())
                val ix = canvasW / 2 + (radius - 4.dp.toPx()) * cos(ang).toFloat()
                val iy = canvasH / 2 + (radius - 4.dp.toPx()) * sin(ang).toFloat()
                drawCircle(Color.White, radius = 5.dp.toPx(), center = Offset(ix, iy))
            }
        }
        if (display.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(display, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
        }
    }
}

private fun formatFreq(hz: Int): String = when {
    hz >= 1000 -> "${hz / 1000} k"
    else -> "$hz"
}
