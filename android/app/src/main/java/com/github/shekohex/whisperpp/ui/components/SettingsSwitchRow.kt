package com.github.shekohex.whisperpp.ui.components

import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role

@Composable
fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
) {
    SettingsRow(
        title = title,
        supporting = supporting,
        leadingIcon = leadingIcon,
        enabled = enabled,
        modifier = modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
        trailing = {
            Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        },
    )
}

@ThemePreviews
@Composable
private fun SettingsSwitchRowPreview() {
    PreviewSurface {
        SettingsSwitchRow(
            title = "Remove filler words",
            supporting = "Drops “um”, “uh” and similar from transcripts",
            leadingIcon = Icons.Filled.Mic,
            checked = true,
            onCheckedChange = {},
        )
    }
}
