package com.lekhani.android.data.dictionary

import android.content.Context
import android.net.Uri
import android.util.Log
import com.lekhani.android.ffi.AndroidLekhaniSession
import com.lekhani.android.ffi.LekhaniException
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * LekhaniDictionaryManager
 *
 * Manages custom user vocabulary, dictionary export/backup, and one-click
 * migration from Ridmik Keyboard and Desktop Avro dictionaries.
 *
 * Privacy guarantee:
 *   - 100% offline — zero network transmission
 *   - Stored locally on-device in application private storage
 */
class LekhaniDictionaryManager(
    private val session: AndroidLekhaniSession = AndroidLekhaniSession()
) {

    companion object {
        private const val TAG = "LekhaniDictMgr"
    }

    /**
     * Retrieve all personal user words currently registered in the engine.
     */
    fun getUserWords(): List<String> {
        return try {
            session.getUserWords()
        } catch (e: LekhaniException) {
            Log.e(TAG, "Error fetching user words: $e")
            emptyList()
        }
    }

    /**
     * Add a personal word to the dictionary.
     */
    fun addUserWord(word: String): Boolean {
        val trimmed = word.trim()
        if (trimmed.length < 2) return false
        return try {
            session.addUserWord(trimmed)
        } catch (e: LekhaniException) {
            Log.e(TAG, "Error adding word '$word': $e")
            false
        }
    }

    /**
     * Delete a personal word from the dictionary and candidate memory.
     */
    fun deleteUserWord(word: String): Boolean {
        val trimmed = word.trim()
        return try {
            session.deleteUserWord(trimmed)
        } catch (e: LekhaniException) {
            Log.e(TAG, "Error deleting word '$word': $e")
            false
        }
    }

    /**
     * Export the personal dictionary and associations as pretty-printed JSON.
     */
    fun exportJson(): String {
        return try {
            session.exportDictionaryJson()
        } catch (e: LekhaniException) {
            Log.e(TAG, "Error exporting dictionary JSON: $e")
            "{}"
        }
    }

    /**
     * Import a previously exported JSON backup.
     * Returns the count of imported words.
     */
    fun importJson(jsonContent: String): Int {
        return try {
            session.importDictionaryJson(jsonContent).toInt()
        } catch (e: LekhaniException) {
            Log.e(TAG, "Error importing dictionary JSON: $e")
            0
        }
    }

    /**
     * Persist user-learned vocabulary and bigrams to local binary file.
     */
    fun saveLearned(path: String): Boolean {
        return try {
            session.saveUserLearned(path)
        } catch (e: LekhaniException) {
            Log.e(TAG, "Error saving learned dictionary to $path: $e")
            false
        }
    }

    /**
     * Load user-learned vocabulary and bigrams from local binary file.
     */
    fun loadLearned(path: String): Boolean {
        return try {
            session.loadUserLearned(path)
        } catch (e: LekhaniException) {
            Log.e(TAG, "Error loading learned dictionary from $path: $e")
            false
        }
    }

    /**
     * Check if user learner has unsaved in-memory mutations.
     */
    fun isLearnedDirty(): Boolean {
        return try {
            session.isUserLearnedDirty()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Import words from plain text, CSV, Avro .txt format, or Ridmik user dictionary backup.
     *
     * Supported formats:
     *   1. Newline-separated words: "স্মার্টফোন\nল্যাপটপ"
     *   2. Avro dictionary format: "buffer=শব্দ" (e.g. "ami=আমি")
     *   3. CSV/TSV with frequency: "শব্দ,15" or "শব্দ\t15"
     */
    fun importRawWordList(rawContent: String): Int {
        val lines = rawContent.lines()
        val extractedWords = mutableListOf<String>()

        for (line in lines) {
            val clean = line.trim()
            if (clean.isBlank() || clean.startsWith("#") || clean.startsWith("//")) continue

            val word = when {
                // Avro format: input=bengali
                clean.contains("=") -> {
                    clean.substringAfter("=").trim()
                }
                // CSV format: bengali,frequency
                clean.contains(",") -> {
                    clean.substringBefore(",").trim()
                }
                // TSV format: bengali\tfrequency
                clean.contains("\t") -> {
                    clean.substringBefore("\t").trim()
                }
                // Plain word
                else -> clean
            }

            if (word.length >= 2) {
                extractedWords.add(word)
            }
        }

        if (extractedWords.isEmpty()) return 0

        return try {
            session.importRawWords(extractedWords).toInt()
        } catch (e: LekhaniException) {
            Log.e(TAG, "Error importing raw words: $e")
            0
        }
    }

    /**
     * Read content from an Android content URI (from file picker) and import.
     */
    fun importFromUri(context: Context, uri: Uri): Pair<Int, String> {
        val contentResolver = context.contentResolver
        val content = try {
            contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
            } ?: return Pair(0, "ফাইল পড়া যায়নি")
        } catch (e: Exception) {
            Log.e(TAG, "Error reading from URI: $e")
            return Pair(0, "ফাইল পড়তে সমস্যা হয়েছে: ${e.message}")
        }

        val trimmed = content.trim()
        val count = if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            importJson(trimmed)
        } else {
            importRawWordList(trimmed)
        }

        return Pair(count, "$count টি শব্দ সফলভাবে যুক্ত হয়েছে")
    }

    /**
     * Clear all user dictionary entries.
     */
    fun clearDictionary(): Boolean {
        return try {
            session.clearUserDictionary()
        } catch (e: LekhaniException) {
            Log.e(TAG, "Error clearing dictionary: $e")
            false
        }
    }

    /**
     * Retrieve count of background auto-learned words from typing stream.
     */
    fun getLearnedWordsCount(): Int {
        return try {
            session.getLearnedWordsCount().toInt()
        } catch (e: LekhaniException) {
            Log.e(TAG, "Error fetching learned words count: $e")
            0
        }
    }

    /**
     * Clear background auto-learned words while keeping explicit user words intact.
     */
    fun clearLearnedHistory(): Boolean {
        return try {
            session.clearLearnedHistory()
        } catch (e: LekhaniException) {
            Log.e(TAG, "Error clearing learned history: $e")
            false
        }
    }

    /**
     * Retrieve all custom user autocorrect / shortcut rules.
     */
    fun getAutocorrectRules(): Map<String, String> {
        return try {
            session.getAutocorrectRules()
        } catch (e: LekhaniException) {
            Log.e(TAG, "Error fetching autocorrect rules: $e")
            emptyMap()
        }
    }

    /**
     * Add a custom user autocorrect / shortcut rule.
     */
    fun addAutocorrectRule(trigger: String, replacement: String): Boolean {
        val cleanTrig = trigger.trim()
        val cleanRepl = replacement.trim()
        if (cleanTrig.isEmpty() || cleanRepl.isEmpty()) return false
        return try {
            session.addAutocorrectRule(cleanTrig, cleanRepl)
        } catch (e: LekhaniException) {
            Log.e(TAG, "Error adding autocorrect rule: $e")
            false
        }
    }

    /**
     * Delete a custom user autocorrect / shortcut rule.
     */
    fun deleteAutocorrectRule(trigger: String): Boolean {
        return try {
            session.deleteAutocorrectRule(trigger.trim())
        } catch (e: LekhaniException) {
            Log.e(TAG, "Error deleting autocorrect rule: $e")
            false
        }
    }

    /**
     * Clear all custom user autocorrect rules.
     */
    fun clearAutocorrectRules(): Boolean {
        return try {
            session.clearAutocorrectRules()
        } catch (e: LekhaniException) {
            Log.e(TAG, "Error clearing autocorrect rules: $e")
            false
        }
    }

    /**
     * Save custom user autocorrect rules to JSON file.
     */
    fun saveAutocorrect(path: String): Boolean {
        return try {
            session.saveUserAutocorrect(path)
        } catch (e: LekhaniException) {
            Log.e(TAG, "Error saving autocorrect rules to $path: $e")
            false
        }
    }

    /**
     * Load custom user autocorrect rules from JSON file.
     */
    fun loadAutocorrect(path: String): Boolean {
        return try {
            session.loadUserAutocorrect(path)
        } catch (e: LekhaniException) {
            Log.e(TAG, "Error loading autocorrect rules from $path: $e")
            false
        }
    }
}
