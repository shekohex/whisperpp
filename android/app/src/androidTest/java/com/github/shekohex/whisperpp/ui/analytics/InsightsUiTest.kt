package com.github.shekohex.whisperpp.ui.analytics

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.shekohex.whisperpp.analytics.AnalyticsPeriod
import com.github.shekohex.whisperpp.analytics.AnalyticsPeriodSnapshot
import com.github.shekohex.whisperpp.ui.home.DashboardHeroCard
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InsightsUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun heroCard_showsTimeSavedAndMilestone_andOpensInsights() {
        var opened = false
        composeRule.setContent {
            MaterialTheme {
                DashboardHeroCard(lifetimeWords = 165_800, timeSavedSeconds = 41 * 3_600L + 30 * 60L, onViewInsights = { opened = true })
            }
        }

        composeRule.onNodeWithText("You have saved 41h 30m with Whisper++").assertIsDisplayed()
        composeRule.onNodeWithText("Dictated 165.8K words, equivalent to Homer’s Odyssey.").assertIsDisplayed()
        composeRule.onNodeWithText("View insights").performClick()
        assertTrue(opened)
    }

    @Test
    fun heroCard_emptyState() {
        composeRule.setContent {
            MaterialTheme { DashboardHeroCard(lifetimeWords = 0, timeSavedSeconds = 0, onViewInsights = {}) }
        }

        composeRule.onNodeWithText("Continue using Whisper++ to unlock stats and insights.").assertIsDisplayed()
    }

    @Test
    fun insights_periodSelectorAndReset() {
        val selected = mutableListOf<AnalyticsPeriod>()
        composeRule.setContent {
            var totals by remember { mutableStateOf(totals(words = 165_800, sessions = 12)) }
            MaterialTheme {
                InsightsContent(
                    state = AnalyticsViewModel.State(
                        period = AnalyticsPeriod.LAST_30_DAYS,
                        totals = totals,
                        calendar = sampleCalendar(),
                        modelLabels = emptyMap(),
                    ),
                    onSelectPeriod = { selected += it },
                    onConfirmReset = { totals = totals(words = 0, sessions = 0) },
                )
            }
        }

        composeRule.onNodeWithTag("insights_stat_words").assertTextEquals("165.8K")
        composeRule.onNodeWithText("7d").performClick()
        assertTrue(selected == listOf(AnalyticsPeriod.LAST_7_DAYS))

        composeRule.onNodeWithTag(INSIGHTS_RESET_TAG).performScrollTo().performClick()
        composeRule.onNodeWithText("Reset analytics?").assertIsDisplayed()
        composeRule.onNodeWithText("Reset").performClick()
        composeRule.onNodeWithTag("insights_stat_words").performScrollTo().assertTextEquals("0")
    }

    private fun totals(words: Long, sessions: Long) = AnalyticsPeriodSnapshot(
        period = AnalyticsPeriod.LAST_30_DAYS,
        words = words,
        sessions = sessions,
    )
}
