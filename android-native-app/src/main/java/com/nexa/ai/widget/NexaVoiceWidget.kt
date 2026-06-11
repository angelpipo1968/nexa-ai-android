package com.nexa.ai.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.nexa.ai.R
import com.nexa.ai.MainActivity

class NexaVoiceWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_START_VOICE = "com.nexa.ai.ACTION_START_VOICE"
        const val EXTRA_VOICE_MODE = "voice_mode"
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_voice)
            
            // Click to open app in voice mode
            val voiceIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_START_VOICE
                putExtra(EXTRA_VOICE_MODE, true)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val voicePending = PendingIntent.getActivity(
                context, 0, voiceIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_voice_btn, voicePending)
            
            manager.updateAppWidget(id, views)
        }
    }
}
