package com.kampusagi.android.data.remote

import com.kampusagi.android.core.config.AppConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.logging.LogLevel
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/**
 * Holds the single Supabase client. When the build has no credentials the
 * client is null and every feature that needs the backend reports
 * [com.kampusagi.android.domain.model.AppError.NOT_CONFIGURED] instead of
 * pretending to work.
 */
@Singleton
class SupabaseProvider @Inject constructor() {

    val client: SupabaseClient? by lazy {
        if (!AppConfig.isBackendConfigured) return@lazy null
        createSupabaseClient(
            supabaseUrl = AppConfig.supabaseUrl,
            supabaseKey = AppConfig.supabaseAnonKey,
        ) {
            // Never log requests: they carry tokens and personal data.
            defaultLogLevel = LogLevel.NONE
            defaultSerializer = KotlinXSerializer(
                Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                    explicitNulls = true
                },
            )
            install(Auth) {
                scheme = AppConfig.AUTH_SCHEME
                host = AppConfig.AUTH_HOST
                flowType = FlowType.PKCE
            }
            install(Postgrest)
            install(Storage)
            install(Functions)
            install(Realtime)
        }
    }

    fun require(): SupabaseClient = client ?: throw BackendNotConfiguredException()
}

class BackendNotConfiguredException : IllegalStateException("Backend is not configured")
