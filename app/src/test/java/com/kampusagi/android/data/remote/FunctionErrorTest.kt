package com.kampusagi.android.data.remote

import com.kampusagi.android.domain.model.AppError
import org.junit.Assert.assertEquals
import org.junit.Test

class FunctionErrorTest {

    @Test
    fun `maps edge function error codes`() {
        assertEquals(AppError.INVALID_INPUT, functionError(400, """{"error":"invalid_document_path"}"""))
        assertEquals(AppError.DOCUMENT_TOO_LARGE, functionError(422, """{"error":"document_too_large"}"""))
        assertEquals(AppError.DOCUMENT_NOT_PDF, functionError(422, """{"error":"invalid_document"}"""))
        assertEquals(AppError.VERIFICATION_NOT_ALLOWED, functionError(409, """{"error":"verification_not_allowed"}"""))
        assertEquals(AppError.SESSION_EXPIRED, functionError(401, ""))
        assertEquals(AppError.SERVER, functionError(500, """{"error":"server_error"}"""))
        assertEquals(AppError.UNKNOWN, functionError(418, "teapot"))
    }
}

class AiErrorMappingTest {
    @Test
    fun `maps ai error codes`() {
        assertEquals(AppError.AI_NOT_CONFIGURED, functionError(503, """{"error":"ai_not_configured"}"""))
        assertEquals(AppError.AI_QUOTA_EXCEEDED, functionError(429, """{"error":"ai_quota_exceeded"}"""))
        assertEquals(AppError.AI_REFUSED, functionError(422, """{"error":"ai_refused"}"""))
        assertEquals(AppError.AI_FAILED, functionError(502, """{"error":"ai_failed"}"""))
        assertEquals(AppError.INVALID_INPUT, functionError(422, """{"error":"invalid_requirement"}"""))
        assertEquals(AppError.TOO_MANY_ACTIVE_REQUIREMENTS, functionError(409, """{"error":"too_many_active_requirements"}"""))
        assertEquals(AppError.ACCOUNT_NOT_APPROVED, functionError(403, """{"error":"approved_student_required"}"""))
    }
}
