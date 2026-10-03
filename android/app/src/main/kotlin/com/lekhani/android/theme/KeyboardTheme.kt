package com.lekhani.android.theme

import android.content.Context
import android.graphics.Color
import android.os.Build

/**
 * ThemeCategory
 * Categorization taxonomy for Lekhani keyboard themes.
 */
enum class ThemeCategory(val titleBengali: String, val titleEnglish: String) {
    ALL("সব থিম", "All Themes"),
    CLASSIC("ক্লাসিক ও মডার্ন", "Classic & Modern"),
    RGB_CHROMA("আরজিবি ডাইনামিক", "RGB & Dynamic"),
    NEON("নিওন ও সাইবার", "Neon & Cyber"),
    AESTHETIC("এসথেটিক পেস্টেল", "Aesthetic & Pastel"),
    CONTRAST_NATURE("কনট্রাস্ট ও প্রকৃতি", "Contrast & Nature"),
    CUSTOM("কাস্টম", "Custom");
}

/**
 * ChromaMode
 * Dynamic color-shifting algorithms for 120 FPS animated RGB themes.
 */
enum class ChromaMode {
    NONE,
    RAINBOW_FLOW,       // Continuous 360° chromatic wave
    AURORA_BOREALIS,    // Emerald teal -> cyan -> polar violet wave
    SUNSET_HORIZON,     // Golden amber -> fiery coral -> dusk magenta wave
    COSMIC_NEBULA,      // Indigo -> laser violet -> hot pink wave
    MATRIX_PULSE,       // Phosphor lime -> cyber aqua wave
}

/**
 * KeyboardTheme
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
    val isRgbChroma: Boolean = false,
    val chromaMode: ChromaMode = if (isRgbChroma) ChromaMode.RAINBOW_FLOW else ChromaMode.NONE,
    val category: ThemeCategory = ThemeCategory.CLASSIC,
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
        json.put("isRgbChroma", isRgbChroma)
        json.put("chromaMode", chromaMode.name)
        json.put("category", category.name)
        return json
    }

    companion object {
        fun fromJson(json: org.json.JSONObject): KeyboardTheme {
            val cat = try {
                ThemeCategory.valueOf(json.optString("category", ThemeCategory.CUSTOM.name))
            } catch (_: Exception) {
                ThemeCategory.CUSTOM
            }
            val isChroma = json.optBoolean("isRgbChroma", false)
            val cMode = try {
                ChromaMode.valueOf(json.optString("chromaMode", if (isChroma) ChromaMode.RAINBOW_FLOW.name else ChromaMode.NONE.name))
            } catch (_: Exception) {
                if (isChroma) ChromaMode.RAINBOW_FLOW else ChromaMode.NONE
            }
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
                isRgbChroma = isChroma,
                chromaMode = cMode,
                category = cat,
            )
        }
    }
}

/**
 * ThemeRegistry
 * Provides built-in theme presets and dynamic Material You theme generation.
 */
object ThemeRegistry {

    // Classic & Modern IDs
    const val ID_FLOW_TEAL = "flow_teal"
    const val ID_OLED_BLACK = "oled_black"
    const val ID_AVRO_BLUE = "avro_blue"
    const val ID_DAYLIGHT_LIGHT = "daylight_light"
    const val ID_MATERIAL_YOU = "material_you"

    // Neon & Cyber IDs
    const val ID_CYBERPUNK_NEON = "cyberpunk_neon"
    const val ID_MATRIX_GREEN = "matrix_green"
    const val ID_TOKYO_MIDNIGHT = "tokyo_midnight"
    const val ID_SOLAR_FLARE = "solar_flare"
    const val ID_SYNTHWAVE_84 = "synthwave_84"
    const val ID_CYBER_INDIGO = "cyber_indigo"

    // Aesthetic & Pastel IDs
    const val ID_SAKURA_BLOSSOM = "sakura_blossom"
    const val ID_LAVENDER_HAZE = "lavender_haze"
    const val ID_MATCHA_MINT = "matcha_mint"
    const val ID_PEACH_SORBET = "peach_sorbet"
    const val ID_NORDIC_FROST = "nordic_frost"
    const val ID_MOCHA_LATTE = "mocha_latte"

    // RGB & Dynamic Chroma IDs
    const val ID_RGB_CHROMA_FLOW = "rgb_chroma_flow"
    const val ID_AURORA_BOREALIS = "aurora_borealis"
    const val ID_SUNSET_HORIZON = "sunset_horizon"
    const val ID_COSMIC_NEBULA = "cosmic_nebula"
    const val ID_MATRIX_PULSE = "matrix_pulse"

