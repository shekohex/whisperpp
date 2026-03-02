package com.github.shekohex.whisperpp.ui.keyboard

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.datastore.preferences.core.edit
import com.github.shekohex.whisperpp.EXTRA_SETTINGS_DESTINATION
import com.github.shekohex.whisperpp.MainActivity
import com.github.shekohex.whisperpp.SECURE_FIELD_EXPLANATION_DONT_SHOW_AGAIN
import com.github.shekohex.whisperpp.dataStore
import com.github.shekohex.whisperpp.privacy.SecureFieldDetector
import com.github.shekohex.whisperpp.ui.settings.SettingsScreen
import com.github.shekohex.whisperpp.ui.theme.WhisperToInputTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
class BlockedExplanationActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val blockedByPolicy = intent.getBooleanExtra(EXTRA_EXTERNAL_SEND_BLOCKED_BY_APP_POLICY, false)
        val blockedPackageName = intent.getStringExtra(EXTRA_EXTERNAL_SEND_BLOCKED_PACKAGE_NAME)
        val reasonName = intent.getStringExtra(EXTRA_EXTERNAL_SEND_BLOCKED_REASON)
        val reason = reasonName?.let { name ->
            runCatching { SecureFieldDetector.Reason.valueOf(name) }.getOrNull()
        }
        val copy = blockedExplanationCopySpec(
            externalSendBlockedReason = reason,
            externalSendBlockedByAppPolicy = blockedByPolicy,
            blockedPackageName = blockedPackageName,
        )

        setContent {
            WhisperToInputTheme {
                val sheetState = rememberModalBottomSheetState(
                    skipPartiallyExpanded = true,
                )
                val scope = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    sheetState.show()
                }

                ModalBottomSheet(
                    onDismissRequest = { finish() },
                    sheetState = sheetState,
                    windowInsets = WindowInsets(0, 0, 0, 0),
                ) {
                    BlockedExplanationContent(
                        copy = copy,
                        onOpenSettings = {
                            startActivity(
                                Intent(this@BlockedExplanationActivity, MainActivity::class.java)
                                    .putExtra(EXTRA_SETTINGS_DESTINATION, SettingsScreen.PrivacySafety.route),
                            )
                            finish()
                        },
                        onClose = { finish() },
                        onDontShowAgain = {
                            scope.launch {
                                this@BlockedExplanationActivity.dataStore.edit { prefs ->
                                    prefs[SECURE_FIELD_EXPLANATION_DONT_SHOW_AGAIN] = true
                                }
                            }
                            finish()
                        },
                        showCloseAction = true,
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_EXTERNAL_SEND_BLOCKED_REASON = "extra_external_send_blocked_reason"
        const val EXTRA_EXTERNAL_SEND_BLOCKED_BY_APP_POLICY = "extra_external_send_blocked_by_app_policy"
        const val EXTRA_EXTERNAL_SEND_BLOCKED_PACKAGE_NAME = "extra_external_send_blocked_package_name"
    }
}
