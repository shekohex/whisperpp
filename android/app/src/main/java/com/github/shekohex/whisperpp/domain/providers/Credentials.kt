package com.github.shekohex.whisperpp.domain.providers

/** Secret material for one provider call. [toString] never prints the key. */
data class Credentials(val apiKey: String?) {
    val hasApiKey: Boolean
        get() = !apiKey.isNullOrBlank()

    override fun toString(): String = "Credentials(apiKey=${if (hasApiKey) "***" else "none"})"

    companion object {
        val NONE = Credentials(apiKey = null)
    }
}
