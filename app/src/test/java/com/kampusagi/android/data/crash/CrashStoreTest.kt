package com.kampusagi.android.data.crash

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CrashStoreTest {

    @get:Rule val folder = TemporaryFolder()

    private fun report(n: Int) = CrashReport("1.0.0 (1)", 34, "Test Device", "E$n", "message $n", "trace $n", "2026-09-26T12:00:00Z")

    @Test
    fun `reports come back oldest first and survive a new store instance`() {
        val directory = folder.newFolder("crashes")
        CrashStore(directory).apply {
            save(report(1))
            Thread.sleep(2)
            save(report(2))
        }
        assertEquals(listOf("E1", "E2"), CrashStore(directory).pending().map { it.second.exceptionType })
    }

    @Test
    fun `only the newest reports are kept`() {
        val store = CrashStore(folder.newFolder("crashes"))
        repeat(CrashStore.MAX_PENDING + 3) {
            store.save(report(it))
            Thread.sleep(2)
        }
        val kept = store.pending().map { it.second.exceptionType }
        assertEquals(CrashStore.MAX_PENDING, kept.size)
        assertEquals("E3", kept.first())
        assertEquals("E${CrashStore.MAX_PENDING + 2}", kept.last())
    }

    @Test
    fun `a sent report is deleted by removing its file`() {
        val store = CrashStore(folder.newFolder("crashes"))
        store.save(report(1))
        store.pending().single().first.delete()
        assertTrue(store.pending().isEmpty())
    }

    @Test
    fun `a missing directory means nothing is pending`() {
        assertTrue(CrashStore(folder.root.resolve("never-created")).pending().isEmpty())
    }

    @Test
    fun `reports are built from the throwable and trimmed to the server limits`() {
        val error = IllegalStateException("x".repeat(CrashReport.MAX_MESSAGE + 50))
        val built = CrashReport.from(error, "1.0.0 (7)", 34, "Pixel 8", Instant.parse("2026-09-26T12:00:00Z"))
        assertEquals("java.lang.IllegalStateException", built.exceptionType)
        assertEquals(CrashReport.MAX_MESSAGE, built.message?.length)
        assertTrue(built.stacktrace.length <= CrashReport.MAX_STACKTRACE)
        assertTrue(built.stacktrace.startsWith("java.lang.IllegalStateException"))
        assertEquals("2026-09-26T12:00:00Z", built.occurredAt)
    }
}
