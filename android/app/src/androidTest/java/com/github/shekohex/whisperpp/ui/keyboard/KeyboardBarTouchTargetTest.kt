package com.github.shekohex.whisperpp.ui.keyboard

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.domain.modes.Mode
import com.github.shekohex.whisperpp.domain.modes.ModeResolution
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** WP-3.7: the keyboard bar's chips/buttons keep 48 dp touch targets and "Ready" fits at 360 dp. */
@RunWith(AndroidJUnit4::class)
class KeyboardBarTouchTargetTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun readyBar_controlsHave48dpTouchTargets_at360dp() {
        val item = KeyboardModeUiState.Item("c", "Chat", Mode.Icon.CHAT, isDefault = false, available = true)
        val modeState = KeyboardModeUiState(active = item, source = ModeResolution.Source.APP, items = listOf(item))
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.requiredWidth(360.dp)) {
                    KeyboardBar(model = previewBarModel(modeState = modeState), actions = previewBarActions())
                }
            }
        }

        listOf(KEYBOARD_CONTEXT_PILL_TAG, KEYBOARD_SETTINGS_BUTTON_TAG, KEYBOARD_COMMAND_BUTTON_TAG).forEach { tag ->
            composeRule.onNodeWithTag(tag)
                .assertIsDisplayed()
                .assertHeightIsAtLeast(48.dp)
                .assertWidthIsAtLeast(48.dp)
        }
        val ready = composeRule.activity.getString(R.string.input_ready)
        // The status is exposed as one live-region description (WP-3.7).
        composeRule.onNodeWithContentDescription(ready).assertIsDisplayed()
    }
}
