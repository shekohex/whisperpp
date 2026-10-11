package com.github.shekohex.whisperpp.ime

import android.os.Bundle
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Lifecycle, saved-state and ViewModel owners for exactly ONE IME input view instance.
 *
 * The service creates a fresh instance in every `onCreateInputView()` and destroys the previous one,
 * so a `ComposeView` using `DisposeOnViewTreeLifecycleDestroyed` is always disposed together with its
 * view and a rebuilt view never attaches to a stale (STOPPED/DESTROYED) lifecycle.
 *
 * State flow: CREATED (constructed) → RESUMED ([onShown]) → CREATED ([onHidden]) → DESTROYED ([destroy]).
 */
class ImeViewLifecycle internal constructor(
    restoredState: Bundle?,
    createRegistry: (LifecycleOwner) -> LifecycleRegistry,
) : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    constructor(restoredState: Bundle? = null) : this(restoredState, ::LifecycleRegistry)

    private val registry: LifecycleRegistry = createRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle
        get() = registry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    val currentState: Lifecycle.State
        get() = registry.currentState

    init {
        savedStateController.performAttach()
        savedStateController.performRestore(restoredState)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    /** Installs the owners on [view] and, when given, on the IME window's decor view (dialogs/popups look there). */
    fun attach(view: View, decorView: View? = null) {
        view.installOwners()
        decorView?.installOwners()
    }

    fun onShown() {
        if (isDestroyed()) return
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun onHidden() {
        if (isDestroyed()) return
        if (registry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            registry.currentState = Lifecycle.State.STARTED
            registry.currentState = Lifecycle.State.CREATED
        }
    }

    /**
     * Saves registered state into [outState] (when given) so the next instance can restore it, then moves
     * to DESTROYED, which disposes the composition, and clears the ViewModels.
     */
    fun destroy(outState: Bundle? = null) {
        if (isDestroyed()) return
        onHidden()
        outState?.let { savedStateController.performSave(it) }
        registry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }

    private fun isDestroyed(): Boolean = registry.currentState == Lifecycle.State.DESTROYED

    private fun View.installOwners() {
        setViewTreeLifecycleOwner(this@ImeViewLifecycle)
        setViewTreeSavedStateRegistryOwner(this@ImeViewLifecycle)
        setViewTreeViewModelStoreOwner(this@ImeViewLifecycle)
    }
}
