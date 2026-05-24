package com.example.project.data.repository

import com.example.project.data.local.dao.ZoneDao
import com.example.project.data.local.entity.toDomain
import com.example.project.data.local.entity.toEntity
import com.example.project.data.remote.mqtt.MqttManager
import com.example.project.data.remote.mqtt.MqttTopics
import com.example.project.model.AddZoneRequest
import com.example.project.model.Ld2450Data
import com.example.project.model.Vertex
import com.example.project.model.Zone
import com.squareup.moshi.Moshi
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
    private val mqttManager: MqttManager,
    private val okHttpClient: OkHttpClient
) {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val ld2450Adapter = moshi.adapter(Ld2450Data::class.java)
    private val requestAdapter = moshi.adapter(AddZoneRequest::class.java)

    private val _radarData = MutableStateFlow(Ld2450Data())
    val radarData: StateFlow<Ld2450Data> = _radarData

    private val _addZoneResult = MutableStateFlow<AddZoneResult?>(null)
    val addZoneResult: StateFlow<AddZoneResult?> = _addZoneResult

    val mqttConnectionState: StateFlow<Boolean> = mqttManager.connectionState

    private var subscribed = false

    fun allZones(): Flow<List<Zone>> =
        zoneDao.getAllZones().map { list -> list.map { it.toDomain() } }

    suspend fun getZoneById(zoneId: String): Zone? =
        zoneDao.getZoneById(zoneId)?.toDomain()

    fun subscribeToRadar(brokerUrl: String = MqttTopics.DEFAULT_BROKER_URL) {
        if (subscribed) return
        subscribed = true

        mqttManager.connect(brokerUrl)
        mqttManager.subscribe(MqttTopics.LD2450_TOPIC) { _, message ->
            android.util.Log.d("RadarDebug", "MQTT message received, length=${message.length}")
            try {
                val data = ld2450Adapter.fromJson(message)
                if (data != null) {
                    android.util.Log.d("RadarDebug", "Parsed OK, targets=${data.targets.size}")
                    _radarData.value = data
                } else {
                    android.util.Log.e("RadarDebug", "Parsed null")
                }
            } catch (e: Exception) {
                android.util.Log.e("RadarDebug", "JSON parse error: ${e.message}")
            }
        }
    }

    fun unsubscribeFromRadar() {
        if (subscribed) {
            try {
                mqttManager.unsubscribe(MqttTopics.LD2450_TOPIC)
                subscribed = false
            } catch (_: Exception) {}
        }
    }

    suspend fun saveZoneLocally(zone: Zone) {
        zoneDao.insertZone(zone.toEntity())
    }

    suspend fun deleteZone(zoneId: String) {
        zoneDao.deleteZoneById(zoneId)
    }

    suspend fun setZoneEnabled(zoneId: String, enabled: Boolean) {
        zoneDao.setZoneEnabled(zoneId, enabled)
    }

    suspend fun addZoneToServer(
        serverUrl: String,
        zoneName: String,
        vertices: List<Vertex>,
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
                    vertices = vertices
                )
                saveZoneLocally(localZone)
                _addZoneResult.value = AddZoneResult.Success
                android.util.Log.d("RadarRepo", "Zone $zoneName created, HTTP ${result.code}")
            } else {
                val errorBody = result.body?.string() ?: "HTTP ${result.code}"
                _addZoneResult.value = AddZoneResult.Error(errorBody)
                android.util.Log.e("RadarRepo", "Zone creation failed: $errorBody")
            }
        } catch (e: Exception) {
            _addZoneResult.value = AddZoneResult.Error(
                e.message ?: "Ошибка сети"
            )
            android.util.Log.e("RadarRepo", "HTTP error: ${e.message}")
        }
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
