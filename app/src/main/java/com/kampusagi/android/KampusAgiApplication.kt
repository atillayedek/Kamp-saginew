package com.kampusagi.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.content.getSystemService
import com.kampusagi.android.core.config.AppConfig
import com.kampusagi.android.data.crash.CrashReporter
import com.kampusagi.android.data.push.PushRepositoryImpl
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class KampusAgiApplication : Application() {

    @Inject lateinit var crashReporter: CrashReporter
    @Inject lateinit var pushRepository: PushRepositoryImpl

    override fun onCreate() {
        super.onCreate()
        crashReporter.install()
        pushRepository.initializeFirebase()
        // Transactional (messages, comments, verification, moderation) and campaigns are separate
        // channels, so campaigns can be silenced on their own; campaigns are sent only with consent.
        getSystemService<NotificationManager>()?.createNotificationChannels(
            listOf(
                NotificationChannel(
                    AppConfig.NOTIFICATION_CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
                NotificationChannel(
                    AppConfig.MARKETING_CHANNEL_ID,
                    getString(R.string.notification_channel_marketing),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = getString(R.string.notification_channel_marketing_description) },
            ),
        )
    }
}
