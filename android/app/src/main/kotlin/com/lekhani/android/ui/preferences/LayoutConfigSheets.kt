package com.lekhani.android.ui.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lekhani.android.data.settings.KeyboardPreferences

/**
 * Dedicated settings dialog for Lekhani প্রবাহ (Flow) layout.
 */
@Composable
fun ProbahoSettingsDialog(
    prefs: KeyboardPreferences,
    isEnglish: Boolean = false,
    onDismiss: () -> Unit
) {
    var showBilateralAura by remember { mutableStateOf(prefs.showBilateralAura) }
    var showKeyHints by remember { mutableStateOf(prefs.showKeyHints) }
    var swipeUpFlickEnabled by remember { mutableStateOf(prefs.swipeUpFlickEnabled) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top App Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isEnglish) "Back" else "ফিরে যান"
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (isEnglish) "Lekhani প্রবাহ (Flow) Settings" else "লেখনী প্রবাহ লেআউট সেটিংস",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = if (isEnglish) "Ergonomic two-thumb layout preferences" else "দ্বি-আঙুল আঙুলচালনা ও ফ্লো সেটিংস",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Feature Overview Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Keyboard,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = if (isEnglish)
                                    "Probaho layout splits vowels on the left thumb and consonants on the right thumb for natural alternation."
                                else
                                    "প্রবাহ লেআউটে বাম থাম্বে স্বরবর্ণ ও ডান থাম্বে ব্যঞ্জনবর্ণ সাজানো থাকে, যা সর্বোচ্চ টাইপিং গতি নিশ্চিত করে।",
                                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Toggles Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Subscript Hints
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = if (isEnglish) "Key Hints (Subscripts)" else "কি-এর উপরের সংকেত (সাবস্ক্রিপ্ট)",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                    )
                                    Text(
                                        text = if (isEnglish) "Show secondary / shifted characters in the top corner of keys"
                                               else "কি-এর কোণায় শিফট বা ফ্লিক করে টাইপযোগ্য বর্ণ সংকেত দেখাবে",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = showKeyHints,
                                    onCheckedChange = {
                                        showKeyHints = it
                                        prefs.showKeyHints = it
                                    }
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            // Swipe Up Flick
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = if (isEnglish) "Swipe-Up Flick for Shift" else "উপরে সোয়াইপ করে শিফট টাইপ",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                    )
                                    Text(
                                        text = if (isEnglish) "Flick upward on any key to type its secondary shifted letter without pressing Shift"
                                               else "শিফট না চেপেই দ্রুত উপরে সোয়াইপ করে মহাপ্রাণ বর্ণ টাইপ করুন",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = swipeUpFlickEnabled,
                                    onCheckedChange = {
                                        swipeUpFlickEnabled = it
                                        prefs.swipeUpFlickEnabled = it
                                    }
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            // Bilateral Thumb Aura
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = if (isEnglish) "Bilateral Thumb Zone Aura" else "দ্বিপাক্ষিক থাম্ব জোন আভা",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                    )
                                    Text(
                                        text = if (isEnglish) "Subtle color tint on left-hand vowel realm keys (off by default)"
                                               else "বাম হাতের স্বরবর্ণ অঞ্চলে হালকা রঙের আভা দেখাবে",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = showBilateralAura,
                                    onCheckedChange = {
                                        showBilateralAura = it
                                        prefs.showBilateralAura = it
                                    }
                                )
                            }
                        }
                    }

                    // Smart Rules Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (isEnglish) "Intelligent Orthography (Always Active)" else "স্বয়ংক্রিয় ব্যাকরণ নিয়ম (সক্রিয়)",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (isEnglish)
                                    "• Smart Kar Promotion: Typing a Kar at word start promotes it to independent vowel (া → আ, ি → ই)\n" +
                                    "• Auto Kar Demotion: Typing vowel after consonant converts to Kar (ব + ঋ → বৃ)\n" +
                                    "• Hasanta Phola Quick-Picks: Tapping Hasanta (্) surfaces R-phola (্র), Ya-phola (্য), and authentic conjuncts."
                                else
                                    "• কার স্বয়ংক্রিয় রূপান্তর: শব্দের শুরুতে কার চাপলে পূর্ণ স্বরবর্ণ হয় (া → আ, ি → ই)\n" +
                                    "• স্বরবর্ণ ডিমোশন: ব্যঞ্জনের পর স্বরবর্ণ চাপলে কার হয় (ব + ঋ → বৃ)\n" +
                                    "• হসন্ত ফলা কুইক-পিক: হসন্ত (্) চাপলে র-ফলা (্র), য-ফলা (্য) ও যুক্তবর্ণ সরাসরি সাজেশনে আসে।",
                                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dedicated settings dialog for Avro Phonetic layout.
 */
