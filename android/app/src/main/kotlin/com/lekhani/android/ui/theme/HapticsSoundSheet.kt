package com.lekhani.android.ui.theme

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lekhani.android.data.settings.KeyboardPreferences
import com.lekhani.android.feedback.LekhaniFeedbackManager

/**
 * HapticsSoundSheet
 * Settings sheet for keypress vibration intensity and sound packs.
 */
@Composable
fun HapticsSoundSheet(
    prefs: KeyboardPreferences,
    onClose: () -> Unit,
    isEnglish: Boolean = false,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val feedbackManager = remember { LekhaniFeedbackManager(context) }

    var hapticEnabled by remember { mutableStateOf(prefs.hapticEnabled) }
    var hapticDuration by remember { mutableIntStateOf(prefs.hapticDurationMs) }
    var soundEnabled by remember { mutableStateOf(prefs.soundEnabled) }
    var soundPack by remember { mutableStateOf(prefs.soundPack) }
    var soundVolume by remember { mutableFloatStateOf(prefs.soundVolume) }
    var testTapCount by remember { mutableIntStateOf(0) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = if (isEnglish) "Back" else "ফিরে যান",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = if (isEnglish) "Haptics & Sound Profiles" else "হ্যাপটিক্স ও সাউন্ড প্রোফাইল",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (isEnglish) "Control keypress vibration and audio feedback" else "বাটনে চাপলে কম্পন এবং অডিও ফিডব্যাক নিয়ন্ত্রণ করুন",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section 1: Haptic Vibration
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = if (isEnglish) "Haptic Vibration" else "হ্যাপটিক ভাইব্রেশন",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = if (isEnglish) "Vibrate on every keypress" else "প্রতিটি কি-প্রেসে মৃদু ভাইব্রেশন দিন",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
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
                                    hapticDuration <= 15 -> if (isEnglish) "Light" else "হালকা"
                                    hapticDuration <= 35 -> if (isEnglish) "Medium" else "মাঝারি"
                                    else -> if (isEnglish) "Strong" else "জোরালো"
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isEnglish) "Vibration strength" else "কম্পনের মাত্রা",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                    )
                                    Text(
                                        text = "$strengthLabel ($hapticDuration ms)",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Slider(
                                    value = hapticDuration.toFloat(),
                                    onValueChange = {
                                        val snapped = ((it / 5f).roundToInt() * 5).coerceIn(5, 60)
                                        hapticDuration = snapped
                                        prefs.hapticDurationMs = snapped
                                    },
                                    onValueChangeFinished = {
                                        feedbackManager.onKeyFeedback(view)
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
                                        val isSelected = hapticDuration == ms
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                hapticDuration = ms
                                                prefs.hapticDurationMs = ms
                                                feedbackManager.onKeyFeedback(view)
                                            },
                                            label = { Text(label, fontSize = 11.sp, maxLines = 1) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 2: Sound Feedback
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = if (isEnglish) "Keypress Sound" else "কি-প্রেস সাউন্ড ফিডব্যাক",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = if (isEnglish) "Audio clicks on key tap" else "টাইপিংয়ে অডিও শব্দ বাজান",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
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
                                    text = if (isEnglish) "Select Sound Profile" else "সাউন্ড প্যাক নির্বাচন করুন",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                val soundPacks = if (isEnglish) {
                                    listOf(
                                        KeyboardPreferences.SOUND_SYSTEM to "System",
                                        KeyboardPreferences.SOUND_BUBBLE to "Bubble",
                                        KeyboardPreferences.SOUND_MECHANICAL to "Mechanical",
                                        KeyboardPreferences.SOUND_TYPEWRITER to "Typewriter",
                                        KeyboardPreferences.SOUND_WOODBLOCK to "Woodblock",
                                    )
                                } else {
                                    listOf(
                                        KeyboardPreferences.SOUND_SYSTEM to "সিস্টেম",
                                        KeyboardPreferences.SOUND_BUBBLE to "বাবল (Bubble)",
                                        KeyboardPreferences.SOUND_MECHANICAL to "মেকানিক্যাল",
                                        KeyboardPreferences.SOUND_TYPEWRITER to "টাইপরাইটার",
                                        KeyboardPreferences.SOUND_WOODBLOCK to "উডব্লক",
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    soundPacks.forEach { (id, label) ->
                                        FilterChip(
                                            selected = soundPack == id,
                                            onClick = {
                                                soundPack = id
                                                prefs.soundPack = id
                                                feedbackManager.onKeyFeedback(view)
                                            },
                                            label = { Text(label, fontSize = 12.sp) },
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = if (isEnglish) "Sound Volume: ${(soundVolume * 100).toInt()}%" else "সাউন্ড ভলিউম: ${(soundVolume * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Slider(
                                    value = soundVolume,
                                    onValueChange = {
                                        soundVolume = it
                                        prefs.soundVolume = it
                                    },
                                    valueRange = 0.1f..1.0f
                                )
                            }
                        }
                    }
                }

                // Section 3: Test Key Tap
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                testTapCount++
                                feedbackManager.onKeyFeedback(view)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isEnglish) "Tap here to test feedback" else "এখানে চাপ দিয়ে টেস্ট করুন", fontWeight = FontWeight.Medium)
                        }

                        if (testTapCount > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    text = if (isEnglish) {
                                        "✓ Feedback Active: ${if (hapticEnabled) "${hapticDuration}ms vibration" else "vibration off"}${if (soundEnabled) " + sound" else ""} (Tap #$testTapCount)"
                                    } else {
                                        "✓ ফিডব্যাক সক্রিয়: ${if (hapticEnabled) "${hapticDuration}ms ভাইব্রেশন" else "ভাইব্রেশন বন্ধ"}${if (soundEnabled) " + সাউন্ড" else ""} (ট্যাপ #$testTapCount)"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    feedbackManager.release()
                    onClose()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(if (isEnglish) "Done" else "সম্পন্ন", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

