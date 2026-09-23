package com.example.alarmcontroller.communication

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class MessageDirection {
    TX, // Transmitted / Sent
    RX  // Received
}

data class DeviceMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val direction: MessageDirection,
    val timestamp: Long = System.currentTimeMillis()
) {
    val formattedTime: String
        get() {
            val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            return formatter.format(Date(timestamp))
        }
}
