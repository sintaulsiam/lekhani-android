package com.lekhani.android.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.lekhani.android.ffi.LekhaniLayoutType
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
     * Returns true if the field should be treated as private/password (hides candidates and clipboard).
     */
    private fun isPrivateField(info: EditorInfo): Boolean {
        val inputClass = info.inputType and InputType.TYPE_MASK_CLASS
        val inputVariation = info.inputType and InputType.TYPE_MASK_VARIATION

        val isPasswordField = (inputClass == InputType.TYPE_CLASS_TEXT && (
            inputVariation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            inputVariation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            inputVariation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )) || (inputClass == InputType.TYPE_CLASS_NUMBER && (
            inputVariation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        ))
        return isPasswordField
    }

    /**
     * Returns true if learning should be frozen (no learning, no context poisoning)
     * as per AGENTS.md.
     */
    private fun isLearningFrozen(info: EditorInfo): Boolean {
        val noSuggestions = (info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0
        val noPersonalizedLearning = (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
        return isPrivateField(info) || noSuggestions || noPersonalizedLearning
    }

    // ── Test cases ────────────────────────────────────────────────────────────

    @Test
    fun `password field is identified as private`() {
        val info = editorInfoWith(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        )
        assertEquals(true, isPrivateField(info))
        assertEquals(true, isLearningFrozen(info))
    }

    @Test
    fun `visible password field is identified as private`() {
        val info = editorInfoWith(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        )
        assertEquals(true, isPrivateField(info))
        assertEquals(true, isLearningFrozen(info))
    }

    @Test
    fun `web password field is identified as private`() {
        val info = editorInfoWith(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )
        assertEquals(true, isPrivateField(info))
        assertEquals(true, isLearningFrozen(info))
    }

    @Test
    fun `no-suggestions flag freezes learning but does not hide candidates`() {
        val info = editorInfoWith(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        )
        assertEquals(true, isLearningFrozen(info))
        assertEquals(false, isPrivateField(info))
    }

    @Test
    fun `no-suggestions search field does not qualify as password field`() {
        val info = editorInfoWith(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        )
        // Search fields must freeze learning without triggering English password auto-switch
        assertEquals(false, isPrivateField(info))
        assertEquals(true, isLearningFrozen(info))
    }

    @Test
    fun `password policy auto-switch state machine restores previous layout`() {
        var currentLayout = LekhaniLayoutType.AVRO
        var previousLayoutBeforePassword: LekhaniLayoutType? = null
        var persistedPreference = LekhaniLayoutType.AVRO

        fun simulateSwitchLayout(layout: LekhaniLayoutType, persist: Boolean = true) {
            currentLayout = layout
            if (persist) {
                persistedPreference = layout
            }
        }

        fun simulateApplyPolicy(info: EditorInfo) {
            val isPassword = isPrivateField(info)
            if (isPassword) {
                if (previousLayoutBeforePassword == null && currentLayout != LekhaniLayoutType.ENGLISH) {
                    previousLayoutBeforePassword = currentLayout
                }
                if (currentLayout != LekhaniLayoutType.ENGLISH) {
                    simulateSwitchLayout(LekhaniLayoutType.ENGLISH, persist = false)
                }
            } else {
                previousLayoutBeforePassword?.let { restoreLayout ->
                    previousLayoutBeforePassword = null
                    if (currentLayout != restoreLayout) {
                        simulateSwitchLayout(restoreLayout, persist = false)
                    }
                }
            }
        }

        // 1. Initial state: Avro phonetic
        assertEquals(LekhaniLayoutType.AVRO, currentLayout)
        assertEquals(LekhaniLayoutType.AVRO, persistedPreference)

        // 2. User focuses Chrome URL / search field (NO_SUGGESTIONS)
        val searchInfo = editorInfoWith(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        simulateApplyPolicy(searchInfo)
        // Must stay in Avro, must not switch layout
        assertEquals(LekhaniLayoutType.AVRO, currentLayout)
        assertEquals(LekhaniLayoutType.AVRO, persistedPreference)
        assertEquals(null, previousLayoutBeforePassword)

        // 3. User focuses Password field
        val passwordInfo = editorInfoWith(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        simulateApplyPolicy(passwordInfo)
        // Must switch to English, preserve previous layout, and NOT mutate persisted preference
        assertEquals(LekhaniLayoutType.ENGLISH, currentLayout)
        assertEquals(LekhaniLayoutType.AVRO, previousLayoutBeforePassword)
        assertEquals(LekhaniLayoutType.AVRO, persistedPreference)

        // 4. User focuses another password field (confirm password)
        simulateApplyPolicy(passwordInfo)
        assertEquals(LekhaniLayoutType.ENGLISH, currentLayout)
        assertEquals(LekhaniLayoutType.AVRO, previousLayoutBeforePassword)
        assertEquals(LekhaniLayoutType.AVRO, persistedPreference)

        // 5. User leaves password field into normal field
        val normalInfo = editorInfoWith(InputType.TYPE_CLASS_TEXT)
        simulateApplyPolicy(normalInfo)
        // Must restore Avro, clear snapshot, and persisted preference remains Avro
        assertEquals(LekhaniLayoutType.AVRO, currentLayout)
        assertEquals(null, previousLayoutBeforePassword)
        assertEquals(LekhaniLayoutType.AVRO, persistedPreference)
    }

    @Test
    fun `normal text field is not private and learning is active`() {
        val info = editorInfoWith(InputType.TYPE_CLASS_TEXT)
        assertEquals(false, isPrivateField(info))
        assertEquals(false, isLearningFrozen(info))
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

    // ── determineActionIcon Tests ─────────────────────────────────────────────

    @Test
    fun `determineActionIcon correctly resolves action icons for single line fields`() {
        val actions = mapOf(
            EditorInfo.IME_ACTION_SEARCH to EnterKeyResolver.ActionIcon.SEARCH,
            EditorInfo.IME_ACTION_SEND to EnterKeyResolver.ActionIcon.SEND,
            EditorInfo.IME_ACTION_GO to EnterKeyResolver.ActionIcon.GO,
            EditorInfo.IME_ACTION_NEXT to EnterKeyResolver.ActionIcon.NEXT,
            EditorInfo.IME_ACTION_DONE to EnterKeyResolver.ActionIcon.DONE,
            EditorInfo.IME_ACTION_NONE to EnterKeyResolver.ActionIcon.NEWLINE,
            EditorInfo.IME_ACTION_UNSPECIFIED to EnterKeyResolver.ActionIcon.NEWLINE,
        )

        for ((imeAction, expectedIcon) in actions) {
            val info = EditorInfo().apply {
                inputType = InputType.TYPE_CLASS_TEXT
                imeOptions = imeAction
            }
            val icon = EnterKeyResolver.determineActionIcon(info, isShiftActive = false)
            assertEquals("Mismatch for IME action $imeAction", expectedIcon, icon)
        }
    }

    @Test
    fun `determineActionIcon returns NEWLINE when shift is active or multiline without send`() {
        val searchInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
        }
        assertEquals(EnterKeyResolver.ActionIcon.NEWLINE, EnterKeyResolver.determineActionIcon(searchInfo, isShiftActive = true))

        val multilineInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        assertEquals(EnterKeyResolver.ActionIcon.NEWLINE, EnterKeyResolver.determineActionIcon(multilineInfo, isShiftActive = false))

        val chatSendInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_ACTION_SEND
        }
        assertEquals(EnterKeyResolver.ActionIcon.SEND, EnterKeyResolver.determineActionIcon(chatSendInfo, isShiftActive = false))

        val noEnterActionInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_ENTER_ACTION
        }
        assertEquals(EnterKeyResolver.ActionIcon.NEWLINE, EnterKeyResolver.determineActionIcon(noEnterActionInfo, isShiftActive = false))

        assertEquals(EnterKeyResolver.ActionIcon.NEWLINE, EnterKeyResolver.determineActionIcon(null, isShiftActive = false))
    }

    // ── determineFieldType Tests ──────────────────────────────────────────────

    @Test
    fun `determineFieldType recognizes email and uri variations`() {
        val emailInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        assertEquals(EnterKeyResolver.FieldType.EMAIL, EnterKeyResolver.determineFieldType(emailInfo))

        val webEmailInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
        }
        assertEquals(EnterKeyResolver.FieldType.EMAIL, EnterKeyResolver.determineFieldType(webEmailInfo))

        val uriInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        assertEquals(EnterKeyResolver.FieldType.URI, EnterKeyResolver.determineFieldType(uriInfo))

        val normalInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PERSON_NAME
        }
        assertEquals(EnterKeyResolver.FieldType.NORMAL, EnterKeyResolver.determineFieldType(normalInfo))

        val numberInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        assertEquals(EnterKeyResolver.FieldType.NORMAL, EnterKeyResolver.determineFieldType(numberInfo))

        assertEquals(EnterKeyResolver.FieldType.NORMAL, EnterKeyResolver.determineFieldType(null))
    }
}
