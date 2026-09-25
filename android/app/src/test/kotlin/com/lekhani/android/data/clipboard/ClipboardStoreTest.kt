package com.lekhani.android.data.clipboard

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for clipboard sensitive detection and item modeling.
 * Pure JVM test suite.
 */
class ClipboardStoreTest {

    private val otpRegex = Regex("^\\b\\d{4,8}\\b$")
    private val cardRegex = Regex("^(?:\\d[ -]?){13,19}$")

    private fun isSensitive(text: String): Boolean {
        if (otpRegex.matches(text)) return true
        if (cardRegex.matches(text)) return true
        return false
    }

    @Test
    fun `otp tokens are correctly identified as sensitive`() {
        assertTrue(isSensitive("1234"))
        assertTrue(isSensitive("123456"))
        assertTrue(isSensitive("98765432"))
    }

    @Test
    fun `credit card numbers are correctly identified as sensitive`() {
        assertTrue(isSensitive("4111222233334444"))
        assertTrue(isSensitive("4111-2222-3333-4444"))
        assertTrue(isSensitive("4111 2222 3333 4444"))
    }

    @Test
    fun `regular text and sentences are not flagged as sensitive`() {
        assertFalse(isSensitive("Hello world"))
        assertFalse(isSensitive("আমি বাংলায় গান গাই"))
        assertFalse(isSensitive("Meeting at 3pm today"))
        assertFalse(isSensitive("Invoice number #12345"))
    }

    @Test
    fun `clip item data class preserves fields`() {
        val now = 1700000000000L
        val clip = ClipItem(
            id = 1L,
            text = "কপি করা লেখা",
            timestamp = now,
            isPinned = true,
            isSensitive = false,
        )

        assertEquals(1L, clip.id)
        assertEquals("কপি করা লেখা", clip.text)
        assertEquals(now, clip.timestamp)
        assertTrue(clip.isPinned)
        assertFalse(clip.isSensitive)
    }
}
