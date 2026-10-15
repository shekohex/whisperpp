package com.github.shekohex.whisperpp.ui.keyboard

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.shekohex.whisperpp.keyboard.KeyboardState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveTranscriptUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val state = mutableStateOf(KeyboardState.RecordingLocked)
    private val transcript = mutableStateOf("turn left")
    private val enabled = mutableStateOf(true)
    private val confirmationVisible = mutableStateOf(false)
    private val blocked = mutableStateOf(false)

    @Test
    fun correctionsReplaceEarlierWords_andCompletionCollapsesCard() {
        showKeyboard()
        composeRule.onNodeWithText("turn left").assertIsDisplayed()

        composeRule.runOnIdle { transcript.value = "turn right" }
        composeRule.onNodeWithText("turn left").assertDoesNotExist()
        composeRule.onNodeWithText("turn right").assertIsDisplayed()

        composeRule.runOnIdle { state.value = KeyboardState.Transcribing }
        composeRule.onNodeWithText("turn right").assertDoesNotExist()

        composeRule.runOnIdle { state.value = KeyboardState.Enhancing }
        composeRule.onNodeWithText("turn right").assertDoesNotExist()
    }

    @Test
    fun settingAndConfirmationSheetHideCard() {
        showKeyboard()
        composeRule.runOnIdle { enabled.value = false }
        composeRule.onNodeWithText("turn left").assertDoesNotExist()

        composeRule.runOnIdle {
            enabled.value = true
            confirmationVisible.value = true
        }
        composeRule.onNodeWithText("turn left").assertDoesNotExist()

        composeRule.runOnIdle { confirmationVisible.value = false }
        composeRule.onNodeWithText("turn left").assertIsDisplayed()
    }

    @Test
    fun privacyBlockHidesCard_evenIfCaptionWasAlreadyVisible() {
        showKeyboard()
        composeRule.onNodeWithText("turn left").assertIsDisplayed()

        composeRule.runOnIdle { blocked.value = true }

        composeRule.onNodeWithText("turn left").assertDoesNotExist()
    }

    @Test
    fun pausedArabicCaptionFitsNarrowRtlKeyboard_andIdleHidesIt() {
        val arabic = "هذا نص عربي مباشر"
        transcript.value = arabic
        state.value = KeyboardState.PausedLocked
        showKeyboard(LayoutDirection.Rtl)
        composeRule.onNodeWithText(arabic).assertIsDisplayed()

        composeRule.runOnIdle { state.value = KeyboardState.Ready }

        composeRule.onNodeWithText(arabic).assertDoesNotExist()
    }

    private fun showKeyboard(direction: LayoutDirection = LayoutDirection.Ltr) {
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    Box(Modifier.requiredWidth(320.dp)) {
                        KeyboardScreen(
                            state = state.value,
                            languageLabel = "Auto",
                            level = { 0f },
                            recordingTimeMs = { 1_000L },
                            showLongPressHint = false,
                            liveTranscript = transcript.value,
                            showLiveTranscript = enabled.value,
                            cancelConfirmationVisible = confirmationVisible.value,
                            externalSendBlockedByAppPolicy = blocked.value,
                            onRecordIntent = {},
                            onCancelAction = {},
                            onDeleteAction = {},
                            onOpenSettings = {},
                            onDismissHint = {},
                        )
                    }
                }
            }
        }
    }
}
