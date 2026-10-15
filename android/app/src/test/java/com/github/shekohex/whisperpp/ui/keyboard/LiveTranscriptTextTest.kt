package com.github.shekohex.whisperpp.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveTranscriptTextTest {
    @Test
    fun newWords_leaveExistingWordsUnchanged() {
        assertEquals(5, liveTranscriptChangeStart("hello", "hello world"))
        assertEquals(6, liveTranscriptChangeStart("hello ", "hello world"))
    }

    @Test
    fun revisedWords_animateFromWholeWordBoundary() {
        assertEquals(4, liveTranscriptChangeStart("the cap sat", "the cat sat"))
        assertEquals(0, liveTranscriptChangeStart("hel", "hello"))
    }

    @Test
    fun retractionsAndEmptyText_haveValidSpanBounds() {
        assertEquals(0, liveTranscriptChangeStart("hello there", "hello"))
        assertEquals(0, liveTranscriptChangeStart("hello", ""))
        assertEquals(0, liveTranscriptChangeStart("", "hello"))
    }

    @Test
    fun arabicAndEmoji_keepWholeWordBoundaries() {
        assertEquals(4, liveTranscriptChangeStart("هذا نص", "هذا نصح"))
        assertEquals(3, liveTranscriptChangeStart("hi 😀", "hi 😁"))
    }
}
