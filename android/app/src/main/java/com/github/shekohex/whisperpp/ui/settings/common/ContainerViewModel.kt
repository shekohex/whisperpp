package com.github.shekohex.whisperpp.ui.settings.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.shekohex.whisperpp.app.appContainer
import com.github.shekohex.whisperpp.di.AppContainer

/**
 * Scopes a [ViewModel] to the current `ViewModelStoreOwner` (the nav back-stack entry) and builds it
 * from the [AppContainer]. The [SavedStateHandle] carries the typed route arguments.
 */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(
    key: String? = null,
    noinline create: (container: AppContainer, handle: SavedStateHandle) -> VM,
): VM {
    val container = LocalContext.current.appContainer
    return viewModel(
        key = key,
        factory = viewModelFactory {
            initializer { create(container, createSavedStateHandle()) }
        },
    )
}
