package com.github.shekohex.whisperpp.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseMetadataParserTest {
    private val platform = """{"signature":"sha256:abc","url":"https://example.com/a.apk"}"""

    private fun valid(release: ReleaseMetadataParser.Outcome): LatestRelease {
        assertTrue("expected Valid but was $release", release is ReleaseMetadataParser.Outcome.Valid)
        return (release as ReleaseMetadataParser.Outcome.Valid).release
    }

    private fun assertInvalid(json: String) {
        val outcome = ReleaseMetadataParser.parse(json)
        assertTrue("expected Invalid for $json but was $outcome", outcome is ReleaseMetadataParser.Outcome.Invalid)
        val error = (outcome as ReleaseMetadataParser.Outcome.Invalid).error
        assertTrue(!error.isTransient)
    }

    @Test
    fun validDocumentIsParsed() {
        val release = valid(
            ReleaseMetadataParser.parse(
                """{"version":"1.2.0","version_code":12,"pub_date":"2026-03-04T10:11:12.123Z",
                    "changelog":"notes","platforms":{"arm64-v8a":$platform}}"""
            )
        )
        assertEquals("1.2.0", release.version)
        assertEquals(12, release.versionCode)
        assertEquals("2026-03-04", release.pubDate)
        assertEquals("notes", release.changelog)
        assertEquals("https://example.com/a.apk", release.platforms.getValue("arm64-v8a").url)
    }

    @Test
    fun missingPubDateIsAllowed() {
        val release = valid(
            ReleaseMetadataParser.parse("""{"version":"1","version_code":2,"platforms":{"universal":$platform}}""")
        )
        assertNull(release.pubDate)
    }

    @Test
    fun nullAndGarbagePubDateFallBackToNull() {
        for (raw in listOf("null", "\"\"", "\"yesterday\"", "42", "{}")) {
            val release = valid(
                ReleaseMetadataParser.parse(
                    """{"version":"1","version_code":2,"pub_date":$raw,"platforms":{"universal":$platform}}"""
                )
            )
            assertNull("pub_date=$raw", release.pubDate)
        }
    }

    @Test
    fun missingOrEmptyPlatformsAreRejected() {
        assertInvalid("""{"version":"1","version_code":2}""")
        assertInvalid("""{"version":"1","version_code":2,"platforms":null}""")
        assertInvalid("""{"version":"1","version_code":2,"platforms":{}}""")
        assertInvalid("""{"version":"1","version_code":2,"platforms":[]}""")
    }

    @Test
    fun unusablePlatformEntriesAreDroppedAndAllBadIsRejected() {
        val mixed = valid(
            ReleaseMetadataParser.parse(
                """{"version":"1","version_code":2,"platforms":{
                    "x86":null,"x86_64":{"url":"https://e.com/x"},"arm64-v8a":$platform,
                    "armeabi-v7a":{"signature":"s","url":"http://insecure/x.apk"}}}"""
            )
        )
        assertEquals(setOf("arm64-v8a"), mixed.platforms.keys)
        assertInvalid("""{"version":"1","version_code":2,"platforms":{"x86":null}}""")
    }

    @Test
    fun missingRequiredFieldsAreRejected() {
        assertInvalid("""{"version_code":2,"platforms":{"universal":$platform}}""")
        assertInvalid("""{"version":"  ","version_code":2,"platforms":{"universal":$platform}}""")
        assertInvalid("""{"version":"1","platforms":{"universal":$platform}}""")
        assertInvalid("""{"version":"1","version_code":"abc","platforms":{"universal":$platform}}""")
        assertInvalid("""{"version":"1","version_code":0,"platforms":{"universal":$platform}}""")
    }

    @Test
    fun malformedDocumentsAreRejected() {
        assertInvalid("")
        assertInvalid("not json {")
        assertInvalid("[]")
        assertInvalid("null")
        assertInvalid("\"text\"")
    }

    @Test
    fun normalizeDate() {
        assertEquals("2026-01-02", ReleaseMetadataParser.normalizeDate("2026-01-02"))
        assertEquals("2026-01-02", ReleaseMetadataParser.normalizeDate(" 2026-01-02T03:04:05Z "))
        assertNull(ReleaseMetadataParser.normalizeDate(null))
        assertNull(ReleaseMetadataParser.normalizeDate("02/01/2026"))
    }
}
