package com.example.project.ui.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.project.data.repository.AuthRepository
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
    val isLoggedOut: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val mqttManager: MqttManager
) : ViewModel() {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        _uiState.value = SettingsUiState(
            user = authRepository.getCurrentUser(),
            brokerUrl = prefs.getString(KEY_BROKER_URL, MqttTopics.DEFAULT_BROKER_URL) ?: MqttTopics.DEFAULT_BROKER_URL,
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
        prefs.edit().putString(KEY_BROKER_URL, url).apply()
        _uiState.value = _uiState.value.copy(brokerUrl = url)
    }

    fun saveServerUrl(url: String) {
        prefs.edit().putString(KEY_SERVER_URL, url).apply()
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

    companion object {
        private const val PREFS_NAME = "contexthub_settings"
        private const val KEY_BROKER_URL = "broker_url"
        private const val KEY_SERVER_URL = "server_url"
    }
}
