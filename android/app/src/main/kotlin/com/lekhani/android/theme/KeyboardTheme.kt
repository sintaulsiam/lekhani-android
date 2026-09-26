package com.lekhani.android.theme

import android.content.Context
import android.graphics.Color
import android.os.Build

/**
 * KeyboardTheme
 * ══════════════════════════════════════════════════════════════════════════════
 * Immutable definition of color tokens used by [KeyboardCanvasView],
 * [CandidateStripView], and the keyboard toolbar.
 *
 * All color values are packed 32-bit ARGB ints for zero-allocation rendering.
 */
data class KeyboardTheme(
    val id: String,
    val nameBengali: String,
    val nameEnglish: String,
    val backgroundColor: Int,
    val keyNormalColor: Int,
    val keyShiftColor: Int,
    val keySpaceColor: Int,
    val keyHasantaColor: Int,
    val keyBorderColor: Int,
    val labelColor: Int,
    val labelDimColor: Int,
    val accentColor: Int,
    val rippleColor: Int,
    val glideStrokeColor: Int,
    val glideGlowColor: Int,
    val isDark: Boolean = true,
    val isCustom: Boolean = false,
) {
    fun toJson(): org.json.JSONObject {
        val json = org.json.JSONObject()
        json.put("id", id)
        json.put("nameBengali", nameBengali)
        json.put("nameEnglish", nameEnglish)
        json.put("backgroundColor", backgroundColor)
        json.put("keyNormalColor", keyNormalColor)
        json.put("keyShiftColor", keyShiftColor)
        json.put("keySpaceColor", keySpaceColor)
        json.put("keyHasantaColor", keyHasantaColor)
        json.put("keyBorderColor", keyBorderColor)
        json.put("labelColor", labelColor)
        json.put("labelDimColor", labelDimColor)
        json.put("accentColor", accentColor)
        json.put("rippleColor", rippleColor)
        json.put("glideStrokeColor", glideStrokeColor)
        json.put("glideGlowColor", glideGlowColor)
        json.put("isDark", isDark)
        json.put("isCustom", isCustom)
        return json
    }

    companion object {
        fun fromJson(json: org.json.JSONObject): KeyboardTheme {
            return KeyboardTheme(
                id = json.getString("id"),
                nameBengali = json.optString("nameBengali", "কাস্টম থিম"),
                nameEnglish = json.optString("nameEnglish", "Custom Theme"),
                backgroundColor = json.getInt("backgroundColor"),
                keyNormalColor = json.getInt("keyNormalColor"),
                keyShiftColor = json.getInt("keyShiftColor"),
                keySpaceColor = json.getInt("keySpaceColor"),
                keyHasantaColor = json.getInt("keyHasantaColor"),
                keyBorderColor = json.getInt("keyBorderColor"),
                labelColor = json.getInt("labelColor"),
                labelDimColor = json.getInt("labelDimColor"),
                accentColor = json.getInt("accentColor"),
                rippleColor = json.optInt("rippleColor", 0x4000E5B8),
                glideStrokeColor = json.optInt("glideStrokeColor", 0xFF00E5B8.toInt()),
                glideGlowColor = json.optInt("glideGlowColor", 0x4000E5B8),
                isDark = json.optBoolean("isDark", true),
                isCustom = json.optBoolean("isCustom", true),
            )
        }
    }
}

/**
 * ThemeRegistry
 * ══════════════════════════════════════════════════════════════════════════════
 * Provides built-in theme presets and dynamic Material You theme generation.
 */
object ThemeRegistry {

    const val ID_FLOW_TEAL = "flow_teal"
    const val ID_OLED_BLACK = "oled_black"
    const val ID_AVRO_BLUE = "avro_blue"
    const val ID_HIGH_CONTRAST = "high_contrast"
    const val ID_CYBER_INDIGO = "cyber_indigo"
    const val ID_DAYLIGHT_LIGHT = "daylight_light"
    const val ID_SAKURA_DUSK = "sakura_dusk"
    const val ID_FOREST_EMERALD = "forest_emerald"
    const val ID_NORDIC_FROST = "nordic_frost"
    const val ID_SUNSET_AMBER = "sunset_amber"
    const val ID_MOCHA_LATTE = "mocha_latte"
    const val ID_MATERIAL_YOU = "material_you"

