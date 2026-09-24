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
        LekhaniLayoutType.PROBHAT  -> NationalLayout.layout   // Phase 3 stub → National until Probhat is fully defined
        LekhaniLayoutType.GBOARD   -> NationalLayout.layout   // Phase 3 stub → National until Gboard is fully defined
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
}
