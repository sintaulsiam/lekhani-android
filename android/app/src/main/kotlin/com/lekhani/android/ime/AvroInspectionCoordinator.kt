package com.lekhani.android.ime

import android.util.Log
import android.view.inputmethod.InputConnection
import com.lekhani.android.data.avro.AvroReverseTransliterator
import com.lekhani.android.data.avro.AvroWordHistory
import com.lekhani.android.data.settings.KeyboardPreferences
import com.lekhani.android.ffi.AndroidLekhaniSession
import com.lekhani.android.ffi.LekhaniLayoutType
import com.lekhani.android.ui.candidate.CandidateBlacklist
import com.lekhani.android.ui.candidate.CandidateItem
import com.lekhani.android.ui.candidate.HomophoneAnnotator

/**
 * Ephemeral inspection state for a word at or around the cursor in Avro phonetic mode.
 *
 * @param word The full word token around the cursor
 * @param charsBeforeCursor Number of characters of the word before the cursor
 * @param charsAfterCursor Number of characters of the word after the cursor
 * @param rawPhonetic The canonical Avro English phonetic representation
 * @param previewEnglish Formatted English preview token
 * @param hasTrailingSpace Whether the inspected word was followed by a trailing space
 */
data class InspectedWord(
    val word: String,
    val charsBeforeCursor: Int,
    val charsAfterCursor: Int,
    val rawPhonetic: String,
    val previewEnglish: String,
    val hasTrailingSpace: Boolean = false,
) {
    val rawEnglish: String get() = rawPhonetic
}

sealed class AvroInspectionResult {
    object NoChange : AvroInspectionResult()
    object Clear : AvroInspectionResult()
    data class Update(
        val items: List<CandidateItem>,
        val newPredictedContext: String? = null,
    ) : AvroInspectionResult()
}

data class RecompositionPlan(
    val fullRawText: String,
)

data class BackspaceReopeningPlan(
    val rawToReopen: String,
)

data class InspectedCommitResult(
    val textCommitted: String,
    val rawPhonetic: String,
)

/**
 * AvroInspectionCoordinator
 *
 * Encapsulates:
 * - Dynamic word boundary scanning at/around cursor without mutating document
 * - Generation of Avro English preview tokens (Slot 0/Slot 1) and candidate suggestions
 * - Dynamic recomposition when typing keys into an already-committed word
 * - Word reopening on backspace
 * - Replacement of inspected word when tapping candidate strip
 */
