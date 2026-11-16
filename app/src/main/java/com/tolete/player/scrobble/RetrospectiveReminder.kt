package com.tolete.player.scrobble

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tolete.player.MainActivity
import com.tolete.player.R
import java.util.Calendar

/**
 * Lembra o usuário de gerar retrospectivas semanal / mensal / anual.
 * Os lembretes são verificados ao abrir o app e por um alarme local às 20h,
 * inclusive quando o processo do app estiver encerrado.
 */
object RetrospectiveReminder {

    const val ACTION_OPEN_RETROSPECTIVE = "com.tolete.player.OPEN_RETROSPECTIVE_CHOICE"
    const val EXTRA_PERIOD = "period" // weekly | monthly | yearly
    const val ACTION_CHECK_REMINDER = "com.tolete.player.CHECK_RETROSPECTIVE_REMINDER"

    private const val CHANNEL_ID = "lyron_retrospective"
    private const val PREFS = "lyron_retrospective_reminders"
    private const val KEY_LAST_WEEKLY = "last_weekly_id"
    private const val KEY_LAST_MONTHLY = "last_monthly_id"
    private const val KEY_LAST_YEARLY = "last_yearly_id"
    private const val REQUEST_CHECK = 7104

    private const val ID_WEEKLY = 7101
    private const val ID_MONTHLY = 7102
    private const val ID_YEARLY = 7103

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Retrospectivas",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Lembretes semanais, mensais e anuais para exportar a retrospectiva"
                }
            )
        }
    }

    /** Avalia todos os períodos vencidos de hoje e agenda o próximo horário de lembrete. */
    fun checkAndNotify(ctx: Context) {
        val app = ctx.applicationContext
        val now = Calendar.getInstance()
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        if (now.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY && now.get(Calendar.HOUR_OF_DAY) >= 20) {
            val weekId = "%04d-W%02d".format(now.get(Calendar.YEAR), now.get(Calendar.WEEK_OF_YEAR))
            if (prefs.getString(KEY_LAST_WEEKLY, null) != weekId && show(
                    app, ID_WEEKLY, "weekly", "Retrospectiva da semana",
                    "Bora dar uma olhada em como foi sua semana?"
                )
            ) {
                prefs.edit().putString(KEY_LAST_WEEKLY, weekId).apply()
            }
        }

        val lastDayOfMonth = now.getActualMaximum(Calendar.DAY_OF_MONTH)
        if (now.get(Calendar.DAY_OF_MONTH) == lastDayOfMonth && now.get(Calendar.HOUR_OF_DAY) >= 20) {
            val monthId = "%04d-%02d".format(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1)
            if (prefs.getString(KEY_LAST_MONTHLY, null) != monthId && show(
                    app, ID_MONTHLY, "monthly", "Retrospectiva do mês",
                    "O mês passou voando né? Bora ver se tu bateu o record do mês passado?"
                )
            ) {
                prefs.edit().putString(KEY_LAST_MONTHLY, monthId).apply()
            }
        }

        if (now.get(Calendar.MONTH) == Calendar.DECEMBER &&
            now.get(Calendar.DAY_OF_MONTH) == 31 && now.get(Calendar.HOUR_OF_DAY) >= 20
        ) {
            val yearId = now.get(Calendar.YEAR).toString()
            if (prefs.getString(KEY_LAST_YEARLY, null) != yearId && show(
                    app, ID_YEARLY, "yearly", "Retrospectiva do ano",
                    "Foi um ano com muitos baixos e alguns altos, mas pelo menos na Música foram só altos. Que tal relembrar?"
                )
            ) {
                prefs.edit().putString(KEY_LAST_YEARLY, yearId).apply()
            }
        }

        scheduleNext(app, Calendar.getInstance())
    }

    private fun scheduleNext(ctx: Context, now: Calendar) {
        // Inspeciona datas locais futuras; vários períodos podem coincidir no mesmo sábado.
        val day = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        var nextTrigger: Long? = null
        for (offset in 0..370) {
            val candidate = (day.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, 20)
            }
            if (!candidate.after(now)) continue

            val lastDay = candidate.getActualMaximum(Calendar.DAY_OF_MONTH)
            val isReminderDate = candidate.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
                candidate.get(Calendar.DAY_OF_MONTH) == lastDay ||
                (candidate.get(Calendar.MONTH) == Calendar.DECEMBER &&
                    candidate.get(Calendar.DAY_OF_MONTH) == 31)
            if (isReminderDate) {
                nextTrigger = candidate.timeInMillis
                break
            }
        }

        val triggerAt = nextTrigger ?: return
        val alarmIntent = Intent(ctx, RetrospectiveReminderReceiver::class.java).apply {
            action = ACTION_CHECK_REMINDER
        }
        val pending = PendingIntent.getBroadcast(
            ctx,
            REQUEST_CHECK,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarms = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Não exige permissão de alarme exato; pode haver pequeno atraso imposto pelo Android.
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
    }

    private fun show(
        ctx: Context,
        notifId: Int,
        period: String,
        title: String,
        text: String
    ): Boolean {
        ensureChannel(ctx)
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .getNotificationChannel(CHANNEL_ID)
            if (channel == null || channel.importance == NotificationManager.IMPORTANCE_NONE) return false
        }

        val open = PendingIntent.getActivity(
            ctx,
            notifId,
            Intent(ctx, MainActivity::class.java).apply {
                action = ACTION_OPEN_RETROSPECTIVE
                putExtra(EXTRA_PERIOD, period)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_calendar)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .build()
        return try {
            NotificationManagerCompat.from(ctx).notify(notifId, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
