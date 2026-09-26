package com.lekhani.android.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying National (জাতীয়) layout completeness and independent vowel hints.
 */
class NationalLayoutTest {

    private val layout = NationalLayout.layout

    @Test
    fun `national layout has 3 main rows and spacebar row`() {
        assertEquals(3, layout.rows.size)
        assertEquals(6, layout.spacebarRow.size)
    }

    @Test
    fun `national layout has hasanta and linker keys`() {
        val row2 = layout.rows[1]
        val hasantaKey = row2.find { it.label == "্" }
        assertNotNull("Hasanta key must be present in Home Row", hasantaKey)
        assertEquals("।", hasantaKey?.shiftedLabel)
    }

    @Test
    fun `national layout has independent vowel hints and long-press shortcuts`() {
        val allKeys = layout.rows.flatten() + layout.spacebarRow

        val riKarKey = allKeys.find { it.label == "ৃ" }
        assertNotNull(riKarKey)
        assertEquals("ঋ", riKarKey?.hintLabel)
        assertEquals(KeyAction.Character("ঋ"), riKarKey?.longPressAction)

        val uKarKey = allKeys.find { it.label == "ু" }
        assertNotNull(uKarKey)
        assertEquals("উ", uKarKey?.hintLabel)
        assertEquals(KeyAction.Character("উ"), uKarKey?.longPressAction)

        val iKarKey = allKeys.find { it.label == "ি" }
        assertNotNull(iKarKey)
        assertEquals("ই", iKarKey?.hintLabel)
        assertEquals(KeyAction.Character("ই"), iKarKey?.longPressAction)

        val oKarKey = allKeys.find { it.label == "ো" }
        assertNotNull(oKarKey)
        assertEquals("ও", oKarKey?.hintLabel)
        assertEquals(KeyAction.Character("ও"), oKarKey?.longPressAction)

        val eKarKey = allKeys.find { it.label == "ে" }
        assertNotNull(eKarKey)
        assertEquals("এ", eKarKey?.hintLabel)
        assertEquals(KeyAction.Character("এ"), eKarKey?.longPressAction)
    }

    @Test
    fun `dari key provides double dari shortcut`() {
        val dariKey = layout.spacebarRow.find { it.label == "।" }
        assertNotNull(dariKey)
        assertEquals("॥", dariKey?.hintLabel)
        assertEquals(KeyAction.Character("॥"), dariKey?.longPressAction)
    }
}
