package com.lekhani.android.canvas

import android.graphics.Rect
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import com.lekhani.android.feedback.LekhaniFeedbackManager
import com.lekhani.android.ime.EnterKeyResolver
import com.lekhani.android.model.Key
import com.lekhani.android.model.KeyAction

/**
 * KeyboardAccessibilityDelegate
 *
 * Implements WCAG 2.1 TalkBack accessibility exploration for [KeyboardCanvasView]
 * via [ExploreByTouchHelper]. Provides detailed Bengali pronunciation and semantic
 * announcements for individual keys, modifiers, diacritics, and action buttons.
 */
class KeyboardAccessibilityDelegate(
    private val host: Host
) : ExploreByTouchHelper(host.view) {

    interface Host {
        val view: View
        val resolvedKeys: List<ResolvedKey>
        val isShifted: Boolean
        val isCapsLock: Boolean
        val isShiftActive: Boolean
        val isBottomRowKeyEmoji: Boolean
        val enterActionIcon: EnterKeyResolver.ActionIcon
        val feedbackManager: LekhaniFeedbackManager?
        val keyListener: KeyboardCanvasView.KeyListener?
        fun findKeyIndex(x: Float, y: Float): Int
    }

    override fun getVirtualViewAt(x: Float, y: Float): Int {
        val keyIndex = host.findKeyIndex(x, y)
        return if (keyIndex in host.resolvedKeys.indices) keyIndex else HOST_ID
    }

    override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
        for (i in host.resolvedKeys.indices) {
            virtualViewIds.add(i)
        }
    }

    @Suppress("DEPRECATION")
    override fun onPopulateNodeForVirtualView(virtualViewId: Int, node: AccessibilityNodeInfoCompat) {
        if (virtualViewId !in host.resolvedKeys.indices) {
            node.text = ""
            node.setBoundsInParent(Rect())
            return
        }
        val resolved = host.resolvedKeys[virtualViewId]
        val key = resolved.key
        val bounds = resolved.bounds

        val rect = Rect(
            bounds.left.toInt(),
            bounds.top.toInt(),
            bounds.right.toInt(),
            bounds.bottom.toInt()
        )
        node.setBoundsInParent(rect)

        val desc = getBengaliAccessibilityDescription(key, host.isShifted)
        node.contentDescription = desc
        node.className = "android.widget.Button"
        node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
        node.isClickable = true
        node.isEnabled = true
    }

    override fun onPerformActionForVirtualView(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
        if (action == AccessibilityNodeInfoCompat.ACTION_CLICK && virtualViewId in host.resolvedKeys.indices) {
            val key = host.resolvedKeys[virtualViewId].key
            val act = key.activeAction(host.isShifted)
            host.feedbackManager?.onKeyFeedback(host.view)
                ?: host.view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            host.keyListener?.onKey(key, act)
            return true
        }
        return false
    }

    private fun getBengaliAccessibilityDescription(key: Key, shifted: Boolean): String {
        val label = key.displayLabel(shifted)
        return when (key.action) {
            KeyAction.Space -> "স্পেসবার (Spacebar)"
            KeyAction.Backspace -> "ব্যাকস্পেস (Backspace)"
            KeyAction.Enter -> {
                val resolvedIcon = if (host.isShiftActive) EnterKeyResolver.ActionIcon.NEWLINE else host.enterActionIcon
                when (resolvedIcon) {
                    EnterKeyResolver.ActionIcon.SEARCH -> "অনুসন্ধান (Search)"
                    EnterKeyResolver.ActionIcon.SEND -> "পাঠান (Send)"
                    EnterKeyResolver.ActionIcon.GO -> "যান (Go)"
                    EnterKeyResolver.ActionIcon.NEXT -> "পরবর্তী (Next)"
                    EnterKeyResolver.ActionIcon.DONE -> "সম্পন্ন (Done)"
                    EnterKeyResolver.ActionIcon.NEWLINE -> "এন্টার (Enter)"
                }
            }
            KeyAction.Shift -> when {
                host.isCapsLock -> "ক্যাপস লক সক্রিয় (Caps Lock Active)"
                shifted -> "শিফট সক্রিয় (Shift Active)"
                else -> "শিফট (Shift)"
            }
            KeyAction.SwitchNumeric -> "সংখ্যা ও প্রতীক (Numbers and symbols)"
            KeyAction.SwitchMoreSymbols -> "অতিরিক্ত প্রতীক (More symbols)"
            KeyAction.SwitchAlpha -> "বর্ণমালা (Alphabet)"
            KeyAction.SwitchNumpad -> "নম্বর প্যাড (Number pad)"
            KeyAction.ToggleBengaliDigits -> "সংখ্যা পরিবর্তন (Toggle Digits)"
            KeyAction.SwitchLayout -> if (host.isBottomRowKeyEmoji) "ইমোজি (Emoji)" else "লেআউট পরিবর্তন (Switch Layout)"
            KeyAction.VoiceTyping -> "ভয়েস টাইপিং (Voice Typing)"
            KeyAction.SwitchEmoji -> "ইমোজি (Emoji)"
            KeyAction.SwitchClipboard -> "ক্লিপবোর্ড (Clipboard)"
            else -> {
                if (key.contentDesc.isNotBlank() && key.contentDesc != key.label) {
                    key.contentDesc
                } else {
                    when (label) {
                        "অ" -> "অ, স্বর অ"
                        "আ" -> "আ, স্বর আ"
                        "ই" -> "ই, হ্রস্ব ই"
                        "ঈ" -> "ঈ, দীর্ঘ ঈ"
                        "উ" -> "উ, হ্রস্ব উ"
                        "ঊ" -> "ঊ, দীর্ঘ ঊ"
                        "ঋ" -> "ঋ, রি"
                        "এ" -> "এ"
                        "ঐ" -> "ঐ"
                        "ও" -> "ও"
                        "ঔ" -> "ঔ"
                        "ক" -> "ক"
                        "খ" -> "খ"
                        "গ" -> "গ"
                        "ঘ" -> "ঘ"
                        "ঙ" -> "ঙ, উঙ"
                        "চ" -> "চ"
                        "ছ" -> "ছ"
                        "জ" -> "জ, বর্গীয় জ"
                        "ঝ" -> "ঝ"
                        "ঞ" -> "ঞ, ইঞ"
                        "ট" -> "ট"
                        "ঠ" -> "ঠ"
                        "ড" -> "ড"
                        "ঢ" -> "ঢ"
                        "ণ" -> "ণ, মূর্ধন্য ণ"
                        "ত" -> "ত"
                        "থ" -> "থ"
                        "দ" -> "দ"
                        "ধ" -> "ধ"
                        "ন" -> "ন, দন্ত্য ন"
                        "প" -> "প"
                        "ফ" -> "ফ"
                        "ব" -> "ব"
                        "ভ" -> "ভ"
                        "ম" -> "ম"
                        "য" -> "য, অন্তঃস্থ য"
                        "র" -> "র"
                        "ল" -> "ল"
                        "শ" -> "শ, তালব্য শ"
                        "ষ" -> "ষ, মূর্ধন্য ষ"
                        "স" -> "স, দন্ত্য স"
                        "হ" -> "হ"
                        "ড়" -> "ড়, ড-এ বিন্দু ড়"
                        "ঢ়" -> "ঢ়, ঢ-এ বিন্দু ঢ়"
                        "য়" -> "য়, অন্তঃস্থ য়"
                        "ৎ" -> "ৎ, খণ্ড ত"
                        "ং" -> "ং, অনুস্বার"
                        "ঃ" -> "ঃ, বিসর্গ"
                        "ঁ" -> "ঁ, চন্দ্রবিন্দু"
                        "্" -> "্, হসন্ত"
                        "া" -> "আ-কার"
                        "ি" -> "ই-কার"
                        "ী" -> "ঈ-কার"
                        "ু" -> "উ-কার"
                        "ূ" -> "ঊ-কার"
                        "ৃ" -> "ঋ-কার"
                        "ে" -> "এ-কার"
                        "ৈ" -> "ঐ-কার"
                        "ো" -> "ও-কার"
                        "ৌ" -> "ঔ-কার"
                        "।" -> "দাঁড়ি (Dari)"
                        "॥" -> "ডাবল দাঁড়ি (Double Dari)"
                        else -> label
                    }
                }
            }
        }
    }
}
