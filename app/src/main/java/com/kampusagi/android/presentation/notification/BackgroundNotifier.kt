package com.kampusagi.android.presentation.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.kampusagi.android.MainActivity
import com.kampusagi.android.R
import com.kampusagi.android.core.config.AppConfig
import com.kampusagi.android.core.di.ApplicationScope
import com.kampusagi.android.domain.model.AppNotification
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.NotificationKind
import com.kampusagi.android.domain.repository.NotificationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shows a system notification when a new notification arrives through
 * Supabase Realtime while the app is in the background (D30). There is no
 * FCM: once Android stops the app's process, nothing arrives until the app is
 * opened again, and the in-app list then shows everything from the server.
 */
@Singleton
class BackgroundNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: NotificationRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private var job: Job? = null

    /** Starts listening for the signed-in person, replacing any earlier listener. */
    fun start() {
        job?.cancel()
        job = scope.launch {
            val initial = repository.notifications()
            val baseline = when (initial) {
                is AppResult.Success -> NotificationAnnouncer.baselineOf(initial.value, Instant.now())
                is AppResult.Failure -> {
                    Log.w(TAG, "Existing notifications not loaded (${initial.error}); announcing only newer ones")
                    Instant.now()
                }
            }
            val announcer = NotificationAnnouncer(baseline)
            repository.changes()
                .catch { Log.w(TAG, "Notification realtime stopped; the list still loads when the app is opened", it) }
                .collect {
                    when (val result = repository.notifications()) {
                        is AppResult.Success -> {
                            val fresh = announcer.select(result.value)
                            if (fresh.isNotEmpty() && !isInForeground()) fresh.forEach(::show)
                        }
                        is AppResult.Failure -> Log.w(TAG, "Notifications not reloaded: ${result.error}")
                    }
                }
        }
    }

    /** Stops listening and removes this account's notifications from the shade (sign-out). */
    fun stop() {
        job?.cancel()
        job = null
        NotificationManagerCompat.from(context).cancelAll()
    }

    private suspend fun isInForeground(): Boolean = withContext(Dispatchers.Main.immediate) {
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }

    private fun show(notification: AppNotification) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val intent = Intent(context, MainActivity::class.java)
            .setAction(AppConfig.ACTION_OPEN_NOTIFICATIONS)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val built = NotificationCompat.Builder(context, AppConfig.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text(notification))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        try {
            manager.notify(notification.id.hashCode(), built)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission was revoked", e)
        }
    }

    // Message content is never shown: the lock screen is not private.
    private fun text(notification: AppNotification): String {
        val who = notification.actorName ?: context.getString(R.string.author_unknown)
        return when (notification.kind) {
            NotificationKind.NEW_MESSAGE -> context.getString(R.string.notification_new_message, who)
            NotificationKind.NEW_COMMENT -> context.getString(R.string.notification_new_comment, who)
            NotificationKind.MENTIONED -> context.getString(R.string.notification_mentioned, who)
            NotificationKind.VERIFICATION_APPROVED -> context.getString(R.string.notification_verification_approved)
            NotificationKind.VERIFICATION_REJECTED -> context.getString(R.string.notification_verification_rejected)
            NotificationKind.CONTENT_REMOVED -> context.getString(R.string.notification_content_removed)
            NotificationKind.ACCOUNT_SUSPENDED -> context.getString(R.string.notification_account_suspended)
            NotificationKind.APPEAL_DECIDED -> context.getString(R.string.notification_appeal_decided)
            NotificationKind.DSR_ANSWERED -> context.getString(R.string.notification_dsr_answered)
        }
    }

    private companion object {
        const val TAG = "BackgroundNotifier"
    }
}
