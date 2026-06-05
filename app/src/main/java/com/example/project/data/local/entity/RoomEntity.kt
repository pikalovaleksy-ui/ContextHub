package com.example.project.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.project.model.Room

@Entity(tableName = "rooms")
data class RoomEntity(
    @PrimaryKey val id: String,
    val name: String
)

fun RoomEntity.toDomain(): Room = Room(
    id = id,
    name = name
)

fun Room.toEntity(): RoomEntity = RoomEntity(
    id = id,
    name = name
)
