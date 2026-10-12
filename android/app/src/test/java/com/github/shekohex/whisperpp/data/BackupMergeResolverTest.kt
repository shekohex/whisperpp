package com.github.shekohex.whisperpp.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupMergeResolverTest {
    private fun provider(id: String, name: String) = ServiceProvider(
        id = id,
        name = name,
        type = ProviderType.CUSTOM,
        endpoint = "https://example.com",
        models = emptyList(),
    )

    private fun payload(vararg providers: ServiceProvider) = SettingsBackupPayload(
        providersModels = ProvidersModelsBackup(providers.toList()),
    )

    @Test
    fun mergeKeepsCurrentAndLetsImportedWinPerId() {
        val current = payload(provider("a", "Current A"), provider("b", "Current B"))
        val imported = payload(provider("b", "Imported B"), provider("c", "Imported C"))

        val resolved = BackupMergeResolver.resolve(
            current = current,
            imported = imported,
            availableCategoryIds = setOf(SETTINGS_BACKUP_CATEGORY_PROVIDERS_MODELS),
            restoreMode = RestoreMode.MERGE,
        )

        assertEquals(
            listOf("Current A" to "a", "Imported B" to "b", "Imported C" to "c"),
            resolved.providersModels.providers.map { it.name to it.id },
        )
    }

    @Test
    fun overwriteReplacesWithImported() {
        val current = payload(provider("a", "Current A"))
        val imported = payload(provider("c", "Imported C"))

        val resolved = BackupMergeResolver.resolve(
            current = current,
            imported = imported,
            availableCategoryIds = setOf(SETTINGS_BACKUP_CATEGORY_PROVIDERS_MODELS),
            restoreMode = RestoreMode.OVERWRITE,
        )

        assertEquals(listOf("c"), resolved.providersModels.providers.map { it.id })
    }

    @Test
    fun unavailableCategoryKeepsCurrent() {
        val current = payload(provider("a", "Current A"))
        val imported = payload(provider("c", "Imported C"))

        val resolved = BackupMergeResolver.resolve(
            current = current,
            imported = imported,
            availableCategoryIds = emptySet(),
            restoreMode = RestoreMode.OVERWRITE,
        )

        assertEquals(listOf("a"), resolved.providersModels.providers.map { it.id })
    }
}
