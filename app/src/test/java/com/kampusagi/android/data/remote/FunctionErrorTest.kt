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
