package com.nexa.ai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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
import com.nexa.ai.ui.theme.NexaTheme
import com.nexa.ai.viewmodel.NexaViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: NexaViewModel by viewModels()

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startListening()
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

        setContent {
            val uiState by viewModel.uiState.collectAsState()

            NexaTheme(themeMode = uiState.themeMode) {
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
                        onToggleVoiceMode = {
                            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                                == PackageManager.PERMISSION_GRANTED
                            ) {
                                viewModel.toggleVoiceMode()
                            } else {
                                requestPermission.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onStopVoiceMode = { viewModel.stopVoiceMode() }
                    )
                }
            }
        }
    }
}
