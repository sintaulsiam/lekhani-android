package com.lekhani.android.ime

import com.lekhani.android.data.settings.KeyboardPreferences
import com.lekhani.android.ffi.LekhaniLayoutType
import com.lekhani.android.ui.candidate.CandidateStripState
import com.lekhani.android.ui.candidate.UndoInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateStripCoordinatorTest {

    @Test
    fun testBlacklistFiltering() {
        val params = CandidateStripCoordinator.Params(
            rawCandidates = listOf("ভালো", "খারাপ", "সুন্দর"),
            rawInput = "valo",
            layout = LekhaniLayoutType.AVRO,
            undo = null,
            englishPredictiveSuggestions = true,
            avroShowEnglishPreview = false,
            codeShieldEnabled = false,
            avroStripOrder = KeyboardPreferences.STRIP_ORDER_BENGALI_FIRST,
            isBlacklisted = { it == "খারাপ" },
            verbParadigmChecker = { false }
        )

        val state = CandidateStripCoordinator.resolveStripState(params)
        assertTrue(state is CandidateStripState.Candidates)
        val items = (state as CandidateStripState.Candidates).items
        assertTrue(items.any { it.text == "ভালো" })
        assertTrue(items.any { it.text == "সুন্দর" })
        assertFalse(items.any { it.text == "খারাপ" })
    }

    @Test
    fun testSmartMathEvaluation() {
        val params = CandidateStripCoordinator.Params(
            rawCandidates = listOf("১০০"),
            rawInput = "50+50=",
            layout = LekhaniLayoutType.AVRO,
            undo = null,
            englishPredictiveSuggestions = true,
            avroShowEnglishPreview = false,
            codeShieldEnabled = false,
            avroStripOrder = KeyboardPreferences.STRIP_ORDER_BENGALI_FIRST,
            isBlacklisted = { false },
            verbParadigmChecker = { false }
        )

        val state = CandidateStripCoordinator.resolveStripState(params)
        assertTrue(state is CandidateStripState.Candidates)
        val items = (state as CandidateStripState.Candidates).items
        assertEquals("= 100", items[0].text)
        assertTrue(items[0].isPrimary)
    }

    @Test
    fun testEnglishFirstVsBengaliFirstOrdering() {
        // Bengali First (Default)
        val paramsBengaliFirst = CandidateStripCoordinator.Params(
            rawCandidates = listOf("আমি"),
            rawInput = "ami",
            layout = LekhaniLayoutType.AVRO,
            undo = null,
            englishPredictiveSuggestions = true,
            avroShowEnglishPreview = true,
            codeShieldEnabled = true,
            avroStripOrder = KeyboardPreferences.STRIP_ORDER_BENGALI_FIRST,
            isBlacklisted = { false },
            verbParadigmChecker = { false }
        )
        val stateBengaliFirst = CandidateStripCoordinator.resolveStripState(paramsBengaliFirst)
        assertTrue(stateBengaliFirst is CandidateStripState.Candidates)
        val itemsBengaliFirst = (stateBengaliFirst as CandidateStripState.Candidates).items
        assertEquals("আমি", itemsBengaliFirst[0].text)
        assertEquals("ami", itemsBengaliFirst[1].text)
        assertTrue(itemsBengaliFirst[1].isVerbatimPreview)

        // English First (Classic Avro)
        val paramsEnglishFirst = paramsBengaliFirst.copy(
            avroStripOrder = KeyboardPreferences.STRIP_ORDER_ENGLISH_FIRST
        )
        val stateEnglishFirst = CandidateStripCoordinator.resolveStripState(paramsEnglishFirst)
        assertTrue(stateEnglishFirst is CandidateStripState.Candidates)
        val itemsEnglishFirst = (stateEnglishFirst as CandidateStripState.Candidates).items
        assertEquals("ami", itemsEnglishFirst[0].text)
        assertEquals("আমি", itemsEnglishFirst[1].text)
    }

    @Test
    fun testUndoStateWhenEmpty() {
        val undo = UndoInfo("ami", "আমি ")
        val params = CandidateStripCoordinator.Params(
            rawCandidates = emptyList(),
            rawInput = "",
            layout = LekhaniLayoutType.AVRO,
            undo = undo,
            englishPredictiveSuggestions = true,
            avroShowEnglishPreview = false,
            codeShieldEnabled = false,
            avroStripOrder = KeyboardPreferences.STRIP_ORDER_BENGALI_FIRST,
            isBlacklisted = { false },
            verbParadigmChecker = { false }
        )

        val state = CandidateStripCoordinator.resolveStripState(params)
        assertTrue(state is CandidateStripState.Undo)
        assertEquals(undo, (state as CandidateStripState.Undo).undoInfo)
    }
}
