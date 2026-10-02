package com.kampusagi.android.presentation.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.Profile
import com.kampusagi.android.domain.model.ProfileDraft
import com.kampusagi.android.domain.model.University
import com.kampusagi.android.domain.repository.UniversityRepository
import com.kampusagi.android.domain.usecase.CompleteProfileUseCase
import com.kampusagi.android.domain.usecase.FormResult
import com.kampusagi.android.domain.usecase.ProfileInputError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

sealed interface UniversitiesState {
    data object Loading : UniversitiesState
    data class Loaded(val universities: List<University>) : UniversitiesState
    data class Failed(val error: AppError) : UniversitiesState
}

data class ProfileFormState(
    val fullName: String = "",
    val username: String = "",
    val universityId: String? = null,
    val department: String = "",
    val inputErrors: Set<ProfileInputError> = emptySet(),
    val error: AppError? = null,
    val isSubmitting: Boolean = false,
)

@HiltViewModel
class ProfileSetupViewModel @Inject constructor(
    private val universityRepository: UniversityRepository,
    private val completeProfile: CompleteProfileUseCase,
) : ViewModel() {

    var form by mutableStateOf(ProfileFormState())
        private set

    var universities by mutableStateOf<UniversitiesState>(UniversitiesState.Loading)
        private set

    var saved by mutableStateOf(false)
        private set

    private var prefilled = false

    init {
        loadUniversities()
    }

    /** Fills the form once with what the person already saved (editing before verification). */
    fun prefill(profile: Profile?) {
        if (prefilled || profile == null) return
        prefilled = true
        form = form.copy(
            fullName = profile.fullName.orEmpty(),
            username = profile.username.orEmpty(),
            universityId = profile.universityId,
            department = profile.department.orEmpty(),
        )
    }

    fun loadUniversities() {
        universities = UniversitiesState.Loading
        viewModelScope.launch {
            universities = when (val result = universityRepository.getActiveUniversities()) {
                is AppResult.Success -> UniversitiesState.Loaded(result.value)
                is AppResult.Failure -> UniversitiesState.Failed(result.error)
            }
        }
    }

    fun onFullNameChange(value: String) {
        form = form.copy(fullName = value, inputErrors = form.inputErrors - ProfileInputError.FULL_NAME_INVALID, error = null)
    }

    fun onUsernameChange(value: String) {
        form = form.copy(username = value, inputErrors = form.inputErrors - ProfileInputError.USERNAME_INVALID, error = null)
    }

    fun onUniversitySelected(id: String) {
        form = form.copy(universityId = id, inputErrors = form.inputErrors - ProfileInputError.UNIVERSITY_REQUIRED, error = null)
    }

    fun onDepartmentChange(value: String) {
        form = form.copy(department = value, inputErrors = form.inputErrors - ProfileInputError.DEPARTMENT_INVALID, error = null)
    }

    fun submit() {
        if (form.isSubmitting) return
        form = form.copy(isSubmitting = true, error = null)
        val draft = ProfileDraft(
            fullName = form.fullName,
            username = form.username,
            universityId = form.universityId,
            department = form.department,
        )
        viewModelScope.launch {
            when (val result = completeProfile(draft)) {
                is FormResult.Invalid -> form = form.copy(isSubmitting = false, inputErrors = result.errors)
                is FormResult.Failed -> form = form.copy(
                    isSubmitting = false,
                    error = result.error,
                    inputErrors = if (result.error == AppError.USERNAME_TAKEN) {
                        form.inputErrors + ProfileInputError.USERNAME_INVALID
                    } else {
                        form.inputErrors
                    },
                )
                is FormResult.Success -> {
                    form = form.copy(isSubmitting = false)
                    saved = true
                }
            }
        }
    }
}
