package com.github.shekohex.whisperpp.domain.autolearn

import java.text.Normalizer

/**
 * Finds learnable edits between dictated and user-corrected text (port of VoiceInk
 * `CorrectionDiffEngine`). Text is split into word and structural-boundary segments, aligned with
 * an LCS, and every run of changed segments becomes one candidate snippet that carries up to three
 * unchanged words of context on each side (never across a boundary such as `,` or a sentence end).
 */
object CorrectionDiffEngine {

    private class Segment(val text: String, val start: Int, val end: Int, val isBoundary: Boolean)

    private class Hunk(val original: IntRange, val corrected: IntRange)

    private val TRAILING_SENTENCE_PUNCTUATION = setOf('.', ',', '!', '?', ';', ':', '…', '،', '؛', '؟', '。', '，', '！', '？')
    private val LEADING_WRAPPERS = setOf('"', '“', '‘', '(', '[', '{', '«')
    private val STRUCTURAL_SEPARATORS = setOf(
        ',', '!', '?', ';', ':', '…', '(', ')', '[', ']', '{', '}', '"', '“', '”',
        '،', '؛', '؟', '«', '»', '。', '，', '！', '？',
    )
    private val APOSTROPHES = setOf('\'', '’', '‘')

    fun candidates(revision: AutoLearnRevision): List<DetectedCorrectionCandidate> =
        candidates(revision.original, revision.corrected)

    fun candidates(originalText: String, correctedText: String): List<DetectedCorrectionCandidate> {
        val original = Normalizer.normalize(originalText, Normalizer.Form.NFC)
        val corrected = Normalizer.normalize(correctedText, Normalizer.Form.NFC)
        if (original == corrected) return emptyList()

        val originalSegments = segments(original)
        val correctedSegments = segments(corrected)
        if (originalSegments.size > AutoLearnLimits.MAX_DIFF_SEGMENTS ||
            correctedSegments.size > AutoLearnLimits.MAX_DIFF_SEGMENTS
        ) {
            return emptyList()
        }

        val hunks = hunks(originalSegments, correctedSegments)
        val seen = HashSet<String>()
        val results = ArrayList<DetectedCorrectionCandidate>()
        for ((index, hunk) in hunks.withIndex()) {
            if (results.size >= AutoLearnLimits.MAX_CANDIDATES_PER_REVISION) break
            if (hunk.original.isEmpty() || hunk.corrected.isEmpty()) continue
            if (hunk.original.count() > AutoLearnLimits.MAX_CANDIDATE_SEGMENTS ||
                hunk.corrected.count() > AutoLearnLimits.MAX_CANDIDATE_SEGMENTS
            ) {
                continue
            }
            val source = fragment(original, originalSegments, hunk.original) ?: continue
            val destination = fragment(corrected, correctedSegments, hunk.corrected) ?: continue
            val (cleanSource, cleanDestination) = cleanedPair(source, destination) ?: continue
            if (!isLearnablePair(cleanSource, cleanDestination)) continue

            val previous = hunks.getOrNull(index - 1)
            val next = hunks.getOrNull(index + 1)
            val reviewOriginal = reviewRange(
                hunk.original,
                lowerLimit = previous?.original?.let { it.last + 1 } ?: 0,
                upperLimit = next?.original?.first ?: originalSegments.size,
                segments = originalSegments,
            )
            val reviewCorrected = reviewRange(
                hunk.corrected,
                lowerLimit = previous?.corrected?.let { it.last + 1 } ?: 0,
                upperLimit = next?.corrected?.first ?: correctedSegments.size,
                segments = correctedSegments,
            )
            val originalSnippet = fragment(original, originalSegments, reviewOriginal) ?: continue
            val correctedSnippet = fragment(corrected, correctedSegments, reviewCorrected) ?: continue

            if (!seen.add(cleanSource + "\u0000" + cleanDestination)) continue
            results += DetectedCorrectionCandidate(originalText = originalSnippet, correctedText = correctedSnippet)
        }
        return results
    }

    private fun isLearnablePair(source: String, destination: String): Boolean {
        if (source == destination) return false
        // Case-only edits are never learned; neither are edits without any letter or digit.
        if (source.equals(destination, ignoreCase = true)) return false
        if (!source.any { it.isLetterOrDigit() } || !destination.any { it.isLetterOrDigit() }) return false
        if (source.contains(',') || source.contains('،')) return false
        if (characterCount(source) > AutoLearnLimits.MAX_CANDIDATE_CHARACTERS ||
            characterCount(destination) > AutoLearnLimits.MAX_CANDIDATE_CHARACTERS
        ) {
            return false
        }
        if (!isWithinUnspacedPrivacyLimit(source, destination)) return false
        return !containsControlCharacter(source) && !containsControlCharacter(destination)
    }

    private fun segments(text: String): List<Segment> {
        val results = ArrayList<Segment>()
        var segmentStart = -1
        var index = 0
        while (index < text.length) {
            val char = text[index]
            when {
                char.isWhitespace() -> {
                    if (segmentStart >= 0) {
                        results += Segment(text.substring(segmentStart, index), segmentStart, index, false)
                        segmentStart = -1
                    }
                }
                isStructuralSeparator(text, index) -> {
                    if (segmentStart >= 0) {
                        results += Segment(text.substring(segmentStart, index), segmentStart, index, false)
                        segmentStart = -1
                    }
                    results += Segment(text.substring(index, index + 1), index, index + 1, true)
                }
                segmentStart < 0 -> segmentStart = index
            }
            index++
        }
        if (segmentStart >= 0) {
            results += Segment(text.substring(segmentStart), segmentStart, text.length, false)
        }
        return results
    }

