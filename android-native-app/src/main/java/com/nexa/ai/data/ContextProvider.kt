package com.nexa.ai.data

import javax.inject.Inject
import javax.inject.Singleton

/**
 * ContextProvider — Provides application context and contextual data
 * for enriching AI prompts and responses.
 *
 * Currently a placeholder; will be expanded to provide:
 * - Time-of-day context
 * - Device state context
 * - User activity context
 */
@Singleton
class ContextProvider @Inject constructor() {

    /**
     * Build a contextual string for AI prompt enrichment.
     * @return Context string or empty string if no context available
     */
    fun getSystemContext(): String = ""

    /**
     * Get the current time-of-day context.
     */
    fun getTimeContext(): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 6..11 -> "morning"
            in 12..14 -> "midday"
            in 15..18 -> "afternoon"
            in 19..22 -> "evening"
            else -> "night"
        }
    }
}
