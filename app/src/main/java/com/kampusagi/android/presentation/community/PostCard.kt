package com.kampusagi.android.presentation.community

import com.kampusagi.android.presentation.common.LinkedBodyText
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.icon.AppIcons
import com.kampusagi.android.core.designsystem.theme.Spacing
import com.kampusagi.android.domain.model.Author
import com.kampusagi.android.domain.model.Post
import com.kampusagi.android.domain.model.PostCategory
import com.kampusagi.android.presentation.common.avatar.UserAvatar
import com.kampusagi.android.presentation.common.relativeTime

@Composable
fun AuthorLine(author: Author, createdAt: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        UserAvatar(author.id, author.fullName, author.username, size = 40.dp)
        Column(modifier = Modifier.padding(start = Spacing.sm + Spacing.xs).weight(1f)) {
            Text(
                author.fullName ?: author.username?.let { "@$it" } ?: stringResource(R.string.author_unknown),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val details = listOfNotNull(author.username?.let { "@$it" }, author.university, relativeTime(createdAt).ifEmpty { null })
            Text(
                details.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Small tinted label with the category icon, shown on every post. */
@Composable
fun CategoryLabel(category: PostCategory, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.extraSmall,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 3.dp),
        ) {
            Icon(category.icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(
                stringResource(category.labelRes()),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }
    }
}

@Composable
fun PostActions(post: Post, onToggleLike: () -> Unit, onToggleSave: () -> Unit, onOpenComments: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onToggleLike) {
            Icon(
                imageVector = if (post.likedByMe) AppIcons.FavoriteFilled else AppIcons.Favorite,
                contentDescription = stringResource(if (post.likedByMe) R.string.cd_unlike else R.string.cd_like),
                tint = if (post.likedByMe) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(post.likeCount.toString(), style = MaterialTheme.typography.labelMedium)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(start = Spacing.sm)
                .let { if (onOpenComments != null) it.clickable(onClick = onOpenComments) else it }
                .padding(Spacing.sm),
        ) {
            Icon(
                AppIcons.ChatBubble,
                contentDescription = stringResource(R.string.cd_comments),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp),
            )
            Text(
                post.commentCount.toString(),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = Spacing.xs + 2.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onToggleSave) {
            Icon(
                imageVector = if (post.savedByMe) AppIcons.BookmarkFilled else AppIcons.Bookmark,
                contentDescription = stringResource(if (post.savedByMe) R.string.cd_unsave else R.string.cd_save),
                tint = if (post.savedByMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Category and, for listings, the price; shown above the text. */
@Composable
fun PostLabels(post: Post) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        CategoryLabel(post.category)
        post.listing?.let { ListingLabel(it) }
    }
}

/** Photos, poll and event of a post, in that order. */
@Composable
fun PostAttachments(post: Post, callbacks: PostCallbacks) {
    post.poll?.let { PollView(it, onVote = callbacks.onVote, modifier = Modifier.padding(horizontal = Spacing.md)) }
    post.event?.let { EventView(it, onToggleAttending = callbacks.onToggleAttending, modifier = Modifier.padding(horizontal = Spacing.md)) }
}

/** A feed entry: author, labels, text, photos and actions on a flat surface, separated by a hairline. */
@Composable
fun PostCard(post: Post, callbacks: PostCallbacks, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().clickable(onClick = callbacks.onOpen)) {
        Column(
            modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, top = Spacing.md, bottom = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            AuthorLine(post.author, post.createdAt, modifier = Modifier.clickable { callbacks.onOpenAuthor(post.author.id) })
            PostLabels(post)
            LinkedBodyText(post.body, style = MaterialTheme.typography.bodyLarge, maxLines = 12, overflow = TextOverflow.Ellipsis)
        }
        PostMedia(post.media)
        Column(
            modifier = Modifier.padding(top = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            PostAttachments(post, callbacks)
        }
        Row(modifier = Modifier.padding(horizontal = Spacing.xs)) {
            PostActions(post, onToggleLike = callbacks.onToggleLike, onToggleSave = callbacks.onToggleSave, onOpenComments = callbacks.onOpen)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
