package com.kampusagi.android.presentation.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.ProfileState
import com.kampusagi.android.domain.model.ProfileStats
import com.kampusagi.android.domain.repository.ImageEncoder
import com.kampusagi.android.domain.repository.ProfileMediaRepository
import com.kampusagi.android.domain.repository.ProfileRepository
import com.kampusagi.android.presentation.common.avatar.AvatarLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

sealed interface StatsState {
    data object Loading : StatsState
    data class Loaded(val stats: ProfileStats) : StatsState
    data class Failed(val error: AppError) : StatsState
}

@HiltViewModel
class ProfileStatsViewModel @Inject constructor(
    private val repository: ProfileMediaRepository,
) : ViewModel() {

    var state by mutableStateOf<StatsState>(StatsState.Loading)
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            state = when (val result = repository.stats()) {
                is AppResult.Success -> StatsState.Loaded(result.value)
                is AppResult.Failure -> StatsState.Failed(result.error)
            }
        }
    }
}

const val MAX_BIO_LENGTH = 300

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val media: ProfileMediaRepository,
    private val imageEncoder: ImageEncoder,
    private val avatarLoader: AvatarLoader,
) : ViewModel() {

    private val profile get() = (profileRepository.profileState.value as? ProfileState.Loaded)?.profile

    var bio by mutableStateOf(profile?.bio.orEmpty())
        private set

    var savingBio by mutableStateOf(false)
        private set

    var bioSaved by mutableStateOf(false)
        private set

    var uploadingPhoto by mutableStateOf(false)
        private set

    var error by mutableStateOf<AppError?>(null)
        private set

    val hasPhoto: Boolean get() = profile?.avatarPath != null

    val canSaveBio: Boolean get() = !savingBio && bio.trim() != profile?.bio.orEmpty()

    fun onBioChange(value: String) {
        if (value.length <= MAX_BIO_LENGTH) bio = value
        bioSaved = false
        error = null
    }

    fun saveBio() {
        if (!canSaveBio) return
        savingBio = true
        error = null
        viewModelScope.launch {
            when (val result = media.updateBio(bio.trim())) {
                is AppResult.Success -> bioSaved = true
                is AppResult.Failure -> error = result.error
            }
            savingBio = false
        }
    }

    fun onPhotoPicked(uri: String) {
        if (uploadingPhoto) return
        uploadingPhoto = true
        error = null
        viewModelScope.launch {
            val jpeg = when (val encoded = imageEncoder.avatarJpeg(uri)) {
                is AppResult.Success -> encoded.value
                is AppResult.Failure -> {
                    error = encoded.error
                    uploadingPhoto = false
                    return@launch
                }
            }
            when (val result = media.uploadAvatar(jpeg)) {
                is AppResult.Success -> profile?.id?.let(avatarLoader::invalidate)
                is AppResult.Failure -> error = result.error
            }
            uploadingPhoto = false
        }
    }

    fun removePhoto() {
        if (uploadingPhoto) return
        uploadingPhoto = true
        error = null
        viewModelScope.launch {
            when (val result = media.removeAvatar()) {
                is AppResult.Success -> profile?.id?.let(avatarLoader::invalidate)
                is AppResult.Failure -> error = result.error
            }
            uploadingPhoto = false
        }
    }
}
