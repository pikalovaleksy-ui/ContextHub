package com.example.project.ui.smartthings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.project.data.remote.api.SmartThingsApi
import com.example.project.data.remote.mqtt.MqttTopics
import com.example.project.data.repository.RadarRepository
import com.example.project.model.SmartThingsBinding
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeviceBindingState(
    val deviceId: String,
    val deviceName: String,
    val capabilities: List<String>,
    val selected: Boolean = false,
    val action: String = "on",
    val extraParams: Map<String, String> = emptyMap()
)

sealed class SmartThingsUiState {
    object Loading : SmartThingsUiState()
    data class Ready(
        val devices: List<DeviceBindingState>,
        val zoneName: String
    ) : SmartThingsUiState()
    data class Error(val message: String) : SmartThingsUiState()
    object Saving : SmartThingsUiState()
    object Saved : SmartThingsUiState()
}

@HiltViewModel
class SmartThingsSetupViewModel @Inject constructor(
    private val smartThingsApi: SmartThingsApi,
    private val radarRepository: RadarRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SmartThingsUiState>(SmartThingsUiState.Loading)
    val uiState: StateFlow<SmartThingsUiState> = _uiState

    private var zoneId: String = ""

    fun init(zoneId: String, roomId: String) {
        this.zoneId = zoneId
        viewModelScope.launch {
            val zone = radarRepository.getZoneById(zoneId)
            if (zone == null) {
                _uiState.value = SmartThingsUiState.Error("Зона не найдена")
                return@launch
            }
            val existingBindings = zone.smartThingsBindings.associateBy { it.deviceId }
            try {
                val response = smartThingsApi.getDevices()
                if (response.isSuccessful) {
                    val devices = response.body() ?: emptyList()
                    val bindingStates = devices.map { dto ->
                        val existing = existingBindings[dto.deviceId]
                        DeviceBindingState(
                            deviceId = dto.deviceId,
                            deviceName = dto.name,
                            capabilities = dto.capabilities,
                            selected = existing != null,
                            action = existing?.action ?: "on",
                            extraParams = existing?.extraParams ?: emptyMap()
                        )
                    }
                    _uiState.value = SmartThingsUiState.Ready(
                        devices = bindingStates,
                        zoneName = zone.name
                    )
                } else {
                    _uiState.value = SmartThingsUiState.Error("Ошибка загрузки устройств")
                }
            } catch (e: Exception) {
                _uiState.value = SmartThingsUiState.Error(
                    e.message ?: "Ошибка сети"
                )
            }
        }
    }

    fun toggleDevice(deviceId: String) {
        val state = _uiState.value as? SmartThingsUiState.Ready ?: return
        val updated = state.devices.map { d ->
            if (d.deviceId == deviceId) d.copy(selected = !d.selected) else d
        }
        _uiState.value = state.copy(devices = updated)
    }

    fun setAction(deviceId: String, action: String) {
        val state = _uiState.value as? SmartThingsUiState.Ready ?: return
        val updated = state.devices.map { d ->
            if (d.deviceId == deviceId) d.copy(action = action) else d
        }
        _uiState.value = state.copy(devices = updated)
    }

    fun setExtraParam(deviceId: String, key: String, value: String) {
        val state = _uiState.value as? SmartThingsUiState.Ready ?: return
        val updated = state.devices.map { d ->
            if (d.deviceId == deviceId) d.copy(
                extraParams = d.extraParams + (key to value)
            ) else d
        }
        _uiState.value = state.copy(devices = updated)
    }

    fun save() {
        val state = _uiState.value as? SmartThingsUiState.Ready ?: return
        val bindings = state.devices
            .filter { it.selected }
            .map { SmartThingsBinding(it.deviceId, it.action, it.extraParams) }

        _uiState.value = SmartThingsUiState.Saving
        viewModelScope.launch {
            radarRepository.saveSmartThingsBindings(zoneId, bindings)
            radarRepository.saveBindingsToServer(
                serverUrl = MqttTopics.DEFAULT_SERVER_URL,
                zoneId = zoneId,
                bindings = bindings
            )
            _uiState.value = SmartThingsUiState.Saved
        }
    }
}