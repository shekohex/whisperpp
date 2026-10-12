package com.github.shekohex.whisperpp.domain.analytics

import com.github.shekohex.whisperpp.domain.analytics.MomentumCopy.Variant
import org.junit.Assert.assertEquals
import org.junit.Test

class MomentumCopyTest {
    @Test
    fun picksVariantFromLastSevenDays() {
        assertEquals(Variant.GET_STARTED, MomentumCopy.pick(0, List(7) { 0 }))
        assertEquals(Variant.WELCOME_BACK, MomentumCopy.pick(12, List(7) { 0 }))
        assertEquals(Variant.KEEP_GOING, MomentumCopy.pick(12, listOf(0, 0, 4, 0, 0, 1, 0)))
        assertEquals(Variant.ON_A_ROLL, MomentumCopy.pick(12, listOf(1, 0, 2, 0, 0, 1, 0)))
    }

    @Test
    fun onlyTheLastSevenEntriesCount() {
        assertEquals(Variant.WELCOME_BACK, MomentumCopy.pick(5, listOf(3, 3, 3) + List(7) { 0 }))
    }
}
