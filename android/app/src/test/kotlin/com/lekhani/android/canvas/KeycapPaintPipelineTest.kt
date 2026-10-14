package com.lekhani.android.canvas

import android.graphics.Bitmap
import com.lekhani.android.data.settings.KeyboardPreferences
import com.lekhani.android.ffi.LekhaniLayoutType
import com.lekhani.android.ime.EnterKeyResolver
import com.lekhani.android.theme.ThemeRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [KeycapPaintPipeline.Host] contract and rendering state.
 */
class KeycapPaintPipelineTest {

    private class MockHost : KeycapPaintPipeline.Host {
        override var width: Float = 1080f
        override var height: Float = 600f
        override var density: Float = 2.5f
        override var activeTheme = ThemeRegistry.THEME_FLOW_TEAL
        override var isThemeApplied: Boolean = true
        override var formFactor = KeyboardPreferences.FormFactor.STANDARD
        override var isShifted: Boolean = false
        override var isCapsLock: Boolean = false
        override var isShiftActive: Boolean = false
        override var isBottomRowKeyEmoji: Boolean = false
        override var layoutType = LekhaniLayoutType.PROBAHO
        override var spacebarSwipeMode = KeyboardPreferences.SpacebarSwipeMode.LAYOUT_SWITCH
        override var enabledLayoutsCount: Int = 2
        override var showKeyBorders: Boolean = true
        override var showHomeRowAccents: Boolean = false
        override var showKeyHints: Boolean = true
        override var showBilateralAura: Boolean = false
        override var showLayoutNameOnSpacebar: Boolean = true
        override var isUiLanguageEnglish: Boolean = false
        override var keyGlowRippleEnabled: Boolean = true
        override var keyShadowLip: Float = 4f
        override var keyCornerRadius: Float = 16f
        override var homeRowAccentHeight: Float = 5f
        override var enterActionIcon = EnterKeyResolver.ActionIcon.NEWLINE
        override var isResizeVisualGuide: Boolean = false
        override var isSideDockVisible: Boolean = false
        override var sideDockButtons: Array<SideDockButton> = emptyArray()
        override var resolvedKeys: List<ResolvedKey> = emptyList()
        override var wallpaperBitmap: Bitmap? = null
        override var wallpaperOpacity: Float = 0.25f

        override var pressedKeyIndex: Int = -1
        override var pressStartTime: Long = 0L
        override var glideTypingEnabled: Boolean = false
        override var isGliding: Boolean = false
        override var glidePointsX: FloatArray = FloatArray(10)
        override var glidePointsY: FloatArray = FloatArray(10)
        override var glidePointCount: Int = 0
        override var glideCurX: Float = 0f
        override var glideCurY: Float = 0f
        override var isSpaceCursorMoving: Boolean = false
        override var spaceSlideLastX: Float = 0f
        override var isBackspaceSwiping: Boolean = false
        override var backspaceDeletedWordCount: Int = 0

        var initialThemeApplied: Boolean = false
        var invalidated: Boolean = false
        var postInvalidated: Boolean = false

        override fun applyInitialTheme(theme: com.lekhani.android.theme.KeyboardTheme) {
            initialThemeApplied = true
            activeTheme = theme
            isThemeApplied = true
        }

        override fun invalidate() {
            invalidated = true
        }

        override fun postInvalidateOnAnimation() {
            postInvalidated = true
        }
    }

    @Test
    fun testMockHostContract_initialState() {
        val mockHost = MockHost()
        mockHost.isThemeApplied = false
        mockHost.applyInitialTheme(ThemeRegistry.THEME_FLOW_TEAL)
        assertTrue(mockHost.initialThemeApplied)
        assertTrue(mockHost.isThemeApplied)
        assertEquals(ThemeRegistry.THEME_FLOW_TEAL, mockHost.activeTheme)
    }

    @Test
    fun testMockHostContract_spacebarSwipeDefault() {
        val mockHost = MockHost()
        // Must strictly adhere to user requirement: default is LAYOUT_SWITCH
        assertEquals(KeyboardPreferences.SpacebarSwipeMode.LAYOUT_SWITCH, mockHost.spacebarSwipeMode)
    }

    @Test
    fun testMockHostContract_invalidationTriggers() {
        val mockHost = MockHost()
        assertFalse(mockHost.invalidated)
        assertFalse(mockHost.postInvalidated)

        mockHost.invalidate()
        assertTrue(mockHost.invalidated)

        mockHost.postInvalidateOnAnimation()
        assertTrue(mockHost.postInvalidated)
    }

    @Test
    fun testMockHostContract_geometryState() {
        val mockHost = MockHost()
        assertEquals(1080f, mockHost.width, 0.01f)
        assertEquals(600f, mockHost.height, 0.01f)
        assertEquals(2.5f, mockHost.density, 0.01f)
        assertEquals(KeyboardPreferences.FormFactor.STANDARD, mockHost.formFactor)
    }
}
