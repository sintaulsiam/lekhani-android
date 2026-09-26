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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
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

/**
 * DictionaryManagementSheet
 * ══════════════════════════════════════════════════════════════════════════════
 * Material 3 Expressive UI for User Dictionary Management (Phase 9):
 *  - Personal word search, add, delete
 *  - One-click migration from Ridmik Keyboard & Avro (.txt / CSV / JSON)
 *  - Offline JSON backup export & share
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryManagementSheet(
    dictManager: LekhaniDictionaryManager,
    isEnglish: Boolean = false,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var newWordInput by remember { mutableStateOf("") }
    var userWords by remember { mutableStateOf(dictManager.getUserWords()) }
    var showImportDialog by remember { mutableStateOf(false) }
    var rawImportText by remember { mutableStateOf("") }
    var showClearConfirm by remember { mutableStateOf(false) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isEnglish) "Personal Dictionary" else "ব্যক্তিগত শব্দভাণ্ডার",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = if (isEnglish) "Personal Dictionary • ${userWords.size} words" else "ব্যক্তিগত শব্দভাণ্ডার • ${userWords.size} টি শব্দ",
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
            Spacer(modifier = Modifier.height(8.dp))

            // ── Search & Add Row ────────────────────────────────────────────────
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(if (isEnglish) "Search words..." else "শব্দ খুঁজুন (Search words)...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

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

            Spacer(modifier = Modifier.height(14.dp))

            // ── Action Buttons Row (Import / Export / Clear) ────────────────────
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

            Spacer(modifier = Modifier.height(14.dp))

            // ── Word List ───────────────────────────────────────────────────────
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
                                    imageVector = Icons.Filled.MenuBook,
                                    contentDescription = "Dictionary",
                                    tint = Color(0xFF00E5B8),
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Text(
                                text = if (searchQuery.isBlank()) {
                                    if (isEnglish) "Personal Dictionary is Empty" else "ব্যক্তিগত শব্দভাণ্ডার খালি"
                                } else {
                                    if (isEnglish) "No Words Found" else "শব্দ পাওয়া যায়নি"
                                },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = if (searchQuery.isBlank()) {
                                    if (isEnglish)
                                        "As you type, new custom words will be automatically saved locally, or you can add them above."
                                    else
                                        "টাইপ করার সাথে সাথে নতুন শব্দগুলো লোকাল স্টোরেজে স্বয়ংক্রিয়ভাবে সংরক্ষিত হবে অথবা ওপরের ইনপুট বক্সে লিখে যোগ করুন।"
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
                        "All custom words and automatically learned dictionary entries will be deleted. This cannot be undone."
                    else
                        "আপনার তৈরি বা স্বয়ংক্রিয়ভাবে শেখা সমস্ত ব্যক্তিগত শব্দভাণ্ডার মুছে ফেলা হবে। এটি ফেরানো সম্ভব নয়।"
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
}
