package com.github.shekohex.whisperpp.updater

import android.os.Build
import com.github.shekohex.whisperpp.BuildConfig
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

private data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String,
    val name: String?,
    val body: String?,
    @SerializedName("published_at") val publishedAt: String?,
    val prerelease: Boolean
)

class UpdateChecker(
    private val httpClient: HttpClient,
    private val gson: Gson = GsonBuilder().create(),
    private val currentVersionCode: Int = BuildConfig.VERSION_CODE,
    private val supportedAbis: () -> List<String> = { Build.SUPPORTED_ABIS.toList() },
    githubRepo: String = BuildConfig.GITHUB_REPO,
    private val baseDownloadUrl: String = "https://github.com/$githubRepo/releases/download",
    private val apiBaseUrl: String = "https://api.github.com/repos/$githubRepo"
) : UpdateCheckSource {

    override suspend fun checkForUpdate(channel: UpdateChannel): UpdateCheckResult {
        return try {
            val tag = when (channel) {
                UpdateChannel.STABLE -> "latest"
                UpdateChannel.NIGHTLY -> "nightly"
            }

            val response = httpClient.get("$baseDownloadUrl/$tag/latest.json")
            if (!response.status.isSuccess()) {
                return UpdateCheckResult.Error(classifyHttpStatus(response.status.value))
            }

            var release = when (val parsed = ReleaseMetadataParser.parse(response.bodyAsText())) {
                is ReleaseMetadataParser.Outcome.Valid -> parsed.release
                is ReleaseMetadataParser.Outcome.Invalid -> return UpdateCheckResult.Error(parsed.error)
            }

            if (release.versionCode <= currentVersionCode) {
                return UpdateCheckResult.UpToDate
            }

            val abis = supportedAbis()
            val selection = PlatformSelector.select(release, abis)
                ?: return UpdateCheckResult.Error(UpdateError.NoCompatibleApk(abis))

            if (release.changelog.isNullOrBlank()) {
                release = release.copy(changelog = fetchChangelog(channel))
            }

            UpdateCheckResult.Available(
                release = release,
                deviceAbi = selection.abi,
                platformInfo = selection.info
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            UpdateCheckResult.Error(classifyException(e))
        }
    }

    private suspend fun fetchChangelog(channel: UpdateChannel): String? {
        return try {
            val releaseUrl = when (channel) {
                UpdateChannel.STABLE -> "$apiBaseUrl/releases/latest"
                UpdateChannel.NIGHTLY -> "$apiBaseUrl/releases/tags/nightly"
            }

            val response = httpClient.get(releaseUrl) {
                header("Accept", "application/vnd.github+json")
                header("X-GitHub-Api-Version", "2022-11-28")
            }

            if (!response.status.isSuccess()) {
                return null
            }

            val ghRelease = gson.fromJson(response.bodyAsText(), GitHubRelease::class.java)
            ghRelease?.body
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        fun classifyHttpStatus(status: Int): UpdateError = when {
            status >= 500 || status == 408 || status == 429 -> UpdateError.Server(status)
            else -> UpdateError.Client(status)
        }

        fun classifyException(e: Exception): UpdateError = when (e) {
            is IOException -> UpdateError.Network(e.message ?: "Network error", e)
            else -> UpdateError.Unexpected(e)
        }
    }
}
