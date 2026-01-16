package com.github.shekohex.whisperpp.updater

import android.os.Build
import com.google.gson.annotations.SerializedName

enum class UpdateChannel {
    @SerializedName("stable") STABLE,
    @SerializedName("nightly") NIGHTLY;

    companion object {
        fun fromString(value: String): UpdateChannel = when (value.lowercase()) {
            "nightly" -> NIGHTLY
            else -> STABLE
        }
    }
}

enum class Abi(val value: String) {
    @SerializedName("armeabi-v7a") ARMEABI_V7A("armeabi-v7a"),
    @SerializedName("arm64-v8a") ARM64_V8A("arm64-v8a"),
    @SerializedName("x86") X86("x86"),
    @SerializedName("x86_64") X86_64("x86_64"),
    @SerializedName("universal") UNIVERSAL("universal");

    companion object {
        fun fromDevice(): Abi {
            val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
            return entries.find { it.value == primaryAbi } ?: ARM64_V8A
        }

        fun fromString(value: String): Abi? = entries.find { it.value == value }
    }
}

data class PlatformInfo(
    val signature: String,
    val url: String
)

data class LatestRelease(
    val version: String,
    @SerializedName("version_code") val versionCode: Int,
    @SerializedName("pub_date") val pubDate: String,
    val changelog: String? = null,
    val platforms: Map<String, PlatformInfo>
)

sealed class UpdateCheckResult {
    data class Available(
        val release: LatestRelease,
        val deviceAbi: Abi,
        val platformInfo: PlatformInfo
    ) : UpdateCheckResult()

    data object UpToDate : UpdateCheckResult()

    data class Error(val message: String, val cause: Throwable? = null) : UpdateCheckResult()
}

sealed class DownloadState {
    data object Idle : DownloadState()
    data class Downloading(val progress: Float, val bytesDownloaded: Long, val totalBytes: Long) : DownloadState()
    data class Verifying(val filePath: String) : DownloadState()
    data class Ready(val filePath: String) : DownloadState()
    data class Failed(val message: String, val cause: Throwable? = null) : DownloadState()
}
