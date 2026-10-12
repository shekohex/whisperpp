package com.github.shekohex.whisperpp.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

private val SettingsRowMinHeight = 56.dp
private const val DisabledAlpha = 0.38f

@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val clickable = if (onClick != null) {
        Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    ListItem(
        modifier = modifier
            .heightIn(min = SettingsRowMinHeight)
            .then(clickable)
            .then(if (enabled) Modifier else Modifier.alpha(DisabledAlpha)),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = supporting?.let { text ->
            { Text(text, style = MaterialTheme.typography.bodyMedium) }
        },
        leadingContent = leadingIcon?.let { icon ->
            { Icon(imageVector = icon, contentDescription = null) }
        },
        trailingContent = trailing,
    )
}

/** A [SettingsRow] that navigates somewhere: trailing chevron, mirrored in RTL. */
@Composable
fun SettingsNavRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leadingIcon: ImageVector? = null,
) {
    SettingsRow(
        title = title,
        supporting = supporting,
        leadingIcon = leadingIcon,
        modifier = modifier,
        trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        onClick = onClick,
    )
}

@ThemePreviews
@Composable
private fun SettingsRowPreview() {
    PreviewSurface {
        SettingsRow(
            title = "Speech-to-text provider",
            supporting = "OpenAI · gpt-4o-transcribe",
            leadingIcon = Icons.Filled.Mic,
            trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
            onClick = {},
        )
    }
}
