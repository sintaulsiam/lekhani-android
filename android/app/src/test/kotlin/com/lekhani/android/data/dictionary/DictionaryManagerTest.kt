package com.lekhani.android.data.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryManagerTest {

    @Test
    fun testWordListParsingFormats() {
        val rawInput = """
            # This is a comment
            স্মার্টফোন
            ল্যাপটপ,42
            ami=আমি
            tumi=তুমি
            // another comment
            
            ট্যাবলেট	15
        """.trimIndent()

        val lines = rawInput.lines()
        val extractedWords = mutableListOf<String>()
        for (line in lines) {
            val clean = line.trim()
            if (clean.isBlank() || clean.startsWith("#") || clean.startsWith("//")) continue

            val word = when {
                clean.contains("=") -> clean.substringAfter("=").trim()
                clean.contains(",") -> clean.substringBefore(",").trim()
                clean.contains("\t") -> clean.substringBefore("\t").trim()
                else -> clean
            }
            if (word.length >= 2) {
                extractedWords.add(word)
            }
        }

        assertEquals(5, extractedWords.size)
        assertTrue(extractedWords.contains("স্মার্টফোন"))
        assertTrue(extractedWords.contains("ল্যাপটপ"))
        assertTrue(extractedWords.contains("আমি"))
        assertTrue(extractedWords.contains("তুমি"))
        assertTrue(extractedWords.contains("ট্যাবলেট"))
    }
}
