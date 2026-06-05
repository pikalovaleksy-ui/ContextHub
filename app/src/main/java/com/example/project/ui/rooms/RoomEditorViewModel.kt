package com.example.project.ui.rooms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.project.data.repository.AddZoneResult
import com.example.project.data.repository.RadarRepository
import com.example.project.data.remote.mqtt.MqttTopics
import com.example.project.model.Vertex
import com.example.project.model.Zone
import com.example.project.model.ZoneColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RoomEditorUiState(
    val roomId: String = "",
    val roomName: String = "",
    val editingVertices: List<Vertex> = emptyList(),
    val newZoneName: String = "",
    val canSave: Boolean = false,
    val statusText: String = "Тапните по карте для добавления вершины",
    val mqttConnected: Boolean = false,
    val targetLabels: Map<Int, String> = emptyMap(),
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val saveError: String? = null,
    val selectedZoneId: String? = null,
    val newZoneColor: Int = 0xFF6750A4.toInt()
)

@HiltViewModel
class RoomEditorViewModel @Inject constructor(
    private val radarRepository: RadarRepository
) : ViewModel() {

    val radarData = radarRepository.radarData

    val roomZones: StateFlow<List<Zone>> = radarRepository.allZones()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(RoomEditorUiState())
    val uiState: StateFlow<RoomEditorUiState> = _uiState

    fun init(roomId: String, roomName: String) {
        _uiState.value = _uiState.value.copy(roomId = roomId, roomName = roomName)
    }

    init {
        viewModelScope.launch {
            radarRepository.addZoneResult.collect { result ->
                val current = _uiState.value
                when (result) {
                    is AddZoneResult.Sending -> {
                        _uiState.value = current.copy(isSaving = true, saveError = null)
                    }
                    is AddZoneResult.Success -> {
                        _uiState.value = current.copy(
                            isSaving = false,
                            saveSuccess = true,
                            statusText = "Зона сохранена!",
                            editingVertices = emptyList(),
                            newZoneName = ""
                        )
                        radarRepository.clearAddZoneResult()
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
                _uiState.value = _uiState.value.copy(mqttConnected = connected)
            }
        }
        viewModelScope.launch {
            roomZones.collect { zones ->
                val usedColors = zones.filter { it.roomId == _uiState.value.roomId }
                    .map { it.color }.toSet()
                val nextColor = ZoneColors.palette.firstOrNull { it !in usedColors }
                    ?: ZoneColors.palette.last()
                _uiState.value = _uiState.value.copy(newZoneColor = nextColor)
            }
        }
    }

    fun setNewZoneName(name: String) {
        _uiState.value = _uiState.value.copy(newZoneName = name)
        updateCanSave()
    }

    fun addVertexFromTap(vertex: Vertex) {
        val current = _uiState.value
        _uiState.value = current.copy(editingVertices = current.editingVertices + vertex)
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
            val v = _uiState.value.editingVertices.lastOrNull()
            if (v != null) {
                kotlin.math.sqrt(
                    ((it.x - v.x) * (it.x - v.x) + (it.y - v.y) * (it.y - v.y)).toFloat()
                )
            } else 0f
        }

        if (nearest != null) {
            val current = _uiState.value
            _uiState.value = current.copy(
                editingVertices = current.editingVertices + Vertex(nearest.x, nearest.y),
                statusText = "Вершина добавлена: (${nearest.x}, ${nearest.y})"
            )
            updateTargetLabels()
            updateCanSave()
        }
    }

    fun undoVertex() {
        val current = _uiState.value
        if (current.editingVertices.isNotEmpty()) {
            _uiState.value = current.copy(editingVertices = current.editingVertices.dropLast(1))
            updateCanSave()
            updateStatus()
        }
    }

    fun clearVertices() {
        _uiState.value = _uiState.value.copy(
            editingVertices = emptyList(),
            statusText = "Вершины очищены"
        )
        updateCanSave()
    }

    fun selectZone(zoneId: String?) {
        _uiState.value = _uiState.value.copy(selectedZoneId = zoneId)
    }

    fun deleteSelectedZone() {
        val zoneId = _uiState.value.selectedZoneId ?: return
        viewModelScope.launch {
            radarRepository.deleteZone(zoneId)
            _uiState.value = _uiState.value.copy(
                selectedZoneId = null,
                statusText = "Зона удалена"
            )
        }
    }

    fun saveNewZone() {
        val current = _uiState.value
        val name = current.newZoneName.trim()

        if (name.isBlank()) {
            _uiState.value = current.copy(
                saveError = "Введите название зоны",
                statusText = "Ошибка: введите название зоны"
            )
            return
        }
        if (name.length > 30) {
            _uiState.value = current.copy(
                saveError = "Максимум 30 символов",
                statusText = "Ошибка: максимум 30 символов"
            )
            return
        }
        if (!name.matches(Regex("^[a-zA-Zа-яА-ЯёЁ0-9_]+$"))) {
            _uiState.value = current.copy(
                saveError = "Недопустимые символы в названии",
                statusText = "Ошибка: недопустимые символы"
            )
            return
        }
        if (current.editingVertices.size < 3) {
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
                vertices = current.editingVertices,
                roomId = current.roomId
            )
        }
    }

    private fun updateCanSave() {
        val state = _uiState.value
        _uiState.value = state.copy(
            canSave = state.editingVertices.size >= 3 && state.newZoneName.isNotBlank()
        )
    }

    private fun updateStatus() {
        val count = _uiState.value.editingVertices.size
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
            val idx = _uiState.value.editingVertices.indexOfFirst { v ->
                kotlin.math.abs(v.x - t.x) < 50 && kotlin.math.abs(v.y - t.y) < 50
            }
            labels[t.id] = if (idx >= 0) "V${idx + 1}" else "T${t.id}"
        }
        _uiState.value = _uiState.value.copy(targetLabels = labels)
    }
}
