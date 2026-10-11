package com.github.shekohex.whisperpp.core.network

import java.util.Locale

private const val LOCAL_MDNS_SUFFIX = ".local"
private const val LOCALHOST = "localhost"

/**
 * Returns true when [host] is a loopback, private (RFC 1918), link-local, or mDNS (`*.local`) host.
 * Cleartext HTTP is only allowed for these hosts (J-01).
 */
fun isLocalNetworkHost(host: String): Boolean {
    val normalized = host.trim().removePrefix("[").removeSuffix("]").trimEnd('.').lowercase(Locale.ROOT)
    if (normalized.isEmpty()) {
        return false
    }
    if (normalized == LOCALHOST || normalized.endsWith(".$LOCALHOST")) {
        return true
    }
    if (normalized.endsWith(LOCAL_MDNS_SUFFIX) && normalized.length > LOCAL_MDNS_SUFFIX.length) {
        return true
    }
    parseIpv4(normalized)?.let { return isLocalIpv4(it) }
    if (normalized.contains(':')) {
        return isLocalIpv6(normalized)
    }
    return false
}

/**
 * Returns true when a request to [scheme]://[host] may be sent: HTTPS always, HTTP only to local-network hosts.
 */
fun isTransportAllowed(scheme: String, host: String): Boolean {
    return when (scheme.lowercase(Locale.ROOT)) {
        "https", "wss" -> true
        "http", "ws" -> isLocalNetworkHost(host)
        else -> false
    }
}

private fun parseIpv4(host: String): IntArray? {
    val parts = host.split('.')
    if (parts.size != 4) {
        return null
    }
    val octets = IntArray(4)
    for ((index, part) in parts.withIndex()) {
        if (part.isEmpty() || part.length > 3 || !part.all(Char::isDigit)) {
            return null
        }
        val value = part.toInt()
        if (value > 255) {
            return null
        }
        octets[index] = value
    }
    return octets
}

private fun isLocalIpv4(octets: IntArray): Boolean {
    val first = octets[0]
    val second = octets[1]
    return first == 127 ||
        first == 10 ||
        (first == 172 && second in 16..31) ||
        (first == 192 && second == 168) ||
        (first == 169 && second == 254)
}

private fun isLocalIpv6(host: String): Boolean {
    val address = host.substringBefore('%')
    if (address == "::1" || address == "0:0:0:0:0:0:0:1") {
        return true
    }
    val firstGroup = address.substringBefore(':').ifEmpty { return false }
    val value = firstGroup.toIntOrNull(16) ?: return false
    if (firstGroup.length > 4) {
        return false
    }
    val isLinkLocal = value and 0xffc0 == 0xfe80
    val isUniqueLocal = value and 0xfe00 == 0xfc00
    return isLinkLocal || isUniqueLocal
}
