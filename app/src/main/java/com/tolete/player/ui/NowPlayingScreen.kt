package com.tolete.player.ui

import android.content.Intent

import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import com.tolete.player.ui.MusicViewModel.AudioOutputBackend

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.Crossfade
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Share
import androidx.media3.common.Player
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.rememberCoroutineScope
import com.tolete.player.data.LyricsSearchResult
import com.tolete.player.data.LyricsSource
import com.tolete.player.data.LyricsType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.Settings
import android.net.Uri
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin
import androidx.compose.ui.text.font.Font
import androidx.compose.runtime.DisposableEffect
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.app.Activity
import coil.compose.AsyncImage
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.request.ImageRequest
import com.tolete.player.R
import com.tolete.player.data.LyricLine
import com.tolete.player.data.LyricAnimation
import com.tolete.player.data.LyricsEmbedder
import com.tolete.player.data.LyricsExtractor
import com.tolete.player.data.Track
import com.tolete.player.video.LyricsVideoGenerator
import com.tolete.player.video.WaveformExtractor
import com.tolete.player.video.LyricsVideoStyle
import com.tolete.player.visualizer.MilkVisualizer
import com.tolete.player.visualizer.ProjectMNative
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

/**
 * Até 2 linhas de letra; reduz a fonte automaticamente até o texto caber
 * na largura disponível (quebra suave na 2ª linha se necessário).
 */
/**
 * Contorno das letras desenhado com o MESMO método do popup de letras: Paint nativo em modo
 * STROKE (junta arredondada) por baixo e o preenchimento por cima. A largura do traço é dobrada
 * porque metade fica escondida sob o preenchimento: a borda visível tem [borderWidthDp] dp.
 */
@Composable
private fun rememberLyricTypeface(
    family: FontFamily?,
    weight: FontWeight,
    italic: Boolean
): android.graphics.Typeface {
    val resolver = androidx.compose.ui.platform.LocalFontFamilyResolver.current
    val state = remember(family, weight, italic) {
        resolver.resolve(
            family,
            weight,
            if (italic) androidx.compose.ui.text.font.FontStyle.Italic
            else androidx.compose.ui.text.font.FontStyle.Normal,
            androidx.compose.ui.text.font.FontSynthesis.All
        )
    }
    return (state.value as? android.graphics.Typeface) ?: android.graphics.Typeface.DEFAULT
}

private class LyricLayoutHolder {
    var layout: android.text.StaticLayout? = null
    var baseline = 0f
}

