package com.tolete.player

import android.annotation.SuppressLint
import android.content.Context
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("UnsafeOptInUsageError")
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var consecutiveErrors = 0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val toggleFavoriteCommand = SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY)
    private val closeAppCommand = SessionCommand(ACTION_CLOSE_APP, Bundle.EMPTY)

    private val sessionCallback = object : MediaSession.Callback {
        // Libera comandos personalizados (favoritar / fechar) para os controladores, inclusive a notificação
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(toggleFavoriteCommand)
                .add(closeAppCommand)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
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
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
        }
    }

    override fun onCreate() {
        super.onCreate()

        val prefs = getSharedPreferences("tolete", MODE_PRIVATE)
        val bitPerfect = prefs.getBoolean("bit_perfect", false)

        // Visualizador .milk: o TeeAudioProcessor só "espia" o PCM (repassa os bytes idênticos,
        // então não altera o áudio nem o bit-perfect). Custa ~zero enquanto AudioTap.active == false.
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink? = DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessors(arrayOf(TeeAudioProcessor(AudioTap)))
                .build()
        }

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

        // Bit-perfect: evita processamento extra (skip silence, etc.)
        player.skipSilenceEnabled = false
        if (bitPerfect) {
            // Mantém parâmetros de reprodução neutros (sem pitch/speed)
            player.playbackParameters = androidx.media3.common.PlaybackParameters.DEFAULT
        }

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) consecutiveErrors = 0
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                refreshCustomButtons()
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

    // ---------- Botões personalizados na notificação ----------

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
     * Layout: [Fechar] ... controles padrão ... [Favoritar]
     * Media3 coloca os custom buttons na ordem da lista; o sistema de notificação
     * costuma mostrar o primeiro à esquerda e o último à direita dos controles.
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
            // se a faixa mudou enquanto líamos o arquivo, o refresh da nova faixa cuida do resto
            if (session.player.currentMediaItem?.mediaId == id) {
                session.setCustomLayout(
                    ImmutableList.of(
                        closeButton(),
                        favoriteButton(fav)
                    )
                )
            }
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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private companion object {
        const val MAX_CONSECUTIVE_ERRORS = 3
        const val ACTION_TOGGLE_FAVORITE = "com.tolete.player.TOGGLE_FAVORITE"
        const val ACTION_CLOSE_APP = "com.tolete.player.CLOSE_APP"
    }
}
