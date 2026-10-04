package com.lekhani.android.ui.preferences

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lekhani.android.data.dictionary.LekhaniDictionaryManager
import com.lekhani.android.data.settings.KeyboardPreferences
import com.lekhani.android.feedback.LekhaniFeedbackManager

/**
 * Filter categories for organizing preferences cleanly.
 */
private enum class PrefCategory(val titleEn: String, val titleBn: String) {
    ALL("All", "সকল"),
    TYPING("Typing", "টাইপিং"),
    GESTURES("Gestures", "জেশ্চার"),
    DISPLAY("Display", "ডিসপ্লে"),
    SOUND("Sound & Haptics", "সাউন্ড ও কম্পন"),
    FORM_FACTOR("Modes", "কীবোর্ড মোড"),
    TOOLS("Tools & Privacy", "টুলস ও নিরাপত্তা")
}

/**
 * PreferencesTabContent
 * Unified Material 3 Preferences screen with live search and category pill navigation:
 * 1. App Theme Mode & Display Appearance
 * 2. General Typing, Numbers & Punctuation
 * 3. Gestures (Spacebar swipe, Backspace swipe-delete, Glide typing)
 * 4. Display & Keycap Customization (Fonts, Subscript hints, Popups)
 * 5. Sound & Haptic Vibration Feedback
 * 6. Form Factors & Sizing (One-handed, Floating, Split)
 * 7. Tools & Privacy (Dictionary, Typo learning, Zero-telemetry)
 */
