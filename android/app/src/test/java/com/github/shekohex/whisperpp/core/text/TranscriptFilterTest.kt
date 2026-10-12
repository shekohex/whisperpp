package com.github.shekohex.whisperpp.core.text

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptFilterTest {

    private val fillers = TranscriptFilter.Options(removeFillers = true)

    @Test
    fun trimsAndCollapsesRepeatedWhitespace() {
        assertEquals("hello world", TranscriptFilter.apply("  hello \t  world  "))
        assertEquals("one\n\ntwo", TranscriptFilter.apply("one  \n\n\n\n  two"))
    }

    @Test
    fun keepsSpeakerSpacingBeforePunctuation_whenFillersAreOff() {
        assertEquals("Bonjour !", TranscriptFilter.apply("Bonjour !"))
    }

    @Test
    fun stripsQuotesWrappingTheWholeTranscript() {
        assertEquals("Hello world", TranscriptFilter.apply("\"Hello world\""))
        assertEquals("Hello world", TranscriptFilter.apply("“Hello world”"))
        assertEquals("مرحبا", TranscriptFilter.apply("«مرحبا»"))
    }

    @Test
    fun keepsQuotesThatBelongToTheSpeech() {
        assertEquals("\"a\" and \"b\"", TranscriptFilter.apply("\"a\" and \"b\""))
        assertEquals("\"quoted\" start", TranscriptFilter.apply("\"quoted\" start"))
    }

    @Test
    fun fillers_areKeptByDefault() {
        assertEquals("um hello", TranscriptFilter.apply("um hello"))
    }

    @Test
    fun fillers_englishAreRemovedWithTheirCommas() {
        assertEquals(
            "So I think we should go.",
            TranscriptFilter.apply("Um, so I, uh, think we should, like, go.", fillers),
        )
        assertEquals("Hello. Next", TranscriptFilter.apply("Hello, um. Next", fillers))
    }

    @Test
    fun fillers_arabicAreRemoved() {
        assertEquals("انا رايح", TranscriptFilter.apply("يعني انا امم رايح", fillers))
    }

    @Test
    fun fillers_neverMatchInsideWordsOrWithoutThePause() {
        assertEquals("Umbrella hum", TranscriptFilter.apply("Umbrella hum", fillers))
        assertEquals("I like apples", TranscriptFilter.apply("I like apples", fillers))
    }

    @Test
    fun paragraphs_areOffByDefault() {
        val text = "First sentence here. Second sentence here. Third one."
        assertEquals(text, TranscriptFilter.apply(text, TranscriptFilter.Options(paragraphMinChars = 20)))
    }

    @Test
    fun paragraphs_breakAtSentenceBoundariesAfterMinChars() {
        val options = TranscriptFilter.Options(formatParagraphs = true, paragraphMinChars = 20)
        assertEquals(
            "First sentence here.\n\nSecond sentence here.\n\nThird one.",
            TranscriptFilter.apply("First sentence here. Second sentence here. Third one.", options),
        )
    }

    @Test
    fun paragraphs_leaveShortOrAlreadyFormattedTextAlone() {
        val options = TranscriptFilter.Options(formatParagraphs = true, paragraphMinChars = 20)
        assertEquals("Short. Text.", TranscriptFilter.apply("Short. Text.", options))
        assertEquals("Line one is long enough.\nLine two.", TranscriptFilter.apply("Line one is long enough.\nLine two.", options))
    }
}
