package com.kampusagi.android.presentation.common.avatar

import androidx.lifecycle.ViewModel
import com.kampusagi.android.presentation.common.media.PostPhotoLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Hands the singleton image loaders to Compose, which provides them through composition locals. */
@HiltViewModel
class AvatarHostViewModel @Inject constructor(
    val loader: AvatarLoader,
    val photos: PostPhotoLoader,
) : ViewModel()
