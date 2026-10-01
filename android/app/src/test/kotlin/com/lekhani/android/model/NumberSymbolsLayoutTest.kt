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
        assertTrue(row2.any { it.label == "$" })
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

        // Spacebar row must have English digits toggle ('123') to avoid keyboard trap
        val toggleKey = layout.spacebarRow.find { it.action == KeyAction.ToggleBengaliDigits }
        assertNotNull("123 toggle key must exist in Bengali numeric layout", toggleKey)
        assertEquals("123", toggleKey?.label)
    }

    @Test
    fun testMoreSymbolsLayoutStructure() {
        val layout = NumberSymbolsLayout.moreSymbolsLayout
        assertEquals("More Symbols", layout.name)
        val row1 = layout.rows[0]
        assertEquals(10, row1.size)
        assertTrue(row1.any { it.label == "~" })
        assertTrue(row1.any { it.label == "{" })
        assertTrue(row1.any { it.label == "৲" })

        val row2 = layout.rows[1]
        assertTrue(row2.any { it.label == "€" })
        assertTrue(row2.any { it.label == "£" })

        // Row 3 switches back to ?123, has cursor left AND right, and 10 total keys
        val row3 = layout.rows[2]
        assertEquals(10, row3.size)
        assertEquals("?123", row3[0].label)
        assertEquals(KeyAction.SwitchNumeric, row3[0].action)
        assertEquals(KeyAction.CursorLeft, row3[1].action)
        assertEquals(KeyAction.CursorRight, row3[2].action)
        assertTrue(row3.any { it.label == "©" })
        assertTrue(row3.any { it.label == "®" })
    }

    @Test
    fun testProbhatSpacebarRowConsistency() {
        val probhat = ProbhatLayout.layout
        val globeKey = probhat.spacebarRow.find { it.action == KeyAction.SwitchLayout }
        assertNotNull("Globe switch layout key must be present in Probhat layout", globeKey)
        assertEquals("🌐", globeKey?.label)
    }

    @Test
    fun testBengaliTypographicalGlyphs() {
        val moreLayout = NumberSymbolsLayout.moreSymbolsLayout
        val takaSign = moreLayout.rows[0].find { it.label == "৲" }
        assertNotNull(takaSign)

        val bengaliNumeric = NumberSymbolsLayout.bengaliNumericLayout
        val periodKey = bengaliNumeric.spacebarRow.find { it.label == "." }
        assertNotNull(periodKey)
        assertEquals("।", periodKey?.hintLabel)
    }

    @Test
    fun testPhoneDialpadLayoutStructure() {
        val dialpad = NumberSymbolsLayout.phoneDialpadLayout
        assertEquals("Phone Dialpad", dialpad.name)
        assertEquals(4, dialpad.rows.size)

        // 4 columns in each row (standard keyboard-like proportion)
        dialpad.rows.forEach { row ->
            assertEquals(4, row.size)
        }

        // Row 1: 1, 2, 3, ⌫
        assertEquals("1", dialpad.rows[0][0].label)
        assertEquals("2", dialpad.rows[0][1].label)
        assertEquals("3", dialpad.rows[0][2].label)
        assertEquals(KeyAction.Backspace, dialpad.rows[0][3].action)

        // No garbage hints on digit keys
        org.junit.Assert.assertNull(dialpad.rows[0][0].hintLabel)
        org.junit.Assert.assertNull(dialpad.rows[0][1].hintLabel)

        // Row 4: *, 0, #, ↵
        assertEquals("*", dialpad.rows[3][0].label)
        assertEquals("0", dialpad.rows[3][1].label)
        assertEquals("#", dialpad.rows[3][2].label)
        assertEquals(KeyAction.Enter, dialpad.rows[3][3].action)

        // Control row must have ABC switch and Space
        assertTrue(dialpad.spacebarRow.any { it.action == KeyAction.SwitchAlpha })
        assertTrue(dialpad.spacebarRow.any { it.action == KeyAction.Space })
    }

    @Test
    fun testNumpadPinLayoutStructure() {
        val numpad = NumberSymbolsLayout.numpadPinLayout
        assertEquals("Number PIN Pad", numpad.name)
        assertEquals(4, numpad.rows.size)

        // 4 columns in each row (proper keyboard proportions)
        numpad.rows.forEach { row ->
            assertEquals(4, row.size)
        }

        // Row 1: 1, 2, 3, ⌫
        assertEquals("1", numpad.rows[0][0].label)
        assertEquals("2", numpad.rows[0][1].label)
        assertEquals("3", numpad.rows[0][2].label)
        assertEquals(KeyAction.Backspace, numpad.rows[0][3].action)

        // No garbage hints on digit keys
        org.junit.Assert.assertNull(numpad.rows[0][0].hintLabel)
        org.junit.Assert.assertNull(numpad.rows[0][1].hintLabel)

        // Row 4: ., 0, ,, ↵
        assertEquals(".", numpad.rows[3][0].label)
        assertEquals("0", numpad.rows[3][1].label)
        assertEquals(",", numpad.rows[3][2].label)
        assertEquals(KeyAction.Enter, numpad.rows[3][3].action)

        // Control row must have ABC, Bengali digits toggle ('১২৩'), and Space
        val abcKey = numpad.spacebarRow.find { it.action == KeyAction.SwitchAlpha }
        assertNotNull(abcKey)
        val toggleKey = numpad.spacebarRow.find { it.action == KeyAction.ToggleBengaliDigits }
        assertNotNull(toggleKey)
        assertEquals("১২৩", toggleKey?.label)
        assertTrue(numpad.spacebarRow.any { it.action == KeyAction.Space })
    }

    @Test
    fun testBengaliNumpadPinLayoutStructure() {
        val bengaliNumpad = NumberSymbolsLayout.bengaliNumpadPinLayout
        assertEquals("Bengali Number PIN Pad", bengaliNumpad.name)
        assertEquals(4, bengaliNumpad.rows.size)

        // 4 columns in each row
        bengaliNumpad.rows.forEach { row ->
            assertEquals(4, row.size)
        }

        // Row 1: ১, ২, ৩, ⌫
        assertEquals("১", bengaliNumpad.rows[0][0].label)
        assertEquals("২", bengaliNumpad.rows[0][1].label)
        assertEquals("৩", bengaliNumpad.rows[0][2].label)
        assertEquals(KeyAction.Backspace, bengaliNumpad.rows[0][3].action)

        // No garbage hints
        org.junit.Assert.assertNull(bengaliNumpad.rows[0][0].hintLabel)

        // Row 4: ., ০, ।, ↵
        assertEquals(".", bengaliNumpad.rows[3][0].label)
        assertEquals("০", bengaliNumpad.rows[3][1].label)
        assertEquals("।", bengaliNumpad.rows[3][2].label)
        assertEquals(KeyAction.Enter, bengaliNumpad.rows[3][3].action)

        // Control row must have English digits toggle ('123') and Space
        val toggleKey = bengaliNumpad.spacebarRow.find { it.action == KeyAction.ToggleBengaliDigits }
        assertNotNull(toggleKey)
        assertEquals("123", toggleKey?.label)
        assertTrue(bengaliNumpad.spacebarRow.any { it.action == KeyAction.Space })
    }
}
