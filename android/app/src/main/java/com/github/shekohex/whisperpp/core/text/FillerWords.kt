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

    /**
     * Hesitation sounds only. Words such as "يعني" ("I mean") and "اه"/"آه" ("yes"/"oh") carry meaning
     * and are never removed; a doubled "اه اه" is a hesitation.
     */
    val ARABIC: List<String> = listOf(
        "امم", "اممم", "إمم", "ممم", "ااه", "آآ", "اه اه",
    )

    val ALL: List<String> = ENGLISH + ARABIC
}
