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
import androidx.compose.material.icons.filled.ArrowBack
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

/**
 * ThemeStudioSheet
 * ══════════════════════════════════════════════════════════════════════════════
 * Material 3 Expressive theme customization studio.
 * Supports built-in themes, Material You dynamic wallpaper color matching,
 * and custom background wallpaper with opacity adjustment.
 */
@Composable
fun ThemeStudioSheet(
    prefs: KeyboardPreferences,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var selectedThemeId by remember { mutableStateOf(prefs.themeId) }
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
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "ফিরে যান",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "থিম ও কালার স্টুডিও",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "কীবোর্ডের ভিজ্যুয়াল লুক এবং ওয়ালপেপার পরিবর্তন করুন",
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
                // Section 1: Themes
                item {
                    Text(
                        text = "কালার প্যালেট ও প্রিসেট",
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
                        onSelect = {
                            selectedThemeId = theme.id
                            prefs.themeId = theme.id
                        }
                    )
                }

                // Section 2: Wallpaper Background
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "কাস্টম ওয়ালপেপার ব্যাকগ্রাউন্ড",
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
                                        contentDescription = "Wallpaper",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = if (wallpaperUri.isNotBlank()) "ওয়ালপেপার যুক্ত হয়েছে" else "কোনো ওয়ালপেপার নেই",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                    )
                                }

                                Row {
                                    Button(
                                        onClick = { photoPickerLauncher.launch("image/*") },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("নির্বাচন")
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
                                                contentDescription = "ওয়ালপেপার মুছুন",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }

                            if (wallpaperUri.isNotBlank()) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "স্বচ্ছতা (Opacity): ${(wallpaperOpacity * 100).toInt()}%",
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
                Text("সম্পন্ন", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ThemePreviewCard(
    theme: KeyboardTheme,
    isSelected: Boolean,
    onSelect: () -> Unit,
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
                    text = theme.nameBengali,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(theme.labelColor)
                )
                Text(
                    text = theme.nameEnglish,
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
                        Text("Space", color = Color(theme.labelDimColor), fontSize = 11.sp)
                    }
                }
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(theme.accentColor)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = if (theme.isDark) Color.Black else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
