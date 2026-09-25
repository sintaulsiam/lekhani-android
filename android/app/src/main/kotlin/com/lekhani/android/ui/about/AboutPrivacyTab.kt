package com.lekhani.android.ui.about

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AboutPrivacyTab(
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 18.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Brand Header ────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF00E5B8), Color(0xFF006C50), Color(0xFF051C14))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "ল",
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "লেখনী কীবোর্ড",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
            Text(
                text = "Lekhani Bengali Keyboard • 2026 Edition",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF00E5B8).copy(alpha = 0.15f)
            ) {
                Text(
                    text = "v0.1.0 • 100% Offline • Zero Telemetry",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFF00E5B8),
                        fontWeight = FontWeight.SemiBold
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }

        // ── Privacy & Security Guarantee Card ──────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF0C241B)
            )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.VerifiedUser,
                        contentDescription = "Privacy",
                        tint = Color(0xFF00E5B8),
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "গোপনীয়তা ও নিরাপত্তা (Privacy Guarantee)",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8CF4CB)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                PrivacyFeatureItem(
                    title = "১০০% অফলাইন (Zero Network)",
                    desc = "এই অ্যাপ্লিকেশনে কোনো ইন্টারনেট পারমিশন (android.permission.INTERNET) নেই। আপনার কোনো ডেটা ক্লাউডে পাঠানো সম্ভব নয়।"
                )
                PrivacyFeatureItem(
                    title = "অন-ডিভাইস এআই (On-Device AI)",
                    desc = "N-gram ভাষা মডেল ও ব্যাকরণ অ্যালগরিদম সরাসরি আপনার ফোনে এক্সিকিউট হয়, কোনো সার্ভার কল ছাড়াই।"
                )
                PrivacyFeatureItem(
                    title = "নিরাপদ কি-স্ট্রোক (No Keylogging)",
                    desc = "টাইপিং হিস্ট্রি ও ক্লিপবোর্ড ডেটা কেবল আপনার ডিভাইসের এনক্রিপ্টেড স্টোরেজে স্থানীয়ভাবে সংরক্ষিত থাকে।"
                )
            }
        }

        // ── Three-Tier Architecture Card ───────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Memory,
                        contentDescription = "Architecture",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "ইঞ্জিন আর্কিটেকচার (Architecture)",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                ArchitectureTierItem(
                    tier = "Tier 1: Pure Rust Engine",
                    detail = "lekhani-parser (11 ns Avro trie) • lekhani-ai (N-gram Scorer) • lekhani-core (IME state machine)"
                )
                ArchitectureTierItem(
                    tier = "Tier 2: Native FFI Bridge",
                    detail = "crates/lekhani-android (Zero-allocation UniFFI C-ABI) • AndroidLekhaniSession"
                )
                ArchitectureTierItem(
                    tier = "Tier 3: Android Native Layer",
                    detail = "LekhaniInputMethodService • KeyboardCanvasView (Hardware Canvas 120 FPS) • Jetpack Compose M3"
                )
            }
        }

        // ── Layout Standards Card ──────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Keyboard,
                        contentDescription = "Layouts",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "সমর্থিত লেআউট স্ট্যান্ডার্ডস (Layouts)",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "• Lekhani প্রবাহ (Flow): এরগনোমিক টু-থাম্ব স্বরবর্ণ ও ব্যঞ্জনবর্ণ ইঞ্জিন\n" +
                           "• অভ্র ফোনেটিক (Avro): পরিচিত ফোনেটিক ট্রান্সলিটারেশন ও ডিকশনারি\n" +
                           "• জাতীয় (BBS National): বাংলাদেশ সরকারি মানসম্মত স্ট্যান্ডার্ড\n" +
                           "• প্রভাত (Probhat): জনপ্রিয় ফিক্সড ফোনেটিক লেআউট\n" +
                           "• জি-বোর্ড বাংলা (Gboard Style): পরিচিত গুগল কি-ম্যাপিং\n" +
                           "• ইংরেজি (English QWERTY): দ্বিভাষিক আলফানিউমেরিক টাইপিং",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp
                    )
                )
            }
        }

        // ── Credits & Licenses ─────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Code,
                        contentDescription = "Open Source",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "স্বত্ব ও মুক্ত উৎস (Open Source)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Lekhani Project is open source under MIT / Apache-2.0 licenses. Special thanks to OpenBangla keyboard community and BBS for phonetic standards and corpus data.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PrivacyFeatureItem(title: String, desc: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = "• $title",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF00E5B8)
            )
        )
        Text(
            text = desc,
            style = MaterialTheme.typography.bodySmall.copy(
                color = Color(0xFFB0D0C4)
            ),
            modifier = Modifier.padding(start = 14.dp, top = 2.dp)
        )
    }
}

@Composable
private fun ArchitectureTierItem(tier: String, detail: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = tier,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.padding(start = 8.dp, top = 2.dp)
        )
    }
}
