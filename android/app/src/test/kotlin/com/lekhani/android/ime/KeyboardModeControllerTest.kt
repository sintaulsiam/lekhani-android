package com.lekhani.android.ime

import com.lekhani.android.ffi.LekhaniLayoutType
import com.lekhani.android.model.NumberSymbolsLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardModeControllerTest {

    @Test
    fun testResolveNumberSymbolsLayoutModes() {
        val controller = KeyboardModeController()

        // 1. Standard Bengali numeric layout
        val defaultLayout = controller.resolveNumberSymbolsLayout(LekhaniLayoutType.AVRO)
        assertEquals(NumberSymbolsLayout.numericLayout, defaultLayout)

        // 2. English numeric layout
        val engLayout = controller.resolveNumberSymbolsLayout(LekhaniLayoutType.ENGLISH)
        assertEquals(NumberSymbolsLayout.englishNumericLayout, engLayout)

        // 3. More symbols mode
        controller.isMoreSymbolsMode = true
        val moreSymbolsLayout = controller.resolveNumberSymbolsLayout(LekhaniLayoutType.AVRO)
        assertEquals(NumberSymbolsLayout.moreSymbolsLayout, moreSymbolsLayout)
        controller.isMoreSymbolsMode = false

        // 4. Numeric PIN field
        controller.isNumericFieldMode = true
        val pinLayout = controller.resolveNumberSymbolsLayout(LekhaniLayoutType.AVRO)
        assertEquals(NumberSymbolsLayout.numpadPinLayout, pinLayout)

        // 5. Bengali Numeric PIN field
        controller.isBengaliDigitsMode = true
        val bengaliPinLayout = controller.resolveNumberSymbolsLayout(LekhaniLayoutType.AVRO)
        assertEquals(NumberSymbolsLayout.bengaliNumpadPinLayout, bengaliPinLayout)
        controller.isNumericFieldMode = false
        controller.isBengaliDigitsMode = false

        // 6. Phone dialpad mode
        controller.isPhoneDialpadMode = true
        val dialpadLayout = controller.resolveNumberSymbolsLayout(LekhaniLayoutType.AVRO)
        assertEquals(NumberSymbolsLayout.phoneDialpadLayout, dialpadLayout)
    }

    @Test
    fun testRestoreAlphaKeyboard() {
        val controller = KeyboardModeController().apply {
            isNumericMode = true
            isMoreSymbolsMode = true
            isNumericFieldMode = true
            isPhoneDialpadMode = true
            previousLayoutBeforeNumeric = LekhaniLayoutType.PROBAHO
        }

        val restored = controller.restoreAlphaKeyboard(currentLayoutType = LekhaniLayoutType.AVRO)
        assertEquals(LekhaniLayoutType.PROBAHO, restored)
        assertNull(controller.previousLayoutBeforeNumeric)
        assertFalse(controller.isNumericMode)
        assertFalse(controller.isMoreSymbolsMode)
        assertFalse(controller.isNumericFieldMode)
        assertFalse(controller.isPhoneDialpadMode)
    }

    @Test
    fun testReset() {
        val controller = KeyboardModeController().apply {
            isNumericMode = true
            previousLayoutBeforeNumeric = LekhaniLayoutType.NATIONAL
        }

        controller.reset()
        assertFalse(controller.isNumericMode)
        assertNull(controller.previousLayoutBeforeNumeric)
    }
}
