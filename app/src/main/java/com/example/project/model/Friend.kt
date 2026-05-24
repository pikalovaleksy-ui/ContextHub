package com.example.project.model

data class Friend(
    val id: String,
    val name: String,
    val deviceId: String,
    val status: FriendStatus = FriendStatus.OFFLINE,
    val lastTouchReceived: Long = 0L
)

enum class FriendStatus {
    ONLINE, OFFLINE
}
