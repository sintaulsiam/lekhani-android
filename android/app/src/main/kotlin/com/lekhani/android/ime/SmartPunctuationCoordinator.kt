package com.lekhani.android.ime

/**
 * SmartPunctuationCoordinator
 *
 * Encapsulates all punctuation formatting rules, auto-spacing, Dari conversions,
 * ellipsis transformations, and double-space Dari shortcuts for Lekhani Keyboard.
 *
 * Keeps the main IME service free of low-level string manipulation.
 */
object SmartPunctuationCoordinator {

    sealed class PunctuationResult {
        data class Replace(val deleteLength: Int, val textToCommit: String) : PunctuationResult()
        data class Commit(
            val finalText: String,
            val shouldDeletePrecedingSpace: Boolean,
            val isDari: Boolean,
        ) : PunctuationResult()
        data class PassThrough(val text: String) : PunctuationResult()
    }

    private val PUNCTUATION_CHARS = listOf('।', '॥', '?', '!', ';', ',', '.')

    /**
     * Resolves the punctuation formatting and succession conversions when a key commits text.
     */
    fun processCommit(
        rawText: String,
        textBefore: String?,
        isUrlOrEmailOrNumeric: Boolean,
    ): PunctuationResult {
        val text = if (isUrlOrEmailOrNumeric && rawText == "।") "." else rawText
        val isSinglePunct = !isUrlOrEmailOrNumeric && text.length == 1 && text[0] in PUNCTUATION_CHARS
        val isCommittedWordWithPunct = !isUrlOrEmailOrNumeric && text.length > 1 && text.last() in PUNCTUATION_CHARS
        val isPunctuation = isSinglePunct || isCommittedWordWithPunct

        if (!isPunctuation) {
            return PunctuationResult.PassThrough(text)
        }

        if (isSinglePunct) {
            if (text == ",") {
                val lastChar = textBefore?.lastOrNull()
                val finalText = if (lastChar != null && lastChar.isDigit()) text else "$text "
                val shouldDeleteSpace = (lastChar == ' ')
                return PunctuationResult.Commit(
                    finalText = finalText,
                    shouldDeletePrecedingSpace = shouldDeleteSpace,
                    isDari = false,
                )
            } else if (text == "।") {
                val before = textBefore ?: ""
                if (before.endsWith("।। ") || before.endsWith("।।")) {
                    // 3rd dot typed in succession -> convert to ellipsis "… "
                    val delLen = if (before.endsWith("।। ")) 3 else 2
                    return PunctuationResult.Replace(deleteLength = delLen, textToCommit = "… ")
                } else if (before.endsWith("। ") || before.endsWith("।")) {
                    // 2nd dot typed in succession -> convert to Double Dari "।। "
                    val delLen = if (before.endsWith("। ")) 2 else 1
                    return PunctuationResult.Replace(deleteLength = delLen, textToCommit = "।। ")
                } else {
                    val shouldDeleteSpace = before.endsWith(" ")
                    return PunctuationResult.Commit(
                        finalText = "$text ",
                        shouldDeletePrecedingSpace = shouldDeleteSpace,
                        isDari = true,
                    )
                }
            } else {
                val shouldDeleteSpace = textBefore?.endsWith(" ") == true
                return PunctuationResult.Commit(
                    finalText = "$text ",
                    shouldDeletePrecedingSpace = shouldDeleteSpace,
                    isDari = false,
                )
            }
        } else {
            // Committed word with punctuation
            return PunctuationResult.Commit(
                finalText = "$text ",
                shouldDeletePrecedingSpace = false,
                isDari = text.endsWith("।"),
            )
        }
    }

    /**
     * Resolves the double-tap space shortcut: converts double-space to "। " or ". ".
     */
    fun resolveDoubleSpaceShortcut(
        textBefore: String?,
        isEnglish: Boolean,
        doubleSpaceDariEnabled: Boolean,
        isComposing: Boolean,
        isUrlOrEmailOrNumeric: Boolean,
        timeDeltaMs: Long,
    ): String? {
        if (!doubleSpaceDariEnabled || isComposing || isUrlOrEmailOrNumeric || timeDeltaMs > 450) {
            return null
        }
        if (textBefore != null && textBefore.endsWith(" ") && textBefore.length >= 2) {
            val prevChar = textBefore[textBefore.length - 2]
            val isPunct = prevChar in listOf('।', '॥', '.', '?', '!', ',', ';', ':', '\n', ' ')
            if (!isPunct) {
                return if (isEnglish) ". " else "। "
            }
        }
        return null
    }
}
