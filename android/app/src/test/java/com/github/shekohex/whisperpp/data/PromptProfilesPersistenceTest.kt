package com.github.shekohex.whisperpp.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptProfilesPersistenceTest {

    private val gson = Gson()

    @Test
    fun sanitizeAppPromptMappings_dropsBlankPackageNames_andTrimsFields() {
        val result = sanitizeAppPromptMappings(
            listOf(
                AppPromptMapping(packageName = "  ", profileId = "p1"),
                AppPromptMapping(
                    packageName = " com.example.app ",
                    profileId = " profile ",
                    appPromptAppend = "  hello ",
                    sttOverride = ProviderModelOverride(providerId = " p ", modelId = " m "),
                ),
            )
        )

        assertEquals(1, result.size)
        assertEquals("com.example.app", result[0].packageName)
        assertEquals("profile", result[0].profileId)
        assertEquals("hello", result[0].appPromptAppend)
        assertEquals("p", result[0].sttOverride?.providerId)
        assertEquals("m", result[0].sttOverride?.modelId)
    }

    @Test
    fun sanitizePromptProfiles_dropsBlankIdsOrNames_andTrimsFields() {
        val result = sanitizePromptProfiles(
            listOf(
                PromptProfile(id = " ", name = "Name", promptAppend = "A"),
                PromptProfile(id = "id", name = " ", promptAppend = "A"),
                PromptProfile(id = "  p1 ", name = "  Profile 1 ", promptAppend = "  APPEND  "),
            )
        )

        assertEquals(1, result.size)
        assertEquals("p1", result[0].id)
        assertEquals("Profile 1", result[0].name)
        assertEquals("APPEND", result[0].promptAppend)
    }

    @Test
    fun sanitizeAppPromptMappings_keepsOrphanProfileReference() {
        val mappings = sanitizeAppPromptMappings(
            listOf(
                AppPromptMapping(
                    packageName = "com.example.app",
                    profileId = "missing-profile",
                )
            )
        )

        assertEquals(1, mappings.size)
        assertEquals("missing-profile", mappings[0].profileId)
    }

    @Test
    fun parsePromptProfilesJson_invalidJson_returnsEmptyList() {
        val result = parsePromptProfilesJson("not-json", gson)
        assertTrue(result.isEmpty())
    }

    @Test
    fun parseAppPromptMappingsJson_invalidJson_returnsEmptyList() {
        val result = parseAppPromptMappingsJson("not-json", gson)
        assertTrue(result.isEmpty())
    }
}
