package com.lekhani.android.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.lekhani.android.data.clipboard.ClipItem
import com.lekhani.android.data.clipboard.LekhaniClipboardStore
import com.lekhani.android.data.dictionary.LekhaniDictionaryManager
import com.lekhani.android.data.settings.KeyboardPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * LekhaniBackupManager
 *
 * Centralized, 100% offline, cloud-free backup and restore manager.
 * Exports personal vocabulary, learned typing memory, custom autocorrect shortcuts,
 * keyboard preferences, and pinned clipboard items into a clean, human-readable JSON format.
 */
object LekhaniBackupManager {

    private const val TAG = "LekhaniBackupMgr"
    const val BACKUP_SCHEMA_VERSION = 1
    const val DEFAULT_BACKUP_FILENAME_PREFIX = "Lekhani_Backup_"

    data class BackupSummary(
        val timestamp: Long,
        val schemaVersion: Int,
        val userWordsCount: Int,
        val learnedWordsCount: Int,
        val shortcutsCount: Int,
        val preferencesCount: Int,
        val pinnedClipsCount: Int,
    )

    /**
     * Creates a full JSON backup string containing selected components.
     */
    fun createBackupJson(
        context: Context,
        dictManager: LekhaniDictionaryManager,
        includeLearned: Boolean = true,
        includeShortcuts: Boolean = true,
        includePreferences: Boolean = true,
        includeClipboard: Boolean = true,
    ): String {
        val root = JSONObject()
        val now = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)

        root.put("schemaVersion", BACKUP_SCHEMA_VERSION)
        root.put("appName", "Lekhani Keyboard")
        root.put("exportedAt", dateFormat.format(Date(now)))
        root.put("timestamp", now)

        // 1. Vocabulary & Learned Engine Memory
        if (includeLearned) {
            val vocabObj = JSONObject()
            val userWords = dictManager.getUserWords()
            val wordsArray = JSONArray()
            for (w in userWords) {
                wordsArray.put(w)
            }
            vocabObj.put("userWords", wordsArray)

            val engineJson = dictManager.exportJson()
            if (engineJson.isNotBlank() && engineJson != "{}") {
                try {
                    vocabObj.put("engineLearner", JSONObject(engineJson))
                } catch (_: Exception) {
                    vocabObj.put("engineLearnerRaw", engineJson)
                }
            }
            vocabObj.put("learnedCount", dictManager.getLearnedWordsCount())
            root.put("vocabulary", vocabObj)
        }

        // 2. Custom Autocorrect Shortcuts
        if (includeShortcuts) {
            val rules = dictManager.getAutocorrectRules()
            val shortcutsObj = JSONObject()
            for ((trigger, repl) in rules) {
                shortcutsObj.put(trigger, repl)
            }
            root.put("shortcuts", shortcutsObj)
        }

        // 3. Preferences & Layout Configuration
        if (includePreferences) {
            val prefs = getDevicePrefs(context)
            val prefsObj = JSONObject()
            for ((key, value) in prefs.all) {
                when (value) {
                    is Boolean -> prefsObj.put(key, value)
                    is Int     -> prefsObj.put(key, value)
                    is Long    -> prefsObj.put(key, value)
                    is Float   -> prefsObj.put(key, value.toDouble())
                    is String  -> prefsObj.put(key, value)
                }
            }
            root.put("preferences", prefsObj)
        }

        // 4. Pinned / Saved Clipboard Vault Items
        if (includeClipboard) {
            try {
                val clipStore = LekhaniClipboardStore(context)
                val pinnedClips = clipStore.clips.value.filter { it.isPinned || it.isSaved }
                val clipsArray = JSONArray()
                for (item in pinnedClips) {
                    val itemObj = JSONObject().apply {
                        put("text", item.text)
                        put("timestamp", item.timestamp)
                        put("isPinned", item.isPinned)
                        put("isSaved", item.isSaved)
                    }
                    clipsArray.put(itemObj)
                }
                root.put("pinnedClipboard", clipsArray)
            } catch (e: Exception) {
                logError("Error packaging clipboard items: $e")
            }
        }

