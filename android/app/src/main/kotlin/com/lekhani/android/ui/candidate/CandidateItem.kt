package com.lekhani.android.ui.candidate

/**
 * CandidateItem — a single entry in the horizontal candidate strip.
 *
 * @param text           The candidate word (NFC-normalized Bengali or English)
 * @param isPrimary      True for the centre-pinned primary suggestion
 * @param homophones     If non-null, this candidate is a known homophone of another word.
 *                       The value is the contrasting homophone shown in the disambiguation badge
 *                       (e.g. "পরা" when text == "পড়া").
 * @param isBlacklisted  True if the user has long-pressed to remove this from memory.
 *                       Blacklisted candidates are filtered out before being passed to this class;
 *                       the field exists so the UI can briefly flash a "removed" state.
 */
data class CandidateItem(
    val text: String,
    val isPrimary: Boolean = false,
    val homophones: String? = null,
    val isBlacklisted: Boolean = false,
    val isEmoji: Boolean = false,
)

/**
 * UndoInfo — metadata for rolling back an autocorrection / transliteration commit.
 *
 * @param originalText The verbatim raw text typed by user before commitment
 * @param committedText The text that was committed (including trailing space if any)
 */
data class UndoInfo(
    val originalText: String,
    val committedText: String,
)

/**
 * CandidateStripState — the full state published to the Compose strip.
 */
sealed class CandidateStripState {
    /** No composing in progress — strip is hidden or shows next-word hints */
    data object Empty : CandidateStripState()

    /**
     * Candidates are available.
     * [items] is always non-empty when this state is active.
     * [undoInfo] is optional rollback info if the previous word was substituted.
     */
    data class Candidates(
        val items: List<CandidateItem>,
        val undoInfo: UndoInfo? = null,
    ) : CandidateStripState()

    /**
     * Real-time Emoji Search Mode.
     * Displays current search query and live matching emojis above the keyboard.
     */
    data class EmojiSearch(val query: String, val emojis: List<String>) : CandidateStripState()

    /**
     * Single Undo chip mode (shown when no next-word candidates exist, but a substitution occurred).
     */
    data class Undo(val undoInfo: UndoInfo) : CandidateStripState()

    /**
     * Smart Clipboard Quick-Paste chip (OTP, URL, Email, Phone, Recent snippet).
     */
    data class QuickChip(
        val label: String,
        val icon: String,
        val pasteText: String,
    ) : CandidateStripState()

    /**
     * Mid-gesture live preview bar while dragging from Backspace.
     * Supports single-finger preview as well as two-thumb multi-touch copy/cut.
     */
    data class SwipeDeletePreview(
        val previewText: String,
        val wordCount: Int,
        val granularity: DeleteGranularity = DeleteGranularity.WORD,
        val onCopy: () -> Unit = {},
        val onCut: () -> Unit = {},
    ) : CandidateStripState()

    /**
     * Contextual text selection toolbar shown whenever text is highlighted in the active app.
     */
    data class Selection(
        val selectedText: String = "",
        val onCut: () -> Unit,
        val onCopy: () -> Unit,
        val onPaste: () -> Unit,
        val onSelectAll: () -> Unit,
        val onDelete: () -> Unit,
    ) : CandidateStripState()
}

/**
 * Granularity level for swipe deletion.
 */
enum class DeleteGranularity {
    CHAR,
    WORD,
    SENTENCE
}

