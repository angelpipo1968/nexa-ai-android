package com.nexa.ai.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.nexa.ai.MainActivity
import com.nexa.ai.R
import java.util.Calendar

object NexaNotificationManager {
    
    private const val CHANNEL_REMINDERS = "nexa_reminders"
    private const val CHANNEL_WEATHER = "nexa_weather"
    private const val CHANNEL_SUMMARY = "nexa_summary"
    
    fun createChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val channels = listOf(
            NotificationChannel(
                CHANNEL_REMINDERS,
                "Recordatorios de Nexa",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Recordatorios configurados por voz"
                enableVibration(true)
            },
            NotificationChannel(
                CHANNEL_WEATHER,
                "Alertas del Clima",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alertas de lluvia y condiciones climáticas"
            },
            NotificationChannel(
                CHANNEL_SUMMARY,
                "Resumen Matutino",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Resumen diario de clima, noticias y agenda"
            }
        )
        
        manager.createNotificationChannels(channels)
    }
    
    fun scheduleReminder(context: Context, text: String, timeMillis: Long, id: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("reminder_text", text)
            putExtra("reminder_id", id)
        }
        
        val pending = PendingIntent.getBroadcast(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pending)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pending)
        }
    }
    
    fun showReminderNotification(context: Context, text: String, id: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannels(context)
        
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pending = PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("NEXA Recordatorio")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        
        manager.notify(id, notification)
    }
    
    fun scheduleMorningSummary(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 7)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) add(Calendar.DAY_OF_YEAR, 1)
        }
        
        val intent = Intent(context, MorningSummaryService::class.java)
        val pending = PendingIntent.getService(
            context, 9999, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Schedule repeating daily at 7 AM
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pending
        )
    }
}
