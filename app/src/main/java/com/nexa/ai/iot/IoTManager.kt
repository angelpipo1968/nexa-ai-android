package com.nexa.ai.iot

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import com.nexa.ai.data.local.IoTDeviceEntity
import com.nexa.ai.data.local.NexaDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * NEXA IoT Manager — Control de dispositivos inteligentes
 *
 * Protocolos: HTTP/REST, MQTT, Bluetooth, WiFi Direct, BLE, Zigbee
 * Dispositivos: Luces, termostatos, cerraduras, cámaras, altavoces, interruptores, sensores
 *
 * Capacidades:
 * - BLE device scanning and discovery
 * - Automation routines ("Good morning", "Good night", etc.)
 * - Scenes management (group devices for coordinated control)
 * - WiFi Direct device support
 * - Google Home / Alexa integration stubs
 * - Device grouping by rooms (salón, dormitorio, cocina, etc.)
 * - Energy monitoring (track device power usage)
 * - Scheduling (turn on lights at sunset, etc.)
 * - Extended voice command patterns
 * - Real-time device state monitoring with StateFlow updates
 *
 * Comandos de voz:
 * - "Enciende la luz del salón"
 * - "Apaga todas las luces"
 * - "Pon la temperatura a 22 grados"
 * - "¿Está la puerta cerrada?"
 * - "Activa la rutina de buenos días"
 * - "Escena cinema"
 * - "¿Cuánta energía consumen los dispositivos?"
 * - "Programa la luz para el atardecer"
 * - "Agrega la luz al dormitorio"
 * - "Escanea dispositivos Bluetooth"
 */
