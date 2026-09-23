package com.example.alarmcontroller.communication

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Common communication interface for both Bluetooth BLE and future Wi-Fi / Network communication.
 */
interface DeviceCommunicator {
    val connectionState: StateFlow<ConnectionState>
    val incomingMessages: SharedFlow<DeviceMessage>

    suspend fun connect(target: String): Result<Unit>
    fun disconnect()
    suspend fun sendMessage(message: String): Result<Unit>
}
