package com.tolete.player.scrobble

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tolete.player.data.FavoritesBridge
import com.tolete.player.data.LastFmScrobbler

/** Recebe os toques nos botões da notificação do scrobbler. */
class ScrobbleActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        LastFmScrobbler.init(context)
        when (intent.action) {
            ScrobbleNotifier.ACTION_SKIP -> ScrobbleNotifier.onSkip(context)
            ScrobbleNotifier.ACTION_FAVORITE -> {
                val pending = goAsync()
                ScrobbleNotifier.onFavorite(context) { pending.finish() }
            }
            ScrobbleNotifier.ACTION_REFRESH -> FavoritesBridge.notifyChanged() // força o serviço a redesenhar
        }
    }
}
