package com.lekhani.android.theme

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * CustomThemeManager
 * Manages user-created and duplicated keyboard themes in Device Protected Storage.
 * Ensures zero network usage and 100% on-device offline persistence.
 */
class CustomThemeManager private constructor(context: Context) {

    private val deviceContext = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
        context.createDeviceProtectedStorageContext()
    } else {
        context
    }

    private val prefs = deviceContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAllCustomThemes(): List<KeyboardTheme> {
        val jsonStr = prefs.getString(KEY_CUSTOM_THEMES_JSON, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<KeyboardTheme>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(KeyboardTheme.fromJson(obj))
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getCustomTheme(id: String): KeyboardTheme? {
        return getAllCustomThemes().find { it.id == id }
    }

    fun saveCustomTheme(theme: KeyboardTheme) {
        val current = getAllCustomThemes().toMutableList()
        val index = current.indexOfFirst { it.id == theme.id }
        val updatedTheme = if (!theme.isCustom) theme.copy(isCustom = true) else theme
        if (index >= 0) {
            current[index] = updatedTheme
        } else {
            current.add(0, updatedTheme)
        }
        persistThemes(current)
    }

    fun deleteCustomTheme(id: String) {
        val current = getAllCustomThemes().filterNot { it.id == id }
        persistThemes(current)
    }

    fun duplicateTheme(sourceTheme: KeyboardTheme, newName: String): KeyboardTheme {
        val newId = "custom_${System.currentTimeMillis()}"
        val duplicated = sourceTheme.copy(
            id = newId,
            nameBengali = newName,
            nameEnglish = newName,
            isCustom = true,
        )
        saveCustomTheme(duplicated)
        return duplicated
    }

    private fun persistThemes(themes: List<KeyboardTheme>) {
        val jsonArray = JSONArray()
        for (theme in themes) {
            jsonArray.put(theme.toJson())
        }
        prefs.edit { putString(KEY_CUSTOM_THEMES_JSON, jsonArray.toString()) }
    }

    companion object {
        private const val PREFS_NAME = "lekhani_custom_themes"
        private const val KEY_CUSTOM_THEMES_JSON = "custom_themes_json"

        @Volatile
        private var instance: CustomThemeManager? = null

        fun get(context: Context): CustomThemeManager {
            return instance ?: synchronized(this) {
                instance ?: CustomThemeManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
