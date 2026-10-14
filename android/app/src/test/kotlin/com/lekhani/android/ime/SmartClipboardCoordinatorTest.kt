package com.lekhani.android.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SmartClipboardCoordinatorTest {

    @Test
    fun testResolveQuickChipForOtp() {
        val coordinator = SmartClipboardCoordinator()

        coordinator.recordSnippet("OTP: 482901")

        val chip = coordinator.resolveQuickChip(
            isPrivateField = false,
            isComposing = false,
            isEnglish = true
        )

        assertNotNull(chip)
        assertEquals("482901", chip?.pasteText)
    }

    @Test
    fun testResolveQuickChipSuppressedInPrivateField() {
        val coordinator = SmartClipboardCoordinator()

        coordinator.recordSnippet("Your OTP is 482901")

        val chip = coordinator.resolveQuickChip(
            isPrivateField = true,
            isComposing = false,
            isEnglish = true
        )

        assertNull(chip)
    }

    @Test
    fun testResolveQuickChipSuppressedWhenComposing() {
        val coordinator = SmartClipboardCoordinator()

        coordinator.recordSnippet("https://example.com")

        val chip = coordinator.resolveQuickChip(
            isPrivateField = false,
            isComposing = true,
            isEnglish = true
        )

        assertNull(chip)
    }

    @Test
    fun testResolveQuickChipSuppressedWhenDismissed() {
        val coordinator = SmartClipboardCoordinator()

        coordinator.recordSnippet("https://example.com")
        coordinator.isQuickChipDismissed = true

        val chip = coordinator.resolveQuickChip(
            isPrivateField = false,
            isComposing = false,
            isEnglish = true
        )

        assertNull(chip)
    }
}
