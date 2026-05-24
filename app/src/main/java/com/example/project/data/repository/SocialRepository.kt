package com.example.project.data.repository

import com.example.project.data.local.dao.FriendDao
import com.example.project.data.local.entity.toDomain
import com.example.project.data.local.entity.toEntity
import com.example.project.model.Friend
import com.example.project.model.FriendStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SocialRepository @Inject constructor(
    private val friendDao: FriendDao
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

    fun sendTouch(friendDeviceId: String, senderName: String) {
        _touchReceived.value = TouchEvent(senderName, friendDeviceId)
    }

    fun onTouchReceived(friendName: String, friendDeviceId: String) {
        _touchReceived.value = TouchEvent(friendName, friendDeviceId)
    }

    fun getOwnDeviceId(): String = "device_${UUID.randomUUID().toString().take(6)}"

    fun getMockFriends(): List<Friend> = listOf(
        Friend("friend_1", "Алексей", "device_abc123", FriendStatus.ONLINE),
        Friend("friend_2", "Мария", "device_def456", FriendStatus.ONLINE),
        Friend("friend_3", "Павел", "device_ghi789", FriendStatus.OFFLINE)
    )
}

data class TouchEvent(
    val friendName: String,
    val friendDeviceId: String
)
