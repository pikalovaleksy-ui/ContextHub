package com.example.project.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WifiNetworkDto(
    @Json(name = "ssid") val ssid: String,
    @Json(name = "signalStrength") val signalStrength: Int = 0,
    @Json(name = "encrypted") val encrypted: Boolean = true
)

@JsonClass(generateAdapter = true)
data class WifiConfigRequest(
    @Json(name = "ssid") val ssid: String,
    @Json(name = "password") val password: String
)

@JsonClass(generateAdapter = true)
data class DeviceStatusDto(
    @Json(name = "deviceId") val deviceId: String,
    @Json(name = "status") val status: String,
    @Json(name = "ip") val ip: String = ""
)

@JsonClass(generateAdapter = true)
data class SmartThingsDeviceDto(
    @Json(name = "deviceId") val deviceId: String,
    @Json(name = "name") val name: String,
    @Json(name = "capabilities") val capabilities: List<String> = emptyList()
)
