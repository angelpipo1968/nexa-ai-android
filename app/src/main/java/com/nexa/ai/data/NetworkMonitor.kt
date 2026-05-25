package com.nexa.ai.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NetworkMonitor — Provides reactive network state to the entire app.
 * Wraps NexaApplication.isNetworkAvailable for Hilt injection.
 *
 * Usage in ViewModel:
 *   @Inject lateinit var networkMonitor: NetworkMonitor
 *   val isOnline = networkMonitor.isOnline  // StateFlow<Boolean>
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Reactive network state. Emits `true` when internet is available, `false` otherwise.
     */
    val isOnline: StateFlow<Boolean>
        get() {
            val app = context.applicationContext
            return if (app is com.nexa.ai.NexaApplication) {
                app.isNetworkAvailable
            } else {
                kotlinx.coroutines.flow.MutableStateFlow(checkNetworkFallback())
            }
        }

    /**
     * Check current network state synchronously.
     */
    fun checkNow(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val capabilities = cm.getNetworkCapabilities(cm.activeNetwork)
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } catch (_: Exception) {
            true
        }
    }

    private fun checkNetworkFallback(): Boolean = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = cm.getNetworkCapabilities(cm.activeNetwork)
        capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    } catch (_: Exception) {
        true
    }
}
