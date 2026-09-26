package com.lekhani.android.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the keyboard layout data model and Probaho layout definition.
 * Pure JVM — no Android framework required.
 */
class ProbahLayoutTest {

    private val layout = ProbahLayout.layout

    @Test
    fun `layout has exactly 3 main rows`() {
        assertEquals(3, layout.rows.size)
    }

    @Test
    fun `each main row has exactly 10 keys`() {
        layout.rows.forEach { row ->
            assertEquals("Row should have 10 keys", 10, row.size)
        }
    }

    @Test
    fun `spacebar row has 7 keys`() {
        assertEquals(7, layout.spacebarRow.size)
    }

    @Test
    fun `home row flag is only set on row 2`() {
        // Row 0 (top) — no home row keys
        layout.rows[0].forEach { key ->
            assertFalse("Row 0 key '${key.label}' should not be home row", key.isHomeRow)
        }
        // Row 1 (home) — ALL keys are home row
        layout.rows[1].forEach { key ->
            assertTrue("Row 1 key '${key.label}' should be home row", key.isHomeRow)
        }
        // Row 2 (bottom) — no home row keys
        layout.rows[2].forEach { key ->
            assertFalse("Row 2 key '${key.label}' should not be home row", key.isHomeRow)
        }
    }

    @Test
    fun `row 1 contains base vowel kars on left half`() {
        val homeRow = layout.rows[1]
        // Left 5 keys: অ া ি ু ে
        assertEquals("অ", homeRow[0].label)
        assertEquals("া", homeRow[1].label)
        assertEquals("ি", homeRow[2].label)
        assertEquals("ু", homeRow[3].label)
        assertEquals("ে", homeRow[4].label)
    }

    @Test
    fun `row 1 contains golden 5 consonants on right half`() {
        val homeRow = layout.rows[1]
        // Right 5 keys: র ত ন স ক
        assertEquals("র", homeRow[5].label)
        assertEquals("ত", homeRow[6].label)
        assertEquals("ন", homeRow[7].label)
        assertEquals("স", homeRow[8].label)
        assertEquals("ক", homeRow[9].label)
    }

    @Test
    fun `shift key is first in bottom row with widthWeight gt 1`() {
        val shiftKey = layout.rows[2].first()
        assertEquals(KeyAction.Shift, shiftKey.action)
        assertTrue(shiftKey.widthWeight > 1.0f)
    }

    @Test
    fun `backspace key is last in bottom row with widthWeight gt 1`() {
        val backspace = layout.rows[2].last()
        assertEquals(KeyAction.Backspace, backspace.action)
        assertTrue(backspace.widthWeight > 1.0f)
    }

    @Test
    fun `spacebar has largest width weight in spacebar row`() {
        val space = layout.spacebarRow.find { it.action == KeyAction.Space }
        assertNotNull("Spacebar key should exist", space)
        val maxWeight = layout.spacebarRow.maxOf { it.widthWeight }
        assertEquals(space!!.widthWeight, maxWeight, 0.01f)
    }

    @Test
    fun `hasanta key is in spacebar row and has correct label`() {
        val hasanta = layout.spacebarRow.find { it.label == "্" }
        assertNotNull("Hasanta key should be in spacebar row", hasanta)
        assertEquals(KeyAction.Character("্"), hasanta!!.action)
    }

    @Test
    fun `ZWJ is on shift layer of nasal modifier key`() {
        // ZWJ (\u200D) should be the shifted action of ঁ (4th key in bottom row after shift key)
        val nasalKey = layout.rows[2].find { it.label == "ঁ" }
        assertNotNull("ঁ key should exist", nasalKey)
        assertEquals(KeyAction.Character("\u200D"), nasalKey!!.shiftedAction)
    }

    @Test
    fun `ZWNJ is on shift layer of visarga key`() {
        // ZWNJ (\u200C) should be the shifted action of ঃ
        val visargaKey = layout.rows[2].find { it.label == "ঃ" }
        assertNotNull("ঃ key should exist", visargaKey)
        assertEquals(KeyAction.Character("\u200C"), visargaKey!!.shiftedAction)
    }

    @Test
    fun `long vowels sit above their short partners in top row vs home row`() {
        // ী (long I) is top row position 2; ি (short I) is home row position 2
        assertEquals("ী", layout.rows[0][2].label)
        assertEquals("ি", layout.rows[1][2].label)
        // ূ (long U) is top row position 3; ু (short U) is home row position 3
        assertEquals("ূ", layout.rows[0][3].label)
        assertEquals("ু", layout.rows[1][3].label)
    }

    @Test
    fun `key displayLabel respects shift state`() {
        val kavargaKey = layout.rows[1][9]  // ক / খ
        assertEquals("ক", kavargaKey.displayLabel(shifted = false))
        assertEquals("খ", kavargaKey.displayLabel(shifted = true))
    }

    @Test
    fun `key activeAction respects shift state`() {
        val kavargaKey = layout.rows[1][9]  // ক / খ
        assertEquals(KeyAction.Character("ক"), kavargaKey.activeAction(shifted = false))
        assertEquals(KeyAction.Character("খ"), kavargaKey.activeAction(shifted = true))
    }

    @Test
    fun `ri-kar is accessible on shifted oi and as hint on o`() {
        val oiKey = layout.rows[0].find { it.label == "ৈ" }
        assertNotNull("ৈ key should exist", oiKey)
        assertEquals("ৃ", oiKey!!.shiftedLabel)
        assertEquals(KeyAction.Character("ৃ"), oiKey.shiftedAction)

        val oKey = layout.rows[1].find { it.label == "অ" }
        assertNotNull("অ key should exist", oKey)
        assertEquals("ৃ", oKey!!.hintLabel)
    }

    @Test
    fun `all character keys have non-empty labels`() {
        (layout.rows.flatten() + layout.spacebarRow).forEach { key ->
            assertTrue("Key label must not be blank: ${key.label}", key.label.isNotBlank())
        }
    }
}
