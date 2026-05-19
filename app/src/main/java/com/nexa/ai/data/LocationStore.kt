package com.nexa.ai.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Provides device geolocation using FusedLocationProviderClient.
 * Returns location data as a simple data class with coordinates and address.
 */
class LocationStore(private val context: Context) {

    data class LocationData(
        val latitude: Double = 0.0,
        val longitude: Double = 0.0,
        val address: String = "",
        val city: String = "",
        val country: String = "",
        val isAvailable: Boolean = false
    )

    private var fusedClient: FusedLocationProviderClient? = null

    fun initialize() {
        try {
            fusedClient = LocationServices.getFusedLocationProviderClient(context)
        } catch (e: Exception) {
            android.util.Log.e("LocationStore", "Failed to init FusedLocation: ${e.message}", e)
        }
    }

    /**
     * Gets the current location with address.
     * Requires ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION permission.
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): LocationData {
        val client = fusedClient ?: return LocationData()
        return try {
            // Try high accuracy first for better results
            val location = try {
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
            } catch (e: Exception) {
                android.util.Log.w("LocationStore", "HIGH_ACCURACY failed, trying BALANCED: ${e.message}")
                try {
                    client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
                } catch (e2: Exception) {
                    android.util.Log.w("LocationStore", "BALANCED failed, trying lastKnown: ${e2.message}")
                    try {
                        client.lastLocation.await()
                    } catch (_: Exception) {
                        null
                    }
                }
            }

            if (location != null) {
                resolveAddress(location)
            } else {
                // Final fallback: try lastLocation one more time
                try {
                    val lastLocation = client.lastLocation.await()
                    if (lastLocation != null) {
                        resolveAddress(lastLocation)
                    } else {
                        LocationData(isAvailable = false)
                    }
                } catch (_: Exception) {
                    LocationData(isAvailable = false)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("LocationStore", "Location error: ${e.message}", e)
            LocationData(isAvailable = false)
        }
    }

    /**
     * Resolves a Location to a human-readable address using Geocoder.
     * Uses blocking Geocoder API on a background thread (safe inside a suspend function).
     */
    private fun resolveAddress(location: Location): LocationData {
        val geocoder = Geocoder(context, Locale.getDefault())
        var address = ""
        var city = ""
        var country = ""

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+: Use async Geocoder API
                geocoder.getFromLocation(location.latitude, location.longitude, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        val addr = addresses.firstOrNull()
                        if (addr != null) {
                            address = addr.getAddressLine(0) ?: ""
                            city = addr.locality ?: addr.subAdminArea ?: ""
                            country = addr.countryName ?: ""
                        }
                    }
                    override fun onError(errorMessage: String?) {
                        android.util.Log.w("LocationStore", "Geocoder error: $errorMessage")
                    }
                })
                // Small delay for async result
                Thread.sleep(500)
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                val addr = addresses?.firstOrNull()
                if (addr != null) {
                    address = addr.getAddressLine(0) ?: ""
                    city = addr.locality ?: addr.subAdminArea ?: ""
                    country = addr.countryName ?: ""
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("LocationStore", "Geocoder failed: ${e.message}")
        }

        return LocationData(
            latitude = location.latitude,
            longitude = location.longitude,
            address = address,
            city = city,
            country = country,
            isAvailable = true
        )
    }

    fun destroy() {
        fusedClient = null
    }
}
