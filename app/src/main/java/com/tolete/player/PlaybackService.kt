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
import androidx.media3.common.ForwardingPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.common.MimeTypes
import com.tolete.player.visualizer.AudioTapSink
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
import com.tolete.player.data.LastFmScrobbler
import com.tolete.player.data.LibraryCache
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
            val s = speed.coerceIn(0.5f, 2f)
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

        // Visualizador .milk: o AudioTapSink (em buildAudioSink) só "espia" o PCM, sem alterar
        // o áudio nem o bit-perfect. Custa ~zero enquanto AudioTap.active == false.
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
                enableAudioOutputPlaybackParameters: Boolean
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
                    .setEnableAudioOutputPlaybackParameters(true)
                android.util.Log.i(
                    "PlaybackService",
                    "AudioSink: usbExclusive=$usbExclusive device=${appliedUsbDeviceLabel.ifBlank { "-" }} float=$useFloat"
                )
                val sink = builder.build()
                // AudioTapSink (visualizador .milk) fica por fora de tudo e só espia o PCM.
                if (!usbExclusive) return AudioTapSink(sink)
                // USB exclusivo: antes de cada AudioTrack ser criado, pede ao Android 14+ que
                // reabra a saída USB na taxa da faixa (senão o DAC fica preso em 48 kHz).
                val appCtx = context.applicationContext
                return AudioTapSink(object : ForwardingAudioSink(sink) {
                    // Media3 1.11: o override agora é configure(AudioSinkConfig)
                    override fun configure(audioSinkConfig: AudioSink.AudioSinkConfig) {
                        val inputFormat = audioSinkConfig.format
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
                            val channels = audioSinkConfig.outputChannelMapping?.length()?.takeIf { it > 0 }
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
                        super.configure(audioSinkConfig)
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
            // Media3 1.11 ligou o "dynamic scheduling" por padrão (era desligado na 1.10.1).
            // Nele, getCurrentPosition() fica defasado em até ~200 ms e atualiza em degraus
            // (androidx/media#3286, só corrigido na 1.12). Como o karaokê lê a posição a cada
            // ~48 ms, a palavra enchia, esvaziava e enchia de novo, e a letra atrasava.
            // Voltar ao agendamento fixo restaura a posição contínua da versão anterior.
            .experimentalSetDynamicSchedulingEnabled(false)
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
                // Trocou de faixa durante a pausa? Garante que a notificação mostre a faixa certa ao voltar.
                syncScrobbleCurrent(newPlay = false)
                scrobbleTick.value++
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val playing = player.isPlaying
                scheduleIdleStopIfNeeded(playing)
                scrobbleTick.value++
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                scrobbleTick.value++
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                refreshCustomButtons()
                // Nova faixa (ou repetição): atualiza a faixa atual do scrobbler agora, tocando ou pausado.
                syncScrobbleCurrent(newPlay = true)
                scrobbleTick.value++
                val sid = player.audioSessionId
                if (sid != 0) currentAudioSessionId = sid
                // O fade de saída da faixa anterior NÃO pode continuar rodando na faixa nova
                // (nem ao repetir a mesma faixa): ele terminava em volume 0 e deixava tudo mudo.
                cancelEndFade()
                val cf = crossfadeMs()
                if (cf > 0 &&
                    (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                        reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)
                ) {
                    // Nova faixa (ou repetição): fade in (simula crossfade na transição automática)
                    player.volume = 0f
                    rampVolume(player, 0f, 1f, cf)
                } else if (player.volume < 0.99f && fadeVolumeJob?.isActive != true) {
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
                if (ms <= 0) {
                    super.pause()
                    player.volume = 1f
                    return
                }
                val start = player.volume.coerceIn(0.01f, 1f)
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

        mediaSession = MediaSession.Builder(this, fadingPlayer)
            .setCallback(sessionCallback)
            .setSessionActivity(sessionActivity)
            .build()

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

        // end-crossfade watcher: fade out no final da faixa (+ rede de segurança contra volume preso em 0)
        scope.launch {
            while (true) {
                delay(200)
                val p = exoPlayerRef ?: continue
                val cf = crossfadeMs()
                if (!p.isPlaying) {
                    // Pausa por foco de áudio/sistema no meio do fade: não deixa terminar em 0,
                    // senão ao retomar a faixa toca muda.
                    if (endFadeJob?.isActive == true) endFadeJob?.cancel()
                    endFadeJob = null
                    endCrossfadeStarted = false
                    continue
                }
                val dur = p.duration
                val remain = if (dur > 0) dur - p.currentPosition else Long.MAX_VALUE
                val inEndZone = cf > 0 && remain in 1 until cf.toLong()
                val fading = fadeVolumeJob?.isActive == true || endFadeJob?.isActive == true
                // Rede de segurança: tocando, sem nenhum fade ativo, fora do trecho final -> volume 100%.
                if (!fading && p.volume < 0.99f && (!endCrossfadeStarted || !inEndZone)) {
                    p.volume = 1f
                    if (!inEndZone) endCrossfadeStarted = false
                }
                if (cf <= 0) {
                    endCrossfadeStarted = false
                    continue
                }
                if (inEndZone && !endCrossfadeStarted) {
                    endCrossfadeStarted = true
                    val start = p.volume.coerceIn(0.05f, 1f)
                    rampVolume(p, start, 0f, remain.toInt().coerceAtLeast(50), isEndFade = true)
                }
            }
        }
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
        ScrobbleNotifier.cancel(applicationContext)
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
