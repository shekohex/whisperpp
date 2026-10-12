package com.github.shekohex.whisperpp.updater

object PlatformSelector {
    data class Selection(val abi: Abi, val info: PlatformInfo)

    /** Prefers the device's ABIs in preference order, then the universal APK. */
    fun select(release: LatestRelease, supportedAbis: List<String>): Selection? {
        for (abi in supportedAbis) {
            val info = release.platforms[abi] ?: continue
            return Selection(Abi.fromString(abi) ?: Abi.UNIVERSAL, info)
        }
        val universal = release.platforms[Abi.UNIVERSAL.value] ?: return null
        return Selection(Abi.UNIVERSAL, universal)
    }
}
