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
import androidx.compose.foundation.layout.width
import com.lekhani.android.ffi.LekhaniLayoutType
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
 * Reusable row displaying a tri-state cascading setting override:
 * [ Follow Global (On/Off) ] [ Always On ] [ Always Off ]
 */
@Composable
fun LayoutOverrideRow(
    title: String,
    subtitle: String,
    override: KeyboardPreferences.SettingOverride,
    globalActive: Boolean,
    isEnglish: Boolean,
    onOverrideChanged: (KeyboardPreferences.SettingOverride) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val globalStateText = if (globalActive) {
                if (isEnglish) "On" else "চালু"
            } else {
                if (isEnglish) "Off" else "বন্ধ"
            }
            FilterChip(
                selected = override == KeyboardPreferences.SettingOverride.FOLLOW_GLOBAL,
                onClick = { onOverrideChanged(KeyboardPreferences.SettingOverride.FOLLOW_GLOBAL) },
                label = {
                    Text(
                        if (isEnglish) "Follow Global ($globalStateText)"
                        else "গ্লোবাল ($globalStateText)",
                        fontSize = 11.sp
                    )
                },
                shape = RoundedCornerShape(8.dp)
            )
            FilterChip(
                selected = override == KeyboardPreferences.SettingOverride.ALWAYS_ON,
                onClick = { onOverrideChanged(KeyboardPreferences.SettingOverride.ALWAYS_ON) },
                label = { Text(if (isEnglish) "Always On" else "সর্বদা চালু", fontSize = 11.sp) },
                shape = RoundedCornerShape(8.dp)
            )
            FilterChip(
                selected = override == KeyboardPreferences.SettingOverride.ALWAYS_OFF,
                onClick = { onOverrideChanged(KeyboardPreferences.SettingOverride.ALWAYS_OFF) },
                label = { Text(if (isEnglish) "Always Off" else "সর্বদা বন্ধ", fontSize = 11.sp) },
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
}

/**
 * Reusable section for configuring per-layout overrides with cascading fallback to global defaults.
 */
@Composable
fun LayoutOverrideSection(
    layout: LekhaniLayoutType,
    prefs: KeyboardPreferences,
    isEnglish: Boolean
) {
    var stripOverride by remember { mutableStateOf(prefs.getCandidateStripOverride(layout)) }
    var autocorrectOverride by remember { mutableStateOf(prefs.getAutocorrectOverride(layout)) }
    var numberRowOverride by remember { mutableStateOf(prefs.getNumberRowOverride(layout)) }
    var nextWordOverride by remember { mutableStateOf(prefs.getNextWordOverride(layout)) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = if (isEnglish) "Layout Overrides" else "লেআউট ওভাররাইড সেটিংস",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            // Candidate Strip Override
            LayoutOverrideRow(
                title = if (isEnglish) "Candidate & Suggestion Strip" else "সাজেশন ও ক্যান্ডিডেট বার",
                subtitle = if (isEnglish) "Show word suggestions above the keyboard" else "কীবোর্ডের উপরে শব্দের পরামর্শ দেখাবে",
                override = stripOverride,
                globalActive = prefs.candidateStripEnabled,
                isEnglish = isEnglish,
                onOverrideChanged = {
                    stripOverride = it
                    prefs.setCandidateStripOverride(layout, it)
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // Auto-Correction Override
            LayoutOverrideRow(
                title = if (isEnglish) "Auto-Correction / Typo Recovery" else "স্বয়ংক্রিয় বানান সংশোধন",
                subtitle = if (isEnglish) "Automatically recover and correct typos" else "ভুল বানানে স্বয়ংক্রিয় সংশোধন করবে",
                override = autocorrectOverride,
                globalActive = prefs.autocorrectEnabled,
                isEnglish = isEnglish,
                onOverrideChanged = {
                    autocorrectOverride = it
                    prefs.setAutocorrectOverride(layout, it)
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // Dedicated Number Row Override
            LayoutOverrideRow(
                title = if (isEnglish) "Dedicated Number Row" else "স্বতন্ত্র সংখ্যা সারি",
                subtitle = if (isEnglish) "Display dedicated number row on this layout" else "এই লেআউটে উপরে আলাদা সংখ্যা সারি দেখাবে",
                override = numberRowOverride,
                globalActive = prefs.showDedicatedNumberRow,
                isEnglish = isEnglish,
                onOverrideChanged = {
                    numberRowOverride = it
                    prefs.setNumberRowOverride(layout, it)
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // Next-Word Prediction Override
            LayoutOverrideRow(
                title = if (isEnglish) "Next-Word Predictions" else "পরবর্তী শব্দ অনুমান",
                subtitle = if (isEnglish) "Predict subsequent words as you type" else "বাক্যে পরবর্তী সম্ভাব্য শব্দের পরামর্শ দেবে",
                override = nextWordOverride,
                globalActive = prefs.nextWordPredictionEnabled,
                isEnglish = isEnglish,
                onOverrideChanged = {
                    nextWordOverride = it
                    prefs.setNextWordOverride(layout, it)
                }
            )
        }
    }
}

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
                            text = if (isEnglish) "Lekhani প্রবাহ (Flow) Settings" else "লেখনী প্রবাহ সেটিংস",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = if (isEnglish) "Ergonomic two-thumb layout" else "দ্বি-আঙুল এরগনোমিক লেআউট",
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
                            // Bilateral Thumb Aura
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = if (isEnglish) "Thumb Zone Tint" else "থাম্ব জোন আভা",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                    )
                                    Text(
                                        text = if (isEnglish) "Subtle color tint for vowel and consonant zones"
                                               else "স্বরবর্ণ ও ব্যঞ্জনবর্ণ অঞ্চলে রঙের আভা দেখাবে",
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

                    // Layout Overrides Card
                    LayoutOverrideSection(
                        layout = LekhaniLayoutType.PROBAHO,
                        prefs = prefs,
                        isEnglish = isEnglish
                    )
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
                            text = if (isEnglish) "Phonetic typing and previews" else "উচ্চারণভিত্তিক টাইপিং ও প্রিভিউ",
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
                            // English Input Preview
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = if (isEnglish) "English Input Preview" else "ইংরেজি প্রিভিউ",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                    )
                                    Text(
                                        text = if (isEnglish) "Show typed English letters above Bengali preview"
                                               else "বাংলা প্রিভিউয়ের উপরে টাইপ করা ইংরেজি অক্ষর দেখাবে",
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

                            // Smart Recomposition
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = if (isEnglish) "Smart Recomposition" else "শব্দ সমন্বয়",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                    )
                                    Text(
                                        text = if (isEnglish) "Adjust previous letters as typing changes word structure"
                                               else "টাইপ করার সাথে সাথে পূর্ববর্তী অক্ষরের উচ্চারণ সমন্বয় করবে",
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

                            // Candidate Strip Priority
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = if (isEnglish) "Suggestion Priority" else "সাজেশন অগ্রাধিকার",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                )
                                Text(
                                    text = if (isEnglish) "First suggestion in the candidate bar"
                                           else "সাজেশন বারে সবার শুরুতে যা দেখাবে",
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
                                            Text(if (isEnglish) "Bengali First" else "বাংলা আগে", fontSize = 12.sp)
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
                                            Text(if (isEnglish) "English First" else "ইংরেজি আগে", fontSize = 12.sp)
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            // Reopen Word on Backspace
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = if (isEnglish) "Reopen Word on Backspace" else "ব্যাকস্পেসে শব্দ এডিট",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                    )
                                    Text(
                                        text = if (isEnglish) "Resume phonetic editing when deleting back into a word"
                                               else "শব্দ লেখার পর ব্যাকস্পেস দিলে পূর্বের রূপ ফিরিয়ে আনবে",
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

                            // Bengali Numbers in Avro
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = if (isEnglish) "Bengali Numbers (১, ২, ৩)" else "বাংলা সংখ্যা (১, ২, ৩)",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                    )
                                    Text(
                                        text = if (isEnglish) "Type Bengali numbers on number keys by default"
                                               else "সংখ্যা সারিতে ডিফল্টভাবে বাংলা সংখ্যা লিখবে",
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

                    // Layout Overrides Card
                    LayoutOverrideSection(
                        layout = LekhaniLayoutType.AVRO,
                        prefs = prefs,
                        isEnglish = isEnglish
                    )
                }
            }
        }
    }
}

/**
 * Dedicated settings dialog for Probhat (प्रभात) layout.
 */
@Composable
fun ProbhatSettingsDialog(
    prefs: KeyboardPreferences,
    isEnglish: Boolean = false,
    onDismiss: () -> Unit
) {
    var probhatHasantaConjuncts by remember { mutableStateOf(prefs.probhatHasantaConjuncts) }
    var probhatSmartInitialKar by remember { mutableStateOf(prefs.probhatSmartInitialKar) }
    var probhatGeminateDoubleTap by remember { mutableStateOf(prefs.probhatGeminateDoubleTap) }
    var probhatDeadKeyHaptic by remember { mutableStateOf(prefs.probhatDeadKeyHaptic) }

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
                            text = if (isEnglish) "Probhat (प्रभात) Settings" else "প্রভাত লেআউট সেটিংস",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = if (isEnglish) "Probhat layout preferences" else "প্রভাত লেআউট কনফিগারেশন",
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
                            // Conjunct Suggestions
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = if (isEnglish) "Conjunct Suggestions" else "যুক্তবর্ণ সাজেশন",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                    )
                                    Text(
                                        text = if (isEnglish) "Show conjunct suggestions when typing Hasanta (্)"
                                               else "হসন্ত (্) চাপলে সম্ভাব্য যুক্তবর্ণের সাজেশন দেখাবে",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = probhatHasantaConjuncts,
                                    onCheckedChange = {
                                        probhatHasantaConjuncts = it
                                        prefs.probhatHasantaConjuncts = it
                                    }
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            // Auto-Fix Initial Vowels
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = if (isEnglish) "Auto-Fix Initial Vowels" else "শুরুতে স্বরবর্ণ সংশোধন",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                    )
                                    Text(
                                        text = if (isEnglish) "Convert vowel signs at word start to full vowels (া → আ)"
                                               else "শব্দের শুরুতে কার চিহ্ন দিলে স্বরবর্ণে রূপান্তর করবে (া → আ)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = probhatSmartInitialKar,
                                    onCheckedChange = {
                                        probhatSmartInitialKar = it
                                        prefs.probhatSmartInitialKar = it
                                    }
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            // Double-Tap for Conjuncts
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = if (isEnglish) "Double-Tap for Conjuncts" else "ডাবল ট্যাপে দ্বিত্ব বর্ণ",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                    )
                                    Text(
                                        text = if (isEnglish) "Double-tap a consonant for geminates (ত → ত্ত, ব → ব্ব). Tap a 3rd time for separate letters (বলল, তত)."
                                               else "ব্যঞ্জনবর্ণে ডাবল ট্যাপে দ্বিত্ব রূপ (ত → ত্ত, ব → ব্ব)। পৃথক অক্ষরের জন্য ৩য় বার চাপুন (বলল, তত)।",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = probhatGeminateDoubleTap,
                                    onCheckedChange = {
                                        probhatGeminateDoubleTap = it
                                        prefs.probhatGeminateDoubleTap = it
                                    }
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            // Hasanta Vibration
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = if (isEnglish) "Hasanta Vibration" else "হসন্ত ভাইব্রেশন",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                    )
                                    Text(
                                        text = if (isEnglish) "Subtle vibration when Hasanta is pressed"
                                               else "হসন্ত চাপলে মৃদু ভাইব্রেশন প্রদান করবে",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = probhatDeadKeyHaptic,
                                    onCheckedChange = {
                                        probhatDeadKeyHaptic = it
                                        prefs.probhatDeadKeyHaptic = it
                                    }
                                )
                            }
                        }
                    }

                    // Layout Overrides Card
                    LayoutOverrideSection(
                        layout = LekhaniLayoutType.PROBHAT,
                        prefs = prefs,
                        isEnglish = isEnglish
                    )
                }
            }
        }
    }
}

