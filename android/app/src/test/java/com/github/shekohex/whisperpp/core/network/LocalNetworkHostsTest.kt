package com.github.shekohex.whisperpp.core.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetworkHostsTest {

    @Test
    fun loopbackHosts_areLocal() {
        listOf("localhost", "LOCALHOST", "api.localhost", "127.0.0.1", "127.10.20.30", "::1", "[::1]")
            .forEach { assertTrue(it, isLocalNetworkHost(it)) }
    }

    @Test
    fun privateIpv4Ranges_areLocal() {
        listOf(
            "10.0.0.1",
            "10.0.2.2",
            "10.255.255.255",
            "172.16.0.1",
            "172.31.255.254",
            "192.168.0.10",
            "192.168.1.100",
            "169.254.1.1",
        ).forEach { assertTrue(it, isLocalNetworkHost(it)) }
    }

    @Test
    fun mdnsAndIpv6LocalHosts_areLocal() {
        listOf("nas.local", "Whisper-Server.local.", "fe80::1", "fe80::1%wlan0", "fd12:3456::1", "fc00::1")
            .forEach { assertTrue(it, isLocalNetworkHost(it)) }
    }

    @Test
    fun publicHosts_areNotLocal() {
        listOf(
            "",
            "api.openai.com",
            "local",
            ".local",
            "localhost.example.com",
            "example.local.com",
            "8.8.8.8",
            "172.15.0.1",
            "172.32.0.1",
            "192.169.0.1",
            "11.0.0.1",
            "1.10.0.0",
            "10.0.0",
            "10.0.0.256",
            "10.0.0.1.5",
            "2001:db8::1",
            "fec0::1",
            "::ffff:8.8.8.8",
        ).forEach { assertFalse(it, isLocalNetworkHost(it)) }
    }

    @Test
    fun transport_httpsAlwaysAllowed_httpOnlyForLocalHosts() {
        assertTrue(isTransportAllowed("https", "api.openai.com"))
        assertTrue(isTransportAllowed("HTTPS", "192.168.1.5"))
        assertTrue(isTransportAllowed("http", "192.168.1.5"))
        assertTrue(isTransportAllowed("http", "localhost"))
        assertFalse(isTransportAllowed("http", "api.openai.com"))
        assertFalse(isTransportAllowed("http", "8.8.8.8"))
        assertFalse(isTransportAllowed("ftp", "localhost"))
    }
}
