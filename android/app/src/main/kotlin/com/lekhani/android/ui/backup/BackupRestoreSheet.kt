package com.lekhani.android.ui.backup

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lekhani.android.data.backup.LekhaniBackupManager
import com.lekhani.android.data.dictionary.LekhaniDictionaryManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * BackupRestoreSheet
 *
 * Professional Material 3 bottom-sheet / full-screen dialog for Lekhani Keyboard's
 * 100% offline, zero-telemetry backup and restore system.
 * Allows users to export and import:
 * - Learned vocabulary & N-gram typing memory
 * - Custom autocorrect shortcuts
 * - Keyboard preferences and layout configurations
 * - Pinned clipboard items
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreSheet(
    dictManager: LekhaniDictionaryManager,
    isEnglish: Boolean = false,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    // Export Toggles
    var exportLearned by remember { mutableStateOf(true) }
    var exportShortcuts by remember { mutableStateOf(true) }
    var exportPreferences by remember { mutableStateOf(true) }
    var exportClipboard by remember { mutableStateOf(true) }

    // Live counts
    val userWordsCount = remember { dictManager.getUserWords().size }
    val learnedCount = remember { dictManager.getLearnedWordsCount() }
    val shortcutsCount = remember { dictManager.getAutocorrectRules().size }

    // Restore State
    var rawImportJson by remember { mutableStateOf<String?>(null) }
    var parsedSummary by remember { mutableStateOf<LekhaniBackupManager.BackupSummary?>(null) }
    var parseError by remember { mutableStateOf<String?>(null) }
    var restoreSuccessSummary by remember { mutableStateOf<LekhaniBackupManager.BackupSummary?>(null) }

    // Restore Selection Toggles
    var restoreVocabulary by remember { mutableStateOf(true) }
    var restoreShortcuts by remember { mutableStateOf(true) }
    var restorePreferences by remember { mutableStateOf(true) }
    var restoreClipboard by remember { mutableStateOf(true) }

    // File Picker for Export (CreateDocument)
    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val json = LekhaniBackupManager.createBackupJson(
                context = context,
                dictManager = dictManager,
                includeLearned = exportLearned,
                includeShortcuts = exportShortcuts,
                includePreferences = exportPreferences,
                includeClipboard = exportClipboard,
            )
            val success = LekhaniBackupManager.writeBackupToUri(context, uri, json)
            if (success) {
                Toast.makeText(
                    context,
                    if (isEnglish) "Backup saved successfully" else "ব্যাকআপ সফলভাবে সংরক্ষণ করা হয়েছে",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    context,
                    if (isEnglish) "Failed to save backup file" else "ব্যাকআপ ফাইল সংরক্ষণ করা যায়নি",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // File Picker for Restore (OpenDocument)
    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val content = LekhaniBackupManager.readBackupFromUri(context, uri)
            if (content != null) {
                val summary = LekhaniBackupManager.inspectBackup(content)
                if (summary != null) {
                    rawImportJson = content
                    parsedSummary = summary
                    parseError = null
                    restoreSuccessSummary = null
                } else {
                    parseError = if (isEnglish) {
                        "Invalid backup file format. Please ensure the file was generated by Lekhani Keyboard."
                    } else {
                        "অকার্যকর ব্যাকআপ ফাইল। ফাইলটি লেখনী কীবোর্ড দ্বারা তৈরি কিনা তা নিশ্চিত করুন।"
                    }
                    parsedSummary = null
                    rawImportJson = null
                }
            } else {
                parseError = if (isEnglish) "Unable to read selected file." else "নির্বাচিত ফাইলটি পড়া যায়নি।"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isEnglish) "Backup & Restore" else "ব্যাকআপ ও রিস্টোর",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = if (isEnglish) "100% On-device & private" else "১০০% অফলাইন ও নিরাপদ",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isEnglish) "Back" else "পিছনে যান"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = if (isEnglish) "Close" else "বন্ধ করুন"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(if (isEnglish) "Export Backup" else "ব্যাকআপ নিন") },
                    icon = { Icon(Icons.Filled.FileDownload, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(if (isEnglish) "Restore Data" else "রিস্টোর করুন") },
                    icon = { Icon(Icons.Filled.SettingsBackupRestore, contentDescription = null) }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (selectedTab == 0) {
                    // ── EXPORT TAB ───────────────────────────────────────────
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isEnglish) "Local & Privacy-First" else "সম্পূর্ণ নিরাপদ ও অফলাইন",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = if (isEnglish) {
                                        "Backups are exported in standard JSON. Your typing memory stays private and never connects to the internet."
                                    } else {
                                        "ব্যাকআপ স্ট্যান্ডার্ড JSON ফরম্যাটে তৈরি হয়। টাইপিং স্মৃতি সম্পূর্ণ গোপন থাকে এবং কখনো ইন্টারনেটে যায় না।"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Text(
                        text = if (isEnglish) "Select Items to Backup" else "ব্যাকআপে অন্তর্ভুক্ত বিষয়সমূহ",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                            BackupItemRow(
                                title = if (isEnglish) "Vocabulary & Typing Memory" else "শব্দকোষ ও টাইপিং স্মৃতি",
                                subtitle = if (isEnglish) "$userWordsCount user words • $learnedCount learned bigrams" else "$userWordsCount টি সংরক্ষিত শব্দ • $learnedCount টি শেখা বিগ্ৰাম",
                                icon = Icons.AutoMirrored.Filled.MenuBook,
                                checked = exportLearned,
                                onCheckedChange = { exportLearned = it }
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            BackupItemRow(
                                title = if (isEnglish) "Text Expansion Shortcuts" else "অটোকারেক্ট ও শর্টকাট",
                                subtitle = if (isEnglish) "$shortcutsCount custom replacement rules" else "$shortcutsCount টি নিজস্ব রিপ্লেসমেন্ট নিয়ম",
                                icon = Icons.Filled.Transform,
                                checked = exportShortcuts,
                                onCheckedChange = { exportShortcuts = it }
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            BackupItemRow(
                                title = if (isEnglish) "Keyboard Preferences" else "কীবোর্ড পছন্দ ও সেটিংস",
                                subtitle = if (isEnglish) "Layouts, themes, sounds, and haptic feedback" else "লেআউট, থিম, সাউন্ড ও কম্পন সেটিংস",
                                icon = Icons.Filled.Settings,
                                checked = exportPreferences,
                                onCheckedChange = { exportPreferences = it }
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            BackupItemRow(
                                title = if (isEnglish) "Pinned Clipboard Items" else "পিন করা ক্লিপবোর্ড তথ্য",
                                subtitle = if (isEnglish) "Starred and pinned snippets in clipboard vault" else "ক্লিপবোর্ডে সংরক্ষিত ও পিন করা নোটসমূহ",
                                icon = Icons.AutoMirrored.Filled.Assignment,
                                checked = exportClipboard,
                                onCheckedChange = { exportClipboard = it }
                            )
                        }
                    }

                    val hasAnySelected = exportLearned || exportShortcuts || exportPreferences || exportClipboard

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                                val fileName = "${LekhaniBackupManager.DEFAULT_BACKUP_FILENAME_PREFIX}${dateStr}.json"
                                createDocLauncher.launch(fileName)
                            },
                            enabled = hasAnySelected,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isEnglish) "Save to File" else "ফাইল সংরক্ষণ")
                        }

                        FilledTonalButton(
                            onClick = {
                                val json = LekhaniBackupManager.createBackupJson(
                                    context = context,
                                    dictManager = dictManager,
                                    includeLearned = exportLearned,
                                    includeShortcuts = exportShortcuts,
                                    includePreferences = exportPreferences,
                                    includeClipboard = exportClipboard,
                                )
                                val shareIntent = LekhaniBackupManager.createShareIntent(context, json, isEnglish)
                                context.startActivity(shareIntent)
                            },
                            enabled = hasAnySelected,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isEnglish) "Share" else "শেয়ার")
                        }
                    }
                } else {
                    // ── RESTORE TAB ──────────────────────────────────────────
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.FileUpload,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isEnglish) "Restore & Merge" else "পুনরুদ্ধার ও একত্রীকরণ",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = if (isEnglish) {
                                        "Select a Lekhani backup JSON file. Data will be safely merged into your local keyboard storage."
                                    } else {
                                        "লেখনী ব্যাকআপ ফাইলটি নির্বাচন করুন। নতুন তথ্যগুলো নিরাপদে আপনার কীবোর্ডে যুক্ত হবে।"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            openDocLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isEnglish) "Select Backup File (.json)" else "ব্যাকআপ ফাইল নির্বাচন করুন (.json)")
                    }

                    // Error display if parsing failed
                    if (parseError != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = parseError ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    // Success Summary display
                    if (restoreSuccessSummary != null) {
                        val s = restoreSuccessSummary!!
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isEnglish) "Restore Completed Successfully!" else "রিস্টোর সফলভাবে সম্পন্ন হয়েছে!",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Text(
                                    text = if (isEnglish) {
                                        "• ${s.userWordsCount} user words added\n• ${s.learnedWordsCount} learned bigrams merged\n• ${s.shortcutsCount} shortcuts restored\n• ${s.preferencesCount} preferences updated\n• ${s.pinnedClipsCount} clipboard notes restored"
                                    } else {
                                        "• ${s.userWordsCount} টি নতুন শব্দ যুক্ত হয়েছে\n• ${s.learnedWordsCount} টি শেখা বিগ্ৰাম অন্তর্ভুক্ত হয়েছে\n• ${s.shortcutsCount} টি শর্টকাট রিস্টোর হয়েছে\n• ${s.preferencesCount} টি পছন্দ আপডেট হয়েছে\n• ${s.pinnedClipsCount} টি ক্লিপবোর্ড নোট যুক্ত হয়েছে"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    // Inspect Preview Card
                    if (parsedSummary != null && rawImportJson != null) {
                        val summary = parsedSummary!!
                        val dateFormatted = remember(summary.timestamp) {
                            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(summary.timestamp))
                        }

                        Text(
                            text = if (isEnglish) "Backup File Contents" else "ব্যাকআপ ফাইলের বিষয়বস্তু",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = if (isEnglish) "Created: $dateFormatted (Schema v${summary.schemaVersion})" else "তৈরি: $dateFormatted (ভার্সন v${summary.schemaVersion})",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                                BackupItemRow(
                                    title = if (isEnglish) "Vocabulary & Memory" else "শব্দকোষ ও টাইপিং স্মৃতি",
                                    subtitle = if (isEnglish) "${summary.userWordsCount} custom words • ${summary.learnedWordsCount} learned patterns" else "${summary.userWordsCount} টি সংরক্ষিত শব্দ • ${summary.learnedWordsCount} টি শেখা প্যাটার্ন",
                                    icon = Icons.AutoMirrored.Filled.MenuBook,
                                    checked = restoreVocabulary,
                                    onCheckedChange = { restoreVocabulary = it }
                                )

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                                BackupItemRow(
                                    title = if (isEnglish) "Shortcuts & Rules" else "শর্টকাট ও নিয়মসমূহ",
                                    subtitle = if (isEnglish) "${summary.shortcutsCount} custom autocorrect rules" else "${summary.shortcutsCount} টি শর্টকাট নিয়ম",
                                    icon = Icons.Filled.Transform,
                                    checked = restoreShortcuts,
                                    onCheckedChange = { restoreShortcuts = it }
                                )

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                                BackupItemRow(
                                    title = if (isEnglish) "Preferences" else "কীবোর্ড পছন্দসমূহ",
                                    subtitle = if (isEnglish) "${summary.preferencesCount} configuration keys" else "${summary.preferencesCount} টি কনফিগারেশন মান",
                                    icon = Icons.Filled.Settings,
                                    checked = restorePreferences,
                                    onCheckedChange = { restorePreferences = it }
                                )

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                                BackupItemRow(
                                    title = if (isEnglish) "Pinned Clipboard Items" else "পিন করা ক্লিপবোর্ড",
                                    subtitle = if (isEnglish) "${summary.pinnedClipsCount} saved clips" else "${summary.pinnedClipsCount} টি সংরক্ষিত ক্লিপ",
                                    icon = Icons.AutoMirrored.Filled.Assignment,
                                    checked = restoreClipboard,
                                    onCheckedChange = { restoreClipboard = it }
                                )
                            }
                        }

                        val canRestore = restoreVocabulary || restoreShortcuts || restorePreferences || restoreClipboard

                        Button(
                            onClick = {
                                val jsonStr = rawImportJson ?: return@Button
                                val result = LekhaniBackupManager.restoreBackupJson(
                                    context = context,
                                    dictManager = dictManager,
                                    jsonString = jsonStr,
                                    restoreVocabulary = restoreVocabulary,
                                    restoreShortcuts = restoreShortcuts,
                                    restorePreferences = restorePreferences,
                                    restoreClipboard = restoreClipboard
                                )
                                restoreSuccessSummary = result
                                parsedSummary = null
                                rawImportJson = null
                                Toast.makeText(
                                    context,
                                    if (isEnglish) "Restored successfully" else "সফলভাবে রিস্টোর করা হয়েছে",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            enabled = canRestore,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isEnglish) "Restore & Merge Selected Data" else "নির্বাচিত তথ্য রিস্টোর ও মার্জ করুন")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun BackupItemRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onCheckedChange(!checked) },
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}
