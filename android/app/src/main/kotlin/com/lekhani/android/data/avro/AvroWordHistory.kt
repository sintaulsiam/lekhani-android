package com.lekhani.android.data.avro

import java.util.LinkedHashMap

/**
 * AvroWordHistory
 *
 * Keeps a thread-safe LRU history of recently committed Avro words mapped to their
 * exact raw English inputs. Ensures 100% fidelity when recomposing words typed
 * during the current session (e.g. slang, contractions, abbreviations, or specific spelling variants).
 */
class AvroWordHistory(private val maxEntries: Int = 100) {

    private val cache = object : LinkedHashMap<String, String>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > maxEntries
        }
    }

    var lastCommittedWord: String? = null
        private set
    var lastCommittedRaw: String? = null
        private set
    var lastCommittedWithSpace: Boolean = false
        private set

    @Synchronized
    fun record(bengaliWord: String, rawEnglish: String, withSpace: Boolean = false) {
        if (bengaliWord.isBlank() || rawEnglish.isBlank()) return
        cache[bengaliWord] = rawEnglish
        lastCommittedWord = bengaliWord
        lastCommittedRaw = rawEnglish
        lastCommittedWithSpace = withSpace
    }

    @Synchronized
    fun get(bengaliWord: String): String? {
        return cache[bengaliWord]
    }

    @Synchronized
    fun clearLast() {
        lastCommittedWord = null
        lastCommittedRaw = null
        lastCommittedWithSpace = false
    }

    @Synchronized
    fun clear() {
        cache.clear()
        clearLast()
    }
}
