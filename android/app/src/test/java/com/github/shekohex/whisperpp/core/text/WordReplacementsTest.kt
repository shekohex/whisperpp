package com.github.shekohex.whisperpp.core.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordReplacementsTest {

    private fun apply(text: String, vararg rules: Replacement) = WordReplacements.apply(text, rules.toList())

    @Test
    fun longestOriginalIsAppliedFirst() {
        val result = apply(
            "I use open ai and open source",
            Replacement(listOf("open"), "Open-X"),
            Replacement(listOf("open ai"), "OpenAI"),
        )
        assertEquals("I use OpenAI and Open-X source", result)
    }

    @Test
    fun matchingIsCaseInsensitive() {
        assertEquals("ChatGPT rocks", apply("CHAT GPT rocks", Replacement(listOf("chat gpt"), "ChatGPT")))
    }

    @Test
    fun wordBoundaries_preventMatchesInsideWords() {
        val rule = Replacement(listOf("cat"), "dog")
        assertEquals("dog concatenate dog.", apply("cat concatenate cat.", rule))
        assertEquals("(dog)", apply("(cat)", rule))
    }

    @Test
    fun wordBoundaries_workForArabic() {
        val rule = Replacement(listOf("جوجل"), "Google")
        assertEquals("بحثت في Google", apply("بحثت في جوجل", rule))
        assertEquals("الجوجلي", apply("الجوجلي", rule))
    }

    @Test
    fun cjkAndThai_matchWithoutBoundaries() {
        assertEquals("我喜欢AI技术", apply("我喜欢人工智能技术", Replacement(listOf("人工智能"), "AI")))
        assertEquals("ありがとうthanksです", apply("ありがとう感謝です", Replacement(listOf("感謝"), "thanks")))
        assertEquals("พูดhelloครับ", apply("พูดสวัสดีครับ", Replacement(listOf("สวัสดี"), "hello")))
        assertFalse(WordReplacements.usesWordBoundaries("人工智能"))
        assertTrue(WordReplacements.usesWordBoundaries("cat"))
    }

    @Test
    fun latinOriginal_nextToCjk_stillMatches() {
        assertEquals("用AI做", apply("用ai做", Replacement(listOf("ai"), "AI")))
    }

    @Test
    fun lowercaseReplacement_takesTheMatchedCapitalisation() {
        val rule = Replacement(listOf("teh"), "the")
        assertEquals("The cat and the dog. THE END", apply("Teh cat and teh dog. TEH END", rule))
    }

    @Test
    fun mixedCaseReplacement_isUsedAsWritten() {
        assertEquals("iPhone and iPhone", apply("IPHONE and Iphone", Replacement(listOf("iphone"), "iPhone")))
    }

    @Test
    fun everyOriginalOfARule_isReplaced() {
        val rule = Replacement(listOf("gonna", "gunna"), "going to")
        assertEquals("I'm going to go, going to", apply("I'm gonna go, gunna", rule))
    }

    @Test
    fun regexCharactersInOriginals_areLiteral() {
        assertEquals("I love C++ a lot", apply("I love c++ a lot", Replacement(listOf("c++"), "C++")))
        assertEquals("a.b", apply("a.b", Replacement(listOf("a?b"), "x")))
    }

    @Test
    fun noRules_orBlankOriginals_leaveTextUnchanged() {
        assertEquals("hello", WordReplacements.apply("hello", emptyList()))
        assertEquals("hello", apply("hello", Replacement(listOf(" "), "x")))
        assertTrue(WordReplacements.compile(listOf(Replacement(listOf(""), "x"))).isEmpty)
    }
}
