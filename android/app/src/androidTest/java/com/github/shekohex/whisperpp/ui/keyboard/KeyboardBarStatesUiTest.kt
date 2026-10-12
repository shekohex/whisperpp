package com.github.shekohex.whisperpp.ui.keyboard

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.keyboard.KeyboardState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * WP-4.3 (L-08): for every [KeyboardState] the record button exposes the right state description and
 * accessibility actions, and the first action forwards the matching [RecordIntent] (TalkBack never
 * needs the drag gesture).
 */
@RunWith(AndroidJUnit4::class)
class KeyboardBarStatesUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private data class Expectation(
        val state: KeyboardState,
        val stateRes: Int,
        val actionRes: List<Int>,
        val firstIntent: RecordIntent?,
    )

    private val expectations = listOf(
        Expectation(
            KeyboardState.Ready,
            R.string.keyboard_record_state_idle,
            listOf(R.string.record_action_start),
            RecordIntent.PressStart(locked = true),
        ),
        Expectation(
            KeyboardState.Recording,
            R.string.keyboard_record_state_recording,
            listOf(R.string.record_action_send, R.string.record_action_lock, R.string.record_action_cancel),
            RecordIntent.Stop,
        ),
        Expectation(
            KeyboardState.RecordingLocked,
            R.string.keyboard_record_state_locked,
            listOf(R.string.record_action_send, R.string.record_action_pause, R.string.record_action_cancel),
            RecordIntent.Stop,
        ),
        Expectation(
            KeyboardState.Paused,
            R.string.keyboard_record_state_paused,
            listOf(R.string.record_action_send, R.string.record_action_resume, R.string.record_action_cancel),
            RecordIntent.Stop,
        ),
        Expectation(
            KeyboardState.PausedLocked,
            R.string.keyboard_record_state_paused,
            listOf(R.string.record_action_send, R.string.record_action_resume, R.string.record_action_cancel),
            RecordIntent.Stop,
        ),
        Expectation(KeyboardState.Transcribing, R.string.keyboard_record_state_processing, emptyList(), null),
        Expectation(KeyboardState.Enhancing, R.string.keyboard_record_state_processing, emptyList(), null),
    )

    @Test fun ready() = verify(KeyboardState.Ready)

    @Test fun recording() = verify(KeyboardState.Recording)

    @Test fun recordingLocked() = verify(KeyboardState.RecordingLocked)

    @Test fun paused() = verify(KeyboardState.Paused)

    @Test fun pausedLocked() = verify(KeyboardState.PausedLocked)

    @Test fun transcribing() = verify(KeyboardState.Transcribing)

    @Test fun enhancing() = verify(KeyboardState.Enhancing)

    private fun verify(state: KeyboardState) {
        val expected = expectations.single { it.state == state }
        val intents = mutableListOf<RecordIntent>()
        val actions = KeyboardBarActions(
            onRecordIntent = { intents += it },
            onGestureUi = {},
            onCancelAction = {},
            onDeleteAction = {},
            onUndoAction = {},
            onEnhancementUndoAction = {},
            onOpenSettings = {},
            onOpenContextSheet = {},
            onAllowMicPermission = {},
            onBlockedAction = {},
            onCommandToggle = {},
            onDismissHint = {},
        )
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.requiredWidth(360.dp)) {
                    KeyboardBar(model = previewBarModel(state = state), actions = actions)
                }
            }
        }

        val stateLabel = composeRule.activity.getString(expected.stateRes)
        val button = composeRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, stateLabel),
        )
        button.assertIsDisplayed()

        val expectedLabels = expected.actionRes.map { composeRule.activity.getString(it) }
        button.assert(
            SemanticsMatcher("custom actions are $expectedLabels ($state)") { node ->
                val actions = node.config.getOrNull(SemanticsActions.CustomActions).orEmpty()
                actions.map { action -> action.label } == expectedLabels
            },
        )

        if (expected.firstIntent == null) {
            button.assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        } else {
            button.performSemanticsAction(SemanticsActions.OnClick)
            assertEquals(state.name, listOf(expected.firstIntent), intents)
        }
    }
}
