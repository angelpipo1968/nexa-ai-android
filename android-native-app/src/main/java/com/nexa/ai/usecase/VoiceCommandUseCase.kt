package com.nexa.ai.usecase

import com.nexa.ai.viewmodel.AppLanguage
import com.nexa.ai.viewmodel.VoiceType
import com.nexa.ai.viewmodel.ThemeMode

/**
 * VoiceCommandUseCase — Parses and handles voice commands.
 * Extracted from NexaViewModel to reduce complexity and improve testability.
 *
 * This use case encapsulates all voice command parsing logic, returning typed
 * VoiceCommand sealed class instances that the ViewModel can dispatch to the
 * appropriate handlers. This separation enables:
 * - Unit testing of command parsing without Android dependencies
 * - Easy addition of new commands without modifying the ViewModel
 * - Clear mapping between voice patterns and actions
 */
class VoiceCommandUseCase {

    /**
     * Sealed hierarchy of all supported voice commands.
     * Each command carries the language context for localized responses.
     */
    sealed class VoiceCommand {
        data class ClearChat(val lang: AppLanguage) : VoiceCommand()
        data class ExportPdf(val lang: AppLanguage) : VoiceCommand()
        data class StopHandsFree(val lang: AppLanguage) : VoiceCommand()
        data class SwitchLanguage(val language: AppLanguage) : VoiceCommand()
        data class ChangeVoice(val voiceType: VoiceType) : VoiceCommand()
        data class NewChat(val lang: AppLanguage) : VoiceCommand()
        data class RepeatLast(val lang: AppLanguage) : VoiceCommand()
        data class Silence(val lang: AppLanguage) : VoiceCommand()
        data class ShowHelp(val lang: AppLanguage) : VoiceCommand()
        data class ReadLast(val lang: AppLanguage) : VoiceCommand()
        data class ChangeTheme(val themeMode: ThemeMode) : VoiceCommand()
        data class OpenSettings(val lang: AppLanguage) : VoiceCommand()
        data class CreateImage(val prompt: String, val lang: AppLanguage) : VoiceCommand()
        data class CreateWebsite(val lang: AppLanguage) : VoiceCommand()
        data class Share(val lang: AppLanguage) : VoiceCommand()
        data class OpenCamera(val lang: AppLanguage) : VoiceCommand()
        data class WriteCode(val lang: AppLanguage) : VoiceCommand()
        data class OpenApp(val appName: String, val lang: AppLanguage) : VoiceCommand()
        data class SetAlarm(val hour: Int, val minute: Int, val lang: AppLanguage) : VoiceCommand()
        data class MakeCall(val contact: String, val lang: AppLanguage) : VoiceCommand()
        data class SetReminder(val text: String, val lang: AppLanguage) : VoiceCommand()
        data class SetTimer(val seconds: Int, val lang: AppLanguage) : VoiceCommand()
        data class RememberFact(val fact: String, val lang: AppLanguage) : VoiceCommand()
        data class WhatDoYouKnow(val lang: AppLanguage) : VoiceCommand()
        data object None : VoiceCommand()
    }

