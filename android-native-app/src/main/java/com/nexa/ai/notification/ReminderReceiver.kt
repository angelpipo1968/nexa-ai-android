package com.nexa.ai.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra("reminder_text") ?: return
        val id = intent.getIntExtra("reminder_id", 0)
        NexaNotificationManager.showReminderNotification(context, text, id)
    }
}
