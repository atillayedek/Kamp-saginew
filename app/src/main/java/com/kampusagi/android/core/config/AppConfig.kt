package com.kampusagi.android.core.config

import com.kampusagi.android.BuildConfig

object AppConfig {
    val supabaseUrl: String = BuildConfig.SUPABASE_URL
    val supabaseAnonKey: String = BuildConfig.SUPABASE_ANON_KEY

    /** False when the build was made without Supabase credentials. */
    val isBackendConfigured: Boolean =
        supabaseUrl.startsWith("https://") && supabaseAnonKey.isNotBlank()

    val firebaseProjectId: String = BuildConfig.FIREBASE_PROJECT_ID
    val firebaseAppId: String = BuildConfig.FIREBASE_APP_ID
    val firebaseApiKey: String = BuildConfig.FIREBASE_API_KEY
    val firebaseSenderId: String = BuildConfig.FIREBASE_SENDER_ID

    /** False when the build has no Firebase client configuration: push stays off. */
    val isPushConfigured: Boolean = listOf(firebaseProjectId, firebaseAppId, firebaseApiKey, firebaseSenderId)
        .all { it.isNotBlank() }

    const val NOTIFICATION_CHANNEL_ID = "kampusagi_default"

    /** Intent action of a tapped system notification: the app opens the notification list. */
    const val ACTION_OPEN_NOTIFICATIONS = "com.kampusagi.android.OPEN_NOTIFICATIONS"

    const val AUTH_SCHEME = "kampusagi"
    const val AUTH_HOST = "auth-callback"
    const val AUTH_REDIRECT_URL = "$AUTH_SCHEME://$AUTH_HOST"
    const val PASSWORD_RECOVERY_REDIRECT_URL = "$AUTH_REDIRECT_URL?type=recovery"

    const val STUDENT_DOCUMENTS_BUCKET = "student-documents"
    const val AVATARS_BUCKET = "avatars"
    const val POST_MEDIA_BUCKET = "post-media"
    const val GROUP_MEDIA_BUCKET = "group-media"
    const val COURSE_NOTES_BUCKET = "course-notes"
    const val SUBMIT_STUDENT_DOCUMENT_FUNCTION = "submit-student-document"
    const val VERIFY_PURCHASE_FUNCTION = "verify-purchase"
    const val DELETE_ACCOUNT_FUNCTION = "delete-account"
    const val SUBMIT_COURSE_NOTE_FUNCTION = "submit-course-note"
    const val DELETE_ACCOUNT_CONFIRMATION = "DELETE"

    /** Public privacy policy page required by Google Play; empty when the build was made without it. */
    val privacyPolicyUrl: String = BuildConfig.PRIVACY_POLICY_URL.takeIf { it.startsWith("https://") }.orEmpty()

    /** Terms of use accepted at sign-up; empty when the build was made without the website address. */
    val termsUrl: String = BuildConfig.TERMS_URL.takeIf { it.startsWith("https://") }.orEmpty()
}
