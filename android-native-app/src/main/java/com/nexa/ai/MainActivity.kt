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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.nexa.ai.ui.NexaChatScreen
import com.nexa.ai.ui.ProvideWindowAdaptiveInfo
import com.nexa.ai.ui.theme.LocalAccentColor
import com.nexa.ai.ui.theme.NexaAccent
import com.nexa.ai.ui.theme.NexaTheme
import com.nexa.ai.viewmodel.NexaViewModel
import androidx.compose.ui.graphics.Color
import java.io.ByteArrayOutputStream

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
            // Convert bitmap to base64 for vision API
            val byteArrayOutputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
            val byteArray = byteArrayOutputStream.toByteArray()
            val base64 = Base64.encodeToString(byteArray, Base64.NO_WRAP)
            viewModel.sendVisionRequest(base64)
        } else {
            viewModel.clearCameraRequest()
        }
    }

    private val pickFile = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val fileName = it.lastPathSegment ?: "archivo"
            viewModel.setPendingAttachment(fileName)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

            val effectiveAccent = if (uiState.accentColor != 0L) Color(uiState.accentColor) else NexaAccent
            CompositionLocalProvider(LocalAccentColor provides effectiveAccent) {
            NexaTheme(themeMode = uiState.themeMode, accentColor = effectiveAccent) {
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
                                    // Open camera directly for vision
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
}
