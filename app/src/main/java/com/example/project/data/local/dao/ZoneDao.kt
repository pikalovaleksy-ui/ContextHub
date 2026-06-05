package com.example.project.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.project.data.local.entity.ZoneEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ZoneDao {
    @Query("SELECT * FROM zones ORDER BY name ASC")
    fun getAllZones(): Flow<List<ZoneEntity>>

    @Query("SELECT * FROM zones WHERE id = :zoneId")
    suspend fun getZoneById(zoneId: String): ZoneEntity?

    @Query("SELECT * FROM zones WHERE roomId = :roomId ORDER BY name ASC")
    fun getZonesByRoomId(roomId: String): Flow<List<ZoneEntity>>

    @Query("SELECT * FROM zones WHERE roomId = :roomId")
    suspend fun getZonesByRoomIdSync(roomId: String): List<ZoneEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertZone(zone: ZoneEntity)

    @Update
    suspend fun updateZone(zone: ZoneEntity)

    @Delete
    suspend fun deleteZone(zone: ZoneEntity)

    @Query("DELETE FROM zones WHERE id = :zoneId")
    suspend fun deleteZoneById(zoneId: String)

    @Query("DELETE FROM zones WHERE roomId = :roomId")
    suspend fun deleteZonesByRoomId(roomId: String)

    @Query("SELECT * FROM zones WHERE enabled = 1")
    suspend fun getEnabledZones(): List<ZoneEntity>

    @Query("UPDATE zones SET enabled = :enabled WHERE id = :zoneId")
    suspend fun setZoneEnabled(zoneId: String, enabled: Boolean)
}
