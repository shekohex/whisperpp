package com.github.shekohex.whisperpp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.github.shekohex.whisperpp.ui.theme.AppShapes
import com.github.shekohex.whisperpp.ui.theme.OnWarningContainerDark
import com.github.shekohex.whisperpp.ui.theme.OnWarningContainerLight
import com.github.shekohex.whisperpp.ui.theme.Spacing
import com.github.shekohex.whisperpp.ui.theme.WarningContainerDark
import com.github.shekohex.whisperpp.ui.theme.WarningContainerLight

enum class ChipStatus { Ok, Warning, Error, Neutral }

@Composable
fun StatusChip(
    text: String,
    status: ChipStatus,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val isLight = scheme.surface.luminance() > 0.5f
    val (container, content) = when (status) {
        ChipStatus.Ok -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        ChipStatus.Warning -> if (isLight) {
            WarningContainerLight to OnWarningContainerLight
        } else {
            WarningContainerDark to OnWarningContainerDark
        }
        ChipStatus.Error -> scheme.errorContainer to scheme.onErrorContainer
        ChipStatus.Neutral -> scheme.surfaceContainerHigh to scheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier,
        shape = AppShapes.chip,
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs + 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@ThemePreviews
@Composable
private fun StatusChipPreview() {
    PreviewSurface {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            StatusChip("Keyboard enabled", ChipStatus.Ok, icon = Icons.Filled.CheckCircle)
            StatusChip("Microphone needed", ChipStatus.Warning, icon = Icons.Filled.WarningAmber)
            StatusChip("Connection failed", ChipStatus.Error, icon = Icons.Filled.ErrorOutline)
            StatusChip("Not configured", ChipStatus.Neutral)
        }
    }
}
