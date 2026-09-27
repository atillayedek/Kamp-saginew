package com.kampusagi.android.domain.repository

import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.AuthRedirectResult
import com.kampusagi.android.domain.model.AuthState
import com.kampusagi.android.domain.model.BlockedUser
import com.kampusagi.android.domain.model.ChatMessage
import com.kampusagi.android.domain.model.Conversation
import com.kampusagi.android.domain.model.Comment
import com.kampusagi.android.domain.model.FeedCursor
import com.kampusagi.android.domain.model.FeedPage
import com.kampusagi.android.domain.model.Match
import com.kampusagi.android.domain.model.MessageCursor
import com.kampusagi.android.domain.model.AppNotification
import com.kampusagi.android.domain.model.OpenReport
import com.kampusagi.android.domain.model.PendingVerification
import com.kampusagi.android.domain.model.Plan
import com.kampusagi.android.domain.model.StoreOffer
import com.kampusagi.android.domain.model.StorePurchase
import com.kampusagi.android.domain.model.SubscriptionStatus
import com.kampusagi.android.domain.model.Post
import com.kampusagi.android.domain.model.PostCategory
import com.kampusagi.android.domain.model.PostScope
import com.kampusagi.android.domain.model.ProfileDraft
import com.kampusagi.android.domain.model.ProfileStats
import com.kampusagi.android.domain.model.ProfileState
import com.kampusagi.android.domain.model.Requirement
import com.kampusagi.android.domain.model.RequirementDraft
import com.kampusagi.android.domain.model.ReportAction
import com.kampusagi.android.domain.model.ReportReason
import com.kampusagi.android.domain.model.ReportTarget
import com.kampusagi.android.domain.model.SignUpResult
import com.kampusagi.android.domain.model.University
import com.kampusagi.android.domain.model.Verification
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val authState: StateFlow<AuthState>
    val isBackendConfigured: Boolean

    suspend fun signIn(email: String, password: String): AppResult<Unit>
    suspend fun signUp(email: String, password: String): AppResult<SignUpResult>
    suspend fun resendVerificationEmail(email: String): AppResult<Unit>
    suspend fun sendPasswordReset(email: String): AppResult<Unit>
    suspend fun updatePassword(newPassword: String): AppResult<Unit>
    suspend fun handleAuthRedirect(url: String): AuthRedirectResult
    suspend fun signOut(): AppResult<Unit>
}

interface ProfileRepository {
    /** Null while nobody is signed in. */
    val profileState: StateFlow<ProfileState?>

    /** Reloads the signed-in person's profile from the backend. */
    suspend fun refresh()

    suspend fun completeProfile(draft: ProfileDraft): AppResult<Unit>
}

/** Bio, profile photo and stats of the signed-in person, and other people's photos. */
interface ProfileMediaRepository {
    suspend fun updateBio(bio: String): AppResult<Unit>

    /** Uploads a JPEG (already resized), makes it the profile photo and removes the previous file. */
    suspend fun uploadAvatar(jpeg: ByteArray): AppResult<Unit>

    suspend fun removeAvatar(): AppResult<Unit>

    suspend fun stats(): AppResult<ProfileStats>

    /** Photo paths of the given people who have one (blocked people are left out by the server). */
    suspend fun avatarPaths(userIds: Collection<String>): AppResult<Map<String, String>>

    /** Downloads a photo with the signed-in session; the bucket is private. */
    suspend fun downloadAvatar(path: String): AppResult<ByteArray>
}

interface UniversityRepository {
    suspend fun getActiveUniversities(): AppResult<List<University>>
}

interface VerificationRepository {
    /** Uploads the PDF and asks the backend to validate and record it. */
    suspend fun submitDocument(bytes: ByteArray): AppResult<Unit>

    /** The signed-in person's most recent request, or null if they never submitted one. */
    suspend fun latestVerification(): AppResult<Verification?>
}

/** Reads a document the person picked with the system file picker. */
interface ImageEncoder {
    /** Decodes the picked image, crops it square and returns a small JPEG for the profile photo. */
    suspend fun avatarJpeg(uri: String): AppResult<ByteArray>
}

interface DocumentReader {
    suspend fun read(uri: String, maxBytes: Int): AppResult<ByteArray>
}

interface AdminRepository {
    suspend fun pendingVerifications(): AppResult<List<PendingVerification>>

