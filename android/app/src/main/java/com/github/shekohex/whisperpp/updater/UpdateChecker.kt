package com.github.shekohex.whisperpp.updater

import com.github.shekohex.whisperpp.BuildConfig
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

private data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String,
    val name: String?,
    val body: String?,
    @SerializedName("published_at") val publishedAt: String?,
    val prerelease: Boolean
)

class UpdateChecker(
    private val httpClient: HttpClient,
    private val gson: Gson = GsonBuilder().create()
) {
    private val githubRepo = BuildConfig.GITHUB_REPO
    private val baseDownloadUrl = "https://github.com/$githubRepo/releases/download"
    private val apiBaseUrl = "https://api.github.com/repos/$githubRepo"

    suspend fun checkForUpdate(channel: UpdateChannel): UpdateCheckResult {
        return try {
            val tag = when (channel) {
                UpdateChannel.STABLE -> "latest"
                UpdateChannel.NIGHTLY -> "nightly"
            }

            val latestJsonUrl = "$baseDownloadUrl/$tag/latest.json"
            val response = httpClient.get(latestJsonUrl)
            if (!response.status.isSuccess()) {
                return UpdateCheckResult.Error(
                    "Failed to fetch update info: HTTP ${response.status.value}"
                )
            }

            val body = response.bodyAsText()
            var release = gson.fromJson(body, LatestRelease::class.java)
                ?: return UpdateCheckResult.Error("Failed to parse update info")

            val currentVersionCode = BuildConfig.VERSION_CODE
            if (release.versionCode <= currentVersionCode) {
                return UpdateCheckResult.UpToDate
            }

            if (release.changelog.isNullOrBlank()) {
                val changelog = fetchChangelog(channel)
                release = release.copy(changelog = changelog)
            }

            val deviceAbi = Abi.fromDevice()
            val platformInfo = release.platforms[deviceAbi.value]
                ?: release.platforms[Abi.UNIVERSAL.value]
                ?: return UpdateCheckResult.Error("No APK available for ${deviceAbi.value}")

            UpdateCheckResult.Available(
                release = release,
                deviceAbi = deviceAbi,
                platformInfo = platformInfo
            )
        } catch (e: Exception) {
            UpdateCheckResult.Error("Update check failed: ${e.message}", e)
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
        } catch (e: Exception) {
            null
        }
    }
}
