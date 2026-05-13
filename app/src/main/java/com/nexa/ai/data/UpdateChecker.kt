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

    companion object {
        // Check GitHub releases directly — no server needed
        private const val GITHUB_RELEASES_URL =
            "https://api.github.com/repos/angelpipo1968/nexa-ai-android/releases/latest"
        private const val FALLBACK_DOWNLOAD_URL =
            "https://github.com/angelpipo1968/nexa-ai-android/releases/latest"
    }

    /**
     * Checks GitHub Releases for new versions.
     * Compares remote versionName (e.g. "3.2") against current versionName (e.g. "3.1").
     * When a new release is found, returns UpdateInfo with the APK download URL.
     */
    suspend fun checkForUpdate(currentVersionCode: Int, currentVersionName: String = ""): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(GITHUB_RELEASES_URL)
                .addHeader("Accept", "application/vnd.github+json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val body = response.body?.string() ?: return@withContext null
            val obj = gson.fromJson(body, JsonObject::class.java)

            val tagName = obj.get("tag_name")?.asString ?: return@withContext null
            val remoteVersionName = tagName.removePrefix("v")

            // Compare version strings: "3.2" > "3.1", "4.0" > "3.9"
            val remoteParts = remoteVersionName.split(".").map { it.toIntOrNull() ?: 0 }
            val localParts = currentVersionName.split(".").map { it.toIntOrNull() ?: 0 }
            val maxLen = maxOf(remoteParts.size, localParts.size)

            var isNewer = false
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val l = localParts.getOrElse(i) { 0 }
                if (r > l) { isNewer = true; break }
                if (r < l) { break }
            }

            if (isNewer) {
                // Find APK download URL from assets
                val assets = obj.getAsJsonArray("assets")
                var apkUrl = FALLBACK_DOWNLOAD_URL
                if (assets != null) {
                    for (asset in assets) {
                        val assetObj = asset.asJsonObject
                        val name = assetObj.get("name")?.asString ?: ""
                        if (name.endsWith(".apk")) {
                            apkUrl = assetObj.get("browser_download_url")?.asString ?: FALLBACK_DOWNLOAD_URL
                            break
                        }
                    }
                }

                val changelog = obj.get("body")?.asString ?: "Nueva versión disponible"

                UpdateInfo(
                    versionCode = currentVersionCode + 1,
                    versionName = remoteVersionName,
                    downloadUrl = apkUrl,
                    changelog = changelog.take(500),
                    forceUpdate = false
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
