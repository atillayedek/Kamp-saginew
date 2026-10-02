package com.kampusagi.android.data.repository

import android.net.Uri
import android.util.Log
import com.kampusagi.android.BuildConfig
import com.kampusagi.android.core.config.AppConfig
import com.kampusagi.android.core.di.ApplicationScope
import com.kampusagi.android.data.remote.SupabaseProvider
import com.kampusagi.android.data.remote.safeCall
import com.kampusagi.android.data.remote.toAppError
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.AuthRedirectResult
import com.kampusagi.android.domain.model.AuthState
import com.kampusagi.android.domain.model.SignUpConsents
import com.kampusagi.android.domain.model.SignUpResult
import com.kampusagi.android.domain.repository.AuthRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val provider: SupabaseProvider,
    @ApplicationScope scope: CoroutineScope,
) : AuthRepository {

    override val isBackendConfigured: Boolean = provider.client != null

    override val authState: StateFlow<AuthState> = provider.client?.let { client ->
        client.auth.sessionStatus
            .map { status -> status.toAuthState(client) }
            .stateIn(scope, SharingStarted.Eagerly, AuthState.Loading)
    } ?: MutableStateFlow<AuthState>(AuthState.SignedOut).asStateFlow()

    /**
     * When the access token cannot be refreshed (for example offline) the
     * stored session is kept, so the person stays signed in and every request
     * reports a network error until the connection is back.
     */
    private fun SessionStatus.toAuthState(client: SupabaseClient): AuthState = when (this) {
        is SessionStatus.Authenticated -> session.user?.toSignedIn() ?: AuthState.Loading
        is SessionStatus.NotAuthenticated -> AuthState.SignedOut
        is SessionStatus.RefreshFailure -> client.auth.currentSessionOrNull()?.user?.toSignedIn()
            ?: AuthState.SignedOut
        else -> AuthState.Loading
    }

    /** app_metadata can only be written with the service role, so users cannot mark themselves admin. */
    private fun UserInfo.toSignedIn() = AuthState.SignedIn(
        userId = id,
        email = email.orEmpty(),
        isAdmin = (appMetadata?.get("role") as? JsonPrimitive)?.contentOrNull == ADMIN_ROLE,
    )

    override suspend fun signIn(email: String, password: String): AppResult<Unit> = call { client ->
        client.auth.signInWith(Email) {
            this.email = email.normalizedEmail()
            this.password = password
        }
    }

    /**
     * The sign-up form's answers travel in the user metadata: the database checks the age and records
     * every acceptance, notice and optional consent in consent_logs in the same transaction as the
     * account (kvkk_before_signup), and drops the birth date.
     */
    override suspend fun signUp(email: String, password: String, consents: SignUpConsents): AppResult<SignUpResult> = call { client ->
        client.auth.signUpWith(Email, redirectUrl = AppConfig.AUTH_REDIRECT_URL) {
            this.email = email.normalizedEmail()
            this.password = password
            data = buildJsonObject {
                put(
                    "kvkk",
                    buildJsonObject {
                        put("birth_date", consents.birthDate.toString())
                        put("accepted", buildJsonObject { consents.accepted.forEach { (type, version) -> put(type, version) } })
                        put("informed", buildJsonObject { consents.informed.forEach { (type, version) -> put(type, version) } })
                        put(
                            "consents",
                            buildJsonObject {
                                consents.consents.forEach { (type, choice) ->
                                    put(type, buildJsonObject {
                                        put("version", choice.first)
                                        put("granted", choice.second)
                                    })
                                }
                            },
                        )
                        put("app_version", BuildConfig.VERSION_NAME)
                        put("platform", "android")
                        put("user_agent", "KampusAgi/${BuildConfig.VERSION_NAME} (${ComplianceRepositoryImpl.deviceInfo()})")
                    },
                )
            }
        }
        if (client.auth.currentSessionOrNull() != null) SignUpResult.SIGNED_IN else SignUpResult.VERIFICATION_REQUIRED
    }

    override suspend fun resendVerificationEmail(email: String): AppResult<Unit> = call { client ->
        client.auth.resendEmail(OtpType.Email.SIGNUP, email.normalizedEmail())
    }

    override suspend fun sendPasswordReset(email: String): AppResult<Unit> = call { client ->
        client.auth.resetPasswordForEmail(
            email = email.normalizedEmail(),
            redirectUrl = AppConfig.PASSWORD_RECOVERY_REDIRECT_URL,
        )
    }

    override suspend fun updatePassword(newPassword: String): AppResult<Unit> = call { client ->
        client.auth.updateUser { password = newPassword }
    }

    override suspend fun signOut(): AppResult<Unit> {
        val client = provider.client ?: return AppResult.Failure(AppError.NOT_CONFIGURED)
        return safeCall { client.auth.signOut() }.fold(
            onSuccess = { AppResult.Success(Unit) },
            onFailure = { error ->
                // The server could not be told (offline or the session is already gone). The refresh
                // token expires on its own; signing out on this device must still succeed.
                Log.w(TAG, "Remote sign-out failed, clearing the local session", error)
                safeCall { client.auth.clearSession() }.fold(
                    onSuccess = { AppResult.Success(Unit) },
                    onFailure = { AppResult.Failure(it.toAppError()) },
                )
            },
        )
    }

    override suspend fun handleAuthRedirect(url: String): AuthRedirectResult {
        val client = provider.client ?: return AuthRedirectResult.NotAnAuthLink
        val uri = Uri.parse(url)
        if (uri.scheme != AppConfig.AUTH_SCHEME || uri.host != AppConfig.AUTH_HOST) {
            return AuthRedirectResult.NotAnAuthLink
        }
        val params = uri.allParameters()

        if (params.containsKey("error") || params.containsKey("error_code")) {
            val description = (params["error_code"].orEmpty() + " " + params["error_description"].orEmpty()).lowercase()
            return if ("expired" in description || "invalid" in description) {
                AuthRedirectResult.LinkExpired
            } else {
                AuthRedirectResult.Failed(AppError.UNKNOWN)
            }
        }

        val code = params["code"] ?: return AuthRedirectResult.NotAnAuthLink
        val isRecovery = params["type"] == "recovery"

        return safeCall { client.auth.exchangeCodeForSession(code) }.fold(
            onSuccess = {
                if (isRecovery) AuthRedirectResult.PasswordRecovery else AuthRedirectResult.SignedIn
            },
            onFailure = { error ->
                when (error) {
                    is HttpRequestException, is IOException -> AuthRedirectResult.Failed(AppError.NETWORK)
                    // Expired/used codes, or a link opened on a different device than the one that asked for it.
                    is RestException, is IllegalArgumentException -> AuthRedirectResult.LinkExpired
                    else -> AuthRedirectResult.Failed(error.toAppError())
                }
            },
        )
    }

    private suspend fun <T> call(block: suspend (SupabaseClient) -> T): AppResult<T> {
        val client = provider.client ?: return AppResult.Failure(AppError.NOT_CONFIGURED)
        return safeCall { block(client) }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { AppResult.Failure(it.toAppError()) },
        )
    }

    private fun String.normalizedEmail() = trim().lowercase(Locale.ROOT)

    /** Query and fragment parameters (implicit-flow errors arrive in the fragment). */
    private fun Uri.allParameters(): Map<String, String> = buildMap {
        queryParameterNames.forEach { name -> getQueryParameter(name)?.let { put(name, it) } }
        encodedFragment?.split('&')?.forEach { pair ->
            val parts = pair.split('=', limit = 2)
            if (parts.size == 2) put(Uri.decode(parts[0]), Uri.decode(parts[1]))
        }
    }

    private companion object {
        const val TAG = "AuthRepository"
        const val ADMIN_ROLE = "admin"
    }
}
