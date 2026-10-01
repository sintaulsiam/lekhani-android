package com.lekhani.android.ime

import android.view.KeyEvent
import com.lekhani.android.data.settings.KeyboardPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for volume key cursor navigation event handling logic.
 */
class VolumeKeyCursorNavigationTest {

    /**
     * Replicates the exact dispatch decision logic in LekhaniInputMethodService.onKeyDown
     */
    private fun resolveVolumeKeyCursorDelta(
        mode: KeyboardPreferences.VolumeKeyCursorMode,
        isInputViewShown: Boolean,
        hasInputConnection: Boolean,
        keyCode: Int
    ): Pair<Boolean, Int?> {
        if (mode != KeyboardPreferences.VolumeKeyCursorMode.DISABLED && isInputViewShown && hasInputConnection) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    val delta = if (mode == KeyboardPreferences.VolumeKeyCursorMode.UP_LEFT_DOWN_RIGHT) -1 else 1
                    return true to delta
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    val delta = if (mode == KeyboardPreferences.VolumeKeyCursorMode.UP_LEFT_DOWN_RIGHT) 1 else -1
                    return true to delta
                }
            }
        }
        return false to null
    }

    @Test
    fun `disabled mode does not intercept volume up or down`() {
        val (interceptedUp, deltaUp) = resolveVolumeKeyCursorDelta(
            mode = KeyboardPreferences.VolumeKeyCursorMode.DISABLED,
            isInputViewShown = true,
            hasInputConnection = true,
            keyCode = KeyEvent.KEYCODE_VOLUME_UP
        )
        assertFalse(interceptedUp)
        assertEquals(null, deltaUp)

        val (interceptedDown, deltaDown) = resolveVolumeKeyCursorDelta(
            mode = KeyboardPreferences.VolumeKeyCursorMode.DISABLED,
            isInputViewShown = true,
            hasInputConnection = true,
            keyCode = KeyEvent.KEYCODE_VOLUME_DOWN
        )
        assertFalse(interceptedDown)
        assertEquals(null, deltaDown)
    }

    @Test
    fun `up-left down-right moves left on volume up and right on volume down`() {
        val (interceptedUp, deltaUp) = resolveVolumeKeyCursorDelta(
            mode = KeyboardPreferences.VolumeKeyCursorMode.UP_LEFT_DOWN_RIGHT,
            isInputViewShown = true,
            hasInputConnection = true,
            keyCode = KeyEvent.KEYCODE_VOLUME_UP
        )
        assertTrue(interceptedUp)
        assertEquals(-1, deltaUp)

        val (interceptedDown, deltaDown) = resolveVolumeKeyCursorDelta(
            mode = KeyboardPreferences.VolumeKeyCursorMode.UP_LEFT_DOWN_RIGHT,
            isInputViewShown = true,
            hasInputConnection = true,
            keyCode = KeyEvent.KEYCODE_VOLUME_DOWN
        )
        assertTrue(interceptedDown)
        assertEquals(1, deltaDown)
    }

    @Test
    fun `up-right down-left moves right on volume up and left on volume down`() {
        val (interceptedUp, deltaUp) = resolveVolumeKeyCursorDelta(
            mode = KeyboardPreferences.VolumeKeyCursorMode.UP_RIGHT_DOWN_LEFT,
            isInputViewShown = true,
            hasInputConnection = true,
            keyCode = KeyEvent.KEYCODE_VOLUME_UP
        )
        assertTrue(interceptedUp)
        assertEquals(1, deltaUp)

        val (interceptedDown, deltaDown) = resolveVolumeKeyCursorDelta(
            mode = KeyboardPreferences.VolumeKeyCursorMode.UP_RIGHT_DOWN_LEFT,
            isInputViewShown = true,
            hasInputConnection = true,
            keyCode = KeyEvent.KEYCODE_VOLUME_DOWN
        )
        assertTrue(interceptedDown)
        assertEquals(-1, deltaDown)
    }

    @Test
    fun `hidden input view never intercepts volume keys`() {
        val (interceptedUp, _) = resolveVolumeKeyCursorDelta(
            mode = KeyboardPreferences.VolumeKeyCursorMode.UP_LEFT_DOWN_RIGHT,
            isInputViewShown = false,
            hasInputConnection = true,
            keyCode = KeyEvent.KEYCODE_VOLUME_UP
        )
        assertFalse(interceptedUp)
    }

    @Test
    fun `null input connection never intercepts volume keys`() {
        val (interceptedDown, _) = resolveVolumeKeyCursorDelta(
            mode = KeyboardPreferences.VolumeKeyCursorMode.UP_LEFT_DOWN_RIGHT,
            isInputViewShown = true,
            hasInputConnection = false,
            keyCode = KeyEvent.KEYCODE_VOLUME_DOWN
        )
        assertFalse(interceptedDown)
    }

    @Test
    fun `unrelated key codes are ignored`() {
        val (interceptedOther, _) = resolveVolumeKeyCursorDelta(
            mode = KeyboardPreferences.VolumeKeyCursorMode.UP_LEFT_DOWN_RIGHT,
            isInputViewShown = true,
            hasInputConnection = true,
            keyCode = KeyEvent.KEYCODE_A
        )
        assertFalse(interceptedOther)
    }
}