/**
 * Dedicated settings dialog for National (জাতীয় - BBS) layout.
 */
@Composable
fun NationalSettingsDialog(
    prefs: KeyboardPreferences,
    isEnglish: Boolean = false,
    onDismiss: () -> Unit
) {
    var nationalJuktobornoAssist by remember { mutableStateOf(prefs.nationalJuktobornoAssist) }
    var nationalNumeralsBengali by remember { mutableStateOf(prefs.nationalNumeralsBengali) }

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
                            text = if (isEnglish) "National (BBS) Settings" else "জাতীয় (BBS) লেআউট সেটিংস",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = if (isEnglish) "Official BBS National layout" else "সরকারি মানসম্মত ফিক্সড লেআউট",
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
                            // Juktoborno Assist
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = if (isEnglish) "Juktoborno Assist" else "যুক্তবর্ণ সহায়িকা",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                    )
                                    Text(
                                        text = if (isEnglish) "Show conjunct previews and suggestions when typing Hasanta (্)"
                                               else "হসন্ত (্) চাপলে সম্ভাব্য যুক্তবর্ণের সাজেশন দেখাবে",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = nationalJuktobornoAssist,
                                    onCheckedChange = {
                                        nationalJuktobornoAssist = it
                                        prefs.nationalJuktobornoAssist = it
                                    }
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            // Bengali Numbers in National
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = if (isEnglish) "Bengali Numbers (১, ২, ৩)" else "বাংলা সংখ্যা (১, ২, ৩)",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                    )
                                    Text(
                                        text = if (isEnglish) "Type Bengali numbers on the number row"
                                               else "সংখ্যা সারিতে ডিফল্টভাবে বাংলা সংখ্যা লিখবে",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = nationalNumeralsBengali,
                                    onCheckedChange = {
                                        nationalNumeralsBengali = it
                                        prefs.nationalNumeralsBengali = it
                                    }
                                )
                            }
                        }
                    }

                    // Layout Overrides Card
                    LayoutOverrideSection(
                        layout = LekhaniLayoutType.NATIONAL,
                        prefs = prefs,
                        isEnglish = isEnglish
                    )
                }
            }
        }
    }
}

