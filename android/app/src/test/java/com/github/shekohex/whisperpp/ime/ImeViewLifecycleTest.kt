package com.github.shekohex.whisperpp.ime

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ImeViewLifecycleTest {

    private fun newLifecycle() = ImeViewLifecycle(null) { owner -> LifecycleRegistry.createUnsafe(owner) }

    @Test
    fun startsCreated() {
        val lifecycle = newLifecycle()

        assertEquals(Lifecycle.State.CREATED, lifecycle.currentState)
        assertEquals(Lifecycle.State.CREATED, lifecycle.lifecycle.currentState)
    }

    @Test
    fun shownResumesAndHiddenReturnsToCreated() {
        val lifecycle = newLifecycle()
        val observed = mutableListOf<Lifecycle.Event>()
        lifecycle.lifecycle.addObserver(
            LifecycleEventObserver { _, event -> observed += event },
        )
        observed.clear()

        lifecycle.onShown()
        assertEquals(Lifecycle.State.RESUMED, lifecycle.currentState)

        lifecycle.onHidden()
        assertEquals(Lifecycle.State.CREATED, lifecycle.currentState)
        assertEquals(
            listOf(
                Lifecycle.Event.ON_START,
                Lifecycle.Event.ON_RESUME,
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP,
            ),
            observed,
        )
    }

    @Test
    fun hiddenBeforeShownStaysCreated() {
        val lifecycle = newLifecycle()

        lifecycle.onHidden()

        assertEquals(Lifecycle.State.CREATED, lifecycle.currentState)
    }

    @Test
    fun destroyFromResumedIsDestroyedAndClearsViewModels() {
        val lifecycle = newLifecycle()
        val viewModel = ViewModelProvider(lifecycle)[TrackingViewModel::class.java]
        lifecycle.onShown()

        lifecycle.destroy()

        assertEquals(Lifecycle.State.DESTROYED, lifecycle.currentState)
        assertTrue(viewModel.cleared)
    }

    @Test
    fun callsAfterDestroyAreIgnored() {
        val lifecycle = newLifecycle()
        lifecycle.destroy()

        lifecycle.onShown()
        lifecycle.onHidden()
        lifecycle.destroy()

        assertEquals(Lifecycle.State.DESTROYED, lifecycle.currentState)
    }

    @Test
    fun eachInstanceHasItsOwnOwners() {
        val first = newLifecycle()
        first.onShown()
        first.destroy()
        val second = newLifecycle()

        assertNotSame(first.lifecycle, second.lifecycle)
        assertNotSame(first.viewModelStore, second.viewModelStore)
        assertNotSame(first.savedStateRegistry, second.savedStateRegistry)
        assertEquals(Lifecycle.State.CREATED, second.currentState)
    }

    class TrackingViewModel : ViewModel() {
        var cleared = false

        override fun onCleared() {
            cleared = true
        }
    }
}
