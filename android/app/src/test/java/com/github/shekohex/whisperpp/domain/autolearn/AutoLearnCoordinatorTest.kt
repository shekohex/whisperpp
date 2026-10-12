package com.github.shekohex.whisperpp.domain.autolearn

import com.github.shekohex.whisperpp.core.network.NetworkError
import com.github.shekohex.whisperpp.data.autolearn.AutoLearnPendingQueue
import com.github.shekohex.whisperpp.data.autolearn.AutoLearnProposalStore
import com.github.shekohex.whisperpp.data.autolearn.AutoLearnSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoLearnCoordinatorTest {

    private val day = 24L * 60 * 60 * 1000
    private var now = 10 * day
    private val settings = MutableStateFlow(AutoLearnSettings(enabled = true))
    private val queue = AutoLearnPendingQueue(InMemoryPreferencesStore(), clock = { now })
    private val dictionary = FakeDictionaryEditor()
    private val proposals = AutoLearnProposalStore(InMemoryPreferencesStore(), dictionary, clock = { now })
    private val provider = FakeEnhancementProvider()

    private fun coordinator(provider: FakeEnhancementProvider = this.provider) = AutoLearnCoordinator(
        settings = settings,
        markReviewed = { at -> settings.value = settings.value.copy(lastReviewAt = at) },
        queue = queue,
        reviewer = AutoLearnAiReviewer(prompt = { "P" }, resolveModel = { Result.success(AutoLearnReviewModel(provider)) }),
        proposalStore = proposals,
        scope = CoroutineScope(Dispatchers.Unconfined),
        clock = { now },
    )

    private val revision = AutoLearnRevision("send it to vois ink now", "send it to VoiceInk now", capturedAt = 0L)
    private val accept = """[{"candidateID":0,"learningAction":"addReplacementAndVocabulary","incorrectTextToReplace":"vois ink","correctedVocabularyTerm":"VoiceInk"}]"""

    @Test
    fun isDue_honoursScheduleAndManualTrigger() {
        val auto = AutoLearnReviewTrigger.AUTOMATIC
        val immediately = AutoLearnSettings(reviewSchedule = AutoLearnReviewSchedule.IMMEDIATELY)
        val daily = AutoLearnSettings(reviewSchedule = AutoLearnReviewSchedule.DAILY, lastReviewAt = now)
        val manually = AutoLearnSettings(reviewSchedule = AutoLearnReviewSchedule.MANUALLY)

        assertTrue(AutoLearnCoordinator.isDue(immediately, auto, now))
        assertFalse(AutoLearnCoordinator.isDue(daily, auto, now + day - 1))
        assertTrue(AutoLearnCoordinator.isDue(daily, auto, now + day))
        assertTrue(AutoLearnCoordinator.isDue(daily, auto, now - 1))
        assertFalse(AutoLearnCoordinator.isDue(manually, auto, now))
        assertTrue(AutoLearnCoordinator.isDue(manually, AutoLearnReviewTrigger.MANUAL, now))
    }

    @Test
    fun disabled_neitherQueuesNorReviews() = runBlocking {
        settings.value = AutoLearnSettings(enabled = false)
        val coordinator = coordinator()
        assertEquals(0, coordinator.submitRevision(revision))
        assertEquals(AutoLearnReviewOutcome.Disabled, coordinator.reviewPendingIfDue(AutoLearnReviewTrigger.MANUAL))
    }

    @Test
    fun immediately_appliesAndEmitsLearned() = runBlocking {
        val provider = FakeEnhancementProvider(listOf(Result.success(accept)))
        val coordinator = coordinator(provider)
        val events = mutableListOf<AutoLearnEvent>()
        val collector = launch(Dispatchers.Unconfined) { coordinator.events.collect { events += it } }
        assertEquals(1, coordinator.submitRevision(revision))

        val outcome = coordinator.reviewPendingIfDue(AutoLearnReviewTrigger.AUTOMATIC) as AutoLearnReviewOutcome.Completed

        assertEquals(1, outcome.reviewed)
        assertEquals(listOf("VoiceInk"), dictionary.dictionary.vocabulary)
        assertEquals(0, queue.outstandingCount.first())
        assertEquals(now, settings.value.lastReviewAt)
        assertEquals(listOf(AutoLearnEvent.Learned(outcome.learned!!)), events)
        collector.cancel()
        assertTrue(coordinator.undo(outcome.learned!!))
        assertTrue(dictionary.dictionary.vocabulary.isEmpty())
    }

    @Test
    fun daily_storesProposals_onlyWhenDue() = runBlocking {
        settings.value = AutoLearnSettings(enabled = true, reviewSchedule = AutoLearnReviewSchedule.DAILY, lastReviewAt = now)
        val provider = FakeEnhancementProvider(listOf(Result.success(accept)))
        val coordinator = coordinator(provider)
        coordinator.submitRevision(revision)

        assertEquals(AutoLearnReviewOutcome.NotDue, coordinator.reviewPendingIfDue(AutoLearnReviewTrigger.AUTOMATIC))
        assertTrue(provider.requests.isEmpty())

        now += day
        val outcome = coordinator.reviewPendingIfDue(AutoLearnReviewTrigger.AUTOMATIC) as AutoLearnReviewOutcome.Completed
        assertEquals(1, outcome.proposalsAdded)
        assertEquals(null, outcome.learned)
        assertTrue(dictionary.dictionary.vocabulary.isEmpty())
        assertEquals(1, coordinator.proposalCount.first())

        val summary = coordinator.applyProposals(coordinator.proposals.first().map { it.id })
        assertEquals(1, summary.vocabularyCount)
        assertEquals(0, coordinator.proposalCount.first())
    }

    @Test
    fun manually_reviewsOnlyOnManualTrigger() = runBlocking {
        settings.value = AutoLearnSettings(enabled = true, reviewSchedule = AutoLearnReviewSchedule.MANUALLY)
        val coordinator = coordinator(FakeEnhancementProvider(listOf(Result.success(accept))))
        coordinator.submitRevision(revision)

        assertEquals(AutoLearnReviewOutcome.NotDue, coordinator.reviewPendingIfDue(AutoLearnReviewTrigger.AUTOMATIC))
        assertTrue(coordinator.reviewPendingIfDue(AutoLearnReviewTrigger.MANUAL) is AutoLearnReviewOutcome.Completed)
        assertEquals(1, coordinator.proposalCount.first())
    }

    @Test
    fun malformedResponse_releasesBatchBackToQueue() = runBlocking {
        val coordinator = coordinator(FakeEnhancementProvider(listOf(Result.success("I think these are fine!"))))
        coordinator.submitRevision(revision)

        val outcome = coordinator.reviewPendingIfDue(AutoLearnReviewTrigger.AUTOMATIC)

        assertTrue((outcome as AutoLearnReviewOutcome.Failed).error is NetworkError.InvalidResponse)
        assertEquals(1, queue.pendingCount.first())
        assertEquals(0L, settings.value.lastReviewAt)
    }

    @Test
    fun nothingPending() = runBlocking {
        assertEquals(AutoLearnReviewOutcome.NothingPending, coordinator().reviewPendingIfDue(AutoLearnReviewTrigger.MANUAL))
    }
}
