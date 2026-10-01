package com.kampusagi.android.presentation.legal

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.component.LoadingView
import com.kampusagi.android.core.designsystem.component.MessageView
import com.kampusagi.android.core.designsystem.component.PrimaryButton
import com.kampusagi.android.core.designsystem.icon.AppIcons
import com.kampusagi.android.core.designsystem.theme.Spacing
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.LegalDocument
import com.kampusagi.android.domain.repository.ComplianceRepository
import com.kampusagi.android.domain.usecase.LegalMarkdown
import com.kampusagi.android.presentation.common.messageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

sealed interface LegalDocumentState {
    data object Loading : LegalDocumentState
    data class Loaded(val document: LegalDocument, val blocks: List<LegalMarkdown.Block>) : LegalDocumentState
    data class Failed(val error: AppError) : LegalDocumentState
}

/** One published legal text, read from the database (the same version the person accepts). */
@HiltViewModel
class LegalDocumentViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val compliance: ComplianceRepository,
) : ViewModel() {
    /** From the navigation route; screens outside navigation call [open]. */
    private var docType: String? = savedStateHandle["docType"]

    var state by mutableStateOf<LegalDocumentState>(LegalDocumentState.Loading)
        private set

    init {
        if (docType != null) load()
    }

    fun open(type: String) {
        if (type == docType && state is LegalDocumentState.Loaded) return
        docType = type
        load()
    }

    fun load() {
        val type = docType ?: return
        state = LegalDocumentState.Loading
        viewModelScope.launch {
            state = when (val result = compliance.legalDocument(type)) {
                is AppResult.Success -> LegalDocumentState.Loaded(result.value, LegalMarkdown.parse(result.value.content))
                is AppResult.Failure -> LegalDocumentState.Failed(result.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalDocumentScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    docType: String? = null,
    viewModel: LegalDocumentViewModel = hiltViewModel(key = docType),
) {
    LaunchedEffect(docType) { docType?.let(viewModel::open) }
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text((viewModel.state as? LegalDocumentState.Loaded)?.document?.title ?: stringResource(R.string.legal_loading_title))
            },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.cd_back)) }
            },
        )
        when (val state = viewModel.state) {
            LegalDocumentState.Loading -> LoadingView()
            is LegalDocumentState.Failed -> MessageView(
                icon = AppIcons.CloudOff,
                title = stringResource(R.string.legal_load_failed),
                body = stringResource(state.error.messageRes()),
            ) {
                PrimaryButton(text = stringResource(R.string.action_retry), onClick = viewModel::load)
            }
            is LegalDocumentState.Loaded -> Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                LegalBlocks(state.blocks)
                Text(
                    stringResource(R.string.legal_version_footer, state.document.version, state.document.sha256),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.md),
                )
            }
        }
    }
}

private fun spans(parts: List<LegalMarkdown.Span>): AnnotatedString = buildAnnotatedString {
    for (part in parts) {
        when (part.style) {
            LegalMarkdown.Style.TEXT -> append(part.text)
            LegalMarkdown.Style.BOLD -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(part.text) }
            LegalMarkdown.Style.CODE -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(part.text) }
        }
    }
}

/** Draws parsed legal Markdown natively; the first heading is the screen title, so it is skipped. */
@Composable
fun LegalBlocks(blocks: List<LegalMarkdown.Block>) {
    blocks.forEach { block ->
        when (block) {
            is LegalMarkdown.Block.Heading -> if (block.level > 1) {
                Text(
                    spans(block.text),
                    style = if (block.level == 2) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
            is LegalMarkdown.Block.Paragraph -> Text(spans(block.text), style = MaterialTheme.typography.bodyMedium)
            is LegalMarkdown.Block.Quote -> Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(spans(block.text), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(Spacing.sm))
            }
            is LegalMarkdown.Block.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                block.items.forEachIndexed { index, item ->
                    Row {
                        Text(
                            when {
                                item.checkbox -> "☐"
                                block.ordered -> "${index + 1}."
                                else -> "•"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.width(28.dp),
                        )
                        Text(spans(item.text), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            is LegalMarkdown.Block.Table -> Column(
                modifier = Modifier.horizontalScroll(rememberScrollState()).border(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                (listOf(block.head) + block.rows).forEachIndexed { rowIndex, row ->
                    Row {
                        row.forEach { cell ->
                            Text(
                                spans(cell),
                                style = if (rowIndex == 0) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .width(180.dp)
                                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                    .padding(Spacing.xs),
                            )
                        }
                    }
                }
            }
        }
    }
}
