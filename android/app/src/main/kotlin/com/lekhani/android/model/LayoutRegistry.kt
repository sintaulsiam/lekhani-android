package com.lekhani.android.model

import com.lekhani.android.ffi.LekhaniLayoutType

/**
 * LayoutRegistry — single source of truth for layout → KeyboardLayout mapping
 * ══════════════════════════════════════════════════════════════════════════════
 * All layout objects are singletons (Kotlin objects), so this registry holds
 * only references — zero heap allocation at call time.
 */
object LayoutRegistry {

    fun get(type: LekhaniLayoutType): KeyboardLayout = when (type) {
        LekhaniLayoutType.PROBAHO  -> ProbahLayout.layout
        LekhaniLayoutType.AVRO     -> AvroPhoneticLayout.layout
        LekhaniLayoutType.NATIONAL -> NationalLayout.layout
        LekhaniLayoutType.PROBHAT  -> ProbhatLayout.layout
        LekhaniLayoutType.GBOARD   -> GboardBengaliLayout.layout
        LekhaniLayoutType.ENGLISH  -> EnglishQwertyLayout.layout
    }

    /** All layouts that a user can enable, in default priority order */
    val all: List<LekhaniLayoutType> = listOf(
        LekhaniLayoutType.PROBAHO,
        LekhaniLayoutType.AVRO,
        LekhaniLayoutType.NATIONAL,
        LekhaniLayoutType.PROBHAT,
        LekhaniLayoutType.GBOARD,
        LekhaniLayoutType.ENGLISH,
    )

    fun getBengaliName(type: LekhaniLayoutType): String = when (type) {
        LekhaniLayoutType.PROBAHO  -> "লেখনি প্রবাহ"
        LekhaniLayoutType.AVRO     -> "অভ্র ফোনেটিক"
        LekhaniLayoutType.NATIONAL -> "জাতীয় (BBS)"
        LekhaniLayoutType.PROBHAT  -> "प्रभात (প্রভাত)"
        LekhaniLayoutType.GBOARD   -> "জি-বোর্ড বাংলা"
        LekhaniLayoutType.ENGLISH  -> "ইংরেজি (QWERTY)"
    }

    fun getEnglishName(type: LekhaniLayoutType): String = when (type) {
        LekhaniLayoutType.PROBAHO  -> "Lekhani Probaho"
        LekhaniLayoutType.AVRO     -> "Avro Phonetic"
        LekhaniLayoutType.NATIONAL -> "National BBS"
        LekhaniLayoutType.PROBHAT  -> "Probhat"
        LekhaniLayoutType.GBOARD   -> "Gboard Style"
        LekhaniLayoutType.ENGLISH  -> "English QWERTY"
    }

    fun getDescription(type: LekhaniLayoutType): String = when (type) {
        LekhaniLayoutType.PROBAHO  -> "দ্বি-অঙ্গুলি আধুনিক প্রবাহ লেআউট (বাঁয়ে স্বরবর্ণ, ডানে ব্যঞ্জনবর্ণ)"
        LekhaniLayoutType.AVRO     -> "রোমান ইংরেজি অক্ষরে ক্লাসিক ফোনেটিক প্রতিবর্ণীকরণ (ami → আমি)"
        LekhaniLayoutType.NATIONAL -> "বাংলাদেশ সরকারি BBS মানসম্মত অফিশিয়াল ফিক্সড লেআউট"
        LekhaniLayoutType.PROBHAT  -> "জনপ্রিয় ফোনেটিক ফিক্সড লেআউট (প্রভাত স্ট্যান্ডার্ড)"
        LekhaniLayoutType.GBOARD   -> "অ্যান্ড্রয়েড ব্যবহারকারীদের পরিচিত গুগল জি-বোর্ড ম্যাপিং"
        LekhaniLayoutType.ENGLISH  -> "আন্তর্জাতিক মানসম্মত ইংরেজি বর্ণমালা ও পাসওয়ার্ড লেয়ার"
    }

    fun getSpacebarLabel(type: LekhaniLayoutType, isEnglish: Boolean = false): String = if (isEnglish) {
        when (type) {
            LekhaniLayoutType.PROBAHO  -> "Space • Probaho"
            LekhaniLayoutType.AVRO     -> "Space • Avro"
            LekhaniLayoutType.NATIONAL -> "Space • National"
            LekhaniLayoutType.PROBHAT  -> "Space • Probhat"
            LekhaniLayoutType.GBOARD   -> "Space • Gboard"
            LekhaniLayoutType.ENGLISH  -> "Space • English"
        }
    } else {
        when (type) {
            LekhaniLayoutType.PROBAHO  -> "স্পেস • প্রবাহ"
            LekhaniLayoutType.AVRO     -> "স্পেস • অভ্র"
            LekhaniLayoutType.NATIONAL -> "স্পেস • জাতীয়"
            LekhaniLayoutType.PROBHAT  -> "স্পেস • প্রভাত"
            LekhaniLayoutType.GBOARD   -> "স্পেস • জিবোর্ড"
            LekhaniLayoutType.ENGLISH  -> "স্পেস • ইংরেজি"
        }
    }

    const val PREF_ENABLED_LAYOUTS = "pref_enabled_layouts_order"

    /**
     * Parses a comma-separated list of enabled layouts from SharedPreferences.
     * Ensures at least one valid layout remains returned.
     */
    fun parseEnabledLayouts(csv: String?): List<LekhaniLayoutType> {
        if (csv.isNullOrBlank()) {
            return all
        }
        val parsed = csv.split(",")
            .mapNotNull { name ->
                runCatching { LekhaniLayoutType.valueOf(name.trim()) }.getOrNull()
            }
            .distinct()
        return if (parsed.isEmpty()) listOf(LekhaniLayoutType.PROBAHO, LekhaniLayoutType.ENGLISH) else parsed
    }

    /**
     * Serializes an ordered list of layouts to comma-separated string for DPS storage.
     */
    fun serializeEnabledLayouts(layouts: List<LekhaniLayoutType>): String {
        return layouts.joinToString(",") { it.name }
    }
}
