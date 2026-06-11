package com.nexa.ai.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.widget.RemoteViews
import com.nexa.ai.R
import com.nexa.ai.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.gson.JsonObject
import com.google.gson.Gson

class NexaWeatherWidget : AppWidgetProvider() {

    companion object {
        private const val PREFS_NAME = "nexa_weather_widget"
        private const val KEY_TEMP = "last_temp"
        private const val KEY_CONDITION = "last_condition"
        private const val KEY_CITY = "last_city"
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_weather)
            
            // Load cached weather data
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            views.setTextViewText(R.id.widget_temp, prefs.getString(KEY_TEMP, "--"))
            views.setTextViewText(R.id.widget_condition, prefs.getString(KEY_CONDITION, "NEXA"))
            views.setTextViewText(R.id.widget_city, prefs.getString(KEY_CITY, ""))
            
            // Click to open app
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            val pending = PendingIntent.getActivity(
                context, 2, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_weather_layout, pending)
            
            manager.updateAppWidget(id, views)
        }
        
        // Fetch fresh weather data
        fetchWeather(context, ids, manager)
    }
    
    private fun fetchWeather(context: Context, ids: IntArray, manager: AppWidgetManager) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val city = prefs.getString(KEY_CITY, null)
                
                val client = OkHttpClient.Builder().connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS).build()
                val url = if (city != null) {
                    "https://www.nexa-ai.dev/api/weather?city=$city"
                } else {
                    "https://www.nexa-ai.dev/api/weather"
                }
                
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@launch
                
                val json = Gson().fromJson(body, JsonObject::class.java)
                val temp = json.get("temperature")?.asString ?: "--"
                val condition = json.get("condition")?.asString ?: "NEXA"
                val responseCity = json.get("city")?.asString ?: ""
                
                prefs.edit()
                    .putString(KEY_TEMP, temp)
                    .putString(KEY_CONDITION, condition)
                    .putString(KEY_CITY, responseCity)
                    .apply()
                
                // Update widget views
                for (id in ids) {
                    val views = RemoteViews(context.packageName, R.layout.widget_weather)
                    views.setTextViewText(R.id.widget_temp, temp)
                    views.setTextViewText(R.id.widget_condition, condition)
                    views.setTextViewText(R.id.widget_city, responseCity)
                    manager.updateAppWidget(id, views)
                }
            } catch (e: Exception) {
                android.util.Log.e("NexaWeatherWidget", "Weather fetch failed: ${e.message}")
            }
        }
    }
}
