package com.kampusagi.android.presentation.common.avatar

import java.util.Locale

/** Letters and colour of the automatic avatar shown until someone adds a photo. */
object AvatarInitials {

    private val turkish = Locale.forLanguageTag("tr")

    /** First letters of the first and last word of the name; the username's first letter otherwise. */
    fun of(fullName: String?, username: String?): String {
        val words = fullName.orEmpty().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val letters = when {
            words.size >= 2 -> "${words.first().first()}${words.last().first()}"
            words.size == 1 -> words.first().take(1)
            !username.isNullOrBlank() -> username.trim().take(1)
            else -> "?"
        }
        return letters.uppercase(turkish)
    }

    /** Stable index into the avatar palette, so a person always gets the same colour. */
    fun colorIndex(userId: String, paletteSize: Int): Int {
        require(paletteSize > 0)
        return Math.floorMod(userId.hashCode(), paletteSize)
    }
}
