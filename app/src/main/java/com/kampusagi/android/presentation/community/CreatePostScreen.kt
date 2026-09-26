package com.kampusagi.android.presentation.community

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.component.PrimaryButton
import com.kampusagi.android.core.designsystem.theme.Spacing
import com.kampusagi.android.domain.model.PostScope
import com.kampusagi.android.domain.usecase.PostTextValidator
import com.kampusagi.android.presentation.common.messageRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePostScreen(
    onBack: () -> Unit,
    onCreated: (PostScope) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreatePostViewModel = hiltViewModel(),
) {
    LaunchedEffect(viewModel.createdIn) { viewModel.createdIn?.let(onCreated) }

    Column(modifier = modifier.fillMaxSize().imePadding()) {
        TopAppBar(
            title = { Text(stringResource(R.string.create_post_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                }
            },
        )
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                PostScope.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = viewModel.scope == option,
                        onClick = { viewModel.onScopeChange(option) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = PostScope.entries.size),
                        enabled = !viewModel.isSubmitting,
                    ) { Text(stringResource(option.labelRes())) }
                }
            }
            Text(
                stringResource(
                    if (viewModel.scope == PostScope.GENERAL) R.string.create_post_general_hint else R.string.create_post_university_hint,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = viewModel.body,
                onValueChange = viewModel::onBodyChange,
                placeholder = { Text(stringResource(R.string.create_post_placeholder)) },
                supportingText = { Text("${viewModel.body.length} / ${PostTextValidator.MAX_POST_LENGTH}") },
                enabled = !viewModel.isSubmitting,
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
            )
            viewModel.error?.let { error ->
                Text(
                    stringResource(error.messageRes()),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            PrimaryButton(
                text = stringResource(R.string.action_share),
                onClick = viewModel::submit,
                enabled = viewModel.canSubmit,
                loading = viewModel.isSubmitting,
            )
        }
    }
}
