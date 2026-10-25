package com.lekhani.android.ime

import com.lekhani.android.data.emoji.EmojiData
import com.lekhani.android.data.settings.KeyboardPreferences
import com.lekhani.android.data.smart.SmartAssistant
import com.lekhani.android.ffi.LekhaniLayoutType
import com.lekhani.android.ui.candidate.CandidateStripState
import com.lekhani.android.ui.candidate.HomophoneAnnotator
import com.lekhani.android.ui.candidate.UndoInfo

/**
 * CandidateStripCoordinator
 *
 * Encapsulates the entire candidate strip resolution pipeline:
 * - Blacklist filtering
 * - Avro verbatim preview & Code Shield ordering
 * - Smart Math evaluation ("500+250=" -> "= 750")
 * - Dynamic date & time query suggestions
 * - Inline contextual emojis
 * - Homophone badge & verb honorific paradigm annotations
 * - Jitter-free CandidateStripState derivation
 */
object CandidateStripCoordinator {

    data class Params(
        val rawCandidates: List<String>,
        val rawInput: String,
        val layout: LekhaniLayoutType,
        val undo: UndoInfo?,
        val englishPredictiveSuggestions: Boolean,
        val avroShowEnglishPreview: Boolean,
        val codeShieldEnabled: Boolean,
        val avroStripOrder: String,
        val isBlacklisted: (String) -> Boolean,
        val verbParadigmChecker: (String) -> Boolean,
        val lpsChips: List<com.lekhani.android.ffi.LpsLetterChipItem> = emptyList(),
        val topicAnnotator: ((List<String>) -> List<com.lekhani.android.ffi.CandidateTopicAnnotation>)? = null,
    )

    fun resolveStripState(params: Params): CandidateStripState {
        val isEngLayout = params.layout == LekhaniLayoutType.ENGLISH
        if (isEngLayout && !params.englishPredictiveSuggestions) {
            return if (params.undo != null) CandidateStripState.Undo(params.undo) else CandidateStripState.Empty
        }

        val filtered = params.rawCandidates.filter { cand ->
            !params.isBlacklisted(cand) &&
            (!isEngLayout || cand.all { it.code < 128 } || EmojiData.isEmoji(cand) || cand.startsWith("="))
        }.distinct().toMutableList()

        // 1. Avro Verbatim Token & Code Shield:
        // Configurable positioning: Bengali First (Recommended) vs English First (Classic)
        val rawInput = params.rawInput
        var primaryIndex = 0
        var verbatimIndex = -1
        if (params.layout == LekhaniLayoutType.AVRO && rawInput.isNotBlank()) {
            if (params.avroShowEnglishPreview) {
                filtered.remove(rawInput)
                val isCode = params.codeShieldEnabled && SmartAssistant.isCodeToken(rawInput)
                val isBengaliFirst = params.avroStripOrder == KeyboardPreferences.STRIP_ORDER_BENGALI_FIRST

                if (isCode) {
                    filtered.add(0, rawInput)
                    primaryIndex = 0
                    verbatimIndex = 0
                } else if (isBengaliFirst) {
                    primaryIndex = 0
                    if (filtered.isNotEmpty()) {
                        filtered.add(1, rawInput)
                        verbatimIndex = 1
                    } else {
                        filtered.add(0, rawInput)
                        verbatimIndex = 0
                    }
                } else {
                    filtered.add(0, rawInput)
                    verbatimIndex = 0
                    primaryIndex = if (filtered.size > 1) 1 else 0
                }
            } else {
                primaryIndex = 0
            }
        }

        // 2. Smart Math Evaluation: e.g. "500+250=" -> "= 750"
        val mathAnswer = SmartAssistant.evaluateMath(rawInput)
        if (mathAnswer != null && !filtered.contains("= $mathAnswer") && !filtered.contains(mathAnswer)) {
            filtered.add(0, "= $mathAnswer")
            primaryIndex = 0
        }

        // 3. Dynamic Date & Time Suggestions
        val topCandidate = filtered.getOrNull(primaryIndex) ?: filtered.firstOrNull() ?: ""
        if (rawInput.length >= 3 || topCandidate.length >= 3) {
            if (SmartAssistant.isDateQuery(rawInput) || SmartAssistant.isDateQuery(topCandidate)) {
                val dateStr = SmartAssistant.getFormattedDate(!isEngLayout)
                if (!filtered.contains(dateStr)) filtered.add(dateStr)
            }
            if (SmartAssistant.isTimeQuery(rawInput) || SmartAssistant.isTimeQuery(topCandidate)) {
                val timeStr = SmartAssistant.getFormattedTime(!isEngLayout)
                if (!filtered.contains(timeStr)) filtered.add(timeStr)
            }
        }

        // 4. Inline Contextual Emojis: Gboard-style trailing suggestions (only when token length >= 2)
        if (rawInput.length >= 2 || topCandidate.length >= 2) {
            val contextualEmojis = mutableListOf<String>()
            if (rawInput.length >= 2) {
                contextualEmojis.addAll(EmojiData.findContextualEmojis(rawInput, 2))
            }
            if (topCandidate.length >= 2 && contextualEmojis.size < 2) {
                contextualEmojis.addAll(EmojiData.findContextualEmojis(topCandidate, 2 - contextualEmojis.size))
            }
            for (emoji in contextualEmojis.distinct()) {
                if (!filtered.contains(emoji)) {
                    filtered.add(emoji)
                }
            }
        }

        if (filtered.isEmpty()) {
            return if (params.undo != null) CandidateStripState.Undo(params.undo) else CandidateStripState.Empty
        }

        val topicAnnotations = if (params.topicAnnotator != null && filtered.isNotEmpty() && !isEngLayout) {
            try {
                params.topicAnnotator.invoke(filtered)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }

        val items = HomophoneAnnotator.annotate(
            filtered,
            primaryIdx = primaryIndex,
            verbatimIdx = verbatimIndex,
            verbParadigmChecker = params.verbParadigmChecker,
            topicAnnotations = topicAnnotations,
        )

        return CandidateStripState.Candidates(
            items = items,
            undoInfo = params.undo,
            lpsChips = params.lpsChips,
        )
    }

    /**
     * Checks if emission should be skipped to prevent redundant Compose recompositions.
     */
    fun shouldSkipEmission(currentState: CandidateStripState, newState: CandidateStripState): Boolean {
        return currentState is CandidateStripState.Candidates &&
                newState is CandidateStripState.Candidates &&
                currentState.items == newState.items &&
                currentState.undoInfo == newState.undoInfo &&
                currentState.lpsChips == newState.lpsChips
    }
}
