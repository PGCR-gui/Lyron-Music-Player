package com.tolete.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.*

/**
 * Seletor de cor com color wheel (HSV), slider de valor (brightness),
 * opacidade e campo hex — inspirado no seletor padrão de cor do Android.
 */
@Composable
fun ColorWheelPickerDialog(
    initialColor: Int,
    onDismiss: () -> Unit,
    onColorSelected: (Int) -> Unit,
    showAlpha: Boolean = true,
    title: String = "Escolher cor"
) {
    val initial = Color(initialColor)
    var hue by remember { mutableFloatStateOf(rgbToHsv(initial).first) }
    var sat by remember { mutableFloatStateOf(rgbToHsv(initial).second) }
    var value by remember { mutableFloatStateOf(rgbToHsv(initial).third) }
    var alpha by remember { mutableFloatStateOf(initial.alpha) }

    val currentColor = hsvToColor(hue, sat, value, alpha)
    val argb = currentColor.toArgb()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF2A2A2E),
            tonalElevation = 8.dp
        ) {
            Column(
                Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )

                // Preview + hex
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(currentColor)
                            .border(1.dp, Color.White.copy(0.3f), RoundedCornerShape(12.dp))
                    )
                    var hexText by remember(argb) {
                        mutableStateOf(toHexString(argb, showAlpha))
                    }
                    OutlinedTextField(
                        value = hexText,
                        onValueChange = { input ->
                            val filtered = input.filter { it in "#0123456789abcdefABCDEF" }.take(if (showAlpha) 9 else 7)
                            hexText = filtered
                            parseHex(filtered)?.let { parsed ->
                                val c = Color(parsed)
                                val (h, s, v) = rgbToHsv(c)
                                hue = h
                                sat = s
                                value = v
                                if (showAlpha) alpha = c.alpha
                            }
                        },
                        singleLine = true,
                        label = { Text(if (showAlpha) "Hex (#AARRGGBB)" else "Hex (#RRGGBB)") },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF69F0AE),
                            unfocusedBorderColor = Color.White.copy(0.3f),
                            focusedLabelColor = Color.White.copy(0.7f),
                            unfocusedLabelColor = Color.White.copy(0.5f),
                            cursorColor = Color(0xFF69F0AE)
                        )
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Color wheel + value slider
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ColorWheel(
                        hue = hue,
                        saturation = sat,
                        value = value,
                        onHueSatChange = { h, s ->
                            hue = h
                            sat = s
                        },
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                    )
                    ValueSlider(
                        value = value,
                        hue = hue,
                        saturation = sat,
                        onValueChange = { value = it },
                        modifier = Modifier
                            .width(28.dp)
                            .height(200.dp)
                    )
                }

                if (showAlpha) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Opacidade: ${(alpha * 100).toInt()}%",
                        color = Color.White.copy(0.75f),
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Slider(
                        value = alpha,
                        onValueChange = { alpha = it },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = currentColor.copy(alpha = 1f),
                            inactiveTrackColor = Color.White.copy(0.2f)
                        )
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("CANCELAR", color = Color.White.copy(0.7f))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onColorSelected(argb)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF69F0AE),
                            contentColor = Color.Black
                        )
                    ) {
                        Text("OK", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Botão compacto que mostra a cor atual e abre o ColorWheelPickerDialog.
 */
@Composable
fun ColorPickerButton(
    color: Int,
    onColorChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showAlpha: Boolean = true,
    label: String? = null
) {
    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(0.08f))
            .clickable { showDialog = true }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(color))
                .border(1.dp, Color.White.copy(0.35f), RoundedCornerShape(10.dp))
        )
        Column(Modifier.weight(1f)) {
            if (label != null) {
                Text(label, fontSize = 14.sp, color = Color.White.copy(0.9f))
            }
            Text(
                toHexString(color, showAlpha),
                fontSize = 12.sp,
                color = Color.White.copy(0.55f)
            )
        }
        Text("▶", color = Color.White.copy(0.4f), fontSize = 12.sp)
    }

    if (showDialog) {
        ColorWheelPickerDialog(
            initialColor = color,
            onDismiss = { showDialog = false },
            onColorSelected = onColorChange,
            showAlpha = showAlpha,
            title = label ?: "Escolher cor"
        )
    }
}

