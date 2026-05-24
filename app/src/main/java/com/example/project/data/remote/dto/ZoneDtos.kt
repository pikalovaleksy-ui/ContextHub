package com.example.project.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ZoneDto(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "x1") val x1: Float,
    @Json(name = "y1") val y1: Float,
    @Json(name = "x2") val x2: Float,
    @Json(name = "y2") val y2: Float,
    @Json(name = "trigger") val trigger: String,
    @Json(name = "dwellTimeMinutes") val dwellTimeMinutes: Int = 0,
    @Json(name = "smartThingsDeviceId") val smartThingsDeviceId: String? = null,
    @Json(name = "smartThingsAction") val smartThingsAction: String? = null
)
