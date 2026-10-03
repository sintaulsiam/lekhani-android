package com.lekhani.android.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.lekhani.android.ui.layoutflow.LayoutFlowScreen
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.text.style.TextOverflow
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

    private val requestedTabState = mutableStateOf(0)

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
        requestedTabState.value = startTab

        setContent {
            val context = LocalContext.current
            val currentTab by requestedTabState
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
                        initialTab = currentTab,
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
                            window.decorView.post {
                                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                                try {
                                    imm?.showInputMethodPicker()
                                } catch (_: Exception) {
                                    startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val tab = if (intent.getBooleanExtra(EXTRA_OPEN_CLIPBOARD, false)) {
            3
        } else {
            intent.getIntExtra(EXTRA_TAB_INDEX, -1).takeIf { it >= 0 }
        }
        if (tab != null) {
            requestedTabState.value = tab
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
    val configuration = LocalConfiguration.current
    val isCompactHeight = configuration.screenHeightDp < 540
    val isCompactWidth = configuration.screenWidthDp < 360
    var selectedTab by remember { mutableIntStateOf(initialTab) }

    LaunchedEffect(initialTab) {
        selectedTab = initialTab
    }

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
    var showLayoutFlowScreen by remember { mutableStateOf(false) }

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

    if (showLayoutFlowScreen) {
        var flowEnabledLayouts by remember(showLayoutFlowScreen) {
            mutableStateOf(LayoutRegistry.parseEnabledLayouts(prefs.getString(LayoutRegistry.PREF_ENABLED_LAYOUTS, null)))
        }
        var flowActiveLayout by remember(showLayoutFlowScreen) {
            val name = prefs.getString(LayoutRegistry.PREF_ACTIVE_LAYOUT, null)
            mutableStateOf(
                try {
                    if (name != null) LekhaniLayoutType.valueOf(name) else LayoutRegistry.DEFAULT_ACTIVE_LAYOUT
                } catch (_: Exception) {
                    LayoutRegistry.DEFAULT_ACTIVE_LAYOUT
                }
            )
        }

        // Live synchronization: updates active layout when user swipes spacebar in IME
        DisposableEffect(showLayoutFlowScreen) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
                if (key == LayoutRegistry.PREF_ACTIVE_LAYOUT) {
                    val name = sp.getString(key, null)
                    val updated = try {
                        if (name != null) LekhaniLayoutType.valueOf(name) else LayoutRegistry.DEFAULT_ACTIVE_LAYOUT
                    } catch (_: Exception) {
                        LayoutRegistry.DEFAULT_ACTIVE_LAYOUT
                    }
                    if (flowActiveLayout != updated) {
                        flowActiveLayout = updated
                    }
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            onDispose {
                prefs.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }

        LayoutFlowScreen(
            enabledLayouts = flowEnabledLayouts,
            activeLayout = flowActiveLayout,
            isEnglish = isEnglish,
            onLayoutsReordered = { newOrder ->
                flowEnabledLayouts = newOrder
                prefs.edit().putString(
                    LayoutRegistry.PREF_ENABLED_LAYOUTS,
                    LayoutRegistry.serializeEnabledLayouts(newOrder)
                ).apply()
            },
            onActiveLayoutChanged = { newActive ->
                flowActiveLayout = newActive
                prefs.edit().putString(LayoutRegistry.PREF_ACTIVE_LAYOUT, newActive.name).apply()
            },
            onResetToDefault = {
                flowEnabledLayouts = LayoutRegistry.DEFAULT_ENABLED_LAYOUTS
                flowActiveLayout = LayoutRegistry.DEFAULT_ACTIVE_LAYOUT
                prefs.edit().putString(
                    LayoutRegistry.PREF_ENABLED_LAYOUTS,
                    LayoutRegistry.serializeEnabledLayouts(LayoutRegistry.DEFAULT_ENABLED_LAYOUTS)
                ).putString(
                    LayoutRegistry.PREF_ACTIVE_LAYOUT,
                    LayoutRegistry.DEFAULT_ACTIVE_LAYOUT.name
                ).apply()
            },
            onBack = { showLayoutFlowScreen = false }
        )
    } else {
    Scaffold(
        topBar = {
            Column {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = if (isCompactWidth) 10.dp else 16.dp,
                                vertical = if (isCompactHeight) 4.dp else 10.dp
                            ),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LekhaniBrandLogo(
                                size = if (isCompactHeight || isCompactWidth) 26.dp else 32.dp,
                                shapeCornerPercent = 25
                            )
                            Spacer(modifier = Modifier.width(if (isCompactWidth) 6.dp else 10.dp))
                            Text(
                                text = if (isEnglish) "Lekhani Keyboard" else "লেখনী কীবোর্ড",
                                style = (if (isCompactHeight || isCompactWidth) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium)
                                    .copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Bilingual Language Switcher Segment
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(if (isCompactWidth) 2.dp else 3.dp),
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
                                            .padding(
                                                horizontal = if (isCompactWidth) 7.dp else 10.dp,
                                                vertical = if (isCompactHeight) 2.dp else 4.dp
                                            )
                                    ) {
                                        Text(
                                            text = "বাংলা",
                                            fontSize = if (isCompactWidth) 11.sp else 12.sp,
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
                                            .padding(
                                                horizontal = if (isCompactWidth) 7.dp else 10.dp,
                                                vertical = if (isCompactHeight) 2.dp else 4.dp
                                            )
                                    ) {
                                        Text(
                                            text = "EN",
                                            fontSize = if (isCompactWidth) 11.sp else 12.sp,
                                            fontWeight = if (isEnglish) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isEnglish) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(if (isCompactWidth) 4.dp else 6.dp))

                            // Quick About & Privacy Dialog Trigger
                            IconButton(
                                onClick = { showAboutDialog = true },
                                modifier = Modifier.size(if (isCompactHeight || isCompactWidth) 36.dp else 48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = if (isEnglish) "About & Privacy" else "অ্যাপ সম্পর্কিত তথ্য",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(if (isCompactHeight || isCompactWidth) 20.dp else 24.dp)
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    thickness = 1.dp
                )
            }
        },
        bottomBar = {
            Column {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    thickness = 1.dp
                )
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    modifier = if (isCompactHeight) Modifier.height(54.dp) else Modifier
                ) {
                    navItems.forEachIndexed { index, (label, icon, _) ->
                        NavigationBarItem(
                            selected = (selectedTab == index),
                            onClick = { selectedTab = index },
                            icon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    modifier = Modifier.size(if (isCompactHeight) 18.dp else 22.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = if (isCompactHeight) 9.5.sp else 11.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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
                    isEnglish = isEnglish,
                    onOpenImeSettings = onOpenImeSettings,
                    onOpenImePicker = onOpenImePicker,
                    onOpenLayoutFlow = { showLayoutFlowScreen = true }
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
                    onOpenAbout = { showAboutDialog = true },
                    onOpenClipboard = { selectedTab = 3 },
                    onAppThemeModeChanged = onAppThemeModeChanged
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
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding(),
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
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding(),
                color = MaterialTheme.colorScheme.background
            ) {
                ToolbarCustomizationSheet(
                    prefs = keyboardPrefs,
                    isEnglish = isEnglish,
                    onClose = { showToolbarSheet = false }
                )
            }
        }
    }

    if (showDictionarySheet) {
        Dialog(
            onDismissRequest = { showDictionarySheet = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding(),
                color = MaterialTheme.colorScheme.background
            ) {
                DictionaryManagementSheet(
                    dictManager = dictManager,
                    isEnglish = isEnglish,
                    onClose = { showDictionarySheet = false }
                )
            }
        }
    }
    }
}

@Composable
private fun LayoutsTabContent(
    context: Context,
    prefs: android.content.SharedPreferences,
    isEnglish: Boolean = false,
    onOpenImeSettings: () -> Unit,
    onOpenImePicker: () -> Unit,
    onOpenLayoutFlow: () -> Unit,
) {
    val scrollState = rememberScrollState()

    var layoutPrefVersion by remember { mutableIntStateOf(0) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == LayoutRegistry.PREF_ENABLED_LAYOUTS || key == LayoutRegistry.PREF_ACTIVE_LAYOUT) {
                layoutPrefVersion++
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    var orderedLayouts by remember(layoutPrefVersion) {
        val saved = LayoutRegistry.parseEnabledLayouts(prefs.getString(LayoutRegistry.PREF_ENABLED_LAYOUTS, null))
        val missing = LayoutRegistry.all.filter { !saved.contains(it) }
        mutableStateOf(saved + missing)
    }

    var enabledLayouts by remember(layoutPrefVersion) {
        mutableStateOf(
            LayoutRegistry.parseEnabledLayouts(prefs.getString(LayoutRegistry.PREF_ENABLED_LAYOUTS, null)).toSet()
        )
    }

    val activeLayout = remember(layoutPrefVersion) {
        val name = prefs.getString(LayoutRegistry.PREF_ACTIVE_LAYOUT, null)
        try {
            if (name != null) LekhaniLayoutType.valueOf(name) else LayoutRegistry.DEFAULT_ACTIVE_LAYOUT
        } catch (_: Exception) {
            LayoutRegistry.DEFAULT_ACTIVE_LAYOUT
        }
    }

    fun persistLayouts(newOrder: List<LekhaniLayoutType>, newEnabled: Set<LekhaniLayoutType>) {
        orderedLayouts = newOrder
        enabledLayouts = newEnabled
        val enabledOrdered = newOrder.filter { newEnabled.contains(it) }
        prefs.edit().putString(
            LayoutRegistry.PREF_ENABLED_LAYOUTS,
            LayoutRegistry.serializeEnabledLayouts(enabledOrdered)
        ).apply()
        if (!newEnabled.contains(activeLayout)) {
            enabledOrdered.firstOrNull()?.let { primary ->
                prefs.edit().putString(LayoutRegistry.PREF_ACTIVE_LAYOUT, primary.name).apply()
            }
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

    fun resetToDefaultLayoutOrder() {
        persistLayouts(LayoutRegistry.DEFAULT_ENABLED_LAYOUTS, LayoutRegistry.DEFAULT_ENABLED_LAYOUTS.toSet())
        prefs.edit().putString(LayoutRegistry.PREF_ACTIVE_LAYOUT, LayoutRegistry.DEFAULT_ACTIVE_LAYOUT.name).apply()
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
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
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
                                       else if (!isEnabled) (if (isEnglish) "Enable Step 1 first" else "প্রথমে ধাপ ১ চালু করুন")
                                       else (if (isEnglish) "Tap to select Lekhani" else "ডিফল্ট কীবোর্ড হিসেবে বেছে নিন"),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (isDefault) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                        if (!isDefault) {
                            FilledTonalButton(
                                onClick = {
                                    if (isEnabled) {
                                        onOpenImePicker()
                                    } else {
                                        onOpenImeSettings()
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(if (isEnglish) "Select" else "নির্বাচন")
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (!isEnabled && Build.VERSION.SDK_INT >= 33) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (isEnglish)
                                        "If Lekhani is grayed out in settings, go to App Info ➔ tap ⋮ (top-right) ➔ 'Allow restricted settings'."
                                    else
                                        "লেখনী ধূসর থাকলে, অ্যাপ ইনফো ➔ ⋮ (উপরে ডানে) ➔ 'Allow restricted settings' চালু করুন।",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
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
                val haptic = LocalHapticFeedback.current

                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                                imageVector = Icons.Filled.Keyboard,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isEnglish) "Keyboard Layouts" else "কীবোর্ড লেআউট",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isEnglish) "Spacebar swipe sequence"
                                       else "স্পেসবারে সোয়াইপ ক্রম",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    FilledTonalButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onOpenLayoutFlow()
                        },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isEnglish) "Edit Flow" else "ফ্লো সাজান",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )

                orderedLayouts.forEachIndexed { index, type ->
                    key(type) {
                        val isChecked = enabledLayouts.contains(type)
                        val isHome = isChecked && (type == activeLayout)
                        val title = if (isEnglish) LayoutRegistry.getEnglishName(type) else LayoutRegistry.getBengaliName(type)
                        val desc = LayoutRegistry.getDescription(type, isEnglish)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp, horizontal = 4.dp),
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
                                    if (isHome) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = if (isEnglish) "Active" else "সক্রিয়",
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

                            Switch(
                                checked = isChecked,
                                enabled = !isChecked || enabledLayouts.size > 1,
                                onCheckedChange = { toggleLayout(type, it) }
                            )
                        }

                        if (index < orderedLayouts.lastIndex) {
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
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
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    if (testText.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilledTonalButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                    cm?.setPrimaryClip(android.content.ClipData.newPlainText("Lekhani Text", testText))
                                    android.widget.Toast.makeText(
                                        context,
                                        if (isEnglish) "Copied to clipboard" else "ক্লিপবোর্ডে কপি করা হয়েছে",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isEnglish) "Copy" else "কপি", fontSize = 11.sp)
                            }
                            FilledTonalButton(
                                onClick = { testText = "" },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear", modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isEnglish) "Clear" else "মুছুন", fontSize = 11.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = testText,
                    onValueChange = { testText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(if (isEnglish) "Type here to test..." else "এখানে লিখে পরীক্ষা করুন...") },
                    shape = RoundedCornerShape(10.dp)
                )

                if (testText.isNotEmpty()) {
                    val charCount = testText.length
                    val wordCount = if (testText.isBlank()) 0 else testText.trim().split(Regex("\\s+")).size
                    Text(
                        text = if (isEnglish) "$charCount characters • $wordCount words"
                               else "${charCount}টি অক্ষর • ${wordCount}টি শব্দ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 6.dp, start = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}


/**
 * LekhaniAppTheme
 * Independent Material 3 Expressive theme engine for Lekhani Settings & Studio.
 * Completely decoupled from keyboard canvas colors to guarantee WCAG AAA contrast,
 * clean typography, and zero layout visual breakage across all custom & extreme themes.
 */
@Composable
fun LekhaniAppTheme(
    theme: KeyboardTheme? = null,
    appThemeMode: KeyboardPreferences.AppThemeMode = KeyboardPreferences.AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isSystemDark = (context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES

    val shouldUseDark = when (appThemeMode) {
        KeyboardPreferences.AppThemeMode.LIGHT -> false
        KeyboardPreferences.AppThemeMode.DARK -> true
        KeyboardPreferences.AppThemeMode.SYSTEM,
        KeyboardPreferences.AppThemeMode.DYNAMIC -> isSystemDark
        KeyboardPreferences.AppThemeMode.MATCH_KEYBOARD -> theme?.isDark ?: isSystemDark
    }

    val colorScheme = remember(appThemeMode, isSystemDark, theme, shouldUseDark) {
        if (appThemeMode == KeyboardPreferences.AppThemeMode.MATCH_KEYBOARD && theme != null) {
            val primaryColor = Color(theme.accentColor)
            val isAccentLight = androidx.core.graphics.ColorUtils.calculateLuminance(theme.accentColor) > 0.45
            val onPrimaryColor = if (isAccentLight) Color(0xFF001F18) else Color.White

            if (shouldUseDark) {
                darkColorScheme(
                    primary = primaryColor,
                    onPrimary = onPrimaryColor,
                    primaryContainer = primaryColor.copy(alpha = 0.20f),
                    onPrimaryContainer = if (isAccentLight) primaryColor else Color(0xFFF1F5F9),
                    secondary = Color(theme.keyShiftColor),
                    onSecondary = Color(theme.labelColor),
                    secondaryContainer = Color(theme.keyNormalColor),
                    onSecondaryContainer = Color(theme.labelColor),
                    background = Color(0xFF090B0E),
                    onBackground = Color(0xFFF1F5F9),
                    surface = Color(0xFF111418),
                    onSurface = Color(0xFFF1F5F9),
                    surfaceVariant = Color(0xFF181C22),
                    onSurfaceVariant = Color(0xFF94A3B8),
                    outline = Color(theme.keyBorderColor).copy(alpha = 0.5f),
                    outlineVariant = Color(0xFF262D38)
                )
            } else {
                lightColorScheme(
                    primary = primaryColor,
                    onPrimary = onPrimaryColor,
                    primaryContainer = primaryColor.copy(alpha = 0.14f),
                    onPrimaryContainer = if (isAccentLight) Color(0xFF0F172A) else primaryColor,
                    secondary = Color(0xFF0284C7),
                    onSecondary = Color.White,
                    secondaryContainer = Color(0xFFE0F2FE),
                    onSecondaryContainer = Color(0xFF034466),
                    surface = Color(0xFFFFFFFF),
                    onSurface = Color(0xFF0F172A),
                    background = Color(0xFFFFFFFF),
                    onBackground = Color(0xFF0F172A),
                    surfaceVariant = Color(0xFFF1F5F9),
                    onSurfaceVariant = Color(0xFF475569),
                    outline = Color(0xFFE2E8F0),
                    outlineVariant = Color(0xFFEEF2F6)
                )
            }
        } else if (appThemeMode == KeyboardPreferences.AppThemeMode.DYNAMIC
            && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ wallpaper dynamic Material You colors
            if (shouldUseDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else if (shouldUseDark) {
            // Ultra-Aesthetic Modern Obsidian & Slate Dark Theme
            darkColorScheme(
                primary = Color(0xFF00F0B5),
                onPrimary = Color(0xFF002A1F),
                primaryContainer = Color(0xFF0F362A),
                onPrimaryContainer = Color(0xFF65FFD2),
                secondary = Color(0xFF38BDF8),
                onSecondary = Color(0xFF002235),
                secondaryContainer = Color(0xFF152A38),
                onSecondaryContainer = Color(0xFF7DD3FC),
                tertiary = Color(0xFFA78BFA),
                onTertiary = Color(0xFF27134A),
                background = Color(0xFF090B0E),
                onBackground = Color(0xFFF1F5F9),
                surface = Color(0xFF111418),
                onSurface = Color(0xFFF1F5F9),
                surfaceVariant = Color(0xFF181C22),
                onSurfaceVariant = Color(0xFF94A3B8),
                outline = Color(0xFF262D38),
                outlineVariant = Color(0xFF1B2028)
            )
        } else {
            // Pure White Canvas & Radiant Emerald Light Theme
            lightColorScheme(
                primary = Color(0xFF007A55),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFE6F7F0),
                onPrimaryContainer = Color(0xFF003828),
                secondary = Color(0xFF0284C7),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFE0F2FE),
                onSecondaryContainer = Color(0xFF034466),
                tertiary = Color(0xFF7C3AED),
                onTertiary = Color.White,
                tertiaryContainer = Color(0xFFEDE9FE),
                onTertiaryContainer = Color(0xFF3B0764),
                background = Color(0xFFFFFFFF),
                onBackground = Color(0xFF0F172A),
                surface = Color(0xFFFFFFFF),
                onSurface = Color(0xFF0F172A),
                surfaceVariant = Color(0xFFF1F5F9),
                onSurfaceVariant = Color(0xFF475569),
                outline = Color(0xFFE2E8F0),
                outlineVariant = Color(0xFFEEF2F6)
            )
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
