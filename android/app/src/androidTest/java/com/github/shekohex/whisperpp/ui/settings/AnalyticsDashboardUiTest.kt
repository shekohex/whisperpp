package com.github.shekohex.whisperpp.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.shekohex.whisperpp.dataStore
import com.github.shekohex.whisperpp.analytics.AnalyticsRepository
import com.github.shekohex.whisperpp.analytics.analyticsDataStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class AnalyticsDashboardUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val activity get() = composeRule.activity

    @Before
    fun setUp() {
        clearStores()
    }

    @After
    fun tearDown() {
        clearStores()
    }

    @Test
    fun settingsHome_showsAnalyticsCardBeforeSetupSection_andOpensAnalyticsRoute() {
        setSettingsContent(SettingsScreen.Main.route)

        val analyticsCard = composeRule.onNodeWithTag("analytics_home_card")
        analyticsCard.assertIsDisplayed()

        val analyticsTop = analyticsCard.fetchSemanticsNode().boundsInRoot.top
        val setupTop = composeRule.onNodeWithText("Setup essentials").fetchSemanticsNode().boundsInRoot.top
        assert(analyticsTop < setupTop)

        analyticsCard.performClick()
        composeRule.onNodeWithText("Analytics dashboard").assertIsDisplayed()
    }

    @Test
    fun analyticsScreen_showsEmptyTrendScaffold_whenHistoryIsEmpty() {
        setSettingsContent(SettingsScreen.Analytics.route)

        composeRule.onNodeWithText("Last 7 days").assertIsDisplayed()
        composeRule.onNodeWithText(
            "No bars yet, but the trend is ready to light up with your next Whisper++ session.",
        ).assertIsDisplayed()
    }

    @Test
    fun analyticsScreen_showsNamedLifetimeTotals() {
        seedCompletedAndCancelledAnalytics()
        setSettingsContent(SettingsScreen.Analytics.route)

        composeRule.onNodeWithText("Dictation minutes").assertIsDisplayed()
        composeRule.onNodeWithText("Words per minute").assertIsDisplayed()
        composeRule.onNodeWithText("Keystrokes saved").assertIsDisplayed()
        composeRule.onNodeWithTag("analytics_metric_minutes_value").assertIsDisplayed()
        composeRule.onNodeWithTag("analytics_metric_wpm_value").assertIsDisplayed()
        composeRule.onNodeWithTag("analytics_metric_keystrokes_value").assertIsDisplayed()
    }

    @Test
    fun analyticsScreen_resetConfirmation_zeroesDashboardAndStaysVisible() {
        seedCompletedAndCancelledAnalytics()
        setSettingsContent(SettingsScreen.Analytics.route)

        composeRule.onNodeWithText("Reset analytics").performClick()
        composeRule.onNodeWithText("Reset analytics?").assertIsDisplayed()
        composeRule.onNodeWithText("Reset").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag("analytics_metric_minutes_value").assertTextEquals("0")
            }.isSuccess
        }

        composeRule.onNodeWithText("Analytics dashboard").assertIsDisplayed()
        composeRule.onNodeWithTag("analytics_metric_minutes_value").assertIsDisplayed()
        composeRule.onNodeWithTag("analytics_metric_wpm_value").assertIsDisplayed()
        composeRule.onNodeWithTag("analytics_metric_keystrokes_value").assertIsDisplayed()
        composeRule.onNodeWithTag("analytics_completed_sessions_value").assertIsDisplayed()
        composeRule.onNodeWithTag("analytics_cancelled_sessions_value").assertIsDisplayed()
        composeRule.onNodeWithTag("analytics_raw_words_value").assertIsDisplayed()
        composeRule.onNodeWithTag("analytics_final_words_value").assertIsDisplayed()
        composeRule.onNodeWithText(
            "No bars yet, but the trend is ready to light up with your next Whisper++ session.",
        ).assertIsDisplayed()
    }

    private fun setSettingsContent(startRoute: String) {
        composeRule.setContent {
            MaterialTheme {
                SettingsNavigation(
                    dataStore = activity.dataStore,
                    startRoute = startRoute,
                )
            }
        }
    }

    private fun clearStores() = runBlocking {
        activity.dataStore.edit { it.clear() }
        AnalyticsRepository(activity.analyticsDataStore).resetAnalytics()
    }

    private fun seedCompletedAndCancelledAnalytics() = runBlocking {
        val repository = AnalyticsRepository(activity.analyticsDataStore)
        val today = LocalDate.now()
        repository.recordCompletedSession(
            durationMs = 120_000,
            rawText = "alpha beta gamma delta epsilon zeta",
            finalInsertedText = "alpha beta gamma delta",
            recordedOn = today,
        )
        repository.recordCancelledSession(
            durationMs = 30_000,
            recordedOn = today,
        )
    }
}
