package com.nexa.ai.automotive.sensors

import java.util.concurrent.atomic.AtomicInteger

/**
 * VehicleState — Thread-safe, centralized, in-memory repository for vehicle telemetry.
 * Serves as the real-time context database for local offline LLM queries.
 */
object VehicleState {
    private val _speed = AtomicInteger(0)
    private val _rpm = AtomicInteger(0)
    private val _fuelLevel = AtomicInteger(100)
    private val _engineTemp = AtomicInteger(90)
    private val _brakeActive = java.util.concurrent.atomic.AtomicBoolean(false)

    var speed: Int
        get() = _speed.get()
        set(value) { _speed.set(value) }

    var rpm: Int
        get() = _rpm.get()
        set(value) { _rpm.set(value) }

    var fuelLevel: Int
        get() = _fuelLevel.get()
        set(value) { _fuelLevel.set(value) }

    var engineTemp: Int
        get() = _engineTemp.get()
        set(value) { _engineTemp.set(value) }

    var brakeActive: Boolean
        get() = _brakeActive.get()
        set(value) { _brakeActive.set(value) }

    /**
     * Resets state when OBD interface is disconnected.
     */
    fun reset() {
        _speed.set(0)
        _rpm.set(0)
        _fuelLevel.set(100)
        _engineTemp.set(90)
        _brakeActive.set(false)
    }
}
