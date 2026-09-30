package com.lekhani.android.data.smart

import com.lekhani.android.data.emoji.EmojiData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Lekhani's SmartAssistant on-device intelligence engine:
 *  - Math evaluation (English and Bengali numerals)
 *  - Dynamic Date & Time queries
 *  - Code & token detection (Code Shield)
 *  - Smart clipboard OTP, URL, email, and phone heuristics
 *  - Contextual emoji lookup
 */
class SmartAssistantTest {

    @Test
    fun testNumeralConversion() {
        assertEquals("১২৩৪৫৬৭৮৯০", SmartAssistant.toBengaliDigits("1234567890"))
        assertEquals("1234567890", SmartAssistant.toEnglishDigits("১২৩৪৫৬৭৮৯০"))
        assertEquals("টেস্ট ১২৩", SmartAssistant.toBengaliDigits("টেস্ট 123"))
    }

    @Test
    fun testInlineArithmetic() {
        // Simple addition
        assertEquals("750", SmartAssistant.evaluateMath("500+250="))
        assertEquals("750", SmartAssistant.evaluateMath("500+250"))

        // Bengali digits multiplication
        assertEquals("৬০০", SmartAssistant.evaluateMath("১২০*৫="))
        assertEquals("৬০০", SmartAssistant.evaluateMath("১২০x৫"))

        // Division
        assertEquals("25", SmartAssistant.evaluateMath("100/4="))
        assertEquals("৫০", SmartAssistant.evaluateMath("১০০/২="))

        // Subtraction
        assertEquals("50", SmartAssistant.evaluateMath("75-25="))

        // Invalid expressions
        assertNull(SmartAssistant.evaluateMath("hello+world"))
        assertNull(SmartAssistant.evaluateMath("100/0"))
        assertNull(SmartAssistant.evaluateMath("abc"))
    }

    @Test
    fun testDynamicDateAndTime() {
        assertTrue(SmartAssistant.isDateQuery("tarikh"))
        assertTrue(SmartAssistant.isDateQuery("তারিখ"))
        assertTrue(SmartAssistant.isDateQuery("date"))
        assertFalse(SmartAssistant.isDateQuery("kemon"))

        assertTrue(SmartAssistant.isTimeQuery("somoy"))
        assertTrue(SmartAssistant.isTimeQuery("সময়"))
        assertTrue(SmartAssistant.isTimeQuery("time"))
        assertFalse(SmartAssistant.isTimeQuery("bhalo"))

        val bDate = SmartAssistant.getFormattedDate(isBengali = true)
        assertNotNull(bDate)
        assertTrue(bDate.isNotEmpty())

        val eDate = SmartAssistant.getFormattedDate(isBengali = false)
        assertNotNull(eDate)
        assertTrue(eDate.contains("202"))

        val bTime = SmartAssistant.getFormattedTime(isBengali = true)
        assertNotNull(bTime)
        assertTrue(bTime.contains(":") || bTime.contains("AM") || bTime.contains("PM"))
    }

    @Test
    fun testCodeAndTokenShield() {
        // Mentions and hashtags
        assertTrue(SmartAssistant.isCodeToken("@siam"))
        assertTrue(SmartAssistant.isCodeToken("#bangladesh"))

        // CLI flags
        assertTrue(SmartAssistant.isCodeToken("--help"))
        assertTrue(SmartAssistant.isCodeToken("-rf"))
        assertTrue(SmartAssistant.isCodeToken("-v"))

        // URLs and web domains
        assertTrue(SmartAssistant.isCodeToken("https://github.com"))
        assertTrue(SmartAssistant.isCodeToken("http://localhost:3000"))
        assertTrue(SmartAssistant.isCodeToken("www.lekhani.app"))
        assertTrue(SmartAssistant.isCodeToken("api.github.com"))
        assertTrue(SmartAssistant.isCodeToken("service.io"))

        // Code identifiers
        assertTrue(SmartAssistant.isCodeToken("getUserProfile"))
        assertTrue(SmartAssistant.isCodeToken("onClick"))
        assertTrue(SmartAssistant.isCodeToken("user_id"))
        assertTrue(SmartAssistant.isCodeToken("api_key_v2"))

        // Keywords
        assertTrue(SmartAssistant.isCodeToken("async"))
        assertTrue(SmartAssistant.isCodeToken("await"))
        assertTrue(SmartAssistant.isCodeToken("docker"))
        assertTrue(SmartAssistant.isCodeToken("cargo"))

        // Normal Bengali words or romanized words should NOT be code tokens
        assertFalse(SmartAssistant.isCodeToken("amar"))
        assertFalse(SmartAssistant.isCodeToken("bhalo"))
        assertFalse(SmartAssistant.isCodeToken("tumi"))
    }

    @Test
    fun testSmartClipboardParsing() {
        // OTP 6 digits
        val otpClip = SmartAssistant.inspectClipboard("482910", isEnglish = true)
        assertNotNull(otpClip)
        assertEquals(SmartAssistant.QuickChipType.OTP, otpClip!!.type)
        assertEquals("482910", otpClip.fullText)
        assertTrue(otpClip.label.contains("482910"))

        // URL
        val urlClip = SmartAssistant.inspectClipboard("https://lekhani.app/download")
        assertNotNull(urlClip)
        assertEquals(SmartAssistant.QuickChipType.URL, urlClip!!.type)

        // Email
        val emailClip = SmartAssistant.inspectClipboard("support@lekhani.app")
        assertNotNull(emailClip)
        assertEquals(SmartAssistant.QuickChipType.EMAIL, emailClip!!.type)

        // Phone
        val phoneClip = SmartAssistant.inspectClipboard("01712345678")
        assertNotNull(phoneClip)
        assertEquals(SmartAssistant.QuickChipType.PHONE, phoneClip!!.type)

        // Recent text
        val textClip = SmartAssistant.inspectClipboard("শুভ সকাল", isEnglish = false)
        assertNotNull(textClip)
        assertEquals(SmartAssistant.QuickChipType.RECENT, textClip!!.type)
    }

    @Test
    fun testContextualEmojis() {
        val chaEmojis = EmojiData.findContextualEmojis("cha")
        assertTrue(chaEmojis.contains("☕"))

        val hasiEmojis = EmojiData.findContextualEmojis("hasi")
        assertTrue(hasiEmojis.any { it == "😀" || it == "😃" || it == "😄" })

        val khelaEmojis = EmojiData.findContextualEmojis("khela")
        assertTrue(khelaEmojis.any { it == "⚽" || it == "🏏" })

        val takaEmojis = EmojiData.findContextualEmojis("taka")
        assertTrue(takaEmojis.any { it == "💰" || it == "💵" || it == "৳" || it == "🤑" })

        assertTrue(EmojiData.isEmoji("☕"))
        assertTrue(EmojiData.isEmoji("😀"))
        assertFalse(EmojiData.isEmoji("bangla"))
    }
}
