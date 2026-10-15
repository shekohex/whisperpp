package com.github.shekohex.whisperpp.domain.dictation

import com.github.shekohex.whisperpp.domain.dictation.DictationSessionTestSupport.FakeStreaming
import com.github.shekohex.whisperpp.domain.dictation.DictationSessionTestSupport.Harness
import com.github.shekohex.whisperpp.domain.dictation.DictationSessionTestSupport.sessionTest
import com.github.shekohex.whisperpp.domain.modes.OutputMode
import com.github.shekohex.whisperpp.domain.providers.StreamEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DictationSessionEnhancementStreamingTest {
    private class StreamingEnhancer(private val probeEnabled: Boolean = true) : DictationEnhancer {
        val result = CompletableDeferred<DictationEnhancer.Result>()
        lateinit var emit: suspend (String) -> Unit
        var cancelled = false

        override suspend fun isEnabled(request: DictationEnhancer.Request) = probeEnabled || request.rawText != "a"

        override suspend fun enhance(request: DictationEnhancer.Request): DictationEnhancer.Result =
            error("Expected streaming enhancement")

        override suspend fun enhanceStreaming(
            request: DictationEnhancer.Request,
            onPartial: suspend (String) -> Unit,
        ): DictationEnhancer.Result {
            emit = onPartial
            return try {
                result.await()
            } finally {
                cancelled = !result.isCompleted
            }
        }

        fun complete(text: String) {
            result.complete(DictationEnhancer.Result.Enhanced(text, "Provider (model)"))
        }
    }

    private fun Harness.startEnhancing(): StreamingEnhancer {
        val enhancer = StreamingEnhancer()
        this.enhancer = enhancer
        recordFor(1_000, locked = true)
        session.onStop()
        test.runCurrent()
        assertTrue(session.state.value is SessionState.Enhancing)
        return enhancer
    }

    @Test
    fun partialsReplaceOneComposition_andFinalTextCommitsOnceWithOneUndo() = sessionTest { h ->
        val enhancer = h.startEnhancing()
        assertEquals("prefix ", h.ic.getText())

        enhancer.emit("Good")
        assertEquals("prefix Good", h.ic.getText())
        assertFalse(h.committer.isUndoAvailable(h.focus))
        enhancer.emit("Good day.")
        assertEquals("prefix Good day.", h.ic.getText())
        enhancer.complete("Good day!")
        advanceUntilIdle()

        assertEquals("prefix Good day!", h.ic.getText())
        assertEquals(listOf(SessionEvent.Committed("Good day!")), h.events.filterIsInstance<SessionEvent.Committed>())
        assertTrue(h.committer.undoLastInsertion(h.focus))
        assertEquals("prefix ", h.ic.getText())
        assertFalse(h.committer.isUndoAvailable(h.focus))
    }

    @Test
    fun failureReplacesProvisionalTextWithRawTranscript() = sessionTest { h ->
        val enhancer = h.startEnhancing()
        enhancer.emit("unfinished rewrite")

        enhancer.result.complete(DictationEnhancer.Result.Kept("failed", isError = true))
        advanceUntilIdle()

        assertEquals("prefix hello", h.ic.getText())
        assertEquals(1, h.events.filterIsInstance<SessionEvent.Committed>().size)
    }

    @Test
    fun cancellationReplacesProvisionalTextWithRaw_andIgnoresLateTokens() = sessionTest { h ->
        val enhancer = h.startEnhancing()
        enhancer.emit("unfinished rewrite")

        h.session.onCancel()
        runCurrent()
        enhancer.emit("late rewrite")

        assertTrue(enhancer.cancelled)
        assertEquals("prefix hello", h.ic.getText())
        assertEquals(1, h.events.filterIsInstance<SessionEvent.Committed>().size)
    }

    @Test
    fun blankFinalOutputFallsBackToRaw_notProvisionalText() = sessionTest { h ->
        val enhancer = h.startEnhancing()
        enhancer.emit("unfinished rewrite")

        enhancer.complete(" ")
        advanceUntilIdle()

        assertEquals("prefix hello", h.ic.getText())
    }

    @Test
    fun retryResetReplacesEarlierAttempt_withoutDuplicatingOutput() = sessionTest { h ->
        val enhancer = h.startEnhancing()
        enhancer.emit("failed attempt")
        enhancer.emit("")
        assertEquals("prefix ", h.ic.getText())

        enhancer.emit("second attempt")
        enhancer.complete("second attempt.")
        advanceUntilIdle()

        assertEquals("prefix second attempt.", h.ic.getText())
    }

    @Test
    fun changedFocusRejectsTokens_andHoldsFinalResultInsteadOfInserting() = sessionTest { h ->
        val enhancer = h.startEnhancing()
        enhancer.emit("first")

        h.session.onFocusChanged(h.otherFocus)
        enhancer.emit("must not reach another field")
        assertEquals("prefix first", h.ic.getText())
        enhancer.complete("completed rewrite")
        advanceUntilIdle()

        assertEquals("completed rewrite", h.session.pendingCommit.value)
        assertTrue(h.events.none { it is SessionEvent.Committed })
    }

    @Test
    fun hiddenKeyboardRejectsTokens_andWaitsToCommitUntilShown() = sessionTest { h ->
        val enhancer = h.startEnhancing()
        enhancer.emit("first")

        h.session.onWindowHidden()
        enhancer.emit("hidden")
        enhancer.complete("completed rewrite")
        runCurrent()
        assertEquals("prefix first", h.ic.getText())
        assertTrue(h.session.state.value is SessionState.Committing)

        h.session.onWindowShown()
        advanceUntilIdle()
        assertEquals("prefix completed rewrite", h.ic.getText())
    }

    @Test
    fun privacyBlockRemovesProvisionalText_andRejectsLateTokens() = sessionTest { h ->
        val enhancer = h.startEnhancing()
        enhancer.emit("private rewrite")

        h.sendAllowed = false
        h.session.onExternalSendBlocked()
        runCurrent()
        enhancer.emit("late rewrite")

        assertTrue(enhancer.cancelled)
        assertEquals("prefix ", h.ic.getText())
        assertTrue(h.events.none { it is SessionEvent.Committed })
    }

    @Test
    fun clipboardModeNeverWritesStreamingTextIntoEditor() = sessionTest { h ->
        h.options = DictationOptions(outputMode = OutputMode.CLIPBOARD)
        val enhancer = h.startEnhancing()

        enhancer.emit("streamed")
        assertEquals("prefix ", h.ic.getText())
        enhancer.complete("completed rewrite")
        advanceUntilIdle()

        assertEquals("prefix ", h.ic.getText())
        assertEquals(
            listOf(SessionEvent.CopyToClipboard("completed rewrite")),
            h.events.filterIsInstance<SessionEvent.CopyToClipboard>(),
        )
    }

    @Test
    fun selectionReplacementStreamsInOriginalRange_withoutSelectingOldBoundsAgain() = sessionTest { h ->
        h.options = DictationOptions(outputMode = OutputMode.REPLACE_SELECTION)
        h.ic.setText("before old after")
        h.ic.setSelection(7, 10)
        val enhancer = h.startEnhancing()

        enhancer.emit("a much longer rewrite")
        assertEquals("before a much longer rewrite after", h.ic.getText())
        enhancer.emit("new")
        enhancer.complete("new text!")
        advanceUntilIdle()

        assertEquals("before new text! after", h.ic.getText())
    }

    @Test
    fun cancellingSelectionReplacementRestoresRaw_insideProvisionalRange() = sessionTest { h ->
        h.options = DictationOptions(outputMode = OutputMode.REPLACE_SELECTION)
        h.ic.setText("before old after")
        h.ic.setSelection(7, 10)
        val enhancer = h.startEnhancing()
        enhancer.emit("a much longer rewrite")

        h.session.onCancel()
        runCurrent()

        assertEquals("before hello after", h.ic.getText())
    }

    @Test
    fun userEditsStopStreaming_andHoldFinalTextWithoutOverwritingEdits() = sessionTest { h ->
        val enhancer = h.startEnhancing()
        enhancer.emit("first")
        h.ic.setText("user wrote this")

        enhancer.emit("must not overwrite")
        enhancer.complete("completed rewrite")
        advanceUntilIdle()

        assertEquals("user wrote this", h.ic.getText())
        assertEquals("completed rewrite", h.session.pendingCommit.value)
        assertTrue(h.events.none { it is SessionEvent.Committed })
    }

    @Test
    fun cursorMovementAfterLastTokenPreventsFinalInsert() = sessionTest { h ->
        val enhancer = h.startEnhancing()
        enhancer.emit("first")
        h.ic.setSelection(0, 0)

        enhancer.complete("completed rewrite")
        advanceUntilIdle()

        assertEquals("prefix first", h.ic.getText())
        assertEquals(0 to 0, h.ic.getSelection())
        assertEquals("completed rewrite", h.session.pendingCommit.value)
    }

    @Test
    fun cancellationAfterUserEditHoldsRaw_insteadOfOverwritingUserText() = sessionTest { h ->
        val enhancer = h.startEnhancing()
        enhancer.emit("first")
        h.ic.setText("user wrote this")

        h.session.onCancel()
        runCurrent()

        assertEquals("user wrote this", h.ic.getText())
        assertEquals("hello", h.session.pendingCommit.value)
    }

    @Test
    fun editorFinishingCompositionDoesNotDuplicateStreamedText() = sessionTest { h ->
        val enhancer = h.startEnhancing()
        enhancer.emit("first")
        h.ic.finishComposingText()
        enhancer.emit("second")
        assertEquals("prefix second", h.ic.getText())
        h.ic.finishComposingText()

        enhancer.complete("completed rewrite")
        advanceUntilIdle()

        assertEquals("prefix completed rewrite", h.ic.getText())
    }

    @Test
    fun editorWithoutTextSnapshotKeepsBufferedInsertion() = sessionTest { h ->
        h.ic.extractedTextAvailable = false
        val enhancer = h.startEnhancing()

        enhancer.emit("provisional")
        assertEquals("prefix ", h.ic.getText())
        enhancer.complete("completed rewrite")
        advanceUntilIdle()

        assertEquals("prefix completed rewrite", h.ic.getText())
    }

    @Test
    fun enablingEnhancementAfterRawPartials_doesNotOverwriteUserEdits() = sessionTest { h ->
        val enhancer = StreamingEnhancer(probeEnabled = false)
        val streaming = FakeStreaming(committed = "hello")
        h.enhancer = enhancer
        h.streaming = streaming
        h.recordFor(1_000, locked = true)
        streaming.events.emit(StreamEvent.Partial("hello"))
        runCurrent()
        h.ic.setText("user wrote this")
        h.session.onStop()
        runCurrent()

        enhancer.emit("rewrite")
        enhancer.complete("completed rewrite")
        advanceUntilIdle()

        assertEquals("user wrote this", h.ic.getText())
        assertEquals("completed rewrite", h.session.pendingCommit.value)
    }
}
