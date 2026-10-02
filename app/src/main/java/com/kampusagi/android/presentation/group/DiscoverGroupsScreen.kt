package com.kampusagi.android.presentation.group

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.component.LoadingView
import com.kampusagi.android.core.designsystem.component.MessageView
import com.kampusagi.android.core.designsystem.component.PrimaryButton
import com.kampusagi.android.core.designsystem.icon.AppIcons
import com.kampusagi.android.core.designsystem.theme.Spacing
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.GroupKind
import com.kampusagi.android.domain.repository.GroupRepository
import com.kampusagi.android.presentation.common.messageRes
import com.kampusagi.android.presentation.main.DiscoverGroupsRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@HiltViewModel
class DiscoverGroupsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: GroupRepository,
) : ViewModel() {

    val kind: GroupKind = savedStateHandle.toRoute<DiscoverGroupsRoute>().kind

    var query by mutableStateOf("")
        private set

    var state by mutableStateOf<GroupListState>(GroupListState.Loading)
        private set

    var joiningId by mutableStateOf<String?>(null)
        private set

    var joinError by mutableStateOf<AppError?>(null)
        private set

    /** A group just joined, for the screen to open. */
    var joined by mutableStateOf<String?>(null)
        private set

    private var searchJob: Job? = null

    init {
        search()
    }

    fun onQueryChange(value: String) {
        if (value.length > 64) return
        query = value
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            fetch()
        }
    }

    fun search() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch { fetch() }
    }

    fun join(groupId: String) {
        if (joiningId != null) return
        joiningId = groupId
        joinError = null
        viewModelScope.launch {
            when (val result = repository.join(groupId)) {
                is AppResult.Success -> joined = groupId
                is AppResult.Failure -> joinError = result.error
            }
            joiningId = null
        }
    }

    fun onOpened() {
        joined = null
    }

    private suspend fun fetch() {
        if (state !is GroupListState.Loaded) state = GroupListState.Loading
        state = when (val result = repository.discover(kind, query)) {
            is AppResult.Success -> GroupListState.Loaded(result.value)
            is AppResult.Failure -> GroupListState.Failed(result.error)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverGroupsScreen(
    onBack: () -> Unit,
    onJoined: (String) -> Unit,
    viewModel: DiscoverGroupsViewModel = hiltViewModel(),
) {
    LaunchedEffect(viewModel.joined) {
        viewModel.joined?.let {
            viewModel.onOpened()
            onJoined(it)
        }
    }
    val channel = viewModel.kind == GroupKind.CHANNEL
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(if (channel) R.string.channels_discover_title else R.string.groups_discover_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.cd_back)) }
            },
        )
        OutlinedTextField(
            value = viewModel.query,
            onValueChange = viewModel::onQueryChange,
            placeholder = {
                Text(stringResource(if (channel) R.string.channels_search_hint else R.string.groups_search_hint))
            },
            leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
        )
        viewModel.joinError?.let {
            Text(
                stringResource(it.messageRes()),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = Spacing.md),
            )
        }
        when (val state = viewModel.state) {
            GroupListState.Loading -> LoadingView()
            is GroupListState.Failed -> MessageView(
                icon = AppIcons.CloudOff,
                title = stringResource(R.string.groups_load_failed),
                body = stringResource(state.error.messageRes()),
            ) {
                PrimaryButton(text = stringResource(R.string.action_retry), onClick = viewModel::search)
            }
            is GroupListState.Loaded -> if (state.groups.isEmpty()) {
                MessageView(
                    icon = if (channel) AppIcons.Campaign else AppIcons.Groups,
                    title = stringResource(R.string.search_no_results),
                    body = stringResource(if (channel) R.string.channels_discover_empty else R.string.groups_discover_empty),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.groups, key = { it.id }) { group ->
                        GroupRow(group, onClick = { viewModel.join(group.id) }) {
                            FilledTonalButton(onClick = { viewModel.join(group.id) }, enabled = viewModel.joiningId == null) {
                                Text(stringResource(if (channel) R.string.channel_follow else R.string.group_join))
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}
