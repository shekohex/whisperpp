package com.github.shekohex.whisperpp.ui.modes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.domain.modes.Mode
import com.github.shekohex.whisperpp.domain.modes.ModeAvailability
import com.github.shekohex.whisperpp.ui.components.ChipStatus
import com.github.shekohex.whisperpp.ui.components.PreviewSurface
import com.github.shekohex.whisperpp.ui.components.StatusChip
import com.github.shekohex.whisperpp.ui.components.ThemePreviews
import com.github.shekohex.whisperpp.ui.settings.common.languageLabel
import com.github.shekohex.whisperpp.ui.theme.AppShapes
import com.github.shekohex.whisperpp.ui.theme.Spacing

private const val MAX_APP_LABELS = 2

/** A mode card (§4.10): header with icon/toggle/menu, app count, setting chips and availability. */
@Composable
internal fun ModeCard(
    card: ModesViewModel.Card,
    onClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onSetDefault: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mode = card.mode
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.md, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            ModeCardHeader(
                mode = mode,
                onEnabledChange = onEnabledChange,
                onSetDefault = onSetDefault,
                onDuplicate = onDuplicate,
                onDelete = onDelete,
            )
            ModeCardChips(card = card, modifier = Modifier.padding(end = Spacing.md))
        }
    }
}

@Composable
private fun ModeCardHeader(
    mode: Mode,
    onEnabledChange: (Boolean) -> Unit,
    onSetDefault: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = AppShapes.chip,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Icon(mode.icon.imageVector, contentDescription = null, modifier = Modifier.padding(Spacing.sm).size(24.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Spacing.md),
        ) {
            Text(
                text = mode.name,
                style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Content),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = modeSubtitle(mode),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!mode.isDefault) {
            val switchDescription = stringResource(R.string.modes_card_enabled_description, mode.name)
            Switch(
                checked = mode.enabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.semantics { contentDescription = switchDescription },
            )
        }
        ModeCardMenu(
            isDefault = mode.isDefault,
            onSetDefault = onSetDefault,
            onDuplicate = onDuplicate,
            onDelete = onDelete,
        )
    }
}

@Composable
private fun modeSubtitle(mode: Mode): String {
    val apps = if (mode.appPackages.isEmpty()) {
        stringResource(R.string.modes_card_no_apps)
    } else {
        pluralStringResource(R.plurals.modes_card_apps, mode.appPackages.size, mode.appPackages.size)
    }
    return if (mode.isDefault) stringResource(R.string.modes_card_default_subtitle, apps) else apps
}

@Composable
private fun ModeCardMenu(
    isDefault: Boolean,
    onSetDefault: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.modes_card_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (!isDefault) {
                MenuItem(Icons.Outlined.StarOutline, stringResource(R.string.modes_action_set_default)) {
                    expanded = false
                    onSetDefault()
                }
            }
            MenuItem(Icons.Filled.ContentCopy, stringResource(R.string.modes_action_duplicate)) {
                expanded = false
                onDuplicate()
            }
            if (!isDefault) {
                MenuItem(Icons.Filled.Delete, stringResource(R.string.modes_action_delete)) {
                    expanded = false
                    onDelete()
                }
            }
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModeCardChips(card: ModesViewModel.Card, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (card.availability is ModeAvailability.Unavailable) {
            StatusChip(
                text = stringResource(R.string.modes_unavailable),
                status = ChipStatus.Error,
                icon = Icons.Filled.ErrorOutline,
            )
        }
        if (card.mode.isDefault) {
            StatusChip(stringResource(R.string.modes_default_chip), ChipStatus.Ok, icon = Icons.Filled.Star)
        }
        StatusChip(
            text = card.sttModel ?: stringResource(R.string.modes_chip_not_set),
            status = ChipStatus.Neutral,
            icon = Icons.Filled.Mic,
        )
        StatusChip(
            text = card.language?.let { languageLabel(it) } ?: stringResource(R.string.modes_chip_keyboard_language),
            status = ChipStatus.Neutral,
            icon = Icons.Filled.Language,
        )
        StatusChip(
            text = when {
                !card.enhancementOn -> stringResource(R.string.modes_chip_enhancement_off)
                else -> card.enhancementModel ?: stringResource(R.string.modes_chip_not_set)
            },
            status = ChipStatus.Neutral,
            icon = Icons.Filled.AutoAwesome,
        )
        card.prompt?.let { StatusChip(it, ChipStatus.Neutral, icon = Icons.Filled.Description) }
        if (card.appLabels.isNotEmpty()) {
            StatusChip(appsChipText(card.appLabels), ChipStatus.Neutral, icon = Icons.Filled.Keyboard)
        }
    }
}

@Composable
private fun appsChipText(labels: List<String>): String {
    val shown = labels.take(MAX_APP_LABELS).joinToString(stringResource(R.string.modes_list_separator))
    val more = labels.size - MAX_APP_LABELS
    return if (more > 0) stringResource(R.string.modes_chip_apps_more, shown, more) else shown
}

@ThemePreviews
@Composable
private fun ModeCardPreview() {
    PreviewSurface {
        ModeCard(
            card = ModesViewModel.Card(
                mode = Mode(id = "chat", name = "Chat", icon = Mode.Icon.CHAT, appPackages = listOf("a", "b", "c")),
                availability = ModeAvailability.Unavailable(ModeAvailability.Reason.MISSING_MODEL, null, "gpt"),
                sttModel = "whisper-1",
                language = "ar",
                enhancementModel = "GPT-4o mini",
                enhancementOn = true,
                prompt = "Chat",
                appLabels = listOf("Slack", "WhatsApp", "Signal"),
            ),
            onClick = {},
            onEnabledChange = {},
            onSetDefault = {},
            onDuplicate = {},
            onDelete = {},
            modifier = Modifier.padding(Spacing.lg),
        )
    }
}
