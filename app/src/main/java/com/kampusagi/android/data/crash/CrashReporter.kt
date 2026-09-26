package com.kampusagi.android.data.crash

import android.content.Context
import android.os.Build
import android.util.Log
import com.kampusagi.android.BuildConfig
import com.kampusagi.android.core.di.ApplicationScope
import com.kampusagi.android.data.remote.SupabaseProvider
import com.kampusagi.android.data.remote.safeCall
import com.kampusagi.android.data.remote.toAppError
import com.kampusagi.android.domain.model.AppError
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.postgrest.postgrest
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Records fatal crashes on the device and sends them to Supabase
 * (`report_client_error`) once someone is signed in (D31).
 */
@Singleton
class CrashReporter @Inject constructor(
    @ApplicationContext context: Context,
    private val provider: SupabaseProvider,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val store = CrashStore(File(context.filesDir, DIRECTORY))
    private val sending = Mutex()

    /** Called once from Application.onCreate. */
    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                store.save(
                    CrashReport.from(
                        error,
                        appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        androidSdk = Build.VERSION.SDK_INT,
                        deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                        now = Instant.now(),
                    ),
                )
            } catch (e: Throwable) {
                // The process is dying; the crash itself is still passed on below.
                Log.e(TAG, "Crash report could not be written", e)
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Sends stored reports for the signed-in person; kept on the device if sending fails. */
    fun sendPending() {
        val client = provider.client ?: return
        scope.launch {
            sending.withLock {
                for ((file, report) in store.pending()) {
                    val result = safeCall { client.postgrest.rpc("report_client_error", report.toParams()) }
                    val error = result.exceptionOrNull()?.toAppError()
                    when {
                        error == null -> file.delete()
                        // The server will never accept it: do not retry forever.
                        error == AppError.INVALID_INPUT -> {
                            Log.w(TAG, "Crash report rejected as invalid; removed")
                            file.delete()
                        }
                        else -> {
                            Log.w(TAG, "Crash reports not sent ($error); retried on next sign-in")
                            return@withLock
                        }
                    }
                }
            }
        }
    }

    private fun CrashReport.toParams() = buildJsonObject {
        put("p_app_version", appVersion)
        put("p_android_sdk", androidSdk)
        put("p_device_model", deviceModel)
        put("p_exception_type", exceptionType)
        put("p_message", message)
        put("p_stacktrace", stacktrace)
        put("p_occurred_at", occurredAt)
    }

    private companion object {
        const val DIRECTORY = "crash-reports"
        const val TAG = "CrashReporter"
    }
}
