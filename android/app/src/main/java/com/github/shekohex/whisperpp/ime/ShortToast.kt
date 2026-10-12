package com.github.shekohex.whisperpp.ime

import android.content.Context
import android.widget.Toast
import com.github.shekohex.whisperpp.R

internal fun Context.showShortToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

internal fun Context.showSetupRequiredToast() {
    showShortToast(getString(R.string.toast_setup_required_stt))
}
