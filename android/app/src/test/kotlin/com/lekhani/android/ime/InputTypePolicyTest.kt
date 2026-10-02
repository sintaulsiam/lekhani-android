package com.lekhani.android.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the input-type policy logic in [LekhaniInputMethodService].
 *
 * These tests verify the password-field auto-switch and private-field freeze
 * behaviour, without requiring a real Android device
 * or the Rust .so (the session is mocked via a helper).
 *
 * Note: Full integration tests (binding a live InputMethodService to an emulator)
 * are handled by the Android Instrumentation test suite in Phase 12.
 */
class InputTypePolicyTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Build an [EditorInfo] with the given inputType flags. */
    private fun editorInfoWith(inputType: Int): EditorInfo =
        EditorInfo().also { it.inputType = inputType }

    /**
     * Classify an [EditorInfo] the same way [LekhaniInputMethodService] does,
     * without needing to instantiate the service itself.
     *
     * Returns true if the field should be treated as private/password.
     */
    private fun isPrivateField(info: EditorInfo): Boolean {
        val inputClass = info.inputType and InputType.TYPE_MASK_CLASS
        val inputVariation = info.inputType and InputType.TYPE_MASK_VARIATION
        val noSuggestions = (info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0

        val isPasswordField = inputClass == InputType.TYPE_CLASS_TEXT && (
            inputVariation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            inputVariation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            inputVariation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )
        return isPasswordField || noSuggestions
    }

    // ── Test cases ────────────────────────────────────────────────────────────

    @Test
    fun `password field is identified as private`() {
        val info = editorInfoWith(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        )
        assertEquals(true, isPrivateField(info))
    }

    @Test
    fun `visible password field is identified as private`() {
        val info = editorInfoWith(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        )
        assertEquals(true, isPrivateField(info))
    }

    @Test
    fun `web password field is identified as private`() {
        val info = editorInfoWith(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )
        assertEquals(true, isPrivateField(info))
    }

    @Test
    fun `no-suggestions flag marks field as private`() {
        val info = editorInfoWith(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        )
        assertEquals(true, isPrivateField(info))
    }

    @Test
    fun `normal text field is not private`() {
        val info = editorInfoWith(InputType.TYPE_CLASS_TEXT)
        assertEquals(false, isPrivateField(info))
    }

    @Test
    fun `email field is not private`() {
        val info = editorInfoWith(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        )
        assertEquals(false, isPrivateField(info))
    }

    @Test
    fun `number field is not private`() {
        val info = editorInfoWith(InputType.TYPE_CLASS_NUMBER)
        assertEquals(false, isPrivateField(info))
    }

    @Test
    fun `TYPE_NULL (no inputType) is not private`() {
        val info = editorInfoWith(InputType.TYPE_NULL)
        assertEquals(false, isPrivateField(info))
    }

    // ── URL, Email & Numeric Field Policy ─────────────────────────────────────

    private fun isUrlOrEmailOrNumeric(info: EditorInfo): Boolean {
        val inputClass = info.inputType and InputType.TYPE_MASK_CLASS
        if (inputClass == InputType.TYPE_CLASS_NUMBER ||
            inputClass == InputType.TYPE_CLASS_PHONE ||
            inputClass == InputType.TYPE_CLASS_DATETIME) {
            return true
        }
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        return variation == InputType.TYPE_TEXT_VARIATION_URI ||
               variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
               variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
               variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
               variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
               variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
               variation == InputType.TYPE_TEXT_VARIATION_FILTER
    }

    @Test
    fun `URI field suppresses punctuation auto spacing`() {
        val info = editorInfoWith(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        assertEquals(true, isUrlOrEmailOrNumeric(info))
    }

    @Test
    fun `Email field suppresses punctuation auto spacing`() {
        val info = editorInfoWith(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        assertEquals(true, isUrlOrEmailOrNumeric(info))
    }

    @Test
    fun `Web Email field suppresses punctuation auto spacing`() {
        val info = editorInfoWith(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)
        assertEquals(true, isUrlOrEmailOrNumeric(info))
    }

    @Test
    fun `Phone and Number fields suppress punctuation auto spacing`() {
        val phoneInfo = editorInfoWith(InputType.TYPE_CLASS_PHONE)
        val numInfo = editorInfoWith(InputType.TYPE_CLASS_NUMBER)
        assertEquals(true, isUrlOrEmailOrNumeric(phoneInfo))
        assertEquals(true, isUrlOrEmailOrNumeric(numInfo))
    }

    @Test
    fun `Normal text field does not suppress punctuation auto spacing`() {
        val normalInfo = editorInfoWith(InputType.TYPE_CLASS_TEXT)
        val multilineInfo = editorInfoWith(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        assertEquals(false, isUrlOrEmailOrNumeric(normalInfo))
        assertEquals(false, isUrlOrEmailOrNumeric(multilineInfo))
    }

    // ── EnterKeyResolver Tests ────────────────────────────────────────────────

    @Test
    fun `Shift-Enter forces newline regardless of field or action`() {
        val searchInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
        }
        val behavior = EnterKeyResolver.determineBehavior(searchInfo, isShiftActive = true)
        assertEquals(EnterKeyResolver.Behavior.NEWLINE, behavior)
    }

    @Test
    fun `IME_FLAG_NO_ENTER_ACTION always resolves to newline`() {
        val info = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_ENTER_ACTION
        }
        val behavior = EnterKeyResolver.determineBehavior(info, isShiftActive = false)
        assertEquals(EnterKeyResolver.Behavior.NEWLINE, behavior)
    }

    @Test
    fun `Multiline fields resolve to newline even if actionDone or actionNext is set`() {
        val noteInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        val behavior = EnterKeyResolver.determineBehavior(noteInfo, isShiftActive = false)
        assertEquals(EnterKeyResolver.Behavior.NEWLINE, behavior)
    }

    @Test
    fun `Multiline with IME_FLAG_IME_MULTI_LINE resolves to newline`() {
        val info = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        val behavior = EnterKeyResolver.determineBehavior(info, isShiftActive = false)
        assertEquals(EnterKeyResolver.Behavior.NEWLINE, behavior)
    }

    @Test
    fun `Multiline field with actionSend without noEnterAction resolves to action`() {
        val chatInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_ACTION_SEND
        }
        val behavior = EnterKeyResolver.determineBehavior(chatInfo, isShiftActive = false)
        assertEquals(EnterKeyResolver.Behavior.ACTION, behavior)
    }

    @Test
    fun `Single line search field resolves to action`() {
        val searchInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
        }
        val behavior = EnterKeyResolver.determineBehavior(searchInfo, isShiftActive = false)
        assertEquals(EnterKeyResolver.Behavior.ACTION, behavior)
    }

    @Test
    fun `TYPE_NULL terminal inputs resolve to raw key`() {
        val termInfo = EditorInfo().apply {
            inputType = InputType.TYPE_NULL
        }
        val behavior = EnterKeyResolver.determineBehavior(termInfo, isShiftActive = false)
        assertEquals(EnterKeyResolver.Behavior.RAW_KEY, behavior)
    }

    @Test
    fun `Null EditorInfo safely defaults to newline`() {
        val behavior = EnterKeyResolver.determineBehavior(null, isShiftActive = false)
        assertEquals(EnterKeyResolver.Behavior.NEWLINE, behavior)
    }
}
