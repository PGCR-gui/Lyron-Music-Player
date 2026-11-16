package com.tolete.player

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.ForwardingPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.common.MimeTypes
import com.tolete.player.data.DsfExtractor
import com.tolete.player.visualizer.AudioTapSink
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.tolete.player.data.AudioOutputInfo
import com.tolete.player.data.UsbMixer
import com.tolete.player.data.AudioOutputPrefs
import com.tolete.player.data.FavoritesBridge
import com.tolete.player.data.FavoritesFile
import com.tolete.player.data.HighRatePcmDownsampler
import com.tolete.player.data.LastFmScrobbler
import com.tolete.player.data.LibraryCache
import com.tolete.player.data.LyricsExtractor
import com.tolete.player.data.LyricLine
import com.tolete.player.data.Track
import com.tolete.player.data.ScrobbleEdits
import com.tolete.player.scrobble.ScrobbleNotifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("UnsafeOptInUsageError")
class PlaybackService : MediaSessionService() {

    companion object {
        /** Sessão de áudio do ExoPlayer (para Equalizer nativo). 0 = indisponível. */
        @Volatile
        var currentAudioSessionId: Int = 0
            private set

        /**
         * Referência direta ao ExoPlayer (mesmo processo da UI).
         * Usada para aplicar velocidade de forma confiável no sincronizador.
         */
        @Volatile
        var exoPlayerRef: ExoPlayer? = null
            private set

        /** Aplica velocidade no player ativo. Retorna true se aplicou. */
        @JvmStatic
        fun setSpeed(speed: Float): Boolean {
            val p = exoPlayerRef
            if (p == null) {
                android.util.Log.w("PlaybackService", "setSpeed($speed): exoPlayerRef=null")
                return false
            }
            val s = speed.coerceIn(0.25f, 2f)
            val apply = Runnable {
                try {
                    // Duas formas: API dedicada + PlaybackParameters
                    p.setPlaybackSpeed(s)
                    p.playbackParameters =
                        androidx.media3.common.PlaybackParameters(s, /* pitch= */ 1f)
                    android.util.Log.i(
                        "PlaybackService",
                        "setSpeed ok -> requested=$s actual=${p.playbackParameters.speed}"
                    )
                } catch (e: Exception) {
                    android.util.Log.e("PlaybackService", "setSpeed failed", e)
                }
            }
            return if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                apply.run()
                true
            } else {
                android.os.Handler(android.os.Looper.getMainLooper()).post(apply)
                true
            }
        }

        @JvmStatic
        fun currentSpeed(): Float =
            exoPlayerRef?.playbackParameters?.speed?.takeIf { it > 0f } ?: 1f

        private const val MAX_CONSECUTIVE_ERRORS = 3
        private fun formatSampleRate(hz: Int): String =
            if (hz % 1000 == 0) "${hz / 1000} kHz" else String.format(java.util.Locale.ROOT, "%.1f kHz", hz / 1000.0)
        /** 7 minutos pausado → remove notificação e encerra o serviço. */
        private const val IDLE_STOP_MS = 7 * 60 * 1000L
        /** Oculta somente a notificação do scrobbler após uma pausa longa. */
        private const val SCROBBLE_PAUSE_HIDE_MS = 3 * 60 * 1000L
        const val ACTION_TOGGLE_FAVORITE = "com.tolete.player.TOGGLE_FAVORITE"
        const val ACTION_CLOSE_APP = "com.tolete.player.CLOSE_APP"
        const val ACTION_SET_SPEED = "com.tolete.player.SET_SPEED"
        const val EXTRA_SPEED = "speed"

