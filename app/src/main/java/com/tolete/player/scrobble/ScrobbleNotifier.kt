package com.tolete.player.scrobble

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.tolete.player.MainActivity
import com.tolete.player.R
import com.tolete.player.data.FavoritesBridge
import com.tolete.player.data.FavoritesFile
import com.tolete.player.data.LastFmScrobbler
import com.tolete.player.data.ScrobbleEdits
import com.tolete.player.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Notificação persistente do scrobbler: música + artista e três botões
 * (pular scrobbling, editar tags, favoritar no app e no Last.fm).
 * Quem a mantém atualizada é o PlaybackService.
 */
object ScrobbleNotifier {

    const val ACTION_SKIP = "com.tolete.player.SCROBBLE_SKIP"
    const val ACTION_FAVORITE = "com.tolete.player.SCROBBLE_FAVORITE"
    /** Disparada ao dispensar a notificação: ela é recriada. */
    const val ACTION_REFRESH = "com.tolete.player.SCROBBLE_REFRESH"
    /** Toque no corpo da notificação: o app abre o dashboard do Last.fm. */
    const val ACTION_OPEN_LASTFM_DASHBOARD = "com.tolete.player.OPEN_LASTFM_DASHBOARD"
    /** @deprecated Mantido só por compatibilidade com PendingIntents antigos. */
    const val ACTION_SHOW_TRACK_INFO = ACTION_OPEN_LASTFM_DASHBOARD

    private const val SYMBOL_SKIP = "\uD83D\uDEAB"          // 🚫 bloqueio
    private const val SYMBOL_EDIT = "\u270F\uFE0F"          // ✏️ lápis
    private const val SYMBOL_FAVORITE_ON = "\u2764\uFE0F"   // ❤️ favoritada
    private const val SYMBOL_FAVORITE_OFF = "\uD83E\uDD0D"  // 🤍 não favoritada

    private const val CHANNEL_ID = "lyron_scrobbler"
    const val NOTIFICATION_ID = 7042

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Scrobbler (Last.fm)", NotificationManager.IMPORTANCE_LOW)
                    .apply {
                        description = "Controles do scrobbling: pular, editar tags e favoritar"
                        setShowBadge(false)
                    }
            )
        }
    }

    private fun broadcast(ctx: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, code,
            Intent(ctx, ScrobbleActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun cancel(ctx: Context) {
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(NOTIFICATION_ID)
    }

    /**
     * Mostra/atualiza. Sem login ou com o scrobbler desligado, remove a notificação.
     * [isFavorite] vem pronto (a leitura do arquivo é feita fora da thread principal).
     */
    fun show(
        ctx: Context,
        track: Track?,
        isPlaying: Boolean,
        isFavorite: Boolean,
        scrobbled: Boolean = false
    ) {
        if (!LastFmScrobbler.enabled.value) {
            cancel(ctx)
            return
        }
        ensureChannel(ctx)

        val skipped = track != null && ScrobbleEdits.isSkipped(track.artist, track.title)
        // Scrobble já enviado: troca o ícone por um de confirmação e some com os botões.
        val sent = track != null && scrobbled && !skipped
        val b = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(if (sent) R.drawable.ic_notif_check else R.drawable.ic_notif_lyre)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(
                // Request code próprio (10): o PendingIntent da sessão de mídia usa o 0 e o mesmo
                // destino, e com UPDATE_CURRENT um sobrescreveria os extras do outro.
                PendingIntent.getActivity(
                    ctx, 10,
                    Intent(ctx, MainActivity::class.java).apply {
                        action = ACTION_OPEN_LASTFM_DASHBOARD
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            // Se o usuário dispensar (Android 14+ permite), a notificação volta.
            .setDeleteIntent(broadcast(ctx, ACTION_REFRESH, 4))

        if (track == null) {
            b.setContentTitle("Scrobbler ativo")
                .setContentText("Nada tocando")
        } else {
            val meta = LastFmScrobbler.metaFor(track)
            b.setContentTitle(meta.track)
                .setContentText(meta.artist)
            val state = when {
                skipped -> "Scrobbling pulado nesta faixa"
                sent -> "Enviado ao Last.fm"
                isPlaying -> "Tocando · enviando ao Last.fm"
                else -> "Pausado"
            }
            b.setSubText(state)
            // Enviado: sem botões (só ícone de confirmação + texto).
            if (!sent) addActionButtons(ctx, b, isFavorite)
        }

        try {
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIFICATION_ID, b.build())
        } catch (_: SecurityException) {
            // sem permissão de notificação: o app já pede; aqui só ignora
        }
    }

    /**
     * Botões só com símbolos. O Android 12+ mostra apenas o TÍTULO da ação (o ícone é ignorado),
     * por isso o símbolo vai no título; o ícone continua para versões mais antigas.
     */
    private fun addActionButtons(
        ctx: Context,
        b: NotificationCompat.Builder,
        isFavorite: Boolean
    ) {
        b.addAction(
            R.drawable.ic_notif_skip_scrobble,
            SYMBOL_SKIP,
            broadcast(ctx, ACTION_SKIP, 1)
        )
        b.addAction(
            R.drawable.ic_notif_edit,
            SYMBOL_EDIT,
            PendingIntent.getActivity(
                ctx, 2,
                Intent(ctx, ScrobbleEditActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        b.addAction(
            if (isFavorite) R.drawable.ic_notif_favorite else R.drawable.ic_notif_favorite_border,
            if (isFavorite) SYMBOL_FAVORITE_ON else SYMBOL_FAVORITE_OFF,
            broadcast(ctx, ACTION_FAVORITE, 3)
        )
    }

    // ---------- ações ----------

    fun onSkip(ctx: Context) {
        val t = LastFmScrobbler.current.value ?: return
        val nowSkipped = ScrobbleEdits.toggleSkipped(t.artist, t.title)
        Toast.makeText(
            ctx,
            if (nowSkipped) "Esta faixa não será enviada ao Last.fm" else "Scrobbling reativado para esta faixa",
            Toast.LENGTH_SHORT
        ).show()
        if (nowSkipped) LastFmScrobbler.cancelNowPlaying()
        else if (com.tolete.player.PlaybackService.exoPlayerRef?.isPlaying == true) LastFmScrobbler.nowPlaying(t)
    }

    /** Favorita/desfavorita no app e no Last.fm. Chamado pelo receiver (com goAsync). */
    fun onFavorite(ctx: Context, done: () -> Unit) {
        val t = LastFmScrobbler.current.value
        if (t == null) { done(); return }
        scope.launch {
            val appCtx = ctx.applicationContext
            val owner = FavoritesBridge.owner
            val wasFav = withContext(Dispatchers.IO) {
                owner?.isFavorite(t.id) ?: FavoritesFile.isFavorite(appCtx, t.id)
            }
            if (owner != null) {
                owner.toggleFavorite(t.id)
            } else {
                withContext(Dispatchers.IO) { FavoritesFile.toggle(appCtx, t.id) }
                FavoritesBridge.notifyChanged()
            }
            val nowFav = !wasFav
            val loved = LastFmScrobbler.setLoved(t, nowFav)
            val msg = when {
                loved && nowFav -> "Favoritada no app e no Last.fm"
                loved -> "Removida dos favoritos do app e do Last.fm"
                nowFav -> "Favoritada no app (Last.fm não atualizado)"
                else -> "Removida dos favoritos do app (Last.fm não atualizado)"
            }
            Toast.makeText(appCtx, msg, Toast.LENGTH_SHORT).show()
            done()
        }
    }
}
