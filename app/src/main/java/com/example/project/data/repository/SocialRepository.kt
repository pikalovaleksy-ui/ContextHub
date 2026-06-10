package com.example.project.data.repository

import com.example.project.data.local.dao.FriendDao
import com.example.project.data.local.entity.toDomain
import com.example.project.data.local.entity.toEntity
import com.example.project.data.remote.mqtt.MqttTopics
import com.example.project.model.Friend
import com.example.project.model.FriendStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SocialRepository @Inject constructor(
    private val friendDao: FriendDao,
    private val okHttpClient: OkHttpClient
) {
    private val _touchReceived = MutableStateFlow<TouchEvent?>(null)
    val touchReceived: Flow<TouchEvent?> = _touchReceived

    fun getAllFriends(): Flow<List<Friend>> =
        friendDao.getAllFriends().map { list -> list.map { it.toDomain() } }

    suspend fun addFriend(name: String, deviceId: String): Friend {
        val friend = Friend(
            id = UUID.randomUUID().toString(),
            name = name,
            deviceId = deviceId,
            status = FriendStatus.ONLINE
        )
        friendDao.insertFriend(friend.toEntity())
        return friend
    }

    suspend fun removeFriend(friendId: String) {
        friendDao.deleteFriendById(friendId)
    }

    private val _ownDeviceId = "device_${UUID.randomUUID().toString().take(6)}"
    private var inboxJob: Job? = null
    private var _socialEnabled = MutableStateFlow(true)
    val socialEnabled: Flow<Boolean> = _socialEnabled

    suspend fun sendTouch(friendDeviceId: String, senderName: String) {
        sendTouchToServer(friendDeviceId, senderName)
    }

    fun onTouchReceived(friendName: String, friendDeviceId: String) {
        _touchReceived.value = TouchEvent(friendName, friendDeviceId)
    }

    private suspend fun sendTouchToServer(targetDeviceId: String, senderName: String) {
        val json = """{"senderName":"$senderName","senderDeviceId":"$_ownDeviceId"}"""
        try {
            val url = MqttTopics.socialTouchUrl(MqttTopics.DEFAULT_SERVER_URL, targetDeviceId)
            val body = json.toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).post(body).build()
            withContext(Dispatchers.IO) {
                okHttpClient.newCall(request).execute()
            }
        } catch (_: Exception) {}
    }

    fun startInboxPolling(scope: CoroutineScope) {
        inboxJob?.cancel()
        inboxJob = scope.launch(Dispatchers.IO) {
            while (true) {
                try {
                    val url = MqttTopics.socialInboxUrl(MqttTopics.DEFAULT_SERVER_URL, _ownDeviceId)
                    val request = Request.Builder().url(url).get().build()
                    val response = okHttpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val events = parseInboxEvents(body)
                        for (event in events) {
                            _touchReceived.value = TouchEvent(
                                event["senderName"] ?: "Unknown",
                                event["senderDeviceId"] ?: ""
                            )
                        }
                    }
                } catch (_: Exception) {}
                delay(10_000)
            }
        }
    }

    fun stopInboxPolling() {
        inboxJob?.cancel()
        inboxJob = null
    }

    fun getOwnDeviceId(): String = _ownDeviceId

    suspend fun setSocialEnabled(enabled: Boolean) {
        _socialEnabled.value = enabled
        val json = """{"deviceId":"$_ownDeviceId","enabled":$enabled}"""
        try {
            val url = MqttTopics.socialEnabledUrl(MqttTopics.DEFAULT_SERVER_URL)
            val body = json.toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).post(body).build()
            withContext(Dispatchers.IO) {
                okHttpClient.newCall(request).execute()
            }
        } catch (_: Exception) {}
    }

    suspend fun checkSocialEnabled(): Boolean {
        return try {
            val url = "${MqttTopics.socialEnabledUrl(MqttTopics.DEFAULT_SERVER_URL)}/$_ownDeviceId"
            val request = Request.Builder().url(url).get().build()
            val response = withContext(Dispatchers.IO) {
                okHttpClient.newCall(request).execute()
            }
            if (response.isSuccessful) {
                val body = response.body?.string() ?: "{}"
                body.contains("\"enabled\":true")
            } else true
        } catch (_: Exception) { true }
    }

    suspend fun findUserOnServer(deviceId: String): Friend? {
        return try {
            val url = "${MqttTopics.socialUserUrl(MqttTopics.DEFAULT_SERVER_URL)}?deviceId=$deviceId"
            val request = Request.Builder().url(url).get().build()
            val response = withContext(Dispatchers.IO) {
                okHttpClient.newCall(request).execute()
            }
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return null
                val name = extractField(body, "name") ?: deviceId
                Friend(UUID.randomUUID().toString(), name, deviceId, FriendStatus.ONLINE)
            } else null
        } catch (_: Exception) { null }
    }

    private fun parseInboxEvents(json: String): List<Map<String, String>> {
        val result = mutableListOf<Map<String, String>>()
        try {
            val eventsStart = json.indexOf("\"events\":[")
            if (eventsStart < 0) return result
            val arrStart = json.indexOf('[', eventsStart)
            val arrEnd = json.lastIndexOf(']')
            if (arrStart < 0 || arrEnd < 0) return result
            val arr = json.substring(arrStart, arrEnd + 1)
            val entries = arr.split("},{")
            for (entry in entries) {
                val e = entry.trim(' ', '[', ']', '{', '}')
                val senderName = extractField(e, "senderName") ?: continue
                val senderDeviceId = extractField(e, "senderDeviceId") ?: continue
                result.add(mapOf("senderName" to senderName, "senderDeviceId" to senderDeviceId))
            }
        } catch (_: Exception) {}
        return result
    }

    private fun extractField(json: String, key: String): String? {
        val search = "\"$key\":\""
        val start = json.indexOf(search)
        if (start < 0) return null
        val valueStart = start + search.length
        val valueEnd = json.indexOf('"', valueStart)
        return if (valueEnd > valueStart) json.substring(valueStart, valueEnd) else null
    }

    fun getMockFriends(): List<Friend> = listOf(
        Friend("friend_1", "Алексей", "device_abc123", FriendStatus.ONLINE),
        Friend("friend_2", "Мария", "device_def456", FriendStatus.ONLINE),
        Friend("friend_3", "Павел", "device_ghi789", FriendStatus.OFFLINE),
        Friend("friend_mock_server", "Тестовый друг", "mock_friend_1", FriendStatus.ONLINE)
    )
}

data class TouchEvent(
    val friendName: String,
    val friendDeviceId: String
)
