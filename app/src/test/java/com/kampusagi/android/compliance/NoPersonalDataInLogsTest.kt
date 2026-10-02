package com.kampusagi.android.compliance

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * KVKK md.12: application logs (Logcat, and through it any crash report) must never contain
 * e-mail addresses, phone numbers, passwords, tokens, document contents or message bodies.
 * This scans every Log.x(...) call in the app sources for arguments that name such data.
 */
class NoPersonalDataInLogsTest {

    private val logCall = Regex("""Log\.[vdiwe]\s*\((.*)""")

    /** Identifiers that hold personal or secret data in this code base. */
    private val forbidden = Regex(
        """\$\{?\s*(email|password|phone|token|purchaseToken|body|content|text|draft|bytes|pdf|details|fullName|full_name|ip|path|userId|uri)\b(?!\.error)""",
        RegexOption.IGNORE_CASE,
    )

    @Test
    fun `log calls never interpolate personal data`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main/java") }
            .firstOrNull { it.isDirectory }
            ?: generateSequence(File("").absoluteFile) { it.parentFile }.map { File(it, "src/main/java") }.first { it.isDirectory }
        val offenders = root.walkTopDown()
            .filter { it.extension == "kt" }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    val call = logCall.find(line)?.groupValues?.get(1) ?: return@mapIndexedNotNull null
                    if (forbidden.containsMatchIn(call)) "${file.relativeTo(root)}:${index + 1}: ${line.trim()}" else null
                }
            }
            .toList()
        assertEquals("Log calls with personal data:\n" + offenders.joinToString("\n"), emptyList<String>(), offenders)
    }
}