        return root.toString(2)
    }

    /**
     * Inspects a backup JSON string and returns a preview summary without modifying state.
     */
    fun inspectBackup(jsonString: String): BackupSummary? {
        return try {
            val root = JSONObject(jsonString)
            val version = root.optInt("schemaVersion", 1)
            val timestamp = root.optLong("timestamp", System.currentTimeMillis())

            var userWordsCount = 0
            var learnedCount = 0
            if (root.has("vocabulary")) {
                val vocab = root.getJSONObject("vocabulary")
                userWordsCount = vocab.optJSONArray("userWords")?.length() ?: 0
                learnedCount = vocab.optInt("learnedCount", 0)
            }

            val shortcutsCount = root.optJSONObject("shortcuts")?.length() ?: 0
            val preferencesCount = root.optJSONObject("preferences")?.length() ?: 0
            val pinnedClipsCount = root.optJSONArray("pinnedClipboard")?.length() ?: 0

            BackupSummary(
                timestamp = timestamp,
                schemaVersion = version,
                userWordsCount = userWordsCount,
                learnedWordsCount = learnedCount,
                shortcutsCount = shortcutsCount,
                preferencesCount = preferencesCount,
                pinnedClipsCount = pinnedClipsCount,
            )
        } catch (e: Exception) {
            logError("Error inspecting backup JSON: $e")
            null
        }
    }

    /**
     * Restores data from backup JSON and merges it into the local engine, preferences, and clipboard.
     */
    fun restoreBackupJson(
        context: Context,
        dictManager: LekhaniDictionaryManager,
        jsonString: String,
        restoreVocabulary: Boolean = true,
        restoreShortcuts: Boolean = true,
        restorePreferences: Boolean = true,
        restoreClipboard: Boolean = true,
    ): BackupSummary {
        val root = JSONObject(jsonString)
        val version = root.optInt("schemaVersion", 1)
        val timestamp = root.optLong("timestamp", System.currentTimeMillis())

        var restoredUserWords = 0
        var restoredLearnedWords = 0
        var restoredShortcuts = 0
        var restoredPreferences = 0
        var restoredClips = 0

        // 1. Restore Vocabulary & Learned Memory
        if (restoreVocabulary && root.has("vocabulary")) {
            val vocab = root.getJSONObject("vocabulary")

            // User Words
            val wordsArray = vocab.optJSONArray("userWords")
            if (wordsArray != null) {
                for (i in 0 until wordsArray.length()) {
                    val word = wordsArray.getString(i)
                    if (dictManager.addUserWord(word)) {
                        restoredUserWords++
                    }
                }
            }

            // Engine Learner JSON (bigrams & frequency graph)
            val engineLearner = vocab.optJSONObject("engineLearner")?.toString()
                ?: vocab.optString("engineLearnerRaw", "")
            if (engineLearner.isNotBlank() && engineLearner != "{}") {
                val importedCount = dictManager.importJson(engineLearner)
                restoredLearnedWords = importedCount
            }

            // Persist merged learner to disk
            val learnedFile = File(context.filesDir, "user_learned.bin")
            dictManager.saveLearned(learnedFile.absolutePath)
        }

        // 2. Restore Shortcuts
        if (restoreShortcuts && root.has("shortcuts")) {
            val shortcuts = root.getJSONObject("shortcuts")
            val keys = shortcuts.keys()
            while (keys.hasNext()) {
                val trigger = keys.next()
                val repl = shortcuts.getString(trigger)
                if (dictManager.addAutocorrectRule(trigger, repl)) {
                    restoredShortcuts++
                }
            }
        }

        // 3. Restore Preferences
        if (restorePreferences && root.has("preferences")) {
            val prefs = getDevicePrefs(context)
            val editor = prefs.edit()
            val prefsObj = root.getJSONObject("preferences")
            val keys = prefsObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                when (val value = prefsObj.get(key)) {
                    is Boolean -> { editor.putBoolean(key, value); restoredPreferences++ }
                    is Int     -> { editor.putInt(key, value); restoredPreferences++ }
                    is Long    -> { editor.putLong(key, value); restoredPreferences++ }
                    is Double  -> { editor.putFloat(key, value.toFloat()); restoredPreferences++ }
                    is String  -> { editor.putString(key, value); restoredPreferences++ }
                }
            }
            editor.apply()
        }

        // 4. Restore Pinned Clipboard
        if (restoreClipboard && root.has("pinnedClipboard")) {
            try {
                val clipStore = LekhaniClipboardStore(context)
                val clipsArray = root.getJSONArray("pinnedClipboard")
                for (i in 0 until clipsArray.length()) {
                    val obj = clipsArray.getJSONObject(i)
                    val text = obj.getString("text")
                    val isPinned = obj.optBoolean("isPinned", true)
                    val isSaved = obj.optBoolean("isSaved", false)
                    clipStore.addClip(text, isSaved = isSaved)
                    val added = clipStore.clips.value.find { it.text == text.trim() }
                    if (added != null) {
                        if (isPinned && !added.isPinned) {
                            clipStore.togglePin(added.id)
                        }
                        restoredClips++
                    }
                }
            } catch (e: Exception) {
                logError("Error restoring clipboard items: $e")
            }
        }

        return BackupSummary(
            timestamp = timestamp,
            schemaVersion = version,
            userWordsCount = restoredUserWords,
            learnedWordsCount = restoredLearnedWords,
            shortcutsCount = restoredShortcuts,
            preferencesCount = restoredPreferences,
            pinnedClipsCount = restoredClips,
        )
    }

    /**
     * Builds an Android Share Intent for exporting backup JSON to any app or chat.
     */
    fun createShareIntent(context: Context, backupJson: String, isEnglish: Boolean): Intent {
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val subject = "${DEFAULT_BACKUP_FILENAME_PREFIX}${dateStr}.json"

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, backupJson)
        }
        return Intent.createChooser(sendIntent, if (isEnglish) "Export Lekhani Backup" else "লেখনী ব্যাকআপ এক্সপোর্ট করুন")
    }

    /**
     * Writes backup JSON to a user-selected Uri via Storage Access Framework (SAF).
     */
    fun writeBackupToUri(context: Context, uri: Uri, backupJson: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { os ->
                OutputStreamWriter(os, Charsets.UTF_8).use { writer ->
                    writer.write(backupJson)
                    writer.flush()
                }
            }
            true
        } catch (e: Exception) {
            logError("Failed writing backup to URI: $e")
            false
        }
    }

    /**
     * Reads backup JSON content from a user-selected Uri.
     */
    fun readBackupFromUri(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            }
        } catch (e: Exception) {
            logError("Failed reading backup from URI: $e")
            null
        }
    }

    private fun logError(msg: String) {
        try {
            Log.e(TAG, msg)
        } catch (_: Throwable) {
            // No-op in JVM unit test runner where android.util.Log is not mocked
        }
    }

    private fun getDevicePrefs(context: Context) =
        context.createDeviceProtectedStorageContext().getSharedPreferences(
            KeyboardPreferences.PREFS_NAME,
            Context.MODE_PRIVATE
        )
}
