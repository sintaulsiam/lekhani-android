package com.lekhani.android.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

import com.lekhani.android.data.dictionary.LekhaniDictionaryManager
import com.lekhani.android.data.settings.KeyboardPreferences
import com.lekhani.android.ffi.LekhaniLayoutType
import com.lekhani.android.model.LayoutRegistry
import com.lekhani.android.ui.about.AboutPrivacyTab
import com.lekhani.android.ui.dictionary.DictionaryManagementSheet
import com.lekhani.android.ui.theme.FormFactorGesturesSheet
import com.lekhani.android.ui.theme.ThemeStudioSheet
import com.lekhani.android.ui.theme.ToolbarCustomizationSheet
import com.lekhani.android.ui.tools.ToolsFeedbackTab

/**
 * LekhaniSettingsActivity
 * ══════════════════════════════════════════════════════════════════════════════
 * Main entry point and modern 5-tab Material 3 Settings app for Lekhani Keyboard.
 */
class LekhaniSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LekhaniAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LekhaniSettingsScreen(
                        onOpenImeSettings = {
                            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                        },
                        onOpenImePicker = {
                            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                            imm?.showInputMethodPicker()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun LekhaniSettingsScreen(
    onOpenImeSettings: () -> Unit = {},
    onOpenImePicker: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    val deviceContext = remember {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            context.createDeviceProtectedStorageContext()
        } else {
            context
        }
    }
    val prefs = remember {
        deviceContext.getSharedPreferences("lekhani_device_prefs", Context.MODE_PRIVATE)
    }

    val keyboardPrefs = remember { KeyboardPreferences.get(context) }
    val dictManager = remember { LekhaniDictionaryManager() }

    var showDictionarySheet by remember { mutableStateOf(false) }
    var showToolbarSheet by remember { mutableStateOf(false) }

    val navItems = listOf(
        Triple("লেআউট", Icons.Filled.Keyboard, "Layouts"),
        Triple("থিম", Icons.Filled.Palette, "Themes"),
        Triple("মোড", Icons.Filled.AspectRatio, "Modes"),
        Triple("টুল", Icons.Filled.Tune, "Tools"),
        Triple("সম্পর্কে", Icons.Filled.Info, "About")
    )

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                navItems.forEachIndexed { index, (label, icon, _) ->
                    NavigationBarItem(
                        selected = (selectedTab == index),
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium)
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> LayoutsTabContent(
                    context = context,
                    prefs = prefs,
                    onOpenImeSettings = onOpenImeSettings,
                    onOpenImePicker = onOpenImePicker
                )
                1 -> ThemeStudioSheet(
                    prefs = keyboardPrefs,
                    onClose = { selectedTab = 0 }
                )
                2 -> FormFactorGesturesSheet(
                    prefs = keyboardPrefs,
                    onClose = { selectedTab = 0 }
                )
                3 -> ToolsFeedbackTab(
                    prefs = keyboardPrefs,
                    dictManager = dictManager,
                    onOpenToolbarCustomizer = { showToolbarSheet = true },
                    onOpenDictionaryManager = { showDictionarySheet = true }
                )
                4 -> AboutPrivacyTab()
            }
        }
    }

    if (showToolbarSheet) {
        Dialog(
            onDismissRequest = { showToolbarSheet = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            ToolbarCustomizationSheet(
                prefs = keyboardPrefs,
                onClose = { showToolbarSheet = false }
            )
        }
    }

    if (showDictionarySheet) {
        Dialog(
            onDismissRequest = { showDictionarySheet = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            DictionaryManagementSheet(
                dictManager = dictManager,
                onClose = { showDictionarySheet = false }
            )
        }
    }
}

@Composable
private fun LayoutsTabContent(
    context: Context,
    prefs: android.content.SharedPreferences,
    onOpenImeSettings: () -> Unit,
    onOpenImePicker: () -> Unit
) {
    val scrollState = rememberScrollState()

    var enabledLayouts by remember {
        mutableStateOf(
            LayoutRegistry.parseEnabledLayouts(prefs.getString(LayoutRegistry.PREF_ENABLED_LAYOUTS, null))
        )
    }

    var isEnabled by remember { mutableStateOf(false) }
    var isDefault by remember { mutableStateOf(false) }
    var testText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val enabledList = imm?.enabledInputMethodList ?: emptyList()
        val pkg = context.packageName
        isEnabled = enabledList.any { it.packageName == pkg }

        val defaultIme = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        ) ?: ""
        isDefault = defaultIme.contains(pkg)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 18.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // ── Brand Header ────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF00E5B8), Color(0xFF006C50), Color(0xFF051C14))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "ল",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "লেখনি কীবোর্ড",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
            Text(
                text = "Lekhani Bengali Keyboard • 2026 Edition",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }

        // ── Status & Activation Cards ───────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "কীবোর্ড সেটআপ (Setup Status)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Step 1: Enable
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "১. লেখনি সক্রিয় করুন (Enable Lekhani)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = if (isEnabled) "সক্রিয় করা আছে (Enabled)" else "সিস্টেম সেটিংসে সক্ষম করুন",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (isEnabled) Color(0xFF00E5B8) else MaterialTheme.colorScheme.error
                            )
                        )
                    }
                    if (!isEnabled) {
                        Button(
                            onClick = onOpenImeSettings,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("সক্ষম করুন")
                        }
                    } else {
                        Text("✓", color = Color(0xFF00E5B8), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Step 2: Set as Default
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "২. ডিফল্ট কীবোর্ড নির্বাচন করুন (Set Default)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = if (isDefault) "ডিফল্ট হিসেবে সক্রিয় (Active)" else "প্রধান কীবোর্ড হিসেবে বেছে নিন",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (isDefault) Color(0xFF00E5B8) else MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                    if (!isDefault) {
                        FilledTonalButton(
                            onClick = onOpenImePicker,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("ডিফল্ট করুন")
                        }
                    } else {
                        Text("✓", color = Color(0xFF00E5B8), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                }
            }
        }

        // ── Layout Selection ────────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "কীবোর্ড লেআউট নির্বাচন (Keyboard Layouts)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = "যে লেআউটগুলো সক্রিয় রাখবেন সেগুলো চালু করুন (স্পেসবারে সোয়াইপ বা গ্লোব বাটনে পরিবর্তন হবে):",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.padding(vertical = 6.dp)
                )

                val allLayouts = listOf(
                    Triple(LekhaniLayoutType.PROBAHO, "লেখনি প্রবাহ (Lekhani Probaho)", "দ্বি-অঙ্গুলি আধুনিক প্রবাহ লেআউট (বাঁয়ে স্বরবর্ণ, ডানে ব্যঞ্জনবর্ণ)"),
                    Triple(LekhaniLayoutType.AVRO, "অভ্র ফোনেটিক (Avro Phonetic)", "রোমান ইংরেজি অক্ষরে ক্লাসিক ফোনেটিক প্রতিবর্ণীকরণ (ami → আমি)"),
                    Triple(LekhaniLayoutType.NATIONAL, "জাতীয় (BBS) (National BBS)", "বাংলাদেশ সরকারি BBS মানসম্মত অফিশিয়াল ফিক্সড লেআউট"),
                    Triple(LekhaniLayoutType.PROBHAT, "প্রভাত (प्रभात) (Probhat)", "জনপ্রিয় ফোনেটিক ফিক্সড লেআউট (প্রভাত স্ট্যান্ডার্ড)"),
                    Triple(LekhaniLayoutType.GBOARD, "জি-বোর্ড বাংলা (Gboard Style)", "অ্যান্ড্রয়েড ব্যবহারকারীদের পরিচিত গুগল জি-বোর্ড ম্যাপিং"),
                    Triple(LekhaniLayoutType.ENGLISH, "ইংরেজি (QWERTY) (English QWERTY)", "আন্তর্জাতিক মানসম্মত ইংরেজি বর্ণমালা ও পাসওয়ার্ড লেয়ার"),
                )

                allLayouts.forEachIndexed { index, (type, title, desc) ->
                    val isChecked = enabledLayouts.contains(type)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text(text = desc, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                        }
                        Switch(
                            checked = isChecked,
                            onCheckedChange = { checked ->
                                val updated = if (checked) {
                                    enabledLayouts + type
                                } else {
                                    if (enabledLayouts.size > 1) enabledLayouts - type else enabledLayouts
                                }
                                enabledLayouts = updated
                                prefs.edit().putString(
                                    LayoutRegistry.PREF_ENABLED_LAYOUTS,
                                    LayoutRegistry.serializeEnabledLayouts(updated)
                                ).apply()
                            }
                        )
                    }
                    if (index < allLayouts.size - 1) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }

        // ── Interactive Typing Test Box ─────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "টাইপিং পরীক্ষা (Test Typing)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = "এখানে ট্যাপ করে লেখনি কীবোর্ড পরীক্ষা করুন:",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                OutlinedTextField(
                    value = testText,
                    onValueChange = { testText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    placeholder = { Text("এখানে বাংলা লিখুন...") },
                    trailingIcon = {
                        if (testText.isNotEmpty()) {
                            IconButton(onClick = { testText = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun LekhaniAppTheme(
    content: @Composable () -> Unit
) {
    val darkColorScheme = darkColorScheme(
        primary = Color(0xFF00E5B8),
        onPrimary = Color(0xFF003829),
        primaryContainer = Color(0xFF00513C),
        onPrimaryContainer = Color(0xFF8CF4CB),
        secondary = Color(0xFFB1CCC0),
        surface = Color(0xFF161B19),
        background = Color(0xFF0F1412),
        surfaceVariant = Color(0xFF1F2925),
        onSurfaceVariant = Color(0xFF98A6A0),
        onSurface = Color(0xFFE1E3DF),
        onBackground = Color(0xFFE1E3DF)
    )

    MaterialTheme(
        colorScheme = darkColorScheme,
        content = content
    )
}
