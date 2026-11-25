package com.lekhani.android.ime

import android.content.Context
import android.content.SharedPreferences
import android.provider.UserDictionary
import android.util.Log
import com.lekhani.android.ffi.AndroidLekhaniSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Coordinates offline dictionary resolution, system UserDictionary import,
 * and Direct-Boot-safe learner/autocorrect file persistence for LekhaniIME.
 */
class ImeAssetPersistenceCoordinator(
    private val context: Context,
    private val devicePrefs: SharedPreferences,
    private val session: AndroidLekhaniSession,
    private val scope: CoroutineScope,
    private val tag: String = "LekhaniIME",
) {
    @Volatile
    var isEnglishDictLoaded: Boolean = false
        private set

    private val directBootFilesDir: File by lazy {
        context.createDeviceProtectedStorageContext().filesDir
    }

    fun ensureEnglishDictionaryLoaded() {
        if (isEnglishDictLoaded) return
        val candidates = listOf(
            File(context.filesDir, "dictionaries/english_dict.bin"),
            File(context.applicationContext.filesDir, "dictionaries/english_dict.bin"),
            File(directBootFilesDir, "dictionaries/english_dict.bin"),
        )
        for (f in candidates) {
            if (f.exists() && f.length() > 0) {
                val ok = session.loadEnglishDictionary(f.absolutePath)
                if (ok) {
                    isEnglishDictLoaded = true
                    Log.i(tag, "English dictionary loaded from ${f.absolutePath} (${f.length()} bytes)")
                    return
                }
            }
        }
        Log.w(tag, "English dictionary file not found or failed to load")
    }

    fun importSystemUserDictionaryIfNeeded() {
        if (devicePrefs.getBoolean("system_user_dict_imported", false)) return

        try {
            val words = mutableListOf<String>()
            val cursor = context.contentResolver.query(
                UserDictionary.Words.CONTENT_URI,
                arrayOf(UserDictionary.Words.WORD),
                null,
                null,
                null,
            )
            cursor?.use {
                val colIdx = it.getColumnIndex(UserDictionary.Words.WORD)
                if (colIdx >= 0) {
                    while (it.moveToNext()) {
                        val word = it.getString(colIdx)?.trim()
                        if (!word.isNullOrEmpty()) {
                            words.add(word)
                        }
                    }
                }
            }
            if (words.isNotEmpty()) {
                val count = session.importRawWords(words)
                Log.i(tag, "Imported $count words from system UserDictionary")
            }
            devicePrefs.edit().putBoolean("system_user_dict_imported", true).apply()
        } catch (e: SecurityException) {
            Log.d(tag, "UserDictionary permission not granted or unavailable: ${e.message}")
        } catch (e: Exception) {
            Log.w(tag, "Could not import system UserDictionary: ${e.message}")
        }
    }

    fun getUserLearnedFile(): File {
        val dpsFile = File(directBootFilesDir, "user_learned.bin")
        try {
            val oldFile = File(context.filesDir, "user_learned.bin")
            if (!dpsFile.exists() && oldFile.exists()) {
                oldFile.copyTo(dpsFile, overwrite = true)
                oldFile.delete()
                Log.i(tag, "Migrated user_learned.bin to Device Protected Storage")
            }
        } catch (e: Exception) {
            Log.w(tag, "Migration check for user_learned.bin: ${e.message}")
        }
        return dpsFile
    }

    fun getUserAutocorrectFile(): File {
        val dpsFile = File(directBootFilesDir, "user_autocorrect.json")
        try {
            val oldFile = File(context.filesDir, "user_autocorrect.json")
            if (!dpsFile.exists() && oldFile.exists()) {
                oldFile.copyTo(dpsFile, overwrite = true)
                oldFile.delete()
                Log.i(tag, "Migrated user_autocorrect.json to Device Protected Storage")
            }
        } catch (e: Exception) {
            Log.w(tag, "Migration check for user_autocorrect.json: ${e.message}")
        }
        return dpsFile
    }

    fun persistUserLearnedAsync() {
        val file = getUserLearnedFile()
        scope.launch(Dispatchers.IO) {
            persistUserLearnedInternal(file)
        }
    }

    fun persistUserLearnedInternal(file: File) {
        try {
            if (session.isUserLearnedDirty()) {
                session.saveUserLearned(file.absolutePath)
                Log.i(tag, "User learned data successfully persisted to ${file.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to persist user learned data: ${e.message}")
        }
    }
}
