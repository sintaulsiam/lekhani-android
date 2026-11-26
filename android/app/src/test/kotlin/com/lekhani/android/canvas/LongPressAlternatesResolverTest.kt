package com.lekhani.android.canvas

import com.lekhani.android.model.GboardBengaliLayout
import com.lekhani.android.model.NationalLayout
import com.lekhani.android.model.ProbhatLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class LongPressAlternatesResolverTest {

    @Test
    fun `probhat shifted ঐ prioritizes ঞ first before এ`() {
        val eKey = ProbhatLayout.layout.rows[0].find { it.label == "এ" }
        assertNotNull(eKey)

        val alts = LongPressAlternatesResolver.resolve(eKey!!, isShifted = true)
        assertEquals("ঞ", alts.firstOrNull())
        assertEquals("এ", alts.getOrNull(1))
    }

    @Test
    fun `probhat unshifted ত prioritizes থ shift partner and preserves ৎ hint`() {
        val taKey = ProbhatLayout.layout.rows[1].find { it.label == "ত" }
        assertNotNull(taKey)

        val alts = LongPressAlternatesResolver.resolve(taKey!!, isShifted = false)
        assertEquals("থ", alts.firstOrNull())
        assertEquals("ৎ", alts.getOrNull(1))
    }

    @Test
    fun `national kars prioritize shift partner unshifted and independent vowel when shifted`() {
        val allKeys = NationalLayout.layout.rows.flatten()
        val uKar = allKeys.first { it.label == "ু" }
        val iKar = allKeys.first { it.label == "ি" }
        val aaKar = allKeys.first { it.label == "া" }
        val hasanta = allKeys.first { it.label == "্" }

        assertEquals("ূ", LongPressAlternatesResolver.resolve(uKar, isShifted = false).firstOrNull())
        assertEquals("উ", LongPressAlternatesResolver.resolve(uKar, isShifted = false).getOrNull(1))
        assertEquals("ঊ", LongPressAlternatesResolver.resolve(uKar, isShifted = true).firstOrNull())
        assertEquals("ী", LongPressAlternatesResolver.resolve(iKar, isShifted = false).firstOrNull())
        assertEquals("ই", LongPressAlternatesResolver.resolve(iKar, isShifted = false).getOrNull(1))
        assertEquals("ঈ", LongPressAlternatesResolver.resolve(iKar, isShifted = true).firstOrNull())
        assertEquals("অ", LongPressAlternatesResolver.resolve(aaKar, isShifted = false).firstOrNull())
        assertEquals("আ", LongPressAlternatesResolver.resolve(aaKar, isShifted = false).getOrNull(1))
        assertEquals("আ", LongPressAlternatesResolver.resolve(aaKar, isShifted = true).firstOrNull())
        assertEquals("।", LongPressAlternatesResolver.resolve(hasanta, isShifted = false).firstOrNull())
        assertEquals("ঁ", LongPressAlternatesResolver.resolve(hasanta, isShifted = false).getOrNull(1))
    }

    @Test
    fun `shifted key uses active character alternates instead of unshifted base`() {
        val kaKey = ProbhatLayout.layout.rows[1].find { it.label == "ক" }!!
        val shiftedAlts = LongPressAlternatesResolver.resolve(kaKey, isShifted = true)
        // Active char is খ, so alternates should come from খ (which has ক, ক্ষ, ্খ, ১) rather than ক's ক্ত/ঙ্ক/ক্র
        assertEquals("ক", shiftedAlts.firstOrNull())
        assertFalse(shiftedAlts.contains("ক্ত"))
    }

    @Test
    fun `gboard vowelsRow does not expose kar hints and dari prioritizes double dari`() {
        val oKey = GboardBengaliLayout.vowelsRow.first { it.label == "অ" }
        val alts = LongPressAlternatesResolver.resolve(oKey, isShifted = false)
        assertFalse("অ should not prioritize া in Gboard vowelsRow", alts.contains("া"))
        assertEquals("আ", alts.firstOrNull())

        val dariKey = GboardBengaliLayout.layout.spacebarRow.first { it.label == "।" }
        val dariAlts = LongPressAlternatesResolver.resolve(dariKey, isShifted = false)
        assertEquals("?", dariAlts.firstOrNull())
        assertEquals("॥", dariAlts.getOrNull(1))
    }
}
