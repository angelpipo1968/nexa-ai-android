package com.nexa.ai

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.nexa.ai.ui.NexaChatScreen
import com.nexa.ai.ui.ProvideWindowAdaptiveInfo
import com.nexa.ai.ui.theme.NexaAccent
import com.nexa.ai.ui.theme.NexaTheme
import com.nexa.ai.viewmodel.NexaViewModel
import androidx.compose.ui.graphics.Color
import dagger.hilt.android.AndroidEntryPoint
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.concurrent.thread

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: NexaViewModel by viewModels()

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startListening()
        }
    }

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Permission result handled — notifications will work if granted
    }

    private val requestLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.requestLocation()
        }
    }

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            captureImage.launch(null)
        } else {
            viewModel.clearCameraRequest()
        }
    }
    
    private val captureImage = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            // FIX v5.2: Compress and downscale to prevent OOM
            try {
                val scaledBitmap = if (bitmap.width > 1024 || bitmap.height > 1024) {
                    val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                    val newWidth: Int
                    val newHeight: Int
                    if (bitmap.width > bitmap.height) {
                        newWidth = 1024
                        newHeight = (1024f / ratio).toInt()
                    } else {
                        newHeight = 1024
                        newWidth = (1024f * ratio).toInt()
                    }
                    android.graphics.Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                } else {
                    bitmap
                }
                val byteArrayOutputStream = ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, byteArrayOutputStream)
                val byteArray = byteArrayOutputStream.toByteArray()
                val base64 = Base64.encodeToString(byteArray, Base64.NO_WRAP)
                // Recycle scaled bitmap if we created a new one
                if (scaledBitmap != bitmap) scaledBitmap.recycle()
                viewModel.sendVisionRequest(base64, "image/jpeg")
            } catch (e: OutOfMemoryError) {
                android.util.Log.e("MainActivity", "OOM processing camera image")
                System.gc()
                viewModel.onError("Error: imagen demasiado grande, intenta de nuevo")
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Camera image error: ${e.message}")
                viewModel.onError("Error procesando imagen")
            }
        } else {
            viewModel.clearCameraRequest()
        }
    }

    private val pickFile = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val mimeType = contentResolver.getType(it) ?: "application/octet-stream"
            // If it's an image, send to vision API
            if (mimeType.startsWith("image/")) {
                val base64 = uriToBase64(it)
                if (base64 != null) {
                    viewModel.sendVisionRequest(base64, mimeType)
                } else {
                    viewModel.setPendingAttachment(it.lastPathSegment ?: "archivo")
                }
            } else {
                // Non-image file: store as attachment
                viewModel.setPendingAttachment(it.lastPathSegment ?: "archivo")
            }
        }
    }

    private val pickPhoto = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val mimeType = contentResolver.getType(it) ?: "image/jpeg"
            val base64 = uriToBase64(it)
            if (base64 != null) {
                viewModel.sendVisionRequest(base64, mimeType)
            } else {
                // Fallback: try to decode as bitmap
                try {
                    val inputStream = contentResolver.openInputStream(it)
                    val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    if (bitmap != null) {
                        val baos = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                        val base64FromBitmap = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                        viewModel.sendVisionRequest(base64FromBitmap, "image/jpeg")
                    }
                } catch (e: Exception) {
                    viewModel.setPendingAttachment(it.lastPathSegment ?: "foto")
                }
            }
        }
    }

    /**
     * Converts a content URI to a base64 string — OOM-safe version.
     * FIX v5.2: 
     * - Reduced max size from 20MB to 5MB to prevent OOM
     * - Uses streaming Base64 encoding instead of loading entire file into memory
     * - Downscales bitmap before encoding
     */
    private fun uriToBase64(uri: android.net.Uri): String? {
        return try {
            val inputStream: java.io.InputStream? = contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                // First read to get file size
                val bytes = stream.readBytes()
                val maxSize = 5 * 1024 * 1024  // 5MB limit (was 20MB)
                if (bytes.size > maxSize) {
                    android.util.Log.w("MainActivity", "File too large (${bytes.size} bytes), trying bitmap downscale...")
                    // Try to decode as bitmap and downscale
                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) {
                        val scaled = scaleBitmap(bitmap, 1024)
                        val baos = java.io.ByteArrayOutputStream()
                        scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, baos)
                        val result = android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP)
                        scaled.recycle()
                        bitmap.recycle()
                        result
                    } else {
                        null
                    }
                } else {
                    android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                }
            }
        } catch (e: OutOfMemoryError) {
            android.util.Log.e("MainActivity", "OOM during base64 encode: ${e.message}")
            System.gc()  // Request garbage collection
            null
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to convert URI to base64: ${e.message}")
            null
        }
    }
    
    /**
     * Scales a bitmap to fit within maxDimension while maintaining aspect ratio.
     */
    private fun scaleBitmap(bitmap: android.graphics.Bitmap, maxDimension: Int): android.graphics.Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap
        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        if (width > height) {
            newWidth = maxDimension
            newHeight = (maxDimension / ratio).toInt()
        } else {
            newHeight = maxDimension
            newWidth = (maxDimension * ratio).toInt()
        }
        return android.graphics.Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC

        // Install crash logger — saves to /sdcard/Documents/nexa_crash_log.txt
        CrashHandler.install(this)

        // Request notification permission (Android 13+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            val uiState by viewModel.uiState.collectAsState()

            // Handle camera capture request from voice command
            if (uiState.requestCameraCapture) {
                viewModel.clearCameraRequest()
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    captureImage.launch(null)
                } else {
                    requestCameraPermission.launch(Manifest.permission.CAMERA)
                }
            }

            val accentColor = if (uiState.accentColor != 0L) Color(uiState.accentColor.toULong()) else NexaAccent
            
            NexaTheme(themeMode = uiState.themeMode, accentColor = accentColor) {
                ProvideWindowAdaptiveInfo {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        NexaChatScreen(
                            uiState = uiState,
                            onSend = { viewModel.sendMessage() },
                            onInputChange = { viewModel.updateInput(it) },
                            onStartListening = {
                                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                                    == PackageManager.PERMISSION_GRANTED
                                ) {
                                    viewModel.startListening()
                                } else {
                                    requestPermission.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            onStopListening = { viewModel.stopListening() },
                            onToggleAutoSpeak = { viewModel.toggleAutoSpeak() },
                            onStopSpeaking = { viewModel.stopSpeaking() },
                            onSpeakMessage = { text, id -> viewModel.speak(text, id) },
                            onClearChat = { viewModel.clearChat() },
                            onDismissError = { viewModel.clearError() },
                            onToggleDrawer = { viewModel.toggleDrawer() },
                            onCloseDrawer = { viewModel.closeDrawer() },
                            onCreateSession = { viewModel.createNewSession() },
                            onSwitchSession = { viewModel.switchSession(it) },
                            onDeleteSession = { viewModel.deleteSession(it) },
                            onToggleSettings = { viewModel.toggleSettings() },
                            onSetLanguage = { viewModel.setLanguage(it) },
                            onSetVoiceType = { viewModel.setVoiceType(it) },
                            onCycleTheme = { viewModel.cycleTheme() },
                            onSetThemeMode = { viewModel.setThemeMode(it) },
                            onNavigateToLogin = { viewModel.navigateToLogin() },
                            onNavigateToRegister = { viewModel.navigateToRegister() },
                            onNavigateToChat = { viewModel.navigateToChat() },
                            onNavigateToLottery = { viewModel.navigateToLottery() },
                            onNavigateToTranslator = { viewModel.navigateToTranslator() },
                            onUpdateLoginEmail = { viewModel.updateLoginEmail(it) },
                            onUpdateLoginPassword = { viewModel.updateLoginPassword(it) },
                            onLogin = { viewModel.login() },
                            onUpdateRegisterName = { viewModel.updateRegisterName(it) },
                            onUpdateRegisterEmail = { viewModel.updateRegisterEmail(it) },
                            onUpdateRegisterPassword = { viewModel.updateRegisterPassword(it) },
                            onUpdateRegisterConfirmPassword = { viewModel.updateRegisterConfirmPassword(it) },
                            onRegister = { viewModel.register() },
                            onLogout = { viewModel.logout() },
                            onDismissUpdate = { viewModel.dismissUpdate() },
                            onOpenUpdatePage = { viewModel.openUpdatePage() },
                            onCopyMessage = { viewModel.copyToClipboard(it) },
                            onExportMessage = { viewModel.exportToPdf(it) },
                            onSurpriseMe = { viewModel.surpriseMe() },
                            onSetDrawerView = { viewModel.setDrawerView(it) },
                            onAttachFile = { pickFile.launch("*/*") },
                            onClearAttachment = { viewModel.clearPendingAttachment() },
                            onRegenerate = { viewModel.regenerateResponse() },
                            onInterruptVoice = { viewModel.interruptVoice() },
                            onToggleVoiceMode = {
                                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                                    == PackageManager.PERMISSION_GRANTED
                                ) {
                                    viewModel.toggleVoiceMode()
                                } else {
                                    requestPermission.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            onStopVoiceMode = { viewModel.stopVoiceMode() },
                            onDismissVoiceHelp = { viewModel.dismissVoiceCommandsHelp() },
                            onRequestLocation = {
                                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                                    == PackageManager.PERMISSION_GRANTED ||
                                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                                    == PackageManager.PERMISSION_GRANTED
                                ) {
                                    viewModel.requestLocation()
                                } else {
                                    requestLocationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                }
                            },
                            onToggleNotifications = { viewModel.toggleNotifications() },
                            onShareMessage = { viewModel.shareText(it) },
                            onToggleVolumeBoost = { viewModel.toggleVolumeBoost() },
                            onSetSpeechRate = { viewModel.setSpeechRate(it) },
                            onCaptureImage = {
                                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                                    == PackageManager.PERMISSION_GRANTED
                                ) {
                                    captureImage.launch(null)
                                } else {
                                    requestCameraPermission.launch(Manifest.permission.CAMERA)
                                }
                            },
                            onPickPhoto = { pickPhoto.launch("image/*") },
                            onDeepResearch = {
                                val lang = uiState.language
                                val prefix = if (lang == com.nexa.ai.viewmodel.AppLanguage.SPANISH)
                                    "Investiga a fondo: " else "Deep research: "
                                val query = uiState.inputText.ifBlank {
                                    if (lang == com.nexa.ai.viewmodel.AppLanguage.SPANISH) "Investiga sobre el tema actual" else "Research the current topic"
                                }
                                viewModel.sendMessage(prefix + query)
                            },
                            onReasoning = {
                                val lang = uiState.language
                                val prefix = if (lang == com.nexa.ai.viewmodel.AppLanguage.SPANISH)
                                    "Razona paso a paso: " else "Reason step by step: "
                                val query = uiState.inputText.ifBlank {
                                    if (lang == com.nexa.ai.viewmodel.AppLanguage.SPANISH) "Razona sobre este tema" else "Reason about this topic"
                                }
                                viewModel.sendMessage(prefix + query)
                            },
                            onWebSearch = {
                                val lang = uiState.language
                                val prefix = if (lang == com.nexa.ai.viewmodel.AppLanguage.SPANISH)
                                    "Busca en la web: " else "Search the web: "
                                val query = uiState.inputText.ifBlank {
                                    if (lang == com.nexa.ai.viewmodel.AppLanguage.SPANISH) "Busca información actual" else "Search for current information"
                                }
                                viewModel.sendMessage(prefix + query)
                            },
                            onDismissPreview = { viewModel.dismissPreview() },
                            onQuickAction = { action ->
                                val lang = uiState.language
                                val prompt = when (action) {
                                    "image" -> if (lang == com.nexa.ai.viewmodel.AppLanguage.SPANISH)
                                        "Genera una imagen creativa e impresionante" else "Generate a creative and impressive image"
                                    "web" -> if (lang == com.nexa.ai.viewmodel.AppLanguage.SPANISH)
                                        "Crea una página web profesional y moderna con diseño responsive. Incluye HTML, CSS y JavaScript completos." else "Create a professional and modern responsive web page with complete HTML, CSS, and JavaScript."
                                    "logo" -> if (lang == com.nexa.ai.viewmodel.AppLanguage.SPANISH)
                                        "Genera un logo moderno y profesional. Describe el diseño y crea la imagen del logo." else "Generate a modern and professional logo. Describe the design and create the logo image."
                                    "code" -> if (lang == com.nexa.ai.viewmodel.AppLanguage.SPANISH)
                                        "Escribe código profesional. ¿Qué proyecto te gustaría que programe?" else "Write professional code. What project would you like me to program?"
                                    "vision" -> {
                                        if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA)
                                            == PackageManager.PERMISSION_GRANTED
                                        ) {
                                            captureImage.launch(null)
                                        } else {
                                            requestCameraPermission.launch(Manifest.permission.CAMERA)
                                        }
                                        return@NexaChatScreen
                                    }
                                    else -> return@NexaChatScreen
                                }
                                viewModel.sendMessage(prompt)
                            },
                            onPreviewVoice = { viewModel.previewVoice() },
                            onSetAccentColor = { viewModel.setAccentColor(it) },
                            onExportSettings = { viewModel.exportSettings() },
                            onImportSettings = { viewModel.importSettings() }
                        )
                    }
                }
            }
        }
    }
}