    /** Downloads the document into the app's private cache for viewing. */
    suspend fun downloadDocument(path: String): AppResult<File>

    suspend fun review(verificationId: String, approve: Boolean, reason: String?): AppResult<Unit>
}

interface CommunityRepository {
    /** [category] null means every category. */
    suspend fun feed(scope: PostScope, category: PostCategory?, cursor: FeedCursor?): AppResult<FeedPage>
    suspend fun post(postId: String): AppResult<Post>
    suspend fun comments(postId: String): AppResult<List<Comment>>
    suspend fun createPost(scope: PostScope, category: PostCategory, body: String): AppResult<String>
    suspend fun deletePost(postId: String): AppResult<Unit>
    suspend fun addComment(postId: String, body: String): AppResult<Unit>
    suspend fun deleteComment(commentId: String): AppResult<Unit>

    /** Returns the post's like count after the change. */
    suspend fun setLiked(postId: String, liked: Boolean): AppResult<Int>
}

interface RequirementRepository {
    /** Asks the backend (OpenAI) to structure the student's text. Nothing is stored. */
    suspend fun analyze(text: String): AppResult<RequirementDraft>

    /** Stores the edited draft; the backend embeds it for matching. Returns the new id. */
    suspend fun publish(originalText: String, draft: RequirementDraft): AppResult<String>

    suspend fun myRequirements(): AppResult<List<Requirement>>

    suspend fun close(requirementId: String): AppResult<Unit>

    /** Other students' active requirements ranked by similarity to one of ours. */
    suspend fun matches(requirementId: String): AppResult<List<Match>>
}

interface ChatRepository {
    suspend fun conversations(): AppResult<List<Conversation>>

    /** Returns the existing conversation with that student or creates it. */
    suspend fun startConversation(otherUserId: String): AppResult<String>

    /** Newest first; [before] pages towards older messages. */
    suspend fun messages(conversationId: String, before: MessageCursor?): AppResult<List<ChatMessage>>

    /** Idempotent for the same [messageId]; returns the server timestamp. */
    suspend fun send(conversationId: String, messageId: String, body: String): AppResult<String>

    suspend fun markRead(conversationId: String): AppResult<Unit>

    /**
     * Emits whenever a message or read receipt changes in [conversationId]
     * (or in any of the person's conversations when it is null). Backed by
     * Supabase Realtime; callers reload from the server on each emission.
     */
    fun changes(conversationId: String?): Flow<Unit>
}

interface NotificationRepository {
    suspend fun notifications(): AppResult<List<AppNotification>>

    /** Marks the given notifications read, or all of them when [ids] is null. */
    suspend fun markRead(ids: List<String>?): AppResult<Unit>

    /** Emits when the person's notifications change (Supabase Realtime). */
    fun changes(): Flow<Unit>
}

/** Backend side of Premium: plans, the person's status and server-side purchase verification. */
interface PremiumRepository {
    suspend fun plans(): AppResult<List<Plan>>
    suspend fun subscription(): AppResult<SubscriptionStatus>

    /** Sends a Google Play purchase to the backend, which verifies it with Google. */
    suspend fun verify(purchase: StorePurchase): AppResult<Unit>
}

/** Blocking and reporting for students; the report queue for admins. */
interface ModerationRepository {
    suspend fun block(userId: String): AppResult<Unit>
    suspend fun unblock(userId: String): AppResult<Unit>
    suspend fun blockedUsers(): AppResult<List<BlockedUser>>

    /** Reporting the same thing again while it is open is not an error. */
    suspend fun report(target: ReportTarget, targetId: String, reason: ReportReason, details: String?): AppResult<Unit>

    /** The other member of a conversation, for blocking or reporting them from the chat. */
    suspend fun conversationPartner(conversationId: String): AppResult<String>

    suspend fun openReports(): AppResult<List<OpenReport>>
    suspend fun resolve(reportId: String, action: ReportAction): AppResult<Unit>
}

interface AccountRepository {
    /** Permanently deletes the signed-in account on the server, then ends the local session. */
    suspend fun deleteAccount(): AppResult<Unit>

    /** Whether the signed-in person agreed to receive marketing e-mail. */
    suspend fun marketingConsent(): AppResult<Boolean>

    suspend fun setMarketingConsent(optIn: Boolean): AppResult<Unit>
}
