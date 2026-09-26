package com.kampusagi.android.presentation.main

import com.kampusagi.android.domain.model.PostScope
import kotlinx.serialization.Serializable

@Serializable data object FeedRoute
@Serializable data object ProfileRoute
@Serializable data class PostDetailRoute(val postId: String)
@Serializable data class CreatePostRoute(val scope: PostScope)
