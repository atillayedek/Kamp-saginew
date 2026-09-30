package com.kampusagi.android.data.repository

import android.content.Context
import android.util.Log
import com.kampusagi.android.core.config.AppConfig
import com.kampusagi.android.data.remote.CourseNoteDto
import com.kampusagi.android.data.remote.NoteCourseDto
import com.kampusagi.android.data.remote.ReputationDto
import com.kampusagi.android.data.remote.SupabaseProvider
import com.kampusagi.android.data.remote.functionError
import com.kampusagi.android.data.remote.safeCall
import com.kampusagi.android.data.remote.toAppError
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.Author
import com.kampusagi.android.domain.model.Badge
import com.kampusagi.android.domain.model.CourseNote
import com.kampusagi.android.domain.model.NewCourseNote
import com.kampusagi.android.domain.model.NoteCourse
import com.kampusagi.android.domain.model.Reputation
import com.kampusagi.android.domain.repository.NoteRepository
import com.kampusagi.android.domain.repository.ReputationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Singleton
class NoteRepositoryImpl @Inject constructor(
    private val provider: SupabaseProvider,
    @ApplicationContext private val context: Context,
) : NoteRepository {

    override suspend fun notes(courseCode: String?, query: String?): AppResult<List<CourseNote>> = call { client ->
        client.postgrest.rpc(
            "list_course_notes",
            buildJsonObject {
                put("p_course_code", courseCode)
                put("p_query", query?.trim()?.ifEmpty { null })
            },
        ).decodeList<CourseNoteDto>().map {
            CourseNote(
                id = it.noteId,
                courseCode = it.courseCode,
                courseName = it.courseName,
                title = it.title,
                description = it.description,
                fileSize = it.fileSize,
                downloadCount = it.downloadCount,
                createdAt = it.createdAt,
                isMine = it.isMine,
                author = Author(it.authorId, it.authorFullName, it.authorUsername, university = null),
            )
        }
    }

    override suspend fun courses(): AppResult<List<NoteCourse>> = call { client ->
        client.postgrest.rpc("list_note_courses", JsonObject(emptyMap())).decodeList<NoteCourseDto>().map {
            NoteCourse(it.courseCode, it.courseName, it.noteCount)
        }
    }

    override suspend fun upload(note: NewCourseNote): AppResult<Unit> {
        val client = provider.client ?: return AppResult.Failure(AppError.NOT_CONFIGURED)
        val userId = client.auth.currentUserOrNull()?.id ?: return AppResult.Failure(AppError.SESSION_EXPIRED)
        val path = "$userId/${UUID.randomUUID()}.pdf"
        safeCall {
            client.storage.from(AppConfig.COURSE_NOTES_BUCKET).upload(path, note.pdf) {
                upsert = false
                contentType = ContentType.Application.Pdf
            }
        }.onFailure { return AppResult.Failure(it.toAppError()) }

        // The function checks the bytes and records the note; it removes the file itself when it refuses.
        val response = safeCall {
            client.functions.invoke(
                function = AppConfig.SUBMIT_COURSE_NOTE_FUNCTION,
                body = buildJsonObject {
                    put("path", path)
                    put("course_code", note.courseCode.trim())
                    put("course_name", note.courseName.trim())
                    put("title", note.title.trim())
                    put("description", note.description?.trim()?.ifEmpty { null })
                },
            )
        }.getOrElse { return AppResult.Failure(it.toAppError()) }
        if (!response.status.isSuccess()) {
            val body = safeCall { response.bodyAsText() }.getOrDefault("")
            return AppResult.Failure(functionError(response.status.value, body))
        }
        return AppResult.Success(Unit)
    }

    /**
     * Kept in the app's private cache under one fixed name, so only the note
     * being read is stored on the device and a PDF viewer can open it.
     */
    override suspend fun open(noteId: String): AppResult<File> {
        val client = provider.client ?: return AppResult.Failure(AppError.NOT_CONFIGURED)
        val path = safeCall {
            client.postgrest.rpc("open_course_note", JsonObject(mapOf("p_note_id" to JsonPrimitive(noteId)))).decodeAs<String>()
        }.getOrElse { return AppResult.Failure(it.toAppError()) }
        val bytes = safeCall { client.storage.from(AppConfig.COURSE_NOTES_BUCKET).downloadAuthenticated(path) }
            .getOrElse { return AppResult.Failure(it.toAppError()) }
        return withContext(Dispatchers.IO) {
            safeCall {
                val directory = File(context.cacheDir, NOTES_DIRECTORY).apply { mkdirs() }
                File(directory, NOTE_FILE).apply { writeBytes(bytes) }
            }.fold(
                onSuccess = { AppResult.Success(it) },
                onFailure = {
                    Log.w(TAG, "Note could not be written to the cache", it)
                    AppResult.Failure(AppError.DOCUMENT_UNREADABLE)
                },
            )
        }
    }

    override suspend fun delete(noteId: String): AppResult<Unit> = call { client ->
        client.postgrest.rpc("delete_course_note", JsonObject(mapOf("p_note_id" to JsonPrimitive(noteId))))
        Unit
    }

    private suspend fun <T> call(block: suspend (SupabaseClient) -> T): AppResult<T> {
        val client = provider.client ?: return AppResult.Failure(AppError.NOT_CONFIGURED)
        return safeCall { block(client) }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { AppResult.Failure(it.toAppError()) },
        )
    }

    companion object {
        /** Must match res/xml/file_paths.xml. */
        const val NOTES_DIRECTORY = "notes"
        private const val NOTE_FILE = "note.pdf"
        private const val TAG = "NoteRepository"
    }
}

@Singleton
class ReputationRepositoryImpl @Inject constructor(
    private val provider: SupabaseProvider,
) : ReputationRepository {

    override suspend fun reputation(userId: String): AppResult<Reputation> {
        val client = provider.client ?: return AppResult.Failure(AppError.NOT_CONFIGURED)
        return safeCall {
            val row = client.postgrest.rpc("user_badges", JsonObject(mapOf("p_user_id" to JsonPrimitive(userId))))
                .decodeList<ReputationDto>()
                .first()
            // Badges added on the server later are skipped until the app knows them.
            Reputation(row.points, row.badges.mapNotNull { name -> Badge.entries.firstOrNull { it.name == name } })
        }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { AppResult.Failure(it.toAppError()) },
        )
    }
}
