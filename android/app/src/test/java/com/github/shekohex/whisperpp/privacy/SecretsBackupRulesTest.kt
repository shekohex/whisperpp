package com.github.shekohex.whisperpp.privacy

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class SecretsBackupRulesTest {

    @Test
    fun secretsPrefs_areExcludedFromFullBackup() {
        val root = parse("backup_rules.xml")

        assertTrue(hasSecretsExclude(root))
    }

    @Test
    fun secretsPrefs_areExcludedFromCloudBackupAndDeviceTransfer() {
        val root = parse("data_extraction_rules.xml")

        listOf("cloud-backup", "device-transfer").forEach { section ->
            val sectionElement = root.getElementsByTagName(section).item(0) as? Element
            assertTrue("Missing <$section>", sectionElement != null)
            assertTrue("Secrets not excluded in <$section>", hasSecretsExclude(sectionElement!!))
        }
    }

    private fun hasSecretsExclude(parent: Element): Boolean {
        val excludes = parent.getElementsByTagName("exclude")
        return (0 until excludes.length)
            .map { excludes.item(it) as Element }
            .any { it.getAttribute("domain") == "sharedpref" && it.getAttribute("path") == SECRETS_PREFS_FILE }
    }

    private fun parse(fileName: String): Element {
        val file = listOf("src/main/res/xml/$fileName", "app/src/main/res/xml/$fileName")
            .map(::File)
            .firstOrNull(File::exists)
            ?: error("Expected $fileName to exist")
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
    }

    private companion object {
        const val SECRETS_PREFS_FILE = "whisperpp_secrets.xml"
    }
}
