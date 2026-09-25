package com.lekhani.android.data.emoji

data class SymbolCategory(
    val name: String,
    val items: List<String>,
)

object SymbolData {
    val categories: List<SymbolCategory> = listOf(
        SymbolCategory(
            name = "বাংলা ও বিরামচিহ্ন (Bengali & Punctuation)",
            items = listOf(
                "৳", "।", "॥", "‘", "’", "“", "”", "—", "–", "…",
                "•", "¿", "¡", "«", "»", "‹", "›", "§", "¶", "†"
            )
        ),
        SymbolCategory(
            name = "মুদ্রা প্রতীক (Currencies)",
            items = listOf(
                "৳", "$", "€", "¥", "₹", "£", "₿", "₩", "₽", "¢",
                "₺", "₴", "₫", "₱", "₲", "₸", "₼", "₾", "₿"
            )
        ),
        SymbolCategory(
            name = "গণিত ও বিজ্ঞান (Math & Science)",
            items = listOf(
                "+", "−", "×", "÷", "=", "≠", "≈", "±", "√", "π",
                "∞", "≤", "≥", "%", "‰", "°", "∆", "∑", "∏", "∫",
                "∂", "µ", "Ω", "θ", "λ", "½", "⅓", "¼", "¾"
            )
        ),
        SymbolCategory(
            name = "তীর ও বন্ধনী (Arrows & Brackets)",
            items = listOf(
                "←", "→", "↑", "↓", "↔", "↕", "↖", "↗", "↘", "↙",
                "⇒", "⇐", "⇑", "⇓", "⇔", "【", "】", "〔", "〕", "〈", "〉"
            )
        )
    )
}
