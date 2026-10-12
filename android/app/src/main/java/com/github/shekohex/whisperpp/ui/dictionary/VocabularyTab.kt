package com.github.shekohex.whisperpp.ui.dictionary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.ui.components.EmptyState
import com.github.shekohex.whisperpp.ui.components.PreviewSurface
import com.github.shekohex.whisperpp.ui.components.ThemePreviews
import com.github.shekohex.whisperpp.ui.theme.Spacing

/** Vocabulary terms as removable chips with an add field and a count. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VocabularyTab(
    terms: List<String>,
    onAdd: (input: String, onResult: (DictionaryViewModel.AddResult) -> Unit) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        AddTermRow(onAdd = onAdd)
        Text(
            text = pluralStringResource(R.plurals.dictionary_vocabulary_count, terms.size, terms.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (terms.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Spellcheck,
                title = stringResource(R.string.dictionary_vocabulary_empty_title),
                body = stringResource(R.string.dictionary_vocabulary_empty_body),
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                terms.forEach { term ->
                    val removeLabel = stringResource(R.string.dictionary_remove_term, term)
                    InputChip(
                        selected = false,
                        onClick = { onRemove(term) },
                        label = {
                            Text(term, style = MaterialTheme.typography.labelLarge.copy(textDirection = TextDirection.Content))
                        },
                        trailingIcon = {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = removeLabel,
                                modifier = Modifier.size(InputChipDefaults.IconSize),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AddTermRow(onAdd: (input: String, onResult: (DictionaryViewModel.AddResult) -> Unit) -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<DictionaryViewModel.AddResult?>(null) }
    val submit = {
        onAdd(input) { result ->
            error = result.takeIf { it != DictionaryViewModel.AddResult.ADDED }
            if (result == DictionaryViewModel.AddResult.ADDED) input = ""
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = {
                input = it
                error = null
            },
            label = { Text(stringResource(R.string.dictionary_vocabulary_label)) },
            placeholder = { Text(stringResource(R.string.dictionary_vocabulary_hint)) },
            singleLine = true,
            isError = error != null,
            supportingText = error?.let { result -> { Text(stringResource(result.messageRes())) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.weight(1f),
        )
        FilledIconButton(onClick = submit, enabled = input.isNotBlank()) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.dictionary_add_term))
        }
    }
}

@ThemePreviews
@Composable
private fun VocabularyTabPreview() {
    PreviewSurface {
        VocabularyTab(
            terms = listOf("Whisper++", "VoiceInk", "Kotlin", "Jetpack Compose", "شادي"),
            onAdd = { _, _ -> },
            onRemove = {},
        )
    }
}
