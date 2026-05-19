package com.nexa.ai.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
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
    onToggleVoiceMode: () -> Unit = {},
    onStopVoiceMode: () -> Unit = {},
    onDismissVoiceHelp: () -> Unit = {},
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
    onCycleTheme: () -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit = {},
    onNavigateToLogin: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToLottery: () -> Unit = {}, onNavigateToTranslator: () -> Unit = {},
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
    onClearAttachment: () -> Unit = {},
    onInterruptVoice: () -> Unit = {},
    onPinSession: (String) -> Unit = {},
    onRenameSession: (String) -> Unit = {},
    onCloneSession: (String) -> Unit = {},
    onArchiveSession: (String) -> Unit = {},
    onShareSession: (String) -> Unit = {},
    onDownloadSession: (String) -> Unit = {},
    onRegenerate: () -> Unit = {},
    onRequestLocation: () -> Unit = {},
    onToggleNotifications: () -> Unit = {},
    onShareMessage: (String) -> Unit = {},
    onToggleVolumeBoost: () -> Unit = {},
    onSetSpeechRate: (Float) -> Unit = {},
    onCaptureImage: () -> Unit = {},
    onDismissPreview: () -> Unit = {},
    onQuickAction: (String) -> Unit = {}
) {
    // Update dialog
    if (uiState.showUpdateDialog && uiState.updateInfo != null) {
        UpdateDialog(updateInfo = uiState.updateInfo, onDismiss = onDismissUpdate,
            onUpdate = onOpenUpdatePage, language = uiState.language)
    }

    // Resolve dark mode from theme setting + system
    val isSystemDark = isSystemInDarkTheme()
    val isDark = uiState.isDark(isSystemDark)

    // Screen navigation
    when (uiState.currentScreen) {
        Screen.LOGIN -> LoginScreen(
            email = uiState.loginEmail, password = uiState.loginPassword,
            error = uiState.loginError, isLoading = uiState.isLoggingIn,
            onEmailChange = onUpdateLoginEmail, onPasswordChange = onUpdateLoginPassword,
            onLogin = onLogin, onGoToRegister = onNavigateToRegister, onBack = onNavigateToChat,
            isDarkTheme = isDark, language = uiState.language)
        Screen.REGISTER -> RegisterScreen(
            name = uiState.registerName, email = uiState.registerEmail,
            password = uiState.registerPassword, confirmPassword = uiState.registerConfirmPassword,
            error = uiState.registerError, isLoading = uiState.isRegistering,
            onNameChange = onUpdateRegisterName, onEmailChange = onUpdateRegisterEmail,
            onPasswordChange = onUpdateRegisterPassword, onConfirmPasswordChange = onUpdateRegisterConfirmPassword,
            onRegister = onRegister, onGoToLogin = onNavigateToLogin, onBack = onNavigateToChat,
            isDarkTheme = isDark, language = uiState.language)
        Screen.CHAT -> ChatMainScreen(
            uiState = uiState, isDarkTheme = isDark,
            onSend = onSend, onInputChange = onInputChange,
            onStartListening = onStartListening, onStopListening = onStopListening,
            onToggleAutoSpeak = onToggleAutoSpeak, onStopSpeaking = onStopSpeaking,
            onSpeakMessage = onSpeakMessage, onClearChat = onClearChat,
            onDismissError = onDismissError, onToggleDrawer = onToggleDrawer,
            onCreateSession = onCreateSession,
            onSwitchSession = onSwitchSession, onDeleteSession = onDeleteSession,
            onToggleSettings = onToggleSettings, onSetLanguage = onSetLanguage,
            onSetVoiceType = onSetVoiceType, onCycleTheme = onCycleTheme,
            onNavigateToLogin = onNavigateToLogin, onLogout = onLogout,
            onCopyMessage = onCopyMessage, onExportMessage = onExportMessage,
            onSurpriseMe = onSurpriseMe, onSetDrawerView = onSetDrawerView,
            onAttachFile = onAttachFile, onClearAttachment = onClearAttachment,
            onNavigateToLottery = onNavigateToLottery, onNavigateToTranslator = onNavigateToTranslator,
            onPinSession = onPinSession, onRenameSession = onRenameSession,
            onCloneSession = onCloneSession, onArchiveSession = onArchiveSession,
            onShareSession = onShareSession, onDownloadSession = onDownloadSession,
            onRegenerate = onRegenerate,
            onInterruptVoice = onInterruptVoice,
            onToggleVoiceMode = onToggleVoiceMode, onStopVoiceMode = onStopVoiceMode,
            onDismissVoiceHelp = onDismissVoiceHelp,
            onShareMessage = onShareMessage,
            onQuickAction = onQuickAction,
            onCaptureImage = onCaptureImage,
            onDismissPreview = onDismissPreview)
        Screen.LOTTERY -> LotteryScreen(
            language = uiState.language,
            isDarkTheme = isDark,
            onBack = onNavigateToChat
        )
        Screen.SETTINGS -> SettingsScreen(
            uiState = uiState, isDarkTheme = isDark,
            onBack = onNavigateToChat,
            onSetLanguage = onSetLanguage, onSetVoiceType = onSetVoiceType,
            onSetThemeMode = onSetThemeMode,
            onToggleAutoSpeak = onToggleAutoSpeak,
            onClearChat = onClearChat, onNavigateToLogin = onNavigateToLogin, onLogout = onLogout,
            onRequestLocation = onRequestLocation, onToggleNotifications = onToggleNotifications,
            onToggleVolumeBoost = onToggleVolumeBoost, onSetSpeechRate = onSetSpeechRate
        )
        Screen.TRANSLATOR -> TranslatorScreen(
            onBack = onNavigateToChat
        )
    }

}
