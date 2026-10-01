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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
                            onObject = { reason -> viewModel.objectTo(match, reason) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun MatchCard(
    match: Match,
    starting: Boolean,
    enabled: Boolean,
    onMessage: () -> Unit,
    onObject: (reason: String?) -> Unit,
) {
    var explaining by rememberSaveable { mutableStateOf(false) }
    var objecting by rememberSaveable { mutableStateOf(false) }
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
            if (match.sharedTags.isNotEmpty()) {
                Text(
                    stringResource(R.string.match_shared_tags, match.sharedTags.joinToString(", ")),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (match.description.isNotBlank()) {
                Text(match.description, style = MaterialTheme.typography.bodyMedium)
            }
            formatStartsAt(match.startsAt)?.let {
                Text(stringResource(R.string.requirement_starts_at, it), style = MaterialTheme.typography.bodySmall)
            }
            match.locationText?.let {
                Text(stringResource(R.string.requirement_location, it), style = MaterialTheme.typography.bodySmall)
            }
            if (match.tags.isNotEmpty()) {
                Text(match.tags.joinToString("  ") { "#$it" }, style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onMessage, enabled = enabled) {
                    Text(stringResource(if (starting) R.string.admin_working else R.string.action_message))
                }
                TextButton(onClick = { explaining = true }) { Text(stringResource(R.string.match_why)) }
            }
        }
    }
    if (explaining) {
        AlertDialog(
            onDismissRequest = { explaining = false },
            title = { Text(stringResource(R.string.match_why)) },
            text = { Text(matchExplanation(match)) },
            confirmButton = { TextButton(onClick = { explaining = false }) { Text(stringResource(R.string.action_ok)) } },
            dismissButton = {
                TextButton(onClick = {
                    explaining = false
                    objecting = true
                }) { Text(stringResource(R.string.match_object)) }
            },
        )
    }
    if (objecting) {
        var reason by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { objecting = false },
            title = { Text(stringResource(R.string.match_object)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(stringResource(R.string.match_object_body), style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { if (it.length <= 500) reason = it },
                        label = { Text(stringResource(R.string.match_object_reason)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    objecting = false
                    onObject(reason.trim().ifEmpty { null })
                }) { Text(stringResource(R.string.match_object_confirm)) }
            },
            dismissButton = { TextButton(onClick = { objecting = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** Why the rules suggested this match: the same inputs find_matches scores, in plain words. */
@Composable
private fun matchExplanation(match: Match): String = buildString {
    append(stringResource(R.string.match_why_rules))
    append("\n\n")
    append(
        if (match.sharedTags.isNotEmpty()) stringResource(R.string.match_why_tags, match.sharedTags.joinToString(", "))
        else stringResource(R.string.match_why_words),
    )
    if (match.startsAt != null) append("\n").append(stringResource(R.string.match_why_time))
    if (match.locationText != null) append("\n").append(stringResource(R.string.match_why_place))
    append("\n\n").append(stringResource(R.string.match_why_score, match.score))
}
