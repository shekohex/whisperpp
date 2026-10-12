package com.github.shekohex.whisperpp.core.text

/**
 * Applies [Replacement] rules (port of VoiceInk `WordReplacementService`): longest original first,
 * case-insensitive, word-boundary aware except for scripts without spaces (CJK, Thai), where the
 * original matches as a plain substring. An all-lowercase replacement takes the capitalisation of
 * the matched text ("Gpt" → "Chat", "GPT" → "CHAT").
 */
class WordReplacements private constructor(private val rules: List<CompiledRule>) {

    private class CompiledRule(val regex: Regex, val replacement: String)

    fun apply(text: String): String {
        if (text.isEmpty()) return text
        return rules.fold(text) { current, rule ->
            rule.regex.replace(current) { match -> adaptCase(match.value, rule.replacement) }
        }
    }

    val isEmpty: Boolean
        get() = rules.isEmpty()

    companion object {
        val EMPTY = WordReplacements(emptyList())

        // Letters, marks and digits of spaced scripts; Han/Kana/Hangul/Thai never form a boundary.
        private const val WORD_CHAR =
            "[\\p{L}\\p{M}\\p{N}&&[^\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\p{IsHangul}\\p{IsThai}]]"

        private val NON_SPACED_RANGES = listOf(
            0x3040..0x309F, // Hiragana
            0x30A0..0x30FF, // Katakana
            0x4E00..0x9FFF, // CJK Unified Ideographs
            0xAC00..0xD7AF, // Hangul Syllables
            0x0E00..0x0E7F, // Thai
        )

        fun compile(rules: List<Replacement>): WordReplacements {
            val variants = rules.flatMap { rule ->
                rule.originals.map { it.trim() }.filter { it.isNotEmpty() }.map { it to rule.replacement }
            }
            if (variants.isEmpty()) return EMPTY
            // Stable sort: equal lengths keep rule order, so the first rule for a trigger wins.
            val ordered = variants.sortedWith(compareByDescending<Pair<String, String>> { it.first.length })
            return WordReplacements(ordered.map { (original, replacement) -> compileRule(original, replacement) })
        }

        fun apply(text: String, rules: List<Replacement>): String = compile(rules).apply(text)

        private fun compileRule(original: String, replacement: String): CompiledRule {
            val escaped = Regex.escape(original)
            val pattern = if (usesWordBoundaries(original)) "(?<!$WORD_CHAR)$escaped(?!$WORD_CHAR)" else escaped
            return CompiledRule(Regex(pattern, RegexOption.IGNORE_CASE), replacement)
        }

        internal fun usesWordBoundaries(text: String): Boolean {
            var index = 0
            while (index < text.length) {
                val codePoint = text.codePointAt(index)
                if (NON_SPACED_RANGES.any { codePoint in it }) return false
                index += Character.charCount(codePoint)
            }
            return true
        }

        internal fun adaptCase(matched: String, replacement: String): String {
            if (replacement.isEmpty() || replacement != replacement.lowercase()) return replacement
            val letters = matched.filter { it.isLetter() }
            if (letters.isEmpty()) return replacement
            return when {
                letters.length > 1 && letters.all { it.isUpperCase() } -> replacement.uppercase()
                letters.first().isUpperCase() -> replacement.replaceFirstChar { it.uppercaseChar() }
                else -> replacement
            }
        }
    }
}
