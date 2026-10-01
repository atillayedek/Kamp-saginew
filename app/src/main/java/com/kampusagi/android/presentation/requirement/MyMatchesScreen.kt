package com.kampusagi.android.presentation.requirement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.component.LoadingView
import com.kampusagi.android.core.designsystem.component.MessageView
import com.kampusagi.android.core.designsystem.component.PrimaryButton
import com.kampusagi.android.core.designsystem.icon.AppIcons
import com.kampusagi.android.core.designsystem.theme.Spacing
import com.kampusagi.android.presentation.common.messageRes

/** Every match of the person's active requirements in one place, grouped by requirement. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyMatchesScreen(
    onOpenChat: (conversationId: String, title: String) -> Unit,
    onCreateRequirement: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MyMatchesViewModel = hiltViewModel(),
) {
    LaunchedEffect(viewModel.openChat) {
        viewModel.openChat?.let { (id, title) ->
            viewModel.onChatOpened()
            onOpenChat(id, title)
        }
    }
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.my_matches_title)) })
        when (val state = viewModel.state) {
            MyMatchesState.Loading -> LoadingView()
            is MyMatchesState.Failed -> MessageView(
                icon = AppIcons.CloudOff,
                title = stringResource(R.string.my_matches_failed),
                body = stringResource(state.error.messageRes()),
            ) {
                PrimaryButton(text = stringResource(R.string.action_retry), onClick = viewModel::load)
            }
            is MyMatchesState.Loaded -> PullToRefreshBox(
                isRefreshing = viewModel.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.groups.isEmpty() -> MessageView(
                        icon = AppIcons.Handshake,
                        title = stringResource(R.string.my_matches_no_requirements_title),
                        body = stringResource(R.string.my_matches_no_requirements_body),
                    ) {
                        PrimaryButton(text = stringResource(R.string.action_new_requirement), onClick = onCreateRequirement)
                    }
                    state.groups.all { it.matches.isEmpty() } -> MessageView(
                        icon = AppIcons.Handshake,
                        title = stringResource(R.string.my_matches_empty_title),
                        body = stringResource(R.string.my_matches_empty_body),
                    )
                    else -> LazyColumn(
                        contentPadding = PaddingValues(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        viewModel.chatError?.let { error ->
                            item {
                                Text(
                                    stringResource(error.messageRes()),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        state.groups.filter { it.matches.isNotEmpty() }.forEach { group ->
                            item(key = "header-${group.requirement.id}") {
                                Column(modifier = Modifier.padding(top = Spacing.sm)) {
                                    Text(
                                        stringResource(R.string.my_matches_for, group.requirement.title),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        pluralMatches(group.matches.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            items(group.matches, key = { "${group.requirement.id}-${it.requirementId}" }) { match ->
                                MatchCard(
                                    match = match,
                                    starting = viewModel.startingChatWith == match.owner.id,
                                    enabled = viewModel.startingChatWith == null,
                                    onMessage = { viewModel.startChat(match) },
                                    onObject = { reason -> viewModel.objectTo(group.requirement.id, match, reason) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun pluralMatches(count: Int): String = stringResource(R.string.my_matches_count, count)
