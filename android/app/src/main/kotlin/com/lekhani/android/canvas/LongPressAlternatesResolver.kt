package com.lekhani.android.canvas

import com.lekhani.android.model.BengaliAlternates
import com.lekhani.android.model.Key
import com.lekhani.android.model.KeyAction

/**
 * LongPressAlternatesResolver
 *
 * Resolves up to 4 alternate characters for long-press popups.
 * Prioritizes the layout-specific Shift character as Candidate #0 on fixed and
 * Bengali layouts (where Shift is a distinct letter or modifier, e.g. ক -> খ, া -> অ).
 * For Latin letters (English/Avro), numbers and diacritics are prioritized.
 */
object LongPressAlternatesResolver {

    fun resolve(key: Key, isShifted: Boolean): Array<String> {
        if (key.action !is KeyAction.Character) return emptyArray()

        val activeChar = key.displayLabel(isShifted)
        val candidates = ArrayList<String>(4)

        // 1. Shift alternative (Primary on fixed & flow layouts where shift is a distinct character)
        val shiftedChar = if (isShifted) key.label else key.shiftedLabel
        if (!shiftedChar.isNullOrEmpty() && shiftedChar != activeChar) {
            val isCaseShiftOnly = shiftedChar.equals(activeChar, ignoreCase = true)
            if (!isCaseShiftOnly) {
                candidates.add(shiftedChar)
            }
        }

        // 2. Explicit key longPressAction character (if configured)
        val explicitLongChar = (key.activeLongPressAction(isShifted) as? KeyAction.Character)?.token
        if (!explicitLongChar.isNullOrEmpty() && explicitLongChar != activeChar && !candidates.contains(explicitLongChar)) {
            candidates.add(explicitLongChar)
        }

        // 3. Key hint label (e.g. top-row numbers or dead-key shortcuts like ৎ, ঁ)
        val hint = key.displayHint(isShifted)
        if (!hint.isNullOrEmpty() && hint != activeChar && !candidates.contains(hint)) {
            candidates.add(hint)
        }

        // 4. Secondary variants & conjuncts from BengaliAlternates dictionary
        val rawToken = (key.action as? KeyAction.Character)?.token
        val rawAlts = (rawToken?.let { BengaliAlternates.getAlternates(it) })
            ?: BengaliAlternates.getAlternates(activeChar)
        if (rawAlts != null) {
            for (alt in rawAlts) {
                if (candidates.size >= 4) break
                if (alt.isNotEmpty() && alt != activeChar && !candidates.contains(alt)) {
                    candidates.add(alt)
                }
            }
        }

        // 5. Fallback for Latin keys without hint: add uppercase shift if no other alternate exists
        if (candidates.isEmpty() && !shiftedChar.isNullOrEmpty() && shiftedChar != activeChar) {
            candidates.add(shiftedChar)
        }

        return if (candidates.isEmpty()) emptyArray() else candidates.toTypedArray()
    }
}
