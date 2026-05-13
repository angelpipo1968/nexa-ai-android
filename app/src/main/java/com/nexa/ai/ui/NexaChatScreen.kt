package com.nexa.ai.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nexa.ai.viewmodel.*

// ═══════════════════════════════════════
//  MAIN SCREEN WITH NAVIGATION
// ═══════════════════════════════════════

@Composable
fun NexaChatScreen(
    uiState: NexaUiState,
    onSend: () -> Unit,
    onInputChange: (String) -> Unit,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onToggleAutoSpeak: () -> Unit,
    onStopSpeaking: () -> Unit,
    onSpeakMessage: (String, String) -> Unit,
    onClearChat: () -> Unit,
    onDismissError: () -> Unit,
    onToggleDrawer: () -> Unit,
    onCloseDrawer: () -> Unit,
    onCreateSession: () -> Unit,
    onSwitchSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onToggleSettings: () -> Unit,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetVoiceType: (VoiceType) -> Unit,
    onToggleTheme: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToChat: () -> Unit,
    onUpdateLoginEmail: (String) -> Unit,
    onUpdateLoginPassword: (String) -> Unit,
    onLogin: () -> Unit,
    onUpdateRegisterName: (String) -> Unit,
    onUpdateRegisterEmail: (String) -> Unit,
    onUpdateRegisterPassword: (String) -> Unit,
    onUpdateRegisterConfirmPassword: (String) -> Unit,
    onRegister: () -> Unit,
    onLogout: () -> Unit,
    onDismissUpdate: () -> Unit,
    onOpenUpdatePage: () -> Unit,
    onCopyMessage: (String) -> Unit,
    onExportMessage: (Message) -> Unit,
    onSurpriseMe: () -> Unit,
    onSetDrawerView: (Int) -> Unit,
    onAttachFile: () -> Unit,
    onClearAttachment: () -> Unit = {}
) {
    // Update dialog
    if (uiState.showUpdateDialog && uiState.updateInfo != null) {
        UpdateDialog(updateInfo = uiState.updateInfo, onDismiss = onDismissUpdate,
            onUpdate = onOpenUpdatePage, language = uiState.language)
    }

    // Screen navigation
    when (uiState.currentScreen) {
        Screen.LOGIN -> LoginScreen(
            email = uiState.loginEmail, password = uiState.loginPassword,
            error = uiState.loginError, isLoading = uiState.isLoggingIn,
            onEmailChange = onUpdateLoginEmail, onPasswordChange = onUpdateLoginPassword,
            onLogin = onLogin, onGoToRegister = onNavigateToRegister, onBack = onNavigateToChat,
            isDarkTheme = uiState.isDarkTheme, language = uiState.language)
        Screen.REGISTER -> RegisterScreen(
            name = uiState.registerName, email = uiState.registerEmail,
            password = uiState.registerPassword, confirmPassword = uiState.registerConfirmPassword,
            error = uiState.registerError, isLoading = uiState.isRegistering,
            onNameChange = onUpdateRegisterName, onEmailChange = onUpdateRegisterEmail,
            onPasswordChange = onUpdateRegisterPassword, onConfirmPasswordChange = onUpdateRegisterConfirmPassword,
            onRegister = onRegister, onGoToLogin = onNavigateToLogin, onBack = onNavigateToChat,
            isDarkTheme = uiState.isDarkTheme, language = uiState.language)
        Screen.CHAT -> ChatMainScreen(
            uiState = uiState, onSend = onSend, onInputChange = onInputChange,
            onStartListening = onStartListening, onStopListening = onStopListening,
            onToggleAutoSpeak = onToggleAutoSpeak, onStopSpeaking = onStopSpeaking,
            onSpeakMessage = onSpeakMessage, onClearChat = onClearChat,
            onDismissError = onDismissError, onToggleDrawer = onToggleDrawer,
            onCloseDrawer = onCloseDrawer, onCreateSession = onCreateSession,
            onSwitchSession = onSwitchSession, onDeleteSession = onDeleteSession,
            onToggleSettings = onToggleSettings, onSetLanguage = onSetLanguage,
            onSetVoiceType = onSetVoiceType, onToggleTheme = onToggleTheme,
            onNavigateToLogin = onNavigateToLogin, onLogout = onLogout,
            onCopyMessage = onCopyMessage, onExportMessage = onExportMessage,
            onSurpriseMe = onSurpriseMe, onSetDrawerView = onSetDrawerView,
            onAttachFile = onAttachFile, onClearAttachment = onClearAttachment)
    }

    // Settings dialog
    if (uiState.showSettings) {
        GeneralSettingsDialog(uiState = uiState, onDismiss = onToggleSettings,
            onSetLanguage = onSetLanguage, onSetVoiceType = onSetVoiceType,
            onToggleTheme = onToggleTheme, onToggleAutoSpeak = onToggleAutoSpeak,
            onClearChat = onClearChat, onNavigateToLogin = onNavigateToLogin, onLogout = onLogout)
    }
}
