package com.github.shekohex.whisperpp.core.text

/**
 * Deterministic clean-up of a raw transcript before it is committed (VoiceInk
 * `TranscriptionOutputFilter`): trims, drops quotes some providers wrap the text in, optionally
 * removes filler words and splits long text into paragraphs, and collapses repeated whitespace.
 */
object TranscriptFilter {
    data class Options(
        val removeFillers: Boolean = false,
        val formatParagraphs: Boolean = false,
        /** A paragraph ends at the first sentence boundary after this many characters. */
        val paragraphMinChars: Int = DEFAULT_PARAGRAPH_MIN_CHARS,
        val fillerWords: List<String> = FillerWords.ALL,
    )

    const val DEFAULT_PARAGRAPH_MIN_CHARS = 240

    private val QUOTE_PAIRS = mapOf(
        '"' to '"',
        '\'' to '\'',
        '“' to '”',
        '„' to '“',
        '«' to '»',
        '「' to '」',
        '『' to '』',
    )

    private const val WORD_CHAR = "[\\p{L}\\p{M}\\p{N}]"
    private val HORIZONTAL_SPACE_RUN = Regex("[\\t\\x0B\\f\\r \\u00A0\\u2000-\\u200A\\u202F\\u205F\\u3000]{2,}")
    private val SPACE_AROUND_NEWLINE = Regex("[ \\t]*\\n[ \\t]*")
    private val EXCESS_NEWLINES = Regex("\\n{3,}")
    private val SPACE_BEFORE_PUNCTUATION = Regex("[ \\t]+([.,!?;:،؛؟])")
    private val LEADING_PUNCTUATION = Regex("^[,،;؛]+\\s*")
    private val SENTENCE_BOUNDARY = Regex("(?<=[.!?؟。！？])\\s+")

    fun apply(text: String, options: Options = Options()): String {
        var result = stripWrappingQuotes(text.trim())
        if (options.removeFillers) result = removeFillers(result, options.fillerWords)
        result = collapseWhitespace(result)
        if (options.formatParagraphs) result = formatParagraphs(result, options.paragraphMinChars)
        return result.trim()
    }

    internal fun stripWrappingQuotes(text: String): String {
        if (text.length < 2) return text
        val close = QUOTE_PAIRS[text.first()] ?: return text
        if (text.last() != close) return text
        val inner = text.substring(1, text.length - 1)
        // A quote inside means the outer marks belong to the speech ("a" and "b"), not the provider.
        if (inner.contains(text.first()) || inner.contains(close)) return text
        return inner.trim()
    }

    internal fun removeFillers(text: String, fillers: List<String>): String {
        if (fillers.isEmpty() || text.isEmpty()) return text
        val startsUpper = text.first().isUpperCase()
        var result = text
        fillers.map { it.trim() }.filter { it.isNotEmpty() }.sortedByDescending { it.length }.forEach { filler ->
            result = fillerRegex(filler).replace(result, " ")
        }
        // Only gaps left by a removed filler are tightened ("so um." → "so ."); other spacing is the speaker's.
        result = SPACE_BEFORE_PUNCTUATION.replace(result, "$1")
        result = LEADING_PUNCTUATION.replace(result.trimStart(), "")
        if (startsUpper && result.isNotEmpty() && result.first().isLowerCase()) {
            result = result.first().uppercaseChar() + result.substring(1)
        }
        return result
    }

    private fun fillerRegex(filler: String): Regex {
        val word = filler.trimEnd(',', '،')
        val escaped = Regex.escape(word)
        // Swallow the comma on either side so "I, um, think" becomes "I think", not "I,, think".
        val pattern = "(?:[,،][ \\t]*)?(?<!$WORD_CHAR)$escaped(?!$WORD_CHAR)" +
            if (word.length != filler.length) "[,،]" else "[,،]?"
        return Regex(pattern, setOf(RegexOption.IGNORE_CASE))
    }

    internal fun collapseWhitespace(text: String): String {
        var result = HORIZONTAL_SPACE_RUN.replace(text, " ")
        result = SPACE_AROUND_NEWLINE.replace(result, "\n")
        result = EXCESS_NEWLINES.replace(result, "\n\n")
        return result
    }

    internal fun formatParagraphs(text: String, minChars: Int): String {
        if (text.contains('\n') || text.length <= minChars) return text
        val sentences = text.split(SENTENCE_BOUNDARY).filter { it.isNotBlank() }
        if (sentences.size < 2) return text
        val paragraphs = mutableListOf<String>()
        val current = StringBuilder()
        sentences.forEach { sentence ->
            if (current.isNotEmpty()) current.append(' ')
            current.append(sentence)
            if (current.length >= minChars) {
                paragraphs += current.toString()
                current.clear()
            }
        }
        if (current.isNotEmpty()) paragraphs += current.toString()
        return paragraphs.joinToString("\n\n")
    }
}
