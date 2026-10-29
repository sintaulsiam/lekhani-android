package com.lekhani.android.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {

    @Test
    fun testIsVersionNewer_higherPatch() {
        assertTrue(AppUpdateManager.isVersionNewer("0.3.1", "0.3.0"))
        assertTrue(AppUpdateManager.isVersionNewer("v0.3.1", "0.3.0"))
    }

    @Test
    fun testIsVersionNewer_higherMinor() {
        assertTrue(AppUpdateManager.isVersionNewer("0.4.0", "0.3.0"))
        assertTrue(AppUpdateManager.isVersionNewer("v1.0.0", "0.3.0"))
    }

    @Test
    fun testIsVersionNewer_sameVersion() {
        assertFalse(AppUpdateManager.isVersionNewer("0.3.0", "0.3.0"))
        assertFalse(AppUpdateManager.isVersionNewer("v0.3.0", "0.3.0"))
    }

    @Test
    fun testIsVersionNewer_lowerVersion() {
        assertFalse(AppUpdateManager.isVersionNewer("0.2.9", "0.3.0"))
        assertFalse(AppUpdateManager.isVersionNewer("v0.2.0", "0.3.0"))
    }

    @Test
    fun testIsVersionNewer_withSuffixes() {
        assertTrue(AppUpdateManager.isVersionNewer("0.3.1-beta1", "0.3.0"))
        assertFalse(AppUpdateManager.isVersionNewer("0.3.0-rc1", "0.3.0"))
    }
}
