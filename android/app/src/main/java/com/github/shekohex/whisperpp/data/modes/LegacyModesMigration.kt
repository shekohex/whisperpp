package com.github.shekohex.whisperpp.data.modes

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.github.shekohex.whisperpp.data.AppPromptMapping
import com.github.shekohex.whisperpp.data.AppendMode
import com.github.shekohex.whisperpp.data.LanguageProfile
import com.github.shekohex.whisperpp.data.PreferenceKeys
import com.github.shekohex.whisperpp.data.PromptLibrary
import com.github.shekohex.whisperpp.data.PromptProfile
import com.github.shekohex.whisperpp.data.ProviderModelOverride
import com.github.shekohex.whisperpp.data.parseAppPromptMappingsJson
import com.github.shekohex.whisperpp.data.parsePromptProfilesJson
import com.github.shekohex.whisperpp.data.sanitizePromptProfiles
import com.github.shekohex.whisperpp.domain.modes.Mode
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first

/**
 * One-way migration of the legacy trio into modes (§4.10): every app mapping becomes a mode owning its
 * packages, every language profile a mode with that language (and the default mode takes the profile of
 * the keyboard's current language), every user prompt profile ("preset") a mode using it. Legacy stores
 * are only read. Guarded by [PreferenceKeys.MODES_MIGRATED] and deterministic ids, so it runs once.
 * Prompt text that only lived in a mapping (`appPromptAppend`) is saved as a new prompt profile.
 */
