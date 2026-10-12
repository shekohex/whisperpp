package com.github.shekohex.whisperpp.ui.providers

import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.domain.providers.DiscoveredModel
import com.github.shekohex.whisperpp.ui.providers.ModelDiscoveryViewModel.AddAction
import com.github.shekohex.whisperpp.ui.providers.ModelDiscoveryViewModel.Filter
import com.github.shekohex.whisperpp.ui.providers.ModelDiscoveryViewModel.UiState

/** Lists a provider's models with filters and add actions (§4.5.2). [existingIds] show as added. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelDiscoverySheet(
    viewModel: ModelDiscoveryViewModel,
    existingIds: Set<String>,
    onAdd: (List<DiscoveredModel>) -> Unit,
    onDismiss: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val add = { action: AddAction -> onAdd(viewModel.take(action)) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.model_discovery_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                TextButton(enabled = state != UiState.Loading, onClick = viewModel::refresh) {
                    Text(stringResource(R.string.model_discovery_refresh))
                }
            }
            when (val current = state) {
                UiState.Idle, UiState.Loading -> LoadingRow()
                is UiState.Error -> Text(current.message, color = MaterialTheme.colorScheme.error)
                is UiState.Loaded -> LoadedContent(
                    state = current,
                    existingIds = existingIds,
                    onFilter = viewModel::setFilter,
                    onToggle = viewModel::toggle,
                    onAdd = add,
                )
            }
        }
    }
}

@Composable
private fun LoadingRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.padding(4.dp))
        Text(stringResource(R.string.model_discovery_loading))
    }
}

@Composable
private fun LoadedContent(
    state: UiState.Loaded,
    existingIds: Set<String>,
    onFilter: (Filter) -> Unit,
    onToggle: (String) -> Unit,
    onAdd: (AddAction) -> Unit,
) {
    state.fetchedAtMillis?.let { fetchedAt ->
        Text(
            stringResource(R.string.model_discovery_fetched_at, DateUtils.getRelativeTimeSpanString(fetchedAt)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Filter.entries.forEach { filter ->
            FilterChip(
                selected = state.filter == filter,
                onClick = { onFilter(filter) },
                label = { Text(stringResource(filter.labelRes)) },
            )
        }
    }
    val visible = state.visibleModels
    if (visible.isEmpty()) {
        Text(stringResource(R.string.model_discovery_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Box(modifier = Modifier.heightIn(max = 420.dp)) {
            LazyColumn {
                items(visible, key = { it.id }) { model ->
                    val added = model.id in existingIds
                    DiscoveredModelRow(
                        model = model,
                        checked = added || model.id in state.selectedIds,
                        added = added,
                        onToggle = { onToggle(model.id) },
                    )
                }
            }
        }
    }
    val selectable = state.selectedIds - existingIds
    Button(
        enabled = selectable.isNotEmpty(),
        onClick = { onAdd(AddAction.SELECTED) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.model_discovery_add_selected, selectable.size))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            enabled = state.models.any { it.capabilities.stt && it.id !in existingIds },
            onClick = { onAdd(AddAction.ALL_STT) },
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.model_discovery_add_all_stt))
        }
        OutlinedButton(
            enabled = state.models.any { it.capabilities.text && it.id !in existingIds },
            onClick = { onAdd(AddAction.ALL_TEXT) },
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.model_discovery_add_all_text))
        }
    }
}

@Composable
private fun DiscoveredModelRow(model: DiscoveredModel, checked: Boolean, added: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = !added, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            enabled = !added,
            onCheckedChange = null,
            modifier = Modifier.minimumInteractiveComponentSize(),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(model.displayName, style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content))
            if (model.displayName != model.id) {
                Text(model.id, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                capabilityLabels(model, added).forEach { label ->
                    AssistChip(onClick = {}, enabled = false, label = { Text(stringResource(label)) })
                }
            }
        }
    }
}

private fun capabilityLabels(model: DiscoveredModel, added: Boolean): List<Int> = buildList {
    val caps = model.capabilities
    if (added) add(R.string.model_discovery_already_added)
    if (caps.stt) add(R.string.model_discovery_cap_stt)
    if (caps.text) add(R.string.model_discovery_cap_text)
    if (caps.realtime) add(R.string.model_discovery_cap_realtime)
    if (caps.thinking) add(R.string.model_discovery_cap_thinking)
    if (!model.isClassified) add(R.string.model_discovery_cap_unclassified)
}

private val Filter.labelRes: Int
    @StringRes get() = when (this) {
        Filter.ALL -> R.string.model_discovery_filter_all
        Filter.STT -> R.string.model_discovery_filter_stt
        Filter.TEXT -> R.string.model_discovery_filter_text
        Filter.REALTIME -> R.string.model_discovery_filter_realtime
    }
