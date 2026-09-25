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
                            text = "ব্যক্তিগত শব্দভাণ্ডার",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Personal Dictionary • ${userWords.size} টি শব্দ",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
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
                placeholder = { Text("শব্দ খুঁজুন (Search words)...") },
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
                    placeholder = { Text("নতুন শব্দ লিখুন...") },
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
                            Toast.makeText(context, "'$word' যোগ করা হয়েছে", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A87E))
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("যোগ")
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
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("📥 ইমপোর্ট", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = {
                        val json = dictManager.exportJson()
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_SUBJECT, "Lekhani_Dictionary_Backup.json")
                            putExtra(Intent.EXTRA_TEXT, json)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "ব্যাকআপ এক্সপোর্ট করুন"))
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("📤 এক্সপোর্ট", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = { showClearConfirm = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("🗑️ মুছুন", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── Word List ───────────────────────────────────────────────────────
            if (filteredWords.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isBlank()) "কোনো শব্দ যুক্ত করা হয়নি" else "শব্দ পাওয়া যায়নি",
                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
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
                                        Toast.makeText(context, "'$word' মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
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
    }

    // ── Import Dialog (Paste raw text or Ridmik/Avro format) ─────────────
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("অভিধান ইমপোর্ট (Import Dictionary)") },
            text = {
                Column {
                    Text(
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
                        placeholder = { Text("শব্দ১\nশব্দ২\nami=আমি...") }
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
                        Toast.makeText(context, "$count টি শব্দ সফলভাবে যুক্ত হয়েছে", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("ইমপোর্ট করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("বাতিল")
                }
            }
        )
    }

    // ── Clear Confirmation Dialog ───────────────────────────────────────
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("সব ব্যক্তিগত শব্দ মুছবেন?") },
            text = {
                Text("আপনার তৈরি বা স্বয়ংক্রিয়ভাবে শেখা সমস্ত ব্যক্তিগত শব্দভাণ্ডার মুছে ফেলা হবে। এটি ফেরানো সম্ভব নয়।")
            },
            confirmButton = {
                Button(
                    onClick = {
                        dictManager.clearDictionary()
                        userWords = dictManager.getUserWords()
                        showClearConfirm = false
                        Toast.makeText(context, "শব্দভাণ্ডার মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("হ্যাঁ, সব মুছুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("বাতিল")
                }
            }
        )
    }
}
