package com.nexa.ai.automotive.safety

import android.util.Log
import com.nexa.ai.automotive.sensors.VehicleState

/**
 * DrivingMonitor — Evaluates vehicle telemetry in real-time.
 * Strictly enforces Car UX Restrictions and blocks visual UI elements when driving.
 */
object DrivingMonitor {
    private const val TAG = "DrivingSafety"
    private const val SPEED_LIMIT_KMH = 10

    var onSafetyRestrictionChanged: ((Boolean) -> Unit)? = null
    private var isRestricted = false

    /**
     * Determines whether the user is currently driving.
     */
    fun isDriving(): Boolean {
        return VehicleState.speed > SPEED_LIMIT_KMH
    }

    /**
     * Periodically monitors vehicle speed to enforce hands-free operation.
     * Can be invoked by the main coordinator loop or on OBD telemetries updating.
     */
    fun evaluateSafety() {
        val currentDrivingState = isDriving()
        if (currentDrivingState != isRestricted) {
            isRestricted = currentDrivingState
            Log.w(TAG, "Driving safety state changed! Hands-free restriction active: $isRestricted")
            onSafetyRestrictionChanged?.invoke(isRestricted)
        }
    }

    /**
     * Forces immediate UI lockout if driver interaction is attempted while moving.
     */
    fun checkAndEnforce(onRestricted: () -> Unit) {
        evaluateSafety()
        if (isRestricted) {
            Log.w(TAG, "Driver distraction detected! Forcing visual block.")
            onRestricted()
        }
    }
}
