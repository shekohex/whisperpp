package com.github.shekohex.whisperpp.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.shekohex.whisperpp.analytics.AnalyticsSnapshot
import com.github.shekohex.whisperpp.dataStore
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnalyticsDashboardUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsHome_showsAnalyticsCardBeforeSetupSection_andOpensAnalyticsRoute() {
        setHomeContent()

        val analyticsCard = composeRule.onNodeWithTag("analytics_home_card")
        analyticsCard.assertIsDisplayed()

        val analyticsTop = analyticsCard.fetchSemanticsNode().boundsInRoot.top
        val setupTop = composeRule.onNodeWithText("Setup essentials").fetchSemanticsNode().boundsInRoot.top
        assertTrue(analyticsTop < setupTop)

        analyticsCard.performClick()
        composeRule.onNodeWithText("Analytics dashboard").assertIsDisplayed()
    }

    @Test
    fun analyticsScreen_showsEmptyTrendScaffold_whenHistoryIsEmpty() {
        setAnalyticsContent(AnalyticsSnapshot())

        composeRule.onNodeWithText("Last 7 days").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(
            "No bars yet, but the trend is ready to light up with your next Whisper++ session.",
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun analyticsScreen_showsNamedLifetimeTotals() {
        setAnalyticsContent(
            AnalyticsSnapshot(
                totalCompletedSessions = 2,
                totalCancelledSessions = 1,
                totalRecordingDurationMinutes = 2,
                totalRawWordCount = 6,
                totalFinalInsertedWordCount = 4,
                averageWordsPerMinute = 120,
                estimatedKeystrokesSaved = 22,
                estimatedTimeSavedMinutes = 3,
            ),
        )

        composeRule.onNodeWithText("Dictation minutes").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Words per minute").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Keystrokes saved").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("analytics_metric_minutes_value").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("analytics_metric_wpm_value").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("analytics_metric_keystrokes_value").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun analyticsScreen_resetConfirmation_zeroesDashboardAndStaysVisible() {
        setAnalyticsContent(
            AnalyticsSnapshot(
                totalCompletedSessions = 2,
                totalCancelledSessions = 1,
                totalRecordingDurationMinutes = 2,
                totalRawWordCount = 6,
                totalFinalInsertedWordCount = 4,
                averageWordsPerMinute = 120,
                estimatedKeystrokesSaved = 22,
                estimatedTimeSavedMinutes = 3,
            ),
        )

        composeRule.onNodeWithText("Reset analytics").performScrollTo().performClick()
        composeRule.onNodeWithText("Reset analytics?").assertIsDisplayed()
        composeRule.onNodeWithText("Reset").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag("analytics_metric_minutes_value").assertTextEquals("0")
            }.isSuccess
        }

        composeRule.onNodeWithText("Analytics dashboard").assertIsDisplayed()
        composeRule.onNodeWithTag("analytics_metric_minutes_value").performScrollTo().assertTextEquals("0")
        composeRule.onNodeWithTag("analytics_metric_wpm_value").performScrollTo().assertTextEquals("0")
        composeRule.onNodeWithTag("analytics_metric_keystrokes_value").performScrollTo().assertTextEquals("0")
        composeRule.onNodeWithTag("analytics_completed_sessions_value").performScrollTo().assertTextEquals("0")
        composeRule.onNodeWithTag("analytics_cancelled_sessions_value").performScrollTo().assertTextEquals("0")
        composeRule.onNodeWithTag("analytics_raw_words_value").performScrollTo().assertTextEquals("0")
        composeRule.onNodeWithTag("analytics_final_words_value").performScrollTo().assertTextEquals("0")
        composeRule.onNodeWithText(
            "No bars yet, but the trend is ready to light up with your next Whisper++ session.",
        ).performScrollTo().assertIsDisplayed()
    }

    private fun setHomeContent() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeRule.setContent {
            MaterialTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = SettingsScreen.Main.route) {
                    composable(SettingsScreen.Main.route) {
                        SettingsHomeScreen(
                            dataStore = context.dataStore,
                            navController = navController,
                        )
                    }
                    composable(SettingsScreen.Analytics.route) {
                        androidx.compose.material3.Text("Analytics dashboard")
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun setAnalyticsContent(initialSnapshot: AnalyticsSnapshot) {
        composeRule.setContent {
            var snapshot by mutableStateOf(initialSnapshot)
            MaterialTheme {
                AnalyticsDashboardScreen(
                    snapshot = snapshot,
                    onBack = {},
                    onConfirmReset = { snapshot = AnalyticsSnapshot() },
                )
            }
        }
        composeRule.waitForIdle()
    }
}
