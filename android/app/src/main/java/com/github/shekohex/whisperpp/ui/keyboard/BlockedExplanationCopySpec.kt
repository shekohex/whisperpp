package com.github.shekohex.whisperpp.ui.keyboard

import androidx.annotation.StringRes
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.privacy.SecureFieldDetector

internal data class BlockedExplanationCopySpec(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @StringRes val reasonRes: Int,
    val reasonArg: String? = null,
    val showDontShowAgain: Boolean,
)

internal fun blockedExplanationCopySpec(
    externalSendBlockedReason: SecureFieldDetector.Reason?,
    externalSendBlockedByAppPolicy: Boolean,
    blockedPackageName: String?,
): BlockedExplanationCopySpec {
    if (externalSendBlockedByAppPolicy) {
        val packageName = blockedPackageName?.trim().orEmpty()
        return if (packageName.isNotEmpty()) {
            BlockedExplanationCopySpec(
                titleRes = R.string.blocked_app_policy_sheet_title,
                descriptionRes = R.string.blocked_app_policy_sheet_description,
                reasonRes = R.string.blocked_app_policy_reason_for_package,
                reasonArg = packageName,
                showDontShowAgain = false,
            )
        } else {
            BlockedExplanationCopySpec(
                titleRes = R.string.blocked_app_policy_sheet_title,
                descriptionRes = R.string.blocked_app_policy_sheet_description,
                reasonRes = R.string.blocked_app_policy_reason_generic,
                reasonArg = null,
                showDontShowAgain = false,
            )
        }
    }

    val reasonRes = when (externalSendBlockedReason) {
        SecureFieldDetector.Reason.PasswordLike -> R.string.secure_field_reason_password
        SecureFieldDetector.Reason.OtpLike -> R.string.secure_field_reason_otp
        SecureFieldDetector.Reason.NoPersonalizedLearning -> R.string.secure_field_reason_no_personalized_learning
        SecureFieldDetector.Reason.Unknown,
        null,
        -> R.string.secure_field_reason_unknown
    }

    return BlockedExplanationCopySpec(
        titleRes = R.string.secure_field_sheet_title,
        descriptionRes = R.string.secure_field_sheet_description,
        reasonRes = reasonRes,
        reasonArg = null,
        showDontShowAgain = true,
    )
}
