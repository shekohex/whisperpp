package com.github.shekohex.whisperpp.privacy

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureFieldDetectorTest {

    @Test
    fun detect_returnsPasswordLike_forPasswordInputTypes() {
        val password = editorInfo(
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
        )
        val webPassword = editorInfo(
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        )
        val pin = editorInfo(
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
        )

        assertPasswordLike(password)
        assertPasswordLike(webPassword)
        assertPasswordLike(pin)
    }

    @Test
    fun detect_returnsOtpLike_forNumericVerificationHint() {
        val editorInfo = editorInfo(
            inputType = InputType.TYPE_CLASS_NUMBER,
            hintText = "Verification code",
        )

        val result = SecureFieldDetector.detect(editorInfo)

        assertTrue(result.isSecure)
        assertEquals(SecureFieldDetector.Reason.OtpLike, result.reason)
    }

    @Test
    fun detect_returnsNoPersonalizedLearning_whenImeFlagPresent() {
        val editorInfo = editorInfo(
            inputType = InputType.TYPE_CLASS_TEXT,
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING,
        )

        val result = SecureFieldDetector.detect(editorInfo)

        assertTrue(result.isSecure)
        assertEquals(SecureFieldDetector.Reason.NoPersonalizedLearning, result.reason)
    }

    @Test
    fun detect_returnsUnknown_forNormalTextField() {
        val editorInfo = editorInfo(
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            hintText = "Email address",
        )

        val result = SecureFieldDetector.detect(editorInfo)

        assertFalse(result.isSecure)
        assertEquals(SecureFieldDetector.Reason.Unknown, result.reason)
    }

    private fun assertPasswordLike(editorInfo: EditorInfo) {
        val result = SecureFieldDetector.detect(editorInfo)
        assertTrue(result.isSecure)
        assertEquals(SecureFieldDetector.Reason.PasswordLike, result.reason)
    }

    private fun editorInfo(
        inputType: Int,
        hintText: String? = null,
        imeOptions: Int = 0,
    ): EditorInfo {
        return EditorInfo().apply {
            this.inputType = inputType
            this.hintText = hintText
            this.imeOptions = imeOptions
        }
    }
}
