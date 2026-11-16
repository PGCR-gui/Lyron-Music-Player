package com.tolete.player.scrobble

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restaura o lembrete depois de reinicialização, atualização ou mudança do relógio/fuso. */
class RetrospectiveReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> RetrospectiveReminder.checkAndNotify(context)
        }
    }
}
