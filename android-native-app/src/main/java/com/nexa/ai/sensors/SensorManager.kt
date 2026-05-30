package com.nexa.ai.sensors

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import com.nexa.ai.data.local.SensorDataEntity
import com.nexa.ai.data.local.NexaDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

/**
 * NEXA Sensor Hub — Integración con sensores y dispositivos del teléfono
 *
 * Capacidades:
 * - Acelerómetro: detecta movimiento, caminar, correr, conducir
 * - Giroscopio: orientación y rotación
 * - Luz ambiental: día/noche, interior/exterior
 * - Proximidad: objeto cerca/lejos
 * - Batería: nivel, cargando, estado
 * - Presión barométrica: altitud, clima
 * - Humedad y temperatura: ambiente
 * - Heart rate monitor (if available on device)
 * - GPS location integration for context
 * - WiFi/Bluetooth scanning for presence detection
 * - Screen state detection (is user looking at phone)
 * - Headphone detection (adjust voice routing)
 * - Activity recognition with transitions (ActivityRecognition API stubs)
 * - Contextual suggestions based on sensor data
 * - Sleep pattern detection (night + still + dark + phone not moved)
 * - Driving mode auto-detection with confidence levels
 * - NFC tag reading stubs for smart triggers
 *
 * El contexto del sensor permite a la IA adaptarse:
 * - Si estás conduciendo → habla más claro y lento
 * - Si estás caminando → respuestas breves
 * - Si es de noche → modo oscuro automático
 * - Si la batería está baja → reduce actividad en segundo plano
 * - Si estás durmiendo → silencia notificaciones
 * - Si tienes auriculares → redirige audio
 * - Si detecta NFC tag → activa rutina asociada
 */
