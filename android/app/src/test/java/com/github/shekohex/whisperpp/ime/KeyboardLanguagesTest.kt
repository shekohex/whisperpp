package com.github.shekohex.whisperpp.ime

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardLanguagesTest {
    @Test
    fun codes_startWithAutoEnglishArabic_thenSystemLanguagesDeduplicated() {
        val codes = keyboardLanguageCodes(listOf("de", "EN", "ar", "fr", "de", " ", "und"))

        assertEquals(listOf("auto", "en", "ar", "de", "fr"), codes)
    }

    @Test
    fun codes_withoutSystemLanguages_offerDefaults() {
        assertEquals(listOf("auto", "en", "ar"), keyboardLanguageCodes(emptyList()))
    }

    @Test
    fun shortLabel_usesLocalizedAutoLabel_andUpperCasesCodes() {
        assertEquals("تلقائي", keyboardLanguageShortLabel("auto", "تلقائي"))
        assertEquals("AR", keyboardLanguageShortLabel("ar", "Auto"))
    }

    @Test
    fun displayName_isTheLanguagesOwnName() {
        assertEquals("Auto", keyboardLanguageDisplayName("auto", "Auto"))
        assertEquals("English", keyboardLanguageDisplayName("en", "Auto"))
        assertEquals("Deutsch", keyboardLanguageDisplayName("de", "Auto"))
    }
}
