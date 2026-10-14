package com.lekhani.android.canvas

import com.lekhani.android.ime.EnterKeyResolver
import com.lekhani.android.model.Ch
import com.lekhani.android.model.Key
import com.lekhani.android.model.KeyAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Unit tests for [KeyboardGeometryEngine].
 */
class KeyboardGeometryEngineTest {

    private val engine = KeyboardGeometryEngine()

    @Test
    fun testGetAdaptiveSpaceRow_normalFieldReturnsOriginal() {
        val originalRow = listOf(
            Ch(","),
            Key(label = "Space", action = KeyAction.Space, contentDesc = "Space"),
            Ch(".")
        )

        val result = engine.getAdaptiveSpaceRow(
            originalRow = originalRow,
            fieldType = EnterKeyResolver.FieldType.NORMAL,
            isNumberSymbolsActive = false
        )

        assertSame(originalRow, result)
    }

    @Test
    fun testGetAdaptiveSpaceRow_symbolsModeReturnsOriginal() {
        val originalRow = listOf(
            Ch(","),
            Key(label = "Space", action = KeyAction.Space, contentDesc = "Space"),
            Ch(".")
        )

        val result = engine.getAdaptiveSpaceRow(
            originalRow = originalRow,
            fieldType = EnterKeyResolver.FieldType.EMAIL,
            isNumberSymbolsActive = true
        )

        assertSame(originalRow, result)
    }

    @Test
    fun testGetAdaptiveSpaceRow_emailFieldReplacesNeighbors() {
        val originalRow = listOf(
            Ch("a"),
            Ch(","),
            Key(label = "Space", action = KeyAction.Space, contentDesc = "Space"),
            Ch("x"),
            Ch("b")
        )

        val result = engine.getAdaptiveSpaceRow(
            originalRow = originalRow,
            fieldType = EnterKeyResolver.FieldType.EMAIL,
            isNumberSymbolsActive = false
        )

        assertEquals(5, result.size)
        assertEquals("a", result[0].label)
        assertEquals("@", result[1].label)
        assertEquals(KeyAction.Character("@"), result[1].action)
        assertEquals(KeyAction.Space, result[2].action)
        assertEquals(".", result[3].label)
        assertEquals(KeyAction.Character("."), result[3].action)
        assertEquals("b", result[4].label)
    }

    @Test
    fun testGetAdaptiveSpaceRow_urlFieldReplacesNeighbors() {
        val originalRow = listOf(
            Ch(","),
            Key(label = "Space", action = KeyAction.Space, contentDesc = "Space"),
            Ch(",")
        )

        val result = engine.getAdaptiveSpaceRow(
            originalRow = originalRow,
            fieldType = EnterKeyResolver.FieldType.URI,
            isNumberSymbolsActive = false
        )

        assertEquals(3, result.size)
        assertEquals("/", result[0].label)
        assertEquals(KeyAction.Character("/"), result[0].action)
        assertEquals(KeyAction.Space, result[1].action)
        assertEquals(".", result[2].label)
        assertEquals(KeyAction.Character("."), result[2].action)
    }

    @Test
    fun testGetAdaptiveSpaceRow_noSpaceKeyReturnsOriginal() {
        val originalRow = listOf(Ch("a"), Ch("b"))

        val result = engine.getAdaptiveSpaceRow(
            originalRow = originalRow,
            fieldType = EnterKeyResolver.FieldType.EMAIL,
            isNumberSymbolsActive = false
        )

        assertSame(originalRow, result)
    }

    @Test
    fun testSideDockActionValues() {
        assertEquals("EXPAND_STANDARD", SideDockAction.EXPAND_STANDARD.name)
        assertEquals("SWAP_SIDE", SideDockAction.SWAP_SIDE.name)
        assertEquals("TOGGLE_FLOATING", SideDockAction.TOGGLE_FLOATING.name)
    }
}
