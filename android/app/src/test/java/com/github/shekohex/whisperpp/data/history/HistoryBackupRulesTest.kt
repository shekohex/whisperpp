package com.github.shekohex.whisperpp.data.history

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Kept audio and the history database never go to Android auto backup / device transfer. */
class HistoryBackupRulesTest {
    @Test
    fun historyAudioAndDatabase_areExcludedFromBothRuleFiles() {
        listOf("backup_rules.xml", "data_extraction_rules.xml").forEach { name ->
            val rules = listOf("src/main/res/xml/$name", "app/src/main/res/xml/$name")
                .map(::File)
                .first(File::exists)
                .readText()
            assertTrue(name, rules.contains("""domain="file" path="${HistoryAudioStore.DIR_NAME}/""""))
            assertTrue(name, rules.contains("""domain="database" path="$HISTORY_DATABASE_NAME""""))
        }
    }
}
