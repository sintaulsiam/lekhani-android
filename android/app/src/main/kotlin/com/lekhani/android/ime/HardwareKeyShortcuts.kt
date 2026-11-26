package com.lekhani.android.ime

import android.view.KeyEvent
import android.view.inputmethod.InputConnection

/**
 * Handles Desktop / Samsung DeX Ctrl+A/C/V/X hardware keyboard shortcuts.
 */
object HardwareKeyShortcuts {
    fun handleCtrlShortcut(
        keyCode: Int,
        ic: InputConnection,
        onFlushComposing: () -> Unit
    ): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_A -> {
                onFlushComposing()
                ic.performContextMenuAction(android.R.id.selectAll)
                true
            }
            KeyEvent.KEYCODE_C -> {
                ic.performContextMenuAction(android.R.id.copy)
                true
            }
            KeyEvent.KEYCODE_V -> {
                onFlushComposing()
                ic.performContextMenuAction(android.R.id.paste)
                true
            }
            KeyEvent.KEYCODE_X -> {
                onFlushComposing()
                ic.performContextMenuAction(android.R.id.cut)
                true
            }
            else -> false
        }
    }
}
