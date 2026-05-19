package com.nexa.ai.viewmodel

import com.nexa.ai.data.LocationStore
import com.nexa.ai.data.UpdateInfo
import java.util.UUID

// ═══════════════════════════════════════
//  DATA MODELS
// ═══════════════════════════════════════

data class Message(
    val id: String = System.currentTimeMillis().toString(),
    val role: String,
    val content: String,
    val isStreaming: Boolean = false,
    val attachmentName: String? = null
)

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val messages: List<Message> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class VoiceType { MALE_1, MALE_2, MALE_3, FEMALE_1, FEMALE_2, FEMALE_3 }

enum class AppLanguage(val code: String, val label: String) {
    SPANISH("es", "Español"),
    ENGLISH("en", "English")
}

/** Theme mode: DARK, LIGHT, or SYSTEM (follow device setting). */
enum class ThemeMode { DARK, LIGHT, SYSTEM }

data class UserData(
    val email: String = "",
    val displayName: String = "",
    val isLoggedIn: Boolean = false
)

enum class Screen { CHAT, LOGIN, REGISTER, LOTTERY, SETTINGS, TRANSLATOR }

// ═══════════════════════════════════════
//  UI STATE
// ═══════════════════════════════════════

data class NexaUiState(
    // Chat
    val sessions: List<ChatSession> = emptyList(),
    val activeSessionId: String? = null,
    val inputText: String = "",
    val isThinking: Boolean = false,
    val currentProvider: String? = null,
    val error: String? = null,
    val pendingAttachment: String? = null,
    // Speech
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val speakingMessageId: String? = null,
    val autoSpeak: Boolean = true,
    val voiceMode: Boolean = false,
    val voiceVolumeLevel: Float = 0f,  // 0f..1f real-time mic volume for visual feedback
    // Settings
    val language: AppLanguage = AppLanguage.SPANISH,
    val voiceType: VoiceType = VoiceType.FEMALE_1,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val drawerOpen: Boolean = false,
    val drawerView: Int = 0,
    // Location
    val locationData: LocationStore.LocationData = LocationStore.LocationData(),
    val isLocating: Boolean = false,
    // Notifications
    val notificationsEnabled: Boolean = true,
    // Audio/Volume settings
    val volumeBoostEnabled: Boolean = true,  // Volume boost for hands-free
    val speechRate: Float = 1.0f,  // TTS speech rate 0.5f - 2.0f
    // Camera
    val cameraImageUri: String? = null,  // Base64 image from camera for vision
    val requestCameraCapture: Boolean = false,  // Signal to UI to open camera
    // Preview
    val previewContent: String? = null,  // HTML/code content for preview
    val showPreview: Boolean = false,  // Whether to show preview overlay
    // AI Capabilities
    val showCapabilitiesMenu: Boolean = false,  // Quick actions menu
    // Auto-scroll
    val autoScrollEnabled: Boolean = true,  // Auto-scroll to latest message
    // Font size
    val chatFontSize: Float = 14f,  // Chat font size in sp
    // Auth
    val currentScreen: Screen = Screen.CHAT,
    val user: UserData = UserData(),
    val loginEmail: String = "",
    val loginPassword: String = "",
    val loginError: String? = null,
    val isLoggingIn: Boolean = false,
    val registerName: String = "",
    val registerEmail: String = "",
    val registerPassword: String = "",
    val registerConfirmPassword: String = "",
    val registerError: String? = null,
    val isRegistering: Boolean = false,
    // Voice commands help
    val showVoiceCommandsHelp: Boolean = false,
    // Update
    val updateInfo: UpdateInfo? = null,
    val showUpdateDialog: Boolean = false
) {
    val activeSession: ChatSession?
        get() = sessions.find { it.id == activeSessionId }

    val messages: List<Message>
        get() = activeSession?.messages ?: emptyList()

    /** Whether the app should currently use dark colors. */
    fun isDark(isSystemDark: Boolean): Boolean = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemDark
    }
}
