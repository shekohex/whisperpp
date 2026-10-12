package com.github.shekohex.whisperpp.domain.analytics

/**
 * Word-count milestones for the Home hero card (§4.9), verbatim from VoiceInk
 * `DashboardStatsModels.swift`. After the last book, War and Peace repeats (×2, ×3, …).
 */
object Milestones {
    enum class Book(val title: String, val words: Long) {
        THE_METAMORPHOSIS("The Metamorphosis", 21_180),
        ANIMAL_FARM("Animal Farm", 29_966),
        THE_GREAT_GATSBY("The Great Gatsby", 47_094),
        HOMERS_ILIAD("Homer's Iliad", 114_715),
        HOMERS_ODYSSEY("Homer's Odyssey", 121_365),
        ILIAD_AND_ODYSSEY("Iliad and Odyssey", 236_080),
        WAR_AND_PEACE("War and Peace", 700_000),
    }

    /** [book] read [multiplier] times; only War and Peace repeats. */
    data class Milestone(val book: Book, val multiplier: Int = 1) {
        val words: Long get() = book.words * multiplier
    }

    data class Progress(
        val lastReached: Milestone?,
        val next: Milestone,
        val wordsToGo: Long,
        /** 0..1 progress from [lastReached] (or zero words) to [next]. */
        val fraction: Float,
    )

    private val FIXED: List<Milestone> = Book.entries
        .filter { it != Book.WAR_AND_PEACE }
        .map { Milestone(it) }

    /** First milestone strictly above [words]. */
    fun nextMilestone(words: Long): Milestone {
        FIXED.firstOrNull { it.words > words }?.let { return it }
        val repeat = Book.WAR_AND_PEACE.words
        val multiplier = (words.coerceAtLeast(0) / repeat + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return Milestone(Book.WAR_AND_PEACE, multiplier)
    }

    /** Highest milestone with at most [words]; null below the first. */
    fun lastReached(words: Long): Milestone? {
        val repeat = Book.WAR_AND_PEACE.words
        if (words >= repeat) {
            return Milestone(Book.WAR_AND_PEACE, (words / repeat).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        }
        return FIXED.lastOrNull { it.words <= words }
    }

    /** "N words to go until …". */
    fun wordsToGo(words: Long): Long = nextMilestone(words).words - words.coerceAtLeast(0)

    fun progress(words: Long): Progress {
        val safe = words.coerceAtLeast(0)
        val last = lastReached(safe)
        val next = nextMilestone(safe)
        val from = last?.words ?: 0L
        val span = (next.words - from).coerceAtLeast(1)
        return Progress(
            lastReached = last,
            next = next,
            wordsToGo = next.words - safe,
            fraction = ((safe - from).toFloat() / span).coerceIn(0f, 1f),
        )
    }
}
