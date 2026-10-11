package com.github.shekohex.whisperpp.dictation

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusIdentityTest {

    @Test
    fun freshStartInputMintsNewId() {
        val identity = FocusIdentity()

        val first = identity.onStartInput(restarting = false)
        val second = identity.onStartInput(restarting = false)

        assertEquals(1L, first)
        assertEquals(2L, second)
    }

    @Test
    fun restartOfSameEditorKeepsId() {
        val identity = FocusIdentity()
        val id = identity.onStartInput(restarting = false)

        val restarted = identity.onStartInput(restarting = true)

        assertEquals(id, restarted)
        assertEquals(id, identity.currentId)
    }

    @Test
    fun restartWithoutPriorEditorStillMintsId() {
        val identity = FocusIdentity()

        val id = identity.onStartInput(restarting = true)

        assertEquals(1L, id)
    }
}
