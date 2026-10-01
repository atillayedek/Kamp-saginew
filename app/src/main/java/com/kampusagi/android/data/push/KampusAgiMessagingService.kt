package com.kampusagi.android.data.push

import android.Manifest
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.os.Build
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.kampusagi.android.MainActivity
import com.kampusagi.android.R
import com.kampusagi.android.core.config.AppConfig
import com.kampusagi.android.core.di.ApplicationScope
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.AuthState
import com.kampusagi.android.domain.repository.AuthRepository
import com.kampusagi.android.domain.repository.PushRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Receives FCM messages sent by the `dispatch-push` Edge Function. */
@AndroidEntryPoint
class KampusAgiMessagingService : FirebaseMessagingService() {

    @Inject lateinit var pushRepository: PushRepository
    @Inject lateinit var authRepository: AuthRepository
    @Inject @field:ApplicationScope lateinit var scope: CoroutineScope

    override fun onNewToken(token: String) {
        if (authRepository.authState.value !is AuthState.SignedIn) return
        scope.launch {
            val result = pushRepository.registerToken(token)
            if (result is AppResult.Failure) Log.w(TAG, "New FCM token not registered: ${result.error}")
        }
    }

    /**
     * In the background Android shows FCM notification messages itself; in the
     * foreground they arrive here and are shown the same way.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        val notification = message.notification ?: return
        val intent = Intent(this, MainActivity::class.java)
            .setAction(AppConfig.ACTION_OPEN_NOTIFICATIONS)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val channel = if (message.data["channel"] == "marketing") AppConfig.MARKETING_CHANNEL_ID else AppConfig.NOTIFICATION_CHANNEL_ID
        val built = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        val manager = NotificationManagerCompat.from(this)
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        try {
            manager.notify(message.messageId?.hashCode() ?: System.currentTimeMillis().toInt(), built)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission was revoked", e)
        }
    }

    private companion object {
        const val TAG = "MessagingService"
    }
}
