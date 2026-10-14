package com.lekhani.android.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AvroInspectionCoordinatorTest {

    @Test
    fun testIsWordChar() {
        // Bengali characters are word characters
        assertTrue(AvroInspectionCoordinator.isWordChar('ক'))
        assertTrue(AvroInspectionCoordinator.isWordChar('া'))
        assertTrue(AvroInspectionCoordinator.isWordChar('হ'))

        // English letters and digits are word characters
        assertTrue(AvroInspectionCoordinator.isWordChar('a'))
        assertTrue(AvroInspectionCoordinator.isWordChar('Z'))
        assertTrue(AvroInspectionCoordinator.isWordChar('5'))

        // Whitespace and punctuation are NOT word characters
        assertFalse(AvroInspectionCoordinator.isWordChar(' '))
        assertFalse(AvroInspectionCoordinator.isWordChar('\t'))
        assertFalse(AvroInspectionCoordinator.isWordChar('\n'))
        assertFalse(AvroInspectionCoordinator.isWordChar('।'))
        assertFalse(AvroInspectionCoordinator.isWordChar('॥'))
        assertFalse(AvroInspectionCoordinator.isWordChar('.'))
        assertFalse(AvroInspectionCoordinator.isWordChar(','))
        assertFalse(AvroInspectionCoordinator.isWordChar('?'))
        assertFalse(AvroInspectionCoordinator.isWordChar('!'))
    }

    @Test
    fun testIsAvroPhoneticKey() {
        assertTrue(AvroInspectionCoordinator.isAvroPhoneticKey("a"))
        assertTrue(AvroInspectionCoordinator.isAvroPhoneticKey("k"))
        assertTrue(AvroInspectionCoordinator.isAvroPhoneticKey("T"))
        assertTrue(AvroInspectionCoordinator.isAvroPhoneticKey("1"))
        assertTrue(AvroInspectionCoordinator.isAvroPhoneticKey(":"))
        assertTrue(AvroInspectionCoordinator.isAvroPhoneticKey("^"))
        assertTrue(AvroInspectionCoordinator.isAvroPhoneticKey("`"))

        assertFalse(AvroInspectionCoordinator.isAvroPhoneticKey(" "))
        assertFalse(AvroInspectionCoordinator.isAvroPhoneticKey("."))
        assertFalse(AvroInspectionCoordinator.isAvroPhoneticKey(","))
        assertFalse(AvroInspectionCoordinator.isAvroPhoneticKey("!"))
        assertFalse(AvroInspectionCoordinator.isAvroPhoneticKey("multi"))
    }

    @Test
    fun testInspectedWordProperties() {
        val inspected = InspectedWord(
            word = "আমার",
            charsBeforeCursor = 4,
            charsAfterCursor = 0,
            rawPhonetic = "amar",
            previewEnglish = "Amar",
            hasTrailingSpace = true
        )

        assertEquals("আমার", inspected.word)
        assertEquals(4, inspected.charsBeforeCursor)
        assertEquals(0, inspected.charsAfterCursor)
        assertEquals("amar", inspected.rawPhonetic)
        assertEquals("amar", inspected.rawEnglish)
        assertEquals("Amar", inspected.previewEnglish)
        assertTrue(inspected.hasTrailingSpace)
    }
}
