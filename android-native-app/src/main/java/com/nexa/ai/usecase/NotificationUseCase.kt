package com.nexa.ai.usecase

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.nexa.ai.notification.ReminderReceiver
import java.util.Calendar

/**
 * NotificationUseCase — Handles scheduling and managing notifications.
 * Extracted from NexaViewModel to decouple notification logic from the ViewModel.
 *
 * This use case encapsulates all Android AlarmManager and notification scheduling
 * operations, including:
 * - Scheduling reminder notifications via AlarmManager
 * - Parsing time strings from voice commands into epoch millis
 * - Parsing timer durations from voice commands
 *
 * By extracting this logic, the ViewModel no longer needs direct access to
 * AlarmManager or Context for notification operations, and these operations
 * can be tested in isolation with a mocked Context.
 */
class NotificationUseCase(private val context: Context) {

    /**
     * Schedule a reminder notification.
     *
     * Handles SDK compatibility:
     * - On Android S+ (API 31+): checks canScheduleExactAlarms() before using exact alarms
     * - Falls back to inexact alarms when exact alarm permission is not granted
     * - Uses FLAG_IMMUTABLE for security on all API levels
     *
     * @param text Reminder text to display in the notification
     * @param timeMillis When to show the notification (epoch millis)
     * @param id Unique notification ID for PendingIntent differentiation
     */
    fun scheduleReminder(text: String, timeMillis: Long, id: Int) {
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

    /**
     * Parse a time string from voice command.
     *
     * Supported formats:
     * - "a las 7:30" / "at 7:30" → 7:30 today or tomorrow
     * - "a las 5 de la tarde" / "at 3pm" → 17:00 or 15:00 today or tomorrow
     * - "a las 7" / "at 3" → 7:00 or 3:00 today or tomorrow
     *
     * If the parsed time has already passed today, it automatically
     * schedules for tomorrow.
     *
     * @param text Voice command text containing a time reference
     * @return epoch millis for the next occurrence of that time, or null if parsing fails
     */
    fun parseVoiceTime(text: String): Long? {
        // Simple HH:mm pattern
        val timeRegex = Regex("(\\d{1,2}):(\\d{2})")
        val match = timeRegex.find(text)

        if (match != null) {
            val hour = match.groupValues[1].toInt().coerceIn(0, 23)
            val minute = match.groupValues[2].toInt().coerceIn(0, 59)
            return getNextTimeMillis(hour, minute)
        }

        // Just hour pattern: "a las 7", "at 3"
        val hourRegex = Regex("(\\d{1,2})")
        val hourMatch = hourRegex.find(text.replace(Regex("(alarma|alarm|a las|at)"), "").trim())

        if (hourMatch != null) {
            val hour = hourMatch.groupValues[1].toInt().coerceIn(0, 23)
            val isPM = text.contains("tarde") || text.contains("pm") || text.contains("noche")
            val adjustedHour = if (isPM && hour < 12) hour + 12 else hour
            return getNextTimeMillis(adjustedHour, 0)
        }

        return null
    }

    /**
     * Parse duration from voice command for timer.
     *
     * Supported formats:
     * - "5 minutos" / "5 minutes" / "5 mins" → 300 seconds
     * - "30 seconds" / "30 segundos" / "30 secs" → 30 seconds
     * - "1 minute 30 seconds" / "1 minuto 30 segundos" → 90 seconds
     *
     * @param text Voice command text containing a duration
     * @return duration in seconds, or 0 if parsing fails
     */
    fun parseTimerDuration(text: String): Int {
        val minuteRegex = Regex("(\\d+)\\s*(minutos?|minutes?|mins?)")
        val minMatch = minuteRegex.find(text)
        val secondsRegex = Regex("(\\d+)\\s*(segundos?|seconds?|secs?)")
        val secMatch = secondsRegex.find(text)

        return (minMatch?.groupValues?.get(1)?.toIntOrNull()?.times(60) ?: 0) +
               (secMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0)
    }

    /**
     * Calculate the next occurrence of the given hour and minute.
     * If the time has already passed today, returns tomorrow's occurrence.
     *
     * @param hour Hour of day (0-23)
     * @param minute Minute of hour (0-59)
     * @return epoch millis for the next occurrence
     */
    private fun getNextTimeMillis(hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return calendar.timeInMillis
    }
}
