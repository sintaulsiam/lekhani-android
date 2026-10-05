package com.lekhani.android.data.backup

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LekhaniBackupManagerTest {

    @Test
    fun testInspectValidBackup() {
        val root = JSONObject().apply {
            put("schemaVersion", 1)
            put("appName", "Lekhani Keyboard")
            put("timestamp", 1728100000000L)

            val vocab = JSONObject().apply {
                val words = JSONArray().apply {
                    put("অভ্র")
                    put("লেখনী")
                    put("বাংলা")
                }
                put("userWords", words)
                put("learnedCount", 42)
            }
            put("vocabulary", vocab)

            val shortcuts = JSONObject().apply {
                put("shartho", "স্বার্থ")
                put("dhk", "ঢাকা")
            }
            put("shortcuts", shortcuts)

            val preferences = JSONObject().apply {
                put("haptic_enabled", true)
                put("font_scale", 1.0)
            }
            put("preferences", preferences)

            val clips = JSONArray().apply {
                put(JSONObject().apply {
                    put("text", "Important note")
                    put("isPinned", true)
                })
            }
            put("pinnedClipboard", clips)
        }

        val summary = LekhaniBackupManager.inspectBackup(root.toString())
        assertNotNull(summary)
        assertEquals(1, summary!!.schemaVersion)
        assertEquals(3, summary.userWordsCount)
        assertEquals(42, summary.learnedWordsCount)
        assertEquals(2, summary.shortcutsCount)
        assertEquals(2, summary.preferencesCount)
        assertEquals(1, summary.pinnedClipsCount)
    }

    @Test
    fun testInspectMalformedBackup() {
        val invalidJson = "This is not JSON content"
        val summary = LekhaniBackupManager.inspectBackup(invalidJson)
        assertNull(summary)
    }

    @Test
    fun testInspectEmptyObjectBackup() {
        val emptyJson = "{}"
        val summary = LekhaniBackupManager.inspectBackup(emptyJson)
        assertNotNull(summary)
        assertEquals(1, summary!!.schemaVersion)
        assertEquals(0, summary.userWordsCount)
        assertEquals(0, summary.shortcutsCount)
        assertEquals(0, summary.preferencesCount)
        assertEquals(0, summary.pinnedClipsCount)
    }
}
