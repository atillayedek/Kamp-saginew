package com.kampusagi.android.domain.usecase

import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.ProfileDraft
import com.kampusagi.android.domain.repository.ProfileRepository
import java.util.Locale
import javax.inject.Inject

enum class ProfileInputError { FULL_NAME_INVALID, USERNAME_INVALID, UNIVERSITY_REQUIRED, DEPARTMENT_INVALID }

/**
 * Mirrors the checks in `public.complete_profile` so mistakes are shown next
 * to the field before a request is made. The database function stays the
 * authority.
 */
object ProfileInputValidator {
    private val USERNAME = Regex("^[a-z0-9_.]{3,30}$")

    fun normalize(draft: ProfileDraft): ProfileDraft = draft.copy(
        fullName = draft.fullName.trim().replace(Regex("\\s+"), " "),
        username = draft.username.trim().lowercase(Locale.ROOT),
        department = draft.department.trim(),
    )

    fun validate(draft: ProfileDraft): Set<ProfileInputError> {
        val normalized = normalize(draft)
        return buildSet {
            if (normalized.fullName.length !in 2..100) add(ProfileInputError.FULL_NAME_INVALID)
            if (!USERNAME.matches(normalized.username)) add(ProfileInputError.USERNAME_INVALID)
            if (normalized.universityId.isNullOrBlank()) add(ProfileInputError.UNIVERSITY_REQUIRED)
            if (normalized.department.length !in 2..120) add(ProfileInputError.DEPARTMENT_INVALID)
        }
    }
}

class CompleteProfileUseCase @Inject constructor(private val profileRepository: ProfileRepository) {
    suspend operator fun invoke(draft: ProfileDraft): FormResult<Unit, ProfileInputError> {
        val errors = ProfileInputValidator.validate(draft)
        if (errors.isNotEmpty()) return FormResult.Invalid(errors)
        return when (val result = profileRepository.completeProfile(ProfileInputValidator.normalize(draft))) {
            is AppResult.Success -> FormResult.Success(Unit)
            is AppResult.Failure -> FormResult.Failed(result.error)
        }
    }
}
