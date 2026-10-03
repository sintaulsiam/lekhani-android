package com.lekhani.android.ui.theme

import android.graphics.BitmapFactory
import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lekhani.android.data.settings.KeyboardPreferences
import com.lekhani.android.theme.ChromaMode
import com.lekhani.android.theme.ChromaStyle
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
    ThemeCategory.RGB_CHROMA -> Icons.Filled.Animation
    ThemeCategory.NEON -> Icons.Filled.Bolt
    ThemeCategory.AESTHETIC -> Icons.Filled.Palette
    ThemeCategory.CLASSIC -> Icons.Filled.Star
    ThemeCategory.CONTRAST_NATURE -> Icons.Filled.Forest
    ThemeCategory.CUSTOM -> Icons.Filled.Tune
}

/**
 * Computes dynamic color transitions for Chroma preview elements in Compose.
 */
fun computeChromaComposeColor(mode: ChromaMode, phase: Float, xRatio: Float = 0.5f): Color {
    return Color(ThemeChromaUtils.getColorAtPhase(mode, phase, xRatio))
}

/**
 * ThemeStudioSheet
 * Redesigned, clean Material 3 Theme Studio.
 * - 2-Column visual keyboard thumbnail grid (instant visual identification, fills full height)
 * - On-demand bottom-anchored live preview bar (slides up when a theme card is tapped)
 * - Clean collapsible custom wallpaper controls
 * - Zero wasted vertical space: grid is always the primary focus
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
    var selectedCategory by remember { mutableStateOf(ThemeCategory.ALL) }
    var wallpaperUri by remember { mutableStateOf(prefs.customWallpaperUri) }
    var wallpaperOpacity by remember { mutableFloatStateOf(prefs.wallpaperOpacity) }
    var showWallpaperPanel by remember { mutableStateOf(wallpaperUri.isNotBlank()) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val uriStr = uri.toString()
            wallpaperUri = uriStr
            prefs.customWallpaperUri = uriStr
            showWallpaperPanel = true
        }
    }

    val allPresetThemes = remember(context) {
        ThemeRegistry.PRESET_THEMES + listOf(ThemeRegistry.createMaterialYouTheme(context))
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

    // ── Preview bar state ─────────────────────────────────────────────────────
    // previewThemeId: the theme currently showing in the bottom bar (null = bar hidden)
    var previewThemeId by remember { mutableStateOf<String?>(null) }
    val previewTheme = remember(previewThemeId, customThemes, allPresetThemes) {
        previewThemeId?.let { pid ->
            customThemes.firstOrNull { it.id == pid }
                ?: allPresetThemes.firstOrNull { it.id == pid }
                ?: ThemeRegistry.resolveTheme(context, pid)
        }
    }

    val chunkedThemes = remember(filteredThemes) {
        filteredThemes.chunked(2)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
            // ── Clean Header ──────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isEnglish) "Back" else "ফিরে যান",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isEnglish) "Themes" else "থিম",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { showWallpaperPanel = !showWallpaperPanel },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (showWallpaperPanel || wallpaperUri.isNotBlank())
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Wallpaper,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = if (showWallpaperPanel || wallpaperUri.isNotBlank())
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isEnglish) "Wallpaper" else "ওয়ালপেপার",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = {
                            // Always start from neutral baseline, not active theme
                            val baseline = ThemeRegistry.THEME_FLOW_TEAL
                            themeToEdit = baseline.copy(
                                id = "custom_${System.currentTimeMillis()}",
                                nameBengali = "আমার থিম",
                                nameEnglish = "My Custom Theme",
                                isCustom = true,
                                category = ThemeCategory.CUSTOM
                            )
                            showEditorDialog = true
                        },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isEnglish) "New" else "নতুন",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // ── Collapsible Wallpaper Panel ───────────────────────────────
            AnimatedVisibility(
                visible = showWallpaperPanel,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (wallpaperUri.isNotBlank())
                                    (if (isEnglish) "Custom Wallpaper Active" else "কাস্টম ওয়ালপেপার সক্রিয়")
                                else
                                    (if (isEnglish) "No Wallpaper Set" else "কোনো ওয়ালপেপার নেই"),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = { photoPickerLauncher.launch("image/*") },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(if (isEnglish) "Choose Image" else "ছবি নির্বাচন", fontSize = 11.sp)
                                }
                                if (wallpaperUri.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            wallpaperUri = ""
                                            prefs.customWallpaperUri = ""
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }
                        if (wallpaperUri.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${(wallpaperOpacity * 100).toInt()}%",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(32.dp)
                                )
                                Slider(
                                    value = wallpaperOpacity,
                                    onValueChange = {
                                        wallpaperOpacity = it
                                        prefs.wallpaperOpacity = it
                                    },
                                    valueRange = 0.05f..0.85f,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // (Preview bar is now anchored at the bottom — see Box overlay below)

            // ── Category Filter Chips ─────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val categories = listOf(
                    ThemeCategory.ALL,
                    ThemeCategory.RGB_CHROMA,
                    ThemeCategory.NEON,
                    ThemeCategory.AESTHETIC,
                    ThemeCategory.CLASSIC,
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
                                modifier = Modifier.size(13.dp),
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        label = {
                            val title = when (cat) {
                                ThemeCategory.ALL -> if (isEnglish) "All" else "সব"
                                ThemeCategory.RGB_CHROMA -> if (isEnglish) "Chroma" else "ক্রোমা"
                                ThemeCategory.NEON -> if (isEnglish) "Neon" else "নিওন"
                                ThemeCategory.AESTHETIC -> if (isEnglish) "Aesthetic" else "এসথেটিক"
                                ThemeCategory.CLASSIC -> if (isEnglish) "Classic" else "ক্লাসিক"
                                ThemeCategory.CONTRAST_NATURE -> if (isEnglish) "Nature" else "প্রকৃতি"
                                ThemeCategory.CUSTOM -> if (isEnglish) "Custom" else "কাস্টম"
                            }
                            Text(title, fontSize = 11.5.sp)
                        },
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── 2-Column Visual Keyboard Grid ─────────────────────────────
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                                text = if (isEnglish) "No custom themes created yet." else "কোনো কাস্টম থিম তৈরি করা হয়নি।",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                } else {
                    items(chunkedThemes.size) { rowIdx ->
                        val pair = chunkedThemes[rowIdx]
                        if (pair.size == 1) {
                            // Orphaned single card: full-width
                            val theme1 = pair[0]
                            ThemeGridThumbnailCard(
                                theme = theme1,
                                isSelected = selectedThemeId == theme1.id,
                                isPreviewing = previewThemeId == theme1.id,
                                isEnglish = isEnglish,
                                onSelect = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    // Toggle preview: tap same card = dismiss, different card = open
                                    previewThemeId = if (previewThemeId == theme1.id) null else theme1.id
                                },
                                onApply = {
                                    selectedThemeId = theme1.id
                                    prefs.themeId = theme1.id
                                    onThemeChanged?.invoke(theme1.id)
                                    previewThemeId = null
                                },
                                onEdit = if (theme1.isCustom) {
                                    {
                                        themeToEdit = theme1
                                        showEditorDialog = true
                                    }
                                } else null,
                                onDelete = if (theme1.isCustom) {
                                    {
                                        customThemeManager.deleteCustomTheme(theme1.id)
                                        customThemes = customThemeManager.getAllCustomThemes()
                                        if (selectedThemeId == theme1.id) {
                                            val fallback = ThemeRegistry.THEME_FLOW_TEAL.id
                                            selectedThemeId = fallback
                                            prefs.themeId = fallback
                                            onThemeChanged?.invoke(fallback)
                                        }
                                    }
                                } else null
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    val theme1 = pair[0]
                                    ThemeGridThumbnailCard(
                                        theme = theme1,
                                        isSelected = selectedThemeId == theme1.id,
                                        isPreviewing = previewThemeId == theme1.id,
                                        isEnglish = isEnglish,
                                        onSelect = {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            previewThemeId = if (previewThemeId == theme1.id) null else theme1.id
                                        },
                                        onApply = {
                                            selectedThemeId = theme1.id
                                            prefs.themeId = theme1.id
                                            onThemeChanged?.invoke(theme1.id)
                                            previewThemeId = null
                                        },
                                        onEdit = if (theme1.isCustom) {
                                            {
                                                themeToEdit = theme1
                                                showEditorDialog = true
                                            }
                                        } else null,
                                        onDelete = if (theme1.isCustom) {
                                            {
                                                customThemeManager.deleteCustomTheme(theme1.id)
                                                customThemes = customThemeManager.getAllCustomThemes()
                                                if (selectedThemeId == theme1.id) {
                                                    val fallback = ThemeRegistry.THEME_FLOW_TEAL.id
                                                    selectedThemeId = fallback
                                                    prefs.themeId = fallback
                                                    onThemeChanged?.invoke(fallback)
                                                }
                                            }
                                        } else null
                                    )
                                }

                                Box(modifier = Modifier.weight(1f)) {
                                    val theme2 = pair[1]
                                    ThemeGridThumbnailCard(
                                        theme = theme2,
                                        isSelected = selectedThemeId == theme2.id,
                                        isPreviewing = previewThemeId == theme2.id,
                                        isEnglish = isEnglish,
                                        onSelect = {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            previewThemeId = if (previewThemeId == theme2.id) null else theme2.id
                                        },
                                        onApply = {
                                            selectedThemeId = theme2.id
                                            prefs.themeId = theme2.id
                                            onThemeChanged?.invoke(theme2.id)
                                            previewThemeId = null
                                        },
                                        onEdit = if (theme2.isCustom) {
                                            {
                                                themeToEdit = theme2
                                                showEditorDialog = true
                                            }
                                        } else null,
                                        onDelete = if (theme2.isCustom) {
                                            {
                                                customThemeManager.deleteCustomTheme(theme2.id)
                                                customThemes = customThemeManager.getAllCustomThemes()
                                                if (selectedThemeId == theme2.id) {
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
                        }
                    }
                }
            }
            } // end Column

            // ── Bottom-Anchored On-Demand Preview Bar ─────────────────────
            AnimatedVisibility(
                visible = previewTheme != null,
                enter = slideInVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(),
                exit = slideOutVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                previewTheme?.let { theme ->
                    ThemePreviewBottomBar(
                        theme = theme,
                        isApplied = selectedThemeId == theme.id,
                        isEnglish = isEnglish,
                        prefs = prefs,
                        onApply = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            selectedThemeId = theme.id
                            prefs.themeId = theme.id
                            onThemeChanged?.invoke(theme.id)
                            previewThemeId = null
                        },
                        onDismiss = { previewThemeId = null }
                    )
                }
            }
        } // end Box

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
 * Modern 2-Column Miniature Keyboard Thumbnail Card.
 * Renders an authentic, scaled representation of the keyboard with real theme colors,
 * key borders, candidate strip, and glowing indicators.
 */
