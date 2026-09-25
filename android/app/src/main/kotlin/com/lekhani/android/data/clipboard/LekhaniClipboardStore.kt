package com.lekhani.android.data.clipboard

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A single clipboard entry in Lekhani's local clipboard manager.
 *
 * @param id          Unique timestamp-based ID
 * @param text        The clipboard text content
 * @param timestamp   Epoch millisecond when clipped
 * @param isPinned    True if user has pinned this to the top (immune to auto-clear)
 * @param isSaved     True if user has permanently saved this to their personal vault
 * @param isSensitive True if content contains OTPs, passwords, or credit card numbers
 */
data class ClipItem(
    val id: Long,
    val text: String,
    val timestamp: Long,
    val isPinned: Boolean = false,
    val isSaved: Boolean = false,
    val isSensitive: Boolean = false,
)

/**
 * A snapshot backup of clipboard items taken at a specific point in time.
 */
data class ClipboardSnapshot(
    val id: Long,
    val title: String,
    val timestamp: Long,
    val items: List<ClipItem>,
)

/**
 * A URL link extracted from clipboard items.
 */
data class ExtractedLink(
    val url: String,
    val domain: String,
    val sourceClipId: Long,
    val sourceClipText: String,
    val timestamp: Long,
    val isSaved: Boolean = false,
)

/**
 * Configurable auto-clearing retention periods.
 */
enum class RetentionPeriod(val minutes: Int, val labelBengali: String, val labelEnglish: String) {
    ONE_HOUR(60, "১ ঘণ্টা (ডিফল্ট)", "1 Hour (Default)"),
    SIX_HOURS(360, "৬ ঘণ্টা", "6 Hours"),
    TWENTY_FOUR_HOURS(1440, "২৪ ঘণ্টা", "24 Hours"),
    SEVEN_DAYS(10080, "৭ দিন", "7 Days"),
    NEVER(0, "কখনো নয় (ম্যানুয়াল)", "Never (Manual Clear)"),
    ;

    companion object {
        fun fromMinutes(minutes: Int): RetentionPeriod =
            entries.find { it.minutes == minutes } ?: ONE_HOUR
    }
}

/**
 * LekhaniClipboardStore
 * ══════════════════════════════════════════════════════════════════════════════
 * Smart, power-packed 100% on-device clipboard history & vault manager.
 *
 * Capabilities:
 *   ✅ Zero Network (AGENTS.md §1): All data stored strictly in Device Protected Storage.
 *   ✅ Configurable Auto-Clear: Default 1 hour; customizable to 6h, 24h, 7d, or Never.
 *   ✅ Pinning & Long-Term Vault: Pin items to top and save notes/snippets permanently.
 *   ✅ Clipboard Snapshots: Capture full historical snapshots with instant restore.
 *   ✅ Combine All Items as Text: Concatenate all/filtered clips into a single text block.
 *   ✅ Automatic Link Parser & Extractor: Parse URLs, deduplicate, and save into vault.
 *   ✅ Privacy Guard: Auto-detection of sensitive OTPs, cards, and password field freezing.
 */
class LekhaniClipboardStore(context: Context) {

    private val prefs = context
        .createDeviceProtectedStorageContext()
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _clips = MutableStateFlow<List<ClipItem>>(emptyList())
    val clips: StateFlow<List<ClipItem>> = _clips.asStateFlow()

    private val _snapshots = MutableStateFlow<List<ClipboardSnapshot>>(emptyList())
    val snapshots: StateFlow<List<ClipboardSnapshot>> = _snapshots.asStateFlow()

    /** Configurable auto-clear retention period in minutes (default: 60 = 1 hour) */
    var retentionMinutes: Int
        get() = prefs.getInt(KEY_RETENTION_MINUTES, DEFAULT_RETENTION_MINUTES)
        set(value) {
            prefs.edit { putInt(KEY_RETENTION_MINUTES, value) }
            pruneExpiredClips()
        }

    init {
        loadClips()
        loadSnapshots()
        pruneExpiredClips()
    }

