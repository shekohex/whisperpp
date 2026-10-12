package com.github.shekohex.whisperpp.data.autolearn

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.github.shekohex.whisperpp.data.PreferenceKeys
import com.github.shekohex.whisperpp.domain.autolearn.AutoLearnCandidate
import com.github.shekohex.whisperpp.domain.autolearn.DetectedCorrectionCandidate
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Diff candidates waiting for AI review (§4.11 step 3), persisted as JSON so they survive process
 * death. A review batch [claims][claimBatch] candidates and then [removes][remove] them on success or
 * [releases][release] them on failure; claims left by a killed process are released by [releaseAll].
 */
class AutoLearnPendingQueue(
    private val dataStore: DataStore<Preferences>,
    private val clock: () -> Long = System::currentTimeMillis,
    private val gson: Gson = Gson(),
) {
    /** Candidates not claimed by an in-flight review. */
    val pendingCount: Flow<Int> = dataStore.data
        .map { prefs -> read(prefs).count { !it.claimed } }
        .distinctUntilChanged()

    /** Every retained candidate, including a batch currently under review. */
    val outstandingCount: Flow<Int> = dataStore.data
        .map { prefs -> read(prefs).size }
        .distinctUntilChanged()

    /**
     * Appends new candidates, skipping pairs already queued (case-insensitive); the oldest entries are
     * dropped beyond [MAX_PENDING]. Returns how many were added.
     */
    suspend fun enqueue(candidates: List<DetectedCorrectionCandidate>): Int {
        if (candidates.isEmpty()) return 0
        var added = 0
        edit { current ->
            val known = current.mapTo(HashSet()) { pairKey(it.originalText, it.correctedText) }
            val now = clock()
            val additions = candidates.mapNotNull { candidate ->
                val original = candidate.originalText.trim()
                val corrected = candidate.correctedText.trim()
                if (original.isEmpty() || corrected.isEmpty()) return@mapNotNull null
                if (!known.add(pairKey(original, corrected))) return@mapNotNull null
                AutoLearnCandidate(candidate.id, original, corrected, enqueuedAt = now)
            }
            added = additions.size
            (current + additions).takeLast(MAX_PENDING)
        }
        return added
    }

    /** Marks up to [max] unclaimed candidates (oldest first) as claimed and returns them. */
    suspend fun claimBatch(max: Int = BATCH_SIZE): List<AutoLearnCandidate> {
        var claimed: List<AutoLearnCandidate> = emptyList()
        edit { current ->
            val ids = current.asSequence().filter { !it.claimed }.take(max.coerceAtLeast(0)).map { it.id }.toSet()
            val updated = current.map { if (it.id in ids) it.copy(claimed = true) else it }
            claimed = updated.filter { it.id in ids }
            updated
        }
        return claimed
    }

    suspend fun release(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val keys = ids.toSet()
        edit { current -> current.map { if (it.id in keys) it.copy(claimed = false) else it } }
    }

    /** Releases every claim; call when no review is running (e.g. after process death). */
    suspend fun releaseAll() {
        edit { current -> current.map { if (it.claimed) it.copy(claimed = false) else it } }
    }

    suspend fun remove(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val keys = ids.toSet()
        edit { current -> current.filterNot { it.id in keys } }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(PreferenceKeys.AutoLearn.QUEUE_JSON) }
    }

    private suspend fun edit(transform: (List<AutoLearnCandidate>) -> List<AutoLearnCandidate>) {
        dataStore.edit { prefs ->
            val current = read(prefs)
            val updated = transform(current)
            if (updated != current) prefs[PreferenceKeys.AutoLearn.QUEUE_JSON] = gson.toJson(updated)
        }
    }

    /** Decodes entry by entry so one unreadable record cannot strand the whole queue. */
    private fun read(prefs: Preferences): List<AutoLearnCandidate> {
        val raw = prefs[PreferenceKeys.AutoLearn.QUEUE_JSON]?.trim().orEmpty()
        if (raw.isEmpty()) return emptyList()
        val decoded = try {
            gson.fromJson<List<AutoLearnCandidate?>>(raw, CANDIDATE_LIST_TYPE).orEmpty()
        } catch (_: Exception) {
            return emptyList()
        }
        return decoded.mapNotNull { it?.sanitized() }
    }

    private fun AutoLearnCandidate.sanitized(): AutoLearnCandidate? {
        // Gson leaves missing fields null despite the Kotlin types.
        val id = (id as String?)?.takeIf { it.isNotBlank() } ?: return null
        val original = (originalText as String?)?.takeIf { it.isNotBlank() } ?: return null
        val corrected = (correctedText as String?)?.takeIf { it.isNotBlank() } ?: return null
        return AutoLearnCandidate(id, original, corrected, enqueuedAt, claimed)
    }

    private fun pairKey(original: String, corrected: String): String =
        original.lowercase() + "\u0000" + corrected.lowercase()

    companion object {
        const val BATCH_SIZE = 25
        const val MAX_PENDING = 200
        private val CANDIDATE_LIST_TYPE = object : TypeToken<List<AutoLearnCandidate?>>() {}.type
    }
}
