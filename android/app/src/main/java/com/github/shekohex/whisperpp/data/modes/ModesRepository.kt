package com.github.shekohex.whisperpp.data.modes

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.github.shekohex.whisperpp.data.PreferenceKeys
import com.github.shekohex.whisperpp.domain.modes.Mode
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Modes (§4.10) stored as JSON under [PreferenceKeys.MODES_V1]. Until something is stored the list is
 * [ModeSeeds.ALL]. Every write keeps the [ModeRules] invariants (one default, unique packages).
 */
class ModesRepository(
    private val dataStore: DataStore<Preferences>,
    private val gson: Gson = Gson(),
) {
    val modes: Flow<List<Mode>> = dataStore.data.map { readModes(it, gson) }.distinctUntilChanged()

    /** Mode picked with the keyboard chip; null = resolve by app, then default. */
    val selectedModeId: Flow<String?> = dataStore.data
        .map { prefs -> prefs[PreferenceKeys.SELECTED_MODE_ID]?.trim()?.takeIf { it.isNotEmpty() } }
        .distinctUntilChanged()

    /** False while legacy app mappings / language profiles / presets wait for [LegacyModesMigration]. */
    val isMigrated: Flow<Boolean> = dataStore.data
        .map { it[PreferenceKeys.MODES_MIGRATED] == true }
        .distinctUntilChanged()

    /**
     * Inserts or replaces [mode] by id. Its packages move to it from other modes; it becomes the default
     * when flagged so, and a default mode stays default. False when [mode] is invalid.
     */
    suspend fun upsert(mode: Mode): Boolean {
        val sanitized = ModeRules.sanitize(mode) ?: return false
        return mutate { current ->
            val existing = current.firstOrNull { it.id == sanitized.id }
            val updated = sanitized.copy(isDefault = sanitized.isDefault || existing?.isDefault == true)
            val list = if (existing == null) current + updated else current.map { if (it.id == updated.id) updated else it }
            list to setOf(updated.id)
        }
    }

    /** False for an unknown id or the default mode (make another mode default first). */
    suspend fun delete(modeId: String): Boolean {
        val id = modeId.trim()
        var deleted = false
        dataStore.edit { prefs ->
            val current = readModes(prefs, gson)
            val target = current.firstOrNull { it.id == id }
            if (target == null || target.isDefault) return@edit
            writeModes(prefs, ModeRules.normalize(current - target), gson)
            if (prefs[PreferenceKeys.SELECTED_MODE_ID] == id) prefs.remove(PreferenceKeys.SELECTED_MODE_ID)
            deleted = true
        }
        return deleted
    }

    /** Makes [modeId] the (enabled) default; the previous default becomes an ordinary mode. */
    suspend fun setDefault(modeId: String): Boolean = mutate { current ->
        val id = modeId.trim()
        if (current.none { it.id == id }) return@mutate null
        current.map { it.copy(isDefault = it.id == id, enabled = it.enabled || it.id == id) } to setOf(id)
    }

    /** The default mode cannot be disabled. */
    suspend fun setEnabled(modeId: String, enabled: Boolean): Boolean = mutate { current ->
        val id = modeId.trim()
        val target = current.firstOrNull { it.id == id } ?: return@mutate null
        if (target.isDefault && !enabled) return@mutate null
        current.map { if (it.id == id) it.copy(enabled = enabled) else it } to emptySet()
    }

    /** Sets the packages of [modeId]; a package already used by another mode moves here. */
    suspend fun assignApps(modeId: String, packages: List<String>): Boolean = mutate { current ->
        val id = modeId.trim()
        if (current.none { it.id == id }) return@mutate null
        current.map { if (it.id == id) it.copy(appPackages = packages) else it } to setOf(id)
    }

    /** Keyboard mode chip; null clears the explicit choice. */
    suspend fun selectMode(modeId: String?) {
        val id = modeId?.trim()?.takeIf { it.isNotEmpty() }
        dataStore.edit { prefs ->
            if (id == null) prefs.remove(PreferenceKeys.SELECTED_MODE_ID) else prefs[PreferenceKeys.SELECTED_MODE_ID] = id
        }
    }

    /** [transform] returns the new list and the ids that win package/default conflicts, or null for no change. */
    private suspend fun mutate(transform: (List<Mode>) -> Pair<List<Mode>, Set<String>>?): Boolean {
        var changed = false
        dataStore.edit { prefs ->
            val (list, claimFirst) = transform(readModes(prefs, gson)) ?: return@edit
            writeModes(prefs, ModeRules.normalize(list, claimFirst), gson)
            changed = true
        }
        return changed
    }
}

/** Stored modes, or null when nothing (valid) is stored yet. */
internal fun storedModes(prefs: Preferences, gson: Gson): List<Mode>? {
    val raw = prefs[PreferenceKeys.MODES_V1]?.trim().orEmpty()
    if (raw.isEmpty()) return null
    val type = object : TypeToken<List<Mode?>>() {}.type
    val decoded: List<Mode?> = try {
        gson.fromJson(raw, type)
    } catch (_: Exception) {
        null
    } ?: return null
    return decoded.mapNotNull(ModeRules::sanitize).takeIf { it.isNotEmpty() }?.let { ModeRules.normalize(it) }
}

internal fun readModes(prefs: Preferences, gson: Gson): List<Mode> = storedModes(prefs, gson) ?: ModeSeeds.ALL

internal fun writeModes(prefs: MutablePreferences, modes: List<Mode>, gson: Gson) {
    prefs[PreferenceKeys.MODES_V1] = gson.toJson(modes)
}
