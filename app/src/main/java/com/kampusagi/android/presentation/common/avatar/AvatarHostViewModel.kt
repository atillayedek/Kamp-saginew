package com.kampusagi.android.presentation.common.avatar

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Hands the singleton [AvatarLoader] to Compose, which provides it through [LocalAvatarLoader]. */
@HiltViewModel
class AvatarHostViewModel @Inject constructor(val loader: AvatarLoader) : ViewModel()
