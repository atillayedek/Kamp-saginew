package com.kampusagi.android.presentation.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.component.LinkButton
import com.kampusagi.android.core.designsystem.component.SecondaryButton
import com.kampusagi.android.core.designsystem.theme.Spacing
import com.kampusagi.android.domain.model.Profile

@Composable
fun ProfileTab(
    profile: Profile,
    isAdmin: Boolean,
    onOpenAdmin: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(profile.fullName.orEmpty(), style = MaterialTheme.typography.headlineMedium)
        profile.username?.let { Text("@$it", style = MaterialTheme.typography.bodyLarge) }
        ProfileField(R.string.field_university, profile.universityName)
        ProfileField(R.string.field_department, profile.department)
        ProfileField(R.string.field_email, profile.email)
        Text(
            stringResource(R.string.profile_verified),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
        if (isAdmin) SecondaryButton(text = stringResource(R.string.action_open_admin), onClick = onOpenAdmin)
        LinkButton(text = stringResource(R.string.action_sign_out), onClick = onSignOut)
    }
}

@Composable
private fun ProfileField(label: Int, value: String?) {
    if (value.isNullOrBlank()) return
    Column {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
