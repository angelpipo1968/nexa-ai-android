package com.nexa.ai

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class NexaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialization code here
    }
}
