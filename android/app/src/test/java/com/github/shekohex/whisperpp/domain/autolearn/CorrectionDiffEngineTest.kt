package com.github.shekohex.whisperpp.domain.autolearn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CorrectionDiffEngineTest {

    private fun diff(original: String, corrected: String): List<Pair<String, String>> =
        CorrectionDiffEngine.candidates(AutoLearnRevision(original, corrected, capturedAt = 0L))
            .map { it.originalText to it.correctedText }

    @Test
    fun personalName_includesContextOnBothSides() {
        assertEquals(
            listOf("I talked to john smith yesterday" to "I talked to John Smyth yesterday"),
            diff("I talked to john smith yesterday", "I talked to John Smyth yesterday"),
        )
    }

    @Test
    fun context_isLimitedToThreeWordsPerSide() {
        assertEquals(listOf("b c d e f g h" to "b c d x f g h"), diff("a b c d e f g h i", "a b c d x f g h i"))
    }

    @Test
    fun context_stopsAtStructuralBoundaries() {
        assertEquals(listOf("pracash said" to "Prakash said"), diff("Hi, pracash said", "Hi, Prakash said"))
        assertEquals(
            listOf("see you at mcdonalds" to "see you at McDonald's"),
            diff("see you at mcdonalds. Bye", "see you at McDonald's. Bye"),
        )
    }

    @Test
    fun context_doesNotCrossNeighbouringHunks() {
        assertEquals(
            listOf("aa bb cc" to "xx bb cc", "bb cc dd ee" to "bb cc yy ee"),
            diff("aa bb cc dd ee", "xx bb cc yy ee"),
        )
    }

    @Test
    fun identicalText_hasNoCandidates() {
        assertTrue(diff("same text", "same text").isEmpty())
    }

    @Test
    fun caseOnlyEdits_areSkipped() {
        assertTrue(diff("hello world", "Hello world").isEmpty())
    }

    @Test
    fun punctuationAndWhitespaceOnlyEdits_areSkipped() {
        assertTrue(diff("hello world", "hello world!").isEmpty())
        assertTrue(diff("hello, world", "hello; world").isEmpty())
        assertTrue(diff("hello  world", "hello world").isEmpty())
    }

    @Test
    fun pureInsertionsAndDeletions_areSkipped() {
        assertTrue(diff("a b c", "a b x c").isEmpty())
        assertTrue(diff("a b x c", "a b c").isEmpty())
    }

    @Test
    fun sourceSpanningComma_isSkipped() {
        assertTrue(diff("foo, bar", "fooz barz").isEmpty())
    }

    @Test
    fun repeatedEdit_isDeduplicated() {
        assertEquals(listOf("teh cat" to "the cat"), diff("teh cat teh dog", "the cat the dog").take(1))
        assertEquals(1, diff("teh cat teh dog", "the cat the dog").size)
    }

    @Test
    fun tooManySegments_skipsRevision() {
        val words = (0..AutoLearnLimits.MAX_DIFF_SEGMENTS).map { "w$it" }
        val corrected = words.toMutableList().apply { this[0] = "changed" }
        assertTrue(diff(words.joinToString(" "), corrected.joinToString(" ")).isEmpty())
    }

    @Test
    fun candidates_areCappedPerRevision() {
        val original = (0 until 50).map { if (it % 2 == 0) "alpha$it" else "keep$it" }
        val corrected = original.map { it.replace("alpha", "beta") }
        assertEquals(
            AutoLearnLimits.MAX_CANDIDATES_PER_REVISION,
            diff(original.joinToString(" "), corrected.joinToString(" ")).size,
        )
    }

    @Test
    fun arabic_nameCorrection() {
        assertEquals(
            listOf("ذهبت إلى محمد احمد اليوم" to "ذهبت إلى محمد أحمد اليوم"),
            diff("ذهبت إلى محمد احمد اليوم", "ذهبت إلى محمد أحمد اليوم"),
        )
    }

    @Test
    fun arabic_commaIsABoundary() {
        assertEquals(
            listOf("اسمي شادي خليفه" to "اسمي شادي خليفة"),
            diff("مرحبا، اسمي شادي خليفه", "مرحبا، اسمي شادي خليفة"),
        )
    }

    @Test
    fun arabic_diacriticEdit_isACandidate() {
        assertEquals(listOf("قال مُحمد" to "قال محمد"), diff("قال مُحمد", "قال محمد"))
    }

    @Test
    fun mixedDirection_arabicToLatin() {
        assertEquals(
            listOf("please call شادي tomorrow" to "please call Shady tomorrow"),
            diff("please call شادي tomorrow", "please call Shady tomorrow"),
        )
        assertEquals(
            listOf("اتصل ب jon غدا" to "اتصل ب John غدا"),
            diff("اتصل ب jon غدا", "اتصل ب John غدا"),
        )
    }

    @Test
    fun cjk_shortUnspacedEdit_isKept_longOneIsSkipped() {
        assertEquals(listOf("我喜欢苹果" to "我喜欢平果"), diff("我喜欢苹果", "我喜欢平果"))
        val long = "我".repeat(40)
        assertTrue(diff(long, long.dropLast(1) + "你").isEmpty())
    }

    @Test
    fun decomposedInput_isNormalizedBeforeComparing() {
        // "é" as e + combining acute vs precomposed: identical after NFC.
        assertTrue(diff("café time", "café time").isEmpty())
    }
}
