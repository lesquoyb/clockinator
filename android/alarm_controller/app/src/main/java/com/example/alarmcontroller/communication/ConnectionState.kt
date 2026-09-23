package com.example.alarmcontroller.communication

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data class Connecting(val deviceName: String?) : ConnectionState
    data class Connected(val deviceName: String, val address: String) : ConnectionState
    data class Error(val message: String) : ConnectionState
}
