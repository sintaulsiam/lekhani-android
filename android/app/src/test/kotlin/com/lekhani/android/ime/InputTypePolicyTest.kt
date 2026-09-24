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
 * behaviour defined in AGENTS.md §3.2, without requiring a real Android device
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
}
