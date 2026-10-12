package com.github.shekohex.whisperpp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import com.github.shekohex.whisperpp.ui.theme.Spacing

/** Rounded surface with a soft primaryContainer → tertiaryContainer gradient (dashboard hero). */
@Composable
fun GradientHeroSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    // Stop short of pure tertiaryContainer so onPrimaryContainer text keeps its contrast.
    val brush = Brush.linearGradient(
        listOf(scheme.primaryContainer, lerp(scheme.primaryContainer, scheme.tertiaryContainer, 0.6f)),
    )
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = scheme.primaryContainer,
        contentColor = scheme.onPrimaryContainer,
    ) {
        Column(
            modifier = Modifier
                .clip(shape)
                .background(brush)
                .padding(Spacing.xl),
            content = content,
        )
    }
}

@ThemePreviews
@Composable
private fun GradientHeroSurfacePreview() {
    PreviewSurface {
        GradientHeroSurface(modifier = Modifier.padding(Spacing.lg)) {
            Text("You have saved", style = MaterialTheme.typography.titleMedium)
            Text("41h 30m", style = MaterialTheme.typography.displaySmall)
            Text("with Whisper++", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
