package com.github.shekohex.whisperpp.ui.history

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDirection
import com.github.shekohex.whisperpp.ui.components.PreviewSurface
import com.github.shekohex.whisperpp.ui.components.ThemePreviews
import com.github.shekohex.whisperpp.ui.theme.Spacing

/** Full transcript text, selectable, with direction taken from the content (mixed Arabic/English). */
@Composable
fun TranscriptTextCard(text: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        SelectionContainer {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
                modifier = Modifier.padding(Spacing.lg),
            )
        }
    }
}

@ThemePreviews
@Composable
private fun TranscriptTextCardPreview() {
    PreviewSurface {
        TranscriptTextCard(text = "Let's meet at المطار at 9, ثم نذهب إلى the hotel.", modifier = Modifier.padding(Spacing.lg))
    }
}
