package com.nexa.ai.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.nexa.ai.R
import com.nexa.ai.MainActivity

class NexaChatWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_QUICK_CHAT = "com.nexa.ai.ACTION_QUICK_CHAT"
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_chat)
            
            val chatIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_QUICK_CHAT
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val chatPending = PendingIntent.getActivity(
                context, 1, chatIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_chat_btn, chatPending)
            
            manager.updateAppWidget(id, views)
        }
    }
}
