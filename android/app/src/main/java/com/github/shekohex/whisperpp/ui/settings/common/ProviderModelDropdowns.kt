package com.github.shekohex.whisperpp.ui.settings.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.data.ServiceProvider

/**
 * Read-only dropdown with a list of options; used for provider and model pickers.
 * [clearLabel] adds a leading "none/inherit" entry; [emptyLabel] is shown when [options] is empty.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SettingsDropdown(
    label: String,
    value: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    clearLabel: String? = null,
    onClear: () -> Unit = {},
    emptyLabel: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            readOnly = true,
            enabled = enabled,
            value = value,
            onValueChange = {},
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            clearLabel?.let {
                DropdownMenuItem(
                    text = { Text(it) },
                    onClick = {
                        onClear()
                        expanded = false
                    },
                )
            }
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
            if (options.isEmpty() && emptyLabel != null) {
                DropdownMenuItem(text = { Text(emptyLabel) }, onClick = { expanded = false })
            }
        }
    }
}

/** Provider dropdown followed by a model dropdown limited to [isCompatible] models. */
@Composable
fun ProviderModelDropdowns(
    providers: List<ServiceProvider>,
    selectedProvider: ServiceProvider?,
    providerValue: String,
    modelValue: String,
    isCompatible: (ModelConfig) -> Boolean,
    providerClearLabel: String,
    onClearProvider: () -> Unit,
    onSelectProvider: (ServiceProvider) -> Unit,
    onSelectModel: (ModelConfig) -> Unit,
    providerLabel: String = "Provider",
    modelLabel: String = "Model",
    modelClearLabel: String? = null,
    onClearModel: () -> Unit = {},
    emptyProvidersLabel: String = "No Providers Configured",
) {
    SettingsDropdown(
        label = providerLabel,
        value = providerValue,
        options = providers,
        optionLabel = { it.name },
        onSelect = onSelectProvider,
        clearLabel = providerClearLabel,
        onClear = onClearProvider,
        emptyLabel = emptyProvidersLabel,
    )
    SettingsDropdown(
        label = modelLabel,
        value = modelValue,
        options = selectedProvider?.models?.filter(isCompatible).orEmpty(),
        optionLabel = { it.name },
        onSelect = onSelectModel,
        enabled = selectedProvider != null,
        clearLabel = modelClearLabel,
        onClear = onClearModel,
        emptyLabel = "No compatible models",
    )
}