@Composable
private fun ColorWheel(
    hue: Float,
    saturation: Float,
    value: Float,
    onHueSatChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier) {
        val sizePx = with(density) { minOf(maxWidth, maxHeight).toPx() }
        val radius = sizePx / 2f
        val center = Offset(radius, radius)

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val (h, s) = offsetToHueSat(offset, center, radius)
                        onHueSatChange(h, s)
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val (h, s) = offsetToHueSat(change.position, center, radius)
                        onHueSatChange(h, s)
                    }
                }
        ) {
            // Draw HSV wheel as concentric rings / angular sweeps
            val steps = 72
            for (i in 0 until steps) {
                val startAngle = i * 360f / steps
                val sweep = 360f / steps + 0.5f
                val h = startAngle
                val color = hsvToColor(h, 1f, value, 1f)
                drawArc(
                    color = color,
                    startAngle = startAngle - 90f,
                    sweepAngle = sweep,
                    useCenter = true,
                    size = androidx.compose.ui.geometry.Size(sizePx, sizePx)
                )
            }
            // White overlay from center for saturation (radial)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, Color.Transparent),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
            // Value darkening is applied via the value param in outer colors;
            // extra dark overlay if value < 1
            if (value < 1f) {
                drawCircle(
                    color = Color.Black.copy(alpha = 1f - value),
                    radius = radius,
                    center = center
                )
            }

            // Selector indicator
            val angleRad = Math.toRadians((hue - 90).toDouble())
            val r = saturation * radius * 0.92f
            val selX = center.x + (r * cos(angleRad)).toFloat()
            val selY = center.y + (r * sin(angleRad)).toFloat()
            drawCircle(
                color = Color.White,
                radius = 12f,
                center = Offset(selX, selY),
                style = Stroke(width = 3f)
            )
            drawCircle(
                color = hsvToColor(hue, saturation, value, 1f),
                radius = 8f,
                center = Offset(selX, selY)
            )
        }
    }
}

@Composable
private fun ValueSlider(
    value: Float,
    hue: Float,
    saturation: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val topColor = hsvToColor(hue, saturation, 1f, 1f)
    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Color.White.copy(0.25f), RoundedCornerShape(8.dp))
    ) {
        val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val v = 1f - (offset.y / heightPx).coerceIn(0f, 1f)
                        onValueChange(v)
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val v = 1f - (change.position.y / heightPx).coerceIn(0f, 1f)
                        onValueChange(v)
                    }
                }
        ) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(topColor, Color.Black)
                )
            )
            // Thumb
            val y = (1f - value) * size.height
            drawCircle(
                color = Color.White,
                radius = 10f,
                center = Offset(size.width / 2f, y),
                style = Stroke(width = 2.5f)
            )
        }
    }
}

// --- HSV / RGB helpers ---

private fun rgbToHsv(color: Color): Triple<Float, Float, Float> {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val h = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }.let { if (it < 0) it + 360f else it }
    val s = if (max == 0f) 0f else delta / max
    val v = max
    return Triple(h, s, v)
}

private fun hsvToColor(h: Float, s: Float, v: Float, a: Float): Color {
    val c = v * s
    val x = c * (1 - abs((h / 60f) % 2 - 1))
    val m = v - c
    val (r1, g1, b1) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(
        red = (r1 + m).coerceIn(0f, 1f),
        green = (g1 + m).coerceIn(0f, 1f),
        blue = (b1 + m).coerceIn(0f, 1f),
        alpha = a.coerceIn(0f, 1f)
    )
}

private fun offsetToHueSat(offset: Offset, center: Offset, radius: Float): Pair<Float, Float> {
    val dx = offset.x - center.x
    val dy = offset.y - center.y
    val dist = sqrt(dx * dx + dy * dy).coerceAtMost(radius)
    val sat = (dist / radius).coerceIn(0f, 1f)
    var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
    if (angle < 0) angle += 360f
    if (angle >= 360f) angle -= 360f
    return angle to sat
}

private fun toHexString(argb: Int, withAlpha: Boolean): String {
    return if (withAlpha) {
        String.format("#%08X", argb)
    } else {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        String.format("#%02X%02X%02X", r, g, b)
    }
}

private fun parseHex(raw: String): Int? {
    var s = raw.trim().removePrefix("#")
    return try {
        when (s.length) {
            3 -> {
                s = s.map { "$it$it" }.joinToString("")
                (0xFF shl 24) or s.toInt(16)
            }
            6 -> (0xFF shl 24) or s.toInt(16)
            8 -> s.toLong(16).toInt()
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}
