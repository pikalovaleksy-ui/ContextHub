package com.example.project.data.repository

import com.example.project.data.local.dao.RoomDao
import com.example.project.data.local.dao.ZoneDao
import com.example.project.data.local.entity.ZoneEntity
import com.example.project.data.local.entity.toDomain
import com.example.project.data.local.entity.toEntity
import com.example.project.data.remote.mqtt.MqttManager
import com.example.project.data.remote.mqtt.MqttTopics
import com.example.project.model.AddZoneRequest
import com.example.project.model.Ld2450Data
import com.example.project.model.Room
import com.example.project.data.remote.dto.SmartThingsBindingDto
import com.example.project.model.SmartThingsBinding
import com.example.project.model.Vertex
import com.example.project.model.Zone
import com.example.project.model.ZoneColors
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RadarRepository @Inject constructor(
    private val zoneDao: ZoneDao,
    private val roomDao: RoomDao,
    private val mqttManager: MqttManager,
    private val okHttpClient: OkHttpClient
) {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val ld2450Adapter = moshi.adapter(Ld2450Data::class.java)
    private val requestAdapter = moshi.adapter(AddZoneRequest::class.java)
    private val bindingDtoListAdapter = moshi.adapter<List<SmartThingsBindingDto>>(
        Types.newParameterizedType(List::class.java, SmartThingsBindingDto::class.java)
    )

    private val _radarData = MutableStateFlow(Ld2450Data())
    val radarData: StateFlow<Ld2450Data> = _radarData

    private val _addZoneResult = MutableStateFlow<AddZoneResult?>(null)
    val addZoneResult: StateFlow<AddZoneResult?> = _addZoneResult

    val mqttConnectionState: StateFlow<Boolean> = mqttManager.connectionState

    init {
        subscribeToRadar()
    }

    // ─── Rooms ──────────────────────────────────────────────────────────────

    fun allRooms(): Flow<List<Room>> =
        roomDao.getAllRooms().map { list -> list.map { it.toDomain() } }

    suspend fun getRoomById(roomId: String): Room? =
        roomDao.getRoomById(roomId)?.toDomain()

    suspend fun createRoom(name: String): Room {
        val room = Room(id = UUID.randomUUID().toString(), name = name)
        roomDao.insertRoom(room.toEntity())
        return room
    }

    suspend fun deleteRoom(roomId: String) {
        zoneDao.deleteZonesByRoomId(roomId)
        roomDao.deleteRoomById(roomId)
    }

    // ─── Zones ──────────────────────────────────────────────────────────────

    fun allZones(): Flow<List<Zone>> =
        zoneDao.getAllZones().map { list -> list.map { it.toDomain() } }

    fun zonesByRoomId(roomId: String): Flow<List<Zone>> =
        zoneDao.getZonesByRoomId(roomId).map { list -> list.map { it.toDomain() } }

    suspend fun getZoneById(zoneId: String): Zone? =
        zoneDao.getZoneById(zoneId)?.toDomain()

    suspend fun saveZoneLocally(zone: Zone) {
        zoneDao.insertZone(zone.toEntity())
    }

    suspend fun deleteZone(zoneId: String) {
        zoneDao.deleteZoneById(zoneId)
    }

    suspend fun setZoneEnabled(zoneId: String, enabled: Boolean) {
        zoneDao.setZoneEnabled(zoneId, enabled)
    }

    suspend fun saveSmartThingsBindings(
        zoneId: String,
        bindings: List<SmartThingsBinding>
    ) {
        val zone = zoneDao.getZoneById(zoneId) ?: return
        zoneDao.updateZone(zone.copy(
            smartThingsBindingsJson = ZoneEntity.bindingsToJson(bindings)
        ))
    }

    suspend fun saveBindingsToServer(
        serverUrl: String,
        zoneId: String,
        bindings: List<SmartThingsBinding>
    ) {
        try {
            val dtos = bindings.map { b ->
                SmartThingsBindingDto(b.deviceId, b.action, b.extraParams)
            }
            val json = bindingDtoListAdapter.toJson(dtos)
            val url = MqttTopics.zoneBindingsUrl(serverUrl, zoneId)
            withContext(Dispatchers.IO) {
                val body = json.toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()
                okHttpClient.newCall(request).execute()
            }
        } catch (_: Exception) {}
    }

    // ─── MQTT ───────────────────────────────────────────────────────────────

    fun subscribeToRadar(brokerUrl: String = MqttTopics.DEFAULT_BROKER_URL) {
        mqttManager.connect(brokerUrl)
        mqttManager.subscribe(MqttTopics.LD2450_TOPIC) { _, message ->
            try {
                val data = ld2450Adapter.fromJson(message)
                if (data != null) {
                    _radarData.value = data
                }
            } catch (_: Exception) {}
        }
    }

    fun unsubscribeFromRadar() {}

    // ─── Server API ─────────────────────────────────────────────────────────

    suspend fun addZoneToServer(
        serverUrl: String,
        zoneName: String,
        vertices: List<Vertex>,
        roomId: String,
        deviceId: String = MqttTopics.DEVICE_ID
    ) {
        if (vertices.size < 3) {
            _addZoneResult.value = AddZoneResult.Error("Минимум 3 вершины")
            return
        }
        if (zoneName.isBlank()) {
            _addZoneResult.value = AddZoneResult.Error("Укажите название зоны")
            return
        }

        val existingZones = zoneDao.getZonesByRoomIdSync(roomId)
        if (existingZones.size >= 10) {
            _addZoneResult.value = AddZoneResult.Error("Максимум 10 зон на комнату")
            return
        }

        val usedColors = existingZones.map { it.color }.toSet()
        val availableColor = ZoneColors.palette.firstOrNull { it !in usedColors }
            ?: ZoneColors.palette.last()

        _addZoneResult.value = AddZoneResult.Sending

        try {
            val request = AddZoneRequest(zoneName, vertices)
            val json = requestAdapter.toJson(request)
            val url = MqttTopics.addZoneUrl(serverUrl, deviceId)

            val result = withContext(Dispatchers.IO) {
                val body = json.toRequestBody("application/json".toMediaType())
                val httpRequest = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()
                okHttpClient.newCall(httpRequest).execute()
            }

            if (result.isSuccessful) {
                val localZone = Zone(
                    id = UUID.randomUUID().toString(),
                    name = zoneName,
                    vertices = vertices,
                    roomId = roomId,
                    color = availableColor
                )
                saveZoneLocally(localZone)
                _addZoneResult.value = AddZoneResult.Success
            } else {
                val errorBody = result.body?.string() ?: "HTTP ${result.code}"
                _addZoneResult.value = AddZoneResult.Error(errorBody)
            }
        } catch (e: Exception) {
            _addZoneResult.value = AddZoneResult.Error(
                e.message ?: "Ошибка сети"
            )
        }
    }

    suspend fun deleteZoneFromServer(zoneName: String) {
        try {
            val url = MqttTopics.deleteZoneUrl(MqttTopics.DEFAULT_SERVER_URL, zoneName)
            withContext(Dispatchers.IO) {
                val request = Request.Builder().url(url).delete().build()
                okHttpClient.newCall(request).execute()
            }
        } catch (_: Exception) {}
    }

    suspend fun saveZoneWithServer(
        serverUrl: String,
        zone: Zone,
        deviceId: String = MqttTopics.DEVICE_ID
    ) {
        try {
            val request = AddZoneRequest(zone.name, zone.vertices)
            val json = requestAdapter.toJson(request)
            val url = MqttTopics.addZoneUrl(serverUrl, deviceId)
            withContext(Dispatchers.IO) {
                val body = json.toRequestBody("application/json".toMediaType())
                val httpRequest = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()
                okHttpClient.newCall(httpRequest).execute()
            }
        } catch (_: Exception) {}
    }

    fun clearAddZoneResult() {
        _addZoneResult.value = null
    }
}

sealed class AddZoneResult {
    object Sending : AddZoneResult()
    object Success : AddZoneResult()
    data class Error(val message: String) : AddZoneResult()
}
