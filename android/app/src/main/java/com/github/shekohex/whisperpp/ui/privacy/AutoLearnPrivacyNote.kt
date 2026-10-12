package com.github.shekohex.whisperpp.ui.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.ui.components.PreviewSurface
import com.github.shekohex.whisperpp.ui.components.ThemePreviews
import com.github.shekohex.whisperpp.ui.theme.Spacing

/** Auto-Learn data note (§4.11 step 6): what is sent, to whom, and where to turn it off. */
@Composable
fun AutoLearnPrivacyNote(
    enabled: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = stringResource(if (enabled) R.string.autolearn_privacy_status_on else R.string.autolearn_privacy_status_off),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = stringResource(R.string.autolearn_privacy_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth().testTag("privacy_autolearn_settings_button"),
        ) {
            Text(stringResource(R.string.autolearn_privacy_open_settings))
        }
    }
}

@ThemePreviews
@Composable
private fun AutoLearnPrivacyNotePreview() {
    PreviewSurface { AutoLearnPrivacyNote(enabled = true, onOpenSettings = {}) }
}
