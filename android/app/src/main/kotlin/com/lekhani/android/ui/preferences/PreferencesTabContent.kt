package com.lekhani.android.ui.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
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
import androidx.compose.ui.graphics.Color
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
    var candidateStripEnabled by remember { mutableStateOf(prefs.candidateStripEnabled) }
    var autocorrectEnabled by remember { mutableStateOf(prefs.autocorrectEnabled) }
    var nextWordPredictionEnabled by remember { mutableStateOf(prefs.nextWordPredictionEnabled) }
    var autoSwitchNumpad by remember { mutableStateOf(prefs.autoSwitchNumpad) }
    var showKeyPreviews by remember { mutableStateOf(prefs.showKeyPreviews) }
    var showLayoutNameOnSpacebar by remember { mutableStateOf(prefs.showLayoutNameOnSpacebar) }
    var keyGlowRipple by remember { mutableStateOf(prefs.keyGlowRippleEnabled) }
    var showKeyHints by remember { mutableStateOf(prefs.showKeyHints) }
    var swipeUpFlickEnabled by remember { mutableStateOf(prefs.swipeUpFlickEnabled) }

    var smartInitialKarEnabled by remember { mutableStateOf(prefs.smartInitialKarEnabled) }
    var hasantaConjunctsEnabled by remember { mutableStateOf(prefs.hasantaConjunctsEnabled) }
    var geminateDoubleTapEnabled by remember { mutableStateOf(prefs.geminateDoubleTapEnabled) }
    var hasantaHapticEnabled by remember { mutableStateOf(prefs.hasantaHapticEnabled) }

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
    val autocorrectCount = remember { dictManager.getAutocorrectRules().size }

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

    val showDictionaryQuickCard = (selectedCategory == PrefCategory.ALL || selectedCategory == PrefCategory.TOOLS) &&
            matchesSearch("dictionary", "vocabulary", "shortcut", "autocorrect", "personal", "শব্দকোষ", "ডিকশনারি", "শর্টকাট", "নিয়ম", "কাস্টম শব্দ")

    val showAppearance = (selectedCategory == PrefCategory.ALL || selectedCategory == PrefCategory.DISPLAY) &&
            matchesSearch("app appearance", "theme mode", "dark", "light", "system", "sync keyboard", "অ্যাপিয়ারেন্স", "থিম", "ডার্ক", "লাইট")

    val showTyping = (selectedCategory == PrefCategory.ALL || selectedCategory == PrefCategory.TYPING) &&
            matchesSearch("typing", "number row", "numpad", "spacing", "dari", "spacebar", "autocomplete", "autocorrect", "candidate", "strip", "prediction", "learn", "glide", "code shield", "avro", "probaho", "smart initial kar", "hasanta", "conjunct", "geminate", "double tap", "যুক্তবর্ণ", "কার", "হসন্ত", "দ্বিত্ব", "টাইপিং", "সংখ্যা সারি", "নম্বর", "দাঁড়ি", "সাজেশন", "কোড শিল্ড", "ক্যান্ডিডেট বার", "অটোকারেক্ট", "ভবিষ্যদ্বাণী")

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

    val anyCardShown = showDictionaryQuickCard || showAppearance || showTyping || showGestures || showDisplay || showSound || showFormFactor || showTools || showPrivacy

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

        // ── Quick Access: Personal Dictionary & Shortcuts ───────────────────
        if (showDictionaryQuickCard) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenDictionaryManager() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isEnglish) "Personal Dictionary & Shortcuts" else "ব্যক্তিগত শব্দকোষ ও শর্টকাট",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isEnglish)
                                "$userWordsCount custom words • $autocorrectCount shortcuts"
                            else
                                "$userWordsCount টি কাস্টম শব্দ • $autocorrectCount টি শর্টকাট",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    FilledTonalButton(
                        onClick = onOpenDictionaryManager,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(if (isEnglish) "Open" else "খুলুন", fontSize = 12.sp)
                    }
                }
            }
        }

        // ── 0. App Appearance Card ──────────────────────────────────────────
        if (showAppearance) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isEnglish) "App Appearance" else "অ্যাপ অ্যাপিয়ারেন্স",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
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
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isEnglish) "Typing & Autocomplete" else "টাইপিং ও সাজেশন",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 2.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )

                    // Candidate & Suggestion Strip (Global Master)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = if (isEnglish) "Candidate & Suggestion Strip" else "সাজেশন ও ক্যান্ডিডেট বার",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = if (isEnglish) "Show word suggestions & toolbar strip above keyboard"
                                       else "কীবোর্ডের উপর শব্দ সাজেশন ও ক্যান্ডিডেট বার প্রদর্শন",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = candidateStripEnabled,
                            onCheckedChange = {
                                candidateStripEnabled = it
                                prefs.candidateStripEnabled = it
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // Auto-Correction (Global Master)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = if (isEnglish) "Auto-Correction" else "স্বয়ংক্রিয় সংশোধন (Auto-Correct)",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = if (isEnglish) "Automatically correct typos and common mistakes"
                                       else "টাইপ করার সময় ভুল বানান স্বয়ংক্রিয়ভাবে সংশোধন করবে",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autocorrectEnabled,
                            onCheckedChange = {
                                autocorrectEnabled = it
                                prefs.autocorrectEnabled = it
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // Next-Word Prediction (Global Master)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = if (isEnglish) "Next-Word Prediction" else "পরবর্তী শব্দের অনুমান",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = if (isEnglish) "Predict and suggest upcoming words based on context"
                                       else "প্রসঙ্গ অনুযায়ী পরবর্তী সম্ভাব্য শব্দ সাজেস্ট করবে",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = nextWordPredictionEnabled,
                            onCheckedChange = {
                                nextWordPredictionEnabled = it
                                prefs.nextWordPredictionEnabled = it
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

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
                                text = if (isEnglish) "Show number row above keyboard" else "কীবোর্ডের উপরে সংখ্যার সারি দেখাবে",
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
                                text = if (isEnglish) "Show numeric pad for phone and PIN fields" else "ফোন নম্বর বা পিন ফিল্ডে নম্বর প্যাড দেখাবে",
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
                                text = if (isEnglish) "Add space after punctuation" else "যতিচিহ্নের পর স্পেস যুক্ত করবে",
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
                                text = if (isEnglish) "Double-tap space to insert Dari (।)" else "টানা দুইবার স্পেস চাপলে দাঁড়ি (।) বসবে",
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
                                text = if (isEnglish) "Spacebar inserts top suggestion" else "স্পেসবার চাপলে প্রথম সাজেশন নির্বাচন হবে",
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
                                text = if (isEnglish) "Remember frequently typed words locally" else "ঘনঘন টাইপ করা শব্দগুলো মনে রাখবে",
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
                                text = if (isEnglish) "Glide Typing" else "গ্লাইড টাইপিং",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = if (isEnglish) "Slide finger across letters to type (English)" else "আঙুল টেনে ইংরেজি শব্দ লিখুন",
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
                                text = if (isEnglish) "Code Shield" else "কোড শিল্ড",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = if (isEnglish) "Pause suggestions in code, URLs, and passwords" else "কোড, ইউআরএল ও পাসওয়ার্ডে সাজেশন বন্ধ রাখবে",
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

        // ── 2b. Bengali Smart Input (Global Defaults) ───────────────────────
        if (showTyping) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Keyboard,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isEnglish) "Bengali Smart Input (Global Defaults)" else "বাংলা স্মার্ট ইনপুট (গ্লোবাল ডিফল্ট)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = if (isEnglish) "Default behaviors across fixed layouts (can be overridden per layout)"
                                       else "সকল ফিক্সড লেআউটের সাধারণ নিয়ম (লেআউটভিত্তিক পরিবর্তন সম্ভব)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 2.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )

                    // 1. Smart Initial Kar to Vowel
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = if (isEnglish) "Auto-Fix Initial Vowels" else "শব্দের শুরুতে কার রূপান্তর",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = if (isEnglish) "Convert standalone vowel signs at word starts (া -> আ)"
                                       else "শব্দের শুরুতে কার চিহ্ন চাপলে পূর্ণ স্বরবর্ণে রূপান্তর করবে (যেমন: া -> আ)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = smartInitialKarEnabled,
                            onCheckedChange = {
                                smartInitialKarEnabled = it
                                prefs.smartInitialKarEnabled = it
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // 2. Conjunct Suggestions on Hasanta
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = if (isEnglish) "Conjunct Suggestions on Hasanta" else "হসন্ত চাপলে যুক্তবর্ণ সাজেশন",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = if (isEnglish) "Show valid conjuncts when pressing Hasanta (্)"
                                       else "ব্যঞ্জনবর্ণের পর হসন্ত (্) চাপলে সম্ভাব্য সকল যুক্তবর্ণ দেখাবে",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = hasantaConjunctsEnabled,
                            onCheckedChange = {
                                hasantaConjunctsEnabled = it
                                prefs.hasantaConjunctsEnabled = it
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // 3. Double-Tap for Geminate Conjuncts
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = if (isEnglish) "Double-Tap for Conjuncts" else "ডাবল ট্যাপে দ্বিত্ব যুক্তবর্ণ",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = if (isEnglish) "Quickly double-tap consonant to form conjunct (ত x 2 -> ত্ত)"
                                       else "একই বর্ণ পরপর দুইবার চাপলে দ্বিত্ব যুক্তবর্ণ তৈরি করবে (যেমন: ত x ২ -> ত্ত)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = geminateDoubleTapEnabled,
                            onCheckedChange = {
                                geminateDoubleTapEnabled = it
                                prefs.geminateDoubleTapEnabled = it
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // 4. Hasanta Key Vibration Feedback
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = if (isEnglish) "Hasanta Vibration Feedback" else "হসন্ত চাপলে হ্যাপটিক ভাইব্রেশন",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = if (isEnglish) "Distinct tactile tick feedback when pressing Hasanta (্)"
                                       else "হসন্ত (্) চাপলে আলাদা সূক্ষ্ম স্পর্শ অনুভূতি প্রদান করবে",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = hasantaHapticEnabled,
                            onCheckedChange = {
                                hasantaHapticEnabled = it
                                prefs.hasantaHapticEnabled = it
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
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Swipe,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isEnglish) "Gestures & Navigation" else "জেশ্চার ও নেভিগেশন",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
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
                                if (isEnglish) "Switch Layout" else "লেআউট পরিবর্তন",
                                KeyboardPreferences.SpacebarSwipeMode.LAYOUT_SWITCH
                            ),
                            Pair(
                                if (isEnglish) "Off" else "বন্ধ",
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
                                if (isEnglish) "Language / Layout Switcher" else "ভাষা ও লেআউট সুইচার",
                                KeyboardPreferences.BottomRowKeyMode.LANGUAGE_SWITCH
                            ),
                            Pair(
                                if (isEnglish) "Emoji Key" else "ইমোজি কী",
                                KeyboardPreferences.BottomRowKeyMode.EMOJI
                            ),
                            Pair(
                                if (isEnglish) "Comma (,)" else "কমা (,)",
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
                                text = if (isEnglish) "Slide left from backspace to delete" else "মুছতে ব্যাকস্পেস থেকে বামে সোয়াইপ করুন",
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
                        text = if (isEnglish) "Volume Key Cursor" else "ভলিউম কী দিয়ে কার্সর",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val volOptions = listOf(
                            Pair(
                                if (isEnglish) "Off" else "বন্ধ",
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
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isEnglish) "Display & Keys" else "ডিসপ্লে ও কী বিন্যাস",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
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
                                text = if (isEnglish) "Show outlines around keys" else "কী-এর চারপাশে বর্ডার দেখাবে",
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
                                text = if (isEnglish) "Show character preview above pressed key" else "কী চাপলে উপরে প্রিভিউ বাবল দেখাবে",
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
                                text = if (isEnglish) "Key Hints" else "কী সহায়িকা",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = if (isEnglish) "Show shifted characters on keys"
                                       else "কী-এর কোণায় শিফট বর্ণ দেখাবে",
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
                                text = if (isEnglish) "Swipe-Up for Shift" else "উপরে সোয়াইপ করে শিফট",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = if (isEnglish) "Flick key upward to type shifted letter"
                                       else "কী-এর উপর সোয়াইপ করে শিফট বর্ণ লিখুন",
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
                                text = if (isEnglish) "Show layout label on spacebar" else "স্পেসবারে লেআউটের নাম দেখাবে",
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
                                text = if (isEnglish) "Touch Ripple" else "টাচ রিপল",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = if (isEnglish) "Visual ripple effect on key press" else "কী চাপলে রিপল অ্যানিমেশন দেখাবে",
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
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Vibration,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isEnglish) "Sound & Haptics" else "সাউন্ড ও কম্পন",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
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
                                text = if (isEnglish) "Vibration" else "ভাইব্রেশন",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = if (isEnglish) "Vibrate on key press" else "কী চাপলে ভাইব্রেশন হবে",
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
                                text = if (isEnglish) "Play click sound on key press" else "কী চাপলে অডিও সাউন্ড হবে",
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
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Smartphone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isEnglish) "Form Factor & Modes" else "কীবোর্ড মোড",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    FormFactorPrefOption(
                        title = if (isEnglish) "Standard" else "সাধারণ (Standard)",
                        subtitle = if (isEnglish) "Default full width" else "ডিফল্ট পূর্ণ প্রস্থ",
                        icon = Icons.Filled.Smartphone,
                        selected = formFactor == KeyboardPreferences.FormFactor.STANDARD,
                        onSelect = {
                            formFactor = KeyboardPreferences.FormFactor.STANDARD
                            prefs.formFactor = KeyboardPreferences.FormFactor.STANDARD
                        }
                    )
                    FormFactorPrefOption(
                        title = if (isEnglish) "One-Handed Mode" else "একহাতে ব্যবহারের মোড (One-Handed)",
                        subtitle = if (isEnglish) "Compact for one-handed typing" else "এক হাতে সহজে টাইপ করার জন্য",
                        icon = Icons.Filled.Smartphone,
                        selected = formFactor == KeyboardPreferences.FormFactor.ONE_HANDED_RIGHT || formFactor == KeyboardPreferences.FormFactor.ONE_HANDED_LEFT,
                        onSelect = {
                            formFactor = KeyboardPreferences.FormFactor.ONE_HANDED_RIGHT
                            prefs.formFactor = KeyboardPreferences.FormFactor.ONE_HANDED_RIGHT
                        }
                    )
                    FormFactorPrefOption(
                        title = if (isEnglish) "Floating Keyboard" else "ভাসমান কীবোর্ড (Floating)",
                        subtitle = if (isEnglish) "Movable mini keyboard" else "পর্দার যেকোনো জায়গায় নেওয়া যায়",
                        icon = Icons.Filled.PictureInPictureAlt,
                        selected = formFactor == KeyboardPreferences.FormFactor.FLOATING,
                        onSelect = {
                            formFactor = KeyboardPreferences.FormFactor.FLOATING
                            prefs.formFactor = KeyboardPreferences.FormFactor.FLOATING
                        }
                    )
                    FormFactorPrefOption(
                        title = if (isEnglish) "Split Keyboard" else "বিভক্ত কীবোর্ড (Split)",
                        subtitle = if (isEnglish) "Split for two-thumb typing on large screens" else "বড় স্ক্রিনে দুই হাতে সহজে টাইপ করার জন্য",
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
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Book,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isEnglish) "Tools & Vocabulary" else "টুলস ও শব্দকোষ",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
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
                                text = if (isEnglish) "Reorder shortcuts on the candidate bar" else "ক্যান্ডিডেট বারের শর্টকাটগুলো সাজান",
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
                                text = if (isEnglish) "Manage your custom and learned words" else "সংরক্ষিত ও কাস্টম শব্দসমূহ দেখুন",
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
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isEnglish) "Privacy & Security" else "নিরাপত্তা ও গোপনীয়তা",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
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
                                text = if (isEnglish) "100% On-Device & Private" else "১০০% অফলাইন ও নিরাপদ",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = if (isEnglish) "Keystrokes and text never leave your device" else "টাইপ করা কোনো তথ্য কখনো ডিভাইস থেকে বাইরে যায় না",
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
                                text = if (isEnglish) "About Lekhani" else "লেখনী পরিচিতি",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = if (isEnglish) "Version, license, and credits" else "অ্যাপ সংস্করণ, লাইসেন্স ও তথ্য",
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent
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
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
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
}
