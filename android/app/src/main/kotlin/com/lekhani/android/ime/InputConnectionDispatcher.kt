package com.lekhani.android.ime

import android.os.Build
import android.view.KeyEvent
import android.view.inputmethod.InputConnection

/**
 * InputConnectionDispatcher
 *
 * Implements the **Mediator Pattern** between [LekhaniInputMethodService] and
 * Android's platform [InputConnection] IPC boundary.
 *
 * Responsibilities:
 *   - Centralizes null checks, exception handling, and IPC boundary resilience.
 *   - Encapsulates batch editing scopes with guaranteed `endBatchEdit()` unwinding.
 *   - Provides safe cursor and surrounding-text retrieval with defensive bounds.
 *   - Shields IME business logic from Android Binder quirks and Chromium/WebView inconsistencies.
 */
class InputConnectionDispatcher(
    private val connectionProvider: () -> InputConnection?,
) {

    /** Current active connection or null if detached. */
    val connection: InputConnection?
        get() = connectionProvider()

    /**
     * Commits the given text to the active text field and repositions the cursor.
     */
    fun commitText(text: CharSequence, newCursorPosition: Int = 1): Boolean {
        return try {
            connection?.commitText(text, newCursorPosition) ?: false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Sets active composing (pre-edit) text with underline or IME styling.
     */
    fun setComposingText(text: CharSequence, newCursorPosition: Int = 1): Boolean {
        return try {
            connection?.setComposingText(text, newCursorPosition) ?: false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Finalizes composing text, turning it into committed text without altering contents.
     */
    fun finishComposingText(): Boolean {
        return try {
            connection?.finishComposingText() ?: false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Deletes [beforeLength] characters before the cursor and [afterLength] characters after the cursor.
     */
    fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        return try {
            connection?.deleteSurroundingText(beforeLength, afterLength) ?: false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Deletes [beforeLength] unicode code points before the cursor and [afterLength] code points after.
     * Gracefully falls back to char-based deletion on Android API < 24.
     */
    fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
        return try {
            val ic = connection ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                ic.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
            } else {
                ic.deleteSurroundingText(beforeLength, afterLength)
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Dispatches a key event to the target application.
     */
    fun sendKeyEvent(event: KeyEvent): Boolean {
        return try {
            connection?.sendKeyEvent(event) ?: false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Retrieves up to [n] characters of text immediately preceding the cursor.
     */
    fun getTextBeforeCursor(n: Int, flags: Int = 0): CharSequence? {
        return try {
            connection?.getTextBeforeCursor(n, flags)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Retrieves up to [n] characters of text immediately following the cursor.
     */
    fun getTextAfterCursor(n: Int, flags: Int = 0): CharSequence? {
        return try {
            connection?.getTextAfterCursor(n, flags)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Retrieves currently selected text, or null if no text is selected or IPC fails.
     */
    fun getSelectedText(flags: Int = 0): CharSequence? {
        return try {
            connection?.getSelectedText(flags)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Repositions the selection / cursor to [start] and [end].
     */
    fun setSelection(start: Int, end: Int): Boolean {
        return try {
            connection?.setSelection(start, end) ?: false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Performs an edit action on the text field (e.g. cut, copy, paste).
     */
    fun performContextMenuAction(id: Int): Boolean {
        return try {
            connection?.performContextMenuAction(id) ?: false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Performs an editor action (e.g. IME_ACTION_SEARCH, IME_ACTION_SEND).
     */
    fun performEditorAction(editorAction: Int): Boolean {
        return try {
            connection?.performEditorAction(editorAction) ?: false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Executes [block] inside a `beginBatchEdit()` / `endBatchEdit()` transaction.
     * Ensures atomic multi-operation text updates (such as undo substitution or punctuation swaps).
     */
    inline fun <T> withBatchEdit(block: (InputConnection) -> T): T? {
        val ic = connection ?: return null
        ic.beginBatchEdit()
        return try {
            block(ic)
        } finally {
            ic.endBatchEdit()
        }
    }

    /**
     * Safely retrieves surrounding context (before and after cursor) as Strings.
     */
    fun getSurroundingContext(maxChars: Int = 64): Pair<String, String> {
        val before = try {
            getTextBeforeCursor(maxChars, 0)?.toString() ?: ""
        } catch (_: Exception) {
            ""
        }
        val after = try {
            getTextAfterCursor(maxChars, 0)?.toString() ?: ""
        } catch (_: Exception) {
            ""
        }
        return Pair(before, after)
    }
}
