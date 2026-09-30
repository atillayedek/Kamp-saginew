package com.kampusagi.android.presentation.group

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.GroupKind
import com.kampusagi.android.domain.model.GroupSummary
import com.kampusagi.android.domain.repository.GroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

sealed interface GroupListState {
    data object Loading : GroupListState
    data class Loaded(val groups: List<GroupSummary>) : GroupListState
    data class Failed(val error: AppError) : GroupListState
}

/** The person's study groups and channels, refreshed live when a message arrives in any of them. */
@HiltViewModel
class GroupsViewModel @Inject constructor(
    private val repository: GroupRepository,
) : ViewModel() {

    private val states = mutableStateMapOf<GroupKind, GroupListState>()

    var isRefreshing by mutableStateOf(false)
        private set

    fun state(kind: GroupKind): GroupListState = states[kind] ?: GroupListState.Loading

    val unreadTotal: Int
        get() = GroupKind.entries.sumOf { kind -> (states[kind] as? GroupListState.Loaded)?.groups?.sumOf { it.unreadCount } ?: 0 }

    init {
        load()
        viewModelScope.launch {
            repository.changes(groupId = null)
                .catch { Log.w(TAG, "Group realtime stopped; lists still load when opened", it) }
                .collect { load() }
        }
    }

    fun load() {
        GroupKind.entries.forEach { kind -> viewModelScope.launch { fetch(kind) } }
    }

    fun refresh() {
        if (isRefreshing) return
        isRefreshing = true
        viewModelScope.launch {
            GroupKind.entries.forEach { fetch(it) }
            isRefreshing = false
        }
    }

    private suspend fun fetch(kind: GroupKind) {
        states[kind] = when (val result = repository.myGroups(kind)) {
            is AppResult.Success -> GroupListState.Loaded(result.value)
            // A failed refresh keeps the list that is already on screen.
            is AppResult.Failure -> states[kind]?.takeIf { it is GroupListState.Loaded } ?: GroupListState.Failed(result.error)
        }
    }

    private companion object {
        const val TAG = "Groups"
    }
}
