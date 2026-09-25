package com.lekhani.android.ui.theme

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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lekhani.android.data.settings.KeyboardPreferences

/**
 * ErgonomicsSizingSheet
 * ══════════════════════════════════════════════════════════════════════════════
 * Deep customization sheet for keyboard height, margins, chin padding,
 * long-press latency, font style, and borders.
 */
@Composable
fun ErgonomicsSizingSheet(
    prefs: KeyboardPreferences,
    onClose: () -> Unit,
) {
    var heightScale by remember { mutableFloatStateOf(prefs.heightScale) }
    var marginH by remember { mutableFloatStateOf(prefs.keyMarginH) }
    var marginV by remember { mutableFloatStateOf(prefs.keyMarginV) }
    var chinPadding by remember { mutableFloatStateOf(prefs.bottomChinPadding) }
    var longPressDelay by remember { mutableLongStateOf(prefs.longPressDelayMs) }
    var showBorders by remember { mutableStateOf(prefs.showKeyBorders) }
    var fontStyle by remember { mutableStateOf(prefs.fontStyle) }
    var fontScale by remember { mutableFloatStateOf(prefs.fontScale) }

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
                        text = "আকার ও আরগোনোমিক্স",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "কীবোর্ডের উচ্চতা, স্পেসিং, ফন্ট ও বাটন টিউনিং",
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
                // Section 1: Dimensions
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "কীবোর্ড ডাইমেনশন ও উচ্চতা",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Height Slider
                            Text(
                                text = "কীবোর্ড উচ্চতা: ${(heightScale * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Slider(
                                value = heightScale,
                                onValueChange = {
                                    heightScale = it
                                    prefs.heightScale = it
                                },
                                valueRange = 0.8f..1.4f
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Bottom Chin Padding
                            Text(
                                text = "নিচের চিবুক প্যাডিং (Chin Padding): ${chinPadding.toInt()} dp",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Slider(
                                value = chinPadding,
                                onValueChange = {
                                    chinPadding = it
                                    prefs.bottomChinPadding = it
                                },
                                valueRange = 0f..48f
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Horizontal Margin
                            Text(
                                text = "কি হরাইজন্টাল মার্জিন: ${String.format("%.1f", marginH)} dp",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Slider(
                                value = marginH,
                                onValueChange = {
                                    marginH = it
                                    prefs.keyMarginH = it
                                },
                                valueRange = 1.0f..7.0f
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Vertical Spacing
                            Text(
                                text = "রো ভার্টিকাল স্পেসিং: ${String.format("%.1f", marginV)} dp",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Slider(
                                value = marginV,
                                onValueChange = {
                                    marginV = it
                                    prefs.keyMarginV = it
                                },
                                valueRange = 1.0f..7.0f
                            )
                        }
                    }
                }

                // Section 2: Touch & Latency
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "টাচ প্রতিক্রিয়া ও দীর্ঘ প্রেস",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "লং-প্রেস ডিলে: ${longPressDelay} ms",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Slider(
                                value = longPressDelay.toFloat(),
                                onValueChange = {
                                    val rounded = ((it / 25).toInt() * 25).toLong()
                                    longPressDelay = rounded
                                    prefs.longPressDelayMs = rounded
                                },
                                valueRange = 100f..700f
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "কি বর্ডার ও আউটলাইন",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                    )
                                    Text(
                                        text = "প্রতিটি বাটনে পাতলা ফ্রেম প্রদর্শন করুন",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = showBorders,
                                    onCheckedChange = {
                                        showBorders = it
                                        prefs.showKeyBorders = it
                                    }
                                )
                            }
                        }
                    }
                }

                // Section 3: Fonts & Typography
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "বাংলা ফন্ট ও টেক্সট স্কেলিং",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "ফন্ট স্টাইল নির্বাচন করুন",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            val fontOptions = listOf(
                                KeyboardPreferences.FONT_SYSTEM to "সিস্টেম ডিফল্ট",
                                KeyboardPreferences.FONT_SERIF to "সেরিফ (Serif)",
                                KeyboardPreferences.FONT_SANS_SERIF to "স্যান্স-সেরিফ",
                                KeyboardPreferences.FONT_MONOSPACE to "মনোস্পেস",
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                fontOptions.take(2).forEach { (key, label) ->
                                    FilterChip(
                                        selected = fontStyle == key,
                                        onClick = {
                                            fontStyle = key
                                            prefs.fontStyle = key
                                        },
                                        label = { Text(label, fontSize = 12.sp) }
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                fontOptions.drop(2).forEach { (key, label) ->
                                    FilterChip(
                                        selected = fontStyle == key,
                                        onClick = {
                                            fontStyle = key
                                            prefs.fontStyle = key
                                        },
                                        label = { Text(label, fontSize = 12.sp) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "ফন্ট সাইজ স্কেল: ${(fontScale * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Slider(
                                value = fontScale,
                                onValueChange = {
                                    fontScale = it
                                    prefs.fontScale = it
                                },
                                valueRange = 0.8f..1.3f
                            )
                        }
                    }
                }

                // Reset to default button
                item {
                    OutlinedButton(
                        onClick = {
                            heightScale = 1.0f
                            prefs.heightScale = 1.0f
                            marginH = 3.5f
                            prefs.keyMarginH = 3.5f
                            marginV = 4.0f
                            prefs.keyMarginV = 4.0f
                            chinPadding = 0f
                            prefs.bottomChinPadding = 0f
                            longPressDelay = 300L
                            prefs.longPressDelayMs = 300L
                            showBorders = true
                            prefs.showKeyBorders = true
                            fontStyle = KeyboardPreferences.FONT_SYSTEM
                            prefs.fontStyle = KeyboardPreferences.FONT_SYSTEM
                            fontScale = 1.0f
                            prefs.fontScale = 1.0f
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ডিফল্ট মানে রিসেট করুন")
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
                Text("সংরক্ষণ ও সম্পন্ন", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