    private fun isStructuralSeparator(text: String, index: Int): Boolean {
        val char = text[index]
        if (char in STRUCTURAL_SEPARATORS) return true
        if (char == '.' || char == '。') {
            val next = index + 1
            return next == text.length || text[next].isWhitespace()
        }
        if (char in APOSTROPHES) {
            if (index == 0 || index + 1 >= text.length) return true
            return text[index - 1].isWhitespace() || text[index + 1].isWhitespace()
        }
        return false
    }

    /** Maximal runs of non-matching segments, from an LCS alignment that prefers deletions first. */
    private fun hunks(original: List<Segment>, corrected: List<Segment>): List<Hunk> {
        val n = original.size
        val m = corrected.size
        // suffix[i][j] = LCS length of original[i..] and corrected[j..].
        val suffix = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                suffix[i][j] = if (original[i].text == corrected[j].text) {
                    suffix[i + 1][j + 1] + 1
                } else {
                    maxOf(suffix[i + 1][j], suffix[i][j + 1])
                }
            }
        }

        val hunks = ArrayList<Hunk>()
        var i = 0
        var j = 0
        var startI = -1
        var startJ = -1
        fun finish() {
            if (startI < 0) return
            hunks += Hunk(startI until i, startJ until j)
            startI = -1
            startJ = -1
        }
        while (i < n || j < m) {
            val matches = i < n && j < m && original[i].text == corrected[j].text &&
                suffix[i][j] == suffix[i + 1][j + 1] + 1
            if (matches) {
                finish()
                i++
                j++
                continue
            }
            if (startI < 0) {
                startI = i
                startJ = j
            }
            if (j >= m || (i < n && suffix[i + 1][j] >= suffix[i][j + 1])) i++ else j++
        }
        finish()
        return hunks
    }

    private fun fragment(text: String, segments: List<Segment>, range: IntRange): String? {
        if (range.isEmpty() || range.first !in segments.indices || range.last !in segments.indices) return null
        val value = text.substring(segments[range.first].start, segments[range.last].end).trim()
        return value.ifEmpty { null }
    }

    private fun reviewRange(range: IntRange, lowerLimit: Int, upperLimit: Int, segments: List<Segment>): IntRange {
        var lower = range.first
        var upperExclusive = range.last + 1
        var leading = 0
        var trailing = 0
        while (lower > lowerLimit && leading < AutoLearnLimits.REVIEW_CONTEXT_SEGMENTS_PER_SIDE) {
            if (segments[lower - 1].isBoundary) break
            lower--
            leading++
        }
        while (upperExclusive < upperLimit && trailing < AutoLearnLimits.REVIEW_CONTEXT_SEGMENTS_PER_SIDE) {
            if (segments[upperExclusive].isBoundary) break
            upperExclusive++
            trailing++
        }
        return lower until upperExclusive
    }

    private fun cleanedPair(source: String, destination: String): Pair<String, String>? {
        val cleanSource = cleanedEdges(source).trim()
        val cleanDestination = cleanedEdges(destination).trim()
        if (cleanSource.isEmpty() || cleanDestination.isEmpty()) return null
        return cleanSource to cleanDestination
    }

    private fun cleanedEdges(text: String): String =
        text.trimStart { it in LEADING_WRAPPERS }.trimEnd { it in TRAILING_SENTENCE_PUNCTUATION }

    private fun containsControlCharacter(text: String): Boolean = text.any { Character.getType(it) == Character.CONTROL.toInt() }

    private fun characterCount(text: String): Int = text.codePointCount(0, text.length)

    /** Scripts written without spaces leak whole sentences as one "word"; cap those snippets. */
    private fun isWithinUnspacedPrivacyLimit(source: String, destination: String): Boolean {
        val unspaced = source.none { it.isWhitespace() } && destination.none { it.isWhitespace() }
        if (!unspaced || (!hasCompactScript(source) && !hasCompactScript(destination))) return true
        return characterCount(source) <= AutoLearnLimits.MAX_UNSPACED_CANDIDATE_CHARACTERS &&
            characterCount(destination) <= AutoLearnLimits.MAX_UNSPACED_CANDIDATE_CHARACTERS
    }

    private fun hasCompactScript(text: String): Boolean {
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            if (isCompactScript(codePoint)) return true
            index += Character.charCount(codePoint)
        }
        return false
    }

    private fun isCompactScript(codePoint: Int): Boolean = when (codePoint) {
        in 0x0E00..0x0EFF, // Thai and Lao
        in 0x1000..0x109F, // Myanmar
        in 0x1780..0x17FF, // Khmer
        in 0x1100..0x11FF, // Hangul Jamo
        in 0x3040..0x30FF, // Hiragana and Katakana
        in 0x3130..0x318F, // Hangul Compatibility Jamo
        in 0x3400..0x4DBF, // CJK Extension A
        in 0x4E00..0x9FFF, // CJK Unified Ideographs
        in 0xAC00..0xD7AF, // Hangul Syllables
        in 0x20000..0x2FA1F, // Additional CJK ideographs
        -> true
        else -> false
    }
}
