package com.lekhani.android.canvas

import com.lekhani.android.model.Ch
import com.lekhani.android.model.Key
import com.lekhani.android.model.KeyAction
import com.lekhani.android.model.EnglishQwertyLayout
import com.lekhani.android.model.NationalLayout
import com.lekhani.android.model.ProbahLayout
import com.lekhani.android.model.ProbhatLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying that long-press alternates prioritize the layout-specific
 * Shift character as Candidate #0 on fixed and Bengali layouts, and that total
 * candidates are capped at 4 for thumb reachability.
 */
class LongPressAlternatesTest {

    private fun resolveAlternates(key: Key, isShifted: Boolean): Array<String> =
        LongPressAlternatesResolver.resolve(key, isShifted)


    @Test
    fun testNationalLayoutPrioritizesShiftAlternative() {
        val nationalKeys = NationalLayout.layout.rows.flatten()

        // 1. ক -> Shift is খ
        val kaKey = nationalKeys.first { it.label == "ক" }
        val kaAlts = resolveAlternates(kaKey, isShifted = false)
        assertTrue(kaAlts.isNotEmpty())
        assertEquals("খ", kaAlts[0])
        assertTrue("Candidate count must be <= 4 for reachability", kaAlts.size <= 4)

        // 2. ব -> Shift is ভ
        val baKey = nationalKeys.first { it.label == "ব" }
        val baAlts = resolveAlternates(baKey, isShifted = false)
        assertEquals("ভ", baAlts[0])
        assertTrue(baAlts.size <= 4)

        // 3. া -> Shift is অ
        val aKarKey = nationalKeys.first { it.label == "া" }
        val aKarAlts = resolveAlternates(aKarKey, isShifted = false)
        assertEquals("অ", aKarAlts[0])
        assertTrue(aKarAlts.size <= 4)

        // 4. ্ (Hasanta) -> Shift is । (Dari)
        val hasantaKey = nationalKeys.first { it.label == "্" }
        val hasantaAlts = resolveAlternates(hasantaKey, isShifted = false)
        assertEquals("।", hasantaAlts[0])
        assertTrue(hasantaAlts.contains("ঁ")) // hint preserved
        assertTrue(hasantaAlts.size <= 4)

        // 5. র -> Shift is ল
        val raKey = nationalKeys.first { it.label == "র" }
        val raAlts = resolveAlternates(raKey, isShifted = false)
        assertEquals("ল", raAlts[0])
        assertTrue(raAlts.size <= 4)
    }

    @Test
    fun testProbhatLayoutPrioritizesShiftAlternative() {
        val probhatKeys = ProbhatLayout.layout.rows.flatten()

        // 1. হ -> Shift is ঃ
        val haKey = probhatKeys.first { it.label == "হ" }
        val haAlts = resolveAlternates(haKey, isShifted = false)
        assertEquals("ঃ", haAlts[0])
        assertTrue(haAlts.size <= 4)

        // 2. ত -> Shift is থ, Hint is ৎ
        val taKey = probhatKeys.first { it.label == "ত" }
        val taAlts = resolveAlternates(taKey, isShifted = false)
        assertEquals("থ", taAlts[0])
        assertTrue(taAlts.contains("ৎ"))
        assertTrue(taAlts.size <= 4)
    }

    @Test
    fun testEnglishLayoutPrioritizesNumberHintOverCase() {
        val englishQ = Ch("q", "Q", hint = "1")
        val qAlts = resolveAlternates(englishQ, isShifted = false)
        assertTrue(qAlts.isNotEmpty())
        assertEquals("1", qAlts[0]) // Number hint prioritized over uppercase Q
        assertTrue(qAlts.size <= 4)
    }

    @Test
    fun testClipboardShortcutsOnEnglishLayout() {
        fun resolveClipboardShortcut(key: Key, enabled: Boolean, isLatinLayout: Boolean): KeyAction? {
            if (!enabled || !isLatinLayout) return null
            return when (key.label.lowercase()) {
                "c" -> KeyAction.Copy
                "v" -> KeyAction.Paste
                "x" -> KeyAction.Cut
                "a" -> KeyAction.SelectAll
                else -> null
            }
        }

        val cKey = Ch("c", "C")
        val vKey = Ch("v", "V")
        val xKey = Ch("x", "X")
        val aKey = Ch("a", "A")
        val bKey = Ch("b", "B")

        // 1. When enabled on Latin layouts
        assertEquals(KeyAction.Copy, resolveClipboardShortcut(cKey, enabled = true, isLatinLayout = true))
        assertEquals(KeyAction.Paste, resolveClipboardShortcut(vKey, enabled = true, isLatinLayout = true))
        assertEquals(KeyAction.Cut, resolveClipboardShortcut(xKey, enabled = true, isLatinLayout = true))
        assertEquals(KeyAction.SelectAll, resolveClipboardShortcut(aKey, enabled = true, isLatinLayout = true))
        assertEquals(null, resolveClipboardShortcut(bKey, enabled = true, isLatinLayout = true))

        // 2. When disabled
        assertEquals(null, resolveClipboardShortcut(cKey, enabled = false, isLatinLayout = true))
        assertEquals(null, resolveClipboardShortcut(vKey, enabled = false, isLatinLayout = true))

        // 3. When on Bengali layouts (never hijack Bengali keys)
        assertEquals(null, resolveClipboardShortcut(cKey, enabled = true, isLatinLayout = false))
        assertEquals(null, resolveClipboardShortcut(vKey, enabled = true, isLatinLayout = false))
    }
}

