package com.lekhani.android.ui.candidate

/**
 * CandidateStripMode
 *
 * Implements the **State Pattern** for [CandidateStripView].
 * Replaces ad-hoc integer mode indices (`0..8`) and fragile conditional casting
 * with a strictly typed sealed hierarchy.
 */
sealed interface CandidateStripMode {
    data class Notice(val notice: CandidateStripState.Notice) : CandidateStripMode
    data class EmojiSearch(val search: CandidateStripState.EmojiSearch) : CandidateStripMode
    data class SwipeDeletePreview(val preview: CandidateStripState.SwipeDeletePreview) : CandidateStripMode
    data class Selection(val selection: CandidateStripState.Selection) : CandidateStripMode
    data class RetroCorrection(val chip: CandidateStripState.RetroCorrectionChip) : CandidateStripMode
    data class Undo(val undo: CandidateStripState.Undo) : CandidateStripMode
    data class QuickChip(val chip: CandidateStripState.QuickChip) : CandidateStripMode
    data class Candidates(val candidates: CandidateStripState.Candidates) : CandidateStripMode
    data object Toolbar : CandidateStripMode

    companion object {
        /**
         * Resolves the active [CandidateStripMode] from the strip state and UI flags.
         */
        fun resolve(
            state: CandidateStripState,
            hasItems: Boolean,
            isToolsMenuOpen: Boolean,
            showToolbarOverride: Boolean,
        ): CandidateStripMode {
            return when {
                state is CandidateStripState.Notice -> Notice(state)
                state is CandidateStripState.EmojiSearch -> EmojiSearch(state)
                state is CandidateStripState.SwipeDeletePreview -> SwipeDeletePreview(state)
                state is CandidateStripState.Selection -> Selection(state)
                state is CandidateStripState.RetroCorrectionChip && !isToolsMenuOpen -> RetroCorrection(state)
                hasItems && !showToolbarOverride && !isToolsMenuOpen && state is CandidateStripState.Candidates -> Candidates(state)
                state is CandidateStripState.Undo && !isToolsMenuOpen -> Undo(state)
                state is CandidateStripState.QuickChip && !isToolsMenuOpen && !showToolbarOverride -> QuickChip(state)
                else -> Toolbar
            }
        }
    }
}
