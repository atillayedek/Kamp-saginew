package com.kampusagi.android.domain.usecase

/**
 * Finds @mentions and #tags in post and comment text so they can be shown as links.
 *
 * The patterns match the database (`extract_usernames` / `extract_tags` in
 * `20261003100200_mentions_tags.sql`): the server records the same mentions and tags the app
 * underlines. An "@" or "#" glued to a word (e-mail addresses, "C#") is not a link.
 */
object BodyLinks {

    sealed interface Segment {
        data class Plain(val text: String) : Segment

        /** [username] is lower-case without "@", as typed; a sentence-ending "." is not part of [text]. */
        data class Mention(val text: String, val username: String) : Segment

        /** [key] is the folded tag the server stores ("#Kampüs" -> "kampus"). */
        data class Tag(val text: String, val key: String) : Segment
    }

    private const val LETTERS = "A-Za-z0-9_çğıöşüÇĞİÖŞÜâîûÂÎÛ"
    private val MENTION = Regex("(?<![A-Za-z0-9_.@])@([A-Za-z0-9_.]{3,31})")
    private val TAG = Regex("(?<![$LETTERS&#])#([$LETTERS]{2,40})")
    private val USERNAME = Regex("^[a-z0-9_.]{3,30}$")
    private val TAG_KEY = Regex("^[a-z0-9_]{2,40}$")

    /** Splits [text] into plain parts and links, in order; joining every segment's text gives [text] back. */
    fun parse(text: String): List<Segment> {
        val links = (mentions(text) + tags(text)).sortedBy { it.first.first }
        val segments = mutableListOf<Segment>()
        var position = 0
        for ((range, segment) in links) {
            if (range.first < position) continue
            if (range.first > position) segments += Segment.Plain(text.substring(position, range.first))
            segments += segment
            position = range.last + 1
        }
        if (position < text.length) segments += Segment.Plain(text.substring(position))
        return segments
    }

    /** Folded tag key as the server stores it, or null when it is not a valid tag. */
    fun tagKey(raw: String): String? = fold(raw.trim().removePrefix("#")).takeIf { TAG_KEY.matches(it) && it.any(Char::isLetter) }

    /**
     * The "@word" being typed at the end of [text] (without "@"), or null. Used to offer people
     * while writing; an empty string means "@" was just typed.
     */
    fun mentionBeingTyped(text: String): String? {
        val match = Regex("(?:^|[^A-Za-z0-9_.@])@([A-Za-z0-9_.]{0,30})$").find(text) ?: return null
        return match.groupValues[1].lowercase()
    }

    /** Replaces the "@word" at the end of [text] with "@[username] ". */
    fun completeMention(text: String, username: String): String {
        val typed = mentionBeingTyped(text) ?: return text
        return text.dropLast(typed.length + 1) + "@" + username + " "
    }

    private fun mentions(text: String): List<Pair<IntRange, Segment>> = MENTION.findAll(text).mapNotNull { match ->
        val raw = match.groupValues[1].lowercase()
        // "@ayse." at the end of a sentence: the dot is not underlined. The server tries the name
        // with and without it (resolve_username), so a name that really ends in "." still opens.
        val trimmed = raw.trimEnd('.')
        if (!USERNAME.matches(raw) && !USERNAME.matches(trimmed)) return@mapNotNull null
        val end = match.range.first + 1 + trimmed.length
        (match.range.first until end) to Segment.Mention(text.substring(match.range.first, end), raw)
    }.toList()

    private fun tags(text: String): List<Pair<IntRange, Segment>> = TAG.findAll(text).mapNotNull { match ->
        val key = tagKey(match.groupValues[1]) ?: return@mapNotNull null
        match.range to Segment.Tag(match.value, key)
    }.toList()

    /** Same folding as the database's `search_fold`. */
    fun fold(text: String): String {
        val from = "İIıÇçĞğÖöŞşÜüÂâÎîÛû"
        val to = "iiiccggoossuuaaiiuu"
        return buildString(text.length) {
            for (c in text) {
                val i = from.indexOf(c)
                append(if (i >= 0) to[i] else c.lowercaseChar())
            }
        }
    }
}
