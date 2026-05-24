package com.example.project.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.project.model.Friend
import com.example.project.model.FriendStatus

@Entity(tableName = "friends")
data class FriendEntity(
    @PrimaryKey val id: String,
    val name: String,
    val deviceId: String,
    val status: String = "OFFLINE",
    val lastTouchReceived: Long = 0L
)

fun FriendEntity.toDomain(): Friend = Friend(
    id = id,
    name = name,
    deviceId = deviceId,
    status = FriendStatus.valueOf(status),
    lastTouchReceived = lastTouchReceived
)

fun Friend.toEntity(): FriendEntity = FriendEntity(
    id = id,
    name = name,
    deviceId = deviceId,
    status = status.name,
    lastTouchReceived = lastTouchReceived
)
