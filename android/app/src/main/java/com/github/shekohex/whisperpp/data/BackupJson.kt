package com.github.shekohex.whisperpp.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import com.google.gson.JsonObject

internal fun JsonObject.readCategoryObject(
    propertyName: String,
    categoryId: String,
    availableCategoryIds: MutableSet<String>,
    skippedItems: MutableList<SkippedImportItem>,
): JsonObject? {
    val element = get(propertyName) ?: return null
    if (element.isJsonNull) {
        return null
    }
    if (!element.isJsonObject) {
        skippedItems += SkippedImportItem(
            categoryId = categoryId,
            reason = "Expected category object.",
        )
        return null
    }
    availableCategoryIds += categoryId
    return element.asJsonObject
}

internal fun JsonObject.stringValue(name: String): String {
    val value = get(name) ?: return ""
    if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) {
        return ""
    }
    return value.asString.trim()
}

internal fun JsonObject.booleanValue(name: String): Boolean? {
    val value = get(name) ?: return null
    if (!value.isJsonPrimitive || !value.asJsonPrimitive.isBoolean) {
        return null
    }
    return value.asBoolean
}

internal fun JsonObject.numberValue(name: String): Number? {
    val value = get(name) ?: return null
    if (!value.isJsonPrimitive || !value.asJsonPrimitive.isNumber) {
        return null
    }
    return value.asNumber
}

internal inline fun <reified T : Enum<T>> JsonObject.enumValue(
    name: String,
    defaultValue: T,
): T {
    val rawValue = stringValue(name)
    if (rawValue.isBlank()) {
        return defaultValue
    }
    return runCatching { enumValueOf<T>(rawValue) }.getOrElse { defaultValue }
}

internal fun MutablePreferences.putStringOrRemove(
    key: PreferenceKey<String>,
    value: String,
) {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) {
        remove(key)
    } else {
        this[key] = trimmed
    }
}

internal fun Preferences.stringValue(key: PreferenceKey<String>): String {
    return this[key]?.trim().orEmpty()
}
