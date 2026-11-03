package com.tolete.player.video

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.tolete.player.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Serviço em primeiro plano que mantém o processo vivo durante a geração do vídeo e mostra
 * a notificação de progresso (com botão Cancelar). Ao terminar remove a notificação de progresso
 * e, se o app estiver minimizado, avisa que o vídeo foi salvo (ou que falhou).
 */
class LyricsVideoService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            LyricsVideoGenerator.cancel()
            return START_NOT_STICKY
        }

        ensureChannel()
        val current = LyricsVideoGenerator.state.value as? LyricsVideoGenerator.State.Running
        val first = progressNotification(current?.trackTitle ?: "", current?.stage ?: "Preparando…", current?.progress ?: 0f)
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ServiceCompat.startForeground(
                    this, NOTIF_PROGRESS, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIF_PROGRESS, first)
            }
        } catch (_: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (watcher?.isActive != true) {
            watcher = scope.launch {
                LyricsVideoGenerator.state.collect { st ->
                    when (st) {
                        is LyricsVideoGenerator.State.Running -> notify(
                            NOTIF_PROGRESS, progressNotification(st.trackTitle, st.stage, st.progress)
                        )
                        is LyricsVideoGenerator.State.Saved -> {
                            finishWith(
                                "Vídeo salvo",
                                "${st.trackTitle} · Lyron/Lyrics Videos/${st.file.name}"
                            )
                        }
                        is LyricsVideoGenerator.State.Failed -> {
                            val bg = !LyricsVideoGenerator.isAppForeground()
                            finishWith(if (bg) "Falha ao gerar o vídeo" else null, st.message)
                        }
                        is LyricsVideoGenerator.State.Done, LyricsVideoGenerator.State.Idle ->
                            finishWith(null, null)
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun finishWith(title: String?, text: String?) {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        if (title != null) {
            notify(
                NOTIF_RESULT,
                NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setContentIntent(openAppIntent())
                    .setAutoCancel(true)
                    .build()
            )
        }
        stopSelf()
    }

    private fun progressNotification(title: String, stage: String, progress: Float): Notification {
        val pct = (progress * 100).toInt().coerceIn(0, 100)
        val cancel = PendingIntent.getService(
            this, 1,
            Intent(this, LyricsVideoService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Gerando vídeo com letra")
            .setContentText(if (title.isBlank()) "$stage $pct%" else "$title · $stage $pct%")
            .setProgress(100, pct, false)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openAppIntent())
            .addAction(0, "Cancelar", cancel)
            .build()
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun notify(id: Int, n: Notification) {
        try {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(id, n)
        } catch (_: Exception) { }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Vídeo com letra", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL_ID = "lyron_lyrics_video"
        const val NOTIF_PROGRESS = 7301
        const val NOTIF_RESULT = 7302
        const val ACTION_CANCEL = "com.tolete.player.action.CANCEL_LYRICS_VIDEO"
    }
}
