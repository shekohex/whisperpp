package com.github.shekohex.whisperpp.data

import java.security.GeneralSecurityException

/** Thrown when an imported backup envelope carries crypto/KDF parameters outside the accepted bounds. */
class InvalidBackupCryptoParamsException(message: String) : GeneralSecurityException(message)
