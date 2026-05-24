package com.example.project.ui.zones

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.project.data.repository.RadarRepository
import com.example.project.model.Zone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ZonesUiState(
    val zones: List<Zone> = emptyList(),
    val isLoading: Boolean = true,
    val triggeredZone: String? = null
)

@HiltViewModel
class ZonesViewModel @Inject constructor(
    private val radarRepository: RadarRepository
) : ViewModel() {

    val zones = radarRepository.allZones()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _triggeredZone = MutableStateFlow<String?>(null)
    val triggeredZone: StateFlow<String?> = _triggeredZone

    fun deleteZone(zoneId: String) {
        viewModelScope.launch {
            radarRepository.deleteZone(zoneId)
        }
    }

    fun toggleZoneEnabled(zoneId: String, enabled: Boolean) {
        viewModelScope.launch {
            radarRepository.setZoneEnabled(zoneId, enabled)
        }
    }

    fun clearTrigger() {
        _triggeredZone.value = null
    }
}
