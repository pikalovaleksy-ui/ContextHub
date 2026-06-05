package com.example.project.ui.rooms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.project.data.repository.RadarRepository
import com.example.project.model.Room
import com.example.project.model.Target
import com.example.project.model.Zone
import kotlinx.coroutines.flow.map
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RoomWithZones(
    val room: Room,
    val zones: List<Zone>
)

@HiltViewModel
class RoomsViewModel @Inject constructor(
    private val radarRepository: RadarRepository
) : ViewModel() {

    val radarTargets: StateFlow<List<Target>> = radarRepository.radarData
        .map { it.targets }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val roomsWithZones: StateFlow<List<RoomWithZones>> =
        combine(
            radarRepository.allRooms(),
            radarRepository.allZones()
        ) { rooms, zones ->
            rooms.map { room ->
                RoomWithZones(
                    room = room,
                    zones = zones.filter { it.roomId == room.id }
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createRoom(name: String) {
        viewModelScope.launch {
            radarRepository.createRoom(name)
        }
    }

    fun deleteRoom(roomId: String) {
        viewModelScope.launch {
            radarRepository.deleteRoom(roomId)
        }
    }

    fun toggleZoneEnabled(zoneId: String, enabled: Boolean) {
        viewModelScope.launch {
            radarRepository.setZoneEnabled(zoneId, enabled)
        }
    }

    fun deleteZone(zoneId: String) {
        viewModelScope.launch {
            radarRepository.deleteZone(zoneId)
        }
    }
}
