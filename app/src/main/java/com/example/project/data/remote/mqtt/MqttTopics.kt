package com.example.project.data.remote.mqtt

object MqttTopics {
    const val LD2450_TOPIC = "assistants/1/ld2450"
    const val DEVICE_ID = "1"

    // HiveMQ public broker (эмулятор должен иметь интернет)
    const val DEFAULT_BROKER_URL = "tcp://broker.hivemq.com:1883"

    // Локальный API сервер (должен быть запущен start.bat)
    const val DEFAULT_SERVER_URL = "http://10.0.2.2:8080"
    const val CLIENT_ID_PREFIX = "contexthub_android_"

    fun addZoneUrl(serverUrl: String, deviceId: String = DEVICE_ID): String =
        "$serverUrl/api/management_assistant/addZone/$deviceId"
}