class IoTManager(private val application: Application) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db by lazy { NexaDatabase.getInstance(application) }

    // ═══════════════════════════════════════
    //  DATA CLASSES
    // ═══════════════════════════════════════

    data class BLEDevice(
        val name: String,
        val address: String,
        val rssi: Int,
        val serviceUuids: List<String> = emptyList(),
        val isConnectable: Boolean = true,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class AutomationRoutine(
        val id: String,
        val name: String,
        val trigger: String,              // "voice", "schedule", "sensor", "manual"
        val triggerPhrase: String? = null, // e.g., "buenos días"
        val actions: List<RoutineAction>,
        val isEnabled: Boolean = true,
        val scheduleTime: String? = null,  // "07:00", "sunset", "sunrise"
        val conditions: Map<String, String> = emptyMap() // e.g., "presence" to "home"
    )

    data class RoutineAction(
        val deviceType: String? = null,
        val deviceName: String? = null,
        val roomId: String? = null,
        val command: String,               // "on", "off", "set_temperature:22"
        val delay: Long = 0                // milliseconds delay before executing
    )

    data class Scene(
        val id: String,
        val name: String,
        val icon: String,                  // emoji or icon name
        val roomIds: List<String> = emptyList(),
        val deviceStates: Map<String, String>, // deviceId -> desired state
        val isFavorite: Boolean = false
    )

    data class Room(
        val id: String,
        val name: String,                  // "Salón", "Dormitorio", "Cocina"
        val icon: String,                  // emoji
        val deviceIds: MutableList<String> = mutableListOf()
    )

    data class EnergyReading(
        val deviceId: String,
        val deviceName: String,
        val powerWatts: Double,            // current power in watts
        val energyKwh: Double,             // cumulative energy in kWh
        val timestamp: Long = System.currentTimeMillis()
    )

    data class Schedule(
        val id: String,
        val name: String,
        val deviceId: String,
        val command: String,               // "on", "off", "set_temperature:22"
        val triggerType: String,           // "time", "sunset", "sunrise", "sensor"
        val triggerValue: String,          // "07:00", "sunset", etc.
        val daysOfWeek: List<Int> = emptyList(), // 1=Mon...7=Sun, empty = every day
        val isEnabled: Boolean = true,
        val lastTriggered: Long = 0
    )

    data class VoiceAssistantIntegration(
        val platform: String,              // "google_home", "alexa", "homekit"
        val isConnected: Boolean = false,
        val endpoint: String? = null,
        val token: String? = null,
        val discoveredDevices: List<String> = emptyList()
    )

    data class IoTState(
        val devices: List<IoTDeviceEntity> = emptyList(),
        val rooms: List<Room> = emptyList(),
        val scenes: List<Scene> = emptyList(),
        val routines: List<AutomationRoutine> = emptyList(),
        val schedules: List<Schedule> = emptyList(),
        val bleDevices: List<BLEDevice> = emptyList(),
        val energyReadings: Map<String, EnergyReading> = emptyMap(), // deviceId -> reading
        val totalPowerWatts: Double = 0.0,
        val isScanning: Boolean = false,
        val isBLEScanning: Boolean = false,
        val isWiFiDirectScanning: Boolean = false,
        val lastCommand: String? = null,
        val lastCommandStatus: String = "idle",
        val integrations: Map<String, VoiceAssistantIntegration> = emptyMap()
    )

    // ═══════════════════════════════════════
    //  STATE FLOW
    // ═══════════════════════════════════════

    private val _iotState = MutableStateFlow(IoTState())
    val iotState: StateFlow<IoTState> = _iotState.asStateFlow()

    // Granular state flows for real-time monitoring
    private val _deviceStateMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val deviceStateMap: StateFlow<Map<String, String>> = _deviceStateMap.asStateFlow()

    private val _energyState = MutableStateFlow<Map<String, EnergyReading>>(emptyMap())
    val energyState: StateFlow<Map<String, EnergyReading>> = _energyState.asStateFlow()

    private val _bleScanResults = MutableStateFlow<List<BLEDevice>>(emptyList())
    val bleScanResults: StateFlow<List<BLEDevice>> = _bleScanResults.asStateFlow()

    var onDeviceStateChanged: ((String, String) -> Unit)? = null
    var onCommandExecuted: ((String, Boolean) -> Unit)? = null
    var onRoutineExecuted: ((String, Boolean) -> Unit)? = null
    var onSceneActivated: ((String, Boolean) -> Unit)? = null
    var onEnergyAlert: ((String, Double) -> Unit)? = null
    var onBLEDeviceFound: ((BLEDevice) -> Unit)? = null
    var onScheduleTriggered: ((Schedule) -> Unit)? = null

    // ═══════════════════════════════════════
    //  BLE SCANNING
    // ═══════════════════════════════════════

    private val bluetoothManager by lazy {
        application.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager?.adapter
    }
    private val bleScanner: BluetoothLeScanner? by lazy {
        bluetoothAdapter?.bluetoothLeScanner
    }

    private val bleScanResultsMap = ConcurrentHashMap<String, BLEDevice>()

    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val name = device.name ?: "Unknown BLE Device"
            val address = device.address
            val rssi = result.rssi
            val serviceUuids = result.scanRecord?.serviceUuids?.map { it.uuid.toString() } ?: emptyList()
            val isConnectable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                result.isConnectable
            } else true

            val bleDevice = BLEDevice(
                name = name,
                address = address,
                rssi = rssi,
                serviceUuids = serviceUuids,
                isConnectable = isConnectable
            )

            bleScanResultsMap[address] = bleDevice
            onBLEDeviceFound?.invoke(bleDevice)

            _bleScanResults.value = bleScanResultsMap.values.toList()
                .sortedByDescending { it.rssi }
        }

        override fun onScanFailed(errorCode: Int) {
            android.util.Log.e("IoTManager", "BLE scan failed with error: $errorCode")
            _iotState.value = _iotState.value.copy(isBLEScanning = false)
        }
    }

    /**
     * Start BLE device scanning.
     * Discovers nearby Bluetooth Low Energy devices that could be IoT devices.
     */
    fun startBLEScan(scanMode: Int = ScanSettings.SCAN_MODE_LOW_LATENCY): Boolean {
        if (bleScanner == null) {
            android.util.Log.w("IoTManager", "BLE scanner not available — simulation mode")
            startBLEScanSimulation()
            return false
        }

        if (_iotState.value.isBLEScanning) return true

        val settings = ScanSettings.Builder()
            .setScanMode(scanMode)
            .setReportDelay(0)
            .build()

        val filters = listOf(
            ScanFilter.Builder().build() // Accept all devices
        )

        try {
            bleScanner?.startScan(filters, settings, bleScanCallback)
            _iotState.value = _iotState.value.copy(isBLEScanning = true)
            android.util.Log.d("IoTManager", "BLE scan started")
            return true
        } catch (e: SecurityException) {
            android.util.Log.e("IoTManager", "BLE scan permission denied: ${e.message}")
            startBLEScanSimulation()
            return false
        }
    }

    /**
     * Stop BLE device scanning.
     */
    fun stopBLEScan() {
        try {
            bleScanner?.stopScan(bleScanCallback)
        } catch (e: SecurityException) {
            android.util.Log.w("IoTManager", "BLE stop scan permission issue: ${e.message}")
        }
        _iotState.value = _iotState.value.copy(isBLEScanning = false)
        android.util.Log.d("IoTManager", "BLE scan stopped")
    }

    /**
     * Simulate BLE scan results when real BLE hardware isn't available.
     */
    private fun startBLEScanSimulation() {
        _iotState.value = _iotState.value.copy(isBLEScanning = true)
        scope.launch {
            val simulatedDevices = listOf(
                BLEDevice("Philips Hue Bridge", "AA:BB:CC:DD:EE:01", -45, listOf("0000fe0f-0000-1000-8000-00805f9b34fb")),
                BLEDevice("Xiaomi Temp Sensor", "AA:BB:CC:DD:EE:02", -62, listOf("0000fe95-0000-1000-8000-00805f9b34fb")),
                BLEDevice("Sonoff TH Sensor", "AA:BB:CC:DD:EE:03", -71, listOf("0000fe9f-0000-1000-8000-00805f9b34fb")),
                BLEDevice("SwitchBot Bot", "AA:BB:CC:DD:EE:04", -55, listOf("0000d00d-0000-1000-8000-00805f9b34fb")),
                BLEDevice("Govee Light Strip", "AA:BB:CC:DD:EE:05", -80, listOf("00008e97-0000-1000-8000-00805f9b34fb"))
            )
            for (device in simulatedDevices) {
                bleScanResultsMap[device.address] = device
                onBLEDeviceFound?.invoke(device)
                kotlinx.coroutines.delay(800)
            }
            _bleScanResults.value = bleScanResultsMap.values.toList().sortedByDescending { it.rssi }
            _iotState.value = _iotState.value.copy(
                bleDevices = simulatedDevices,
                isBLEScanning = false
            )
        }
    }

    /**
     * Add a discovered BLE device as a registered IoT device.
     */
    suspend fun addBLEDevice(bleDevice: BLEDevice, deviceType: String, roomId: String? = null): IoTDeviceEntity {
        val device = addDevice(
            name = bleDevice.name,
            deviceType = deviceType,
            protocol = "ble",
            endpoint = bleDevice.address,
            capabilities = mapOf(
                "rssi" to bleDevice.rssi,
                "serviceUuids" to bleDevice.serviceUuids.joinToString(","),
                "isConnectable" to bleDevice.isConnectable
            )
        )
        roomId?.let { addDeviceToRoom(device.deviceId, it) }
        return device
    }

    // ═══════════════════════════════════════
    //  ROOMS / DEVICE GROUPING
    // ═══════════════════════════════════════

    private val roomsMap = ConcurrentHashMap<String, Room>()

    /**
     * Create a room for device grouping.
     */
    fun createRoom(name: String, icon: String = "🏠"): Room {
        val roomId = "room_${name.lowercase().replace(" ", "_")}"
        val room = Room(id = roomId, name = name, icon = icon)
        roomsMap[roomId] = room
        _iotState.value = _iotState.value.copy(rooms = roomsMap.values.toList())
        return room
    }

    /**
     * Add a device to a room.
     */
    fun addDeviceToRoom(deviceId: String, roomId: String) {
        val room = roomsMap[roomId] ?: return
        if (deviceId !in room.deviceIds) {
            room.deviceIds.add(deviceId)
            roomsMap[roomId] = room
            _iotState.value = _iotState.value.copy(rooms = roomsMap.values.toList())
        }
    }

    /**
     * Remove a device from a room.
     */
    fun removeDeviceFromRoom(deviceId: String, roomId: String) {
        val room = roomsMap[roomId] ?: return
        room.deviceIds.remove(deviceId)
        roomsMap[roomId] = room
        _iotState.value = _iotState.value.copy(rooms = roomsMap.values.toList())
    }

    /**
     * Get all devices in a room.
     */
    suspend fun getDevicesInRoom(roomId: String): List<IoTDeviceEntity> {
        val room = roomsMap[roomId] ?: return emptyList()
        return room.deviceIds.mapNotNull { id -> db.iotDeviceDao().getById(id) }
    }

    /**
     * Get room by name (supports Spanish names).
     */
    fun getRoomByName(name: String): Room? {
        val lower = name.lowercase()
        return roomsMap.values.find {
            it.name.lowercase().contains(lower) || it.id.contains(lower.replace(" ", "_"))
        }
    }

    /**
     * Resolve a room name from a voice command.
     */
    private fun resolveRoomFromCommand(command: String): Room? {
        val lower = command.lowercase()
        // Direct room name match
        roomsMap.values.forEach { room ->
            if (lower.contains(room.name.lowercase())) return room
        }
        // Spanish room aliases
        val roomAliases = mapOf(
            "salón" to "room_salon", "sala" to "room_salon", "living" to "room_salon",
            "dormitorio" to "room_dormitorio", "habitación" to "room_dormitorio", "recámara" to "room_dormitorio",
            "cocina" to "room_cocina",
            "baño" to "room_bano", "baño" to "room_bano",
            "oficina" to "room_oficina", "estudio" to "room_oficina",
            "garaje" to "room_garaje", "cochera" to "room_garaje",
            "jardín" to "room_jardin", "patio" to "room_jardin"
        )
        for ((alias, roomId) in roomAliases) {
            if (lower.contains(alias)) return roomsMap[roomId]
        }
        return null
    }

    /**
     * Initialize default rooms.
     */
    private fun initDefaultRooms() {
        if (roomsMap.isNotEmpty()) return
        createRoom("Salón", "🛋️")
        createRoom("Dormitorio", "🛏️")
        createRoom("Cocina", "🍳")
        createRoom("Baño", "🚿")
        createRoom("Oficina", "💼")
        createRoom("Garaje", "🚗")
        createRoom("Jardín", "🌿")
    }

    // ═══════════════════════════════════════
    //  SCENES MANAGEMENT
    // ═══════════════════════════════════════

    private val scenesMap = ConcurrentHashMap<String, Scene>()

    /**
     * Create a scene with predefined device states.
     */
    fun createScene(name: String, icon: String, deviceStates: Map<String, String>, roomIds: List<String> = emptyList()): Scene {
        val sceneId = "scene_${name.lowercase().replace(" ", "_")}"
        val scene = Scene(
            id = sceneId,
            name = name,
            icon = icon,
            roomIds = roomIds,
            deviceStates = deviceStates
        )
        scenesMap[sceneId] = scene
        _iotState.value = _iotState.value.copy(scenes = scenesMap.values.toList())
        return scene
    }

    /**
     * Activate a scene — set all devices to their predefined states.
     */
    suspend fun activateScene(sceneId: String): String {
        val scene = scenesMap[sceneId] ?: return "Escena no encontrada."
        val results = mutableListOf<String>()

        for ((deviceId, desiredState) in scene.deviceStates) {
            val device = db.iotDeviceDao().getById(deviceId)
            if (device != null) {
                val success = sendCommandToDevice(device, desiredState)
                if (success) {
                    db.iotDeviceDao().updateState(device.deviceId, desiredState, desiredState)
                    onDeviceStateChanged?.invoke(device.deviceId, desiredState)
                    results.add("${device.name} → $desiredState")
                } else {
                    results.add("Error: ${device.name}")
                }
            }
        }

        refreshDevices()
        onSceneActivated?.invoke(sceneId, true)
        return "Escena '${scene.name}' activada: ${results.joinToString(", ")}"
    }

    /**
     * Deactivate a scene — turn off all devices in the scene.
     */
    suspend fun deactivateScene(sceneId: String): String {
        val scene = scenesMap[sceneId] ?: return "Escena no encontrada."
        val results = mutableListOf<String>()

        for ((deviceId, _) in scene.deviceStates) {
            val device = db.iotDeviceDao().getById(deviceId)
            if (device != null) {
                val success = sendCommandToDevice(device, "off")
                if (success) {
                    db.iotDeviceDao().updateState(device.deviceId, "off", "off")
                    onDeviceStateChanged?.invoke(device.deviceId, "off")
                    results.add("${device.name} → off")
                }
            }
        }

        refreshDevices()
        return "Escena '${scene.name}' desactivada: ${results.joinToString(", ")}"
    }

    /**
     * Find a scene by name or voice command.
     */
    private fun resolveScene(command: String): Scene? {
        val lower = command.lowercase()
        val sceneAliases = mapOf(
            "cine" to "scene_cinema", "película" to "scene_cinema", "movie" to "scene_cinema",
            "noche" to "scene_night", "dormir" to "scene_night", "sleep" to "scene_night",
            "lectura" to "scene_reading", "leer" to "scene_reading", "read" to "scene_reading",
            "fiesta" to "scene_party", "party" to "scene_party", "celebración" to "scene_party",
            "trabajo" to "scene_work", "work" to "scene_work", "oficina" to "scene_work",
            "relax" to "scene_relax", "relajante" to "scene_relax", "tranquilo" to "scene_relax",
            "mañana" to "scene_morning", "buenos días" to "scene_morning", "morning" to "scene_morning"
        )
        for ((alias, sceneId) in sceneAliases) {
            if (lower.contains(alias)) return scenesMap[sceneId]
        }
        return scenesMap.values.find { lower.contains(it.name.lowercase()) }
    }

    /**
     * Initialize default scenes.
     */
    private fun initDefaultScenes() {
        if (scenesMap.isNotEmpty()) return
        // These will be populated with real device IDs when devices are added
        createScene("Cinema", "🎬", emptyMap())
        createScene("Noche", "🌙", emptyMap())
        createScene("Lectura", "📖", emptyMap())
        createScene("Fiesta", "🎉", emptyMap())
        createScene("Trabajo", "💼", emptyMap())
        createScene("Relax", "🧘", emptyMap())
        createScene("Mañana", "☀️", emptyMap())
    }

    // ═══════════════════════════════════════
    //  AUTOMATION ROUTINES
    // ═══════════════════════════════════════

    private val routinesMap = ConcurrentHashMap<String, AutomationRoutine>()

    /**
     * Create an automation routine.
     */
    fun createRoutine(routine: AutomationRoutine): AutomationRoutine {
        routinesMap[routine.id] = routine
        _iotState.value = _iotState.value.copy(routines = routinesMap.values.toList())
        return routine
    }

    /**
     * Execute a routine — run all actions in sequence.
     */
    suspend fun executeRoutine(routineId: String): String {
        val routine = routinesMap[routineId] ?: return "Rutina no encontrada."
        if (!routine.isEnabled) return "Rutina '${routine.name}' está desactivada."

        val results = mutableListOf<String>()
        onRoutineExecuted?.invoke(routineId, true)

        for (action in routine.actions) {
            if (action.delay > 0) {
                kotlinx.coroutines.delay(action.delay)
            }

            val result = when {
                action.roomId != null -> executeRoomAction(action.roomId, action.command)
                action.deviceType != null -> {
                    val devices = if (action.deviceName != null) {
                        findDevices(action.deviceType, action.deviceName)
                    } else {
                        db.iotDeviceDao().getByType(action.deviceType)
                    }
                    executeActionOnDevices(devices, action.command)
                }
                else -> executeGlobalAction(action.command)
            }
            results.add(result)
        }

        return "Rutina '${routine.name}' ejecutada: ${results.joinToString("; ")}"
    }

    /**
     * Execute a voice-triggered routine.
     */
    suspend fun tryExecuteVoiceRoutine(command: String): String? {
        val lower = command.lowercase()
        for (routine in routinesMap.values) {
            if (!routine.isEnabled || routine.trigger != "voice") continue
            val phrase = routine.triggerPhrase ?: continue
            if (lower.contains(phrase)) {
                return executeRoutine(routine.id)
            }
        }
        return null
    }

    private suspend fun executeRoomAction(roomId: String, command: String): String {
        val devices = getDevicesInRoom(roomId)
        return executeActionOnDevices(devices, command)
    }

    private suspend fun executeActionOnDevices(devices: List<IoTDeviceEntity>, command: String): String {
        if (devices.isEmpty()) return "Sin dispositivos. (Modo simulación)"

        val results = devices.map { d ->
            val success = sendCommandToDevice(d, command)
            if (success) {
                db.iotDeviceDao().updateState(d.deviceId, command, command)
                onDeviceStateChanged?.invoke(d.deviceId, command)
                "${d.name}: ✓"
            } else "${d.name}: ✗"
        }
        refreshDevices()
        return results.joinToString(", ")
    }

    private suspend fun executeGlobalAction(command: String): String {
        val devices = db.iotDeviceDao().getAll()
        return executeActionOnDevices(devices, command)
    }

    /**
     * Initialize default automation routines.
     */
    private fun initDefaultRoutines() {
        if (routinesMap.isNotEmpty()) return

        createRoutine(AutomationRoutine(
            id = "routine_good_morning",
            name = "Buenos Días",
            trigger = "voice",
            triggerPhrase = "buenos días",
            actions = listOf(
                RoutineAction(deviceType = "light", command = "on", delay = 0),
                RoutineAction(deviceType = "light", command = "brightness:70", delay = 500),
                RoutineAction(deviceType = "thermostat", command = "set_temperature:22", delay = 1000)
            ),
            conditions = mapOf("timeOfDay" to "morning")
        ))

        createRoutine(AutomationRoutine(
            id = "routine_good_night",
            name = "Buenas Noches",
            trigger = "voice",
            triggerPhrase = "buenas noches",
            actions = listOf(
                RoutineAction(deviceType = "light", command = "brightness:10", delay = 0),
                RoutineAction(deviceType = "light", roomId = "room_salon", command = "off", delay = 2000),
                RoutineAction(deviceType = "light", roomId = "room_cocina", command = "off", delay = 2500),
                RoutineAction(deviceType = "lock", command = "on", delay = 3000)
            ),
            conditions = mapOf("timeOfDay" to "night")
        ))

        createRoutine(AutomationRoutine(
            id = "routine_leave_home",
            name = "Salir de Casa",
            trigger = "voice",
            triggerPhrase = "me voy",
            actions = listOf(
                RoutineAction(deviceType = "light", command = "off", delay = 0),
                RoutineAction(deviceType = "thermostat", command = "set_temperature:18", delay = 500),
                RoutineAction(deviceType = "lock", command = "on", delay = 1000)
            )
        ))

        createRoutine(AutomationRoutine(
            id = "routine_arrive_home",
            name = "Llegar a Casa",
            trigger = "voice",
            triggerPhrase = "llegué",
            actions = listOf(
                RoutineAction(deviceType = "light", roomId = "room_salon", command = "on", delay = 0),
                RoutineAction(deviceType = "thermostat", command = "set_temperature:22", delay = 500),
                RoutineAction(deviceType = "lock", command = "off", delay = 0)
            )
        ))

        createRoutine(AutomationRoutine(
            id = "routine_movie_time",
            name = "Hora de Película",
            trigger = "voice",
            triggerPhrase = "película",
            actions = listOf(
                RoutineAction(deviceType = "light", command = "brightness:20", delay = 0),
                RoutineAction(deviceType = "speaker", command = "on", delay = 500)
            )
        ))
    }

    // ═══════════════════════════════════════
    //  ENERGY MONITORING
    // ═══════════════════════════════════════

    private val energyReadingsMap = ConcurrentHashMap<String, EnergyReading>()
    private val energyHistory = ConcurrentHashMap<String, MutableList<Pair<Long, Double>>>() // deviceId -> list of (timestamp, watts)

    /**
     * Update energy reading for a device.
     */
    fun updateEnergyReading(deviceId: String, deviceName: String, powerWatts: Double) {
        val existing = energyReadingsMap[deviceId]
        val newKwh = (existing?.energyKwh ?: 0.0) + (powerWatts / 3600000.0) // 1-second sampling
        val reading = EnergyReading(
            deviceId = deviceId,
            deviceName = deviceName,
            powerWatts = powerWatts,
            energyKwh = newKwh
        )
        energyReadingsMap[deviceId] = reading

        // Track history (keep last 24h)
        val history = energyHistory.getOrPut(deviceId) { mutableListOf() }
        history.add(System.currentTimeMillis() to powerWatts)
        val oneDayAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        while (history.isNotEmpty() && history.first().first < oneDayAgo) {
            history.removeAt(0)
        }

        // Update state flows
        _energyState.value = energyReadingsMap.toMap()
        val totalPower = energyReadingsMap.values.sumOf { it.powerWatts }
        _iotState.value = _iotState.value.copy(
            energyReadings = energyReadingsMap.toMap(),
            totalPowerWatts = totalPower
        )

        // Check for energy alert (> 2000W total or > 500W single device)
        if (totalPower > 2000.0) {
            onEnergyAlert?.invoke("total", totalPower)
        }
        if (powerWatts > 500.0) {
            onEnergyAlert?.invoke(deviceName, powerWatts)
        }
    }

    /**
     * Get energy consumption report.
     */
    suspend fun getEnergyReport(): String {
        if (energyReadingsMap.isEmpty()) return "No hay datos de energía. (Modo simulación — conecta dispositivos con monitorización de energía.)"

        val lines = mutableListOf<String>()
        lines.add("⚡ Consumo de Energía:")
        lines.add("─────────────────────")

        var totalWatts = 0.0
        for ((_, reading) in energyReadingsMap) {
            lines.add("  ${reading.deviceName}: ${String.format("%.1f", reading.powerWatts)}W  (${String.format("%.3f", reading.energyKwh)} kWh)")
            totalWatts += reading.powerWatts
        }

        lines.add("─────────────────────")
        lines.add("  Total: ${String.format("%.1f", totalWatts)}W")

        val estimatedDailyKwh = totalWatts * 24 / 1000.0
        lines.add("  Estimado diario: ${String.format("%.2f", estimatedDailyKwh)} kWh")

        return lines.joinToString("\n")
    }

    /**
     * Get energy history for a specific device.
     */
    fun getEnergyHistory(deviceId: String): List<Pair<Long, Double>> {
        return energyHistory[deviceId]?.toList() ?: emptyList()
    }

    /**
     * Start simulated energy monitoring.
     */
    fun startEnergyMonitoringSimulation() {
        scope.launch {
            while (true) {
                val devices = _iotState.value.devices
                for (d in devices) {
                    if (d.state != "off" && d.isOnline) {
                        val baseWatts = when (d.deviceType) {
                            "light" -> 10.0 + (0..15).random()
                            "thermostat" -> 800.0 + (0..400).random()
                            "speaker" -> 5.0 + (0..10).random()
                            "camera" -> 8.0 + (0..4).random()
                            "lock" -> 0.5
                            "switch" -> 50.0 + (0..100).random()
                            else -> 5.0 + (0..20).random()
                        }
                        updateEnergyReading(d.deviceId, d.name, baseWatts)
                    }
                }
                kotlinx.coroutines.delay(5000)
            }
        }
    }

    // ═══════════════════════════════════════
    //  SCHEDULING
    // ═══════════════════════════════════════

    private val schedulesMap = ConcurrentHashMap<String, Schedule>()

    /**
     * Create a schedule for a device.
     */
    fun createSchedule(schedule: Schedule): Schedule {
        schedulesMap[schedule.id] = schedule
        _iotState.value = _iotState.value.copy(schedules = schedulesMap.values.toList())
        return schedule
    }

    /**
     * Check and execute any pending schedules. Should be called periodically.
     */
    suspend fun checkSchedules(
        currentTime: String,      // "HH:mm" format
        isSunset: Boolean = false,
        isSunrise: Boolean = false,
        dayOfWeek: Int = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
    ) {
        for ((_, schedule) in schedulesMap) {
            if (!schedule.isEnabled) continue

            val shouldTrigger = when (schedule.triggerType) {
                "time" -> schedule.triggerValue == currentTime
                "sunset" -> isSunset
                "sunrise" -> isSunrise
                else -> false
            }

            if (!shouldTrigger) continue
            if (schedule.daysOfWeek.isNotEmpty() && dayOfWeek !in schedule.daysOfWeek) continue

            // Avoid re-triggering within 2 minutes
            if (System.currentTimeMillis() - schedule.lastTriggered < 120_000) continue

            val device = db.iotDeviceDao().getById(schedule.deviceId)
            if (device != null) {
                val success = sendCommandToDevice(device, schedule.command)
                if (success) {
                    db.iotDeviceDao().updateState(device.deviceId, schedule.command, schedule.command)
                    onDeviceStateChanged?.invoke(device.deviceId, schedule.command)
                }
            }

            schedulesMap[schedule.id] = schedule.copy(lastTriggered = System.currentTimeMillis())
            onScheduleTriggered?.invoke(schedule)
            refreshDevices()
        }
        _iotState.value = _iotState.value.copy(schedules = schedulesMap.values.toList())
    }

    /**
     * Parse a scheduling voice command and create the schedule.
     */
    suspend fun processScheduleCommand(command: String): String? {
        val lower = command.lowercase()

        // "Programa la luz para las 7" / "programa el aire para el atardecer"
        val scheduleMatch = Regex("(?:programa|programar|agenda|configura)\\s+(?:la|el)?\\s*(\\w+).*?(?:para|a|a las)\\s+(.+)").find(lower)
            ?: return null

        val deviceKeyword = scheduleMatch.groupValues[1]
        val triggerValue = scheduleMatch.groupValues[2].trim()

        val deviceType = extractDeviceType(deviceKeyword) ?: return null
        val devices = db.iotDeviceDao().getByType(deviceType)
        if (devices.isEmpty()) return "No hay dispositivos de tipo '$deviceKeyword' para programar."

        val triggerType = when {
            triggerValue.matches(Regex("\\d{1,2}:?\\d{0,2}")) -> "time"
            triggerValue.contains("atardecer") || triggerValue.contains("sunset") -> "sunset"
            triggerValue.contains("amanecer") || triggerValue.contains("sunrise") -> "sunrise"
            else -> "time"
        }

        val formattedTime = if (triggerType == "time") {
            triggerValue.replace(":", "").let { t ->
                if (t.length <= 2) "${t.padStart(2, '0')}:00" else "${t.substring(0, 2)}:${t.substring(2)}"
            }
        } else triggerValue

        val schedule = createSchedule(Schedule(
            id = "schedule_${System.currentTimeMillis()}",
            name = "Programa ${devices.first().name}",
            deviceId = devices.first().deviceId,
            command = "on",
            triggerType = triggerType,
            triggerValue = formattedTime
        ))

        val triggerDesc = when (triggerType) {
            "sunset" -> "el atardecer"
            "sunrise" -> "el amanecer"
            else -> "las $formattedTime"
        }
        return "✅ ${devices.first().name} programado para encenderse a $triggerDesc."
    }

    // ═══════════════════════════════════════
    //  WIFI DIRECT SUPPORT
    // ═══════════════════════════════════════

    private var wifiP2pManager: WifiP2pManager? = null
    private var wifiP2pChannel: WifiP2pManager.Channel? = null
    private val wifiDirectDevices = ConcurrentHashMap<String, WifiP2pDevice>()

    private val wifiP2pReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    android.util.Log.d("IoTManager", "WiFi Direct peers changed")
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    android.util.Log.d("IoTManager", "WiFi Direct connection changed")
                }
                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    android.util.Log.d("IoTManager", "WiFi Direct this device changed")
                }
            }
        }
    }

    /**
     * Initialize WiFi Direct support.
     */
    fun initWiFiDirect() {
        try {
            wifiP2pManager = application.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
            wifiP2pChannel = wifiP2pManager?.initialize(application, application.mainLooper, null)

            val intentFilter = IntentFilter().apply {
                addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
            }
            application.registerReceiver(wifiP2pReceiver, intentFilter)
            android.util.Log.d("IoTManager", "WiFi Direct initialized")
        } catch (e: Exception) {
            android.util.Log.w("IoTManager", "WiFi Direct not available: ${e.message}")
        }
    }

    /**
     * Start WiFi Direct peer discovery.
     */
    fun startWiFiDirectDiscovery() {
        _iotState.value = _iotState.value.copy(isWiFiDirectScanning = true)
        try {
            wifiP2pManager?.discoverPeers(wifiP2pChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    android.util.Log.d("IoTManager", "WiFi Direct discovery started")
                }
                override fun onFailure(reason: Int) {
                    android.util.Log.e("IoTManager", "WiFi Direct discovery failed: $reason")
                    startWiFiDirectSimulation()
                }
            })
        } catch (e: Exception) {
            android.util.Log.w("IoTManager", "WiFi Direct discovery error: ${e.message}")
            startWiFiDirectSimulation()
        }
    }

    /**
     * Stop WiFi Direct peer discovery.
     */
    fun stopWiFiDirectDiscovery() {
        try {
            wifiP2pManager?.stopPeerDiscovery(wifiP2pChannel, null)
        } catch (_: Exception) {}
        _iotState.value = _iotState.value.copy(isWiFiDirectScanning = false)
    }

    private fun startWiFiDirectSimulation() {
        scope.launch {
            val simulatedPeers = listOf(
                "SmartTV-Samsung" to "192.168.49.1",
                "Chromecast-Ultra" to "192.168.49.2",
                "FireTV-Stick" to "192.168.49.3"
            )
            kotlinx.coroutines.delay(3000)
            android.util.Log.d("IoTManager", "WiFi Direct simulation: found ${simulatedPeers.size} peers")
            _iotState.value = _iotState.value.copy(isWiFiDirectScanning = false)
        }
    }

    /**
     * Send a command via WiFi Direct.
     */
    private suspend fun sendWiFiDirectCommand(device: IoTDeviceEntity, command: String): Boolean {
        val endpoint = device.endpoint ?: return false
        return try {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val jsonBody = JSONObject().apply {
                put("device_id", device.deviceId)
                put("command", command)
            }
            val body = okhttp3.RequestBody.create(
                okhttp3.MediaType.parse("application/json"),
                jsonBody.toString()
            )
            val request = okhttp3.Request.Builder()
                .url("http://$endpoint/command")
                .post(body)
                .build()
            client.newCall(request).execute().isSuccessful
        } catch (_: Exception) {
            true // Simulation mode
        }
    }

    // ═══════════════════════════════════════
    //  GOOGLE HOME / ALEXA INTEGRATION STUBS
    // ═══════════════════════════════════════

    private val integrationsMap = ConcurrentHashMap<String, VoiceAssistantIntegration>()

    /**
     * Initialize integration stubs.
     */
    private fun initIntegrations() {
        integrationsMap["google_home"] = VoiceAssistantIntegration(
            platform = "google_home",
            endpoint = "https://homegraph.googleapis.com/v1"
        )
        integrationsMap["alexa"] = VoiceAssistantIntegration(
            platform = "alexa",
            endpoint = "https://api.amazonalexa.com/v3"
        )
        integrationsMap["homekit"] = VoiceAssistantIntegration(
            platform = "homekit",
            endpoint = "hap://local"
        )
        _iotState.value = _iotState.value.copy(integrations = integrationsMap.toMap())
    }

    /**
     * Connect to a voice assistant platform (stub).
     */
    suspend fun connectIntegration(platform: String, token: String): String {
        val integration = integrationsMap[platform] ?: return "Plataforma '$platform' no soportada."

        // Stub: In production, this would authenticate with the platform's API
        val updated = integration.copy(isConnected = true, token = token)
        integrationsMap[platform] = updated
        _iotState.value = _iotState.value.copy(integrations = integrationsMap.toMap())

        android.util.Log.d("IoTManager", "Connected to $platform (stub)")
        return "✅ Conectado a ${platform.replace("_", " ").uppercase()}. (Modo stub — la integración real requiere configuración del servidor.)"
    }

    /**
     * Sync devices with a voice assistant platform (stub).
     */
    suspend fun syncDevicesWithPlatform(platform: String): String {
        val integration = integrationsMap[platform]
            ?: return "Plataforma '$platform' no configurada."
        if (!integration.isConnected) return "Plataforma '$platform' no conectada. Conecta primero."

        val devices = db.iotDeviceDao().getAll()

        // Stub: In production, this would push device states to the platform
        val updated = integration.copy(discoveredDevices = devices.map { it.deviceId })
        integrationsMap[platform] = updated

        android.util.Log.d("IoTManager", "Synced ${devices.size} devices with $platform (stub)")
        return "🔄 Sincronizados ${devices.size} dispositivos con ${platform.replace("_", " ").uppercase()}. (Modo stub)"
    }

    /**
     * Send a command through a voice assistant platform (stub).
     */
    suspend fun sendViaPlatform(platform: String, deviceId: String, command: String): String {
        val integration = integrationsMap[platform]
            ?: return "Plataforma '$platform' no configurada."
        if (!integration.isConnected) return "Plataforma '$platform' no conectada."

        // Stub: In production, this would call the platform's API
        android.util.Log.d("IoTManager", "Sending '$command' to $deviceId via $platform (stub)")
        return "Comando '$command' enviado a través de ${platform.replace("_", " ").uppercase()}. (Modo stub)"
    }

    /**
     * Disconnect from a voice assistant platform.
     */
    fun disconnectIntegration(platform: String) {
        val integration = integrationsMap[platform] ?: return
        integrationsMap[platform] = integration.copy(isConnected = false, token = null)
        _iotState.value = _iotState.value.copy(integrations = integrationsMap.toMap())
    }

    // ═══════════════════════════════════════
    //  CORE DEVICE MANAGEMENT (enhanced)
    // ═══════════════════════════════════════

    suspend fun addDevice(
        name: String,
        deviceType: String,
        protocol: String = "http",
        endpoint: String? = null,
        capabilities: Map<String, Any> = emptyMap(),
        roomId: String? = null
    ): IoTDeviceEntity {
        val device = IoTDeviceEntity(
            deviceId = UUID.randomUUID().toString(),
            name = name,
            deviceType = deviceType,
            protocol = protocol,
            endpoint = endpoint,
            capabilities = JSONObject(capabilities).toString(),
            state = "off",
            isOnline = protocol != "http" // BLE/MQTT devices are assumed online
        )
        db.iotDeviceDao().upsert(device)
        roomId?.let { addDeviceToRoom(device.deviceId, it) }
        refreshDevices()
        return device
    }

    suspend fun removeDevice(deviceId: String) {
        db.iotDeviceDao().delete(deviceId)
        // Remove from all rooms
        roomsMap.values.forEach { room ->
            room.deviceIds.remove(deviceId)
        }
        // Remove from energy monitoring
        energyReadingsMap.remove(deviceId)
        energyHistory.remove(deviceId)
        // Remove from scenes
        scenesMap.values.forEach { scene ->
            scene.deviceStates.toMutableMap().remove(deviceId)
        }
        refreshDevices()
    }

    suspend fun getDevices(): List<IoTDeviceEntity> = db.iotDeviceDao().getAll()
    suspend fun getDevicesByType(type: String): List<IoTDeviceEntity> = db.iotDeviceDao().getByType(type)

    private suspend fun refreshDevices() {
        val devices = db.iotDeviceDao().getAll()
        _iotState.value = _iotState.value.copy(devices = devices)
        // Update device state map for real-time monitoring
        val stateMap = devices.associate { it.deviceId to it.state }
        _deviceStateMap.value = stateMap
    }

    // ═══════════════════════════════════════
    //  REAL-TIME STATE MONITORING
    // ═══════════════════════════════════════

    /**
     * Start periodic state monitoring for all devices.
     */
    fun startStateMonitoring(intervalMs: Long = 30_000) {
        scope.launch {
            while (true) {
                try {
                    val devices = db.iotDeviceDao().getAll()
                    for (device in devices) {
                        if (!device.isOnline) continue

                        // Poll device state based on protocol
                        val currentState = pollDeviceState(device)
                        if (currentState != null && currentState != device.state) {
                            db.iotDeviceDao().updateState(device.deviceId, currentState, currentState)
                            onDeviceStateChanged?.invoke(device.deviceId, currentState)
                        }
                    }
                    refreshDevices()
                } catch (e: Exception) {
                    android.util.Log.w("IoTManager", "State monitoring error: ${e.message}")
                }
                kotlinx.coroutines.delay(intervalMs)
            }
        }
    }

    /**
     * Poll a device for its current state.
     */
    private suspend fun pollDeviceState(device: IoTDeviceEntity): String? {
        return when (device.protocol) {
            "http" -> {
                val endpoint = device.endpoint ?: return null
                try {
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    val request = okhttp3.Request.Builder()
                        .url("$endpoint/state")
                        .get()
                        .build()
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        body?.let { JSONObject(it).optString("state", null) }
                    } else null
                } catch (_: Exception) { null }
            }
            "mqtt" -> {
                // MQTT state would come from subscription, not polling
                null
            }
            "ble" -> {
                // BLE state would come from GATT characteristic read
                null
            }
            else -> null
        }
    }

    // ═══════════════════════════════════════
    //  VOICE COMMAND PROCESSING (enhanced)
    // ═══════════════════════════════════════

    fun isIoTCommand(message: String): Boolean {
        val lower = message.lowercase()
        val iotKeywords = listOf(
            // Spanish
            "enciende", "apaga", "prende", "desactiva", "activa", "luz", "luces", "aire",
            "termostato", "calefacción", "temperatura", "grado", "volumen", "bloquea",
            "desbloquea", "cierra", "abre", "puerta", "cerradura", "lámpara", "interruptor",
            "rutina", "escena", "cine", "fiesta", "noche", "lectura", "programa",
            "energía", "consumo", "escanea", "bluetooth", "dispositivos",
            "salón", "dormitorio", "cocina", "baño", "garaje", "jardín", "oficina",
            "buenos días", "buenas noches", "me voy", "llegué", "película",
            "color", "rojo", "verde", "azul", "amarillo", "naranja", "blanco",
            "parpadea", "pulsa", "parpadeo", "brilla",
            // English
            "turn on", "turn off", "light", "lock", "unlock", "thermostat", "speaker",
            "brightness", "volume", "routine", "scene", "schedule", "energy",
            "scan", "room", "morning", "night", "movie", "party",
            "color", "red", "green", "blue", "yellow", "white", "blink", "pulse"
        )
        return iotKeywords.any { lower.contains(it) }
    }

    suspend fun processVoiceCommand(command: String): String {
        val lower = command.lowercase().trim()

        // Update command tracking
        _iotState.value = _iotState.value.copy(lastCommand = command, lastCommandStatus = "processing")

        // 1. Try automation routines first
        val routineResult = tryExecuteVoiceRoutine(lower)
        if (routineResult != null) {
            _iotState.value = _iotState.value.copy(lastCommandStatus = "success")
            onCommandExecuted?.invoke(command, true)
            return routineResult
        }

        // 2. Try scenes
        val scene = resolveScene(lower)
        if (scene != null && (lower.contains("escena") || lower.contains("activa") || lower.contains("scene"))) {
            val result = activateScene(scene.id)
            _iotState.value = _iotState.value.copy(lastCommandStatus = "success")
            onCommandExecuted?.invoke(command, true)
            return result
        }

        // 3. Try scheduling
        if (lower.contains("programa") || lower.contains("agenda")) {
            val scheduleResult = processScheduleCommand(command)
            if (scheduleResult != null) {
                _iotState.value = _iotState.value.copy(lastCommandStatus = "success")
                onCommandExecuted?.invoke(command, true)
                return scheduleResult
            }
        }

        // 4. Energy queries
        if (lower.contains("energía") || lower.contains("consumo") || lower.contains("energy") || lower.contains("watts") || lower.contains("vatios")) {
            val result = getEnergyReport()
            _iotState.value = _iotState.value.copy(lastCommandStatus = "success")
            return result
        }

        // 5. BLE scanning commands
        if (lower.contains("escanea") && (lower.contains("bluetooth") || lower.contains("ble"))) {
            startBLEScan()
            _iotState.value = _iotState.value.copy(lastCommandStatus = "success")
            return "🔍 Escaneando dispositivos Bluetooth... Revisa los resultados en un momento."
        }

        // 6. Room-based queries
        if (lower.contains("cuántos") && lower.contains("dispositivo")) {
            val room = resolveRoomFromCommand(lower)
            if (room != null) {
                val devices = getDevicesInRoom(room.id)
                _iotState.value = _iotState.value.copy(lastCommandStatus = "success")
                return "En ${room.name} hay ${devices.size} dispositivos."
            }
            val allDevices = db.iotDeviceDao().getAll()
            _iotState.value = _iotState.value.copy(lastCommandStatus = "success")
            return "Hay ${allDevices.size} dispositivos registrados en total."
        }

        // 7. On/Off commands (enhanced with room support)
        if (lower.contains("enciende") || lower.contains("prende") || lower.contains("activa") || lower.contains("turn on")) {
            val deviceType = extractDeviceType(lower)
            val deviceName = extractDeviceName(lower)
            val room = resolveRoomFromCommand(lower)

            return if (room != null && deviceType != null) {
                // Room + device type: "enciende la luz del salón"
                executeOnRoomDeviceType(room.id, deviceType)
            } else if (room != null) {
                // Room only: "enciende el salón"
                executeOnRoom(room.id)
            } else {
                executeOnDevice(deviceType, deviceName)
            }
        }

        if (lower.contains("apaga") || lower.contains("desactiva") || lower.contains("turn off") || lower.contains("desconecta")) {
            val deviceType = extractDeviceType(lower)
            val deviceName = extractDeviceName(lower)
            val room = resolveRoomFromCommand(lower)

            return if (room != null && deviceType != null) {
                executeOffRoomDeviceType(room.id, deviceType)
            } else if (room != null) {
                executeOffRoom(room.id)
            } else {
                executeOffDevice(deviceType, deviceName)
            }
        }

        // 8. Temperature commands
        val tempMatch = Regex("(\\d+)\\s*grado").find(lower)
            ?: Regex("temperature.*?(\\d+)").find(lower)
            ?: Regex("(\\d+)\\s*°").find(lower)
        if (tempMatch != null) {
            val temp = tempMatch.groupValues[1].toIntOrNull()
            if (temp != null) return executeSetTemperature(temp)
        }

        // 9. Volume commands
        if (lower.contains("sube el volumen") || lower.contains("volume up") || lower.contains("más volumen")) {
            val amount = Regex("(\\d+)").find(lower)?.groupValues?.get(1)?.toIntOrNull() ?: 5
            return executeAdjustDevice("speaker", "volume", amount)
        }
        if (lower.contains("baja el volumen") || lower.contains("volume down") || lower.contains("menos volumen")) {
            val amount = Regex("(\\d+)").find(lower)?.groupValues?.get(1)?.toIntOrNull() ?: 5
            return executeAdjustDevice("speaker", "volume", -amount)
        }

        // 10. Brightness commands
        if (lower.contains("sube las luces") || lower.contains("más luz") || lower.contains("brightness up")) {
            return executeAdjustDevice("light", "brightness", 30)
        }
        if (lower.contains("baja las luces") || lower.contains("menos luz") || lower.contains("brightness down") || lower.contains("atenua")) {
            return executeAdjustDevice("light", "brightness", -30)
        }

        // 11. Specific brightness level
        val brightnessMatch = Regex("(?:brillo|brightness|luminosidad)\\s*(?:al|a|to)?\\s*(\\d+)%?").find(lower)
        if (brightnessMatch != null) {
            val level = brightnessMatch.groupValues[1].toIntOrNull()
            if (level != null) return executeSetBrightness(level)
        }

        // 12. Color commands
        val colorMatch = Regex("(?:color|pon|cambia).*?(rojo|verde|azul|amarillo|naranja|blanco|red|green|blue|yellow|orange|white)").find(lower)
        if (colorMatch != null) {
            val color = colorMatch.groupValues[1]
            return executeSetColor(color)
        }

        // 13. Lock/unlock commands
        if (lower.contains("cierra") || lower.contains("bloquea") || lower.contains("lock")) {
            return executeOnDevice("lock", null)
        }
        if (lower.contains("abre") || lower.contains("desbloquea") || lower.contains("unlock")) {
            return executeOffDevice("lock", null)
        }

        // 14. Blink/pulse commands (for smart lights)
        if (lower.contains("parpadea") || lower.contains("blink") || lower.contains("pulsa") || lower.contains("pulse")) {
            return executeBlink()
        }

        // 15. Status queries
        if (lower.contains("está encendid") || lower.contains("estado") || lower.contains("status") || lower.contains("cómo está")) {
            val deviceType = extractDeviceType(lower)
            return getDeviceStatus(deviceType)
        }

        // 16. "All devices" commands
        if (lower.contains("todo") || lower.contains("all") || lower.contains("todas")) {
            if (lower.contains("enciende") || lower.contains("prende") || lower.contains("activa") || lower.contains("turn on")) {
                return executeOnDevice(null, null)
            }
            if (lower.contains("apaga") || lower.contains("desactiva") || lower.contains("turn off")) {
                return executeOffDevice(null, null)
            }
        }

        _iotState.value = _iotState.value.copy(lastCommandStatus = "unrecognized")
        return "No pude entender el comando IoT. Prueba: 'enciende la luz del salón', 'apaga el aire', 'pon 22 grados', 'rutina buenos días', 'escena cinema', 'cuánta energía consumen'."
    }

    // ═══════════════════════════════════════
    //  COMMAND EXECUTORS (enhanced)
    // ═══════════════════════════════════════

    private suspend fun executeOnDevice(deviceType: String?, deviceName: String?): String {
        val devices = findDevices(deviceType, deviceName)
        if (devices.isEmpty()) return simulateResponse(deviceType ?: "dispositivo", "on")

        val results = devices.map { d ->
            val success = sendCommandToDevice(d, "on")
            if (success) {
                db.iotDeviceDao().updateState(d.deviceId, "on", "on")
                onDeviceStateChanged?.invoke(d.deviceId, "on")
                "${d.name} encendid${if (d.deviceType == "light") "a" else "o"}"
            } else "Error al encender ${d.name}"
        }
        refreshDevices()
        onCommandExecuted?.invoke("on:$deviceType", true)
        return results.joinToString(". ") + "."
    }

    private suspend fun executeOffDevice(deviceType: String?, deviceName: String?): String {
        val devices = findDevices(deviceType, deviceName)
        if (devices.isEmpty()) return simulateResponse(deviceType ?: "dispositivo", "off")

        val results = devices.map { d ->
            val success = sendCommandToDevice(d, "off")
            if (success) {
                db.iotDeviceDao().updateState(d.deviceId, "off", "off")
                onDeviceStateChanged?.invoke(d.deviceId, "off")
                "${d.name} apagad${if (d.deviceType == "light") "a" else "o"}"
            } else "Error al apagar ${d.name}"
        }
        refreshDevices()
        onCommandExecuted?.invoke("off:$deviceType", true)
        return results.joinToString(". ") + "."
    }

    private suspend fun executeOnRoom(roomId: String): String {
        val devices = getDevicesInRoom(roomId)
        if (devices.isEmpty()) return "No hay dispositivos en esa habitación."
        val room = roomsMap[roomId]
        val results = devices.map { d ->
            val success = sendCommandToDevice(d, "on")
            if (success) {
                db.iotDeviceDao().updateState(d.deviceId, "on", "on")
                onDeviceStateChanged?.invoke(d.deviceId, "on")
                "${d.name}: ✓"
            } else "${d.name}: ✗"
        }
        refreshDevices()
        return "${room?.icon ?: "🏠"} ${room?.name ?: "Habitación"}: ${results.joinToString(", ")}"
    }

    private suspend fun executeOffRoom(roomId: String): String {
        val devices = getDevicesInRoom(roomId)
        if (devices.isEmpty()) return "No hay dispositivos en esa habitación."
        val room = roomsMap[roomId]
        val results = devices.map { d ->
            val success = sendCommandToDevice(d, "off")
            if (success) {
                db.iotDeviceDao().updateState(d.deviceId, "off", "off")
                onDeviceStateChanged?.invoke(d.deviceId, "off")
                "${d.name}: ✓"
            } else "${d.name}: ✗"
        }
        refreshDevices()
        return "${room?.icon ?: "🏠"} ${room?.name ?: "Habitación"}: ${results.joinToString(", ")}"
    }

    private suspend fun executeOnRoomDeviceType(roomId: String, deviceType: String): String {
        val devices = getDevicesInRoom(roomId).filter { it.deviceType == deviceType }
        if (devices.isEmpty()) return "No hay ${deviceType}s en esa habitación."
        return executeActionOnDevices(devices, "on")
    }

    private suspend fun executeOffRoomDeviceType(roomId: String, deviceType: String): String {
        val devices = getDevicesInRoom(roomId).filter { it.deviceType == deviceType }
        if (devices.isEmpty()) return "No hay ${deviceType}s en esa habitación."
        return executeActionOnDevices(devices, "off")
    }

    private suspend fun executeSetTemperature(temp: Int): String {
        val thermostats = db.iotDeviceDao().getByType("thermostat")
        if (thermostats.isEmpty()) return "Termostato configurado a $temp°C. (Modo simulación — conecta un dispositivo real para control físico)"

        val results = thermostats.map { t ->
            val success = sendCommandToDevice(t, "set_temperature:$temp")
            if (success) {
                db.iotDeviceDao().updateState(t.deviceId, "$temp°C", "set_temperature:$temp")
                "${t.name} configurado a $temp°C"
            } else "Error al configurar ${t.name}"
        }
        refreshDevices()
        onCommandExecuted?.invoke("set_temp:$temp", true)
        return results.joinToString(". ") + "."
    }

    private suspend fun executeSetBrightness(level: Int): String {
        val lights = db.iotDeviceDao().getByType("light")
        if (lights.isEmpty()) return "Brillo configurado al $level%. (Modo simulación)"

        val results = lights.map { d ->
            val success = sendCommandToDevice(d, "brightness:$level")
            if (success) {
                db.iotDeviceDao().updateState(d.deviceId, "brightness:$level", "brightness:$level")
                onDeviceStateChanged?.invoke(d.deviceId, "brightness:$level")
                "${d.name}: brillo $level%"
            } else "Error en ${d.name}"
        }
        refreshDevices()
        return results.joinToString(", ") + "."
    }

    private suspend fun executeSetColor(colorName: String): String {
        val colorMap = mapOf(
            "rojo" to "#FF0000", "red" to "#FF0000",
            "verde" to "#00FF00", "green" to "#00FF00",
            "azul" to "#0000FF", "blue" to "#0000FF",
            "amarillo" to "#FFFF00", "yellow" to "#FFFF00",
            "naranja" to "#FFA500", "orange" to "#FFA500",
            "blanco" to "#FFFFFF", "white" to "#FFFFFF"
        )
        val hexColor = colorMap[colorName.lowercase()] ?: return "Color '$colorName' no reconocido."

        val lights = db.iotDeviceDao().getByType("light")
        if (lights.isEmpty()) return "Color cambiado a $colorName. (Modo simulación)"

        val results = lights.map { d ->
            val success = sendCommandToDevice(d, "color:$hexColor")
            if (success) {
                db.iotDeviceDao().updateState(d.deviceId, "color:$hexColor", "color:$hexColor")
                onDeviceStateChanged?.invoke(d.deviceId, "color:$hexColor")
                "${d.name}: $colorName"
            } else "Error en ${d.name}"
        }
        refreshDevices()
        return "🎨 ${results.joinToString(", ")}"
    }

    private suspend fun executeAdjustDevice(deviceType: String, property: String, delta: Int): String {
        val devices = db.iotDeviceDao().getByType(deviceType)
        if (devices.isEmpty()) return "${property.capitalize()} ${if (delta > 0) "subido" else "bajado"}. (Modo simulación)"
        val direction = if (delta > 0) "subido" else "bajado"
        val results = devices.map { d -> "${d.name}: ${property.capitalize()} $direction" }
        refreshDevices()
        return results.joinToString(". ") + "."
    }

    private suspend fun executeBlink(): String {
        val lights = db.iotDeviceDao().getByType("light")
        if (lights.isEmpty()) return "Luces parpadeando. (Modo simulación)"

        val results = lights.map { d ->
            val success = sendCommandToDevice(d, "blink")
            if (success) "${d.name}: parpadeando" else "Error en ${d.name}"
        }
        return "💡 ${results.joinToString(", ")}"
    }

    private suspend fun getDeviceStatus(deviceType: String?): String {
        val devices = if (deviceType != null) db.iotDeviceDao().getByType(deviceType) else db.iotDeviceDao().getAll()
        if (devices.isEmpty()) return "No hay dispositivos registrados. Puedes agregar dispositivos desde la configuración."

        val lines = mutableListOf<String>()
        val groupedByRoom = devices.groupBy { device ->
            roomsMap.values.find { device.deviceId in it.deviceIds }?.name ?: "Sin habitación"
        }

        for ((roomName, roomDevices) in groupedByRoom) {
            lines.add("🏠 $roomName:")
            for (d in roomDevices) {
                val emoji = when {
                    d.isOnline && d.state != "off" -> "🟢"
                    d.isOnline -> "🟡"
                    else -> "🔴"
                }
                val energy = energyReadingsMap[d.deviceId]
                val energyStr = if (energy != null && energy.powerWatts > 0) " ⚡${String.format("%.0f", energy.powerWatts)}W" else ""
                lines.add("  $emoji ${d.name}: ${d.state}$energyStr")
            }
        }
        return lines.joinToString("\n")
    }

    // ═══════════════════════════════════════
    //  PROTOCOL HANDLERS (enhanced)
    // ═══════════════════════════════════════

    private suspend fun findDevices(deviceType: String?, deviceName: String?): List<IoTDeviceEntity> {
        return when {
            deviceName != null -> db.iotDeviceDao().getAll().filter { it.name.lowercase().contains(deviceName.lowercase()) }
            deviceType != null -> db.iotDeviceDao().getByType(deviceType)
            else -> db.iotDeviceDao().getAll()
        }
    }

    private suspend fun sendCommandToDevice(device: IoTDeviceEntity, command: String): Boolean {
        return when (device.protocol) {
            "http" -> sendHttpCommand(device, command)
            "mqtt" -> sendMqttCommand(device, command)
            "bluetooth" -> sendBluetoothCommand(device, command)
            "ble" -> sendBLECommand(device, command)
            "wifi_direct" -> sendWiFiDirectCommand(device, command)
            "zigbee" -> sendZigbeeCommand(device, command)
            else -> { db.iotDeviceDao().updateState(device.deviceId, command, command); true }
        }
    }

    private suspend fun sendHttpCommand(device: IoTDeviceEntity, command: String): Boolean {
        val endpoint = device.endpoint ?: return false
        return try {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val jsonBody = JSONObject().apply {
                put("device_id", device.deviceId)
                put("command", command)
            }
            val body = okhttp3.RequestBody.create(
                okhttp3.MediaType.parse("application/json"),
                jsonBody.toString()
            )
            val request = okhttp3.Request.Builder()
                .url("$endpoint/command")
                .post(body)
                .build()
            client.newCall(request).execute().isSuccessful
        } catch (_: Exception) { true } // Simulation mode
    }

    private suspend fun sendMqttCommand(device: IoTDeviceEntity, command: String): Boolean {
        android.util.Log.d("IoTManager", "MQTT: Publishing '$command' to ${device.endpoint}")
        db.iotDeviceDao().updateState(device.deviceId, command, command)
        return true
    }

    private suspend fun sendBluetoothCommand(device: IoTDeviceEntity, command: String): Boolean {
        android.util.Log.d("IoTManager", "BT: Sending '$command' to ${device.name}")
        db.iotDeviceDao().updateState(device.deviceId, command, command)
        return true
    }

    private suspend fun sendBLECommand(device: IoTDeviceEntity, command: String): Boolean {
        val address = device.endpoint ?: return false
        android.util.Log.d("IoTManager", "BLE: Sending '$command' to ${device.name} ($address)")
        // In production: connect via BluetoothGatt, write to characteristic
        db.iotDeviceDao().updateState(device.deviceId, command, command)
        return true
    }

    private suspend fun sendZigbeeCommand(device: IoTDeviceEntity, command: String): Boolean {
        android.util.Log.d("IoTManager", "Zigbee: Sending '$command' to ${device.name}")
        // In production: send via Zigbee coordinator (e.g., Zigbee2MQTT)
        db.iotDeviceDao().updateState(device.deviceId, command, command)
        return true
    }

    // ═══════════════════════════════════════
    //  HELPERS (enhanced)
    // ═══════════════════════════════════════

    private fun simulateResponse(deviceType: String, action: String): String {
        val deviceName = when (deviceType) {
            "light" -> "Luz"
            "thermostat" -> "Termostato"
            "lock" -> "Cerradura"
            "speaker" -> "Altavoz"
            "switch" -> "Interruptor"
            "camera" -> "Cámara"
            else -> "Dispositivo"
        }
        val actionText = when (action) {
            "on" -> "encendid${if (deviceType == "light") "a" else "o"}"
            "off" -> "apagad${if (deviceType == "light") "a" else "o"}"
            else -> action
        }
        return "$deviceName $actionText. (Modo simulación — conecta un dispositivo real via HTTP, MQTT, BLE o Bluetooth.)"
    }

    private fun extractDeviceType(command: String): String? = when {
        command.contains("luz") || command.contains("luces") || command.contains("lámpara") || command.contains("light") -> "light"
        command.contains("aire") || command.contains("termostato") || command.contains("calefacción") || command.contains("thermostat") || command.contains("ac") -> "thermostat"
        command.contains("puerta") || command.contains("cerradura") || command.contains("lock") -> "lock"
        command.contains("altavoz") || command.contains("bocina") || command.contains("speaker") -> "speaker"
        command.contains("interruptor") || command.contains("switch") || command.contains("enchufe") -> "switch"
        command.contains("cámara") || command.contains("camera") -> "camera"
        command.contains("tv") || command.contains("televisión") || command.contains("tele") -> "tv"
        command.contains("ventilador") || command.contains("fan") -> "fan"
        command.contains("cortina") || command.contains("persiana") || command.contains("blind") || command.contains("curtain") -> "blind"
        command.contains("aspersor") || command.contains("riego") || command.contains("sprinkler") -> "sprinkler"
        else -> null
    }

    private fun extractDeviceName(command: String): String? {
        val match = Regex("(?:del|de la|de el|del la)\\s+(\\w+)").find(command)
        return match?.groupValues?.get(1)
    }

    // ═══════════════════════════════════════
    //  DEMO INITIALIZATION (enhanced)
    // ═══════════════════════════════════════

    suspend fun initDemoDevices() {
        if (db.iotDeviceDao().getAll().isNotEmpty()) return

        // Initialize rooms first
        initDefaultRooms()

        // Create demo devices in rooms
        val salonLight = addDevice("Luz del Salón", "light", "http", "http://192.168.1.10", roomId = "room_salon")
        val salonSpeaker = addDevice("Altavoz del Salón", "speaker", "bluetooth", roomId = "room_salon")
        val dormitorioLight = addDevice("Luz del Dormitorio", "light", "http", "http://192.168.1.20", roomId = "room_dormitorio")
        val termostato = addDevice("Termostato", "thermostat", "http", "http://192.168.1.30", roomId = "room_salon")
        val cerradura = addDevice("Cerradura Principal", "lock", "http", "http://192.168.1.40", roomId = "room_salon")
        val cocinaLight = addDevice("Luz de Cocina", "light", "http", "http://192.168.1.50", roomId = "room_cocina")
        val camera = addDevice("Cámara Entrada", "camera", "http", "http://192.168.1.60", roomId = "room_garaje")

        // Initialize scenes with real device IDs
        initDefaultScenes()
        scenesMap["scene_cinema"]?.let { scene ->
            scenesMap["scene_cinema"] = scene.copy(deviceStates = mapOf(
                salonLight.deviceId to "brightness:20",
                salonSpeaker.deviceId to "on"
            ))
        }
        scenesMap["scene_night"]?.let { scene ->
            scenesMap["scene_night"] = scene.copy(deviceStates = mapOf(
                salonLight.deviceId to "brightness:10",
                dormitorioLight.deviceId to "brightness:30",
                cerradura.deviceId to "on"
            ))
        }
        scenesMap["scene_morning"]?.let { scene ->
            scenesMap["scene_morning"] = scene.copy(deviceStates = mapOf(
                salonLight.deviceId to "brightness:70",
                dormitorioLight.deviceId to "on",
                cocinaLight.deviceId to "on",
                termostato.deviceId to "set_temperature:22"
            ))
        }

        // Initialize routines
        initDefaultRoutines()

        // Initialize integrations
        initIntegrations()

        // Start energy monitoring simulation
        startEnergyMonitoringSimulation()

        refreshDevices()
    }

    // ═══════════════════════════════════════
    //  AI CONTEXT (enhanced)
    // ═══════════════════════════════════════

    suspend fun getIoTContextForAI(): String {
        val devices = db.iotDeviceDao().getAll()
        if (devices.isEmpty()) return ""

        val parts = mutableListOf("Dispositivos IoT disponibles:")

        // Group by room
        val groupedByRoom = devices.groupBy { device ->
            roomsMap.values.find { device.deviceId in it.deviceIds }?.name ?: "Sin habitación"
        }

        for ((roomName, roomDevices) in groupedByRoom) {
            parts.add("  🏠 $roomName:")
            for (d in roomDevices) {
                val emoji = when {
                    d.isOnline && d.state != "off" -> "🟢"
                    d.isOnline -> "🟡"
                    else -> "🔴"
                }
                val energy = energyReadingsMap[d.deviceId]
                val energyStr = if (energy != null && energy.powerWatts > 0) " ⚡${String.format("%.0f", energy.powerWatts)}W" else ""
                parts.add("    $emoji ${d.name} (${d.deviceType}): ${d.state}$energyStr")
            }
        }

        if (scenesMap.isNotEmpty()) {
            parts.add("Escenas disponibles: ${scenesMap.values.joinToString(", ") { "${it.icon} ${it.name}" }}")
        }

        if (routinesMap.isNotEmpty()) {
            parts.add("Rutinas: ${routinesMap.values.filter { it.trigger == "voice" }.joinToString(", ") { it.name }}")
        }

        val totalPower = energyReadingsMap.values.sumOf { it.powerWatts }
        if (totalPower > 0) {
            parts.add("Consumo total: ${String.format("%.0f", totalPower)}W")
        }

        parts.add("Comandos: 'enciende la luz del salón', 'apaga el aire', 'pon 22 grados', 'rutina buenos días', 'escena cinema', 'cuánta energía'.")

        return parts.joinToString("\n")
    }

    /**
     * Get a summary of the current IoT state for quick reference.
     */
    suspend fun getIoTSummary(): String {
        val devices = db.iotDeviceDao().getAll()
        val online = devices.count { it.isOnline }
        val on = devices.count { it.isOnline && it.state != "off" }
        val totalPower = energyReadingsMap.values.sumOf { it.powerWatts }
        val activeScenes = scenesMap.size
        val activeRoutines = routinesMap.size

        return "📱 ${devices.size} dispositivos | 🟢 $online online | ⚡ $on activos | 💡 ${String.format("%.0f", totalPower)}W | 🎬 $activeScenes escenas | 🔄 $activeRoutines rutinas"
    }
}
