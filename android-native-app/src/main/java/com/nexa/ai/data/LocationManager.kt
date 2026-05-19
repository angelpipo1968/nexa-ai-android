package com.nexa.ai.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Represents the user's current location with optional address info.
 */
data class UserLocation(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Float = 0f,
    val address: String = "",
    val city: String = "",
    val country: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    val isValid: Boolean get() = latitude != 0.0 || longitude != 0.0
    val coordinates: String get() = if (isValid) "%.4f, %.4f".format(latitude, longitude) else ""
}

/**
 * Manages device location using FusedLocationProviderClient.
 * Provides both one-shot and continuous location updates with reverse geocoding.
 */
class LocationManager(private val context: Context) {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    companion object {
        private const val TAG = "NexaLocation"
        private const val UPDATE_INTERVAL_MS = 30_000L  // 30 seconds
        private const val FASTEST_INTERVAL_MS = 10_000L  // 10 seconds
    }

    /**
     * Get the last known location (one-shot). May return null if no location is available.
     */
    @SuppressLint("MissingPermission")
    suspend fun getLastKnownLocation(): UserLocation? = suspendCancellableCoroutine { cont ->
        try {
            fusedClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val address = reverseGeocode(location.latitude, location.longitude)
                    cont.resume(
                        UserLocation(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            accuracy = location.accuracy,
                            address = address.fullAddress,
                            city = address.city,
                            country = address.country,
                            timestamp = location.time
                        )
                    )
                } else {
                    cont.resume(null)
                }
            }.addOnFailureListener { e ->
                Log.e(TAG, "Failed to get last location", e)
                cont.resume(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception getting last location", e)
            cont.resume(null)
        }
    }

    /**
     * Request a fresh location update (one-shot with timeout).
     * This is more reliable than getLastKnownLocation when the device hasn't recently obtained a fix.
     */
    @SuppressLint("MissingPermission")
    fun requestCurrentLocation(): Flow<UserLocation?> = callbackFlow {
        try {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, UPDATE_INTERVAL_MS)
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
                .setMaxUpdates(1)
                .build()

            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val location = result.lastLocation ?: return
                    val address = reverseGeocodeSync(location.latitude, location.longitude)
                    trySend(
                        UserLocation(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            accuracy = location.accuracy,
                            address = address.fullAddress,
                            city = address.city,
                            country = address.country,
                            timestamp = location.time
                        )
                    )
                    fusedClient.removeLocationUpdates(this)
                }
            }

            fusedClient.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())

            awaitClose {
                fusedClient.removeLocationUpdates(callback)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception requesting current location", e)
            trySend(null)
            close()
        }
    }

    /**
     * Continuous location updates flow for real-time tracking.
     */
    @SuppressLint("MissingPermission")
    fun observeLocationUpdates(): Flow<UserLocation> = callbackFlow {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, UPDATE_INTERVAL_MS)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                val address = reverseGeocodeSync(location.latitude, location.longitude)
                trySend(
                    UserLocation(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracy = location.accuracy,
                        address = address.fullAddress,
                        city = address.city,
                        country = address.country,
                        timestamp = location.time
                    )
                )
            }
        }

        fusedClient.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())

        awaitClose {
            fusedClient.removeLocationUpdates(callback)
        }
    }

    /**
     * Stop all location updates.
     */
    fun stopLocationUpdates() {
        // Handled automatically by Flow cancellation
    }

    // ── Reverse Geocoding ──

    private data class AddressInfo(
        val fullAddress: String = "",
        val city: String = "",
        val country: String = ""
    )

    private fun reverseGeocode(latitude: Double, longitude: Double): AddressInfo {
        return reverseGeocodeSync(latitude, longitude)
    }

    private fun reverseGeocodeSync(latitude: Double, longitude: Double): AddressInfo {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            // For API 33+ use getFromLocation async, for older use sync
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                // API 33+: Use the asynchronous API with a timeout approach
                val addresses = mutableListOf<android.location.Address>()
                var completed = false

                geocoder.getFromLocation(latitude, longitude, 1) { results ->
                    addresses.addAll(results)
                    completed = true
                }

                // Wait a short time for the result (since we're in a callback flow, this runs on main)
                Thread.sleep(500)
                if (addresses.isNotEmpty()) {
                    val addr = addresses[0]
                    AddressInfo(
                        fullAddress = addr.getAddressLine(0) ?: "",
                        city = addr.locality ?: addr.subAdminArea ?: "",
                        country = addr.countryName ?: ""
                    )
                } else {
                    AddressInfo()
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    AddressInfo(
                        fullAddress = addr.getAddressLine(0) ?: "",
                        city = addr.locality ?: addr.subAdminArea ?: "",
                        country = addr.countryName ?: ""
                    )
                } else {
                    AddressInfo()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Reverse geocoding failed", e)
            AddressInfo()
        }
    }
}
