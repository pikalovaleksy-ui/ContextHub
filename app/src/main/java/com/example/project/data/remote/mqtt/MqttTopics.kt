package com.example.project.data.remote.mqtt

object MqttTopics {
    const val LD2450_TOPIC = "assistants/1/ld2450"
    const val DEVICE_ID = "1"

    var DEFAULT_BROKER_URL = "tcp://broker.hivemq.com:1883"
    var DEFAULT_SERVER_URL = "http://10.0.2.2:8080"
    const val CLIENT_ID_PREFIX = "contexthub_android_"

    fun addZoneUrl(serverUrl: String, deviceId: String = DEVICE_ID): String =
        "$serverUrl/api/management_assistant/addZone/$deviceId"

    fun deleteZoneUrl(serverUrl: String, zoneName: String): String =
        "$serverUrl/api/zones/$zoneName"

    fun takePhotoUrl(serverUrl: String = DEFAULT_SERVER_URL, deviceId: String = DEVICE_ID): String =
        "$serverUrl/api/management_assistant/takePhoto/$deviceId"

    fun zoneBindingsUrl(serverUrl: String, zoneId: String): String =
        "$serverUrl/api/v1/zones/$zoneId/bindings"

    fun zoneEnabledUrl(serverUrl: String, zoneId: String): String =
        "$serverUrl/api/v1/zones/$zoneId/enabled"

    fun allZonesEnabledUrl(serverUrl: String, deviceId: String = DEVICE_ID): String =
        "$serverUrl/api/v1/devices/$deviceId/zones/enabled"

    fun socialTouchUrl(serverUrl: String, targetDeviceId: String): String =
        "$serverUrl/api/v1/social/touch/$targetDeviceId"

    fun socialInboxUrl(serverUrl: String, myDeviceId: String): String =
        "$serverUrl/api/v1/social/touch/inbox/$myDeviceId"

    fun socialEnabledUrl(serverUrl: String): String =
        "$serverUrl/api/v1/social/enabled"

    fun socialUserUrl(serverUrl: String): String =
        "$serverUrl/api/v1/social/user"
}
