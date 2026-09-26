package com.lekhani.android.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Number & Symbol Layout Verification Tests
 */
class NumberSymbolsLayoutTest {

    @Test
    fun testNumericLayoutStructure() {
        val layout = NumberSymbolsLayout.numericLayout
        assertEquals("Numbers & Symbols", layout.name)
        assertEquals(3, layout.rows.size)

        // Row 1: 1..0
        val row1 = layout.rows[0]
        assertEquals(10, row1.size)
        assertEquals("1", row1[0].label)
        assertEquals("0", row1[9].label)
        assertEquals("১", row1[0].hintLabel)

        // Row 2: Symbols
        val row2 = layout.rows[1]
        assertEquals(10, row2.size)
        assertTrue(row2.any { it.label == "৳" })
        assertTrue(row2.any { it.label == "@" })

        // Row 3: More symbols toggle and backspace
        val row3 = layout.rows[2]
        assertEquals("=\\<", row3[0].label)
        assertEquals(KeyAction.SwitchMoreSymbols, row3[0].action)
        assertEquals(KeyAction.Backspace, row3.last().action)

        // Spacebar row: ABC, ১২৩, Comma, Space, Period, Enter
        val spaceRow = layout.spacebarRow
        assertEquals(KeyAction.SwitchAlpha, spaceRow[0].action)
        assertEquals(KeyAction.ToggleBengaliDigits, spaceRow[1].action)
        assertEquals(KeyAction.Space, spaceRow[3].action)
    }

    @Test
    fun testBengaliNumericLayoutStructure() {
        val layout = NumberSymbolsLayout.bengaliNumericLayout
        assertEquals("Bengali Numbers", layout.name)
        val row1 = layout.rows[0]
        assertEquals("১", row1[0].label)
        assertEquals("০", row1[9].label)
        assertEquals("1", row1[0].hintLabel)
    }

    @Test
    fun testMoreSymbolsLayoutStructure() {
        val layout = NumberSymbolsLayout.moreSymbolsLayout
        assertEquals("More Symbols", layout.name)
        val row1 = layout.rows[0]
        assertEquals(10, row1.size)
        assertTrue(row1.any { it.label == "~" })
        assertTrue(row1.any { it.label == "{" })

        val row2 = layout.rows[1]
        assertTrue(row2.any { it.label == "€" })
        assertTrue(row2.any { it.label == "¥" })

        // Row 3 switches back to ?123
        val row3 = layout.rows[2]
        assertEquals("?123", row3[0].label)
        assertEquals(KeyAction.SwitchNumeric, row3[0].action)
    }
}
