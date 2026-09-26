package com.kampusagi.android.domain.usecase

import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.repository.AdminRepository
import com.kampusagi.android.domain.repository.DocumentReader
import com.kampusagi.android.domain.repository.VerificationRepository
import javax.inject.Inject

/**
 * The same checks the `submit-student-document` Edge Function makes, so an
 * obviously wrong file is refused before it is uploaded. The server decides.
 */
object StudentDocumentValidator {
    /** Same limit as the storage bucket and the Edge Function. */
    const val MAX_BYTES = 10 * 1024 * 1024
    private const val HEADER_WINDOW = 1024
    private val PDF_HEADER = "%PDF-".toByteArray(Charsets.ISO_8859_1)
    private val BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    fun check(bytes: ByteArray): AppError? {
        if (bytes.isEmpty()) return AppError.DOCUMENT_UNREADABLE
        if (bytes.size > MAX_BYTES) return AppError.DOCUMENT_TOO_LARGE
        var start = 0
        if (bytes.size >= BOM.size && bytes.copyOfRange(0, BOM.size).contentEquals(BOM)) start = BOM.size
        val limit = minOf(bytes.size, HEADER_WINDOW)
        while (start < limit && bytes[start].toInt().toChar().isWhitespace()) start++
        if (start + PDF_HEADER.size > bytes.size) return AppError.DOCUMENT_NOT_PDF
        return if (bytes.copyOfRange(start, start + PDF_HEADER.size).contentEquals(PDF_HEADER)) null else AppError.DOCUMENT_NOT_PDF
    }
}

class SubmitStudentDocumentUseCase @Inject constructor(
    private val documentReader: DocumentReader,
    private val verificationRepository: VerificationRepository,
) {
    suspend operator fun invoke(uri: String): AppResult<Unit> {
        // Read one byte more than allowed so an oversized file is detected without loading it all.
        val bytes = when (val read = documentReader.read(uri, StudentDocumentValidator.MAX_BYTES + 1)) {
            is AppResult.Success -> read.value
            is AppResult.Failure -> return read
        }
        StudentDocumentValidator.check(bytes)?.let { return AppResult.Failure(it) }
        return verificationRepository.submitDocument(bytes)
    }
}

object RejectionReasonValidator {
    val LENGTH = 3..500

    fun isValid(reason: String): Boolean = reason.trim().length in LENGTH
}

class ReviewVerificationUseCase @Inject constructor(private val adminRepository: AdminRepository) {
    suspend operator fun invoke(verificationId: String, approve: Boolean, reason: String): AppResult<Unit> {
        if (!approve && !RejectionReasonValidator.isValid(reason)) {
            return AppResult.Failure(AppError.REJECTION_REASON_REQUIRED)
        }
        return adminRepository.review(verificationId, approve, if (approve) null else reason.trim())
    }
}
