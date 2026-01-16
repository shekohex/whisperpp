package com.github.shekohex.whisperpp.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.shekohex.whisperpp.ui.theme.WhisperToInputTheme

@Composable
fun AppIconCard(
    modifier: Modifier = Modifier,
    isDark: Boolean = false
) {
    val backgroundColor = if (isDark) Color(0xFF1A1A1A) else Color(0xFFFFFFFF)
    
    Surface(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(percent = 22)),
        color = backgroundColor,
        shadowElevation = 8.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            VoiceWaveform(
                modifier = Modifier.fillMaxSize(0.6f)
            )
        }
    }
}

@Preview(name = "Light Mode", showBackground = true)
@Composable
fun PreviewAppIconLight() {
    WhisperToInputTheme(darkTheme = false) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF0F0F0))
                .padding(40.dp),
            contentAlignment = Alignment.Center
        ) {
            AppIconCard(modifier = Modifier.size(128.dp), isDark = false)
        }
    }
}

@Preview(name = "Dark Mode", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PreviewAppIconDark() {
    WhisperToInputTheme(darkTheme = true) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF333333))
                .padding(40.dp),
            contentAlignment = Alignment.Center
        ) {
            AppIconCard(modifier = Modifier.size(128.dp), isDark = true)
        }
    }
}