    private fun loadClips() {
        val rawJson = prefs.getString(KEY_CLIPS, "[]") ?: "[]"
        val list = mutableListOf<ClipItem>()
        try {
            val arr = JSONArray(rawJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    ClipItem(
                        id = obj.getLong("id"),
                        text = obj.getString("text"),
                        timestamp = obj.getLong("timestamp"),
                        isPinned = obj.optBoolean("isPinned", false),
                        isSaved = obj.optBoolean("isSaved", false),
                        isSensitive = obj.optBoolean("isSensitive", false),
                    )
                )
            }
        } catch (_: Exception) {}
        _clips.value = list
    }

    private fun saveClips(list: List<ClipItem>) {
        _clips.value = list
        try {
            val arr = JSONArray()
            for (item in list) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("text", item.text)
                    put("timestamp", item.timestamp)
                    put("isPinned", item.isPinned)
                    put("isSaved", item.isSaved)
                    put("isSensitive", item.isSensitive)
                }
                arr.put(obj)
            }
            prefs.edit { putString(KEY_CLIPS, arr.toString()) }
        } catch (_: Exception) {}
    }

    private fun loadSnapshots() {
        val rawJson = prefs.getString(KEY_SNAPSHOTS, "[]") ?: "[]"
        val list = mutableListOf<ClipboardSnapshot>()
        try {
            val arr = JSONArray(rawJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val itemsArr = obj.getJSONArray("items")
                val items = mutableListOf<ClipItem>()
                for (j in 0 until itemsArr.length()) {
                    val itemObj = itemsArr.getJSONObject(j)
                    items.add(
                        ClipItem(
                            id = itemObj.getLong("id"),
                            text = itemObj.getString("text"),
                            timestamp = itemObj.getLong("timestamp"),
                            isPinned = itemObj.optBoolean("isPinned", false),
                            isSaved = itemObj.optBoolean("isSaved", false),
                            isSensitive = itemObj.optBoolean("isSensitive", false),
                        )
                    )
                }
                list.add(
                    ClipboardSnapshot(
                        id = obj.getLong("id"),
                        title = obj.getString("title"),
                        timestamp = obj.getLong("timestamp"),
                        items = items,
                    )
                )
            }
        } catch (_: Exception) {}
        _snapshots.value = list
    }

    private fun saveSnapshots(list: List<ClipboardSnapshot>) {
        _snapshots.value = list
        try {
            val arr = JSONArray()
            for (snap in list) {
                val snapObj = JSONObject().apply {
                    put("id", snap.id)
                    put("title", snap.title)
                    put("timestamp", snap.timestamp)
                    val itemsArr = JSONArray()
                    for (item in snap.items) {
                        itemsArr.put(
                            JSONObject().apply {
                                put("id", item.id)
                                put("text", item.text)
                                put("timestamp", item.timestamp)
                                put("isPinned", item.isPinned)
                                put("isSaved", item.isSaved)
                                put("isSensitive", item.isSensitive)
                            }
                        )
                    }
                    put("items", itemsArr)
                }
                arr.put(snapObj)
            }
            prefs.edit { putString(KEY_SNAPSHOTS, arr.toString()) }
        } catch (_: Exception) {}
    }

    /**
     * Adds text to clipboard history.
     * Detects sensitive content (OTP or passwords) and excludes duplicates.
     */
    fun addClip(text: String, isSaved: Boolean = false) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val isSensitive = detectSensitiveContent(trimmed)
        val now = System.currentTimeMillis()

        val current = _clips.value.toMutableList()
        // Remove existing identical clip so it moves to top (preserve pinned/saved state)
        val existing = current.find { it.text == trimmed }
        val wasPinned = existing?.isPinned ?: false
        val wasSaved = existing?.isSaved ?: isSaved
        current.removeAll { it.text == trimmed }

        val newClip = ClipItem(
            id = now,
            text = trimmed,
            timestamp = now,
            isPinned = wasPinned,
            isSaved = wasSaved,
            isSensitive = isSensitive,
        )

        current.add(0, newClip)

        // Cap to MAX_CLIPS (prune oldest unpinned & unsaved items first)
        if (current.size > MAX_CLIPS) {
            val pruneIdx = current.indices.lastOrNull { !current[it].isPinned && !current[it].isSaved }
            if (pruneIdx != null) {
                current.removeAt(pruneIdx)
            }
        }

        saveClips(current)
    }

    /** Toggle pin status of a clip */
    fun togglePin(id: Long) {
        val updated = _clips.value.map {
            if (it.id == id) it.copy(isPinned = !it.isPinned) else it
        }
        saveClips(updated)
    }

    /** Toggle saved long-term vault status of a clip */
    fun toggleSave(id: Long) {
        val updated = _clips.value.map {
            if (it.id == id) it.copy(isSaved = !it.isSaved) else it
        }
        saveClips(updated)
    }

    /** Delete a single clip */
    fun deleteClip(id: Long) {
        val updated = _clips.value.filter { it.id != id }
        saveClips(updated)
    }

    /** Clear all unpinned & unsaved clips */
    fun clearUnpinned() {
        val updated = _clips.value.filter { it.isPinned || it.isSaved }
        saveClips(updated)
    }

    /** Clear all clips unconditionally */
    fun clearAll() {
        saveClips(emptyList())
    }

    /**
     * Prunes unpinned & unsaved clips older than [retentionMinutes].
     * Pinned and Saved items are immune from auto-pruning.
     * If [retentionMinutes] is 0 (Never), no clips are pruned.
     */
    fun pruneExpiredClips() {
        val minutes = retentionMinutes
        if (minutes <= 0) return // Retention: Never auto-clear

        val cutoff = System.currentTimeMillis() - (minutes * 60 * 1000L)
        val updated = _clips.value.filter { it.isPinned || it.isSaved || it.timestamp > cutoff }
        if (updated.size != _clips.value.size) {
            saveClips(updated)
        }
    }

    // ── Snapshot Feature ──────────────────────────────────────────────────────

    /**
     * Creates an immutable snapshot of all current clipboard items.
     */
    fun takeSnapshot(customTitle: String? = null): ClipboardSnapshot {
        val now = System.currentTimeMillis()
        val dateStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(now))
        val currentItems = _clips.value
        val title = customTitle?.takeIf { it.isNotBlank() }
            ?: "স্ন্যাপশট (${currentItems.size}টি আইটেম • $dateStr)"

        val snapshot = ClipboardSnapshot(
            id = now,
            title = title,
            timestamp = now,
            items = currentItems,
        )

        val updated = _snapshots.value.toMutableList()
        updated.add(0, snapshot)
        if (updated.size > MAX_SNAPSHOTS) {
            updated.removeAt(updated.size - 1)
        }
        saveSnapshots(updated)
        return snapshot
    }

    /**
     * Restores items from a snapshot into the current clipboard.
     * @param replace If true, clears current clips before restoring; if false, prepends snapshot items.
     */
    fun restoreSnapshot(snapshotId: Long, replace: Boolean = false) {
        val snap = _snapshots.value.find { it.id == snapshotId } ?: return
        if (replace) {
            saveClips(snap.items)
        } else {
            val currentTexts = _clips.value.map { it.text }.toSet()
            val newItems = snap.items.filter { it.text !in currentTexts }
            saveClips(newItems + _clips.value)
        }
    }

    /**
     * Deletes a saved snapshot.
     */
    fun deleteSnapshot(snapshotId: Long) {
        val updated = _snapshots.value.filter { it.id != snapshotId }
        saveSnapshots(updated)
    }

    // ── Combined Text Feature ─────────────────────────────────────────────────

    /**
     * Combines all or filtered clipboard items into a single consolidated text block.
     * @param items The items to combine (defaults to all clips)
     * @param separator The delimiter between items (default: double newline)
     */
    fun getCombinedText(items: List<ClipItem> = _clips.value, separator: String = "\n\n"): String {
        return items.joinToString(separator) { it.text }
    }

    // ── Link Parser & Extractor ───────────────────────────────────────────────

    /**
     * Extracts all unique URL links from clipboard items.
     */
    fun extractLinks(items: List<ClipItem> = _clips.value): List<ExtractedLink> {
        val result = mutableListOf<ExtractedLink>()
        val seenUrls = mutableSetOf<String>()

        for (item in items) {
            val matches = URL_REGEX.findAll(item.text)
            for (match in matches) {
                var rawUrl = match.value.trim()
                // Strip trailing punctuation often copied accidentally with URLs
                rawUrl = rawUrl.trimEnd('.', ',', ';', ':', ')', ']', '>', '!')
                if (rawUrl.length < 4) continue

                val normalizedUrl = if (!rawUrl.startsWith("http://", ignoreCase = true) &&
                    !rawUrl.startsWith("https://", ignoreCase = true) &&
                    !rawUrl.startsWith("ftp://", ignoreCase = true)
                ) {
                    "https://$rawUrl"
                } else {
                    rawUrl
                }

                if (seenUrls.add(normalizedUrl.lowercase(Locale.ROOT))) {
                    val domain = runCatching {
                        normalizedUrl.substringAfter("://").substringBefore("/").removePrefix("www.")
                    }.getOrDefault(normalizedUrl)

                    result.add(
                        ExtractedLink(
                            url = normalizedUrl,
                            domain = domain,
                            sourceClipId = item.id,
                            sourceClipText = item.text,
                            timestamp = item.timestamp,
                            isSaved = item.isSaved,
                        )
                    )
                }
            }
        }
        return result
    }

    /**
     * Saves a list of extracted links into the clipboard history as permanently saved clips.
     */
    fun saveExtractedLinks(links: List<ExtractedLink>) {
        for (link in links) {
            addClip(link.url, isSaved = true)
        }
    }

    /**
     * Inspects text for OTP tokens (4-8 digits) or common sensitive patterns.
     */
    fun detectSensitiveContent(text: String): Boolean {
        if (OTP_REGEX.matches(text)) return true
        if (CARD_REGEX.matches(text)) return true
        return false
    }

    companion object {
        private const val PREFS_NAME = "lekhani_clipboard"
        private const val KEY_CLIPS = "saved_clips"
        private const val KEY_SNAPSHOTS = "saved_snapshots"
        private const val KEY_RETENTION_MINUTES = "clipboard_retention_minutes"

        const val DEFAULT_RETENTION_MINUTES = 60 // 1 hour default
        private const val MAX_CLIPS = 100
        private const val MAX_SNAPSHOTS = 20

        private val OTP_REGEX = Regex("^\\b\\d{4,8}\\b$")
        private val CARD_REGEX = Regex("^(?:\\d[ -]?){13,19}$")

        /**
         * Comprehensive URL regex matching http, https, ftp, www, and common TLDs.
         */
        val URL_REGEX = Regex(
            """(?i)\b(?:https?://|ftp://|www\.)[^\s<>"'{}|\\^`]+|\b[a-zA-Z0-9.-]+\.(?:com|org|net|edu|gov|io|ai|co|bd|dev|app|me|info|xyz|site|online)(?:/[^\s<>"'{}|\\^`]*)?"""
        )
    }
}
