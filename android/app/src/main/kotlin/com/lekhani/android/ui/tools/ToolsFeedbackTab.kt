package com.lekhani.android.ui.tools

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
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import kotlin.math.roundToInt
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lekhani.android.data.dictionary.LekhaniDictionaryManager
import com.lekhani.android.data.settings.KeyboardPreferences

@Composable
fun ToolsFeedbackTab(
    prefs: KeyboardPreferences,
    dictManager: LekhaniDictionaryManager,
    isEnglish: Boolean = false,
    onOpenToolbarCustomizer: () -> Unit,
    onOpenDictionaryManager: () -> Unit,
    onOpenClipboardManager: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

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
        // ── Haptic Feedback Card ────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
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
                                text = if (isEnglish) "Haptic Feedback" else "হ্যাপটিক ফিডব্যাক (Haptics)",
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
                    val strengthLabel = when {
                        hapticDuration <= 15f -> if (isEnglish) "Light" else "হালকা"
                        hapticDuration <= 35f -> if (isEnglish) "Medium" else "মাঝারি"
                        else -> if (isEnglish) "Strong" else "জোরালো"
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEnglish) "Vibration strength" else "ভাইব্রেশন মাত্রা",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = "$strengthLabel (${hapticDuration.toInt()} ms)",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = hapticDuration,
                        onValueChange = {
                            val snapped = ((it / 5f).roundToInt() * 5).coerceIn(5, 60).toFloat()
                            hapticDuration = snapped
                            prefs.hapticDurationMs = snapped.toInt()
                        },
                        valueRange = 5f..60f
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            10 to (if (isEnglish) "Light (10 ms)" else "হালকা (১০ ms)"),
                            25 to (if (isEnglish) "Medium (25 ms)" else "মাঝারি (২৫ ms)"),
                            45 to (if (isEnglish) "Strong (45 ms)" else "জোরালো (৪৫ ms)")
                        ).forEach { (ms, label) ->
                            val isSelected = hapticDuration.toInt() == ms
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    hapticDuration = ms.toFloat()
                                    prefs.hapticDurationMs = ms
                                },
                                label = { Text(label, fontSize = 11.sp, maxLines = 1) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // ── Audio Feedback Card ─────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
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

        // ── Toolbar Customization Entry Card ────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = "Toolbar",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isEnglish) "Quick Toolbar" else "কুইক টুলবার (Toolbar Shortcuts)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = if (isEnglish) "Customize shortcut tools on keyboard strip"
                                   else "কীবোর্ড স্ট্রিপের শর্টকাট টুল সক্রিয় ও সাজান",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onOpenToolbarCustomizer,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (isEnglish) "Customize Toolbar" else "টুলবার সাজান (Customize Toolbar)")
                }
            }
        }

        // ── Personal Dictionary Card ───────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Book,
                        contentDescription = "Dictionary",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isEnglish) "Personal Dictionary" else "ব্যক্তিগত অভিধান (User Dictionary)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = if (isEnglish) "$userWordsCount custom words stored"
                                   else "$userWordsCount টি নিজস্ব শব্দ সংরক্ষিত",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedButton(
                    onClick = onOpenDictionaryManager,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (isEnglish) "Manage & Backup Vocabulary" else "শব্দতালিকা ও ব্যাকআপ (Manage & Backup)")
                }
            }
        }

        // ── Powerful Clipboard Suite Card ──────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Assignment,
                        contentDescription = "Clipboard Suite",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isEnglish) "Clipboard Suite & Vault" else "ক্লিপবোর্ড ও ভল্ট (Clipboard Suite)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = if (isEnglish) "Configurable auto-clear, snapshots, link extractor & text combiner"
                                   else "স্বয়ংক্রিয় ক্লিয়ার, স্ন্যাপশট ব্যাকআপ, লিংক এক্সট্র্যাক্ট ও টেক্সট কম্বাইনার",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onOpenClipboardManager,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (isEnglish) "Open Clipboard Manager" else "ক্লিপবোর্ড ম্যানেজার খুলুন (Open Clipboard)")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
