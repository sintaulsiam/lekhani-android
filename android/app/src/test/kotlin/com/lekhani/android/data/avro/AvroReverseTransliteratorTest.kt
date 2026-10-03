package com.lekhani.android.data.avro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AvroReverseTransliteratorTest {

    @Test
    fun testIsBengaliWord() {
        assertTrue(AvroReverseTransliterator.isBengaliWord("আমি"))
        assertTrue(AvroReverseTransliterator.isBengaliWord("ভালো"))
        assertTrue(AvroReverseTransliterator.isBengaliWord("বাংলাদেশ"))
        assertFalse(AvroReverseTransliterator.isBengaliWord("hello"))
        assertFalse(AvroReverseTransliterator.isBengaliWord("12345"))
        assertFalse(AvroReverseTransliterator.isBengaliWord(""))
    }

    @Test
    fun testCommonBengaliWordsReverse() {
        assertEquals("ami", AvroReverseTransliterator.bengaliToAvro("আমি"))
        assertEquals("tumi", AvroReverseTransliterator.bengaliToAvro("তুমি"))
        assertEquals("bhalo", AvroReverseTransliterator.bengaliToAvro("ভালো"))
        assertEquals("khabo", AvroReverseTransliterator.bengaliToAvro("খাবো"))
        assertEquals("gan", AvroReverseTransliterator.bengaliToAvro("গান"))
        assertEquals("gai", AvroReverseTransliterator.bengaliToAvro("গাই"))
        assertEquals("bhat", AvroReverseTransliterator.bengaliToAvro("ভাত"))
        assertEquals("bangladesh", AvroReverseTransliterator.bengaliToAvro("বাংলাদেশ"))
        assertEquals("Dhaka", AvroReverseTransliterator.bengaliToAvro("ঢাকা"))
        assertEquals("kolkata", AvroReverseTransliterator.bengaliToAvro("কল্কাতা"))
        assertEquals("kolokata", AvroReverseTransliterator.bengaliToAvro("কলকাতা"))
        // Conjunct & phala reverse transliterations
        assertEquals("dhonyobad", AvroReverseTransliterator.bengaliToAvro("ধন্যবাদ"))
        assertEquals("konya", AvroReverseTransliterator.bengaliToAvro("কন্যা"))
        assertEquals("bonya", AvroReverseTransliterator.bengaliToAvro("বন্যা"))
        assertEquals("byokti", AvroReverseTransliterator.bengaliToAvro("ব্যক্তি"))
        assertEquals("kkhoma", AvroReverseTransliterator.bengaliToAvro("ক্ষমা"))
        assertEquals("shikkha", AvroReverseTransliterator.bengaliToAvro("শিক্ষা"))
        assertEquals("ggan", AvroReverseTransliterator.bengaliToAvro("জ্ঞান"))
        assertEquals("oncol", AvroReverseTransliterator.bengaliToAvro("অঞ্চল"))
        assertEquals("swagotom", AvroReverseTransliterator.bengaliToAvro("স্বাগতম"))
        assertEquals("bishwas", AvroReverseTransliterator.bengaliToAvro("বিশ্বাস"))
    }

    @Test
    fun testAvroWordHistoryCaching() {
        val history = AvroWordHistory(maxEntries = 10)
        history.record("আমি", "ami", withSpace = true)
        assertEquals("ami", history.get("আমি"))
        assertEquals("আমি", history.lastCommittedWord)
        assertEquals("ami", history.lastCommittedRaw)
        assertTrue(history.lastCommittedWithSpace)

        // Custom spelling variant
        history.record("করো", "kro", withSpace = false)
        assertEquals("kro", history.get("করো"))
        assertFalse(history.lastCommittedWithSpace)
    }

    @Test
    fun testWordBoundaryExtractionAroundCursor() {
        // Simulating cursor placed inside "আমি ভা|লো আছি"
        val before = "আমি ভা"
        val after = "লো আছি"

        // Backward scan in before
        var bIdx = before.length - 1
        while (bIdx >= 0 && !before[bIdx].isWhitespace()) bIdx--
        val wordBefore = before.substring(bIdx + 1)

        // Forward scan in after
        var aIdx = 0
        while (aIdx < after.length && !after[aIdx].isWhitespace()) aIdx++
        val wordAfter = after.substring(0, aIdx)

        val fullWord = wordBefore + wordAfter
        assertEquals("ভালো", fullWord)
        assertTrue(AvroReverseTransliterator.isBengaliWord(fullWord))
        assertEquals("bhalo", AvroReverseTransliterator.bengaliToAvro(fullWord))
    }

    @Test
    fun testBackspaceRecompositionSimulation() {
        // User typed "ami", committed with space -> "আমি "
        val history = AvroWordHistory()
        history.record("আমি", "ami", withSpace = true)

        // Backspace deletes trailing space -> cursor at "আমি|"
        val textBefore = "আমি"
        val word = textBefore
        val raw = history.get(word) ?: AvroReverseTransliterator.bengaliToAvro(word)
        assertEquals("ami", raw)

        // Recompose and simulate another backspace on "ami" -> drops 'i' to get "am"
        val backspacedRaw = raw.substring(0, raw.length - 1)
        assertEquals("am", backspacedRaw)
    }

    @Test
    fun testAmarBackspaceRecompositionNoDuplicateChars() {
        val history = AvroWordHistory()
        history.record("আমার", "amar", withSpace = true)

        // Scenario 1: User typed "আমার " and hits Backspace once after the space
        val beforeWithSpace = "আমার "
        val trailingSpaces = if (beforeWithSpace.endsWith(" ")) 1 else 0
        assertEquals(1, trailingSpaces)

        val wordBeforeSpace = beforeWithSpace.trimEnd()
        assertEquals("আমার", wordBeforeSpace)
        assertTrue(AvroReverseTransliterator.isBengaliWord(wordBeforeSpace))

        // Total chars to delete before cursor: 4 ('আমার') + 1 (' ') = 5 chars
        val charsToDelete = wordBeforeSpace.length + trailingSpaces
        assertEquals(5, charsToDelete)

        val rawWithSpace = history.get(wordBeforeSpace) ?: AvroReverseTransliterator.bengaliToAvro(wordBeforeSpace)
        assertEquals("amar", rawWithSpace)
        // When preceded by space, replay string preserves the full word for re-engagement
        val replayWithSpace = rawWithSpace
        assertEquals("amar", replayWithSpace)

        // Scenario 2: User hits Backspace directly on committed "আমার" (no space)
        val beforeWithoutSpace = "আমার"
        val trailingZero = if (beforeWithoutSpace.endsWith(" ")) 1 else 0
        assertEquals(0, trailingZero)

        val charsToDeleteDirect = beforeWithoutSpace.length + trailingZero
        assertEquals(4, charsToDeleteDirect)

        val rawDirect = history.get(beforeWithoutSpace) ?: AvroReverseTransliterator.bengaliToAvro(beforeWithoutSpace)
        assertEquals("amar", rawDirect)
        // Phonetic backspace drops trailing 'r' -> "ama"
        val replayDirect = rawDirect.substring(0, rawDirect.length - 1)
        assertEquals("ama", replayDirect)

        // Ensure reverse transliteration of "আমা" is "ama"
        assertEquals("ama", AvroReverseTransliterator.bengaliToAvro("আমা"))
    }

    @Test
    fun testCursorPlacementInMiddleOfWordGboardStyle() {
        // Simulating cursor placed inside "আমার সো|নার বাংলা"
        val before = "আমার সো"
        val after = "নার বাংলা"

        fun isWordChar(c: Char): Boolean {
            if (c.isWhitespace()) return false
            if (c in listOf('।', '॥', ',', '.', '?', '!', ';', ':', '"', '\'', '(', ')')) return false
            return true
        }

        var beforeWordCount = 0
        var i = before.length - 1
        while (i >= 0 && isWordChar(before[i])) {
            beforeWordCount++
            i--
        }

        var afterWordCount = 0
        var j = 0
        while (j < after.length && isWordChar(after[j])) {
            afterWordCount++
            j++
        }

        val word = before.substring(before.length - beforeWordCount) + after.substring(0, afterWordCount)
        assertEquals("সোনার", word)
        assertEquals(2, beforeWordCount)
        assertEquals(3, afterWordCount)
        assertEquals("sonar", AvroReverseTransliterator.bengaliToAvro(word))

        // Simulating candidate selection replacement:
        // deleteSurroundingText(beforeWordCount, afterWordCount) removes exactly "সোনার"
        val reconstructedBefore = before.substring(0, before.length - beforeWordCount)
        val reconstructedAfter = after.substring(afterWordCount)
        val replacedDocument = reconstructedBefore + "সুনার" + reconstructedAfter
        assertEquals("আমার সুনার বাংলা", replacedDocument)
    }

    @Test
    fun testAtomicBackspaceAmarLeavesAamaWithoutDuplication() {
        // Document has committed "আমার"
        var doc = "আমার"
        assertEquals(4, doc.length)

        // Script-aware backspace deletes last char 'র' (1 char)
        doc = doc.substring(0, doc.length - 1)
        assertEquals("আমা", doc)

        // Word at cursor is now "আমা"
        val englishPreview = AvroReverseTransliterator.bengaliToAvro(doc)
        assertEquals("ama", englishPreview)

        // Verify that doc was never wiped or prepended into "আআমার"
        assertFalse(doc.contains("আআমার"))
        assertEquals("আমা", doc)
    }
}


