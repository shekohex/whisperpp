package com.github.shekohex.whisperpp.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

@Composable
fun WhisperTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> BrandDarkColorScheme
        else -> BrandLightColorScheme
    }

    CompositionLocalProvider(LocalSpacing provides Spacing) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = WhisperShapes,
            content = content
        )
    }
}

@Deprecated(
    message = "Use WhisperTheme",
    replaceWith = ReplaceWith("WhisperTheme(darkTheme, dynamicColor, content)")
)
@Composable
fun WhisperToInputTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) = WhisperTheme(darkTheme, dynamicColor, content)
