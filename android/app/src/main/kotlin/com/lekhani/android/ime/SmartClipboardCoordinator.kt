package com.lekhani.android.ime

import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import com.lekhani.android.data.clipboard.LekhaniClipboardStore
import com.lekhani.android.data.smart.SmartAssistant
import com.lekhani.android.ui.candidate.CandidateStripState

/**
 * SmartClipboardCoordinator
 *
 * Encapsulates:
 * - System primary clipboard synchronization
 * - Protection against private/password fields
 * - Sniffing OTP, URLs, numbers, and recent snippets
 * - Formatting and expiring CandidateStripState.QuickChip (120s TTL)
 */
class SmartClipboardCoordinator(
    private val context: Context? = null,
    private val clipboardStore: LekhaniClipboardStore? = null,
    private val onNewClipDetected: () -> Unit = {},
) {
    companion object {
        private const val TAG = "SmartClipboardCoord"
        private const val QUICK_CHIP_MAX_AGE_MS = 120_000L
    }

    var lastCopiedText: String? = null
        private set
    var lastCopiedTime: Long = 0L
        private set
    var isQuickChipDismissed: Boolean = false

    private var clipboardListener: ClipboardManager.OnPrimaryClipChangedListener? = null

    fun attach() {
        val sysClipboard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val listener = ClipboardManager.OnPrimaryClipChangedListener {
            syncSystemClipboard(isPrivateField = false)
            onNewClipDetected()
        }
        clipboardListener = listener
        sysClipboard.addPrimaryClipChangedListener(listener)
    }

    fun detach() {
        clipboardListener?.let { listener ->
            val sysClipboard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            sysClipboard?.removePrimaryClipChangedListener(listener)
            clipboardListener = null
        }
    }

    fun syncSystemClipboard(isPrivateField: Boolean) {
        val sysClipboard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        runCatching {
            if (!isPrivateField && sysClipboard.hasPrimaryClip()) {
                val clip = sysClipboard.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val item = clip.getItemAt(0)
                    val text = item?.text?.toString() ?: (context?.let { ctx -> item?.coerceToText(ctx)?.toString() })
                    if (LekhaniClipboardStore.isValidClipText(text)) {
                        val trimmed = text!!.trim()
                        if (trimmed != lastCopiedText) {
                            lastCopiedText = trimmed
                            lastCopiedTime = System.currentTimeMillis()
                            isQuickChipDismissed = false
                        }
                        clipboardStore?.addClip(trimmed)
                    }
                }
            }
        }.onFailure { e ->
            Log.w(TAG, "Failed to sync system primary clip: ${e.message}")
        }
    }

    fun resolveQuickChip(
        isPrivateField: Boolean,
        isComposing: Boolean,
        isEnglish: Boolean,
    ): CandidateStripState.QuickChip? {
        if (isPrivateField || isQuickChipDismissed || isComposing) return null

        val text = lastCopiedText ?: return null
        if (!LekhaniClipboardStore.isValidClipText(text)) return null

        val ageMs = System.currentTimeMillis() - lastCopiedTime
        if (ageMs in 0..QUICK_CHIP_MAX_AGE_MS) {
            val chipInfo = SmartAssistant.inspectClipboard(text, isEnglish)
            if (chipInfo != null) {
                return CandidateStripState.QuickChip(
                    label = chipInfo.label,
                    icon = chipInfo.icon,
                    pasteText = chipInfo.fullText,
                    chipType = chipInfo.type,
                )
            }
        }
        return null
    }

    fun recordSnippet(text: String) {
        val trimmed = text.trim()
        if (trimmed.isNotEmpty() && trimmed != lastCopiedText) {
            lastCopiedText = trimmed
            lastCopiedTime = System.currentTimeMillis()
            isQuickChipDismissed = false
        }
    }
}
