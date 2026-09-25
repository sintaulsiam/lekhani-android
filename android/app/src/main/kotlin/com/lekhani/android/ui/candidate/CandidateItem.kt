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
     */
    data class Candidates(val items: List<CandidateItem>) : CandidateStripState()

    /**
     * Real-time Emoji Search Mode.
     * Displays current search query and live matching emojis above the keyboard.
     */
    data class EmojiSearch(val query: String, val emojis: List<String>) : CandidateStripState()
}
