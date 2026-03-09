package com.github.shekohex.whisperpp.analytics

import com.github.shekohex.whisperpp.data.SETTINGS_BACKUP_CATEGORY_MANIFEST
import com.github.shekohex.whisperpp.data.SettingsBackupPayload
import com.google.gson.Gson
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AnalyticsPrivacyTest {

    private val gson = Gson()

    @Test
    fun analyticsStorePath_isExcludedFromBothBackupRuleFiles() {
        val backupRules = resolveProjectFile("src/main/res/xml/backup_rules.xml", "app/src/main/res/xml/backup_rules.xml").readText()
        val extractionRules = resolveProjectFile("src/main/res/xml/data_extraction_rules.xml", "app/src/main/res/xml/data_extraction_rules.xml").readText()

        assertTrue(backupRules.contains(ANALYTICS_STORE_PATH))
        assertTrue(extractionRules.contains(ANALYTICS_STORE_PATH))
    }

    @Test
    fun analyticsDoesNotAppearInBackupManifestOrExportPayload() {
        val manifestIds = SETTINGS_BACKUP_CATEGORY_MANIFEST.map { it.id }
        val payloadJson = gson.toJson(SettingsBackupPayload())

        assertFalse(manifestIds.any { it.contains("analytics", ignoreCase = true) })
        assertFalse(payloadJson.contains("analytics", ignoreCase = true))
    }

    private fun resolveProjectFile(vararg candidates: String): File {
        return candidates
            .map(::File)
            .firstOrNull(File::exists)
            ?: error("Expected one of ${candidates.joinToString()} to exist")
    }
}
