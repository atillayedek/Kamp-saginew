package com.kampusagi.android.domain.usecase

import java.util.Locale

/** Same limits as `create_group`, `create_course_note` and `normalize_course_code` in the database. */
object GroupRules {
    const val MAX_NAME = 60
    const val MAX_DESCRIPTION = 500
    const val MAX_NOTE_TITLE = 120
    const val MAX_COURSE_NAME = 120
    const val MAX_NOTE_BYTES = 20 * 1024 * 1024

    private val turkishFold = mapOf('İ' to 'I', 'ı' to 'i', 'Ç' to 'C', 'ç' to 'c', 'Ğ' to 'G', 'ğ' to 'g', 'Ö' to 'O', 'ö' to 'o', 'Ş' to 'S', 'ş' to 's', 'Ü' to 'U', 'ü' to 'u')

    fun isValidName(name: String): Boolean = name.trim().length in 2..MAX_NAME

    /** "blm 101" → "BLM101"; null when nothing valid remains (2–12 letters/digits). */
    fun normalizeCourseCode(code: String): String? {
        val folded = code.map { turkishFold[it] ?: it }.joinToString("").uppercase(Locale.ROOT)
        val cleaned = folded.filter { it in 'A'..'Z' || it in '0'..'9' }
        return cleaned.takeIf { it.length in 2..12 }
    }

    fun isValidNoteText(value: String, max: Int): Boolean = value.trim().length in 2..max
}