@Composable
fun AvroSettingsDialog(
    prefs: KeyboardPreferences,
    isEnglish: Boolean = false,
    onDismiss: () -> Unit
) {
    var avroShowEnglishPreview by remember { mutableStateOf(prefs.avroShowEnglishPreview) }
    var avroDynamicRecomposition by remember { mutableStateOf(prefs.avroDynamicRecomposition) }
    var avroStripOrder by remember { mutableStateOf(prefs.avroStripOrder) }
    var avroPhoneticBackspaceReopening by remember { mutableStateOf(prefs.avroPhoneticBackspaceReopening) }
    var avroNumeralsBengali by remember { mutableStateOf(prefs.avroNumeralsBengali) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top App Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isEnglish) "Back" else "ফিরে যান"
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (isEnglish) "Avro Phonetic Settings" else "অভ্র ফোনেটিক সেটিংস",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = if (isEnglish) "Phonetic transliteration rules and previews" else "উচ্চারণভিত্তিক টাইপিং ও সাজেশন বিন্যাস",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Dual Script English Preview
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = if (isEnglish) "Dual Script English Preview" else "ইংরেজি ও বাংলা প্রিভিউ ব্যাজ",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                    )
                                    Text(
                                        text = if (isEnglish) "Show typed English input above the Bengali preedit word bubble"
                                               else "টাইপ করা রোমান অক্ষরগুলো বাংলা শব্দের উপরে প্রিভিউ হিসেবে দেখাবে",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = avroShowEnglishPreview,
                                    onCheckedChange = {
                                        avroShowEnglishPreview = it
                                        prefs.avroShowEnglishPreview = it
                                    }
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            // Dynamic Recomposition
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = if (isEnglish) "Dynamic Recomposition" else "শব্দ পুনর্গঠন (রি-কম্পোজিশন)",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                    )
                                    Text(
                                        text = if (isEnglish) "Automatically re-evaluate previous characters when ambiguous prefixes change"
                                               else "উচ্চারণের সুবিধার্থে পূর্ববর্তী বর্ণগুলোকে নতুন অক্ষরের সাথে সমন্বয় করবে",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = avroDynamicRecomposition,
                                    onCheckedChange = {
                                        avroDynamicRecomposition = it
                                        prefs.avroDynamicRecomposition = it
                                    }
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            // Candidate Strip Order
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = if (isEnglish) "Candidate Strip Priority" else "সাজেশন স্ট্রিপে অগ্রাধিকার",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                )
                                Text(
                                    text = if (isEnglish) "Select which candidate appears in the prime first position"
                                           else "সাজেশন তালিকায় সবার শুরুতে কোন শব্দটি থাকবে নির্বাচন করুন",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = avroStripOrder == KeyboardPreferences.STRIP_ORDER_BENGALI_FIRST,
                                        onClick = {
                                            avroStripOrder = KeyboardPreferences.STRIP_ORDER_BENGALI_FIRST
                                            prefs.avroStripOrder = KeyboardPreferences.STRIP_ORDER_BENGALI_FIRST
                                        },
                                        label = {
                                            Text(if (isEnglish) "Bengali First" else "বাংলা শব্দ আগে", fontSize = 12.sp)
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    FilterChip(
                                        selected = avroStripOrder == KeyboardPreferences.STRIP_ORDER_ENGLISH_FIRST,
                                        onClick = {
                                            avroStripOrder = KeyboardPreferences.STRIP_ORDER_ENGLISH_FIRST
                                            prefs.avroStripOrder = KeyboardPreferences.STRIP_ORDER_ENGLISH_FIRST
                                        },
                                        label = {
                                            Text(if (isEnglish) "English First" else "ইংরেজি শব্দ আগে", fontSize = 12.sp)
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            // Phonetic Backspace Reopening
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = if (isEnglish) "Backspace Reopens Pre-edit" else "ব্যাকস্পেসে শব্দ পুনরায় সম্পাদনা",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                    )
                                    Text(
                                        text = if (isEnglish) "Hitting backspace right after committing a word reopens the phonetic composition buffer"
                                               else "শব্দ লেখার সাথে সাথে ব্যাকস্পেস দিলে পূর্বের রোমান কম্পোজিশন ফিরে আসবে",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = avroPhoneticBackspaceReopening,
                                    onCheckedChange = {
                                        avroPhoneticBackspaceReopening = it
                                        prefs.avroPhoneticBackspaceReopening = it
                                    }
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            // Bengali Numerals in Avro
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = if (isEnglish) "Bengali Digits in Avro (১, ২, ৩)" else "অভ্রতে বাংলা সংখ্যা (১, ২, ৩)",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                    )
                                    Text(
                                        text = if (isEnglish) "Number row outputs Bengali digits by default instead of English"
                                               else "সংখ্যা সারিতে ইংরেজি সংখ্যার বদলে ডিফল্টভাবে বাংলা সংখ্যা টাইপ হবে",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = avroNumeralsBengali,
                                    onCheckedChange = {
                                        avroNumeralsBengali = it
                                        prefs.avroNumeralsBengali = it
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
