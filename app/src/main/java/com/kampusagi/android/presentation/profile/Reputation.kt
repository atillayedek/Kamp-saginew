package com.kampusagi.android.presentation.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.icon.AppIcons
import com.kampusagi.android.core.designsystem.theme.Spacing
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.Badge
import com.kampusagi.android.domain.model.Reputation
import com.kampusagi.android.domain.repository.ReputationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class ReputationViewModel @Inject constructor(
    private val repository: ReputationRepository,
) : ViewModel() {

    /** Null until loaded; a failure leaves the section out rather than showing made-up numbers. */
    var reputation by mutableStateOf<Reputation?>(null)
        private set

    private var loadedFor: String? = null

    fun load(userId: String) {
        if (loadedFor == userId) return
        loadedFor = userId
        viewModelScope.launch {
            when (val result = repository.reputation(userId)) {
                is AppResult.Success -> reputation = result.value
                is AppResult.Failure -> {
                    loadedFor = null
                    android.util.Log.w("Reputation", "Badges not loaded: ${result.error}")
                }
            }
        }
    }
}

/** Points and badges the server computed from the person's real activity. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReputationSection(userId: String, modifier: Modifier = Modifier) {
    val viewModel: ReputationViewModel = hiltViewModel(key = "reputation-$userId")
    LaunchedEffect(userId) { viewModel.load(userId) }
    val reputation = viewModel.reputation ?: return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.MilitaryTech, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(
                stringResource(R.string.reputation_points, reputation.points),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }
        if (reputation.badges.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                reputation.badges.forEach { BadgeChip(it) }
            }
        }
    }
}

@Composable
private fun BadgeChip(badge: Badge) {
    Surface(
        color = if (badge == Badge.PREMIUM) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (badge == Badge.PREMIUM) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 4.dp)) {
            Icon(badge.icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(stringResource(badge.labelRes()), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = Spacing.xs))
        }
    }
}

fun Badge.labelRes(): Int = when (this) {
    Badge.PREMIUM -> R.string.badge_premium
    Badge.CHANNEL_OWNER -> R.string.badge_channel_owner
    Badge.HELPFUL -> R.string.badge_helpful
    Badge.NOTE_SHARER -> R.string.badge_note_sharer
    Badge.POPULAR -> R.string.badge_popular
    Badge.EVENT_ORGANIZER -> R.string.badge_event_organizer
    Badge.ACTIVE_MEMBER -> R.string.badge_active_member
}

private val Badge.icon: ImageVector
    @Composable get() = when (this) {
        Badge.PREMIUM -> AppIcons.WorkspacePremium
        Badge.CHANNEL_OWNER -> AppIcons.Campaign
        Badge.HELPFUL -> AppIcons.VolunteerActivism
        Badge.NOTE_SHARER -> AppIcons.MenuBook
        Badge.POPULAR -> AppIcons.LocalFireDepartment
        Badge.EVENT_ORGANIZER -> AppIcons.Event
        Badge.ACTIVE_MEMBER -> AppIcons.Stars
    }
