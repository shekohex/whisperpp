package com.github.shekohex.whisperpp.ui.dictionary

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.app.appContainer
import com.github.shekohex.whisperpp.ui.components.LoadingState
import com.github.shekohex.whisperpp.ui.components.PageScaffold
import com.github.shekohex.whisperpp.ui.settings.common.containerViewModel
import com.github.shekohex.whisperpp.ui.theme.Spacing
import kotlinx.coroutines.launch

private const val TAB_REPLACEMENTS = 0
private const val TAB_VOCABULARY = 1

@Composable
fun defaultDictionaryViewModel(): DictionaryViewModel =
    containerViewModel { container, _ -> DictionaryViewModel(container.dictionaryRepository) }

/** Dictionary tab (§4.11): `Word Replacements | Vocabulary`, gear → settings sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryScreen(viewModel: DictionaryViewModel = defaultDictionaryViewModel()) {
    val rules by viewModel.replacements.collectAsStateWithLifecycle()
    val terms by viewModel.vocabulary.collectAsStateWithLifecycle()
    val proposalCount by LocalContext.current.appContainer.autoLearnCoordinator.proposalCount
        .collectAsStateWithLifecycle(initialValue = 0)
    var tab by rememberSaveable { mutableIntStateOf(TAB_REPLACEMENTS) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current

    val onRemoved: (DictionaryViewModel.Removed) -> Unit = { removed ->
        scope.launch {
            val message = when (removed) {
                is DictionaryViewModel.Removed.Rule -> resources.getString(R.string.dictionary_rule_deleted)
                is DictionaryViewModel.Removed.Term -> resources.getString(R.string.dictionary_term_removed, removed.term)
            }
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = resources.getString(R.string.autolearn_undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undo(removed)
        }
    }

    PageScaffold(
        title = stringResource(R.string.nav_dictionary),
        actions = {
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.dictionary_settings_title))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (proposalCount > 0) ReviewBanner(proposalCount)
            TabSelector(selected = tab, onSelect = { tab = it })
            val currentRules = rules
            val currentTerms = terms
            when {
                currentRules == null || currentTerms == null -> LoadingState()
                tab == TAB_REPLACEMENTS -> ReplacementsTab(
                    rules = currentRules,
                    onAdd = { originals, replacement, onResult ->
                        scope.launch { onResult(viewModel.addReplacement(originals, replacement)) }
                    },
                    onEdit = { old, originals, replacement ->
                        scope.launch { viewModel.updateReplacement(old, originals, replacement) }
                    },
                    onDelete = { rule -> scope.launch { onRemoved(viewModel.removeReplacement(rule)) } },
                )
                else -> VocabularyTab(
                    terms = currentTerms,
                    onAdd = { input, onResult -> scope.launch { onResult(viewModel.addVocabulary(input)) } },
                    onRemove = { term -> scope.launch { onRemoved(viewModel.removeVocabulary(term)) } },
                )
            }
        }
    }

    if (showSettings) {
        DictionarySettingsSheet(
            onDismiss = { showSettings = false },
            onReviewNow = { showSettings = false },
        )
    }
}

@Composable
private fun TabSelector(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf(R.string.dictionary_tab_replacements, R.string.dictionary_tab_vocabulary)
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
    ) {
        labels.forEachIndexed { index, label ->
            SegmentedButton(
                selected = selected == index,
                onClick = { onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index, labels.size),
            ) {
                Text(stringResource(label))
            }
        }
    }
}

@Composable
private fun ReviewBanner(count: Int) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        leadingContent = { Icon(Icons.Outlined.AutoFixHigh, contentDescription = null) },
        headlineContent = { Text(pluralStringResource(R.plurals.autolearn_to_review, count, count)) },
        trailingContent = {
            TextButton(onClick = AutoLearnReviewUiState::openSheet) {
                Text(stringResource(R.string.autolearn_review_now))
            }
        },
    )
}
