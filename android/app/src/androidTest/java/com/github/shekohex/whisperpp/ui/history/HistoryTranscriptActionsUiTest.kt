package com.github.shekohex.whisperpp.ui.history

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.history.TranscriptHistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryTranscriptActionsUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun completedTake_hasOneRetranscribeAction_withReEnhance_notInAttentionCard() {
        val entry = TranscriptHistoryEntry(
            createdAt = 1L,
            rawText = "saved transcript",
            providerId = "provider",
            sttModelId = "model",
            durationMs = 1_000L,
            wordCount = 2,
            audioPath = "recording.wav",
        )
        val state = HistoryDetailViewModel.UiState(entry = entry, isLoading = false, canRetry = true)
        var retries = 0
        var enhancements = 0
        composeRule.setContent {
            MaterialTheme {
                Column {
                    HistoryAttentionCard(entry, state, { retries++ }, {}, {})
                    TranscriptActions(state, { enhancements++ }, {}, { retries++ }, {})
                }
            }
        }

        val retry = composeRule.activity.getString(R.string.history_retranscribe)
        val enhance = composeRule.activity.getString(R.string.history_reenhance)
        composeRule.onAllNodesWithText(retry).assertCountEquals(1)
        composeRule.onNodeWithText(enhance).assertIsDisplayed().performClick()
        composeRule.onNodeWithText(retry).assertIsDisplayed().performClick()

        assertEquals(1, enhancements)
        assertEquals(1, retries)
    }
}
