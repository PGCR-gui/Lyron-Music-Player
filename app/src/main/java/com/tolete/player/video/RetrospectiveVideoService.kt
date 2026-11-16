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
 * Serviço em primeiro plano que mantém o processo vivo enquanto os vídeos da retrospectiva são
 * renderizados (10 vídeos 1080×1920 pesam bastante) e mostra a notificação de progresso.
 */
class RetrospectiveVideoService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            RetrospectiveVideoGenerator.cancel()
            return START_NOT_STICKY
        }

        ensureChannel()
        val current = RetrospectiveVideoGenerator.state.value as? RetrospectiveVideoGenerator.State.Running
        val first = progressNotification(current)
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
                RetrospectiveVideoGenerator.state.collect { st ->
                    when (st) {
                        is RetrospectiveVideoGenerator.State.Running ->
                            notify(NOTIF_PROGRESS, progressNotification(st))
                        is RetrospectiveVideoGenerator.State.Finished -> {
                            val n = st.result.videos.size
                            val cancelled = st.result.failed.any { it.equals("cancelado", ignoreCase = true) }
                            val failed = st.result.failed.filterNot { it.equals("cancelado", ignoreCase = true) }
                            val title = when {
                                cancelled && n > 0 -> "Geração cancelada parcialmente"
                                cancelled -> "Geração cancelada"
                                n > 0 -> "Vídeo(s) pronto(s)"
                                else -> "Falha ao gerar vídeo(s)"
                            }
                            val text = when {
                                cancelled && n > 0 -> "$n vídeo(s) concluído(s) antes do cancelamento. Abra o app para salvar ou compartilhar."
                                cancelled -> "A geração foi cancelada antes de concluir um vídeo."
                                n > 0 && failed.isNotEmpty() -> "$n vídeo(s) gerado(s); ${failed.size} card(s) falharam. Abra o app para ver os resultados."
                                n > 0 -> "$n arquivo(s) de vídeo gerado(s). Abra o app para salvar ou compartilhar."
                                else -> failed.firstOrNull() ?: "erro desconhecido"
                            }
                            finishWith(
                                title,
                                text
                            )
                        }
                        is RetrospectiveVideoGenerator.State.Failed ->
                            if (st.message == "cancelado") finishWith(null, null)
                            else finishWith("Falha ao gerar vídeo(s)", st.message)
                        RetrospectiveVideoGenerator.State.Idle ->
                            finishWith(null, null)
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun finishWith(title: String?, text: String?) {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        if (title != null && text != null) {
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

    private fun progressNotification(running: RetrospectiveVideoGenerator.State.Running?): Notification {
        val safeTotal = running?.total?.coerceAtLeast(1) ?: 1
        val overall = ((running?.overallFrac ?: 0f).coerceIn(0f, 1f) * 100).toInt()
        val item = if (running != null && running.batch) {
            "Card ${(running.index + 1).coerceAtMost(safeTotal)}/$safeTotal · ${running.title}"
        } else running?.title.orEmpty()
        val detail = listOf(item, running?.stage.orEmpty(), "$overall%")
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        val cancel = PendingIntent.getService(
            this, 2,
            Intent(this, RetrospectiveVideoService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(if (running?.batch == true) "Gerando vídeos do Lyron" else "Gerando vídeo do Lyron")
            .setContentText(detail)
            .setProgress(100, overall, false)
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
                    NotificationChannel(CHANNEL_ID, "Geração de vídeos", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL_ID = "lyron_retro_video"
        const val NOTIF_PROGRESS = 7311
        const val NOTIF_RESULT = 7312
        const val ACTION_CANCEL = "com.tolete.player.action.CANCEL_RETRO_VIDEO"
    }
}
