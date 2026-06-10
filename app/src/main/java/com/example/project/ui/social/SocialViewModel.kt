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
    val touchSent: Boolean = false,
    val socialEnabled: Boolean = true
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
        socialRepository.startInboxPolling(viewModelScope)
        viewModelScope.launch {
            socialRepository.touchReceived.collect { event ->
                if (event != null) {
                    _uiState.value = _uiState.value.copy(touchReceived = event.friendName)
                }
            }
        }
        viewModelScope.launch {
            val enabled = socialRepository.checkSocialEnabled()
            _uiState.value = _uiState.value.copy(socialEnabled = enabled)
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
        if (!_uiState.value.socialEnabled) return
        viewModelScope.launch {
            val name = "Я"
            socialRepository.sendTouch(friend.deviceId, name)
            _uiState.value = _uiState.value.copy(touchSent = true)
        }
    }

    fun clearTouchReceived() {
        _uiState.value = SocialUiState(socialEnabled = _uiState.value.socialEnabled)
    }

    fun setSocialEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(socialEnabled = enabled)
        viewModelScope.launch {
            socialRepository.setSocialEnabled(enabled)
        }
    }

    fun getOwnDeviceId(): String = socialRepository.getOwnDeviceId()

    fun lookUpAndAddFriend(deviceId: String, name: String, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            val existing = socialRepository.findUserOnServer(deviceId)
            if (existing != null) {
                socialRepository.addFriend(existing.name, existing.deviceId)
                callback(true)
            } else {
                // fallback: add with whatever name user typed
                socialRepository.addFriend(name, deviceId)
                callback(true)
            }
        }
    }

    fun loadMockFriends() {
        viewModelScope.launch {
            socialRepository.getMockFriends().forEach { friend ->
                socialRepository.addFriend(friend.name, friend.deviceId)
            }
        }
    }
}
