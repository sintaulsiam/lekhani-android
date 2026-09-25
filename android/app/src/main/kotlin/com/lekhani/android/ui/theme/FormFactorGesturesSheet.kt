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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
 * - Spacebar cursor slide navigation
 * - Swipe-to-delete gesture on Backspace
 * - Key glow and spring ripple effects
 */
@Composable
fun FormFactorGesturesSheet(
    prefs: KeyboardPreferences,
    onClose: () -> Unit,
) {
    var formFactor by remember { mutableStateOf(prefs.formFactor) }
    var spaceCursorSlide by remember { mutableStateOf(prefs.spaceCursorSlideEnabled) }
    var swipeToDelete by remember { mutableStateOf(prefs.swipeToDeleteEnabled) }
    var keyGlowRipple by remember { mutableStateOf(prefs.keyGlowRippleEnabled) }

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
                        contentDescription = "Back"
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ফর্ম ফ্যাক্টর ও অঙ্গভঙ্গি",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    formFactor = KeyboardPreferences.FormFactor.STANDARD
                    spaceCursorSlide = true
                    swipeToDelete = true
                    keyGlowRipple = true
                }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset",
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
                        text = "কীবোর্ড মোড ও ফর্ম ফ্যাক্টর (Form Factors)",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF00A87E)
                        )
                    )
                    Text(
                        text = "ডিভাইসের আকার এবং সুবিধাজনক ব্যবহারের ধরন নির্বাচন করুন",
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
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FormFactorOptionItem(
                                title = "মানক পূর্ণ স্ক্রিন (Standard)",
                                subtitle = "সাধারণ পূর্ণ প্রস্থ কীবোর্ড লেআউট",
                                icon = "📱",
                                selected = formFactor == KeyboardPreferences.FormFactor.STANDARD,
                                onSelect = { formFactor = KeyboardPreferences.FormFactor.STANDARD }
                            )

                            FormFactorOptionItem(
                                title = "একহাতে মোড - ডান (One-Handed Right)",
                                subtitle = "ডান পাশে সংকুচিত কীবোর্ড, একহাতে দ্রুত ব্যবহারের জন্য",
                                icon = "👉",
                                selected = formFactor == KeyboardPreferences.FormFactor.ONE_HANDED_RIGHT,
                                onSelect = { formFactor = KeyboardPreferences.FormFactor.ONE_HANDED_RIGHT }
                            )

                            FormFactorOptionItem(
                                title = "একহাতে মোড - বাম (One-Handed Left)",
                                subtitle = "বাম পাশে সংকুচিত কীবোর্ড, একহাতে দ্রুত ব্যবহারের জন্য",
                                icon = "👈",
                                selected = formFactor == KeyboardPreferences.FormFactor.ONE_HANDED_LEFT,
                                onSelect = { formFactor = KeyboardPreferences.FormFactor.ONE_HANDED_LEFT }
                            )

                            FormFactorOptionItem(
                                title = "ভাসমান কীবোর্ড (Floating Window)",
                                subtitle = "স্ক্রিনের যেকোনো জায়গায় টেনে রাখা যায় এমন কমপ্যাক্ট উইন্ডো",
                                icon = "🪟",
                                selected = formFactor == KeyboardPreferences.FormFactor.FLOATING,
                                onSelect = { formFactor = KeyboardPreferences.FormFactor.FLOATING }
                            )

                            FormFactorOptionItem(
                                title = "দ্বিখণ্ডিত মোড (Split Mode)",
                                subtitle = "ট্যাবলেট ও ফোল্ডেবলের দুই বুড়ো আঙুলে আরামদায়ক টাইপিং",
                                icon = "✂️",
                                selected = formFactor == KeyboardPreferences.FormFactor.SPLIT,
                                onSelect = { formFactor = KeyboardPreferences.FormFactor.SPLIT }
                            )
                        }
                    }
                }

                // ── Advanced Gestures Section ────────────────────────────────
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "স্মার্ট অঙ্গভঙ্গি (Smart Gestures)",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF00A87E)
                        )
                    )
                    Text(
                        text = "টাইপিং আরও মসৃণ ও গতিশীল করার স্বজ্ঞাত জেশ্চার",
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
                            // Spacebar cursor slide
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "স্পেসবারে কার্সর স্লাইড (Cursor Slide)",
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = "স্পেসবারে বামে বা ডানে আঙুল টেনে টেক্সটের কার্সর সহজেই নির্দিষ্ট অক্ষরে নেওয়া যায়",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                                Switch(
                                    checked = spaceCursorSlide,
                                    onCheckedChange = { spaceCursorSlide = it }
                                )
                            }

                            // Swipe-to-delete
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "ব্যাকস্পেস সোয়াইপ ডিলিট (Swipe to Delete)",
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = "ব্যাকস্পেস থেকে বামে সোয়াইপ করে এক বা একাধিক সম্পূর্ণ শব্দ একবারে মুছে ফেলা যায়",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                                Switch(
                                    checked = swipeToDelete,
                                    onCheckedChange = { swipeToDelete = it }
                                )
                            }

                            // Key Glow & Spring Ripple
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "কি গ্লো ও স্প্রিং রিপল (Key Glow & Ripple)",
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = "কি স্পর্শে ১২০ FPS স্প্রিং ইনসেট ও উজ্জ্বল থিম রঙের আভা",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                                Switch(
                                    checked = keyGlowRipple,
                                    onCheckedChange = { keyGlowRipple = it }
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
                    Text("বাতিল")
                }
                Button(
                    onClick = {
                        prefs.formFactor = formFactor
                        prefs.spaceCursorSlideEnabled = spaceCursorSlide
                        prefs.swipeToDeleteEnabled = swipeToDelete
                        prefs.keyGlowRippleEnabled = keyGlowRipple
                        onClose()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A87E))
                ) {
                    Text("সংরক্ষণ করুন")
                }
            }
        }
    }
}

@Composable
private fun FormFactorOptionItem(
    title: String,
    subtitle: String,
    icon: String,
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
        Text(text = icon, fontSize = 24.sp, modifier = Modifier.padding(end = 12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) Color(0xFF00A87E) else MaterialTheme.colorScheme.onSurface
                )
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
        RadioButton(
            selected = selected,
            onClick = onSelect
        )
    }
}
