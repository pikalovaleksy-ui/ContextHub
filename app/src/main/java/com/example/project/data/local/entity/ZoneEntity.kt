package com.example.project.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.project.model.SmartThingsBinding
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
    val enabled: Boolean = true,
    val roomId: String = "",
    val color: Int = 0,
    val smartThingsBindingsJson: String = "[]"
) {
    companion object {
        private val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        private val vertexListAdapter = moshi.adapter<List<Vertex>>(
            Types.newParameterizedType(List::class.java, Vertex::class.java)
        )

        private val bindingListAdapter = moshi.adapter<List<SmartThingsBinding>>(
            Types.newParameterizedType(List::class.java, SmartThingsBinding::class.java)
        )

        fun verticesToJson(vertices: List<Vertex>): String =
            vertexListAdapter.toJson(vertices)

        fun verticesFromJson(json: String): List<Vertex> =
            try { vertexListAdapter.fromJson(json) ?: emptyList() } catch (_: Exception) { emptyList() }

        fun bindingsToJson(bindings: List<SmartThingsBinding>): String =
            bindingListAdapter.toJson(bindings)

        fun bindingsFromJson(json: String): List<SmartThingsBinding> =
            try { bindingListAdapter.fromJson(json) ?: emptyList() } catch (_: Exception) { emptyList() }
    }
}

fun ZoneEntity.toDomain(): Zone = Zone(
    id = id,
    name = name,
    vertices = ZoneEntity.verticesFromJson(verticesJson),
    trigger = try { TriggerType.valueOf(trigger) } catch (_: Exception) { TriggerType.ENTER },
    dwellTimeMinutes = dwellTimeMinutes,
    enabled = enabled,
    roomId = roomId,
    color = color,
    smartThingsBindings = ZoneEntity.bindingsFromJson(smartThingsBindingsJson)
)

fun Zone.toEntity(): ZoneEntity = ZoneEntity(
    id = id,
    name = name,
    verticesJson = ZoneEntity.verticesToJson(vertices),
    trigger = trigger.name,
    dwellTimeMinutes = dwellTimeMinutes,
    enabled = enabled,
    roomId = roomId,
    color = color,
    smartThingsBindingsJson = ZoneEntity.bindingsToJson(smartThingsBindings)
)
