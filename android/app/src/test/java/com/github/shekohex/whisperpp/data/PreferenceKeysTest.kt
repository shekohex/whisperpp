package com.github.shekohex.whisperpp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferenceKeysTest {

    @Test
    fun settingsKeyNames_areUnique() {
        assertNoDuplicates(PreferenceKeys.SETTINGS.map { it.name })
    }

    @Test
    fun analyticsKeyNames_areUnique() {
        assertNoDuplicates(PreferenceKeys.Analytics.ALL.map { it.name })
    }

    @Test
    fun updaterKeyNames_areUnique() {
        assertNoDuplicates(PreferenceKeys.Updater.ALL.map { it.name })
    }

    @Test
    fun keyNames_areUniqueAcrossStores() {
        val all = PreferenceKeys.SETTINGS + PreferenceKeys.Analytics.ALL + PreferenceKeys.Updater.ALL
        assertNoDuplicates(all.map { it.name })
    }

    @Test
    fun keyLists_containEveryDeclaredKey() {
        assertEquals(declaredKeyCount(PreferenceKeys), PreferenceKeys.SETTINGS.size)
        assertEquals(declaredKeyCount(PreferenceKeys.Analytics), PreferenceKeys.Analytics.ALL.size)
        assertEquals(declaredKeyCount(PreferenceKeys.Updater), PreferenceKeys.Updater.ALL.size)
    }

    @Test
    fun persistedKeyNames_areUnchanged() {
        assertEquals("providers_json", PreferenceKeys.PROVIDERS_JSON.name)
        assertEquals("active-stt-provider-id", PreferenceKeys.ACTIVE_STT_PROVIDER_ID.name)
        assertEquals("is-auto-recording-start", PreferenceKeys.AUTO_RECORDING_START.name)
        assertEquals("verbose-network-logs-enabled", PreferenceKeys.VERBOSE_NETWORK_LOGS_ENABLED.name)
        assertEquals("analytics_daily_buckets_json", PreferenceKeys.Analytics.DAILY_BUCKETS_JSON.name)
        assertEquals("download_id", PreferenceKeys.Updater.DOWNLOAD_ID.name)
    }

    private fun assertNoDuplicates(names: List<String>) {
        val duplicates = names.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        assertTrue("Duplicate preference key names: $duplicates", duplicates.isEmpty())
    }

    private fun declaredKeyCount(holder: Any): Int {
        return holder.javaClass.declaredFields.count { field ->
            field.type == PreferenceKey::class.java
        }
    }
}
