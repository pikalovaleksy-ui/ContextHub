package com.example.project.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.project.model.TriggerType
import com.example.project.model.Vertex
import com.example.project.model.Zone
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@Entity(tableName = "zones")
data class ZoneEntity(
    @PrimaryKey val id: String,
    val name: String,
    val verticesJson: String,
    val trigger: String,
    val dwellTimeMinutes: Int = 0,
    val enabled: Boolean = true
) {
    companion object {
        private val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        private val vertexListAdapter = moshi.adapter<List<Vertex>>(
            Types.newParameterizedType(List::class.java, Vertex::class.java)
        )

        fun verticesToJson(vertices: List<Vertex>): String =
            vertexListAdapter.toJson(vertices)

        fun verticesFromJson(json: String): List<Vertex> =
            try { vertexListAdapter.fromJson(json) ?: emptyList() } catch (_: Exception) { emptyList() }
    }
}

fun ZoneEntity.toDomain(): Zone = Zone(
    id = id,
    name = name,
    vertices = ZoneEntity.verticesFromJson(verticesJson),
    trigger = try { TriggerType.valueOf(trigger) } catch (_: Exception) { TriggerType.ENTER },
    dwellTimeMinutes = dwellTimeMinutes,
    enabled = enabled
)

fun Zone.toEntity(): ZoneEntity = ZoneEntity(
    id = id,
    name = name,
    verticesJson = ZoneEntity.verticesToJson(vertices),
    trigger = trigger.name,
    dwellTimeMinutes = dwellTimeMinutes,
    enabled = enabled
)