    val THEME_FLOW_TEAL = KeyboardTheme(
        id = ID_FLOW_TEAL,
        nameBengali = "প্রবাহ টিল",
        nameEnglish = "Flow Teal",
        backgroundColor = 0xFF0D1117.toInt(),
        keyNormalColor = 0xFF1A2035.toInt(),
        keyShiftColor = 0xFF141926.toInt(),
        keySpaceColor = 0xFF1E2840.toInt(),
        keyHasantaColor = 0xFF003D4D.toInt(),
        keyBorderColor = 0xFF2A3550.toInt(),
        labelColor = 0xFFE8EAF0.toInt(),
        labelDimColor = 0xFF9098B0.toInt(),
        accentColor = 0xFF00D4A0.toInt(),
        rippleColor = 0x4000D4A0.toInt(),
        glideStrokeColor = 0xFF00E5B8.toInt(),
        glideGlowColor = 0x4000D4A0.toInt(),
        isDark = true,
    )

    val THEME_OLED_BLACK = KeyboardTheme(
        id = ID_OLED_BLACK,
        nameBengali = "ওলেড ব্ল্যাক",
        nameEnglish = "OLED Pure Black",
        backgroundColor = 0xFF000000.toInt(),
        keyNormalColor = 0xFF121212.toInt(),
        keyShiftColor = 0xFF090909.toInt(),
        keySpaceColor = 0xFF181818.toInt(),
        keyHasantaColor = 0xFF152C22.toInt(),
        keyBorderColor = 0xFF252525.toInt(),
        labelColor = 0xFFFFFFFF.toInt(),
        labelDimColor = 0xFF8E8E93.toInt(),
        accentColor = 0xFF00E676.toInt(),
        rippleColor = 0x3300E676.toInt(),
        glideStrokeColor = 0xFF00E676.toInt(),
        glideGlowColor = 0x4000E676.toInt(),
        isDark = true,
    )

    val THEME_AVRO_BLUE = KeyboardTheme(
        id = ID_AVRO_BLUE,
        nameBengali = "ক্লাসিক ব্লু",
        nameEnglish = "Classic Blue",
        backgroundColor = 0xFF0A111E.toInt(),
        keyNormalColor = 0xFF152238.toInt(),
        keyShiftColor = 0xFF0E1726.toInt(),
        keySpaceColor = 0xFF1D2D4A.toInt(),
        keyHasantaColor = 0xFF1A365D.toInt(),
        keyBorderColor = 0xFF233554.toInt(),
        labelColor = 0xFFF0F4F8.toInt(),
        labelDimColor = 0xFF88A0BF.toInt(),
        accentColor = 0xFF2196F3.toInt(),
        rippleColor = 0x402196F3.toInt(),
        glideStrokeColor = 0xFF42A5F5.toInt(),
        glideGlowColor = 0x402196F3.toInt(),
        isDark = true,
    )

    val THEME_HIGH_CONTRAST = KeyboardTheme(
        id = ID_HIGH_CONTRAST,
        nameBengali = "হাই কনট্রাস্ট হলুদ",
        nameEnglish = "High Contrast Yellow",
        backgroundColor = 0xFF000000.toInt(),
        keyNormalColor = 0xFF080808.toInt(),
        keyShiftColor = 0xFF181818.toInt(),
        keySpaceColor = 0xFF080808.toInt(),
        keyHasantaColor = 0xFF2A2400.toInt(),
        keyBorderColor = 0xFF888888.toInt(),
        labelColor = 0xFFFFD600.toInt(),
        labelDimColor = 0xFFFFF176.toInt(),
        accentColor = 0xFFFFD600.toInt(),
        rippleColor = 0x40FFD600.toInt(),
        glideStrokeColor = 0xFFFFD600.toInt(),
        glideGlowColor = 0x50FFD600.toInt(),
        isDark = true,
    )

    val THEME_CYBER_INDIGO = KeyboardTheme(
        id = ID_CYBER_INDIGO,
        nameBengali = "ডিপ ভায়োলেট",
        nameEnglish = "Deep Violet",
        backgroundColor = 0xFF0C081A.toInt(),
        keyNormalColor = 0xFF1A1333.toInt(),
        keyShiftColor = 0xFF120D24.toInt(),
        keySpaceColor = 0xFF251B47.toInt(),
        keyHasantaColor = 0xFF3B1D66.toInt(),
        keyBorderColor = 0xFF3A2D5C.toInt(),
        labelColor = 0xFFF5EEFF.toInt(),
        labelDimColor = 0xFFA996C8.toInt(),
        accentColor = 0xFFB388FF.toInt(),
        rippleColor = 0x40B388FF.toInt(),
        glideStrokeColor = 0xFF7C4DFF.toInt(),
        glideGlowColor = 0x407C4DFF.toInt(),
        isDark = true,
    )

