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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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

        Spacer(modifier = Modifier.height(16.dp))
    }
}
