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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lekhani.android.data.dictionary.LekhaniDictionaryManager
import java.io.File

/**
 * DictionaryManagementSheet
 * Material 3 Expressive UI for User Vocabulary & Shortcuts:
 *  - Tab 0: Personal Dictionary (custom words, search, import, export)
 *  - Tab 1: Text Replacements & Custom Auto-Correct Rules (triggers ➔ replacements)
 *  - Tab 2: Learned Typing Memory (frequency stats & history reset)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DictionaryManagementSheet(
    dictManager: LekhaniDictionaryManager,
    isEnglish: Boolean = false,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val acFile = remember { File(context.filesDir, "user_autocorrect.json") }
    val learnedFile = remember { File(context.filesDir, "user_learned.bin") }

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
    val systemRules = remember { dictManager.getSystemAutocorrectRules(context) }
    var shortcutSubTab by remember { mutableIntStateOf(0) } // 0 = My Shortcuts, 1 = Built-in Typo Rules
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
            dictManager.saveLearned(learnedFile.absolutePath)
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

    val filteredSystemRules = remember(searchQuery, systemRules) {
        if (searchQuery.isBlank()) {
            systemRules.toList()
        } else {
            val q = searchQuery.trim().lowercase()
            systemRules.filter { (k, v) ->
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
                ),
                actions = {
                    if (selectedSubTab == 0) {
                        IconButton(
                            onClick = {
                                try {
                                    filePickerLauncher.launch(arrayOf("*/*", "text/plain", "application/json"))
                                } catch (e: Exception) {
                                    showImportDialog = true
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FileDownload,
                                contentDescription = if (isEnglish) "Import words" else "শব্দ আমদানি করুন",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = {
                                val json = dictManager.exportJson()
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_SUBJECT, "Lekhani_Dictionary_Backup.json")
                                    putExtra(Intent.EXTRA_TEXT, json)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, if (isEnglish) "Export Backup" else "ব্যাকআপ এক্সপোর্ট করুন"))
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FileUpload,
                                contentDescription = if (isEnglish) "Export backup" else "ব্যাকআপ এক্সপোর্ট করুন",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (userWords.isNotEmpty()) {
                            IconButton(onClick = { showClearConfirm = true }) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteSweep,
                                    contentDescription = if (isEnglish) "Clear all words" else "সব মুছুন",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    } else if (selectedSubTab == 1) {
                        IconButton(
                            onClick = {
                                val json = dictManager.exportJson()
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_SUBJECT, "Lekhani_Dictionary_Backup.json")
                                    putExtra(Intent.EXTRA_TEXT, json)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, if (isEnglish) "Export Backup" else "ব্যাকআপ এক্সপোর্ট করুন"))
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FileUpload,
                                contentDescription = if (isEnglish) "Export rules" else "নিয়ম এক্সপোর্ট করুন",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { showAddRuleDialog = true }) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = if (isEnglish) "Add rule" else "নিয়ম যোগ করুন",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
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

                    // Add Word Input with integrated action
                    OutlinedTextField(
                        value = newWordInput,
                        onValueChange = { newWordInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(if (isEnglish) "Add custom word..." else "নতুন শব্দ লিখুন...") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            val word = newWordInput.trim()
                            IconButton(
                                onClick = {
                                    if (word.length >= 2) {
                                        dictManager.addUserWord(word)
                                        dictManager.saveLearned(learnedFile.absolutePath)
                                        userWords = dictManager.getUserWords()
                                        newWordInput = ""
                                        Toast.makeText(context, if (isEnglish) "'$word' added" else "'$word' যোগ করা হয়েছে", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = word.length >= 2
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = if (isEnglish) "Add" else "যোগ",
                                    tint = if (word.length >= 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                val word = newWordInput.trim()
                                if (word.length >= 2) {
                                    dictManager.addUserWord(word)
                                    dictManager.saveLearned(learnedFile.absolutePath)
                                    userWords = dictManager.getUserWords()
                                    newWordInput = ""
                                    Toast.makeText(context, if (isEnglish) "'$word' added" else "'$word' যোগ করা হয়েছে", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Adaptive Action Chips (FlowRow guarantees scaling on small screens and large accessibility text)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AssistChip(
                            onClick = {
                                try {
                                    filePickerLauncher.launch(arrayOf("*/*", "text/plain", "application/json"))
                                } catch (e: Exception) {
                                    showImportDialog = true
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.FileDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            label = { Text(if (isEnglish) "Import" else "ইমপোর্ট", fontSize = 12.sp) },
                            shape = RoundedCornerShape(8.dp)
                        )

                        AssistChip(
                            onClick = {
                                val json = dictManager.exportJson()
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_SUBJECT, "Lekhani_Dictionary_Backup.json")
                                    putExtra(Intent.EXTRA_TEXT, json)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, if (isEnglish) "Export Backup" else "ব্যাকআপ এক্সপোর্ট করুন"))
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.FileUpload,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            label = { Text(if (isEnglish) "Export" else "এক্সপোর্ট", fontSize = 12.sp) },
                            shape = RoundedCornerShape(8.dp)
                        )

                        if (userWords.isNotEmpty()) {
                            AssistChip(
                                onClick = { showClearConfirm = true },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.DeleteSweep,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                label = {
                                    Text(
                                        text = if (isEnglish) "Clear" else "মুছুন",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = AssistChipDefaults.assistChipColors(
                                    labelColor = MaterialTheme.colorScheme.error
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Word List
                    if (filteredWords.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )

                                    Text(
                                        text = if (searchQuery.isBlank()) {
                                            if (isEnglish) "No Custom Words" else "ব্যক্তিগত শব্দভাণ্ডার খালি"
                                        } else {
                                            if (isEnglish) "No Words Found" else "শব্দ পাওয়া যায়নি"
                                        },
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Text(
                                        text = if (searchQuery.isBlank()) {
                                            if (isEnglish) "Words you add or import will appear here."
                                            else "আপনার যুক্ত করা বা ইমপোর্ট করা শব্দগুলো এখানে থাকবে।"
                                        } else {
                                            if (isEnglish) "No words matching '$searchQuery'."
                                            else "'$searchQuery' এর সাথে কোনো শব্দ মেলেনি।"
                                        },
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                                dictManager.saveLearned(learnedFile.absolutePath)
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
                    // Filter Chips: My Shortcuts vs Built-in Typo Rules
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = shortcutSubTab == 0,
                            onClick = { shortcutSubTab = 0 },
                            label = {
                                Text(
                                    if (isEnglish) "My Shortcuts (${autocorrectRules.size})"
                                    else "আমার শর্টকাট (${autocorrectRules.size})"
                                )
                            },
                            leadingIcon = if (shortcutSubTab == 0) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                        FilterChip(
                            selected = shortcutSubTab == 1,
                            onClick = { shortcutSubTab = 1 },
                            label = {
                                Text(
                                    if (isEnglish) "Built-in Typo Rules (${systemRules.size})"
                                    else "বিল্ট-ইন সংশোধন (${systemRules.size})"
                                )
                            },
                            leadingIcon = if (shortcutSubTab == 1) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }

                    // Search Bar with integrated Add action for shortcuts
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                if (shortcutSubTab == 0) {
                                    if (isEnglish) "Search shortcuts..." else "শর্টকাট খুঁজুন..."
                                } else {
                                    if (isEnglish) "Search built-in rules..." else "সংশোধন খুঁজুন..."
                                }
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = if (shortcutSubTab == 0) {
                            {
                                IconButton(onClick = { showAddRuleDialog = true }) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = if (isEnglish) "New" else "নতুন",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        } else null,
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (shortcutSubTab == 0) {
                        // User's custom shortcuts
                        if (filteredRules.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Transform,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Text(
                                            text = if (isEnglish) "No Custom Shortcuts" else "কোনো শর্টকাট নেই",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = if (isEnglish)
                                                "Create shortcuts like 'omw' ➔ 'On my way!' or 'dh' ➔ 'ধন্যবাদ'."
                                            else
                                                "স্বয়ংক্রিয় প্রতিস্থাপনের জন্য শর্টকাট তৈরি করুন (যেমন: 'dh' ➔ 'ধন্যবাদ')।",
                                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                        Button(
                                            onClick = { showAddRuleDialog = true },
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(if (isEnglish) "Add Shortcut" else "শর্টকাট যোগ করুন")
                                        }
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
                                                    Toast.makeText(context, if (isEnglish) "Shortcut removed" else "শর্টকাটটি মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
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
                    } else {
                        // Built-in Typo Rules (read-only)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isEnglish)
                                        "${systemRules.size} built-in phonetic corrections run automatically in the background."
                                    else
                                        "${systemRules.size} টি বিল্ট-ইন ফোনেটিক বানান সংশোধন ব্যাকগ্রাউন্ডে সক্রিয় থাকে।",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (filteredSystemRules.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isEnglish) "No matching built-in rules" else "কোনো মিল পাওয়া যায়নি",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(filteredSystemRules, key = { it.first }) { (typo, correction) ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = typo,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                                contentDescription = null,
                                                modifier = Modifier.size(12.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = correction,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                                color = MaterialTheme.colorScheme.primary
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
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = if (isEnglish) "Autonomous Typing Memory" else "অন-ডিভাইস টাইপিং মেমোরি",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = if (isEnglish) "$learnedWordsCount auto-learned words" else "$learnedWordsCount টি নিজে থেকে শেখা শব্দ",
                                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    )
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            Text(
                                text = if (isEnglish)
                                    "Lekhani adapts to your typing on-device. Frequently typed words are remembered to improve candidate suggestions. Custom words and shortcuts are kept safe."
                                else
                                    "লেখনি সম্পূর্ণ অফলাইনে আপনার লেখার ধরন অনুযায়ী শেখে। ঘনঘন টাইপ করা শব্দগুলো পরামর্শ তালিকায় এগিয়ে থাকে। রিসেট করলেও কাস্টম শব্দ ও শর্টকাট অক্ষত থাকবে।",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Button(
                                onClick = { showClearLearnedConfirm = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (isEnglish) "Reset Typing History" else "টাইপিং হিস্টোরি রিসেট করুন")
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
            title = { Text(if (isEnglish) "Add Shortcut" else "শর্টকাট যোগ করুন") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (isEnglish)
                            "Enter shortcut and replacement text:"
                        else
                            "শর্টকাট ও প্রতিস্থাপক শব্দ লিখুন:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newTriggerInput,
                        onValueChange = { newTriggerInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (isEnglish) "Shortcut (e.g. omw)" else "শর্টকাট (যেমন: omw, dh)") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                    OutlinedTextField(
                        value = newReplacementInput,
                        onValueChange = { newReplacementInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (isEnglish) "Replacement" else "প্রতিস্থাপক শব্দ/বাক্য") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                val trig = newTriggerInput.trim()
                                val repl = newReplacementInput.trim()
                                if (trig.isNotEmpty() && repl.isNotEmpty()) {
                                    dictManager.addAutocorrectRule(trig, repl)
                                    dictManager.saveAutocorrect(acFile.absolutePath)
                                    autocorrectRules = dictManager.getAutocorrectRules()
                                    showAddRuleDialog = false
                                    newTriggerInput = ""
                                    newReplacementInput = ""
                                    Toast.makeText(context, if (isEnglish) "Shortcut added: $trig ➔ $repl" else "শর্টকাট যোগ হয়েছে: $trig ➔ $repl", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
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
                            Toast.makeText(context, if (isEnglish) "Shortcut added: $trig ➔ $repl" else "শর্টকাট যোগ হয়েছে: $trig ➔ $repl", Toast.LENGTH_SHORT).show()
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
            title = { Text(if (isEnglish) "Import Dictionary" else "শব্দ তালিকা ইমপোর্ট") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        if (isEnglish)
                            "Paste word list below (one word per line):"
                        else
                            "শব্দ তালিকা বা ব্যাকআপ পেস্ট করুন (প্রতি লাইনে একটি শব্দ):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rawImportText,
                        onValueChange = { rawImportText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        placeholder = { Text(if (isEnglish) "word1\nword2..." else "শব্দ১\nশব্দ২...") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val count = dictManager.importRawWordList(rawImportText)
                        dictManager.saveLearned(learnedFile.absolutePath)
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
                        dictManager.saveLearned(learnedFile.absolutePath)
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
                        dictManager.saveLearned(learnedFile.absolutePath)
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
