package com.lekhani.android.ui.theme

import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lekhani.android.data.settings.KeyboardPreferences
import com.lekhani.android.theme.KeyboardTheme
import com.lekhani.android.theme.ThemeRegistry

import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.BorderStroke
import com.lekhani.android.theme.CustomThemeManager

/**
 * ThemeStudioSheet
 * ══════════════════════════════════════════════════════════════════════════════
 * Material 3 Expressive theme customization studio.
 * Supports built-in themes, Material You dynamic wallpaper color matching,
 * independent app appearance mode, and custom background wallpaper with opacity adjustment.
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
    val customThemeManager = remember { CustomThemeManager.get(context) }
    var customThemes by remember { mutableStateOf(customThemeManager.getAllCustomThemes()) }
    var themeToEdit by remember { mutableStateOf<KeyboardTheme?>(null) }
    var showEditorDialog by remember { mutableStateOf(false) }

    var selectedThemeId by remember { mutableStateOf(prefs.themeId) }
    var selectedAppThemeMode by remember { mutableStateOf(prefs.appThemeMode) }
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
                        text = if (isEnglish) "Customize keyboard colors and background wallpaper" else "কীবোর্ডের ভিজ্যুয়াল লুক এবং ওয়ালপেপার পরিবর্তন করুন",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section 0: App Appearance
                item {
                    Text(
                        text = if (isEnglish) "App Appearance" else "অ্যাপের থিম মোড",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val modes = listOf(
                            Triple(KeyboardPreferences.AppThemeMode.SYSTEM, Icons.Default.BrightnessAuto, if (isEnglish) "System" else "সিস্টেম"),
                            Triple(KeyboardPreferences.AppThemeMode.LIGHT, Icons.Default.LightMode, if (isEnglish) "Light" else "লাইট"),
                            Triple(KeyboardPreferences.AppThemeMode.DARK, Icons.Default.DarkMode, if (isEnglish) "Dark" else "ডার্ক"),
                            Triple(KeyboardPreferences.AppThemeMode.MATCH_KEYBOARD, Icons.Default.Keyboard, if (isEnglish) "Keyboard" else "অনুরূপ")
                        )
                        modes.forEach { (mode, icon, title) ->
                            val isSelected = selectedAppThemeMode == mode
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        selectedAppThemeMode = mode
                                        prefs.appThemeMode = mode
                                        onAppThemeModeChanged?.invoke(mode)
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = title,
                                        modifier = Modifier.size(20.dp),
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 11.sp
                                        ),
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                // Section 1: Custom Themes
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEnglish) "Custom Themes" else "কাস্টম থিমসমূহ",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedButton(
                            onClick = {
                                val baseTheme = ThemeRegistry.resolveTheme(context, selectedThemeId)
                                themeToEdit = baseTheme.copy(
                                    id = "custom_${System.currentTimeMillis()}",
                                    nameBengali = "আমার থিম",
                                    nameEnglish = "My Custom Theme",
                                    isCustom = true
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
                }

                if (customThemes.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            )
                        ) {
                            Text(
                                text = if (isEnglish) "No custom themes created yet. Tap '+ Create' or duplicate any preset below!"
                                       else "কোনো কাস্টম থিম নেই। '+ নতুন থিম' চাপুন বা নিচের যেকোনো প্রিসেট ডুপ্লিকেট করে এডিট করুন!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                } else {
                    items(customThemes.size) { idx ->
                        val customTheme = customThemes[idx]
                        val isSelected = selectedThemeId == customTheme.id
                        ThemePreviewCard(
                            theme = customTheme,
                            isSelected = isSelected,
                            isEnglish = isEnglish,
                            onSelect = {
                                selectedThemeId = customTheme.id
                                prefs.themeId = customTheme.id
                                onThemeChanged?.invoke(customTheme.id)
                            },
                            onEdit = {
                                themeToEdit = customTheme
                                showEditorDialog = true
                            },
                            onDuplicate = {
                                val dup = customThemeManager.duplicateTheme(
                                    customTheme,
                                    if (isEnglish) "${customTheme.nameEnglish} (Copy)" else "${customTheme.nameBengali} (কপি)"
                                )
                                customThemes = customThemeManager.getAllCustomThemes()
                                selectedThemeId = dup.id
                                prefs.themeId = dup.id
                                onThemeChanged?.invoke(dup.id)
                            },
                            onDelete = {
                                customThemeManager.deleteCustomTheme(customTheme.id)
                                customThemes = customThemeManager.getAllCustomThemes()
                                if (selectedThemeId == customTheme.id) {
                                    val fallback = ThemeRegistry.THEME_FLOW_TEAL.id
                                    selectedThemeId = fallback
                                    prefs.themeId = fallback
                                    onThemeChanged?.invoke(fallback)
                                }
                            }
                        )
                    }
                }

                // Section 2: Preset Themes
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isEnglish) "Keyboard Color Palettes & Presets" else "কীবোর্ড কালার প্যালেট ও প্রিসেট",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                val allThemes = ThemeRegistry.PRESET_THEMES + listOf(
                    ThemeRegistry.createMaterialYouTheme(context)
                )

                items(allThemes.size) { idx ->
                    val theme = allThemes[idx]
                    val isSelected = selectedThemeId == theme.id

                    ThemePreviewCard(
                        theme = theme,
                        isSelected = isSelected,
                        isEnglish = isEnglish,
                        onSelect = {
                            selectedThemeId = theme.id
                            prefs.themeId = theme.id
                            onThemeChanged?.invoke(theme.id)
                        },
                        onDuplicate = {
                            val dup = customThemeManager.duplicateTheme(
                                theme,
                                if (isEnglish) "${theme.nameEnglish} (Custom)" else "${theme.nameBengali} (কাস্টম)"
                            )
                            customThemes = customThemeManager.getAllCustomThemes()
                            themeToEdit = dup
                            showEditorDialog = true
                        }
                    )
                }

                // Section 2: Wallpaper Background
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isEnglish) "Custom Wallpaper Background" else "কাস্টম ওয়ালপেপার ব্যাকগ্রাউন্ড",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
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
                                            if (isEnglish) "Wallpaper Applied" else "ওয়ালপেপার যুক্ত হয়েছে"
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
                                        Text(if (isEnglish) "Choose Image" else "ছবি নির্বাচন করুন")
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = if (isSelected) 2.5.dp else 1.dp,
                color = if (isSelected) Color(theme.accentColor) else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(theme.backgroundColor))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isEnglish) theme.nameEnglish else theme.nameBengali,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(theme.labelColor)
                )
                Text(
                    text = if (isEnglish) theme.nameBengali else theme.nameEnglish,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(theme.labelDimColor)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Mini Key Swatches
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp, 32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(theme.keyNormalColor))
                            .border(1.dp, Color(theme.keyBorderColor), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("ক", color = Color(theme.labelColor), fontSize = 13.sp)
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp, 32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(theme.keyShiftColor))
                            .border(1.dp, Color(theme.keyBorderColor), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⇧", color = Color(theme.labelDimColor), fontSize = 12.sp)
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp, 32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(theme.keyHasantaColor))
                            .border(1.dp, Color(theme.keyBorderColor), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("্", color = Color(theme.accentColor), fontSize = 13.sp)
                    }

                    Box(
                        modifier = Modifier
                            .size(72.dp, 32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(theme.keySpaceColor))
                            .border(1.dp, Color(theme.keyBorderColor), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (isEnglish) "Space" else "স্পেস", color = Color(theme.labelDimColor), fontSize = 11.sp)
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
                            .background(Color(theme.accentColor)),
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
