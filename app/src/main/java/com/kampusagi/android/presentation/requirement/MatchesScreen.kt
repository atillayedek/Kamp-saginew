package com.kampusagi.android.presentation.requirement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.component.LoadingView
import com.kampusagi.android.core.designsystem.component.MessageView
import com.kampusagi.android.core.designsystem.component.PrimaryButton
import com.kampusagi.android.core.designsystem.component.SecondaryButton
import com.kampusagi.android.core.designsystem.theme.Spacing
import com.kampusagi.android.domain.model.Match
import com.kampusagi.android.presentation.common.messageRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MatchesViewModel = hiltViewModel(),
) {
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
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                }
            },
        )
        when (val state = viewModel.state) {
            MatchesState.Loading -> LoadingView()
            is MatchesState.Failed -> MessageView(
                icon = Icons.Outlined.CloudOff,
                title = stringResource(R.string.matches_load_failed),
                body = stringResource(state.error.messageRes()),
            ) {
                PrimaryButton(text = stringResource(R.string.action_retry), onClick = viewModel::load)
            }
            is MatchesState.Loaded -> if (state.matches.isEmpty()) {
                MessageView(
                    icon = Icons.Outlined.PersonSearch,
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
                    item {
                        Text(
                            stringResource(R.string.matches_score_explained),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(state.matches, key = { it.requirementId }) { match -> MatchCard(match) }
                }
            }
        }
    }
}

@Composable
private fun MatchCard(match: Match) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
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
        }
    }
}
