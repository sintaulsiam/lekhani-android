package com.lekhani.android.model
 
import org.junit.Assert.*
import org.junit.Test
 
/**
 * Unit tests verifying character coverage and dynamic rows for Gboard Bengali layout.
 */
class GboardLayoutTest {
 
    private val layout = GboardBengaliLayout.layout
 
    @Test
    fun `gboard layout has exactly 5 main rows`() {
        assertEquals(5, layout.rows.size)
    }

    @Test
    fun `gboard row 1 contains all 11 independent vowels including Ri`() {
        val vowels = layout.rows[0].map { it.label }
        assertEquals(11, vowels.size)
        val expectedVowels = listOf("অ", "আ", "ই", "ঈ", "উ", "ঊ", "ঋ", "এ", "ঐ", "ও", "ঔ")
        assertEquals(expectedVowels, vowels)
    }

    @Test
    fun `gboard row 5 has both Rro and Rrha, hasanta, anusvara, visarga, chandrabindu, and backspace`() {
        val row5 = layout.rows[4]
        assertEquals(11, row5.size)
        val labels = row5.map { it.label }
        assertTrue("Row 5 must contain 'ড়'", labels.contains("ড়"))
        assertTrue("Row 5 must contain 'ঢ়'", labels.contains("ঢ়"))
        assertTrue("Row 5 must contain hasanta '্'", labels.contains("্"))
        assertTrue("Row 5 must contain anusvara 'ং'", labels.contains("ং"))
        assertTrue("Row 5 must contain visarga 'ঃ'", labels.contains("ঃ"))
        assertTrue("Row 5 must contain chandrabindu 'ঁ'", labels.contains("ঁ"))
        assertTrue("Row 5 must contain backspace '⌫'", labels.contains("⌫"))
    }

    @Test
    fun `dynamic row 5 preserves hasanta and provides ligature phalas and anusvara`() {
        val dynamicRow = GboardBengaliLayout.getDynamicRow5("ক")
        assertEquals(11, dynamicRow.size)
        val labels = dynamicRow.map { it.label }
        assertTrue("Dynamic row 5 must preserve hasanta '্'", labels.contains("্"))
        assertEquals("Hasanta must be at index 6 in dynamic row 5", "্", dynamicRow[6].label)
        assertTrue("Dynamic row 5 must contain ya-phala ligature 'ক্য'", labels.contains("ক্য"))
        assertTrue("Dynamic row 5 must contain ra-phala ligature 'ক্র'", labels.contains("ক্র"))
        assertTrue("Dynamic row 5 must contain 'ড়'", labels.contains("ড়"))
        assertTrue("Dynamic row 5 must contain 'ঢ়'", labels.contains("ঢ়"))
        assertTrue("Dynamic row 5 must preserve anusvara", labels.contains("ং"))
        assertTrue("Dynamic row 5 must contain backspace", labels.contains("⌫"))

        // Verify ba-phala is accessible via hint/long press on ya-phala
        val yaPhalaKey = dynamicRow.find { it.label == "ক্য" }
        assertNotNull(yaPhalaKey)
        assertEquals("ক্ব", yaPhalaKey?.hintLabel)
        assertEquals(KeyAction.Character("্\u09AC"), yaPhalaKey?.longPressAction)
    }

    @Test
    fun `dynamic vowel row has 11 keys with Ri-kar and independent vowel escape`() {
        val dynamicKars = GboardBengaliLayout.getDynamicVowelsRow("ব")
        assertEquals(11, dynamicKars.size)
        val labels = dynamicKars.map { it.label }
        assertTrue("Dynamic kars must contain 'বৃ'", labels.contains("বৃ"))
        assertTrue("Dynamic kars must contain escape key 'অ'", labels.contains("অ"))

        val escapeKey = dynamicKars.last()
        assertEquals("অ", escapeKey.label)
        assertEquals(KeyAction.ToggleGboardVowels, escapeKey.action)
        assertEquals(KeyAction.Character("অ"), escapeKey.longPressAction)
    }

    @Test
    fun `empty dynamic vowel row has 10 kars and independent vowel escape`() {
        val emptyKars = GboardBengaliLayout.getDynamicVowelsRow("")
        assertEquals(11, emptyKars.size)
        assertEquals("া", emptyKars[0].label)
        assertEquals("ৃ", emptyKars[5].label)
        assertEquals("ৌ", emptyKars[9].label)
        assertEquals("অ", emptyKars[10].label)
        assertEquals(KeyAction.ToggleGboardVowels, emptyKars[10].action)
    }
}
