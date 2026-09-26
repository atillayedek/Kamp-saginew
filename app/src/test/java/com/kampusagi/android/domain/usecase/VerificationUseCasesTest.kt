package com.kampusagi.android.domain.usecase

import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.PendingVerification
import com.kampusagi.android.domain.model.Verification
import com.kampusagi.android.domain.repository.AdminRepository
import com.kampusagi.android.domain.repository.DocumentReader
import com.kampusagi.android.domain.repository.VerificationRepository
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private fun pdf(size: Int = 64, prefix: ByteArray = "%PDF-1.7\n".toByteArray()): ByteArray =
    ByteArray(size).also { prefix.copyInto(it) }

class StudentDocumentValidatorTest {

    @Test
    fun `accepts a pdf header`() {
        assertNull(StudentDocumentValidator.check(pdf()))
    }

    @Test
    fun `accepts whitespace or a bom before the header`() {
        assertNull(StudentDocumentValidator.check(pdf(prefix = "\n  %PDF-1.4".toByteArray())))
        assertNull(StudentDocumentValidator.check(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "%PDF-1.5".toByteArray()))
    }

    @Test
    fun `rejects other files whatever their name`() {
        assertEquals(AppError.DOCUMENT_NOT_PDF, StudentDocumentValidator.check("<html>%PDF-1.4".toByteArray()))
        assertEquals(AppError.DOCUMENT_NOT_PDF, StudentDocumentValidator.check(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)))
        assertEquals(AppError.DOCUMENT_NOT_PDF, StudentDocumentValidator.check("%PD".toByteArray()))
        assertEquals(AppError.DOCUMENT_UNREADABLE, StudentDocumentValidator.check(ByteArray(0)))
    }

    @Test
    fun `size limit matches the backend`() {
        assertNull(StudentDocumentValidator.check(pdf(StudentDocumentValidator.MAX_BYTES)))
        assertEquals(AppError.DOCUMENT_TOO_LARGE, StudentDocumentValidator.check(pdf(StudentDocumentValidator.MAX_BYTES + 1)))
    }
}

private class ScriptedReader(private val result: AppResult<ByteArray>) : DocumentReader {
    var requestedMax = 0
    override suspend fun read(uri: String, maxBytes: Int): AppResult<ByteArray> = result.also { requestedMax = maxBytes }
}

private class RecordingVerificationRepository : VerificationRepository {
    var submitted: ByteArray? = null
    override suspend fun submitDocument(bytes: ByteArray): AppResult<Unit> = AppResult.Success(Unit).also { submitted = bytes }
    override suspend fun latestVerification(): AppResult<Verification?> = AppResult.Success(null)
}

private class RecordingAdminRepository : AdminRepository {
    var lastReview: Triple<String, Boolean, String?>? = null
    override suspend fun pendingVerifications(): AppResult<List<PendingVerification>> = AppResult.Success(emptyList())
    override suspend fun downloadDocument(path: String): AppResult<File> = AppResult.Failure(AppError.NOT_FOUND)
    override suspend fun review(verificationId: String, approve: Boolean, reason: String?): AppResult<Unit> =
        AppResult.Success(Unit).also { lastReview = Triple(verificationId, approve, reason) }
}

class VerificationUseCasesTest {

    @Test
    fun `reads one byte over the limit and uploads a valid pdf`() = runTest {
        val reader = ScriptedReader(AppResult.Success(pdf()))
        val repository = RecordingVerificationRepository()
        assertEquals(AppResult.Success(Unit), SubmitStudentDocumentUseCase(reader, repository)("content://doc"))
        assertEquals(StudentDocumentValidator.MAX_BYTES + 1, reader.requestedMax)
        assertEquals(64, repository.submitted?.size)
    }

    @Test
    fun `an invalid file is never uploaded`() = runTest {
        val repository = RecordingVerificationRepository()
        val result = SubmitStudentDocumentUseCase(ScriptedReader(AppResult.Success("PK\u0003\u0004".toByteArray())), repository)("content://doc")
        assertEquals(AppResult.Failure(AppError.DOCUMENT_NOT_PDF), result)
        assertNull(repository.submitted)
    }

    @Test
    fun `read errors are passed through`() = runTest {
        val repository = RecordingVerificationRepository()
        val result = SubmitStudentDocumentUseCase(ScriptedReader(AppResult.Failure(AppError.DOCUMENT_UNREADABLE)), repository)("content://doc")
        assertEquals(AppResult.Failure(AppError.DOCUMENT_UNREADABLE), result)
        assertNull(repository.submitted)
    }

    @Test
    fun `rejection needs a reason, approval sends none`() = runTest {
        val repository = RecordingAdminRepository()
        val useCase = ReviewVerificationUseCase(repository)
        assertEquals(AppResult.Failure(AppError.REJECTION_REASON_REQUIRED), useCase("v1", approve = false, reason = "  x "))
        assertNull(repository.lastReview)
        useCase("v1", approve = false, reason = "  Belge okunmuyor ")
        assertEquals(Triple("v1", false, "Belge okunmuyor"), repository.lastReview)
        useCase("v2", approve = true, reason = "ignored")
        assertEquals(Triple("v2", true, null), repository.lastReview)
    }
}

class PostTextValidatorTest {
    @Test
    fun `post and comment limits match the database`() {
        assertEquals(false, PostTextValidator.isValidPost("   "))
        assertEquals(true, PostTextValidator.isValidPost("a".repeat(PostTextValidator.MAX_POST_LENGTH)))
        assertEquals(false, PostTextValidator.isValidPost("a".repeat(PostTextValidator.MAX_POST_LENGTH + 1)))
        assertEquals(true, PostTextValidator.isValidComment(" merhaba "))
        assertEquals(false, PostTextValidator.isValidComment("a".repeat(PostTextValidator.MAX_COMMENT_LENGTH + 1)))
    }
}
