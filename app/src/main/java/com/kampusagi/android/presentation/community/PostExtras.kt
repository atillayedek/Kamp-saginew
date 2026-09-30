package com.kampusagi.android.presentation.community

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.icon.AppIcons
import com.kampusagi.android.core.designsystem.theme.Spacing
import com.kampusagi.android.domain.model.Listing
import com.kampusagi.android.domain.model.Poll
import com.kampusagi.android.domain.model.PostEvent
import com.kampusagi.android.domain.usecase.PriceFormat
import com.kampusagi.android.presentation.common.media.PostPhoto
import com.kampusagi.android.presentation.requirement.formatStartsAt
import java.time.Instant

/** Photos of a post, swiped sideways with a position indicator, as on Instagram. */
@Composable
fun PostMedia(paths: List<String>, modifier: Modifier = Modifier) {
    if (paths.isEmpty()) return
    val pager = rememberPagerState { paths.size }
    Box(modifier = modifier.fillMaxWidth().aspectRatio(4f / 5f)) {
        HorizontalPager(state = pager, key = { paths[it] }, modifier = Modifier.fillMaxSize()) { page ->
            PostPhoto(paths[page], modifier = Modifier.fillMaxWidth().fillMaxHeight())
        }
        if (paths.size > 1) {
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                contentColor = Color.White,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.sm),
            ) {
                Text(
                    "${pager.currentPage + 1}/${paths.size}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.align(Alignment.BottomCenter).padding(Spacing.sm),
            ) {
                paths.indices.forEach { index ->
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (index == pager.currentPage) Color.White else Color.White.copy(alpha = 0.45f)),
                    )
                }
            }
        }
    }
}

/**
 * Options to vote on; once the person voted (or the poll closed) the results
 * are shown as bars. Tapping another option moves the vote.
 */
@Composable
fun PollView(poll: Poll, onVote: (String?) -> Unit, modifier: Modifier = Modifier) {
    val closed = PollMath.isClosed(poll, Instant.now())
    val showResults = closed || poll.myOptionId != null
    val percentages = PollMath.percentages(poll.options.map { it.votes })
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        poll.options.forEachIndexed { index, option ->
            val mine = option.id == poll.myOptionId
            val shape = MaterialTheme.shapes.small
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .border(
                        width = 1.dp,
                        color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = shape,
                    )
                    .clickable(enabled = !closed && !mine) { onVote(option.id) },
            ) {
                if (showResults) {
                    Box(modifier = Modifier.matchParentSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(percentages[index] / 100f)
                                .background(
                                    if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                ),
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = 10.dp),
                ) {
                    Text(
                        option.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (mine) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                    )
                    if (mine) {
                        Icon(
                            AppIcons.CheckCircle,
                            contentDescription = stringResource(R.string.poll_your_vote),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = Spacing.xs).size(18.dp),
                        )
                    }
                    if (showResults) {
                        Text("%${percentages[index]}", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val footer = buildList {
                add(pluralStringResource(R.plurals.poll_votes, poll.totalVotes, poll.totalVotes))
                when {
                    closed -> add(stringResource(R.string.poll_closed))
                    poll.closesAt != null -> formatStartsAt(poll.closesAt)?.let { add(stringResource(R.string.poll_closes_at, it)) }
                }
            }
            Text(
                footer.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (!closed && poll.myOptionId != null) {
                TextButton(onClick = { onVote(null) }) { Text(stringResource(R.string.poll_withdraw)) }
            }
        }
    }
}

/** Date, place, attendee count and the attend button of an event post. */
@Composable
fun EventView(event: PostEvent, onToggleAttending: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            IconLine(AppIcons.CalendarMonth, formatStartsAt(event.startsAt) ?: event.startsAt)
            event.location?.let { IconLine(AppIcons.LocationOn, it) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pluralStringResource(R.plurals.event_attendees, event.attendeeCount, event.attendeeCount),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                if (event.attending) {
                    OutlinedButton(onClick = onToggleAttending) {
                        Icon(AppIcons.EventAvailable, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.event_attending), modifier = Modifier.padding(start = Spacing.xs))
                    }
                } else {
                    FilledTonalButton(onClick = onToggleAttending) { Text(stringResource(R.string.event_attend)) }
                }
            }
        }
    }
}

@Composable
private fun IconLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = Spacing.sm))
    }
}

/** Price label of a marketplace post; sold items are marked. */
@Composable
fun ListingLabel(listing: Listing, modifier: Modifier = Modifier) {
    val sold = listing.sold
    Surface(
        color = if (sold) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = if (sold) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.extraSmall,
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 3.dp)) {
            Icon(AppIcons.Sell, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(
                when {
                    sold -> stringResource(R.string.listing_sold)
                    listing.priceKurus == 0L -> stringResource(R.string.listing_free)
                    else -> PriceFormat.format(listing.priceKurus)
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }
    }
}
