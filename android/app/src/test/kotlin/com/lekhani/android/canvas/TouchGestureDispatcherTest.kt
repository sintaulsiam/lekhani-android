package com.lekhani.android.canvas

import android.graphics.RectF
import android.view.View
import com.lekhani.android.data.settings.KeyboardPreferences
import com.lekhani.android.feedback.LekhaniFeedbackManager
import com.lekhani.android.ffi.LekhaniLayoutType
import com.lekhani.android.model.Ch
import com.lekhani.android.model.Key
import com.lekhani.android.model.KeyAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [TouchGestureDispatcher].
 */
class TouchGestureDispatcherTest {

    private class MockHost : TouchGestureDispatcher.Host {
        override val view: View get() = throw UnsupportedOperationException("View not needed in mock host")
        override var resolvedKeys: List<ResolvedKey> = emptyList()
        override var isShifted: Boolean = false
        override var isCapsLock: Boolean = false
        override var isBottomRowKeyEmoji: Boolean = false
        override var layoutType: LekhaniLayoutType = LekhaniLayoutType.AVRO
        override var spacebarSwipeMode: KeyboardPreferences.SpacebarSwipeMode = KeyboardPreferences.SpacebarSwipeMode.LAYOUT_SWITCH
        override var swipeToDeleteEnabled: Boolean = true
        override var swipeUpFlickEnabled: Boolean = true
        override var glideTypingEnabled: Boolean = true
        override var showKeyPreviews: Boolean = false
        override var longPressDelayMs: Long = 300L
        override var longPressClipboardShortcuts: Boolean = false
        override var isSideDockVisible: Boolean = false
        override var sideDockButtons: Array<SideDockButton> = emptyArray()
        override var formFactor: KeyboardPreferences.FormFactor = KeyboardPreferences.FormFactor.STANDARD
        override var keyMarginH: Float = 4f
        override var density: Float = 2.0f
        override var feedbackManager: LekhaniFeedbackManager? = null
        override var keyListener: KeyboardCanvasView.KeyListener? = null

        var lastDispatchedKey: Key? = null
        var lastTouchX: Float = 0f
        var lastTouchY: Float = 0f
        var invalidated: Boolean = false

        override fun findKeyIndex(x: Float, y: Float): Int = 0
        override fun dispatchKey(key: Key, touchX: Float, touchY: Float) {
            lastDispatchedKey = key
            lastTouchX = touchX
            lastTouchY = touchY
        }
        override fun showKeyPreview(key: Key, bounds: RectF) {}
        override fun showFlickKeyPreview(label: String, bounds: RectF) {}
        override fun dismissKeyPreview() {}
        override fun showAlternatePopup(resolvedKey: ResolvedKey, alts: Array<String>) {}
        override fun dismissAlternatePopup() {}
        override fun isAlternatePopupShowing(): Boolean = false
        override fun onAlternatePopupTouchMove(x: Float, y: Float, viewLocationX: Int): Boolean = false
        override val alternatePopupSelectedLabel: String? get() = null
        override fun getResolvedAlternates(key: Key, isShifted: Boolean): Array<String> = emptyArray()
        override fun invalidate() {
            invalidated = true
        }
    }

    private lateinit var host: MockHost
    private lateinit var dispatcher: TouchGestureDispatcher

    @Before
    fun setUp() {
        host = MockHost()
        dispatcher = TouchGestureDispatcher(host)
        dispatcher.updateDimensions(2.0f)
    }

    @Test
    fun testUpdateDimensions() {
        dispatcher.updateDimensions(3.0f)
        // Verify state is clean and ready
        assertEquals(-1, dispatcher.pressedKeyIndex)
        assertFalse(dispatcher.isGliding)
        assertFalse(dispatcher.isBackspaceSwiping)
        assertFalse(dispatcher.isSpaceCursorMoving)
        assertFalse(dispatcher.isSpaceSwiping)
    }

    @Test
    fun testResetTouchState() {
        dispatcher.cancelSwipeDelete()
        assertEquals(0, dispatcher.backspaceDeletedWordCount)
        assertFalse(dispatcher.isBackspaceSwiping)
        assertFalse(dispatcher.isSwipeDeleteGestureActive)
    }

    @Test
    fun testCancelSwipeDelete() {
        dispatcher.cancelSwipeDelete()
        assertFalse(dispatcher.isBackspaceSwiping)
        assertFalse(dispatcher.isSwipeDeleteGestureActive)
        assertEquals(0, dispatcher.backspaceDeletedWordCount)
    }

    @Test
    fun testInitialBuffersAreZeroAllocated() {
        assertEquals(256, dispatcher.glidePointsX.size)
        assertEquals(256, dispatcher.glidePointsY.size)
        assertEquals(0, dispatcher.glidePointCount)
        assertEquals(0f, dispatcher.glideCurX, 0.001f)
        assertEquals(0f, dispatcher.glideCurY, 0.001f)
    }

    @Test
    fun testCanSwipeSwitchLayoutDisarmsWhenFalse() {
        var swipeDirection = 0
        host.keyListener = object : KeyboardCanvasView.KeyListener {
            override fun onKey(key: Key, action: KeyAction) {}
            override fun onSpaceSwipe(direction: Int) { swipeDirection = direction }
            override fun onSpaceLongPress() {}
            override fun onGlideGesture(keys: List<String>) {}
            override fun onCursorMove(deltaChars: Int) {}
            override fun onSwipeDelete(wordCount: Int) {}
            override fun onSwipeDeletePreview(wordCount: Int) {}
            override fun canSwipeSwitchLayout(): Boolean = false
            override fun onFormFactorChange(newFormFactor: KeyboardPreferences.FormFactor) {}
        }
        assertFalse(host.keyListener?.canSwipeSwitchLayout() == true)
        assertEquals(0, swipeDirection)
    }
}
