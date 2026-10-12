package com.github.shekohex.whisperpp.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.ui.components.PreviewSurface
import com.github.shekohex.whisperpp.ui.components.ThemePreviews
import com.github.shekohex.whisperpp.ui.theme.Spacing

/** Third-party libraries bundled in the app; names and license identifiers are not translated. */
internal data class OpenSourceLibrary(val name: String, val license: String)

private const val APACHE_2 = "Apache License 2.0"

internal val OPEN_SOURCE_LIBRARIES = listOf(
    OpenSourceLibrary("Android Jetpack (AndroidX, Compose, Room, WorkManager, DataStore)", APACHE_2),
    OpenSourceLibrary("Material Components for Android", APACHE_2),
    OpenSourceLibrary("Kotlin standard library & kotlinx.coroutines", APACHE_2),
    OpenSourceLibrary("Ktor", APACHE_2),
    OpenSourceLibrary("OkHttp", APACHE_2),
    OpenSourceLibrary("Gson", APACHE_2),
    OpenSourceLibrary("compose-markdown", "MIT License"),
    OpenSourceLibrary("quick-transfer", APACHE_2),
)

@Composable
internal fun OpenSourceLicensesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_licenses_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(stringResource(R.string.about_licenses_intro), style = MaterialTheme.typography.bodyMedium)
                OPEN_SOURCE_LIBRARIES.forEach { library ->
                    Column {
                        Text(library.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            library.license,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.about_licenses_close)) }
        },
    )
}

@ThemePreviews
@Composable
private fun OpenSourceLicensesDialogPreview() {
    PreviewSurface { OpenSourceLicensesDialog(onDismiss = {}) }
}
