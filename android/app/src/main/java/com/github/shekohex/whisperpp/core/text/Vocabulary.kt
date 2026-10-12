package com.github.shekohex.whisperpp.core.text

/** Custom vocabulary (preferred spellings) as sent to transcription and enhancement models. */
object Vocabulary {
    /** Whisper-style prompts are cut at ~224 tokens; stay well inside that. */
    const val MAX_TERMS = 100
    const val MAX_CHARS = 1_000

    /** Trimmed, non-blank, case-insensitively unique terms in their original order. */
    fun normalize(terms: List<String>): List<String> = terms
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }

    /** Terms joined with ", ", capped at [MAX_TERMS] terms and [MAX_CHARS] characters (whole terms only). */
    fun join(terms: List<String>): String {
        val joined = StringBuilder()
        normalize(terms).take(MAX_TERMS).forEach { term ->
            val separator = if (joined.isEmpty()) "" else ", "
            if (joined.length + separator.length + term.length > MAX_CHARS) return joined.toString()
            joined.append(separator).append(term)
        }
        return joined.toString()
    }

    /** [prompt] with the vocabulary appended, for models that accept a prompt/vocabulary hint. */
    fun appendToPrompt(prompt: String, terms: List<String>): String {
        val vocabulary = join(terms)
        if (vocabulary.isEmpty()) return prompt
        val base = prompt.trim()
        return if (base.isEmpty()) vocabulary else "$base\n$vocabulary"
    }

    /** System-prompt block for enhancement, or null when there is no vocabulary. */
    fun enhancementBlock(terms: List<String>): String? {
        val vocabulary = join(terms)
        if (vocabulary.isEmpty()) return null
        return "Vocabulary (preferred spellings): $vocabulary"
    }
}
