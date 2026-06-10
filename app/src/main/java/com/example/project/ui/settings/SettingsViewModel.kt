package com.example.project.ui.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.project.data.repository.AuthRepository
import com.example.project.data.repository.RadarRepository
import com.example.project.data.repository.SocialRepository
import com.example.project.data.remote.mqtt.MqttManager
import com.example.project.data.remote.mqtt.MqttTopics
import com.example.project.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val user: User? = null,
    val brokerUrl: String = MqttTopics.DEFAULT_BROKER_URL,
    val serverUrl: String = MqttTopics.DEFAULT_SERVER_URL,
    val mqttConnected: Boolean = false,
    val isLoggedOut: Boolean = false,
    val allZonesEnabled: Boolean = false,
    val socialTouchEnabled: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val radarRepository: RadarRepository,
    private val socialRepository: SocialRepository,
    private val mqttManager: MqttManager
) : ViewModel() {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun normalizeBrokerUrl(url: String): String {
        val trimmed = url.trim()
        if (!trimmed.contains("://")) return "tcp://$trimmed"
        return trimmed
    }

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        val savedBrokerUrl = prefs.getString(KEY_BROKER_URL, MqttTopics.DEFAULT_BROKER_URL) ?: MqttTopics.DEFAULT_BROKER_URL
        val normalized = normalizeBrokerUrl(savedBrokerUrl)
        if (normalized != savedBrokerUrl) {
            prefs.edit().putString(KEY_BROKER_URL, normalized).apply()
        }
        _uiState.value = SettingsUiState(
            user = authRepository.getCurrentUser(),
            brokerUrl = normalized,
            serverUrl = prefs.getString(KEY_SERVER_URL, MqttTopics.DEFAULT_SERVER_URL) ?: MqttTopics.DEFAULT_SERVER_URL,
            mqttConnected = mqttManager.connectionState.value
        )
        viewModelScope.launch {
            mqttManager.connectionState.collect { connected ->
                _uiState.value = _uiState.value.copy(mqttConnected = connected)
            }
        }
    }

    fun saveBrokerUrl(url: String) {
        val normalized = normalizeBrokerUrl(url)
        prefs.edit().putString(KEY_BROKER_URL, normalized).apply()
        MqttTopics.DEFAULT_BROKER_URL = normalized
        _uiState.value = _uiState.value.copy(brokerUrl = normalized)
    }

    fun saveServerUrl(url: String) {
        prefs.edit().putString(KEY_SERVER_URL, url).apply()
        MqttTopics.DEFAULT_SERVER_URL = url
        _uiState.value = _uiState.value.copy(serverUrl = url)
    }

    fun connectMqtt(brokerUrl: String) {
        saveBrokerUrl(brokerUrl)
        mqttManager.connect(brokerUrl)
    }

    fun disconnectMqtt() {
        mqttManager.disconnect()
        _uiState.value = _uiState.value.copy(mqttConnected = false)
    }

    fun updateProfile(name: String, email: String) {
        authRepository.updateProfile(name, email)
        _uiState.value = _uiState.value.copy(user = authRepository.getCurrentUser())
    }

    fun logout() {
        authRepository.logout()
        _uiState.value = SettingsUiState(isLoggedOut = true)
    }

    fun toggleAllZones(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(allZonesEnabled = enabled)
        viewModelScope.launch {
            radarRepository.setAllZonesEnabled(enabled)
        }
    }

    fun setSocialTouchEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(socialTouchEnabled = enabled)
        prefs.edit().putBoolean(KEY_SOCIAL_ENABLED, enabled).apply()
        viewModelScope.launch {
            socialRepository.setSocialEnabled(enabled)
        }
    }

    companion object {
        private const val PREFS_NAME = "contexthub_settings"
        private const val KEY_BROKER_URL = "broker_url"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_SOCIAL_ENABLED = "social_enabled"
    }
}
