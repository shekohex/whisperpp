package com.github.shekohex.whisperpp.ui.keyboard

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirstUseDisclosureContentUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun content_isVisibleAndNonEmptyInConstrainedHost() {
        val disclosure = FirstUseDisclosureUiState(
            title = "Dictation (audio)",
            dataSent = "Recorded audio is uploaded for transcription.",
            endpointLines = listOf(
                "OpenAI: https://api.openai.com/v1/audio/transcriptions",
                "Backup: https://example.com/transcribe",
            ),
            contextLine = "Use Context is disabled. No before-cursor context text is sent.",
        )

        assertTrue(disclosure.title.isNotBlank())
        assertTrue(disclosure.dataSent.isNotBlank())
        assertTrue(disclosure.endpointLines.all { it.isNotBlank() })
        assertTrue(disclosure.contextLine.isNotBlank())

        composeRule.setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier
                        .requiredHeight(240.dp)
                        .requiredWidth(360.dp),
                ) {
                    FirstUseDisclosureContent(
                        disclosure = disclosure,
                        onContinue = {},
                        onOpenPrivacySafety = {},
                        onCancel = {},
                    )
                }
            }
        }

        assertDisplayedWithScroll(disclosure.title)
        assertDisplayedWithScroll(disclosure.dataSent)
        disclosure.endpointLines.forEach(::assertDisplayedWithScroll)
        assertDisplayedWithScroll(disclosure.contextLine)
        assertDisplayedWithScroll("Continue")
        assertDisplayedWithScroll("Open Privacy & Safety")
        assertDisplayedWithScroll("Cancel")
    }

    private fun assertDisplayedWithScroll(text: String) {
        composeRule.onNodeWithText(text).performScrollTo().assertIsDisplayed()
    }
}
