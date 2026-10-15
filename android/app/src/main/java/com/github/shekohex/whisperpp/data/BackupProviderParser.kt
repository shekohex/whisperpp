package com.github.shekohex.whisperpp.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject

/** Decodes the `providersModels` backup category, tolerating pre-v3 model shapes. */
internal object BackupProviderParser {
    fun parseProvidersModelsCategory(
        root: JsonObject,
        availableCategoryIds: MutableSet<String>,
        skippedItems: MutableList<SkippedImportItem>,
    ): ProvidersModelsBackup {
        val category = root.readCategoryObject("providersModels", SETTINGS_BACKUP_CATEGORY_PROVIDERS_MODELS, availableCategoryIds, skippedItems)
            ?: return ProvidersModelsBackup()
        val providersElement = category.get("providers")
        if (providersElement == null || providersElement.isJsonNull) {
            return ProvidersModelsBackup()
        }
        if (!providersElement.isJsonArray) {
            skippedItems += SkippedImportItem(
                categoryId = SETTINGS_BACKUP_CATEGORY_PROVIDERS_MODELS,
                reason = "Expected providers array.",
            )
            return ProvidersModelsBackup()
        }

        val providers = mutableListOf<ServiceProvider>()
        providersElement.asJsonArray.forEachIndexed { index, element ->
            parseProvider(element, index, skippedItems)?.let(providers::add)
        }
        return ProvidersModelsBackup(providers = providers)
    }

    private fun parseProvider(
        element: JsonElement,
        index: Int,
        skippedItems: MutableList<SkippedImportItem>,
    ): ServiceProvider? {
        if (!element.isJsonObject) {
            skippedItems += SkippedImportItem(
                categoryId = SETTINGS_BACKUP_CATEGORY_PROVIDERS_MODELS,
                itemKey = "provider[$index]",
                reason = "Expected provider object.",
            )
            return null
        }

        val providerObject = element.asJsonObject
        val id = providerObject.stringValue("id").trim()
        val name = providerObject.stringValue("name").trim()
        val endpoint = providerObject.stringValue("endpoint").trim()
        if (id.isBlank() || name.isBlank() || endpoint.isBlank()) {
            skippedItems += SkippedImportItem(
                categoryId = SETTINGS_BACKUP_CATEGORY_PROVIDERS_MODELS,
                itemKey = "provider[$index]",
                reason = "Provider is missing id, name, or endpoint.",
            )
            return null
        }

        val type = providerObject.enumValue("type", ProviderType.CUSTOM)
        val authMode = providerObject.enumValue(
            "authMode",
            if (type == ProviderType.WHISPER_ASR) ProviderAuthMode.NO_AUTH else ProviderAuthMode.API_KEY,
        )
        val models = parseProviderModels(
            providerId = id,
            providerType = type,
            endpoint = endpoint,
            modelsElement = providerObject.get("models"),
            skippedItems = skippedItems,
        )

        return ServiceProvider(
            id = id,
            name = name,
            type = type,
            endpoint = endpoint,
            authMode = authMode,
            models = models,
            temperature = providerObject.numberValue("temperature")?.toFloat() ?: 0f,
            prompt = providerObject.stringValue("prompt"),
            languageCode = providerObject.stringValue("languageCode").ifBlank { "auto" },
            timeout = providerObject.numberValue("timeout")?.toInt() ?: 10000,
            thinkingEnabled = providerObject.booleanValue("thinkingEnabled") ?: false,
            thinkingType = providerObject.enumValue("thinkingType", ThinkingType.LEVEL),
            thinkingBudget = providerObject.numberValue("thinkingBudget")?.toInt() ?: 4096,
            thinkingLevel = providerObject.stringValue("thinkingLevel").ifBlank { "medium" },
        )
    }

    private fun parseProviderModels(
        providerId: String,
        providerType: ProviderType,
        endpoint: String,
        modelsElement: JsonElement?,
        skippedItems: MutableList<SkippedImportItem>,
    ): List<ModelConfig> {
        if (modelsElement == null || modelsElement.isJsonNull) {
            return emptyList()
        }
        if (!modelsElement.isJsonArray) {
            skippedItems += SkippedImportItem(
                categoryId = SETTINGS_BACKUP_CATEGORY_PROVIDERS_MODELS,
                itemKey = providerId,
                reason = "Provider models must be an array.",
            )
            return emptyList()
        }

        val models = mutableListOf<ModelConfig>()
        modelsElement.asJsonArray.forEachIndexed { index, modelElement ->
            if (!modelElement.isJsonObject) {
                skippedItems += SkippedImportItem(
                    categoryId = SETTINGS_BACKUP_CATEGORY_PROVIDERS_MODELS,
                    itemKey = "$providerId:model[$index]",
                    reason = "Expected model object.",
                )
                return@forEachIndexed
            }
            val modelObject = modelElement.asJsonObject
            val modelId = modelObject.stringValue("id").trim()
            val modelName = modelObject.stringValue("name").trim()
            if (modelId.isBlank() || modelName.isBlank()) {
                skippedItems += SkippedImportItem(
                    categoryId = SETTINGS_BACKUP_CATEGORY_PROVIDERS_MODELS,
                    itemKey = "$providerId:model[$index]",
                    reason = "Model is missing id or name.",
                )
                return@forEachIndexed
            }
            models += ModelConfig(
                id = modelId,
                name = modelName,
                capabilities = parseModelCapabilities(modelObject, providerType, endpoint),
                timeoutMs = modelObject.numberValue("timeoutMs")?.toInt()?.takeIf { it > 0 },
            )
        }
        return models
    }

    /** Backups from before schema v3 carry only the legacy kind/flags. */
    private fun parseModelCapabilities(
        modelObject: JsonObject,
        providerType: ProviderType,
        endpoint: String,
    ): ModelCapabilities {
        val capabilities = modelObject.get("capabilities")
        if (capabilities != null && capabilities.isJsonObject) {
            val obj = capabilities.asJsonObject
            return ModelCapabilities(
                stt = obj.booleanValue("stt") ?: false,
                text = obj.booleanValue("text") ?: false,
                realtime = obj.booleanValue("realtime") ?: false,
                thinking = obj.booleanValue("thinking") ?: false,
                vocabulary = obj.booleanValue("vocabulary") ?: false,
            )
        }
        return ModelCapabilities.fromLegacy(
            kind = modelObject.enumValue("kind", inferModelKind(providerType, endpoint)),
            isThinking = modelObject.booleanValue("isThinking") ?: false,
            streamingPartialsSupported = modelObject.booleanValue("streamingPartialsSupported") ?: false,
            providerType = providerType,
            modelId = modelObject.stringValue("id"),
        )
    }

    private fun inferModelKind(
        providerType: ProviderType,
        endpoint: String,
    ): ModelKind {
        return when {
            providerType == ProviderType.WHISPER_ASR -> ModelKind.STT
            providerType == ProviderType.GEMINI -> ModelKind.MULTIMODAL
            endpoint.contains("/audio/transcriptions") -> ModelKind.STT
            else -> ModelKind.TEXT
        }
    }
}
