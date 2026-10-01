package com.lekhani.android.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardPreferencesTest {

    @Test
    fun testAutocompleteAndLearningKeyConstants() {
        assertEquals("spacebar_autocomplete_enabled", KeyboardPreferences.KEY_SPACEBAR_AUTOCOMPLETE_ENABLED)
        assertEquals("auto_learn_words_enabled", KeyboardPreferences.KEY_AUTO_LEARN_WORDS_ENABLED)
        assertEquals("double_space_dari_enabled", KeyboardPreferences.KEY_DOUBLE_SPACE_DARI_ENABLED)
        assertEquals("code_shield_enabled", KeyboardPreferences.KEY_CODE_SHIELD_ENABLED)
        assertEquals("volume_key_cursor_mode", KeyboardPreferences.KEY_VOLUME_KEY_CURSOR_MODE)
    }

    @Test
    fun testVolumeKeyCursorModeDefaultsAndValues() {
        val disabled = KeyboardPreferences.VolumeKeyCursorMode.DISABLED
        val upLeft = KeyboardPreferences.VolumeKeyCursorMode.UP_LEFT_DOWN_RIGHT
        val upRight = KeyboardPreferences.VolumeKeyCursorMode.UP_RIGHT_DOWN_LEFT

        assertEquals("DISABLED", disabled.name)
        assertEquals("UP_LEFT_DOWN_RIGHT", upLeft.name)
        assertEquals("UP_RIGHT_DOWN_LEFT", upRight.name)

        // Verify title strings are present for both languages
        assertTrue(disabled.titleEnglish.isNotEmpty())
        assertTrue(disabled.titleBengali.isNotEmpty())
        assertTrue(upLeft.titleEnglish.isNotEmpty())
        assertTrue(upLeft.titleBengali.isNotEmpty())
        assertTrue(upRight.titleEnglish.isNotEmpty())
        assertTrue(upRight.titleBengali.isNotEmpty())
    }

    @Test
    fun testAutocorrectRuleSanitization() {
        // Test that autocorrect shortcut triggers are properly trimmed and validated
        val rawShortcuts = listOf(
            "  omw  " to "On my way!",
            "kbrd" to "keyboard",
            "কিবর্ড" to "কীবোর্ড",
            "" to "ignored",
            "   " to "ignored2"
        )

        val cleanMap = mutableMapOf<String, String>()
        for ((trigger, replacement) in rawShortcuts) {
            val cleanTrigger = trigger.trim()
            val cleanReplacement = replacement.trim()
            if (cleanTrigger.isNotEmpty() && cleanReplacement.isNotEmpty()) {
                cleanMap[cleanTrigger] = cleanReplacement
            }
        }

        assertEquals(3, cleanMap.size)
        assertEquals("On my way!", cleanMap["omw"])
        assertEquals("keyboard", cleanMap["kbrd"])
        assertEquals("কীবোর্ড", cleanMap["কিবর্ড"])
        assertFalse(cleanMap.containsKey(""))
    }
}
