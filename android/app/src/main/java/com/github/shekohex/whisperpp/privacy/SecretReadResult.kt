package com.github.shekohex.whisperpp.privacy

sealed interface SecretReadResult {
    data class Present(val value: String) : SecretReadResult

    data object Missing : SecretReadResult

    /** A value is stored but cannot be decrypted (e.g. restored from backup without its Keystore key). */
    data object DecryptionFailed : SecretReadResult
}
