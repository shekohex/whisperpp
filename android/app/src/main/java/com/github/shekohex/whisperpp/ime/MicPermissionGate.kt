package com.github.shekohex.whisperpp.ime

/**
 * Keyboard-side mic permission state: whether the "Microphone access needed" banner is shown and
 * which user action to retry once access is granted. Pure logic, no Android dependencies.
 *
 * The gate is the single owner of the pending action: it survives denials (the banner can be tapped
 * again) and is only dropped by a grant or by the user starting another action.
 */
class MicPermissionGate(
    private val isGranted: () -> Boolean,
    private val onMissingChanged: (Boolean) -> Unit,
) {
    var isMissing: Boolean = false
        private set

    var pendingAction: String? = null
        private set

    private var pendingRetry: (() -> Unit)? = null
    private var readyRetry: (() -> Unit)? = null

    /**
     * Returns true when recording may proceed. Otherwise shows the banner and remembers [retry]
     * under [action] so it can run after the user grants access.
     */
    fun check(action: String, retry: (() -> Unit)?): Boolean {
        if (isGranted()) {
            // The user started an action with access in place; an older pending retry is obsolete.
            clearPending()
            readyRetry = null
            setMissing(false)
            return true
        }
        pendingAction = action
        pendingRetry = retry
        readyRetry = null
        setMissing(true)
        return false
    }

    /** Handles a result from [PermissionRequestActivity]. A denial keeps the banner and the pending retry. */
    fun onResult(granted: Boolean, retryAction: String?) {
        if (!granted) return
        val retry = pendingRetry.takeIf { retryAction != null && retryAction == pendingAction }
        clearPending()
        readyRetry = retry
        setMissing(false)
    }

    private fun runReadyRetry(): Boolean {
        val retry = readyRetry ?: return false
        readyRetry = null
        retry()
        return true
    }

    /**
     * Called when the input view starts: runs an armed retry, or clears the banner if access was
     * granted elsewhere (e.g. system settings) without auto-starting anything.
     */
    fun onInputViewStarted() {
        if (runReadyRetry()) return
        if (isMissing && isGranted()) {
            clearPending()
            setMissing(false)
        }
    }

    private fun clearPending() {
        pendingAction = null
        pendingRetry = null
    }

    private fun setMissing(missing: Boolean) {
        if (isMissing == missing) return
        isMissing = missing
        onMissingChanged(missing)
    }

    companion object {
        const val ACTION_DICTATION = "dictation"
        const val ACTION_COMMAND = "command"
    }
}
