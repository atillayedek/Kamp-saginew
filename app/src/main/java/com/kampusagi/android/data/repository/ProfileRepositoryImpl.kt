package com.kampusagi.android.data.repository

import android.util.Log
import com.kampusagi.android.core.di.ApplicationScope
import com.kampusagi.android.data.remote.ProfileDto
import com.kampusagi.android.data.remote.SupabaseProvider
import com.kampusagi.android.data.remote.UnknownStatusException
import com.kampusagi.android.data.remote.safeCall
import com.kampusagi.android.data.remote.toAppError
import com.kampusagi.android.domain.model.AccountStatus
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.AuthState
import com.kampusagi.android.domain.model.Profile
import com.kampusagi.android.domain.model.ProfileDraft
import com.kampusagi.android.domain.model.ProfileState
import com.kampusagi.android.domain.repository.AuthRepository
import com.kampusagi.android.domain.repository.ProfileRepository
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val provider: SupabaseProvider,
    private val authRepository: AuthRepository,
    @ApplicationScope scope: CoroutineScope,
) : ProfileRepository {

    private val state = MutableStateFlow<ProfileState?>(null)
    override val profileState: StateFlow<ProfileState?> = state.asStateFlow()

    init {
        scope.launch {
            authRepository.authState
                .map { (it as? AuthState.SignedIn)?.userId }
                .distinctUntilChanged()
                .collectLatest { userId ->
                    if (userId == null) {
                        state.value = null
                    } else {
                        state.value = ProfileState.Loading
                        load(userId)
                    }
                }
        }
    }

    override suspend fun refresh() {
        val userId = currentUserId() ?: return
        if (state.value !is ProfileState.Loaded) state.value = ProfileState.Loading
        load(userId)
    }

    override suspend fun completeProfile(draft: ProfileDraft): AppResult<Unit> {
        val client = provider.client ?: return AppResult.Failure(AppError.NOT_CONFIGURED)
        val userId = currentUserId() ?: return AppResult.Failure(AppError.SESSION_EXPIRED)
        val result = safeCall {
            client.postgrest.rpc(
                "complete_profile",
                buildJsonObject {
                    put("p_full_name", draft.fullName)
                    put("p_username", draft.username)
                    put("p_university_id", draft.universityId)
                    put("p_department", draft.department)
                },
            )
        }
        return result.fold(
            onSuccess = {
                load(userId)
                AppResult.Success(Unit)
            },
            onFailure = { AppResult.Failure(it.toAppError()) },
        )
    }

    private suspend fun load(userId: String) {
        val client = provider.client
        if (client == null) {
            state.value = ProfileState.Failed(AppError.NOT_CONFIGURED)
            return
        }
        safeCall {
            client.postgrest.from("profiles")
                .select(Columns.raw(ProfileDto.COLUMNS)) {
                    filter { eq("id", userId) }
                }
                .decodeSingle<ProfileDto>()
                .toDomain()
        }.fold(
            onSuccess = { state.value = ProfileState.Loaded(it) },
            onFailure = { error ->
                Log.w(TAG, "Profile could not be loaded", error)
                // Keep showing the last known profile when a background refresh fails.
                if (state.value !is ProfileState.Loaded) state.value = ProfileState.Failed(error.toAppError())
            },
        )
    }

    private fun currentUserId(): String? = (authRepository.authState.value as? AuthState.SignedIn)?.userId

    private fun ProfileDto.toDomain(): Profile = Profile(
        id = id,
        email = email,
        fullName = fullName,
        username = username,
        universityId = universityId,
        universityName = universities?.name,
        department = department,
        status = AccountStatus.entries.firstOrNull { it.name == accountStatus }
            ?: throw UnknownStatusException(accountStatus),
        bio = bio,
        avatarPath = avatarPath,
    )

    private companion object {
        const val TAG = "ProfileRepository"
    }
}
