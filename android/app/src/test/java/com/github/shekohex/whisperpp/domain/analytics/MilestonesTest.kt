package com.github.shekohex.whisperpp.domain.analytics

import com.github.shekohex.whisperpp.domain.analytics.Milestones.Book
import com.github.shekohex.whisperpp.domain.analytics.Milestones.Milestone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MilestonesTest {
    @Test
    fun books_matchVoiceInkVerbatim() {
        assertEquals(
            listOf(
                "The Metamorphosis" to 21_180L,
                "Animal Farm" to 29_966L,
                "The Great Gatsby" to 47_094L,
                "Homer's Iliad" to 114_715L,
                "Homer's Odyssey" to 121_365L,
                "Iliad and Odyssey" to 236_080L,
                "War and Peace" to 700_000L,
            ),
            Book.entries.map { it.title to it.words },
        )
    }

    @Test
    fun belowFirstMilestone_nothingReached_andWordsToGoCountsDown() {
        assertNull(Milestones.lastReached(0))
        assertEquals(Milestone(Book.THE_METAMORPHOSIS), Milestones.nextMilestone(0))
        assertEquals(12_300L, Milestones.wordsToGo(8_880))
    }

    @Test
    fun exactMilestone_isReached_andNextIsTheFollowingBook() {
        assertEquals(Milestone(Book.ANIMAL_FARM), Milestones.lastReached(29_966))
        assertEquals(Milestone(Book.THE_GREAT_GATSBY), Milestones.nextMilestone(29_966))
        assertEquals(Milestone(Book.HOMERS_ODYSSEY), Milestones.lastReached(165_800))
    }

    @Test
    fun afterIliadAndOdyssey_warAndPeaceRepeats() {
        assertEquals(Milestone(Book.ILIAD_AND_ODYSSEY), Milestones.lastReached(699_999))
        assertEquals(Milestone(Book.WAR_AND_PEACE, 1), Milestones.nextMilestone(236_080))
        assertEquals(Milestone(Book.WAR_AND_PEACE, 1), Milestones.lastReached(700_000))
        assertEquals(Milestone(Book.WAR_AND_PEACE, 2), Milestones.nextMilestone(700_000))
        assertEquals(Milestone(Book.WAR_AND_PEACE, 3), Milestones.lastReached(2_150_000))
        assertEquals(2_800_000L, Milestones.nextMilestone(2_150_000).words)
        assertEquals(650_000L, Milestones.wordsToGo(2_150_000))
    }

    @Test
    fun progress_isFractionBetweenLastAndNext() {
        val progress = Milestones.progress(25_573)
        assertEquals(Milestone(Book.THE_METAMORPHOSIS), progress.lastReached)
        assertEquals(Milestone(Book.ANIMAL_FARM), progress.next)
        assertEquals(4_393L, progress.wordsToGo)
        assertEquals(0.5f, progress.fraction, 0.001f)
    }
}
