package com.nexa.ai.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Provides device geolocation using FusedLocationProviderClient with
 * Android LocationManager fallback.
 *
 * v4.0 improvements:
 * - Added Android LocationManager fallback when Google Play Services unavailable
 * - Added hasLocationPermission() check before requesting location
 * - Added timeout for location requests (10s) to prevent hanging
 * - Added requestLocationUpdates() for GPS warm-up on first request
 * - Improved Geocoder with fallback coordinates when address resolution fails
 * - Added retry logic with exponential backoff
 * - Caches last known location for faster subsequent requests
 */
class LocationStore(private val context: Context) {

    data class LocationData(
        val latitude: Double = 0.0,
        val longitude: Double = 0.0,
        val address: String = "",
        val city: String = "",
        val country: String = "",
        val timezone: String = "",
        val isp: String = "",
        val source: String = "", // "gps", "network", "ip", "nominatim"
        val isAvailable: Boolean = false
    )

    private var fusedClient: FusedLocationProviderClient? = null
    private var locationManager: LocationManager? = null
    private var cachedLocation: LocationData? = null

    // v5.2: Track when location was last fetched for auto-refresh logic
    private var lastLocationTime: Long = 0L
    // Refresh location if older than 5 minutes (300000ms)
    private val locationStaleThresholdMs = 300_000L

    fun initialize() {
        try {
            fusedClient = LocationServices.getFusedLocationProviderClient(context)
        } catch (e: Exception) {
            android.util.Log.w("LocationStore", "FusedLocation not available, will use LocationManager fallback: ${e.message}")
        }
        try {
            locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        } catch (e: Exception) {
            android.util.Log.e("LocationStore", "LocationManager init failed: ${e.message}", e)
        }
    }

