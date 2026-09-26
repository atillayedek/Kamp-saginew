package com.kampusagi.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.content.getSystemService
import com.kampusagi.android.core.config.AppConfig
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class KampusAgiApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        getSystemService<NotificationManager>()?.createNotificationChannel(
            NotificationChannel(
                AppConfig.NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }
}
