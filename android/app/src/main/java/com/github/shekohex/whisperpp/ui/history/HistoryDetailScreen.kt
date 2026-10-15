package com.github.shekohex.whisperpp.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.RuntimeConfigLoader
import com.github.shekohex.whisperpp.data.history.HistoryAudioStore
import com.github.shekohex.whisperpp.data.history.TranscriptHistoryEntry
import com.github.shekohex.whisperpp.ime.SttRequestFactory
import com.github.shekohex.whisperpp.ime.SttTranscriber
import com.github.shekohex.whisperpp.ui.components.ConfirmDialog
import com.github.shekohex.whisperpp.ui.components.EmptyState
import com.github.shekohex.whisperpp.ui.components.LoadingState
import com.github.shekohex.whisperpp.ui.components.PageScaffold
import com.github.shekohex.whisperpp.ui.settings.common.containerViewModel
import com.github.shekohex.whisperpp.ui.theme.Spacing
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

private enum class DetailTab { Raw, Enhanced }

/** One transcript: raw vs enhanced text, audio playback, copy / share / delete and re-enhance. */
@Composable
fun HistoryDetailScreen(entryId: Long, onBack: () -> Unit) {
    val appContext = LocalContext.current.applicationContext
    val viewModel = containerViewModel(key = "history-detail-$entryId") { container, _ ->
        val audioDir = File(appContext.filesDir, HistoryAudioStore.DIR_NAME)
        val config = RuntimeConfigLoader(
            dataStore = container.settingsDataStore,
            repository = container.settingsRepository,
            hasCredential = { !container.secretsStore.getProviderApiKey(it).isNullOrBlank() },
        )
        val sttTranscriber = SttTranscriber(appContext, container.providerFactory)
        HistoryDetailViewModel(
            entryId = entryId,
            repository = container.transcriptHistoryRepository,
            reEnhancer = PromptHistoryReEnhancer(
                context = appContext,
                config = config,
                providers = container.providerFactory,
                enhancementPresetId = container.settingsRepository.enhancementPresetId,
                vocabulary = { container.dictionaryRepository.vocabulary.first() },
            ),
            transcriber = SttHistoryTranscriber(
                context = appContext,
                config = config,
                requests = SttRequestFactory(
                    scriptConversion = sttTranscriber::scriptConversion,
                    audioMediaType = SttTranscriber.MEDIA_TYPE_WAV,
                    vocabulary = { container.dictionaryRepository.vocabulary.first() },
                ),
                transcriber = sttTranscriber,
                replacements = { container.dictionaryRepository.replacements.first() },
            ),
            appResolver = PackageManagerAppResolver(appContext.packageManager),
            keepAudio = { container.settingsRepository.historySettings.first().keepAudio },
            resolveAudio = { path -> resolveHistoryAudioFile(audioDir, path) },
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.history_copied)
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmEnhance by rememberSaveable { mutableStateOf(false) }
    var confirmRetranscribe by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.isGone) { if (state.isGone) onBack() }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            val text = when (message) {
                is HistoryDetailViewModel.Message.Res -> resources.getString(message.textRes)
                is HistoryDetailViewModel.Message.Format -> resources.getString(message.textRes, message.argument)
                is HistoryDetailViewModel.Message.Text -> message.text
            }
            snackbarHost.showSnackbar(text)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.pauseAudio() }

    val entry = state.entry
    PageScaffold(
        title = stringResource(R.string.history_detail_title),
        largeTitle = false,
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHost) },
        actions = {
            if (entry != null) {
                if (entry.hasText) {
                    IconButton(onClick = { shareText(context, entry.finalText) }) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.history_share))
                    }
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.history_delete))
                }
            }
        },
    ) {
        when {
            state.isLoading -> LoadingState(Modifier.fillMaxSize())
            entry == null -> EmptyState(
                icon = Icons.Default.History,
                title = stringResource(R.string.history_detail_missing_title),
                body = stringResource(R.string.history_detail_missing_body),
                modifier = Modifier.fillMaxSize(),
            )
            else -> HistoryDetailContent(
                entry = entry,
                state = state,
                onCopied = { text ->
                    copyTranscript(context, text)
                    scope.launch { snackbarHost.showSnackbar(copiedMessage) }
                },
                onReEnhance = { confirmEnhance = true },
                onCancelReEnhance = viewModel::cancelReEnhance,
                onRetry = {
                    if (entry.hasText) confirmRetranscribe = true else viewModel.retryTranscription()
                },
                onCancelRetry = viewModel::cancelRetry,
                onMarkResolved = viewModel::markResolved,
            )
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.history_delete_one_title),
            text = stringResource(R.string.history_delete_one_message),
            confirmLabel = stringResource(R.string.history_delete),
            destructive = true,
            onConfirm = {
                confirmDelete = false
                viewModel.delete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
    if (confirmRetranscribe) {
        ConfirmDialog(
            title = stringResource(R.string.history_retranscribe),
            text = stringResource(R.string.history_retranscribe_confirm_message),
            confirmLabel = stringResource(R.string.history_retranscribe),
            onConfirm = {
                confirmRetranscribe = false
                viewModel.retryTranscription()
            },
            onDismiss = { confirmRetranscribe = false },
        )
    }
    if (confirmEnhance) {
        ConfirmDialog(
            title = stringResource(R.string.history_reenhance_confirm_title),
            text = stringResource(R.string.history_reenhance_confirm_message),
            confirmLabel = stringResource(R.string.history_reenhance_confirm),
            onConfirm = {
                confirmEnhance = false
                viewModel.reEnhance()
            },
            onDismiss = { confirmEnhance = false },
        )
    }
}

@Composable
private fun HistoryDetailContent(
    entry: TranscriptHistoryEntry,
    state: HistoryDetailViewModel.UiState,
    onCopied: (String) -> Unit,
    onReEnhance: () -> Unit,
    onCancelReEnhance: () -> Unit,
    onRetry: () -> Unit,
    onCancelRetry: () -> Unit,
    onMarkResolved: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        HistoryAttentionCard(entry, state, onRetry, onCancelRetry, onMarkResolved)
        if (entry.hasText) TranscriptSection(entry, onCopied)
        state.player?.let { HistoryAudioCard(it) }
        if (entry.hasText) {
            TranscriptActions(
                state = state,
                onStart = onReEnhance,
                onCancel = onCancelReEnhance,
                onRetry = onRetry,
                onCancelRetry = onCancelRetry,
            )
        }
        HistoryDetailMetadata(entry = entry, app = state.app)
    }
}

