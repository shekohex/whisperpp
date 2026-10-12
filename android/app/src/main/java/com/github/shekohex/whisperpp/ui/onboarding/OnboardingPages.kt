package com.github.shekohex.whisperpp.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.ui.components.PreviewSurface
import com.github.shekohex.whisperpp.ui.components.SetupStepChip
import com.github.shekohex.whisperpp.ui.components.ThemePreviews
import com.github.shekohex.whisperpp.ui.theme.Spacing

@Composable
internal fun WelcomePage(modifier: Modifier = Modifier) {
    OnboardingPageLayout(
        icon = Icons.Filled.GraphicEq,
        title = stringResource(R.string.onboarding_welcome_title),
        body = stringResource(R.string.onboarding_welcome_body),
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.onboarding_welcome_cloud),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun EnableKeyboardPage(
    enabled: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingPageLayout(
        icon = Icons.Filled.Keyboard,
        title = stringResource(R.string.onboarding_enable_title),
        body = stringResource(R.string.onboarding_enable_body),
        modifier = modifier,
    ) {
        SetupStepChip(
            done = enabled,
            doneText = stringResource(R.string.setup_status_keyboard_enabled),
            pendingText = stringResource(R.string.setup_status_keyboard_not_enabled),
        )
        StepButton(text = stringResource(R.string.onboarding_enable_action), done = enabled, onClick = onOpenSettings)
    }
}

@Composable
internal fun SelectKeyboardPage(
    enabled: Boolean,
    selected: Boolean,
    onShowPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingPageLayout(
        icon = Icons.Filled.TouchApp,
        title = stringResource(R.string.onboarding_select_title),
        body = stringResource(R.string.onboarding_select_body),
        modifier = modifier,
    ) {
        SetupStepChip(
            done = selected,
            doneText = stringResource(R.string.setup_status_keyboard_selected),
            pendingText = stringResource(R.string.setup_status_keyboard_not_selected),
        )
        if (!enabled && !selected) {
            Text(
                text = stringResource(R.string.onboarding_select_needs_enable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        StepButton(
            text = stringResource(R.string.onboarding_select_action),
            done = selected,
            enabled = enabled || selected,
            onClick = onShowPicker,
        )
    }
}

@Composable
internal fun MicrophonePage(
    granted: Boolean,
    permanentlyDenied: Boolean,
    onAllow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingPageLayout(
        icon = Icons.Filled.Mic,
        title = stringResource(R.string.onboarding_mic_title),
        body = stringResource(R.string.onboarding_mic_body),
        modifier = modifier,
    ) {
        SetupStepChip(
            done = granted,
            doneText = stringResource(R.string.setup_status_mic_granted),
            pendingText = stringResource(R.string.setup_status_mic_not_granted),
        )
        if (!granted && permanentlyDenied) {
            Text(
                text = stringResource(R.string.onboarding_mic_denied),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        StepButton(
            text = stringResource(
                if (permanentlyDenied) R.string.onboarding_mic_open_settings else R.string.onboarding_mic_action,
            ),
            done = granted,
            onClick = onAllow,
            modifier = Modifier.testTag("onboarding_mic_button"),
        )
    }
}

@Composable
internal fun ProviderPage(
    configured: Boolean,
    hasProviders: Boolean,
    onConnect: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingPageLayout(
        icon = Icons.Filled.Cloud,
        title = stringResource(R.string.onboarding_provider_title),
        body = stringResource(R.string.onboarding_provider_body),
        modifier = modifier,
    ) {
        SetupStepChip(
            done = configured,
            doneText = stringResource(R.string.setup_status_provider_ready),
            pendingText = stringResource(R.string.setup_status_provider_missing),
        )
        if (!configured) {
            Button(onClick = onConnect) {
                Text(
                    stringResource(
                        if (hasProviders) R.string.onboarding_provider_choose else R.string.onboarding_provider_add,
                    ),
                )
            }
            OutlinedButton(onClick = onLater) { Text(stringResource(R.string.onboarding_later)) }
        }
    }
}

@Composable
private fun StepButton(
    text: String,
    done: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (done) {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) { Text(text) }
    } else {
        Button(onClick = onClick, enabled = enabled, modifier = modifier) { Text(text) }
    }
}

@Composable
private fun OnboardingPageLayout(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Box(modifier = Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        content()
    }
}

@ThemePreviews
@Composable
private fun WelcomePagePreview() {
    PreviewSurface { WelcomePage() }
}

@ThemePreviews
@Composable
private fun EnableKeyboardPagePreview() {
    PreviewSurface { EnableKeyboardPage(enabled = false, onOpenSettings = {}) }
}

@ThemePreviews
@Composable
private fun SelectKeyboardPagePreview() {
    PreviewSurface { SelectKeyboardPage(enabled = true, selected = true, onShowPicker = {}) }
}

@ThemePreviews
@Composable
private fun MicrophonePagePreview() {
    PreviewSurface { MicrophonePage(granted = false, permanentlyDenied = true, onAllow = {}) }
}

@ThemePreviews
@Composable
private fun ProviderPagePreview() {
    PreviewSurface { ProviderPage(configured = false, hasProviders = false, onConnect = {}, onLater = {}) }
}