/** Texto de várias linhas, centralizado, com contorno (linhas simples / sem karaokê). */
@Composable
private fun OutlinedLyricText(
    text: String,
    color: Color,
    borderColor: Color,
    borderWidthDp: Float,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontFamily: FontFamily?,
    fontWeight: FontWeight,
    italic: Boolean,
    lineHeightMul: Float,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE
) {
    val tf = rememberLyricTypeface(fontFamily, fontWeight, italic)
    val density = LocalDensity.current
    val sizePx = with(density) { fontSize.toPx() }
    val strokePx = borderWidthDp * 2f * density.density
    val paint = remember { android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
    val holder = remember { LyricLayoutHolder() }
    androidx.compose.ui.layout.Layout(
        content = {},
        modifier = modifier.drawBehind {
            val sl = holder.layout ?: return@drawBehind
            drawIntoCanvas { c ->
                val nc = c.nativeCanvas
                if (strokePx > 0f) {
                    paint.style = android.graphics.Paint.Style.STROKE
                    paint.strokeWidth = strokePx
                    paint.strokeJoin = android.graphics.Paint.Join.ROUND
                    paint.color = borderColor.copy(alpha = borderColor.alpha * color.alpha).toArgb()
                    sl.draw(nc)
                }
                paint.style = android.graphics.Paint.Style.FILL
                paint.strokeWidth = 0f
                paint.color = color.toArgb()
                sl.draw(nc)
            }
        },
        measurePolicy = { _, constraints ->
            paint.textSize = sizePx
            paint.typeface = tf
            val w = if (constraints.hasBoundedWidth) constraints.maxWidth
            else paint.measureText(text).toInt().coerceAtLeast(1)
            val natural = paint.fontMetricsInt.let { it.descent - it.ascent }
            val sl = android.text.StaticLayout.Builder
                .obtain(text, 0, text.length, paint, w.coerceAtLeast(1))
                .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .setLineSpacing((lineHeightMul * sizePx - natural).coerceAtLeast(0f), 1f)
                .setMaxLines(maxLines)
                .setEllipsize(android.text.TextUtils.TruncateAt.END)
                .build()
            holder.layout = sl
            layout(constraints.constrainWidth(w), constraints.constrainHeight(sl.height)) {}
        }
    )
}

/** Uma palavra do karaokê com contorno (mesmo Paint STROKE + FILL do popup). */
@Composable
private fun OutlinedWordText(
    text: String,
    color: Color,
    borderColor: Color,
    borderWidthDp: Float,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontFamily: FontFamily?,
    fontWeight: FontWeight,
    italic: Boolean,
    lineHeightMul: Float,
    shadow: androidx.compose.ui.graphics.Shadow?,
    modifier: Modifier = Modifier
) {
    val tf = rememberLyricTypeface(fontFamily, fontWeight, italic)
    val density = LocalDensity.current
    val sizePx = with(density) { fontSize.toPx() }
    val strokePx = borderWidthDp * 2f * density.density
    val paint = remember { android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
    val holder = remember { LyricLayoutHolder() }
    androidx.compose.ui.layout.Layout(
        content = {},
        modifier = modifier.drawBehind {
            drawIntoCanvas { c ->
                val nc = c.nativeCanvas
                paint.textSize = sizePx
                paint.typeface = tf
                if (strokePx > 0f) {
                    paint.style = android.graphics.Paint.Style.STROKE
                    paint.strokeWidth = strokePx
                    paint.strokeJoin = android.graphics.Paint.Join.ROUND
                    paint.color = borderColor.copy(alpha = borderColor.alpha * color.alpha).toArgb()
                    nc.drawText(text, 0f, holder.baseline, paint)
                }
                paint.style = android.graphics.Paint.Style.FILL
                paint.strokeWidth = 0f
                paint.color = color.toArgb()
                if (shadow != null) {
                    paint.setShadowLayer(shadow.blurRadius, shadow.offset.x, shadow.offset.y, shadow.color.toArgb())
                }
                nc.drawText(text, 0f, holder.baseline, paint)
                if (shadow != null) paint.clearShadowLayer()
            }
        },
        measurePolicy = { _, constraints ->
            paint.textSize = sizePx
            paint.typeface = tf
            val fm = paint.fontMetrics
            val asc = -fm.ascent
            val desc = fm.descent
            val w = Math.ceil(paint.measureText(text).toDouble()).toInt().coerceAtLeast(1)
            val h = Math.ceil((lineHeightMul * sizePx).toDouble()).toInt().coerceAtLeast(1)
            // Entrelinha distribuída de forma proporcional ao ascent/descent (como o Text do Compose).
            val extra = (h - (asc + desc)).coerceAtLeast(0f)
            holder.baseline = extra * asc / (asc + desc) + asc
            layout(constraints.constrainWidth(w), constraints.constrainHeight(h)) {}
        }
    )
}

@Composable
private fun AutoSizeSingleLineLyric(
    text: String,
    baseFontSize: androidx.compose.ui.unit.TextUnit,
    fontFamily: androidx.compose.ui.text.font.FontFamily?,
    color: Color,
    modifier: Modifier = Modifier,
    minFontSizeSp: Float = 11f,
    maxLines: Int = 2,
    borderColor: Color = Color.Black,
    borderWidth: Float = 0f
) {
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
    BoxWithConstraints(modifier = modifier) {
        val maxWidthPx = constraints.maxWidth
        // Busca binária: maior fonte em que o texto cabe em até [maxLines] linhas.
        val fontSizeSp = remember(text, baseFontSize, maxWidthPx, fontFamily, maxLines) {
            if (text.isEmpty() || maxWidthPx <= 0) {
                baseFontSize.value.coerceAtLeast(minFontSizeSp)
            } else {
                var lo = minFontSizeSp
                var hi = baseFontSize.value.coerceAtLeast(minFontSizeSp)
                var best = lo
                repeat(14) {
                    val mid = (lo + hi) / 2f
                    val layout = textMeasurer.measure(
                        text = androidx.compose.ui.text.AnnotatedString(text),
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = mid.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = fontFamily,
                            textAlign = TextAlign.Center
                        ),
                        constraints = androidx.compose.ui.unit.Constraints(
                            maxWidth = maxWidthPx
                        ),
                        maxLines = maxLines,
                        softWrap = true,
                        overflow = TextOverflow.Clip
                    )
                    val fits = !layout.hasVisualOverflow && layout.lineCount <= maxLines
                    if (fits) {
                        best = mid
                        lo = mid
                    } else {
                        hi = mid
                    }
                }
                best
            }
        }
        if (borderWidth > 0f) {
            OutlinedLyricText(
                text = text,
                color = color,
                borderColor = borderColor,
                borderWidthDp = borderWidth,
                fontSize = fontSizeSp.sp,
                fontFamily = fontFamily,
                fontWeight = FontWeight.Medium,
                italic = false,
                lineHeightMul = 1.25f,
                maxLines = maxLines,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Text(
                text = text,
                textAlign = TextAlign.Center,
                fontSize = fontSizeSp.sp,
                fontWeight = FontWeight.Medium,
                color = color,
                maxLines = maxLines,
                softWrap = true,
                overflow = TextOverflow.Ellipsis,
                fontFamily = fontFamily,
                lineHeight = (fontSizeSp * 1.25f).sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun formatSampleRate(hz: Int): String =
    if (hz <= 0) "—"
    else if (hz % 1000 == 0) "${hz / 1000} kHz"
    else String.format(Locale.getDefault(), "%.1f kHz", hz / 1000.0)

private fun formatChannels(n: Int): String = when (n) {
    0 -> "—"
    1 -> "Mono"
    2 -> "Estéreo"
    6 -> "5.1"
    8 -> "7.1"
    else -> "$n canais"
}

private fun technicalInfo(t: Track): List<String> = buildList {
    if (t.bitrate > 0) add("${Math.round(t.bitrate / 1000.0)} kbps")
    if (t.sampleRate > 0) add(formatSampleRate(t.sampleRate))
    if (t.bitDepth > 0) add("${t.bitDepth}-bit")
    // codec shown as icon instead of text when available
    if (t.channels > 0) add(formatChannels(t.channels))
}

/** O parser já grava em endMs o prazo de 1,5 s do backing vocal; não somar outra janela. */
private fun isBackingLineVisible(line: LyricLine, positionMs: Long): Boolean {
    val parsedEnd = line.endMs.takeIf { it > line.timeMs }
    val lastTimedWord = line.words.mapNotNull { it.timeMs.takeIf { time -> time >= 0L } }.maxOrNull()
    val expiration = parsedEnd ?: ((lastTimedWord ?: line.timeMs).coerceAtLeast(0L) + 1_500L)
    return positionMs <= expiration
}

private fun codecDrawableRes(codec: String?): Int? {
    if (codec.isNullOrBlank()) return null
    val c = codec.lowercase()
    return when {
        c.contains("mp3") || c.contains("mpeg") -> R.drawable.mp3
        c.contains("flac") -> R.drawable.flac
        c.contains("aac") || c.contains("m4a") -> R.drawable.aac
        c.contains("alac") -> R.drawable.alac
        c.contains("wav") || c.contains("pcm") -> R.drawable.wav
        c.contains("ogg") || c.contains("vorbis") || c.contains("opus") -> R.drawable.ogg
        c.contains("dsd") || c.contains("dsf") || c.contains("dff") -> R.drawable.dsd
        else -> null
    }
}

/** Fundo borrado isolado — evita refazer blur a cada atualização de posição. */
@Composable
private fun NowPlayingBlurredBackground(artPath: String?) {
    Crossfade(
        targetState = artPath,
        animationSpec = tween(500),
        label = "background",
        modifier = Modifier.fillMaxSize()
    ) { art ->
        if (art != null) {
            AsyncImage(
                model = File(art),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(14.dp, BlurredEdgeTreatment.Unbounded)
            )
        } else {
            Box(Modifier.fillMaxSize().background(AppGradient))
        }
    }
}

@Composable
fun NowPlayingScreen(
    track: Track,
    isPlaying: Boolean,
    playButtonIsPlaying: Boolean = isPlaying,
    shuffle: Boolean,
    repeatMode: Int = Player.REPEAT_MODE_OFF,
    queue: List<Track>,
    isFavorite: Boolean,
    lyricsFontPath: String? = null,
    lyricsTranslationFontPath: String? = null,
    getPosition: () -> Long,
    /** Duração real do player (Media3); usada quando a tag do arquivo está ausente (ex.: Dolby). */
    playerDurationMs: Long = 0L,
    onClose: () -> Unit,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit = {},
    onSkipToQueueItem: (Int) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit = {},
    onToggleFavoriteTrack: (Track) -> Unit = {},
    onDeleteTrack: (Track) -> Unit = {},
    onPlayNextFromQueue: (Track) -> Unit = {},
    pinnedQueueIds: Set<String> = emptySet(),
    onTogglePinQueue: (Track) -> Unit = {},
    isFavoriteCheck: (String) -> Boolean = { false },
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onEditTags: () -> Unit = {},
    onEditLyrics: () -> Unit = {},
    onAddToBlacklist: () -> Unit = {},
    artistNames: List<String>,
    onOpenArtist: (String) -> Unit,
    onOpenContainingFolder: () -> Unit = {},
    onOpenAlbum: () -> Unit,
    loadLyrics: suspend () -> List<LyricLine>,
    searchLyrics: suspend (String, String) -> List<LyricsSearchResult> = { _, _ -> emptyList() },
    embedLyricsResult: suspend (LyricsSearchResult, com.tolete.player.data.LyricsEmbedder.Target) -> List<LyricLine> = { _, _ -> emptyList() },
    appColors: AppColors = DefaultAppColors,
    milkPresets: List<String> = emptyList(),
    onDeletePreset: (String) -> Unit = {},
    milkIntervalSec: Int = 30,
    milkSwitchMode: String = "TIME",
    milkPresetOrder: String = "RANDOM",
    milkFavoritesOnly: Boolean = false,
    lyricsFontSize: Float = 22f,
    lyricsPrimaryColor: Int = 0xFFFFFFFF.toInt(),
    lyricsSecondaryColor: Int = 0x66FFFFFF,
    lyricsBorderColor: Int = 0xFF000000.toInt(),
    lyricsBorderWidth: Float = 0f,
    lyricsBackingBorderColor: Int = lyricsBorderColor,
    lyricsBackingBorderWidth: Float = lyricsBorderWidth,
    lyricsTranslationBorderColor: Int = lyricsBorderColor,
    lyricsTranslationBorderWidth: Float = lyricsBorderWidth,
    lyricsTranslationColor: Int = 0xFFB0BEC5.toInt(),
    lyricsBackingColor: Int = 0xFFCE93D8.toInt(),
    lyricsBackingSecondaryColor: Int = 0x99CE93D8.toInt(),
    playCount: Int = 0,
    firstPlayedAt: Long? = null,
    lastPlayedAt: Long = 0L,
    lyricsPopupEnabled: Boolean = false,
    onLyricsPopupEnabledChange: (Boolean) -> Unit = {},
    seekBarStyle: SeekBarStyle = SeekBarStyle.Standard,
    seekWaveformTheme: WaveformTheme = WaveformTheme.BARS,
    immersiveNowPlaying: Boolean = true,
    getPlaybackSpeed: () -> Float = { 1f },
    onPlaybackSpeed: (Float) -> Unit = {},
    lyricsEmphasisFontPath: String? = null,
    lyricsBackingFontPath: String? = null,
    audioOutputMode: AudioOutputBackend = AudioOutputBackend.DEFAULT,
    lastFmUsername: String? = null,
    libraryTracks: List<Track> = emptyList(),
    openLastFmTrackInfo: Boolean = false,
    onLastFmTrackInfoConsumed: () -> Unit = {}
) {
    // Mantém a tela acesa enquanto o Now Playing estiver aberto
    KeepScreenOn()

    // Modo imersivo: esconde barra de status e de navegação enquanto o Now Playing estiver aberto
    val view = LocalView.current
    DisposableEffect(immersiveNowPlaying) {
        if (!immersiveNowPlaying) {
            return@DisposableEffect onDispose { }
        }
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val prevBehavior = controller?.systemBarsBehavior
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (prevBehavior != null) controller.systemBarsBehavior = prevBehavior
        }
    }

    // Visualizador .milk: liga ao tocar na capa, desliga ao tocar no visualizador
    var showVisualizer by remember { mutableStateOf(false) }
    var presetLocked by remember { mutableStateOf(false) }
    var forcePresetPath by remember { mutableStateOf<String?>(null) }
    var currentPresetPath by remember { mutableStateOf<String?>(null) }
    var showPresetPicker by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(getPosition()) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    var showExactJump by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showTechDetails by remember { mutableStateOf(false) }
    var techPillShowsPreset by remember(track.id) { mutableStateOf(false) }
    var showLastFmTrackInfo by remember(track.id) { mutableStateOf(false) }
    var showOutputInfo by remember { mutableStateOf(false) }
    var showPlaybackSpeedDialog by remember { mutableStateOf(false) }
    var playbackSpeedDraft by remember { mutableFloatStateOf(getPlaybackSpeed().coerceIn(0.25f, 2f)) }
    var pendingDeleteTrack by remember { mutableStateOf<Track?>(null) }
    var showArtistMenu by remember(track.id) { mutableStateOf(false) }
    var showLyricsSearch by remember { mutableStateOf(false) }
    var showLyricsContributionDialog by remember(track.id) { mutableStateOf(false) }
    var lyricsSyncByDraft by remember(track.id) { mutableStateOf("") }
    var pendingLyricsContribution by remember(track.id) { mutableStateOf<List<LyricLine>?>(null) }
    var showFullLyrics by remember { mutableStateOf(false) }
    var showVideoDialog by remember { mutableStateOf(false) }
    var videoProgressHidden by remember { mutableStateOf(false) }

    LaunchedEffect(openLastFmTrackInfo, track.id) {
        if (openLastFmTrackInfo) {
            showLastFmTrackInfo = true
            onLastFmTrackInfoConsumed()
        }
    }

    var showEqualizer by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var sleepJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var lyrics by remember { mutableStateOf<List<LyricLine>>(emptyList()) }
    // Animação de coração grande sobre a capa (favoritar / desfavoritar)
    var heartBurst by remember { mutableStateOf<Boolean?>(null) } // true = fav, false = unfav, null = idle
    var prevFavorite by remember(track.id) { mutableStateOf(isFavorite) }
    // Direção e variedade da animação de troca de capa
    var artNavForward by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
        if (granted) onLyricsPopupEnabledChange(true)
    }
    var sleepMinutes by remember {
        mutableIntStateOf(
            context.getSharedPreferences("tolete", android.content.Context.MODE_PRIVATE)
                .getInt("sleep_timer_min", 0)
        )
    }
    var milkPresetFavorites by remember {
        mutableStateOf(
            context.getSharedPreferences("tolete", android.content.Context.MODE_PRIVATE)
                .getStringSet("milk_preset_favs", emptySet())?.toSet() ?: emptySet()
        )
    }
    LaunchedEffect(milkPresetFavorites) {
        context.getSharedPreferences("tolete", android.content.Context.MODE_PRIVATE)
            .edit().putStringSet("milk_preset_favs", milkPresetFavorites).apply()
    }

    fun chooseAutomaticPreset(): String? {
        val pool = if (milkFavoritesOnly) {
            milkPresets.filter { java.io.File(it).name in milkPresetFavorites }
        } else milkPresets
        if (pool.isEmpty()) return null
        if (milkPresetOrder == "SEQUENTIAL") {
            // Ordem alfabética pelo nome do arquivo, a partir do preset atual
            val ordered = pool.sortedBy {
                java.io.File(it).nameWithoutExtension.lowercase(java.util.Locale.ROOT)
            }
            val currentIndex = ordered.indexOf(currentPresetPath)
            return if (currentIndex < 0) ordered.first()
            else ordered[(currentIndex + 1) % ordered.size]
        }
        return pool.filterNot { it == currentPresetPath }.ifEmpty { pool }.randomOrNull()
    }

    // Modo por música: não consulta o intervalo. Se o visualizador for ligado depois da mudança,
    // escolhe um preset do pool atual; se já estava ligado, troca na transição da faixa.
    LaunchedEffect(
        track.id, milkSwitchMode, showVisualizer, milkPresets,
        milkPresetOrder, milkFavoritesOnly, milkPresetFavorites, presetLocked
    ) {
        if (milkSwitchMode == "TRACK" && showVisualizer && !presetLocked) {
            chooseAutomaticPreset()?.let { if (it != currentPresetPath) forcePresetPath = it }
        }
    }

    // Modo por tempo: o temporizador troca apenas o preset, nunca a música.
    LaunchedEffect(
        milkIntervalSec, milkSwitchMode, showVisualizer, isPlaying,
        milkPresets, milkPresetOrder, milkFavoritesOnly, milkPresetFavorites, presetLocked
    ) {
        if (milkSwitchMode != "TIME" || milkIntervalSec <= 0 || !showVisualizer || !isPlaying) {
            return@LaunchedEffect
        }
        while (true) {
            kotlinx.coroutines.delay(milkIntervalSec * 1000L)
            if (milkSwitchMode == "TIME" && showVisualizer && isPlaying && !presetLocked) {
                chooseAutomaticPreset()?.let { if (it != currentPresetPath) forcePresetPath = it }
            }
        }
    }

    LaunchedEffect(isFavorite, track.id) {
        if (isFavorite != prevFavorite) {
            heartBurst = isFavorite
            prevFavorite = isFavorite
            // favoritar: ~0.9s; desfavoritar.gif: 1.1s
            kotlinx.coroutines.delay(if (isFavorite) 900L else 1100L)
            heartBurst = null
        }
    }

    // Posição da UI geral (menos frequente) + posição fina para karaokê em tela cheia
    var lyricsPosition by remember { mutableLongStateOf(getPosition()) }
    // Enquanto arrasta ou logo após seek, não deixa o ticker do player puxar a barra de volta
    var isDraggingSeek by remember { mutableStateOf(false) }
    var pendingSeekMs by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(track.id, isPlaying) {
        position = getPosition()
        lyricsPosition = position
        // Não zera pendingSeekMs aqui: um seek feito pausado ainda precisa confirmar
        var pendingSince = if (pendingSeekMs != null) System.currentTimeMillis() else 0L
        while (true) {
            // Atualização a cada frame: 48–80 ms fazia a palavra avançar em blocos visíveis
            // e a transição podia ser substituída antes de terminar em versos curtos.
            delay(if (isPlaying) (if (showFullLyrics) 16 else 32) else 100)
            val p = getPosition()
            lyricsPosition = p
            if (isDraggingSeek || dragFraction != null) continue
            val pending = pendingSeekMs
            if (pending != null) {
                if (pendingSince == 0L) pendingSince = System.currentTimeMillis()
                // Confirma quando o player chega perto do alvo, ou desiste após ~1,5s
                if (kotlin.math.abs(p - pending) <= 250L || System.currentTimeMillis() - pendingSince > 1500L) {
                    pendingSeekMs = null
                    pendingSince = 0L
                    position = p
                }
                continue
            }
            pendingSince = 0L
            if (!isPlaying) {
                // Pausado: sincroniza se o player mudou (seek externo / rota de áudio)
                if (kotlin.math.abs(p - position) > 80L) position = p
                continue
            }
            // Em reprodução: atualiza com limiar menor para a seekbar não "saltar"
            if (kotlin.math.abs(p - position) >= 80L || p < position) {
                position = p
            }
        }
    }

    // Carrega letras sempre que a faixa muda — limpa na hora para não “vazar” a letra anterior
    LaunchedEffect(track.id) {
        val id = track.id
        lyrics = emptyList()
        val loaded = try {
            loadLyrics()
        } catch (_: Exception) {
            emptyList()
        }
        // Só aplica se ainda estamos na mesma faixa (evita corrida com auto-fetch lento)
        if (id == track.id) {
            lyrics = loaded
        }
    }

    val duration = when {
        playerDurationMs > 0L -> playerDurationMs
        track.duration > 0L -> track.duration
        else -> 1L
    }
    val fraction = dragFraction ?: (position.toFloat() / duration).coerceIn(0f, 1f)

    // ── Waveform da seekbar: só trabalha quando esse estilo está ativo ─────────────
    val seekWaveActive = seekBarStyle == SeekBarStyle.Waveform
    val seekPeaksDurationMs = if (track.duration > 0L) track.duration else playerDurationMs
    var seekPeaks by remember(track.id) {
        mutableStateOf<FloatArray?>(
            if (seekWaveActive && seekPeaksDurationMs > 0L) WaveformExtractor.seekCached(track.id, seekPeaksDurationMs) else null
        )
    }
    var seekPeaksFailed by remember(track.id) { mutableStateOf(false) }
    LaunchedEffect(track.id, seekPeaksDurationMs, seekWaveActive) {
        if (!seekWaveActive || seekPeaksDurationMs <= 0L || seekPeaks != null) return@LaunchedEffect
        if (WaveformExtractor.seekKnownFailed(track.id, seekPeaksDurationMs)) {
            seekPeaksFailed = true
            return@LaunchedEffect
        }
        seekPeaksFailed = false
        val result = withContext(Dispatchers.IO) {
            WaveformExtractor.extractForSeekBar(
                context = context.applicationContext,
                uri = track.uri,
                key = track.id,
                durationMs = seekPeaksDurationMs,
                cancelled = { !isActive }
            )
        }
        if (result != null) seekPeaks = result else seekPeaksFailed = true
    }
    val shownPosition = dragFraction?.let { (it * duration).toLong() } ?: position

    // Linha ativa (só sincronizadas com timeMs >= 0)
    val synced = remember(lyrics) { lyrics.filter { it.timeMs >= 0 } }
    // Linhas ativas simultâneas: principal + tradução + backing.
    // A tradução ({tr}) é ligada à linha principal pela ORDEM das linhas (a tradução logo depois,
    // ou logo antes no mesmo instante), nunca por comparação de tempo — assim ela sempre aparece
    // junto da principal e jamais ocupa o lugar dela.
    val activeLines = remember(synced, position / 50) {
        if (synced.isEmpty()) Triple<LyricLine?, LyricLine?, LyricLine?>(null, null, null)
        else {
            var mainIdx = -1
            var lastBg: LyricLine? = null
            var lastTr: LyricLine? = null
            for (i in synced.indices) {
                val l = synced[i]
                if (l.timeMs > position) break
                when {
                    l.isTranslation -> lastTr = l
                    l.isBackground -> lastBg = l
                    else -> mainIdx = i
                }
            }
            if (mainIdx >= 0) {
                val main = synced[mainIdx]
                val trIdx = pairedTranslationIndex(synced, mainIdx)
                val tr = if (trIdx >= 0) synced[trIdx] else null
                val latestBg = synced.lastOrNull {
                    it.isBackground && !it.isTranslation && it.timeMs <= position
                }
                val bg = latestBg?.takeIf { isBackingLineVisible(it, position) }
                Triple<LyricLine?, LyricLine?, LyricLine?>(main, bg, tr)
            } else {
                val bg = lastBg?.takeIf { isBackingLineVisible(it, position) }
                Triple<LyricLine?, LyricLine?, LyricLine?>(null, bg, lastTr)
            }
        }
    }
    val activeLine = activeLines.first
    val activeBgLine = activeLines.second
    val activeTrLine = activeLines.third

    val lyricsFont = remember(lyricsFontPath) {
        BuiltInFonts.resolveFamily(lyricsFontPath)
    }
    val lyricsTranslationFont = remember(lyricsTranslationFontPath, lyricsFontPath) {
        BuiltInFonts.resolveFamily(lyricsTranslationFontPath) ?: BuiltInFonts.resolveFamily(lyricsFontPath)
    }
    val lyricsBackingFont = remember(lyricsBackingFontPath, lyricsFontPath) {
        BuiltInFonts.resolveFamily(lyricsBackingFontPath) ?: BuiltInFonts.resolveFamily(lyricsFontPath)
    }
    val lyricsEmphasisFont = remember(lyricsEmphasisFontPath, lyricsFontPath) {
        BuiltInFonts.resolveFamily(lyricsEmphasisFontPath)
    }

    val layout = rememberLayoutScale()
    val hPad = if (layout.compact) 14.dp else layout.dp(24)
    val titleSp = if (layout.compact) 20.sp else layout.sp(24)
    val artistSp = if (layout.compact) 14.sp else layout.sp(16)
    val playBtn = if (layout.compact) 56.dp else layout.dp(64)
    val sideBtn = if (layout.compact) 44.dp else layout.dp(52)
    val iconSm = if (layout.compact) 36.dp else layout.dp(40)
    val topBarH = if (layout.compact) 48.dp else 56.dp

    val playerMenuBackdrop = rememberGraphicsLayer()
    var playerMenuBackdropOrigin by remember {
        mutableStateOf<androidx.compose.ui.geometry.Offset>(androidx.compose.ui.geometry.Offset.Zero)
    }
    // Não captura o GLSurfaceView do visualizer no backdrop de vidro — gera artefatos
    // e o Surface fura o menu. Com preset aberto, usa menu sem sample do GL.
    val capturePlayerBackdrop = LocalMenuVisualStyle.current == MenuVisualStyle.GLASS &&
        GlassConfig.quality != GlassQuality.LIGHT &&
        (showMenu || showArtistMenu || showQueue) &&
        !showVisualizer

    CompositionLocalProvider(
        LocalGlassBackdrop provides playerMenuBackdrop,
        LocalGlassBackdropOrigin provides playerMenuBackdropOrigin
    ) {
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { playerMenuBackdropOrigin = it.positionOnScreen() }
            .drawWithContent {
                if (capturePlayerBackdrop) {
                    playerMenuBackdrop.record { this@drawWithContent.drawContent() }
                    drawLayer(playerMenuBackdrop)
                } else {
                    drawContent()
                }
            }
            .background(Color(0xFF0B0B14))
            .pointerInput(Unit) {}
    ) {
        // Composable isolado: só redesenha quando a capa muda, não a cada tick de posição.
        NowPlayingBlurredBackground(artPath = track.artPath)
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(0.35f), Color.Black.copy(0.50f), Color.Black.copy(0.75f))
                    )
                )
        )

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = hPad)
        ) {
            // Top bar
            Row(
                Modifier.fillMaxWidth().height(topBarH),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose, modifier = Modifier.offset(x = (-12).dp)) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Fechar", modifier = Modifier.size(32.dp))
                }
                Text(
                    "Tocando agora",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.85f)
                )
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Mais opções")
                    }
                    AppDropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        shape = RoundedCornerShape(16.dp),
                        lensEnabled = true,
                        modifier = Modifier.appMenuOutline(RoundedCornerShape(16.dp))
                    ) {
                        LensMenuItem(
                            text = {
                                Text("Adicionar a uma playlist", color = MaterialTheme.colorScheme.onSurface)
                            },
                            onClick = {
                                showMenu = false
                                onAddToPlaylist()
                            }
                        )
                        LensMenuItem(
                            text = {
                                Text("Editar tags", color = MaterialTheme.colorScheme.onSurface)
                            },
                            onClick = {
                                showMenu = false
                                onEditTags()
                            }
                        )
                        LensMenuItem(
                            text = {
                                Text("Colocar na blacklist", color = MaterialTheme.colorScheme.onSurface)
                            },
                            onClick = {
                                showMenu = false
                                onAddToBlacklist()
                            }
                        )
                        LensMenuItem(
                            text = {
                                Text("Buscar letra", color = MaterialTheme.colorScheme.onSurface)
                            },
                            onClick = {
                                showMenu = false
                                showLyricsSearch = true
                            }
                        )
                        LensMenuItem(
                            text = {
                                Text(
                                    if (lyricsPopupEnabled) "Desativar letras em popup" else "Ativar letras em popup",
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                showMenu = false
                                if (lyricsPopupEnabled) {
                                    onLyricsPopupEnabledChange(false)
                                } else {
                                    val hasOverlay = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                                        Settings.canDrawOverlays(context)
                                    if (hasOverlay) {
                                        onLyricsPopupEnabledChange(true)
                                    } else {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                        overlayPermissionLauncher.launch(intent)
                                    }
                                }
                            }
                        )
                        LensMenuItem(
                            text = {
                                Text("Editar letra", color = MaterialTheme.colorScheme.onSurface)
                            },
                            onClick = {
                                showMenu = false
                                onEditLyrics()
                            }
                        )
                        LensMenuItem(
                            text = {
                                Text("Compartilhar letra completa (.lrc + .txt)", color = MaterialTheme.colorScheme.onSurface)
                            },
                            onClick = {
                                showMenu = false
                                scope.launch {
                                    val linesForShare = lyrics.takeIf { current ->
                                        current.any { it.text.isNotBlank() || it.words.any { word -> word.text.isNotBlank() } }
                                    } ?: try {
                                        loadLyrics().also { loaded -> if (loaded.isNotEmpty()) lyrics = loaded }
                                    } catch (_: Exception) {
                                        emptyList()
                                    }
                                    if (linesForShare.none { it.text.isNotBlank() || it.words.any { word -> word.text.isNotBlank() } }) {
                                        Toast.makeText(context, "Nenhuma letra disponível para compartilhar", Toast.LENGTH_SHORT).show()
                                        return@launch
                                    }
                                    val configuredName = context
                                        .getSharedPreferences("tolete", android.content.Context.MODE_PRIVATE)
                                        .getString(LyricsContributionShareHelper.SYNC_BY_PREFERENCE_KEY, "")
                                        .orEmpty()
                                        .trim()
                                    if (configuredName.isNotBlank()) {
                                        LyricsContributionShareHelper.share(context, track, linesForShare, configuredName)
                                    } else {
                                        lyricsSyncByDraft = ""
                                        pendingLyricsContribution = linesForShare
                                        showLyricsContributionDialog = true
                                    }
                                }
                            }
                        )
                        if (!lastFmUsername.isNullOrBlank()) {
                            LensMenuItem(
                                text = { Text("Informações da música no Last.fm", color = MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    showMenu = false
                                    showLastFmTrackInfo = true
                                }
                            )
                        }
                        LensMenuItem(
                            text = {
                                Text(
                                    if (sleepMinutes > 0) "Timer de sono (${sleepMinutes} min)" else "Timer de sono",
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                showMenu = false
                                showSleepTimer = true
                            }
                        )
                        LensMenuItem(
                            text = {
                                Text(
                                    "Velocidade: ${(getPlaybackSpeed() * 100f).toInt()}%",
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                playbackSpeedDraft = getPlaybackSpeed().coerceIn(0.25f, 2f)
                                showMenu = false
                                showPlaybackSpeedDialog = true
                            }
                        )
                        LensMenuItem(
                            text = {
                                Text("Gerar foto do trecho", color = MaterialTheme.colorScheme.onSurface)
                            },
                            onClick = {
                                showMenu = false
                                // Linhas completas (não só o texto): a foto aplica o destaque {des}
                                val lyric = activeLine?.takeIf { it.text.isNotBlank() }
                                val linesToShare = if (lyric != null) {
                                    listOf(lyric)
                                } else {
                                    lyrics.filter { it.text.isNotBlank() }.take(15)
                                }
                                if (linesToShare.isEmpty()) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Nenhuma letra disponível para gerar foto",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Gerando foto…",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                    LyricsShareHelper.shareSnippet(context, track, linesToShare)
                                }
                            }
                        )
                        LensMenuItem(
                            text = {
                                Text("Gerar vídeo", color = MaterialTheme.colorScheme.onSurface)
                            },
                            onClick = {
                                showMenu = false
                                when {
                                    LyricsVideoGenerator.isRunning() -> {
                                        videoProgressHidden = false
                                        android.widget.Toast.makeText(
                                            context,
                                            "Já existe um vídeo sendo gerado",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    // Também abre sem letra: o gerador faz vídeo só com capa/visualizador
                                    else -> showVideoDialog = true
                                }
                            }
                        )
                        LensMenuItem(
                            text = {
                                Text("Saída de áudio", color = MaterialTheme.colorScheme.onSurface)
                            },
                            onClick = {
                                showMenu = false
                                showOutputInfo = true
                            }
                        )
                        LensMenuItem(
                            text = {
                                Text("Excluir arquivo", color = MaterialTheme.colorScheme.error)
                            },
                            onClick = {
                                showMenu = false
                                pendingDeleteTrack = track
                            },
                            leadingIcon = {
                                Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            }
                        )
                    }
                }
            }

            // Informações técnicas (clicável)
            val info = technicalInfo(track)
            val codecRes = codecDrawableRes(track.codec)
            val infoPillModifier = Modifier
                .padding(horizontal = 4.dp, vertical = 6.dp)
                .fillMaxWidth()
                .height(36.dp)
                .pointerInput(milkPresets.size, currentPresetPath, techPillShowsPreset) {
                    var dragDistance = 0f
                    val threshold = 48.dp.toPx()
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            // Qualquer lado com deslocamento suficiente alterna entre
                            // informações técnicas e o menu de presets
                            if (kotlin.math.abs(dragDistance) >= threshold && milkPresets.isNotEmpty()) {
                                techPillShowsPreset = !techPillShowsPreset
                            }
                            dragDistance = 0f
                        },
                        onHorizontalDrag = { change, amount ->
                            dragDistance += amount
                            change.consume()
                        }
                    )
                }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (info.isNotEmpty() || codecRes != null || milkPresets.isNotEmpty()) {
                    if (techPillShowsPreset && milkPresets.isNotEmpty()) {
                        PresetGlassBar(
                            presetPath = currentPresetPath,
                            isFavorite = currentPresetPath?.let {
                                java.io.File(it).name in milkPresetFavorites
                            } == true,
                            onFavorite = {
                                currentPresetPath?.let { path ->
                                    val name = java.io.File(path).name
                                    milkPresetFavorites = if (name in milkPresetFavorites) {
                                        milkPresetFavorites - name
                                    } else milkPresetFavorites + name
                                }
                            },
                            onOpenList = { showPresetPicker = true },
                            modifier = infoPillModifier
                        )
                    } else {
                        Row(
                            modifier = infoPillModifier
                                .clip(CircleShape)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.22f),
                                            Color.White.copy(alpha = 0.08f),
                                            Color.White.copy(alpha = 0.14f)
                                        )
                                    ),
                                    CircleShape
                                )
                                .border(1.dp, Color.White.copy(0.30f), CircleShape)
                                .clickable { showTechDetails = true }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (codecRes != null) {
                                Image(
                                    painter = painterResource(codecRes),
                                    contentDescription = track.codec,
                                    modifier = Modifier
                                        .height(18.dp)
                                        .padding(end = if (info.isNotEmpty()) 8.dp else 0.dp)
                                )
                            }
                            if (info.isNotEmpty()) {
                                Text(
                                    info.joinToString("  ·  "),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.92f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Capa + letra. A letra ocupa todo o espaço entre a capa e o título
            // e fica centralizada nele (sobra espaço para 2 linhas quando a frase é longa).
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val topPadding = if (layout.compact) 4.dp else 8.dp
                val lyricsMinHeight = if (layout.compact) 48.dp else 72.dp
                // Em telas baixas limita a capa para sobrar espaço a título/controles
                val maxArt = if (layout.compact) maxHeight * 0.42f else maxHeight - lyricsMinHeight - topPadding
                val side = maxOf(minOf(maxWidth, maxArt), if (layout.compact) 96.dp else 120.dp)

                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier
                            .padding(top = topPadding)
                            .size(side)
                            .pointerInput(track.id, milkPresets.size) {
                                detectTapGestures(
                                    onDoubleTap = { onToggleFavorite() },
                                    onTap = {
                                        when {
                                            showVisualizer -> showVisualizer = false
                                            !ProjectMNative.isAvailable -> Toast.makeText(
                                                context,
                                                "Visualizador indisponível nesta versão do app",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            milkPresets.isEmpty() -> Toast.makeText(
                                                context,
                                                "Importe presets .milk em Configurações › Visualizador",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            else -> showVisualizer = true
                                        }
                                    }
                                )
                            }
                            .pointerInput(track.id, showVisualizer, milkPresets.size, currentPresetPath) {
                                var totalX = 0f
                                var totalY = 0f
                                detectDragGestures(
                                    onDragEnd = {
                                        when {
                                            // deslizar para cima → bloqueia preset
                                            totalY < -80f && kotlin.math.abs(totalY) > kotlin.math.abs(totalX) -> {
                                                if (showVisualizer) {
                                                    presetLocked = true
                                                    Toast.makeText(context, "Preset bloqueado", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                            // deslizar para baixo → desbloqueia preset
                                            totalY > 80f && kotlin.math.abs(totalY) > kotlin.math.abs(totalX) -> {
                                                if (showVisualizer && presetLocked) {
                                                    presetLocked = false
                                                    Toast.makeText(context, "Preset desbloqueado", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                            // com visualizer: esquerda/direita trocam preset ALEATORIAMENTE; sem: trocam faixa
                                            totalX < -80f && kotlin.math.abs(totalX) > kotlin.math.abs(totalY) -> {
                                                if (showVisualizer && milkPresets.isNotEmpty()) {
                                                    val candidates = milkPresets.filter { it != currentPresetPath }
                                                    val next = (candidates.ifEmpty { milkPresets }).random()
                                                    forcePresetPath = next
                                                    presetLocked = true
                                                    currentPresetPath = next
                                                } else {
                                                    artNavForward = true
                                                    onNext()
                                                }
                                            }
                                            totalX > 80f && kotlin.math.abs(totalX) > kotlin.math.abs(totalY) -> {
                                                if (showVisualizer && milkPresets.isNotEmpty()) {
                                                    val candidates = milkPresets.filter { it != currentPresetPath }
                                                    val prev = (candidates.ifEmpty { milkPresets }).random()
                                                    forcePresetPath = prev
                                                    presetLocked = true
                                                    currentPresetPath = prev
                                                } else {
                                                    artNavForward = false
                                                    onPrevious()
                                                }
                                            }
                                        }
                                        totalX = 0f
                                        totalY = 0f
                                    },
                                    onDragCancel = { totalX = 0f; totalY = 0f },
                                    onDrag = { _, amount ->
                                        totalX += amount.x
                                        totalY += amount.y
                                    }
                                )
                            }
                    ) {
                        // Próxima: capa rola da direita → esquerda; anterior: esquerda → direita
                        AnimatedContent(
                            targetState = track.id to track.artPath,
                            transitionSpec = {
                                val forward = artNavForward
                                (slideInHorizontally(
                                    initialOffsetX = { full -> if (forward) full else -full },
                                    animationSpec = tween(420, easing = FastOutSlowInEasing)
                                ) + fadeIn(tween(280))) togetherWith
                                    (slideOutHorizontally(
                                        targetOffsetX = { full -> if (forward) -full else full },
                                        animationSpec = tween(360, easing = FastOutSlowInEasing)
                                    ) + fadeOut(tween(240)))
                            },
                            label = "artCarousel",
                            modifier = Modifier.fillMaxSize()
                        ) { (_, path) ->
                            ArtImage(
                                path = path,
                                size = side,
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.shadow(24.dp, RoundedCornerShape(20.dp))
                            )
                        }
                        // Visualizador .milk: ocupa exatamente a área da capa (mesmos cantos)
                        if (showVisualizer) {
                            // Só pausa o GL em overlays Compose que “furam” a superfície.
                            // O picker de presets é um Dialog próprio — pausar o GL aqui
                            // deixava a capa preta e atrapalhava a seleção após a busca.
                            val overlayMenusOpen = showMenu || showArtistMenu || showQueue
                            MilkVisualizer(
                                presets = milkPresets,
                                intervalSeconds = milkIntervalSec,
                                cornerRadius = 20.dp,
                                locked = presetLocked,
                                isPlaying = isPlaying,
                                surfaceActive = !overlayMenusOpen,
                                forcePresetPath = forcePresetPath,
                                modifier = Modifier.fillMaxSize(),
                                onFailure = {
                                    showVisualizer = false
                                    Toast.makeText(context, "Não foi possível iniciar o visualizador", Toast.LENGTH_SHORT).show()
                                },
                                onCurrentPreset = { path ->
                                    // Atualiza sempre o nome exibido (auto e manual)
                                    if (path != null) currentPresetPath = path
                                }
                            )
                            // Limpa force após aplicar para não reaplicar
                            LaunchedEffect(forcePresetPath) {
                                if (forcePresetPath != null) {
                                    kotlinx.coroutines.delay(400)
                                    forcePresetPath = null
                                }
                            }
                            if (presetLocked) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = "Preset bloqueado",
                                    tint = Color.White.copy(0.9f),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(10.dp)
                                        .size(22.dp)
                                        .background(Color.Black.copy(0.45f), CircleShape)
                                        .padding(3.dp)
                                )
                            }
                            }
                        if (track.isHiRes) {
                            HdBadge(Modifier.align(Alignment.BottomEnd).padding(12.dp))
                        }
                        // coração no canto inferior esquerdo: oco = não favorita, cheio = favorita
                        FavoriteHeart(
                            isFavorite = isFavorite,
                            onClick = onToggleFavorite,
                            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                            overArt = true
                        )
                        // Coração gigante (favoritar) / partido (desfavoritar) sobre a capa
                        // Qualificado: evita ColumnScope.AnimatedVisibility (este Box está dentro de Column)
                        androidx.compose.animation.AnimatedVisibility(
                            visible = heartBurst != null,
                            enter = fadeIn(tween(120)),
                            exit = fadeOut(tween(300)),
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            val fav = heartBurst == true
                            val ctx = LocalContext.current
                            val gifLoader = remember(ctx) {
                                ImageLoader.Builder(ctx)
                                    .components { add(GifDecoder.Factory()) }
                                    .build()
                            }
                            AsyncImage(
                                model = ImageRequest.Builder(ctx)
                                    .data(if (fav) R.raw.favoritar else R.raw.desfavoritar)
                                    .build(),
                                imageLoader = gifLoader,
                                contentDescription = null,
                                modifier = Modifier.size(140.dp)
                            )
                        }
                    }

                    // Letra — palavra-a-palavra quando houver sync; senão linha inteira
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clickable(enabled = lyrics.isNotEmpty()) { showFullLyrics = true },
                        contentAlignment = Alignment.Center
                    ) {
                        if (synced.isNotEmpty()) {
                            val line = activeLine
                            val finePos = if (isPlaying) lyricsPosition else position
                            Column(
                                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                            if (line != null && line.hasSyllableSync) {
                                SyllableLine(
                                    line = line,
                                    position = finePos,
                                    fontSize = lyricsFontSize.sp,
                                    fontFamily = lyricsFont,
                                    primary = Color(lyricsPrimaryColor).copy(alpha = 0.95f),
                                    secondary = Color(lyricsSecondaryColor),
                                    borderColor = Color(lyricsBorderColor),
                                    borderWidth = lyricsBorderWidth,
                                    emphasisFont = lyricsEmphasisFont,
                                    isBackground = false,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else if (line != null) {
                            AnimatedContent(
                                targetState = activeLine?.text.orEmpty(),
                                transitionSpec = {
                                    (slideInVertically(
                                        initialOffsetY = { it / 4 },
                                        animationSpec = tween(260, easing = FastOutSlowInEasing)
                                    ) + fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                                        scaleIn(initialScale = 0.94f, animationSpec = tween(260, easing = FastOutSlowInEasing))) togetherWith
                                        (slideOutVertically(
                                            targetOffsetY = { -it / 5 },
                                            animationSpec = tween(200, easing = FastOutSlowInEasing)
                                        ) + fadeOut(tween(180)) +
                                            scaleOut(targetScale = 1.02f, animationSpec = tween(200)))
                                },
                                label = "lyricLine"
                            ) { lineText ->
                                if (lineText.isNotEmpty()) {
                                    AutoSizeSingleLineLyric(
                                        text = lineText,
                                        baseFontSize = lyricsFontSize.sp,
                                        fontFamily = lyricsFont,
                                        color = Color(lyricsPrimaryColor).copy(alpha = 0.95f),
                                        borderColor = Color(lyricsBorderColor),
                                        borderWidth = lyricsBorderWidth,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            }
                            // Tradução e backing simultâneos (primeiro plano = principal)
                            activeTrLine?.takeIf { it.text.isNotBlank() }?.let { trLine ->
                                if (trLine.hasSyllableSync) {
                                    SyllableLine(
                                        line = trLine,
                                        position = finePos,
                                        fontSize = (lyricsFontSize - 3f).coerceAtLeast(12f).sp,
                                        fontFamily = lyricsTranslationFont ?: lyricsFont,
                                        primary = Color(lyricsTranslationColor).copy(alpha = 0.95f),
                                        secondary = Color(lyricsTranslationColor).copy(alpha = 0.55f),
                                        borderColor = Color(lyricsTranslationBorderColor),
                                        borderWidth = lyricsTranslationBorderWidth,
                                        emphasisFont = lyricsEmphasisFont,
                                        isTranslation = true,
                                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                                    )
                                } else {
                                    val trFill = Color(lyricsTranslationColor).copy(alpha = 0.9f)
                                    if (lyricsTranslationBorderWidth > 0f) {
                                        OutlinedLyricText(
                                            text = trLine.text,
                                            color = trFill,
                                            borderColor = Color(lyricsTranslationBorderColor),
                                            borderWidthDp = lyricsTranslationBorderWidth,
                                            fontSize = (lyricsFontSize - 3f).coerceAtLeast(12f).sp,
                                            fontFamily = lyricsTranslationFont ?: lyricsFont,
                                            fontWeight = FontWeight.Normal,
                                            italic = true,
                                            lineHeightMul = 1.2f,
                                            maxLines = 2,
                                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                                        )
                                    } else {
                                        Text(
                                            trLine.text,
                                            color = trFill,
                                            fontSize = (lyricsFontSize - 3f).coerceAtLeast(12f).sp,
                                            fontFamily = lyricsTranslationFont ?: lyricsFont,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                            textAlign = TextAlign.Center,
                                            maxLines = 2,
                                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                                        )
                                    }
                                }
                            }
                            activeBgLine?.takeIf { it.text.isNotBlank() }?.let { bgLine ->
                                if (bgLine.hasSyllableSync) {
                                    SyllableLine(
                                        line = bgLine,
                                        position = finePos,
                                        fontSize = (lyricsFontSize - 4f).coerceAtLeast(11f).sp,
                                        fontFamily = lyricsBackingFont ?: lyricsFont,
                                        primary = Color(lyricsBackingColor).copy(alpha = 0.95f),
                                        secondary = Color(lyricsBackingSecondaryColor),
                                        borderColor = Color(lyricsBackingBorderColor),
                                        borderWidth = lyricsBackingBorderWidth,
                                        emphasisFont = lyricsEmphasisFont,
                                        isBackground = true,
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                    )
                                } else {
                                    val bgFill = Color(lyricsBackingColor).copy(alpha = 0.85f)
                                    if (lyricsBackingBorderWidth > 0f) {
                                        OutlinedLyricText(
                                            text = bgLine.text,
                                            color = bgFill,
                                            borderColor = Color(lyricsBackingBorderColor),
                                            borderWidthDp = lyricsBackingBorderWidth,
                                            fontSize = (lyricsFontSize - 4f).coerceAtLeast(11f).sp,
                                            fontFamily = lyricsBackingFont ?: lyricsFont,
                                            fontWeight = FontWeight.Normal,
                                            italic = true,
                                            lineHeightMul = 1.2f,
                                            maxLines = 2,
                                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                        )
                                    } else {
                                        Text(
                                            bgLine.text,
                                            color = bgFill,
                                            fontSize = (lyricsFontSize - 4f).coerceAtLeast(11f).sp,
                                            fontFamily = lyricsBackingFont ?: lyricsFont,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                            textAlign = TextAlign.Center,
                                            maxLines = 2,
                                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                            } // Column
                        } else if (lyrics.isNotEmpty()) {
                            // Sem timestamps: trecho atual (até 2 linhas na tela)
                            val plain = lyrics.firstOrNull { it.text.isNotBlank() }?.text.orEmpty()
                            if (plain.isNotEmpty()) {
                                AutoSizeSingleLineLyric(
                                    text = plain,
                                    baseFontSize = lyricsFontSize.sp,
                                    fontFamily = lyricsFont,
                                    color = Color(lyricsPrimaryColor).copy(alpha = 0.95f),
                                    borderColor = Color(lyricsBorderColor),
                                    borderWidth = lyricsBorderWidth,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Título em uma linha; se for longo, rola (marquee)
            Text(
                track.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (layout.compact) 2.dp else 4.dp)
                    .clickable { onOpenContainingFolder() }
                    .basicMarquee(
                        iterations = Int.MAX_VALUE,
                        animationMode = MarqueeAnimationMode.Immediately,
                        initialDelayMillis = 1200,
                        velocity = 40.dp
                    ),
                fontSize = titleSp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                softWrap = false,
                color = Color.White
            )
            Spacer(Modifier.height(10.dp))
            // Artista com ícone
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (artistNames.size > 1) showArtistMenu = true
                        else onOpenArtist(artistNames.firstOrNull() ?: track.artist)
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Person,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = Color.White.copy(alpha = 0.75f)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    track.artist,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                AppDropdownMenu(
                    expanded = showArtistMenu,
                    onDismissRequest = { showArtistMenu = false },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.appMenuOutline(RoundedCornerShape(16.dp))
                ) {
                    artistNames.forEach { name ->
                        LensMenuItem(
                            text = { Text(name, color = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                showArtistMenu = false
                                onOpenArtist(name)
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            // Álbum com ícone
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = if (layout.compact) 6.dp else 12.dp)
                    .clickable(onClick = onOpenAlbum),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Album,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = Color.White.copy(alpha = 0.75f)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    track.album,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            SeekBar(
                fraction = fraction,
                accentColor = appColors.accent,
                style = seekBarStyle,
                isPlaying = isPlaying,
                peaks = seekPeaks,
                peaksFailed = seekPeaksFailed,
                waveformTheme = seekWaveformTheme,
                durationMs = duration,
                onRequestExactJump = { showExactJump = true },
                onDrag = {
                    isDraggingSeek = true
                    dragFraction = it
                },
                onDragEnd = { f ->
                    val target = (f * duration).toLong().coerceIn(0L, duration)
                    position = target
                    pendingSeekMs = target
                    dragFraction = null
                    isDraggingSeek = false
                    onSeek(target)
                },
                onDragCancel = {
                    dragFraction = null
                    isDraggingSeek = false
                }
            )
            if (showExactJump) {
                ExactJumpDialog(
                    durationMs = duration,
                    initialMs = position,
                    onDismiss = { showExactJump = false },
                    onConfirm = { ms ->
                        val target = ms.coerceIn(0L, duration)
                        showExactJump = false
                        position = target
                        pendingSeekMs = target
                        onSeek(target)
                    }
                )
            }

            // Tempos
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatDuration(shownPosition), fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f))
                Text(formatDuration(duration), fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f))
            }
            // Controles em uma única linha: shuffle, repeat, prev, play, next, eq, queue
            // play/pause, previous e next maiores que os outros quatro
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleShuffle, modifier = Modifier.size(iconSm)) {
                    Icon(
                        painter = painterResource(if (shuffle) R.drawable.ic_random_on else R.drawable.ic_random_off),
                        contentDescription = if (shuffle) "Aleatório ligado" else "Aleatório desligado",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(onClick = onCycleRepeat, modifier = Modifier.size(iconSm)) {
                    val repeatIcon = when (repeatMode) {
                        Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_once
                        Player.REPEAT_MODE_ALL -> R.drawable.ic_repeat_all
                        else -> R.drawable.ic_repeat_off
                    }
                    Icon(
                        painter = painterResource(repeatIcon),
                        contentDescription = when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> "Repetir música atual"
                            Player.REPEAT_MODE_ALL -> "Repetir fila"
                            else -> "Repetição desligada"
                        },
                        tint = Color.Unspecified,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(onClick = { artNavForward = false; onPrevious() }, modifier = Modifier.size(sideBtn)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_previous),
                        contentDescription = "Anterior",
                        tint = Color.White,
                        modifier = Modifier.size(sideBtn * 0.65f)
                    )
                }
                Box(
                    Modifier
                        .size(playBtn)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(onClick = onToggle),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(if (playButtonIsPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                        contentDescription = if (playButtonIsPlaying) "Pausar" else "Tocar",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(playBtn * 0.56f)
                    )
                }
                IconButton(onClick = { artNavForward = true; onNext() }, modifier = Modifier.size(sideBtn)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_next),
                        contentDescription = "Próxima",
                        tint = Color.White,
                        modifier = Modifier.size(sideBtn * 0.65f)
                    )
                }
                IconButton(onClick = { showEqualizer = true }, modifier = Modifier.size(iconSm)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_equalizer),
                        contentDescription = "Equalizador",
                        modifier = Modifier.size(24.dp),
                        tint = Color.White.copy(alpha = 0.9f)
                    )
                }
                IconButton(onClick = { showQueue = true }, modifier = Modifier.size(iconSm)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_queue),
                        contentDescription = "Fila",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // ---- Vídeo com letra ----
        DisposableEffect(Unit) {
            LyricsVideoGenerator.attachUi()
            onDispose { LyricsVideoGenerator.detachUi() }
        }
        val videoState by LyricsVideoGenerator.state.collectAsState()
        LaunchedEffect(videoState) {
            when (val vs = videoState) {
                is LyricsVideoGenerator.State.Done -> {
                    ShareImageActions.offerSaveOrShare(
                        context = context,
                        cacheFile = vs.file,
                        subdir = "Lyrics Videos",
                        shareTitle = "Compartilhar vídeo",
                        shareText = vs.trackTitle + " — " + vs.artist,
                        mime = "video/mp4",
                        dialogTitle = "Vídeo gerado"
                    )
                    LyricsVideoGenerator.consume()
                }
                is LyricsVideoGenerator.State.Saved -> {
                    android.widget.Toast.makeText(
                        context, "Vídeo salvo em Lyron/Lyrics Videos", android.widget.Toast.LENGTH_LONG
                    ).show()
                    LyricsVideoGenerator.consume()
                }
                is LyricsVideoGenerator.State.Failed -> {
                    android.widget.Toast.makeText(context, vs.message, android.widget.Toast.LENGTH_LONG).show()
                    LyricsVideoGenerator.consume()
                }
                else -> Unit
            }
        }

        if (showVideoDialog) {
            LyricsVideoDialog(
                track = track,
                currentPositionMs = position,
                milkPresets = milkPresets,
                initialMilkPreset = currentPresetPath,
                hasExternalLrc = LyricsEmbedder.hasExternalLrc(track),
                onDismiss = { showVideoDialog = false },
                loadLyrics = { source ->
                    when (source) {
                        LyricsEmbedder.Target.LRC_FILE -> LyricsEmbedder.loadExternalLrc(track)
                        LyricsEmbedder.Target.EMBEDDED -> LyricsExtractor.loadEmbedded(context, track)
                    }
                },
                onGenerate = { startMs, endMs, shortSide, aspect, fps, milkPresetPath, lyricsSource, useLyrics ->
                    showVideoDialog = false
                    videoProgressHidden = false
                    scope.launch {
                        // Sem letra (escolhido pelo usuário ou fonte sem letra sincronizada): o vídeo sai
                        // só com capa/visualizador, título, artista e barra de progresso.
                        val sourceLines = if (!useLyrics) {
                            emptyList()
                        } else {
                            when (lyricsSource) {
                                LyricsEmbedder.Target.LRC_FILE ->
                                    LyricsEmbedder.loadExternalLrc(track)
                                LyricsEmbedder.Target.EMBEDDED ->
                                    LyricsExtractor.loadEmbedded(context, track)
                            }.filter { it.timeMs >= 0 }
                        }
                        if (useLyrics && sourceLines.isEmpty()) {
                            android.widget.Toast.makeText(
                                context,
                                "Sem letra sincronizada nesta fonte: gerando o vídeo sem letra",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                        LyricsVideoGenerator.start(
                            context = context,
                            track = track,
                            lyrics = sourceLines,
                            options = LyricsVideoGenerator.Options(
                                startMs = startMs,
                                endMs = endMs,
                                shortSide = shortSide,
                                aspect = aspect,
                                fps = fps,
                                milkPresetPath = milkPresetPath,
                                style = LyricsVideoStyle(
                                    fontPath = lyricsFontPath,
                                    translationFontPath = lyricsTranslationFontPath,
                                    primaryColor = lyricsPrimaryColor,
                                    secondaryColor = lyricsSecondaryColor,
                                    borderColor = lyricsBorderColor,
                                    borderWidth = lyricsBorderWidth,
                                    translationColor = lyricsTranslationColor,
                                    emphasisFontPath = lyricsEmphasisFontPath
                                )
                            )
                        )
                    }
                }
            )
        }

        (videoState as? LyricsVideoGenerator.State.Running)?.let { running ->
            if (!videoProgressHidden) {
                LyricsVideoProgressDialog(
                    running = running,
                    onCancel = { LyricsVideoGenerator.cancel() },
                    onHide = { videoProgressHidden = true }
                )
            }
        }

        if (showEqualizer) {
            EqualizerSheet(onDismiss = { showEqualizer = false })
        }

        if (showSleepTimer) {
            SleepTimerDialog(
                currentMinutes = sleepMinutes,
                onDismiss = { showSleepTimer = false },
                onSet = { mins ->
                    sleepMinutes = mins
                    context.getSharedPreferences("tolete", android.content.Context.MODE_PRIVATE)
                        .edit().putInt("sleep_timer_min", mins).apply()
                    sleepJob?.cancel()
                    if (mins > 0) {
                        sleepJob = scope.launch {
                            kotlinx.coroutines.delay(mins * 60_000L)
                            onToggle() // pausa se estiver tocando
                            sleepMinutes = 0
                            context.getSharedPreferences("tolete", android.content.Context.MODE_PRIVATE)
                                .edit().putInt("sleep_timer_min", 0).apply()
                        }
                    }
                    showSleepTimer = false
                }
            )
        }

        if (showQueue) {
            QueueSheet(
                queue = queue,
                currentId = track.id,
                appColors = appColors,
                onDismiss = { showQueue = false },
                onSkipTo = onSkipToQueueItem,
                onMove = onMoveQueueItem,
                onRemoveFromQueue = onRemoveFromQueue,
                onToggleFavorite = onToggleFavoriteTrack,
                onDelete = { t -> pendingDeleteTrack = t },
                onPlayNext = onPlayNextFromQueue,
                isFavorite = isFavoriteCheck,
                pinnedIds = pinnedQueueIds,
                onTogglePin = onTogglePinQueue,
            )
        }

        pendingDeleteTrack?.let { toDelete ->
            AppAlertDialog(
                onDismissRequest = { pendingDeleteTrack = null },
                title = { Text("Excluir permanentemente?") },
                text = {
                    Text(
                        "\"${toDelete.title}\" será apagado do armazenamento. Esta ação não pode ser desfeita."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDeleteTrack(toDelete)
                            pendingDeleteTrack = null
                        }
                    ) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteTrack = null }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        if (showOutputInfo) {
            AudioOutputInfoDialog(
                mode = audioOutputMode,
                onDismiss = { showOutputInfo = false }
            )
        }

        if (showPlaybackSpeedDialog) {
            PlaybackSpeedDialog(
                value = playbackSpeedDraft,
                onValueChange = { playbackSpeedDraft = it },
                onDismiss = { showPlaybackSpeedDialog = false },
                onApply = {
                    onPlaybackSpeed(playbackSpeedDraft)
                    showPlaybackSpeedDialog = false
                }
            )
        }

        if (showTechDetails) {
            TechDetailsDialog(
                track = track,
                onDismiss = { showTechDetails = false },
                playCount = playCount,
                firstPlayedAt = firstPlayedAt,
                lastPlayedAt = lastPlayedAt
            )
        }
        if (showLastFmTrackInfo) {
            lastFmUsername?.takeIf { it.isNotBlank() }?.let { user ->
                NowPlayingLastFmInfoSheet(
                    user = user,
                    track = track,
                    tracks = libraryTracks,
                    appColors = appColors,
                    firstPlayedAt = firstPlayedAt,
                    lastPlayedAt = lastPlayedAt,
                    onDismiss = { showLastFmTrackInfo = false }
                )
            }
        }

        if (showFullLyrics && lyrics.isNotEmpty()) {
            FullscreenLyrics(
                track = track,
                // Conserva linhas plain de arquivos mistos; o renderer usa timestamps apenas
                // para destaque/seek e deixa as linhas sem tempo visíveis na lista.
                lyrics = lyrics,
                position = lyricsPosition,
                lyricsFont = lyricsFont,
                translationFont = lyricsTranslationFont,
                backingFont = lyricsBackingFont,
                emphasisFont = lyricsEmphasisFont,
                fontSizeSp = lyricsFontSize,
                primaryColor = Color(lyricsPrimaryColor),
                secondaryColor = Color(lyricsSecondaryColor),
                translationColor = Color(lyricsTranslationColor),
                backingColor = Color(lyricsBackingColor),
                backingSecondaryColor = Color(lyricsBackingSecondaryColor),
                borderColor = Color(lyricsBorderColor),
                borderWidth = lyricsBorderWidth,
                backingBorderColor = Color(lyricsBackingBorderColor),
                backingBorderWidth = lyricsBackingBorderWidth,
                translationBorderColor = Color(lyricsTranslationBorderColor),
                translationBorderWidth = lyricsTranslationBorderWidth,
                onSeek = { ms ->
                    onSeek(ms)
                    position = ms
                    lyricsPosition = ms
                },
                onClose = { showFullLyrics = false }
            )
        }

        if (showLyricsSearch) {
            var pendingLyricResult by remember { mutableStateOf<LyricsSearchResult?>(null) }
            LyricsSearchDialog(
                initialTitle = track.title,
                initialArtist = track.artist,
                onDismiss = {
                    showLyricsSearch = false
                    pendingLyricResult = null
                },
                onSearch = { t, a -> searchLyrics(t, a) },
                onPick = { result ->
                    pendingLyricResult = result
                }
            )
            pendingLyricResult?.let { result ->
                AppAlertDialog(
                    onDismissRequest = { pendingLyricResult = null },
                    title = { Text(result.variant?.let { "Salvar letra · ${it.label}" } ?: "Salvar letra") },
                    text = {
                        Text(
                            "Como deseja salvar a letra encontrada?\n\n" +
                                "• Arquivo .lrc — ao lado do áudio (precisa de Acesso a todos os arquivos)\n" +
                                "• Embutir no arquivo — grava nas tags do áudio"
                        )
                    },
                    confirmButton = {
                        Column {
                            TextButton(
                                onClick = {
                                    val r = result
                                    pendingLyricResult = null
                                    scope.launch {
                                        val lines = embedLyricsResult(
                                            r,
                                            com.tolete.player.data.LyricsEmbedder.Target.LRC_FILE
                                        )
                                        if (lines.isNotEmpty()) lyrics = lines
                                        showLyricsSearch = false
                                        android.widget.Toast.makeText(
                                            context,
                                            if (lines.isNotEmpty()) "Letra salva em .lrc"
                                            else "Falha ao salvar .lrc (ative Acesso a todos os arquivos)",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            ) { Text("Salvar em .lrc") }
                            TextButton(
                                onClick = {
                                    val r = result
                                    pendingLyricResult = null
                                    scope.launch {
                                        val lines = embedLyricsResult(
                                            r,
                                            com.tolete.player.data.LyricsEmbedder.Target.EMBEDDED
                                        )
                                        if (lines.isNotEmpty()) lyrics = lines
                                        showLyricsSearch = false
                                        android.widget.Toast.makeText(
                                            context,
                                            if (lines.isNotEmpty()) "Letra embutida no arquivo"
                                            else "Falha ao embutir letra",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            ) { Text("Embutir no arquivo") }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingLyricResult = null }) {
                            Text("Cancelar")
                        }
                    }
                )
            }
        }
        if (showLyricsContributionDialog) {
            val dialogPalette = menuPalette(LocalAppColors.current)
            AppAlertDialog(
                onDismissRequest = {
                    showLyricsContributionDialog = false
                    pendingLyricsContribution = null
                },
                title = { Text("Preparar arquivos de letra", color = dialogPalette.content) },
                text = {
                    Column {
                        Text(
                            "Informe o nome que será colocado em sync_by no trecho para o index.json do GitHub.",
                            color = dialogPalette.muted
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = lyricsSyncByDraft,
                            onValueChange = { lyricsSyncByDraft = it },
                            label = { Text("Nome para sync_by", color = dialogPalette.muted) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = dialogPalette.content,
                                unfocusedTextColor = dialogPalette.content,
                                focusedBorderColor = dialogPalette.accent,
                                unfocusedBorderColor = dialogPalette.muted,
                                cursorColor = dialogPalette.accent,
                                focusedLabelColor = dialogPalette.accent,
                                unfocusedLabelColor = dialogPalette.muted
                            )
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "O nome pode ser salvo depois em Configurações → Avançado.",
                            fontSize = 12.sp,
                            color = dialogPalette.muted
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = lyricsSyncByDraft.isNotBlank(),
                        onClick = {
                            val author = lyricsSyncByDraft.trim()
                            showLyricsContributionDialog = false
                            pendingLyricsContribution?.let { lines ->
                                LyricsContributionShareHelper.share(context, track, lines, author)
                            }
                            pendingLyricsContribution = null
                        }
                    ) { Text("Gerar e compartilhar", color = dialogPalette.accent) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showLyricsContributionDialog = false
                        pendingLyricsContribution = null
                    }) {
                        Text("Cancelar", color = dialogPalette.content)
                    }
                }
            )
        }
        if (showPresetPicker) {
            MilkPresetPickerDialog(
                presets = milkPresets,
                currentPath = currentPresetPath,
                onDismiss = { showPresetPicker = false },
                onSelect = { path ->
                    forcePresetPath = path
                    presetLocked = true
                    showPresetPicker = false
                    showVisualizer = true
                },
                onDelete = onDeletePreset,
                onFavoritesChanged = { milkPresetFavorites = it }
            )
        }
    }
    }
}

@Composable
internal fun TechDetailsDialog(
    track: Track,
    onDismiss: () -> Unit,
    playCount: Int = 0,
    firstPlayedAt: Long? = null,
    lastPlayedAt: Long = 0L
) {
    var showPlayHistory by remember(track.id) { mutableStateOf(false) }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (showPlayHistory) "Histórico de reprodução" else "Informações técnicas") },
        text = {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { showPlayHistory = false }) {
                        Text("Técnico", color = if (!showPlayHistory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { showPlayHistory = true }) {
                        Text("Reproduções", color = if (showPlayHistory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (showPlayHistory) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        TechRow("Título", track.title)
                        TechRow("Artista", track.artist)
                        TechRow("Primeira vez tocada", firstPlayedAt?.takeIf { it > 0L }?.let {
                            java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it))
                        } ?: "—")
                        TechRow("Última reprodução", lastPlayedAt.takeIf { it > 0L }?.let {
                            java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it))
                        } ?: "—")
                        TechRow("Vezes reproduzida", if (playCount > 0) playCount.toString() else "—")
                    }
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        TechRow("Título", track.title)
                        TechRow("Artista", track.artist)
                        TechRow("Álbum", track.album)
                        TechRow("Gênero", track.genre)
                        track.year?.let { TechRow("Ano", it.toString()) }
                        TechRow("Codec", track.codec ?: "—")
                        TechRow("Bitrate", if (track.bitrate > 0) "${Math.round(track.bitrate / 1000.0)} kbps" else "—")
                        TechRow("Amostragem", formatSampleRate(track.sampleRate))
                        TechRow("Bit depth", if (track.bitDepth > 0) "${track.bitDepth}-bit" else "—")
                        TechRow("Canais", formatChannels(track.channels))
                        TechRow("Duração", formatDuration(track.duration))
                        TechRow("Tamanho", if (track.fileSize > 0) {
                            val mb = track.fileSize / (1024.0 * 1024.0)
                            if (mb >= 1.0) "%.2f MB".format(mb) else "%.0f KB".format(track.fileSize / 1024.0)
                        } else "—")
                        TechRow("Nº da faixa", track.trackNumber?.toString() ?: "—")
                        TechRow("Nº do disco", track.discNumber?.toString() ?: "—")
                        TechRow("Compositores", track.composer.ifBlank { "—" })
                        TechRow("Arquivo", track.path)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fechar") }
        }
    )
}

@Composable
private fun TechRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, modifier = Modifier.width(100.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

/** Botão de favoritar: coração oco quando não favorita, cheio (vermelho) quando favorita. */
@Composable
private fun FavoriteHeart(
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    overArt: Boolean = false // true = fundo escuro translúcido para contrastar com a capa
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .then(if (overArt) Modifier.background(Color.Black.copy(alpha = 0.35f)) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(targetState = isFavorite, animationSpec = tween(180), label = "favoriteHeart") { fav ->
            Icon(
                imageVector = if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (fav) "Remover dos favoritos" else "Favoritar",
                tint = if (fav) Color(0xFFE53935) else Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
internal fun HdBadge(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.hi_res_audio),
        contentDescription = "Hi-Res Audio",
        modifier = modifier
            .size(width = 52.dp, height = 50.dp)
            .shadow(4.dp, RoundedCornerShape(6.dp)),
        contentScale = ContentScale.Fit
    )
}

// Medidas do gesto "deslizar para cima" da seekbar (coordenadas locais; y negativo = acima da barra).
private val SeekJumpArmDistance = 28.dp
private val SeekJumpBtnW = 132.dp
private val SeekJumpBtnH = 44.dp
private val SeekJumpBtnGap = 10.dp
private val SeekJumpBtnCenterY = (-84).dp
private val SeekBubbleW = 84.dp
private val SeekBubbleH = 34.dp

/** m:ss, ou h:mm:ss a partir de 1 hora. */
private fun formatExactTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val sec = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

@Composable
private fun SeekTimeBubble(text: String, accent: Color, armed: Boolean) {
    Box(
        Modifier
            .size(SeekBubbleW, SeekBubbleH)
            .clip(RoundedCornerShape(17.dp))
            .background(Color.Black.copy(alpha = 0.80f))
            .border(1.dp, accent.copy(alpha = if (armed) 0.95f else 0.55f), RoundedCornerShape(17.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun SeekJumpButtons(hover: Int, accent: Color) {
    Row(
        Modifier.width(SeekJumpBtnW * 2 + SeekJumpBtnGap),
        horizontalArrangement = Arrangement.spacedBy(SeekJumpBtnGap)
    ) {
        SeekJumpPill("Ir para…", hover == 1, accent)
        SeekJumpPill("Cancelar", hover == 2, accent)
    }
}

@Composable
private fun SeekJumpPill(label: String, selected: Boolean, accent: Color) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.07f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "jumpPillScale"
    )
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier
            .size(SeekJumpBtnW, SeekJumpBtnH)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(if (selected) accent else Color.Black.copy(alpha = 0.80f))
            .border(1.dp, if (selected) accent else Color.White.copy(alpha = 0.28f), shape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) Color.Black else Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/** Diálogo "Vamos para": o usuário digita hh:mm:ss exatos. */
@Composable
private fun ExactJumpDialog(
    durationMs: Long,
    initialMs: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val maxSec = (durationMs / 1000).coerceAtLeast(0)
    val hasHours = maxSec >= 3600
    val init = (initialMs / 1000).coerceIn(0, maxSec)
    var hh by remember { mutableStateOf((init / 3600).toString()) }
    var mm by remember { mutableStateOf(((init % 3600) / 60).toString()) }
    var ss by remember { mutableStateOf((init % 60).toString()) }
    val h = hh.toLongOrNull() ?: 0L
    val m = mm.toLongOrNull() ?: 0L
    val sec = ss.toLongOrNull() ?: 0L
    val target = h * 3600 + m * 60 + sec
    val valid = sec < 60 && (!hasHours || m < 60) && target <= maxSec
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    @Composable
    fun field(value: String, label: String, enabled: Boolean, mod: Modifier, onChange: (String) -> Unit) {
        OutlinedTextField(
            value = value,
            onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(2)) },
            label = { Text(label, fontSize = 10.sp) },
            enabled = enabled,
            singleLine = true,
            isError = !valid && enabled,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 20.sp, textAlign = TextAlign.Center),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                imeAction = androidx.compose.ui.text.input.ImeAction.Done
            ),
            modifier = mod.width(72.dp)
        )
    }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Vamos para") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    field(hh, "hh", hasHours, Modifier) { hh = it }
                    Text(" : ", fontSize = 20.sp)
                    field(mm, "mm", true, Modifier.focusRequester(focus)) { mm = it }
                    Text(" : ", fontSize = 20.sp)
                    field(ss, "ss", true, Modifier) { ss = it }
                }
                if (!valid) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tempo inválido (máximo ${formatExactTime(durationMs)})",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        confirmButton = {
            TextButton(onClick = { onConfirm(target * 1000L) }, enabled = valid) { Text("OK") }
        }
    )
}

/** Estilos da barra de progresso (seekbar) do Now Playing. */
enum class SeekBarStyle {
    Normal,    // barra fina, thumb discreto
    Standard,  // padrão atual (gradiente + thumb circular)
    Wavy,      // onda senoidal no progresso
    Thick,     // trilha mais grossa, thumb maior
    Slim,      // trilha bem fina
    Liquid,    // bolha/elipse no ponto de progresso (efeito “líquido”)
    WavyLiquid, // onda que corre (Wavy) + thumb de vidro líquido (Liquid)
    Waveform,  // forma de onda real da música; o desenho é escolhido em Configurações
    Glass      // trilho escuro de vidro fumê, progresso champagne e cursor branco
}

/**
 * Reduz os picos da faixa a [cols] colunas (mistura de pico e média, como no editor de trecho).
 * Sem picos (carregando ou falha) devolve uma linha baixa e uniforme.
 */
private fun buildSeekAmps(peaks: FloatArray?, cols: Int): FloatArray {
    val out = FloatArray(cols)
    val n = peaks?.size ?: 0
    if (peaks == null || n == 0) {
        java.util.Arrays.fill(out, 0.12f)
        return out
    }
    for (i in 0 until cols) {
        val b0 = ((i.toLong() * n) / cols).toInt().coerceIn(0, n - 1)
        val b1 = ((((i + 1).toLong() * n) / cols).toInt() - 1).coerceIn(b0, n - 1)
        var mx = 0f
        var sum = 0f
        for (b in b0..b1) {
            val v = peaks[b]
            if (v > mx) mx = v
            sum += v
        }
        out[i] = max(0.4f * mx + 0.6f * (sum / (b1 - b0 + 1)), 0.04f)
    }
    return out
}

@Composable
private fun SeekBar(
    fraction: Float,
    accentColor: Color = Color.White,
    style: SeekBarStyle = SeekBarStyle.Standard,
    isPlaying: Boolean = true,
    peaks: FloatArray? = null,
    peaksFailed: Boolean = false,
    waveformTheme: WaveformTheme = WaveformTheme.BARS,
    durationMs: Long = 0L,
    onRequestExactJump: () -> Unit = {},
    onDrag: (Float) -> Unit,
    onDragEnd: (Float) -> Unit,
    onDragCancel: () -> Unit
) {
    var barWidth by remember { mutableFloatStateOf(1f) }
    var lastDragFraction by remember { mutableFloatStateOf(fraction) }
    var dragging by remember { mutableStateOf(false) }
    // Deslizar para cima durante o arrasto: mostra "Ir para…" / "Cancelar".
    var jumpArmed by remember { mutableStateOf(false) }
    var jumpHover by remember { mutableIntStateOf(0) } // 0 nenhum · 1 Ir para · 2 Cancelar
    val haptics = LocalHapticFeedback.current
    // O pointerInput(Unit) nunca reinicia: sempre chama a versão mais recente dos callbacks.
    val curOnDrag by rememberUpdatedState(onDrag)
    val curOnDragEnd by rememberUpdatedState(onDragEnd)
    val curOnDragCancel by rememberUpdatedState(onDragCancel)
    val curOnJump by rememberUpdatedState(onRequestExactJump)
    val density = LocalDensity.current.density
    val trackHeight = when (style) {
        SeekBarStyle.Thick -> 10.dp
        SeekBarStyle.Slim, SeekBarStyle.Normal -> 3.dp
        SeekBarStyle.Wavy -> 8.dp
        SeekBarStyle.WavyLiquid -> 8.dp
        SeekBarStyle.Waveform -> 38.dp
        SeekBarStyle.Glass -> 8.dp
        SeekBarStyle.Liquid -> 6.dp
        SeekBarStyle.Standard -> 5.dp
    }
    val thumbSize = when (style) {
        SeekBarStyle.Thick -> 18.dp
        SeekBarStyle.Slim, SeekBarStyle.Normal -> 10.dp
        SeekBarStyle.Liquid -> 16.dp
        SeekBarStyle.WavyLiquid -> 16.dp
        SeekBarStyle.Waveform -> 14.dp
        SeekBarStyle.Glass -> 13.dp
        SeekBarStyle.Wavy -> 12.dp
        SeekBarStyle.Standard -> 14.dp
    }
    val boxHeight = when (style) {
        SeekBarStyle.Thick, SeekBarStyle.Liquid, SeekBarStyle.Wavy, SeekBarStyle.WavyLiquid, SeekBarStyle.Glass -> 32.dp
        SeekBarStyle.Waveform -> 48.dp
        else -> 28.dp
    }

    // ── Animações ────────────────────────────────────────────────────────────
    // “Toque”: mola com leve overshoot → o thumb Liquid estica/encolhe como gota.
    val press by animateFloatAsState(
        targetValue = if (dragging) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "seekPress"
    )
    // Wavy: a onda achata ao arrastar (ou pausar) e volta a crescer ao soltar.
    val waveAmp by animateFloatAsState(
        targetValue = if (dragging || !isPlaying) 0f else 1f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "waveAmp"
    )
    // Wavy: a fase da onda corre continuamente (a onda “flui” para a direita).
    val wavePhase: androidx.compose.runtime.State<Float>? =
        if (style == SeekBarStyle.Wavy || style == SeekBarStyle.WavyLiquid) {
            rememberInfiniteTransition(label = "wave").animateFloat(
                initialValue = 0f,
                targetValue = (2.0 * Math.PI).toFloat(),
                animationSpec = infiniteRepeatable(tween(durationMillis = 1400, easing = LinearEasing)),
                label = "wavePhase"
            )
        } else null

    // Waveform: amplitudes por coluna, recalculadas só quando os picos ou a largura mudam
    // (nada é refeito a cada quadro de reprodução).
    val isWaveform = style == SeekBarStyle.Waveform
    val waveColPx = with(LocalDensity.current) { 3.dp.toPx() }
    val waveCols = max(8, (barWidth / waveColPx).toInt())
    val waveAmps = remember(peaks, waveCols, isWaveform) {
        if (isWaveform) buildSeekAmps(peaks, waveCols) else FloatArray(0)
    }
    // Enquanto os picos carregam, a parte "ainda não tocada" respira suavemente.
    val loadPulse: androidx.compose.runtime.State<Float>? =
        if (isWaveform && peaks == null && !peaksFailed) {
            rememberInfiniteTransition(label = "seekLoad").animateFloat(
                initialValue = 0.55f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(durationMillis = 900, easing = FastOutSlowInEasing),
                    androidx.compose.animation.core.RepeatMode.Reverse
                ),
                label = "seekLoadPulse"
            )
        } else null

    Box(
        Modifier
            .fillMaxWidth()
            .height(boxHeight)
            .onSizeChanged { barWidth = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    barWidth = w
                    val f = (o.x / w).coerceIn(0f, 1f)
                    lastDragFraction = f
                    onDrag(f)
                    onDragEnd(f)
                }
            }
            .pointerInput(Unit) {
                val armY = -SeekJumpArmDistance.toPx()
                val disarmY = armY * 0.45f
                val btnW = SeekJumpBtnW.toPx()
                val btnH = SeekJumpBtnH.toPx()
                val gap = SeekJumpBtnGap.toPx()
                val cy = SeekJumpBtnCenterY.toPx()
                val tol = 14.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val slop = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                        change.consume()
                    } ?: return@awaitEachGesture
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    barWidth = w
                    lastDragFraction = (down.position.x / w).coerceIn(0f, 1f)
                    dragging = true
                    curOnDrag(lastDragFraction)

                    var armed = false
                    var hover = 0
                    val left = androidx.compose.ui.geometry.Rect(
                        w / 2f - gap / 2f - btnW, cy - btnH / 2f - tol, w / 2f - gap / 2f, cy + btnH / 2f + tol
                    )
                    val right = androidx.compose.ui.geometry.Rect(
                        w / 2f + gap / 2f, cy - btnH / 2f - tol, w / 2f + gap / 2f + btnW, cy + btnH / 2f + tol
                    )
                    val completed = drag(slop.id) { change ->
                        change.consume()
                        val pos = change.position
                        if (!armed && pos.y < armY) {
                            armed = true
                            jumpArmed = true
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        } else if (armed && pos.y > disarmY) {
                            armed = false
                            hover = 0
                            jumpArmed = false
                            jumpHover = 0
                        }
                        if (armed) {
                            // Valor congelado: só escolhe entre os botões
                            val h = when {
                                left.contains(pos) -> 1
                                right.contains(pos) -> 2
                                else -> 0
                            }
                            if (h != hover) {
                                hover = h
                                jumpHover = h
                                if (h != 0) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        } else {
                            val f = (pos.x / w).coerceIn(0f, 1f)
                            lastDragFraction = f
                            curOnDrag(f)
                        }
                    }
                    val wasArmed = armed
                    val finalHover = hover
                    dragging = false
                    jumpArmed = false
                    jumpHover = 0
                    when {
                        !completed -> curOnDragCancel()
                        wasArmed -> {
                            // Soltou com os botões visíveis: nada é movido; só "Ir para" abre o diálogo.
                            curOnDragCancel()
                            if (finalHover == 1) curOnJump()
                        }
                        else -> curOnDragEnd(lastDragFraction)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (dragging && durationMs > 1L) {
            val dens = LocalDensity.current
            val bubbleW = with(dens) { SeekBubbleW.roundToPx() }
            val thumbX = lastDragFraction.coerceIn(0f, 1f) * barWidth
            val bx = (thumbX - bubbleW / 2f).coerceIn(0f, (barWidth - bubbleW).coerceAtLeast(0f)).toInt()
            val by = -with(dens) { (SeekBubbleH + 8.dp).roundToPx() }
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(bx, by),
                properties = PopupProperties(focusable = false, clippingEnabled = false)
            ) {
                SeekTimeBubble(
                    text = formatExactTime((lastDragFraction.coerceIn(0f, 1f) * durationMs).toLong()),
                    accent = accentColor,
                    armed = jumpArmed
                )
            }
            if (jumpArmed) {
                val rowW = with(dens) { (SeekJumpBtnW * 2 + SeekJumpBtnGap).roundToPx() }
                val rx = ((barWidth - rowW) / 2f).toInt()
                val ry = with(dens) { (SeekJumpBtnCenterY - SeekJumpBtnH / 2).roundToPx() }
                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(rx, ry),
                    properties = PopupProperties(focusable = false, clippingEnabled = false)
                ) {
                    SeekJumpButtons(hover = jumpHover, accent = accentColor)
                }
            }
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .graphicsLayer { if (isWaveform) scaleY = 1f + 0.12f * press.coerceIn(0f, 1f) }
        ) {
            if (size.width > 1f) barWidth = size.width
            val h = size.height
            val frac = fraction.coerceIn(0f, 1f)
            val progW = size.width * frac

            when (style) {
                SeekBarStyle.Glass -> {
                    val border = 1.dp.toPx()
                    val innerHeight = (h - border * 2f).coerceAtLeast(1f)
                    val radius = CornerRadius(h / 2f, h / 2f)
                    val innerRadius = CornerRadius(innerHeight / 2f, innerHeight / 2f)
                    drawRoundRect(
                        color = Color(0xFF101114).copy(alpha = 0.82f),
                        cornerRadius = radius
                    )
                    if (progW > border) {
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color(0xFFB9A77A), Color(0xFFF2E8C8))
                            ),
                            topLeft = androidx.compose.ui.geometry.Offset(border, border),
                            size = Size((progW - border).coerceAtMost(size.width - border), innerHeight),
                            cornerRadius = innerRadius
                        )
                    }
                    drawLine(
                        color = Color.White.copy(alpha = 0.20f),
                        start = androidx.compose.ui.geometry.Offset(border * 2f, border),
                        end = androidx.compose.ui.geometry.Offset(size.width - border * 2f, border),
                        strokeWidth = 0.7.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.22f),
                        cornerRadius = radius,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = border)
                    )
                }
                SeekBarStyle.Wavy -> {
                    val strokeW = 3.dp.toPx()
                    val amp = 2.5.dp.toPx() * waveAmp
                    val wavelength = 16.dp.toPx()
                    val k = (2.0 * Math.PI).toFloat() / wavelength
                    val phase = wavePhase?.value ?: 0f
                    val mid = h / 2f

                    // Trilha restante: linha reta e fina (como no vídeo)
                    drawLine(
                        color = Color.White.copy(0.22f),
                        start = androidx.compose.ui.geometry.Offset(progW, mid),
                        end = androidx.compose.ui.geometry.Offset(size.width, mid),
                        strokeWidth = strokeW,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    // Progresso: onda que corre
                    if (progW > 1f) {
                        val path = androidx.compose.ui.graphics.Path()
                        path.moveTo(0f, mid + amp * kotlin.math.sin(-phase))
                        var x = 3f
                        while (x < progW) {
                            path.lineTo(x, mid + amp * kotlin.math.sin(k * x - phase))
                            x += 3f
                        }
                        path.lineTo(progW, mid + amp * kotlin.math.sin(k * progW - phase))
                        drawPath(
                            path = path,
                            color = accentColor,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = strokeW,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                join = androidx.compose.ui.graphics.StrokeJoin.Round
                            )
                        )
                    }
                }
                SeekBarStyle.Liquid -> {
                    val r = CornerRadius(h / 2f, h / 2f)
                    val restW = thumbSize.toPx() * 1.75f
                    val cx = restW / 2f + frac * (size.width - restW)
                    drawRoundRect(Color.White.copy(0.18f), cornerRadius = r)
                    if (cx > 0f) {
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                listOf(accentColor.copy(0.55f), accentColor)
                            ),
                            size = Size(cx, h),
                            cornerRadius = r
                        )
                    }
                }
                SeekBarStyle.WavyLiquid -> {
                    val strokeW = 3.dp.toPx()
                    val amp = 2.6.dp.toPx() * waveAmp
                    val k = (2.0 * Math.PI).toFloat() / 16.dp.toPx()
                    val phase = wavePhase?.value ?: 0f
                    val mid = h / 2f
                    // mesma geometria do Liquid: o ponto de progresso é o centro da pílula
                    val restW = thumbSize.toPx() * 1.75f
                    val cx = restW / 2f + frac * (size.width - restW)
                    val taperLen = 18.dp.toPx()
                    // trilha restante: linha reta, fina e arredondada, saindo de baixo do thumb
                    drawLine(
                        color = Color.White.copy(0.20f),
                        start = androidx.compose.ui.geometry.Offset(cx, mid),
                        end = androidx.compose.ui.geometry.Offset(size.width, mid),
                        strokeWidth = strokeW,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    if (cx > 1f) {
                        val path = androidx.compose.ui.graphics.Path()
                        var x = 0f
                        var first = true
                        while (x < cx) {
                            // a onda "assenta" suavemente ao chegar no thumb, como líquido escorrendo para a gota
                            val t = ((cx - x) / taperLen).coerceIn(0f, 1f)
                            val settle = t * t * (3f - 2f * t)
                            val y = mid + amp * settle * sin(k * x - phase)
                            if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
                            x += 3f
                        }
                        path.lineTo(cx, mid)
                        val round = androidx.compose.ui.graphics.StrokeCap.Round
                        // brilho suave por baixo
                        drawPath(
                            path = path,
                            color = accentColor.copy(alpha = 0.16f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeW * 2.8f, cap = round)
                        )
                        // linha principal, em degradê como o progresso do Liquid
                        drawPath(
                            path = path,
                            brush = Brush.horizontalGradient(
                                colors = listOf(accentColor.copy(0.55f), accentColor),
                                startX = 0f,
                                endX = max(cx, 1f)
                            ),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = strokeW,
                                cap = round,
                                join = androidx.compose.ui.graphics.StrokeJoin.Round
                            )
                        )
                    }
                }
                SeekBarStyle.Waveform -> {
                    val barW = 2.dp.toPx()
                    val cols = waveAmps.size.coerceAtLeast(1)
                    // distribui as colunas por toda a largura: o fim da waveform coincide com o fim da barra
                    val gapPx = (size.width / cols - barW).coerceAtLeast(0.5f)
                    val pulse = loadPulse?.value ?: 1f
                    drawWaveAmps(
                        theme = waveformTheme,
                        amps = waveAmps,
                        selL = 0f,
                        selR = progW,
                        accent = accentColor,
                        offColor = Color.White.copy(alpha = 0.28f * pulse),
                        barW = barW,
                        gap = gapPx,
                        minH = 2.dp.toPx()
                    )
                }
                else -> {
                    val r = CornerRadius(h / 2f, h / 2f)
                    drawRoundRect(Color.White.copy(0.22f), cornerRadius = r)
                    if (progW > 0f) {
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                listOf(accentColor.copy(alpha = 0.7f), accentColor)
                            ),
                            size = Size(progW, h),
                            cornerRadius = r
                        )
                    }
                }
            }
        }

        when (style) {
            SeekBarStyle.Glass -> {
                Canvas(Modifier.fillMaxSize()) {
                    val thumbW = 13.dp.toPx()
                    val thumbH = 10.dp.toPx()
                    val cx = (fraction.coerceIn(0f, 1f) * size.width)
                        .coerceIn(thumbW / 2f, size.width - thumbW / 2f)
                    val cy = size.height / 2f
                    val top = cy - thumbH / 2f
                    val radius = CornerRadius(thumbH / 2f, thumbH / 2f)
                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.56f),
                        topLeft = androidx.compose.ui.geometry.Offset(cx - thumbW / 2f - 1.dp.toPx(), top - 1.dp.toPx()),
                        size = Size(thumbW + 2.dp.toPx(), thumbH + 2.dp.toPx()),
                        cornerRadius = radius
                    )
                    drawRoundRect(
                        color = Color(0xFFF5F5F2),
                        topLeft = androidx.compose.ui.geometry.Offset(cx - thumbW / 2f, top),
                        size = Size(thumbW, thumbH),
                        cornerRadius = radius
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.72f),
                        start = androidx.compose.ui.geometry.Offset(cx - thumbW * 0.28f, top + 1.5.dp.toPx()),
                        end = androidx.compose.ui.geometry.Offset(cx + thumbW * 0.28f, top + 1.5.dp.toPx()),
                        strokeWidth = 0.8.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
            }
            SeekBarStyle.Liquid, SeekBarStyle.WavyLiquid -> {
                // Thumb: pílula branca em repouso → “lente de vidro” maior ao arrastar.
                Canvas(Modifier.fillMaxSize()) {
                    val frac = fraction.coerceIn(0f, 1f)
                    val restH = thumbSize.toPx()
                    val restW = restH * 1.75f
                    val p = press
                    val w = (restW + (restW * 2.6f - restW) * p).coerceAtLeast(restH)
                    val hh = (restH + (restH * 1.7f - restH) * p).coerceAtLeast(restH * 0.6f)
                    val bw = size.width
                    val cxRest = restW / 2f + frac * (bw - restW)
                    val cx = cxRest.coerceIn(w / 2f, bw - w / 2f)
                    val cy = size.height / 2f
                    val topLeft = androidx.compose.ui.geometry.Offset(cx - w / 2f, cy - hh / 2f)
                    val sz = Size(w, hh)
                    val cr = CornerRadius(hh / 2f, hh / 2f)
                    val glass = p.coerceIn(0f, 1f)

                    // Preenchimento: branco sólido → quase transparente
                    drawRoundRect(
                        color = Color.White.copy(alpha = 1f - 0.88f * glass),
                        topLeft = topLeft, size = sz, cornerRadius = cr
                    )
                    if (glass > 0.01f) {
                        // Borda com franja laranja/azul (refração)
                        drawRoundRect(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFFFFB27A), Color(0xFF7FA6FF)),
                                start = topLeft,
                                end = androidx.compose.ui.geometry.Offset(topLeft.x + w, topLeft.y + hh)
                            ),
                            topLeft = topLeft, size = sz, cornerRadius = cr,
                            alpha = 0.75f * glass,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(1.2.dp.toPx())
                        )
                        // Brilho interno discreto
                        val inset = 2.dp.toPx()
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.28f * glass),
                            topLeft = androidx.compose.ui.geometry.Offset(topLeft.x + inset, topLeft.y + inset),
                            size = Size((w - inset * 2f).coerceAtLeast(1f), (hh - inset * 2f).coerceAtLeast(1f)),
                            cornerRadius = CornerRadius((hh - inset * 2f) / 2f, (hh - inset * 2f) / 2f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(0.8.dp.toPx())
                        )
                    }
                }
            }
            SeekBarStyle.Waveform -> {
                // Cursor: pílula branca fina com halo; cresce um pouco ao arrastar.
                Canvas(Modifier.fillMaxSize()) {
                    val frac = fraction.coerceIn(0f, 1f)
                    val p = press.coerceIn(0f, 1f)
                    val w = 3.dp.toPx() + 1.8.dp.toPx() * p
                    val hh = size.height * (0.88f + 0.12f * p)
                    val cx = (frac * size.width).coerceIn(w / 2f, size.width - w / 2f)
                    val cy = size.height / 2f
                    drawRoundRect(
                        color = accentColor.copy(alpha = 0.22f + 0.10f * p),
                        topLeft = androidx.compose.ui.geometry.Offset(cx - w * 1.8f, cy - hh / 2f),
                        size = Size(w * 3.6f, hh),
                        cornerRadius = CornerRadius(w * 1.8f, w * 1.8f)
                    )
                    drawRoundRect(
                        color = Color.White,
                        topLeft = androidx.compose.ui.geometry.Offset(cx - w / 2f, cy - hh / 2f),
                        size = Size(w, hh),
                        cornerRadius = CornerRadius(w / 2f, w / 2f)
                    )
                }
            }
            SeekBarStyle.Wavy -> {
                // Thumb em barra vertical; levemente maior ao arrastar.
                val thumbW = 4.dp
                val thumbH = 22.dp
                Box(
                    Modifier
                        // Box pai centraliza o thumb; deslocamos do centro até o ponto de progresso
                        .offset {
                            androidx.compose.ui.unit.IntOffset(
                                ((fraction.coerceIn(0f, 1f) - 0.5f) * barWidth).toInt(),
                                0
                            )
                        }
                        .graphicsLayer {
                            scaleY = 1f + 0.18f * press.coerceIn(0f, 1f)
                        }
                        .size(width = thumbW, height = thumbH)
                        .clip(CircleShape)
                        .background(accentColor)
                )
            }
            else -> {
                val thumbPx = with(LocalDensity.current) { thumbSize.toPx() }
                Box(
                    Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(
                        Modifier
                            .offset(
                                x = ((fraction.coerceIn(0f, 1f) * (barWidth - thumbPx)) / density).dp
                            )
                            .size(thumbSize)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QueueSheet(
    queue: List<Track>,
    currentId: String?,
    appColors: AppColors = DefaultAppColors,
    onDismiss: () -> Unit,
    onSkipTo: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit = {},
    onToggleFavorite: (Track) -> Unit = {},
    onDelete: (Track) -> Unit = {},
    onPlayNext: (Track) -> Unit = {},
    isFavorite: (String) -> Boolean = { false },
    pinnedIds: Set<String> = emptySet(),
    onTogglePin: (Track) -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val order = remember { mutableStateListOf<Track>().apply { addAll(queue) } }
    LaunchedEffect(queue) {
        if (order.toList() != queue) {
            order.clear()
            order.addAll(queue)
        }
    }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var dropTargetId by remember { mutableStateOf<String?>(null) }   // linha sobre a qual o item vai ficar
    var dropAfter by remember { mutableStateOf(false) }              // true = indicador abaixo da linha alvo
    var menuForIndex by remember { mutableStateOf<Int?>(null) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val selectionMode = selectedIds.isNotEmpty()
    var showSelectionMenu by remember { mutableStateOf(false) }

    val currentTrack = order.firstOrNull { it.id == currentId } ?: order.firstOrNull()
    var queueSearch by remember { mutableStateOf("") }
    val filtered = remember(order, queueSearch) {
        if (queueSearch.isBlank()) order
        else order.filter {
            it.title.contains(queueSearch, true) || it.artist.contains(queueSearch, true)
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        dragHandle = null
    ) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
            // Fundo liquid glass: capa borrada
            if (currentTrack?.artPath != null) {
                AsyncImage(
                    model = currentTrack.artPath,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(40.dp)
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(appColors.gradientBottom.copy(alpha = 0.80f))
            )
            Column(Modifier.fillMaxSize()) {
        // Header: contagem + ações de seleção + fechar
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (selectionMode) "${selectedIds.size} selecionada(s)"
                else "${order.indexOfFirst { it.id == currentId }.coerceAtLeast(0) + 1} / ${order.size}",
                color = Color.White.copy(0.7f),
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            if (selectionMode) {
                TextButton(onClick = { showSelectionMenu = true }) {
                    Text("Ações", color = appColors.accent, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = { selectedIds = emptySet() }) {
                    Text("Limpar", color = Color.White.copy(0.75f))
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, contentDescription = "Fechar", tint = Color.White)
            }
        }
        // Busca na fila
        OutlinedTextField(
            value = queueSearch,
            onValueChange = { queueSearch = it },
            placeholder = { Text("Pesquise nesta fila...", color = Color.White.copy(0.4f)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color.White.copy(0.3f),
                unfocusedBorderColor = Color.White.copy(0.15f),
                cursorColor = appColors.accent
            )
        )
        val listState = rememberLazyListState()
        val dragScope = rememberCoroutineScope()
        val autoScrollJob = remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

        /** Recalcula a posição de destino usando as alturas/offsets reais das linhas visíveis. */
        fun updateDropTarget() {
            val id = draggingId ?: return
            val infos = listState.layoutInfo.visibleItemsInfo
            val me = infos.firstOrNull { it.key == id } ?: return
            val center = me.offset + me.size / 2f + dragOffsetY
            val target = infos.firstOrNull { center >= it.offset && center < it.offset + it.size }
                ?: if (center < (infos.firstOrNull()?.offset ?: 0)) infos.firstOrNull() else infos.lastOrNull()
            if (target == null) return
            dropTargetId = target.key as? String
            // alvo abaixo da origem => item fica depois dele (indicador embaixo); acima => antes (indicador em cima)
            dropAfter = target.index > me.index
        }

        fun stopDrag() {
            autoScrollJob.value?.cancel()
            autoScrollJob.value = null
            draggingId = null
            dropTargetId = null
            dragOffsetY = 0f
        }

        fun finishDrag() {
            val id = draggingId
            val targetId = dropTargetId
            val after = dropAfter
            if (id != null && targetId != null && targetId != id) {
                val from = order.indexOfFirst { it.id == id }
                val targetIdx = order.indexOfFirst { it.id == targetId }
                if (from >= 0 && targetIdx >= 0) {
                    val to = if (after) {
                        if (targetIdx >= from) targetIdx else targetIdx + 1
                    } else {
                        if (targetIdx > from) targetIdx - 1 else targetIdx
                    }.coerceIn(0, order.lastIndex)
                    if (to != from) {
                        val item = order.removeAt(from)
                        order.add(to, item)
                        onMove(from, to)
                    }
                }
            }
            stopDrag()
        }

        /** Rolagem automática quando o item arrastado chega perto das bordas da lista. */
        fun startAutoScroll() {
            autoScrollJob.value?.cancel()
            autoScrollJob.value = dragScope.launch {
                while (draggingId != null) {
                    val id = draggingId
                    val info = listState.layoutInfo
                    val me = info.visibleItemsInfo.firstOrNull { it.key == id }
                    if (me != null) {
                        val top = me.offset + dragOffsetY
                        val bottom = top + me.size
                        val edge = me.size * 0.9f
                        val viewportEnd = info.viewportEndOffset - info.afterContentPadding
                        val delta = when {
                            top < info.viewportStartOffset + edge -> -18f
                            bottom > viewportEnd - edge -> 18f
                            else -> 0f
                        }
                        if (delta != 0f) {
                            val consumed = listState.scrollBy(delta)
                            // mantém o item sob o dedo enquanto a lista rola
                            dragOffsetY += consumed
                            updateDropTarget()
                        }
                    }
                    kotlinx.coroutines.delay(16)
                }
            }
        }

        LaunchedEffect(currentId, order.size, queueSearch) {
            if (queueSearch.isNotBlank()) return@LaunchedEffect
            val idx = order.indexOfFirst { it.id == currentId }
            if (idx >= 0) {
                listState.scrollToItem(idx.coerceAtMost((order.size - 1).coerceAtLeast(0)))
            }
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 32.dp),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            itemsIndexed(filtered, key = { _, t -> t.id }) { index, track ->
                val realIndex = order.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                val active = track.id == currentId
                val isDragging = draggingId == track.id
                val showLineAbove = dropTargetId == track.id && !dropAfter && !isDragging
                val showLineBelow = dropTargetId == track.id && dropAfter && !isDragging
                val selected = track.id in selectedIds
                Box {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                if (isDragging) {
                                    translationY = dragOffsetY
                                    alpha = 0.92f
                                    scaleX = 1.03f
                                    scaleY = 1.03f
                                    shadowElevation = 16f
                                }
                            }
                            .zIndex(if (isDragging) 1f else 0f)
                            .then(
                                when {
                                    selected -> Modifier
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                        .background(Color.White.copy(0.14f), RoundedCornerShape(12.dp))
                                    active -> Modifier
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                        .border(1.5.dp, Color(0xFF4ADE80).copy(0.85f), RoundedCornerShape(12.dp))
                                        .background(Color(0xFF4ADE80).copy(0.12f), RoundedCornerShape(12.dp))
                                    else -> Modifier
                                }
                            )
                            .pointerInput(realIndex, selectionMode, track.id) {
                                detectTapGestures(
                                    onTap = {
                                        if (selectionMode) {
                                            selectedIds = if (track.id in selectedIds)
                                                selectedIds - track.id else selectedIds + track.id
                                        } else {
                                            onSkipTo(realIndex)
                                        }
                                    },
                                    onLongPress = {
                                        if (selectionMode) {
                                            selectedIds = if (track.id in selectedIds)
                                                selectedIds - track.id else selectedIds + track.id
                                        } else {
                                            // inicia seleção + menu individual
                                            selectedIds = setOf(track.id)
                                            menuForIndex = realIndex
                                        }
                                    }
                                )
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectionMode) {
                            Box(
                                Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(if (selected) appColors.accent else Color.White.copy(0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    Text("✓", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                        }
                        ArtImage(path = track.artPath, size = 44.dp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(
                                track.title,
                                fontSize = 15.sp,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (active) appColors.accent else Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                track.artist,
                                fontSize = 12.sp,
                                color = Color.White.copy(0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (track.id in pinnedIds) {
                            Icon(
                                Icons.Rounded.PushPin,
                                contentDescription = "Fixada",
                                tint = appColors.accent,
                                modifier = Modifier.padding(end = 6.dp).size(18.dp)
                            )
                        }
                        if (!selectionMode) {
                            Icon(
                                Icons.Rounded.DragHandle,
                                contentDescription = "Arrastar",
                                tint = Color.White.copy(0.45f),
                                modifier = Modifier
                                    .size(28.dp)
                                    .pointerInput(track.id) {
                                        detectDragGestures(
                                            onDragStart = {
                                                draggingId = track.id
                                                dragOffsetY = 0f
                                                dropTargetId = null
                                                updateDropTarget()
                                                startAutoScroll()
                                            },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                dragOffsetY += amount.y
                                                updateDropTarget()
                                            },
                                            onDragEnd = { finishDrag() },
                                            onDragCancel = { stopDrag() }
                                        )
                                    }
                            )
                        }
                    }
                    // Indicador de onde a música vai ficar ao soltar
                    if (showLineAbove) {
                        Box(
                            Modifier
                                .align(Alignment.TopCenter)
                                .padding(horizontal = 12.dp)
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(appColors.accent)
                        )
                    }
                    if (showLineBelow) {
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 12.dp)
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(appColors.accent)
                        )
                    }
                    AppDropdownMenu(
                        expanded = menuForIndex == realIndex && !showSelectionMenu,
                        onDismissRequest = { menuForIndex = null },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.appMenuOutline(RoundedCornerShape(16.dp))
                    ) {
                        val fav = isFavorite(track.id)
                        LensMenuItem(
                            text = { Text("Reproduzir a seguir", color = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                menuForIndex = null
                                selectedIds = emptySet()
                                onPlayNext(track)
                            }
                        )
                        LensMenuItem(
                            text = { Text("Remover da fila", color = MaterialTheme.colorScheme.onSurface) },
                            leadingIcon = {
                                Icon(Icons.Rounded.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                            },
                            onClick = {
                                menuForIndex = null
                                selectedIds = emptySet()
                                onRemoveFromQueue(realIndex)
                            }
                        )
                        val pinned = track.id in pinnedIds
                        LensMenuItem(
                            text = { Text(if (pinned) "Desafixar" else "Fixar", color = MaterialTheme.colorScheme.onSurface) },
                            leadingIcon = {
                                Icon(Icons.Rounded.PushPin, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                            },
                            onClick = {
                                menuForIndex = null
                                selectedIds = emptySet()
                                onTogglePin(track)
                            }
                        )
                        LensMenuItem(
                            text = {
                                Text(if (fav) "Desfavoritar" else "Favoritar", color = MaterialTheme.colorScheme.onSurface)
                            },
                            leadingIcon = {
                                Icon(
                                    if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    contentDescription = null,
                                    tint = if (fav) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                menuForIndex = null
                                onToggleFavorite(track)
                            }
                        )
                        LensMenuItem(
                            text = { Text("Excluir permanentemente", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            },
                            onClick = {
                                menuForIndex = null
                                selectedIds = emptySet()
                                onDelete(track)
                            }
                        )
                    }
                }
            }
        }

        if (showSelectionMenu && selectionMode) {
            val selectedTracks = order.filter { it.id in selectedIds }
            AppAlertDialog(
                onDismissRequest = { showSelectionMenu = false },
                title = {
                    Text(
                        if (selectedTracks.size == 1) "1 faixa na fila"
                        else "${selectedTracks.size} faixas na fila",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = {
                    Column {
                        TextButton(
                            onClick = {
                                selectedTracks.forEach { onPlayNext(it) }
                                showSelectionMenu = false
                                selectedIds = emptySet()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Reproduzir a seguir", color = MaterialTheme.colorScheme.onSurface) }
                        TextButton(
                            onClick = {
                                order.mapIndexedNotNull { i, t -> if (t.id in selectedIds) i else null }
                                    .sortedDescending()
                                    .forEach { onRemoveFromQueue(it) }
                                showSelectionMenu = false
                                selectedIds = emptySet()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Remover da fila", color = MaterialTheme.colorScheme.onSurface) }
                        TextButton(
                            onClick = {
                                selectedTracks.forEach { onToggleFavorite(it) }
                                showSelectionMenu = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val allFav = selectedTracks.isNotEmpty() && selectedTracks.all { isFavorite(it.id) }
                            Text(if (allFav) "Desfavoritar" else "Favoritar", color = MaterialTheme.colorScheme.onSurface)
                        }
                        TextButton(
                            onClick = {
                                showSelectionMenu = false
                                selectedTracks.forEach { onDelete(it) }
                                selectedIds = emptySet()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Excluir permanentemente", color = MaterialTheme.colorScheme.error) }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSelectionMenu = false }) {
                        Text("Fechar", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            )
        }
            } // Column
        } // Box
    }
}



@Composable
internal fun LyricsSearchDialog(
    initialTitle: String,
    initialArtist: String,
    onDismiss: () -> Unit,
    onSearch: suspend (String, String) -> List<LyricsSearchResult>,
    onPick: (LyricsSearchResult) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var artist by remember { mutableStateOf(initialArtist) }
    var results by remember { mutableStateOf<List<LyricsSearchResult>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val dialogDark = menuPalette(LocalAppColors.current).content
    val dialogMuted = menuPalette(LocalAppColors.current).muted
    val dialogAccent = menuPalette(LocalAppColors.current).accent
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buscar letra", color = dialogDark) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título", color = dialogMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = dialogDark,
                        unfocusedTextColor = dialogDark,
                        focusedBorderColor = dialogAccent,
                        unfocusedBorderColor = dialogMuted,
                        cursorColor = dialogAccent,
                        focusedLabelColor = dialogAccent,
                        unfocusedLabelColor = dialogMuted
                    )
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artista", color = dialogMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = dialogDark,
                        unfocusedTextColor = dialogDark,
                        focusedBorderColor = dialogAccent,
                        unfocusedBorderColor = dialogMuted,
                        cursorColor = dialogAccent,
                        focusedLabelColor = dialogAccent,
                        unfocusedLabelColor = dialogMuted
                    )
                )
                Spacer(Modifier.height(12.dp))
                if (loading) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                } else if (searched && results.isEmpty()) {
                    Text("Nenhum resultado encontrado.", fontSize = 14.sp, color = dialogMuted)
                    if (report.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        // Mostra por fonte se houve resposta (número) ou falha (HTTP 503, sem conexão…).
                        Text(report, fontSize = 11.sp, color = dialogMuted.copy(alpha = 0.8f))
                    }
                } else if (results.isNotEmpty()) {
                    var expandedId by remember { mutableStateOf<String?>(null) }
                    LazyColumn(Modifier.height(280.dp)) {
                        items(results, key = { it.id }) { r ->
                            val expanded = expandedId == r.id
                            val isLyronRepository = r.source == LyricsSource.LYRON_REPOSITORY
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        when {
                                            expanded -> Color(0xFF1B3A2A)
                                            isLyronRepository -> Color(0xFF2A203A)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .then(
                                        if (isLyronRepository) {
                                            Modifier.border(1.dp, Color(0xFFB985FF), RoundedCornerShape(10.dp))
                                        } else Modifier
                                    )
                                    .clickable {
                                        if (expanded) onPick(r)
                                        else expandedId = r.id
                                    }
                                    .padding(horizontal = 8.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    if (expanded) "Letra" else r.trackName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (expanded) Color(0xFFB8F5C8) else dialogDark,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!expanded) {
                                    Text(
                                        buildString {
                                            append(r.artistName)
                                            if (r.albumName.isNotBlank()) append(" · ${r.albumName}")
                                            if (r.durationSec > 0) append(" · ${r.durationSec}s")
                                            r.syncedBy?.takeIf { it.isNotBlank() }?.let {
                                                append(" · sync: $it")
                                            }
                                        },
                                        fontSize = 12.sp,
                                        color = dialogMuted,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        Modifier.padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        LyricsBadge(
                                            r.source.label,
                                            if (isLyronRepository) Color(0xFFE7D1FF) else Color(0xFFECEFF1),
                                            if (isLyronRepository) Color(0xFF45216B) else Color(0xFF263238)
                                        )
                                        r.variant?.let { variant ->
                                            LyricsBadge(
                                                variant.label,
                                                if (variant == com.tolete.player.data.LyricsVariant.ROMANIZED) Color(0xFFD9F2E6) else Color(0xFFFFE8C2),
                                                if (variant == com.tolete.player.data.LyricsVariant.ROMANIZED) Color(0xFF185C3A) else Color(0xFF704500)
                                            )
                                        }
                                        val (badgeBg, badgeFg) = when (r.type) {
                                            LyricsType.PLAIN -> Color(0xFFE0E0E0) to Color(0xFF424242)
                                            LyricsType.SYNCED -> Color(0xFFD6E4FF) to Color(0xFF1E4DB7)
                                            LyricsType.WORD -> Color(0xFFE6D8FF) to Color(0xFF5B2CA0)
                                        }
                                        LyricsBadge(r.type.label, badgeBg, badgeFg)
                                        if (r.hasElrcEffects) {
                                            LyricsBadge("ELRC+", Color(0xFFFFE0B2), Color(0xFF8A3B00))
                                        }
                                    }
                                } else {
                                    // Preview estilo referência: faixa/artista + linhas sincronizadas
                                    Text("faixa", fontSize = 11.sp, color = Color(0xFF8BC9A0))
                                    Text(
                                        "${r.trackName}",
                                        fontSize = 13.sp,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text("artista", fontSize = 11.sp, color = Color(0xFF8BC9A0))
                                    Text(
                                        r.artistName,
                                        fontSize = 13.sp,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text("fonte", fontSize = 11.sp, color = Color(0xFF8BC9A0))
                                    Text(
                                        r.source.label,
                                        fontSize = 13.sp,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    r.variant?.let { variant ->
                                        Text("variante", fontSize = 11.sp, color = Color(0xFF8BC9A0))
                                        Text(variant.label, fontSize = 13.sp, color = Color.White)
                                    }
                                    Text("tipo de letra", fontSize = 11.sp, color = Color(0xFF8BC9A0))
                                    Text(
                                        r.type.label,
                                        fontSize = 13.sp,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    r.syncedBy?.takeIf { it.isNotBlank() }?.let { by ->
                                        Text("sincronizado por", fontSize = 11.sp, color = Color(0xFF8BC9A0))
                                        Text(
                                            by,
                                            fontSize = 13.sp,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (r.hasElrcEffects) {
                                        Row(Modifier.padding(top = 4.dp)) {
                                            LyricsBadge("ELRC+", Color(0xFFFFE0B2), Color(0xFF8A3B00))
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    val previewLines = remember(r.id) { r.lines.take(12) }
                                    if (previewLines.isEmpty()) {
                                        Text(
                                            "(sem prévia)",
                                            fontSize = 12.sp,
                                            color = Color(0xFFD0E8D8)
                                        )
                                    } else {
                                        previewLines.forEach { line ->
                                            val ts = if (line.timeMs >= 0) {
                                                val totalSec = line.timeMs / 1000
                                                val m = totalSec / 60
                                                val s = totalSec % 60
                                                val cs = ((line.timeMs % 1000) / 10)
                                                "[%02d:%02d.%02d]".format(m, s, cs)
                                            } else ""
                                            Text(
                                                "$ts${line.text}",
                                                fontSize = 12.sp,
                                                color = Color(0xFFD0E8D8),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Text(
                                        "Toque de novo para usar esta letra",
                                        fontSize = 11.sp,
                                        color = Color(0xFF8BC9A0),
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        loading = true
                        searched = false
                        report = ""
                        results = try {
                            onSearch(title.trim(), artist.trim())
                        } catch (e: Exception) {
                            report = "erro inesperado: ${e.javaClass.simpleName}"
                            emptyList()
                        }
                        if (report.isEmpty()) report = com.tolete.player.data.LyricsSearch.lastReport
                        loading = false
                        searched = true
                    }
                }
            ) { Text("Pesquisar", color = dialogAccent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Descartar", color = dialogMuted) }
        }
    )
}


@Composable
private fun LyricsBadge(text: String, background: Color, foreground: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = foreground,
            maxLines = 1
        )
    }
}


/**
 * Índice da tradução ({tr}) que pertence à linha principal em [mainIdx], ou -1.
 * Regra: a tradução logo DEPOIS da principal (pulando backing no meio); se não houver, a que vem
 * logo ANTES, desde que esteja praticamente no mesmo instante e não pertença a outra principal.
 */
internal fun pairedTranslationIndex(lines: List<LyricLine>, mainIdx: Int): Int {
    if (mainIdx !in lines.indices) return -1
    var j = mainIdx + 1
    while (j < lines.size && lines[j].isBackground && !lines[j].isTranslation) j++
    if (j < lines.size && lines[j].isTranslation) return j

    var k = mainIdx - 1
    while (k >= 0 && lines[k].isBackground && !lines[k].isTranslation) k--
    if (k >= 0 && lines[k].isTranslation &&
        kotlin.math.abs(lines[k].timeMs - lines[mainIdx].timeMs) <= 500L
    ) {
        var p = k - 1
        while (p >= 0 && lines[p].isBackground && !lines[p].isTranslation) p--
        val ownedByPrevious = p >= 0 && !lines[p].isTranslation &&
            kotlin.math.abs(lines[p].timeMs - lines[k].timeMs) <= 500L
        if (!ownedByPrevious) return k
    }
    return -1
}


@Composable
internal fun FullscreenLyrics(
    track: Track,
    lyrics: List<LyricLine>,
    position: Long,
    lyricsFont: FontFamily?,
    translationFont: FontFamily? = null,
    backingFont: FontFamily? = null,
    emphasisFont: FontFamily? = null,
    fontSizeSp: Float = 22f,
    primaryColor: Color = Color.White,
    secondaryColor: Color = Color.White.copy(alpha = 0.40f),
    translationColor: Color = Color(0xFFB0BEC5),
    backingColor: Color = Color(0xFFCE93D8),
    backingSecondaryColor: Color = Color(0x99CE93D8),
    borderColor: Color = Color.Black,
    borderWidth: Float = 0f,
    backingBorderColor: Color = borderColor,
    backingBorderWidth: Float = borderWidth,
    translationBorderColor: Color = borderColor,
    translationBorderWidth: Float = borderWidth,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit
) {
    KeepScreenOn()
    val context = LocalContext.current
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val hasTimestamps = remember(lyrics) { lyrics.any { it.timeMs >= 0 } }
    // Índices ativos: principal + backing + tradução.
    // A linha principal nunca é uma tradução; a tradução ativa é a que pertence (pela ordem)
    // à principal ativa.
    val (activeIndex, activeBgIndex, activeTrIndex) = remember(lyrics, position, hasTimestamps) {
        if (!hasTimestamps) Triple(-1, -1, -1)
        else {
            var bestMain = -1
            var latestStartedBg = -1
            var lastStartedMain = -1
            var lastStarted = -1
            for (i in lyrics.indices) {
                val line = lyrics[i]
                if (line.timeMs < 0) continue
                if (line.timeMs > position) break
                lastStarted = i
                if (!line.isTranslation && !line.isBackground) lastStartedMain = i
                if (line.isBackground && !line.isTranslation) latestStartedBg = i
                val stillActive = if (line.isBackground && !line.isTranslation) {
                    isBackingLineVisible(line, position)
                } else {
                    line.endMs < 0 || position <= line.endMs + 120L
                }
                if (!stillActive) continue
                when {
                    line.isTranslation -> Unit
                    line.isBackground -> Unit
                    else -> {
                        if (bestMain < 0 || line.timeMs >= lyrics[bestMain].timeMs) bestMain = i
                    }
                }
            }
            val main = when {
                bestMain >= 0 -> bestMain
                lastStartedMain >= 0 -> lastStartedMain
                lastStarted >= 0 && !lyrics[lastStarted].isTranslation -> lastStarted
                else -> -1
            }
            fun overlapsMain(idx: Int): Boolean {
                if (idx < 0 || main < 0) return idx >= 0
                val mainLine = lyrics[main]
                val other = lyrics[idx]
                val mainEnd = if (mainLine.endMs > 0) mainLine.endMs else Long.MAX_VALUE
                return other.timeMs <= mainEnd && (other.endMs < 0 || other.endMs >= mainLine.timeMs)
            }
            // Uma BG nova substitui a anterior mesmo que esta ainda esteja na janela de 1,5 s.
            val bg = latestStartedBg.takeIf {
                it >= 0 && isBackingLineVisible(lyrics[it], position) && overlapsMain(it)
            } ?: -1
            val tr = if (main >= 0) pairedTranslationIndex(lyrics, main) else -1
            Triple(main, bg, tr)
        }
    }
    var selectMode by remember { mutableStateOf(false) }
    var selectedLines by remember { mutableStateOf(setOf<Int>()) }

    LaunchedEffect(activeIndex) {
        if (hasTimestamps && !selectMode && activeIndex in lyrics.indices) {
            listState.animateScrollToItem(
                index = activeIndex,
                scrollOffset = -180
            )
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B14))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Crossfade(
            targetState = track.artPath,
            animationSpec = tween(400),
            label = "fullLyricsBg",
            modifier = Modifier.fillMaxSize()
        ) { art ->
            if (art != null) {
                AsyncImage(
                    model = File(art),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(22.dp, BlurredEdgeTreatment.Unbounded)
                )
            } else {
                Box(Modifier.fillMaxSize().background(AppGradient))
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(0.45f), Color.Black.copy(0.60f), Color.Black.copy(0.80f))
                    )
                )
        )

        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (selectMode) {
                        selectMode = false
                        selectedLines = emptySet()
                    } else onClose()
                }) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Fechar", modifier = Modifier.size(32.dp))
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (selectMode) "${selectedLines.size} linha(s)" else track.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (selectMode) "Selecione linhas → compartilhar = foto" else track.artist,
                        fontSize = 12.sp,
                        color = Color.White.copy(0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = {
                    if (selectMode) {
                        if (selectedLines.isNotEmpty()) {
                            val ordered = selectedLines.sorted().mapNotNull { lyrics.getOrNull(it) }.take(15)
                            android.widget.Toast.makeText(context, "Gerando foto…", android.widget.Toast.LENGTH_SHORT).show()
                            LyricsShareHelper.shareSnippet(context, track, ordered)
                            selectMode = false
                            selectedLines = emptySet()
                        } else {
                            android.widget.Toast.makeText(
                                context,
                                "Selecione até 15 linhas para gerar a foto",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        selectMode = true
                        android.widget.Toast.makeText(
                            context,
                            "Toque nas linhas (até 15) e depois no compartilhar para gerar a foto",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = "Gerar foto do trecho",
                        tint = if (selectMode && selectedLines.isNotEmpty()) Color(0xFF69F0AE) else Color.White
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp),
                contentPadding = PaddingValues(vertical = 120.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                itemsIndexed(lyrics) { index, line ->
                    val isMainActive = index == activeIndex
                    val isBgActive = index == activeBgIndex
                    val isTrActive = index == activeTrIndex
                    val isActive = isMainActive || isBgActive || isTrActive
                    val isBg = line.isBackground
                    val isTr = line.isTranslation
                    val baseSize = when {
                        isMainActive -> (fontSizeSp + 6f).sp
                        isBgActive -> (fontSizeSp + 1f).sp
                        isTrActive -> (fontSizeSp - 1f).coerceAtLeast(12f).sp
                        isBg || isTr -> (fontSizeSp - 2f).coerceAtLeast(12f).sp
                        else -> fontSizeSp.sp
                    }
                    val scale by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = when {
                            isMainActive -> 1.07f
                            isBgActive || isTrActive -> 1.03f
                            else -> 1f
                        },
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = 0.92f,
                            stiffness = 180f
                        ),
                        label = "lyricScale"
                    )
                    val alpha by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = when {
                            !hasTimestamps -> 0.85f
                            line.timeMs < 0 -> 0.78f
                            isMainActive -> 1f
                            isBgActive || isTrActive -> 0.92f
                            kotlin.math.abs(index - activeIndex) <= 1 -> 0.55f
                            kotlin.math.abs(index - activeIndex) <= 3 -> 0.34f
                            else -> 0.18f
                        },
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = 0.94f,
                            stiffness = 150f
                        ),
                        label = "lyricAlpha"
                    )
                    val isSelected = index in selectedLines
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                this.alpha = if (selectMode) 1f else alpha
                            }
                            .then(
                                if (selectMode && isSelected)
                                    Modifier.background(Color(0xFF69F0AE).copy(0.2f), RoundedCornerShape(10.dp))
                                else Modifier
                            )
                            .clickable {
                                if (selectMode) {
                                    selectedLines = if (isSelected) {
                                        selectedLines - index
                                    } else if (selectedLines.size < 15) {
                                        selectedLines + index
                                    } else {
                                        android.widget.Toast.makeText(
                                            context,
                                            "Máximo de 15 linhas",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                        selectedLines
                                    }
                                } else if (line.timeMs >= 0) {
                                    onSeek(line.timeMs)
                                }
                            }
                            .padding(vertical = if (isBg) 6.dp else 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isActive && line.hasSyllableSync) {
                            SyllableLine(
                                line = line,
                                position = position,
                                fontSize = baseSize,
                                fontFamily = when {
                                    isTr -> translationFont ?: lyricsFont
                                    isBg -> backingFont ?: lyricsFont
                                    else -> lyricsFont
                                },
                                // Tradução usa a cor de tradução; letra usa primária/secundária
                                primary = when {
                                    isTr -> translationColor
                                    isBg -> backingColor
                                    else -> primaryColor
                                },
                                secondary = when {
                                    isTr -> translationColor.copy(alpha = 0.55f)
                                    isBg -> backingSecondaryColor
                                    else -> secondaryColor
                                },
                                borderColor = when { isTr -> translationBorderColor; isBg -> backingBorderColor; else -> borderColor },
                                borderWidth = when { isTr -> translationBorderWidth; isBg -> backingBorderWidth; else -> borderWidth },
                                emphasisFont = emphasisFont,
                                isTranslation = isTr,
                                isBackground = isBg,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            val lyColor = when {
                                isMainActive -> primaryColor
                                isBgActive -> backingColor.copy(alpha = 0.95f)
                                isTrActive -> translationColor.copy(alpha = 0.95f)
                                isBg -> backingSecondaryColor
                                isTr -> secondaryColor  // fora do timestamp: secundária
                                else -> secondaryColor
                            }
                            val lyFont = when {
                                isTr -> translationFont ?: lyricsFont
                                isBg -> backingFont ?: lyricsFont
                                else -> lyricsFont
                            }
                            val lyWeight = when {
                                isMainActive -> FontWeight.Bold
                                isBgActive -> FontWeight.SemiBold
                                else -> FontWeight.Normal
                            }
                            val lyItalic = if (isBg || isTr) androidx.compose.ui.text.font.FontStyle.Italic
                            else androidx.compose.ui.text.font.FontStyle.Normal
                            val lyBorderColor = when { isTr -> translationBorderColor; isBg -> backingBorderColor; else -> borderColor }
                            val lyBorderWidth = when { isTr -> translationBorderWidth; isBg -> backingBorderWidth; else -> borderWidth }
                            if (lyBorderWidth > 0f) {
                                OutlinedLyricText(
                                    text = line.text,
                                    color = lyColor,
                                    borderColor = lyBorderColor,
                                    borderWidthDp = lyBorderWidth,
                                    fontSize = baseSize,
                                    fontFamily = lyFont,
                                    fontWeight = lyWeight,
                                    italic = isBg || isTr,
                                    lineHeightMul = 1.32f,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Text(
                                    line.text,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                    fontSize = baseSize,
                                    lineHeight = (baseSize.value * 1.32f).sp,
                                    fontWeight = lyWeight,
                                    color = lyColor,
                                    fontFamily = lyFont,
                                    fontStyle = lyItalic
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SyllableLine(
    line: LyricLine,
    position: Long,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontFamily: FontFamily?,
    primary: Color,
    secondary: Color,
    borderColor: Color,
    borderWidth: Float,
    emphasisFont: FontFamily? = null,
    isTranslation: Boolean = false,
    isBackground: Boolean = false,
    modifier: Modifier = Modifier
) {
    var activeWordIdx = -1
    for (i in line.words.indices) {
        val timeMs = line.words[i].timeMs
        if (timeMs >= 0L) {
            if (position >= timeMs) activeWordIdx = i else break
        }
    }
    val wordProgress = if (activeWordIdx < 0) {
        0f
    } else {
        val current = line.words[activeWordIdx]
        val nextTimedWord = line.words.drop(activeWordIdx + 1).firstOrNull {
            it.timeMs > current.timeMs
        }
        // Preferência: endMs já calculado no parse/save → próximo timestamp de palavra → fim da linha.
        // Sem hard-cap agressivo de 800 ms: letras manuais (espaçamento maior) e baixadas
        // passam a preencher no ritmo real da sincronização, o que deixa o karaokê bem mais suave.
        val candidateEnd = when {
            current.endMs > current.timeMs -> current.endMs
            nextTimedWord != null && nextTimedWord.timeMs > current.timeMs -> nextTimedWord.timeMs
            line.endMs > current.timeMs -> line.endMs
            else -> current.timeMs + 1_200L
        }
        // Só limita spans absurdamente longos (silêncios / fim de verso) para não “arrastar” o fill.
        val end = minOf(candidateEnd, current.timeMs + 2_200L)
        val span = (end - current.timeMs).coerceAtLeast(1L).toFloat()
        val linear = ((position - current.timeMs) / span).coerceIn(0f, 1f)
        // Smoothstep: desacelera ao entrar/sair da palavra.
        linear * linear * (3f - 2f * linear)
    }

    val effects = rememberInfiniteTransition(label = "lyricWordEffects")
    val effectPhase by effects.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 460, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "lyricWordEffectPhase"
    )
    val shakePhase by effects.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 110, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "lyricWordShakePhase"
    )

    val dimSecondary = secondary.copy(alpha = if (isBackground) 0.48f else 0.58f)
    val activePrimary = if (isBackground) primary.copy(alpha = 0.88f) else primary
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        line.words.forEachIndexed { index, word ->
            val hasFx = word.animation != LyricAnimation.NONE
            // Tradução: sem karaokê palavra-a-palavra — só palavras com efeito animam no tempo delas
            val fullyActive = if (isTranslation) false else index < activeWordIdx
            val isCurrent = if (isTranslation) {
                hasFx && (word.timeMs < 0L || position >= word.timeMs) &&
                    (index == activeWordIdx || word.timeMs < 0L)
            } else index == activeWordIdx
            // A animação começa no tempo da palavra (ou da linha se não houver timestamp)
            val effectStarted = hasFx && (word.timeMs < 0L || position >= word.timeMs)
            val animStartMs = when {
                word.timeMs >= 0L -> word.timeMs
                line.timeMs >= 0L -> line.timeMs
                else -> -1L
            }
            val animElapsed = if (animStartMs >= 0L) (position - animStartMs).coerceAtLeast(0L) else 0L
            val animElapsedF = animElapsed.toFloat()

            val rainbowColor = if (word.animation == LyricAnimation.RNB && effectStarted) {
                val hue = ((animElapsedF / 12f) % 360f)
                Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.85f, 1f)))
            } else null
            val flashBoost = if (word.animation == LyricAnimation.FLA && effectStarted && animElapsed < 220L) {
                1f - (animElapsedF / 220f)
            } else 0f
            val color = when {
                isTranslation && !hasFx -> activePrimary
                rainbowColor != null -> rainbowColor
                word.animation == LyricAnimation.GLO && effectStarted ->
                    androidx.compose.ui.graphics.lerp(primary, secondary, effectPhase)
                word.animation == LyricAnimation.FLA && effectStarted && flashBoost > 0f ->
                    androidx.compose.ui.graphics.lerp(activePrimary, Color.White, flashBoost * 0.85f)
                word.animation == LyricAnimation.FRZ && effectStarted -> activePrimary
                fullyActive -> activePrimary
                isCurrent -> androidx.compose.ui.graphics.lerp(dimSecondary, activePrimary, wordProgress)
                else -> dimSecondary
            }

            val isDes = word.animation == LyricAnimation.DES && effectStarted
            val isShaking = word.animation == LyricAnimation.POP && effectStarted
            val isVib = word.animation == LyricAnimation.VIB && effectStarted && animElapsed < 450L
            val isWav = word.animation == LyricAnimation.WAV && effectStarted
            val isEco = word.animation == LyricAnimation.ECO && effectStarted
            val isSli = word.animation == LyricAnimation.SLI && effectStarted
            val isZou = word.animation == LyricAnimation.ZOU && effectStarted
            val isZin = word.animation == LyricAnimation.ZIN && effectStarted
            val isShd = word.animation == LyricAnimation.SHD && effectStarted
            val isTyp = word.animation == LyricAnimation.TYP && effectStarted

            val isDed = word.animation == LyricAnimation.DED && effectStarted
            // Mesmo tempo na letra e na tradução (1s)
            val dedDelayMs = 1000L
            val dedAlpha = if (isDed && animStartMs >= 0L) {
                when {
                    animElapsed < dedDelayMs -> 1f
                    animElapsed < dedDelayMs + 200L -> 1f - ((animElapsed - dedDelayMs).toFloat() / 200f)
                    else -> 0f
                }
            } else 1f

            // Zoom só via graphicsLayer (não mexe no layout). DES só via fontSize (reserva espaço).
            val zoomScale = when {
                isZou -> {
                    val k = (animElapsedF / 300f).coerceIn(0f, 1f)
                    1.55f - 0.55f * (1f - (1f - k) * (1f - k))
                }
                isZin -> {
                    val k = (animElapsedF / 300f).coerceIn(0f, 1f)
                    0.55f + 0.45f * (1f - (1f - k) * (1f - k))
                }
                else -> 1f
            }
            val desFontMul = if (isDes) 1.24f else 1f

            val slideY = if (isSli) {
                val k = (animElapsedF / 280f).coerceIn(0f, 1f)
                val e = 1f - (1f - k) * (1f - k)
                (1f - e) * 14f
            } else 0f

            val displayText = if (isTyp && word.text.isNotEmpty()) {
                val n = word.text.length
                val chars = ((animElapsedF / 55f).toInt() + 1).coerceIn(0, n)
                word.text.take(chars)
            } else word.text

            val leadingSpace = if (index > 0 && !word.isSyllable) {
                val previous = line.words[index - 1].text
                if (previous.isNotEmpty() && word.text.isNotEmpty() &&
                    !previous.last().isWhitespace() && !word.text.first().isWhitespace() &&
                    !",.;:!?)]}".contains(word.text.first())
                ) " " else ""
            } else ""

            val shadowStyle = when {
                isShd && animElapsed < 500L -> {
                    val k = 1f - (animElapsedF / 500f).coerceIn(0f, 1f)
                    androidx.compose.ui.text.TextStyle(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = primary.copy(alpha = 0.55f * k),
                            offset = androidx.compose.ui.geometry.Offset(4f * k, 6f * k),
                            blurRadius = 10f * k + borderWidth
                        )
                    )
                }
                else -> androidx.compose.ui.text.TextStyle.Default
            }

            val wordLayer = Modifier.graphicsLayer {
                alpha = dedAlpha
                scaleX = zoomScale
                scaleY = zoomScale
                var ty = slideY.dp.toPx()
                if (isWav) {
                    ty += (sin((animElapsedF / 160f) * 2f * Math.PI.toFloat()) * 2.8f).dp.toPx()
                }
                if (isShaking) {
                    val phase = shakePhase * 2f * Math.PI.toFloat()
                    translationX = (sin(phase) * 2.4f).dp.toPx()
                    ty += (sin(phase * 2f) * 1.1f).dp.toPx()
                } else if (isVib) {
                    val phase = shakePhase * 2f * Math.PI.toFloat()
                    translationX = (sin(phase) * 1.1f).dp.toPx()
                    ty += (sin(phase * 1.7f) * 0.7f).dp.toPx()
                } else {
                    translationX = 0f
                }
                translationY = ty
            }
            val wordWeight = when {
                word.animation == LyricAnimation.FRZ && effectStarted ->
                    if (isBackground) FontWeight.SemiBold else FontWeight.Bold
                fullyActive || (isCurrent && wordProgress > 0.15f) ->
                    if (isBackground) FontWeight.SemiBold else FontWeight.Bold
                else -> FontWeight.Normal
            }
            val wordFamily = if (isDes) emphasisFont ?: fontFamily else fontFamily
            val wordItalic = if (isBackground || isTranslation)
                androidx.compose.ui.text.font.FontStyle.Italic
            else androidx.compose.ui.text.font.FontStyle.Normal

            // Box: eco sobrepõe sem ocupar espaço extra no FlowRow
            Box {
                if (isEco) {
                    val ghostPhase = ((animElapsedF / 180f) % 1f)
                    Text(
                        text = leadingSpace + word.text,
                        modifier = Modifier.graphicsLayer {
                            alpha = dedAlpha * 0.35f * (1f - ghostPhase * 0.5f)
                            translationX = 3.2f.dp.toPx()
                            translationY = (2.2f + ghostPhase * 3f).dp.toPx()
                            scaleX = zoomScale
                            scaleY = zoomScale
                        },
                        color = color.copy(alpha = (color.alpha * 0.7f).coerceIn(0f, 1f)),
                        fontSize = fontSize * desFontMul,
                        fontWeight = FontWeight.Normal,
                        fontFamily = fontFamily,
                        fontStyle = if (isBackground || isTranslation)
                            androidx.compose.ui.text.font.FontStyle.Italic
                        else androidx.compose.ui.text.font.FontStyle.Normal,
                        lineHeight = (fontSize.value * 1.32f * desFontMul).sp,
                        textAlign = TextAlign.Center
                    )
                }
                if (borderWidth > 0f) {
                    OutlinedWordText(
                        text = leadingSpace + displayText,
                        color = color,
                        borderColor = borderColor,
                        borderWidthDp = borderWidth,
                        fontSize = fontSize * desFontMul,
                        fontFamily = wordFamily,
                        fontWeight = wordWeight,
                        italic = isBackground || isTranslation,
                        lineHeightMul = 1.32f,
                        shadow = shadowStyle.shadow,
                        modifier = wordLayer
                    )
                } else {
                    Text(
                        text = leadingSpace + displayText,
                        modifier = wordLayer,
                        color = color,
                        fontSize = fontSize * desFontMul,
                        fontWeight = wordWeight,
                        fontFamily = wordFamily,
                        fontStyle = wordItalic,
                        lineHeight = (fontSize.value * 1.32f * desFontMul).sp,
                        textAlign = TextAlign.Center,
                        style = shadowStyle
                    )
                }
            }
        }
    }
}


@Composable
private fun PresetGlassBar(
    presetPath: String?,
    isFavorite: Boolean,
    onFavorite: () -> Unit,
    onOpenList: () -> Unit,
    modifier: Modifier = Modifier
) {
    val name = presetPath?.let { java.io.File(it).nameWithoutExtension } ?: "Preset"
    Row(
        modifier
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.22f),
                        Color.White.copy(alpha = 0.08f),
                        Color.White.copy(alpha = 0.14f)
                    )
                ),
                CircleShape
            )
            .border(1.dp, Color.White.copy(0.30f), CircleShape)
            .clickable(onClick = onOpenList)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            name,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = "Favoritar preset",
            tint = if (isFavorite) Color(0xFFE53935) else Color.White.copy(0.85f),
            modifier = Modifier
                .size(18.dp)
                .clickable(onClick = onFavorite)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MilkPresetPickerDialog(
    presets: List<String>,
    currentPath: String?,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onDelete: ((String) -> Unit)? = null,
    manageMode: Boolean = false,
    onFavoritesChanged: (Set<String>) -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("tolete", Context.MODE_PRIVATE)
    }
    var favorites by remember {
        mutableStateOf(prefs.getStringSet("milk_preset_favs", emptySet())?.toSet() ?: emptySet())
    }
    var query by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var showClearFavoritesConfirmation by remember { mutableStateOf(false) }

    fun toggleFav(path: String) {
        val name = java.io.File(path).name
        val next = favorites.toMutableSet()
        if (name in next) next.remove(name) else next.add(name)
        favorites = next
        prefs.edit().putStringSet("milk_preset_favs", next).apply()
        onFavoritesChanged(next)
    }
    val sorted = remember(presets, favorites, query) {
        val fav = presets.filter { java.io.File(it).name in favorites }
        val rest = presets.filter { java.io.File(it).name !in favorites }
        val base = fav + rest
        if (query.isBlank()) base
        else {
            val q = query.trim().lowercase()
            base.filter {
                java.io.File(it).name.removeSuffix(".milk").removeSuffix(".MILK").lowercase().contains(q)
            }
        }
    }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val currentIndex = remember(sorted, currentPath) {
        if (currentPath == null) 0
        else sorted.indexOfFirst {
            it == currentPath || java.io.File(it).name == java.io.File(currentPath).name
        }.coerceAtLeast(0)
    }
    LaunchedEffect(currentIndex, sorted.size, query) {
        if (sorted.isNotEmpty() && query.isBlank()) listState.scrollToItem(currentIndex)
    }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (manageMode) "Gerenciar presets" else "Visualizer — presets",
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showSearch = !showSearch }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = "Buscar",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                if (manageMode) {
                    IconButton(
                        onClick = { showClearFavoritesConfirmation = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Filled.HeartBroken,
                            contentDescription = "Limpar todos os favoritos",
                            tint = Color(0xFFE57373)
                        )
                    }
                }
            }
        },
        text = {
            Column(
                Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.13f), Color(0xFF171923).copy(alpha = 0.60f))
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(22.dp))
                    .padding(10.dp)
            ) {
                if (showSearch) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Buscar preset…") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                }
                if (sorted.isEmpty()) {
                    Text(
                        if (query.isNotBlank()) "Nenhum preset encontrado." else "Nenhum preset disponível.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                } else {
                    LazyColumn(state = listState, modifier = Modifier.height(320.dp)) {
                        items(sorted.size) { i ->
                            val path = sorted[i]
                            val name = java.io.File(path).name.removeSuffix(".milk").removeSuffix(".MILK")
                            val isFav = java.io.File(path).name in favorites
                            val isCurrent = currentPath != null && (
                                path == currentPath || java.io.File(path).name == java.io.File(currentPath).name
                            )
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(50.dp))
                                    .background(
                                        if (isCurrent) MaterialTheme.colorScheme.primary.copy(0.19f)
                                        else Color.White.copy(alpha = 0.055f)
                                    )
                                    .border(
                                        1.dp,
                                        if (isCurrent) MaterialTheme.colorScheme.primary.copy(0.42f)
                                        else Color.White.copy(alpha = 0.10f),
                                        RoundedCornerShape(50.dp)
                                    )
                                    .combinedClickable(
                                        onClick = {
                                            if (!manageMode) {
                                                // Fecha o IME antes de aplicar — evita toque “comido” pela busca
                                                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
                                                    as? android.view.inputmethod.InputMethodManager
                                                imm?.hideSoftInputFromWindow(
                                                    (context as? android.app.Activity)?.currentFocus?.windowToken,
                                                    0
                                                )
                                                onSelect(path)
                                            }
                                        },
                                        onLongClick = {
                                            if (onDelete != null) pendingDelete = path
                                        }
                                    )
                                    .padding(vertical = 8.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    name,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { toggleFav(path) }, modifier = Modifier.size(36.dp)) {
                                    Icon(
                                        imageVector = if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                        contentDescription = "Favoritar",
                                        tint = if (isFav) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                if (manageMode && onDelete != null) {
                                    IconButton(
                                        onClick = { pendingDelete = path },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Delete,
                                            contentDescription = "Excluir",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                } else if (isCurrent) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = "Atual",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                if (manageMode) {
                    TextButton(onClick = {
                        val favPaths = presets.filter { java.io.File(it).name in favorites }
                        if (favPaths.isEmpty()) {
                            android.widget.Toast.makeText(
                                context,
                                "Nenhum preset favorito para exportar",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            try {
                                val dir = java.io.File(context.cacheDir, "updates").apply { mkdirs() }
                                val out = java.io.File(dir, "milk_favorites_${System.currentTimeMillis()}.zip")
                                java.util.zip.ZipOutputStream(java.io.FileOutputStream(out)).use { zos ->
                                    for (p in favPaths) {
                                        val f = java.io.File(p)
                                        if (!f.isFile) continue
                                        zos.putNextEntry(java.util.zip.ZipEntry(f.name))
                                        f.inputStream().use { it.copyTo(zos) }
                                        zos.closeEntry()
                                    }
                                }
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    context.packageName + ".updates",
                                    out
                                )
                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "application/zip"
                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(
                                    android.content.Intent.createChooser(intent, "Exportar presets favoritos")
                                )
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(
                                    context,
                                    "Falha ao exportar: ${e.message}",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }) {
                        Text("Exportar favoritos (.zip)", color = MaterialTheme.colorScheme.primary)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Fechar") }
            }
        }
    )

    pendingDelete?.let { path ->
        val name = java.io.File(path).name.removeSuffix(".milk").removeSuffix(".MILK")
        AppAlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Excluir preset?") },
            text = {
                Text("Remover \"$name\"? Esta ação não pode ser desfeita.")
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete?.invoke(path)
                    pendingDelete = null
                }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") }
            }
        )
    }
    if (showClearFavoritesConfirmation) {
        AppAlertDialog(
            onDismissRequest = { showClearFavoritesConfirmation = false },
            title = { Text("Limpar favoritos?") },
            text = {
                Text("Remover todos os presets da lista de favoritos? Os arquivos não serão excluídos.")
            },
            confirmButton = {
                TextButton(onClick = {
                    favorites = emptySet()
                    prefs.edit().putStringSet("milk_preset_favs", mutableSetOf()).apply()
                    onFavoritesChanged(emptySet())
                    showClearFavoritesConfirmation = false
                }) {
                    Text("Limpar favoritos", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearFavoritesConfirmation = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}



@Composable
private fun PlaybackSpeedDialog(
    value: Float,
    onValueChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    onApply: () -> Unit
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Velocidade de reprodução") },
        text = {
            Column(
                Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.14f), Color(0xFF171923).copy(alpha = 0.62f))
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(22.dp))
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "${(value * 100f).toInt()}%",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold
                )
                androidx.compose.material3.Slider(
                    value = value.coerceIn(0.25f, 2f),
                    onValueChange = { onValueChange(it.coerceIn(0.25f, 2f)) },
                    valueRange = 0.25f..2f,
                    steps = 0,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("25%", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text("200%", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("Cancelar") }
                TextButton(onClick = onApply) { Text("Aplicar") }
            }
        }
    )
}

@Composable
private fun SleepTimerDialog(
    currentMinutes: Int,
    onDismiss: () -> Unit,
    onSet: (Int) -> Unit
) {
    val options = listOf(0, 5, 10, 15, 30, 45, 60)
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Timer de sono") },
        text = {
            Column {
                options.forEach { m ->
                    val label = if (m == 0) "Desligado" else "$m minutos"
                    Text(
                        text = label,
                        color = if (m == currentMinutes) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSet(m) }
                            .padding(vertical = 12.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