class LegacyModesMigration(
    private val dataStore: DataStore<Preferences>,
    private val gson: Gson = Gson(),
) {
    /** What [migrate] would do now; the UI asks for a backup export first when [hasLegacyData]. */
    data class Preview(
        val alreadyMigrated: Boolean,
        val appMappingCount: Int,
        val languageProfileCount: Int,
        val presetCount: Int,
        val modesToAdd: List<Mode>,
        /** The default mode after taking the current language's profile; null when unchanged. */
        val defaultModeUpdate: Mode?,
        val promptsToAdd: List<PromptProfile>,
        /** The full mode list after migrating. */
        val resultingModes: List<Mode>,
    ) {
        val hasLegacyData: Boolean
            get() = appMappingCount + languageProfileCount + presetCount > 0
    }

    suspend fun migrationPreview(): Preview = preview(dataStore.data.first())

    /** Runs the migration unless it already ran; returns what was applied (empty when already migrated). */
    suspend fun migrate(): Preview {
        var result: Preview? = null
        dataStore.edit { prefs ->
            val preview = preview(prefs)
            result = preview
            if (preview.alreadyMigrated) return@edit
            writeModes(prefs, preview.resultingModes, gson)
            if (preview.promptsToAdd.isNotEmpty()) {
                val prompts = parsePromptProfilesJson(prefs[PreferenceKeys.PROMPT_PROFILES_JSON], gson) + preview.promptsToAdd
                prefs[PreferenceKeys.PROMPT_PROFILES_JSON] = gson.toJson(sanitizePromptProfiles(prompts))
            }
            prefs[PreferenceKeys.MODES_MIGRATED] = true
        }
        return checkNotNull(result)
    }

    /**
     * Startup step: with no legacy data to lose (fresh install, or nothing configured) the seeds are
     * stored and the migration is marked done. Otherwise waits for the UI to call [migrate]. True when
     * modes are migrated afterwards.
     */
    suspend fun runIfNothingToMigrate(): Boolean {
        var migrated = false
        dataStore.edit { prefs ->
            if (prefs[PreferenceKeys.MODES_MIGRATED] == true) {
                migrated = true
                return@edit
            }
            if (preview(prefs).hasLegacyData) return@edit
            if (storedModes(prefs, gson) == null) writeModes(prefs, ModeSeeds.ALL, gson)
            prefs[PreferenceKeys.MODES_MIGRATED] = true
            migrated = true
        }
        return migrated
    }

    private fun preview(prefs: Preferences): Preview {
        val promptProfiles = parsePromptProfilesJson(prefs[PreferenceKeys.PROMPT_PROFILES_JSON], gson)
        val preview = plan(
            current = readModes(prefs, gson),
            mappings = parseAppPromptMappingsJson(prefs[PreferenceKeys.APP_PROMPT_MAPPINGS_JSON], gson),
            languageProfiles = parseLanguageProfiles(prefs[PreferenceKeys.PROFILES_JSON]),
            promptProfiles = promptProfiles,
            keyboardLanguage = prefs[PreferenceKeys.LANGUAGE_CODE],
        )
        return preview.copy(alreadyMigrated = prefs[PreferenceKeys.MODES_MIGRATED] == true)
    }

    private fun parseLanguageProfiles(json: String?): List<LanguageProfile> {
        val raw = json?.trim().orEmpty()
        if (raw.isEmpty()) return emptyList()
        val type = object : TypeToken<List<LanguageProfile?>>() {}.type
        val decoded: List<LanguageProfile?> = try {
            gson.fromJson(raw, type)
        } catch (_: Exception) {
            null
        } ?: return emptyList()
        return decoded.filterNotNull().mapNotNull { profile ->
            val code = (profile.languageCode as String?).orEmpty().trim()
            if (code.isEmpty()) return@mapNotNull null
            LanguageProfile(
                languageCode = code,
                transcriptionProviderId = (profile.transcriptionProviderId as String?).orEmpty().trim(),
                transcriptionModelId = (profile.transcriptionModelId as String?).orEmpty().trim(),
                enhancementProviderId = (profile.enhancementProviderId as String?).orEmpty().trim(),
                enhancementModelId = (profile.enhancementModelId as String?).orEmpty().trim(),
            )
        }
    }

    companion object {
        const val APP_MODE_PREFIX = "legacy-app-"
        const val APP_PROMPT_PREFIX = "legacy-app-prompt-"
        const val LANGUAGE_MODE_PREFIX = "legacy-lang-"
        const val PRESET_MODE_PREFIX = "legacy-preset-"

        private data class MappingKey(
            val profileId: String?,
            val appendMode: AppendMode,
            val appPromptAppend: String?,
            val stt: Mode.Selection?,
            val text: Mode.Selection?,
            val autoSend: Boolean?,
        )

        /** Pure migration plan from the current modes (stored or seeds) and the legacy stores. */
        internal fun plan(
            current: List<Mode>,
            mappings: List<AppPromptMapping>,
            languageProfiles: List<LanguageProfile>,
            promptProfiles: List<PromptProfile>,
            keyboardLanguage: String?,
        ): Preview {
            val ids = current.map { it.id }.toMutableSet()
            val claimed = current.flatMap { it.appPackages }.toMutableSet()
            val profilesById = promptProfiles.associateBy { it.id }
            val promptIds = profilesById.keys.toMutableSet()
            val added = mutableListOf<Mode>()
            val prompts = mutableListOf<PromptProfile>()

            // App mappings: identical settings share one mode. Packages a mode already owns stay there.
            mappings.filter { it.packageName !in claimed }
                .groupBy { it.toKey() }
                .forEach { (key, group) ->
                    val packages = group.map { it.packageName }.distinct()
                    val first = packages.first()
                    val id = APP_MODE_PREFIX + first
                    if (!ids.add(id)) return@forEach
                    val profile = key.profileId?.let(profilesById::get)
                    val promptId = when {
                        key.appendMode == AppendMode.NO_APPEND -> null
                        // A missing profile meant "base prompt only", app text included.
                        key.profileId != null && profile == null -> null
                        key.appPromptAppend != null -> {
                            val promptId = APP_PROMPT_PREFIX + first
                            if (promptIds.add(promptId)) {
                                prompts += PromptProfile(
                                    id = promptId,
                                    name = "${profile?.name ?: first} (app)",
                                    promptAppend = listOfNotNull(profile?.promptAppend, key.appPromptAppend)
                                        .filter { it.isNotBlank() }
                                        .joinToString(separator = "\n\n"),
                                )
                            }
                            promptId
                        }
                        else -> profile?.id
                    }
                    claimed += packages
                    added += Mode(
                        id = id,
                        name = appModeName(profile?.name, packages),
                        icon = iconFor(profile?.id),
                        sttSelection = key.stt,
                        enhancementSelection = key.text,
                        promptId = promptId,
                        autoSend = key.autoSend,
                        appPackages = packages,
                    )
                }

            // Language profiles: one mode per language; the keyboard's current language feeds the default.
            val activeLanguage = keyboardLanguage?.trim()?.takeUnless { it.isEmpty() || it.equals("auto", ignoreCase = true) }
            val default = current.firstOrNull { it.isDefault }
            var defaultUpdate: Mode? = null
            val profiles = languageProfiles.distinctBy { it.languageCode.lowercase() }
            profiles.forEach { profile ->
                val stt = selection(profile.transcriptionProviderId, profile.transcriptionModelId)
                val text = selection(profile.enhancementProviderId, profile.enhancementModelId)
                if (stt == null && text == null) return@forEach
                if (default != null && profile.languageCode.equals(activeLanguage, ignoreCase = true)) {
                    val base = defaultUpdate ?: default
                    val updated = base.copy(
                        sttSelection = base.sttSelection ?: stt,
                        enhancementSelection = base.enhancementSelection ?: text,
                    )
                    if (updated != default) defaultUpdate = updated
                }
                val id = LANGUAGE_MODE_PREFIX + profile.languageCode
                if (!ids.add(id)) return@forEach
                added += Mode(
                    id = id,
                    name = "Dictation (${profile.languageCode})",
                    icon = Mode.Icon.MIC,
                    sttSelection = stt,
                    language = profile.languageCode,
                    enhancementSelection = text,
                )
            }

            // Presets: user prompt profiles no mode or app mapping uses yet become modes with enhancement on.
            val builtInIds = PromptLibrary.BUILT_IN.map { it.id }.toSet()
            val presets = promptProfiles.filter { it.id !in builtInIds && !it.id.startsWith(APP_PROMPT_PREFIX) }
            val usedPromptIds = (current + added).mapNotNull { it.promptId }.toSet() + mappings.mapNotNull { it.profileId }
            presets.filter { it.id !in usedPromptIds }.forEach { profile ->
                val id = PRESET_MODE_PREFIX + profile.id
                if (!ids.add(id)) return@forEach
                added += Mode(
                    id = id,
                    name = profile.name,
                    icon = Mode.Icon.NOTES,
                    enhancementEnabled = true,
                    promptId = profile.id,
                )
            }

            val update = defaultUpdate
            val base = current.map { if (update != null && it.id == update.id) update else it }
            return Preview(
                alreadyMigrated = false,
                appMappingCount = mappings.size,
                languageProfileCount = profiles.count {
                    selection(it.transcriptionProviderId, it.transcriptionModelId) != null ||
                        selection(it.enhancementProviderId, it.enhancementModelId) != null
                },
                presetCount = presets.size,
                modesToAdd = added.toList(),
                defaultModeUpdate = defaultUpdate,
                promptsToAdd = prompts.toList(),
                resultingModes = ModeRules.normalize(base + added),
            )
        }

        private fun AppPromptMapping.toKey() = MappingKey(
            profileId = profileId,
            appendMode = appendMode,
            appPromptAppend = appPromptAppend,
            stt = sttOverride?.toSelection(),
            text = textOverride?.toSelection(),
            autoSend = autoSend,
        )

        private fun ProviderModelOverride.toSelection(): Mode.Selection? = selection(providerId, modelId)

        private fun selection(providerId: String, modelId: String): Mode.Selection? =
            if (providerId.isBlank() && modelId.isBlank()) null else Mode.Selection(providerId.trim(), modelId.trim())

        private fun appModeName(profileName: String?, packages: List<String>): String = when {
            packages.size > 1 -> "${profileName ?: "Apps"} (${packages.size} apps)"
            profileName != null -> "$profileName · ${packages.first()}"
            else -> packages.first()
        }

        private fun iconFor(promptId: String?): Mode.Icon = when (promptId) {
            PromptLibrary.CHAT_ID -> Mode.Icon.CHAT
            PromptLibrary.EMAIL_ID -> Mode.Icon.MAIL
            PromptLibrary.REWRITE_ID -> Mode.Icon.EDIT
            PromptLibrary.ASSISTANT_ID -> Mode.Icon.ASSISTANT
            else -> Mode.Icon.CUSTOM
        }
    }
}
