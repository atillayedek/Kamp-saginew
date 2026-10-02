package com.kampusagi.android.data.crash

import android.util.Log
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** One fatal crash, as stored on the device and sent to `report_client_error`. */
@Serializable
data class CrashReport(
    val appVersion: String,
    val androidSdk: Int,
    val deviceModel: String,
    val exceptionType: String,
    val message: String?,
    val stacktrace: String,
    val occurredAt: String,
) {
    companion object {
        const val MAX_MESSAGE = 1000
        const val MAX_STACKTRACE = 16000

        fun from(error: Throwable, appVersion: String, androidSdk: Int, deviceModel: String, now: Instant) = CrashReport(
            appVersion = appVersion,
            androidSdk = androidSdk,
            deviceModel = deviceModel,
            exceptionType = error.javaClass.name,
            message = error.message?.take(MAX_MESSAGE),
            stacktrace = error.stackTraceToString().take(MAX_STACKTRACE),
            occurredAt = now.toString(),
        )
    }
}

/**
 * Crash reports kept in the app's private storage until they are sent. Writing
 * happens on the crashing thread, so it is small and synchronous; only the
 * newest [MAX_PENDING] reports are kept.
 */
class CrashStore(private val directory: File) {

    fun save(report: CrashReport) {
        directory.mkdirs()
        File(directory, "${System.currentTimeMillis()}-${UUID.randomUUID()}.json").writeText(json.encodeToString(report))
        files().dropLast(MAX_PENDING).forEach { it.delete() }
    }

    /** Oldest first. Files that cannot be read are removed: they can never be sent. */
    fun pending(): List<Pair<File, CrashReport>> = files().mapNotNull { file ->
        try {
            file to json.decodeFromString<CrashReport>(file.readText())
        } catch (e: IOException) {
            Log.w(TAG, "Unreadable crash report removed", e)
            file.delete()
            null
        } catch (e: SerializationException) {
            Log.w(TAG, "Malformed crash report removed", e)
            file.delete()
            null
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Malformed crash report removed", e)
            file.delete()
            null
        }
    }

    private fun files(): List<File> =
        directory.listFiles { file -> file.isFile && file.name.endsWith(".json") }?.sortedBy { it.name }.orEmpty()

    companion object {
        const val MAX_PENDING = 10
        private const val TAG = "CrashStore"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
