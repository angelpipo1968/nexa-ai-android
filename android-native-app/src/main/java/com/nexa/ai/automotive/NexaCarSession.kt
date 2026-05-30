package com.nexa.ai.automotive

import android.content.Intent
import androidx.car.app.Session
import androidx.car.app.Screen
import com.nexa.ai.automotive.ui.NexaCarMessageScreen

/**
 * NexaCarSession — Handles the lifecycle of the vehicle connection
 * and spawns the initial Play-compliant messaging template screen.
 */
class NexaCarSession : Session() {

    override fun onCreateScreen(intent: Intent): Screen {
        return NexaCarMessageScreen(carContext)
    }
}
