package com.lekhani.android.data.emoji

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for Emoji, Kaomoji, and Symbol data structures and bilingual search.
 * Pure JVM test suite.
 */
class EmojiSearchTest {

    @Test
    fun `bengali keyword search finds matching emojis`() {
        val smileResults = EmojiData.search("হাসি")
        assertTrue("Expected search for 'হাসি' to find smileys", smileResults.isNotEmpty())
        assertTrue(smileResults.any { it.emoji == "😀" || it.emoji == "😂" })

        val fireResults = EmojiData.search("আগুন")
        assertTrue("Expected search for 'আগুন' to find fire", fireResults.isNotEmpty())
        assertTrue(fireResults.any { it.emoji == "🔥" })

        val flagResults = EmojiData.search("বাংলাদেশ")
        assertTrue("Expected search for 'বাংলাদেশ' to find BD flag", flagResults.isNotEmpty())
        assertTrue(flagResults.any { it.emoji == "🇧🇩" })
    }

    @Test
    fun `english keyword search finds matching emojis`() {
        val smileResults = EmojiData.search("smile")
        assertTrue(smileResults.isNotEmpty())
        assertTrue(smileResults.any { it.emoji == "😀" })

        val heartResults = EmojiData.search("heart")
        assertTrue(heartResults.isNotEmpty())
        assertTrue(heartResults.any { it.emoji == "❤️" })
    }

    @Test
    fun `empty and unknown queries return empty lists`() {
        assertTrue(EmojiData.search("").isEmpty())
        assertTrue(EmojiData.search("   ").isEmpty())
        assertTrue(EmojiData.search("xyznonexistentword123").isEmpty())
    }

    @Test
    fun `categories are well-formed and non-empty`() {
        assertFalse(EmojiData.categories.isEmpty())
        EmojiData.categories.forEach { category ->
            assertFalse("Category id should not be blank", category.id.isBlank())
            assertFalse("Category title should not be blank", category.title.isBlank())
            assertFalse("Category icon should not be blank", category.icon.isBlank())
            assertFalse("Category items should not be empty", category.items.isEmpty())
        }
    }

    @Test
    fun `kaomoji categories contain expected emoticons`() {
        assertFalse(KaomojiData.categories.isEmpty())
        val allKaomojis = KaomojiData.categories.flatMap { it.items }
        assertTrue(allKaomojis.contains("(◕‿◕)"))
        assertTrue(allKaomojis.contains("¯\\_(ツ)_/¯"))
        assertTrue(allKaomojis.contains("(ノಠ益ಠ)ノ彡┻━┻"))
    }

    @Test
    fun `symbol categories contain bengali and currency symbols`() {
        assertFalse(SymbolData.categories.isEmpty())
        val allSymbols = SymbolData.categories.flatMap { it.items }
        assertTrue("Must contain Bengali Taka symbol ৳", allSymbols.contains("৳"))
        assertTrue("Must contain Dari ।", allSymbols.contains("।"))
        assertTrue("Must contain Double Dari ॥", allSymbols.contains("॥"))
        assertTrue("Must contain Dollar $", allSymbols.contains("$"))
        assertTrue("Must contain Plus +", allSymbols.contains("+"))
    }

    @Test
    fun `people category items have skin-tone variations`() {
        val peopleCat = EmojiData.categories.find { it.id == "people" }
        assertNotNull(peopleCat)
        val withSkinTones = peopleCat!!.items.filter { it.skinTones.isNotEmpty() }
        assertTrue("People category should have items with skin tone variations", withSkinTones.isNotEmpty())
        withSkinTones.forEach { item ->
            assertEquals(5, item.skinTones.size)
        }
    }
}
