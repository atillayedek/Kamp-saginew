package com.kampusagi.android.presentation.requirement

import com.kampusagi.android.core.designsystem.icon.AppIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.component.LoadingView
import com.kampusagi.android.core.designsystem.component.MessageView
import com.kampusagi.android.core.designsystem.component.PrimaryButton
import com.kampusagi.android.core.designsystem.component.SecondaryButton
import com.kampusagi.android.core.designsystem.theme.Spacing
import com.kampusagi.android.domain.model.Match
import com.kampusagi.android.presentation.common.avatar.UserAvatar
import com.kampusagi.android.presentation.common.messageRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchesScreen(
    onBack: () -> Unit,
    onOpenChat: (conversationId: String, title: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MatchesViewModel = hiltViewModel(),
) {
    LaunchedEffect(viewModel.openChat) {
        viewModel.openChat?.let { (id, title) ->
            viewModel.onChatOpened()
            onOpenChat(id, title)
        }
    }
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(stringResource(R.string.matches_title))
                    Text(
                        viewModel.title,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                }
            },
        )
        when (val state = viewModel.state) {
            MatchesState.Loading -> LoadingView()
            is MatchesState.Failed -> MessageView(
                icon = AppIcons.CloudOff,
                title = stringResource(R.string.matches_load_failed),
                body = stringResource(state.error.messageRes()),
            ) {
                PrimaryButton(text = stringResource(R.string.action_retry), onClick = viewModel::load)
            }
            is MatchesState.Loaded -> if (state.matches.isEmpty()) {
                MessageView(
                    icon = AppIcons.PersonSearch,
                    title = stringResource(R.string.matches_empty_title),
                    body = stringResource(R.string.matches_empty_body),
                ) {
                    SecondaryButton(text = stringResource(R.string.action_refresh_status), onClick = viewModel::load)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    contentPadding = PaddingValues(Spacing.md),
                ) {
                    viewModel.chatError?.let { error ->
                        item {
                            Text(
                                stringResource(error.messageRes()),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    item {
                        Text(
                            stringResource(R.string.matches_score_explained),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(state.matches, key = { it.requirementId }) { match ->
                        MatchCard(
                            match = match,
                            starting = viewModel.startingChatWith == match.owner.id,
                            enabled = viewModel.startingChatWith == null,
                            onMessage = { viewModel.startChat(match) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun MatchCard(match: Match, starting: Boolean, enabled: Boolean, onMessage: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(match.owner.id, match.owner.fullName, match.owner.username, size = 44.dp)
                Column(modifier = Modifier.weight(1f).padding(start = Spacing.sm + Spacing.xs)) {
                    Text(match.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOfNotNull(
                            match.owner.fullName ?: match.owner.username?.let { "@$it" },
                            match.owner.department,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        stringResource(R.string.match_score, match.score),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                    )
                }
            }
            Text(match.description, style = MaterialTheme.typography.bodyMedium)
            formatStartsAt(match.startsAt)?.let {
                Text(stringResource(R.string.requirement_starts_at, it), style = MaterialTheme.typography.bodySmall)
            }
            match.locationText?.let {
                Text(stringResource(R.string.requirement_location, it), style = MaterialTheme.typography.bodySmall)
            }
            if (match.tags.isNotEmpty()) {
                Text(match.tags.joinToString("  ") { "#$it" }, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onMessage, enabled = enabled) {
                Text(stringResource(if (starting) R.string.admin_working else R.string.action_message))
            }
        }
    }
}
