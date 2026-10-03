package com.lekhani.android.ui.theme

import android.net.Uri
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.lekhani.android.data.settings.KeyboardPreferences
import com.lekhani.android.theme.ChromaMode
import com.lekhani.android.theme.CustomThemeManager
import com.lekhani.android.theme.KeyboardTheme
import com.lekhani.android.theme.ThemeCategory
import com.lekhani.android.theme.ThemeChromaUtils
import com.lekhani.android.theme.ThemeRegistry

/**
 * Maps each [ThemeCategory] to a Material 3 vector icon.
 */
fun ThemeCategory.getIcon(): ImageVector = when (this) {
    ThemeCategory.ALL -> Icons.Filled.AutoAwesome
    ThemeCategory.NEON -> Icons.Filled.Bolt
    ThemeCategory.AESTHETIC -> Icons.Filled.Palette
    ThemeCategory.RGB_CHROMA -> Icons.Filled.Animation
    ThemeCategory.CLASSIC -> Icons.Filled.Star
    ThemeCategory.CONTRAST_NATURE -> Icons.Filled.Forest
    ThemeCategory.CUSTOM -> Icons.Filled.Tune
}

/**
 * Computes dynamic color transitions for Chroma preview elements in Compose.
 */
private fun computeChromaComposeColor(mode: ChromaMode, phase: Float, xRatio: Float = 0.5f): Color {
    return Color(ThemeChromaUtils.getColorAtPhase(mode, phase, xRatio))
}

/**
 * ThemeStudioSheet
 * Material 3 Expressive theme customization studio.
 * Supports independent app appearance modes, rich categorized keyboard themes
 * (Neon & Cyber, Aesthetic & Pastel, 120 FPS RGB Chroma Dynamic, Classic),
 * live interactive sandbox keyboard previews, and custom wallpaper backgrounds.
 */
