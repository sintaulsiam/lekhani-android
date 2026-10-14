package com.lekhani.android.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartPunctuationCoordinatorTest {

    @Test
    fun testDariConvertsToEllipsisOnThirdSuccession() {
        // 1st Dari -> Commit "। "
        val res1 = SmartPunctuationCoordinator.processCommit("।", "আমি", isUrlOrEmailOrNumeric = false)
        assertTrue(res1 is SmartPunctuationCoordinator.PunctuationResult.Commit)
        val commit1 = res1 as SmartPunctuationCoordinator.PunctuationResult.Commit
        assertEquals("। ", commit1.finalText)
        assertEquals(false, commit1.shouldDeletePrecedingSpace)
        assertEquals(true, commit1.isDari)

        // Preceding space is deleted if user typed a space before Dari
        val resSpace = SmartPunctuationCoordinator.processCommit("।", "আমি ", isUrlOrEmailOrNumeric = false)
        val commitSpace = resSpace as SmartPunctuationCoordinator.PunctuationResult.Commit
        assertEquals(true, commitSpace.shouldDeletePrecedingSpace)

        // 2nd Dari -> Replace with Double Dari "।। "
        val res2 = SmartPunctuationCoordinator.processCommit("।", "আমি। ", isUrlOrEmailOrNumeric = false)
        assertTrue(res2 is SmartPunctuationCoordinator.PunctuationResult.Replace)
        val rep2 = res2 as SmartPunctuationCoordinator.PunctuationResult.Replace
        assertEquals(2, rep2.deleteLength)
        assertEquals("।। ", rep2.textToCommit)

        // 3rd Dari -> Replace with Ellipsis "… "
        val res3 = SmartPunctuationCoordinator.processCommit("।", "আমি।। ", isUrlOrEmailOrNumeric = false)
        assertTrue(res3 is SmartPunctuationCoordinator.PunctuationResult.Replace)
        val rep3 = res3 as SmartPunctuationCoordinator.PunctuationResult.Replace
        assertEquals(3, rep3.deleteLength)
        assertEquals("… ", rep3.textToCommit)
    }

    @Test
    fun testCommaFormattingWithDigits() {
        // Comma between digits should NOT append space
        val digitComma = SmartPunctuationCoordinator.processCommit(",", "100", isUrlOrEmailOrNumeric = false)
        val commitDigit = digitComma as SmartPunctuationCoordinator.PunctuationResult.Commit
        assertEquals(",", commitDigit.finalText)

        // Comma after words should append trailing space
        val wordComma = SmartPunctuationCoordinator.processCommit(",", "Hello", isUrlOrEmailOrNumeric = false)
        val commitWord = wordComma as SmartPunctuationCoordinator.PunctuationResult.Commit
        assertEquals(", ", commitWord.finalText)
    }

    @Test
    fun testDoubleSpaceShortcut() {
        // Double space in Bengali -> "। "
        val shortcutBangla = SmartPunctuationCoordinator.resolveDoubleSpaceShortcut(
            textBefore = "আমি ভালো ",
            isEnglish = false,
            doubleSpaceDariEnabled = true,
            isComposing = false,
            isUrlOrEmailOrNumeric = false,
            timeDeltaMs = 200,
        )
        assertEquals("। ", shortcutBangla)

        // Double space in English -> ". "
        val shortcutEnglish = SmartPunctuationCoordinator.resolveDoubleSpaceShortcut(
            textBefore = "Hello world ",
            isEnglish = true,
            doubleSpaceDariEnabled = true,
            isComposing = false,
            isUrlOrEmailOrNumeric = false,
            timeDeltaMs = 200,
        )
        assertEquals(". ", shortcutEnglish)

        // Should not trigger if after punctuation
        val shortcutAfterPunct = SmartPunctuationCoordinator.resolveDoubleSpaceShortcut(
            textBefore = "Hello world. ",
            isEnglish = true,
            doubleSpaceDariEnabled = true,
            isComposing = false,
            isUrlOrEmailOrNumeric = false,
            timeDeltaMs = 200,
        )
        assertNull(shortcutAfterPunct)

        // Should not trigger if timeout expired (> 450 ms)
        val shortcutTimeout = SmartPunctuationCoordinator.resolveDoubleSpaceShortcut(
            textBefore = "Hello world ",
            isEnglish = true,
            doubleSpaceDariEnabled = true,
            isComposing = false,
            isUrlOrEmailOrNumeric = false,
            timeDeltaMs = 600,
        )
        assertNull(shortcutTimeout)
    }
}
