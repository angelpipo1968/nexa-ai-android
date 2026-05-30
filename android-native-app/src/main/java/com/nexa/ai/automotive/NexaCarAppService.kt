package com.nexa.ai.automotive

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/**
 * NexaCarAppService — Official entrypoint for Google Play compliant
 * Android Auto and Android Automotive OS sessions.
 */
class NexaCarAppService : CarAppService() {

    override fun onCreateSession(): Session {
        return NexaCarSession()
    }

    override fun createHostValidator(): HostValidator {
        // Allows testing on both official emulators and actual vehicle headunits
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }
}
