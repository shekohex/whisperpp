package com.github.shekohex.whisperpp.ui.keyboard

import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.privacy.SecureFieldDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockedExplanationCopySpecTest {
    @Test
    fun secureFieldReasons_mapToSecureFieldResources() {
        val cases: List<Pair<SecureFieldDetector.Reason?, Int>> = listOf(
            SecureFieldDetector.Reason.PasswordLike to R.string.secure_field_reason_password,
            SecureFieldDetector.Reason.OtpLike to R.string.secure_field_reason_otp,
            SecureFieldDetector.Reason.NoPersonalizedLearning to R.string.secure_field_reason_no_personalized_learning,
            SecureFieldDetector.Reason.Unknown to R.string.secure_field_reason_unknown,
            null to R.string.secure_field_reason_unknown,
        )

        for ((reason, expectedReasonRes) in cases) {
            val copy = blockedExplanationCopySpec(
                externalSendBlockedReason = reason,
                externalSendBlockedByAppPolicy = false,
                blockedPackageName = null,
            )

            assertEquals(R.string.secure_field_sheet_title, copy.titleRes)
            assertEquals(R.string.secure_field_sheet_description, copy.descriptionRes)
            assertEquals(expectedReasonRes, copy.reasonRes)
            assertNull(copy.reasonArg)
            assertTrue(copy.showDontShowAgain)
        }
    }

    @Test
    fun appPolicyWithoutPackage_usesGenericReason() {
        val copy = blockedExplanationCopySpec(
            externalSendBlockedReason = SecureFieldDetector.Reason.PasswordLike,
            externalSendBlockedByAppPolicy = true,
            blockedPackageName = "   ",
        )

        assertEquals(R.string.blocked_app_policy_sheet_title, copy.titleRes)
        assertEquals(R.string.blocked_app_policy_sheet_description, copy.descriptionRes)
        assertEquals(R.string.blocked_app_policy_reason_generic, copy.reasonRes)
        assertNull(copy.reasonArg)
        assertFalse(copy.showDontShowAgain)
    }

    @Test
    fun appPolicyWithPackage_usesPackageReasonArg() {
        val copy = blockedExplanationCopySpec(
            externalSendBlockedReason = null,
            externalSendBlockedByAppPolicy = true,
            blockedPackageName = "com.example.app",
        )

        assertEquals(R.string.blocked_app_policy_sheet_title, copy.titleRes)
        assertEquals(R.string.blocked_app_policy_sheet_description, copy.descriptionRes)
        assertEquals(R.string.blocked_app_policy_reason_for_package, copy.reasonRes)
        assertEquals("com.example.app", copy.reasonArg)
        assertFalse(copy.showDontShowAgain)
    }
}
