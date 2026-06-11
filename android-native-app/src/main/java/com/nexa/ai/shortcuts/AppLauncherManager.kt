package com.nexa.ai.shortcuts

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.AlarmClock
import android.net.Uri
import android.util.Log

/**
 * AppLauncherManager — Handles voice commands to open apps, set alarms, make calls.
 * Triggered by voice commands in NexaViewModel.
 */
class AppLauncherManager(private val context: Context) {

    companion object {
        private const val TAG = "NexaAppLauncher"
        
        // Common app package mappings
        private val APP_PACKAGES = mapOf(
            // Spanish names
            "whatsapp" to "com.whatsapp",
            "facebook" to "com.facebook.katana",
            "instagram" to "com.instagram.android",
            "twitter" to "com.twitter.android",
            "x" to "com.twitter.android",
            "youtube" to "com.google.android.youtube",
            "spotify" to "com.spotify.music",
            "netflix" to "com.netflix.mediaclient",
            "tiktok" to "com.zhiliaoapp.musically",
            "telegram" to "org.telegram.messenger",
            "google maps" to "com.google.android.apps.maps",
            "mapas" to "com.google.android.apps.maps",
            "gmail" to "com.google.android.gm",
            "correo" to "com.google.android.gm",
            "chrome" to "com.android.chrome",
            "navegador" to "com.android.chrome",
            "cámara" to "com.android.camera",
            "calculadora" to "com.android.calculator2",
            "reloj" to "com.android.clock",
            "calendario" to "com.google.android.calendar",
            "contactos" to "com.android.contacts",
            "teléfono" to "com.android.dialer",
            "ajustes" to "com.android.settings",
            "configuración" to "com.android.settings",
            "play store" to "com.android.vending",
            "tienda" to "com.android.vending",
            // English names
            "browser" to "com.android.chrome",
            "camera" to "com.android.camera",
            "calculator" to "com.android.calculator2",
            "clock" to "com.android.clock",
            "calendar" to "com.google.android.calendar",
            "contacts" to "com.android.contacts",
            "phone" to "com.android.dialer",
            "settings" to "com.android.settings",
            "store" to "com.android.vending",
            "maps" to "com.google.android.apps.maps",
            "email" to "com.google.android.gm",
        )
    }

    /**
     * Open an app by name.
     * @return true if the app was found and opened, false otherwise
     */
    fun openApp(appName: String): Boolean {
        val packageName = findAppPackage(appName)
        
        if (packageName != null) {
            return try {
                val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    Log.i(TAG, "Opened app: $appName ($packageName)")
                    true
                } else {
                    // App installed but no launch intent
                    openPlayStore(packageName)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to open app $appName: ${e.message}")
                openPlayStore(packageName)
            }
        }
        
        // Try searching by name in Play Store
        return openPlayStoreSearch(appName)
    }

    /**
     * Set an alarm.
     * @param hour Hour (0-23)
     * @param minute Minute (0-59)
     * @param message Optional alarm message
     * @return true if alarm was set successfully
     */
    fun setAlarm(hour: Int, minute: Int, message: String = "Alarma Nexa"): Boolean {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.i(TAG, "Alarm set for $hour:$minute - $message")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set alarm: ${e.message}")
            false
        }
    }

    /**
     * Set a timer.
     * @param seconds Duration in seconds
     * @param message Optional timer message
     * @return true if timer was set successfully
     */
    fun setTimer(seconds: Int, message: String = "Timer Nexa"): Boolean {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set timer: ${e.message}")
            false
        }
    }

    /**
     * Make a phone call.
     * @param phoneNumber The phone number to call
     * @return true if the call intent was started
     */
    fun makeCall(phoneNumber: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phoneNumber")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to make call: ${e.message}")
            false
        }
    }

    /**
     * Open a URL in the browser.
     */
    fun openUrl(url: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(if (url.startsWith("http")) url else "https://$url")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open URL: ${e.message}")
            false
        }
    }

    // ─── Private Helpers ─────────────────────────

    private fun findAppPackage(name: String): String? {
        val lower = name.lowercase().trim()
        // Direct match
        if (APP_PACKAGES.containsKey(lower)) return APP_PACKAGES[lower]
        // Fuzzy match
        return APP_PACKAGES.keys.firstOrNull { key -> 
            lower.contains(key) || key.contains(lower) 
        }?.let { APP_PACKAGES[it] }
    }

    private fun openPlayStore(packageName: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://details?id=$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            // Play Store not available, open in browser
            openUrl("https://play.google.com/store/apps/details?id=$packageName")
        }
    }

    private fun openPlayStoreSearch(query: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://search?q=$query")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            openUrl("https://play.google.com/store/search?q=$query")
        }
    }
}
