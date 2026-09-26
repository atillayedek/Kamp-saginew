package com.kampusagi.android.presentation.community

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.theme.Spacing
import com.kampusagi.android.domain.model.Author
import com.kampusagi.android.domain.model.Post
import com.kampusagi.android.presentation.common.relativeTime

@Composable
fun AuthorLine(author: Author, createdAt: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            author.fullName ?: author.username?.let { "@$it" } ?: stringResource(R.string.author_unknown),
            style = MaterialTheme.typography.titleMedium,
        )
        val details = listOfNotNull(author.username?.let { "@$it" }, author.university, relativeTime(createdAt).ifEmpty { null })
        Text(
            details.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun PostActions(post: Post, onToggleLike: () -> Unit, onOpenComments: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onToggleLike) {
            Icon(
                imageVector = if (post.likedByMe) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = stringResource(if (post.likedByMe) R.string.cd_unlike else R.string.cd_like),
                tint = if (post.likedByMe) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(post.likeCount.toString(), style = MaterialTheme.typography.bodyMedium)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(start = Spacing.md)
                .let { if (onOpenComments != null) it.clickable(onClick = onOpenComments) else it }
                .padding(Spacing.sm),
        ) {
            Icon(
                Icons.Outlined.ChatBubbleOutline,
                contentDescription = stringResource(R.string.cd_comments),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Text(
                post.commentCount.toString(),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }
    }
}

@Composable
fun PostCard(post: Post, onOpen: () -> Unit, onToggleLike: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(
            modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, top = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            AuthorLine(post.author, post.createdAt)
            Text(post.body, style = MaterialTheme.typography.bodyLarge, maxLines = 12)
            PostActions(post, onToggleLike = onToggleLike, onOpenComments = onOpen)
        }
    }
}
