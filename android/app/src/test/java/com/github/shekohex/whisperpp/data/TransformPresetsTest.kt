package com.github.shekohex.whisperpp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransformPresetsTest {

    @Test
    fun presets_hasAtLeastThree_andContainsRequiredIds() {
        assertTrue(TRANSFORM_PRESETS.size >= 3)
        val ids = TRANSFORM_PRESETS.map { it.id }.toSet()
        assertTrue(ids.contains(TRANSFORM_PRESET_ID_CLEANUP))
        assertTrue(ids.contains(TRANSFORM_PRESET_ID_SHORTEN))
        assertTrue(ids.contains(TRANSFORM_PRESET_ID_TONE_REWRITE))
    }

    @Test
    fun presets_idsAreUnique() {
        val ids = TRANSFORM_PRESETS.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun presetById_unknownReturnsNull_knownReturnsPreset() {
        assertNull(presetById("unknown"))
        assertNotNull(presetById(TRANSFORM_PRESET_ID_CLEANUP))
        assertNotNull(presetById(TRANSFORM_PRESET_ID_SHORTEN))
        assertNotNull(presetById(TRANSFORM_PRESET_ID_TONE_REWRITE))
    }
}
