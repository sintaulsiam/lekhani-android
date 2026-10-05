package com.lekhani.android.ui.avro

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * AvroCheatSheetDialog
 *
 * Comprehensive interactive reference guide for typing complex formal words,
 * Sanskrit Tatsama conjuncts, and special modifiers in Avro Phonetic.
 */
@Composable
fun AvroCheatSheetDialog(
    isEnglish: Boolean = false,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

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
                // Header
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isEnglish) "Avro Phonetic Guide" else "অভ্র ফোনেটিক নির্দেশিকা",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = if (isEnglish) "How to type complex words & conjuncts" else "কঠিন যুক্তবর্ণ ও শব্দ লেখার নিয়ম",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = if (isEnglish) "Close" else "বন্ধ করুন"
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(if (isEnglish) "Smart Words" else "সহজ টাইপিং") },
                        icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(if (isEnglish) "Conjuncts" else "যুক্তবর্ণ") },
                        icon = { Icon(Icons.Filled.Extension, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text(if (isEnglish) "Modifiers" else "চিহ্ন ও প্রতীক") },
                        icon = { Icon(Icons.Filled.Code, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (selectedTab) {
                        0 -> SmartTypingTab(isEnglish)
                        1 -> ConjunctsTab(isEnglish)
                        2 -> ModifiersTab(isEnglish)
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun SmartTypingTab(isEnglish: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Lightbulb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = if (isEnglish) "Lekhani Smart Suggestion" else "লেখনী স্মার্ট সাজেশন",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = if (isEnglish) {
                        "You don't need complex capital letters! Type naturally based on how the word sounds, and Lekhani will suggest the correct formal spelling."
                    } else {
                        "জটিল বড় হাতের অক্ষরের প্রয়োজন নেই! সাধারণ উচ্চারণে লিখলেই লেখনী স্বয়ংক্রিয়ভাবে শুদ্ধ রূপ সাজেশনে নিয়ে আসবে।"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }

    val smartWords = listOf(
        Triple("biggan", "বিজ্ঞান", "Science"),
        Triple("gyan", "জ্ঞান", "Knowledge"),
        Triple("ucchash", "উচ্ছ্বাস", "Emotion / Outburst"),
        Triple("santona", "সান্ত্বনা", "Consolation"),
        Triple("dondo", "দ্বন্দ্ব", "Conflict / Duality"),
        Triple("kingkortobbobimur", "কিংকর্তব্যবিমূঢ়", "Bewildered / Nonplussed"),
        Triple("shartho", "স্বার্থ", "Self-interest"),
        Triple("shadhinota", "স্বাধীনতা", "Independence"),
        Triple("shottadhikari", "স্বত্বাধিকারী", "Proprietor"),
        Triple("swayottoshashon", "স্বায়ত্তশাসন", "Self-governance / Autonomy"),
        Triple("ontohsotta", "অন্তঃসত্ত্বা", "Pregnant"),
        Triple("ujjol / ujjwol", "উজ্জ্বল", "Bright"),
        Triple("daridro", "দারিদ্র্য", "Poverty"),
        Triple("oporahno", "অপরাহ্ণ", "Afternoon"),
        Triple("sayanno", "সায়াহ্ন", "Dusk / Twilight"),
        Triple("brahmon", "ব্রাহ্মণ", "Brahmin"),
        Triple("biddan", "বিদ্বান", "Scholar"),
        Triple("dwip", "দ্বীপ", "Island"),
        Triple("ahban", "আহ্বান", "Call / Invitation"),
        Triple("jihba", "জিহ্বা", "Tongue"),
        Triple("hridoy", "হৃদয়", "Heart"),
        Triple("krittim", "কৃত্রিম", "Artificial"),
        Triple("durniti", "দুর্নীতি", "Corruption"),
        Triple("sushoma", "সুষমা", "Grace / Beauty"),
        Triple("chikitshok", "চিকিৎসক", "Physician"),
        Triple("shreshtho", "শ্রেষ্ঠ", "Foremost / Best"),
        Triple("ashchorjo", "আশ্চর্য", "Wonder / Astonishing"),
        Triple("punorujjibito", "পুনরুজ্জীবিত", "Revived / Resuscitated"),
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        smartWords.forEach { (casual, formal, meaning) ->
            CheatSheetRow(
                keystroke = casual,
                output = formal,
                note = if (isEnglish) meaning else null
            )
        }
    }
}

@Composable
private fun ConjunctsTab(isEnglish: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = if (isEnglish) "Classical Avro Conjunct Rules" else "ধ্রুপদী অভ্র যুক্তবর্ণের নিয়ম",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = if (isEnglish) {
                    "Exact key combinations for pure phonetic conversion using letter cases."
                } else {
                    "অক্ষরের ক্যাপিটালাইজেশন ব্যবহার করে নির্দিষ্ট যুক্তবর্ণ তৈরির নিয়ম।"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    val conjunctRules = listOf(
        Triple("jNGan", "জ্ঞান", "জ + ঞ (j + Shift+N + Shift+G)"),
        Triple("kShoma", "ক্ষমা", "ক + ষ (k + Shift+S + h)"),
        Triple("oNgko", "অঙ্ক", "ঙ + ক (Shift+N + g + k)"),
        Triple("poNGco", "পঞ্চ", "ঞ + চ (Shift+N + Shift+G + c)"),
        Triple("baNGcha", "বাঞ্ছা", "ঞ + ছ (Shift+N + Shift+G + c + h)"),
        Triple("goNGj", "গঞ্জ", "ঞ + জ (Shift+N + Shift+G + j)"),
        Triple("oporahNo", "অপরাহ্ণ", "হ + ণ (h + Shift+N)"),
        Triple("sayahno", "সায়াহ্ন", "হ + ন (h + n)"),
        Triple("brahmoN", "ব্রাহ্মণ", "হ + ম (h + m)"),
        Triple("ahwan", "আহ্বান", "হ + ব-ফলা (h + w)"),
        Triple("jihwa", "জিহ্বা", "হ + ব-ফলা (h + w)"),
        Triple("ahlad", "আহ্লাদ", "হ + ল (h + l)"),
        Triple("ucChwas", "উচ্ছ্বাস", "চ + ছ + ব (c + Shift+C + h + w)"),
        Triple("santwona", "সান্ত্বনা", "ন + ত + ব (n + t + w)"),
        Triple("dwondwo", "দ্বন্দ্ব", "দ + ব ও ন + দ + ব (d + w + n + d + w)"),
        Triple("bZbohar", "ব্যবহার", "য-ফলা (Shift+Z)"),
        Triple("sotZ", "সত্য", "য-ফলা (Shift+Z)"),
        Triple("shreShTho", "শ্রেষ্ঠ", "ষ + ঠ (Shift+S + h + Shift+T + h)"),
        Triple("drriShTi", "দৃষ্টি", "ষ + ট (Shift+S + h + Shift+T)"),
        Triple("krriShNo", "কৃষ্ণ", "ষ + ণ (Shift+S + h + Shift+N)"),
        Triple("tIkShNo", "তীক্ষ্ণ", "ক্ষ + ণ (k + Shift+S + h + Shift+N)"),
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        conjunctRules.forEach { (keystroke, output, desc) ->
            CheatSheetRow(
                keystroke = keystroke,
                output = output,
                note = desc
            )
        }
    }
}

@Composable
private fun ModifiersTab(isEnglish: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = if (isEnglish) "Special Signs & Modifiers" else "বিশেষ চিহ্ন ও বিভাজক",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = if (isEnglish) {
                    "Special symbols, Reph, Kar, and join-breaker rules."
                } else {
                    "রেফ, কার, হসন্ত এবং যুক্তবর্ণ বিভাজকের ব্যবহার।"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    val modifierRules = listOf(
        Triple("ca^d", "চাঁদ", if (isEnglish) "Chandrabindu (^)" else "চন্দ্রবিন্দু (^)"),
        Triple("ha^s", "হাঁস", if (isEnglish) "Chandrabindu (^)" else "চন্দ্রবিন্দু (^)"),
        Triple("du:kho", "দুঃখ", if (isEnglish) "Visarga (:)" else "বিসর্গ (:)"),
        Triple("hoThat``", "হঠাৎ", if (isEnglish) "Khanda-Ta (t``)" else "খণ্ড-ত (t``)"),
        Triple("ut``sob", "উৎসব", if (isEnglish) "Khanda-Ta (t``)" else "খণ্ড-ত (t``)"),
        Triple("korrmo", "কর্ম", if (isEnglish) "Reph (rr)" else "রেফ (rr)"),
        Triple("sUrrz", "সূর্য", if (isEnglish) "Reph (rr) + Ja (z)" else "রেফ (rr) + য (z)"),
        Triple("krriShi", "কৃষি", if (isEnglish) "Ri-kar (rri)" else "ঋ-কার (rri)"),
        Triple("hrridoy", "হৃদয়", if (isEnglish) "Ri-kar (rri)" else "ঋ-কার (rri)"),
        Triple("d`h", "দহ", if (isEnglish) "Join breaker (`)" else "যুক্তবর্ণ বিভাজক (`)"),
        Triple("k``", "ক্", if (isEnglish) "Explicit Hasanta (`)" else "স্পষ্ট হসন্ত (`)"),
        Triple("$100", "৳১০০", if (isEnglish) "Taka sign ($)" else "টাকা প্রতীক ($)"),
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        modifierRules.forEach { (keystroke, output, desc) ->
            CheatSheetRow(
                keystroke = keystroke,
                output = output,
                note = desc
            )
        }
    }
}

@Composable
private fun CheatSheetRow(
    keystroke: String,
    output: String,
    note: String? = null
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = keystroke,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
                if (note != null) {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = output,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
    }
}
