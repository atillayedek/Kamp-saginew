package com.kampusagi.android.domain.usecase

import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.PostCategory
import com.kampusagi.android.domain.model.PostScope
import com.kampusagi.android.domain.repository.CommunityRepository
import javax.inject.Inject

/** Same limits as `create_post` and `add_comment` in the database. */
object PostTextValidator {
    const val MAX_POST_LENGTH = 2000
    const val MAX_COMMENT_LENGTH = 1000

    fun isValidPost(body: String): Boolean = body.trim().length in 1..MAX_POST_LENGTH

    fun isValidComment(body: String): Boolean = body.trim().length in 1..MAX_COMMENT_LENGTH
}

class CreatePostUseCase @Inject constructor(private val repository: CommunityRepository) {
    suspend operator fun invoke(scope: PostScope, category: PostCategory, body: String): AppResult<String> {
        if (!PostTextValidator.isValidPost(body)) return AppResult.Failure(AppError.INVALID_INPUT)
        return repository.createPost(scope, category, body.trim())
    }
}

class AddCommentUseCase @Inject constructor(private val repository: CommunityRepository) {
    suspend operator fun invoke(postId: String, body: String): AppResult<Unit> {
        if (!PostTextValidator.isValidComment(body)) return AppResult.Failure(AppError.INVALID_INPUT)
        return repository.addComment(postId, body.trim())
    }
}
