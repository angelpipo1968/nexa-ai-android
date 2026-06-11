package com.nexa.ai.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.gson.JsonObject
import com.google.gson.Gson

class MorningSummaryService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient.Builder().connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS).build()
                val request = Request.Builder()
                    .url("https://www.nexa-ai.dev/api/weather")
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""
                val json = Gson().fromJson(body, JsonObject::class.java)
                val temp = json.get("temperature")?.asString ?: "--"
                val condition = json.get("condition")?.asString ?: ""
                val city = json.get("city")?.asString ?: ""
                
                val summaryText = "Buenos días! En $city: $temp, $condition. Que tengas un excelente día!"
                
                showSummaryNotification(summaryText)
            } catch (e: Exception) {
                showSummaryNotification("Buenos días! Abre Nexa para tu resumen diario.")
            }
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun showSummaryNotification(text: String) {
        NexaNotificationManager.createChannels(this)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val intent = Intent(this, Class.forName("com.nexa.ai.MainActivity"))
        val pending = android.app.PendingIntent.getActivity(
            this, 9998, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, "nexa_summary")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("NEXA - Resumen Matutino")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        
        manager.notify(9998, notification)
    }
}
