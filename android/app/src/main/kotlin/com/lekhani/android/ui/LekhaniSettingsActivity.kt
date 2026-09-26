package com.lekhani.android.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
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
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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

import com.lekhani.android.data.clipboard.LekhaniClipboardStore
import com.lekhani.android.data.dictionary.LekhaniDictionaryManager
import com.lekhani.android.data.settings.KeyboardPreferences
import com.lekhani.android.ffi.LekhaniLayoutType
import com.lekhani.android.model.LayoutRegistry
import com.lekhani.android.theme.KeyboardTheme
import com.lekhani.android.theme.ThemeRegistry
import com.lekhani.android.ui.about.AboutPrivacyTab
import com.lekhani.android.ui.clipboard.ClipboardManagerSheet
import com.lekhani.android.ui.dictionary.DictionaryManagementSheet
import com.lekhani.android.ui.preferences.PreferencesTabContent
import com.lekhani.android.ui.theme.ThemeStudioSheet
import com.lekhani.android.ui.theme.ToolbarCustomizationSheet

/**
 * LekhaniSettingsActivity
 * ══════════════════════════════════════════════════════════════════════════════
 * Main entry point and modern 4-tab Material 3 Settings app for Lekhani Keyboard.
 * Uncluttered navigation bar:
 * - Layouts
 * - Themes
 * - Preferences
 * - Clipboard
 * Dynamic multi-theme support mirroring selected KeyboardTheme live.
 */
class LekhaniSettingsActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_CLIPBOARD = "open_clipboard"
        const val EXTRA_TAB_INDEX = "tab_index"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            com.lekhani.android.data.dictionary.LekhaniAssetInstaller.installAssetsIfNeeded(applicationContext)
        } catch (_: Exception) {}

        val startTab = if (intent?.getBooleanExtra(EXTRA_OPEN_CLIPBOARD, false) == true) {
            3
        } else {
            intent?.getIntExtra(EXTRA_TAB_INDEX, 0) ?: 0
        }

        setContent {
            val context = LocalContext.current
            val keyboardPrefs = remember { KeyboardPreferences.get(context) }
            var currentThemeId by remember { mutableStateOf(keyboardPrefs.themeId) }
            val activeTheme = remember(currentThemeId) { ThemeRegistry.resolveTheme(context, currentThemeId) }

            LekhaniAppTheme(theme = activeTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LekhaniSettingsScreen(
                        initialTab = startTab,
                        onThemeChanged = { newThemeId ->
                            currentThemeId = newThemeId
                        },
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
    initialTab: Int = 0,
    onThemeChanged: (String) -> Unit = {},
    onOpenImeSettings: () -> Unit = {},
    onOpenImePicker: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(initialTab) }

    val deviceContext = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
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
    val clipboardStore = remember { LekhaniClipboardStore(context) }

    var uiLanguage by remember { mutableStateOf(keyboardPrefs.uiLanguage) }
    val isEnglish = uiLanguage == "en"

    var showDictionarySheet by remember { mutableStateOf(false) }
    var showToolbarSheet by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    val navItems = if (isEnglish) {
        listOf(
            Triple("Layouts", Icons.Filled.Keyboard, "Layouts"),
            Triple("Themes", Icons.Filled.Palette, "Themes"),
            Triple("Preferences", Icons.Filled.Tune, "Preferences"),
            Triple("Clipboard", Icons.AutoMirrored.Filled.Assignment, "Clipboard"),
        )
    } else {
        listOf(
            Triple("লেআউট", Icons.Filled.Keyboard, "Layouts"),
            Triple("থিম", Icons.Filled.Palette, "Themes"),
            Triple("পছন্দ", Icons.Filled.Tune, "Preferences"),
            Triple("ক্লিপবোর্ড", Icons.AutoMirrored.Filled.Assignment, "Clipboard"),
        )
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.primaryContainer
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "লে",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isEnglish) "Lekhani Keyboard" else "লেখনী কীবোর্ড",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Bilingual Language Switcher Segment
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Row(
                                modifier = Modifier.padding(3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (!isEnglish) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .clickable {
                                            uiLanguage = "bn"
                                            keyboardPrefs.uiLanguage = "bn"
                                        }
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "বাংলা",
                                        fontSize = 12.sp,
                                        fontWeight = if (!isEnglish) FontWeight.Bold else FontWeight.Normal,
                                        color = if (!isEnglish) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (isEnglish) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .clickable {
                                            uiLanguage = "en"
                                            keyboardPrefs.uiLanguage = "en"
                                        }
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "EN",
                                        fontSize = 12.sp,
                                        fontWeight = if (isEnglish) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isEnglish) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Quick About & Privacy Dialog Trigger
                        IconButton(
                            onClick = { showAboutDialog = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = if (isEnglish) "About & Privacy" else "অ্যাপ সম্পর্কিত তথ্য",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
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
                    clipboardStore = clipboardStore,
                    isEnglish = isEnglish,
                    onOpenImeSettings = onOpenImeSettings,
                    onOpenImePicker = onOpenImePicker,
                    onOpenClipboard = { selectedTab = 3 }
                )
                1 -> ThemeStudioSheet(
                    prefs = keyboardPrefs,
                    isEnglish = isEnglish,
                    onThemeChanged = onThemeChanged,
                    onClose = { selectedTab = 0 }
                )
                2 -> PreferencesTabContent(
                    prefs = keyboardPrefs,
                    dictManager = dictManager,
                    isEnglish = isEnglish,
                    onOpenToolbarCustomizer = { showToolbarSheet = true },
                    onOpenDictionaryManager = { showDictionarySheet = true },
                    onOpenAbout = { showAboutDialog = true }
                )
                3 -> ClipboardManagerSheet(
                    clipboardStore = clipboardStore,
                    prefs = keyboardPrefs,
                    isEnglish = isEnglish,
                    onClose = { selectedTab = 0 }
                )
            }
        }
    }

    if (showAboutDialog) {
        Dialog(
            onDismissRequest = { showAboutDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showAboutDialog = false }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = if (isEnglish) "Back" else "ফিরে যান"
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isEnglish) "About Lekhani Keyboard" else "লেখনী কীবোর্ড সম্পর্কে",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    AboutPrivacyTab(
                        isEnglish = isEnglish,
                        modifier = Modifier.fillMaxSize()
                    )
                }
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
                isEnglish = isEnglish,
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
                isEnglish = isEnglish,
                onClose = { showDictionarySheet = false }
            )
        }
    }
}

