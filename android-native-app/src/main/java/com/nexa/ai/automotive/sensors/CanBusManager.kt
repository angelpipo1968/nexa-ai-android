package com.nexa.ai.automotive.sensors

import android.util.Log

/**
 * CanFrame — Lightweight representation of a Controller Area Network (CAN) frame.
 */
data class CanFrame(
    val id: Int,
    val dlc: Int,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CanFrame) return false
        if (id != other.id) return false
        if (dlc != other.dlc) return false
        return data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + dlc
        result = 31 * result + data.contentHashCode()
        return result
    }
}

/**
 * CanBusManager — Adaptador nativo para el procesamiento de tramas raw de CAN-Bus.
 * Habilita comunicación de bajo nivel en vehículos pesados o integraciones OBD directas.
 */
class CanBusManager {
    companion object {
        private const val TAG = "NexaCAN"
    }
    private var isListening = false

    /**
     * Registers listener callback to process raw CAN-Bus frames.
     */
    fun listenCanBus(onFrame: (CanFrame) -> Unit) {
        if (isListening) return
        isListening = true
        Log.i(TAG, "Subscribed to raw CAN-Bus frame stream")
        
        // Simulating background CAN-bus traffic decoding in hardware trials
        Thread {
            try {
                while (isListening) {
                    // Simulating a steering angle CAN frame (ID: 0x25F)
                    val frame = CanFrame(
                        id = 0x25F,
                        dlc = 4,
                        data = byteArrayOf(0x01, 0x24, 0x00, 0x00)
                    )
                    onFrame(frame)
                    Thread.sleep(100) // 10Hz frequency
                }
            } catch (_: InterruptedException) {
                isListening = false
            }
        }.start()
    }

    /**
     * Unsubscribes from CAN-Bus frames.
     */
    fun stopListening() {
        isListening = false
        Log.i(TAG, "Unsubscribed from CAN-Bus")
    }
}
