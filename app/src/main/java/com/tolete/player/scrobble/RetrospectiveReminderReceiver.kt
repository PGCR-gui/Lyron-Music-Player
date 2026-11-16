package com.tolete.player.scrobble

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Recebe o alarme local das 20h e executa a verificação sem depender da Activity aberta. */
class RetrospectiveReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == RetrospectiveReminder.ACTION_CHECK_REMINDER) {
            RetrospectiveReminder.checkAndNotify(context)
        }
    }
}