/** Raw / enhanced tabs with the text and its copy button. */
@Composable
private fun TranscriptSection(entry: TranscriptHistoryEntry, onCopied: (String) -> Unit) {
    var picked by rememberSaveable(entry.id) { mutableStateOf<DetailTab?>(null) }
    val tab = picked ?: if (entry.enhancedText != null) DetailTab.Enhanced else DetailTab.Raw
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            DetailTab.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = tab == option,
                    onClick = { picked = option },
                    shape = SegmentedButtonDefaults.itemShape(index, DetailTab.entries.size),
                ) {
                    Text(
                        stringResource(
                            if (option == DetailTab.Raw) R.string.history_tab_raw else R.string.history_tab_enhanced,
                        ),
                    )
                }
            }
        }
        val shown = if (tab == DetailTab.Raw) entry.rawText else entry.enhancedText
        if (shown == null) {
            Text(
                text = stringResource(R.string.history_not_enhanced),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            TranscriptTextCard(text = shown)
            FilledTonalButton(onClick = { onCopied(shown) }) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    text = stringResource(
                        if (tab == DetailTab.Raw) R.string.history_copy_raw else R.string.history_copy_enhanced,
                    ),
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
        }
    }
}

@Composable
internal fun TranscriptActions(
    state: HistoryDetailViewModel.UiState,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onCancelRetry: () -> Unit,
) {
    if (state.isRetrying) {
        HistoryRetryProgress(isEnhancing = state.isEnhancing, onCancel = onCancelRetry)
    } else if (state.isEnhancing) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.history_reenhance_running), modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.component_cancel)) }
        }
    } else {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Button(onClick = onStart, enabled = !state.isWorking) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.history_reenhance), modifier = Modifier.padding(start = Spacing.sm))
            }
            if (state.canRetry) HistoryRetryButton(hasText = true, onRetry = onRetry)
        }
    }
}