        /** Preferências de áudio aplicadas na criação do player (para UI técnica). */
        @Volatile var appliedBackend: String = "DEFAULT"
        @Volatile var appliedPreferredSampleRate: Int = 0
        @Volatile var appliedFloatOutput: Boolean = false
        @Volatile var appliedDeviceLabel: String = ""
        @Volatile var appliedConversionInfo: String = ""
        @Volatile var appliedTrackSampleRate: Int = 0
        @Volatile var appliedTrackChannelCount: Int = 0
        @Volatile var appliedTrackEncoding: Int = AudioFormat.ENCODING_INVALID
    }

    private var mediaSession: MediaSession? = null
    private var dedicatedOutputActive = false
    @Volatile private var currentTrackIsDsf = false
    private var deviceCallback: android.media.AudioDeviceCallback? = null
    private var consecutiveErrors = 0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    /** Cancela notificação/serviço após 7 min seguidos sem reprodução. */
    private var idleStopJob: Job? = null
    private var scrobblePauseHideJob: Job? = null
    @Volatile private var scrobbleNotificationHidden = false
    private var lyricsPopupMonitorJob: Job? = null
    /** Incrementado quando o estado do player muda, para redesenhar a notificação do scrobbler. */
    private val scrobbleTick = MutableStateFlow(0)
    /** Índice id -> Track (do cache da biblioteca) para o serviço saber qual faixa está tocando sem depender da UI. */
    @Volatile private var trackIndex: Map<String, Track> = emptyMap()
    private var scrobbleSyncJob: Job? = null

    private val toggleFavoriteCommand = SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY)
    private val closeAppCommand = SessionCommand(ACTION_CLOSE_APP, Bundle.EMPTY)
    private val setSpeedCommand = SessionCommand(ACTION_SET_SPEED, Bundle.EMPTY)

    /** Fade de play/pause e fade-in de faixa nova. */
    private var fadeVolumeJob: Job? = null
    /** Fade de saída no fim da faixa (crossfade). Separado para poder ser cancelado em seek/repetição/troca. */
    private var endFadeJob: Job? = null
    private var endCrossfadeStarted = false
    /** Player auxiliar somente para a janela de crossfade; não pertence à MediaSession nem ao scrobbler. */
    private var crossfadePreviewPlayer: ExoPlayer? = null
    private var crossfadePreviewKey: String? = null
    private var crossfadeFailedKey: String? = null
    private var crossfadeNextKey: String? = null
    private var crossfadeActive = false
    private var crossfadeHandoffInProgress = false
    private var crossfadeMonitorJob: Job? = null
    private var crossfadeMixJob: Job? = null

    private fun prefs() = getSharedPreferences("tolete", MODE_PRIVATE)
    private fun fadeMs(): Int = prefs().getInt("fade_ms", 300).coerceIn(0, 3000)
    private fun crossfadeMs(): Int = prefs().getInt("crossfade_ms", 0).coerceIn(0, 5000)

    private fun rampVolume(
        player: Player,
        from: Float,
        to: Float,
        ms: Int,
        isEndFade: Boolean = false,
        onEnd: (() -> Unit)? = null
    ) {
        // Só um fade escreve em player.volume por vez; senão um fade antigo (ex.: o de saída
        // do fim da faixa) termina depois e deixa o volume em 0 -> reprodução muda.
        fadeVolumeJob?.cancel()
        endFadeJob?.cancel()
        if (ms <= 0) {
            player.volume = to
            onEnd?.invoke()
            return
        }
        val job = scope.launch {
            val steps = (ms / 30).coerceIn(4, 80)
            val start = from.coerceIn(0f, 1f)
            val end = to.coerceIn(0f, 1f)
            for (i in 0..steps) {
                if (!isActive) return@launch
                val f = i.toFloat() / steps
                player.volume = start + (end - start) * f
                delay(ms.toLong() / steps)
            }
            player.volume = end
            onEnd?.invoke()
        }
        if (isEndFade) endFadeJob = job else fadeVolumeJob = job
    }

    /** Cancela o fade de saída do crossfade (seek, repetição, troca de faixa, pausa do sistema). */
    private fun cancelEndFade() {
        endFadeJob?.cancel()
        endFadeJob = null
        endCrossfadeStarted = false
    }

    private fun mediaItemKey(item: MediaItem?): String = item?.mediaId
        ?.takeIf { it.isNotBlank() }
        ?: item?.localConfiguration?.uri?.toString().orEmpty()

    private fun releaseCrossfadePreview() {
        crossfadeMixJob?.cancel()
        crossfadeMixJob = null
        try { crossfadePreviewPlayer?.release() } catch (_: Throwable) { }
        crossfadePreviewPlayer = null
        crossfadePreviewKey = null
        crossfadeNextKey = null
        crossfadeActive = false
        crossfadeHandoffInProgress = false
    }

    private fun cancelCrossfade(player: ExoPlayer, restoreMainVolume: Boolean) {
        val wasActive = crossfadeActive
        releaseCrossfadePreview()
        if (restoreMainVolume && wasActive) player.volume = 1f
    }

    private fun prepareCrossfadePreview(
        item: MediaItem,
        key: String,
        renderersFactory: DefaultRenderersFactory,
        mediaSourceFactory: DefaultMediaSourceFactory,
        audioAttributes: AudioAttributes
    ) {
        releaseCrossfadePreview()
        try {
            val preview = ExoPlayer.Builder(this, renderersFactory)
                .setMediaSourceFactory(mediaSourceFactory)
                .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ false)
                .setHandleAudioBecomingNoisy(false)
                .build()
            preview.volume = 0f
            preview.setMediaItem(item)
            preview.prepare()
            crossfadePreviewPlayer = preview
            crossfadePreviewKey = key
        } catch (t: Throwable) {
            crossfadeFailedKey = key
            android.util.Log.w("PlaybackService", "Não foi possível preparar o próximo áudio para crossfade", t)
            releaseCrossfadePreview()
        }
    }

    private fun beginCrossfade(player: ExoPlayer, preview: ExoPlayer, nextKey: String, durationMs: Long) {
        if (durationMs <= 0L || crossfadeActive) return
        fadeVolumeJob?.cancel()
        endFadeJob?.cancel()
        crossfadeNextKey = nextKey
        crossfadeActive = true
        preview.volume = 0f
        preview.play()
        val startVolume = player.volume.coerceIn(0f, 1f)
        val startedAt = System.currentTimeMillis()
        crossfadeMixJob?.cancel()
        crossfadeMixJob = scope.launch {
            while (isActive && crossfadeActive) {
                // Se o decoder/saída do preview parar durante a sobreposição, mantenha
                // a faixa principal audível em vez de reduzi-la até zero.
                if (!preview.isPlaying || preview.playbackState != Player.STATE_READY) {
                    crossfadeFailedKey = nextKey
                    android.util.Log.w(
                        "PlaybackService",
                        "Crossfade cancelado: preview parou (state=${preview.playbackState}, " +
                            "position=${preview.currentPosition}ms)"
                    )
                    cancelCrossfade(player, restoreMainVolume = true)
                    return@launch
                }
                val fraction = ((System.currentTimeMillis() - startedAt).toFloat() / durationMs)
                    .coerceIn(0f, 1f)
                val angle = fraction * (Math.PI.toFloat() / 2f)
                // Curvas equal-power: a soma percebida permanece estável enquanto as duas faixas se sobrepõem.
                player.volume = startVolume * kotlin.math.cos(angle).coerceIn(0f, 1f)
                preview.volume = kotlin.math.sin(angle).coerceIn(0f, 1f)
                if (fraction >= 1f) break
                delay(16)
            }
            if (crossfadeActive) {
                player.volume = 0f
                preview.volume = 1f
            }
        }
    }

    /** STATE_READY, sozinho, não garante que o player auxiliar começou a produzir áudio. */
    private suspend fun previewHasAudioProgress(preview: ExoPlayer): Boolean {
        val startPosition = preview.currentPosition
        repeat(8) {
            if (preview.playbackState == Player.STATE_READY && preview.isPlaying &&
                preview.currentPosition >= startPosition + 35L
            ) return true
            delay(35)
        }
        return false
    }

    /** Inicia uma sobreposição real apenas quando existe próxima faixa e a saída permite mixagem. */
    private fun startCrossfadeMonitor(
        player: ExoPlayer,
        renderersFactory: DefaultRenderersFactory,
        mediaSourceFactory: DefaultMediaSourceFactory,
        audioAttributes: AudioAttributes,
        allowed: Boolean
    ) {
        crossfadeMonitorJob?.cancel()
        crossfadeMonitorJob = scope.launch {
            var usbConnected = allowed &&
                com.tolete.player.data.UsbAudioRouter.findUsbOutput(this@PlaybackService) != null
            var lastUsbCheckMs = 0L
            while (isActive) {
                val nowMs = System.currentTimeMillis()
                if (allowed && nowMs - lastUsbCheckMs >= 250L) {
                    usbConnected = com.tolete.player.data.UsbAudioRouter.findUsbOutput(this@PlaybackService) != null
                    lastUsbCheckMs = nowMs
                }
                if (crossfadeHandoffInProgress) {
                    delay(16)
                    continue
                }
                val crossfadeDuration = crossfadeMs().toLong()
                val nextIndex = player.nextMediaItemIndex
                val nextItem = if (nextIndex in 0 until player.mediaItemCount) {
                    player.getMediaItemAt(nextIndex)
                } else null
                val currentItem = player.currentMediaItem
                val duration = player.duration
                val remaining = if (duration > 0L) duration - player.currentPosition else Long.MAX_VALUE
                val speedIsNormal = kotlin.math.abs(player.playbackParameters.speed - 1f) < 0.001f
                // Alguns DACs/drivers não mixam duas AudioTracks; não sobreponha áudio USB.
                // O preview é verificado novamente quando a faixa/timer progride.
                val canPrepare = allowed && !usbConnected && crossfadeDuration > 0L && player.isPlaying &&
                    speedIsNormal && currentItem != null && nextItem != null &&
                    !isDsfMediaItem(currentItem) && !isDsfMediaItem(nextItem)

                if (!canPrepare) {
                    if (crossfadeActive) cancelCrossfade(player, restoreMainVolume = true)
                    else if (!crossfadeActive) releaseCrossfadePreview()
                    delay(100)
                    continue
                }
                if (currentItem == null || nextItem == null) {
                    delay(100)
                    continue
                }
                val activeItem = currentItem
                val upcomingItem = nextItem

                val nextKey = mediaItemKey(upcomingItem)
                if (nextKey.isBlank() || nextKey == crossfadeFailedKey) {
                    if (!crossfadeActive) releaseCrossfadePreview()
                    delay(100)
                    continue
                }
                if (crossfadeActive && mediaItemKey(activeItem) == crossfadeNextKey) {
                    delay(16)
                    continue
                }
                if (!crossfadeActive && remaining in 1..(crossfadeDuration + 1_600L) &&
                    crossfadePreviewKey != nextKey
                ) {
                    prepareCrossfadePreview(
                        upcomingItem,
                        nextKey,
                        renderersFactory,
                        mediaSourceFactory,
                        audioAttributes
                    )
                }
                if (!crossfadeActive && remaining in 1..crossfadeDuration) {
                    val preview = crossfadePreviewPlayer
                    if (preview?.playbackState == Player.STATE_READY &&
                        crossfadePreviewKey == nextKey &&
                        preview.duration > crossfadeDuration
                    ) {
                        // Começa a prévia com volume zero; somente após comprovar avanço
                        // reduziremos o volume da faixa atual.
                        preview.play()
                        val previewAdvanced = previewHasAudioProgress(preview)
                        val stillSameTrack = mediaItemKey(player.currentMediaItem) == mediaItemKey(activeItem)
                        val remainingNow = player.duration - player.currentPosition
                        if (previewAdvanced && stillSameTrack && player.isPlaying && remainingNow > 0L) {
                            beginCrossfade(player, preview, nextKey, minOf(crossfadeDuration, remainingNow))
                        } else if (!previewAdvanced) {
                            crossfadeFailedKey = nextKey
                            android.util.Log.w(
                                "PlaybackService",
                                "Crossfade omitido: preview sem progresso; mantendo volume da faixa atual"
                            )
                            releaseCrossfadePreview()
                        } else {
                            releaseCrossfadePreview()
                        }
                    } else if (preview?.playbackState == Player.STATE_READY &&
                        preview.duration in 1..crossfadeDuration
                    ) {
                        crossfadeFailedKey = nextKey
                        releaseCrossfadePreview()
                    } else if (preview?.playbackState == Player.STATE_IDLE) {
                        crossfadeFailedKey = nextKey
                        releaseCrossfadePreview()
                    }
                }
                if (!crossfadeActive && remaining > crossfadeDuration + 1_600L &&
                    crossfadePreviewPlayer != null
                ) {
                    releaseCrossfadePreview()
                }
                delay(if (crossfadeActive) 16 else 80)
            }
        }
    }

    /** Troca para a faixa principal já avançada pelo trecho ouvido no player auxiliar. */
    private fun completeCrossfadeHandoff(player: ExoPlayer, mediaItem: MediaItem?): Boolean {
        val preview = crossfadePreviewPlayer ?: return false
        if (!crossfadeActive || mediaItemKey(mediaItem) != crossfadeNextKey) return false
        val targetPosition = preview.currentPosition.coerceAtLeast(0L)
        crossfadeMixJob?.cancel()
        crossfadeMixJob = null
        crossfadeHandoffInProgress = true
        player.volume = 0f
        player.seekTo(targetPosition)
        scope.launch {
            var waited = 0L
            while (isActive && waited < 400L &&
                kotlin.math.abs(player.currentPosition - targetPosition) > 120L
            ) {
                delay(20)
                waited += 20L
            }
            if (mediaItemKey(player.currentMediaItem) == mediaItemKey(mediaItem)) {
                preview.volume = 0f
                rampVolume(player, 0f, 1f, 100)
            } else {
                player.volume = 1f
            }
            try { preview.release() } catch (_: Throwable) { }
            crossfadePreviewPlayer = null
            crossfadePreviewKey = null
            crossfadeNextKey = null
            crossfadeActive = false
            crossfadeHandoffInProgress = false
        }
        return true
    }

    private val sessionCallback = object : MediaSession.Callback {
        // Aceita qualquer controlador (shade do sistema, Bluetooth, outros apps, Android Auto…)
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .add(toggleFavoriteCommand)
                .add(closeAppCommand)
                .add(setSpeedCommand)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ACTION_TOGGLE_FAVORITE -> {
                    session.player.currentMediaItem?.mediaId?.let { toggleFavorite(it) }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                ACTION_CLOSE_APP -> {
                    // Para a reprodução, encerra a sessão e o serviço (fecha o player em segundo plano)
                    scope.launch {
                        session.player.stop()
                        session.player.clearMediaItems()
                        stopSelf()
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                ACTION_SET_SPEED -> {
                    val speed = args.getFloat(EXTRA_SPEED, 1f).coerceIn(0.25f, 2f)
                    try {
                        session.player.setPlaybackSpeed(speed)
                    } catch (_: Exception) { }
                    session.player.playbackParameters =
                        androidx.media3.common.PlaybackParameters(speed, /* pitch= */ 1f)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
        }
    }

    override fun onCreate() {
        super.onCreate()

        val prefs = getSharedPreferences("tolete", MODE_PRIVATE)
        // Saída de áudio escolhida em Configurações > Player:
        //  • Padrão do sistema: o Android decide a rota e o formato.
        // Fidelidade preserva a taxa da fonte (com o ajuste de compatibilidade 44,1→48 kHz).
        val fidelityMode = AudioOutputPrefs.isFidelity(prefs)
        val dedicatedOutput = fidelityMode
        val resample44100To48000 = fidelityMode
        val bitPerfect = fidelityMode && prefs.getBoolean("bit_perfect", false)
        val preferredSr = 0
        val backendName = when {
            fidelityMode -> AudioOutputPrefs.FIDELITY
            else -> AudioOutputPrefs.DEFAULT
        }
        // Float PCM: ajuda em caminhos hi-res e evita quantização extra em 24/32-bit
        val wantFloat = !bitPerfect && dedicatedOutput
        appliedBackend = backendName
        appliedPreferredSampleRate = preferredSr
        appliedFloatOutput = wantFloat
        android.util.Log.i(
            "PlaybackService",
            "Áudio: backend=$backendName preferredSr=${preferredSr}Hz float=$wantFloat bitPerfect=$bitPerfect"
        )

        // Visualizador .milk: o AudioTapSink (em buildAudioSink) só "espia" o PCM, sem alterar
        // o áudio nem o bit-perfect. Custa ~zero enquanto AudioTap.active == false.
        // Extensão FFmpeg local: decodifica codecs que o MediaCodec do aparelho
        // não cobre (ex.: DSD/DSF, alguns FLAC/Opus/WMA). Preferência: software FFmpeg antes do decoder nativo.
        if (FfmpegLibrary.isAvailable()) {
            android.util.Log.i("PlaybackService", "FFmpeg decoder disponível: ${FfmpegLibrary.getVersion()}")
        } else {
            android.util.Log.w("PlaybackService", "FFmpeg decoder indisponível neste ABI")
        }

        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioOutputPlaybackParameters: Boolean
            ): AudioSink? {
                val usbExclusive = dedicatedOutput
                val outDevice = if (dedicatedOutput) AudioOutputInfo.findPreferredOutput(context) else null
                val rateDevice = outDevice ?: AudioOutputInfo.activeDevice(context)
                appliedDeviceLabel = if (outDevice != null) AudioOutputInfo.describeDevice(outDevice) else ""
                val supportedOutputSampleRatesProvider = {
                    try {
                        val advertisedRates = buildSet {
                            addAll(rateDevice?.sampleRates?.filter { it > 0 }?.toSet().orEmpty())
                            if (usbExclusive && outDevice != null) {
                                addAll(UsbMixer.supportedMixerSampleRates(context, outDevice))
                            }
                        }
                        if (advertisedRates.isNotEmpty()) {
                            advertisedRates
                        } else {
                            val mixerRate = try { AudioOutputInfo.probe(context).sampleRateHz } catch (_: Throwable) { 0 }
                            setOf(mixerRate.takeIf { it > 0 } ?: 48_000)
                        }
                    } catch (_: Throwable) {
                        setOf(48_000)
                    }
                }
                // Float: hi-res, USB DAC ou backends explícitos.
                // CORREÇÃO: manter float também em bit-perfect. O Media3 só preserva
                // 24/32 bits com a saída float ligada; sem ela ele converte hi-res para
                // 16 bits ANTES de chegar ao DAC. Um int24 cabe inteiro na mantissa de
                // um float32, então nada é perdido no caminho.
                val useFloat = enableFloatOutput || wantFloat || usbExclusive || bitPerfect
                appliedFloatOutput = useFloat
                val builder = DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(useFloat)
                    .setAudioProcessors(arrayOf(
                        HighRatePcmDownsampler(
                            resample44100To48000 = resample44100To48000,
                            dsfSourceProvider = { currentTrackIsDsf },
                            forceStereoProvider = { channels ->
                                !routeSupportsMultichannel(context, channels)
                            },
                            supportedOutputSampleRatesProvider = supportedOutputSampleRatesProvider
                        )
                    ))
                    .setEnableAudioOutputPlaybackParameters(true)
                android.util.Log.i(
                    "PlaybackService",
                    "AudioSink: mode=$backendName device=${appliedDeviceLabel.ifBlank { "-" }} float=$useFloat"
                )
                val sink = builder.build()
                // AudioTapSink (visualizador .milk) fica por fora de tudo e só espia o PCM.
                // Fidelidade tenta alinhar o mixer USB à taxa PCM efetivamente selecionada.
                val appCtx = context.applicationContext
                return AudioTapSink(object : ForwardingAudioSink(sink) {
                    // Media3 1.11: o override agora é configure(AudioSinkConfig)
                    override fun configure(audioSinkConfig: AudioSink.AudioSinkConfig) {
                        val inputFormat = audioSinkConfig.format
                        try {
                            if (MimeTypes.AUDIO_RAW == inputFormat.sampleMimeType &&
                                inputFormat.sampleRate > 0
                            ) {
                                val pcm = inputFormat.pcmEncoding
                                val inputChannels = audioSinkConfig.outputChannelMapping?.length()?.takeIf { it > 0 }
                                    ?: inputFormat.channelCount
                                val dsf = currentTrackIsDsf
                                val forceStereo = dsf && inputChannels > 2 &&
                                    !routeSupportsMultichannel(appCtx, inputChannels)
                                val channels = HighRatePcmDownsampler.outputChannelCount(
                                    inputFormat.sampleRate,
                                    inputChannels,
                                    forceStereo
                                )
                                val outputSampleRate = HighRatePcmDownsampler.outputSampleRate(
                                    inputFormat.sampleRate,
                                    channels,
                                    pcm,
                                    resample44100To48000,
                                    dsf,
                                    supportedOutputSampleRatesProvider()
                                )
                                val sourceBits = when (pcm) {
                                    C.ENCODING_PCM_8BIT -> 8
                                    C.ENCODING_PCM_16BIT -> 16
                                    C.ENCODING_PCM_24BIT -> 24
                                    C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 32
                                    else -> 0
                                }
                                val outputBits = when {
                                    pcm == C.ENCODING_PCM_24BIT || pcm == C.ENCODING_PCM_32BIT ||
                                        pcm == C.ENCODING_PCM_FLOAT -> if (useFloat) 32 else 16
                                    pcm == C.ENCODING_PCM_8BIT -> if (useFloat) 32 else 16
                                    else -> sourceBits
                                }
                                val conversions = mutableListOf<String>()
                                if (sourceBits > 0 && outputBits > sourceBits) {
                                    conversions += "${sourceBits}-bit→${outputBits}-bit"
                                }
                                if (inputFormat.sampleRate != outputSampleRate) {
                                    conversions += "${formatSampleRate(inputFormat.sampleRate)}→${formatSampleRate(outputSampleRate)}"
                                }
                                if (dsf && inputFormat.sampleRate > 0) conversions += "FIR DSF · filtro anti-ruído"
                                if (forceStereo) conversions += "${inputChannels}ch→2ch"
                                appliedConversionInfo = conversions.joinToString(" · ").ifBlank {
                                    "Sem conversão adicional para esta faixa"
                                }
                                val highRes = pcm == C.ENCODING_PCM_24BIT ||
                                    pcm == C.ENCODING_PCM_32BIT || pcm == C.ENCODING_PCM_FLOAT
                                val trackEncoding = when {
                                    highRes && useFloat -> AudioFormat.ENCODING_PCM_FLOAT
                                    highRes -> AudioFormat.ENCODING_PCM_16BIT
                                    pcm == C.ENCODING_PCM_8BIT -> AudioFormat.ENCODING_PCM_8BIT
                                    else -> AudioFormat.ENCODING_PCM_16BIT
                                }
                                appliedTrackSampleRate = outputSampleRate
                                appliedTrackChannelCount = channels
                                appliedTrackEncoding = trackEncoding
                                if (usbExclusive) {
                                    com.tolete.player.data.UsbMixer.applyForTrack(
                                        appCtx,
                                        sampleRate = outputSampleRate,
                                        channelCount = channels,
                                        encoding = trackEncoding,
                                        preferredHz = preferredSr,
                                        bitPerfect = bitPerfect && inputFormat.sampleRate == outputSampleRate
                                    )
                                }
                                android.util.Log.i(
                                    "PlaybackService",
                                    "AudioSink.configure source=${inputFormat.sampleRate}Hz/" +
                                        "${inputChannels}ch/enc=${pcm} dsf=$dsf -> " +
                                        "${outputSampleRate}Hz/${channels}ch/enc=$trackEncoding " +
                                        "route=${appliedDeviceLabel.ifBlank { "system" }}"
                                )
                            }
                            super.configure(audioSinkConfig)
                        } catch (t: Throwable) {
                            android.util.Log.e(
                                "PlaybackService",
                                "AudioSink.configure failed input=${inputFormat.sampleRate}Hz/" +
                                    "${inputFormat.channelCount}ch/enc=${inputFormat.pcmEncoding} " +
                                    "mime=${inputFormat.sampleMimeType} dsf=$currentTrackIsDsf " +
                                    "output=${appliedTrackSampleRate}Hz/" +
                                    "${appliedTrackChannelCount}ch/enc=${appliedTrackEncoding}",
                                t
                            )
                            throw t
                        }
                    }
                })
            }

            // Media3 1.11 (assinatura igual à 1.3): (context, extensionRendererMode, mediaCodecSelector,
            // enableDecoderFallback, audioSink, eventHandler, eventListener, out)
            override fun buildAudioRenderers(
                context: Context,
                extensionRendererMode: Int,
                mediaCodecSelector: MediaCodecSelector,
                enableDecoderFallback: Boolean,
                audioSink: AudioSink,
                eventHandler: android.os.Handler,
                eventListener: AudioRendererEventListener,
                out: java.util.ArrayList<androidx.media3.exoplayer.Renderer>
            ) {
                if (FfmpegLibrary.isAvailable()) {
                    out.add(FfmpegAudioRenderer(eventHandler, eventListener, audioSink))
                }
                super.buildAudioRenderers(
                    context,
                    extensionRendererMode,
                    mediaCodecSelector,
                    enableDecoderFallback,
                    audioSink,
                    eventHandler,
                    eventListener,
                    out
                )
            }
        }
        // Preferir extensão quando disponível (ordem de renderers já coloca FFmpeg primeiro)
        renderersFactory.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        val mediaSourceFactory = DefaultMediaSourceFactory(
            this,
            ExtractorsFactory {
                arrayOf(DsfExtractor(), *DefaultExtractorsFactory().createExtractors())
            }
        )
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val player = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(
                audioAttributes,
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true) // pausa ao desconectar o fone
            .setWakeMode(C.WAKE_MODE_LOCAL)
            // Media3 1.11 ligou o "dynamic scheduling" por padrão (era desligado na 1.10.1).
            // Nele, getCurrentPosition() fica defasado em até ~200 ms e atualiza em degraus
            // (androidx/media#3286, só corrigido na 1.12). Como o karaokê lê a posição a cada
            // ~48 ms, a palavra enchia, esvaziava e enchia de novo, e a letra atrasava.
            // Voltar ao agendamento fixo restaura a posição contínua da versão anterior.
            .experimentalSetDynamicSchedulingEnabled(false)
            .build()

        currentAudioSessionId = player.audioSessionId
        exoPlayerRef = player

        // Fidelidade roteia ao DAC USB (ou ao fone com fio) e reage a conectar/desconectar.
        // Também escuta troca fone↔alto-falante no modo normal para evitar pitch/velocidade errados.
        dedicatedOutputActive = dedicatedOutput
        if (dedicatedOutput) {
            applyOutputRouting(player)
        }
        val cb = object : android.media.AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                if (dedicatedOutputActive) applyOutputRouting(player)
                onAudioRouteChanged(player)
            }
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                if (dedicatedOutputActive) applyOutputRouting(player)
                onAudioRouteChanged(player)
            }
        }
        deviceCallback = cb
        (getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager)
            .registerAudioDeviceCallback(cb, android.os.Handler(android.os.Looper.getMainLooper()))

        // Bit-perfect: evita processamento extra (skip silence, etc.)
        // Nota: velocidade do sincronizador ainda pode alterar playbackParameters sob demanda.
        player.skipSilenceEnabled = false
        if (bitPerfect) {
            player.playbackParameters = androidx.media3.common.PlaybackParameters.DEFAULT
        }

        // Watchdog: alguns FLAC reportam duração maior que o áudio real e o player
        // fica “tocando” silêncio nos últimos segundos sem emitir STATE_ENDED.
        startEndOfTrackWatchdog(player)

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) consecutiveErrors = 0
                // Atualiza sessão de áudio (pode mudar após prepare/play)
                val sid = player.audioSessionId
                if (sid != 0 && sid != currentAudioSessionId) {
                    currentAudioSessionId = sid
                }
                scheduleIdleStopIfNeeded(isPlaying)
                scheduleScrobbleNotificationVisibility(effectivelyPlaying(player))
                // Trocou de faixa durante a pausa? Garante que a notificação mostre a faixa certa ao voltar.
                syncScrobbleCurrent(newPlay = false)
                scrobbleTick.value++
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val playing = player.isPlaying
                scheduleIdleStopIfNeeded(playing)
                scheduleScrobbleNotificationVisibility(effectivelyPlaying(player))
                scrobbleTick.value++
                // Fim real da faixa: avança (ou repete) em vez de ficar em silêncio no final
                if (playbackState == Player.STATE_ENDED) {
                    handleTrackEnded(player)
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                scheduleScrobbleNotificationVisibility(effectivelyPlaying(player))
                scrobbleTick.value++
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentTrackIsDsf = isDsfMediaItem(mediaItem)
                android.util.Log.i(
                    "PlaybackService",
                    "Faixa ativa DSF/DSD=$currentTrackIsDsf uri=${mediaItem?.localConfiguration?.uri}"
                )
                refreshCustomButtons()
                // Nova faixa (ou repetição): atualiza a faixa atual do scrobbler agora, tocando ou pausado.
                syncScrobbleCurrent(newPlay = true)
                scrobbleTick.value++
                val sid = player.audioSessionId
                if (sid != 0) currentAudioSessionId = sid
                // O fade de saída da faixa anterior NÃO pode continuar rodando na faixa nova
                // (nem ao repetir a mesma faixa): ele terminava em volume 0 e deixava tudo mudo.
                cancelEndFade()
                if (mediaItemKey(mediaItem) == crossfadeFailedKey) crossfadeFailedKey = null
                val cf = crossfadeMs()
                val crossfaded = (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) &&
                    completeCrossfadeHandoff(player, mediaItem)
                if (crossfaded) {
                    // A faixa auxiliar já tocou o início e o player da sessão assume na posição equivalente.
                } else if (cf > 0 &&
                    (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                        reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)
                ) {
                    // Fallback curto quando a sobreposição não foi possível (ex.: saída exclusiva/DSF).
                    player.volume = 0f
                    rampVolume(player, 0f, 1f, minOf(cf, 350))
                } else if (player.volume < 0.99f && fadeVolumeJob?.isActive != true) {
                    if (crossfadePreviewPlayer != null || crossfadeActive) {
                        cancelCrossfade(player, restoreMainVolume = false)
                    }
                    player.volume = 1f
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                // Voltou/avançou a faixa com o fade de saída em andamento: cancela e restaura o volume.
                if (reason == Player.DISCONTINUITY_REASON_SEEK ||
                    reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
                ) {
                    if (!crossfadeHandoffInProgress && (crossfadeActive || crossfadePreviewPlayer != null)) {
                        cancelCrossfade(player, restoreMainVolume = true)
                    }
                    if (endFadeJob?.isActive == true || endCrossfadeStarted) {
                        cancelEndFade()
                        if (fadeVolumeJob?.isActive != true) player.volume = 1f
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                handlePlayerError(player, error)
            }
        })
        startLyricsPopupMonitor(player)
        scheduleScrobbleNotificationVisibility(effectivelyPlaying(player))

        // Intent que abre o app ao tocar na notificação (fora dos botões de ação)
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Player com fade play/pause (notificação, Bluetooth, UI via MediaController)
        val fadingPlayer = object : ForwardingPlayer(player) {
            override fun play() {
                val ms = fadeMs()
                if (ms <= 0) {
                    player.volume = 1f
                    super.play()
                    return
                }
                player.volume = 0f
                super.play()
                rampVolume(player, 0f, 1f, ms)
            }

            override fun pause() {
                val ms = fadeMs()
                val mainVolume = player.volume.coerceIn(0f, 1f)
                val previewVolume = crossfadePreviewPlayer?.volume?.coerceIn(0f, 1f) ?: 0f
                val start = if (crossfadeActive) {
                    kotlin.math.sqrt((mainVolume * mainVolume + previewVolume * previewVolume).toDouble())
                        .toFloat().coerceIn(0.01f, 1f)
                } else mainVolume.coerceIn(0.01f, 1f)
                cancelCrossfade(player, restoreMainVolume = false)
                player.volume = start
                if (ms <= 0) {
                    super.pause()
                    player.volume = 1f
                    return
                }
                rampVolume(player, start, 0f, ms) {
                    super.pause()
                    player.volume = 1f
                }
            }

            override fun setPlayWhenReady(playWhenReady: Boolean) {
                if (playWhenReady == this.playWhenReady) {
                    super.setPlayWhenReady(playWhenReady)
                    return
                }
                if (playWhenReady) play() else pause()
            }
        }

        // Ícone pequeno da notificação de mídia: a lira do Lyron (silhueta monocromática).
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().also {
                it.setSmallIcon(R.drawable.ic_notif_lyre)
            }
        )

        mediaSession = MediaSession.Builder(this, fadingPlayer)
            .setCallback(sessionCallback)
            .setSessionActivity(sessionActivity)
            .build()

        // O preview toca em paralelo no mixer do Android. Em saída exclusiva/bit-perfect e DSF,
        // mantém-se o fallback de fade-in para evitar duas AudioTracks incompatíveis no DAC.
        startCrossfadeMonitor(
            player,
            renderersFactory,
            mediaSourceFactory,
            audioAttributes,
            allowed = !dedicatedOutput && !bitPerfect
        )

        // Qualquer mudança nos favoritos (no app ou aqui) redesenha o ícone
        scope.launch {
            FavoritesBridge.version.collect { refreshCustomButtons() }
        }

        // Notificação persistente do scrobbler (música/artista + pular, editar tags, favoritar)
        LastFmScrobbler.init(this)
        scope.launch {
            combine(
                LastFmScrobbler.current,
                LastFmScrobbler.enabled,
                ScrobbleEdits.version,
                FavoritesBridge.version,
                LastFmScrobbler.loveVersion
            ) { _, _, _, _, _ -> }
                .combine(LastFmScrobbler.scrobbledId) { _, _ -> }
                .combine(scrobbleTick) { _, _ -> }
                .collectLatest {
                    if (scrobbleNotificationHidden) {
                        ScrobbleNotifier.cancel(applicationContext)
                        return@collectLatest
                    }
                    val before = LastFmScrobbler.current.value
                    val fav = if (before != null) withContext(Dispatchers.IO) { isFavorite(before.id) } else false
                    // Tudo abaixo é lido DEPOIS do acesso ao disco, para nunca mostrar estado velho.
                    val track = LastFmScrobbler.current.value
                    val p = exoPlayerRef
                    ScrobbleNotifier.show(
                        applicationContext,
                        track,
                        p != null && effectivelyPlaying(p),
                        if (track?.id == before?.id) fav else false,
                        scrobbled = track != null && LastFmScrobbler.scrobbledId.value == track.id
                    )
                }
        }
        // Estado inicial (serviço recriado com o player já numa faixa)
        scope.launch {
            syncScrobbleCurrent(newPlay = false)
        }

        // A sobreposição só começa quando o monitor confirma que há próxima faixa e ela está pronta;
        // assim a faixa atual não é cortada nem fica muda quando a fila termina.
    }

    // ---------- Faixa atual do scrobbler (fonte da verdade = player do serviço) ----------

    private suspend fun resolveTrack(id: String): Track? {
        trackIndex[id]?.let { return it }
        // Não achou: recarrega o cache da biblioteca (pode ter sido atualizado por um novo scan).
        val list = withContext(Dispatchers.IO) { LibraryCache(applicationContext).load() }
        if (list != null) trackIndex = list.associateBy { it.id }
        return trackIndex[id]
    }

    /**
     * Mantém LastFmScrobbler.current igual à faixa que o player está tocando, mesmo com o player
     * pausado e mesmo com a tela do app fechada. [newPlay] = nova reprodução (zera o "Enviado").
     */
    private fun syncScrobbleCurrent(newPlay: Boolean) {
        val id = exoPlayerRef?.currentMediaItem?.mediaId
        if (!newPlay && id == LastFmScrobbler.current.value?.id) return
        scrobbleSyncJob?.cancel()
        scrobbleSyncJob = scope.launch {
            val track = if (id == null) null else resolveTrack(id)
            LastFmScrobbler.setCurrent(track, newPlay)
            if (track != null && exoPlayerRef?.isPlaying == true) LastFmScrobbler.nowPlaying(track)
            scrobbleTick.value++
        }
    }

    /** "Tocando" de verdade: inclui o buffering logo após play/troca (isPlaying fica false nesse instante). */
    private fun effectivelyPlaying(p: ExoPlayer): Boolean =
        p.isPlaying || (p.playWhenReady && p.playbackState == Player.STATE_BUFFERING)

    /** Esconde/reexibe o aviso sem interromper o scrobbling nem encerrar o serviço de mídia. */
    private fun scheduleScrobbleNotificationVisibility(isPlaying: Boolean) {
        if (isPlaying) {
            scrobblePauseHideJob?.cancel()
            scrobblePauseHideJob = null
            if (scrobbleNotificationHidden) {
                scrobbleNotificationHidden = false
                scrobbleTick.value++
            }
            return
        }
        if (scrobbleNotificationHidden || scrobblePauseHideJob?.isActive == true) return
        scrobblePauseHideJob = scope.launch {
            delay(SCROBBLE_PAUSE_HIDE_MS)
            val player = exoPlayerRef ?: return@launch
            if (!effectivelyPlaying(player)) {
                scrobbleNotificationHidden = true
                ScrobbleNotifier.cancel(applicationContext)
            }
        }
    }

    /** A leitura das tags ocorre uma vez por faixa, em IO; o overlay só é exibido enquanto toca. */
    private fun startLyricsPopupMonitor(player: ExoPlayer) {
        lyricsPopupMonitorJob?.cancel()
        lyricsPopupMonitorJob = scope.launch {
            var cachedId: String? = null
            var cachedLines: List<LyricLine> = emptyList()
            var lastNotifShown = false
            var lastLocalReloadMs = 0L
            var lastNetworkFetchMs = 0L
            var networkFetchForId: String? = null
            // Gravar tags em um m4a que o ExoPlayer está lendo desloca o áudio no arquivo e causa
            // ERROR_CODE_DECODING_FAILED (a faixa é pulada). Por isso a gravação nas tags da faixa
            // em reprodução fica pendente até a faixa mudar (ou o player parar).
            var pendingEmbedTrack: Track? = null
            var pendingEmbedLines: List<LyricLine> = emptyList()
            try {
                while (isActive) {
                    val prefs = getSharedPreferences("tolete", Context.MODE_PRIVATE)
                    pendingEmbedTrack?.let { pending ->
                        if (player.currentMediaItem?.mediaId != pending.id) {
                            val lines = pendingEmbedLines
                            pendingEmbedTrack = null
                            pendingEmbedLines = emptyList()
                            withContext(Dispatchers.IO) {
                                try {
                                    com.tolete.player.data.LyricsEmbedder.save(
                                        applicationContext, pending, lines,
                                        com.tolete.player.data.LyricsEmbedder.Target.EMBEDDED
                                    )
                                } catch (_: Exception) { }
                            }
                        }
                    }
                    val enabled = prefs.getBoolean("lyrics_popup_enabled", false)
                    val appIsVisible = ProcessLifecycleOwner.get().lifecycle.currentState
                        .isAtLeast(Lifecycle.State.STARTED)
                    val permitted = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M ||
                        Settings.canDrawOverlays(this@PlaybackService)
                    val id = player.currentMediaItem?.mediaId
                    if (!enabled || !permitted || appIsVisible) {
                        LyricsPopupOverlay.hide()
                        if (lastNotifShown) {
                            LyricsPopupNotifier.cancel(applicationContext)
                            lastNotifShown = false
                        }
                        if (id == null) {
                            cachedId = null
                            cachedLines = emptyList()
                        }
                    } else if (!effectivelyPlaying(player) || id == null) {
                        LyricsPopupOverlay.hide()
                        // Mantém a notificação de controle enquanto o popup estiver habilitado
                        if (!lastNotifShown) {
                            LyricsPopupNotifier.show(applicationContext)
                            lastNotifShown = true
                        }
                        if (id == null) {
                            cachedId = null
                            cachedLines = emptyList()
                        }
                    } else {
                        val now = System.currentTimeMillis()
                        val trackChanged = id != cachedId
                        if (trackChanged) {
                            cachedId = id
                            cachedLines = emptyList()
                            lastLocalReloadMs = 0L
                            networkFetchForId = null
                        }
                        // Recarrega do disco a cada ~1,5s (pega inserção manual / auto-fetch do NP)
                        // e tenta rede se ainda vazio e auto-fetch ligado.
                        val shouldReloadLocal = trackChanged || now - lastLocalReloadMs >= 1_500L
                        if (shouldReloadLocal) {
                            lastLocalReloadMs = now
                            val track = try { resolveTrack(id) } catch (_: Exception) { null }
                            if (track != null) {
                                val local = withContext(Dispatchers.IO) {
                                    LyricsExtractor.load(applicationContext, track)
                                }
                                if (local.isNotEmpty()) {
                                    cachedLines = local
                                } else if (
                                    prefs.getBoolean("auto_fetch_lyrics", false) &&
                                    pendingEmbedTrack?.id != id &&
                                    (networkFetchForId != id || now - lastNetworkFetchMs >= 12_000L)
                                ) {
                                    networkFetchForId = id
                                    lastNetworkFetchMs = now
                                    val fetched = withContext(Dispatchers.IO) {
                                        try {
                                            val results = com.tolete.player.data.LyricsSearch.search(
                                                track.title,
                                                track.artist,
                                                prefs.getBoolean("prefer_netease_romanization", false)
                                            )
                                            fun norm(s: String) = java.text.Normalizer
                                                .normalize(s.trim().replace(Regex("\\s+"), " "), java.text.Normalizer.Form.NFC)
                                                .lowercase(java.util.Locale.ROOT)
                                                .filter { it.isLetterOrDigit() || it.isWhitespace() }
                                                .replace(Regex("\\s+"), " ")
                                                .trim()
                                            val wt = norm(track.title)
                                            val wa = norm(track.artist)
                                            val exact = results.filter {
                                                norm(it.trackName) == wt && norm(it.artistName) == wa
                                            }
                                            val typeOrder = prefs.getString("lyrics_type_order", null)
                                                ?.split('|')?.filter { it.isNotBlank() }
                                                ?: listOf("WORD", "SYNCED", "PLAIN")
                                            val sourceOrder = prefs.getString("lyrics_source_order", null)
                                                ?.split('|')?.filter { it.isNotBlank() }
                                                ?: listOf("LYRON_REPOSITORY", "LRCLIB", "QQ_MUSIC", "NETEASE", "KUGOU")
                                            val preferRoman = prefs.getBoolean("prefer_netease_romanization", false)
                                            val best = exact.minWithOrNull(
                                                compareBy<com.tolete.player.data.LyricsSearchResult> {
                                                    if (it.source != com.tolete.player.data.LyricsSource.NETEASE || it.variant == null) 0
                                                    else if ((it.variant == com.tolete.player.data.LyricsVariant.ROMANIZED) == preferRoman) 0 else 1
                                                }.thenBy {
                                                    typeOrder.indexOf(it.type.name).let { rank -> if (rank < 0) 99 else rank }
                                                }.thenBy {
                                                    sourceOrder.indexOf(it.source.name).let { rank -> if (rank < 0) 99 else rank }
                                                }
                                            )
                                            if (best != null && best.lines.isNotEmpty()) {
                                                val stored = com.tolete.player.data.LyricsEmbedder.hasExternalLrc(track) ||
                                                    com.tolete.player.data.LyricsExtractor.loadEmbedded(applicationContext, track).isNotEmpty()
                                                if (stored) return@withContext com.tolete.player.data.LyricsExtractor.load(applicationContext, track)
                                                val rawTarget = prefs.getString("auto_fetch_lyrics_target", "LRC_FILE")
                                                val target = runCatching {
                                                    com.tolete.player.data.LyricsEmbedder.Target.valueOf(rawTarget ?: "LRC_FILE")
                                                }.getOrDefault(com.tolete.player.data.LyricsEmbedder.Target.LRC_FILE)
                                                val saved = target == com.tolete.player.data.LyricsEmbedder.Target.LRC_FILE &&
                                                    com.tolete.player.data.LyricsEmbedder.save(
                                                        applicationContext, track, best.lines, target
                                                    )
                                                if (!saved) {
                                                    // Destino = tags (ou .lrc sem permissão): adia a gravação
                                                    // para depois que esta faixa deixar de tocar.
                                                    pendingEmbedTrack = track
                                                    pendingEmbedLines = best.lines
                                                }
                                                best.lines
                                            } else emptyList()
                                        } catch (_: Exception) {
                                            emptyList()
                                        }
                                    }
                                    if (fetched.isNotEmpty()) cachedLines = fetched
                                }
                            }
                        }
                        if (cachedLines.isEmpty()) {
                            LyricsPopupOverlay.hide()
                        } else {
                            val position = player.currentPosition.coerceAtLeast(0L)
                            val mainIdx = cachedLines.indexOfLast {
                                !it.isTranslation && !it.isBackground && it.timeMs >= 0L && it.timeMs <= position
                            }.takeIf { it >= 0 }
                                ?: cachedLines.indexOfFirst { !it.isTranslation && !it.isBackground }
                                    .takeIf { it >= 0 }
                            val mainLine = mainIdx?.let { cachedLines[it] }
                            if (mainLine == null || mainLine.text.isBlank()) {
                                LyricsPopupOverlay.hide()
                            } else {
                                val themeId = prefs.getString(LyricsPopupOverlay.KEY_THEME, LyricsPopupOverlay.PopupTheme.DYNAMIC.id)
                                ?: LyricsPopupOverlay.PopupTheme.DYNAMIC.id
                            val themeColors = LyricsPopupOverlay.themeTextColors(themeId, prefs)
                            val primary = if (themeId == LyricsPopupOverlay.PopupTheme.DYNAMIC.id)
                                prefs.getInt("lyrics_primary_color", 0xFFFFFFFF.toInt()) else themeColors.first
                            val secondary = if (themeId == LyricsPopupOverlay.PopupTheme.DYNAMIC.id)
                                prefs.getInt("lyrics_secondary_color", 0x66FFFFFF) else themeColors.second
                            val trColor = if (themeId == LyricsPopupOverlay.PopupTheme.DYNAMIC.id)
                                prefs.getInt("lyrics_translation_color", 0xFFB0BEC5.toInt()) else themeColors.third
                            val bgColor = prefs.getInt("lyrics_backing_color", 0xFFCE93D8.toInt())
                            val fontSize = prefs.getFloat(
                                LyricsPopupOverlay.KEY_FONT_SIZE,
                                prefs.getFloat("lyrics_font_size", 22f)
                            ).coerceIn(12f, 40f)
                            val mainTf = LyricsPopupOverlay.resolveTypeface(applicationContext, prefs.getString("lyrics_font_path", null))
                            val trTf = LyricsPopupOverlay.resolveTypeface(applicationContext, prefs.getString("lyrics_translation_font_path", null))
                                ?: mainTf
                            val bgTf = LyricsPopupOverlay.resolveTypeface(applicationContext, prefs.getString("lyrics_backing_font_path", null))
                                ?: mainTf

                            LyricsPopupOverlay.show(applicationContext)
                            LyricsPopupOverlay.applyChrome(applicationContext)
                            LyricsPopupOverlay.updateFrame(
                                LyricsPopupOverlay.PopupFrame(
                                    main = mainLine,
                                    translation = run {
                                        if (mainIdx == null) return@run null
                                        var j = mainIdx + 1
                                        while (j < cachedLines.size && cachedLines[j].isBackground && !cachedLines[j].isTranslation) j++
                                        if (j < cachedLines.size && cachedLines[j].isTranslation) cachedLines[j] else null
                                    },
                                    backing = cachedLines.lastOrNull {
                                        it.isBackground && !it.isTranslation && it.timeMs >= 0L && it.timeMs <= position &&
                                            (it.endMs < 0L || position <= it.endMs + 800L)
                                    },
                                    positionMs = position,
                                    primaryColor = primary,
                                    secondaryColor = secondary,
                                    translationColor = trColor,
                                    backingColor = bgColor,
                                    fontSizeSp = fontSize.coerceAtMost(32f),
                                    mainTypeface = mainTf,
                                    translationTypeface = trTf,
                                    backingTypeface = bgTf
                                )
                            )
                            if (!lastNotifShown) {
                                    LyricsPopupNotifier.show(applicationContext)
                                    lastNotifShown = true
                                }
                            }
                        }
                    }
                    delay(50)
                }
            } finally {
                LyricsPopupOverlay.hide()
                LyricsPopupNotifier.cancel(applicationContext)
            }
        }
    }

    // ---------- Botões personalizados na notificação (estilo sistema) ----------
    // Ordem visual típica: [♡] [⏮] [⏯] [⏭] [✕]  — secundários nas pontas

    private fun favoriteButton(isFavorite: Boolean): CommandButton =
        CommandButton.Builder()
            .setDisplayName(if (isFavorite) "Remover dos favoritos" else "Favoritar")
            .setSessionCommand(toggleFavoriteCommand)
            .setIconResId(
                if (isFavorite) R.drawable.ic_notif_favorite else R.drawable.ic_notif_favorite_border
            )
            .build()

    private fun closeButton(): CommandButton =
        CommandButton.Builder()
            .setDisplayName("Fechar")
            .setSessionCommand(closeAppCommand)
            .setIconResId(R.drawable.ic_notif_close)
            .build()

    private fun isFavorite(trackId: String): Boolean =
        FavoritesBridge.owner?.isFavorite(trackId) ?: FavoritesFile.isFavorite(this, trackId)

    private fun toggleFavorite(trackId: String) {
        val owner = FavoritesBridge.owner
        if (owner != null) {
            owner.toggleFavorite(trackId) // app aberto: o ViewModel altera, salva e avisa
        } else {
            // app fechado: edita o arquivo direto e avisa para o ícone atualizar
            scope.launch {
                withContext(Dispatchers.IO) { FavoritesFile.toggle(applicationContext, trackId) }
                FavoritesBridge.notifyChanged()
            }
        }
    }

    /**
     * Layout: [Favoritar] … prev / play / next … [Fechar]
     * Compatível com o painel de mídia universal do Android (MediaSession).
     */
    private fun refreshCustomButtons() {
        val session = mediaSession ?: return
        val id = session.player.currentMediaItem?.mediaId
        if (id == null) {
            session.setCustomLayout(ImmutableList.of(closeButton()))
            return
        }
        scope.launch {
            val fav = withContext(Dispatchers.IO) { isFavorite(id) }
            if (session.player.currentMediaItem?.mediaId == id) {
                session.setCustomLayout(
                    ImmutableList.of(
                        favoriteButton(fav),
                        closeButton()
                    )
                )
            }
        }
    }

    /**
     * Após [IDLE_STOP_MS] sem reprodução, encerra o serviço e remove a notificação.
     */
    private fun scheduleIdleStopIfNeeded(isPlaying: Boolean) {
        if (isPlaying) {
            idleStopJob?.cancel()
            idleStopJob = null
            return
        }
        idleStopJob?.cancel()
        idleStopJob = scope.launch {
            delay(IDLE_STOP_MS)
            val player = mediaSession?.player ?: return@launch
            if (player.isPlaying) return@launch
            try {
                player.stop()
                player.clearMediaItems()
            } catch (_: Exception) { }
            stopSelf()
        }
    }

    /** Avança/repete quando a faixa chega ao fim oficial (STATE_ENDED). */
    private fun handleTrackEnded(player: ExoPlayer) {
        cancelEndFade()
        cancelCrossfade(player, restoreMainVolume = true)
        try { player.volume = 1f } catch (_: Throwable) { }
        when (player.repeatMode) {
            Player.REPEAT_MODE_ONE -> {
                player.seekTo(0L)
                player.prepare()
                player.play()
            }
            else -> {
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                } else if (player.repeatMode == Player.REPEAT_MODE_ALL && player.mediaItemCount > 0) {
                    player.seekTo(0, 0L)
                    player.prepare()
                    player.play()
                } else {
                    player.pause()
                    player.seekTo(0L)
                }
            }
        }
    }

    /**
     * Detecta “silêncio no final”: posição presa perto do fim com playWhenReady=true
     * sem STATE_ENDED (comum em alguns FLAC com duração de cabeçalho maior que o PCM).
     */
    private var endWatchdogJob: Job? = null
    private fun startEndOfTrackWatchdog(player: ExoPlayer) {
        endWatchdogJob?.cancel()
        endWatchdogJob = scope.launch {
            var stuckNearEndMs = 0L
            var lastPos = -1L
            var lastMediaId: String? = null
            while (isActive) {
                delay(250)
                try {
                    if (!player.playWhenReady) {
                        stuckNearEndMs = 0L
                        continue
                    }
                    val dur = player.duration
                    val pos = player.currentPosition
                    val id = player.currentMediaItem?.mediaId
                    if (id != lastMediaId) {
                        lastMediaId = id
                        stuckNearEndMs = 0L
                        lastPos = -1L
                    }
                    if (dur <= 0L || pos < 0L) {
                        stuckNearEndMs = 0L
                        continue
                    }
                    val remaining = dur - pos
                    // Perto do fim e posição não avança de forma relevante
                    if (remaining in 0L..1_200L) {
                        if (lastPos >= 0L && kotlin.math.abs(pos - lastPos) < 40L) {
                            stuckNearEndMs += 250L
                        } else {
                            stuckNearEndMs = 0L
                        }
                        lastPos = pos
                        // ~900 ms parado no final → força o fim
                        if (stuckNearEndMs >= 900L) {
                            android.util.Log.w(
                                "PlaybackService",
                                "Watchdog fim de faixa: pos=$pos dur=$dur id=$id — avançando"
                            )
                            stuckNearEndMs = 0L
                            handleTrackEnded(player)
                        }
                    } else {
                        stuckNearEndMs = 0L
                        lastPos = pos
                    }
                } catch (_: Throwable) { }
            }
        }
    }

    /**
     * Troca de rota (fone → alto-falante etc.): reseta velocidade e re-sincroniza a posição
     * para o AudioTrack ser reconstruído sem pitch/lento residual.
     */
    private fun onAudioRouteChanged(player: ExoPlayer) {
        try {
            val wasPlaying = player.playWhenReady
            val pos = player.currentPosition.coerceAtLeast(0L)
            val index = player.currentMediaItemIndex
            if (!player.playbackParameters.equals(androidx.media3.common.PlaybackParameters.DEFAULT)) {
                player.playbackParameters = androidx.media3.common.PlaybackParameters.DEFAULT
            }
            // Re-seek força o sink a alinhar com o novo device
            if (index >= 0 && player.mediaItemCount > 0) {
                player.seekTo(index, pos)
            }
            if (wasPlaying && !player.isPlaying) {
                player.play()
            }
            android.util.Log.i("PlaybackService", "Rota de áudio alterada — resync pos=$pos")
        } catch (e: Throwable) {
            android.util.Log.w("PlaybackService", "onAudioRouteChanged: ${e.message}")
        }
    }

    /**
     * Se uma faixa falhar (formato/taxa não aceitos pela saída de áudio, arquivo corrompido...),
     * avisa e pula para a próxima em vez de deixar o player travado em erro.
     * O limite de erros seguidos evita loop infinito com repetição ativada.
     */
    private fun handlePlayerError(player: ExoPlayer, error: PlaybackException) {
        consecutiveErrors++
        val item = player.currentMediaItem
        val causes = generateSequence(error.cause) { it.cause }
            .mapIndexed { index, cause -> "cause[$index]=${cause.javaClass.name}: ${cause.message}" }
            .joinToString(" <- ")
        android.util.Log.e(
            "PlaybackService",
            "PlaybackException code=${error.errorCodeName}/${error.errorCode} " +
                "track=${item?.localConfiguration?.uri} mime=${item?.localConfiguration?.mimeType} " +
                "dsf=$currentTrackIsDsf output=${appliedTrackSampleRate}Hz/" +
                "${appliedTrackChannelCount}ch/enc=${appliedTrackEncoding} " +
                "causeChain=${causes.ifBlank { "<none>" }}",
            error
        )
        Toast.makeText(
            this,
            "Não foi possível tocar esta faixa (${error.errorCodeName})",
            Toast.LENGTH_LONG
        ).show()

        if (player.hasNextMediaItem() && consecutiveErrors <= MAX_CONSECUTIVE_ERRORS) {
            player.seekToNextMediaItem()
            player.prepare()
            player.play()
        } else {
            player.stop()
        }
    }


    /**
     * Roteia a saída do ExoPlayer para o DAC USB ou, se não houver, para o fone com fio, usando a API
     * oficial [ExoPlayer.setPreferredAudioDevice] (por baixo, AudioTrack.setPreferredDevice).
     * Sem nenhum dos dois, passa null e o sistema volta a escolher a rota (alto-falante, Bluetooth...).
     */
    private fun applyOutputRouting(player: ExoPlayer) {
        if (!dedicatedOutputActive) return
        val device = AudioOutputInfo.findPreferredOutput(this)
        appliedDeviceLabel = if (device != null) AudioOutputInfo.describeDevice(device) else ""
        // DAC conectado/desconectado: reaplica o formato do mixer na próxima faixa
        com.tolete.player.data.UsbMixer.invalidate()
        try {
            player.setPreferredAudioDevice(device)
            // Evita velocidade/pitch "preso" após troca de rota (fone → alto-falante etc.)
            try {
                if (!player.playbackParameters.equals(androidx.media3.common.PlaybackParameters.DEFAULT)) {
                    player.playbackParameters = androidx.media3.common.PlaybackParameters.DEFAULT
                }
            } catch (_: Throwable) { }
            android.util.Log.i("PlaybackService", "setPreferredAudioDevice → ${appliedDeviceLabel.ifBlank { "automático" }}")
        } catch (e: Throwable) {
            android.util.Log.w("PlaybackService", "Falha ao rotear a saída: ${e.message}")
        }
    }

    /** DSF/DSD sources are identified from the media URI or explicit MIME type. */
    private fun isDsfMediaItem(item: MediaItem?): Boolean {
        val local = item?.localConfiguration ?: return false
        val mime = local.mimeType.orEmpty().lowercase(java.util.Locale.ROOT)
        val uri = Uri.decode(local.uri.toString()).substringBefore('?').substringBefore('#')
            .lowercase(java.util.Locale.ROOT)
        return mime.contains("dsf") || mime.contains("dsd") ||
            uri.endsWith(".dsf") || uri.endsWith(".dff")
    }

    /** Unknown/unsupported routes are treated as stereo-only rather than risking AudioTrack failure. */
    private fun routeSupportsMultichannel(context: Context, channelCount: Int): Boolean {
        if (channelCount <= 2) return true
        val device = AudioOutputInfo.activeDevice(context) ?: return false
        val channelCounts = try { device.channelCounts } catch (_: Throwable) { null }
        if (channelCounts?.contains(channelCount) != true) return false
        if (android.os.Build.VERSION.SDK_INT >= 34 && com.tolete.player.data.UsbAudioRouter.isUsbType(device.type)) {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                ?: return false
            return try {
                am.getSupportedMixerAttributes(device).any {
                    it.mixerBehavior == android.media.AudioMixerAttributes.MIXER_BEHAVIOR_DEFAULT &&
                        it.format.channelCount == channelCount
                }
            } catch (_: Throwable) {
                false
            }
        }
        return true
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        ScrobbleNotifier.cancel(applicationContext)
        crossfadeMonitorJob?.cancel()
        crossfadeMixJob?.cancel()
        releaseCrossfadePreview()
        idleStopJob?.cancel()
        idleStopJob = null
        scrobblePauseHideJob?.cancel()
        scrobblePauseHideJob = null
        scrobbleNotificationHidden = false
        lyricsPopupMonitorJob?.cancel()
        lyricsPopupMonitorJob = null
        LyricsPopupOverlay.hide()
        LyricsPopupNotifier.cancel(applicationContext)
        scope.cancel()
        deviceCallback?.let {
            try {
                (getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager)
                    .unregisterAudioDeviceCallback(it)
            } catch (_: Exception) {}
        }
        deviceCallback = null
        if (dedicatedOutputActive) {
            // Devolve o DAC ao formato padrão do sistema para os outros apps
            try { com.tolete.player.data.UsbMixer.clear(this) } catch (_: Exception) {}
        }
        currentAudioSessionId = 0
        exoPlayerRef = null
        try {
            com.tolete.player.data.EqualizerManager.release()
        } catch (_: Exception) {}
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
