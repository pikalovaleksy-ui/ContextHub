package com.example.project.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.project.data.repository.AuthRepository
import com.example.project.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false,
    val user: User? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState

    fun login(email: String, password: String) {
        if (email.isBlank()) {
            _uiState.value = AuthUiState(error = "Введите email")
            return
        }
        if (password.isBlank()) {
            _uiState.value = AuthUiState(error = "Введите пароль")
            return
        }

        _uiState.value = AuthUiState(isLoading = true)

        viewModelScope.launch {
            val result = kotlinx.coroutines.Dispatchers.IO.let {
                authRepository.login(email, password)
            }
            result.fold(
                onSuccess = { user ->
                    _uiState.value = AuthUiState(isSuccess = true, user = user)
                },
                onFailure = { error ->
                    _uiState.value = AuthUiState(error = error.message ?: "Ошибка входа")
                }
            )
        }
    }

    fun register(name: String, email: String, password: String, confirmPassword: String) {
        if (name.isBlank() || email.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState(error = "Заполните все поля")
            return
        }
        if (password != confirmPassword) {
            _uiState.value = AuthUiState(error = "Пароли не совпадают")
            return
        }
        if (password.length < 3) {
            _uiState.value = AuthUiState(error = "Пароль должен быть минимум 3 символа")
            return
        }

        _uiState.value = AuthUiState(isLoading = true)

        viewModelScope.launch {
            val result = authRepository.register(name, email, password)
            result.fold(
                onSuccess = { user ->
                    _uiState.value = AuthUiState(isSuccess = true, user = user)
                },
                onFailure = { error ->
                    _uiState.value = AuthUiState(error = error.message ?: "Ошибка регистрации")
                }
            )
        }
    }

    fun resetState() {
        _uiState.value = AuthUiState()
    }
}
