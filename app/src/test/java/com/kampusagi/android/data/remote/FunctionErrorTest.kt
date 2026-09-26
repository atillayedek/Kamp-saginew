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

class BillingErrorMappingTest {
    @Test
    fun `maps purchase verification codes`() {
        assertEquals(AppError.BILLING_NOT_CONFIGURED, functionError(503, """{"error":"billing_not_configured"}"""))
        assertEquals(AppError.BILLING_UNAVAILABLE, functionError(502, """{"error":"billing_unavailable"}"""))
        assertEquals(AppError.PLAN_NOT_AVAILABLE, functionError(404, """{"error":"plan_not_available"}"""))
        assertEquals(AppError.PURCHASE_NOT_ACTIVE, functionError(422, """{"error":"purchase_not_active"}"""))
        assertEquals(AppError.PURCHASE_NOT_FOR_ACCOUNT, functionError(403, """{"error":"purchase_not_for_account"}"""))
        assertEquals(AppError.RECIPIENT_NOT_AVAILABLE, functionError(400, """{"error":"recipient_not_available"}"""))
    }
}

class ModerationErrorMappingTest {
    @Test
    fun `maps moderation and account deletion codes`() {
        assertEquals(AppError.NOT_FOUND, functionError(400, """{"message":"user_not_found"}"""))
        assertEquals(AppError.NOT_FOUND, functionError(400, """{"message":"report_target_not_found"}"""))
        assertEquals(AppError.NOT_FOUND, functionError(400, """{"message":"report_not_found"}"""))
        assertEquals(AppError.INVALID_INPUT, functionError(400, """{"message":"invalid_report"}"""))
        assertEquals(AppError.INVALID_INPUT, functionError(400, """{"message":"invalid_action"}"""))
        assertEquals(AppError.ADMIN_REQUIRED, functionError(400, """{"message":"admin_required"}"""))
        assertEquals(AppError.INVALID_INPUT, functionError(400, """{"error":"confirmation_required"}"""))
        assertEquals(AppError.SERVER, functionError(500, """{"error":"account_deletion_failed"}"""))
    }
}
