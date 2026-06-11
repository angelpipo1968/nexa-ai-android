package com.nexa.ai.usecase

import com.nexa.ai.data.NetworkMonitor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DecideOnlineOfflineUseCase — Business logic for smart routing between local (on-device)
 * and cloud intelligence based on network connectivity and query complexity.
 */
@Singleton
class DecideOnlineOfflineUseCase @Inject constructor(
    private val networkMonitor: NetworkMonitor
) {

    /**
     * Determines whether cloud-based LLM should be used.
     * Returns true if network is online and we want high-capacity cloud intelligence.
     */
    fun shouldUseCloud(): Boolean {
        return networkMonitor.isOnline.value
    }

    /**
     * Determines whether local (on-device) LLM should handle the query.
     * Evaluates offline state, user preference, and query simplicity.
     *
     * @param query The user's input string
     * @param forceLocal Whether the user explicitly activated "local only" privacy mode
     */
    fun shouldUseLocal(query: String, forceLocal: Boolean = false): Boolean {
        if (forceLocal) return true
        
        // If there's no internet, we MUST use local
        if (!networkMonitor.isOnline.value) return true

        val lowerQuery = query.lowercase().trim()

        // Simple local routing queries (utilities, simple questions)
        val isSimpleCommand = lowerQuery.length < 25 && (
            lowerQuery.contains("hora") ||
            lowerQuery.contains("fecha") ||
            lowerQuery.contains("alarma") ||
            lowerQuery.contains("temporizador") ||
            lowerQuery.contains("hola") ||
            lowerQuery.contains("gracias") ||
            lowerQuery.contains("apaga") ||
            lowerQuery.contains("enciende")
        )

        return isSimpleCommand
    }
}
