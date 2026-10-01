package com.kampusagi.android.data.push

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.kampusagi.android.core.config.AppConfig
import com.kampusagi.android.data.remote.SupabaseProvider
import com.kampusagi.android.data.remote.safeCall
import com.kampusagi.android.data.remote.toAppError
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.repository.PushRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.postgrest.postgrest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Real FCM: Firebase is initialised from the build's client configuration (no
 * google-services.json in the repository). Without that configuration push is
 * reported as not configured; nothing pretends to deliver notifications.
 */
@Singleton
class PushRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val provider: SupabaseProvider,
) : PushRepository {

    override val isConfigured: Boolean = AppConfig.isPushConfigured

    /** Called once from Application.onCreate. */
    fun initializeFirebase() {
        if (!isConfigured || FirebaseApp.getApps(context).isNotEmpty()) return
        val options = FirebaseOptions.Builder()
            .setProjectId(AppConfig.firebaseProjectId)
            .setApplicationId(AppConfig.firebaseAppId)
            .setApiKey(AppConfig.firebaseApiKey)
            .setGcmSenderId(AppConfig.firebaseSenderId)
            .build()
        FirebaseApp.initializeApp(context, options)
    }

    override suspend fun registerCurrentDevice(): AppResult<Unit> {
        if (!isConfigured) return AppResult.Failure(AppError.PUSH_NOT_CONFIGURED)
        val token = safeCall { FirebaseMessaging.getInstance().token.await() }
            .getOrElse {
                Log.w(TAG, "FCM token unavailable", it)
                return AppResult.Failure(AppError.NETWORK)
            }
        return registerToken(token)
    }

    override suspend fun registerToken(token: String): AppResult<Unit> {
        val client = provider.client ?: return AppResult.Failure(AppError.NOT_CONFIGURED)
        return safeCall {
            client.postgrest.rpc("register_device_token", JsonObject(mapOf("p_token" to JsonPrimitive(token))))
        }.fold(
            onSuccess = { AppResult.Success(Unit) },
            onFailure = { AppResult.Failure(it.toAppError()) },
        )
    }

    override suspend fun unregisterCurrentDevice(): AppResult<Unit> {
        if (!isConfigured) return AppResult.Success(Unit)
        val client = provider.client ?: return AppResult.Failure(AppError.NOT_CONFIGURED)
        return safeCall {
            val token = FirebaseMessaging.getInstance().token.await()
            client.postgrest.rpc("unregister_device_token", JsonObject(mapOf("p_token" to JsonPrimitive(token))))
        }.fold(
            onSuccess = { AppResult.Success(Unit) },
            onFailure = { AppResult.Failure(it.toAppError()) },
        )
    }

    private companion object {
        const val TAG = "Push"
    }
}
