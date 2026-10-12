package com.github.shekohex.whisperpp.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlatformSelectorTest {
    private fun release(vararg abis: String) = LatestRelease(
        version = "1",
        versionCode = 2,
        pubDate = null,
        platforms = abis.associateWith { PlatformInfo(signature = "sha256:$it", url = "https://e.com/$it.apk") }
    )

    @Test
    fun exactAbiWins() {
        val selection = PlatformSelector.select(release("arm64-v8a", "universal"), listOf("arm64-v8a"))!!
        assertEquals(Abi.ARM64_V8A, selection.abi)
        assertEquals("sha256:arm64-v8a", selection.info.signature)
    }

    @Test
    fun secondaryDeviceAbiIsUsedBeforeUniversal() {
        val selection = PlatformSelector.select(
            release("armeabi-v7a", "universal"),
            listOf("arm64-v8a", "armeabi-v7a")
        )!!
        assertEquals(Abi.ARMEABI_V7A, selection.abi)
    }

    @Test
    fun universalIsTheFallback() {
        val selection = PlatformSelector.select(release("x86", "universal"), listOf("arm64-v8a"))!!
        assertEquals(Abi.UNIVERSAL, selection.abi)
    }

    @Test
    fun noMatchReturnsNull() {
        assertNull(PlatformSelector.select(release("x86"), listOf("arm64-v8a")))
        assertNull(PlatformSelector.select(release("x86"), emptyList()))
    }
}
