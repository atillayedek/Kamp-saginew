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
            // Exception messages can contain HTTP tokens, document paths and
            // user input. Retain only class names and stack frames.
            message = null,
            stacktrace = safeStacktrace(error).take(MAX_STACKTRACE),
            occurredAt = now.toString(),
        )

        private fun safeStacktrace(error: Throwable): String = buildString {
            val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
            var current: Throwable? = error
            while (current != null && seen.add(current) && length < MAX_STACKTRACE) {
                if (isNotEmpty()) append("Caused by: ")
                append(current.javaClass.name).append('\n')
                current.stackTrace.take(100).forEach { append("\tat ").append(it).append('\n') }
                current = current.cause
            }
        }
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
            val report = json.decodeFromString<CrashReport>(file.readText())
            // Reports saved by older versions also pass through the privacy
            // boundary before any upload.
            file to report.copy(message = null, stacktrace = buildString {
                append(report.exceptionType).append('\n')
                report.stacktrace.lineSequence().filter { it.trimStart().startsWith("at ") }
                    .forEach { append(it).append('\n') }
            }.take(CrashReport.MAX_STACKTRACE))
        } catch (e: IOException) {
            Log.w(TAG, "Unreadable crash report removed")
            file.delete()
            null
        } catch (e: SerializationException) {
            Log.w(TAG, "Malformed crash report removed")
            file.delete()
            null
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Malformed crash report removed")
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