    val THEME_DAYLIGHT_LIGHT = KeyboardTheme(
        id = ID_DAYLIGHT_LIGHT,
        nameBengali = "ডেলাইট পেপার",
        nameEnglish = "Daylight Paper",
        backgroundColor = 0xFFECEFF1.toInt(),
        keyNormalColor = 0xFFFFFFFF.toInt(),
        keyShiftColor = 0xFFCFD8DC.toInt(),
        keySpaceColor = 0xFFFFFFFF.toInt(),
        keyHasantaColor = 0xFFB2DFDB.toInt(),
        keyBorderColor = 0xFFB0BEC5.toInt(),
        labelColor = 0xFF1A202C.toInt(),
        labelDimColor = 0xFF546E7A.toInt(),
        accentColor = 0xFF006C50.toInt(),
        rippleColor = 0x33006C50.toInt(),
        glideStrokeColor = 0xFF00897B.toInt(),
        glideGlowColor = 0x3300897B.toInt(),
        isDark = false,
    )

    val THEME_SAKURA_DUSK = KeyboardTheme(
        id = ID_SAKURA_DUSK,
        nameBengali = "সাকুরা পিংক",
        nameEnglish = "Sakura Dusk",
        backgroundColor = 0xFF181419.toInt(),
        keyNormalColor = 0xFF241E26.toInt(),
        keyShiftColor = 0xFF1C171E.toInt(),
        keySpaceColor = 0xFF2C2530.toInt(),
        keyHasantaColor = 0xFF4A2535.toInt(),
        keyBorderColor = 0xFF3A303E.toInt(),
        labelColor = 0xFFFCE4EC.toInt(),
        labelDimColor = 0xFFC48B9F.toInt(),
        accentColor = 0xFFF48FB1.toInt(),
        rippleColor = 0x40F48FB1.toInt(),
        glideStrokeColor = 0xFFFF80AB.toInt(),
        glideGlowColor = 0x40F48FB1.toInt(),
        isDark = true,
    )

    val THEME_FOREST_EMERALD = KeyboardTheme(
        id = ID_FOREST_EMERALD,
        nameBengali = "ফরেস্ট এমারেল্ড",
        nameEnglish = "Forest Emerald",
        backgroundColor = 0xFF0A1410.toInt(),
        keyNormalColor = 0xFF12241C.toInt(),
        keyShiftColor = 0xFF0C1A14.toInt(),
        keySpaceColor = 0xFF182E24.toInt(),
        keyHasantaColor = 0xFF13422E.toInt(),
        keyBorderColor = 0xFF1E3D30.toInt(),
        labelColor = 0xFFE8F5E9.toInt(),
        labelDimColor = 0xFF81C784.toInt(),
        accentColor = 0xFF00E676.toInt(),
        rippleColor = 0x4000E676.toInt(),
        glideStrokeColor = 0xFF00E676.toInt(),
        glideGlowColor = 0x4000E676.toInt(),
        isDark = true,
    )

    val THEME_NORDIC_FROST = KeyboardTheme(
        id = ID_NORDIC_FROST,
        nameBengali = "নর্ডিক ফ্রস্ট",
        nameEnglish = "Nordic Frost",
        backgroundColor = 0xFF0F172A.toInt(),
        keyNormalColor = 0xFF1E293B.toInt(),
        keyShiftColor = 0xFF141E30.toInt(),
        keySpaceColor = 0xFF253349.toInt(),
        keyHasantaColor = 0xFF164E63.toInt(),
        keyBorderColor = 0xFF334155.toInt(),
        labelColor = 0xFFF8FAFC.toInt(),
        labelDimColor = 0xFF94A3B8.toInt(),
        accentColor = 0xFF38BDF8.toInt(),
        rippleColor = 0x4038BDF8.toInt(),
        glideStrokeColor = 0xFF0EA5E9.toInt(),
        glideGlowColor = 0x4038BDF8.toInt(),
        isDark = true,
    )

