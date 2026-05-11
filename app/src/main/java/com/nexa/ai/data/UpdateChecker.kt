package com.nexa.ai.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val changelog: String,
    val forceUpdate: Boolean = false
)

class UpdateChecker {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * Checks https://www.nexa-ai.dev/api/app-update for new versions.
     * The endpoint should return JSON:
     * {
     *   "versionCode": 2,
     *   "versionName": "2.1",
     *   "downloadUrl": "https://github.com/angelpipo1968/nexa-ai-android/releases/latest",
     *   "changelog": "Login, mejoras de UI...",
     *   "forceUpdate": false
     * }
     */
    suspend fun checkForUpdate(currentVersionCode: Int): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://www.nexa-ai.dev/api/app-update")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val body = response.body?.string() ?: return@withContext null
            val obj = gson.fromJson(body, JsonObject::class.java)

            val remoteVersion = obj.get("versionCode")?.asInt ?: return@withContext null

            if (remoteVersion > currentVersionCode) {
                UpdateInfo(
                    versionCode = remoteVersion,
                    versionName = obj.get("versionName")?.asString ?: "$remoteVersion",
                    downloadUrl = obj.get("downloadUrl")?.asString
                        ?: "https://github.com/angelpipo1968/nexa-ai-android/releases/latest",
                    changelog = obj.get("changelog")?.asString ?: "Nueva versión disponible",
                    forceUpdate = obj.get("forceUpdate")?.asBoolean ?: false
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun openDownloadPage(context: Context, url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
