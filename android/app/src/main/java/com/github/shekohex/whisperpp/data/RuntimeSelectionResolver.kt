package com.github.shekohex.whisperpp.data

data class EffectiveRuntimeConfig(
    val sttProviderId: String,
    val sttModelId: String,
    val textProviderId: String,
    val textModelId: String,
    val prompt: String,
    val appendMode: AppendMode,
    val warnings: List<RuntimeWarning>,
)

object RuntimeSelectionResolver {
    fun resolve(
        packageName: String?,
        languageCode: String?,
        providers: List<ServiceProvider>,
        languageDefaults: List<LanguageProfile>,
        appMappings: List<AppPromptMapping>,
        globalSttProviderId: String,
        globalSttModelId: String,
        globalTextProviderId: String,
        globalTextModelId: String,
        basePrompt: String,
        profiles: List<PromptProfile>,
    ): EffectiveRuntimeConfig {
        val warnings = mutableListOf<RuntimeWarning>()
        val normalizedPackageName = packageName?.trim().orEmpty()
        val mapping = normalizedPackageName.takeIf { it.isNotBlank() }?.let { pkg ->
            appMappings.firstOrNull { it.packageName == pkg }
        }

        val normalizedLanguageCode = languageCode?.trim().orEmpty()
        val languageProfile = normalizedLanguageCode
            .takeIf { it.isNotBlank() && !it.equals("auto", ignoreCase = true) }
            ?.let { code -> languageDefaults.firstOrNull { it.languageCode.equals(code, ignoreCase = true) } }

        val resolvedStt = resolveChannel(
            channel = RuntimeChannel.STT,
            override = mapping?.sttOverride,
            language = SelectionIds(
                providerId = languageProfile?.transcriptionProviderId.orEmpty(),
                modelId = languageProfile?.transcriptionModelId.orEmpty(),
            ),
            global = SelectionIds(providerId = globalSttProviderId.trim(), modelId = globalSttModelId.trim()),
            providers = providers,
            allowedKinds = setOf(ModelKind.STT, ModelKind.MULTIMODAL),
            warnings = warnings,
        )

        val resolvedText = resolveChannel(
            channel = RuntimeChannel.TEXT,
            override = mapping?.textOverride,
            language = SelectionIds(
                providerId = languageProfile?.smartFixProviderId.orEmpty(),
                modelId = languageProfile?.smartFixModelId.orEmpty(),
            ),
            global = SelectionIds(providerId = globalTextProviderId.trim(), modelId = globalTextModelId.trim()),
            providers = providers,
            allowedKinds = setOf(ModelKind.TEXT, ModelKind.MULTIMODAL),
            warnings = warnings,
        )

        val appendMode = mapping?.appendMode ?: AppendMode.APPEND
        val promptParts = resolvePromptParts(mapping = mapping, profiles = profiles, warnings = warnings)
        val prompt = PromptComposer.compose(
            basePrompt = basePrompt,
            profileAppend = promptParts.profileAppend,
            appAppend = promptParts.appAppend,
            appendMode = appendMode,
        )

        return EffectiveRuntimeConfig(
            sttProviderId = resolvedStt.providerId,
            sttModelId = resolvedStt.modelId,
            textProviderId = resolvedText.providerId,
            textModelId = resolvedText.modelId,
            prompt = prompt,
            appendMode = appendMode,
            warnings = warnings.toList(),
        )
    }

    private data class SelectionIds(
        val providerId: String,
        val modelId: String,
    )

    private enum class SelectionSource {
        APP,
        LANGUAGE,
        GLOBAL,
    }

    private data class PromptParts(
        val profileAppend: String?,
        val appAppend: String?,
    )

    private fun resolvePromptParts(
        mapping: AppPromptMapping?,
        profiles: List<PromptProfile>,
        warnings: MutableList<RuntimeWarning>,
    ): PromptParts {
        val profileId = mapping?.profileId?.trim().orEmpty()
        if (profileId.isBlank()) {
            return PromptParts(profileAppend = null, appAppend = mapping?.appPromptAppend)
        }

        val profile = profiles.firstOrNull { it.id == profileId }
        if (profile == null) {
            warnings.add(
                RuntimeWarning(
                    kind = RuntimeWarningKind.MISSING_PROFILE,
                    message = "Missing prompt profile '$profileId'; using base prompt only.",
                )
            )
            return PromptParts(profileAppend = null, appAppend = null)
        }

        return PromptParts(profileAppend = profile.promptAppend, appAppend = mapping?.appPromptAppend)
    }

