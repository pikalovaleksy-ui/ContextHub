package com.example.project.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.project.data.repository.DeviceRepository
import com.example.project.model.Device
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val isError: Boolean = false,
    val error: String? = null,
    val device: Device? = null
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState

    fun connect() {
        _uiState.value = OnboardingUiState(isConnecting = true)

        viewModelScope.launch {
            delay(2000)

            val result = deviceRepository.connectToDevice("192.168.1.100")
            result.fold(
                onSuccess = { device ->
                    _uiState.value = OnboardingUiState(
                        isConnected = true,
                        device = device
                    )
                },
                onFailure = { error ->
                    _uiState.value = OnboardingUiState(
                        isError = true,
                        error = error.message
                    )
                }
            )
        }
    }
}
