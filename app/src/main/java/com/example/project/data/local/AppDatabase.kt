package com.example.project.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.project.data.local.dao.DeviceDao
import com.example.project.data.local.dao.FriendDao
import com.example.project.data.local.dao.RoomDao
import com.example.project.data.local.dao.ZoneDao
import com.example.project.data.local.entity.DeviceEntity
import com.example.project.data.local.entity.FriendEntity
import com.example.project.data.local.entity.RoomEntity
import com.example.project.data.local.entity.ZoneEntity

@Database(
    entities = [ZoneEntity::class, DeviceEntity::class, FriendEntity::class, RoomEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun zoneDao(): ZoneDao
    abstract fun deviceDao(): DeviceDao
    abstract fun friendDao(): FriendDao
    abstract fun roomDao(): RoomDao
}
