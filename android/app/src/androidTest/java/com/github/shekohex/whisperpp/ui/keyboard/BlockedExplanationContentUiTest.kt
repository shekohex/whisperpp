package com.github.shekohex.whisperpp.ui.keyboard

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.privacy.SecureFieldDetector
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BlockedExplanationContentUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun secureFieldContent_isVisibleAndNonEmptyInConstrainedHost() {
        val context = composeRule.activity
        val copy = blockedExplanationCopySpec(
            externalSendBlockedReason = SecureFieldDetector.Reason.PasswordLike,
            externalSendBlockedByAppPolicy = false,
            blockedPackageName = null,
        )

        val title = context.getString(R.string.secure_field_sheet_title)
        val description = context.getString(R.string.secure_field_sheet_description)
        val reason = context.getString(R.string.secure_field_reason_password)
        val openSettings = context.getString(R.string.secure_field_sheet_open_settings)
        val dontShowAgain = context.getString(R.string.secure_field_sheet_dont_show_again)

        assertTrue(title.isNotBlank())
        assertTrue(description.isNotBlank())
        assertTrue(reason.isNotBlank())
        assertTrue(openSettings.isNotBlank())
        assertTrue(dontShowAgain.isNotBlank())

        composeRule.setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier
                        .requiredHeight(220.dp)
                        .requiredWidth(360.dp),
                ) {
                    BlockedExplanationContent(
                        copy = copy,
                        onOpenSettings = {},
                        onClose = {},
                        onDontShowAgain = {},
                        showCloseAction = false,
                    )
                }
            }
        }

        assertDisplayedWithScroll(title)
        assertDisplayedWithScroll(description)
        assertDisplayedWithScroll(reason)
        assertDisplayedWithScroll(openSettings)
        assertDisplayedWithScroll(dontShowAgain)
    }

    @Test
    fun appPolicyContent_isVisibleAndNonEmptyInConstrainedHost() {
        val context = composeRule.activity
        val copy = blockedExplanationCopySpec(
            externalSendBlockedReason = null,
            externalSendBlockedByAppPolicy = true,
            blockedPackageName = "com.example.app",
        )

        val title = context.getString(R.string.blocked_app_policy_sheet_title)
        val description = context.getString(R.string.blocked_app_policy_sheet_description)
        val dontShowAgain = context.getString(R.string.secure_field_sheet_dont_show_again)

        assertTrue(title.isNotBlank())
        assertTrue(description.isNotBlank())

        composeRule.setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier
                        .requiredHeight(220.dp)
                        .requiredWidth(360.dp),
                ) {
                    BlockedExplanationContent(
                        copy = copy,
                        onOpenSettings = {},
                        onClose = {},
                        onDontShowAgain = {},
                        showCloseAction = true,
                    )
                }
            }
        }

        assertDisplayedWithScroll(title)
        assertDisplayedWithScroll(description)
        assertDisplayedWithScroll("com.example.app", substring = true)
        composeRule.onAllNodesWithText(dontShowAgain).assertCountEquals(0)
    }

    private fun assertDisplayedWithScroll(text: String, substring: Boolean = false) {
        composeRule.onNodeWithText(text, substring = substring).performScrollTo().assertIsDisplayed()
    }
}
