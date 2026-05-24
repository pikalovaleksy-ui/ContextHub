package com.example.project.model

data class Device(
    val id: String,
    val name: String = "ContextHub",
    val macAddress: String = "",
    val ipAddress: String = "",
    val status: DeviceStatus = DeviceStatus.OFFLINE,
    val lastSeen: Long = 0L
)

enum class DeviceStatus {
    ONLINE, OFFLINE, CONNECTING
}
