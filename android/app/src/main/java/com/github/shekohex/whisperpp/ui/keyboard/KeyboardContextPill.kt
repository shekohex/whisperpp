package com.github.shekohex.whisperpp.ui.keyboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.domain.modes.Mode
import com.github.shekohex.whisperpp.domain.modes.ModeResolution
import com.github.shekohex.whisperpp.ui.components.ChipStatus
import com.github.shekohex.whisperpp.ui.components.PreviewSurface
import com.github.shekohex.whisperpp.ui.components.StatusChip
import com.github.shekohex.whisperpp.ui.components.ThemePreviews
import com.github.shekohex.whisperpp.ui.modes.imageVector

const val KEYBOARD_CONTEXT_PILL_TAG = "keyboard_context_pill"
private val PillMaxWidth = 120.dp
private val PillVisualHeight = 40.dp

/** One language a keyboard dictation can use; [code] is "auto" or a BCP-47 language. */
data class KeyboardLanguageOption(val code: String, val label: String)

/**
 * Single "language · mode" pill on the keyboard bar (replaces the stacked 30 dp language label + mode
 * chip of WP-3.10b). 40 dp visually, 48 dp touch target; sighted users see the mode icon and
 * language code only, TalkBack reads both names.
 */
@Composable
internal fun KeyboardContextPill(
    languageLabel: String,
    modeState: KeyboardModeUiState?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = modeState?.active
    val description = if (active != null) {
        stringResource(R.string.keyboard_context_pill_description, languageLabel, active.name)
    } else {
        stringResource(R.string.keyboard_language_pill_description, languageLabel)
    }
    val clickLabel = stringResource(R.string.keyboard_context_pill_action)
    val unavailable = active != null && !active.available
    Surface(
        onClick = onClick,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = PillVisualHeight)
            .widthIn(max = PillMaxWidth)
            .testTag(KEYBOARD_CONTEXT_PILL_TAG)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                onClick(label = clickLabel) { onClick(); true }
            },
        shape = RoundedCornerShape(20.dp),
        color = if (unavailable) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (unavailable) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = active?.icon?.imageVector ?: Icons.Default.Language,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            val text = languageLabel
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Language and mode pickers in one in-keyboard sheet, opened from [KeyboardContextPill]. */
@Composable
internal fun KeyboardLanguageModeSheet(
    languages: List<KeyboardLanguageOption>,
    selectedLanguage: String,
    modeState: KeyboardModeUiState?,
    maxHeight: Dp,
    onLanguageSelected: (String) -> Unit,
    onModeSelected: (String) -> Unit,
    onManageModes: () -> Unit,
    onDismiss: () -> Unit,
) {
    KeyboardInlineSheet(
        title = stringResource(R.string.keyboard_language_mode_title),
        maxHeight = maxHeight,
        onDismiss = onDismiss,
    ) {
        KeyboardLanguageModeContent(
            languages = languages,
            selectedLanguage = selectedLanguage,
            modeState = modeState,
            onLanguageSelected = onLanguageSelected,
            onModeSelected = onModeSelected,
            onManageModes = onManageModes,
        )
    }
}

@Composable
private fun KeyboardLanguageModeContent(
    languages: List<KeyboardLanguageOption>,
    selectedLanguage: String,
    modeState: KeyboardModeUiState?,
    onLanguageSelected: (String) -> Unit,
    onModeSelected: (String) -> Unit,
    onManageModes: () -> Unit,
) {
    SheetSectionTitle(stringResource(R.string.keyboard_language_section))
    Column(modifier = Modifier.selectableGroup()) {
        languages.forEach { option ->
            KeyboardChoiceRow(
                headline = option.label,
                selected = option.code.equals(selectedLanguage, ignoreCase = true),
                onClick = { onLanguageSelected(option.code) },
            )
        }
    }
    if (modeState == null) return
    SheetSectionTitle(stringResource(R.string.keyboard_mode_picker_title))
    Text(
        text = stringResource(R.string.keyboard_mode_picker_helper),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column(modifier = Modifier.selectableGroup()) {
        modeState.items.forEach { item ->
            KeyboardChoiceRow(
                headline = item.name,
                selected = item.id == modeState.active.id,
                onClick = { onModeSelected(item.id) },
                supporting = when {
                    !item.available -> {
                        { StatusChip(stringResource(R.string.modes_unavailable), ChipStatus.Error) }
                    }
                    item.isDefault -> {
                        { Text(stringResource(R.string.keyboard_mode_default)) }
                    }
                    else -> null
                },
                leading = { Icon(item.icon.imageVector, contentDescription = null) },
            )
        }
    }
    TextButton(onClick = onManageModes, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.keyboard_mode_manage), modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun SheetSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun KeyboardChoiceRow(
    headline: String,
    selected: Boolean,
    onClick: () -> Unit,
    supporting: (@Composable () -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(headline, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = supporting,
        leadingContent = leading,
        trailingContent = { RadioButton(selected = selected, onClick = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    )
}

@ThemePreviews
@Composable
private fun KeyboardContextPillPreview() {
    val items = listOf(
        KeyboardModeUiState.Item("d", "Dictation", Mode.Icon.MIC, isDefault = true, available = true),
        KeyboardModeUiState.Item("c", "Chat", Mode.Icon.CHAT, isDefault = false, available = true),
        KeyboardModeUiState.Item("e", "Email", Mode.Icon.MAIL, isDefault = false, available = false),
    )
    val state = KeyboardModeUiState(active = items[1], source = ModeResolution.Source.APP, items = items)
    val languages = listOf(
        KeyboardLanguageOption("auto", "Auto"),
        KeyboardLanguageOption("en", "English"),
        KeyboardLanguageOption("ar", "العربية"),
    )
    PreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(8.dp)) {
            KeyboardContextPill(languageLabel = "EN", modeState = state, onClick = {})
            KeyboardContextPill(languageLabel = "AR", modeState = state, onClick = {})
            KeyboardLanguageModeSheet(
                languages = languages,
                selectedLanguage = "en",
                modeState = state,
                maxHeight = 600.dp,
                onLanguageSelected = {},
                onModeSelected = {},
                onManageModes = {},
                onDismiss = {},
            )
        }
    }
}
