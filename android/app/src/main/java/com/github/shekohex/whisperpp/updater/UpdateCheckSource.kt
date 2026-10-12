package com.github.shekohex.whisperpp.updater

fun interface UpdateCheckSource {
    suspend fun checkForUpdate(channel: UpdateChannel): UpdateCheckResult
}
