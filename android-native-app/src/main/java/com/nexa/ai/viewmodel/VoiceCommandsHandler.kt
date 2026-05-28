package com.nexa.ai.viewmodel

import android.content.Context
import com.nexa.ai.ui.NexaStrings

// ═══════════════════════════════════════
//  STUBS FOR MISSING CLASSES
// ═══════════════════════════════════════

/** Stub for IoTManager while it is being implemented. */
class IoTManager {
    fun isIoTCommand(cmd: String): Boolean = false
}

/** Stub for VideoGenerator while it is being implemented. */
class VideoGenerator {
    enum class VideoStyles { ANIME, CINEMATIC, REALISTIC, ABSTRACT, VINTAGE, SCI_FI, NATURE }
}

/** Represents the result of processing a voice command. */
sealed class VoiceCommandResult {
    /** Command was handled, no further action needed. */
    data class Handled(val spokenResponse: String) : VoiceCommandResult()
    /** Command was not recognized, proceed with normal message sending. */
    data object NotRecognized : VoiceCommandResult()
}

/**
 * VoiceCommandsHandler — Extracted from NexaViewModel.
 * Handles all voice command detection and execution in hands-free mode.
 */
class VoiceCommandsHandler(
    private val iotManager: IoTManager,
    private val videoGenerator: VideoGenerator
) {

    /**
     * Try to handle the given voice command text.
     * Returns Handled if the command was recognized, NotRecognized otherwise.
     */
    fun tryHandleCommand(
        context: Context,
        cmd: String,
        lang: AppLanguage,
        messages: List<Message>,
        onClearChat: () -> Unit,
        onExportPdf: () -> Unit,
        onStopVoiceMode: () -> Unit,
        onSetLanguage: (AppLanguage) -> Unit,
        onSetVoiceType: (VoiceType) -> Unit,
        onCreateSession: () -> Unit,
        onRepeatLast: () -> Unit,
        onSpeakLast: () -> Unit,
        onSpeak: (String) -> Unit,
        onStopSpeaking: () -> Unit,
        onShowHelp: () -> Unit,
        onReadLast: () -> Unit,
        onSetThemeMode: (ThemeMode) -> Unit,
        onOpenSettings: () -> Unit,
        onSendMessage: (String) -> Unit,
        onShareLast: () -> Unit,
        onCameraCapture: () -> Unit,
        onGenerateVideo: (String, VideoGenerator.VideoStyles) -> Unit
    ): VoiceCommandResult {
        val c = cmd.lowercase().trim()

        // Helpers to get localized strings
        fun s(key: String) = NexaStrings.get(context, key, lang)
        fun sf(key: String, arg: Any) = NexaStrings.get(context, key, lang, arg)

        // Clear chat
        if (c.contains("limpiar chat") || c.contains("borra el chat") || c.contains("clear chat")) {
            onClearChat()
            return VoiceCommandResult.Handled(s("voice_cmd_chat_cleared"))
        }

        // Export PDF
        if (c.contains("exportar pdf") || c.contains("p d f") || c.contains("export pdf")) {
            onExportPdf()
            return VoiceCommandResult.Handled(s("voice_cmd_exporting"))
        }

        // Stop hands-free mode
        if (c.contains("detener manos libres") || c.contains("stop hands free") ||
            c.contains("salir modo voz") || c.contains("exit voice mode")) {
            onStopVoiceMode()
            return VoiceCommandResult.Handled(s("voice_cmd_hands_free_off"))
        }

        // Change language
        if (c.contains("cambiar a inglés") || c.contains("switch to english") ||
            c.contains("habla inglés") || c.contains("speak english")) {
            onSetLanguage(AppLanguage.ENGLISH)
            return VoiceCommandResult.Handled(s("voice_cmd_lang_en"))
        }
        if (c.contains("cambiar a español") || c.contains("switch to spanish") ||
            c.contains("habla español") || c.contains("speak spanish")) {
            onSetLanguage(AppLanguage.SPANISH)
            return VoiceCommandResult.Handled(s("voice_cmd_lang_es"))
        }

        // Change voice
        if (c.contains("voz masculina") || c.contains("male voice") || c.contains("voz de hombre")) {
            onSetVoiceType(VoiceType.MALE_1)
            return VoiceCommandResult.Handled(s("voice_cmd_male"))
        }
        if (c.contains("voz femenina") || c.contains("female voice") || c.contains("voz de mujer")) {
            onSetVoiceType(VoiceType.FEMALE_1)
            return VoiceCommandResult.Handled(s("voice_cmd_female"))
        }

        // New chat
        if (c.contains("nuevo chat") || c.contains("new chat") ||
            c.contains("nueva conversación") || c.contains("new conversation")) {
            onCreateSession()
            return VoiceCommandResult.Handled(s("voice_cmd_new_created"))
        }

        // Repeat last response
        if (c.contains("repite") || c.contains("repito") || c.contains("repeat") ||
            c.contains("say again") || c.contains("otra vez")) {
            onRepeatLast()
            return VoiceCommandResult.Handled("")
        }

        // Stop / silence
        if (c.contains("cállate") || c.contains("callate") || c.contains("silencio") ||
            c.contains("shut up") || c.contains("be quiet") || c.contains("silence")) {
            onStopSpeaking()
            return VoiceCommandResult.Handled(s("voice_cmd_ok"))
        }

        // Help
        if (c.contains("ayuda") || c.contains("comandos") || c.contains("help") ||
            c.contains("commands") || c.contains("qué puedes hacer") || c.contains("what can you do")) {
            onShowHelp()
            return VoiceCommandResult.Handled(s("voice_cmd_help_text"))
        }

        // Read last message
        if (c.contains("lee") || c.contains("leer") || c.contains("read") || c.contains("read it")) {
            onReadLast()
            return VoiceCommandResult.Handled("")
        }

        // Theme
        if (c.contains("modo oscuro") || c.contains("dark mode") || c.contains("tema oscuro")) {
            onSetThemeMode(ThemeMode.DARK)
            return VoiceCommandResult.Handled(s("voice_cmd_dark_on"))
        }
        if (c.contains("modo claro") || c.contains("light mode") || c.contains("tema claro")) {
            onSetThemeMode(ThemeMode.LIGHT)
            return VoiceCommandResult.Handled(s("voice_cmd_light_on"))
        }

        // Open settings
        if (c.contains("abrir ajustes") || c.contains("open settings") || c.contains("ajustes") ||
            c.contains("configuración") || c.contains("configuracion")) {
            onOpenSettings()
            return VoiceCommandResult.Handled(s("voice_cmd_opening_settings"))
        }

        // Create image / generate image / create logo
        if (c.contains("crear imagen") || c.contains("create image") || c.contains("genera imagen") ||
            c.contains("generate image") || c.contains("crear logo") || c.contains("create logo") ||
            c.contains("genera logo") || c.contains("haz una imagen") || c.contains("make an image") ||
            c.contains("dibujar") || c.contains("draw")) {
            val prompt = c
                .replace(Regex("(crear|genera|haz|create|generate|make|draw)\\s+(una |an |a )?(imagen|image|logo|dibujo|drawing|picture|foto|photo)"), "")
                .replace(Regex("(de |of )"), "")
                .trim()
            val imagePrompt = if (prompt.isBlank()) s("voice_cmd_image_default") else sf("voice_cmd_image_prompt", prompt)
            onSendMessage(imagePrompt)
            return VoiceCommandResult.Handled(s("voice_cmd_generating_image"))
        }

        // Create web / build website
        if (c.contains("crear web") || c.contains("create web") || c.contains("crear página") ||
            c.contains("create website") || c.contains("crear sitio") || c.contains("build website") ||
            c.contains("haz una web") || c.contains("make a website") || c.contains("página web")) {
            onSendMessage(s("voice_cmd_web_prompt"))
            return VoiceCommandResult.Handled(s("voice_cmd_creating_web"))
        }

        // Generate video
        if (c.contains("crear video") || c.contains("create video") || c.contains("genera video") ||
            c.contains("generate video") || c.contains("haz un video") || c.contains("make a video") ||
            c.contains("animar") || c.contains("animate") || c.contains("video de")) {
            val videoPrompt = c
                .replace(Regex("(crear|genera|haz|create|generate|make|animate|animar)\\s+(un |a )?(video|animacion|animation)"), "")
                .replace(Regex("(de |of |about )"), "")
                .trim()
            val style = detectVideoStyle(c)
            val prompt = videoPrompt.ifBlank { s("voice_cmd_video_default") }
            onGenerateVideo(prompt, style)
            return VoiceCommandResult.Handled(sf("voice_cmd_generating_video", prompt))
        }

        // Share last response
        if (c.contains("compartir") || c.contains("share") || c.contains("enviar")) {
            onShareLast()
            return VoiceCommandResult.Handled(s("voice_cmd_shared"))
        }

        // Camera / vision
        if (c.contains("qué ves") || c.contains("what do you see") || c.contains("describe") ||
            c.contains("ver cámara") || c.contains("use camera") || c.contains("mira") ||
            c.contains("cámara") || c.contains("camera")) {
            onCameraCapture()
            return VoiceCommandResult.Handled(s("voice_cmd_opening_camera"))
        }

        // Code / program
        if (c.contains("codificar") || c.contains("programar") || c.contains("code") ||
            c.contains("program") || c.contains("escribe código") || c.contains("write code")) {
            onSendMessage(s("voice_cmd_code_prompt"))
            return VoiceCommandResult.Handled(s("voice_cmd_programming_mode"))
        }

        // IoT / Smart Home
        if (iotManager.isIoTCommand(c)) {
            return VoiceCommandResult.Handled("") // IoT handled separately due to async nature
        }

        // Good morning / Good night routines
        if (c.contains("buenos días") || c.contains("good morning") ||
            c.contains("buenas noches") || c.contains("good night")) {
            return VoiceCommandResult.Handled("") // Routines handled separately due to async nature
        }

        return VoiceCommandResult.NotRecognized
    }

    /**
     * Check if a command is an async IoT command that needs special handling.
     */
    fun isIoTCommand(cmd: String): Boolean = iotManager.isIoTCommand(cmd.lowercase().trim())

    /**
     * Get the routine ID for morning/night commands, or null.
     */
    fun getRoutineId(cmd: String): String? {
        val c = cmd.lowercase().trim()
        return when {
            c.contains("buenos días") || c.contains("good morning") -> "routine_good_morning"
            c.contains("buenas noches") || c.contains("good night") -> "routine_good_night"
            else -> null
        }
    }

    private fun detectVideoStyle(cmd: String): VideoGenerator.VideoStyles {
        return when {
            cmd.contains("anime") -> VideoGenerator.VideoStyles.ANIME
            cmd.contains("cinemat") -> VideoGenerator.VideoStyles.CINEMATIC
            cmd.contains("realist") -> VideoGenerator.VideoStyles.REALISTIC
            cmd.contains("abstract") -> VideoGenerator.VideoStyles.ABSTRACT
            cmd.contains("vintage") || cmd.contains("retro") -> VideoGenerator.VideoStyles.VINTAGE
            cmd.contains("ciencia ficcion") || cmd.contains("sci-fi") || cmd.contains("futurist") -> VideoGenerator.VideoStyles.SCI_FI
            cmd.contains("naturaleza") || cmd.contains("nature") -> VideoGenerator.VideoStyles.NATURE
            else -> VideoGenerator.VideoStyles.CINEMATIC
        }
    }
}
