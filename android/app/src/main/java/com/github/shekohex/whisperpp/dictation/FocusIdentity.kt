package com.github.shekohex.whisperpp.dictation

/**
 * Hands out one focus instance id per editor. Only a fresh `onStartInput(restarting = false)` starts a
 * new editor; restarts of the same editor and `onStartInputView` keep the current id.
 */
class FocusIdentity {
    var currentId: Long = 0L
        private set

    fun onStartInput(restarting: Boolean): Long {
        if (!restarting || currentId == 0L) {
            currentId += 1
        }
        return currentId
    }
}
