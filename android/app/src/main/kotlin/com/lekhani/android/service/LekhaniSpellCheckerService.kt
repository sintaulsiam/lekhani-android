package com.lekhani.android.service

import android.service.textservice.SpellCheckerService
import android.util.Log
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import com.lekhani.android.data.avro.AvroReverseTransliterator
import com.lekhani.android.ffi.AndroidLekhaniSession
import com.lekhani.android.ffi.LekhaniLayoutType

/**
 * LekhaniSpellCheckerService
 *
 * System-integrated, 100% on-device Android [SpellCheckerService].
 * Provides zero-telemetry Bengali and English spell checking and suggestions
 * to all Android apps and edit fields.
 *
 * Characteristics:
 *   - Zero Network: Completely local verification via Lekhani engine.
 *   - Fast execution: Cache-accelerated Trie and phonetic transliteration lookup.
 *   - Bilingual: Intelligently verifies both Bengali script and English text.
 */
class LekhaniSpellCheckerService : SpellCheckerService() {

    override fun createSession(): Session {
        return LekhaniSpellCheckerSession()
    }

    private class LekhaniSpellCheckerSession : Session() {

        private val session = AndroidLekhaniSession()

        override fun onCreate() {
            // Session initialized
        }

        override fun onGetSuggestions(textInfo: TextInfo?, suggestionsLimit: Int): SuggestionsInfo {
            val raw = textInfo?.text?.trim()
            if (raw.isNullOrEmpty()) {
                return SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY, emptyArray())
            }

            val limit = suggestionsLimit.coerceIn(1, 10)
            val isBengali = AvroReverseTransliterator.isBengaliWord(raw)

            return try {
                if (isBengali) {
                    checkBengaliWord(raw, limit)
                } else {
                    checkEnglishWord(raw, limit)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking spelling for '$raw': ${e.message}")
                SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY, emptyArray())
            }
        }

        override fun onGetSuggestionsMultiple(
            textInfos: Array<out TextInfo>?,
            suggestionsLimit: Int,
            sequentialWords: Boolean
        ): Array<SuggestionsInfo> {
            val infos = textInfos ?: return emptyArray()
            return Array(infos.size) { i ->
                onGetSuggestions(infos[i], suggestionsLimit)
            }
        }

        private fun checkBengaliWord(word: String, limit: Int): SuggestionsInfo {
            // 1. Check if word is already a known user word
            val userWords = session.getUserWords()
            if (userWords.contains(word)) {
                return SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY, emptyArray())
            }

            // 2. Check custom user autocorrect rules
            val userAutocorrect = session.getAutocorrectRules()
            if (userAutocorrect.containsKey(word)) {
                val replacement = userAutocorrect[word]
                val suggestions = if (!replacement.isNullOrEmpty()) arrayOf(replacement) else emptyArray()
                return SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO, suggestions)
            }

            // 3. Transliterate via Avro phonetic reverse-lookup to verify if recognized
            val avroKey = AvroReverseTransliterator.bengaliToAvro(word)
            session.reset()
            session.setLayout(LekhaniLayoutType.AVRO)
            val result = session.processKey(avroKey)

            if (result.candidates.isNotEmpty()) {
                val topMatch = result.candidates.first()
                if (topMatch == word) {
                    // Exact match: word is valid in lexicon
                    return SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY, emptyArray())
                }

                // Misspelled: return top alternative suggestions
                val suggestions = result.candidates
                    .filter { it != word }
                    .take(limit)
                    .toTypedArray()

                return SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO, suggestions)
            }

            // Unknown / unmatched Bengali word
            return SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO, emptyArray())
        }

        private fun checkEnglishWord(word: String, limit: Int): SuggestionsInfo {
            session.reset()
            session.setLayout(LekhaniLayoutType.ENGLISH)
            val result = session.processKey(word)

            if (result.candidates.isNotEmpty()) {
                val topMatch = result.candidates.first()
                if (topMatch.equals(word, ignoreCase = true)) {
                    return SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY, emptyArray())
                }

                val suggestions = result.candidates
                    .filter { !it.equals(word, ignoreCase = true) }
                    .take(limit)
                    .toTypedArray()

                return SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO, suggestions)
            }

            return SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO, emptyArray())
        }
    }

    companion object {
        private const val TAG = "LekhaniSpellChecker"
    }
}