    private fun resolveChannel(
        channel: RuntimeChannel,
        override: ProviderModelOverride?,
        language: SelectionIds,
        global: SelectionIds,
        providers: List<ServiceProvider>,
        allowedKinds: Set<ModelKind>,
        warnings: MutableList<RuntimeWarning>,
    ): SelectionIds {
        val trimmedOverride = override?.let {
            ProviderModelOverride(providerId = it.providerId.trim(), modelId = it.modelId.trim())
        }?.takeIf { it.isPresent() }

        val languageIds = SelectionIds(providerId = language.providerId.trim(), modelId = language.modelId.trim())
        val globalIds = SelectionIds(providerId = global.providerId.trim(), modelId = global.modelId.trim())

        val candidates = buildList {
            if (trimmedOverride != null) {
                add(
                    SelectionCandidate(
                        source = SelectionSource.APP,
                        selection = SelectionIds(
                            providerId = firstNonBlank(trimmedOverride.providerId, languageIds.providerId, globalIds.providerId),
                            modelId = firstNonBlank(trimmedOverride.modelId, languageIds.modelId, globalIds.modelId),
                        ),
                    )
                )
            }

            if (languageIds.providerId.isNotBlank() || languageIds.modelId.isNotBlank()) {
                add(
                    SelectionCandidate(
                        source = SelectionSource.LANGUAGE,
                        selection = SelectionIds(
                            providerId = firstNonBlank(languageIds.providerId, globalIds.providerId),
                            modelId = firstNonBlank(languageIds.modelId, globalIds.modelId),
                        ),
                    )
                )
            }

            add(SelectionCandidate(source = SelectionSource.GLOBAL, selection = globalIds))
        }

        candidates.forEach { candidate ->
            val providerId = candidate.selection.providerId
            val modelId = candidate.selection.modelId

            if (providerId.isBlank() || modelId.isBlank()) {
                warnings.add(
                    RuntimeWarning(
                        kind = RuntimeWarningKind.MISSING_SELECTION,
                        channel = channel,
                        message = "${channel.name}: Missing provider/model selection in ${candidate.source.name.lowercase()} defaults.",
                    )
                )
                return@forEach
            }

            val provider = providers.firstOrNull { it.id == providerId }
            if (provider == null) {
                warnings.add(
                    RuntimeWarning(
                        kind = RuntimeWarningKind.INVALID_PROVIDER,
                        channel = channel,
                        message = "${channel.name}: Provider '$providerId' not found; falling back from ${candidate.source.name.lowercase()} defaults.",
                    )
                )
                return@forEach
            }

            val model = provider.models.firstOrNull { it.id == modelId }
            if (model == null) {
                warnings.add(
                    RuntimeWarning(
                        kind = RuntimeWarningKind.INVALID_MODEL,
                        channel = channel,
                        message = "${channel.name}: Model '$modelId' not found under provider '$providerId'; falling back from ${candidate.source.name.lowercase()} defaults.",
                    )
                )
                return@forEach
            }

            if (model.kind !in allowedKinds) {
                warnings.add(
                    RuntimeWarning(
                        kind = RuntimeWarningKind.INCOMPATIBLE_MODEL_KIND,
                        channel = channel,
                        message = "${channel.name}: Model '$modelId' kind '${model.kind}' is incompatible; falling back from ${candidate.source.name.lowercase()} defaults.",
                    )
                )
                return@forEach
            }

            return SelectionIds(providerId = providerId, modelId = modelId)
        }

        return SelectionIds(providerId = "", modelId = "")
    }

    private data class SelectionCandidate(
        val source: SelectionSource,
        val selection: SelectionIds,
    )

    private fun firstNonBlank(vararg values: String): String {
        values.forEach { value ->
            val trimmed = value.trim()
            if (trimmed.isNotBlank()) {
                return trimmed
            }
        }
        return ""
    }
}
