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
        getSystemService<NotificationManager>()?.createNotificationChannel(
            NotificationChannel(
                AppConfig.NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }
}
