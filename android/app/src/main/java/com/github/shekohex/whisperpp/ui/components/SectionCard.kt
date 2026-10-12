package com.github.shekohex.whisperpp.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.github.shekohex.whisperpp.ui.theme.AppShapes
import com.github.shekohex.whisperpp.ui.theme.Spacing

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            .semantics { heading() },
    )
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier) {
        if (title != null) SectionHeader(title)
        Card(
            shape = AppShapes.card,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Column(modifier = Modifier.padding(vertical = Spacing.sm), content = content)
        }
    }
}

@ThemePreviews
@Composable
private fun SectionCardPreview() {
    PreviewSurface {
        SectionCard(title = "Dictation", modifier = Modifier.padding(Spacing.lg)) {
            SettingsSwitchRow(title = "Auto-send after insert", checked = false, onCheckedChange = {})
            SettingsSwitchRow(
                title = "Format paragraphs",
                supporting = "Split long transcripts into paragraphs",
                checked = true,
                onCheckedChange = {},
            )
        }
    }
}
