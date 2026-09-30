package com.lekhani.android.ui.candidate

import android.content.Context
import androidx.core.content.edit

/**
 * CandidateBlacklist
 * ══════════════════════════════════════════════════════════════════════════════
 * Persists user-blacklisted candidates to Device Protected Storage.
 * A candidate is blacklisted when the user long-presses it in the strip
 * (FEATURES.md §2 "Candidate Blacklisting").
 *
 * Storage: A Set<String> in SharedPreferences (DPS-backed).
 * Reads: O(1) HashSet lookup — zero allocation in the candidate filtering path.
 * Writes: async (apply()) — never blocks the main thread.
 *
 * Note: The full "Personal Word Editor" UI (Phase 8) will allow viewing and
 * restoring blacklisted words. This class provides the data layer for it.
 */
class CandidateBlacklist(context: Context) {

    private val prefs = context
        .createDeviceProtectedStorageContext()
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** In-memory cache so hot-path filtering never hits SharedPreferences. */
    private val cache: HashSet<String> =
        HashSet(prefs.getStringSet(KEY_BLACKLIST, emptySet()) ?: emptySet())

    /** Returns true if [word] has been blacklisted by the user. */
    fun isBlacklisted(word: String): Boolean = word in cache

    /**
     * Add [word] to the blacklist. Persists asynchronously.
     * Idempotent — calling multiple times for the same word is safe.
     */
    fun add(word: String) {
        if (cache.add(word)) {
            prefs.edit { putStringSet(KEY_BLACKLIST, HashSet(cache)) }
        }
    }

    /**
     * Remove [word] from the blacklist (used by the Word Editor in Phase 8).
     * Persists asynchronously.
     */
    fun remove(word: String) {
        if (cache.remove(word)) {
            prefs.edit { putStringSet(KEY_BLACKLIST, HashSet(cache)) }
        }
    }

    /** Full set of blacklisted words (for the Phase 8 Word Editor screen). */
    fun all(): Set<String> = cache.toSet()

    companion object {
        private const val PREFS_NAME  = "lekhani_blacklist"
        private const val KEY_BLACKLIST = "blacklisted_words"
    }
}
