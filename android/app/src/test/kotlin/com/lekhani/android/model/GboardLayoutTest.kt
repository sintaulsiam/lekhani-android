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
    fun `gboard row 5 has hasanta, anusvara, visarga, and chandrabindu`() {
        val row5 = layout.rows[4]
        val labels = row5.map { it.label }
        assertTrue("Row 5 must contain hasanta '্'", labels.contains("্"))
        assertTrue("Row 5 must contain anusvara 'ং'", labels.contains("ং"))
        assertTrue("Row 5 must contain visarga 'ঃ'", labels.contains("ঃ"))
        assertTrue("Row 5 must contain chandrabindu 'ঁ'", labels.contains("ঁ"))
    }
 
    @Test
    fun `dynamic row 5 provides ligature phalas and anusvara`() {
        val dynamicRow = GboardBengaliLayout.getDynamicRow5("ক")
        val labels = dynamicRow.map { it.label }
        assertTrue("Dynamic row 5 must contain ya-phala ligature 'ক্য'", labels.contains("ক্য"))
        assertTrue("Dynamic row 5 must contain ba-phala ligature 'ক্ব'", labels.contains("ক্ব"))
        assertTrue("Dynamic row 5 must contain ra-phala ligature 'ক্র'", labels.contains("ক্র"))
        assertTrue("Dynamic row 5 must preserve anusvara", labels.contains("ং"))
    }
 
    @Test
    fun `dynamic vowel row produces ri-kar`() {
        val dynamicKars = GboardBengaliLayout.getDynamicVowelsRow("ব")
        val labels = dynamicKars.map { it.label }
        assertTrue("Dynamic kars must contain 'বৃ'", labels.contains("বৃ"))
    }
}
