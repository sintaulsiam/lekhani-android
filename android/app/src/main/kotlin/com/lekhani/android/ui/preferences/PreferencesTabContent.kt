package com.lekhani.android.ui.preferences

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lekhani.android.data.dictionary.LekhaniDictionaryManager
import com.lekhani.android.data.settings.KeyboardPreferences

/**
 * PreferencesTabContent
 * ══════════════════════════════════════════════════════════════════════════════
 * Unified Material 3 Preferences screen combining:
 * 1. Form Factors & Typing Modes
 * 2. Gestures (Spacebar swipe, Backspace swipe-delete, Glide typing)
 * 3. Haptic Vibration & Audio Click Feedback
 * 4. Customization Tools (Toolbar & User Dictionary)
 * 5. About & Privacy Guarantee
 */
@Composable
fun PreferencesTabContent(
    prefs: KeyboardPreferences,
    dictManager: LekhaniDictionaryManager,
    isEnglish: Boolean = false,
    onOpenToolbarCustomizer: () -> Unit,
    onOpenDictionaryManager: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    var formFactor by remember { mutableStateOf(prefs.formFactor) }
    var spacebarSwipeMode by remember { mutableStateOf(prefs.spacebarSwipeMode) }
    var swipeToDelete by remember { mutableStateOf(prefs.swipeToDeleteEnabled) }
    var glideTyping by remember { mutableStateOf(prefs.glideTypingEnabled) }
    var showKeyPreviews by remember { mutableStateOf(prefs.showKeyPreviews) }
    var keyGlowRipple by remember { mutableStateOf(prefs.keyGlowRippleEnabled) }

    var hapticEnabled by remember { mutableStateOf(prefs.hapticEnabled) }
    var hapticDuration by remember { mutableFloatStateOf(prefs.hapticDurationMs.toFloat()) }

    var soundEnabled by remember { mutableStateOf(prefs.soundEnabled) }
    var activeSoundPack by remember { mutableStateOf(prefs.soundPack) }

    val userWordsCount = remember { dictManager.getUserWords().size }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── 1. Keyboard Form Factor Card ───────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Smartphone,
                        contentDescription = "Form Factor",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isEnglish) "Keyboard Form Factor" else "কীবোর্ড লেআউট মোড (Modes)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = if (isEnglish) "Ergonomic layout sizing and docking" else "স্ক্রিন সাইজ ও টাইপিং কমফোর্ট অনুযায়ী নির্বাচন করুন",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                FormFactorPrefOption(
                    title = if (isEnglish) "Standard Full Width" else "ফুল স্ক্রিন (Standard Full)",
                    subtitle = if (isEnglish) "Standard keyboard edge-to-edge" else "সাধারণ পূর্ণ প্রস্থ কীবোর্ড",
                    icon = Icons.Filled.Smartphone,
                    selected = formFactor == KeyboardPreferences.FormFactor.STANDARD,
                    onSelect = {
                        formFactor = KeyboardPreferences.FormFactor.STANDARD
                        prefs.formFactor = KeyboardPreferences.FormFactor.STANDARD
                    }
                )

                FormFactorPrefOption(
                    title = if (isEnglish) "One-Handed (Right)" else "একহাতে মোড - ডান (One-Handed Right)",
                    subtitle = if (isEnglish) "Docked to right side for one-thumb typing" else "ডান পাশে সংকুচিত কীবোর্ড, একহাতে দ্রুত ব্যবহারের জন্য",
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    selected = formFactor == KeyboardPreferences.FormFactor.ONE_HANDED_RIGHT,
                    onSelect = {
                        formFactor = KeyboardPreferences.FormFactor.ONE_HANDED_RIGHT
                        prefs.formFactor = KeyboardPreferences.FormFactor.ONE_HANDED_RIGHT
                    }
                )

                FormFactorPrefOption(
                    title = if (isEnglish) "One-Handed (Left)" else "একহাতে মোড - বাম (One-Handed Left)",
                    subtitle = if (isEnglish) "Docked to left side for one-thumb typing" else "বাম পাশে সংকুচিত কীবোর্ড, একহাতে দ্রুত ব্যবহারের জন্য",
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    selected = formFactor == KeyboardPreferences.FormFactor.ONE_HANDED_LEFT,
                    onSelect = {
                        formFactor = KeyboardPreferences.FormFactor.ONE_HANDED_LEFT
                        prefs.formFactor = KeyboardPreferences.FormFactor.ONE_HANDED_LEFT
                    }
                )

                FormFactorPrefOption(
                    title = if (isEnglish) "Floating Window" else "ভাসমান উইন্ডো (Floating Window)",
                    subtitle = if (isEnglish) "Movable compact window anywhere on screen" else "স্ক্রিনের যেকোনো জায়গায় টেনে রাখা যায় এমন কমপ্যাক্ট উইন্ডো",
                    icon = Icons.Filled.PictureInPictureAlt,
                    selected = formFactor == KeyboardPreferences.FormFactor.FLOATING,
                    onSelect = {
                        formFactor = KeyboardPreferences.FormFactor.FLOATING
                        prefs.formFactor = KeyboardPreferences.FormFactor.FLOATING
                    }
                )

                FormFactorPrefOption(
                    title = if (isEnglish) "Split Mode" else "স্প্লিট মোড (Split Mode)",
                    subtitle = if (isEnglish) "Ergonomic two-thumb typing for tablets & foldables" else "ট্যাবলেট ও ফোল্ডেবলের দুই বুড়ো আঙুলে আরামদায়ক টাইপিং",
                    icon = Icons.Filled.VerticalSplit,
                    selected = formFactor == KeyboardPreferences.FormFactor.SPLIT,
                    onSelect = {
                        formFactor = KeyboardPreferences.FormFactor.SPLIT
                        prefs.formFactor = KeyboardPreferences.FormFactor.SPLIT
                    }
                )
            }
        }

        // ── 2. Smart Gestures & Input Card ─────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Swipe,
                        contentDescription = "Gestures",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isEnglish) "Gestures & Input Behavior" else "জেশ্চার ও টাইপিং অনুভূতি (Gestures)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = if (isEnglish) "Spacebar slide, backspace delete, and glide" else "স্পেসবার সোয়াইপ, ব্যাকস্পেস মুছা ও গ্লাইড টাইপিং",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Spacebar Swipe Action
                Text(
                    text = if (isEnglish) "Spacebar Swipe Behavior" else "স্পেসবার সোয়াইপ অ্যাকশন",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )

                val swipeOptions = if (isEnglish) {
                    listOf(
                        KeyboardPreferences.SpacebarSwipeMode.CURSOR_NAV to ("Cursor Slide Navigation" to "Slide finger to move text cursor precisely"),
                        KeyboardPreferences.SpacebarSwipeMode.LAYOUT_SWITCH to ("Quick Layout Switch" to "Swipe horizontally to switch English/Bengali"),
                        KeyboardPreferences.SpacebarSwipeMode.DISABLED to ("Disabled" to "No gesture on spacebar")
                    )
                } else {
                    listOf(
                        KeyboardPreferences.SpacebarSwipeMode.CURSOR_NAV to ("কার্সার স্লাইড (Cursor Slide)" to "স্পেসবারে আঙুল টেনে কার্সার সূক্ষ্মভাবে সরান"),
                        KeyboardPreferences.SpacebarSwipeMode.LAYOUT_SWITCH to ("লেআউট পরিবর্তন (Layout Switch)" to "সোয়াইপ করে ইংরেজি ও বাংলা পরিবর্তন"),
                        KeyboardPreferences.SpacebarSwipeMode.DISABLED to ("নিষ্ক্রিয় (Disabled)" to "স্পেসবারে সোয়াইপ বন্ধ রাখুন")
                    )
                }

                swipeOptions.forEach { (mode, pair) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                spacebarSwipeMode = mode
                                prefs.spacebarSwipeMode = mode
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (spacebarSwipeMode == mode),
                            onClick = {
                                spacebarSwipeMode = mode
                                prefs.spacebarSwipeMode = mode
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(text = pair.first, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                            Text(text = pair.second, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // Glide Typing Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isEnglish) "Glide Typing (Gesture Input)" else "গ্লাইড টাইপিং (সোয়াইপ ইনপুট)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = if (isEnglish) "Slide continuously over keys to write words" else "আঙুল না তুলে অক্ষরের ওপর দিয়ে টেনে টাইপ করুন",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = glideTyping,
                        onCheckedChange = {
                            glideTyping = it
                            prefs.glideTypingEnabled = it
                        }
                    )
                }

                // Swipe-to-delete Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isEnglish) "Swipe-to-Delete on Backspace" else "ব্যাকস্পেস সোয়াইপ-টু-ডিলিট",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = if (isEnglish) "Slide left from backspace to erase words rapidly" else "ব্যাকস্পেস থেকে বামে সোয়াইপ করে একসাথে একাধিক শব্দ মুছুন",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = swipeToDelete,
                        onCheckedChange = {
                            swipeToDelete = it
                            prefs.swipeToDeleteEnabled = it
                        }
                    )
                }

                // Key Previews Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isEnglish) "Key Press Popup Preview" else "কি-প্রেস পপআপ প্রিভিউ (Popups)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = if (isEnglish) "Display magnified popup bubble when pressing a key" else "বোতাম চাপলে বড় করে প্রিভিউ বাবল প্রদর্শন",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = showKeyPreviews,
                        onCheckedChange = {
                            showKeyPreviews = it
                            prefs.showKeyPreviews = it
                        }
                    )
                }

                // Key Glow & Ripple Effect
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isEnglish) "Key Glow & Spring Ripple" else "কি গ্লো ও স্প্রিং রিপল এফেক্ট",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = if (isEnglish) "Smooth 120 FPS capacitive key animations" else "মসৃণ ১২০ এফপিএস টাচ লাইটিং ও প্রতিক্রিয়া অ্যানিমেশন",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = keyGlowRipple,
                        onCheckedChange = {
                            keyGlowRipple = it
                            prefs.keyGlowRippleEnabled = it
                        }
                    )
                }
            }
        }

        // ── 3. Haptic & Sound Feedback Card ────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Haptics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Vibration,
                            contentDescription = "Haptics",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isEnglish) "Haptic Feedback" else "হ্যাপটিক ভাইব্রেশন (Haptics)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = if (isEnglish) "Vibrate on keystroke" else "কি-প্রেসে স্পর্শ অনুভূতি",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = hapticEnabled,
                        onCheckedChange = {
                            hapticEnabled = it
                            prefs.hapticEnabled = it
                        }
                    )
                }

                if (hapticEnabled) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = if (isEnglish) "Vibration strength: ${hapticDuration.toInt()} ms"
                               else "ভাইব্রেশন মাত্রা: ${hapticDuration.toInt()} ms",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                    Slider(
                        value = hapticDuration,
                        onValueChange = {
                            hapticDuration = it
                            prefs.hapticDurationMs = it.toInt()
                        },
                        valueRange = 5f..60f,
                        steps = 11
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(14.dp))

                // Sound
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Sound",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isEnglish) "Key Sounds" else "কি-প্রেস সাউন্ড (Key Sounds)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = if (isEnglish) "Audio click on key tap" else "বোতাম স্পর্শে অডিও ফিডব্যাক",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = soundEnabled,
                        onCheckedChange = {
                            soundEnabled = it
                            prefs.soundEnabled = it
                        }
                    )
                }

                if (soundEnabled) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = if (isEnglish) "Sound Profile" else "সাউন্ড প্রোফাইল (Sound Pack)",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val soundPacks = if (isEnglish) {
                        listOf(
                            KeyboardPreferences.SOUND_SYSTEM to "System Click",
                            KeyboardPreferences.SOUND_BUBBLE to "Soft Bubble",
                            KeyboardPreferences.SOUND_MECHANICAL to "Mechanical Switch",
                            KeyboardPreferences.SOUND_TYPEWRITER to "Classic Typewriter",
                            KeyboardPreferences.SOUND_WOODBLOCK to "Wooden Clack"
                        )
                    } else {
                        listOf(
                            KeyboardPreferences.SOUND_SYSTEM to "সিস্টেম স্ট্যান্ডার্ড (System Click)",
                            KeyboardPreferences.SOUND_BUBBLE to "সফট বাবল (Soft Bubble)",
                            KeyboardPreferences.SOUND_MECHANICAL to "মেকানিক্যাল সুইচ (Mechanical Click)",
                            KeyboardPreferences.SOUND_TYPEWRITER to "টাইপরাইটার (Typewriter)",
                            KeyboardPreferences.SOUND_WOODBLOCK to "উডেন ক্ল্যাক (Wooden Clack)"
                        )
                    }

                    soundPacks.forEach { (id, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (activeSoundPack == id),
                                onClick = {
                                    activeSoundPack = id
                                    prefs.soundPack = id
                                }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        // ── 4. Quick Toolbar & User Dictionary Card ────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = "Tools",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isEnglish) "Advanced Customization" else "টুলস ও ডিকশনারি (Tools)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = if (isEnglish) "Toolbar actions and custom vocabulary" else "টুলবার অ্যাকশন এবং নিজস্ব শব্দকোষ সাজান",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Toolbar customizer button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isEnglish) "Quick Toolbar Ordering" else "কুইক টুলবার বিন্যাস",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = if (isEnglish) "Reorder or toggle emojis, clipboard, and resize icons" else "ইমোজি, ক্লিপবোর্ড, রিসাইজ বাটন সাজান বা লুকাতে পারেন",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = onOpenToolbarCustomizer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (isEnglish) "Customize" else "সাজান")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // Dictionary manager button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isEnglish) "Personal Dictionary" else "ব্যক্তিগত শব্দকোষ",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = if (isEnglish) "$userWordsCount custom learned words" else "$userWordsCount টি সংরক্ষিত নিজস্ব শব্দ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = onOpenDictionaryManager,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (isEnglish) "Manage" else "ম্যানেজ")
                    }
                }
            }
        }

        // ── 5. About Lekhani & Privacy Card ────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = "Privacy",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isEnglish) "100% Offline & Private" else "১০০% অফলাইন ও সম্পূর্ণ গোপনীয়",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = if (isEnglish) "Zero network access. Keystrokes never leave your phone."
                                   else "কোনো ইন্টারনেট অনুমতি নেই। কোনো তথ্য আপনার ফোন ত্যাগ করে না।",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isEnglish) "Crafted by Syntenium & Developers University"
                                   else "নির্মাতা: সিনটেনিয়াম (Syntenium) ও ডেভেলপার্স ইউনিভার্সিটি",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isEnglish) "Free & Open Source under Apache 2.0"
                                   else "সম্পূর্ণ উন্মুক্ত ও ওপেন সোর্স (Apache 2.0)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = onOpenAbout,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (isEnglish) "About App" else "সম্পর্কে")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun FormFactorPrefOption(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        RadioButton(
            selected = selected,
            onClick = onSelect
        )
    }
}
