package com.tolete.player

import android.annotation.SuppressLint
import android.content.Context
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
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import com.tolete.player.visualizer.AudioTap
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.tolete.player.data.FavoritesBridge
import com.tolete.player.data.FavoritesFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
            val s = speed.coerceIn(0.5f, 2f)
            val apply = Runnable {
                try {
                    // Duas formas: API dedicada + PlaybackParameters (Media3 1.3)
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
        /** 7 minutos pausado → remove notificação e encerra o serviço. */
        private const val IDLE_STOP_MS = 7 * 60 * 1000L
        const val ACTION_TOGGLE_FAVORITE = "com.tolete.player.TOGGLE_FAVORITE"
        const val ACTION_CLOSE_APP = "com.tolete.player.CLOSE_APP"
        const val ACTION_SET_SPEED = "com.tolete.player.SET_SPEED"
        const val EXTRA_SPEED = "speed"

        /** Preferências de áudio aplicadas na criação do player (para UI técnica). */
        @Volatile var appliedBackend: String = "DEFAULT"
        @Volatile var appliedPreferredSampleRate: Int = 0
        @Volatile var appliedFloatOutput: Boolean = false
        @Volatile var appliedUsbDeviceLabel: String = ""
    }

    private var mediaSession: MediaSession? = null
    private var backendIsUsbExclusive = false
    private var usbDeviceCallback: android.media.AudioDeviceCallback? = null
    private var consecutiveErrors = 0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    /** Cancela notificação/serviço após 7 min seguidos sem reprodução. */
    private var idleStopJob: Job? = null

    private val toggleFavoriteCommand = SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY)
    private val closeAppCommand = SessionCommand(ACTION_CLOSE_APP, Bundle.EMPTY)
    private val setSpeedCommand = SessionCommand(ACTION_SET_SPEED, Bundle.EMPTY)

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
                    val speed = args.getFloat(EXTRA_SPEED, 1f).coerceIn(0.5f, 2f)
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
        val bitPerfect = prefs.getBoolean("bit_perfect", false)
        // Escolha de saída de áudio e taxa de amostragem foram removidas das configurações:
        // o player sempre usa a saída padrão do sistema, sem pedido de taxa.
        val preferredSr = 0
        val backendName = "DEFAULT"
        // Float PCM: ajuda em caminhos hi-res / AAudio e evita quantização extra em 24/32-bit
        val wantFloat = !bitPerfect && (
            preferredSr >= 96_000 ||
                backendName == "AAUDIO" ||
                backendName == "AUDIOTRACK" ||
                backendName == "USB_EXCLUSIVE"
        )
        appliedBackend = backendName
        appliedPreferredSampleRate = preferredSr
        appliedFloatOutput = wantFloat
        android.util.Log.i(
            "PlaybackService",
            "Áudio: backend=$backendName preferredSr=${preferredSr}Hz float=$wantFloat bitPerfect=$bitPerfect"
        )

        // Visualizador .milk: o TeeAudioProcessor só "espia" o PCM (repassa os bytes idênticos,
        // então não altera o áudio nem o bit-perfect). Custa ~zero enquanto AudioTap.active == false.
        // Extensão FFmpeg (AAR local): decodifica codecs que o MediaCodec do aparelho
        // não cobre (ex.: alguns FLAC/Opus/WMA). Preferência: software FFmpeg antes do decoder nativo.
        if (FfmpegLibrary.isAvailable()) {
            android.util.Log.i("PlaybackService", "FFmpeg decoder disponível: ${FfmpegLibrary.getVersion()}")
        } else {
            android.util.Log.w("PlaybackService", "FFmpeg decoder indisponível neste ABI")
        }

        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink? {
                val usbExclusive = backendName == "USB_EXCLUSIVE"
                val usbDevice = if (usbExclusive) {
                    com.tolete.player.data.UsbAudioRouter.findUsbOutput(context)
                } else null
                appliedUsbDeviceLabel = if (usbExclusive) {
                    com.tolete.player.data.UsbAudioRouter.label(usbDevice)
                } else ""
                // Float: hi-res, USB DAC ou backends explícitos.
                // CORREÇÃO: manter float também em bit-perfect. O Media3 só preserva
                // 24/32 bits com a saída float ligada; sem ela ele converte hi-res para
                // 16 bits ANTES de chegar ao DAC. Um int24 cabe inteiro na mantissa de
                // um float32, então nada é perdido no caminho.
                val useFloat = enableFloatOutput || wantFloat || usbExclusive || bitPerfect
                appliedFloatOutput = useFloat
                val builder = DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(useFloat)
                    .setEnableAudioTrackPlaybackParams(true)
                    .setAudioProcessors(arrayOf(TeeAudioProcessor(AudioTap)))
                android.util.Log.i(
                    "PlaybackService",
                    "AudioSink: usbExclusive=$usbExclusive device=${appliedUsbDeviceLabel.ifBlank { "-" }} float=$useFloat"
                )
                val sink = builder.build()
                if (!usbExclusive) return sink
                // USB exclusivo: antes de cada AudioTrack ser criado, pede ao Android 14+ que
                // reabra a saída USB na taxa da faixa (senão o DAC fica preso em 48 kHz).
                val appCtx = context.applicationContext
                return object : ForwardingAudioSink(sink) {
                    override fun configure(
                        inputFormat: Format,
                        specifiedBufferSize: Int,
                        outputChannels: IntArray?
                    ) {
                        if (MimeTypes.AUDIO_RAW == inputFormat.sampleMimeType &&
                            inputFormat.sampleRate > 0
                        ) {
                            val pcm = inputFormat.pcmEncoding
                            val highRes = pcm == C.ENCODING_PCM_24BIT ||
                                pcm == C.ENCODING_PCM_32BIT ||
                                pcm == C.ENCODING_PCM_FLOAT
                            // Mesmo critério do DefaultAudioSink: hi-res vira float se habilitado,
                            // senão é convertido para 16-bit.
                            val trackEncoding = when {
                                highRes && useFloat -> AudioFormat.ENCODING_PCM_FLOAT
                                highRes -> AudioFormat.ENCODING_PCM_16BIT
                                pcm == C.ENCODING_PCM_8BIT -> AudioFormat.ENCODING_PCM_8BIT
                                else -> AudioFormat.ENCODING_PCM_16BIT
                            }
                            val channels = outputChannels?.size?.takeIf { it > 0 }
                                ?: inputFormat.channelCount
                            com.tolete.player.data.UsbMixer.applyForTrack(
                                appCtx,
                                sampleRate = inputFormat.sampleRate,
                                channelCount = channels,
                                encoding = trackEncoding,
                                preferredHz = preferredSr,
                                bitPerfect = bitPerfect
                            )
                        }
                        super.configure(inputFormat, specifiedBufferSize, outputChannels)
                    }
                }
            }

            // Media3 1.3.x: (context, extensionRendererMode, mediaCodecSelector,
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

        val player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true) // pausa ao desconectar o fone
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        currentAudioSessionId = player.audioSessionId
        exoPlayerRef = player

        // Saída USB exclusiva: roteia ao DAC e reage a conectar/desconectar
        backendIsUsbExclusive = backendName == "USB_EXCLUSIVE"
        if (backendIsUsbExclusive) {
            applyUsbRouting(player)
            val cb = object : android.media.AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) =
                    applyUsbRouting(player)
                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) =
                    applyUsbRouting(player)
            }
            usbDeviceCallback = cb
            (getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager)
                .registerAudioDeviceCallback(cb, android.os.Handler(android.os.Looper.getMainLooper()))
        }

        // Bit-perfect: evita processamento extra (skip silence, etc.)
        // Nota: velocidade do sincronizador ainda pode alterar playbackParameters sob demanda.
        player.skipSilenceEnabled = false
        if (bitPerfect) {
            player.playbackParameters = androidx.media3.common.PlaybackParameters.DEFAULT
        }

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) consecutiveErrors = 0
                // Atualiza sessão de áudio (pode mudar após prepare/play)
                val sid = player.audioSessionId
                if (sid != 0 && sid != currentAudioSessionId) {
                    currentAudioSessionId = sid
                }
                scheduleIdleStopIfNeeded(isPlaying)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val playing = player.isPlaying
                scheduleIdleStopIfNeeded(playing)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                refreshCustomButtons()
                val sid = player.audioSessionId
                if (sid != 0) currentAudioSessionId = sid
            }

            override fun onPlayerError(error: PlaybackException) {
                handlePlayerError(player, error)
            }
        })

        // Intent que abre o app ao tocar na notificação (fora dos botões de ação)
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(sessionCallback)
            .setSessionActivity(sessionActivity)
            .build()

        // Qualquer mudança nos favoritos (no app ou aqui) redesenha o ícone
        scope.launch {
            FavoritesBridge.version.collect { refreshCustomButtons() }
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

    /**
     * Se uma faixa falhar (formato/taxa não aceitos pela saída de áudio, arquivo corrompido...),
     * avisa e pula para a próxima em vez de deixar o player travado em erro.
     * O limite de erros seguidos evita loop infinito com repetição ativada.
     */
    private fun handlePlayerError(player: ExoPlayer, error: PlaybackException) {
        consecutiveErrors++
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
     * Roteia a saída do ExoPlayer para o DAC USB (Media3 1.3.x não tem AudioTrackProvider,
     * então usamos a API oficial [ExoPlayer.setPreferredAudioDevice], que internamente chama
     * AudioTrack.setPreferredDevice). Passar null devolve o roteamento automático do sistema.
     */
    private fun applyUsbRouting(player: ExoPlayer) {
        if (backendIsUsbExclusive.not()) return
        val usb = com.tolete.player.data.UsbAudioRouter.findUsbOutput(this)
        appliedUsbDeviceLabel = com.tolete.player.data.UsbAudioRouter.label(usb)
        // DAC conectado/desconectado: reaplica o formato do mixer na próxima faixa
        com.tolete.player.data.UsbMixer.invalidate()
        try {
            player.setPreferredAudioDevice(usb)
            android.util.Log.i("PlaybackService", "setPreferredAudioDevice → $appliedUsbDeviceLabel")
        } catch (e: Throwable) {
            android.util.Log.w("PlaybackService", "Falha ao rotear para USB: ${e.message}")
        }
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
        idleStopJob?.cancel()
        idleStopJob = null
        scope.cancel()
        usbDeviceCallback?.let {
            try {
                (getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager)
                    .unregisterAudioDeviceCallback(it)
            } catch (_: Exception) {}
        }
        usbDeviceCallback = null
        if (backendIsUsbExclusive) {
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
