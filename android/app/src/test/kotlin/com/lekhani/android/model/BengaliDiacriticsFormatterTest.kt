package com.lekhani.android.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BengaliDiacriticsFormatterTest {

    @Test
    fun `isBengaliCombiningMark identifies all standard Bengali kars and modifiers`() {
        val combiningChars = listOf(
            'ি', 'া', 'ী', 'ু', 'ূ', 'ৃ', 'ৄ', 'ে', 'ৈ', 'ো', 'ৌ', // Kars
            '্',                                                    // Hasanta / Virama
            'ঁ', 'ং', 'ঃ',                                         // Modifiers
            '়', 'ৗ', 'ৢ', 'ৣ'                                     // Nukta, Au length mark, vocalics
        )
        for (ch in combiningChars) {
            assertTrue("Expected $ch to be recognized as combining mark", BengaliDiacriticsFormatter.isBengaliCombiningMark(ch))
        }
    }

    @Test
    fun `isBengaliCombiningMark returns false for regular consonants, vowels, digits and latin`() {
        val nonCombiningChars = listOf(
            'ক', 'খ', 'গ', 'ম', 'য', 'র', 'ল', 'শ', 'স', 'হ',
            'অ', 'আ', 'ই', 'ঈ', 'উ', 'ঊ', 'ঋ', 'এ', 'ঐ', 'ও', 'ঔ',
            '১', '২', '৩', '০', 'a', 'Z', '?', '1', ' '
        )
        for (ch in nonCombiningChars) {
            assertFalse("Expected $ch to NOT be combining mark", BengaliDiacriticsFormatter.isBengaliCombiningMark(ch))
        }
    }

    @Test
    fun `formatForDisplay prepends NBSP to isolated combining marks`() {
        assertEquals("\u00A0ি", BengaliDiacriticsFormatter.formatForDisplay("ি"))
        assertEquals("\u00A0া", BengaliDiacriticsFormatter.formatForDisplay("া"))
        assertEquals("\u00A0ু", BengaliDiacriticsFormatter.formatForDisplay("ু"))
        assertEquals("\u00A0ে", BengaliDiacriticsFormatter.formatForDisplay("ে"))
        assertEquals("\u00A0্", BengaliDiacriticsFormatter.formatForDisplay("্"))
        assertEquals("\u00A0ং", BengaliDiacriticsFormatter.formatForDisplay("ং"))
        assertEquals("\u00A0ঃ", BengaliDiacriticsFormatter.formatForDisplay("ঃ"))
        assertEquals("\u00A0ঁ", BengaliDiacriticsFormatter.formatForDisplay("ঁ"))
    }

    @Test
    fun `formatForDisplay leaves regular words and already sanitized strings unchanged`() {
        assertEquals("ক", BengaliDiacriticsFormatter.formatForDisplay("ক"))
        assertEquals("অ", BengaliDiacriticsFormatter.formatForDisplay("অ"))
        assertEquals("কি", BengaliDiacriticsFormatter.formatForDisplay("কি"))
        assertEquals("English", BengaliDiacriticsFormatter.formatForDisplay("English"))
        assertEquals("\u00A0ি", BengaliDiacriticsFormatter.formatForDisplay("\u00A0ি"))
        assertEquals("", BengaliDiacriticsFormatter.formatForDisplay(""))
        assertEquals("", BengaliDiacriticsFormatter.formatForDisplay(null))
    }
}
