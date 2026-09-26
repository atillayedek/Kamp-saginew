package com.kampusagi.android.presentation.notification

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppNotification
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.repository.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

sealed interface NotificationsState {
    data object Loading : NotificationsState
    data class Loaded(val items: List<AppNotification>) : NotificationsState
    data class Failed(val error: AppError) : NotificationsState
}

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val repository: NotificationRepository,
) : ViewModel() {

    var state by mutableStateOf<NotificationsState>(NotificationsState.Loading)
        private set

    val unreadCount: Int
        get() = (state as? NotificationsState.Loaded)?.items?.count { !it.read } ?: 0

    init {
        load()
        viewModelScope.launch {
            repository.changes()
                .catch { Log.w(TAG, "Notification realtime stopped; the list still loads on open", it) }
                .collect { load() }
        }
    }

    fun load() {
        viewModelScope.launch {
            val result = repository.notifications()
            state = when (result) {
                is AppResult.Success -> NotificationsState.Loaded(result.value)
                is AppResult.Failure -> if (state is NotificationsState.Loaded) state else NotificationsState.Failed(result.error)
            }
        }
    }

    fun markAllRead() = markRead(ids = null)

    fun onOpened(notification: AppNotification) {
        if (!notification.read) markRead(listOf(notification.id))
    }

    private fun markRead(ids: List<String>?) {
        viewModelScope.launch {
            when (val result = repository.markRead(ids)) {
                is AppResult.Success -> load()
                is AppResult.Failure -> Log.w(TAG, "Could not mark notifications read: ${result.error}")
            }
        }
    }

    private companion object {
        const val TAG = "Notifications"
    }
}
