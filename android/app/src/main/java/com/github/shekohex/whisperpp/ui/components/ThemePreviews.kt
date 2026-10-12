package com.github.shekohex.whisperpp.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.github.shekohex.whisperpp.ui.theme.WhisperTheme

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class ThemePreviews

/** Small phone, foldable/tablet width, and an Arabic RTL phone. Use on main screens. */
@Preview(name = "360dp", widthDp = 360, heightDp = 800, showBackground = true)
@Preview(name = "840dp", widthDp = 840, heightDp = 900, showBackground = true)
@Preview(name = "RTL 360dp", widthDp = 360, heightDp = 800, showBackground = true, locale = "ar")
annotation class WidthPreviews

@Composable
internal fun PreviewSurface(content: @Composable () -> Unit) {
    WhisperTheme(darkTheme = isSystemInDarkTheme(), dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.surface, content = content)
    }
}
