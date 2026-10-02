package com.kampusagi.android.presentation.group

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kampusagi.android.core.designsystem.icon.AppIcons
import com.kampusagi.android.domain.model.GroupKind
import com.kampusagi.android.presentation.common.media.PhotoSource
import com.kampusagi.android.presentation.common.media.PostPhoto

/** The group's photo, or an icon for its kind (megaphone for channels, people for study groups). */
@Composable
fun GroupAvatar(kind: GroupKind, photoPath: String?, modifier: Modifier = Modifier, size: Dp = 52.dp) {
    val shape = CircleShape
    if (photoPath != null) {
        PostPhoto(photoPath, source = PhotoSource.GROUP, modifier = modifier.size(size).clip(shape))
    } else {
        Box(
            modifier = modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (kind == GroupKind.CHANNEL) AppIcons.Campaign else AppIcons.Groups,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(size * 0.5f),
            )
        }
    }
}