/**
 * Dedicated settings dialog for Gboard Style layout.
 */
@Composable
fun GboardSettingsDialog(
    prefs: KeyboardPreferences,
    isEnglish: Boolean = false,
    onDismiss: () -> Unit
) {
    var gboardAlternatePopups by remember { mutableStateOf(prefs.gboardAlternatePopups) }

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
                            text = if (isEnglish) "Gboard Style Settings" else "জি-বোর্ড স্টাইল সেটিংস",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = if (isEnglish) "Familiar Android Bengali layout" else "অ্যান্ড্রয়েড পরিচিত লেআউট",
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
                            // Alternate Popups
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = if (isEnglish) "Alternate Character Popups" else "পপআপ সহায়িকা বর্ণ",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                    )
                                    Text(
                                        text = if (isEnglish) "Show extended vowel & consonant alternatives on long-press"
                                               else "কী-তে লং-প্রেস করলে সম্পর্কিত বর্ণ ও চিহ্ন দেখাবে",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = gboardAlternatePopups,
                                    onCheckedChange = {
                                        gboardAlternatePopups = it
                                        prefs.gboardAlternatePopups = it
                                    }
                                )
                            }
                        }
                    }

                    // Layout Specific Overrides
                    LayoutOverrideSection(
                        layout = LekhaniLayoutType.GBOARD,
                        prefs = prefs,
                        isEnglish = isEnglish
                    )
                }
            }
        }
    }
}

