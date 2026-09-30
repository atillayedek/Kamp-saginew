package com.kampusagi.android.presentation.common.media

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.icon.AppIcons

/** Provided by the signed-in part of the app. */
val LocalPostPhotoLoader = staticCompositionLocalOf<PostPhotoLoader?> { null }

/** One post or group photo from its private bucket; a failed download can be retried with a tap. */
@Composable
fun PostPhoto(
    path: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    source: PhotoSource = PhotoSource.POST,
) {
    val loader = LocalPostPhotoLoader.current
    LaunchedEffect(loader, source, path) { loader?.request(source, path) }
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        when (val state = loader?.state(source, path) ?: PhotoState.Failed) {
            PhotoState.Loading -> CircularProgressIndicator()
            is PhotoState.Loaded -> Image(
                state.image,
                contentDescription = stringResource(R.string.cd_post_photo),
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
            PhotoState.Failed -> Box(
                modifier = Modifier.fillMaxSize().clickable(enabled = loader != null) { loader?.retry(source, path) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    AppIcons.CloudOff,
                    contentDescription = stringResource(R.string.photo_load_failed),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
