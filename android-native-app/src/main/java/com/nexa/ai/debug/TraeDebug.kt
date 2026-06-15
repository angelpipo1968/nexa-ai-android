package com.nexa.ai.debug

import android.util.Log
import com.nexa.ai.BuildConfig

/**
 * Lightweight debug event logger for Nexa AI.
 * Used to trace speech, voice mode, and SSE events during development.
 * All calls are no-ops in release builds (guarded by BuildConfig.DEBUG).
 */
object TraeDebug {

    private const val TAG = "NexaDebug"

    /**
     * Log a debug event with structured data.
     * Only logs in debug builds — stripped from release.
     */
    fun event(
        hypothesisId: String,
        location: String,
        msg: String,
        dataJson: String = "{}"
    ) {
        // Only log in debug builds — avoid overhead in release
        if (!BuildConfig.DEBUG) return
        Log.d(TAG, "[$hypothesisId] $location | $msg | $dataJson")
    }

    /**
     * Log an error event.
     * Always logs (even in release) since errors are important.
     */
    fun error(
        hypothesisId: String,
        location: String,
        msg: String,
        throwable: Throwable? = null
    ) {
        if (throwable != null) {
            Log.e(TAG, "[$hypothesisId] $location | $msg", throwable)
        } else {
            Log.e(TAG, "[$hypothesisId] $location | $msg")
        }
    }
}
