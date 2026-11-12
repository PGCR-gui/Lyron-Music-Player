package com.tolete.player.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tolete.player.R
import kotlin.math.roundToInt

/** Popup liquid-glass exibido pela Activity transparente de abertura externa. */
@Composable
fun ExternalMiniPlayer(
    backdrop: GraphicsLayer,
    title: String,
    artist: String,
    album: String = "",
    artPath: String? = null,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onDismiss: () -> Unit,
    onOpenFull: () -> Unit
) {
    val progress = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    var slider by remember(progress) { mutableFloatStateOf(progress) }
    var draggingSlider by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth(0.90f)
                .clip(RoundedCornerShape(30.dp))
                .clickable(enabled = true, onClick = {})
        ) {
            GlassSurface(
                backdrop = backdrop,
                shape = RoundedCornerShape(30.dp),
                style = GlassStyle.Bar,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF2D3542).copy(alpha = 0.16f))
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(R.drawable.ic_launcher_legacy),
                            contentDescription = "Lyron Music Player",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(11.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Lyron",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Fechar mini player",
                            tint = Color.White.copy(alpha = 0.78f),
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onDismiss)
                                .padding(6.dp)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!artPath.isNullOrBlank()) {
                            AsyncImage(
                                model = artPath,
                                contentDescription = "Capa do álbum",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            )
                        } else {
                            Box(
                                Modifier
                                    .size(68.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White.copy(alpha = 0.10f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.72f),
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                title.ifBlank { "Música" },
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                artist.ifBlank { "Artista desconhecido" },
                                color = Color.White.copy(alpha = 0.72f),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (album.isNotBlank()) {
                                Text(
                                    album,
                                    color = Color.White.copy(alpha = 0.54f),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Slider(
                            value = if (draggingSlider) slider else progress,
                            enabled = !isLoading && errorMessage == null && durationMs > 0L,
                            onValueChange = {
                                draggingSlider = true
                                slider = it
                            },
                            onValueChangeFinished = {
                                draggingSlider = false
                                onSeek(slider)
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White.copy(alpha = 0.90f),
                                inactiveTrackColor = Color.White.copy(alpha = 0.24f),
                                disabledThumbColor = Color.White.copy(alpha = 0.45f),
                                disabledActiveTrackColor = Color.White.copy(alpha = 0.42f),
                                disabledInactiveTrackColor = Color.White.copy(alpha = 0.16f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatMs(positionMs), color = Color.White.copy(alpha = 0.60f), fontSize = 11.sp)
                            Text(formatMs(durationMs), color = Color.White.copy(alpha = 0.60f), fontSize = 11.sp)
                        }
                    }

                    if (errorMessage != null) {
                        Text(
                            errorMessage,
                            color = Color(0xFFFF8A80),
                            fontSize = 12.sp
                        )
                    }

                    PlaybackButtons(
                        backdrop = backdrop,
                        enabled = !isLoading && errorMessage == null,
                        isPlaying = isPlaying,
                        onPlay = onPlay,
                        onPause = onPause
                    )

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White.copy(alpha = if (isLoading || errorMessage != null) 0.04f else 0.09f))
                            .clickable(enabled = !isLoading && errorMessage == null, onClick = onOpenFull)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                Icons.Rounded.OpenInFull,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = if (isLoading || errorMessage != null) 0.4f else 1f),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                "Abrir Now Playing",
                                color = Color.White.copy(alpha = if (isLoading || errorMessage != null) 0.4f else 1f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaybackButtons(
    backdrop: GraphicsLayer,
    enabled: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit
) {
    var selectedIndex by remember { mutableIntStateOf(if (isPlaying) 0 else 1) }
    var dragging by remember { mutableStateOf(false) }
    var dragLeftPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val actions by rememberUpdatedState(newValue = listOf(onPlay, onPause))
    LaunchedEffect(enabled, isPlaying) {
        if (enabled) selectedIndex = if (isPlaying) 0 else 1
    }

    BoxWithConstraints(Modifier.fillMaxWidth().height(72.dp)) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val buttonPx = with(density) { 58.dp.toPx() }
        val slotPx = widthPx / 2f
        val animatedIndex by animateFloatAsState(
            targetValue = selectedIndex.toFloat(),
            animationSpec = spring(dampingRatio = 0.68f, stiffness = 420f),
            label = "externalPlayerPlayPausePill"
        )
        val restingLeftPx = slotPx * animatedIndex + (slotPx - buttonPx) / 2f
        val displayIndex = if (dragging) {
            ((dragLeftPx + buttonPx / 2f) / slotPx).toInt().coerceIn(0, 1)
        } else selectedIndex

        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            val icons = listOf(Icons.Rounded.PlayArrow, Icons.Rounded.Pause)
            val labels = listOf("Reproduzir", "Pausar")
            icons.forEachIndexed { index, image ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable(enabled = enabled) {
                            selectedIndex = index
                            actions[index]()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        image,
                        contentDescription = labels[index],
                        tint = Color.White.copy(alpha = if (enabled) 0.58f else 0.30f),
                        modifier = Modifier.size(27.dp)
                    )
                }
            }
        }

        GlassSurface(
            backdrop = backdrop,
            shape = CircleShape,
            style = GlassStyle.Lens,
            modifier = Modifier
                .offset {
                    val left = if (dragging) dragLeftPx else restingLeftPx
                    IntOffset(left.roundToInt(), ((heightPx - buttonPx) / 2f).roundToInt())
                }
                .size(58.dp)
                .pointerInput(widthPx, enabled, selectedIndex) {
                    if (enabled) {
                        detectDragGestures(
                            onDragStart = {
                                dragLeftPx = restingLeftPx
                                dragging = true
                            },
                            onDragEnd = {
                                val index = ((dragLeftPx + buttonPx / 2f) / slotPx).roundToInt().coerceIn(0, 1)
                                dragging = false
                                selectedIndex = index
                                actions[index]()
                            },
                            onDragCancel = { dragging = false },
                            onDrag = { change, amount ->
                                change.consume()
                                dragLeftPx = (dragLeftPx + amount.x).coerceIn(0f, (widthPx - buttonPx).coerceAtLeast(0f))
                            }
                        )
                    }
                }
                .clickable(enabled = enabled) { actions[selectedIndex]() }
        ) {
            val selectedIcon = if (displayIndex == 0) Icons.Rounded.PlayArrow else Icons.Rounded.Pause
            Icon(
                selectedIcon,
                contentDescription = if (displayIndex == 0) "Reproduzir" else "Pausar",
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(30.dp)
            )
        }
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = (ms / 1000).toInt()
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
