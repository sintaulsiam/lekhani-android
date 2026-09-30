package com.lekhani.android.ui.candidate

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [HomophoneAnnotator]. Pure JVM test suite.
 */
class HomophoneAnnotatorTest {

    @Test
    fun `empty list produces empty candidates`() {
        val result = HomophoneAnnotator.annotate(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `first item is primary by default`() {
        val candidates = listOf("আমাদের", "আমি", "আমরা")
        val result = HomophoneAnnotator.annotate(candidates)

        assertEquals(3, result.size)
        assertTrue(result[0].isPrimary)
        assertFalse(result[1].isPrimary)
        assertFalse(result[2].isPrimary)
    }

    @Test
    fun `custom primary index marks specified candidate as primary`() {
        val candidates = listOf("আমাদের", "আমি", "আমরা")
        val result = HomophoneAnnotator.annotate(candidates, primaryIdx = 1)

        assertFalse(result[0].isPrimary)
        assertTrue(result[1].isPrimary)
        assertFalse(result[2].isPrimary)
    }

    @Test
    fun `known homophone pairs are correctly annotated`() {
        val candidates = listOf("পড়া", "পরা", "বাংলা", "বাঙলা")
        val result = HomophoneAnnotator.annotate(candidates)

        assertEquals("পরা", result[0].homophones)
        assertEquals("পড়া", result[1].homophones)
        assertEquals("বাঙলা", result[2].homophones)
        assertEquals("বাংলা", result[3].homophones)
    }

    @Test
    fun `words without known homophones have null homophone field`() {
        val candidates = listOf("বাংলাদেশ", "কম্পিউটার", "কলম")
        val result = HomophoneAnnotator.annotate(candidates)

        result.forEach { item ->
            assertNull("Expected null homophone for '${item.text}'", item.homophones)
        }
    }

    @Test
    fun `verb and colloquial homophones are identified`() {
        val candidates = listOf("খাব", "যাব", "করব", "ধরব", "করে", "করি")
        val result = HomophoneAnnotator.annotate(candidates)

        assertEquals("যাব", result[0].homophones)
        assertEquals("খাব", result[1].homophones)
        assertEquals("ধরব", result[2].homophones)
        assertEquals("করব", result[3].homophones)
        assertEquals("করি", result[4].homophones)
        assertEquals("করে", result[5].homophones)
    }

    @Test
    fun `verbatim candidate is correctly flagged as preview`() {
        val candidates = listOf("ami", "আমি", "আমী")
        val result = HomophoneAnnotator.annotate(candidates, primaryIdx = 1, verbatimIdx = 0)

        assertTrue(result[0].isVerbatimPreview)
        assertFalse(result[0].isPrimary)
        assertNull(result[0].homophones)

        assertFalse(result[1].isVerbatimPreview)
        assertTrue(result[1].isPrimary)

        assertFalse(result[2].isVerbatimPreview)
        assertFalse(result[2].isPrimary)
    }
}
