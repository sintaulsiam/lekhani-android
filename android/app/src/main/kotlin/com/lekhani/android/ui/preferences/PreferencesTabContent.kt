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
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.lekhani.android.feedback.LekhaniFeedbackManager
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
    var bottomRowKeyMode by remember { mutableStateOf(prefs.bottomRowKeyMode) }
    var swipeToDelete by remember { mutableStateOf(prefs.swipeToDeleteEnabled) }
    var glideTyping by remember { mutableStateOf(prefs.glideTypingEnabled) }
    var showKeyPreviews by remember { mutableStateOf(prefs.showKeyPreviews) }
    var keyGlowRipple by remember { mutableStateOf(prefs.keyGlowRippleEnabled) }

    var hapticEnabled by remember { mutableStateOf(prefs.hapticEnabled) }
    var hapticDuration by remember { mutableFloatStateOf(prefs.hapticDurationMs.toFloat()) }

    var soundEnabled by remember { mutableStateOf(prefs.soundEnabled) }
    var activeSoundPack by remember { mutableStateOf(prefs.soundPack) }

    val context = LocalContext.current
    val view = LocalView.current
    val feedbackManager = remember { LekhaniFeedbackManager(context) }
    var testHapticCounter by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        onDispose {
            feedbackManager.release()
        }
    }

    val userWordsCount = remember { dictManager.getUserWords().size }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── 1. Keyboard Form Factor Card ───────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Smartphone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isEnglish) "Form Factor" else "কীবোর্ড মোড",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = if (isEnglish) "Keyboard sizing and placement" else "কীবোর্ডের আকার ও অবস্থান",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                FormFactorPrefOption(
                    title = if (isEnglish) "Standard" else "ফুল স্ক্রিন",
                    subtitle = if (isEnglish) "Standard full-width keyboard" else "ডিফল্ট পূর্ণ প্রস্থ কীবোর্ড",
                    icon = Icons.Filled.Smartphone,
                    selected = formFactor == KeyboardPreferences.FormFactor.STANDARD,
                    onSelect = {
                        formFactor = KeyboardPreferences.FormFactor.STANDARD
                        prefs.formFactor = KeyboardPreferences.FormFactor.STANDARD
                    }
                )

                FormFactorPrefOption(
                    title = if (isEnglish) "One-Handed (Right)" else "একহাতে (ডান)",
                    subtitle = if (isEnglish) "Docked right for one-thumb reach" else "ডান পাশে চেপে রাখা কীবোর্ড",
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    selected = formFactor == KeyboardPreferences.FormFactor.ONE_HANDED_RIGHT,
                    onSelect = {
                        formFactor = KeyboardPreferences.FormFactor.ONE_HANDED_RIGHT
                        prefs.formFactor = KeyboardPreferences.FormFactor.ONE_HANDED_RIGHT
                    }
                )

                FormFactorPrefOption(
                    title = if (isEnglish) "One-Handed (Left)" else "একহাতে (বাম)",
                    subtitle = if (isEnglish) "Docked left for one-thumb reach" else "বাম পাশে চেপে রাখা কীবোর্ড",
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    selected = formFactor == KeyboardPreferences.FormFactor.ONE_HANDED_LEFT,
                    onSelect = {
                        formFactor = KeyboardPreferences.FormFactor.ONE_HANDED_LEFT
                        prefs.formFactor = KeyboardPreferences.FormFactor.ONE_HANDED_LEFT
                    }
                )

                FormFactorPrefOption(
                    title = if (isEnglish) "Floating" else "ভাসমান",
                    subtitle = if (isEnglish) "Movable window anywhere on screen" else "স্ক্রিনের যেকোনো জায়গায় টেনে নেওয়া যায়",
                    icon = Icons.Filled.PictureInPictureAlt,
                    selected = formFactor == KeyboardPreferences.FormFactor.FLOATING,
                    onSelect = {
                        formFactor = KeyboardPreferences.FormFactor.FLOATING
                        prefs.formFactor = KeyboardPreferences.FormFactor.FLOATING
                    }
                )

                FormFactorPrefOption(
                    title = if (isEnglish) "Split" else "স্প্লিট",
                    subtitle = if (isEnglish) "Ergonomic layout for foldables and tablets" else "ট্যাবলেট ও ফোল্ডেবলে দুই বুড়ো আঙুলে টাইপিং",
                    icon = Icons.Filled.VerticalSplit,
                    selected = formFactor == KeyboardPreferences.FormFactor.SPLIT,
                    onSelect = {
                        formFactor = KeyboardPreferences.FormFactor.SPLIT
                        prefs.formFactor = KeyboardPreferences.FormFactor.SPLIT
                    }
                )
            }
        }

        // ── 2. Gestures & Typing Card ──────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Swipe,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isEnglish) "Gestures & Typing" else "জেশ্চার ও টাইপিং",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = if (isEnglish) "Spacebar, backspace, and swipe actions" else "সোয়াইপ ও টাইপিং নিয়ন্ত্রণ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 2.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                // Spacebar Swipe Action
                Text(
                    text = if (isEnglish) "Spacebar swipe action" else "স্পেসবার সোয়াইপ অ্যাকশন",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )

                val swipeOptions = if (isEnglish) {
                    listOf(
                        KeyboardPreferences.SpacebarSwipeMode.CURSOR_NAV to ("Cursor navigation" to "Slide finger to move cursor"),
                        KeyboardPreferences.SpacebarSwipeMode.LAYOUT_SWITCH to ("Switch layout" to "Swipe horizontally to switch language"),
                        KeyboardPreferences.SpacebarSwipeMode.DISABLED to ("Disabled" to "No swipe action on spacebar")
                    )
                } else {
                    listOf(
                        KeyboardPreferences.SpacebarSwipeMode.CURSOR_NAV to ("কার্সার নিয়ন্ত্রণ" to "স্পেসবারে আঙুল টেনে কার্সার সরান"),
                        KeyboardPreferences.SpacebarSwipeMode.LAYOUT_SWITCH to ("ভাষা পরিবর্তন" to "সোয়াইপ করে ভাষা অদলবদল"),
                        KeyboardPreferences.SpacebarSwipeMode.DISABLED to ("বন্ধ" to "কোনো সোয়াইপ অ্যাকশন থাকবে না")
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

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 2.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )

                // Bottom Row Secondary Key Mode
                Text(
                    text = if (isEnglish) "Bottom-row secondary key" else "নিচের সারির বিকল্প কি",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )

                val bottomKeyOptions = if (isEnglish) {
                    listOf(
                        KeyboardPreferences.BottomRowKeyMode.SMART to ("Smart (Automatic)" to "Emoji key when spacebar switches layout or only 1 layout; Globe otherwise"),
                        KeyboardPreferences.BottomRowKeyMode.EMOJI to ("Always Emoji Key (😊)" to "Dedicated instant-access emoji button"),
                        KeyboardPreferences.BottomRowKeyMode.LANGUAGE_SWITCH to ("Always Language Key (🌐)" to "Dedicated language / layout switch button")
                    )
                } else {
                    listOf(
                        KeyboardPreferences.BottomRowKeyMode.SMART to ("স্মার্ট / স্বয়ংক্রিয়" to "স্পেসবারে ভাষা পরিবর্তন থাকলে ইমোজি কি, নয়তো ভাষা কি"),
                        KeyboardPreferences.BottomRowKeyMode.EMOJI to ("সর্বদা ইমোজি কি (😊)" to "সহজে ইমোজি ব্যবহারের জন্য স্থায়ী বাটন"),
                        KeyboardPreferences.BottomRowKeyMode.LANGUAGE_SWITCH to ("সর্বদা ভাষা কি (🌐)" to "লেআউট ও ভাষা পরিবর্তনের জন্য স্থায়ী বাটন")
                    )
                }

                bottomKeyOptions.forEach { (mode, pair) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                bottomRowKeyMode = mode
                                prefs.bottomRowKeyMode = mode
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (bottomRowKeyMode == mode),
                            onClick = {
                                bottomRowKeyMode = mode
                                prefs.bottomRowKeyMode = mode
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(text = pair.first, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                            Text(text = pair.second, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 2.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )

                // Glide Typing Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = if (isEnglish) "Glide typing" else "গ্লাইড টাইপিং",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = if (isEnglish) "Slide across letters to form words" else "আঙুল না তুলে টেনে শব্দ লিখুন",
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
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = if (isEnglish) "Swipe to delete" else "সোয়াইপ করে মুছুন",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = if (isEnglish) "Slide left from backspace to erase words" else "ব্যাকস্পেস থেকে বামে টেনে শব্দ মুছুন",
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
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = if (isEnglish) "Popup on keypress" else "কী চাপলে প্রিভিউ বাবল",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = if (isEnglish) "Show magnified letter above pressed key" else "কী চাপলে অক্ষরের বড় বাবল দেখাবে",
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

                // Key Tap Animation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = if (isEnglish) "Key tap animation" else "কী ট্যাপ অ্যানিমেশন",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = if (isEnglish) "Visual ripple effect when tapping keys" else "কী চাপলে স্পর্শ অ্যানিমেশন",
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

        // ── 3. Vibration & Sound Feedback Card ─────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Haptics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Vibration,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isEnglish) "Vibration" else "ভাইব্রেশন",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = if (isEnglish) "Haptic feedback on keypress" else "কী চাপলে সূক্ষ্ম কম্পন",
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
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isEnglish) "Vibration strength: ${hapticDuration.toInt()} ms"
                               else "ভাইব্রেশনের মাত্রা: ${hapticDuration.toInt()} ms",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                    Slider(
                        value = hapticDuration,
                        onValueChange = {
                            hapticDuration = it
                            prefs.hapticDurationMs = it.toInt()
                        },
                        onValueChangeFinished = {
                            feedbackManager.onKeyFeedback(view)
                        },
                        valueRange = 5f..60f,
                        steps = 11
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(
                            onClick = {
                                testHapticCounter++
                                feedbackManager.onKeyFeedback(view)
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isEnglish) "Test Haptic Pulse" else "ভাইব্রেশন টেস্ট করুন", fontSize = 12.sp)
                        }
                        if (testHapticCounter > 0) {
                            Text(
                                text = if (isEnglish) "✓ Fired (${hapticDuration.toInt()} ms #$testHapticCounter)"
                                       else "✓ সম্পন্ন (${hapticDuration.toInt()} ms #$testHapticCounter)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(12.dp))

                // Sound
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isEnglish) "Key clicks" else "কী ক্লিক সাউন্ড",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = if (isEnglish) "Audio feedback on tap" else "কী চাপলে অডিও শব্দ",
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
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isEnglish) "Sound profile" else "সাউন্ড প্রোফাইল",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    val soundPacks = if (isEnglish) {
                        listOf(
                            KeyboardPreferences.SOUND_SYSTEM to "System click",
                            KeyboardPreferences.SOUND_BUBBLE to "Soft bubble",
                            KeyboardPreferences.SOUND_MECHANICAL to "Mechanical switch",
                            KeyboardPreferences.SOUND_TYPEWRITER to "Classic typewriter",
                            KeyboardPreferences.SOUND_WOODBLOCK to "Wooden clack"
                        )
                    } else {
                        listOf(
                            KeyboardPreferences.SOUND_SYSTEM to "সিস্টেম ক্লিক",
                            KeyboardPreferences.SOUND_BUBBLE to "সফট বাবল",
                            KeyboardPreferences.SOUND_MECHANICAL to "মেকানিক্যাল সুইচ",
                            KeyboardPreferences.SOUND_TYPEWRITER to "টাইপরাইটার",
                            KeyboardPreferences.SOUND_WOODBLOCK to "উডেন ক্ল্যাক"
                        )
                    }

                    soundPacks.forEach { (id, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    activeSoundPack = id
                                    prefs.soundPack = id
                                }
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (activeSoundPack == id),
                                onClick = {
                                    activeSoundPack = id
                                    prefs.soundPack = id
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        // ── 4. Quick Toolbar & Personal Dictionary Card ────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isEnglish) "Tools & Dictionary" else "টুলস ও ডিকশনারি",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = if (isEnglish) "Toolbar actions and custom vocabulary" else "টুলবার বিন্যাস ও নিজস্ব শব্দভাণ্ডার",
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
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = if (isEnglish) "Candidate strip tools" else "স্ট্রিপের টুলস বিন্যাস",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = if (isEnglish) "Reorder or show/hide quick action icons" else "ইমোজি, ক্লিপবোর্ড, সেটিংস ইত্যাদি সাজান",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = onOpenToolbarCustomizer,
                        shape = RoundedCornerShape(8.dp)
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
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = if (isEnglish) "Personal dictionary" else "ব্যক্তিগত শব্দকোষ",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = if (isEnglish) "$userWordsCount custom words learned" else "$userWordsCount টি নিজস্ব শব্দ সংরক্ষিত",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = onOpenDictionaryManager,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (isEnglish) "Manage" else "ম্যানেজ")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // About item
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = if (isEnglish) "About Lekhani" else "লেখনি সম্পর্কে",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = if (isEnglish) "Version, privacy policy & open-source licenses" else "সংস্করণ, তথ্য সুরক্ষা ও ওপেন সোর্স লাইসেন্স",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = onOpenAbout,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (isEnglish) "About" else "সম্পর্কে")
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
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
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