@Composable
fun PreferencesTabContent(
    prefs: KeyboardPreferences,
    dictManager: LekhaniDictionaryManager,
    isEnglish: Boolean = false,
    onOpenToolbarCustomizer: () -> Unit,
    onOpenDictionaryManager: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenClipboard: (() -> Unit)? = null,
    onOpenLayoutsTab: (() -> Unit)? = null,
    onAppThemeModeChanged: ((KeyboardPreferences.AppThemeMode) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(PrefCategory.ALL) }

    var appThemeMode by remember { mutableStateOf(prefs.appThemeMode) }
    var formFactor by remember { mutableStateOf(prefs.formFactor) }
    var spacebarSwipeMode by remember { mutableStateOf(prefs.spacebarSwipeMode) }
    var volumeKeyCursorMode by remember { mutableStateOf(prefs.volumeKeyCursorMode) }
    var bottomRowKeyMode by remember { mutableStateOf(prefs.bottomRowKeyMode) }
    var swipeToDelete by remember { mutableStateOf(prefs.swipeToDeleteEnabled) }
    var glideTyping by remember { mutableStateOf(prefs.glideTypingEnabled) }
    var spacebarAutocomplete by remember { mutableStateOf(prefs.spacebarAutocompleteEnabled) }
    var autoLearnWords by remember { mutableStateOf(prefs.autoLearnWordsEnabled) }
    var doubleSpaceDari by remember { mutableStateOf(prefs.doubleSpaceDariEnabled) }
    var codeShield by remember { mutableStateOf(prefs.codeShieldEnabled) }
    var smartPunctuationSpacing by remember { mutableStateOf(prefs.smartPunctuationSpacing) }
    var showDedicatedNumberRow by remember { mutableStateOf(prefs.showDedicatedNumberRow) }
    var autoSwitchNumpad by remember { mutableStateOf(prefs.autoSwitchNumpad) }
    var showKeyPreviews by remember { mutableStateOf(prefs.showKeyPreviews) }
    var showLayoutNameOnSpacebar by remember { mutableStateOf(prefs.showLayoutNameOnSpacebar) }
    var keyGlowRipple by remember { mutableStateOf(prefs.keyGlowRippleEnabled) }
    var showKeyHints by remember { mutableStateOf(prefs.showKeyHints) }
    var swipeUpFlickEnabled by remember { mutableStateOf(prefs.swipeUpFlickEnabled) }

    var fontStyle by remember { mutableStateOf(prefs.fontStyle) }
    var fontScale by remember { mutableFloatStateOf(prefs.fontScale) }
    var clipboardRetention by remember { mutableIntStateOf(prefs.clipboardRetentionMinutes) }

    var hapticEnabled by remember { mutableStateOf(prefs.hapticEnabled) }
    var hapticDuration by remember { mutableFloatStateOf(prefs.hapticDurationMs.toFloat()) }

    var soundEnabled by remember { mutableStateOf(prefs.soundEnabled) }
    var soundVolume by remember { mutableFloatStateOf(prefs.soundVolume) }
    var activeSoundPack by remember { mutableStateOf(prefs.soundPack) }

    val context = LocalContext.current
    val view = LocalView.current
    val feedbackManager = remember { LekhaniFeedbackManager(context) }
    var testHapticCounter by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        onDispose {
            feedbackManager.release()
        }
    }

    val userWordsCount = remember { dictManager.getUserWords().size }

    val powerManager = remember { context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager }
    val isIgnoringBattery by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
            } else {
                true
            }
        )
    }

    // Helper search matching
    val query = searchQuery.trim()
    fun matchesSearch(vararg terms: String): Boolean {
        if (query.isBlank()) return true
        return terms.any { it.contains(query, ignoreCase = true) }
    }

    val showAppearance = (selectedCategory == PrefCategory.ALL || selectedCategory == PrefCategory.DISPLAY) &&
            matchesSearch("app appearance", "theme mode", "dark", "light", "system", "sync keyboard", "অ্যাপিয়ারেন্স", "থিম", "ডার্ক", "লাইট")

    val showTyping = (selectedCategory == PrefCategory.ALL || selectedCategory == PrefCategory.TYPING) &&
            matchesSearch("typing", "number row", "numpad", "spacing", "dari", "spacebar", "autocomplete", "learn", "glide", "code shield", "avro", "probaho", "টাইপিং", "সংখ্যা সারি", "নম্বর", "দাঁড়ি", "সাজেশন", "কোড শিল্ড")

    val showGestures = (selectedCategory == PrefCategory.ALL || selectedCategory == PrefCategory.GESTURES) &&
            matchesSearch("gestures", "swipe", "spacebar swipe", "cursor slide", "delete", "bottom row", "volume", "জেশ্চার", "সোয়াইপ", "স্পেসবার", "কার্সর", "মোছা", "ভলিউম")

    val showDisplay = (selectedCategory == PrefCategory.ALL || selectedCategory == PrefCategory.DISPLAY) &&
            matchesSearch("display", "font", "scale", "borders", "popups", "previews", "spacebar label", "hints", "subscripts", "flick", "ripple", "ডিসপ্লে", "ফন্ট", "স্কেল", "বর্ডার", "প্রিভিউ", "সংকেত", "সাবস্ক্রিপ্ট", "ফ্লিক")

    val showSound = (selectedCategory == PrefCategory.ALL || selectedCategory == PrefCategory.SOUND) &&
            matchesSearch("sound", "haptics", "vibration", "volume", "bubble", "mechanical", "system", "সাউন্ড", "ভাইব্রেশন", "কম্পন", "শব্দ", "ভলিউম")

    val showFormFactor = (selectedCategory == PrefCategory.ALL || selectedCategory == PrefCategory.FORM_FACTOR) &&
            matchesSearch("form factor", "modes", "standard", "one-handed", "floating", "split", "কীবোর্ড মোড", "সাধারণ", "একহাতে", "ভাসমান", "বিভক্ত")

    val showTools = (selectedCategory == PrefCategory.ALL || selectedCategory == PrefCategory.TOOLS) &&
            matchesSearch("tools", "vocabulary", "toolbar", "dictionary", "typo", "clipboard", "টুলবার", "শব্দকোষ", "ডিকশনারি", "ক্লিপবোর্ড")

    val showPrivacy = (selectedCategory == PrefCategory.ALL || selectedCategory == PrefCategory.TOOLS) &&
            matchesSearch("privacy", "battery", "offline", "about", "telemetry", "নিরাপত্তা", "ব্যাটারি", "অফলাইন", "পরিচিতি")

    val anyCardShown = showAppearance || showTyping || showGestures || showDisplay || showSound || showFormFactor || showTools || showPrivacy

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── Search & Filter Header ───────────────────────────────────────────
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    text = if (isEnglish) "Search settings..." else "সেটিংস খুঁজুন...",
                    fontSize = 13.5.sp
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = if (isEnglish) "Clear" else "মুছুন",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // ── Category Filter Pills ─────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PrefCategory.values().forEach { cat ->
                val isSelected = (selectedCategory == cat && searchQuery.isEmpty())
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        selectedCategory = cat
                        searchQuery = ""
                    },
                    label = {
                        Text(
                            text = if (isEnglish) cat.titleEn else cat.titleBn,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        // ── Empty Search State ────────────────────────────────────────────────
        if (!anyCardShown) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (isEnglish) "No settings matching \"$searchQuery\""
                               else "\"$searchQuery\" সম্পর্কিত কোনো সেটিংস পাওয়া যায়নি",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { searchQuery = ""; selectedCategory = PrefCategory.ALL }) {
                        Text(if (isEnglish) "Reset Search" else "অনুসন্ধান রিসেট করুন")
                    }
                }
            }
        }

        // ── 0. App Appearance Card ──────────────────────────────────────────
        if (showAppearance) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isEnglish) "App Appearance" else "অ্যাপ অ্যাপিয়ারেন্স",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = if (isEnglish) "Theme mode for Lekhani settings app" else "লেখনী সেটিংস অ্যাপের ইন্টারফেস থিম",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 2.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val modes = listOf(
                            Triple(KeyboardPreferences.AppThemeMode.SYSTEM, Icons.Filled.BrightnessAuto, if (isEnglish) "System" else "সিস্টেম"),
                            Triple(KeyboardPreferences.AppThemeMode.LIGHT, Icons.Filled.LightMode, if (isEnglish) "Light" else "লাইট"),
                            Triple(KeyboardPreferences.AppThemeMode.DARK, Icons.Filled.DarkMode, if (isEnglish) "Dark" else "ডার্ক"),
                            Triple(KeyboardPreferences.AppThemeMode.MATCH_KEYBOARD, Icons.Filled.Palette, if (isEnglish) "Sync Keyboard" else "কীবোর্ড সিঙ্ক"),
                        )
                        modes.forEach { (mode, icon, title) ->
                            val isSel = appThemeMode == mode
                            FilterChip(
                                selected = isSel,
                                onClick = {
                                    appThemeMode = mode
                                    prefs.appThemeMode = mode
                                    onAppThemeModeChanged?.invoke(mode)
                                },
                                leadingIcon = {
                                    Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
                                },
                                label = { Text(title, fontSize = 12.sp) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }
            }
        }

        // ── 1. Typing & Autocomplete Card ──────────────────────────────────
        if (showTyping) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isEnglish) "Typing & Autocomplete" else "টাইপিং ও স্বয়ংক্রিয় সাজেশন",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = if (isEnglish) "Suggestions, auto-learning, and typing assists" else "সাজেশন নির্বাচন, শব্দ শেখা ও টাইপিং সহায়ক নিয়ম",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 2.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )

                    // Layout Specific Options Callout
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = if (isEnglish) "Layout-Specific Options" else "লেআউটভিত্তিক অপশন",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = if (isEnglish) "Dedicated settings for Avro Phonetic & Lekhani প্রবাহ are located under the Layouts tab."
                                           else "অভ্র ফোনেটিক ও লেখনী প্রবাহের নির্দিষ্ট সেটিংস লেআউট ট্যাবে পাওয়া যাবে।",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (onOpenLayoutsTab != null) {
                                FilledTonalButton(
                                    onClick = onOpenLayoutsTab,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text(if (isEnglish) "View" else "দেখুন", fontSize = 11.5.sp)
                                }
                            }
                        }
                    }

                    // Dedicated Number Row Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = if (isEnglish) "Dedicated Number Row" else "স্বতন্ত্র সংখ্যা সারি",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = if (isEnglish) "Always show a top numeric row (1-0)" else "কীবোর্ডের উপরে সব সময় সংখ্যার সারি প্রদর্শন করবে",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = showDedicatedNumberRow,
                            onCheckedChange = {
                                showDedicatedNumberRow = it
                                prefs.showDedicatedNumberRow = it
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // Auto Switch Numpad
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = if (isEnglish) "Auto-Switch to Numeric Pad" else "স্বয়ংক্রিয় নম্বর প্যাড",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = if (isEnglish) "Switch to 3x4 PIN/number pad for phone and PIN fields" else "ফোন নম্বর বা পিন ফিল্ডে স্বয়ংক্রিয়ভাবে ৩x৪ নম্বর প্যাড চালু হবে",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoSwitchNumpad,
                            onCheckedChange = {
                                autoSwitchNumpad = it
                                prefs.autoSwitchNumpad = it
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // Smart Punctuation Spacing
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = if (isEnglish) "Smart Punctuation Spacing" else "যতিচিহ্নে স্বয়ংক্রিয় স্পেস",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = if (isEnglish) "Automatically insert a space after punctuation (, . । ? !)" else "যতিচিহ্ন টাইপ করার পর স্বয়ংক্রিয়ভাবে স্পেস যোগ হবে",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = smartPunctuationSpacing,
                            onCheckedChange = {
                                smartPunctuationSpacing = it
                                prefs.smartPunctuationSpacing = it
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // Double Space Dari
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = if (isEnglish) "Double Space Dari (।)" else "ডাবল স্পেসে দাঁড়ি (।)",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = if (isEnglish) "Double-tap spacebar to insert a Bengali Dari (।)" else "টানা দুইবার স্পেসবার চাপলে দাঁড়ি (।) যুক্ত হবে",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = doubleSpaceDari,
                            onCheckedChange = {
                                doubleSpaceDari = it
                                prefs.doubleSpaceDariEnabled = it
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // Spacebar Autocomplete
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = if (isEnglish) "Spacebar Autocomplete" else "স্পেসবারে স্বয়ংক্রিয় শব্দ নির্বাচন",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = if (isEnglish) "Pressing space commits the top highlighted suggestion" else "স্পেসবার চাপলে তালিকার প্রথম শব্দটি সরাসরি যুক্ত হবে",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = spacebarAutocomplete,
                            onCheckedChange = {
                                spacebarAutocomplete = it
                                prefs.spacebarAutocompleteEnabled = it
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // Auto-learn Words
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = if (isEnglish) "Auto-Learn Words" else "নতুন শব্দ শেখা",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = if (isEnglish) "Privately remember unique words typed frequently" else "নতুন ও ঘনঘন টাইপ করা শব্দ ব্যক্তিগত ডিকশনারিতে সংরক্ষিত হবে",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoLearnWords,
                            onCheckedChange = {
                                autoLearnWords = it
                                prefs.autoLearnWordsEnabled = it
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // Glide Typing
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = if (isEnglish) "Glide Typing (Swipe to Type)" else "গ্লাইড টাইপিং (আঙুল টেনে লেখা)",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = if (isEnglish) "Continuous gesture typing on English QWERTY" else "ইংরেজি কীবোর্ডে আঙুল টেনে নিরবচ্ছিন্ন শব্দ গঠন",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = glideTyping,
                            onCheckedChange = {
                                glideTyping = it
                                prefs.glideTypingEnabled = it
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // Code Shield
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = if (isEnglish) "Code Shield Mode" else "কোড শিল্ড মোড",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = if (isEnglish) "Prevent autocorrect inside URLs, variable names, and code syntax" else "কোড, ভেরিয়েবল বা ইউআরএল লেখার সময় স্বয়ংক্রিয় সংশোধন বন্ধ রাখবে",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = codeShield,
                            onCheckedChange = {
                                codeShield = it
                                prefs.codeShieldEnabled = it
                            }
                        )
                    }
                }
            }
        }

        // ── 2. Gestures & Navigation Card ──────────────────────────────────
        if (showGestures) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Swipe,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isEnglish) "Gestures & Navigation" else "জেশ্চার ও নেভিগেশন",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = if (isEnglish) "Spacebar swipe, erase gestures, and bottom row key" else "স্পেসবারে সোয়াইপ, মোছার অঙ্গভঙ্গি ও নিচের সারির বোতাম",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Spacebar Swipe Action
                    Text(
                        text = if (isEnglish) "Spacebar Swipe Action" else "স্পেসবারে সোয়াইপ মোড",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val swipeOptions = listOf(
                            Pair(
                                if (isEnglish) "Cursor Navigation" else "কার্সর সরানো",
                                KeyboardPreferences.SpacebarSwipeMode.CURSOR_NAV
                            ),
                            Pair(
                                if (isEnglish) "Switch Keyboard Layout" else "লেআউট পরিবর্তন",
                                KeyboardPreferences.SpacebarSwipeMode.LAYOUT_SWITCH
                            ),
                            Pair(
                                if (isEnglish) "Disabled (No swipe action)" else "বন্ধ (কোনো অ্যাকশন নেই)",
                                KeyboardPreferences.SpacebarSwipeMode.DISABLED
                            )
                        )
                        swipeOptions.forEach { pair ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        spacebarSwipeMode = pair.second
                                        prefs.spacebarSwipeMode = pair.second
                                    }
                                    .padding(vertical = 4.dp, horizontal = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = spacebarSwipeMode == pair.second,
                                    onClick = {
                                        spacebarSwipeMode = pair.second
                                        prefs.spacebarSwipeMode = pair.second
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = pair.first, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // Bottom Row Key Mode
                    Text(
                        text = if (isEnglish) "Bottom Row Key (Beside Space)" else "নিচের সারির বোতাম (স্পেসের বামে)",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val bottomKeyOptions = listOf(
                            Pair(
                                if (isEnglish) "Language / Layout Switcher" else "ভাষা / লেআউট সুইচার (🌐)",
                                KeyboardPreferences.BottomRowKeyMode.LANGUAGE_SWITCH
                            ),
                            Pair(
                                if (isEnglish) "Emoji Picker" else "ইমোজি প্যালেট (😊)",
                                KeyboardPreferences.BottomRowKeyMode.EMOJI
                            ),
                            Pair(
                                if (isEnglish) "Smart (Automatic / Comma)" else "স্মার্ট / স্বয়ংক্রিয় (,) ",
                                KeyboardPreferences.BottomRowKeyMode.SMART
                            )
                        )
                        bottomKeyOptions.forEach { pair ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        bottomRowKeyMode = pair.second
                                        prefs.bottomRowKeyMode = pair.second
                                    }
                                    .padding(vertical = 4.dp, horizontal = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = bottomRowKeyMode == pair.second,
                                    onClick = {
                                        bottomRowKeyMode = pair.second
                                        prefs.bottomRowKeyMode = pair.second
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = pair.first, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // Swipe to Delete
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = if (isEnglish) "Swipe to Delete" else "সোয়াইপ করে মুছুন",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = if (isEnglish) "Slide left from backspace to quickly erase words" else "ব্যাকস্পেস থেকে বামে টেনে দ্রুত শব্দগুলো মুছে ফেলুন",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = swipeToDelete,
                            onCheckedChange = {
                                swipeToDelete = it
                                prefs.swipeToDeleteEnabled = it
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // Volume Key Cursor
                    Text(
                        text = if (isEnglish) "Volume Key Cursor Navigation" else "ভলিউম কী দিয়ে কার্সর মুভ",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val volOptions = listOf(
                            Pair(
                                if (isEnglish) "Disabled" else "বন্ধ",
                                KeyboardPreferences.VolumeKeyCursorMode.DISABLED
                            ),
                            Pair(
                                if (isEnglish) "Up = Left, Down = Right" else "ভলিউম আপ = বামে, ডাউন = ডানে",
                                KeyboardPreferences.VolumeKeyCursorMode.UP_LEFT_DOWN_RIGHT
                            ),
                            Pair(
                                if (isEnglish) "Up = Right, Down = Left" else "ভলিউম আপ = ডানে, ডাউন = বামে",
                                KeyboardPreferences.VolumeKeyCursorMode.UP_RIGHT_DOWN_LEFT
                            )
                        )
                        volOptions.forEach { pair ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        volumeKeyCursorMode = pair.second
                                        prefs.volumeKeyCursorMode = pair.second
                                    }
                                    .padding(vertical = 4.dp, horizontal = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = volumeKeyCursorMode == pair.second,
                                    onClick = {
                                        volumeKeyCursorMode = pair.second
                                        prefs.volumeKeyCursorMode = pair.second
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = pair.first, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                            }
                        }
                    }
                }
            }
        }

        // ── 3. Display & Keys Card ─────────────────────────────────────────
        if (showDisplay) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isEnglish) "Display & Keys" else "ডিসপ্লে ও কী বিন্যাস",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = if (isEnglish) "Typography, key borders, popups, and flick gestures" else "ফন্ট স্টাইল, কী বর্ডার, প্রিভিউ বাবল ও ফ্লিক সংকেত",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Font Style
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = if (isEnglish) "Font Style" else "কী ফন্ট স্টাইল",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val fonts = listOf(
                                Pair(KeyboardPreferences.FONT_SYSTEM, if (isEnglish) "System" else "সিস্টেম"),
                                Pair(KeyboardPreferences.FONT_SANS_SERIF, "Sans"),
                                Pair(KeyboardPreferences.FONT_SERIF, "Serif"),
                                Pair(KeyboardPreferences.FONT_MONOSPACE, "Mono")
                            )
                            fonts.forEach { (style, label) ->
                                FilterChip(
                                    selected = fontStyle == style,
                                    onClick = {
                                        fontStyle = style
                                        prefs.fontStyle = style
                                    },
                                    label = { Text(label, fontSize = 12.sp) },
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }

                    // Font Scale
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isEnglish) "Key Font Size" else "ফন্ট সাইজ স্কেল",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = "${(fontScale * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = fontScale,
                            onValueChange = {
                                fontScale = it
                                prefs.fontScale = it
                            },
                            valueRange = 0.8f..1.3f,
                            steps = 4
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // Key Borders
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = if (isEnglish) "Key Borders" else "কী বর্ডার",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = if (isEnglish) "Show outlines around individual keys" else "প্রতিটি কী-এর চারপাশে বর্ডার আউটলাইন দেখাবে",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = prefs.showKeyBorders,
                            onCheckedChange = { prefs.showKeyBorders = it }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // Key Previews (Popups)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = if (isEnglish) "Key Press Popups" else "কী প্রেস প্রিভিউ বাবল",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = if (isEnglish) "Show popup balloon above pressed key" else "কী চাপলে উপরে পপআপ বাবল প্রদর্শন করবে",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = showKeyPreviews,
                            onCheckedChange = {
                                showKeyPreviews = it
                                prefs.showKeyPreviews = it
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // Key Subscripts (Hints)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = if (isEnglish) "Key Hints (Subscripts)" else "কি-এর উপরের সংকেত (সাবস্ক্রিপ্ট)",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = if (isEnglish) "Show secondary / shifted characters in the top corner of keys"
                                       else "কি-এর কোণায় শিফট বা ফ্লিক করে টাইপযোগ্য বর্ণ সংকেত দেখাবে",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = showKeyHints,
                            onCheckedChange = {
                                showKeyHints = it
                                prefs.showKeyHints = it
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // Swipe Up Flick
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = if (isEnglish) "Swipe-Up Flick for Shift" else "উপরে সোয়াইপ করে শিফট টাইপ",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = if (isEnglish) "Flick upward on any key to type its secondary shifted letter without pressing Shift"
                                       else "শিফট না চেপেই দ্রুত উপরে সোয়াইপ করে মহাপ্রাণ বর্ণ টাইপ করুন",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = swipeUpFlickEnabled,
                            onCheckedChange = {
                                swipeUpFlickEnabled = it
                                prefs.swipeUpFlickEnabled = it
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // Show Layout Name on Spacebar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = if (isEnglish) "Layout Name on Spacebar" else "স্পেসবারে লেআউটের নাম",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = if (isEnglish) "Show current layout label on spacebar" else "স্পেসবারের উপর বর্তমান সক্রিয় লেআউটের নাম দেখাবে",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = showLayoutNameOnSpacebar,
                            onCheckedChange = {
                                showLayoutNameOnSpacebar = it
                                prefs.showLayoutNameOnSpacebar = it
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // Key Glow Ripple
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = if (isEnglish) "Touch Glow Ripple" else "টাচ গ্লো রিপল",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = if (isEnglish) "Dynamic radiant glow on tapped keys" else "কী চাপলে মসৃণ রঙের তরঙ্গ অ্যানিমেশন",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = keyGlowRipple,
                            onCheckedChange = {
                                keyGlowRipple = it
                                prefs.keyGlowRippleEnabled = it
                            }
                        )
                    }
                }
            }
        }

        // ── 4. Sound & Haptics Card ─────────────────────────────────────────
        if (showSound) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Vibration,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isEnglish) "Sound & Haptics" else "সাউন্ড ও কম্পন",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = if (isEnglish) "Vibration strength, sound packs, and audio volume" else "কী চাপলে ভাইব্রেশন মাত্রা ও সাউন্ড এফেক্ট",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Vibration Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = if (isEnglish) "Haptic Vibration" else "ভাইব্রেশন (হ্যাপটিক ফিডব্যাক)",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = if (isEnglish) "Tactile vibration tick on keypress" else "কী চাপলে সূক্ষ্ম স্পন্দন অনুভব করুন",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = hapticEnabled,
                            onCheckedChange = {
                                hapticEnabled = it
                                prefs.hapticEnabled = it
                            }
                        )
                    }

                    if (hapticEnabled) {
                        val strengthLabel = when (hapticDuration.toInt()) {
                            in 5..15  -> if (isEnglish) "Light"  else "হালকা"
                            in 16..35 -> if (isEnglish) "Medium" else "মাঝারি"
                            else      -> if (isEnglish) "Strong" else "জোরালো"
                        }
                        Text(
                            text = if (isEnglish) "Vibration strength: $strengthLabel (${hapticDuration.toInt()} ms)"
                                   else "ভাইব্রেশনের মাত্রা: $strengthLabel (${hapticDuration.toInt()} ms)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        Slider(
                            value = hapticDuration,
                            onValueChange = {
                                hapticDuration = it
                                prefs.hapticDurationMs = it.toInt()
                            },
                            onValueChangeFinished = {
                                feedbackManager.onKeyFeedback(view)
                            },
                            valueRange = 5f..80f,
                            steps = 14
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // Sound Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = if (isEnglish) "Keypress Sound" else "কী চাপলে শব্দ",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = if (isEnglish) "Play audio feedback click on typing" else "টাইপিংয়ের সময় অডিও ক্লিক সাউন্ড",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = soundEnabled,
                            onCheckedChange = {
                                soundEnabled = it
                                prefs.soundEnabled = it
                            }
                        )
                    }

                    if (soundEnabled) {
                        // Sound Pack Selector
                        Text(
                            text = if (isEnglish) "Sound Pack" else "সাউন্ড প্যাক",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val packs = listOf(
                                Pair(KeyboardPreferences.SOUND_SYSTEM, if (isEnglish) "System" else "সিস্টেম"),
                                Pair(KeyboardPreferences.SOUND_BUBBLE, if (isEnglish) "Bubble" else "বাবল"),
                                Pair(KeyboardPreferences.SOUND_MECHANICAL, if (isEnglish) "Mechanical" else "মেকানিক্যাল")
                            )
                            packs.forEach { (pack, label) ->
                                FilterChip(
                                    selected = activeSoundPack == pack,
                                    onClick = {
                                        activeSoundPack = pack
                                        prefs.soundPack = pack
                                        feedbackManager.updateCache()
                                        feedbackManager.onKeyFeedback(view)
                                    },
                                    label = { Text(label, fontSize = 12.sp) },
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }

                        // Sound Volume Slider
                        Text(
                            text = if (isEnglish) "Sound Volume: ${(soundVolume * 100).toInt()}%"
                                   else "শব্দের মাত্রা: ${(soundVolume * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        Slider(
                            value = soundVolume,
                            onValueChange = {
                                soundVolume = it
                                prefs.soundVolume = it
                            },
                            onValueChangeFinished = {
                                feedbackManager.updateCache()
                                feedbackManager.onKeyFeedback(view)
                            },
                            valueRange = 0.1f..1.0f,
                            steps = 8
                        )
                    }
                }
            }
        }

        // ── 5. Form Factor Card ────────────────────────────────────────────
        if (showFormFactor) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Smartphone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isEnglish) "Form Factor & Modes" else "কীবোর্ড মোড ও অবস্থান",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = if (isEnglish) "Keyboard sizing and ergonomics" else "কীবোর্ডের আকার ও একহাতে ব্যবহারের বিন্যাস",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    FormFactorPrefOption(
                        title = if (isEnglish) "Standard" else "সাধারণ (Standard)",
                        subtitle = if (isEnglish) "Standard full-width keyboard" else "ডিফল্ট পূর্ণ প্রস্থ কীবোর্ড মোড",
                        icon = Icons.Filled.Smartphone,
                        selected = formFactor == KeyboardPreferences.FormFactor.STANDARD,
                        onSelect = {
                            formFactor = KeyboardPreferences.FormFactor.STANDARD
                            prefs.formFactor = KeyboardPreferences.FormFactor.STANDARD
                        }
                    )
                    FormFactorPrefOption(
                        title = if (isEnglish) "One-Handed Mode" else "একহাতে ব্যবহারের মোড (One-Handed)",
                        subtitle = if (isEnglish) "Docked keyboard with quick side dock controls" else "সহজে এক আঙুলে টাইপ করার সংকুচিত মোড",
                        icon = Icons.Filled.Smartphone,
                        selected = formFactor == KeyboardPreferences.FormFactor.ONE_HANDED_RIGHT || formFactor == KeyboardPreferences.FormFactor.ONE_HANDED_LEFT,
                        onSelect = {
                            formFactor = KeyboardPreferences.FormFactor.ONE_HANDED_RIGHT
                            prefs.formFactor = KeyboardPreferences.FormFactor.ONE_HANDED_RIGHT
                        }
                    )
                    FormFactorPrefOption(
                        title = if (isEnglish) "Floating Keyboard" else "ভাসমান কীবোর্ড (Floating)",
                        subtitle = if (isEnglish) "Draggable keyboard overlay" else "পর্দার যেকোনো জায়গায় সরিয়ে ব্যবহারযোগ্য কীবোর্ড",
                        icon = Icons.Filled.PictureInPictureAlt,
                        selected = formFactor == KeyboardPreferences.FormFactor.FLOATING,
                        onSelect = {
                            formFactor = KeyboardPreferences.FormFactor.FLOATING
                            prefs.formFactor = KeyboardPreferences.FormFactor.FLOATING
                        }
                    )
                    FormFactorPrefOption(
                        title = if (isEnglish) "Split Keyboard" else "বিভক্ত কীবোর্ড (Split)",
                        subtitle = if (isEnglish) "Ergonomic two-thumb split mode for tablets" else "ট্যাবলেট ও বড় স্ক্রিনে দুই হাতের আঙুলচালনা মোড",
                        icon = Icons.Filled.VerticalSplit,
                        selected = formFactor == KeyboardPreferences.FormFactor.SPLIT,
                        onSelect = {
                            formFactor = KeyboardPreferences.FormFactor.SPLIT
                            prefs.formFactor = KeyboardPreferences.FormFactor.SPLIT
                        }
                    )
                }
            }
        }

        // ── 6. Tools & Vocabulary Card ─────────────────────────────────────
        if (showTools) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Book,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isEnglish) "Tools & Vocabulary" else "টুলস ও শব্দকোষ",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = if (isEnglish) "Candidate strip toolbar and custom dictionary" else "টুলবার বিন্যাস ও ব্যক্তিগত শব্দভাণ্ডার",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Toolbar customizer button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = if (isEnglish) "Customize Strip Toolbar" else "টুলবার বিন্যাস সাজান",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = if (isEnglish) "Rearrange shortcuts in the keyboard candidate strip" else "কীবোর্ডের উপরের স্ট্রিপে আপনার পছন্দের টুলগুলো সাজান",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(
                            onClick = onOpenToolbarCustomizer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (isEnglish) "Customize" else "সাজান")
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // User Dictionary Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = if (isEnglish) "Personal Dictionary ($userWordsCount)" else "ব্যক্তিগত ডিকশনারি ($userWordsCount)",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = if (isEnglish) "Manage your learned and custom added words" else "আপনার সংরক্ষিত ও টাইপ করা শব্দসমূহ ব্যবস্থাপনা করুন",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(
                            onClick = onOpenDictionaryManager,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (isEnglish) "Manage" else "ব্যবস্থাপনা")
                        }
                    }
                }
            }
        }

        // ── 7. Privacy & About Card ─────────────────────────────────────────
        if (showPrivacy) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isEnglish) "Privacy & Security" else "নিরাপত্তা ও গোপনীয়তা",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = if (isEnglish) "100% on-device guarantee and system permissions" else "১০০% অফলাইন নিশ্চয়তা ও অ্যাপ সম্পর্কিত তথ্য",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = if (isEnglish) "Zero Telemetry Guarantee" else "শূন্য টেলিমেট্রি নিশ্চয়তা",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = if (isEnglish) "All AI models, grammar logic, and keystrokes execute 100% locally on-device" else "লেখনী কীবোর্ডে টাইপ করা কোনো তথ্য বা শব্দ কখনো ইন্টারনেটে পাঠানো হয় না",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = if (isEnglish) "About Lekhani Keyboard" else "লেখনী কীবোর্ড পরিচিতি",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = if (isEnglish) "Version, license, and architectural details" else "অ্যাপ সংস্করণ, লাইসেন্স ও ইঞ্জিন তথ্য",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = onOpenAbout,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (isEnglish) "About" else "পরিচিতি")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun FormFactorPrefOption(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        RadioButton(
            selected = selected,
            onClick = onSelect
        )
    }
}
