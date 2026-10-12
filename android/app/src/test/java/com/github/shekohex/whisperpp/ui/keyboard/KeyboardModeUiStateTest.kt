package com.github.shekohex.whisperpp.ui.keyboard

import com.github.shekohex.whisperpp.domain.modes.Mode
import com.github.shekohex.whisperpp.domain.modes.ModeResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardModeUiStateTest {
    private val dictation = Mode(id = "dictation", name = "Dictation", icon = Mode.Icon.MIC, isDefault = true)
    private val chat = Mode(id = "chat", name = "Chat", icon = Mode.Icon.CHAT, appPackages = listOf("com.slack"))
    private val email = Mode(id = "email", name = "Email", icon = Mode.Icon.MAIL, enabled = false)
    private val code = Mode(id = "code", name = "Code", icon = Mode.Icon.CODE)
    private val modes = listOf(dictation, chat, email, code)

    @Test
    fun noSelection_usesTheEditorsAppMode() {
        val state = KeyboardModeUiState.resolve(modes, selectedModeId = null, packageName = "com.slack") { true }

        assertEquals("chat", state.active.id)
        assertEquals(ModeResolution.Source.APP, state.source)
    }

    @Test
    fun explicitSelection_winsOverTheAppMode() {
        val state = KeyboardModeUiState.resolve(modes, selectedModeId = "code", packageName = "com.slack") { true }

        assertEquals("code", state.active.id)
        assertEquals(ModeResolution.Source.SELECTED, state.source)
    }

    @Test
    fun disabledSelection_andUnknownApp_fallBackToDefault() {
        val state = KeyboardModeUiState.resolve(modes, selectedModeId = "email", packageName = "com.other") { true }

        assertEquals("dictation", state.active.id)
        assertEquals(ModeResolution.Source.DEFAULT, state.source)
        assertTrue(state.active.isDefault)
    }

    @Test
    fun pickerListsEnabledModesWithAvailability() {
        val state = KeyboardModeUiState.resolve(modes, selectedModeId = null, packageName = null) { it.id != "code" }

        assertEquals(listOf("dictation", "chat", "code"), state.items.map { it.id })
        assertFalse(state.items.single { it.id == "code" }.available)
        assertEquals(Mode.Icon.CHAT, state.items.single { it.id == "chat" }.icon)
    }
}
