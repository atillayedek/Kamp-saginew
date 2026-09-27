package com.kampusagi.android.presentation.community

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
fun PostActions(post: Post, onToggleLike: () -> Unit, onOpenComments: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
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
    }
}

/** A feed entry: author, category, text and actions on a flat surface, separated by a hairline. */
@Composable
fun PostCard(post: Post, onOpen: () -> Unit, onToggleLike: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(
            modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, top = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            AuthorLine(post.author, post.createdAt)
            CategoryLabel(post.category)
            Text(post.body, style = MaterialTheme.typography.bodyLarge, maxLines = 12, overflow = TextOverflow.Ellipsis)
        }
        Row(modifier = Modifier.padding(horizontal = Spacing.xs)) {
            PostActions(post, onToggleLike = onToggleLike, onOpenComments = onOpen)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
