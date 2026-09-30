package com.lekhani.android.ui.dictionary

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lekhani.android.data.dictionary.LekhaniDictionaryManager
import java.io.File

/**
 * DictionaryManagementSheet
 * ══════════════════════════════════════════════════════════════════════════════
 * Material 3 Expressive UI for User Vocabulary & Shortcuts:
 *  - Tab 0: Personal Dictionary (custom words, search, import, export)
 *  - Tab 1: Text Replacements & Custom Auto-Correct Rules (triggers ➔ replacements)
 *  - Tab 2: Learned Typing Memory (frequency stats & history reset)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryManagementSheet(
    dictManager: LekhaniDictionaryManager,
    isEnglish: Boolean = false,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val acFile = remember { File(context.filesDir, "user_autocorrect.json") }

    var selectedSubTab by remember { mutableIntStateOf(0) }

    // Tab 0: Words
    var searchQuery by remember { mutableStateOf("") }
    var newWordInput by remember { mutableStateOf("") }
    var userWords by remember { mutableStateOf(dictManager.getUserWords()) }
    var showImportDialog by remember { mutableStateOf(false) }
    var rawImportText by remember { mutableStateOf("") }
    var showClearConfirm by remember { mutableStateOf(false) }

    // Tab 1: Autocorrect / Shortcuts
    var autocorrectRules by remember { mutableStateOf(dictManager.getAutocorrectRules()) }
    var showAddRuleDialog by remember { mutableStateOf(false) }
    var newTriggerInput by remember { mutableStateOf("") }
    var newReplacementInput by remember { mutableStateOf("") }

    // Tab 2: Learned Memory
    var learnedWordsCount by remember { mutableIntStateOf(dictManager.getLearnedWordsCount()) }
    var showClearLearnedConfirm by remember { mutableStateOf(false) }

    // File picker launcher for JSON/Text import
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val (count, message) = dictManager.importFromUri(context, uri)
            userWords = dictManager.getUserWords()
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val filteredWords = remember(searchQuery, userWords) {
        if (searchQuery.isBlank()) {
            userWords
        } else {
            userWords.filter { it.contains(searchQuery.trim(), ignoreCase = true) }
        }
    }

    val filteredRules = remember(searchQuery, autocorrectRules) {
        if (searchQuery.isBlank()) {
            autocorrectRules.toList()
        } else {
            val q = searchQuery.trim().lowercase()
            autocorrectRules.filter { (k, v) ->
                k.lowercase().contains(q) || v.lowercase().contains(q)
            }.toList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isEnglish) "Vocabulary & Shortcuts" else "শব্দকোষ ও শর্টকাট",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = if (isEnglish) "${userWords.size} custom words • ${autocorrectRules.size} rules" else "${userWords.size} টি কাস্টম শব্দ • ${autocorrectRules.size} টি নিয়ম",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = if (isEnglish) "Close" else "বন্ধ করুন")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // ── Primary Category Tabs ──────────────────────────────────────────
            SecondaryTabRow(
                selectedTabIndex = selectedSubTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedSubTab == 0,
                    onClick = { selectedSubTab = 0 },
                    text = { Text(if (isEnglish) "Custom Words" else "কাস্টম শব্দ") },
                    icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedSubTab == 1,
                    onClick = { selectedSubTab = 1 },
                    text = { Text(if (isEnglish) "Shortcuts" else "শর্টকাট নিয়ম") },
                    icon = { Icon(Icons.Default.Transform, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedSubTab == 2,
                    onClick = {
                        learnedWordsCount = dictManager.getLearnedWordsCount()
                        selectedSubTab = 2
                    },
                    text = { Text(if (isEnglish) "Learned" else "টাইপিং মেমোরি") },
                    icon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            when (selectedSubTab) {
                // ── TAB 0: Custom Personal Words ────────────────────────────────
                0 -> {
                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(if (isEnglish) "Search custom words..." else "কাস্টম শব্দ খুঁজুন...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Add Word Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newWordInput,
                            onValueChange = { newWordInput = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(if (isEnglish) "Add new word..." else "নতুন শব্দ লিখুন...") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val word = newWordInput.trim()
                                if (word.length >= 2) {
                                    dictManager.addUserWord(word)
                                    userWords = dictManager.getUserWords()
                                    newWordInput = ""
                                    Toast.makeText(context, if (isEnglish) "'$word' added" else "'$word' যোগ করা হয়েছে", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A87E))
                        ) {
                            Icon(Icons.Default.Add, contentDescription = if (isEnglish) "Add" else "যোগ")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isEnglish) "Add" else "যোগ")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Action Buttons Row (Import / Export / Clear)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                try {
                                    filePickerLauncher.launch(arrayOf("*/*", "text/plain", "application/json"))
                                } catch (e: Exception) {
                                    showImportDialog = true
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FileDownload,
                                contentDescription = "Import",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isEnglish) "Import" else "ইমপোর্ট", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                val json = dictManager.exportJson()
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_SUBJECT, "Lekhani_Dictionary_Backup.json")
                                    putExtra(Intent.EXTRA_TEXT, json)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, if (isEnglish) "Export Backup" else "ব্যাকআপ এক্সপোর্ট করুন"))
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FileUpload,
                                contentDescription = "Export",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isEnglish) "Export" else "এক্সপোর্ট", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { showClearConfirm = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteSweep,
                                contentDescription = "Clear",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isEnglish) "Clear" else "মুছুন", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Word List
                    if (filteredWords.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color(0xFF00E5B8).copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                            contentDescription = "Dictionary",
                                            tint = Color(0xFF00E5B8),
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }

                                    Text(
                                        text = if (searchQuery.isBlank()) {
                                            if (isEnglish) "Personal Dictionary is Clean" else "ব্যক্তিগত শব্দভাণ্ডার খালি"
                                        } else {
                                            if (isEnglish) "No Words Found" else "শব্দ পাওয়া যায়নি"
                                        },
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Text(
                                        text = if (searchQuery.isBlank()) {
                                            if (isEnglish)
                                                "Only words you explicitly add or import will appear here. No garbage or accidental typos."
                                            else
                                                "এখানে কেবল আপনার সরাসরি যোগ করা বা ইমপোর্ট করা শব্দ সংরক্ষিত থাকবে। কোনো অনাকাঙ্ক্ষিত টাইপো জমা হবে না।"
                                        } else {
                                            if (isEnglish)
                                                "No words matching '$searchQuery'. You can add it as a new word above."
                                            else
                                                "'$searchQuery' এর সাথে মিল থাকা কোনো শব্দ পাওয়া যায়নি। নতুন শব্দ হিসেবে যোগ করতে পারেন।"
                                        },
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 18.sp
                                        ),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredWords, key = { it }) { word ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = word,
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                                        )
                                        IconButton(
                                            onClick = {
                                                dictManager.deleteUserWord(word)
                                                userWords = dictManager.getUserWords()
                                                Toast.makeText(context, if (isEnglish) "'$word' deleted" else "'$word' মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = if (isEnglish) "Delete" else "মুছুন",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── TAB 1: Text Replacements & Auto-Correct Rules ───────────────
                1 -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(if (isEnglish) "Search rules..." else "শর্টকাট বা নিয়ম খুঁজুন...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { showAddRuleDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isEnglish) "New" else "নতুন")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (filteredRules.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Transform,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Text(
                                        text = if (isEnglish) "No Auto-Correct Rules" else "কোনো শর্টকাট নিয়ম নেই",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = if (isEnglish)
                                            "Add custom text replacements like 'omw' ➔ 'On my way!' or 'কিবর্ড' ➔ 'কীবোর্ড'. They will auto-expand on spacebar."
                                        else
                                            "যেকোনো টেক্সট শর্টকাট যোগ করুন, যেমন 'omw' ➔ 'On my way!' বা 'কিবর্ড' ➔ 'কীবোর্ড'। স্পেস চাপলে এগুলো স্বয়ংক্রিয়ভাবে পরিবর্তিত হবে।",
                                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredRules, key = { it.first }) { (trigger, replacement) ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = trigger,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = replacement,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                dictManager.deleteAutocorrectRule(trigger)
                                                dictManager.saveAutocorrect(acFile.absolutePath)
                                                autocorrectRules = dictManager.getAutocorrectRules()
                                                Toast.makeText(context, if (isEnglish) "Rule removed" else "নিয়মটি মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── TAB 2: Learned Typing Memory ────────────────────────────────
                2 -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = if (isEnglish) "Autonomous Typing Memory" else "অন-ডিভাইস টাইপিং মেমোরি",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = if (isEnglish) "$learnedWordsCount auto-learned words" else "$learnedWordsCount টি স্বয়ংক্রিয়ভাবে শেখা শব্দ",
                                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    )
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            Text(
                                text = if (isEnglish)
                                    "Lekhani continuously personalizes predictions on-device without network access. Words are only learned after repeated typing (3+ occurrences) to prevent typos. You can safely clear this cache anytime without losing your custom words or shortcuts."
                                else
                                    "লেখনি আপনার লেখার ধরন অনুযায়ী সম্পূর্ণ অন-ডিভাইসে নিজে থেকে শেখে। টাইপো প্রতিরোধে যেকোনো নতুন শব্দ অন্তত ৩ বার টাইপ করার পর মেমোরিতে যুক্ত হয়। কাস্টম শব্দ না হারিয়েই আপনি যেকোনো সময় এই হিস্টোরি রিসেট করতে পারেন।",
                                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Button(
                                onClick = { showClearLearnedConfirm = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (isEnglish) "Reset Typing History / Clear Learned Words" else "টাইপিং হিস্টোরি রিসেট করুন")
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Add Autocorrect Rule Dialog ─────────────────────────────────────
    if (showAddRuleDialog) {
        AlertDialog(
            onDismissRequest = { showAddRuleDialog = false },
            title = { Text(if (isEnglish) "Add Auto-Correct Shortcut" else "শর্টকাট বা সংশোধন যোগ করুন") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isEnglish)
                            "Enter the shortcut or common typo, and the text it should expand to:"
                        else
                            "শর্টকাট বা প্রায়শই হওয়া ভুল শব্দটি লিখুন, এবং স্পেস চাপলে যে সঠিক লেখাটি বসবে:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newTriggerInput,
                        onValueChange = { newTriggerInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (isEnglish) "Shortcut / Typo (e.g. omw, কিবর্ড)" else "শর্টকাট বা ভুল শব্দ (যেমন: omw, কিবর্ড)") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = newReplacementInput,
                        onValueChange = { newReplacementInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (isEnglish) "Replacement (e.g. On my way!, কীবোর্ড)" else "প্রতিস্থাপক শব্দ/বাক্য (যেমন: On my way!, কীবোর্ড)") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trig = newTriggerInput.trim()
                        val repl = newReplacementInput.trim()
                        if (trig.isNotEmpty() && repl.isNotEmpty()) {
                            dictManager.addAutocorrectRule(trig, repl)
                            dictManager.saveAutocorrect(acFile.absolutePath)
                            autocorrectRules = dictManager.getAutocorrectRules()
                            showAddRuleDialog = false
                            newTriggerInput = ""
                            newReplacementInput = ""
                            Toast.makeText(context, if (isEnglish) "Rule added: $trig ➔ $repl" else "নিয়ম যোগ হয়েছে: $trig ➔ $repl", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text(if (isEnglish) "Save" else "সংরক্ষণ করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddRuleDialog = false }) {
                    Text(if (isEnglish) "Cancel" else "বাতিল")
                }
            }
        )
    }

    // ── Import Dialog (Paste raw text or Ridmik/Avro format) ─────────────
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text(if (isEnglish) "Import Dictionary" else "অভিধান ইমপোর্ট (Import Dictionary)") },
            text = {
                Column {
                    Text(
                        if (isEnglish)
                            "Paste Ridmik backup, Avro user dictionary, or word list below (one word per line):"
                        else
                            "রিদ্মিক কীবোর্ড ব্যাকআপ, অভ্র ইউজার ডিকশনারি, বা সাধারণ শব্দ তালিকা নিচে পেস্ট করুন (প্রতি লাইনে একটি শব্দ):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rawImportText,
                        onValueChange = { rawImportText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        placeholder = { Text(if (isEnglish) "word1\nword2\nami=আমি..." else "শব্দ১\nশব্দ২\nami=আমি...") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val count = dictManager.importRawWordList(rawImportText)
                        userWords = dictManager.getUserWords()
                        showImportDialog = false
                        rawImportText = ""
                        Toast.makeText(context, if (isEnglish) "$count words successfully imported" else "$count টি শব্দ সফলভাবে যুক্ত হয়েছে", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(if (isEnglish) "Import" else "ইমপোর্ট করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text(if (isEnglish) "Cancel" else "বাতিল")
                }
            }
        )
    }

    // ── Clear Confirmation Dialog ───────────────────────────────────────
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(if (isEnglish) "Clear All Personal Words?" else "সব ব্যক্তিগত শব্দ মুছবেন?") },
            text = {
                Text(
                    if (isEnglish)
                        "All custom words in your personal dictionary will be deleted. This cannot be undone."
                    else
                        "আপনার তৈরি সমস্ত কাস্টম শব্দভাণ্ডার মুছে ফেলা হবে। এটি ফেরানো সম্ভব নয়।"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        dictManager.clearDictionary()
                        userWords = dictManager.getUserWords()
                        showClearConfirm = false
                        Toast.makeText(context, if (isEnglish) "Dictionary cleared" else "শব্দভাণ্ডার মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (isEnglish) "Yes, Delete All" else "হ্যাঁ, সব মুছুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(if (isEnglish) "Cancel" else "বাতিল")
                }
            }
        )
    }

    // ── Clear Learned Typing Memory Dialog ──────────────────────────────
    if (showClearLearnedConfirm) {
        AlertDialog(
            onDismissRequest = { showClearLearnedConfirm = false },
            title = { Text(if (isEnglish) "Reset Typing History?" else "টাইপিং হিস্টোরি রিসেট করবেন?") },
            text = {
                Text(
                    if (isEnglish)
                        "All automatically learned vocabulary and candidate frequencies will be reset. Your manually added custom words and shortcuts will NOT be deleted."
                    else
                        "টাইপিং থেকে নিজে থেকে শেখা শব্দ ও ফ্রিকোয়েন্সি হিস্টোরি রিসেট করা হবে। আপনার সরাসরি যুক্ত করা কাস্টম শব্দ বা শর্টকাট মুছবে না।"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        dictManager.clearLearnedHistory()
                        learnedWordsCount = dictManager.getLearnedWordsCount()
                        showClearLearnedConfirm = false
                        Toast.makeText(context, if (isEnglish) "Typing history reset" else "টাইপিং হিস্টোরি রিসেট করা হয়েছে", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (isEnglish) "Reset History" else "রিসেট করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearLearnedConfirm = false }) {
                    Text(if (isEnglish) "Cancel" else "বাতিল")
                }
            }
        )
    }
}
