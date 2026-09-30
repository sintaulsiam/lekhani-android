package com.lekhani.android.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.lekhani.android.theme.ThemeRegistry

/**
 * KeyboardPreferences
 * ══════════════════════════════════════════════════════════════════════════════
 * Centralized, Device Protected Storage backed preferences for Lekhani.
 *
 * All settings are direct-boot safe (AGENTS.md §3.1) and cached in memory
 * for zero-allocation access inside the rendering and typing loops.
 */
class KeyboardPreferences private constructor(context: Context) {

    private val prefs: SharedPreferences = run {
        val safeContext = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            context.createDeviceProtectedStorageContext()
        } else {
            context
        }
        safeContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ── Theme Settings ────────────────────────────────────────────────────────
    var themeId: String
        get() = prefs.getString(KEY_THEME_ID, ThemeRegistry.ID_FLOW_TEAL) ?: ThemeRegistry.ID_FLOW_TEAL
        set(value) = prefs.edit().putString(KEY_THEME_ID, value).apply()

    var appThemeMode: AppThemeMode
        get() {
            val raw = prefs.getString(KEY_APP_THEME_MODE, AppThemeMode.SYSTEM.name)
            return try { AppThemeMode.valueOf(raw ?: AppThemeMode.SYSTEM.name) } catch (_: Exception) { AppThemeMode.SYSTEM }
        }
        set(value) = prefs.edit().putString(KEY_APP_THEME_MODE, value.name).apply()

    var customWallpaperUri: String
        get() = prefs.getString(KEY_CUSTOM_WALLPAPER_URI, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CUSTOM_WALLPAPER_URI, value).apply()

    var wallpaperOpacity: Float
        get() = prefs.getFloat(KEY_WALLPAPER_OPACITY, 0.25f)
        set(value) = prefs.edit().putFloat(KEY_WALLPAPER_OPACITY, value).apply()

    var heightScale: Float
        get() = prefs.getFloat(KEY_HEIGHT_SCALE, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_HEIGHT_SCALE, value).apply()

    var heightScaleLandscape: Float
        get() = prefs.getFloat(KEY_HEIGHT_SCALE_LANDSCAPE, 0.82f)
        set(value) = prefs.edit().putFloat(KEY_HEIGHT_SCALE_LANDSCAPE, value).apply()

