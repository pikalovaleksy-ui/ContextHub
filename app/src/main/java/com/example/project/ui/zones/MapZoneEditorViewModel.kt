package com.example.project.ui.zones

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.project.data.repository.AddZoneResult
import com.example.project.data.repository.RadarRepository
import com.example.project.data.remote.mqtt.MqttTopics
import com.example.project.model.Vertex
import com.example.project.model.Zone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MapEditorUiState(
    val zoneName: String = "",
    val vertices: List<Vertex> = emptyList(),
    val canSave: Boolean = false,
    val statusText: String = "Тапните по карте для добавления вершины",
    val mqttConnected: Boolean = false,
    val targetLabels: Map<Int, String> = emptyMap(),
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val saveError: String? = null
)

@HiltViewModel
class MapZoneEditorViewModel @Inject constructor(
    private val radarRepository: RadarRepository
) : ViewModel() {

    val radarData = radarRepository.radarData
    val savedZones: StateFlow<List<Zone>> = radarRepository.allZones()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(MapEditorUiState())
    val uiState: StateFlow<MapEditorUiState> = _uiState

    init {
        viewModelScope.launch {
            radarRepository.addZoneResult.collect { result ->
                val current = _uiState.value
                when (result) {
                    is AddZoneResult.Sending -> {
                        _uiState.value = current.copy(
                            isSaving = true,
                            saveError = null
                        )
                    }
                    is AddZoneResult.Success -> {
                        _uiState.value = current.copy(
                            isSaving = false,
                            saveSuccess = true,
                            statusText = "Зона сохранена!"
                        )
                    }
                    is AddZoneResult.Error -> {
                        _uiState.value = current.copy(
                            isSaving = false,
                            saveError = result.message,
                            statusText = "Ошибка: ${result.message}"
                        )
                    }
                    null -> {}
                }
            }
        }
        viewModelScope.launch {
            radarRepository.mqttConnectionState.collect { connected ->
                android.util.Log.d("RadarDebug", "MQTT connection state: $connected")
                _uiState.value = _uiState.value.copy(mqttConnected = connected)
            }
        }
    }

    fun connectToRadar() {
        radarRepository.subscribeToRadar()
        updateStatus()
    }

    fun disconnectFromRadar() {
        radarRepository.unsubscribeFromRadar()
    }

    override fun onCleared() {
        super.onCleared()
        disconnectFromRadar()
    }

    fun setZoneName(name: String) {
        _uiState.value = _uiState.value.copy(zoneName = name)
        updateCanSave()
    }

    fun addVertexFromTap(vertex: Vertex) {
        val current = _uiState.value
        _uiState.value = current.copy(
            vertices = current.vertices + vertex
        )
        updateCanSave()
        updateStatus()
    }

    fun markCurrentPosition() {
        val targets = radarData.value.targets
        if (targets.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                statusText = "Нет целей для отметки позиции"
            )
            return
        }

        val nearest = targets.minByOrNull {
            val v = _uiState.value.vertices.lastOrNull()
            if (v != null) {
                kotlin.math.sqrt(
                    ((it.x - v.x) * (it.x - v.x) + (it.y - v.y) * (it.y - v.y)).toFloat()
                )
            } else 0f
        }

        if (nearest != null) {
            val current = _uiState.value
            _uiState.value = current.copy(
                vertices = current.vertices + Vertex(nearest.x, nearest.y),
                statusText = "Вершина добавлена: (${nearest.x}, ${nearest.y})"
            )
            updateTargetLabels()
            updateCanSave()
        }
    }

    fun undoVertex() {
        val current = _uiState.value
        if (current.vertices.isNotEmpty()) {
            _uiState.value = current.copy(
                vertices = current.vertices.dropLast(1)
            )
            updateCanSave()
            updateStatus()
        }
    }

    fun clearVertices() {
        _uiState.value = _uiState.value.copy(
            vertices = emptyList(),
            statusText = "Вершины очищены"
        )
        updateCanSave()
    }

    fun saveZone() {
        val current = _uiState.value
        val name = current.zoneName.trim()

        if (name.isBlank()) {
            _uiState.value = current.copy(
                saveError = "Введите название зоны",
                statusText = "Ошибка: введите название зоны"
            )
            return
        }
        if (current.vertices.size < 3) {
            _uiState.value = current.copy(
                saveError = "Минимум 3 вершины",
                statusText = "Ошибка: минимум 3 вершины"
            )
            return
        }

        viewModelScope.launch {
            radarRepository.addZoneToServer(
                serverUrl = MqttTopics.DEFAULT_SERVER_URL,
                zoneName = name,
                vertices = current.vertices
            )
        }
    }

    private fun updateCanSave() {
        val state = _uiState.value
        _uiState.value = state.copy(
            canSave = state.vertices.size >= 3 && state.zoneName.isNotBlank()
        )
    }

    private fun updateStatus() {
        val count = _uiState.value.vertices.size
        val text = if (count == 0) {
            "Тапните по карте или нажмите Mark Position"
        } else {
            "Добавлено вершин: $count. ${if (count < 3) "Нужно ещё ${3 - count}" else "Можно сохранить!"}"
        }
        _uiState.value = _uiState.value.copy(statusText = text)
    }

    private fun updateTargetLabels() {
        val labels = mutableMapOf<Int, String>()
        radarData.value.targets.forEach { t ->
            val idx = _uiState.value.vertices.indexOfFirst { v ->
                kotlin.math.abs(v.x - t.x) < 50 && kotlin.math.abs(v.y - t.y) < 50
            }
            labels[t.id] = if (idx >= 0) "V${idx + 1}" else "T${t.id}"
        }
        _uiState.value = _uiState.value.copy(targetLabels = labels)
    }
}
