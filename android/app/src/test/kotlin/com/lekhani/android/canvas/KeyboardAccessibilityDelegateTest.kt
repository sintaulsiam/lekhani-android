package com.lekhani.android.canvas

import android.view.View
import com.lekhani.android.feedback.LekhaniFeedbackManager
import com.lekhani.android.ime.EnterKeyResolver
import com.lekhani.android.model.Ch
import com.lekhani.android.model.Key
import com.lekhani.android.model.KeyAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests for [KeyboardAccessibilityDelegate.Host] contract.
 */
class KeyboardAccessibilityDelegateTest {

    private class MockHost : KeyboardAccessibilityDelegate.Host {
        override val view: View get() = throw UnsupportedOperationException("View not needed for host contract test")
        override var resolvedKeys: List<ResolvedKey> = listOf(
            ResolvedKey(
                key = Ch("ক"),
                bounds = android.graphics.RectF(0f, 0f, 100f, 100f)
            ),
            ResolvedKey(
                key = Key(label = "Space", action = KeyAction.Space, contentDesc = "Spacebar"),
                bounds = android.graphics.RectF(0f, 100f, 500f, 200f)
            )
        )
        override var isShifted: Boolean = false
        override var isCapsLock: Boolean = false
        override var isShiftActive: Boolean = false
        override var isBottomRowKeyEmoji: Boolean = false
        override var enterActionIcon = EnterKeyResolver.ActionIcon.NEWLINE
        override var feedbackManager: LekhaniFeedbackManager? = null
        override var keyListener: KeyboardCanvasView.KeyListener? = null

        override fun findKeyIndex(x: Float, y: Float): Int = if (y < 100f) 0 else 1
    }

    @Test
    fun testMockHost_findKeyIndex() {
        val host = MockHost()
        assertEquals(2, host.resolvedKeys.size)
        assertEquals(0, host.findKeyIndex(50f, 50f))
        assertEquals(1, host.findKeyIndex(50f, 150f))
    }

    @Test
    fun testMockHost_keysIntegrity() {
        val host = MockHost()
        val key0 = host.resolvedKeys[0].key
        assertEquals("ক", key0.label)
        val key1 = host.resolvedKeys[1].key
        assertEquals(KeyAction.Space, key1.action)
    }
}
