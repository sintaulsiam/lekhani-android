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
import androidx.compose.foundation.border
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.key
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.DragHandle
import com.lekhani.android.ui.components.LekhaniBrandLogo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
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
            var currentAppThemeMode by remember { mutableStateOf(keyboardPrefs.appThemeMode) }
            val activeTheme = remember(currentThemeId) { ThemeRegistry.resolveTheme(context, currentThemeId) }

            LekhaniAppTheme(theme = activeTheme, appThemeMode = currentAppThemeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LekhaniSettingsScreen(
                        initialTab = startTab,
                        onThemeChanged = { newThemeId ->
                            currentThemeId = newThemeId
                        },
                        onAppThemeModeChanged = { newMode ->
                            currentAppThemeMode = newMode
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
    onAppThemeModeChanged: (KeyboardPreferences.AppThemeMode) -> Unit = {},
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
                        LekhaniBrandLogo(size = 32.dp, shapeCornerPercent = 25)
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
                            modifier = Modifier.size(48.dp)
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
                    onAppThemeModeChanged = onAppThemeModeChanged,
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

    var orderedLayouts by remember {
        val saved = LayoutRegistry.parseEnabledLayouts(prefs.getString(LayoutRegistry.PREF_ENABLED_LAYOUTS, null))
        val missing = LayoutRegistry.all.filter { !saved.contains(it) }
        mutableStateOf(saved + missing)
    }

    var enabledLayouts by remember {
        mutableStateOf(
            LayoutRegistry.parseEnabledLayouts(prefs.getString(LayoutRegistry.PREF_ENABLED_LAYOUTS, null)).toSet()
        )
    }

    fun persistLayouts(newOrder: List<LekhaniLayoutType>, newEnabled: Set<LekhaniLayoutType>) {
        orderedLayouts = newOrder
        enabledLayouts = newEnabled
        val enabledOrdered = newOrder.filter { newEnabled.contains(it) }
        prefs.edit().putString(
            LayoutRegistry.PREF_ENABLED_LAYOUTS,
            LayoutRegistry.serializeEnabledLayouts(enabledOrdered)
        ).apply()
        enabledOrdered.firstOrNull()?.let { primary ->
            prefs.edit().putString(LayoutRegistry.PREF_ACTIVE_LAYOUT, primary.name).apply()
        }
    }

    fun moveLayout(fromIndex: Int, toIndex: Int) {
        if (fromIndex in orderedLayouts.indices && toIndex in orderedLayouts.indices) {
            val mutable = orderedLayouts.toMutableList()
            val item = mutable.removeAt(fromIndex)
            mutable.add(toIndex, item)
            persistLayouts(mutable, enabledLayouts)
        }
    }

    fun toggleLayout(type: LekhaniLayoutType, isChecked: Boolean) {
        val updated = if (isChecked) {
            enabledLayouts + type
        } else {
            if (enabledLayouts.size > 1) enabledLayouts - type else enabledLayouts
        }
        persistLayouts(orderedLayouts, updated)
    }

    fun checkImeStatus(): Pair<Boolean, Boolean> {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val enabledList = imm?.enabledInputMethodList ?: emptyList()
        val pkg = context.packageName
        val enabled = enabledList.any { it.packageName == pkg }

        val defaultIme = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        ) ?: ""
        val default = defaultIme.contains(pkg)
        return Pair(enabled, default)
    }

    val initialStatus = remember { checkImeStatus() }
    var isEnabled by remember { mutableStateOf(initialStatus.first) }
    var isDefault by remember { mutableStateOf(initialStatus.second) }
    var testText by remember { mutableStateOf("") }

    fun refreshImeStatus() {
        val (enabled, default) = checkImeStatus()
        isEnabled = enabled
        isDefault = default
    }

    LaunchedEffect(Unit) {
        refreshImeStatus()
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
        // ── Brand Header (With Unified Logo) ──────────────────────────────────
        LekhaniBrandLogo(size = 64.dp, shapeCornerPercent = 25)

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (isEnglish) "Lekhani Keyboard" else "লেখনী কীবোর্ড",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
            Text(
                text = if (isEnglish) "Offline, private Bengali keyboard"
                       else "সম্পূর্ণ অফলাইন ও নিরাপদ বাংলা কীবোর্ড",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }

        // ── Status & Activation ─────────────────────────────────────────────────
        if (isEnabled && isDefault) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isEnglish) "Active as default keyboard"
                                   else "ডিফল্ট কীবোর্ড চালু আছে",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                    FilledTonalButton(
                        onClick = onOpenImePicker,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(
                            text = if (isEnglish) "Switch" else "বদলান",
                            fontSize = 11.5.sp
                        )
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isEnglish) "Keyboard Setup" else "কীবোর্ড সেটআপ",
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
                                text = if (isEnglish) "1. Enable Lekhani" else "১. লেখনী কীবোর্ড চালু করুন",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = if (isEnabled) (if (isEnglish) "Enabled in system settings" else "সিস্টেম সেটিংসে চালু আছে")
                                       else (if (isEnglish) "Tap to enable in settings" else "সেটিংসে গিয়ে চালু করুন"),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            )
                        }
                        if (!isEnabled) {
                            Button(
                                onClick = onOpenImeSettings,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(if (isEnglish) "Enable" else "চালু করুন")
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
                                text = if (isEnglish) "2. Select as Default" else "২. ডিফল্ট কীবোর্ড নির্বাচন করুন",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = if (isDefault) (if (isEnglish) "Active as default" else "ডিফল্ট হিসেবে সক্রিয়")
                                       else (if (isEnglish) "Tap to select Lekhani" else "ডিফল্ট কীবোর্ড হিসেবে বেছে নিন"),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (isDefault) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                        if (!isDefault) {
                            FilledTonalButton(
                                onClick = onOpenImePicker,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(if (isEnglish) "Select" else "নির্বাচন")
                            }
                        } else {
                            Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                    }
                }
            }
        }

        // ── Quick Access: Clipboard & Vault Card ──────────────────────────────
        val clips by clipboardStore.clips.collectAsState()
        val savedCount = remember(clips) { clips.count { it.isSaved } }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
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
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Assignment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = if (isEnglish) "Clipboard & Vault" else "ক্লিপবোর্ড ও ভল্ট",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isEnglish) "${clips.size} clips • $savedCount saved"
                                   else "${clips.size}টি ক্লিপ • ${savedCount}টি সেভ করা",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                FilledTonalButton(
                    onClick = onOpenClipboard,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(if (isEnglish) "Open" else "খুলুন", fontSize = 12.sp)
                }
            }
        }

        // ── 3. Keyboard Layout Selection & Ordering Card ───────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Keyboard,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isEnglish) "Keyboard Layouts & Priority" else "কীবোর্ড লেআউট ও অগ্রাধিকার",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = if (isEnglish) "Reorder cycling sequence or toggle layouts"
                                   else "লেআউটের ক্রম সাজান এবং চালু বা বন্ধ রাখুন",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )

                var draggingLayout by remember { mutableStateOf<LekhaniLayoutType?>(null) }
                var dragOffsetY by remember { mutableFloatStateOf(0f) }
                var dragStartIndex by remember { mutableIntStateOf(-1) }
                var currentDropIndex by remember { mutableIntStateOf(-1) }
                var itemHeightPx by remember { mutableFloatStateOf(0f) }
                val itemCenterYs = remember { mutableStateMapOf<Int, Float>() }
                val haptic = LocalHapticFeedback.current
                val density = LocalDensity.current

                val isDraggingActive = (draggingLayout != null && dragStartIndex in orderedLayouts.indices && currentDropIndex in orderedLayouts.indices)
                val effectiveRowHeight = if (itemHeightPx > 0f) itemHeightPx else with(density) { 68.dp.toPx() }

                orderedLayouts.forEachIndexed { index, type ->
                    key(type) {
                        val isChecked = enabledLayouts.contains(type)
                        val isPrimary = isChecked && (type == orderedLayouts.firstOrNull { enabledLayouts.contains(it) })
                        val title = if (isEnglish) LayoutRegistry.getEnglishName(type) else LayoutRegistry.getBengaliName(type)
                        val desc = LayoutRegistry.getDescription(type, isEnglish)
                        val isThisDragging = (draggingLayout == type)

                        // Calculate slot opening displacement for non-dragged items
                        val targetShift = when {
                            !isDraggingActive -> 0f
                            isThisDragging -> 0f
                            dragStartIndex > currentDropIndex && index >= currentDropIndex && index < dragStartIndex -> effectiveRowHeight
                            dragStartIndex < currentDropIndex && index > dragStartIndex && index <= currentDropIndex -> -effectiveRowHeight
                            else -> 0f
                        }

                        val shiftAnim = remember(type) { Animatable(0f) }
                        LaunchedEffect(targetShift, isDraggingActive) {
                            if (isDraggingActive) {
                                shiftAnim.animateTo(
                                    targetValue = targetShift,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            } else {
                                shiftAnim.snapTo(0f)
                            }
                        }


                        val showIndicatorAbove = isDraggingActive && (dragStartIndex > currentDropIndex) && (index == currentDropIndex)
                        val showIndicatorBelow = isDraggingActive && (dragStartIndex < currentDropIndex) && (index == currentDropIndex)

                        if (showIndicatorAbove) {
                            DropPlacementIndicator(
                                targetSlotNumber = currentDropIndex + 1,
                                isDefaultSlot = currentDropIndex == 0,
                                isEnglish = isEnglish
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onSizeChanged { size ->
                                    if (size.height > 0 && !isThisDragging) {
                                        itemHeightPx = size.height.toFloat()
                                    }
                                }
                                .onGloballyPositioned { coords ->
                                    val bounds = coords.boundsInParent()
                                    itemCenterYs[index] = bounds.top + bounds.height / 2f
                                }
                        ) {
                            // Minimalist Origin placeholder slot when this item is lifted
                            if (isThisDragging) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .matchParentSize()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f))
                                        .border(
                                            BorderStroke(
                                                1.dp,
                                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                            ),
                                            RoundedCornerShape(12.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                                        )
                                        Text(
                                            text = if (isEnglish) "Origin slot" else "মূল অবস্থান",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                                fontWeight = FontWeight.Medium
                                            )
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                                        )
                                    }
                                }
                            }

                            // Active layout item surface (floats with shadow, glow, and tilt when dragging)
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isThisDragging) {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f)
                                } else {
                                    Color.Transparent
                                },
                                shadowElevation = if (isThisDragging) 16.dp else 0.dp,
                                border = if (isThisDragging) {
                                    BorderStroke(
                                        1.5.dp,
                                        Brush.horizontalGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                            )
                                        )
                                    )
                                } else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .zIndex(if (isThisDragging) 30f else 0f)
                                    .graphicsLayer {
                                        translationY = if (isThisDragging) dragOffsetY else shiftAnim.value
                                        scaleX = if (isThisDragging) 1.03f else 1f
                                        scaleY = if (isThisDragging) 1.03f else 1f
                                    }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (isThisDragging) {
                                                Modifier.background(
                                                    Brush.verticalGradient(
                                                        listOf(
                                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                                                            Color.Transparent
                                                        )
                                                    )
                                                )
                                            } else Modifier
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 7.dp, horizontal = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = title,
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (isChecked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                )
                                                if (isThisDragging) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = MaterialTheme.colorScheme.primary,
                                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                                    ) {
                                                        val targetSlot = currentDropIndex + 1
                                                        Text(
                                                            text = if (isEnglish) "→ Slot #$targetSlot" else "→ $targetSlot নং এ",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                } else if (isPrimary) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                    ) {
                                                        Text(
                                                            text = if (isEnglish) "Default" else "ডিফল্ট",
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                color = MaterialTheme.colorScheme.primary,
                                                                fontWeight = FontWeight.Bold
                                                            ),
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                text = desc,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            )
                                        }

                                        // Switch & Drag Handle
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Switch(
                                                checked = isChecked,
                                                enabled = !isChecked || enabledLayouts.size > 1,
                                                onCheckedChange = { toggleLayout(type, it) }
                                            )

                                            Spacer(modifier = Modifier.width(6.dp))

                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(
                                                        if (isThisDragging) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                                        else Color.Transparent
                                                    )
                                                    .pointerInput(type) {
                                                        detectDragGestures(
                                                            onDragStart = {
                                                                draggingLayout = type
                                                                val sIdx = orderedLayouts.indexOf(type)
                                                                dragStartIndex = sIdx
                                                                currentDropIndex = sIdx
                                                                dragOffsetY = 0f
                                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            },
                                                            onDragEnd = {
                                                                val from = dragStartIndex
                                                                val to = currentDropIndex
                                                                if (from in orderedLayouts.indices && to in orderedLayouts.indices && from != to) {
                                                                    moveLayout(from, to)
                                                                }
                                                                draggingLayout = null
                                                                dragStartIndex = -1
                                                                currentDropIndex = -1
                                                                dragOffsetY = 0f
                                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            },
                                                            onDragCancel = {
                                                                draggingLayout = null
                                                                dragStartIndex = -1
                                                                currentDropIndex = -1
                                                                dragOffsetY = 0f
                                                            },
                                                            onDrag = { change, dragAmount ->
                                                                change.consume()
                                                                val sIdx = dragStartIndex
                                                                if (sIdx >= 0) {
                                                                    val startCenter = itemCenterYs[sIdx] ?: 0f
                                                                    val minCenter = itemCenterYs[0] ?: startCenter
                                                                    val maxCenter = itemCenterYs[orderedLayouts.lastIndex] ?: startCenter
                                                                    val minDragY = minCenter - startCenter - with(density) { 24.dp.toPx() }
                                                                    val maxDragY = maxCenter - startCenter + with(density) { 24.dp.toPx() }

                                                                    dragOffsetY = (dragOffsetY + dragAmount.y).coerceIn(minDragY, maxDragY)
                                                                    val currentFingerY = startCenter + dragOffsetY

                                                                    val closest = itemCenterYs.entries.minByOrNull { kotlin.math.abs(it.value - currentFingerY) }?.key ?: sIdx
                                                                    val newDropIndex = closest.coerceIn(0, orderedLayouts.lastIndex)
                                                                    if (newDropIndex != currentDropIndex) {
                                                                        currentDropIndex = newDropIndex
                                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                    }
                                                                }
                                                            }
                                                        )
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.DragHandle,
                                                    contentDescription = if (isEnglish) "Drag to reorder $title" else "$title এর ক্রম পরিবর্তন করতে টানুন",
                                                    tint = if (isThisDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (showIndicatorBelow) {
                            DropPlacementIndicator(
                                targetSlotNumber = currentDropIndex + 1,
                                isDefaultSlot = false,
                                isEnglish = isEnglish
                            )
                        }

                        if (index < orderedLayouts.lastIndex && !showIndicatorBelow) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                            )
                        }
                    }
                }
            }
        }

        // ── Interactive Typing Test Box ─────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (isEnglish) "Test Typing" else "টাইপিং পরীক্ষা",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = if (isEnglish) "Tap below to test Lekhani live:"
                           else "নিচে লিখে কীবোর্ড পরীক্ষা করুন:",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
                )

                OutlinedTextField(
                    value = testText,
                    onValueChange = { testText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(if (isEnglish) "Type here to test..." else "এখানে লিখে পরীক্ষা করুন...") },
                    trailingIcon = {
                        if (testText.isNotEmpty()) {
                            IconButton(onClick = { testText = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
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
    appThemeMode: KeyboardPreferences.AppThemeMode = KeyboardPreferences.AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isSystemDark = (context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES

    val shouldUseDark = when (appThemeMode) {
        KeyboardPreferences.AppThemeMode.LIGHT -> false
        KeyboardPreferences.AppThemeMode.DARK -> true
        KeyboardPreferences.AppThemeMode.SYSTEM -> isSystemDark
        KeyboardPreferences.AppThemeMode.MATCH_KEYBOARD -> theme.isDark
    }

    val colorScheme = remember(theme.id, appThemeMode, isSystemDark) {
        if (theme.id == ThemeRegistry.ID_MATERIAL_YOU && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (shouldUseDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else if (appThemeMode == KeyboardPreferences.AppThemeMode.LIGHT || (!shouldUseDark && appThemeMode != KeyboardPreferences.AppThemeMode.MATCH_KEYBOARD)) {
            // Clean standard light theme
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
        } else if (appThemeMode == KeyboardPreferences.AppThemeMode.DARK && appThemeMode != KeyboardPreferences.AppThemeMode.MATCH_KEYBOARD) {
            // Clean neutral dark theme
            darkColorScheme(
                primary = Color(0xFF80CBC4),
                onPrimary = Color(0xFF003728),
                primaryContainer = Color(0xFF00513C),
                onPrimaryContainer = Color(0xFFB2DFDB),
                secondary = Color(0xFFB0CCC2),
                onSecondary = Color(0xFF1B352D),
                surface = Color(0xFF161A19),
                background = Color(0xFF0E1211),
                surfaceVariant = Color(0xFF222927),
                onSurfaceVariant = Color(0xFF98A6A1),
                onSurface = Color(0xFFE2E7E5),
                onBackground = Color(0xFFE2E7E5),
                outline = Color(0xFF333E3B),
                outlineVariant = Color(0xFF232C29)
            )
        } else if (!theme.isDark) {
            // Light keyboard theme matching
            if (theme.id == ThemeRegistry.ID_MOCHA_LATTE) {
                lightColorScheme(
                    primary = Color(0xFF6D4C41),
                    onPrimary = Color.White,
                    primaryContainer = Color(0xFFD7CCC8),
                    onPrimaryContainer = Color(0xFF2D241E),
                    secondary = Color(0xFF8D6E63),
                    onSecondary = Color.White,
                    secondaryContainer = Color(0xFFEFEBE9),
                    onSecondaryContainer = Color(0xFF3E2723),
                    background = Color(0xFFFAF6F2),
                    onBackground = Color(0xFF2D241E),
                    surface = Color(0xFFFFFFFF),
                    onSurface = Color(0xFF2D241E),
                    surfaceVariant = Color(0xFFE4D8CE),
                    onSurfaceVariant = Color(0xFF5D4037),
                    outline = Color(0xFFBCAAA4),
                    outlineVariant = Color(0xFFD7CCC8)
                )
            } else {
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
            }
        } else {
            // Dark Themes matching keyboard
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
                ThemeRegistry.ID_SAKURA_DUSK -> darkColorScheme(
                    primary = Color(0xFFF48FB1),
                    onPrimary = Color(0xFF4A1028),
                    primaryContainer = Color(0xFF671D3E),
                    onPrimaryContainer = Color(0xFFFFD9E2),
                    secondary = Color(0xFFDABAC3),
                    onSecondary = Color(0xFF3D2730),
                    surface = Color(0xFF211A22),
                    background = Color(0xFF141016),
                    surfaceVariant = Color(0xFF2E2430),
                    onSurfaceVariant = Color(0xFFC7B1BF),
                    onSurface = Color(0xFFFCE4EC),
                    onBackground = Color(0xFFFCE4EC),
                    outline = Color(0xFF4B394E),
                    outlineVariant = Color(0xFF38293B)
                )
                ThemeRegistry.ID_FOREST_EMERALD -> darkColorScheme(
                    primary = Color(0xFF00E676),
                    onPrimary = Color(0xFF003918),
                    primaryContainer = Color(0xFF005224),
                    onPrimaryContainer = Color(0xFF6EFF9E),
                    secondary = Color(0xFFB5CCBC),
                    onSecondary = Color(0xFF20352A),
                    surface = Color(0xFF0F1D16),
                    background = Color(0xFF08120D),
                    surfaceVariant = Color(0xFF162B21),
                    onSurfaceVariant = Color(0xFF8CB29E),
                    onSurface = Color(0xFFE8F5E9),
                    onBackground = Color(0xFFE8F5E9),
                    outline = Color(0xFF254737),
                    outlineVariant = Color(0xFF1B3528)
                )
                ThemeRegistry.ID_NORDIC_FROST -> darkColorScheme(
                    primary = Color(0xFF38BDF8),
                    onPrimary = Color(0xFF003549),
                    primaryContainer = Color(0xFF004D6A),
                    onPrimaryContainer = Color(0xFFC3E8FF),
                    secondary = Color(0xFFB4C8D8),
                    onSecondary = Color(0xFF1E3240),
                    surface = Color(0xFF152033),
                    background = Color(0xFF0B1320),
                    surfaceVariant = Color(0xFF1E2D44),
                    onSurfaceVariant = Color(0xFF90A4BC),
                    onSurface = Color(0xFFF0F6FC),
                    onBackground = Color(0xFFF0F6FC),
                    outline = Color(0xFF2F4462),
                    outlineVariant = Color(0xFF213149)
                )
                ThemeRegistry.ID_SUNSET_AMBER -> darkColorScheme(
                    primary = Color(0xFFFF9100),
                    onPrimary = Color(0xFF462100),
                    primaryContainer = Color(0xFF643200),
                    onPrimaryContainer = Color(0xFFFFDCBE),
                    secondary = Color(0xFFDBBEA5),
                    onSecondary = Color(0xFF3C2B1B),
                    surface = Color(0xFF221A12),
                    background = Color(0xFF140F0A),
                    surfaceVariant = Color(0xFF2F241A),
                    onSurfaceVariant = Color(0xFFC4AB95),
                    onSurface = Color(0xFFFFF3E0),
                    onBackground = Color(0xFFFFF3E0),
                    outline = Color(0xFF4C3A2B),
                    outlineVariant = Color(0xFF38291D)
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

@Composable
private fun DropPlacementIndicator(
    targetSlotNumber: Int,
    isDefaultSlot: Boolean,
    isEnglish: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(2.dp)
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                        )
                    )
                )
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowElevation = 4.dp
        ) {
            Text(
                text = if (isDefaultSlot) (if (isEnglish) "Slot 1 • Default" else "১ম স্থান • ডিফল্ট")
                       else (if (isEnglish) "Slot $targetSlotNumber" else "$targetSlotNumber নং স্থান"),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                ),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.5.dp)
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(2.dp)
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}
