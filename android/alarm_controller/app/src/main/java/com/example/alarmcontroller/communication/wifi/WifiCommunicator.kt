package com.example.alarmcontroller.communication.wifi

import com.example.alarmcontroller.communication.ConnectionState
import com.example.alarmcontroller.communication.DeviceCommunicator
import com.example.alarmcontroller.communication.DeviceMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Placeholder implementation of [DeviceCommunicator] for future Wi-Fi / TCP / WebSocket communication.
 * When Wi-Fi communication is implemented, simply plug this in place of or alongside [BleManager].
 */
class WifiCommunicator : DeviceCommunicator {

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<DeviceMessage>(extraBufferCapacity = 64)
    override val incomingMessages: SharedFlow<DeviceMessage> = _incomingMessages.asSharedFlow()

    override suspend fun connect(target: String): Result<Unit> {
        // e.g., connect via IP / Hostname:Port or HTTP REST / WebSocket / MQTT
        _connectionState.value = ConnectionState.Connecting(target)
        return Result.failure(NotImplementedError("La communication Wi-Fi sera implémentée prochainement."))
    }

    override fun disconnect() {
        _connectionState.value = ConnectionState.Disconnected
    }

    override suspend fun sendMessage(message: String): Result<Unit> {
        return Result.failure(NotImplementedError("La communication Wi-Fi sera implémentée prochainement."))
    }
}
