package com.tolete.player

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tolete.player.data.TagReader
import com.tolete.player.data.Track
import com.tolete.player.ui.ExternalMiniPlayer
import com.tolete.player.ui.GlassConfig
import com.tolete.player.ui.GlassQuality
import com.tolete.player.ui.MusicViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Activity translúcida: ao abrir áudio de outro app, não inicializa a tela da biblioteca. */
class ExternalPlayerActivity : ComponentActivity() {
    private val viewModel: MusicViewModel by viewModels()

    private var externalUri by mutableStateOf<Uri?>(null)
    private var externalTitle by mutableStateOf("Música")
    private var externalArtist by mutableStateOf("")
    private var externalAlbum by mutableStateOf("")
    private var externalArtPath by mutableStateOf<String?>(null)
    private var externalTrackId by mutableStateOf<String?>(null)
    private var resolvingMetadata by mutableStateOf(true)
    private var playbackError by mutableStateOf<String?>(null)
    private var metadataJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
        window.statusBarColor = AndroidColor.TRANSPARENT
        window.navigationBarColor = AndroidColor.TRANSPARENT
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val isPlaying by viewModel.playbackButtonIsPlaying.collectAsStateWithLifecycle()
                val position by viewModel.positionMs.collectAsStateWithLifecycle()
                val duration by viewModel.durationMs.collectAsStateWithLifecycle()
                val backdrop = rememberGraphicsLayer()

                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .drawWithContent {
                                val scrim = Color.Black.copy(alpha = 0.34f)
                                if (GlassConfig.quality == GlassQuality.LIGHT) {
                                    drawRect(scrim)
                                } else {
                                    backdrop.record { drawRect(scrim) }
                                    drawLayer(backdrop)
                                }
                            }
                            .clickable(onClick = { finish() })
                    )

                    if (externalUri != null) {
                        ExternalMiniPlayer(
                            backdrop = backdrop,
                            title = externalTitle,
                            artist = externalArtist,
                            album = externalAlbum,
                            artPath = externalArtPath,
                            isLoading = resolvingMetadata,
                            errorMessage = playbackError,
                            isPlaying = isPlaying,
                            positionMs = position,
                            durationMs = duration,
                            onPlay = {
                                if (!resolvingMetadata && playbackError == null) viewModel.startPlayback()
                            },
                            onPause = {
                                if (!resolvingMetadata && playbackError == null) viewModel.pausePlayback()
                            },
                            onSeek = { fraction ->
                                if (!resolvingMetadata && playbackError == null && duration > 0L) {
                                    viewModel.seekTo((fraction * duration).toLong())
                                }
                            },
                            onDismiss = { finish() },
                            onOpenFull = { openNowPlaying(externalTrackId) }
                        )
                    }
                }
            }
        }

        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) {
            finish()
            return
        }
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            }
            else -> intent.data
        }
        if (uri == null) {
            finish()
            return
        }

        if (uri.scheme == "content" &&
            intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0
        ) {
            val grantFlags = intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
            if (grantFlags != 0) {
                try {
                    contentResolver.takePersistableUriPermission(uri, grantFlags)
                } catch (_: SecurityException) {
                    // Alguns provedores concedem apenas acesso temporário.
                }
            }
        }

        metadataJob?.cancel()
        externalUri = uri
        externalTitle = queryDisplayName(uri)
            ?: intent.getStringExtra(Intent.EXTRA_TITLE)
            ?: fallbackFileName(uri)
        externalArtist = ""
        externalAlbum = ""
        externalArtPath = null
        resolvingMetadata = true
        playbackError = null

        metadataJob = lifecycleScope.launch {
            val track = try {
                withContext(Dispatchers.IO) { readExternalTrack(uri, externalTitle) }
            } catch (_: Exception) {
                if (!isFinishing && externalUri == uri) {
                    resolvingMetadata = false
                    playbackError = "Não foi possível ler as tags deste arquivo."
                }
                return@launch
            }
            if (isFinishing || externalUri != uri) return@launch
            externalTitle = track.title
            externalArtist = track.artist
            externalAlbum = track.album
            externalArtPath = track.artPath
            externalTrackId = track.id
            val started = viewModel.playExternalUri(track)
            if (isFinishing || externalUri != uri) return@launch
            resolvingMetadata = false
            if (!started) playbackError = "Não foi possível iniciar a reprodução deste arquivo."
        }
    }

    private fun openNowPlaying(trackId: String?) {
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_OPEN_NOW_PLAYING, true)
            trackId?.let { putExtra(MainActivity.EXTRA_OPEN_NOW_PLAYING_TRACK_ID, it) }
        }
        startActivity(mainIntent)
        finish()
    }

    private fun readExternalTrack(uri: Uri, suggestedTitle: String): Track {
        val displayName = queryDisplayName(uri) ?: fallbackFileName(uri).ifBlank { suggestedTitle }
        val extension = displayName.substringAfterLast('.', "").takeIf { it.isNotBlank() }
            ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(
                try { contentResolver.getType(uri) } catch (_: Exception) { null }
            ).orEmpty()
        val safeExtension = extension.lowercase().filter { it.isLetterOrDigit() }
        val retrieverValues = readWithMediaMetadataRetriever(uri)
        val needsTagReader = safeExtension in setOf("flac", "dsf", "dff", "ogg", "opus", "ape", "wv", "wma") ||
            retrieverValues.title.isNullOrBlank() || retrieverValues.artist.isNullOrBlank()

        var tagValues: TagReader.Tags? = null
        var tagArtwork: ByteArray? = null
        if (needsTagReader) {
            val audioFile = materializeForTagReader(uri, safeExtension)
            if (audioFile != null) {
                try {
                    tagValues = TagReader.read(audioFile)
                    tagArtwork = TagReader.readArtworkBytes(audioFile)
                } catch (_: Exception) {
                } finally {
                    if (uri.scheme != "file") audioFile.delete()
                }
            }
        }

        val title = tagValues?.title?.takeIf { it.isNotBlank() }
            ?: retrieverValues.title?.takeIf { it.isNotBlank() }
            ?: displayName.substringBeforeLast('.', displayName).ifBlank { "Música" }
        val artist = tagValues?.artist?.takeIf { it.isNotBlank() }
            ?: retrieverValues.artist?.takeIf { it.isNotBlank() }
            ?: ""
        val album = tagValues?.album?.takeIf { it.isNotBlank() }
            ?: retrieverValues.album?.takeIf { it.isNotBlank() }
            ?: ""
        val albumArtist = tagValues?.albumArtist?.takeIf { it.isNotBlank() }
            ?: retrieverValues.albumArtist.orEmpty()
        val artworkBytes = retrieverValues.artwork ?: tagArtwork
        val artPath = artworkBytes?.let { storeArtwork(uri, it) }
        val duration = tagValues?.durationMs?.takeIf { it > 0L }
            ?: retrieverValues.durationMs

        return Track(
            id = "external_${uri.toString().hashCode().toUInt().toString(16)}",
            title = title,
            artist = artist,
            album = album,
            albumArtist = albumArtist,
            genre = tagValues?.genre.orEmpty(),
            duration = duration,
            uri = uri,
            path = uri.toString(),
            folder = "Arquivo externo",
            year = tagValues?.year?.take(4)?.toIntOrNull(),
            trackNumber = tagValues?.track?.substringBefore('/')?.toIntOrNull(),
            discNumber = tagValues?.disc?.substringBefore('/')?.toIntOrNull(),
            artPath = artPath
        )
    }

    private data class RetrieverValues(
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val albumArtist: String? = null,
        val durationMs: Long = 0L,
        val artwork: ByteArray? = null
    )

    private fun readWithMediaMetadataRetriever(uri: Uri): RetrieverValues {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(this, uri)
            RetrieverValues(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                artwork = retriever.embeddedPicture
            )
        } catch (_: Exception) {
            RetrieverValues()
        } finally {
            try { retriever.release() } catch (_: Exception) { }
        }
    }

    private fun materializeForTagReader(uri: Uri, extension: String): File? {
        if (uri.scheme == "file") {
            return uri.path?.let(::File)?.takeIf { it.isFile && it.canRead() }
        }
        val safeExtension = extension.takeIf { it.isNotBlank() } ?: "audio"
        val temporary = try {
            File.createTempFile("lyron_external_tags_", ".$safeExtension", cacheDir)
        } catch (_: Exception) {
            return null
        }
        return try {
            contentResolver.openInputStream(uri)?.use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            } ?: run {
                temporary.delete()
                return null
            }
            temporary
        } catch (_: Exception) {
            temporary.delete()
            null
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index)?.takeIf { it.isNotBlank() } else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun fallbackFileName(uri: Uri): String =
        (uri.lastPathSegment ?: "Música")
            .substringAfterLast(':')
            .substringAfterLast('/')
            .ifBlank { "Música" }

    private fun storeArtwork(uri: Uri, bytes: ByteArray): String? {
        return try {
            val key = uri.toString().hashCode().toUInt().toString(16)
            val file = File(cacheDir, "external_cover_$key.img")
            file.writeBytes(bytes)
            file.absolutePath
        } catch (_: Exception) {
            null
        }
    }
}