    // High Contrast & Nature IDs
    const val ID_HIGH_CONTRAST = "high_contrast"
    const val ID_FOREST_EMERALD = "forest_emerald"

    // Legacy Aliases
    const val ID_SAKURA_DUSK = ID_SAKURA_BLOSSOM
    const val ID_SUNSET_AMBER = ID_SUNSET_HORIZON

    // ── 1. Classic & Modern ───────────────────────────────────────────────────

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
        category = ThemeCategory.CLASSIC,
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
        category = ThemeCategory.CLASSIC,
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
        category = ThemeCategory.CLASSIC,
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
        category = ThemeCategory.CLASSIC,
    )

    // ── 2. Neon & Cyber ───────────────────────────────────────────────────────

    val THEME_CYBERPUNK_NEON = KeyboardTheme(
        id = ID_CYBERPUNK_NEON,
        nameBengali = "সাইবারপাংক নিওন",
        nameEnglish = "Cyberpunk Neon",
        backgroundColor = 0xFF08060F.toInt(),
        keyNormalColor = 0xFF140E24.toInt(),
        keyShiftColor = 0xFF1D0F38.toInt(),
        keySpaceColor = 0xFF1C1333.toInt(),
        keyHasantaColor = 0xFF3D0845.toInt(),
        keyBorderColor = 0xFF00E5FF.toInt(),
        labelColor = 0xFFFFFFFF.toInt(),
        labelDimColor = 0xFF00E5FF.toInt(),
        accentColor = 0xFFFF007F.toInt(),
        rippleColor = 0x50FF007F.toInt(),
        glideStrokeColor = 0xFF00F0FF.toInt(),
        glideGlowColor = 0x6000F0FF.toInt(),
        isDark = true,
        category = ThemeCategory.NEON,
    )

    val THEME_MATRIX_GREEN = KeyboardTheme(
        id = ID_MATRIX_GREEN,
        nameBengali = "ম্যাট্রিক্স টার্মিনাল",
        nameEnglish = "Matrix Terminal",
        backgroundColor = 0xFF040A06.toInt(),
        keyNormalColor = 0xFF0A1C10.toInt(),
        keyShiftColor = 0xFF07140B.toInt(),
        keySpaceColor = 0xFF0E2415.toInt(),
        keyHasantaColor = 0xFF11381E.toInt(),
        keyBorderColor = 0xFF00FF66.toInt(),
        labelColor = 0xFF00FF66.toInt(),
        labelDimColor = 0xFF00B347.toInt(),
        accentColor = 0xFF00FF66.toInt(),
        rippleColor = 0x4000FF66.toInt(),
        glideStrokeColor = 0xFF00FF66.toInt(),
        glideGlowColor = 0x5000FF66.toInt(),
        isDark = true,
        category = ThemeCategory.NEON,
    )

    val THEME_TOKYO_MIDNIGHT = KeyboardTheme(
        id = ID_TOKYO_MIDNIGHT,
        nameBengali = "টোকিও মিডনাইট",
        nameEnglish = "Tokyo Midnight",
        backgroundColor = 0xFF0D0B18.toInt(),
        keyNormalColor = 0xFF19152E.toInt(),
        keyShiftColor = 0xFF130F24.toInt(),
        keySpaceColor = 0xFF221C3E.toInt(),
        keyHasantaColor = 0xFF3B1E63.toInt(),
        keyBorderColor = 0xFF8A2BE2.toInt(),
        labelColor = 0xFFF0E6FF.toInt(),
        labelDimColor = 0xFF00D2FF.toInt(),
        accentColor = 0xFFB026FF.toInt(),
        rippleColor = 0x40B026FF.toInt(),
        glideStrokeColor = 0xFF00D2FF.toInt(),
        glideGlowColor = 0x5000D2FF.toInt(),
        isDark = true,
        category = ThemeCategory.NEON,
    )

    val THEME_SOLAR_FLARE = KeyboardTheme(
        id = ID_SOLAR_FLARE,
        nameBengali = "সোলার ফ্লেয়ার",
        nameEnglish = "Solar Flare",
        backgroundColor = 0xFF120804.toInt(),
        keyNormalColor = 0xFF24130A.toInt(),
        keyShiftColor = 0xFF1A0C06.toInt(),
        keySpaceColor = 0xFF2E180C.toInt(),
        keyHasantaColor = 0xFF4D220A.toInt(),
        keyBorderColor = 0xFFFF6D00.toInt(),
        labelColor = 0xFFFFF3E0.toInt(),
        labelDimColor = 0xFFFFAB40.toInt(),
        accentColor = 0xFFFF3D00.toInt(),
        rippleColor = 0x40FF6D00.toInt(),
        glideStrokeColor = 0xFFFF9100.toInt(),
        glideGlowColor = 0x50FF6D00.toInt(),
        isDark = true,
        category = ThemeCategory.NEON,
    )

    val THEME_SYNTHWAVE_84 = KeyboardTheme(
        id = ID_SYNTHWAVE_84,
        nameBengali = "সিন্থওয়েভ ৮৪",
        nameEnglish = "Synthwave '84",
        backgroundColor = 0xFF140D26.toInt(),
        keyNormalColor = 0xFF24143D.toInt(),
        keyShiftColor = 0xFF1B0E2E.toInt(),
        keySpaceColor = 0xFF2E1A4E.toInt(),
        keyHasantaColor = 0xFF4F1A5E.toInt(),
        keyBorderColor = 0xFFFF007F.toInt(),
        labelColor = 0xFFFEEBFA.toInt(),
        labelDimColor = 0xFF00E5FF.toInt(),
        accentColor = 0xFFFF007F.toInt(),
        rippleColor = 0x40FF007F.toInt(),
        glideStrokeColor = 0xFF00E5FF.toInt(),
        glideGlowColor = 0x5000E5FF.toInt(),
        isDark = true,
        category = ThemeCategory.NEON,
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
        category = ThemeCategory.NEON,
    )

    // ── 3. Aesthetic & Pastel ─────────────────────────────────────────────────

    val THEME_SAKURA_BLOSSOM = KeyboardTheme(
        id = ID_SAKURA_BLOSSOM,
        nameBengali = "সাকুরা ব্লসম",
        nameEnglish = "Sakura Blossom",
        backgroundColor = 0xFF2A1F26.toInt(),
        keyNormalColor = 0xFF3D2C37.toInt(),
        keyShiftColor = 0xFF30232B.toInt(),
        keySpaceColor = 0xFF473441.toInt(),
        keyHasantaColor = 0xFF5C2D44.toInt(),
        keyBorderColor = 0xFF634A59.toInt(),
        labelColor = 0xFFFFF0F5.toInt(),
        labelDimColor = 0xFFF8BBD0.toInt(),
        accentColor = 0xFFFF80AB.toInt(),
        rippleColor = 0x40FF80AB.toInt(),
        glideStrokeColor = 0xFFFF4081.toInt(),
        glideGlowColor = 0x40FF80AB.toInt(),
        isDark = true,
        category = ThemeCategory.AESTHETIC,
    )

    val THEME_LAVENDER_HAZE = KeyboardTheme(
        id = ID_LAVENDER_HAZE,
        nameBengali = "ল্যাভেন্ডার হেজ",
        nameEnglish = "Lavender Haze",
        backgroundColor = 0xFF1C1929.toInt(),
        keyNormalColor = 0xFF2C273D.toInt(),
        keyShiftColor = 0xFF221E30.toInt(),
        keySpaceColor = 0xFF352F4A.toInt(),
        keyHasantaColor = 0xFF4A3E6E.toInt(),
        keyBorderColor = 0xFF4B426B.toInt(),
        labelColor = 0xFFF3F0FF.toInt(),
        labelDimColor = 0xFFD1C4E9.toInt(),
        accentColor = 0xFFB388FF.toInt(),
        rippleColor = 0x40B388FF.toInt(),
        glideStrokeColor = 0xFF9575CD.toInt(),
        glideGlowColor = 0x40B388FF.toInt(),
        isDark = true,
        category = ThemeCategory.AESTHETIC,
    )

    val THEME_MATCHA_MINT = KeyboardTheme(
        id = ID_MATCHA_MINT,
        nameBengali = "মাচা মিন্ট",
        nameEnglish = "Matcha Mint",
        backgroundColor = 0xFF15221B.toInt(),
        keyNormalColor = 0xFF21352A.toInt(),
        keyShiftColor = 0xFF1A2B22.toInt(),
        keySpaceColor = 0xFF2A4234.toInt(),
        keyHasantaColor = 0xFF355C46.toInt(),
        keyBorderColor = 0xFF385746.toInt(),
        labelColor = 0xFFEDF7F0.toInt(),
        labelDimColor = 0xFFA5D6A7.toInt(),
        accentColor = 0xFF66BB6A.toInt(),
        rippleColor = 0x4066BB6A.toInt(),
        glideStrokeColor = 0xFF81C784.toInt(),
        glideGlowColor = 0x4066BB6A.toInt(),
        isDark = true,
        category = ThemeCategory.AESTHETIC,
    )

    val THEME_PEACH_SORBET = KeyboardTheme(
        id = ID_PEACH_SORBET,
        nameBengali = "পিচ সর্বেত",
        nameEnglish = "Peach Sorbet",
        backgroundColor = 0xFF291B19.toInt(),
        keyNormalColor = 0xFF3D2A26.toInt(),
        keyShiftColor = 0xFF31201D.toInt(),
        keySpaceColor = 0xFF47322E.toInt(),
        keyHasantaColor = 0xFF61372E.toInt(),
        keyBorderColor = 0xFF5E3F39.toInt(),
        labelColor = 0xFFFFF3F0.toInt(),
        labelDimColor = 0xFFFFCCBC.toInt(),
        accentColor = 0xFFFF8A65.toInt(),
        rippleColor = 0x40FF8A65.toInt(),
        glideStrokeColor = 0xFFFF7043.toInt(),
        glideGlowColor = 0x40FF8A65.toInt(),
        isDark = true,
        category = ThemeCategory.AESTHETIC,
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
        category = ThemeCategory.AESTHETIC,
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
        category = ThemeCategory.AESTHETIC,
    )

    // ── 4. RGB Chroma & Dynamic ───────────────────────────────────────────────

    val THEME_RGB_CHROMA_FLOW = KeyboardTheme(
        id = ID_RGB_CHROMA_FLOW,
        nameBengali = "আরজিবি ক্রোমা ফ্লো",
        nameEnglish = "RGB Chroma Flow",
        backgroundColor = 0xFF08080C.toInt(),
        keyNormalColor = 0xFF121218.toInt(),
        keyShiftColor = 0xFF0D0D12.toInt(),
        keySpaceColor = 0xFF171720.toInt(),
        keyHasantaColor = 0xFF1C1A2E.toInt(),
        keyBorderColor = 0xFF00E5FF.toInt(),
        labelColor = 0xFFFFFFFF.toInt(),
        labelDimColor = 0xFFB0BEC5.toInt(),
        accentColor = 0xFF00E5B8.toInt(),
        rippleColor = 0x5000E5B8.toInt(),
        glideStrokeColor = 0xFFFF007F.toInt(),
        glideGlowColor = 0x6000E5FF.toInt(),
        isDark = true,
        isRgbChroma = true,
        chromaMode = ChromaMode.RAINBOW_FLOW,
        category = ThemeCategory.RGB_CHROMA,
    )

    val THEME_AURORA_BOREALIS = KeyboardTheme(
        id = ID_AURORA_BOREALIS,
        nameBengali = "অরোরা বোরিয়ালিস",
        nameEnglish = "Aurora Borealis",
        backgroundColor = 0xFF07121E.toInt(),
        keyNormalColor = 0xFF0E2235.toInt(),
        keyShiftColor = 0xFF0A1A29.toInt(),
        keySpaceColor = 0xFF142E46.toInt(),
        keyHasantaColor = 0xFF144D50.toInt(),
        keyBorderColor = 0xFF00E5A3.toInt(),
        labelColor = 0xFFF0FDF4.toInt(),
        labelDimColor = 0xFF5EEAD4.toInt(),
        accentColor = 0xFF2DD4BF.toInt(),
        rippleColor = 0x402DD4BF.toInt(),
        glideStrokeColor = 0xFF38BDF8.toInt(),
        glideGlowColor = 0x502DD4BF.toInt(),
        isDark = true,
        isRgbChroma = true,
        chromaMode = ChromaMode.AURORA_BOREALIS,
        category = ThemeCategory.RGB_CHROMA,
    )

    val THEME_SUNSET_HORIZON = KeyboardTheme(
        id = ID_SUNSET_HORIZON,
        nameBengali = "সানসেট হরাইজন",
        nameEnglish = "Sunset Horizon",
        backgroundColor = 0xFF190C16.toInt(),
        keyNormalColor = 0xFF2A1525.toInt(),
        keyShiftColor = 0xFF1E0E1B.toInt(),
        keySpaceColor = 0xFF351B30.toInt(),
        keyHasantaColor = 0xFF521C38.toInt(),
        keyBorderColor = 0xFFFF4081.toInt(),
        labelColor = 0xFFFFF0F5.toInt(),
        labelDimColor = 0xFFFFAB91.toInt(),
        accentColor = 0xFFFF5252.toInt(),
        rippleColor = 0x40FF5252.toInt(),
        glideStrokeColor = 0xFFFF9100.toInt(),
        glideGlowColor = 0x50FF5252.toInt(),
        isDark = true,
        isRgbChroma = true,
        chromaMode = ChromaMode.SUNSET_HORIZON,
        category = ThemeCategory.RGB_CHROMA,
    )

    val THEME_COSMIC_NEBULA = KeyboardTheme(
        id = ID_COSMIC_NEBULA,
        nameBengali = "কসমিক নেবুলা",
        nameEnglish = "Cosmic Nebula",
        backgroundColor = 0xFF090A1A.toInt(),
        keyNormalColor = 0xFF141630.toInt(),
        keyShiftColor = 0xFF0E1024.toInt(),
        keySpaceColor = 0xFF1C1E40.toInt(),
        keyHasantaColor = 0xFF332057.toInt(),
        keyBorderColor = 0xFF7C4DFF.toInt(),
        labelColor = 0xFFEDE7F6.toInt(),
        labelDimColor = 0xFFB388FF.toInt(),
        accentColor = 0xFF651FFF.toInt(),
        rippleColor = 0x407C4DFF.toInt(),
        glideStrokeColor = 0xFF00E5FF.toInt(),
        glideGlowColor = 0x507C4DFF.toInt(),
        isDark = true,
        isRgbChroma = true,
        chromaMode = ChromaMode.COSMIC_NEBULA,
        category = ThemeCategory.RGB_CHROMA,
    )

    val THEME_MATRIX_PULSE = KeyboardTheme(
        id = ID_MATRIX_PULSE,
        nameBengali = "ম্যাট্রিক্স পালস",
        nameEnglish = "Matrix Pulse",
        backgroundColor = 0xFF050B07.toInt(),
        keyNormalColor = 0xFF0D1C12.toInt(),
        keyShiftColor = 0xFF08140C.toInt(),
        keySpaceColor = 0xFF12281A.toInt(),
        keyHasantaColor = 0xFF144D2B.toInt(),
        keyBorderColor = 0xFF00FF66.toInt(),
        labelColor = 0xFFE0FFE8.toInt(),
        labelDimColor = 0xFF00E5A3.toInt(),
        accentColor = 0xFF00FF66.toInt(),
        rippleColor = 0x4000FF66.toInt(),
        glideStrokeColor = 0xFF00F5D4.toInt(),
        glideGlowColor = 0x5000FF66.toInt(),
        isDark = true,
        isRgbChroma = true,
        chromaMode = ChromaMode.MATRIX_PULSE,
        category = ThemeCategory.RGB_CHROMA,
    )

    // ── 5. Contrast & Nature ──────────────────────────────────────────────────

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
        category = ThemeCategory.CONTRAST_NATURE,
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
        category = ThemeCategory.CONTRAST_NATURE,
    )

    /**
     * All built-in preset themes ordered categorically.
     */
    val PRESET_THEMES: List<KeyboardTheme> = listOf(
        // Classic & Modern
        THEME_FLOW_TEAL,
        THEME_OLED_BLACK,
        THEME_AVRO_BLUE,
        THEME_DAYLIGHT_LIGHT,

        // RGB Chroma & Dynamic
        THEME_RGB_CHROMA_FLOW,
        THEME_AURORA_BOREALIS,
        THEME_SUNSET_HORIZON,
        THEME_COSMIC_NEBULA,
        THEME_MATRIX_PULSE,

        // Neon & Cyber
        THEME_CYBERPUNK_NEON,
        THEME_MATRIX_GREEN,
        THEME_TOKYO_MIDNIGHT,
        THEME_SOLAR_FLARE,
        THEME_SYNTHWAVE_84,
        THEME_CYBER_INDIGO,

        // Aesthetic & Pastel
        THEME_SAKURA_BLOSSOM,
        THEME_LAVENDER_HAZE,
        THEME_MATCHA_MINT,
        THEME_PEACH_SORBET,
        THEME_NORDIC_FROST,
        THEME_MOCHA_LATTE,

        // Contrast & Nature
        THEME_HIGH_CONTRAST,
        THEME_FOREST_EMERALD,
    )

    /**
     * List of all preset theme IDs.
     */
    fun allPresetIds(): List<String> = PRESET_THEMES.map { it.id }

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
                    category = ThemeCategory.CLASSIC,
                )
            } catch (_: Exception) {
                // Fall back to default if color extraction fails
            }
        }
        return THEME_FLOW_TEAL
    }
}
