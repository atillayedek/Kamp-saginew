package com.kampusagi.android.domain.usecase

import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.NewPost
import com.kampusagi.android.domain.model.PostCategory
import com.kampusagi.android.domain.repository.CommunityRepository
import java.util.Locale
import javax.inject.Inject

/** Same limits as `create_post` and `add_comment` in the database. */
object PostTextValidator {
    const val MAX_POST_LENGTH = 2000
    const val MAX_COMMENT_LENGTH = 1000

    fun isValidPost(body: String): Boolean = body.trim().length in 1..MAX_POST_LENGTH

    fun isValidComment(body: String): Boolean = body.trim().length in 1..MAX_COMMENT_LENGTH
}

/** Same rules `create_post` enforces for photos, polls, events and prices. */
object NewPostValidator {
    const val MAX_PHOTOS = 4
    const val MIN_POLL_OPTIONS = 2
    const val MAX_POLL_OPTIONS = 4
    const val MAX_POLL_OPTION_LENGTH = 80
    const val MAX_LOCATION_LENGTH = 120
    const val MAX_PRICE_KURUS = 1_000_000_000L

    private val turkish = Locale.forLanguageTag("tr")

    fun isValidPollOptions(options: List<String>): Boolean {
        val trimmed = options.map { it.trim() }
        return trimmed.size in MIN_POLL_OPTIONS..MAX_POLL_OPTIONS &&
            trimmed.all { it.length in 1..MAX_POLL_OPTION_LENGTH } &&
            trimmed.map { it.lowercase(turkish) }.toSet().size == trimmed.size
    }

    fun isValid(post: NewPost): Boolean =
        PostTextValidator.isValidPost(post.body) &&
            post.photos.size <= MAX_PHOTOS &&
            (post.pollOptions == null || isValidPollOptions(post.pollOptions)) &&
            (post.pollClosesAt == null || post.pollOptions != null) &&
            (post.eventStartsAt == null || post.category == PostCategory.EVENT) &&
            (post.eventLocation == null || (post.category == PostCategory.EVENT && post.eventLocation.trim().length in 1..MAX_LOCATION_LENGTH)) &&
            (post.priceKurus == null || (post.category == PostCategory.MARKETPLACE && post.priceKurus in 0..MAX_PRICE_KURUS))
}

/** Turkish lira amounts as people type them ("1.250", "99,90") and as the app shows them. */
object PriceFormat {
    /** Kuruş for the typed amount, or null if it is not a valid price. */
    fun parseLira(text: String): Long? {
        val cleaned = text.trim().removeSuffix("TL").removeSuffix("₺").trim().replace(" ", "")
        if (cleaned.isEmpty()) return null
        val match = Regex("^(\\d{1,3}(?:\\.\\d{3})+|\\d+)(?:,(\\d{1,2}))?$").matchEntire(cleaned) ?: return null
        val lira = match.groupValues[1].replace(".", "").toLongOrNull() ?: return null
        val fraction = match.groupValues[2].padEnd(2, '0').ifEmpty { "00" }.toLong()
        val kurus = lira.checkedTimes100()?.plus(fraction) ?: return null
        return kurus.takeIf { it in 0..NewPostValidator.MAX_PRICE_KURUS }
    }

    /** "1.250 ₺", "99,90 ₺"; free items are labelled by the caller. */
    fun format(kurus: Long): String {
        val lira = kurus / 100
        val fraction = kurus % 100
        val grouped = lira.toString().reversed().chunked(3).joinToString(".").reversed()
        return if (fraction == 0L) "$grouped ₺" else "$grouped,${fraction.toString().padStart(2, '0')} ₺"
    }

    private fun Long.checkedTimes100(): Long? = if (this > Long.MAX_VALUE / 100) null else this * 100
}

class CreatePostUseCase @Inject constructor(private val repository: CommunityRepository) {
    suspend operator fun invoke(post: NewPost): AppResult<String> {
        val clean = post.copy(
            body = post.body.trim(),
            pollOptions = post.pollOptions?.map { it.trim() },
            eventLocation = post.eventLocation?.trim()?.ifEmpty { null },
        )
        if (!NewPostValidator.isValid(clean)) return AppResult.Failure(AppError.INVALID_INPUT)
        return repository.createPost(clean)
    }
}

class AddCommentUseCase @Inject constructor(private val repository: CommunityRepository) {
    suspend operator fun invoke(postId: String, body: String): AppResult<Unit> {
        if (!PostTextValidator.isValidComment(body)) return AppResult.Failure(AppError.INVALID_INPUT)
        return repository.addComment(postId, body.trim())
    }
}
