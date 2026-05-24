package com.example.project.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.project.model.Device
import com.example.project.model.DeviceStatus

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val id: String,
    val name: String = "ContextHub",
    val macAddress: String = "",
    val ipAddress: String = "",
    val status: String = "OFFLINE",
    val lastSeen: Long = 0L
)

fun DeviceEntity.toDomain(): Device = Device(
    id = id,
    name = name,
    macAddress = macAddress,
    ipAddress = ipAddress,
    status = DeviceStatus.valueOf(status),
    lastSeen = lastSeen
)

fun Device.toEntity(): DeviceEntity = DeviceEntity(
    id = id,
    name = name,
    macAddress = macAddress,
    ipAddress = ipAddress,
    status = status.name,
    lastSeen = lastSeen
)
