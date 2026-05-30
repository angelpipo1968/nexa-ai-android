package com.nexa.ai.automotive.voice

/**
 * ParsedCommand — DTO containing command intent and extracted parameters for offline vehicle control.
 */
data class ParsedCommand(
    val intent: String,
    val target: String
)

/**
 * VoiceCommandParser — Lightweight, regex-based offline intent recognizer
 * for Nexa AI Automotive (v5.3-auto-certified).
 */
object VoiceCommandParser {
    
    /**
     * Parses the driver's vocal input query to map it to a specific in-car intent.
     */
    fun parse(input: String): ParsedCommand {
        val clean = input.lowercase().trim()
        
        return when {
            clean.contains("navega", true) || clean.contains("conduce a", true) || clean.contains("ir a", true) -> {
                ParsedCommand("NAVIGATE", extractTarget(clean))
            }
            clean.contains("clima", true) || clean.contains("temperatura", true) || clean.contains("aire", true) || clean.contains("calefaccion", true) -> {
                ParsedCommand("CLIMATE", "")
            }
            clean.contains("estado", true) || clean.contains("sensores", true) || clean.contains("combustible", true) || clean.contains("velocidad", true) -> {
                ParsedCommand("STATUS", "")
            }
            else -> {
                ParsedCommand("UNKNOWN", "")
            }
        }
    }

    private fun extractTarget(input: String): String {
        // Strip out triggers like "navega a", "conduce a", "ir a"
        return input
            .replace(Regex("(?i)(navegar|navega|conducir|conduce|ir|viajar)\\s*(a |hacia )?"), "")
            .trim()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
