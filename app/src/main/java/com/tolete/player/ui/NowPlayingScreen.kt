package com.tolete.player.ui

import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import com.tolete.player.data.LrcLibClient
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.tolete.player.data.Track
import com.tolete.player.visualizer.MilkVisualizer
import com.tolete.player.visualizer.ProjectMNative
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

/** Cor de texto em menus com fundo claro (glass). */
private val MenuDarkText = Color(0xFF1A1A1A)

/**
 * Até 2 linhas de letra; reduz a fonte automaticamente até o texto caber
 * na largura disponível (quebra suave na 2ª linha se necessário).
 */
@Composable
private fun AutoSizeSingleLineLyric(
    text: String,
    baseFontSize: androidx.compose.ui.unit.TextUnit,
    fontFamily: androidx.compose.ui.text.font.FontFamily?,
    color: Color,
    modifier: Modifier = Modifier,
    minFontSizeSp: Float = 11f,
    maxLines: Int = 2
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
    shuffle: Boolean,
    repeatMode: Int = Player.REPEAT_MODE_OFF,
    queue: List<Track>,
    isFavorite: Boolean,
    lyricsFontPath: String? = null,
    getPosition: () -> Long,
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
    isFavoriteCheck: (String) -> Boolean = { false },
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onEditTags: () -> Unit = {},
    onEditLyrics: () -> Unit = {},
    artistNames: List<String>,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: () -> Unit,
    loadLyrics: suspend () -> List<LyricLine>,
    searchLyrics: suspend (String, String) -> List<LrcLibClient.SearchResult> = { _, _ -> emptyList() },
    embedLyricsResult: suspend (LrcLibClient.SearchResult, com.tolete.player.data.LyricsEmbedder.Target) -> List<LyricLine> = { _, _ -> emptyList() },
    appColors: AppColors = DefaultAppColors,
    milkPresets: List<String> = emptyList(),
    onDeletePreset: (String) -> Unit = {},
    milkIntervalSec: Int = 30,
    changeSongWithMilkInterval: Boolean = false,
    changePresetOnSongChange: Boolean = true,
    onSkipToRandom: () -> Unit = {},
    lyricsFontSize: Float = 22f,
    lyricsPrimaryColor: Int = 0xFFFFFFFF.toInt(),
    lyricsSecondaryColor: Int = 0x66FFFFFF,
    lyricsBorderColor: Int = 0xFF000000.toInt(),
    lyricsBorderWidth: Float = 0f,
    menuBgColor: Int = 0xE8F2F2F7.toInt(),
    playCount: Int = 0,
    lastPlayedAt: Long = 0L,
    seekBarStyle: SeekBarStyle = SeekBarStyle.Standard
) {
    // Mantém a tela acesa enquanto o Now Playing estiver aberto
    KeepScreenOn()

    // Modo imersivo: esconde barra de status e de navegação enquanto o Now Playing estiver aberto
    val view = LocalView.current
    DisposableEffect(Unit) {
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
    var showQueue by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showTechDetails by remember { mutableStateOf(false) }
    var pendingDeleteTrack by remember { mutableStateOf<Track?>(null) }
    var showArtistMenu by remember(track.id) { mutableStateOf(false) }
    var showLyricsSearch by remember { mutableStateOf(false) }
    var showFullLyrics by remember { mutableStateOf(false) }

    // Troca de preset ao mudar de música
    LaunchedEffect(track.id, changePresetOnSongChange, showVisualizer) {
        if (changePresetOnSongChange && showVisualizer && milkPresets.size > 1 && !presetLocked) {
            forcePresetPath = milkPresets.random()
        }
    }

    // Timer: além do preset, opcionalmente troca a música aleatoriamente
    LaunchedEffect(milkIntervalSec, changeSongWithMilkInterval, showVisualizer, isPlaying) {
        if (!changeSongWithMilkInterval || milkIntervalSec <= 0 || !showVisualizer || !isPlaying) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(milkIntervalSec * 1000L)
            if (changeSongWithMilkInterval && showVisualizer && isPlaying) {
                onSkipToRandom()
            }
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

    LaunchedEffect(isFavorite, track.id) {
        if (isFavorite != prevFavorite) {
            heartBurst = isFavorite
            prevFavorite = isFavorite
            // favoritar: ~0.9s; desfavoritar.gif: 1.1s
            kotlinx.coroutines.delay(if (isFavorite) 900L else 1100L)
            heartBurst = null
        }
    }

    // Atualiza posição com menos frequência e só quando muda de fato,
    // para não recompôr a tela inteira (blur, capa, controles) a cada tick.
    LaunchedEffect(track.id, isPlaying) {
        position = getPosition()
        while (isPlaying) {
            delay(400)
            val p = getPosition()
            if (kotlin.math.abs(p - position) >= 200L || p < position) {
                position = p
            }
        }
    }

    // Carrega letras sempre que a faixa muda
    LaunchedEffect(track.id) {
        lyrics = loadLyrics()
    }

    val duration = track.duration.coerceAtLeast(1L)
    val fraction = dragFraction ?: (position.toFloat() / duration).coerceIn(0f, 1f)
    val shownPosition = dragFraction?.let { (it * duration).toLong() } ?: position

    // Linha ativa (só sincronizadas com timeMs >= 0)
    val synced = remember(lyrics) { lyrics.filter { it.timeMs >= 0 } }
    // Só muda quando a linha muda — evita recompor texto a cada tick de ms.
    val activeLine = remember(synced, position / 50) {
        if (synced.isEmpty()) null
        else {
            var cur: LyricLine? = null
            for (l in synced) {
                if (l.timeMs <= position) cur = l else break
            }
            cur
        }
    }

    val lyricsFont = remember(lyricsFontPath) {
        BuiltInFonts.resolveFamily(lyricsFontPath)
    }

    Box(
        Modifier
            .fillMaxSize()
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
                .padding(horizontal = 24.dp)
        ) {
            // Top bar
            Row(
                Modifier.fillMaxWidth().height(56.dp),
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
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        containerColor = Color(menuBgColor),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .border(
                                1.dp,
                                Brush.verticalGradient(
                                    listOf(Color.White.copy(0.7f), Color.White.copy(0.25f))
                                ),
                                RoundedCornerShape(16.dp)
                            )
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text("Adicionar a uma playlist", color = MenuDarkText)
                            },
                            onClick = {
                                showMenu = false
                                onAddToPlaylist()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text("Editar tags", color = MenuDarkText)
                            },
                            onClick = {
                                showMenu = false
                                onEditTags()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text("Buscar letra", color = MenuDarkText)
                            },
                            onClick = {
                                showMenu = false
                                showLyricsSearch = true
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text("Editar / sincronizar letra", color = MenuDarkText)
                            },
                            onClick = {
                                showMenu = false
                                onEditLyrics()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (sleepMinutes > 0) "Timer de sono (${sleepMinutes} min)" else "Timer de sono",
                                    color = MenuDarkText
                                )
                            },
                            onClick = {
                                showMenu = false
                                showSleepTimer = true
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text("Gerar foto do trecho", color = MenuDarkText)
                            },
                            onClick = {
                                showMenu = false
                                val lyric = activeLine?.text?.takeIf { it.isNotBlank() }
                                val linesToShare = if (lyric != null) {
                                    listOf(lyric)
                                } else {
                                    lyrics.filter { it.text.isNotBlank() }.take(8).map { it.text }
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
                        DropdownMenuItem(
                            text = {
                                Text("Excluir arquivo", color = Color(0xFFC62828))
                            },
                            onClick = {
                                showMenu = false
                                pendingDeleteTrack = track
                            },
                            leadingIcon = {
                                Icon(Icons.Rounded.Delete, contentDescription = null, tint = Color(0xFFC62828))
                            }
                        )
                    }
                }
            }

            // Informações técnicas (clicável)
            val info = technicalInfo(track)
            val codecRes = codecDrawableRes(track.codec)
            Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
                if (info.isNotEmpty() || codecRes != null) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.White.copy(0.18f), Color.White.copy(0.06f))
                                )
                            )
                            .border(
                                1.dp,
                                Brush.verticalGradient(
                                    listOf(Color.White.copy(0.45f), Color.White.copy(0.08f))
                                ),
                                RoundedCornerShape(50)
                            )
                            .clickable { showTechDetails = true }
                            .padding(horizontal = 16.dp, vertical = 7.dp)
                    ) {
                        Row(
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
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // Capa + letra. A letra ocupa todo o espaço entre a capa e o título
            // e fica centralizada nele (sobra espaço para 2 linhas quando a frase é longa).
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val topPadding = 8.dp
                val lyricsMinHeight = 72.dp
                val side = maxOf(minOf(maxWidth, maxHeight - lyricsMinHeight - topPadding), 120.dp)

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
                            MilkVisualizer(
                                presets = milkPresets,
                                intervalSeconds = milkIntervalSec,
                                cornerRadius = 20.dp,
                                locked = presetLocked,
                                isPlaying = isPlaying,
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

                    // Chip do preset ABAIXO da capa (não por cima)
                    if (showVisualizer && milkPresets.isNotEmpty()) {
                        PresetGlassBar(
                            presetPath = currentPresetPath,
                            isFavorite = currentPresetPath?.let { java.io.File(it).name in milkPresetFavorites } == true,
                            onFavorite = {
                                currentPresetPath?.let { path ->
                                    val name = java.io.File(path).name
                                    milkPresetFavorites = if (name in milkPresetFavorites) milkPresetFavorites - name
                                    else milkPresetFavorites + name
                                }
                            },
                            onOpenList = { showPresetPicker = true },
                            modifier = Modifier
                                .padding(horizontal = 4.dp, vertical = 6.dp)
                                .fillMaxWidth()
                        )
                    }

                    // Letra — até 2 linhas; fonte reduz automaticamente se não couber
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clickable(enabled = lyrics.isNotEmpty()) { showFullLyrics = true },
                        contentAlignment = Alignment.Center
                    ) {
                        if (synced.isNotEmpty()) {
                            AnimatedContent(
                                targetState = activeLine?.text.orEmpty(),
                                transitionSpec = {
                                    (slideInVertically(
                                        initialOffsetY = { it / 3 },
                                        animationSpec = tween(420, easing = FastOutSlowInEasing)
                                    ) + fadeIn(tween(320, easing = FastOutSlowInEasing)) +
                                        scaleIn(initialScale = 0.88f, animationSpec = tween(420, easing = FastOutSlowInEasing))) togetherWith
                                        (slideOutVertically(
                                            targetOffsetY = { -it / 4 },
                                            animationSpec = tween(280, easing = FastOutSlowInEasing)
                                        ) + fadeOut(tween(240)) +
                                            scaleOut(targetScale = 1.06f, animationSpec = tween(280)))
                                },
                                label = "lyricLine"
                            ) { line ->
                                if (line.isNotEmpty()) {
                                    AutoSizeSingleLineLyric(
                                        text = line,
                                        baseFontSize = lyricsFontSize.sp,
                                        fontFamily = lyricsFont,
                                        color = Color(lyricsPrimaryColor).copy(alpha = 0.95f),
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                                    )
                                }
                            }
                        } else if (lyrics.isNotEmpty()) {
                            // Sem timestamps: trecho atual (até 2 linhas na tela)
                            val plain = lyrics.firstOrNull { it.text.isNotBlank() }?.text.orEmpty()
                            if (plain.isNotEmpty()) {
                                AutoSizeSingleLineLyric(
                                    text = plain,
                                    baseFontSize = lyricsFontSize.sp,
                                    fontFamily = lyricsFont,
                                    color = Color(lyricsPrimaryColor).copy(alpha = 0.95f),
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Título e artista/álbum (estilo cartão de música)
            Text(
                track.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
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
                DropdownMenu(
                    expanded = showArtistMenu,
                    onDismissRequest = { showArtistMenu = false },
                    containerColor = Color(menuBgColor),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .border(
                            1.dp,
                            Brush.verticalGradient(
                                listOf(Color.White.copy(0.7f), Color.White.copy(0.25f))
                            ),
                            RoundedCornerShape(16.dp)
                        )
                ) {
                    artistNames.forEach { name ->
                        DropdownMenuItem(
                            text = { Text(name, color = MenuDarkText) },
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
                    .padding(bottom = 12.dp)
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
                onDrag = { dragFraction = it },
                onDragEnd = { f ->
                    val target = (f * duration).toLong()
                    position = target
                    dragFraction = null
                    onSeek(target)
                },
                onDragCancel = { dragFraction = null }
            )

            // Tempos
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatDuration(shownPosition), fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f))
                Text(formatDuration(duration), fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f))
            }
            // Linha 1: previous / play / next
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { artNavForward = false; onPrevious() }, modifier = Modifier.size(56.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_previous),
                        contentDescription = "Anterior",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }
                Box(
                    Modifier
                        .padding(horizontal = 10.dp)
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(onClick = onToggle),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                        contentDescription = if (isPlaying) "Pausar" else "Tocar",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(38.dp)
                    )
                }
                IconButton(onClick = { artNavForward = true; onNext() }, modifier = Modifier.size(56.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_next),
                        contentDescription = "Próxima",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }
            // Linha 2: shuffle, repeat | equalizer, queue
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp, top = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleShuffle, modifier = Modifier.size(48.dp)) {
                    Icon(
                        painter = painterResource(if (shuffle) R.drawable.ic_random_on else R.drawable.ic_random_off),
                        contentDescription = if (shuffle) "Aleatório ligado" else "Aleatório desligado",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(onClick = onCycleRepeat, modifier = Modifier.size(48.dp)) {
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
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(onClick = { showEqualizer = true }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_equalizer),
                        contentDescription = "Equalizador",
                        modifier = Modifier.size(28.dp),
                        tint = Color.White.copy(alpha = 0.9f)
                    )
                }
                IconButton(onClick = { showQueue = true }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_queue),
                        contentDescription = "Fila",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(28.dp)
                    )
                }
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
                menuBgColor = menuBgColor
            )
        }

        pendingDeleteTrack?.let { toDelete ->
            AlertDialog(
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
                    ) { Text("Excluir", color = Color(0xFFE53935)) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteTrack = null }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        if (showTechDetails) {
            TechDetailsDialog(
                track = track,
                onDismiss = { showTechDetails = false },
                menuBgColor = menuBgColor,
                playCount = playCount,
                lastPlayedAt = lastPlayedAt
            )
        }

        if (showFullLyrics && lyrics.isNotEmpty()) {
            FullscreenLyrics(
                track = track,
                lyrics = if (synced.isNotEmpty()) synced else lyrics,
                position = position,
                lyricsFont = lyricsFont,
                fontSizeSp = lyricsFontSize,
                primaryColor = Color(lyricsPrimaryColor),
                secondaryColor = Color(lyricsSecondaryColor),
                borderColor = Color(lyricsBorderColor),
                borderWidth = lyricsBorderWidth,
                onSeek = { ms ->
                    onSeek(ms)
                    position = ms
                },
                onClose = { showFullLyrics = false }
            )
        }

        if (showLyricsSearch) {
            var pendingLyricResult by remember { mutableStateOf<LrcLibClient.SearchResult?>(null) }
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
                AlertDialog(
                    onDismissRequest = { pendingLyricResult = null },
                    title = { Text("Salvar letra") },
                    text = {
                        Text(
                            "Como deseja salvar a letra encontrada?\n\n" +
                                "• Arquivo .lrc — ao lado do áudio (recomendado)\n" +
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
                                            "Letra salva em .lrc",
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
                                            "Letra embutida no arquivo",
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
                onDelete = onDeletePreset
            )
        }
    }
}

@Composable
internal fun TechDetailsDialog(
    track: Track,
    onDismiss: () -> Unit,
    menuBgColor: Int = 0xE8F2F2F7.toInt(),
    playCount: Int = 0,
    lastPlayedAt: Long = 0L
) {
    val context = LocalContext.current
    val audioLines = remember {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        buildList {
            add("Saída de áudio")
            try {
                if (Build.VERSION.SDK_INT >= 23) {
                    val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    val active = devices.filter {
                        it.isSink && (
                            it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ||
                                it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                                it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                                it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                                it.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET ||
                                it.type == android.media.AudioDeviceInfo.TYPE_USB_DEVICE ||
                                it.type == android.media.AudioDeviceInfo.TYPE_HEARING_AID ||
                                (Build.VERSION.SDK_INT >= 31 && it.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET)
                            )
                    }
                    if (active.isEmpty()) {
                        add("Dispositivos: padrão do sistema")
                    } else {
                        for (d in active) {
                            val name = if (Build.VERSION.SDK_INT >= 28) d.productName?.toString() else null
                            val type = when (d.type) {
                                android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Alto-falante"
                                android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                                android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Fone com fio"
                                android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
                                android.media.AudioDeviceInfo.TYPE_USB_HEADSET,
                                android.media.AudioDeviceInfo.TYPE_USB_DEVICE -> "USB"
                                else -> "Saída ${d.type}"
                            }
                            val rates = d.sampleRates?.takeIf { it.isNotEmpty() }?.joinToString { formatSampleRate(it) }
                            val ch = d.channelCounts?.maxOrNull()?.let { formatChannels(it) }
                            add("$type${name?.let { " ($it)" } ?: ""}")
                            if (rates != null) add("  Taxas: $rates")
                            if (ch != null) add("  Canais: $ch")
                        }
                    }
                } else {
                    add("Modo: ${am.mode}")
                }
            } catch (_: Exception) {
                add("Informação de saída indisponível")
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(menuBgColor),
        title = { Text("Informações técnicas", color = Color(0xFF1A1A1A)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TechRow("Título", track.title)
                TechRow("Artista", track.artist)
                TechRow("Álbum", track.album)
                TechRow("Gênero", track.genre)
                track.year?.let { TechRow("Ano", it.toString()) }
                TechRow("Codec", track.codec ?: "—")
                TechRow("Bitrate", if (track.bitrate > 0) "${Math.round(track.bitrate / 1000.0)} kbps" else "—")
                TechRow("Amostragem", formatSampleRate(track.sampleRate))
                TechRow("BitDepth", if (track.bitDepth > 0) "${track.bitDepth}-bit" else "—")
                TechRow("Canais", formatChannels(track.channels))
                TechRow("Duração", formatDuration(track.duration))
                TechRow(
                    "Tamanho",
                    if (track.fileSize > 0) {
                        val mb = track.fileSize / (1024.0 * 1024.0)
                        if (mb >= 1.0) "%.2f MB".format(mb) else "%.0f KB".format(track.fileSize / 1024.0)
                    } else "—"
                )
                TechRow("Nº da faixa", track.trackNumber?.toString() ?: "—")
                TechRow("Nº do disco", track.discNumber?.toString() ?: "—")
                TechRow("Compositores", track.composer.ifBlank { "—" })
                TechRow(
                    "Última reprodução",
                    if (lastPlayedAt > 0L) {
                        java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(lastPlayedAt))
                    } else "—"
                )
                TechRow("Vezes reproduzida", if (playCount > 0) playCount.toString() else "—")
                TechRow("Arquivo", track.path)
                Spacer(Modifier.height(12.dp))
                audioLines.forEachIndexed { i, line ->
                    if (i == 0) {
                        Text(line, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF1A1A1A), modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
                    } else {
                        Text(line, fontSize = 13.sp, color = Color(0xFF333333), modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fechar", color = Color(0xFF5C6BC0)) }
        }
    )
}

@Composable
private fun TechRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, modifier = Modifier.width(100.dp), fontSize = 13.sp, color = Color(0xFF666666))
        Text(value, fontSize = 13.sp, color = Color(0xFF1A1A1A), maxLines = 3, overflow = TextOverflow.Ellipsis)
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

/** Estilos da barra de progresso (seekbar) do Now Playing. */
enum class SeekBarStyle {
    Normal,    // barra fina, thumb discreto
    Standard,  // padrão atual (gradiente + thumb circular)
    Wavy,      // onda senoidal no progresso
    Thick,     // trilha mais grossa, thumb maior
    Slim,      // trilha bem fina
    Liquid     // bolha/elipse no ponto de progresso (efeito “líquido”)
}

@Composable
private fun SeekBar(
    fraction: Float,
    accentColor: Color = Color.White,
    style: SeekBarStyle = SeekBarStyle.Standard,
    isPlaying: Boolean = true,
    onDrag: (Float) -> Unit,
    onDragEnd: (Float) -> Unit,
    onDragCancel: () -> Unit
) {
    var barWidth by remember { mutableFloatStateOf(1f) }
    var lastDragFraction by remember { mutableFloatStateOf(fraction) }
    var dragging by remember { mutableStateOf(false) }
    val density = LocalDensity.current.density
    val trackHeight = when (style) {
        SeekBarStyle.Thick -> 10.dp
        SeekBarStyle.Slim, SeekBarStyle.Normal -> 3.dp
        SeekBarStyle.Wavy -> 8.dp
        SeekBarStyle.Liquid -> 6.dp
        SeekBarStyle.Standard -> 5.dp
    }
    val thumbSize = when (style) {
        SeekBarStyle.Thick -> 18.dp
        SeekBarStyle.Slim, SeekBarStyle.Normal -> 10.dp
        SeekBarStyle.Liquid -> 16.dp
        SeekBarStyle.Wavy -> 12.dp
        SeekBarStyle.Standard -> 14.dp
    }
    val boxHeight = when (style) {
        SeekBarStyle.Thick, SeekBarStyle.Liquid, SeekBarStyle.Wavy -> 32.dp
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
        if (style == SeekBarStyle.Wavy) {
            rememberInfiniteTransition(label = "wave").animateFloat(
                initialValue = 0f,
                targetValue = (2.0 * Math.PI).toFloat(),
                animationSpec = infiniteRepeatable(tween(durationMillis = 1400, easing = LinearEasing)),
                label = "wavePhase"
            )
        } else null

    Box(
        Modifier
            .fillMaxWidth()
            .height(boxHeight)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { o ->
                        barWidth = size.width.toFloat().coerceAtLeast(1f)
                        val f = (o.x / barWidth).coerceIn(0f, 1f)
                        lastDragFraction = f
                        dragging = true
                        onDrag(f)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val f = (change.position.x / barWidth).coerceIn(0f, 1f)
                        lastDragFraction = f
                        onDrag(f)
                    },
                    onDragEnd = {
                        dragging = false
                        onDragEnd(lastDragFraction)
                    },
                    onDragCancel = {
                        dragging = false
                        onDragCancel()
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    barWidth = size.width.toFloat().coerceAtLeast(1f)
                    val f = (o.x / barWidth).coerceIn(0f, 1f)
                    lastDragFraction = f
                    onDragEnd(f)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxWidth().height(trackHeight)) {
            barWidth = size.width
            val h = size.height
            val frac = fraction.coerceIn(0f, 1f)
            val progW = size.width * frac

            when (style) {
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
            SeekBarStyle.Liquid -> {
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
    menuBgColor: Int = 0xE8F2F2F7.toInt()
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
                    .background(Color(0xCC0A0A12))
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
                    DropdownMenu(
                        expanded = menuForIndex == realIndex && !showSelectionMenu,
                        onDismissRequest = { menuForIndex = null },
                        containerColor = Color(menuBgColor),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .border(
                                1.dp,
                                Brush.verticalGradient(
                                    listOf(Color.White.copy(0.7f), Color.White.copy(0.25f))
                                ),
                                RoundedCornerShape(16.dp)
                            )
                    ) {
                        val fav = isFavorite(track.id)
                        DropdownMenuItem(
                            text = { Text("Reproduzir a seguir", color = MenuDarkText) },
                            onClick = {
                                menuForIndex = null
                                selectedIds = emptySet()
                                onPlayNext(track)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Remover da fila", color = MenuDarkText) },
                            leadingIcon = {
                                Icon(Icons.Rounded.Close, contentDescription = null, tint = MenuDarkText)
                            },
                            onClick = {
                                menuForIndex = null
                                selectedIds = emptySet()
                                onRemoveFromQueue(realIndex)
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(if (fav) "Desfavoritar" else "Favoritar", color = MenuDarkText)
                            },
                            leadingIcon = {
                                Icon(
                                    if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    contentDescription = null,
                                    tint = if (fav) Color(0xFFE53935) else MenuDarkText
                                )
                            },
                            onClick = {
                                menuForIndex = null
                                onToggleFavorite(track)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Excluir permanentemente", color = Color(0xFFC62828)) },
                            leadingIcon = {
                                Icon(Icons.Rounded.Delete, contentDescription = null, tint = Color(0xFFC62828))
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
            AlertDialog(
                onDismissRequest = { showSelectionMenu = false },
                containerColor = Color(menuBgColor),
                title = {
                    Text(
                        if (selectedTracks.size == 1) "1 faixa na fila"
                        else "${selectedTracks.size} faixas na fila",
                        color = Color(0xFF1A1A1A)
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
                        ) { Text("Reproduzir a seguir", color = Color(0xFF1A1A1A)) }
                        TextButton(
                            onClick = {
                                order.mapIndexedNotNull { i, t -> if (t.id in selectedIds) i else null }
                                    .sortedDescending()
                                    .forEach { onRemoveFromQueue(it) }
                                showSelectionMenu = false
                                selectedIds = emptySet()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Remover da fila", color = Color(0xFF1A1A1A)) }
                        TextButton(
                            onClick = {
                                selectedTracks.forEach { onToggleFavorite(it) }
                                showSelectionMenu = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val allFav = selectedTracks.isNotEmpty() && selectedTracks.all { isFavorite(it.id) }
                            Text(if (allFav) "Desfavoritar" else "Favoritar", color = Color(0xFF1A1A1A))
                        }
                        TextButton(
                            onClick = {
                                showSelectionMenu = false
                                selectedTracks.forEach { onDelete(it) }
                                selectedIds = emptySet()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Excluir permanentemente", color = Color(0xFFC62828)) }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSelectionMenu = false }) {
                        Text("Fechar", color = Color(0xFF1A1A1A))
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
    onSearch: suspend (String, String) -> List<LrcLibClient.SearchResult>,
    onPick: (LrcLibClient.SearchResult) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var artist by remember { mutableStateOf(initialArtist) }
    var results by remember { mutableStateOf<List<LrcLibClient.SearchResult>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val dialogDark = Color(0xFF1A1A1A)
    val dialogMuted = Color(0xFF555555)
    AlertDialog(
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
                        focusedBorderColor = Color(0xFF5C6BC0),
                        unfocusedBorderColor = Color(0xFFAAAAAA),
                        cursorColor = Color(0xFF5C6BC0),
                        focusedLabelColor = Color(0xFF5C6BC0),
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
                        focusedBorderColor = Color(0xFF5C6BC0),
                        unfocusedBorderColor = Color(0xFFAAAAAA),
                        cursorColor = Color(0xFF5C6BC0),
                        focusedLabelColor = Color(0xFF5C6BC0),
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
                } else if (results.isNotEmpty()) {
                    var expandedId by remember { mutableStateOf<Int?>(null) }
                    LazyColumn(Modifier.height(280.dp)) {
                        items(results, key = { it.id }) { r ->
                            val expanded = expandedId == r.id
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (expanded) Color(0xFF1B3A2A) else Color.Transparent)
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
                                            append(if (r.hasSynced) " · sincronizada" else " · simples")
                                        },
                                        fontSize = 12.sp,
                                        color = dialogMuted,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
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
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        if (r.hasSynced) "Linha sincronizada" else "Linha simples",
                                        fontSize = 11.sp,
                                        color = Color(0xFF8BC9A0)
                                    )
                                    val previewLines = remember(r.id) {
                                        r.toLyricLines().take(12)
                                    }
                                    if (previewLines.isEmpty()) {
                                        Text(
                                            r.plainLyrics?.lines()?.take(8)?.joinToString("\n")
                                                ?: "(sem prévia)",
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
                        results = try { onSearch(title.trim(), artist.trim()) } catch (_: Exception) { emptyList() }
                        loading = false
                        searched = true
                    }
                }
            ) { Text("Pesquisar", color = Color(0xFF5C6BC0)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Descartar", color = dialogMuted) }
        }
    )
}


@Composable
internal fun FullscreenLyrics(
    track: Track,
    lyrics: List<LyricLine>,
    position: Long,
    lyricsFont: FontFamily?,
    fontSizeSp: Float = 22f,
    primaryColor: Color = Color.White,
    secondaryColor: Color = Color.White.copy(alpha = 0.40f),
    borderColor: Color = Color.Black,
    borderWidth: Float = 0f,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit
) {
    KeepScreenOn()
    val context = LocalContext.current
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val hasTimestamps = remember(lyrics) { lyrics.any { it.timeMs >= 0 } }
    val activeIndex = remember(lyrics, position, hasTimestamps) {
        if (!hasTimestamps) -1
        else {
            var idx = 0
            for (i in lyrics.indices) {
                if (lyrics[i].timeMs <= position) idx = i else break
            }
            idx
        }
    }
    var selectMode by remember { mutableStateOf(false) }
    var selectedLines by remember { mutableStateOf(setOf<Int>()) }

    LaunchedEffect(activeIndex) {
        if (hasTimestamps && !selectMode && activeIndex in lyrics.indices) {
            listState.animateScrollToItem(
                index = activeIndex,
                scrollOffset = -200
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
                            val ordered = selectedLines.sorted().mapNotNull { lyrics.getOrNull(it)?.text }.take(8)
                            android.widget.Toast.makeText(context, "Gerando foto…", android.widget.Toast.LENGTH_SHORT).show()
                            LyricsShareHelper.shareSnippet(context, track, ordered)
                            selectMode = false
                            selectedLines = emptySet()
                        } else {
                            android.widget.Toast.makeText(
                                context,
                                "Selecione até 8 linhas para gerar a foto",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        selectMode = true
                        android.widget.Toast.makeText(
                            context,
                            "Toque nas linhas (até 8) e depois no compartilhar para gerar a foto",
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
                    val isActive = index == activeIndex
                    val baseSize = if (isActive) (fontSizeSp + 4f).sp else fontSizeSp.sp
                    val scale by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (isActive) 1.06f else 1f,
                        animationSpec = tween(320, easing = FastOutSlowInEasing),
                        label = "lyricScale"
                    )
                    val alpha by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = when {
                            !hasTimestamps -> 0.85f
                            isActive -> 1f
                            kotlin.math.abs(index - activeIndex) <= 1 -> 0.55f
                            else -> 0.32f
                        },
                        animationSpec = tween(280, easing = FastOutSlowInEasing),
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
                                    } else if (selectedLines.size < 8) {
                                        selectedLines + index
                                    } else {
                                        android.widget.Toast.makeText(
                                            context,
                                            "Máximo de 8 linhas",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                        selectedLines
                                    }
                                } else if (line.timeMs >= 0) {
                                    onSeek(line.timeMs)
                                }
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isActive && line.hasSyllableSync) {
                            SyllableLine(
                                line = line,
                                position = position,
                                fontSize = baseSize,
                                fontFamily = lyricsFont,
                                primary = primaryColor,
                                secondary = secondaryColor,
                                borderColor = borderColor,
                                borderWidth = borderWidth,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                line.text,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                fontSize = baseSize,
                                lineHeight = (baseSize.value * 1.35f).sp,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                color = if (isActive) primaryColor else secondaryColor,
                                fontFamily = lyricsFont,
                                style = if (borderWidth > 0f) {
                                    androidx.compose.ui.text.TextStyle(
                                        shadow = androidx.compose.ui.graphics.Shadow(
                                            color = borderColor,
                                            blurRadius = borderWidth * 2f
                                        )
                                    )
                                } else androidx.compose.ui.text.TextStyle.Default
                            )
                        }
                    }
                }
            }
        }
    }
}

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
    modifier: Modifier = Modifier
) {
    // Progresso dentro da palavra atual (0..1) para preenchimento suave
    val activeWordIdx = remember(line, position) {
        var idx = -1
        for (i in line.words.indices) {
            if (position >= line.words[i].timeMs) idx = i else break
        }
        idx
    }
    val wordProgress = remember(line, position, activeWordIdx) {
        if (activeWordIdx < 0 || activeWordIdx >= line.words.lastIndex) 1f
        else {
            val cur = line.words[activeWordIdx]
            val next = line.words[activeWordIdx + 1]
            val span = (next.timeMs - cur.timeMs).coerceAtLeast(1L).toFloat()
            ((position - cur.timeMs) / span).coerceIn(0f, 1f)
        }
    }
    val annotated = androidx.compose.ui.text.buildAnnotatedString {
        line.words.forEachIndexed { i, w ->
            val fullyActive = i < activeWordIdx
            val isCurrent = i == activeWordIdx
            val color = when {
                fullyActive -> primary
                isCurrent -> androidx.compose.ui.graphics.lerp(secondary, primary, wordProgress)
                else -> secondary
            }
            pushStyle(
                androidx.compose.ui.text.SpanStyle(
                    color = color,
                    fontWeight = if (fullyActive || isCurrent) FontWeight.Bold else FontWeight.Normal,
                    fontSize = fontSize,
                    fontFamily = fontFamily,
                    shadow = if (borderWidth > 0f) androidx.compose.ui.graphics.Shadow(
                        color = borderColor,
                        blurRadius = borderWidth * 2f
                    ) else null
                )
            )
            append(w.text)
            pop()
        }
    }
    Text(
        text = annotated,
        modifier = modifier,
        textAlign = TextAlign.Center,
        lineHeight = (fontSize.value * 1.35f).sp
    )
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
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .border(1.dp, Color.White.copy(0.22f), RoundedCornerShape(16.dp))
            .clickable(onClick = onOpenList)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            name,
            color = Color.White,
            fontSize = 13.sp,
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
                .size(20.dp)
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
    manageMode: Boolean = false
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

    fun toggleFav(path: String) {
        val name = java.io.File(path).name
        val next = favorites.toMutableSet()
        if (name in next) next.remove(name) else next.add(name)
        favorites = next
        prefs.edit().putStringSet("milk_preset_favs", next).apply()
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (manageMode) "Gerenciar presets" else "Visualizer — presets",
                    color = Color(0xFF1A1A1A),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showSearch = !showSearch }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = "Buscar",
                        tint = Color(0xFF5C6BC0)
                    )
                }
            }
        },
        text = {
            Column {
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
                        color = Color(0xFF666666),
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
                                    .combinedClickable(
                                        onClick = {
                                            if (!manageMode) onSelect(path)
                                        },
                                        onLongClick = {
                                            if (onDelete != null) pendingDelete = path
                                        }
                                    )
                                    .background(if (isCurrent) Color(0xFF5C6BC0).copy(0.15f) else Color.Transparent)
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    name,
                                    color = Color(0xFF1A1A1A),
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
                                        tint = if (isFav) Color(0xFFE53935) else Color(0xFF888888),
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
                                            tint = Color(0xFFC62828),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                } else if (isCurrent) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = "Atual",
                                        tint = Color(0xFF5C6BC0),
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
            TextButton(onClick = onDismiss) { Text("Fechar", color = Color(0xFF5C6BC0)) }
        }
    )

    pendingDelete?.let { path ->
        val name = java.io.File(path).name.removeSuffix(".milk").removeSuffix(".MILK")
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Excluir preset?") },
            text = {
                Text("Remover \"$name\"? Esta ação não pode ser desfeita.")
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete?.invoke(path)
                    pendingDelete = null
                }) { Text("Excluir", color = Color(0xFFE53935)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") }
            }
        )
    }
}



@Composable
private fun SleepTimerDialog(
    currentMinutes: Int,
    onDismiss: () -> Unit,
    onSet: (Int) -> Unit
) {
    val options = listOf(0, 5, 10, 15, 30, 45, 60)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A24),
        title = { Text("Timer de sono", color = Color.White) },
        text = {
            Column {
                options.forEach { m ->
                    val label = if (m == 0) "Desligado" else "$m minutos"
                    Text(
                        text = label,
                        color = if (m == currentMinutes) Color(0xFFB388FF) else Color.White.copy(0.85f),
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
                Text("Cancelar", color = Color.White.copy(0.7f))
            }
        }
    )
}
