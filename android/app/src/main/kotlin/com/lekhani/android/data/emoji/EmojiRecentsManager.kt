package com.lekhani.android.data.emoji

import android.content.Context
import androidx.core.content.edit

/**
 * Persists recent and favorite emojis in Device Protected Storage.
 */
class EmojiRecentsManager(context: Context) {

    private val prefs = context
        .createDeviceProtectedStorageContext()
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val recentsList: MutableList<String> = mutableListOf()
    private val favoritesSet: MutableSet<String> = mutableSetOf()

    init {
        val savedRecents = prefs.getString(KEY_RECENTS, "") ?: ""
        if (savedRecents.isNotEmpty()) {
            recentsList.addAll(savedRecents.split(","))
        }

        val savedFavs = prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
        favoritesSet.addAll(savedFavs)
    }

    fun addRecent(emoji: String) {
        recentsList.remove(emoji)
        recentsList.add(0, emoji)
        if (recentsList.size > MAX_RECENTS) {
            recentsList.removeAt(recentsList.size - 1)
        }
        prefs.edit { putString(KEY_RECENTS, recentsList.joinToString(",")) }
    }

    fun getRecents(): List<String> = recentsList.toList()

    fun toggleFavorite(emoji: String): Boolean {
        val isFav = if (favoritesSet.contains(emoji)) {
            favoritesSet.remove(emoji)
            false
        } else {
            favoritesSet.add(emoji)
            true
        }
        prefs.edit { putStringSet(KEY_FAVORITES, favoritesSet) }
        return isFav
    }

    var defaultSkinToneIndex: Int
        get() = prefs.getInt(KEY_DEFAULT_SKIN_TONE_INDEX, -1)
        set(value) = prefs.edit { putInt(KEY_DEFAULT_SKIN_TONE_INDEX, value) }

    fun isFavorite(emoji: String): Boolean = emoji in favoritesSet

    fun getFavorites(): List<String> = favoritesSet.toList()

    companion object {
        private const val PREFS_NAME = "lekhani_emoji_recents"
        private const val KEY_RECENTS = "recent_emojis"
        private const val KEY_FAVORITES = "favorite_emojis"
        private const val KEY_DEFAULT_SKIN_TONE_INDEX = "default_skin_tone_index"
        private const val MAX_RECENTS = 40
    }
}
