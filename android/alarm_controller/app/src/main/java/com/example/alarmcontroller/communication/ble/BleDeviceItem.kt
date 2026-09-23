package com.example.alarmcontroller.communication.ble

data class BleDeviceItem(
    val name: String,
    val address: String,
    val rssi: Int
)
