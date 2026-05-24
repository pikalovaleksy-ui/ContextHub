package com.example.project.data.repository

import com.example.project.data.local.dao.DeviceDao
import com.example.project.data.local.entity.toDomain
import com.example.project.data.local.entity.toEntity
import com.example.project.data.remote.mqtt.MqttManager
import com.example.project.data.remote.mqtt.MqttTopics
import com.example.project.model.Device
import com.example.project.model.DeviceStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepository @Inject constructor(
    private val deviceDao: DeviceDao,
    private val mqttManager: MqttManager
) {
    fun getDevice(): Flow<Device?> = deviceDao.getDevice().map { it?.toDomain() }

    suspend fun saveDevice(device: Device) {
        deviceDao.insertDevice(device.toEntity())
    }

    suspend fun getDeviceSync(): Device? = deviceDao.getDeviceSync()?.toDomain()

    suspend fun connectToDevice(ipAddress: String): Result<Device> {
        val device = Device(
            id = UUID.randomUUID().toString().take(8),
            name = "ContextHub",
            ipAddress = ipAddress,
            status = DeviceStatus.ONLINE,
            lastSeen = System.currentTimeMillis()
        )
        deviceDao.insertDevice(device.toEntity())
        val saved = deviceDao.getDeviceSync()?.toDomain()
        return Result.success(saved ?: device)
    }

    suspend fun updateDeviceStatus(status: DeviceStatus) {
        val device = deviceDao.getDeviceSync()?.toDomain()
        if (device != null) {
            deviceDao.updateDevice(device.copy(status = status).toEntity())
        }
    }

    suspend fun connectMqtt(brokerUrl: String = MqttTopics.DEFAULT_BROKER_URL) {
        val device = deviceDao.getDeviceSync()
        if (device != null) {
            mqttManager.connect(brokerUrl, MqttTopics.CLIENT_ID_PREFIX + device.id)
        }
    }

    suspend fun disconnectDevice() {
        mqttManager.disconnect()
        deviceDao.deleteAllDevices()
    }
}
