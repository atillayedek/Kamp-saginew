package com.kampusagi.android.presentation.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.icon.AppIcons
import com.kampusagi.android.core.designsystem.theme.Spacing
import com.kampusagi.android.domain.model.Profile
import com.kampusagi.android.presentation.common.avatar.UserAvatar
import com.kampusagi.android.presentation.profile.ProfileStatsViewModel
import com.kampusagi.android.presentation.profile.StatsState

/** Instagram-style profile: photo and counts, name, bio, then the account menu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileTab(
    profile: Profile,
    isAdmin: Boolean,
    onOpenAdmin: () -> Unit,
    onOpenPremium: () -> Unit,
    onOpenSettings: () -> Unit,
    onEditProfile: () -> Unit,
    onOpenSaved: () -> Unit,
    onOpenMyPosts: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    statsViewModel: ProfileStatsViewModel = hiltViewModel(),
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    profile.username?.let { "@$it" } ?: profile.fullName.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            actions = {
                IconButton(onClick = onOpenSettings) {
                    Icon(AppIcons.Settings, contentDescription = stringResource(R.string.settings_title))
                }
            },
        )
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
            ) {
                UserAvatar(
                    profile.id,
                    profile.fullName,
                    profile.username,
                    size = 88.dp,
                    modifier = Modifier.clickable(onClick = onEditProfile),
                )
                Spacer(Modifier.width(Spacing.lg))
                val stats = (statsViewModel.state as? StatsState.Loaded)?.stats
                Stat(stats?.postCount, R.string.profile_stat_posts, Modifier.weight(1f))
                Stat(stats?.activeRequirementCount, R.string.profile_stat_requirements, Modifier.weight(1f))
                Stat(stats?.conversationCount, R.string.profile_stat_chats, Modifier.weight(1f))
            }
            Column(
                modifier = Modifier.padding(horizontal = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(profile.fullName.orEmpty(), style = MaterialTheme.typography.titleMedium)
                    Icon(
                        AppIcons.Verified,
                        contentDescription = stringResource(R.string.profile_verified),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = Spacing.xs).size(18.dp),
                    )
                }
                val school = listOfNotNull(profile.universityName, profile.department).joinToString(" · ")
                if (school.isNotEmpty()) {
                    Text(school, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (profile.bio.isNullOrBlank()) {
                    Text(
                        stringResource(R.string.profile_add_bio),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = onEditProfile).padding(vertical = Spacing.xs),
                    )
                } else {
                    Text(profile.bio, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Spacing.xs))
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            ) {
                OutlinedButton(onClick = onEditProfile, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.profile_edit))
                }
                OutlinedButton(onClick = onOpenPremium, modifier = Modifier.weight(1f)) {
                    Icon(AppIcons.WorkspacePremium, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text(stringResource(R.string.premium_title))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            MenuRow(AppIcons.GridView, stringResource(R.string.profile_my_posts), onClick = onOpenMyPosts)
            MenuRow(AppIcons.Bookmark, stringResource(R.string.saved_title), onClick = onOpenSaved)
            MenuRow(AppIcons.Mail, profile.email, onClick = null)
            MenuRow(AppIcons.Settings, stringResource(R.string.settings_title), onClick = onOpenSettings)
            if (isAdmin) MenuRow(AppIcons.AdminPanelSettings, stringResource(R.string.action_open_admin), onClick = onOpenAdmin)
            MenuRow(AppIcons.Logout, stringResource(R.string.action_sign_out), onClick = onSignOut, danger = true)
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

@Composable
private fun Stat(value: Int?, label: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value?.toString() ?: "–",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(label),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MenuRow(icon: ImageVector, text: String, onClick: (() -> Unit)?, danger: Boolean = false) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = Spacing.md, vertical = 14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color)
        Text(text, style = MaterialTheme.typography.bodyLarge, color = color, modifier = Modifier.padding(start = Spacing.md))
    }
}