/**
 * Dedicated settings dialog for English (QWERTY) layout.
 */
@Composable
fun EnglishSettingsDialog(
    prefs: KeyboardPreferences,
    isEnglish: Boolean = false,
    onDismiss: () -> Unit
) {
    var englishAutoCapitalize by remember { mutableStateOf(prefs.englishAutoCapitalize) }
    var englishPredictiveSuggestions by remember { mutableStateOf(prefs.englishPredictiveSuggestions) }

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
                            text = if (isEnglish) "English (QWERTY) Settings" else "ইংরেজি (QWERTY) সেটিংস",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = if (isEnglish) "Alphanumeric & password entry" else "ইংরেজি টাইপিং ও টেক্সট এন্ট্রি",
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
                            // Auto-Capitalization
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = if (isEnglish) "Auto-Capitalization" else "স্বয়ংক্রিয় ক্যাপিটালাইজেশন",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                    )
                                    Text(
                                        text = if (isEnglish) "Capitalize the first letter of each sentence automatically"
                                               else "প্রতিটি বাক্যের প্রথম অক্ষর স্বয়ংক্রিয়ভাবে বড় হাতের করবে",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = englishAutoCapitalize,
                                    onCheckedChange = {
                                        englishAutoCapitalize = it
                                        prefs.englishAutoCapitalize = it
                                    }
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            // Predictive Suggestions
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = if (isEnglish) "Predictive Suggestions" else "শব্দ সাজেশন্স",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                    )
                                    Text(
                                        text = if (isEnglish) "Show next-word predictions and completions while typing English"
                                               else "ইংরেজি লেখার সময় পরবর্তী সম্ভাব্য শব্দ ও সাজেশন দেখাবে",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = englishPredictiveSuggestions,
                                    onCheckedChange = {
                                        englishPredictiveSuggestions = it
                                        prefs.englishPredictiveSuggestions = it
                                    }
                                )
                            }
                        }
                    }

                    // Layout Specific Overrides
                    LayoutOverrideSection(
                        layout = LekhaniLayoutType.ENGLISH,
                        prefs = prefs,
                        isEnglish = isEnglish
                    )
                }
            }
        }
    }
}

