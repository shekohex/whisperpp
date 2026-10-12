package com.github.shekohex.whisperpp.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandClipboardPreviewTest {
    @Test
    fun shortText_isShownWhole() {
        val preview = CommandClipboardPreview.of("hello")

        assertEquals("hello", preview.snippet)
        assertEquals(5, preview.charCount)
        assertFalse(preview.isLarge)
    }

    @Test
    fun textAtSnippetLimit_isNotTruncated() {
        val text = "a".repeat(CommandClipboardPreview.SNIPPET_LIMIT)

        assertEquals(text, CommandClipboardPreview.of(text).snippet)
    }

    @Test
    fun longerText_isTruncatedWithEllipsis() {
        val text = "a".repeat(CommandClipboardPreview.SNIPPET_LIMIT + 1)

        val preview = CommandClipboardPreview.of(text)

        assertEquals("a".repeat(CommandClipboardPreview.SNIPPET_LIMIT) + "\u2026", preview.snippet)
        assertEquals(CommandClipboardPreview.SNIPPET_LIMIT + 1, preview.charCount)
    }

    @Test
    fun largeThreshold_isInclusive() {
        val below = CommandClipboardPreview.of("a".repeat(CommandClipboardPreview.LARGE_THRESHOLD - 1))
        val at = CommandClipboardPreview.of("a".repeat(CommandClipboardPreview.LARGE_THRESHOLD))

        assertFalse(below.isLarge)
        assertTrue(at.isLarge)
    }
}