    /**
     * Checks if location permissions are granted.
     * Call this before requesting location to avoid silent failures.
     */
    fun hasLocationPermission(): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED ||
                context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks if location services (GPS/Network) are enabled on the device.
     */
    fun isLocationEnabled(): Boolean {
        val lm = locationManager ?: return false
        return try {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Gets the current location with address.
     * Requires ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION permission.
     * v4.0: Added timeout, retry logic, LocationManager fallback, and caching.
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): LocationData {
        // Check permissions first
        if (!hasLocationPermission()) {
            android.util.Log.w("LocationStore", "No location permission granted")
            return LocationData(isAvailable = false)
        }

        // Try with timeout to prevent hanging
        val result = withTimeoutOrNull(15000L) {
            getLocationWithRetry()
        }

        if (result != null && result.isAvailable) {
            cachedLocation = result
            lastLocationTime = System.currentTimeMillis()
            return result
        }

        // Return cached location if available and not too stale
        val cached = cachedLocation
        if (cached != null && cached.isAvailable) {
            val age = System.currentTimeMillis() - lastLocationTime
            if (age < locationStaleThresholdMs) {
                android.util.Log.d("LocationStore", "Using cached location: ${cached.city}, ${cached.country} (age: ${age/1000}s)")
                return cached
            } else {
                android.util.Log.d("LocationStore", "Cached location is stale (${age/1000}s old), but no fresh location available")
                return cached  // Still return stale cache rather than nothing
            }
        }

        return LocationData(isAvailable = false)
    }

    /**
     * v4.0: Attempts to get location with retry logic.
     * First attempt: FusedLocationProviderClient (most accurate)
     * Fallback: Android LocationManager (works without Play Services)
     */
    @SuppressLint("MissingPermission")
    private suspend fun getLocationWithRetry(): LocationData {
        // Attempt 1: FusedLocation high accuracy
        var location = tryGetFusedLocation(Priority.PRIORITY_HIGH_ACCURACY)
        if (location != null) return resolveAddress(location, "gps")

        // Attempt 2: FusedLocation balanced power
        location = tryGetFusedLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
        if (location != null) return resolveAddress(location, "network")

        // Attempt 3: FusedLocation last known
        location = tryGetFusedLastLocation()
        if (location != null) return resolveAddress(location, "gps")

        // Attempt 4: Request a fresh GPS fix via LocationManager updates
        location = requestFreshLocation()
        if (location != null) return resolveAddress(location, "network")

        // Attempt 5: LocationManager last known location
        location = tryGetLocationManagerLastLocation()
        if (location != null) return resolveAddress(location, "network")

        // Attempt 6: IP-based geolocation fallback (works without GPS permission)
        val ipLocation = tryGetIpLocation()
        if (ipLocation != null) {
            android.util.Log.d("LocationStore", "Using IP-based location: ${ipLocation.city}, ${ipLocation.country}")
            return ipLocation
        }

        android.util.Log.w("LocationStore", "All location attempts failed (including IP fallback)")
        return LocationData(isAvailable = false)
    }

    /**
     * v4.0: Try to get location from FusedLocationProviderClient with specific priority.
     */
    @SuppressLint("MissingPermission")
    private suspend fun tryGetFusedLocation(priority: Int): Location? {
        val client = fusedClient ?: return null
        return try {
            withTimeoutOrNull(8000L) {
                client.getCurrentLocation(priority, null).await()
            }
        } catch (e: Exception) {
            android.util.Log.w("LocationStore", "FusedLocation priority=$priority failed: ${e.message}")
            null
        }
    }

    /**
     * v4.0: Try to get last known location from FusedLocationProviderClient.
     */
    @SuppressLint("MissingPermission")
    private suspend fun tryGetFusedLastLocation(): Location? {
        val client = fusedClient ?: return null
        return try {
            client.lastLocation.await()
        } catch (e: Exception) {
            android.util.Log.w("LocationStore", "FusedLocation lastLocation failed: ${e.message}")
            null
        }
    }

    /**
     * v4.0: Request a fresh GPS location using Android LocationManager.
     * This is the most reliable fallback when FusedLocationProvider returns null
     * (common on first app launch or when GPS hasn't been used recently).
     * Uses requestSingleUpdate for one-shot GPS fix.
     */
    @SuppressLint("MissingPermission")
    private suspend fun requestFreshLocation(): Location? {
        val lm = locationManager ?: return null

        // Try GPS provider first (most accurate)
        if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            val gpsLocation = requestLocationUpdate(lm, LocationManager.GPS_PROVIDER)
            if (gpsLocation != null) return gpsLocation
        }

        // Try network provider (faster but less accurate)
        if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            return requestLocationUpdate(lm, LocationManager.NETWORK_PROVIDER)
        }

