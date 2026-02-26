package com.github.shekohex.whisperpp.privacy

import android.text.InputType
import android.view.inputmethod.EditorInfo
import java.util.Locale

object SecureFieldDetector {
    enum class Reason {
        PasswordLike,
        OtpLike,
        NoPersonalizedLearning,
        Unknown,
    }

    data class Result(
        val isSecure: Boolean,
        val reason: Reason,
    )

    fun detect(editorInfo: EditorInfo?): Result {
        if (editorInfo == null) {
            return Result(isSecure = false, reason = Reason.Unknown)
        }

        val inputType = editorInfo.inputType

        if (isPasswordLike(inputType)) {
            return Result(isSecure = true, reason = Reason.PasswordLike)
        }

        if (isNoPersonalizedLearning(editorInfo)) {
            return Result(isSecure = true, reason = Reason.NoPersonalizedLearning)
        }

        if (isOtpLike(editorInfo, inputType)) {
            return Result(isSecure = true, reason = Reason.OtpLike)
        }

        return Result(isSecure = false, reason = Reason.Unknown)
    }

    private fun isNoPersonalizedLearning(editorInfo: EditorInfo): Boolean {
        return (editorInfo.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
    }

    private fun isPasswordLike(inputType: Int): Boolean {
        val klass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION

        val textPasswordLike = klass == InputType.TYPE_CLASS_TEXT &&
            (
                variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                )
        val numberPasswordLike =
            klass == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD

        return textPasswordLike || numberPasswordLike
    }

    private fun isOtpLike(editorInfo: EditorInfo, inputType: Int): Boolean {
        val hint = editorInfo.hintText
            ?.toString()
            ?.lowercase(Locale.US)
            ?.trim()
            ?.replace(SPACE_REGEX, " ")
            .orEmpty()
        if (hint.isBlank()) {
            return false
        }

        if (!isNumericOrPasswordLike(inputType)) {
            return false
        }

        if (OTP_STRONG_HINT_PATTERNS.any { pattern -> pattern.containsMatchIn(hint) }) {
            return true
        }

        return CODE_HINT_PATTERN.containsMatchIn(hint) &&
            OTP_CODE_CONTEXT_PATTERNS.any { pattern -> pattern.containsMatchIn(hint) }
    }

    private fun isNumericOrPasswordLike(inputType: Int): Boolean {
        val klass = inputType and InputType.TYPE_MASK_CLASS
        return klass == InputType.TYPE_CLASS_NUMBER ||
            isPasswordLike(inputType)
    }

    private val SPACE_REGEX = Regex("\\s+")

    private val OTP_STRONG_HINT_PATTERNS = listOf(
        Regex("\\botp\\b"),
        Regex("\\bone[\\s-]*time\\b"),
        Regex("\\bverification\\b"),
        Regex("\\b2fa\\b"),
        Regex("\\bpasscode\\b"),
        Regex("\\bpin\\b"),
    )

    private val CODE_HINT_PATTERN = Regex("\\bcode\\b")

    private val OTP_CODE_CONTEXT_PATTERNS = listOf(
        Regex("\\b(auth|authenticate|authentication)\\b"),
        Regex("\\b(verify|verification)\\b"),
        Regex("\\bsecurity\\b"),
        Regex("\\b(login|sign[\\s-]*in)\\b"),
        Regex("\\bone[\\s-]*time\\b"),
        Regex("\\botp\\b"),
        Regex("\\b2fa\\b"),
        Regex("\\bpin\\b"),
        Regex("\\bpasscode\\b"),
        Regex("\\btoken\\b"),
        Regex("\\bsms\\b"),
    )
}
