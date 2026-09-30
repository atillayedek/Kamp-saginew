package com.kampusagi.android.presentation.main

import com.kampusagi.android.domain.model.GroupKind
import com.kampusagi.android.domain.model.PostScope
import kotlinx.serialization.Serializable

@Serializable data object FeedRoute
@Serializable data object ProfileRoute
@Serializable data class PostDetailRoute(val postId: String)
@Serializable data class CreatePostRoute(val scope: PostScope)
@Serializable data object RequirementsRoute
@Serializable data object CreateRequirementRoute
@Serializable data class MatchesRoute(val requirementId: String, val title: String)
@Serializable data object ConversationsRoute
@Serializable data class ChatRoute(val conversationId: String, val title: String)
@Serializable data object NotificationsRoute
@Serializable data object PremiumRoute
@Serializable data object SettingsRoute
@Serializable data object MyMatchesRoute
@Serializable data object EditProfileRoute
@Serializable data object SearchRoute
@Serializable data object SavedPostsRoute
@Serializable data object EventsRoute
@Serializable data class UserProfileRoute(val userId: String)
@Serializable data class DiscoverGroupsRoute(val kind: GroupKind)
@Serializable data class CreateGroupRoute(val kind: GroupKind)
@Serializable data class GroupRoute(val groupId: String)
@Serializable data class GroupInfoRoute(val groupId: String)
@Serializable data object NotesRoute
@Serializable data object UploadNoteRoute
