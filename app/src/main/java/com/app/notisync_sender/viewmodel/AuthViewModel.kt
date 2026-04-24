// ============================================================
// FILE: app/src/main/java/com/app/notisync_sender/viewmodel/AuthViewModel.kt
// Purpose: Manages authentication state and login/register operations
// ============================================================

package com.app.notisync_sender.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.notisync_sender.data.repository.AuthRepository
import com.app.notisync_sender.data.repository.AuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.authState

    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _isLoginMode = MutableStateFlow(true)
    val isLoginMode: StateFlow<Boolean> = _isLoginMode.asStateFlow()

    fun onEmailChange(value: String) {
        _email.value = value
    }

    fun onPasswordChange(value: String) {
        _password.value = value
    }

    fun toggleMode() {
        _isLoginMode.value = !_isLoginMode.value
        authRepository.clearError()
    }

    fun submit() {
        val emailValue = _email.value.trim()
        val passwordValue = _password.value

        if (emailValue.isEmpty() || passwordValue.isEmpty()) {
            return
        }

        viewModelScope.launch {
            if (_isLoginMode.value) {
                authRepository.login(emailValue, passwordValue)
            } else {
                authRepository.register(emailValue, passwordValue)
            }
        }
    }

    fun logout() {
        authRepository.logout()
        _email.value = ""
        _password.value = ""
    }

    fun clearError() {
        authRepository.clearError()
    }
}