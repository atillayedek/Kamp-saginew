package com.kampusagi.android.domain.usecase

/**
 * Notices personal data a person is about to share in a message or post, so the app can
 * warn them first ("Kişisel verilerini paylaşmak üzeresin"). Nothing is blocked; the text
 * never leaves the device for this check.
 */
object PersonalDataDetector {

    enum class Kind { TC_IDENTITY_NUMBER, IBAN, PHONE_NUMBER }

    private val ELEVEN_DIGITS = Regex("""(?<!\d)[1-9]\d{10}(?!\d)""")
    private val IBAN = Regex("""(?i)(?<![A-Z0-9])TR\s?\d{2}(?:\s?\d{4}){5}\s?\d{2}(?!\d)""")
    private val PHONE = Regex("""(?<!\d)(?:\+?90[\s-]?|0)?\(?5\d{2}\)?[\s-]?\d{3}[\s-]?\d{2}[\s-]?\d{2}(?!\d)""")

    fun find(text: String): Set<Kind> = buildSet {
        if (ELEVEN_DIGITS.findAll(text).any { isTcIdentityNumber(it.value) }) add(Kind.TC_IDENTITY_NUMBER)
        if (IBAN.findAll(text).any { isValidTrIban(it.value) }) add(Kind.IBAN)
        val withoutIban = IBAN.replace(text, " ")
        val withoutTc = ELEVEN_DIGITS.findAll(withoutIban).filter { isTcIdentityNumber(it.value) }
            .fold(withoutIban) { acc, m -> acc.replace(m.value, " ") }
        if (PHONE.containsMatchIn(withoutTc)) add(Kind.PHONE_NUMBER)
    }

    /** The official checksum of a Turkish identity number (T.C. kimlik no). */
    fun isTcIdentityNumber(value: String): Boolean {
        if (value.length != 11 || value[0] == '0' || !value.all(Char::isDigit)) return false
        val d = value.map { it - '0' }
        val odd = d[0] + d[2] + d[4] + d[6] + d[8]
        val even = d[1] + d[3] + d[5] + d[7]
        val tenth = Math.floorMod(odd * 7 - even, 10)
        return d[9] == tenth && d[10] == (d.take(10).sum() % 10)
    }

    /** ISO 13616 mod-97 check of a Turkish IBAN (26 characters). */
    fun isValidTrIban(value: String): Boolean {
        val iban = value.filterNot(Char::isWhitespace).uppercase()
        if (iban.length != 26 || !iban.startsWith("TR")) return false
        val rearranged = iban.substring(4) + iban.substring(0, 4)
        var remainder = 0
        for (c in rearranged) {
            val digits = if (c.isLetter()) (c - 'A' + 10).toString() else c.toString()
            for (digit in digits) remainder = (remainder * 10 + (digit - '0')) % 97
        }
        return remainder == 1
    }
}

/** Folds Turkish text the way the database does (search_fold): lower case, ASCII letters. */
object TurkishFold {
    private val MAP = mapOf('ç' to 'c', 'ğ' to 'g', 'ı' to 'i', 'ö' to 'o', 'ş' to 's', 'ü' to 'u', 'â' to 'a', 'î' to 'i', 'û' to 'u')

    fun fold(text: String): String =
        text.replace('İ', 'i').replace('I', 'ı').lowercase().map { MAP[it] ?: it }.joinToString("")

    /** Words and phrases from [terms] (already folded) that appear in [text] as whole words. */
    fun matches(text: String, terms: Collection<String>): List<String> {
        val words = " " + fold(text).replace(Regex("[^a-z0-9]+"), " ").trim() + " "
        return terms.filter { words.contains(" $it ") }
    }
}
