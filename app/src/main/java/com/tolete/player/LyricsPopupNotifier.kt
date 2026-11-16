package com.tolete.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat

/**
 * Notificação de controle do popup de letras:
 * - Desligar popup
 * - Bloquear / desbloquear posição (toques atravessam quando bloqueado)
 * - Personalizar (placeholder)
 */
object LyricsPopupNotifier {
    const val CHANNEL_ID = "lyron_lyrics_popup"
    const val NOTIFICATION_ID = 7055

    const val ACTION_DISABLE = "com.tolete.player.LYRICS_POPUP_DISABLE"
    const val ACTION_TOGGLE_LOCK = "com.tolete.player.LYRICS_POPUP_TOGGLE_LOCK"
    const val ACTION_CUSTOMIZE = "com.tolete.player.LYRICS_POPUP_CUSTOMIZE"

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Letras em popup",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controles do overlay de letras"
                setShowBadge(false)
            }
        )
    }

    fun show(context: Context) {
        val ctx = context.applicationContext
        ensureChannel(ctx)
        val locked = LyricsPopupOverlay.isLocked(ctx)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

        fun action(action: String, requestCode: Int): PendingIntent {
            val i = Intent(ctx, LyricsPopupActionReceiver::class.java).setAction(action)
            return PendingIntent.getBroadcast(ctx, requestCode, i, flags)
        }

        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_lyrics_popup)
            .setContentTitle("Letras em popup")
            .setContentText(
                if (locked) "Posição bloqueada · toques atravessam o popup"
                else "Posição livre · arraste o popup para mover"
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "Desligar", action(ACTION_DISABLE, 551))
            .addAction(0, if (locked) "Desbloquear" else "Bloquear", action(ACTION_TOGGLE_LOCK, 552))
            .addAction(0, "Personalizar", action(ACTION_CUSTOMIZE, 553))
            .build()

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notif)
    }

    fun cancel(context: Context) {
        val nm = context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID)
    }
}

class LyricsPopupActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val prefs = context.getSharedPreferences("tolete", Context.MODE_PRIVATE)
        when (action) {
            LyricsPopupNotifier.ACTION_DISABLE -> {
                prefs.edit().putBoolean("lyrics_popup_enabled", false).apply()
                LyricsPopupOverlay.hide()
                LyricsPopupNotifier.cancel(context)
                Toast.makeText(context, "Letras em popup desativadas", Toast.LENGTH_SHORT).show()
            }
            LyricsPopupNotifier.ACTION_TOGGLE_LOCK -> {
                val locked = LyricsPopupOverlay.toggleLocked(context)
                LyricsPopupNotifier.show(context)
                Toast.makeText(
                    context,
                    if (locked) "Posição bloqueada — toques atravessam o popup"
                    else "Posição desbloqueada — arraste para mover",
                    Toast.LENGTH_SHORT
                ).show()
            }
            LyricsPopupNotifier.ACTION_CUSTOMIZE -> {
                val i = Intent(context, LyricsPopupCustomizeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(i)
                } catch (_: Exception) {
                    Toast.makeText(context, "Não foi possível abrir personalização", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
