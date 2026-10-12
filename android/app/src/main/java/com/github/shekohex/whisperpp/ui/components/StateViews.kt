package com.github.shekohex.whisperpp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.ui.theme.Spacing

@Composable
private fun CenteredMessage(
    icon: ImageVector,
    iconTint: Color,
    title: String?,
    body: String,
    modifier: Modifier,
    action: @Composable (() -> Unit)?,
) {
    Column(
        modifier = modifier.padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(48.dp))
        if (title != null) {
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) action()
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
) {
    CenteredMessage(
        icon = icon,
        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
        title = title,
        body = body,
        modifier = modifier,
        action = action,
    )
}

@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CenteredMessage(
        icon = Icons.Filled.ErrorOutline,
        iconTint = MaterialTheme.colorScheme.error,
        title = null,
        body = message,
        modifier = modifier,
        action = {
            Button(onClick = onRetry) { Text(stringResource(R.string.component_retry)) }
        },
    )
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.loading)
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@ThemePreviews
@Composable
private fun EmptyStatePreview() {
    PreviewSurface {
        EmptyState(
            icon = Icons.Filled.CloudOff,
            title = "No providers yet",
            body = "Add a speech-to-text provider to start dictating.",
            action = { Button(onClick = {}) { Text("Add provider") } },
        )
    }
}

@ThemePreviews
@Composable
private fun ErrorStatePreview() {
    PreviewSurface {
        ErrorState(message = "Couldn’t load your settings.", onRetry = {})
    }
}

@ThemePreviews
@Composable
private fun LoadingStatePreview() {
    PreviewSurface { LoadingState() }
}
