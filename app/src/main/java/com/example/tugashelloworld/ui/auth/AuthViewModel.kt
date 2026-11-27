package com.example.tugashelloworld.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tugashelloworld.data.AuthRepository
import com.example.tugashelloworld.util.ErrorMapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface AuthEvent {
    object Success : AuthEvent
    object Registered : AuthEvent
    object PasswordReset : AuthEvent
    data class Error(val message: String) : AuthEvent
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AuthRepository(application)

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _events = Channel<AuthEvent>(Channel.BUFFERED)
    val events: Flow<AuthEvent> = _events.receiveAsFlow()

    fun login(email: String, password: String) = execute {
        repository.login(email, password)
    }

    fun register(name: String, email: String, password: String) =
        execute(AuthEvent.Registered) {
            repository.register(name, email, password)
        }

    fun resetPassword(email: String, name: String, newPassword: String) =
        execute(AuthEvent.PasswordReset) {
            repository.resetPassword(email, name, newPassword)
        }

    /**
     * Menjalankan autentikasi dengan state loading
     * dan penanganan error terpusat.
     */
    private fun execute(
        success: AuthEvent = AuthEvent.Success,
        block: suspend () -> Unit
    ) {
        if (_loading.value) return

        val context = getApplication<Application>()

        viewModelScope.launch {
            _loading.value = true

            try {
                block()
                _events.send(success)

            } catch (e: CancellationException) {
                throw e

            } catch (e: Exception) {
                _events.send(
                    AuthEvent.Error(ErrorMapper.message(context, e))
                )

            } finally {
                _loading.value = false
            }
        }
    }
}
