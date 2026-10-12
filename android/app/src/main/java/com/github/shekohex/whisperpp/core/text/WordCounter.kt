package com.github.shekohex.whisperpp.core.text

import java.text.BreakIterator

/** Locale-aware word count: word-boundary segments that contain a letter or digit. */
object WordCounter {
    fun count(text: String): Int {
        if (text.isBlank()) return 0
        val boundary = BreakIterator.getWordInstance()
        boundary.setText(text)
        var count = 0
        var start = boundary.first()
        var end = boundary.next()
        while (end != BreakIterator.DONE) {
            if ((start until end).any { text[it].isLetterOrDigit() }) count += 1
            start = end
            end = boundary.next()
        }
        return count
    }
}
