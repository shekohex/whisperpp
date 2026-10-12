package com.github.shekohex.whisperpp.ui.modes

import com.github.shekohex.whisperpp.data.modes.InMemoryPreferencesStore
import com.github.shekohex.whisperpp.data.modes.ModeSeeds
import com.github.shekohex.whisperpp.data.modes.ModesRepository
import com.github.shekohex.whisperpp.domain.modes.Mode
import com.github.shekohex.whisperpp.ui.modes.ModeEditViewModel.Status
import com.github.shekohex.whisperpp.ui.modes.ModeEditViewModel.ValidationError
import com.github.shekohex.whisperpp.ui.settings.common.LauncherApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModeEditViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = ModesRepository(InMemoryPreferencesStore())

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun validate_requiresUniqueNonBlankName() {
        val others = listOf(Mode(id = "chat", name = "Chat"))

        assertEquals(setOf(ValidationError.NAME_BLANK), ModeEditViewModel.validate(Mode(id = "x", name = "  "), others))
        assertEquals(setOf(ValidationError.NAME_DUPLICATE), ModeEditViewModel.validate(Mode(id = "x", name = " chat "), others))
        assertEquals(
            setOf(ValidationError.NAME_TOO_LONG),
            ModeEditViewModel.validate(Mode(id = "x", name = "a".repeat(ModeEditViewModel.MAX_NAME_LENGTH + 1)), others),
        )
        assertTrue(ModeEditViewModel.validate(Mode(id = "x", name = "Code"), others).isEmpty())
    }

    @Test
    fun validate_flagsProviderWithoutModel_unlessEnhancementIsOff() {
        val draft = Mode(
            id = "x",
            name = "Code",
            sttSelection = Mode.Selection(providerId = "openai", modelId = ""),
            enhancementSelection = Mode.Selection(providerId = "openai", modelId = ""),
        )

        assertEquals(
            setOf(ValidationError.STT_MODEL_MISSING, ValidationError.ENHANCEMENT_MODEL_MISSING),
            ModeEditViewModel.validate(draft, emptyList()),
        )
        assertEquals(
            setOf(ValidationError.STT_MODEL_MISSING),
            ModeEditViewModel.validate(draft.copy(enhancementEnabled = false), emptyList()),
        )
    }

    @Test
    fun save_withInvalidDraft_showsErrorsAndStoresNothing() = runTest(dispatcher) {
        val viewModel = viewModel(modeId = null)
        advanceUntilIdle()

        viewModel.save()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(Status.EDITING, state.status)
        assertTrue(state.showErrors)
        assertEquals(setOf(ValidationError.NAME_BLANK), state.errors)
        assertEquals(ModeSeeds.ALL.map { it.id }, repository.modes.first().map { it.id })
    }

    @Test
    fun save_newMode_storesTrimmedName() = runTest(dispatcher) {
        val viewModel = viewModel(modeId = null)
        advanceUntilIdle()

        viewModel.update { it.copy(name = "  Code  ", icon = Mode.Icon.CODE) }
        viewModel.save()
        advanceUntilIdle()

        assertEquals(Status.SAVED, viewModel.uiState.value.status)
        val saved = repository.modes.first().single { it.name == "Code" }
        assertEquals(Mode.Icon.CODE, saved.icon)
        assertFalse(saved.isDefault)
    }

    @Test
    fun toggleApp_ownedByAnotherMode_showsOwnerAndMovesOnSave() = runTest(dispatcher) {
        repository.assignApps(ModeSeeds.CHAT_ID, listOf("com.slack", "com.whatsapp"))
        val viewModel = viewModel(modeId = ModeSeeds.EMAIL_ID)
        advanceUntilIdle()

        viewModel.toggleApp("com.slack")
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(listOf("com.slack"), state.draft.appPackages)
        assertEquals(ModeSeeds.CHAT_ID, state.ownerOf("com.slack")?.id)

        viewModel.save()
        advanceUntilIdle()

        val modes = repository.modes.first()
        assertEquals(listOf("com.slack"), modes.single { it.id == ModeSeeds.EMAIL_ID }.appPackages)
        assertEquals(listOf("com.whatsapp"), modes.single { it.id == ModeSeeds.CHAT_ID }.appPackages)
    }

    @Test
    fun toggleApp_twice_removesIt_andUnknownModeIsNotFound() = runTest(dispatcher) {
        val viewModel = viewModel(modeId = ModeSeeds.CHAT_ID)
        advanceUntilIdle()
        viewModel.toggleApp("com.slack")
        viewModel.toggleApp("com.slack")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.draft.appPackages.isEmpty())
        assertNull(viewModel.uiState.value.ownerOf("com.slack"))

        val missing = viewModel(modeId = "gone")
        advanceUntilIdle()
        assertEquals(Status.NOT_FOUND, missing.uiState.value.status)
    }

    private fun TestScope.viewModel(modeId: String?): ModeEditViewModel {
        val viewModel = ModeEditViewModel(
            modeId = modeId,
            modesRepository = repository,
            providers = flowOf(emptyList()),
            profiles = flowOf(emptyList()),
            loadApps = { listOf(LauncherApp("com.slack", "Slack")) },
        )
        backgroundScope.launch { viewModel.uiState.collect {} }
        return viewModel
    }
}
