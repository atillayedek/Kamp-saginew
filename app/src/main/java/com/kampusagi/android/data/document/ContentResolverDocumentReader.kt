package com.kampusagi.android.data.document

import android.content.Context
import android.net.Uri
import android.util.Log
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.repository.DocumentReader
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class ContentResolverDocumentReader @Inject constructor(
    @ApplicationContext private val context: Context,
) : DocumentReader {

    /** Reads at most [maxBytes] bytes, so a huge file never fills memory. */
    override suspend fun read(uri: String, maxBytes: Int): AppResult<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val stream = context.contentResolver.openInputStream(Uri.parse(uri))
                ?: return@withContext AppResult.Failure(AppError.DOCUMENT_UNREADABLE)
            stream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(BUFFER_SIZE)
                while (output.size() < maxBytes) {
                    val read = input.read(buffer, 0, minOf(buffer.size, maxBytes - output.size()))
                    if (read < 0) break
                    output.write(buffer, 0, read)
                }
                AppResult.Success(output.toByteArray())
            }
        } catch (e: IOException) {
            Log.w(TAG, "Picked document could not be read", e)
            AppResult.Failure(AppError.DOCUMENT_UNREADABLE)
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission to read the picked document", e)
            AppResult.Failure(AppError.DOCUMENT_UNREADABLE)
        }
    }

    private companion object {
        const val TAG = "DocumentReader"
        const val BUFFER_SIZE = 64 * 1024
    }
}
