package com.github.shekohex.whisperpp.ui.analytics

import com.github.shekohex.whisperpp.analytics.ModelUsage

/** One "Model usage" row; [key] is null for the folded "Other" row. */
data class ModelShare(val key: String?, val uses: Long, val percent: Int) {
    companion object {
        const val MAX_ROWS = 5

        /** Transcriptions + enhancements per model, biggest first; the tail beyond [maxRows] folds into "Other". */
        fun from(usages: List<ModelUsage>, maxRows: Int = MAX_ROWS): List<ModelShare> {
            val counted = usages
                .map { it.key to (it.sessions + it.enhancementSessions).toLong() }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
            val total = counted.sumOf { it.second }
            if (total == 0L) return emptyList()
            val head = if (counted.size > maxRows) counted.take(maxRows - 1) else counted
            val rest = counted.drop(head.size).sumOf { it.second }
            return buildList {
                head.forEach { (key, uses) -> add(ModelShare(key, uses, StatFormat.percent(uses, total))) }
                if (rest > 0) add(ModelShare(null, rest, StatFormat.percent(rest, total)))
            }
        }
    }
}
