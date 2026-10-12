package com.github.shekohex.whisperpp.data.modes

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.github.shekohex.whisperpp.data.PreferenceKeys
import com.github.shekohex.whisperpp.data.PromptLibrary
import com.github.shekohex.whisperpp.domain.modes.Mode
import com.github.shekohex.whisperpp.domain.modes.OutputMode
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModesRepositoryTest {
    private val store = InMemoryPreferencesStore()
    private val repository = ModesRepository(store)

    @Test
    fun emptyStore_exposesSeeds_withDictationAsTheOnlyDefault() = runBlocking {
        val modes = repository.modes.first()

        assertEquals(
            listOf(ModeSeeds.DICTATION_ID, ModeSeeds.CHAT_ID, ModeSeeds.EMAIL_ID, ModeSeeds.REWRITE_ID, ModeSeeds.ASSISTANT_ID),
            modes.map { it.id },
        )
        assertEquals(listOf(ModeSeeds.DICTATION_ID), modes.filter { it.isDefault }.map { it.id })
        assertEquals(PromptLibrary.CHAT_ID, modes.single { it.id == ModeSeeds.CHAT_ID }.promptId)
        val dictation = modes.first()
        assertNull(dictation.sttSelection)
        assertNull(dictation.enhancementEnabled)
        assertNull(dictation.autoSend)
        assertNull(store.current[PreferenceKeys.MODES_V1])
    }

    @Test
    fun upsert_addsAndReplacesById() = runBlocking {
        assertTrue(repository.upsert(Mode(id = "code", name = "Code", icon = Mode.Icon.CODE)))
        assertTrue(repository.upsert(Mode(id = "code", name = "Code v2", language = "en")))

        val code = repository.modes.first().filter { it.id == "code" }
        assertEquals(1, code.size)
        assertEquals("Code v2", code.single().name)
        assertEquals("en", code.single().language)
        assertEquals(6, repository.modes.first().size)
    }

    @Test
    fun upsert_rejectsBlankIdOrName() = runBlocking {
        assertFalse(repository.upsert(Mode(id = " ", name = "x")))
        assertFalse(repository.upsert(Mode(id = "x", name = "")))
        assertNull(store.current[PreferenceKeys.MODES_V1])
    }

    @Test
    fun upsert_keepsDefaultFlag_andCanMoveIt() = runBlocking {
        repository.upsert(ModeSeeds.DICTATION.copy(name = "Voice", isDefault = false))
        assertEquals(ModeSeeds.DICTATION_ID, repository.modes.first().single { it.isDefault }.id)

        repository.upsert(Mode(id = "new", name = "New", isDefault = true, enabled = false))
        val modes = repository.modes.first()
        assertEquals(listOf("new"), modes.filter { it.isDefault }.map { it.id })
        assertTrue("default is always enabled", modes.single { it.id == "new" }.enabled)
    }

    @Test
    fun delete_refusesTheDefault_untilAnotherModeIsDefault() = runBlocking {
        assertFalse(repository.delete(ModeSeeds.DICTATION_ID))
        assertTrue(repository.setDefault(ModeSeeds.CHAT_ID))
        assertTrue(repository.delete(ModeSeeds.DICTATION_ID))

        val modes = repository.modes.first()
        assertTrue(modes.none { it.id == ModeSeeds.DICTATION_ID })
        assertEquals(ModeSeeds.CHAT_ID, modes.single { it.isDefault }.id)
        assertFalse(repository.delete("unknown"))
    }

    @Test
    fun delete_clearsTheKeyboardSelectionOfThatMode() = runBlocking {
        repository.selectMode(ModeSeeds.EMAIL_ID)
        assertEquals(ModeSeeds.EMAIL_ID, repository.selectedModeId.first())

        repository.delete(ModeSeeds.EMAIL_ID)

        assertNull(repository.selectedModeId.first())
    }

    @Test
    fun setEnabled_cannotDisableTheDefault() = runBlocking {
        assertFalse(repository.setEnabled(ModeSeeds.DICTATION_ID, false))
        assertTrue(repository.setEnabled(ModeSeeds.CHAT_ID, false))

        val modes = repository.modes.first()
        assertTrue(modes.single { it.id == ModeSeeds.DICTATION_ID }.enabled)
        assertFalse(modes.single { it.id == ModeSeeds.CHAT_ID }.enabled)
    }

    @Test
    fun assignApps_movesAPackageFromItsPreviousMode() = runBlocking {
        repository.assignApps(ModeSeeds.CHAT_ID, listOf("org.telegram", " com.whatsapp ", "org.telegram"))
        repository.assignApps(ModeSeeds.EMAIL_ID, listOf("com.google.android.gm", "com.whatsapp"))

        val modes = repository.modes.first().associateBy { it.id }
        assertEquals(listOf("org.telegram"), modes.getValue(ModeSeeds.CHAT_ID).appPackages)
        assertEquals(listOf("com.google.android.gm", "com.whatsapp"), modes.getValue(ModeSeeds.EMAIL_ID).appPackages)
        assertFalse(repository.assignApps("unknown", listOf("x")))
    }

    @Test
    fun upsert_claimsPackagesFromOtherModes() = runBlocking {
        repository.assignApps(ModeSeeds.CHAT_ID, listOf("org.telegram"))
        repository.upsert(Mode(id = "tg", name = "Telegram", appPackages = listOf("org.telegram")))

        val modes = repository.modes.first().associateBy { it.id }
        assertEquals(emptyList<String>(), modes.getValue(ModeSeeds.CHAT_ID).appPackages)
        assertEquals(listOf("org.telegram"), modes.getValue("tg").appPackages)
    }

    @Test
    fun storedJson_withMissingFieldsAndDuplicatePackages_isNormalized() = runBlocking {
        val json = """
            [{"id":"a","name":"A","appPackages":["p"]},
             {"id":"b","name":"B","appPackages":["p","q"],"outputMode":"BOGUS","enabled":false},
             {"id":"","name":"invalid"}]
        """.trimIndent()
        val repo = ModesRepository(InMemoryPreferencesStore(mutablePreferencesOf(PreferenceKeys.MODES_V1 to json)))

        val modes = repo.modes.first()
        assertEquals(listOf("a", "b"), modes.map { it.id })
        assertTrue("first mode becomes default when none is", modes[0].isDefault)
        assertEquals(listOf("p"), modes[0].appPackages)
        assertEquals(listOf("q"), modes[1].appPackages)
        assertEquals(Mode.Icon.MIC, modes[0].icon)
        assertEquals(OutputMode.INSERT, modes[1].outputMode)
        assertFalse(modes[1].enabled)
    }

    @Test
    fun corruptJson_fallsBackToSeeds() = runBlocking {
        val repo = ModesRepository(InMemoryPreferencesStore(mutablePreferencesOf(PreferenceKeys.MODES_V1 to "{nope")))
        assertEquals(ModeSeeds.ALL, repo.modes.first())
    }

    @Test
    fun writes_roundTripThroughGson() = runBlocking {
        val mode = Mode(
            id = "full",
            name = "Full",
            icon = Mode.Icon.NOTES,
            sttSelection = Mode.Selection("p", "m"),
            language = "ar",
            enhancementEnabled = false,
            enhancementSelection = Mode.Selection("p2", "m2"),
            promptId = "prompt",
            formatting = Mode.Formatting(fillerRemoval = true, paragraphs = false),
            contextSources = Mode.ContextSources(selectedText = true, clipboard = true),
            outputMode = OutputMode.CLIPBOARD,
            autoSend = true,
            appPackages = listOf("x.y"),
        )
        repository.upsert(mode)

        val reread = ModesRepository(InMemoryPreferencesStore(store.current), Gson()).modes.first()
        assertEquals(mode, reread.single { it.id == "full" })
    }
}