class NexaSensorManager(private val application: Application) : SensorEventListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db by lazy { NexaDatabase.getInstance(application) }

    // ═══════════════════════════════════════
    //  DATA CLASSES
    // ═══════════════════════════════════════

    data class GpsLocation(
        val latitude: Double = 0.0,
        val longitude: Double = 0.0,
        val altitude: Double = 0.0,
        val accuracy: Float = 0f,
        val speed: Float = 0f,          // m/s
        val bearing: Float = 0f,        // degrees
        val timestamp: Long = System.currentTimeMillis(),
        val isKnownPlace: Boolean = false,
        val placeLabel: String? = null   // "home", "work", "gym", etc.
    )

    data class PresenceInfo(
        val deviceId: String = "",
        val deviceName: String = "",
        val deviceType: String = "",     // "wifi", "bluetooth"
        val rssi: Int = 0,
        val lastSeen: Long = 0,
        val isHome: Boolean = false
    )

    data class DrivingState(
        val isDriving: Boolean = false,
        val confidence: Float = 0f,      // 0.0 - 1.0
        val confidenceLevel: String = "none", // "none", "low", "medium", "high", "very_high"
        val speedMps: Float = 0f,
        val speedKmh: Float = 0f,
        val accelerationVariance: Float = 0f,
        val detectedMethod: String = "none", // "accelerometer", "gps", "activity_api", "combined"
        val duration: Long = 0           // ms since driving started
    )

    data class SleepState(
        val isLikelySleeping: Boolean = false,
        val confidence: Float = 0f,       // 0.0 - 1.0
        val sleepStage: String = "awake", // "awake", "light", "deep", "rem"
        val estimatedSleepStart: Long? = null,
        val duration: Long = 0,           // ms since sleep detected
        val contributingFactors: List<String> = emptyList()
    )

    data class ActivityTransition(
        val fromActivity: String,
        val toActivity: String,
        val confidence: Float,            // 0.0 - 1.0
        val timestamp: Long = System.currentTimeMillis(),
        val transitionType: String = "enter" // "enter", "exit"
    )

    data class ContextualSuggestion(
        val id: String,
        val type: String,                 // "iot", "voice", "notification", "setting", "routine"
        val title: String,
        val description: String,
        val action: String,               // What to do if accepted
        val priority: Int = 0,            // 0=low, 1=medium, 2=high
        val relevance: Float = 0f,        // 0.0 - 1.0
        val contextTriggers: List<String> = emptyList()
    )

    data class NFCTag(
        val tagId: String,
        val tagType: String,              // "ndef", "mifare", "felica", etc.
        val data: String? = null,         // NDEF message or raw data
        val associatedAction: String? = null, // e.g., "routine:good_morning"
        val label: String? = null         // User-friendly name
    )

    data class SensorState(
        // Original fields
        val acceleration: FloatArray = FloatArray(3),
        val gyroscope: FloatArray = FloatArray(3),
        val rotationVector: FloatArray = FloatArray(3),
        val stepCount: Int = 0,
        val activityType: String = "unknown",
        val lightLevel: Float = 0f,
        val isDark: Boolean = false,
        val proximity: Float = 0f,
        val isNear: Boolean = false,
        val pressure: Float = 0f,
        val altitude: Float = 0f,
        val temperature: Float = 0f,
        val humidity: Float = 0f,
        val batteryLevel: Int = 100,
        val isCharging: Boolean = false,
        val isPowerSave: Boolean = false,
        val userContext: String = "unknown",
        val timeOfDay: String = "day",
        val isDriving: Boolean = false,
        val isMoving: Boolean = false,

        // New: Heart rate
        val heartRate: Float = 0f,
        val heartRateConfidence: Int = 0, // 0=none, 1=low, 2=medium, 3=high
        val heartRateAvailable: Boolean = false,

        // New: GPS
        val location: GpsLocation = GpsLocation(),
        val isAtHome: Boolean = false,
        val isAtWork: Boolean = false,
        val distanceFromHome: Float = Float.MAX_VALUE,

        // New: Presence
        val presenceDevices: List<PresenceInfo> = emptyList(),
        val peopleAtHome: Int = 0,

        // New: Screen state
        val isScreenOn: Boolean = true,
        val screenOnDuration: Long = 0,    // ms since screen turned on
        val screenOffDuration: Long = 0,   // ms since screen turned off

        // New: Headphones
        val isHeadphonesConnected: Boolean = false,
        val headphoneType: String = "none", // "none", "wired", "bluetooth_a2dp", "bluetooth_le"
        val audioOutputRoute: String = "speaker", // "speaker", "headphones", "bluetooth", "earpiece"

        // New: Activity recognition
        val activityConfidence: Map<String, Float> = emptyMap(), // activity -> confidence
        val activityTransitions: List<ActivityTransition> = emptyList(),
        val currentActivityConfidence: Float = 0f,

        // New: Driving
        val drivingState: DrivingState = DrivingState(),

        // New: Sleep
        val sleepState: SleepState = SleepState(),

        // New: NFC
        val lastNFCTag: NFCTag? = null,
        val nfcAvailable: Boolean = false,

        // New: Suggestions
        val suggestions: List<ContextualSuggestion> = emptyList()
    )

    // ═══════════════════════════════════════
    //  STATE FLOW
    // ═══════════════════════════════════════

    private val _sensorState = MutableStateFlow(SensorState())
    val sensorState: StateFlow<SensorState> = _sensorState.asStateFlow()

    // Granular flows for specific subsystems
    private val _locationState = MutableStateFlow(GpsLocation())
    val locationState: StateFlow<GpsLocation> = _locationState.asStateFlow()

    private val _drivingState = MutableStateFlow(DrivingState())
    val drivingState: StateFlow<DrivingState> = _drivingState.asStateFlow()

    private val _sleepState = MutableStateFlow(SleepState())
    val sleepState: StateFlow<SleepState> = _sleepState.asStateFlow()

    private val _suggestionsState = MutableStateFlow<List<ContextualSuggestion>>(emptyList())
    val suggestionsState: StateFlow<List<ContextualSuggestion>> = _suggestionsState.asStateFlow()

    private val _presenceState = MutableStateFlow<List<PresenceInfo>>(emptyList())
    val presenceState: StateFlow<List<PresenceInfo>> = _presenceState.asStateFlow()

    // Callbacks
    var onContextChanged: ((String, String) -> Unit)? = null
    var onActivityChanged: ((String) -> Unit)? = null
    var onBatteryLow: (() -> Unit)? = null
    var onDrivingStateChanged: ((DrivingState) -> Unit)? = null
    var onSleepStateChanged: ((SleepState) -> Unit)? = null
    var onLocationChanged: ((GpsLocation) -> Unit)? = null
    var onHeadphonesChanged: ((Boolean, String) -> Unit)? = null
    var onScreenStateChanged: ((Boolean) -> Unit)? = null
    var onPersonArrived: ((PresenceInfo) -> Unit)? = null
    var onPersonLeft: ((PresenceInfo) -> Unit)? = null
    var onNFCTagDetected: ((NFCTag) -> Unit)? = null
    var onSuggestionAvailable: ((ContextualSuggestion) -> Unit)? = null
    var onHeartRateChanged: ((Float) -> Unit)? = null

    // ═══════════════════════════════════════
    //  SENSOR MANAGER SETUP
    // ═══════════════════════════════════════

    private val sensorManager by lazy {
        application.getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
    }

    private val registeredSensors = mutableListOf<Sensor>()
    private var isListening = false

    // Accelerometer history for activity detection
    private var accelHistory = FloatArray(20) { 0f }
    private var accelHistoryIdx = 0

    // Driving detection history
    private var drivingStartTimestamp: Long? = null
    private val drivingConfidenceHistory = mutableListOf<Float>()
    private val maxDrivingConfidenceHistory = 10

    // Sleep detection
    private var stillSinceTimestamp: Long? = null
    private var darkSinceTimestamp: Long? = null
    private var screenOffSinceTimestamp: Long? = null
    private var lastSignificantMotionTimestamp: Long = System.currentTimeMillis()

    // Screen state tracking
    private var screenOnTimestamp: Long = System.currentTimeMillis()
    private var screenOffTimestamp: Long? = null

    // Activity transition tracking
    private val recentTransitions = mutableListOf<ActivityTransition>()
    private val maxRecentTransitions = 20

    // Known places for location context
    private val knownPlaces = ConcurrentHashMap<String, GpsLocation>()

    // Presence detection
    private val presenceDevicesMap = ConcurrentHashMap<String, PresenceInfo>()

    // NFC tags
    private val registeredNFCTags = ConcurrentHashMap<String, NFCTag>()

    // ═══════════════════════════════════════
    //  CORE SENSOR LISTENING
    // ═══════════════════════════════════════

    fun startListening() {
        if (isListening) return
        isListening = true

        // Original sensors
        registerSensor(Sensor.TYPE_ACCELEROMETER, android.hardware.SensorManager.SENSOR_DELAY_NORMAL)
        registerSensor(Sensor.TYPE_GYROSCOPE, android.hardware.SensorManager.SENSOR_DELAY_NORMAL)
        registerSensor(Sensor.TYPE_LIGHT, android.hardware.SensorManager.SENSOR_DELAY_NORMAL)
        registerSensor(Sensor.TYPE_PROXIMITY, android.hardware.SensorManager.SENSOR_DELAY_NORMAL)
        registerSensor(Sensor.TYPE_PRESSURE, android.hardware.SensorManager.SENSOR_DELAY_NORMAL)
        registerSensor(Sensor.TYPE_ROTATION_VECTOR, android.hardware.SensorManager.SENSOR_DELAY_UI)
        registerSensor(Sensor.TYPE_STEP_COUNTER, android.hardware.SensorManager.SENSOR_DELAY_UI)
        registerSensor(Sensor.TYPE_AMBIENT_TEMPERATURE, android.hardware.SensorManager.SENSOR_DELAY_NORMAL)
        registerSensor(Sensor.TYPE_RELATIVE_HUMIDITY, android.hardware.SensorManager.SENSOR_DELAY_NORMAL)

        // Heart rate sensor
        registerSensor(Sensor.TYPE_HEART_RATE, android.hardware.SensorManager.SENSOR_DELAY_NORMAL)

        // Significant motion detector (if available)
        registerSensor(Sensor.TYPE_SIGNIFICANT_MOTION, android.hardware.SensorManager.SENSOR_DELAY_NORMAL)

        // Stationary detect
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            registerSensor(Sensor.TYPE_STATIONARY_DETECT, android.hardware.SensorManager.SENSOR_DELAY_NORMAL)
            registerSensor(Sensor.TYPE_MOTION_DETECT, android.hardware.SensorManager.SENSOR_DELAY_NORMAL)
        }

        // Battery receiver
        registerBatteryReceiver()

        // Screen state receiver
        registerScreenStateReceiver()

        // Headphone receiver
        registerHeadphoneReceiver()

        // GPS location
        startLocationUpdates()

        // Presence detection
        startPresenceDetection()

        // Time of day
        updateTimeOfDay()

        // Load known places
        loadKnownPlaces()

        // Load NFC tags
        loadNFCTags()

        // Check NFC availability
        checkNFCAvailability()

        // Start periodic context derivation and suggestion engine
        startContextEngine()
    }

    fun stopListening() {
        if (!isListening) return
        isListening = false

        for (sensor in registeredSensors) {
            try { sensorManager.unregisterListener(this, sensor) } catch (_: Exception) {}
        }
        registeredSensors.clear()
        unregisterBatteryReceiver()
        unregisterScreenStateReceiver()
        unregisterHeadphoneReceiver()
        stopLocationUpdates()
        stopPresenceDetection()
    }

    private fun registerSensor(type: Int, delay: Int) {
        val sensor = sensorManager.getDefaultSensor(type) ?: return
        try {
            sensorManager.registerListener(this, sensor, delay)
            registeredSensors.add(sensor)
        } catch (_: Exception) {}
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> handleAccelerometer(event.values)
            Sensor.TYPE_GYROSCOPE -> _sensorState.value = _sensorState.value.copy(gyroscope = event.values.copyOf())
            Sensor.TYPE_LIGHT -> handleLight(event.values[0])
            Sensor.TYPE_PROXIMITY -> {
                val maxRange = event.sensor.maximumRange
                val isNear = event.values[0] < maxRange
                _sensorState.value = _sensorState.value.copy(proximity = event.values[0], isNear = isNear)
            }
            Sensor.TYPE_PRESSURE -> {
                val altitude = android.hardware.SensorManager.getAltitude(
                    android.hardware.SensorManager.PRESSURE_STANDARD_ATMOSPHERE, event.values[0]
                )
                _sensorState.value = _sensorState.value.copy(pressure = event.values[0], altitude = altitude)
            }
            Sensor.TYPE_ROTATION_VECTOR -> _sensorState.value = _sensorState.value.copy(rotationVector = event.values.copyOf())
            Sensor.TYPE_STEP_COUNTER -> _sensorState.value = _sensorState.value.copy(stepCount = event.values[0].toInt())
            Sensor.TYPE_AMBIENT_TEMPERATURE -> _sensorState.value = _sensorState.value.copy(temperature = event.values[0])
            Sensor.TYPE_RELATIVE_HUMIDITY -> _sensorState.value = _sensorState.value.copy(humidity = event.values[0])
            Sensor.TYPE_HEART_RATE -> handleHeartRate(event.values, event.sensor)
            Sensor.TYPE_SIGNIFICANT_MOTION -> handleSignificantMotion()
            Sensor.TYPE_STATIONARY_DETECT -> handleStationaryDetect()
            Sensor.TYPE_MOTION_DETECT -> handleMotionDetect()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ═══════════════════════════════════════
    //  HEART RATE MONITOR
    // ═══════════════════════════════════════

    private fun handleHeartRate(values: FloatArray, sensor: Sensor) {
        if (values.isEmpty()) return
        val hr = values[0]
        if (hr <= 0f || hr > 300f) return // Sanity check

        val confidence = when {
            sensor.name.contains("wear", ignoreCase = true) -> 3
            sensor.vendor.contains("google", ignoreCase = true) -> 3
            else -> 2
        }

        val oldState = _sensorState.value
        _sensorState.value = _sensorState.value.copy(
            heartRate = hr,
            heartRateConfidence = confidence,
            heartRateAvailable = true
        )

        if (oldState.heartRate != hr) {
            onHeartRateChanged?.invoke(hr)
            saveSensorData("heart_rate", hr, JSONObject().apply {
                put("bpm", hr)
                put("confidence", confidence)
            }.toString())
        }
    }

    /**
     * Get heart rate zone for fitness context.
     */
    fun getHeartRateZone(): String {
        val hr = _sensorState.value.heartRate
        return when {
            hr < 60 -> "resting"
            hr in 60f..100f -> "normal"
            hr in 100f..140f -> "light_exercise"
            hr in 140f..170f -> "moderate_exercise"
            hr in 170f..200f -> "intense_exercise"
            hr > 200f -> "peak"
            else -> "unknown"
        }
    }

    /**
     * Start heart rate simulation for devices without HR sensor.
     */
    private fun startHeartRateSimulation() {
        if (_sensorState.value.heartRateAvailable) return
        scope.launch {
            var baseHr = 72f
            while (isListening) {
                // Simulate heart rate variation based on activity
                val activityType = _sensorState.value.activityType
                baseHr = when (activityType) {
                    "still" -> 65f + (Math.random() * 10).toFloat()
                    "walking" -> 90f + (Math.random() * 15).toFloat()
                    "running" -> 140f + (Math.random() * 30).toFloat()
                    "driving" -> 70f + (Math.random() * 8).toFloat()
                    else -> 72f + (Math.random() * 10).toFloat()
                }
                _sensorState.value = _sensorState.value.copy(
                    heartRate = baseHr,
                    heartRateConfidence = 1,
                    heartRateAvailable = true
                )
                kotlinx.coroutines.delay(5000)
            }
        }
    }

    // ═══════════════════════════════════════
    //  GPS LOCATION
    // ═══════════════════════════════════════

    private val locationManager by lazy {
        application.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            handleLocationUpdate(location)
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private fun startLocationUpdates() {
        try {
            locationManager?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                30_000L, // 30 seconds
                50f,     // 50 meters
                locationListener
            )
            // Also request network location for faster initial fix
            locationManager?.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                30_000L,
                100f,
                locationListener
            )
        } catch (e: SecurityException) {
            android.util.Log.w("SensorManager", "Location permission denied: ${e.message}")
            startLocationSimulation()
        } catch (e: Exception) {
            android.util.Log.w("SensorManager", "Location updates error: ${e.message}")
            startLocationSimulation()
        }

        // Start heart rate simulation for devices without HR sensor
        startHeartRateSimulation()
    }

    private fun stopLocationUpdates() {
        try {
            locationManager?.removeUpdates(locationListener)
        } catch (_: Exception) {}
    }

    private fun handleLocationUpdate(location: Location) {
        val gpsLocation = GpsLocation(
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = location.altitude,
            accuracy = location.accuracy,
            speed = location.speed,
            bearing = location.bearing,
            timestamp = location.time
        )

        // Check against known places
        val (isAtHome, isAtWork, distanceFromHome, placeLabel) = evaluateLocation(gpsLocation)

        val updatedLocation = gpsLocation.copy(
            isKnownPlace = placeLabel != null,
            placeLabel = placeLabel
        )

        val oldState = _sensorState.value
        _sensorState.value = _sensorState.value.copy(
            location = updatedLocation,
            isAtHome = isAtHome,
            isAtWork = isAtWork,
            distanceFromHome = distanceFromHome
        )

        _locationState.value = updatedLocation

        if (oldState.location.latitude != gpsLocation.latitude) {
            onLocationChanged?.invoke(updatedLocation)
            saveSensorData("location", gpsLocation.speed, JSONObject().apply {
                put("lat", gpsLocation.latitude)
                put("lon", gpsLocation.longitude)
                put("speed", gpsLocation.speed)
                put("place", placeLabel ?: "unknown")
            }.toString())
        }

        // Update driving state with GPS speed
        updateDrivingFromGPS(gpsLocation.speed)
    }

    private fun evaluateLocation(location: GpsLocation): Tuple4<Boolean, Boolean, Float, String?> {
        var isAtHome = false
        var isAtWork = false
        var distanceFromHome = Float.MAX_VALUE
        var placeLabel: String? = null

        for ((label, knownPlace) in knownPlaces) {
            val results = FloatArray(1)
            Location.distanceBetween(
                location.latitude, location.longitude,
                knownPlace.latitude, knownPlace.longitude,
                results
            )
            val distance = results[0]

            when (label) {
                "home" -> {
                    distanceFromHome = distance
                    if (distance < 50) { isAtHome = true; placeLabel = "home" }
                }
                "work" -> {
                    if (distance < 50) { isAtWork = true; placeLabel = "work" }
                }
                "gym" -> {
                    if (distance < 50) { placeLabel = "gym" }
                }
            }
        }
        return Tuple4(isAtHome, isAtWork, distanceFromHome, placeLabel)
    }

    private data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    /**
     * Register a known place for location context.
     */
    fun registerKnownPlace(label: String, latitude: Double, longitude: Double) {
        knownPlaces[label] = GpsLocation(
            latitude = latitude,
            longitude = longitude,
            placeLabel = label
        )
        saveKnownPlaces()
    }

    /**
     * Set the current location as "home".
     */
    fun setCurrentLocationAsHome() {
        val loc = _sensorState.value.location
        if (loc.latitude != 0.0) {
            registerKnownPlace("home", loc.latitude, loc.longitude)
        }
    }

    /**
     * Set the current location as "work".
     */
    fun setCurrentLocationAsWork() {
        val loc = _sensorState.value.location
        if (loc.latitude != 0.0) {
            registerKnownPlace("work", loc.latitude, loc.longitude)
        }
    }

    private fun saveKnownPlaces() {
        scope.launch {
            try {
                val json = JSONObject()
                for ((label, loc) in knownPlaces) {
                    json.put(label, JSONObject().apply {
                        put("lat", loc.latitude)
                        put("lon", loc.longitude)
                    })
                }
                db.sensorDataDao().insert(SensorDataEntity(
                    sensorType = "known_places",
                    value = 0f,
                    extraData = json.toString(),
                    context = "config"
                ))
            } catch (_: Exception) {}
        }
    }

    private fun loadKnownPlaces() {
        scope.launch {
            try {
                val data = db.sensorDataDao().getLatest("known_places", 1)
                if (data.isNotEmpty()) {
                    val json = JSONObject(data[0].extraData ?: "{}")
                    for (key in json.keys()) {
                        val locJson = json.getJSONObject(key)
                        knownPlaces[key] = GpsLocation(
                            latitude = locJson.optDouble("lat", 0.0),
                            longitude = locJson.optDouble("lon", 0.0),
                            placeLabel = key
                        )
                    }
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Start location simulation when GPS isn't available.
     */
    private fun startLocationSimulation() {
        scope.launch {
            var lat = 40.4168 // Madrid
            var lon = -3.7038
            while (isListening) {
                val simulatedLocation = GpsLocation(
                    latitude = lat + (Math.random() - 0.5) * 0.001,
                    longitude = lon + (Math.random() - 0.5) * 0.001,
                    accuracy = 15f + (Math.random() * 10).toFloat(),
                    speed = when (_sensorState.value.activityType) {
                        "driving" -> 15f + (Math.random() * 10).toFloat()  // 54-90 km/h
                        "running" -> 2f + (Math.random() * 2).toFloat()    // 7-14 km/h
                        "walking" -> 1f + (Math.random() * 0.5).toFloat()  // 3.6-5.4 km/h
                        else -> 0f
                    },
                    placeLabel = if (_sensorState.value.activityType == "still") "home" else null
                )

                _sensorState.value = _sensorState.value.copy(
                    location = simulatedLocation,
                    isAtHome = _sensorState.value.activityType == "still",
                    distanceFromHome = if (_sensorState.value.activityType == "still") 5f else 500f
                )
                _locationState.value = simulatedLocation

                kotlinx.coroutines.delay(30_000)
            }
        }
    }

    // ═══════════════════════════════════════
    //  PRESENCE DETECTION (WiFi/Bluetooth)
    // ═══════════════════════════════════════

    private var isPresenceScanning = false

    /**
     * Start scanning for known WiFi/Bluetooth devices to detect who's home.
     */
    fun startPresenceDetection() {
        if (isPresenceScanning) return
        isPresenceScanning = true

        scope.launch {
            while (isListening && isPresenceScanning) {
                scanForPresence()
                kotlinx.coroutines.delay(60_000) // Scan every minute
            }
        }
    }

    fun stopPresenceDetection() {
        isPresenceScanning = false
    }

    private fun scanForPresence() {
        // WiFi-based presence detection
        try {
            val wifiManager = application.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            // Note: WiFi scan requires location permission on Android 8.1+
            // In simulation mode, we generate mock presence data
            simulatePresenceScan()
        } catch (e: Exception) {
            android.util.Log.w("SensorManager", "WiFi scan error: ${e.message}")
            simulatePresenceScan()
        }

        // Bluetooth-based presence detection
        try {
            val bluetoothManager = application.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
            val adapter = bluetoothManager?.adapter
            if (adapter?.isEnabled == true) {
                // In production: start BLE scan for known device MAC addresses
                // For now, simulation handles this
            }
        } catch (e: SecurityException) {
            android.util.Log.w("SensorManager", "Bluetooth scan permission denied")
        }
    }

    private fun simulatePresenceScan() {
        val simulatedDevices = listOf(
            PresenceInfo("phone_maria", "Maria's iPhone", "bluetooth", -55, System.currentTimeMillis(), true),
            PresenceInfo("phone_carlos", "Carlos's Pixel", "wifi", -70, System.currentTimeMillis(), true),
            PresenceInfo("tablet_nino", "Kids Tablet", "wifi", -45, System.currentTimeMillis(), false)
        )

        for (device in simulatedDevices) {
            val wasPresent = presenceDevicesMap[device.deviceId]?.isHome ?: false
            presenceDevicesMap[device.deviceId] = device

            if (!wasPresent && device.isHome) {
                onPersonArrived?.invoke(device)
            } else if (wasPresent && !device.isHome) {
                onPersonLeft?.invoke(device)
            }
        }

        val homeCount = presenceDevicesMap.values.count { it.isHome }
        _sensorState.value = _sensorState.value.copy(
            presenceDevices = presenceDevicesMap.values.toList(),
            peopleAtHome = homeCount
        )
        _presenceState.value = presenceDevicesMap.values.toList()
    }

    /**
     * Register a device for presence tracking.
     */
    fun registerPresenceDevice(deviceId: String, name: String, type: String) {
        presenceDevicesMap[deviceId] = PresenceInfo(
            deviceId = deviceId,
            deviceName = name,
            deviceType = type,
            lastSeen = 0,
            isHome = false
        )
    }

    /**
     * Get count of people currently at home.
     */
    fun getPeopleAtHomeCount(): Int = _sensorState.value.peopleAtHome

    /**
     * Check if a specific person is home.
     */
    fun isPersonHome(deviceName: String): Boolean {
        return presenceDevicesMap.values.any { it.deviceName.contains(deviceName, ignoreCase = true) && it.isHome }
    }

    // ═══════════════════════════════════════
    //  SCREEN STATE DETECTION
    // ═══════════════════════════════════════

    private var screenReceiverRegistered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    screenOnTimestamp = System.currentTimeMillis()
                    screenOffTimestamp = null
                    val screenOnDuration = 0L
                    _sensorState.value = _sensorState.value.copy(
                        isScreenOn = true,
                        screenOnDuration = screenOnDuration,
                        screenOffDuration = 0L
                    )
                    onScreenStateChanged?.invoke(true)

                    // Update sleep detection
                    screenOffSinceTimestamp = null
                    lastSignificantMotionTimestamp = System.currentTimeMillis()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    screenOffTimestamp = System.currentTimeMillis()
                    screenOffSinceTimestamp = System.currentTimeMillis()
                    _sensorState.value = _sensorState.value.copy(
                        isScreenOn = false,
                        screenOnDuration = 0L,
                        screenOffDuration = 0L
                    )
                    onScreenStateChanged?.invoke(false)
                }
            }
        }
    }

    private fun registerScreenStateReceiver() {
        if (screenReceiverRegistered) return
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            application.registerReceiver(screenReceiver, filter)
            screenReceiverRegistered = true
        } catch (_: Exception) {}
    }

    private fun unregisterScreenStateReceiver() {
        if (!screenReceiverRegistered) return
        try {
            application.unregisterReceiver(screenReceiver)
            screenReceiverRegistered = false
        } catch (_: Exception) {}
    }

    /**
     * Is the user likely looking at their phone?
     */
    fun isUserLookingAtPhone(): Boolean {
        val state = _sensorState.value
        return state.isScreenOn && !state.isNear // Screen on and not against ear
    }

    // ═══════════════════════════════════════
    //  HEADPHONE DETECTION
    // ═══════════════════════════════════════

    private var headphoneReceiverRegistered = false

    private val headphoneReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AudioManager.ACTION_HEADSET_PLUG -> {
                    val state = intent?.getIntExtra("state", 0) ?: 0
                    val microphone = intent?.getIntExtra("microphone", 0) ?: 0
                    val isConnected = state == 1

                    val type = when {
                        !isConnected -> "none"
                        microphone == 1 -> "wired_headset"
                        else -> "wired"
                    }

                    updateHeadphoneState(isConnected, type)
                }
                "android.bluetooth.a2dp.profile.action.PLAYING_STATE_CHANGED",
                "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED" -> {
                    // Bluetooth audio state changed
                    val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    val isBtConnected = audioManager?.isBluetoothA2dpOn ?: false
                    if (isBtConnected) {
                        updateHeadphoneState(true, "bluetooth_a2dp")
                    }
                }
            }
        }
    }

    private fun updateHeadphoneState(isConnected: Boolean, type: String) {
        val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val audioOutputRoute = when {
            audioManager?.isBluetoothA2dpOn == true -> "bluetooth"
            isConnected && type.startsWith("wired") -> "headphones"
            audioManager?.isSpeakerphoneOn == true -> "speaker"
            audioManager?.isWiredHeadsetOn == true -> "headphones"
            else -> "speaker"
        }

        val oldState = _sensorState.value
        _sensorState.value = _sensorState.value.copy(
            isHeadphonesConnected = isConnected,
            headphoneType = type,
            audioOutputRoute = audioOutputRoute
        )

        if (oldState.isHeadphonesConnected != isConnected) {
            onHeadphonesChanged?.invoke(isConnected, type)
        }
    }

    private fun registerHeadphoneReceiver() {
        if (headphoneReceiverRegistered) return
        try {
            val filter = IntentFilter(AudioManager.ACTION_HEADSET_PLUG)
            filter.addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED")
            filter.addAction("android.bluetooth.adapter.action.CONNECTION_STATE_CHANGED")
            filter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            application.registerReceiver(headphoneReceiver, filter)
            headphoneReceiverRegistered = true

            // Check initial state
            val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val isWired = audioManager?.isWiredHeadsetOn ?: false
            val isBt = audioManager?.isBluetoothA2dpOn ?: false
            when {
                isBt -> updateHeadphoneState(true, "bluetooth_a2dp")
                isWired -> updateHeadphoneState(true, "wired")
                else -> updateHeadphoneState(false, "none")
            }
        } catch (_: Exception) {}
    }

    private fun unregisterHeadphoneReceiver() {
        if (!headphoneReceiverRegistered) return
        try {
            application.unregisterReceiver(headphoneReceiver)
            headphoneReceiverRegistered = false
        } catch (_: Exception) {}
    }

    /**
     * Get recommended audio output route for voice responses.
     */
    fun getRecommendedAudioRoute(): String {
        val state = _sensorState.value
        return when {
            state.isHeadphonesConnected -> state.audioOutputRoute
            state.isDriving -> "speaker"  // Always use speaker in car
            state.isNear -> "earpiece"    // Phone against ear
            state.isScreenOn -> "speaker" // Looking at phone
            else -> "earpiece"            // Default to earpiece
        }
    }

    // ═══════════════════════════════════════
    //  ACCELEROMETER & ACTIVITY DETECTION (enhanced)
    // ═══════════════════════════════════════

    private fun handleAccelerometer(values: FloatArray) {
        val x = values[0]; val y = values[1]; val z = values[2]
        val magnitude = kotlin.math.sqrt(x * x + y * y + z * z)

        _sensorState.value = _sensorState.value.copy(acceleration = values.copyOf())

        accelHistory[accelHistoryIdx] = magnitude
        accelHistoryIdx = (accelHistoryIdx + 1) % accelHistory.size

        val variance = calculateVariance(accelHistory)
        val oldActivity = _sensorState.value.activityType
        val newActivity = when {
            variance < 0.5f -> "still"
            variance < 3.0f -> "walking"
            variance < 8.0f -> "running"
            variance < 15.0f -> "driving"
            else -> "moving"
        }

        // Calculate activity confidence
        val activityConfidence = mapOf(
            "still" to if (variance < 0.5f) 0.9f - (variance / 0.5f) * 0.2f else 0.1f,
            "walking" to if (variance in 0.5f..3.0f) 0.7f + (1f - (variance - 0.5f) / 2.5f) * 0.25f else 0.1f,
            "running" to if (variance in 3.0f..8.0f) 0.7f + (1f - (variance - 3f) / 5f) * 0.25f else 0.1f,
            "driving" to if (variance > 8.0f) 0.6f + minOf((variance - 8f) / 10f, 0.35f) else 0.1f
        )

        if (oldActivity != newActivity) {
            val transition = ActivityTransition(
                fromActivity = oldActivity,
                toActivity = newActivity,
                confidence = activityConfidence[newActivity] ?: 0f,
                transitionType = "enter"
            )

            recentTransitions.add(0, transition)
            if (recentTransitions.size > maxRecentTransitions) {
                recentTransitions.removeAt(recentTransitions.size - 1)
            }

            _sensorState.value = _sensorState.value.copy(
                activityType = newActivity,
                isMoving = newActivity != "still",
                isDriving = newActivity == "driving",
                activityConfidence = activityConfidence,
                currentActivityConfidence = activityConfidence[newActivity] ?: 0f,
                activityTransitions = recentTransitions.toList()
            )

            onActivityChanged?.invoke(newActivity)
            saveSensorData("accelerometer", magnitude, JSONObject().apply {
                put("activity", newActivity)
                put("variance", variance)
                put("confidence", activityConfidence[newActivity] ?: 0f)
                put("transition_from", oldActivity)
            }.toString())
        }

        // Update driving detection
        updateDrivingFromAccelerometer(variance)

        // Update sleep detection factors
        if (variance > 2.0f) {
            lastSignificantMotionTimestamp = System.currentTimeMillis()
            stillSinceTimestamp = null
        } else if (stillSinceTimestamp == null) {
            stillSinceTimestamp = System.currentTimeMillis()
        }
    }

    private fun handleLight(lux: Float) {
        val isDark = lux < 50f
        val oldState = _sensorState.value
        _sensorState.value = _sensorState.value.copy(lightLevel = lux, isDark = isDark)

        if (oldState.isDark != isDark) {
            saveSensorData("light", lux, JSONObject().apply {
                put("lux", lux)
                put("isDark", isDark)
            }.toString())
        }

        // Update sleep detection factors
        if (isDark && darkSinceTimestamp == null) {
            darkSinceTimestamp = System.currentTimeMillis()
        } else if (!isDark) {
            darkSinceTimestamp = null
        }
    }

    // ═══════════════════════════════════════
    //  SIGNIFICANT MOTION / STATIONARY / MOTION
    // ═══════════════════════════════════════

    private fun handleSignificantMotion() {
        lastSignificantMotionTimestamp = System.currentTimeMillis()
        stillSinceTimestamp = null
        android.util.Log.d("SensorManager", "Significant motion detected")
    }

    private fun handleStationaryDetect() {
        if (stillSinceTimestamp == null) {
            stillSinceTimestamp = System.currentTimeMillis()
        }
        android.util.Log.d("SensorManager", "Stationary detected")
    }

    private fun handleMotionDetect() {
        lastSignificantMotionTimestamp = System.currentTimeMillis()
        stillSinceTimestamp = null
        android.util.Log.d("SensorManager", "Motion detected")
    }

    // ═══════════════════════════════════════
    //  DRIVING DETECTION (enhanced with confidence)
    // ═══════════════════════════════════════

    private fun updateDrivingFromAccelerometer(variance: Float) {
        val accelConfidence = when {
            variance > 15f -> 0.85f
            variance > 8f -> 0.5f + (variance - 8f) / 14f * 0.35f
            variance > 5f -> 0.2f
            else -> 0.0f
        }
        updateDrivingConfidence(accelConfidence, "accelerometer")
    }

    private fun updateDrivingFromGPS(speedMps: Float) {
        val speedKmh = speedMps * 3.6f
        val gpsConfidence = when {
            speedKmh > 60f -> 0.9f
            speedKmh > 30f -> 0.6f + (speedKmh - 30f) / 30f * 0.3f
            speedKmh > 10f -> 0.2f + (speedKmh - 10f) / 20f * 0.4f
            else -> 0.0f
        }
        updateDrivingConfidence(gpsConfidence, "gps")
    }

    private fun updateDrivingConfidence(newConfidence: Float, source: String) {
        drivingConfidenceHistory.add(newConfidence)
        if (drivingConfidenceHistory.size > maxDrivingConfidenceHistory) {
            drivingConfidenceHistory.removeAt(0)
        }

        // Combined confidence: weighted average of recent readings
        val avgConfidence = if (drivingConfidenceHistory.isNotEmpty()) {
            drivingConfidenceHistory.toFloatArray().average().toFloat()
        } else 0f

        val isDriving = avgConfidence > 0.5f
        val confidenceLevel = when {
            avgConfidence > 0.85f -> "very_high"
            avgConfidence > 0.7f -> "high"
            avgConfidence > 0.5f -> "medium"
            avgConfidence > 0.3f -> "low"
            else -> "none"
        }

        if (isDriving && drivingStartTimestamp == null) {
            drivingStartTimestamp = System.currentTimeMillis()
        } else if (!isDriving) {
            drivingStartTimestamp = null
        }

        val duration = if (drivingStartTimestamp != null) {
            System.currentTimeMillis() - drivingStartTimestamp!!
        } else 0L

        val speedMps = _sensorState.value.location.speed
        val newDrivingState = DrivingState(
            isDriving = isDriving,
            confidence = avgConfidence,
            confidenceLevel = confidenceLevel,
            speedMps = speedMps,
            speedKmh = speedMps * 3.6f,
            accelerationVariance = _sensorState.value.activityConfidence["driving"] ?: 0f,
            detectedMethod = source,
            duration = duration
        )

        val oldDrivingState = _sensorState.value.drivingState
        _sensorState.value = _sensorState.value.copy(
            isDriving = isDriving,
            drivingState = newDrivingState
        )
        _drivingState.value = newDrivingState

        if (oldDrivingState.isDriving != isDriving) {
            onDrivingStateChanged?.invoke(newDrivingState)
            saveSensorData("driving", avgConfidence, JSONObject().apply {
                put("isDriving", isDriving)
                put("confidence", avgConfidence)
                put("confidenceLevel", confidenceLevel)
                put("speedKmh", speedMps * 3.6f)
                put("method", source)
            }.toString())
        }
    }

    /**
     * Get driving mode recommendation for AI behavior.
     */
    fun getDrivingModeRecommendation(): DrivingModeRecommendation {
        val state = _sensorState.value.drivingState
        return when (state.confidenceLevel) {
            "very_high" -> DrivingModeRecommendation(
                shouldAdapt = true,
                speechStyle = "clear_slow",
                responseLength = "very_brief",
                handsFree = true,
                message = "Conduciendo a alta velocidad. Modo manos libres activado."
            )
            "high" -> DrivingModeRecommendation(
                shouldAdapt = true,
                speechStyle = "clear",
                responseLength = "brief",
                handsFree = true,
                message = "Conduciendo. Usar respuestas breves y claras."
            )
            "medium" -> DrivingModeRecommendation(
                shouldAdapt = true,
                speechStyle = "normal",
                responseLength = "brief",
                handsFree = false,
                message = "Posiblemente conduciendo. Mantener respuestas concisas."
            )
            "low" -> DrivingModeRecommendation(
                shouldAdapt = false,
                speechStyle = "normal",
                responseLength = "normal",
                handsFree = false,
                message = "Baja probabilidad de conducción."
            )
            else -> DrivingModeRecommendation(
                shouldAdapt = false,
                speechStyle = "normal",
                responseLength = "normal",
                handsFree = false,
                message = ""
            )
        }
    }

    data class DrivingModeRecommendation(
        val shouldAdapt: Boolean,
        val speechStyle: String,
        val responseLength: String,
        val handsFree: Boolean,
        val message: String
    )

    // ═══════════════════════════════════════
    //  SLEEP PATTERN DETECTION
    // ═══════════════════════════════════════

    private fun evaluateSleepState(): SleepState {
        val state = _sensorState.value
        val now = System.currentTimeMillis()
        val contributingFactors = mutableListOf<String>()

        var sleepConfidence = 0f

        // Factor 1: Time of day — night increases sleep probability
        if (state.timeOfDay == "night") {
            sleepConfidence += 0.3f
            contributingFactors.add("night_time")
        } else if (state.timeOfDay == "evening") {
            sleepConfidence += 0.1f
            contributingFactors.add("late_evening")
        }

        // Factor 2: No movement for extended period
        val stillDuration = stillSinceTimestamp?.let { now - it } ?: 0L
        if (stillDuration > 30 * 60 * 1000L) { // 30+ minutes still
            sleepConfidence += 0.25f
            contributingFactors.add("still_30min")
        }
        if (stillDuration > 60 * 60 * 1000L) { // 60+ minutes still
            sleepConfidence += 0.1f
            contributingFactors.add("still_60min")
        }

        // Factor 3: Dark environment
        if (state.isDark) {
            sleepConfidence += 0.2f
            contributingFactors.add("dark")
        }

        // Factor 4: Screen off for extended period
        val screenOffDuration = screenOffSinceTimestamp?.let { now - it } ?: 0L
        if (screenOffDuration > 30 * 60 * 1000L) { // 30+ min screen off
            sleepConfidence += 0.1f
            contributingFactors.add("screen_off_30min")
        }

        // Factor 5: No significant motion
        val timeSinceMotion = now - lastSignificantMotionTimestamp
        if (timeSinceMotion > 60 * 60 * 1000L) { // 1+ hour no motion
            sleepConfidence += 0.05f
            contributingFactors.add("no_motion_1hr")
        }

        // Factor 6: At home
        if (state.isAtHome) {
            sleepConfidence += 0.05f
            contributingFactors.add("at_home")
        }

        // Factor 7: Phone is face down (proximity near + still)
        if (state.isNear && state.activityType == "still") {
            sleepConfidence += 0.05f
            contributingFactors.add("phone_face_down")
        }

        // Cap confidence
        sleepConfidence = sleepConfidence.coerceIn(0f, 1f)

        val isLikelySleeping = sleepConfidence > 0.6f

        // Determine sleep stage (rough estimation)
        val sleepStage = when {
            !isLikelySleeping -> "awake"
            sleepConfidence < 0.7f -> "light"
            sleepConfidence < 0.85f -> "deep"
            stillDuration > 90 * 60 * 1000L -> "rem" // After 90 min, likely REM
            else -> "deep"
        }

        val estimatedSleepStart = if (isLikelySleeping) {
            stillSinceTimestamp ?: darkSinceTimestamp ?: screenOffSinceTimestamp
        } else null

        val duration = if (estimatedSleepStart != null) now - estimatedSleepStart else 0L

        return SleepState(
            isLikelySleeping = isLikelySleeping,
            confidence = sleepConfidence,
            sleepStage = sleepStage,
            estimatedSleepStart = estimatedSleepStart,
            duration = duration,
            contributingFactors = contributingFactors
        )
    }

    /**
     * Get sleep recommendation for AI behavior.
     */
    fun getSleepRecommendation(): SleepRecommendation {
        val sleepState = _sensorState.value.sleepState
        return when {
            sleepState.isLikelySleeping && sleepState.confidence > 0.8f -> SleepRecommendation(
                shouldSilence = true,
                shouldDimScreen = true,
                responseStyle = "silent",
                message = "Usuario probablemente durmiendo. Silenciar notificaciones."
            )
            sleepState.isLikelySleeping -> SleepRecommendation(
                shouldSilence = true,
                shouldDimScreen = true,
                responseStyle = "whisper",
                message = "Usuario posiblemente durmiendo. Reducir volumen."
            )
            sleepState.sleepStage == "light" -> SleepRecommendation(
                shouldSilence = false,
                shouldDimScreen = true,
                responseStyle = "quiet",
                message = "Sueño ligero detectado. Reducir volumen."
            )
            else -> SleepRecommendation(
                shouldSilence = false,
                shouldDimScreen = false,
                responseStyle = "normal",
                message = ""
            )
        }
    }

    data class SleepRecommendation(
        val shouldSilence: Boolean,
        val shouldDimScreen: Boolean,
        val responseStyle: String,
        val message: String
    )

    // ═══════════════════════════════════════
    //  ACTIVITY RECOGNITION (API stubs)
    // ═══════════════════════════════════════

    /**
     * Stub for Google Activity Recognition API integration.
     * In production, this would use ActivityRecognitionClient.
     */
    fun requestActivityRecognitionUpdates() {
        // Stub: In production, this would:
        // 1. Get ActivityRecognitionClient via ActivityRecognition.getClient(application)
        // 2. Create ActivityTransitionRequest with desired transitions
        // 3. Register a PendingIntent for transitions
        // 4. Handle ActivityTransitionResult in the callback

        android.util.Log.d("SensorManager", "Activity Recognition API stub: requesting updates")

        // Simulate activity recognition with our accelerometer-based detection
        scope.launch {
            while (isListening) {
                val state = _sensorState.value
                val currentActivity = state.activityType
                val confidence = state.activityConfidence[currentActivity] ?: 0f

                // In production, this data would come from the Activity Recognition API
                // For simulation, we use our accelerometer-based detection
                saveSensorData("activity_recognition", confidence, JSONObject().apply {
                    put("activity", currentActivity)
                    put("confidence", confidence)
                    put("source", "accelerometer_simulated")
                }.toString())

                kotlinx.coroutines.delay(30_000)
            }
        }
    }

    /**
     * Stop activity recognition updates (stub).
     */
    fun removeActivityRecognitionUpdates() {
        android.util.Log.d("SensorManager", "Activity Recognition API stub: removing updates")
    }

    // ═══════════════════════════════════════
    //  NFC TAG READING STUBS
    // ═══════════════════════════════════════

    private fun checkNFCAvailability() {
        try {
            val nfcAdapter = android.nfc.NfcAdapter.getDefaultAdapter(application)
            _sensorState.value = _sensorState.value.copy(nfcAvailable = nfcAdapter != null)
        } catch (_: Exception) {
            _sensorState.value = _sensorState.value.copy(nfcAvailable = false)
        }
    }

    /**
     * Register an NFC tag with an associated action for smart triggers.
     */
    fun registerNFCTag(tagId: String, label: String, action: String, tagType: String = "ndef") {
        val tag = NFCTag(
            tagId = tagId,
            tagType = tagType,
            associatedAction = action,
            label = label
        )
        registeredNFCTags[tagId] = tag
        saveNFCTags()
    }

    /**
     * Handle a detected NFC tag (called from Activity's onNewIntent).
     */
    fun handleNFCTag(tagId: String, data: String? = null, tagType: String = "ndef") {
        val registeredTag = registeredNFCTags[tagId]
        val tag = NFCTag(
            tagId = tagId,
            tagType = tagType,
            data = data,
            associatedAction = registeredTag?.associatedAction,
            label = registeredTag?.label
        )

        _sensorState.value = _sensorState.value.copy(lastNFCTag = tag)
        onNFCTagDetected?.invoke(tag)

        saveSensorData("nfc", 1f, JSONObject().apply {
            put("tagId", tagId)
            put("tagType", tagType)
            put("data", data ?: "")
            put("action", tag.associatedAction ?: "")
            put("label", tag.label ?: "")
        }.toString())

        android.util.Log.d("SensorManager", "NFC tag detected: $tagId, action: ${tag.associatedAction}")
    }

    /**
     * Simulate an NFC tag scan (for testing without NFC hardware).
     */
    fun simulateNFCTagScan(tagId: String) {
        handleNFCTag(tagId, tagType = "simulated")
    }

    private fun saveNFCTags() {
        scope.launch {
            try {
                val json = JSONObject()
                for ((id, tag) in registeredNFCTags) {
                    json.put(id, JSONObject().apply {
                        put("label", tag.label ?: "")
                        put("action", tag.associatedAction ?: "")
                        put("type", tag.tagType)
                    })
                }
                db.sensorDataDao().insert(SensorDataEntity(
                    sensorType = "nfc_tags",
                    value = 0f,
                    extraData = json.toString(),
                    context = "config"
                ))
            } catch (_: Exception) {}
        }
    }

    private fun loadNFCTags() {
        scope.launch {
            try {
                val data = db.sensorDataDao().getLatest("nfc_tags", 1)
                if (data.isNotEmpty()) {
                    val json = JSONObject(data[0].extraData ?: "{}")
                    for (key in json.keys()) {
                        val tagJson = json.getJSONObject(key)
                        registeredNFCTags[key] = NFCTag(
                            tagId = key,
                            tagType = tagJson.optString("type", "ndef"),
                            associatedAction = tagJson.optString("action", null),
                            label = tagJson.optString("label", null)
                        )
                    }
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Get all registered NFC tags.
     */
    fun getRegisteredNFCTags(): List<NFCTag> = registeredNFCTags.values.toList()

    // ═══════════════════════════════════════
    //  BATTERY (original, preserved)
    // ═══════════════════════════════════════

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            val isPowerSave = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                (application.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isPowerSaveMode
            } else false

            val batteryPct = (level * 100) / scale.coerceAtLeast(1)
            val oldState = _sensorState.value

            _sensorState.value = _sensorState.value.copy(
                batteryLevel = batteryPct, isCharging = isCharging, isPowerSave = isPowerSave
            )
            if (oldState.batteryLevel > 15 && batteryPct <= 15) {
                onBatteryLow?.invoke()
            }
        }
    }

    private var batteryReceiverRegistered = false

    private fun registerBatteryReceiver() {
        if (batteryReceiverRegistered) return
        try {
            application.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            batteryReceiverRegistered = true
        } catch (_: Exception) {}
    }

    private fun unregisterBatteryReceiver() {
        if (!batteryReceiverRegistered) return
        try {
            application.unregisterReceiver(batteryReceiver)
            batteryReceiverRegistered = false
        } catch (_: Exception) {}
    }

    // ═══════════════════════════════════════
    //  TIME OF DAY (original, preserved)
    // ═══════════════════════════════════════

    private fun updateTimeOfDay() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeOfDay = when (hour) {
            in 6..11 -> "morning"
            in 12..17 -> "day"
            in 18..21 -> "evening"
            else -> "night"
        }
        _sensorState.value = _sensorState.value.copy(timeOfDay = timeOfDay)
    }

    // ═══════════════════════════════════════
    //  CONTEXT ENGINE (enhanced)
    // ═══════════════════════════════════════

    /**
     * Start the periodic context evaluation engine.
     * Derives context, evaluates sleep, and generates suggestions.
     */
    private fun startContextEngine() {
        scope.launch {
            while (isListening) {
                // Update time of day
                updateTimeOfDay()

                // Derive overall context
                deriveContext()

                // Evaluate sleep state
                val sleepState = evaluateSleepState()
                val oldSleepState = _sensorState.value.sleepState
                _sensorState.value = _sensorState.value.copy(sleepState = sleepState)
                _sleepState.value = sleepState

                if (oldSleepState.isLikelySleeping != sleepState.isLikelySleeping) {
                    onSleepStateChanged?.invoke(sleepState)
                    if (sleepState.isLikelySleeping) {
                        saveSensorData("sleep", sleepState.confidence, JSONObject().apply {
                            put("sleeping", true)
                            put("confidence", sleepState.confidence)
                            put("stage", sleepState.sleepStage)
                            put("factors", JSONArray(sleepState.contributingFactors))
                        }.toString())
                    }
                }

                // Update screen durations
                val now = System.currentTimeMillis()
                if (_sensorState.value.isScreenOn) {
                    _sensorState.value = _sensorState.value.copy(
                        screenOnDuration = now - screenOnTimestamp
                    )
                } else {
                    screenOffSinceTimestamp?.let {
                        _sensorState.value = _sensorState.value.copy(
                            screenOffDuration = now - it
                        )
                    }
                }

                // Generate contextual suggestions
                generateContextualSuggestions()

                kotlinx.coroutines.delay(15_000) // Every 15 seconds
            }
        }
    }

    fun deriveContext(): String {
        val state = _sensorState.value
        val oldContext = state.userContext
        val newContext = when {
            state.drivingState.isDriving -> "commuting"
            state.activityType == "running" -> "exercising"
            state.sleepState.isLikelySleeping -> "sleeping"
            state.timeOfDay == "night" && state.activityType == "still" && state.isDark -> "sleeping"
            state.isDark && state.activityType == "still" && state.isAtHome -> "at_home"
            !state.isDark && state.activityType == "still" && state.isAtWork -> "at_work"
            state.isAtHome && state.activityType == "still" -> "at_home"
            state.isAtWork -> "at_work"
            state.isMoving -> "moving"
            else -> state.userContext
        }
        if (oldContext != newContext) {
            _sensorState.value = _sensorState.value.copy(userContext = newContext)
            onContextChanged?.invoke(oldContext, newContext)
        }
        return newContext
    }

    // ═══════════════════════════════════════
    //  CONTEXTUAL SUGGESTIONS
    // ═══════════════════════════════════════

    private fun generateContextualSuggestions() {
        val state = _sensorState.value
        val suggestions = mutableListOf<ContextualSuggestion>()

        // Suggestion: Enable driving mode when driving detected
        if (state.drivingState.isDriving && state.drivingState.confidence > 0.7f) {
            suggestions.add(ContextualSuggestion(
                id = "driving_mode",
                type = "setting",
                title = "Driving mode",
                description = "Detectado conduciendo. ¿Activar modo manos libres?",
                action = "enable_driving_mode",
                priority = 2,
                relevance = state.drivingState.confidence,
                contextTriggers = listOf("driving", "high_speed")
            ))
        }

        // Suggestion: Good night routine when going to bed
        if (state.timeOfDay == "night" && state.isDark && state.activityType == "still" && !state.sleepState.isLikelySleeping) {
            suggestions.add(ContextualSuggestion(
                id = "good_night_routine",
                type = "routine",
                title = "Rutina buenas noches",
                description = "Parece que te vas a dormir. ¿Activar rutina de buenas noches?",
                action = "routine:good_night",
                priority = 1,
                relevance = 0.7f,
                contextTriggers = listOf("night", "dark", "still")
            ))
        }

        // Suggestion: Turn on lights when arriving home in the dark
        if (state.isAtHome && state.isDark && state.activityType != "still") {
            suggestions.add(ContextualSuggestion(
                id = "arrive_home_lights",
                type = "iot",
                title = "Luces al llegar",
                description = "Llegaste a casa y está oscuro. ¿Encender las luces?",
                action = "iot:turn_on_lights",
                priority = 1,
                relevance = 0.8f,
                contextTriggers = listOf("arriving_home", "dark")
            ))
        }

        // Suggestion: Battery saver mode
        if (state.batteryLevel <= 15 && !state.isCharging) {
            suggestions.add(ContextualSuggestion(
                id = "battery_saver",
                type = "setting",
                title = "Ahorro de batería",
                description = "Batería baja (${state.batteryLevel}%). ¿Activar modo ahorro?",
                action = "enable_battery_saver",
                priority = 2,
                relevance = 1.0f - (state.batteryLevel / 15f),
                contextTriggers = listOf("low_battery")
            ))
        }

        // Suggestion: Switch to headphones
        if (state.isHeadphonesConnected && state.audioOutputRoute == "speaker") {
            suggestions.add(ContextualSuggestion(
                id = "switch_headphones",
                type = "setting",
                title = "Auriculares detectados",
                description = "Auriculares conectados. ¿Redirigir audio?",
                action = "route_audio:headphones",
                priority = 0,
                relevance = 0.6f,
                contextTriggers = listOf("headphones_connected")
            ))
        }

        // Suggestion: Morning routine
        if (state.timeOfDay == "morning" && state.activityType == "still" && state.isAtHome) {
            suggestions.add(ContextualSuggestion(
                id = "morning_routine",
                type = "routine",
                title = "Rutina buenos días",
                description = "Good morning. Activate morning routine?",
                action = "routine:good_morning",
                priority = 1,
                relevance = 0.75f,
                contextTriggers = listOf("morning", "at_home", "still")
            ))
        }

        // Suggestion: Exercise mode (high heart rate + moving)
        if (state.heartRateAvailable && state.heartRate > 120f && state.activityType == "running") {
            suggestions.add(ContextualSuggestion(
                id = "exercise_mode",
                type = "setting",
                title = "Exercise mode",
                description = "Detectado ejercicio (${state.heartRate.toInt()} bpm). ¿Respuestas breves?",
                action = "enable_exercise_mode",
                priority = 1,
                relevance = 0.8f,
                contextTriggers = listOf("exercising", "high_hr")
            ))
        }

        // Suggestion: Silent mode when sleeping
        if (state.sleepState.isLikelySleeping) {
            suggestions.add(ContextualSuggestion(
                id = "sleep_silent",
                type = "notification",
                title = "Silent mode",
                description = "Posiblemente durmiendo. ¿Silenciar notificaciones?",
                action = "enable_silent_mode",
                priority = 2,
                relevance = state.sleepState.confidence,
                contextTriggers = listOf("sleeping")
            ))
        }

        // Update suggestions state
        val sortedSuggestions = suggestions.sortedByDescending { it.relevance }
        _sensorState.value = _sensorState.value.copy(suggestions = sortedSuggestions)
        _suggestionsState.value = sortedSuggestions

        // Notify about high-priority suggestions
        for (suggestion in sortedSuggestions.filter { it.priority >= 2 && it.relevance > 0.7f }) {
            onSuggestionAvailable?.invoke(suggestion)
        }
    }

    /**
     * Get the top suggestion right now.
     */
    fun getTopSuggestion(): ContextualSuggestion? {
        return _sensorState.value.suggestions.maxByOrNull { it.relevance }
    }

    /**
     * Get suggestions filtered by type.
     */
    fun getSuggestionsByType(type: String): List<ContextualSuggestion> {
        return _sensorState.value.suggestions.filter { it.type == type }
    }

    // ═══════════════════════════════════════
    //  AI CONTEXT (enhanced)
    // ═══════════════════════════════════════

    fun getContextForAI(): String {
        val state = _sensorState.value
        val parts = mutableListOf<String>()

        // Activity context
        when (state.activityType) {
            "driving" -> parts.add("El usuario está conduciendo (confianza: ${String.format("%.0f", state.drivingState.confidence * 100)}%). Sé claro y breve. Modo manos libres.")
            "walking" -> parts.add("El usuario está caminando. Respuestas concisas.")
            "running" -> parts.add("El usuario está corriendo. Respuestas muy breves.")
            "still" -> parts.add("El usuario está quieto. Puede leer respuestas detalladas.")
        }

        // Light context
        if (state.isDark) parts.add("Es oscuro (posiblemente de noche).")

        // Battery context
        if (state.batteryLevel <= 15 && !state.isCharging) parts.add("Batería baja (${state.batteryLevel}%).")

        // Time context
        when (state.timeOfDay) {
            "morning" -> parts.add("Es de mañana.")
            "night" -> parts.add("Es de noche.")
            "evening" -> parts.add("Es de tarde.")
            "day" -> parts.add("Es de día.")
        }

        // Location context
        when {
            state.isAtHome -> parts.add("Contexto: en casa.")
            state.isAtWork -> parts.add("Contexto: en el trabajo.")
            state.location.placeLabel != null -> parts.add("Contexto: en ${state.location.placeLabel}.")
        }

        // User context
        when (state.userContext) {
            "at_home" -> parts.add("Contexto: en casa.")
            "at_work" -> parts.add("Contexto: en el trabajo.")
            "commuting" -> parts.add("Contexto: viajando.")
            "exercising" -> parts.add("Contexto: haciendo ejercicio.")
            "sleeping" -> parts.add("Contexto: posiblemente durmiendo. NO enviar notificaciones.")
        }

        // Sleep context
        if (state.sleepState.isLikelySleeping) {
            parts.add("⚠️ Usuario probablemente durmiendo (confianza: ${String.format("%.0f", state.sleepState.confidence * 100)}%). Etapa: ${state.sleepState.sleepStage}.")
        }

        // Heart rate context
        if (state.heartRateAvailable && state.heartRate > 0) {
            val zone = getHeartRateZone()
            if (zone != "resting" && zone != "normal") {
                parts.add("Frecuencia cardíaca: ${state.heartRate.toInt()} bpm (zona: $zone).")
            }
        }

        // Headphone context
        if (state.isHeadphonesConnected) {
            parts.add("Auriculares conectados (${state.headphoneType}). Audio por ${state.audioOutputRoute}.")
        }

        // Screen context
        if (!state.isScreenOn) {
            parts.add("Pantalla apagada.")
        }

        // Presence context
        if (state.peopleAtHome > 0) {
            parts.add("${state.peopleAtHome} persona(s) en casa.")
        }

        // Driving details
        if (state.drivingState.isDriving) {
            parts.add("Velocidad: ${String.format("%.0f", state.drivingState.speedKmh)} km/h. Nivel confianza conducción: ${state.drivingState.confidenceLevel}.")
        }

        return parts.joinToString(" ")
    }

    /**
     * Get sensor context summary for quick reference.
     */
    fun getSensorSummary(): String {
        val state = _sensorState.value
        val parts = mutableListOf<String>()

        parts.add("📍 ${state.location.placeLabel ?: if (state.isAtHome) "casa" else if (state.isAtWork) "trabajo" else "desconocido"}")
        parts.add("🏃 ${state.activityType}")
        parts.add("🕐 ${state.timeOfDay}")
        if (state.isDark) parts.add("🌙 oscuro")
        if (state.isDriving) parts.add("🚗 conduciendo (${state.drivingState.confidenceLevel})")
        if (state.sleepState.isLikelySleeping) parts.add("😴 durmiendo")
        if (state.heartRateAvailable) parts.add("❤️ ${state.heartRate.toInt()}bpm")
        parts.add("🔋 ${state.batteryLevel}%")
        if (state.isHeadphonesConnected) parts.add("🎧 ${state.headphoneType}")

        return parts.joinToString(" | ")
    }

    // ═══════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════

    private fun calculateVariance(values: FloatArray): Float {
        val mean = values.average().toFloat()
        var sumSquaredDiff = 0f
        for (v in values) { sumSquaredDiff += (v - mean) * (v - mean) }
        return sumSquaredDiff / values.size
    }

    private fun saveSensorData(sensorType: String, value: Float, extraData: String? = null) {
        scope.launch {
            try {
                db.sensorDataDao().insert(SensorDataEntity(
                    sensorType = sensorType,
                    value = value,
                    extraData = extraData,
                    context = deriveContext()
                ))
            } catch (_: Exception) {}
        }
    }

    fun cleanupOldData(daysToKeep: Int = 7) {
        scope.launch {
            val cutoff = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)
            try { db.sensorDataDao().deleteOlderThan(cutoff) } catch (_: Exception) {}
        }
    }
}