@Composable
fun ThemeStudioSheet(
    prefs: KeyboardPreferences,
    isEnglish: Boolean = false,
    onThemeChanged: ((String) -> Unit)? = null,
    onAppThemeModeChanged: ((KeyboardPreferences.AppThemeMode) -> Unit)? = null,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val customThemeManager = remember { CustomThemeManager.get(context) }
    var customThemes by remember { mutableStateOf(customThemeManager.getAllCustomThemes()) }
    var themeToEdit by remember { mutableStateOf<KeyboardTheme?>(null) }
    var showEditorDialog by remember { mutableStateOf(false) }

    var selectedThemeId by remember { mutableStateOf(prefs.themeId) }
    var selectedAppThemeMode by remember { mutableStateOf(prefs.appThemeMode) }
    var selectedCategory by remember { mutableStateOf(ThemeCategory.ALL) }
    var wallpaperUri by remember { mutableStateOf(prefs.customWallpaperUri) }
    var wallpaperOpacity by remember { mutableFloatStateOf(prefs.wallpaperOpacity) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val uriStr = uri.toString()
            wallpaperUri = uriStr
            prefs.customWallpaperUri = uriStr
        }
    }

    val allPresetThemes = remember(context) {
        ThemeRegistry.PRESET_THEMES + listOf(ThemeRegistry.createMaterialYouTheme(context))
    }

    val activePreviewTheme = remember(selectedThemeId, customThemes, allPresetThemes) {
        customThemes.firstOrNull { it.id == selectedThemeId }
            ?: allPresetThemes.firstOrNull { it.id == selectedThemeId }
            ?: ThemeRegistry.resolveTheme(context, selectedThemeId)
    }

    val filteredThemes = remember(selectedCategory, customThemes, allPresetThemes) {
        when (selectedCategory) {
            ThemeCategory.ALL -> customThemes + allPresetThemes
            ThemeCategory.CUSTOM -> customThemes
            else -> {
                val presetsInCat = allPresetThemes.filter { it.category == selectedCategory }
                val customsInCat = customThemes.filter { it.category == selectedCategory }
                customsInCat + presetsInCat
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = if (isEnglish) "Back" else "ফিরে যান",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = if (isEnglish) "Theme & Color Studio" else "থিম ও কালার স্টুডিও",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (isEnglish) "Customize keyboard colors, RGB chroma, and wallpaper"
                               else "কীবোর্ডের নিওন, আরজিবি, কালার ও ওয়ালপেপার পরিবর্তন করুন",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Section 0: App Appearance ─────────────────────────────────
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEnglish) "App Appearance" else "অ্যাপ অ্যাপিয়ারেন্স",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        val activeLabel = when (selectedAppThemeMode) {
                            KeyboardPreferences.AppThemeMode.SYSTEM -> if (isEnglish) "System Default" else "সিস্টেম ডিফল্ট"
                            KeyboardPreferences.AppThemeMode.LIGHT -> if (isEnglish) "Light Mode" else "লাইট মোড"
                            KeyboardPreferences.AppThemeMode.DARK -> if (isEnglish) "Dark Mode" else "ডার্ক মোড"
                            KeyboardPreferences.AppThemeMode.DYNAMIC -> if (isEnglish) "Dynamic Material You" else "ডাইনামিক কালার"
                            KeyboardPreferences.AppThemeMode.MATCH_KEYBOARD -> if (isEnglish) "Match Keyboard" else "কীবোর্ড সিঙ্ক"
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = activeLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(5.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val modes = buildList {
                                add(Triple(KeyboardPreferences.AppThemeMode.SYSTEM, Icons.Default.BrightnessAuto, if (isEnglish) "System" else "সিস্টেম"))
                                add(Triple(KeyboardPreferences.AppThemeMode.LIGHT, Icons.Default.LightMode, if (isEnglish) "Light" else "লাইট"))
                                add(Triple(KeyboardPreferences.AppThemeMode.DARK, Icons.Default.DarkMode, if (isEnglish) "Dark" else "ডার্ক"))
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    add(Triple(KeyboardPreferences.AppThemeMode.DYNAMIC, Icons.Default.AutoAwesome, if (isEnglish) "Dynamic" else "ডাইনামিক"))
                                }
                                add(Triple(KeyboardPreferences.AppThemeMode.MATCH_KEYBOARD, Icons.Filled.Palette, if (isEnglish) "Match Kbd" else "কীবোর্ড ম্যাচ"))
                            }
                            modes.forEach { (mode, icon, title) ->
                                val isSelected = selectedAppThemeMode == mode
                                val animBg by animateColorAsState(
                                    targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    label = "pill_bg"
                                )
                                val animBorderColor by animateColorAsState(
                                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent,
                                    label = "pill_border"
                                )
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(animBg)
                                        .border(BorderStroke(1.dp, animBorderColor), RoundedCornerShape(10.dp))
                                        .clickable {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            selectedAppThemeMode = mode
                                            prefs.appThemeMode = mode
                                            onAppThemeModeChanged?.invoke(mode)
                                        }
                                        .padding(horizontal = 14.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = title,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Section 1: Live Interactive Keyboard Preview ──────────────
                item {
                    Text(
                        text = if (isEnglish) "Live Interactive Preview" else "লাইভ ইন্টারেক্টিভ প্রিভিউ",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LiveKeyboardMiniPreview(theme = activePreviewTheme, isEnglish = isEnglish)
                }

                // ── Section 2: Category Filter Chips ──────────────────────────
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isEnglish) "Theme Collections" else "থিম কালেকশনসমূহ",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            OutlinedButton(
                                onClick = {
                                    val baseTheme = activePreviewTheme
                                    themeToEdit = baseTheme.copy(
                                        id = "custom_${System.currentTimeMillis()}",
                                        nameBengali = "আমার থিম",
                                        nameEnglish = "My Custom Theme",
                                        isCustom = true,
                                        category = ThemeCategory.CUSTOM
                                    )
                                    showEditorDialog = true
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isEnglish) "Create" else "নতুন থিম")
                            }
                        }

                        // Filter chips horizontal row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val categories = listOf(
                                ThemeCategory.ALL,
                                ThemeCategory.CLASSIC,
                                ThemeCategory.RGB_CHROMA,
                                ThemeCategory.NEON,
                                ThemeCategory.AESTHETIC,
                                ThemeCategory.CONTRAST_NATURE,
                                ThemeCategory.CUSTOM,
                            )
                            categories.forEach { cat ->
                                val isSelected = selectedCategory == cat
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        selectedCategory = cat
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = cat.getIcon(),
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    label = {
                                        val title = if (isEnglish) cat.titleEnglish else cat.titleBengali
                                        Text(title)
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }
                    }
                }

                // ── Section 3: Filtered Theme Cards List ──────────────────────

                if (filteredThemes.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            )
                        ) {
                            Text(
                                text = if (isEnglish) "No themes in this collection yet. Tap '+ Create' to craft your own!"
                                       else "এই কালেকশনে কোনো থিম পাওয়া যায়নি। '+ নতুন থিম' দিয়ে তৈরি করুন!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                } else {
                    items(filteredThemes.size) { idx ->
                        val theme = filteredThemes[idx]
                        val isSelected = selectedThemeId == theme.id

                        ThemePreviewCard(
                            theme = theme,
                            isSelected = isSelected,
                            isEnglish = isEnglish,
                            onSelect = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                selectedThemeId = theme.id
                                prefs.themeId = theme.id
                                onThemeChanged?.invoke(theme.id)
                            },
                            onEdit = if (theme.isCustom) {
                                {
                                    themeToEdit = theme
                                    showEditorDialog = true
                                }
                            } else null,
                            onDuplicate = {
                                val dup = customThemeManager.duplicateTheme(
                                    theme,
                                    if (isEnglish) "${theme.nameEnglish} (Copy)" else "${theme.nameBengali} (কপি)"
                                )
                                customThemes = customThemeManager.getAllCustomThemes()
                                selectedThemeId = dup.id
                                prefs.themeId = dup.id
                                onThemeChanged?.invoke(dup.id)
                            },
                            onDelete = if (theme.isCustom) {
                                {
                                    customThemeManager.deleteCustomTheme(theme.id)
                                    customThemes = customThemeManager.getAllCustomThemes()
                                    if (selectedThemeId == theme.id) {
                                        val fallback = ThemeRegistry.THEME_FLOW_TEAL.id
                                        selectedThemeId = fallback
                                        prefs.themeId = fallback
                                        onThemeChanged?.invoke(fallback)
                                    }
                                }
                            } else null
                        )
                    }
                }

                // ── Section 4: Wallpaper Background ──────────────────────────
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isEnglish) "Custom Wallpaper Background" else "কাস্টম ওয়ালপেপার ব্যাকগ্রাউন্ড",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Wallpaper,
                                        contentDescription = if (isEnglish) "Wallpaper" else "ওয়ালপেপার",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = if (wallpaperUri.isNotBlank()) {
                                            if (isEnglish) "Wallpaper Active" else "ওয়ালপেপার যুক্ত হয়েছে"
                                        } else {
                                            if (isEnglish) "No Wallpaper" else "কোনো ওয়ালপেপার নেই"
                                        },
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                    )
                                }

                                Row {
                                    Button(
                                        onClick = { photoPickerLauncher.launch("image/*") },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(if (isEnglish) "Choose Image" else "ছবি নির্বাচন")
                                    }

                                    if (wallpaperUri.isNotBlank()) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        IconButton(
                                            onClick = {
                                                wallpaperUri = ""
                                                prefs.customWallpaperUri = ""
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = if (isEnglish) "Remove Wallpaper" else "ওয়ালপেপার মুছুন",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }

                            if (wallpaperUri.isNotBlank()) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = if (isEnglish) "Background Opacity: ${(wallpaperOpacity * 100).toInt()}%" else "ওয়ালপেপারের স্বচ্ছতা: ${(wallpaperOpacity * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Slider(
                                    value = wallpaperOpacity,
                                    onValueChange = {
                                        wallpaperOpacity = it
                                        prefs.wallpaperOpacity = it
                                    },
                                    valueRange = 0.05f..0.85f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(if (isEnglish) "Done" else "সম্পন্ন", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        if (showEditorDialog && themeToEdit != null) {
            ThemeEditorDialog(
                initialTheme = themeToEdit!!,
                isEnglish = isEnglish,
                onSave = { savedTheme ->
                    customThemeManager.saveCustomTheme(savedTheme)
                    customThemes = customThemeManager.getAllCustomThemes()
                    selectedThemeId = savedTheme.id
                    prefs.themeId = savedTheme.id
                    onThemeChanged?.invoke(savedTheme.id)
                    showEditorDialog = false
                    themeToEdit = null
                },
                onDismiss = {
                    showEditorDialog = false
                    themeToEdit = null
                }
            )
        }
    }
}

/**
 * Modern Theme Card with circular color swatches, category badges, and active state.
 */
@Composable
private fun ThemePreviewCard(
    theme: KeyboardTheme,
    isSelected: Boolean,
    isEnglish: Boolean = false,
    onSelect: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDuplicate: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val liveChromaColor = if (theme.isRgbChroma) {
        val infiniteTransition = rememberInfiniteTransition(label = "CardChroma_${theme.id}")
        val phase by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "CardHue_${theme.id}"
        )
        remember(phase, theme.chromaMode) {
            computeChromaComposeColor(theme.chromaMode, phase, 0.5f)
        }
    } else {
        Color(theme.accentColor)
    }

    val cardAccentColor = if (theme.isRgbChroma) liveChromaColor else Color(theme.accentColor)
    val cardBorderColor = if (isSelected) {
        cardAccentColor
    } else if (theme.isRgbChroma) {
        liveChromaColor.copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = if (isSelected) 2.5.dp else if (theme.isRgbChroma) 1.5.dp else 1.dp,
                color = cardBorderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(theme.backgroundColor))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isEnglish) theme.nameEnglish else theme.nameBengali,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(theme.labelColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(cardAccentColor.copy(alpha = 0.18f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = theme.category.getIcon(),
                            contentDescription = null,
                            tint = cardAccentColor,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (isEnglish) theme.category.titleEnglish else theme.category.titleBengali,
                            fontSize = 10.sp,
                            color = cardAccentColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Circular Color Swatches (Key Normal, Shift/Function, Accent, Border)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ColorSwatchCircle(Color(theme.keyNormalColor), "Key")
                    ColorSwatchCircle(Color(theme.keyShiftColor), "Shift")
                    ColorSwatchCircle(cardAccentColor, "Accent")
                    ColorSwatchCircle(if (theme.isRgbChroma) cardAccentColor else Color(theme.keyBorderColor), "Border")
                    Spacer(modifier = Modifier.width(4.dp))
                    if (theme.isRgbChroma) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Animation,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = liveChromaColor
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (isEnglish) "Dynamic Chroma" else "ডাইনামিক ক্রোমা",
                                fontSize = 11.sp,
                                color = liveChromaColor,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        Text(
                            text = if (theme.isDark) "Dark" else "Light",
                            fontSize = 11.sp,
                            color = Color(theme.labelDimColor)
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onEdit != null) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = if (isEnglish) "Edit" else "সম্পাদনা",
                            tint = Color(theme.labelDimColor),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (onDuplicate != null) {
                    IconButton(onClick = onDuplicate, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = if (isEnglish) "Duplicate" else "কপি করুন",
                            tint = Color(theme.labelDimColor),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = if (isEnglish) "Delete" else "মুছুন",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (isSelected) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(cardAccentColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = if (isEnglish) "Selected" else "নির্বাচিত",
                            tint = if (theme.isDark) Color.Black else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorSwatchCircle(color: Color, desc: String) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
    )
}

/**
 * Interactive Live Keyboard Preview sandbox featuring:
 * - Candidate suggestion strip
 * - Real-time animated rainbow border / accents for RGB Chroma themes
 * - Interactive test typing bar with haptic response
 */
@Composable
private fun LiveKeyboardMiniPreview(
    theme: KeyboardTheme,
    isEnglish: Boolean = false
) {
    val view = LocalView.current
    var testInput by remember { mutableStateOf("") }

    // Dynamic RGB animation for chroma themes
    val infiniteTransition = rememberInfiniteTransition(label = "ChromaFlow")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PhaseAnim"
    )

    val animatedBorderColor = if (theme.isRgbChroma) {
        remember(phase, theme.chromaMode) {
            computeChromaComposeColor(theme.chromaMode, phase, 0.5f)
        }
    } else {
        Color(theme.keyBorderColor)
    }

    val liveAccentColor = if (theme.isRgbChroma) animatedBorderColor else Color(theme.accentColor)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(theme.backgroundColor)),
        border = BorderStroke(1.5.dp, liveAccentColor.copy(alpha = 0.85f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Header: Theme Name + Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isEnglish) theme.nameEnglish else theme.nameBengali,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(theme.labelColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(liveAccentColor.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = theme.category.getIcon(),
                            contentDescription = null,
                            tint = liveAccentColor,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (isEnglish) theme.category.titleEnglish else theme.category.titleBengali,
                            fontSize = 10.sp,
                            color = liveAccentColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (testInput.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(theme.keyNormalColor))
                            .clickable { testInput = "" }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("Clear", fontSize = 10.sp, color = Color(theme.labelDimColor))
                    }
                }
            }

            // Interactive Text Test Strip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(theme.keyShiftColor))
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = if (testInput.isNotBlank()) testInput else (if (isEnglish) "Tap keys below to test typing..." else "নিচে কি চেপে টাইপিং টেস্ট করুন..."),
                    color = if (testInput.isNotBlank()) Color(theme.labelColor) else Color(theme.labelDimColor),
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }

            // Suggestions / Candidate Strip Preview
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val suggestions = listOf("বাংলা", "লেখনী", "বাংলাদেশ", "প্রবাহ")
                suggestions.forEachIndexed { idx, word ->
                    val isHighCandidate = idx == 0
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isHighCandidate) liveAccentColor else Color(theme.keyNormalColor))
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                testInput += if (testInput.isEmpty()) word else " $word"
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = word,
                            fontSize = 11.sp,
                            fontWeight = if (isHighCandidate) FontWeight.Bold else FontWeight.Normal,
                            color = if (isHighCandidate) (if (theme.isDark) Color.Black else Color.White) else Color(theme.labelColor)
                        )
                    }
                }
            }

            // Row 1: Vowels / Consonants
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("আ", "ো", "ী", "প", "ব", "ম", "দ", "ল").forEachIndexed { colIdx, ch ->
                    val keyBorder = if (theme.isRgbChroma) computeChromaComposeColor(theme.chromaMode, phase, colIdx / 7f) else animatedBorderColor
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(30.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(theme.keyNormalColor))
                            .border(1.2.dp, keyBorder, RoundedCornerShape(6.dp))
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                testInput += ch
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(ch, color = Color(theme.labelColor), fontSize = 12.sp)
                    }
                }
            }

            // Row 2: Home Row with accent
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("অ", "া", "ি", "র", "ত", "ন", "স", "ক").forEachIndexed { colIdx, ch ->
                    val keyBorder = if (theme.isRgbChroma) computeChromaComposeColor(theme.chromaMode, phase, colIdx / 7f) else animatedBorderColor
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(30.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(theme.keyNormalColor))
                            .border(1.2.dp, keyBorder, RoundedCornerShape(6.dp))
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                testInput += ch
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(ch, color = Color(theme.labelColor), fontSize = 12.sp)
                    }
                }
            }

            // Row 3: Shift, Hasanta, Spacebar & Enter
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val shiftBorder = if (theme.isRgbChroma) computeChromaComposeColor(theme.chromaMode, phase, 0f) else animatedBorderColor
                val hasantaBorder = if (theme.isRgbChroma) computeChromaComposeColor(theme.chromaMode, phase, 0.25f) else animatedBorderColor
                val spaceBorder = if (theme.isRgbChroma) computeChromaComposeColor(theme.chromaMode, phase, 0.5f) else animatedBorderColor
                val enterBorder = if (theme.isRgbChroma) computeChromaComposeColor(theme.chromaMode, phase, 1.0f) else animatedBorderColor

                Box(
                    modifier = Modifier
                        .weight(1.2f)
                        .height(30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(theme.keyShiftColor))
                        .border(1.2.dp, shiftBorder, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⇧", color = Color(theme.labelDimColor), fontSize = 12.sp)
                }
                Box(
                    modifier = Modifier
                        .weight(1.0f)
                        .height(30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(theme.keyHasantaColor))
                        .border(1.2.dp, hasantaBorder, RoundedCornerShape(6.dp))
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            testInput += "্"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("্", color = liveAccentColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .weight(3.6f)
                        .height(30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(theme.keySpaceColor))
                        .border(1.2.dp, spaceBorder, RoundedCornerShape(6.dp))
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            testInput += " "
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (isEnglish) "Space • Lekhani" else "স্পেস • লেখনী",
                        color = Color(theme.labelDimColor),
                        fontSize = 11.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1.4f)
                        .height(30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(liveAccentColor)
                        .border(1.2.dp, enterBorder, RoundedCornerShape(6.dp))
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            if (testInput.isNotEmpty()) testInput = testInput.dropLast(1)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Backspace",
                        tint = if (theme.isDark) Color.Black else Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