class AvroInspectionCoordinator(
    private val wordInspectionSession: AndroidLekhaniSession,
    private val avroHistory: AvroWordHistory,
    private val blacklist: CandidateBlacklist,
    private val keyboardPrefs: KeyboardPreferences,
) {
    companion object {
        private const val TAG = "AvroInspectionCoord"
        private const val CONTEXT_CHAR_LIMIT = 256
        val SMART_PUNCTUATION_CHARS = setOf('।', '॥', '.', ',', '?', '!', ';', ':', ')', ']', '}', '"', '\'', '\n')

        fun isWordChar(c: Char): Boolean {
            if (c.isWhitespace()) return false
            if (c == '।' || c == '॥' || c == ',' || c == '.' || c == '?' || c == '!' ||
                c == ';' || c == ':' || c == '"' || c == '\'' || c == '(' || c == ')' ||
                c == '[' || c == ']' || c == '{' || c == '}' || c == '<' || c == '>' ||
                c == '/' || c == '\\' || c == '@' || c == '#' || c == '$' || c == '%' ||
                c == '^' || c == '&' || c == '*' || c == '+' || c == '=' || c == '~' || c == '`') {
                return false
            }
            return true
        }

        fun isAvroPhoneticKey(keyToken: String): Boolean {
            if (keyToken.length != 1) return false
            val c = keyToken[0]
            return (c in 'a'..'z') || (c in 'A'..'Z') || (c in '0'..'9') || c == ':' || c == '^' || c == '`'
        }

        /**
         * Checks if a key token is a valid alphabetic / phonetic key that should trigger
         * dynamic recomposition into an already committed word.
         *
         * Digits (0-9), colons (:), circumflex (^), and punctuation marks must NEVER
         * trigger dynamic recomposition, preventing existing words from being unexpectedly
         * swallowed when typing numbers, lists, or symbols right after words.
         */
        fun isAvroRecompositionKey(keyToken: String): Boolean {
            if (keyToken.length != 1) return false
            val c = keyToken[0]
            return (c in 'a'..'z') || (c in 'A'..'Z') || c == '`' || c == '\''
        }
    }

    var activeInspectedWord: InspectedWord? = null
        private set

    fun clear() {
        activeInspectedWord = null
    }

    fun setInspectedWord(word: InspectedWord?) {
        activeInspectedWord = word
    }

    /**
     * Inspects word at cursor and returns candidate items or prediction updates.
     */
    fun inspectWordAtCursor(
        ic: InputConnection,
        mainSession: AndroidLekhaniSession,
        currentCandidateItems: List<CandidateItem>?,
        lastPredictedContext: String?,
        suppressNextWordAfterCommit: Boolean,
    ): AvroInspectionResult {
        val before = try { ic.getTextBeforeCursor(CONTEXT_CHAR_LIMIT, 0)?.toString() } catch (_: Exception) { null } ?: ""
        val after = try { ic.getTextAfterCursor(64, 0)?.toString() } catch (_: Exception) { null } ?: ""
        return inspectWordAtCursorText(
            before = before,
            after = after,
            mainSession = mainSession,
            currentCandidateItems = currentCandidateItems,
            lastPredictedContext = lastPredictedContext,
            suppressNextWordAfterCommit = suppressNextWordAfterCommit,
        )
    }

    /**
     * Pure text + Rust inspection step with zero Binder IPC, safe to run on Dispatchers.Default.
     */
    fun inspectWordAtCursorText(
        before: String,
        after: String,
        mainSession: AndroidLekhaniSession,
        currentCandidateItems: List<CandidateItem>?,
        lastPredictedContext: String?,
        suppressNextWordAfterCommit: Boolean,
    ): AvroInspectionResult {

        // Backward scan in before to find the start of the current word
        var beforeWordCount = 0
        var i = before.length - 1
        while (i >= 0 && isWordChar(before[i])) {
            beforeWordCount++
            i--
        }

        // Forward scan in after to find the end of the current word
        var afterWordCount = 0
        var j = 0
        while (j < after.length && isWordChar(after[j])) {
            afterWordCount++
            j++
        }

        val totalWordLen = beforeWordCount + afterWordCount

        if (totalWordLen == 0) {
            val trimmedBefore = before.trimEnd()
            if (trimmedBefore.isNotEmpty() && !suppressNextWordAfterCommit &&
                keyboardPrefs.isNextWordActive(mainSession.getLayout())) {
                if (trimmedBefore == lastPredictedContext && !currentCandidateItems.isNullOrEmpty()) {
                    return AvroInspectionResult.NoChange
                }
                mainSession.setContext(trimmedBefore)
                val nextWords = try {
                    mainSession.predictNextWords(5u)
                } catch (_: Exception) {
                    emptyList()
                }
                if (nextWords.isNotEmpty()) {
                    activeInspectedWord = null
                    val annotated = HomophoneAnnotator.annotate(nextWords, primaryIdx = 0, verbatimIdx = -1)
                    if (currentCandidateItems != annotated) {
                        return AvroInspectionResult.Update(annotated, newPredictedContext = trimmedBefore)
                    }
                    return AvroInspectionResult.NoChange
                }
            }
            if (activeInspectedWord != null || !currentCandidateItems.isNullOrEmpty()) {
                activeInspectedWord = null
                return AvroInspectionResult.Clear
            }
            return AvroInspectionResult.NoChange
        }

        val word = before.substring(before.length - beforeWordCount) + after.substring(0, afterWordCount)
        val isBengali = AvroReverseTransliterator.isBengaliWord(word)
        val isAsciiWord = !isBengali && word.isNotEmpty() && word.all { it in 'a'..'z' || it in 'A'..'Z' }
        if (!isBengali && !isAsciiWord) {
            val trimmedBefore = before.trimEnd()
            if (trimmedBefore.isNotEmpty() && !suppressNextWordAfterCommit &&
                keyboardPrefs.isNextWordActive(mainSession.getLayout())) {
                mainSession.setContext(trimmedBefore)
                val nextWords = try { mainSession.predictNextWords(5u) } catch (_: Exception) { emptyList() }
                if (nextWords.isNotEmpty()) {
                    activeInspectedWord = null
                    val annotated = HomophoneAnnotator.annotate(nextWords, primaryIdx = 0, verbatimIdx = -1)
                    if (currentCandidateItems != annotated) {
                        return AvroInspectionResult.Update(annotated)
                    }
                    return AvroInspectionResult.NoChange
                }
            }
            if (activeInspectedWord != null) {
                activeInspectedWord = null
                return AvroInspectionResult.Clear
            }
            return AvroInspectionResult.NoChange
        }

        val rawEnglish = if (isBengali) {
            avroHistory.get(word) ?: AvroReverseTransliterator.bengaliToAvro(word)
        } else {
            word
        }
        if (rawEnglish.isBlank()) {
            val trimmedBefore = before.trimEnd()
            if (trimmedBefore.isNotEmpty() && !suppressNextWordAfterCommit &&
                keyboardPrefs.isNextWordActive(mainSession.getLayout())) {
                mainSession.setContext(trimmedBefore)
                val nextWords = try { mainSession.predictNextWords(5u) } catch (_: Exception) { emptyList() }
                if (nextWords.isNotEmpty()) {
                    activeInspectedWord = null
                    val annotated = HomophoneAnnotator.annotate(nextWords, primaryIdx = 0, verbatimIdx = -1)
                    if (currentCandidateItems != annotated) {
                        return AvroInspectionResult.Update(annotated)
                    }
                    return AvroInspectionResult.NoChange
                }
            }
            if (activeInspectedWord != null) {
                activeInspectedWord = null
                return AvroInspectionResult.Clear
            }
            return AvroInspectionResult.NoChange
        }

        // Format English preview token in clean Title Case (e.g. "Amar", "Sonar", "Bangla") matching Gboard
        val formattedEnglish = rawEnglish.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

        // Zero-allocation & zero-flicker deduplication:
        // If navigating within or over the same word, just update cursor offsets without re-querying
        val prevInspected = activeInspectedWord
        if (prevInspected != null && prevInspected.word == word && prevInspected.rawPhonetic == rawEnglish) {
            activeInspectedWord = prevInspected.copy(
                charsBeforeCursor = beforeWordCount,
                charsAfterCursor = afterWordCount,
                hasTrailingSpace = false
            )
            return AvroInspectionResult.NoChange
        }

        activeInspectedWord = InspectedWord(
            word = word,
            charsBeforeCursor = beforeWordCount,
            charsAfterCursor = afterWordCount,
            rawPhonetic = rawEnglish,
            previewEnglish = formattedEnglish,
            hasTrailingSpace = false
        )

        // Query candidate suggestions from the Avro engine via wordInspectionSession with surrounding context
        val candidatesList = mutableListOf<String>()
        try {
            wordInspectionSession.reset()
            val sentenceBefore = before.substring(0, (before.length - beforeWordCount).coerceAtLeast(0)).trim()
            if (sentenceBefore.isNotEmpty()) {
                wordInspectionSession.setContext(sentenceBefore.takeLast(CONTEXT_CHAR_LIMIT))
            }
            val sentenceAfter = after.substring(afterWordCount.coerceAtMost(after.length)).trim()
            if (sentenceAfter.isNotEmpty()) {
                wordInspectionSession.setRightContext(sentenceAfter.take(64))
            }
            var lastRes: com.lekhani.android.ffi.TypingResult? = null
            for (ch in rawEnglish) {
                lastRes = wordInspectionSession.processKey(ch.toString())
            }
            if (lastRes != null) {
                candidatesList.addAll(lastRes.candidates)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying wordInspectionSession: ${e.message}")
        }

        // Build candidate list
        val displayCandidates = mutableListOf<String>()
        val primaryCandidate = if (isBengali) word else (candidatesList.firstOrNull() ?: word)
        val isBengaliFirst = keyboardPrefs.avroStripOrder == KeyboardPreferences.STRIP_ORDER_BENGALI_FIRST

        if (keyboardPrefs.avroShowEnglishPreview) {
            if (isBengaliFirst) {
                displayCandidates.add(primaryCandidate)
                displayCandidates.add(formattedEnglish)
            } else {
                displayCandidates.add(formattedEnglish)
                displayCandidates.add(primaryCandidate)
            }
        } else {
            displayCandidates.add(primaryCandidate)
        }

        for (cand in candidatesList) {
            val isEnglishDup = cand.equals(formattedEnglish, ignoreCase = true) || cand.equals(rawEnglish, ignoreCase = true)
            if (!isEnglishDup && !displayCandidates.contains(cand) && !blacklist.isBlacklisted(cand)) {
                displayCandidates.add(cand)
            }
        }

        val primaryIdx = if (!keyboardPrefs.avroShowEnglishPreview || isBengaliFirst) 0 else 1
        val verbatimIdx = if (!keyboardPrefs.avroShowEnglishPreview) -1 else if (isBengaliFirst) 1 else 0

        val annotated = HomophoneAnnotator.annotate(
            displayCandidates,
            primaryIdx = primaryIdx,
            verbatimIdx = verbatimIdx
        )

        if (currentCandidateItems != annotated) {
            return AvroInspectionResult.Update(annotated)
        }
        return AvroInspectionResult.NoChange
    }

    /**
     * Prepares dynamic recomposition when typing an Avro phonetic key into an inspected word.
     */
    fun prepareDynamicRecomposition(
        keyToken: String,
        ic: InputConnection,
    ): RecompositionPlan? {
        val inspected = activeInspectedWord ?: return null
        if (!keyboardPrefs.avroDynamicRecomposition || !isAvroRecompositionKey(keyToken)) return null

        val rawPrefix = if (inspected.charsAfterCursor == 0) {
            inspected.rawPhonetic
        } else {
            val prefixText = inspected.word.take(inspected.charsBeforeCursor)
            avroHistory.get(prefixText) ?: AvroReverseTransliterator.bengaliToAvro(prefixText)
        }

        if (rawPrefix.isBlank()) return null

        activeInspectedWord = null
        ic.beginBatchEdit()
        try {
            if (inspected.charsBeforeCursor > 0) {
                ic.deleteSurroundingText(inspected.charsBeforeCursor, 0)
            }
        } finally {
            ic.endBatchEdit()
        }

        return RecompositionPlan(fullRawText = rawPrefix + keyToken)
    }

    /**
     * Prepares backspace reopening for an inspected word where the cursor is at the end.
     *
     * Only engages when [isRecentlyCommitted] is true (i.e. the word was just committed
     * in the active typing flow within the recent commit window). If the user navigated
     * to an old or arbitrary word, this returns null so Backspace behaves deterministically
     * as a standard single-character eraser.
     */
    fun prepareBackspaceReopening(
        ic: InputConnection,
        isRecentlyCommitted: Boolean = true,
    ): BackspaceReopeningPlan? {
        val inspected = activeInspectedWord ?: return null
        if (!isRecentlyCommitted || inspected.charsAfterCursor != 0 || !keyboardPrefs.avroPhoneticBackspaceReopening || inspected.rawPhonetic.isBlank()) {
            return null
        }

        val rawToReopen = inspected.rawPhonetic.dropLast(1)
        activeInspectedWord = null
        ic.beginBatchEdit()
        try {
            if (inspected.charsBeforeCursor > 0) {
                ic.deleteSurroundingText(inspected.charsBeforeCursor, 0)
            }
        } finally {
            ic.endBatchEdit()
        }

        return BackspaceReopeningPlan(rawToReopen = rawToReopen)
    }

    /**
     * Commits a chosen candidate replacing the inspected word in the document.
     */
    fun commitCandidateForInspectedWord(
        actualCandidate: String,
        ic: InputConnection,
    ): InspectedCommitResult? {
        val inspected = activeInspectedWord ?: return null
        activeInspectedWord = null

        var textCommitted = actualCandidate
        ic.beginBatchEdit()
        try {
            if (inspected.charsBeforeCursor > 0 || inspected.charsAfterCursor > 0) {
                ic.deleteSurroundingText(inspected.charsBeforeCursor, inspected.charsAfterCursor)
            }
            val charAfter = try { ic.getTextAfterCursor(1, 0)?.toString()?.firstOrNull() } catch (_: Exception) { null }
            val shouldOmitTrailingSpace = keyboardPrefs.smartPunctuationSpacing && charAfter != null && charAfter in SMART_PUNCTUATION_CHARS
            textCommitted = if (shouldOmitTrailingSpace || !inspected.hasTrailingSpace) actualCandidate else "$actualCandidate "
            ic.commitText(textCommitted, 1)
        } finally {
            ic.endBatchEdit()
        }

        return InspectedCommitResult(
            textCommitted = textCommitted,
            rawPhonetic = inspected.rawPhonetic,
        )
    }
}
