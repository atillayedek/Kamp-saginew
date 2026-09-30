package com.kampusagi.android.presentation.announcement

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.icon.AppIcons
import com.kampusagi.android.core.designsystem.theme.Spacing
import com.kampusagi.android.domain.model.Announcement
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.repository.AnnouncementRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/** Announcements for the signed-in student, updated live, and the daily activity mark. */
@HiltViewModel
class AnnouncementsViewModel @Inject constructor(
    private val repository: AnnouncementRepository,
) : ViewModel() {

    var announcements by mutableStateOf<List<Announcement>>(emptyList())
        private set

    init {
        load()
        viewModelScope.launch {
            repository.changes()
                .catch { Log.w(TAG, "Announcement realtime stopped; they still load when the app opens", it) }
                .collect { load() }
        }
    }

    fun load() {
        viewModelScope.launch {
            when (val result = repository.active()) {
                is AppResult.Success -> announcements = result.value
                // Nothing to show is the safe outcome; the feed itself reports connection problems.
                is AppResult.Failure -> Log.w(TAG, "Announcements not loaded: ${result.error}")
            }
        }
    }

    /** Hidden at once; shown again on the next load if the server did not record it. */
    fun dismiss(announcement: Announcement) {
        announcements = announcements.filterNot { it.id == announcement.id }
        viewModelScope.launch {
            val result = repository.dismiss(announcement.id)
            if (result is AppResult.Failure) Log.w(TAG, "Dismissal not saved: ${result.error}")
        }
    }

    /** Called when the app comes to the foreground. */
    fun onAppOpened() {
        viewModelScope.launch {
            val result = repository.touchActivity()
            if (result is AppResult.Failure) Log.w(TAG, "Activity not recorded: ${result.error}")
        }
        load()
    }

    private companion object {
        const val TAG = "Announcements"
    }
}

@Composable
fun AnnouncementBanners(announcements: List<Announcement>, onDismiss: (Announcement) -> Unit) {
    announcements.forEach { announcement ->
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs),
        ) {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(start = Spacing.md, top = Spacing.sm, bottom = Spacing.sm)) {
                Icon(AppIcons.Campaign, contentDescription = null, modifier = Modifier.padding(top = 2.dp))
                Column(modifier = Modifier.weight(1f).padding(horizontal = Spacing.sm)) {
                    Text(announcement.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(announcement.body, style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(onClick = { onDismiss(announcement) }) {
                    Icon(AppIcons.Close, contentDescription = stringResource(R.string.announcement_dismiss))
                }
            }
        }
    }
}
