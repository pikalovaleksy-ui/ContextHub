package com.example.project.ui.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.project.data.repository.SocialRepository
import com.example.project.model.Friend
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SocialUiState(
    val touchReceived: String? = null,
    val touchSent: Boolean = false
)

@HiltViewModel
class SocialViewModel @Inject constructor(
    private val socialRepository: SocialRepository
) : ViewModel() {

    val friends = socialRepository.getAllFriends()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(SocialUiState())
    val uiState: StateFlow<SocialUiState> = _uiState

    init {
        viewModelScope.launch {
            socialRepository.touchReceived.collect { event ->
                if (event != null) {
                    _uiState.value = SocialUiState(
                        touchReceived = event.friendName
                    )
                }
            }
        }
    }

    fun addFriend(name: String, deviceId: String) {
        viewModelScope.launch {
            socialRepository.addFriend(name, deviceId)
        }
    }

    fun removeFriend(friendId: String) {
        viewModelScope.launch {
            socialRepository.removeFriend(friendId)
        }
    }

    fun sendTouch(friend: Friend) {
        val name = "Я"
        socialRepository.sendTouch(friend.deviceId, name)
        _uiState.value = SocialUiState(touchSent = true)
    }

    fun clearTouchReceived() {
        _uiState.value = SocialUiState()
    }

    fun getOwnDeviceId(): String = socialRepository.getOwnDeviceId()

    fun loadMockFriends() {
        viewModelScope.launch {
            socialRepository.getMockFriends().forEach { friend ->
                socialRepository.addFriend(friend.name, friend.deviceId)
            }
        }
    }
}
