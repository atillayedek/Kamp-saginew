package com.kampusagi.android.presentation.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.component.PrimaryButton
import com.kampusagi.android.core.designsystem.icon.AppIcons
import com.kampusagi.android.core.designsystem.theme.Spacing
import com.kampusagi.android.domain.model.Profile
import com.kampusagi.android.presentation.common.avatar.UserAvatar
import com.kampusagi.android.presentation.common.messageRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    profile: Profile,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditProfileViewModel = hiltViewModel(),
) {
    // The system photo picker needs no storage permission.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.onPhotoPicked(it.toString()) }
    }
    Column(modifier = modifier.fillMaxSize().imePadding()) {
        TopAppBar(
            title = { Text(stringResource(R.string.profile_edit)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                }
            },
        )
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center) {
                UserAvatar(profile.id, profile.fullName, profile.username, size = 112.dp)
                if (viewModel.uploadingPhoto) CircularProgressIndicator(modifier = Modifier.size(48.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TextButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !viewModel.uploadingPhoto,
                ) {
                    Icon(AppIcons.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(if (viewModel.hasPhoto) R.string.profile_change_photo else R.string.profile_add_photo),
                        modifier = Modifier.padding(start = Spacing.xs),
                    )
                }
                if (viewModel.hasPhoto) {
                    TextButton(onClick = viewModel::removePhoto, enabled = !viewModel.uploadingPhoto) {
                        Text(stringResource(R.string.profile_remove_photo), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Text(
                stringResource(R.string.profile_photo_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = viewModel.bio,
                onValueChange = viewModel::onBioChange,
                label = { Text(stringResource(R.string.profile_bio)) },
                placeholder = { Text(stringResource(R.string.profile_bio_placeholder)) },
                supportingText = { Text("${viewModel.bio.length} / $MAX_BIO_LENGTH") },
                enabled = !viewModel.savingBio,
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            )
            viewModel.error?.let { error ->
                Text(stringResource(error.messageRes()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            if (viewModel.bioSaved) {
                Text(stringResource(R.string.profile_bio_saved), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            }
            PrimaryButton(
                text = stringResource(R.string.action_save),
                onClick = viewModel::saveBio,
                enabled = viewModel.canSaveBio,
                loading = viewModel.savingBio,
            )
        }
    }
}
