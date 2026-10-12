package com.github.shekohex.whisperpp.ui.modes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.ui.components.PreviewSurface
import com.github.shekohex.whisperpp.ui.components.ThemePreviews
import com.github.shekohex.whisperpp.ui.theme.Spacing

/** Inherit / On / Off for a nullable mode switch (null = the global setting). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TriStateSelector(
    title: String,
    value: Boolean?,
    onValueChange: (Boolean?) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    val options = listOf(
        null to stringResource(R.string.modes_tristate_inherit),
        true to stringResource(R.string.modes_tristate_on),
        false to stringResource(R.string.modes_tristate_off),
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (supporting != null) {
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (option, label) ->
                SegmentedButton(
                    selected = value == option,
                    onClick = { onValueChange(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    label = { Text(label, maxLines = 1) },
                )
            }
        }
    }
}

@ThemePreviews
@Composable
private fun TriStateSelectorPreview() {
    PreviewSurface {
        TriStateSelector(title = "Remove filler words", value = null, onValueChange = {}, supporting = "Inherit: On")
    }
}
