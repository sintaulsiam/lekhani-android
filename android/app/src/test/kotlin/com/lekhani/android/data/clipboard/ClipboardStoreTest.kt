package com.lekhani.android.data.clipboard

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for clipboard sensitive detection, link extraction,
 * combined text, snapshots, and item modeling.
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

    private fun extractLinks(text: String): List<String> {
        return LekhaniClipboardStore.URL_REGEX.findAll(text).map { it.value.trim().trimEnd('.', ',') }.toList()
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
    fun `clip item data class preserves fields including isSaved`() {
        val now = 1700000000000L
        val clip = ClipItem(
            id = 1L,
            text = "কপি করা লেখা",
            timestamp = now,
            isPinned = true,
            isSaved = true,
            isSensitive = false,
        )

        assertEquals(1L, clip.id)
        assertEquals("কপি করা লেখা", clip.text)
        assertEquals(now, clip.timestamp)
        assertTrue(clip.isPinned)
        assertTrue(clip.isSaved)
        assertFalse(clip.isSensitive)
    }

    @Test
    fun `retention period enum maps minutes correctly`() {
        assertEquals(RetentionPeriod.ONE_HOUR, RetentionPeriod.fromMinutes(60))
        assertEquals(RetentionPeriod.SIX_HOURS, RetentionPeriod.fromMinutes(360))
        assertEquals(RetentionPeriod.TWENTY_FOUR_HOURS, RetentionPeriod.fromMinutes(1440))
        assertEquals(RetentionPeriod.SEVEN_DAYS, RetentionPeriod.fromMinutes(10080))
        assertEquals(RetentionPeriod.NEVER, RetentionPeriod.fromMinutes(0))
        assertEquals(RetentionPeriod.ONE_HOUR, RetentionPeriod.fromMinutes(999)) // fallback
    }

    @Test
    fun `combined text combines items with specified separator`() {
        val items = listOf(
            ClipItem(1L, "First paragraph", 100L),
            ClipItem(2L, "Second paragraph", 200L),
            ClipItem(3L, "Third paragraph", 300L),
        )

        val combined = items.joinToString("\n\n") { it.text }
        assertEquals("First paragraph\n\nSecond paragraph\n\nThird paragraph", combined)
    }

    @Test
    fun `url regex extracts http, https, www, and common domains`() {
        val sample1 = "Check out https://github.com/lekhani for code"
        val links1 = extractLinks(sample1)
        assertEquals(1, links1.size)
        assertEquals("https://github.com/lekhani", links1[0])

        val sample2 = "Visit www.example.com and http://test.org/path?q=1 today."
        val links2 = extractLinks(sample2)
        assertEquals(2, links2.size)
        assertEquals("www.example.com", links2[0])
        assertEquals("http://test.org/path?q=1", links2[1])

        val sample3 = "Read news at prothomalo.com/bangladesh or bdnews24.com"
        val links3 = extractLinks(sample3)
        assertEquals(2, links3.size)
        assertEquals("prothomalo.com/bangladesh", links3[0])
        assertEquals("bdnews24.com", links3[1])
    }

    @Test
    fun `snapshot model captures items correctly`() {
        val clips = listOf(
            ClipItem(1L, "Note 1", 100L, isPinned = true),
            ClipItem(2L, "Note 2", 200L, isSaved = true)
        )
        val snapshot = ClipboardSnapshot(
            id = 500L,
            title = "Test Snapshot",
            timestamp = 500L,
            items = clips
        )

        assertEquals(2, snapshot.items.size)
        assertEquals("Test Snapshot", snapshot.title)
        assertTrue(snapshot.items[0].isPinned)
        assertTrue(snapshot.items[1].isSaved)
    }
}
