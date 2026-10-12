package com.github.shekohex.whisperpp.updater

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Validates `latest.json` at the boundary. Gson bypasses Kotlin defaults and null-safety, so the
 * document is read as a tree and only well-formed values reach [LatestRelease].
 */
object ReleaseMetadataParser {
    private val DATE_PREFIX = Regex("""^(\d{4}-\d{2}-\d{2})(?:[T ].*)?$""")

    sealed class Outcome {
        data class Valid(val release: LatestRelease) : Outcome()
        data class Invalid(val error: UpdateError.MalformedMetadata) : Outcome()
    }

    fun parse(json: String): Outcome {
        val root = try {
            JsonParser.parseString(json)
        } catch (e: Exception) {
            return invalid("not valid JSON", e)
        }
        if (!root.isJsonObject) return invalid("expected a JSON object")
        return parseObject(root.asJsonObject)
    }

    /** Extracts `yyyy-MM-dd` from an ISO-8601 timestamp; null for anything else. */
    fun normalizeDate(raw: String?): String? =
        raw?.trim()?.let { DATE_PREFIX.matchEntire(it)?.groupValues?.get(1) }

    private fun parseObject(obj: JsonObject): Outcome {
        val version = obj.string("version")?.trim()?.takeIf { it.isNotEmpty() }
            ?: return invalid("missing version")
        val versionCode = obj.int("version_code")?.takeIf { it > 0 }
            ?: return invalid("missing or invalid version_code")
        val platformsJson = obj.get("platforms")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: return invalid("missing platforms")

        val platforms = LinkedHashMap<String, PlatformInfo>()
        for ((abi, element) in platformsJson.entrySet()) {
            parsePlatform(element)?.let { platforms[abi] = it }
        }
        if (platforms.isEmpty()) return invalid("no usable platform entries")

        return Outcome.Valid(
            LatestRelease(
                version = version,
                versionCode = versionCode,
                pubDate = normalizeDate(obj.string("pub_date")),
                changelog = obj.string("changelog")?.takeIf { it.isNotBlank() },
                platforms = platforms
            )
        )
    }

    private fun parsePlatform(element: JsonElement?): PlatformInfo? {
        val obj = element?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val url = obj.string("url")?.trim()?.takeIf { it.startsWith("https://") } ?: return null
        val signature = obj.string("signature")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return PlatformInfo(signature = signature, url = url)
    }

    private fun invalid(reason: String, cause: Throwable? = null) =
        Outcome.Invalid(UpdateError.MalformedMetadata(reason, cause))

    private fun JsonObject.string(name: String): String? {
        val e = get(name) ?: return null
        return if (e.isJsonPrimitive) e.asString else null
    }

    private fun JsonObject.int(name: String): Int? {
        val e = get(name) ?: return null
        if (!e.isJsonPrimitive) return null
        return e.asString.trim().toIntOrNull()
    }
}
