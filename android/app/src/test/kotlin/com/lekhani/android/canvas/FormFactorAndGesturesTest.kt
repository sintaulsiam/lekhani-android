package com.lekhani.android.canvas

import com.lekhani.android.data.settings.KeyboardPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FormFactorAndGesturesTest
 * ══════════════════════════════════════════════════════════════════════════════
 * Unit tests for Phase 11 Form Factors, Spacebar Cursor Slide, and Backspace Swipe Delete.
 */
class FormFactorAndGesturesTest {

    @Test
    fun testFormFactorEnums() {
        val standard = KeyboardPreferences.FormFactor.STANDARD
        val oneHandedLeft = KeyboardPreferences.FormFactor.ONE_HANDED_LEFT
        val oneHandedRight = KeyboardPreferences.FormFactor.ONE_HANDED_RIGHT
        val floating = KeyboardPreferences.FormFactor.FLOATING
        val split = KeyboardPreferences.FormFactor.SPLIT

        assertEquals("STANDARD", standard.name)
        assertEquals("ONE_HANDED_LEFT", oneHandedLeft.name)
        assertEquals("ONE_HANDED_RIGHT", oneHandedRight.name)
        assertEquals("FLOATING", floating.name)
        assertEquals("SPLIT", split.name)

        assertTrue(standard.titleBengali.isNotEmpty())
        assertTrue(oneHandedLeft.titleBengali.isNotEmpty())
        assertTrue(oneHandedRight.titleBengali.isNotEmpty())
        assertTrue(floating.titleBengali.isNotEmpty())
        assertTrue(split.titleBengali.isNotEmpty())
    }

    @Test
    fun testNewToolbarTools() {
        val oneHandedTool = KeyboardPreferences.ToolbarTool.ONE_HANDED
        val floatingTool = KeyboardPreferences.ToolbarTool.FLOATING
        val splitTool = KeyboardPreferences.ToolbarTool.SPLIT
        val resizeTool = KeyboardPreferences.ToolbarTool.RESIZE

        assertEquals("একহাতে", oneHandedTool.titleBengali)
        assertEquals("One-Handed", oneHandedTool.titleEnglish)

        assertEquals("ভাসমান", floatingTool.titleBengali)
        assertEquals("Floating", floatingTool.titleEnglish)

        assertEquals("বিভক্ত", splitTool.titleBengali)
        assertEquals("Split", splitTool.titleEnglish)

        assertEquals("উচ্চতা", resizeTool.titleBengali)
        assertEquals("Height", resizeTool.titleEnglish)
    }

    @Test
    fun testSwipeDeleteWordBoundaryScanner() {
        // Algorithm test for calculating character count to delete N words
        fun calculateCharsToDelete(text: String, wordCount: Int): Int {
            if (wordCount <= 0 || text.isEmpty()) return 0
            var charsToDelete = 0
            var wordsFound = 0
            var inWord = false
            for (i in text.length - 1 downTo 0) {
                val ch = text[i]
                val isSpace = ch.isWhitespace()
                if (!isSpace) {
                    inWord = true
                } else if (inWord) {
                    wordsFound++
                    inWord = false
                    if (wordsFound >= wordCount) {
                        break
                    }
                }
                charsToDelete++
            }
            return charsToDelete
        }

        val sample1 = "আমি বাংলায় গান গাই "
        // Deleting 1 word from "আমি বাংলায় গান গাই " deletes trailing space + "গাই"
        val chars1 = calculateCharsToDelete(sample1, 1)
        assertEquals("গাই ", sample1.takeLast(chars1))

        // Deleting 2 words deletes "গান গাই "
        val chars2 = calculateCharsToDelete(sample1, 2)
        assertEquals("গান গাই ", sample1.takeLast(chars2))

        // Deleting 3 words deletes "বাংলায় গান গাই "
        val chars3 = calculateCharsToDelete(sample1, 3)
        assertEquals("বাংলায় গান গাই ", sample1.takeLast(chars3))

        // Deleting 10 words deletes the entire string
        val charsAll = calculateCharsToDelete(sample1, 10)
        assertEquals(sample1.length, charsAll)
    }

    @Test
    fun testSpacebarCursorStepAccumulator() {
        // Algorithm test for cursor step calculation
        val stepPx = 40f
        var lastX = 100f
        var curX = 190f // moved +90px right

        val stepDelta = curX - lastX
        val steps = (stepDelta / stepPx).toInt()
        assertEquals(2, steps) // 90 / 40 = 2 steps right
        lastX += steps * stepPx
        assertEquals(180f, lastX)

        curX = 95f // moved left from 180 to 95 -> delta = -85px
        val stepDeltaLeft = curX - lastX
        val stepsLeft = (-stepDeltaLeft / stepPx).toInt()
        assertEquals(2, stepsLeft) // moved 2 steps left
    }

    @Test
    fun testDedicatedNumberRowPreference() {
        assertEquals("pref_show_dedicated_number_row", KeyboardPreferences.KEY_SHOW_DEDICATED_NUMBER_ROW)
    }
}