    val THEME_SUNSET_AMBER = KeyboardTheme(
        id = ID_SUNSET_AMBER,
        nameBengali = "সানসেট অ্যাম্বার",
        nameEnglish = "Sunset Amber",
        backgroundColor = 0xFF17130E.toInt(),
        keyNormalColor = 0xFF241D17.toInt(),
        keyShiftColor = 0xFF1A140F.toInt(),
        keySpaceColor = 0xFF2E241D.toInt(),
        keyHasantaColor = 0xFF4A3018.toInt(),
        keyBorderColor = 0xFF3D3026.toInt(),
        labelColor = 0xFFFFF3E0.toInt(),
        labelDimColor = 0xFFFFB74D.toInt(),
        accentColor = 0xFFFF9100.toInt(),
        rippleColor = 0x40FF9100.toInt(),
        glideStrokeColor = 0xFFFFAB40.toInt(),
        glideGlowColor = 0x40FF9100.toInt(),
        isDark = true,
    )

    val THEME_MOCHA_LATTE = KeyboardTheme(
        id = ID_MOCHA_LATTE,
        nameBengali = "মোকা লাতে",
        nameEnglish = "Mocha Latte",
        backgroundColor = 0xFFF3ECE4.toInt(),
        keyNormalColor = 0xFFFFFFFF.toInt(),
        keyShiftColor = 0xFFE4D8CE.toInt(),
        keySpaceColor = 0xFFFAF6F2.toInt(),
        keyHasantaColor = 0xFFD7CCC8.toInt(),
        keyBorderColor = 0xFFD5C4B5.toInt(),
        labelColor = 0xFF2D241E.toInt(),
        labelDimColor = 0xFF795548.toInt(),
        accentColor = 0xFF6D4C41.toInt(),
        rippleColor = 0x336D4C41.toInt(),
        glideStrokeColor = 0xFF8D6E63.toInt(),
        glideGlowColor = 0x336D4C41.toInt(),
        isDark = false,
    )

    val PRESET_THEMES = listOf(
        THEME_FLOW_TEAL,
        THEME_OLED_BLACK,
        THEME_AVRO_BLUE,
        THEME_NORDIC_FROST,
        THEME_FOREST_EMERALD,
        THEME_SAKURA_DUSK,
        THEME_SUNSET_AMBER,
        THEME_CYBER_INDIGO,
        THEME_HIGH_CONTRAST,
        THEME_DAYLIGHT_LIGHT,
        THEME_MOCHA_LATTE,
    )

    /**
     * Resolves a theme by its string [themeId], or extracts dynamic Material You
     * colors if [themeId] is [ID_MATERIAL_YOU] and the platform is Android 12+.
     */
    fun resolveTheme(context: Context, themeId: String): KeyboardTheme {
        if (themeId == ID_MATERIAL_YOU) {
            return createMaterialYouTheme(context)
        }
        val preset = PRESET_THEMES.find { it.id == themeId }
        if (preset != null) return preset

        val custom = CustomThemeManager.get(context).getCustomTheme(themeId)
        if (custom != null) return custom

        return THEME_FLOW_TEAL
    }

    /**
     * Dynamically builds a theme based on Android 12+ Material You system colors.
     */
    fun createMaterialYouTheme(context: Context): KeyboardTheme {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val sysAccent = context.getColor(android.R.color.system_accent1_300)
                val sysAccentDark = context.getColor(android.R.color.system_accent1_700)
                val sysNeutralDark = context.getColor(android.R.color.system_neutral1_900)
                val sysNeutralSurface = context.getColor(android.R.color.system_neutral1_800)
                val sysNeutralKey = context.getColor(android.R.color.system_neutral1_700)
                val sysText = context.getColor(android.R.color.system_neutral1_50)
                val sysTextDim = context.getColor(android.R.color.system_neutral1_200)

                return KeyboardTheme(
                    id = ID_MATERIAL_YOU,
                    nameBengali = "মেটেরিয়াল ইউ",
                    nameEnglish = "Material You",
                    backgroundColor = sysNeutralDark,
                    keyNormalColor = sysNeutralSurface,
                    keyShiftColor = sysNeutralDark,
                    keySpaceColor = sysNeutralKey,
                    keyHasantaColor = sysAccentDark,
                    keyBorderColor = sysNeutralKey,
                    labelColor = sysText,
                    labelDimColor = sysTextDim,
                    accentColor = sysAccent,
                    rippleColor = (sysAccent and 0x00FFFFFF) or 0x40000000,
                    glideStrokeColor = sysAccent,
                    glideGlowColor = (sysAccent and 0x00FFFFFF) or 0x40000000,
                    isDark = true,
                )
            } catch (_: Exception) {
                // Fall back to default if color extraction fails
            }
        }
        return THEME_FLOW_TEAL
    }
}
