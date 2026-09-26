package com.lekhani.android.ui.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lekhani.android.data.settings.KeyboardPreferences

/**
 * FormFactorGesturesSheet
 * ══════════════════════════════════════════════════════════════════════════════
 * Settings sheet for Phase 11:
 * - One-handed mode with quick-dock arrows (left/right)
 * - Floating keyboard mode
 * - Split keyboard mode for foldables & tablets
 * - Spacebar swipe mode (Cursor Slide vs Layout Swap mutex)
 * - Swipe-to-delete gesture on Backspace
 * - Key glow and spring ripple effects
 */
@Composable
fun FormFactorGesturesSheet(
    prefs: KeyboardPreferences,
    isEnglish: Boolean = false,
    onClose: () -> Unit,
) {
    var formFactor by remember { mutableStateOf(prefs.formFactor) }
    var spacebarSwipeMode by remember { mutableStateOf(prefs.spacebarSwipeMode) }
    var swipeToDelete by remember { mutableStateOf(prefs.swipeToDeleteEnabled) }
    var keyGlowRipple by remember { mutableStateOf(prefs.keyGlowRippleEnabled) }
    var glideTyping by remember { mutableStateOf(prefs.glideTypingEnabled) }
    var showKeyPreviews by remember { mutableStateOf(prefs.showKeyPreviews) }

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
                        text = if (isEnglish) "Modes & Gestures" else "কীবোর্ড মোড ও জেশ্চার",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (isEnglish) "Form factors and smart gestures" else "ফর্ম ফ্যাক্টর এবং উন্নত সোয়াইপ জেশ্চার",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    formFactor = KeyboardPreferences.FormFactor.STANDARD
                    spacebarSwipeMode = KeyboardPreferences.SpacebarSwipeMode.CURSOR_NAV
                    swipeToDelete = true
                    keyGlowRipple = true
                    glideTyping = false
                    showKeyPreviews = true
                }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = if (isEnglish) "Reset" else "রিসেট",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Form Factors Section ─────────────────────────────────────
                item {
                    Text(
                        text = if (isEnglish) "Keyboard Form Factors" else "কীবোর্ড লেআউট মোড (Modes)",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF00E5B8)
                        )
                    )
                    Text(
                        text = if (isEnglish) "Select layout based on screen size and typing comfort" else "স্ক্রিন সাইজ ও টাইপিং কমফোর্ট অনুযায়ী নির্বাচন করুন",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FormFactorOptionItem(
                                title = if (isEnglish) "Standard Full Width" else "ফুল স্ক্রিন (Standard Full)",
                                subtitle = if (isEnglish) "Standard full-width keyboard layout" else "সাধারণ স্ট্যান্ডার্ড পূর্ণ প্রস্থ কীবোর্ড",
                                icon = Icons.Filled.Smartphone,
                                selected = formFactor == KeyboardPreferences.FormFactor.STANDARD,
                                onSelect = { formFactor = KeyboardPreferences.FormFactor.STANDARD }
                            )

                            FormFactorOptionItem(
                                title = if (isEnglish) "One-Handed (Right)" else "একহাতে মোড - ডান (One-Handed Right)",
                                subtitle = if (isEnglish) "Compact keyboard docked to right side" else "ডান পাশে সংকুচিত কীবোর্ড, একহাতে দ্রুত ব্যবহারের জন্য",
                                icon = Icons.AutoMirrored.Filled.ArrowForward,
                                selected = formFactor == KeyboardPreferences.FormFactor.ONE_HANDED_RIGHT,
                                onSelect = { formFactor = KeyboardPreferences.FormFactor.ONE_HANDED_RIGHT }
                            )

                            FormFactorOptionItem(
                                title = if (isEnglish) "One-Handed (Left)" else "একহাতে মোড - বাম (One-Handed Left)",
                                subtitle = if (isEnglish) "Compact keyboard docked to left side" else "বাম পাশে সংকুচিত কীবোর্ড, একহাতে দ্রুত ব্যবহারের জন্য",
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                selected = formFactor == KeyboardPreferences.FormFactor.ONE_HANDED_LEFT,
                                onSelect = { formFactor = KeyboardPreferences.FormFactor.ONE_HANDED_LEFT }
                            )

                            FormFactorOptionItem(
                                title = if (isEnglish) "Floating Window" else "ভাসমান উইন্ডো (Floating Window)",
                                subtitle = if (isEnglish) "Movable compact window anywhere on screen" else "স্ক্রিনের যেকোনো জায়গায় টেনে রাখা যায় এমন কমপ্যাক্ট উইন্ডো",
                                icon = Icons.Filled.PictureInPictureAlt,
                                selected = formFactor == KeyboardPreferences.FormFactor.FLOATING,
                                onSelect = { formFactor = KeyboardPreferences.FormFactor.FLOATING }
                            )

                            FormFactorOptionItem(
                                title = if (isEnglish) "Split Mode" else "স্প্লিট মোড (Split Mode)",
                                subtitle = if (isEnglish) "Ergonomic two-thumb typing for tablets and foldables" else "ট্যাবলেট ও ফোল্ডেবলের দুই বুড়ো আঙুলে আরামদায়ক টাইপিং",
                                icon = Icons.Filled.VerticalSplit,
                                selected = formFactor == KeyboardPreferences.FormFactor.SPLIT,
                                onSelect = { formFactor = KeyboardPreferences.FormFactor.SPLIT }
                            )
                        }
                    }
                }

                // ── Spacebar Swipe Action (Mutex Choice) ────────────────────
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isEnglish) "Spacebar Swipe Action" else "স্পেসবার সোয়াইপ অ্যাকশন (Spacebar Action)",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF00E5B8)
                        )
                    )
                    Text(
                        text = if (isEnglish) "Slide finger on spacebar to navigate cursor or switch layouts" else "স্পেসবারে আঙুল টেনে কার্সার নাকি ভাষা পরিবর্তন করতে চান নির্বাচন করুন",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SwipeModeOptionItem(
                                title = if (isEnglish) "Cursor Slide Navigation" else "কার্সার স্লাইড (Cursor Slide)",
                                subtitle = if (isEnglish) "Slide finger horizontally across spacebar to move cursor" else "স্পেসবারে বামে বা ডানে আঙুল টেনে টেক্সটের কার্সর সূক্ষ্মভাবে সরান",
                                selected = spacebarSwipeMode == KeyboardPreferences.SpacebarSwipeMode.CURSOR_NAV,
                                onSelect = { spacebarSwipeMode = KeyboardPreferences.SpacebarSwipeMode.CURSOR_NAV }
                            )

                            SwipeModeOptionItem(
                                title = if (isEnglish) "Quick Layout Switch" else "লেআউট পরিবর্তন (Layout Switch)",
                                subtitle = if (isEnglish) "Swipe across spacebar to switch between English and Bengali" else "স্পেসবারে সোয়াইপ করে ইংরেজি ও বাংলা লেআউটে দ্রুত অদলবদল করুন",
                                selected = spacebarSwipeMode == KeyboardPreferences.SpacebarSwipeMode.LAYOUT_SWITCH,
                                onSelect = { spacebarSwipeMode = KeyboardPreferences.SpacebarSwipeMode.LAYOUT_SWITCH }
                            )

                            SwipeModeOptionItem(
                                title = if (isEnglish) "Disabled" else "নিষ্ক্রিয় (Disabled)",
                                subtitle = if (isEnglish) "Disable swipe gestures on spacebar" else "স্পেসবারে সোয়াইপ জেশ্চার বন্ধ রাখুন",
                                selected = spacebarSwipeMode == KeyboardPreferences.SpacebarSwipeMode.DISABLED,
                                onSelect = { spacebarSwipeMode = KeyboardPreferences.SpacebarSwipeMode.DISABLED }
                            )
                        }
                    }
                }

                // ── Advanced Gestures Section ────────────────────────────────
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isEnglish) "Smart Gestures & Feedback" else "স্মার্ট জেশ্চার ও স্পর্শ প্রভাব (Gestures)",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF00E5B8)
                        )
                    )
                    Text(
                        text = if (isEnglish) "Enhance typing fluidness with gestures and haptic feedback" else "টাইপিং মসৃণ ও গতিশীল করার স্বজ্ঞাত জেশ্চার",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            // Swipe-to-delete
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isEnglish) "Swipe to Delete" else "সোয়াইপ ডিলিট (Swipe to Delete)",
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = if (isEnglish) "Drag left from Backspace key to delete whole words" else "ব্যাকস্পেস কি থেকে বামে টেনে একাধিক শব্দ একবারে মুছে ফেলুন",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Switch(
                                    checked = swipeToDelete,
                                    onCheckedChange = { swipeToDelete = it }
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            // Key Glow & Spring Ripple
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isEnglish) "Key Glow & Ripple" else "কি গ্লো ও রিপল (Key Glow & Ripple)",
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = if (isEnglish) "120 FPS spring inset and theme accent glow on touch" else "কি স্পর্শে ১২০ FPS স্প্রিং ইনসেট ও থিম আভা ইফেক্ট",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Switch(
                                    checked = keyGlowRipple,
                                    onCheckedChange = { keyGlowRipple = it }
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            // Key Popups (Character preview bubble)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isEnglish) "Key Popups (Preview Bubble)" else "কি পপআপ প্রিভিউ (Key Popups)",
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = if (isEnglish) "Show magnified preview bubble above pressed key" else "কি স্পর্শ করলে উপরে অক্ষরের পপআপ প্রিভিউ বুদবুদ প্রদর্শন",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Switch(
                                    checked = showKeyPreviews,
                                    onCheckedChange = { showKeyPreviews = it }
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            // Glide / Gesture Typing
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isEnglish) "Glide / Gesture Typing" else "গ্লাইড টাইপিং (Glide / Gesture Typing)",
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = if (isEnglish) "Swipe continuous path across keys to type (Off by default)" else "কি-বোর্ডে আঙুল না তুলে সোয়াইপ করে দ্রুত টাইপ করুন (ডিফল্ট বন্ধ)",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Switch(
                                    checked = glideTyping,
                                    onCheckedChange = { glideTyping = it }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onClose,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (isEnglish) "Cancel" else "বাতিল")
                }
                Button(
                    onClick = {
                        prefs.formFactor = formFactor
                        prefs.spacebarSwipeMode = spacebarSwipeMode
                        prefs.swipeToDeleteEnabled = swipeToDelete
                        prefs.keyGlowRippleEnabled = keyGlowRipple
                        prefs.glideTypingEnabled = glideTyping
                        prefs.showKeyPreviews = showKeyPreviews
                        onClose()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A87E))
                ) {
                    Text(if (isEnglish) "Save & Apply" else "সংরক্ষণ করুন")
                }
            }
        }
    }
}

@Composable
private fun FormFactorOptionItem(
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
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = if (selected) Color(0xFF00E5B8) else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) Color(0xFF00E5B8) else MaterialTheme.colorScheme.onSurface
                )
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        RadioButton(
            selected = selected,
            onClick = onSelect
        )
    }
}

@Composable
private fun SwipeModeOptionItem(
    title: String,
    subtitle: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) Color(0xFF00E5B8) else MaterialTheme.colorScheme.onSurface
                )
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        RadioButton(
            selected = selected,
            onClick = onSelect
        )
    }
}