@Composable
private fun LayoutsTabContent(
    context: Context,
    prefs: android.content.SharedPreferences,
    clipboardStore: LekhaniClipboardStore,
    isEnglish: Boolean = false,
    onOpenImeSettings: () -> Unit,
    onOpenImePicker: () -> Unit,
    onOpenClipboard: () -> Unit,
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

    fun refreshImeStatus() {
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

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshImeStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Brand Header (With 'লে' Glyph) ──────────────────────────────────────
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.background
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "লে",
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (isEnglish) "Lekhani Bengali Keyboard" else "লেখনী কীবোর্ড",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
            Text(
                text = if (isEnglish) "Bengali Next-Gen Ergonomic Keyboard • 2026 Edition"
                       else "নেক্সট-জেন এরগনোমিক বাংলা কীবোর্ড • ২০২৬ এডিশন",
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
                    text = if (isEnglish) "3-Step Keyboard Setup Wizard" else "৩-ধাপের কীবোর্ড সেটআপ উইজার্ড",
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
                            text = if (isEnglish) "1. Enable Lekhani Keyboard" else "১. লেখনী কীবোর্ড সক্রিয় করুন",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = if (isEnabled) (if (isEnglish) "Enabled in System Settings" else "সিস্টেম সেটিংসে সক্রিয় করা আছে")
                                   else (if (isEnglish) "Action required in Settings" else "সিস্টেম সেটিংসে সক্ষম করুন"),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        )
                    }
                    if (!isEnabled) {
                        Button(
                            onClick = onOpenImeSettings,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(if (isEnglish) "Enable" else "সক্ষম করুন")
                        }
                    } else {
                        Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
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
                            text = if (isEnglish) "2. Select as Default Keyboard" else "২. ডিফল্ট কীবোর্ড হিসেবে বেছে নিন",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = if (isDefault) (if (isEnglish) "Active as Default" else "ডিফল্ট হিসেবে সক্রিয়")
                                   else (if (isEnglish) "Tap to select Lekhani" else "প্রধান কীবোর্ড হিসেবে বেছে নিন"),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (isDefault) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                    if (!isDefault) {
                        FilledTonalButton(
                            onClick = onOpenImePicker,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(if (isEnglish) "Set Default" else "ডিফল্ট করুন")
                        }
                    } else {
                        Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Step 3: Interactive Typing Playground
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isEnglish) "3. Interactive Typing Playground" else "৩. টাইপিং পরীক্ষা ও প্লেগ্রাউন্ড",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = if (isDefault && isEnabled) {
                                if (isEnglish) "Ready to test! Tap the test box below" else "প্রস্তুত! নিচের বক্সে লিখে পরীক্ষা করুন"
                            } else {
                                if (isEnglish) "Complete Steps 1 & 2 first" else "প্রথমে ধাপ ১ ও ২ সম্পন্ন করুন"
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (isDefault && isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                    if (isDefault && isEnabled) {
                        Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                }

                if (isEnabled && isDefault) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isEnglish) "🎉 Setup complete! Lekhani Keyboard is active as default."
                                   else "🎉 সমস্ত ধাপ সম্পন্ন! লেখনী কীবোর্ড সফলভাবে সক্রিয় ও ডিফল্ট করা হয়েছে।",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // ── Quick Access: Clipboard & Vault Card ──────────────────────────────
        val clips by clipboardStore.clips.collectAsState()
        val savedCount = remember(clips) { clips.count { it.isSaved } }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Assignment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = if (isEnglish) "Clipboard & Vault" else "ক্লিপবোর্ড ও ভল্ট",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isEnglish) "${clips.size} clips • $savedCount saved in vault"
                                   else "${clips.size}টি ক্লিপ • ${savedCount}টি ভল্টে সংরক্ষিত",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                FilledTonalButton(
                    onClick = onOpenClipboard,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(if (isEnglish) "Open" else "ওপেন করুন")
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
                    text = if (isEnglish) "Keyboard Layouts" else "কীবোর্ড লেআউটসমূহ",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = if (isEnglish) "Select enabled layouts (switch via Spacebar swipe, Globe key or Toolbar):"
                           else "সক্রিয় লেআউটসমূহ বেছে নিন (স্পেসবার সোয়াইপ, গ্লোব কি বা টুলবারে পরিবর্তন হবে):",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.padding(vertical = 6.dp)
                )

                val allLayouts = listOf(
                    Triple(LekhaniLayoutType.PROBAHO, "Lekhani প্রবাহ (Probaho)", if (isEnglish) "Ergonomic two-thumb engine (vowels left, consonants right)" else "দ্বি-অঙ্গুলি আধুনিক প্রবাহ লেআউট (বাঁয়ে স্বরবর্ণ, ডানে ব্যঞ্জনবর্ণ)"),
                    Triple(LekhaniLayoutType.AVRO, "অভ্র ফোনেটিক (Avro)", if (isEnglish) "Classic phonetic transliteration (ami → আমি)" else "রোমান ইংরেজি অক্ষরে ক্লাসিক ফোনেটিক প্রতিবর্ণীকরণ (ami → আমি)"),
                    Triple(LekhaniLayoutType.NATIONAL, "জাতীয় (BBS National)", if (isEnglish) "Bangladesh Government BBS official standard layout" else "বাংলাদেশ সরকারি BBS মানসম্মত অফিশিয়াল ফিক্সড লেআউট"),
                    Triple(LekhaniLayoutType.PROBHAT, "প্রভাত (Probhat)", if (isEnglish) "Popular phonetic fixed layout" else "জনপ্রিয় ফোনেটিক ফিক্সড লেআউট (প্রভাত স্ট্যান্ডার্ড)"),
                    Triple(LekhaniLayoutType.GBOARD, "জি-বোর্ড বাংলা (Gboard Style)", if (isEnglish) "Familiar Google Gboard Bengali key mapping" else "অ্যান্ড্রয়েড ব্যবহারকারীদের পরিচিত গুগল জি-বোর্ড ম্যাপিং"),
                    Triple(LekhaniLayoutType.ENGLISH, "English (QWERTY)", if (isEnglish) "Standard alphanumeric QWERTY and password layer" else "আন্তর্জাতিক মানসম্মত ইংরেজি বর্ণমালা ও পাসওয়ার্ড লেয়ার"),
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
                    text = if (isEnglish) "Interactive Typing Test" else "টাইপিং পরীক্ষা (Test Typing)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = if (isEnglish) "Tap below to test Lekhani Keyboard live:"
                           else "নিচে ট্যাপ করে লেখনী কীবোর্ড সরাসরি পরীক্ষা করুন:",
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
                    placeholder = { Text(if (isEnglish) "Type here to test..." else "এখানে বাংলা লিখে পরীক্ষা করুন...") },
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

/**
 * LekhaniAppTheme
 * ══════════════════════════════════════════════════════════════════════════════
 * Dynamic Material 3 theme engine adapting seamlessly to the user's selected
 * [KeyboardTheme] (Daylight Light, OLED Black, Avro Blue, Cyber Indigo, Flow Teal,
 * or Material You on Android 12+).
 */
@Composable
fun LekhaniAppTheme(
    theme: KeyboardTheme,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = remember(theme.id, theme.isDark) {
        if (theme.id == ThemeRegistry.ID_MATERIAL_YOU && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val isSystemDark = (context.resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            if (isSystemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else if (!theme.isDark) {
            // Light Theme (e.g. Daylight Paper)
            lightColorScheme(
                primary = Color(0xFF006C50),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFB2DFDB),
                onPrimaryContainer = Color(0xFF002018),
                secondary = Color(0xFF4A635B),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFCCE8DE),
                onSecondaryContainer = Color(0xFF051F19),
                background = Color(0xFFF7F9FA),
                onBackground = Color(0xFF191C1B),
                surface = Color(0xFFFFFFFF),
                onSurface = Color(0xFF191C1B),
                surfaceVariant = Color(0xFFE8ECEF),
                onSurfaceVariant = Color(0xFF404945),
                outline = Color(0xFFB0BEC5),
                outlineVariant = Color(0xFFCFD8DC)
            )
        } else {
            // Dark Themes
            when (theme.id) {
                ThemeRegistry.ID_OLED_BLACK -> darkColorScheme(
                    primary = Color(0xFF00E676),
                    onPrimary = Color(0xFF00391A),
                    primaryContainer = Color(0xFF005328),
                    onPrimaryContainer = Color(0xFF73FBA4),
                    secondary = Color(0xFFB5CCBA),
                    onSecondary = Color(0xFF213528),
                    surface = Color(0xFF121212),
                    background = Color(0xFF000000), // AMOLED Pure Black
                    surfaceVariant = Color(0xFF1E1E1E),
                    onSurfaceVariant = Color(0xFFA0A0A0),
                    onSurface = Color(0xFFFFFFFF),
                    onBackground = Color(0xFFFFFFFF),
                    outline = Color(0xFF2E2E2E),
                    outlineVariant = Color(0xFF222222)
                )
                ThemeRegistry.ID_AVRO_BLUE -> darkColorScheme(
                    primary = Color(0xFF64B5F6),
                    onPrimary = Color(0xFF0D2847),
                    primaryContainer = Color(0xFF153E6D),
                    onPrimaryContainer = Color(0xFFD0E4FF),
                    secondary = Color(0xFFB8C8DA),
                    onSecondary = Color(0xFF223240),
                    surface = Color(0xFF101926),
                    background = Color(0xFF080D15),
                    surfaceVariant = Color(0xFF1B2638),
                    onSurfaceVariant = Color(0xFF8C9DB5),
                    onSurface = Color(0xFFEDF2F9),
                    onBackground = Color(0xFFEDF2F9),
                    outline = Color(0xFF2E3E56),
                    outlineVariant = Color(0xFF1E2C40)
                )
                ThemeRegistry.ID_CYBER_INDIGO -> darkColorScheme(
                    primary = Color(0xFFCFBCFF),
                    onPrimary = Color(0xFF381E72),
                    primaryContainer = Color(0xFF4F378B),
                    onPrimaryContainer = Color(0xFFEADDFF),
                    secondary = Color(0xFFCBC2DB),
                    onSecondary = Color(0xFF332D41),
                    surface = Color(0xFF140F22),
                    background = Color(0xFF0C081A),
                    surfaceVariant = Color(0xFF221A38),
                    onSurfaceVariant = Color(0xFFA99DC4),
                    onSurface = Color(0xFFF5EEFF),
                    onBackground = Color(0xFFF5EEFF),
                    outline = Color(0xFF3C3058),
                    outlineVariant = Color(0xFF2B2042)
                )
                else -> darkColorScheme( // Flow Teal
                    primary = Color(0xFF00E5B8),
                    onPrimary = Color(0xFF003829),
                    primaryContainer = Color(0xFF00513C),
                    onPrimaryContainer = Color(0xFF8CF4CB),
                    secondary = Color(0xFFB1CCC0),
                    onSecondary = Color(0xFF1C352C),
                    surface = Color(0xFF141A17),
                    background = Color(0xFF0D1117),
                    surfaceVariant = Color(0xFF1B2420),
                    onSurfaceVariant = Color(0xFF90A39B),
                    onSurface = Color(0xFFE1E5E2),
                    onBackground = Color(0xFFE1E5E2),
                    outline = Color(0xFF2B3A34),
                    outlineVariant = Color(0xFF1C2824)
                )
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
