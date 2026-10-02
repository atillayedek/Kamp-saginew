package com.kampusagi.android.presentation.common

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.kampusagi.android.core.designsystem.theme.Spacing
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.MentionSuggestion
import com.kampusagi.android.domain.model.PostScope
import com.kampusagi.android.domain.repository.CommunityRepository
import com.kampusagi.android.domain.usecase.BodyLinks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Where tapping "#tag" and "@username" in a post or comment goes; provided once by the main screen. */
class BodyLinkHandler(val onTag: (String) -> Unit, val onMention: (String) -> Unit)

val LocalBodyLinks = staticCompositionLocalOf<BodyLinkHandler?> { null }

/** Post or comment text with #tags and @mentions shown as links. */
@Composable
fun LinkedBodyText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val handler = LocalBodyLinks.current
    val linkStyle = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
    val annotated = remember(text, handler, linkStyle) {
        buildAnnotatedString {
            for (segment in BodyLinks.parse(text)) {
                when (segment) {
                    is BodyLinks.Segment.Plain -> append(segment.text)
                    is BodyLinks.Segment.Tag -> link(segment.text, linkStyle, handler?.let { { it.onTag(segment.key) } })
                    is BodyLinks.Segment.Mention -> link(segment.text, linkStyle, handler?.let { { it.onMention(segment.username) } })
                }
            }
        }
    }
    Text(annotated, style = style, modifier = modifier, maxLines = maxLines, overflow = overflow)
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.link(text: String, style: SpanStyle, onClick: (() -> Unit)?) {
    if (onClick == null) {
        withStyle(style) { append(text) }
    } else {
        withLink(LinkAnnotation.Clickable(text, TextLinkStyles(style)) { onClick() }) { append(text) }
    }
}

/**
 * Offers people while "@name" is being typed at the end of a post or comment. Owned by the
 * composer's ViewModel; a failed lookup just shows no suggestions (and is logged).
 */
class MentionSuggester(
    private val repository: CommunityRepository,
    private val scope: CoroutineScope,
) {
    var suggestions by mutableStateOf<List<MentionSuggestion>>(emptyList())
        private set

    private var job: Job? = null

    fun onTextChanged(text: String, postScope: PostScope) {
        job?.cancel()
        val typed = BodyLinks.mentionBeingTyped(text)
        if (typed.isNullOrEmpty()) {
            suggestions = emptyList()
            return
        }
        job = scope.launch {
            delay(DEBOUNCE_MS)
            suggestions = when (val result = repository.suggestMentions(typed, postScope)) {
                is AppResult.Success -> result.value
                is AppResult.Failure -> {
                    Log.w(TAG, "Mention suggestions failed: ${result.error}")
                    emptyList()
                }
            }
        }
    }

    /** The text after picking [suggestion]; the list closes. */
    fun complete(text: String, suggestion: MentionSuggestion): String {
        clear()
        return BodyLinks.completeMention(text, suggestion.username)
    }

    fun clear() {
        job?.cancel()
        suggestions = emptyList()
    }

    private companion object {
        const val TAG = "MentionSuggester"
        const val DEBOUNCE_MS = 250L
    }
}

/** The people offered by [MentionSuggester], shown next to the text field. */
@Composable
fun MentionSuggestionList(
    suggestions: List<MentionSuggestion>,
    onPick: (MentionSuggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (suggestions.isEmpty()) return
    Surface(tonalElevation = 3.dp, shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth()) {
        Column {
            suggestions.forEachIndexed { index, person ->
                if (index > 0) HorizontalDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(person) }
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                ) {
                    Text("@" + person.username, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    val detail = listOfNotNull(person.displayName, person.university).joinToString(" · ")
                    if (detail.isNotEmpty()) {
                        Text(
                            detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
