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
    val versionCode: Int,
    /** Release day as `yyyy-MM-dd`, or null when the metadata carried no usable date. */
    val pubDate: String?,
    val changelog: String? = null,
    val platforms: Map<String, PlatformInfo>
)

/** Why an update check failed. [isTransient] decides whether a background retry makes sense. */
sealed class UpdateError(val message: String, val cause: Throwable? = null) {
    abstract val isTransient: Boolean

    /** Connectivity problems and timeouts. */
    class Network(message: String, cause: Throwable? = null) : UpdateError(message, cause) {
        override val isTransient = true
    }

    /** HTTP 5xx, 408 and 429 answers; the server may recover. */
    class Server(val httpStatus: Int) : UpdateError("Update server error: HTTP $httpStatus") {
        override val isTransient = true
    }

    /** Other non-2xx answers (404, 403, ...); retrying will not change the outcome. */
    class Client(val httpStatus: Int) : UpdateError("Update info unavailable: HTTP $httpStatus") {
        override val isTransient = false
    }

    class MalformedMetadata(reason: String, cause: Throwable? = null) :
        UpdateError("Invalid update metadata: $reason", cause) {
        override val isTransient = false
    }

    class NoCompatibleApk(val abis: List<String>) :
        UpdateError("No APK available for ${abis.joinToString().ifEmpty { "this device" }}") {
        override val isTransient = false
    }

    class Unexpected(cause: Throwable) :
        UpdateError("Update check failed: ${cause.message ?: cause.javaClass.simpleName}", cause) {
        override val isTransient = false
    }
}

sealed class UpdateCheckResult {
    data class Available(
        val release: LatestRelease,
        val deviceAbi: Abi,
        val platformInfo: PlatformInfo
    ) : UpdateCheckResult()

    data object UpToDate : UpdateCheckResult()

    class Error(val error: UpdateError) : UpdateCheckResult() {
        val message: String get() = error.message
        val cause: Throwable? get() = error.cause
        val isTransient: Boolean get() = error.isTransient
    }
}

sealed class DownloadState {
    data object Idle : DownloadState()
    data class Downloading(val progress: Float, val bytesDownloaded: Long, val totalBytes: Long) : DownloadState()
    data class Verifying(val filePath: String) : DownloadState()
    data class Ready(val filePath: String) : DownloadState()
    /** [message] is technical (logs); the UI shows text for [reason]. */
    data class Failed(
        val message: String,
        val cause: Throwable? = null,
        val reason: DownloadFailure = DownloadFailure.UNKNOWN,
    ) : DownloadState()
}

/** Why a download failed, mapped to a localized message by the UI. */
enum class DownloadFailure {
    INSECURE_URL,
    STORAGE_UNAVAILABLE,
    INSUFFICIENT_SPACE,
    START_FAILED,
    NOT_FOUND,
    NETWORK,
    FILE_ERROR,
    FILE_MISSING,
    CHECKSUM_MISMATCH,
    INVALID_CHECKSUM,
    VERIFICATION_FAILED,
    UNKNOWN,
}