    fun getHeightScaleForOrientation(orientation: Int): Float {
        return if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            heightScaleLandscape
        } else {
            heightScale
        }
    }

    var keyMarginH: Float
        get() = prefs.getFloat(KEY_MARGIN_H, 3.0f)
        set(value) = prefs.edit().putFloat(KEY_MARGIN_H, value).apply()

    var keyMarginV: Float
        get() = prefs.getFloat(KEY_MARGIN_V, 4.0f)
        set(value) = prefs.edit().putFloat(KEY_MARGIN_V, value).apply()

    var bottomChinPadding: Float
        get() = prefs.getFloat(KEY_BOTTOM_CHIN, 0f)
        set(value) = prefs.edit().putFloat(KEY_BOTTOM_CHIN, value).apply()

    var longPressDelayMs: Long
        get() = prefs.getLong(KEY_LONG_PRESS_DELAY, 300L)
        set(value) = prefs.edit().putLong(KEY_LONG_PRESS_DELAY, value).apply()

    var showKeyBorders: Boolean
        get() = prefs.getBoolean(KEY_SHOW_KEY_BORDERS, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_KEY_BORDERS, value).apply()

    var showDedicatedNumberRow: Boolean
        get() = prefs.getBoolean(KEY_SHOW_DEDICATED_NUMBER_ROW, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_DEDICATED_NUMBER_ROW, value).apply()

    var showHomeRowAccents: Boolean
        get() = prefs.getBoolean(KEY_SHOW_HOMEROW_ACCENTS, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_HOMEROW_ACCENTS, value).apply()

    var showKeyPreviews: Boolean
        get() = prefs.getBoolean(KEY_SHOW_KEY_PREVIEWS, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_KEY_PREVIEWS, value).apply()

    var uiLanguage: String
        get() = prefs.getString(KEY_UI_LANGUAGE, "en") ?: "en"
        set(value) = prefs.edit().putString(KEY_UI_LANGUAGE, value).apply()

    var fontStyle: String
        get() = prefs.getString(KEY_FONT_STYLE, FONT_SYSTEM) ?: FONT_SYSTEM
        set(value) = prefs.edit().putString(KEY_FONT_STYLE, value).apply()

    var fontScale: Float
        get() = prefs.getFloat(KEY_FONT_SCALE, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_FONT_SCALE, value).apply()

    // ── Haptics & Sound Settings ──────────────────────────────────────────────
    var hapticEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTIC_ENABLED, value).apply()

    var hapticDurationMs: Int
        get() = prefs.getInt(KEY_HAPTIC_DURATION_MS, 20)
        set(value) = prefs.edit().putInt(KEY_HAPTIC_DURATION_MS, value).apply()

    var soundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SOUND_ENABLED, value).apply()

    var soundPack: String
        get() = prefs.getString(KEY_SOUND_PACK, SOUND_SYSTEM) ?: SOUND_SYSTEM
        set(value) = prefs.edit().putString(KEY_SOUND_PACK, value).apply()

    var soundVolume: Float
        get() = prefs.getFloat(KEY_SOUND_VOLUME, 0.5f)
        set(value) = prefs.edit().putFloat(KEY_SOUND_VOLUME, value).apply()

    // ── Form Factor & Gesture Settings ────────────────────────────────────────
    var formFactor: FormFactor
        get() {
            val name = prefs.getString(KEY_FORM_FACTOR, FormFactor.STANDARD.name) ?: FormFactor.STANDARD.name
            return runCatching { FormFactor.valueOf(name) }.getOrDefault(FormFactor.STANDARD)
        }
        set(value) = prefs.edit().putString(KEY_FORM_FACTOR, value.name).apply()

    var spacebarSwipeMode: SpacebarSwipeMode
        get() {
            val name = prefs.getString(KEY_SPACEBAR_SWIPE_MODE, SpacebarSwipeMode.LAYOUT_SWITCH.name) ?: SpacebarSwipeMode.LAYOUT_SWITCH.name
            return runCatching { SpacebarSwipeMode.valueOf(name) }.getOrDefault(SpacebarSwipeMode.LAYOUT_SWITCH)
        }
        set(value) {
            prefs.edit().putString(KEY_SPACEBAR_SWIPE_MODE, value.name).apply()
            spaceCursorSlideEnabled = (value == SpacebarSwipeMode.CURSOR_NAV)
        }

    var bottomRowKeyMode: BottomRowKeyMode
        get() {
            val name = prefs.getString(KEY_BOTTOM_ROW_KEY_MODE, BottomRowKeyMode.SMART.name) ?: BottomRowKeyMode.SMART.name
            return runCatching { BottomRowKeyMode.valueOf(name) }.getOrDefault(BottomRowKeyMode.SMART)
        }
        set(value) = prefs.edit().putString(KEY_BOTTOM_ROW_KEY_MODE, value.name).apply()

    var spaceCursorSlideEnabled: Boolean
        get() = prefs.getBoolean(KEY_SPACE_CURSOR_SLIDE, true)
        set(value) = prefs.edit().putBoolean(KEY_SPACE_CURSOR_SLIDE, value).apply()

    var swipeToDeleteEnabled: Boolean
        get() = prefs.getBoolean(KEY_SWIPE_TO_DELETE, true)
        set(value) = prefs.edit().putBoolean(KEY_SWIPE_TO_DELETE, value).apply()

    var keyGlowRippleEnabled: Boolean
        get() = prefs.getBoolean(KEY_KEY_GLOW_RIPPLE, true)
        set(value) = prefs.edit().putBoolean(KEY_KEY_GLOW_RIPPLE, value).apply()

    var glideTypingEnabled: Boolean
        get() = prefs.getBoolean(KEY_GLIDE_TYPING_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_GLIDE_TYPING_ENABLED, value).apply()

    var codeShieldEnabled: Boolean
        get() = prefs.getBoolean(KEY_CODE_SHIELD_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_CODE_SHIELD_ENABLED, value).apply()

    var doubleSpaceDariEnabled: Boolean
        get() = prefs.getBoolean(KEY_DOUBLE_SPACE_DARI_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_DOUBLE_SPACE_DARI_ENABLED, value).apply()

    var spacebarAutocompleteEnabled: Boolean
        get() = prefs.getBoolean(KEY_SPACEBAR_AUTOCOMPLETE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SPACEBAR_AUTOCOMPLETE_ENABLED, value).apply()

    var autoLearnWordsEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_LEARN_WORDS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_LEARN_WORDS_ENABLED, value).apply()

    // ── Clipboard Settings ────────────────────────────────────────────────────
    var clipboardRetentionMinutes: Int
        get() = prefs.getInt(KEY_CLIPBOARD_RETENTION_MINUTES, 60)
        set(value) = prefs.edit().putInt(KEY_CLIPBOARD_RETENTION_MINUTES, value).apply()

    // ── Toolbar & Tool Vault Settings ────────────────────────────────────────
    var toolbarTools: String
        get() = prefs.getString(KEY_TOOLBAR_TOOLS, DEFAULT_TOOLBAR) ?: DEFAULT_TOOLBAR
        set(value) = prefs.edit().putString(KEY_TOOLBAR_TOOLS, value).apply()

    var vaultTools: String
        get() = prefs.getString(KEY_VAULT_TOOLS, DEFAULT_VAULT) ?: DEFAULT_VAULT
        set(value) = prefs.edit().putString(KEY_VAULT_TOOLS, value).apply()

    fun getActiveToolbarTools(): List<ToolbarTool> {
        val raw = toolbarTools
        return raw.split(",")
            .mapNotNull { name -> runCatching { ToolbarTool.valueOf(name.trim()) }.getOrNull() }
            .ifEmpty { DEFAULT_TOOL_LIST }
    }

    fun setToolbarToolsList(tools: List<ToolbarTool>) {
        toolbarTools = tools.joinToString(",") { it.name }
    }

    fun getVaultTools(): List<ToolbarTool> {
        val active = getActiveToolbarTools().toSet()
        val raw = vaultTools
        val saved = raw.split(",")
            .mapNotNull { name -> runCatching { ToolbarTool.valueOf(name.trim()) }.getOrNull() }
            .filter { !active.contains(it) }
        val allAvailable = ToolbarTool.values().toList()
        val missing = allAvailable.filter { !active.contains(it) && !saved.contains(it) }
        return (saved + missing).ifEmpty { DEFAULT_VAULT_LIST }
    }

    fun setVaultToolsList(tools: List<ToolbarTool>) {
        vaultTools = tools.joinToString(",") { it.name }
    }

    fun resetToolsToDefault() {
        setToolbarToolsList(DEFAULT_TOOL_LIST)
        setVaultToolsList(DEFAULT_VAULT_LIST)
    }

    enum class SpacebarSwipeMode(val titleBengali: String, val titleEnglish: String) {
        CURSOR_NAV("কার্সার স্লাইড (Cursor Slide)", "Cursor Slide"),
        LAYOUT_SWITCH("লেআউট পরিবর্তন (Layout Switch)", "Layout Switch"),
        DISABLED("নিষ্ক্রিয় (Disabled)", "Disabled"),
    }

    enum class BottomRowKeyMode(val titleBengali: String, val titleEnglish: String) {
        SMART("স্মার্ট / স্বয়ংক্রিয় (Smart)", "Smart (Automatic)"),
        EMOJI("সর্বদা ইমোজি (Emoji)", "Always Emoji Key (😊)"),
        LANGUAGE_SWITCH("সর্বদা ভাষা (Language)", "Always Language Key (🌐)"),
    }

    enum class FormFactor(val titleBengali: String, val titleEnglish: String) {
        STANDARD("ফুল স্ক্রিন (Standard)", "Standard Full"),
        ONE_HANDED_LEFT("একহাতে বাম (One-Handed Left)", "One-Handed Left"),
        ONE_HANDED_RIGHT("একহাতে ডান (One-Handed Right)", "One-Handed Right"),
        FLOATING("ভাসমান উইন্ডো (Floating)", "Floating Window"),
        SPLIT("স্প্লিট মোড (Split)", "Split Mode"),
    }

    enum class AppThemeMode(val titleBengali: String, val titleEnglish: String) {
        SYSTEM("সিস্টেম ডিফল্ট", "Follow System"),
        LIGHT("লাইট থিম", "Always Light"),
        DARK("ডার্ক থিম", "Always Dark"),
        MATCH_KEYBOARD("কীবোর্ডের অনুরূপ", "Match Keyboard"),
    }

    enum class ToolbarTool(val titleBengali: String, val titleEnglish: String) {
        EMOJI("ইমোজি", "Emoji"),
        TEXT_EDITOR("এডিটর", "Editor"),
        VOICE("ভয়েস", "Voice"),
        CLIPBOARD("ক্লিপবোর্ড", "Clipboard"),
        RESIZE("উচ্চতা", "Height"),
        THEME("থিম", "Theme"),
        ONE_HANDED("একহাতে", "One-Handed"),
        FLOATING("ভাসমান", "Floating"),
        SPLIT("বিভক্ত", "Split"),
        SETTINGS("সেটিংস", "Settings"),
    }

    companion object {
        const val PREFS_NAME = "lekhani_device_prefs"

        const val KEY_UI_LANGUAGE = "ui_language"
        const val KEY_THEME_ID = "theme_id"
        const val KEY_APP_THEME_MODE = "app_theme_mode"
        const val KEY_CUSTOM_WALLPAPER_URI = "custom_wallpaper_uri"
        const val KEY_WALLPAPER_OPACITY = "custom_wallpaper_opacity"

        const val KEY_HEIGHT_SCALE = "keyboard_height_scale"
        const val KEY_HEIGHT_SCALE_LANDSCAPE = "keyboard_height_scale_landscape"
        const val KEY_MARGIN_H = "key_margin_h"
        const val KEY_MARGIN_V = "key_margin_v"
        const val KEY_BOTTOM_CHIN = "bottom_chin_padding"
        const val KEY_LONG_PRESS_DELAY = "long_press_delay_ms"
        const val KEY_SHOW_KEY_BORDERS = "show_key_borders"
        const val KEY_SHOW_KEY_PREVIEWS = "show_key_previews"
        const val KEY_SHOW_DEDICATED_NUMBER_ROW = "pref_show_dedicated_number_row"
        const val KEY_SHOW_HOMEROW_ACCENTS = "show_homerow_accents"
        const val KEY_FONT_STYLE = "font_style"
        const val KEY_FONT_SCALE = "font_scale"

        const val KEY_FORM_FACTOR = "keyboard_form_factor"
        const val KEY_SPACE_CURSOR_SLIDE = "space_cursor_slide"
        const val KEY_SPACEBAR_SWIPE_MODE = "spacebar_swipe_mode"
        const val KEY_BOTTOM_ROW_KEY_MODE = "bottom_row_key_mode"
        const val KEY_SWIPE_TO_DELETE = "swipe_to_delete"
        const val KEY_KEY_GLOW_RIPPLE = "key_glow_ripple"
        const val KEY_GLIDE_TYPING_ENABLED = "glide_typing_enabled"
        const val KEY_CODE_SHIELD_ENABLED = "code_shield_enabled"
        const val KEY_DOUBLE_SPACE_DARI_ENABLED = "double_space_dari_enabled"
        const val KEY_SPACEBAR_AUTOCOMPLETE_ENABLED = "spacebar_autocomplete_enabled"
        const val KEY_AUTO_LEARN_WORDS_ENABLED = "auto_learn_words_enabled"
        const val KEY_CLIPBOARD_RETENTION_MINUTES = "clipboard_retention_minutes"

        const val KEY_HAPTIC_ENABLED = "haptic_enabled"
        const val KEY_HAPTIC_DURATION_MS = "haptic_duration_ms"
        const val KEY_SOUND_ENABLED = "sound_enabled"
        const val KEY_SOUND_PACK = "sound_pack"
        const val KEY_SOUND_VOLUME = "sound_volume"

        const val KEY_TOOLBAR_TOOLS = "toolbar_tools"
        const val KEY_VAULT_TOOLS = "vault_tools"

        const val FONT_SYSTEM = "SYSTEM_DEFAULT"
        const val FONT_SERIF = "SERIF"
        const val FONT_SANS_SERIF = "SANS_SERIF"
        const val FONT_MONOSPACE = "MONOSPACE"

        const val SOUND_SYSTEM = "SYSTEM"
        const val SOUND_BUBBLE = "BUBBLE"
        const val SOUND_MECHANICAL = "MECHANICAL"
        const val SOUND_TYPEWRITER = "TYPEWRITER"
        const val SOUND_WOODBLOCK = "WOODBLOCK"

        val DEFAULT_TOOL_LIST = listOf(
            ToolbarTool.VOICE,
            ToolbarTool.CLIPBOARD,
            ToolbarTool.EMOJI,
            ToolbarTool.TEXT_EDITOR,
            ToolbarTool.THEME,
            ToolbarTool.SETTINGS,
        )

        val DEFAULT_TOOLBAR = DEFAULT_TOOL_LIST.joinToString(",") { it.name }

        val DEFAULT_VAULT_LIST = listOf(
            ToolbarTool.RESIZE,
            ToolbarTool.ONE_HANDED,
            ToolbarTool.FLOATING,
            ToolbarTool.SPLIT,
        )

        val DEFAULT_VAULT = DEFAULT_VAULT_LIST.joinToString(",") { it.name }

        @Volatile
        private var instance: KeyboardPreferences? = null

        fun get(context: Context): KeyboardPreferences {
            return instance ?: synchronized(this) {
                instance ?: KeyboardPreferences(context.applicationContext).also { instance = it }
            }
        }
    }
}
