package com.github.shekohex.whisperpp.ime

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.lifecycleScope
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.app.appContainer
import com.github.shekohex.whisperpp.core.permissions.MicPermission
import com.github.shekohex.whisperpp.data.PreferenceKeys.MIC_PERMISSION_REQUESTED
import com.github.shekohex.whisperpp.ui.theme.WhisperToInputTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Translucent trampoline the keyboard opens when the mic permission is missing (D-02). Shows a
 * short rationale card, requests `RECORD_AUDIO`, reports the outcome via [MicPermissionBus] and
 * finishes, so the user never leaves the host app.
 */
class PermissionRequestActivity : ComponentActivity() {

    private var retryAction: String? = null
    private var published = false

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> finishWith(granted) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        retryAction = intent.getStringExtra(EXTRA_RETRY_ACTION)

        if (MicPermission.isGranted(this)) {
            finishWith(true)
            return
        }

        setContent {
            WhisperToInputTheme {
                var status by remember { mutableStateOf<MicPermission.Status?>(null) }

                LaunchedEffect(Unit) {
                    val requestedBefore = appContainer.settingsDataStore.data.first()[MIC_PERMISSION_REQUESTED] ?: false
                    status = MicPermission.status(this@PermissionRequestActivity, requestedBefore)
                }

                PermissionRequestSheet(
                    visible = status != null,
                    permanentlyDenied = status == MicPermission.Status.PERMANENTLY_DENIED,
                    onDismiss = { finishWith(false) },
                    onAllow = { onAllow(status) },
                )
            }
        }
    }

    override fun onDestroy() {
        // Fallback for a finish without an explicit result (e.g. the task was swiped away). Never report a
        // denial that is not real: re-check, and stay silent when access is in place by now.
        if (isFinishing && !published && !MicPermission.isGranted(this)) {
            publish(false)
        }
        super.onDestroy()
    }

    private fun onAllow(status: MicPermission.Status?) {
        when (status) {
            MicPermission.Status.GRANTED -> finishWith(true)
            MicPermission.Status.PERMANENTLY_DENIED -> {
                // The IME re-checks on onStartInputView once the user comes back from settings.
                startActivity(MicPermission.appSettingsIntent(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                finishWith(false)
            }
            MicPermission.Status.REQUESTABLE, null -> {
                lifecycleScope.launch {
                    appContainer.settingsDataStore.edit { it[MIC_PERMISSION_REQUESTED] = true }
                }
                requestPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun finishWith(granted: Boolean) {
        publish(granted)
        finish()
    }

    private fun publish(granted: Boolean) {
        if (published) return
        published = true
        MicPermissionBus.publish(MicPermissionBus.Result(granted = granted, retryAction = retryAction))
    }

    companion object {
        const val EXTRA_RETRY_ACTION = "extra_retry_action"

        fun intent(context: Context, retryAction: String?): Intent =
            Intent(context, PermissionRequestActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_RETRY_ACTION, retryAction)
    }
}

@Composable
private fun PermissionRequestSheet(
    visible: Boolean,
    permanentlyDenied: Boolean,
    onDismiss: () -> Unit,
    onAllow: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically { it / 2 },
        ) {
            Card(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.mic_permission_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        text = stringResource(
                            if (permanentlyDenied) R.string.mic_permission_denied_rationale else R.string.mic_permission_rationale,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.mic_permission_not_now))
                        }
                        Button(onClick = onAllow) {
                            Text(
                                stringResource(
                                    if (permanentlyDenied) R.string.mic_permission_open_settings else R.string.mic_permission_allow,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
