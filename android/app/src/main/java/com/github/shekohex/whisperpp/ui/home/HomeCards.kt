package com.github.shekohex.whisperpp.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.ui.components.PreviewSurface
import com.github.shekohex.whisperpp.ui.components.ThemePreviews
import com.github.shekohex.whisperpp.ui.theme.AppShapes
import com.github.shekohex.whisperpp.ui.theme.Spacing

internal data class HomeSetupIssue(
    val message: String,
    val actionLabel: String,
    val onFix: () -> Unit,
)

/** Problems the setup checklist doesn't cover (stale selections, pending restore repairs). */
@Composable
internal fun HomeSetupBanner(
    issues: List<HomeSetupIssue>,
    modifier: Modifier = Modifier,
) {
    val onContainer = MaterialTheme.colorScheme.onErrorContainer
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("settings_home_setup_banner"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = onContainer,
        ),
        shape = AppShapes.card,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(imageVector = Icons.Default.Warning, contentDescription = null)
                Text(text = stringResource(R.string.home_issues_title), style = MaterialTheme.typography.titleMedium)
            }
            issues.take(MAX_ISSUES).forEach { issue ->
                Text(text = issue.message, style = MaterialTheme.typography.bodyMedium)
            }
            issues.firstOrNull()?.let { first ->
                TextButton(
                    onClick = first.onFix,
                    colors = ButtonDefaults.textButtonColors(contentColor = onContainer),
                ) {
                    Text(first.actionLabel)
                }
            }
        }
    }
}

private const val MAX_ISSUES = 3

@ThemePreviews
@Composable
private fun HomeSetupBannerPreview() {
    PreviewSurface {
        HomeSetupBanner(
            issues = listOf(HomeSetupIssue("Last restore still needs 2 repair steps.", "Review restore") {}),
            modifier = Modifier.padding(Spacing.lg),
        )
    }
}
