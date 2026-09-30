package com.kampusagi.android.presentation.community

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.component.LoadingView
import com.kampusagi.android.core.designsystem.component.MessageView
import com.kampusagi.android.core.designsystem.icon.AppIcons
import com.kampusagi.android.core.designsystem.theme.Spacing
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.PersonSummary
import com.kampusagi.android.domain.model.Post
import com.kampusagi.android.domain.repository.CommunityRepository
import com.kampusagi.android.presentation.common.avatar.UserAvatar
import com.kampusagi.android.presentation.common.messageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class SearchTab { POSTS, PEOPLE }

sealed interface PeopleState {
    data object Idle : PeopleState
    data object Loading : PeopleState
    data class Loaded(val people: List<PersonSummary>) : PeopleState
    data class Failed(val error: AppError) : PeopleState
}

/** Searches posts and people as the person types (after a short pause, from two characters). */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: CommunityRepository,
    postChanges: PostChanges,
) : PostListViewModel(repository, postChanges) {

    var query by mutableStateOf("")
        private set

    var tab by mutableStateOf(SearchTab.POSTS)
        private set

    var people by mutableStateOf<PeopleState>(PeopleState.Idle)
        private set

    private var searchJob: Job? = null

    val hasQuery: Boolean get() = query.trim().length >= MIN_QUERY

    override suspend fun fetch(): AppResult<List<Post>> = repository.searchPosts(query.trim())

    fun onQueryChange(value: String) {
        if (value.length > MAX_QUERY) return
        query = value
        searchJob?.cancel()
        if (!hasQuery) {
            people = PeopleState.Idle
            return
        }
        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            search()
        }
    }

    fun selectTab(value: SearchTab) {
        tab = value
    }

    fun retry() {
        if (hasQuery) viewModelScope.launch { search() }
    }

    private suspend fun search() {
        load()
        people = PeopleState.Loading
        people = when (val result = repository.searchPeople(query.trim())) {
            is AppResult.Success -> PeopleState.Loaded(result.value)
            is AppResult.Failure -> PeopleState.Failed(result.error)
        }
    }

    private companion object {
        const val MIN_QUERY = 2
        const val MAX_QUERY = 64
        const val DEBOUNCE_MS = 350L
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenPerson: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = Spacing.md, top = Spacing.sm)) {
            IconButton(onClick = onBack) {
                Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.cd_back))
            }
            OutlinedTextField(
                value = viewModel.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
                trailingIcon = {
                    if (viewModel.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(AppIcons.Close, contentDescription = stringResource(R.string.search_clear))
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
        }
        PrimaryTabRow(selectedTabIndex = viewModel.tab.ordinal) {
            SearchTab.entries.forEach { tab ->
                Tab(
                    selected = tab == viewModel.tab,
                    onClick = { viewModel.selectTab(tab) },
                    text = { Text(stringResource(if (tab == SearchTab.POSTS) R.string.search_tab_posts else R.string.search_tab_people)) },
                )
            }
        }
        if (!viewModel.hasQuery) {
            MessageView(
                icon = AppIcons.Search,
                title = stringResource(R.string.search_title),
                body = stringResource(R.string.search_start),
            )
        } else when (viewModel.tab) {
            SearchTab.POSTS -> when (val state = viewModel.state) {
                PostListState.Loading -> LoadingView()
                is PostListState.Failed -> ErrorView(state.error, viewModel::retry)
                is PostListState.Loaded -> if (state.posts.isEmpty()) {
                    MessageView(
                        icon = AppIcons.Search,
                        title = stringResource(R.string.search_no_results),
                        body = stringResource(R.string.search_no_posts),
                    )
                } else {
                    PostLazyList(state.posts, viewModel.interactor, onOpenPost, onOpenPerson)
                }
            }
            SearchTab.PEOPLE -> when (val people = viewModel.people) {
                PeopleState.Idle, PeopleState.Loading -> LoadingView()
                is PeopleState.Failed -> ErrorView(people.error, viewModel::retry)
                is PeopleState.Loaded -> if (people.people.isEmpty()) {
                    MessageView(
                        icon = AppIcons.PersonSearch,
                        title = stringResource(R.string.search_no_results),
                        body = stringResource(R.string.search_no_people),
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(people.people, key = { it.id }) { person -> PersonRow(person, onClick = { onOpenPerson(person.id) }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorView(error: AppError, onRetry: () -> Unit) {
    MessageView(
        icon = AppIcons.CloudOff,
        title = stringResource(R.string.search_failed),
        body = stringResource(error.messageRes()),
    ) {
        com.kampusagi.android.core.designsystem.component.PrimaryButton(text = stringResource(R.string.action_retry), onClick = onRetry)
    }
}

@Composable
fun PersonRow(person: PersonSummary, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        UserAvatar(person.id, person.fullName, person.username, size = 48.dp)
        Column(modifier = Modifier.padding(start = Spacing.md).weight(1f)) {
            Text(
                person.fullName ?: person.username?.let { "@$it" }.orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(person.username?.let { "@$it" }, person.university).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
