package com.github.shekohex.whisperpp.ui.settings.providers

import com.github.shekohex.whisperpp.core.network.NetworkError
import com.github.shekohex.whisperpp.data.ModelCapabilities
import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.domain.providers.DiscoveredCache
import com.github.shekohex.whisperpp.domain.providers.DiscoveredModel
import com.github.shekohex.whisperpp.domain.providers.DiscoveryUnsupported
import com.github.shekohex.whisperpp.domain.providers.ModelDiscovery
import com.github.shekohex.whisperpp.ui.settings.providers.ModelDiscoveryViewModel.AddAction
import com.github.shekohex.whisperpp.ui.settings.providers.ModelDiscoveryViewModel.Filter
import com.github.shekohex.whisperpp.ui.settings.providers.ModelDiscoveryViewModel.UiState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModelDiscoveryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val whisper = DiscoveredModel("whisper-1", "whisper-1", ModelCapabilities.stt(ProviderType.OPENAI))
    private val realtime = DiscoveredModel("gpt-realtime", "gpt-realtime", ModelCapabilities.stt(ProviderType.OPENAI, realtime = true))
    private val gpt = DiscoveredModel("gpt-4o", "GPT-4o", ModelCapabilities.TEXT)
    private val unknown = DiscoveredModel("babbage-002", "babbage-002", ModelCapabilities())

    private var cache: DiscoveredCache? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun open_withoutCache_showsLoadingThenLoaded_andSavesCache() = runTest(dispatcher) {
        val gate = CompletableDeferred<Result<List<DiscoveredModel>>>()
        val viewModel = viewModel()

        viewModel.open(discovery { gate.await() })
        runCurrent()
        assertEquals(UiState.Loading, viewModel.uiState.value)

        gate.complete(Result.success(listOf(whisper, gpt)))
        advanceUntilIdle()

        assertEquals(UiState.Loaded(listOf(whisper, gpt), fetchedAtMillis = 42L), viewModel.uiState.value)
        assertEquals(DiscoveredCache(42L, listOf(whisper, gpt)), cache)
    }

    @Test
    fun open_withCache_showsCachedListWithoutFetching() = runTest(dispatcher) {
        cache = DiscoveredCache(7L, listOf(gpt))
        var calls = 0
        val viewModel = viewModel()

        viewModel.open(discovery { calls++; Result.success(emptyList()) })
        advanceUntilIdle()

        assertEquals(0, calls)
        assertEquals(UiState.Loaded(listOf(gpt), fetchedAtMillis = 7L), viewModel.uiState.value)
    }

    @Test
    fun refreshFailure_showsErrorMessage() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.open(discovery { Result.failure(NetworkError.Unauthorized(401, "api.openai.com")) })
        advanceUntilIdle()

        assertEquals(UiState.Error("Unauthorized"), viewModel.uiState.value)
    }

    @Test
    fun open_withoutDiscovery_reportsUnsupported() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.open(null)
        advanceUntilIdle()

        assertEquals(UiState.Error("unsupported"), viewModel.uiState.value)
    }

    @Test
    fun filtersSelectionAndAddActions() = runTest(dispatcher) {
        val viewModel = loadedViewModel(listOf(whisper, realtime, gpt, unknown))

        viewModel.setFilter(Filter.REALTIME)
        assertEquals(listOf(realtime), loaded(viewModel).visibleModels)
        viewModel.setFilter(Filter.STT)
        assertEquals(listOf(whisper, realtime), loaded(viewModel).visibleModels)
        viewModel.setFilter(Filter.TEXT)
        assertEquals(listOf(gpt), loaded(viewModel).visibleModels)

        viewModel.toggle("gpt-4o")
        viewModel.toggle("babbage-002")
        viewModel.toggle("babbage-002")
        viewModel.toggle("whisper-1")
        assertEquals(setOf("gpt-4o", "whisper-1"), loaded(viewModel).selectedIds)

        assertEquals(listOf(whisper, gpt), viewModel.take(AddAction.SELECTED))
        assertTrue(loaded(viewModel).selectedIds.isEmpty())
        assertEquals(listOf(whisper, realtime), viewModel.take(AddAction.ALL_STT))
        assertEquals(listOf(gpt), viewModel.take(AddAction.ALL_TEXT))
    }

    @Test
    fun refresh_keepsSelectionOfModelsStillListed() = runTest(dispatcher) {
        var listing = listOf(whisper, gpt)
        val viewModel = viewModel()
        viewModel.open(discovery { Result.success(listing) })
        advanceUntilIdle()
        viewModel.toggle("whisper-1")
        viewModel.toggle("gpt-4o")

        listing = listOf(gpt)
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(setOf("gpt-4o"), loaded(viewModel).selectedIds)
    }

    @Test
    fun merge_addsNewModelsWithDiscoveredCapabilities_andKeepsManualOnes() {
        val manual = ModelConfig("gpt-4o", "My GPT", ModelCapabilities(text = true, thinking = true))
        val custom = ModelConfig("local-model", "Local")

        val merged = ModelDiscoveryViewModel.merge(listOf(manual, custom), listOf(gpt, whisper, unknown, whisper))

        assertEquals(
            listOf(
                manual,
                custom,
                ModelConfig("whisper-1", "whisper-1", whisper.capabilities),
                ModelConfig("babbage-002", "babbage-002", ModelCapabilities.TEXT),
            ),
            merged,
        )
    }

    private fun TestScope.loadedViewModel(models: List<DiscoveredModel>): ModelDiscoveryViewModel {
        val viewModel = viewModel()
        viewModel.open(discovery { Result.success(models) })
        advanceUntilIdle()
        return viewModel
    }

    private fun loaded(viewModel: ModelDiscoveryViewModel) = viewModel.uiState.value as UiState.Loaded

    private fun viewModel() = ModelDiscoveryViewModel(
        loadCache = { cache },
        saveCache = { cache = it },
        errorMessage = { error ->
            when (error) {
                is DiscoveryUnsupported -> "unsupported"
                is NetworkError.Unauthorized -> "Unauthorized"
                else -> "other"
            }
        },
        clock = { 42L },
    )

    private fun discovery(block: suspend () -> Result<List<DiscoveredModel>>) = object : ModelDiscovery {
        override suspend fun listModels(): Result<List<DiscoveredModel>> = block()
    }
}