    /**
     * Parse a raw voice transcription into a typed VoiceCommand.
     *
     * @param text Raw voice transcription text
     * @param lang Current app language for context
     * @return Parsed VoiceCommand, or VoiceCommand.None if no command matched
     */
    fun parseCommand(text: String, lang: AppLanguage): VoiceCommand {
        val cmd = text.lowercase().trim()

        // Clear chat
        if (cmd.contains("limpiar chat") || cmd.contains("borra el chat") || cmd.contains("clear chat")) {
            return VoiceCommand.ClearChat(lang)
        }
        // Export PDF
        if (cmd.contains("exportar pdf") || cmd.contains("p d f") || cmd.contains("export pdf")) {
            return VoiceCommand.ExportPdf(lang)
        }
        // Stop hands free
        if (cmd.contains("detener manos libres") || cmd.contains("stop hands free") || cmd.contains("salir modo voz") || cmd.contains("exit voice mode")) {
            return VoiceCommand.StopHandsFree(lang)
        }
        // Switch language
        if (cmd.contains("cambiar a inglés") || cmd.contains("switch to english") || cmd.contains("habla inglés") || cmd.contains("speak english")) {
            return VoiceCommand.SwitchLanguage(AppLanguage.ENGLISH)
        }
        if (cmd.contains("cambiar a español") || cmd.contains("switch to spanish") || cmd.contains("habla español") || cmd.contains("speak spanish")) {
            return VoiceCommand.SwitchLanguage(AppLanguage.SPANISH)
        }
        // Change voice
        if (cmd.contains("voz masculina") || cmd.contains("male voice") || cmd.contains("voz de hombre")) {
            return VoiceCommand.ChangeVoice(VoiceType.MALE_1)
        }
        if (cmd.contains("voz femenina") || cmd.contains("female voice") || cmd.contains("voz de mujer")) {
            return VoiceCommand.ChangeVoice(VoiceType.FEMALE_1)
        }
        // New chat
        if (cmd.contains("nuevo chat") || cmd.contains("new chat") || cmd.contains("nueva conversación") || cmd.contains("new conversation")) {
            return VoiceCommand.NewChat(lang)
        }
        // Repeat
        if (cmd.contains("repite") || cmd.contains("repito") || cmd.contains("repeat") || cmd.contains("say again") || cmd.contains("otra vez")) {
            return VoiceCommand.RepeatLast(lang)
        }
        // Silence
        if (cmd.contains("cállate") || cmd.contains("callate") || cmd.contains("silencio") || cmd.contains("shut up") || cmd.contains("be quiet") || cmd.contains("silence")) {
            return VoiceCommand.Silence(lang)
        }
        // Help
        if (cmd.contains("ayuda") || cmd.contains("comandos") || cmd.contains("help") || cmd.contains("commands") || cmd.contains("qué puedes hacer") || cmd.contains("what can you do")) {
            return VoiceCommand.ShowHelp(lang)
        }
        // Read
        if (cmd.contains("lee") || cmd.contains("leer") || cmd.contains("read") || cmd.contains("read it")) {
            return VoiceCommand.ReadLast(lang)
        }
        // Theme
        if (cmd.contains("modo oscuro") || cmd.contains("dark mode") || cmd.contains("tema oscuro")) {
            return VoiceCommand.ChangeTheme(ThemeMode.DARK)
        }
        if (cmd.contains("modo claro") || cmd.contains("light mode") || cmd.contains("tema claro")) {
            return VoiceCommand.ChangeTheme(ThemeMode.LIGHT)
        }
        // Settings
        if (cmd.contains("abrir ajustes") || cmd.contains("open settings") || cmd.contains("ajustes") || cmd.contains("configuración") || cmd.contains("configuracion")) {
            return VoiceCommand.OpenSettings(lang)
        }
        // Create image
        if (cmd.contains("crear imagen") || cmd.contains("create image") || cmd.contains("genera imagen") ||
            cmd.contains("generate image") || cmd.contains("crear logo") || cmd.contains("create logo") ||
            cmd.contains("genera logo") || cmd.contains("haz una imagen") || cmd.contains("make an image") ||
            cmd.contains("dibujar") || cmd.contains("draw")) {
            val prompt = cmd
                .replace(Regex("(crear|genera|haz|create|generate|make|draw)\\s+(una |an |a )?(imagen|image|logo|dibujo|drawing|picture|foto|photo)"), "")
                .replace(Regex("(de |of )"), "")
                .trim()
            return VoiceCommand.CreateImage(prompt, lang)
        }
        // Create website
        if (cmd.contains("crear web") || cmd.contains("create web") || cmd.contains("crear página") ||
            cmd.contains("create website") || cmd.contains("crear sitio") || cmd.contains("build website") ||
            cmd.contains("haz una web") || cmd.contains("make a website") || cmd.contains("página web")) {
            return VoiceCommand.CreateWebsite(lang)
        }
        // Share
        if (cmd.contains("compartir") || cmd.contains("share") || cmd.contains("enviar")) {
            return VoiceCommand.Share(lang)
        }
        // Camera/Vision
        if (cmd.contains("qué ves") || cmd.contains("what do you see") || cmd.contains("describe") ||
            cmd.contains("ver cámara") || cmd.contains("use camera") || cmd.contains("mira") ||
            cmd.contains("cámara") || cmd.contains("camera")) {
            return VoiceCommand.OpenCamera(lang)
        }
        // Code
        if (cmd.contains("codificar") || cmd.contains("programar") || cmd.contains("code") ||
            cmd.contains("program") || cmd.contains("escribe código") || cmd.contains("write code")) {
            return VoiceCommand.WriteCode(lang)
        }
        // Open app
        if (cmd.contains("abre") || cmd.contains("open") || cmd.contains("abrir")) {
            val appName = cmd.replace(Regex("(abre|open|abrir)\\s+"), "").trim()
            if (appName.isNotBlank()) {
                return VoiceCommand.OpenApp(appName, lang)
            }
        }
        // Alarm
        if (cmd.contains("alarma") || cmd.contains("alarm")) {
            val timeRegex = Regex("(\\d{1,2}):(\\d{2})")
            val match = timeRegex.find(cmd)
            if (match != null) {
                val hour = match.groupValues[1].toInt()
                val minute = match.groupValues[2].toInt()
                return VoiceCommand.SetAlarm(hour, minute, lang)
            }
            val numRegex = Regex("(\\d{1,2})")
            val numMatch = numRegex.find(cmd.replace(Regex("(alarma|alarm)"), ""))
            if (numMatch != null) {
                val hour = numMatch.groupValues[1].toInt()
                return VoiceCommand.SetAlarm(hour, 0, lang)
            }
        }
        // Call
        if (cmd.contains("llama a") || cmd.contains("llamar a") || cmd.contains("call")) {
            val contact = cmd.replace(Regex("(llama a|llamar a|call)\\s+"), "").trim()
            if (contact.isNotBlank()) {
                return VoiceCommand.MakeCall(contact, lang)
            }
        }
        // Reminder
        if (cmd.contains("recuérdame") || cmd.contains("recuerdame") || cmd.contains("remind me") || cmd.contains("recordatorio")) {
            val reminderText = cmd
                .replace(Regex("(recuérdame|recuerdame|remind me|recordatorio)\\s+"), "")
                .trim()
                .replace(Regex("(a las|at)\\s+\\d{1,2}(:\\d{2})?"), "")
                .trim()
            if (reminderText.isNotBlank()) {
                return VoiceCommand.SetReminder(reminderText, lang)
            }
        }
        // Timer
        if (cmd.contains("temporizador") || cmd.contains("timer") || cmd.contains("cuenta atrás")) {
            val minuteRegex = Regex("(\\d+)\\s*(minutos?|minutes?|mins?)")
            val minMatch = minuteRegex.find(cmd)
            val secondsRegex = Regex("(\\d+)\\s*(segundos?|seconds?|secs?)")
            val secMatch = secondsRegex.find(cmd)
            val totalSeconds = (minMatch?.groupValues?.get(1)?.toIntOrNull()?.times(60) ?: 0) +
                               (secMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0)
            if (totalSeconds > 0) {
                return VoiceCommand.SetTimer(totalSeconds, lang)
            }
        }
        // Remember
        if (cmd.contains("recuerda") || cmd.contains("recordar") || cmd.contains("remember this") || cmd.contains("memoriza")) {
            val fact = cmd.replace(Regex("(recuerda|recordar|remember this|memoriza|que)\\s+"), "").trim()
            if (fact.isNotBlank()) {
                return VoiceCommand.RememberFact(fact, lang)
            }
        }
        // What do you know about me
        if (cmd.contains("qué sabes de mí") || cmd.contains("what do you know about me") || cmd.contains("quién soy")) {
            return VoiceCommand.WhatDoYouKnow(lang)
        }

        return VoiceCommand.None
    }

    /**
     * Get localized help text listing all available voice commands.
     *
     * @param lang Current app language
     * @return Help text string in the appropriate language
     */
    fun getHelpText(lang: AppLanguage): String {
        return if (lang == AppLanguage.SPANISH) {
            "Puedes decir: limpiar chat, nuevo chat, exportar PDF, repetir, voz masculina, voz femenina, habla inglés, habla español, detener manos libres, abre WhatsApp, alarma a las 7, recuérdame comprar leche, temporizador 5 minutos, recuerda que hoy es martes, o cállate."
        } else {
            "You can say: clear chat, new chat, export PDF, repeat, male voice, female voice, speak English, speak Spanish, stop hands free, open WhatsApp, alarm at 7, remind me to buy milk, timer 5 minutes, remember today is Tuesday, or shut up."
        }
    }
}
