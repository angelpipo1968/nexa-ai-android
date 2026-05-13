package com.nexa.ai.data

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

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

                val changelog = obj.get("body")?.asString ?: "Nueva versi\u00f3n disponible"

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

    /**
     * Downloads APK via DownloadManager and triggers install when complete.
     * Returns true if download was initiated successfully.
     */
    fun downloadAndInstall(context: Context, url: String, versionName: String): Boolean {
        return try {
            val fileName = "nexa-pro-v$versionName.apk"

            // Delete old APK if exists
            val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val existingFile = File(downloadsDir, fileName)
            if (existingFile.exists()) existingFile.delete()

            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("NEXA PRO v$versionName")
                .setDescription("Descargando actualizaci\u00f3n...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
                .setMimeType("application/vnd.android.package-archive")

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = dm.enqueue(request)

            // Register receiver to handle download completion
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        ctx.unregisterReceiver(this)
                        installApk(ctx, File(downloadsDir, fileName))
                    }
                }
            }

            val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }

            true
        } catch (e: Exception) {
            android.util.Log.e("UpdateChecker", "Download failed", e)
            // Fallback: open in browser
            openDownloadPage(context, url)
            false
        }
    }

    private fun installApk(context: Context, apkFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = uri
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
            }

            context.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("UpdateChecker", "Install failed", e)
            // Fallback: open browser
            openDownloadPage(context, FALLBACK_DOWNLOAD_URL)
        }
    }

    fun openDownloadPage(context: Context, url: String) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}
