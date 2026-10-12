package com.github.shekohex.whisperpp.data.modes

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.github.shekohex.whisperpp.data.AppPromptMapping
import com.github.shekohex.whisperpp.data.AppendMode
import com.github.shekohex.whisperpp.data.LanguageProfile
import com.github.shekohex.whisperpp.data.PreferenceKeys
import com.github.shekohex.whisperpp.data.PromptLibrary
import com.github.shekohex.whisperpp.data.PromptProfile
import com.github.shekohex.whisperpp.data.ProviderModelOverride
import com.github.shekohex.whisperpp.data.parsePromptProfilesJson
import com.github.shekohex.whisperpp.domain.modes.Mode
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyModesMigrationTest {
    private val gson = Gson()

    private fun store(build: MutablePreferences.() -> Unit = {}) =
        InMemoryPreferencesStore(mutablePreferencesOf().apply(build))

    private fun MutablePreferences.mappings(vararg mappings: AppPromptMapping) {
        this[PreferenceKeys.APP_PROMPT_MAPPINGS_JSON] = gson.toJson(mappings.toList())
    }

    private fun MutablePreferences.languages(vararg profiles: LanguageProfile) {
        this[PreferenceKeys.PROFILES_JSON] = gson.toJson(profiles.toList())
    }

    private fun MutablePreferences.prompts(vararg profiles: PromptProfile) {
        this[PreferenceKeys.PROMPT_PROFILES_JSON] = gson.toJson(PromptLibrary.BUILT_IN + profiles.toList())
    }

    @Test
    fun freshInstall_seedsAndMarksMigrated_automatically() = runBlocking {
        val store = store { prompts() }
        val migration = LegacyModesMigration(store)

        assertFalse(migration.migrationPreview().hasLegacyData)
        assertTrue(migration.runIfNothingToMigrate())

        assertEquals(true, store.current[PreferenceKeys.MODES_MIGRATED])
        assertEquals(ModeSeeds.ALL, ModesRepository(store).modes.first())
    }

    @Test
    fun legacyData_isNeverMigratedAutomatically() = runBlocking {
        val store = store { mappings(AppPromptMapping(packageName = "org.telegram", profileId = PromptLibrary.CHAT_ID)) }
        val migration = LegacyModesMigration(store)

        assertFalse(migration.runIfNothingToMigrate())

        assertNull(store.current[PreferenceKeys.MODES_MIGRATED])
        assertNull(store.current[PreferenceKeys.MODES_V1])
        assertTrue(migration.migrationPreview().hasLegacyData)
    }

    @Test
    fun mappingsOnly_becomeAppModes_groupingIdenticalSettings() = runBlocking {
        val stt = ProviderModelOverride("p", "whisper")
        val store = store {
            prompts()
            mappings(
                AppPromptMapping(packageName = "org.telegram", profileId = PromptLibrary.CHAT_ID, autoSend = true),
                AppPromptMapping(packageName = "com.whatsapp", profileId = PromptLibrary.CHAT_ID, autoSend = true),
                AppPromptMapping(packageName = "com.slack", sttOverride = stt, appendMode = AppendMode.NO_APPEND),
            )
        }
        val preview = LegacyModesMigration(store).migrate()

        assertEquals(2, preview.modesToAdd.size)
        val chat = preview.resultingModes.single { it.id == "legacy-app-org.telegram" }
        assertEquals(listOf("org.telegram", "com.whatsapp"), chat.appPackages)
        assertEquals(PromptLibrary.CHAT_ID, chat.promptId)
        assertEquals(Mode.Icon.CHAT, chat.icon)
        assertEquals(true, chat.autoSend)
        assertNull("inherits the global enhancement switch", chat.enhancementEnabled)
        val slack = preview.resultingModes.single { it.id == "legacy-app-com.slack" }
        assertEquals(Mode.Selection("p", "whisper"), slack.sttSelection)
        assertNull(slack.promptId)
        assertEquals(ModeSeeds.DICTATION_ID, preview.resultingModes.single { it.isDefault }.id)
        assertEquals(true, store.current[PreferenceKeys.MODES_MIGRATED])
        assertEquals(preview.resultingModes, ModesRepository(store).modes.first())
    }

    @Test
    fun mappingWithAppText_getsItsOwnPromptProfile() = runBlocking {
        val support = PromptProfile("support", "Support", "Calm")
        val store = store {
            prompts(support)
            mappings(AppPromptMapping(packageName = "com.zendesk", profileId = "support", appPromptAppend = "Ticket context"))
        }
        val preview = LegacyModesMigration(store).migrate()

        val prompt = preview.promptsToAdd.single()
        assertEquals("legacy-app-prompt-com.zendesk", prompt.id)
        assertEquals("Calm\n\nTicket context", prompt.promptAppend)
        assertEquals(prompt.id, preview.resultingModes.single { it.id == "legacy-app-com.zendesk" }.promptId)
        assertTrue(parsePromptProfilesJson(store.current[PreferenceKeys.PROMPT_PROFILES_JSON], gson).any { it.id == prompt.id })
        assertTrue("support is used by the app mode, so no preset mode", preview.modesToAdd.none { it.promptId == "support" })
    }

    @Test
    fun languageProfilesOnly_becomeLanguageModes_andTheCurrentLanguageFeedsTheDefault() = runBlocking {
        val store = store {
            this[PreferenceKeys.LANGUAGE_CODE] = "ar"
            languages(
                LanguageProfile("ar", "p", "whisper-ar", "p", "gpt"),
                LanguageProfile("en", "p", "whisper-en", "", ""),
                LanguageProfile("fr", "", "", "", ""),
            )
        }
        val preview = LegacyModesMigration(store).migrate()

        assertEquals(2, preview.languageProfileCount)
        assertEquals(listOf("legacy-lang-ar", "legacy-lang-en"), preview.modesToAdd.map { it.id })
        val ar = preview.resultingModes.single { it.id == "legacy-lang-ar" }
        assertEquals("ar", ar.language)
        assertEquals(Mode.Selection("p", "whisper-ar"), ar.sttSelection)
        assertEquals(Mode.Selection("p", "gpt"), ar.enhancementSelection)
        assertNull(preview.resultingModes.single { it.id == "legacy-lang-en" }.enhancementSelection)
        val default = preview.resultingModes.single { it.isDefault }
        assertEquals(ModeSeeds.DICTATION_ID, default.id)
        assertEquals(Mode.Selection("p", "whisper-ar"), default.sttSelection)
        assertNull("keeps following the keyboard language", default.language)
    }

    @Test
    fun presetsOnly_becomeModesUsingThatPrompt() = runBlocking {
        val store = store { prompts(PromptProfile("legal", "Legal", "Formal")) }
        val preview = LegacyModesMigration(store).migrate()

        val legal = preview.modesToAdd.single()
        assertEquals("legacy-preset-legal", legal.id)
        assertEquals("Legal", legal.name)
        assertEquals("legal", legal.promptId)
        assertEquals(true, legal.enhancementEnabled)
        assertEquals(1, preview.presetCount)
    }

    @Test
    fun mixed_keepsPackagesTheUserAlreadyAssigned_andLeavesLegacyStoresUntouched() = runBlocking {
        val store = store {
            this[PreferenceKeys.LANGUAGE_CODE] = "en"
            prompts(PromptProfile("legal", "Legal", "Formal"))
            mappings(
                AppPromptMapping(packageName = "org.telegram", profileId = PromptLibrary.CHAT_ID),
                AppPromptMapping(packageName = "com.mail", profileId = PromptLibrary.EMAIL_ID),
            )
            languages(LanguageProfile("en", "p", "whisper", "", ""))
        }
        ModesRepository(store).assignApps(ModeSeeds.CHAT_ID, listOf("org.telegram"))
        val legacyBefore = listOf(
            PreferenceKeys.APP_PROMPT_MAPPINGS_JSON,
            PreferenceKeys.PROFILES_JSON,
        ).map { store.current[it] }

        val preview = LegacyModesMigration(store).migrate()

        assertEquals(
            setOf("legacy-app-com.mail", "legacy-lang-en", "legacy-preset-legal"),
            preview.modesToAdd.map { it.id }.toSet(),
        )
        val modes = preview.resultingModes.associateBy { it.id }
        assertEquals(listOf("org.telegram"), modes.getValue(ModeSeeds.CHAT_ID).appPackages)
        assertEquals(listOf("com.mail"), modes.getValue("legacy-app-com.mail").appPackages)
        assertEquals(Mode.Icon.MAIL, modes.getValue("legacy-app-com.mail").icon)
        assertEquals(legacyBefore, listOf(PreferenceKeys.APP_PROMPT_MAPPINGS_JSON, PreferenceKeys.PROFILES_JSON).map { store.current[it] })
    }

    @Test
    fun migrate_isIdempotent() = runBlocking {
        val store = store {
            prompts(PromptProfile("legal", "Legal", "Formal"))
            mappings(AppPromptMapping(packageName = "com.a", appPromptAppend = "x"))
        }
        val migration = LegacyModesMigration(store)
        migration.migrate()
        val modes = store.current[PreferenceKeys.MODES_V1]
        val prompts = store.current[PreferenceKeys.PROMPT_PROFILES_JSON]

        val second = migration.migrate()

        assertTrue(second.alreadyMigrated)
        assertEquals(modes, store.current[PreferenceKeys.MODES_V1])
        assertEquals(prompts, store.current[PreferenceKeys.PROMPT_PROFILES_JSON])
        assertTrue(migration.runIfNothingToMigrate())
        assertTrue(migration.migrationPreview().alreadyMigrated)
    }

    @Test
    fun plan_skipsIdsThatAlreadyExist() {
        val existing = ModeSeeds.ALL + Mode(id = "legacy-preset-legal", name = "Mine")
        val preview = LegacyModesMigration.plan(
            current = existing,
            mappings = emptyList(),
            languageProfiles = emptyList(),
            promptProfiles = listOf(PromptProfile("legal", "Legal", "Formal")),
            keyboardLanguage = null,
        )
        assertTrue(preview.modesToAdd.isEmpty())
        assertEquals("Mine", preview.resultingModes.single { it.id == "legacy-preset-legal" }.name)
    }
}
