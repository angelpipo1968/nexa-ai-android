package com.nexa.ai.automotive.sensors

import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ObdSensorManager — Handles Bluetooth socket connection to ELM327/OBD-II adapters
 * and decodes standard diagnostic PIDs locally in real-time.
 */
class ObdSensorManager(
    private var socket: BluetoothSocket? = null
) {
    companion object {
        private const val TAG = "NexaOBD"
    }
    private var isRunning = false
    private var workerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private var input: InputStream? = null
    private var output: OutputStream? = null

    init {
        try {
            socket?.let {
                input = it.inputStream
                output = it.outputStream
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error obtaining streams from BluetoothSocket: ${e.message}")
        }
    }

    /**
     * Starts the polling background worker for OBD-II telemetries.
     * Automatically falls back to simulated telemetry if socket is null (demo/field testing mode).
     */
    fun startPolling() {
        if (isRunning) return
        isRunning = true
        Log.i(TAG, "Starting OBD-II telemetry polling...")

        workerJob = scope.launch {
            if (socket == null || input == null || output == null) {
                Log.w(TAG, "Bluetooth socket not connected — Starting simulation mode")
                runSimulationMode()
            } else {
                runHardwarePollingMode()
            }
        }
    }

    /**
     * Polling hardware OBD-II PIDs.
     */
    private suspend fun runHardwarePollingMode() {
        // Initialize ELM327 with AT commands
        try {
            sendObdCommand("ATZ\r") // Reset ELM327
            delay(1000)
            sendObdCommand("ATE0\r") // Echo Off
            delay(200)
            sendObdCommand("ATSP0\r") // Automatic protocol detection
            delay(500)

            while (isRunning) {
                // Poll Speed PID: 01 0D
                val speedRaw = sendObdCommand("010D\r")
                VehicleState.speed = parseSpeedResponse(speedRaw)

                // Poll RPM PID: 01 0C
                val rpmRaw = sendObdCommand("010C\r")
                VehicleState.rpm = parseRpmResponse(rpmRaw)

                // Poll Engine Temp PID: 01 05
                val tempRaw = sendObdCommand("0105\r")
                VehicleState.engineTemp = parseTempResponse(tempRaw)

                delay(250) // 4Hz polling rate
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error polling hardware OBD: ${e.message}", e)
            stopPolling()
        }
    }

    /**
     * Simulated vehicle telemetry for field trials.
     */
    private suspend fun runSimulationMode() {
        var direction = 1
        var currentSpeed = 0
        while (isRunning) {
            // Speed fluctuation: 0 -> 120 -> 0 km/h
            currentSpeed += 2 * direction
            if (currentSpeed >= 120) {
                currentSpeed = 120
                direction = -1
            } else if (currentSpeed <= 0) {
                currentSpeed = 0
                direction = 1
            }

            VehicleState.speed = currentSpeed
            VehicleState.rpm = 800 + (currentSpeed * 35)
            VehicleState.fuelLevel = 75 - (currentSpeed / 20)
            VehicleState.engineTemp = 90 + (currentSpeed / 15)
            VehicleState.brakeActive = currentSpeed == 0

            delay(300)
        }
    }

    private fun sendObdCommand(cmd: String): String {
        val outStream = output ?: return ""
        val inStream = input ?: return ""

        return try {
            outStream.write(cmd.toByteArray())
            outStream.flush()

            val reader = inStream.bufferedReader()
            val response = reader.readLine()
            response ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Error writing command to OBD socket: ${e.message}")
            ""
        }
    }

    private fun parseSpeedResponse(response: String): Int {
        // Expected response format for 010D: "41 0D XX" where XX is speed in Hex km/h
        if (response.isBlank()) return 0
        return try {
            val parts = response.trim().split(" ")
            if (parts.size >= 3) {
                parts[2].toInt(16)
            } else {
                0
            }
        } catch (_: Exception) {
            0
        }
    }

    private fun parseRpmResponse(response: String): Int {
        // Expected response format for 010C: "41 0C XX YY" where RPM = ((XX * 256) + YY) / 4
        if (response.isBlank()) return 0
        return try {
            val parts = response.trim().split(" ")
            if (parts.size >= 4) {
                val xx = parts[2].toInt(16)
                val yy = parts[3].toInt(16)
                ((xx * 256) + yy) / 4
            } else {
                800
            }
        } catch (_: Exception) {
            800
        }
    }

    private fun parseTempResponse(response: String): Int {
        // Expected response format for 0105: "41 05 XX" where Temp = XX - 40
        if (response.isBlank()) return 90
        return try {
            val parts = response.trim().split(" ")
            if (parts.size >= 3) {
                parts[2].toInt(16) - 40
            } else {
                90
            }
        } catch (_: Exception) {
            90
        }
    }

    /**
     * Stops the background polling loop.
     */
    fun stopPolling() {
        isRunning = false
        workerJob?.cancel()
        workerJob = null
        VehicleState.reset()
        Log.i(TAG, "OBD-II polling stopped.")
    }
}
