package com.github.shekohex.whisperpp.ui.dictionary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.core.text.Replacement
import com.github.shekohex.whisperpp.ui.components.EmptyState
import com.github.shekohex.whisperpp.ui.components.PreviewSurface
import com.github.shekohex.whisperpp.ui.components.ThemePreviews
import com.github.shekohex.whisperpp.ui.theme.Spacing

/** Inline add row plus the rule list; swipe a row away to delete it, tap it to edit. */
@Composable
fun ReplacementsTab(
    rules: List<Replacement>,
    onAdd: (originals: String, replacement: String, onResult: (DictionaryViewModel.AddResult) -> Unit) -> Unit,
    onEdit: (old: Replacement, originals: String, replacement: String) -> Unit,
    onDelete: (Replacement) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by rememberSaveable { mutableStateOf<Int?>(null) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Spacing.xl),
    ) {
        item(key = "add") { AddReplacementRow(onAdd = onAdd) }
        if (rules.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Outlined.SwapHoriz,
                    title = stringResource(R.string.dictionary_replacements_empty_title),
                    body = stringResource(R.string.dictionary_replacements_empty_body),
                    modifier = Modifier.padding(top = Spacing.xl),
                )
            }
        }
        items(rules, key = { it.key() }) { rule ->
            ReplacementRow(
                rule = rule,
                onClick = { editing = rules.indexOf(rule) },
                onDelete = { onDelete(rule) },
                modifier = Modifier.animateItem(),
            )
            HorizontalDivider()
        }
    }
    val editedRule = editing?.let(rules::getOrNull)
    if (editedRule != null) {
        ReplacementEditDialog(
            rule = editedRule,
            onSave = { originals, replacement ->
                onEdit(editedRule, originals, replacement)
                editing = null
            },
            onDelete = {
                onDelete(editedRule)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun AddReplacementRow(
    onAdd: (originals: String, replacement: String, onResult: (DictionaryViewModel.AddResult) -> Unit) -> Unit,
) {
    var originals by rememberSaveable { mutableStateOf("") }
    var replacement by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<DictionaryViewModel.AddResult?>(null) }
    val submit = {
        onAdd(originals, replacement) { result ->
            error = result.takeIf { it != DictionaryViewModel.AddResult.ADDED }
            if (result == DictionaryViewModel.AddResult.ADDED) {
                originals = ""
                replacement = ""
            }
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        OutlinedTextField(
            value = originals,
            onValueChange = {
                originals = it
                error = null
            },
            label = { Text(stringResource(R.string.dictionary_original_label)) },
            placeholder = { Text(stringResource(R.string.dictionary_original_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            OutlinedTextField(
                value = replacement,
                onValueChange = {
                    replacement = it
                    error = null
                },
                label = { Text(stringResource(R.string.dictionary_replacement_label)) },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { result -> { Text(stringResource(result.messageRes())) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.weight(1f),
            )
            FilledIconButton(
                onClick = submit,
                enabled = originals.isNotBlank() && replacement.isNotBlank(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.dictionary_add_replacement))
            }
        }
    }
}

@Composable
private fun ReplacementRow(
    rule: Replacement,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberSwipeToDismissBoxState()
    val deleteLabel = stringResource(R.string.dictionary_delete)
    SwipeToDismissBox(
        state = state,
        modifier = modifier.semantics {
            customActions = listOf(CustomAccessibilityAction(deleteLabel) { onDelete(); true })
        },
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = Spacing.xl),
                contentAlignment = if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                    Alignment.CenterStart
                } else {
                    Alignment.CenterEnd
                },
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
        onDismiss = { onDelete() },
    ) {
        ListItem(
            modifier = Modifier.clickable(onClick = onClick),
            headlineContent = {
                Text(
                    text = stringResource(R.string.dictionary_rule_format, rule.originals.joinToString(", "), rule.replacement),
                    style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
                )
            },
        )
    }
}

@Composable
private fun ReplacementEditDialog(
    rule: Replacement,
    onSave: (originals: String, replacement: String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var originals by rememberSaveable(rule) { mutableStateOf(rule.originals.joinToString(", ")) }
    var replacement by rememberSaveable(rule) { mutableStateOf(rule.replacement) }
    val valid = DictionaryViewModel.parseRule(originals, replacement) != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dictionary_edit_replacement)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = originals,
                    onValueChange = { originals = it },
                    label = { Text(stringResource(R.string.dictionary_original_label)) },
                    supportingText = { Text(stringResource(R.string.dictionary_original_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = replacement,
                    onValueChange = { replacement = it },
                    label = { Text(stringResource(R.string.dictionary_replacement_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(originals, replacement) }, enabled = valid) {
                Text(stringResource(R.string.dictionary_save))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDelete) { Text(stringResource(R.string.dictionary_delete)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.dictionary_cancel)) }
            }
        },
    )
}

private fun Replacement.key(): String = originals.joinToString("\u0000") { it.lowercase() } + "\u0001" + replacement

internal fun DictionaryViewModel.AddResult.messageRes(): Int = when (this) {
    DictionaryViewModel.AddResult.ADDED -> R.string.dictionary_added
    DictionaryViewModel.AddResult.DUPLICATE -> R.string.dictionary_duplicate
    DictionaryViewModel.AddResult.INVALID -> R.string.dictionary_invalid
}

@ThemePreviews
@Composable
private fun ReplacementsTabPreview() {
    PreviewSurface {
        ReplacementsTab(
            rules = listOf(
                Replacement(listOf("whisper plus plus", "whisper pp"), "Whisper++"),
                Replacement(listOf("vois ink"), "VoiceInk"),
            ),
            onAdd = { _, _, _ -> },
            onEdit = { _, _, _ -> },
            onDelete = {},
        )
    }
}