@Composable
private fun ThemeGridThumbnailCard(
    theme: KeyboardTheme,
    isSelected: Boolean,
    isPreviewing: Boolean = false,
    isEnglish: Boolean = false,
    onSelect: () -> Unit,
    onApply: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
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
    val cardBorderColor = when {
        isPreviewing -> MaterialTheme.colorScheme.primary
        isSelected -> cardAccentColor
        theme.isRgbChroma -> liveChromaColor.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    }
    val cardBorderWidth = when {
        isPreviewing -> 2.5.dp
        isSelected -> 2.dp
        theme.isRgbChroma -> 1.5.dp
        else -> 1.dp
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(cardBorderWidth, cardBorderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(theme.backgroundColor))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp)
        ) {
            // Miniature Keyboard Mockup Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(theme.backgroundColor))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp, vertical = 3.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Mini Candidate Strip
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(32.dp)
                                .height(5.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(cardAccentColor)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .width(18.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(theme.labelDimColor).copy(alpha = 0.35f))
                        )
                    }

                    // Mini Key Row 1 (7 keys)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        repeat(7) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(2.5.dp))
                                    .background(Color(theme.keyNormalColor))
                                    .border(0.5.dp, Color(theme.keyBorderColor).copy(alpha = 0.35f), RoundedCornerShape(2.5.dp))
                            )
                        }
                    }

                    // Mini Key Row 2 (7 keys)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        repeat(7) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(2.5.dp))
                                    .background(Color(theme.keyNormalColor))
                                    .border(0.5.dp, Color(theme.keyBorderColor).copy(alpha = 0.35f), RoundedCornerShape(2.5.dp))
                            )
                        }
                    }

                    // Mini Key Row 3 (Shift, Space, Enter)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(14.dp)
                                .height(12.dp)
                                .clip(RoundedCornerShape(2.5.dp))
                                .background(Color(theme.keyShiftColor))
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(12.dp)
                                .clip(RoundedCornerShape(2.5.dp))
                                .background(Color(theme.keySpaceColor))
                                .border(0.5.dp, Color(theme.keyBorderColor).copy(alpha = 0.35f), RoundedCornerShape(2.5.dp))
                        )
                        Box(
                            modifier = Modifier
                                .width(16.dp)
                                .height(12.dp)
                                .clip(RoundedCornerShape(2.5.dp))
                                .background(cardAccentColor)
                        )
                    }
                }

                // Selected Checkmark Badge
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(cardAccentColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = if (theme.isDark) Color.Black else Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Card Footer: Title & Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isEnglish) theme.nameEnglish else theme.nameBengali,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(theme.labelColor),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (theme.isRgbChroma) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(liveChromaColor)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (isEnglish) "Chroma" else "ক্রোমা",
                                fontSize = 9.sp,
                                color = liveChromaColor,
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            Text(
                                text = if (theme.isDark) "Dark" else "Light",
                                fontSize = 9.sp,
                                color = Color(theme.labelDimColor)
                            )
                        }
                    }
                }

                if (onEdit != null || onDelete != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (onEdit != null) {
                            IconButton(onClick = onEdit, modifier = Modifier.size(20.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = Color(theme.labelDimColor),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                        if (onDelete != null) {
                            IconButton(onClick = onDelete, modifier = Modifier.size(20.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Bottom-anchored on-demand live theme preview bar.
 * Slides up from the bottom when a theme card is tapped.
 * Shows the full interactive mini keyboard + apply / dismiss actions.
 */
@Composable
private fun ThemePreviewBottomBar(
    theme: KeyboardTheme,
    isApplied: Boolean,
    isEnglish: Boolean = false,
    prefs: KeyboardPreferences,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 12.dp,
        shadowElevation = 16.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f))
            )

            // Header: theme name + close
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = if (isEnglish) theme.nameEnglish else theme.nameBengali,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isEnglish) "Live Preview" else "লাইভ প্রিভিউ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isApplied) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = if (isEnglish) "Applied" else "সেট",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    } else {
                        Button(
                            onClick = onApply,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isEnglish) "Apply" else "সেট করুন",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = if (isEnglish) "Close" else "বন্ধ করুন",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Live interactive mini keyboard preview (authentic 4-row layout)
            LiveKeyboardMiniPreview(theme = theme, isEnglish = isEnglish, prefs = prefs)

            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}


/**
 * Authentic Interactive Live Keyboard Miniature Sandbox:
 * - Faithful 4-row mobile layout (10 top, 9 home, Shift + 7 letters + Backspace, Symbols + Hasanta + Spacebar + Enter)
 * - Exact color matching: Backspace uses [keyShiftColor] with [labelColor] icon; Enter with [liveAccentColor]
 * - Honors [showKeyBorders] preference for solid themes
 * - Honors [ChromaStyle.CLEAN_MINIMAL] (borderless keys, glowing Space + Enter only)
 * - Composites custom wallpaper bitmap + opacity behind keys
 * - Authentic candidate strip with toolbar indicator + primary glowing candidate
 */
@Composable
private fun LiveKeyboardMiniPreview(
    theme: KeyboardTheme,
    isEnglish: Boolean = false,
    prefs: KeyboardPreferences,
) {
    val context = LocalContext.current
    val view = LocalView.current
    var testInput by remember { mutableStateOf("") }

    val duration = ThemeChromaUtils.getCycleDuration(theme.chromaMode).toInt()
    val infiniteTransition = rememberInfiniteTransition(label = "ChromaFlow")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = duration, easing = LinearEasing),
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

    // Load custom wallpaper if active in preferences
    val wallpaperBitmap = remember(prefs.customWallpaperUri) {
        if (prefs.customWallpaperUri.isNotBlank()) {
            try {
                val uri = Uri.parse(prefs.customWallpaperUri)
                val stream = context.contentResolver.openInputStream(uri)
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                val bmp = BitmapFactory.decodeStream(stream, null, opts)
                stream?.close()
                bmp?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        } else null
    }

    // Border calculator matching KeyboardCanvasView exactly
    fun getKeyBorder(xRatio: Float, isSpaceOrEnter: Boolean): BorderStroke? {
        return if (theme.isRgbChroma) {
            when (theme.chromaStyle) {
                ChromaStyle.FULL_BORDER -> {
                    val color = computeChromaComposeColor(theme.chromaMode, phase, xRatio)
                    BorderStroke(1.dp, color)
                }
                ChromaStyle.AMBIENT_BREATHE -> {
                    val color = computeChromaComposeColor(theme.chromaMode, phase, xRatio)
                    BorderStroke(0.8.dp, color.copy(alpha = 0.65f))
                }
                ChromaStyle.CLEAN_MINIMAL -> {
                    if (isSpaceOrEnter) {
                        val color = computeChromaComposeColor(theme.chromaMode, phase, xRatio)
                        BorderStroke(1.2.dp, color)
                    } else null
                }
            }
        } else if (prefs.showKeyBorders) {
            BorderStroke(0.8.dp, Color(theme.keyBorderColor).copy(alpha = 0.45f))
        } else null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(theme.backgroundColor)),
        border = BorderStroke(1.2.dp, liveAccentColor.copy(alpha = 0.8f))
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Background wallpaper layer
            if (wallpaperBitmap != null) {
                Image(
                    bitmap = wallpaperBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = prefs.wallpaperOpacity.coerceIn(0.1f, 1f),
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(12.dp))
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Interactive Text Test Strip + Clear
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(theme.keyShiftColor))
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (testInput.isNotBlank()) testInput else (if (isEnglish) "Tap keys to test..." else "টাইপিং টেস্ট করুন..."),
                        color = if (testInput.isNotBlank()) Color(theme.labelColor) else Color(theme.labelDimColor),
                        fontSize = 11.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    if (testInput.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(theme.keyNormalColor))
                                .clickable { testInput = "" }
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text("Clear", fontSize = 9.sp, color = Color(theme.labelDimColor))
                        }
                    }
                }

                // Candidate & Suggestion Strip (Authentic Lekhani Candidate Strip)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Toolbar indicator icon
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(theme.keyShiftColor).copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = Color(theme.labelDimColor).copy(alpha = 0.8f),
                            modifier = Modifier.size(11.dp)
                        )
                    }

                    // Primary center-glowing candidate
                    val primaryBg = if (theme.isRgbChroma) animatedBorderColor else Color(theme.accentColor)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(primaryBg)
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                testInput += if (testInput.isEmpty()) "বাংলা" else " বাংলা"
                            }
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "বাংলা",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (theme.isDark) Color.Black else Color.White
                        )
                    }

                    // Secondary candidates
                    listOf("লেখনী", "প্রবাহ").forEach { word ->
                        val secBorder = getKeyBorder(0.5f, false)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color(theme.keyNormalColor))
                                .then(if (secBorder != null) Modifier.border(secBorder.width, secBorder.brush, RoundedCornerShape(5.dp)) else Modifier)
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    testInput += if (testInput.isEmpty()) word else " $word"
                                }
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = word,
                                fontSize = 10.5.sp,
                                color = Color(theme.labelColor)
                            )
                        }
                    }
                }

                // ── Row 1: Top Alpha Row (10 keys) ────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.5.dp)
                ) {
                    val r1Keys = listOf("আ", "ি", "ী", "ু", "ূ", "প", "ফ", "ব", "ভ", "ম")
                    r1Keys.forEachIndexed { colIdx, ch ->
                        val xRatio = colIdx / 9f
                        val border = getKeyBorder(xRatio, false)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(24.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(theme.keyNormalColor))
                                .then(if (border != null) Modifier.border(border.width, border.brush, RoundedCornerShape(4.dp)) else Modifier)
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    testInput += ch
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(ch, color = Color(theme.labelColor), fontSize = 10.5.sp)
                        }
                    }
                }

                // ── Row 2: Home Row (9 keys) ──────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.5.dp)
                ) {
                    val r2Keys = listOf("অ", "া", "ে", "র", "ত", "থ", "দ", "ধ", "ন")
                    r2Keys.forEachIndexed { colIdx, ch ->
                        val xRatio = (colIdx + 0.5f) / 9f
                        val border = getKeyBorder(xRatio, false)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(24.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(theme.keyNormalColor))
                                .then(if (border != null) Modifier.border(border.width, border.brush, RoundedCornerShape(4.dp)) else Modifier)
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    testInput += ch
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(ch, color = Color(theme.labelColor), fontSize = 10.5.sp)
                        }
                    }
                }

                // ── Row 3: Shift, Alpha & Backspace (9 keys) ──────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.5.dp)
                ) {
                    val shiftBorder = getKeyBorder(0f, false)
                    // Shift
                    Box(
                        modifier = Modifier
                            .weight(1.3f)
                            .height(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(theme.keyShiftColor))
                            .then(if (shiftBorder != null) Modifier.border(shiftBorder.width, shiftBorder.brush, RoundedCornerShape(4.dp)) else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⇧", color = Color(theme.labelDimColor), fontSize = 10.5.sp)
                    }

                    val r3Keys = listOf("ক", "খ", "গ", "ঘ", "ঙ", "স", "হ")
                    r3Keys.forEachIndexed { colIdx, ch ->
                        val xRatio = (colIdx + 1.5f) / 9f
                        val border = getKeyBorder(xRatio, false)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(24.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(theme.keyNormalColor))
                                .then(if (border != null) Modifier.border(border.width, border.brush, RoundedCornerShape(4.dp)) else Modifier)
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    testInput += ch
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(ch, color = Color(theme.labelColor), fontSize = 10.5.sp)
                        }
                    }

                    // Backspace — colored with keyShiftColor, NOT fake neon accent!
                    val bsBorder = getKeyBorder(1f, false)
                    Box(
                        modifier = Modifier
                            .weight(1.3f)
                            .height(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(theme.keyShiftColor))
                            .then(if (bsBorder != null) Modifier.border(bsBorder.width, bsBorder.brush, RoundedCornerShape(4.dp)) else Modifier)
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                if (testInput.isNotEmpty()) testInput = testInput.dropLast(1)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = "Backspace",
                            tint = Color(theme.labelColor),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                // ── Row 4: Symbols, Hasanta, Space & Enter ────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.5.dp)
                ) {
                    val symBorder = getKeyBorder(0f, false)
                    // Symbols switch
                    Box(
                        modifier = Modifier
                            .weight(1.3f)
                            .height(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(theme.keyShiftColor))
                            .then(if (symBorder != null) Modifier.border(symBorder.width, symBorder.brush, RoundedCornerShape(4.dp)) else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("!#1", color = Color(theme.labelDimColor), fontSize = 9.sp)
                    }

                    // Hasanta
                    val hasantaBorder = getKeyBorder(0.2f, false)
                    Box(
                        modifier = Modifier
                            .weight(1.1f)
                            .height(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(theme.keyHasantaColor))
                            .then(if (hasantaBorder != null) Modifier.border(hasantaBorder.width, hasantaBorder.brush, RoundedCornerShape(4.dp)) else Modifier)
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                testInput += "্"
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("্", color = liveAccentColor, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }

                    // Spacebar
                    val spaceBorder = getKeyBorder(0.5f, true)
                    Box(
                        modifier = Modifier
                            .weight(4.2f)
                            .height(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(theme.keySpaceColor))
                            .then(if (spaceBorder != null) Modifier.border(spaceBorder.width, spaceBorder.brush, RoundedCornerShape(4.dp)) else Modifier)
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                testInput += " "
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isEnglish) "Lekhani" else "বাংলা",
                            color = Color(theme.labelDimColor),
                            fontSize = 9.sp
                        )
                    }

                    // Enter Key — authentic return key with liveAccentColor icon!
                    val enterBorder = getKeyBorder(1f, true)
                    Box(
                        modifier = Modifier
                            .weight(1.4f)
                            .height(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(theme.keyShiftColor))
                            .then(if (enterBorder != null) Modifier.border(enterBorder.width, enterBorder.brush, RoundedCornerShape(4.dp)) else Modifier)
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                testInput += "\n"
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardReturn,
                            contentDescription = "Enter",
                            tint = liveAccentColor,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            } // end Column
        } // end Box
    } // end Card
}
