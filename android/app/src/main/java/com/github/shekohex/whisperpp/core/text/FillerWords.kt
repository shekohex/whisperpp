package com.github.shekohex.whisperpp.core.text

/**
 * Hesitation sounds removed by [TranscriptFilter] when filler removal is on. An entry ending in a
 * comma ("like,") only matches when the speaker paused there, so ordinary uses of the word stay.
 */
object FillerWords {
    val ENGLISH: List<String> = listOf(
        "um", "umm", "uhm", "uh", "uhh", "uhhh", "hmm", "hm", "mmm", "mm", "mh", "ehh", "erm", "er",
        "like,", "you know,",
    )

    val ARABIC: List<String> = listOf(
        "يعني", "اه", "آه", "امم", "اممم", "إمم", "ممم",
    )

    val ALL: List<String> = ENGLISH + ARABIC
}