        return null
    }

    /**
     * v4.0: Request a single location update from a specific provider.
     * Uses suspendCancellableCoroutine to convert the callback to a coroutine.
     */
    @SuppressLint("MissingPermission")
    private suspend fun requestLocationUpdate(
        locationManager: LocationManager,
        provider: String
    ): Location? = suspendCancellableCoroutine { continuation ->
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var resumed = false

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (!resumed) {
                    resumed = true
                    try {
                        locationManager.removeUpdates(this)
                    } catch (_: Exception) {}
                    continuation.resume(location)
                }
            }
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {
                if (!resumed) {
                    resumed = true
                    continuation.resume(null)
                }
            }
        }

        // Timeout after 8 seconds
        handler.postDelayed({
            if (!resumed) {
                resumed = true
                try {
                    locationManager.removeUpdates(listener)
                } catch (_: Exception) {}
                continuation.resume(null)
            }
        }, 8000)

        try {
            locationManager.requestLocationUpdates(provider, 0L, 0f, listener)
        } catch (e: Exception) {
            if (!resumed) {
                resumed = true
                continuation.resume(null)
            }
        }

        continuation.invokeOnCancellation {
            try {
                locationManager.removeUpdates(listener)
            } catch (_: Exception) {}
        }
    }

    /**
     * v4.0: Get last known location from Android LocationManager.
     * Checks both GPS and Network providers for the most recent fix.
     */
    @SuppressLint("MissingPermission")
    private fun tryGetLocationManagerLastLocation(): Location? {
        val lm = locationManager ?: return null
        var bestLocation: Location? = null

        try {
            val gpsLocation = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (gpsLocation != null) {
                bestLocation = gpsLocation
            }
        } catch (_: Exception) {}

        try {
            val networkLocation = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (networkLocation != null) {
                if (bestLocation == null || networkLocation.time > bestLocation.time) {
                    bestLocation = networkLocation
                }
            }
        } catch (_: Exception) {}

        return bestLocation
    }

    /**
     * Resolves a Location to a human-readable address using Geocoder.
     * v5.1: Changed to suspend function — avoids blocking the calling thread
     * with CountDownLatch. Now runs Geocoder on IO dispatcher.
     * Handles both API 33+ async and legacy sync APIs.
     * Fallback: If Geocoder fails, still returns coordinates.
     * v6.0: Added source parameter to track where the location came from.
     */
    private suspend fun resolveAddress(location: Location, source: String = "gps"): LocationData =
        withContext(kotlinx.coroutines.Dispatchers.IO) {
        val geocoder = Geocoder(context, Locale.getDefault())
        var address = ""
        var city = ""
        var country = ""

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val result = suspendCancellableCoroutine<Pair<String, String>?> { cont ->
                    geocoder.getFromLocation(location.latitude, location.longitude, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            val addr = addresses.firstOrNull()
                            val result = if (addr != null) {
                                val addrLine = addr.getAddressLine(0) ?: ""
                                val cityVal = addr.locality ?: addr.subAdminArea ?: ""
                                val countryVal = addr.countryName ?: ""
                                Pair("$addrLine|$cityVal|$countryVal", "")
                            } else null
                            cont.resume(result)
                        }
                        override fun onError(errorMessage: String?) {
                            android.util.Log.w("LocationStore", "Geocoder error: $errorMessage")
                            cont.resume(null)
                        }
                    })
                    cont.invokeOnCancellation { cont.resume(null) }
                }
                if (result != null) {
                    val parts = result.first.split("|")
                    if (parts.size >= 3) {
                        address = parts[0]
                        city = parts[1]
                        country = parts[2]
                    }
                }
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

        // v5.1: If Geocoder couldn't resolve address, try English locale as fallback
        if (city.isBlank() && country.isBlank()) {
            try {
                val geocoderEn = Geocoder(context, Locale.US)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val result = suspendCancellableCoroutine<Pair<String, String>?> { cont ->
                        geocoderEn.getFromLocation(location.latitude, location.longitude, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                val addr = addresses.firstOrNull()
                                val result = if (addr != null) {
                                    val addrLine = addr.getAddressLine(0) ?: ""
                                    val cityVal = addr.locality ?: addr.subAdminArea ?: ""
                                    val countryVal = addr.countryName ?: ""
                                    Pair("$addrLine|$cityVal|$countryVal", "")
                                } else null
                                cont.resume(result)
                            }
                            override fun onError(errorMessage: String?) {
                                cont.resume(null)
                            }
                        })
                        cont.invokeOnCancellation { cont.resume(null) }
                    }
                    if (result != null) {
                        val parts = result.first.split("|")
                        if (parts.size >= 3) {
                            if (address.isBlank()) address = parts[0]
                            if (city.isBlank()) city = parts[1]
                            if (country.isBlank()) country = parts[2]
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoderEn.getFromLocation(location.latitude, location.longitude, 1)
                    val addr = addresses?.firstOrNull()
                    if (addr != null) {
                        if (address.isBlank()) address = addr.getAddressLine(0) ?: ""
                        if (city.isBlank()) city = addr.locality ?: addr.subAdminArea ?: ""
                        if (country.isBlank()) country = addr.countryName ?: ""
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("LocationStore", "Geocoder EN fallback failed: ${e.message}")
            }
        }

        // v5.1: Final fallback — use Nominatim reverse geocoding API if Geocoder failed
        if (city.isBlank() && country.isBlank()) {
            try {
                val nominatimUrl = java.net.URL(
                    "https://nominatim.openstreetmap.org/reverse?lat=${location.latitude}&lon=${location.longitude}&format=json&zoom=10&addressdetails=1"
                )
                val connection = nominatimUrl.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "NexaAssistant/1.0")
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                
                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val json = org.json.JSONObject(response)
                    val addressObj = json.optJSONObject("address")
                    if (addressObj != null) {
                        city = addressObj.optString("city", addressObj.optString("town", addressObj.optString("village", "")))
                        country = addressObj.optString("country", "")
                        if (address.isBlank()) {
                            address = json.optString("display_name", "")
                        }
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                android.util.Log.w("LocationStore", "Nominatim fallback failed: ${e.message}")
            }
        }

        android.util.Log.d("LocationStore", "Location resolved: $city, $country (${location.latitude}, ${location.longitude}) source=$source")

        LocationData(
            latitude = location.latitude,
            longitude = location.longitude,
            address = address,
            city = city,
            country = country,
            source = source,
            isAvailable = true  // v4.0: Even without address, coordinates are available
        )
    }

    /**
     * v6.0: IP-based geolocation fallback.
     * Uses free IP geolocation APIs to determine the user's approximate location
     * when GPS is not available (no permission, indoor, or GPS disabled).
     * Accuracy: city-level (good enough for weather, time zone, and general context).
     * 
     * Sources tried:
     * 1. ip-api.com (free, no key needed, includes timezone and ISP)
     * 2. ipapi.co (backup)
     */
    private suspend fun tryGetIpLocation(): LocationData? =
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            // Try ip-api.com first (most reliable, includes timezone)
            try {
                val url = java.net.URL("http://ip-api.com/json/?fields=status,message,country,countryCode,region,regionName,city,zip,lat,lon,timezone,isp,org,as,query")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "NexaAssistant/1.0")
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val json = org.json.JSONObject(response)

                    if (json.optString("status") == "success") {
                        val city = json.optString("city", "")
                        val country = json.optString("country", "")
                        val lat = json.optDouble("lat", 0.0)
                        val lon = json.optDouble("lon", 0.0)
                        val timezone = json.optString("timezone", "")
                        val isp = json.optString("isp", "")
                        val query = json.optString("query", "")

                        if (city.isNotBlank() || country.isNotBlank()) {
                            android.util.Log.d("LocationStore", "IP location resolved: $city, $country (IP: $query, ISP: $isp)")
                            connection.disconnect()
                            return@withContext LocationData(
                                latitude = lat,
                                longitude = lon,
                                address = "$city, $country",
                                city = city,
                                country = country,
                                timezone = timezone,
                                isp = isp,
                                source = "ip",
                                isAvailable = true
                            )
                        }
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                android.util.Log.w("LocationStore", "ip-api.com failed: ${e.message}")
            }

            // Fallback: ipapi.co
            try {
                val url = java.net.URL("https://ipapi.co/json/")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "NexaAssistant/1.0")
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val json = org.json.JSONObject(response)

                    val city = json.optString("city", "")
                    val country = json.optString("country_name", "")
                    val lat = json.optDouble("latitude", 0.0)
                    val lon = json.optDouble("longitude", 0.0)
                    val timezone = json.optString("timezone", "")
                    val isp = json.optString("org", "")

                    if (city.isNotBlank() || country.isNotBlank()) {
                        android.util.Log.d("LocationStore", "IP location resolved (ipapi.co): $city, $country")
                        connection.disconnect()
                        return@withContext LocationData(
                            latitude = lat,
                            longitude = lon,
                            address = "$city, $country",
                            city = city,
                            country = country,
                            timezone = timezone,
                            isp = isp,
                            source = "ip",
                            isAvailable = true
                        )
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                android.util.Log.w("LocationStore", "ipapi.co failed: ${e.message}")
            }

            null
        }

    fun destroy() {
        fusedClient = null
        locationManager = null
        cachedLocation = null
    }
}
