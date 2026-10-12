package com.github.shekohex.whisperpp.ui.dictionary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.domain.autolearn.AutoLearnReviewProposal
import com.github.shekohex.whisperpp.domain.autolearn.LearningAction
import com.github.shekohex.whisperpp.ui.components.PreviewSurface
import com.github.shekohex.whisperpp.ui.components.ThemePreviews
import com.github.shekohex.whisperpp.ui.dictionary.AutoLearnReviewViewModel.Choice
import com.github.shekohex.whisperpp.ui.settings.common.containerViewModel
import com.github.shekohex.whisperpp.ui.theme.Spacing
import kotlinx.coroutines.launch

@Composable
fun defaultAutoLearnReviewViewModel(): AutoLearnReviewViewModel =
    containerViewModel { container, _ -> AutoLearnReviewViewModel(container.autoLearnCoordinator) }

/**
 * "Review corrections" sheet: each proposal shows what would be learned and its action chips;
 * "Apply all" learns the rest and the app-wide host shows "Learned N corrections" with Undo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoLearnReviewSheet(
    onDismiss: () -> Unit,
    reviewing: Boolean = false,
    viewModel: AutoLearnReviewViewModel = defaultAutoLearnReviewViewModel(),
) {
    val proposals by viewModel.proposals.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        AutoLearnReviewContent(
            proposals = proposals.orEmpty(),
            reviewing = reviewing || proposals == null,
            onChoose = { proposal, choice -> scope.launch { viewModel.choose(proposal, choice) } },
            onApplyAll = {
                scope.launch {
                    viewModel.applyAll()
                    onDismiss()
                }
            },
            onClose = onDismiss,
        )
    }
}

@Composable
fun AutoLearnReviewContent(
    proposals: List<AutoLearnReviewProposal>,
    reviewing: Boolean,
    onChoose: (AutoLearnReviewProposal, Choice) -> Unit,
    onApplyAll: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(bottom = Spacing.lg)) {
        Text(
            text = stringResource(R.string.autolearn_review_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = Spacing.lg),
        )
        Text(
            text = stringResource(R.string.autolearn_review_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        )
        if (reviewing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(Spacing.lg))
        if (proposals.isEmpty() && !reviewing) {
            Text(
                text = stringResource(R.string.autolearn_review_empty),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(Spacing.lg),
            )
        }
        LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
            items(proposals, key = { it.id }) { proposal ->
                ProposalRow(proposal = proposal, onChoose = { onChoose(proposal, it) })
                HorizontalDivider()
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onClose) { Text(stringResource(R.string.autolearn_review_later)) }
            Button(onClick = onApplyAll, enabled = proposals.isNotEmpty()) {
                Text(stringResource(R.string.autolearn_apply_all))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProposalRow(proposal: AutoLearnReviewProposal, onChoose: (Choice) -> Unit) {
    val selected = AutoLearnReviewViewModel.choiceOf(proposal.learningAction)
    val contentStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = stringResource(R.string.dictionary_rule_format, proposal.originalText, proposal.correctedText),
            style = contentStyle,
        )
        Text(
            text = learnedSummary(proposal),
            style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Choice.entries.forEach { choice ->
                FilterChip(
                    selected = choice == selected,
                    onClick = { onChoose(choice) },
                    enabled = AutoLearnReviewViewModel.isAllowed(proposal, choice),
                    label = { Text(stringResource(choice.labelRes())) },
                )
            }
        }
    }
}

@Composable
private fun learnedSummary(proposal: AutoLearnReviewProposal): String {
    val incorrect = proposal.incorrectTextToReplace
    return if (proposal.learningAction.addsReplacement && !incorrect.isNullOrBlank()) {
        stringResource(R.string.autolearn_learns_replacement, incorrect, proposal.correctedVocabularyTerm)
    } else {
        stringResource(R.string.autolearn_learns_vocabulary, proposal.correctedVocabularyTerm)
    }
}

private fun Choice.labelRes(): Int = when (this) {
    Choice.REPLACEMENT -> R.string.autolearn_choice_replacement
    Choice.VOCABULARY -> R.string.autolearn_choice_vocabulary
    Choice.BOTH -> R.string.autolearn_choice_both
    Choice.DISMISS -> R.string.autolearn_choice_dismiss
}

@ThemePreviews
@Composable
private fun AutoLearnReviewContentPreview() {
    PreviewSurface {
        AutoLearnReviewContent(
            proposals = listOf(
                AutoLearnReviewProposal(
                    id = "1",
                    candidateId = "c1",
                    originalText = "send it to vois ink now",
                    correctedText = "send it to VoiceInk now",
                    learningAction = LearningAction.ADD_REPLACEMENT_AND_VOCABULARY,
                    incorrectTextToReplace = "vois ink",
                    correctedVocabularyTerm = "VoiceInk",
                ),
            ),
            reviewing = false,
            onChoose = { _, _ -> },
            onApplyAll = {},
            onClose = {},
        )
    }
}
