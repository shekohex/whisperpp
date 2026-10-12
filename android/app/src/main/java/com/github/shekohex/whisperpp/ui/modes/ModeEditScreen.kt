package com.github.shekohex.whisperpp.ui.modes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.ui.components.EmptyState
import com.github.shekohex.whisperpp.ui.components.LoadingState
import com.github.shekohex.whisperpp.ui.components.PageScaffold
import com.github.shekohex.whisperpp.ui.modes.ModeEditViewModel.Status
import com.github.shekohex.whisperpp.ui.settings.common.containerViewModel
import com.github.shekohex.whisperpp.ui.settings.common.loadLauncherApps
import com.github.shekohex.whisperpp.ui.theme.Spacing

/** Create ([modeId] null) or edit a mode; saves on the top-bar action and returns on success. */
@Composable
fun ModeEditScreen(modeId: String?, onBack: () -> Unit) {
    val appContext = LocalContext.current.applicationContext
    val viewModel = containerViewModel(key = "mode-edit-${modeId.orEmpty()}") { container, _ ->
        ModeEditViewModel(container, modeId, loadApps = { loadLauncherApps(appContext) })
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val saveFailed = stringResource(R.string.modes_error_save)

    LaunchedEffect(state.status) {
        when (state.status) {
            Status.SAVED -> onBack()
            Status.SAVE_FAILED -> snackbarHostState.showSnackbar(saveFailed)
            else -> Unit
        }
    }

    PageScaffold(
        title = stringResource(if (state.isNew) R.string.modes_edit_new_title else R.string.modes_edit_title),
        onBack = onBack,
        largeTitle = false,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        actions = {
            TextButton(
                onClick = viewModel::save,
                enabled = state.status == Status.EDITING || state.status == Status.SAVE_FAILED,
            ) { Text(stringResource(R.string.modes_save)) }
        },
    ) {
        when (state.status) {
            Status.LOADING -> LoadingState(modifier = Modifier.fillMaxSize())
            Status.NOT_FOUND -> EmptyState(
                icon = Icons.Filled.ErrorOutline,
                title = stringResource(R.string.modes_edit_not_found_title),
                body = stringResource(R.string.modes_edit_not_found_body),
                modifier = Modifier.fillMaxSize(),
            )
            else -> ModeEditForm(state = state, viewModel = viewModel)
        }
    }
}

@Composable
private fun ModeEditForm(state: ModeEditViewModel.UiState, viewModel: ModeEditViewModel) {
    val errors = if (state.showErrors) state.errors else emptySet()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        ModeIdentitySection(draft = state.draft, errors = errors, onUpdate = viewModel::update)
        ModeTranscriptionSection(
            draft = state.draft,
            providers = state.providers,
            errors = errors,
            onUpdate = viewModel::update,
        )
        ModeEnhancementSection(
            draft = state.draft,
            providers = state.providers,
            profiles = state.profiles,
            errors = errors,
            onUpdate = viewModel::update,
        )
        ModeBehaviourSection(draft = state.draft, onUpdate = viewModel::update)
        ModeAppsSection(state = state, onToggle = viewModel::toggleApp)
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}
