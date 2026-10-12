package com.github.shekohex.whisperpp.ui.keyboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
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

private val ModeChipMaxWidth = 56.dp

/** Small chip beside the language label: the mode the next dictation uses. */
@Composable
internal fun KeyboardModeChip(state: KeyboardModeUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val active = state.active
    val description = stringResource(R.string.keyboard_mode_chip_description, active.name)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (active.available) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        },
        contentColor = if (active.available) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onErrorContainer
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(active.icon.imageVector, contentDescription = description, modifier = Modifier.size(14.dp))
            Text(
                text = active.name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = ModeChipMaxWidth),
            )
        }
    }
}

/** Bottom-sheet mode picker; [onManage] opens the app's Modes tab. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KeyboardModePickerSheet(
    state: KeyboardModeUiState,
    overlayMaxHeight: Dp,
    onSelect: (String) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        KeyboardModePickerContent(
            state = state,
            onSelect = onSelect,
            onManage = onManage,
            modifier = Modifier.heightIn(max = overlayMaxHeight),
        )
    }
}

@Composable
private fun KeyboardModePickerContent(
    state: KeyboardModeUiState,
    onSelect: (String) -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.keyboard_mode_picker_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.keyboard_mode_picker_helper),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        state.items.forEach { item ->
            KeyboardModeRow(item = item, selected = item.id == state.active.id, onClick = { onSelect(item.id) })
        }
        TextButton(onClick = onManage, modifier = Modifier.align(Alignment.End)) {
            Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.keyboard_mode_manage), modifier = Modifier.padding(start = 8.dp))
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun KeyboardModeRow(item: KeyboardModeUiState.Item, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = when {
            !item.available -> {
                { StatusChip(stringResource(R.string.modes_unavailable), ChipStatus.Error) }
            }
            item.isDefault -> {
                { Text(stringResource(R.string.keyboard_mode_default)) }
            }
            else -> null
        },
        leadingContent = { Icon(item.icon.imageVector, contentDescription = null) },
        trailingContent = if (selected) {
            { Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.keyboard_mode_active)) }
        } else {
            null
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    )
}

@ThemePreviews
@Composable
private fun KeyboardModeChipPreview() {
    val items = listOf(
        KeyboardModeUiState.Item("d", "Dictation", Mode.Icon.MIC, isDefault = true, available = true),
        KeyboardModeUiState.Item("c", "Chat", Mode.Icon.CHAT, isDefault = false, available = true),
        KeyboardModeUiState.Item("e", "Email", Mode.Icon.MAIL, isDefault = false, available = false),
    )
    val state = KeyboardModeUiState(active = items[1], source = ModeResolution.Source.APP, items = items)
    PreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(8.dp)) {
            KeyboardModeChip(state = state, onClick = {})
            KeyboardModePickerContent(state = state, onSelect = {}, onManage = {})
        }
    }
}
