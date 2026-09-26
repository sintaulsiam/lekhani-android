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
)

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
    const val ID_MATERIAL_YOU = "material_you"
    const val ID_DAYLIGHT_LIGHT = "daylight_light"

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

    val PRESET_THEMES = listOf(
        THEME_FLOW_TEAL,
        THEME_OLED_BLACK,
        THEME_AVRO_BLUE,
        THEME_HIGH_CONTRAST,
        THEME_CYBER_INDIGO,
        THEME_DAYLIGHT_LIGHT,
    )

    /**
     * Resolves a theme by its string [themeId], or extracts dynamic Material You
     * colors if [themeId] is [ID_MATERIAL_YOU] and the platform is Android 12+.
     */
    fun resolveTheme(context: Context, themeId: String): KeyboardTheme {
        if (themeId == ID_MATERIAL_YOU) {
            return createMaterialYouTheme(context)
        }
        return PRESET_THEMES.find { it.id == themeId } ?: THEME_FLOW_TEAL
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
