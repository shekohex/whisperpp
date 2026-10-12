package com.github.shekohex.whisperpp.core.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VocabularyTest {

    @Test
    fun appendToPrompt_joinsTermsAfterTheExistingPrompt() {
        assertEquals("Be precise.\nKotlin, Jetpack Compose", Vocabulary.appendToPrompt("Be precise.", listOf(" Kotlin ", "Jetpack Compose", "kotlin")))
        assertEquals("Kotlin", Vocabulary.appendToPrompt("", listOf("Kotlin")))
        assertEquals("Be precise.", Vocabulary.appendToPrompt("Be precise.", listOf(" ")))
    }

    @Test
    fun join_capsTermsAndCharacters() {
        val many = (1..150).map { "t$it" }
        assertEquals(Vocabulary.MAX_TERMS, Vocabulary.join(many).split(", ").size)

        val long = (1..90).map { "term-number-$it" }
        val joined = Vocabulary.join(long)
        assertTrue(joined.length <= Vocabulary.MAX_CHARS)
        assertTrue(joined.split(", ").all { it.startsWith("term-number-") })
    }

    @Test
    fun enhancementBlock_isNullWithoutVocabulary() {
        assertNull(Vocabulary.enhancementBlock(emptyList()))
        assertEquals("Vocabulary (preferred spellings): Whisper++, Ada", Vocabulary.enhancementBlock(listOf("Whisper++", "Ada")))
    }
}
