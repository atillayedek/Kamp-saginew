package com.kampusagi.android.core.config

import com.kampusagi.android.BuildConfig

object AppConfig {
    val supabaseUrl: String = BuildConfig.SUPABASE_URL
    val supabaseAnonKey: String = BuildConfig.SUPABASE_ANON_KEY

    /** False when the build was made without Supabase credentials. */
    val isBackendConfigured: Boolean =
        supabaseUrl.startsWith("https://") && supabaseAnonKey.isNotBlank()

    const val AUTH_SCHEME = "kampusagi"
    const val AUTH_HOST = "auth-callback"
    const val AUTH_REDIRECT_URL = "$AUTH_SCHEME://$AUTH_HOST"
    const val PASSWORD_RECOVERY_REDIRECT_URL = "$AUTH_REDIRECT_URL?type=recovery"
}
