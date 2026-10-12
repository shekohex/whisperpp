package com.github.shekohex.whisperpp.ime

import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.RuntimeChannel
import com.github.shekohex.whisperpp.data.RuntimeWarning
import com.github.shekohex.whisperpp.data.RuntimeWarningKind
import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeWarningNoticesTest {
    private fun warning(kind: RuntimeWarningKind, channel: RuntimeChannel?) =
        RuntimeWarning(kind = kind, channel = channel, message = "log only")

    @Test
    fun messages_areFilteredByChannel_mappedPerKind_andDeduplicated() {
        val warnings = listOf(
            warning(RuntimeWarningKind.INVALID_MODEL, RuntimeChannel.STT),
            warning(RuntimeWarningKind.INVALID_MODEL, RuntimeChannel.STT),
            warning(RuntimeWarningKind.MISSING_SELECTION, RuntimeChannel.TEXT),
            warning(RuntimeWarningKind.MISSING_PROFILE, null),
        )

        assertEquals(
            listOf(R.string.keyboard_warning_stt_model_missing),
            runtimeWarningMessages(warnings, RuntimeChannel.STT),
        )
        assertEquals(
            listOf(R.string.keyboard_warning_text_selection_missing),
            runtimeWarningMessages(warnings, RuntimeChannel.TEXT),
        )
    }

    @Test
    fun messages_emptyWithoutWarnings() {
        assertEquals(emptyList<Int>(), runtimeWarningMessages(emptyList(), RuntimeChannel.TEXT))
    }
}
