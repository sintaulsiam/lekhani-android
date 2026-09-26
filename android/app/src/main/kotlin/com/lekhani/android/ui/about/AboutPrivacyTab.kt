package com.lekhani.android.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AboutPrivacyTab(
    isEnglish: Boolean = false,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 18.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Brand Header (With 'লে' Logo) ───────────────────────────────────────
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF00E5B8), Color(0xFF006C50), Color(0xFF051C14))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "লে",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (isEnglish) "Lekhani Bengali Keyboard" else "লেখনী কীবোর্ড",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
            Text(
                text = if (isEnglish) "Next-Gen Ergonomic Bengali Keyboard • 2026 Edition"
                       else "নেক্সট-জেন এরগনোমিক বাংলা কীবোর্ড • ২০২৬ এডিশন",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Spacer(modifier = Modifier.height(6.dp))
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
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                )
            }
        }

        // ── Developer & Organization Profile Card (Syntenium & BRUR CSE) ───────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Business,
                        contentDescription = "Organization",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isEnglish) "Developer & Organization" else "ডেভেলপার ও প্রতিষ্ঠান",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Developer: Sintaul Mahdi Siam
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = "Developer",
                        modifier = Modifier.size(18.dp),
                        tint = Color(0xFF00E5B8)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isEnglish) "Developer:" else "ডেভেলপার:",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.width(100.dp)
                    )
                    Text(
                        text = if (isEnglish) "Sintaul Mahdi Siam" else "সিনতাউল মাহদী সিয়াম",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Organization: Syntenium
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Business,
                        contentDescription = "Organization",
                        modifier = Modifier.size(18.dp),
                        tint = Color(0xFF00E5B8)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isEnglish) "Organization:" else "প্রতিষ্ঠান:",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.width(100.dp)
                    )
                    Text(
                        text = "Syntenium",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E5B8)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Academic Background: BRUR CSE
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Filled.School,
                        contentDescription = "Education",
                        modifier = Modifier
                            .size(18.dp)
                            .padding(top = 2.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (isEnglish) "Department of Computer Science & Engineering (CSE)"
                                   else "কম্পিউটার সায়েন্স অ্যান্ড ইঞ্জিনিয়ারিং বিভাগ (CSE)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = if (isEnglish) "Begum Rokeya University, Rangpur (BRUR)"
                                   else "বেগম রোকেয়া বিশ্ববিদ্যালয়, রংপুর (BRUR)",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Developer Email Contact Link
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:sintaulsiam@gmail.com")
                                putExtra(Intent.EXTRA_SUBJECT, "Lekhani Keyboard - Inquiry for Sintaul Mahdi Siam")
                            }
                            runCatching { context.startActivity(intent) }
                        }
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Email,
                        contentDescription = "Email",
                        modifier = Modifier.size(18.dp),
                        tint = Color(0xFF00E5B8)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isEnglish) "Developer Email" else "ডেভেলপারের ইমেইল",
                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                        Text(
                            text = "sintaulsiam@gmail.com",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00E5B8)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Organization Email Contact Link
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:syntenium@gmail.com")
                                putExtra(Intent.EXTRA_SUBJECT, "Lekhani Keyboard - Syntenium Support")
                            }
                            runCatching { context.startActivity(intent) }
                        }
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.MailOutline,
                        contentDescription = "Organization Email",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isEnglish) "Organization Email" else "প্রতিষ্ঠানের ইমেইল",
                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                        Text(
                            text = "syntenium@gmail.com",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }
        }

        // ── 100% Offline & Privacy Guarantee Card ──────────────────────────────
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
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isEnglish) "100% Offline & Privacy Guarantee" else "১০০% অফলাইন ও সম্পূর্ণ গোপনীয়তা",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8CF4CB)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                PrivacyFeatureItem(
                    title = if (isEnglish) "Zero Network Permission" else "শূন্য নেটওয়ার্ক পারমিশন (Zero Network)",
                    desc = if (isEnglish) "The app has no INTERNET permission. No keystrokes or data can ever leave your device."
                           else "অ্যাপটিতে কোনো ইন্টারনেট অনুমতি নেই। কোনো কি-স্ট্রোক বা ডেটা ডিভাইস থেকে বের হওয়া অসম্ভব।"
                )
                PrivacyFeatureItem(
                    title = if (isEnglish) "On-Device Engine & Scorer" else "অন-ডিভাইস ইঞ্জিন ও স্কোরার",
                    desc = if (isEnglish) "Grammar parsing and N-gram scoring run 100% locally via native Rust binaries."
                           else "ব্যাকরণ ও N-gram প্রেডিকশন সরাসরি ফোনের প্রসেসরে স্থানীয়ভাবে এক্সিকিউট হয়।"
                )
                PrivacyFeatureItem(
                    title = if (isEnglish) "Device-Protected Storage" else "ডিভাইস সুরক্ষিত স্টোরেজ",
                    desc = if (isEnglish) "Direct Boot compliant: User dictionary is encrypted in private app storage."
                           else "ডিরেক্ট বুট সম্বলিত: ব্যক্তিগত ডিকশনারি ও ক্লিপবোর্ড লোকাল এনক্রিপশনে সুরক্ষিত থাকে।"
                )
            }
        }

        // ── Three-Tier Architecture Card ───────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Memory,
                        contentDescription = "Architecture",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isEnglish) "Three-Tier Architecture" else "থ্রি-টিয়ার আর্কিটেকচার",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                ArchitectureTierItem(
                    tier = "Tier 1: Pure Rust Engine",
                    detail = "lekhani-parser (11 ns trie) • lekhani-ai (N-gram Scorer) • lekhani-core"
                )
                ArchitectureTierItem(
                    tier = "Tier 2: Native FFI Bridge",
                    detail = "crates/lekhani-android (Zero-allocation UniFFI C-ABI) • AndroidLekhaniSession"
                )
                ArchitectureTierItem(
                    tier = "Tier 3: Android Native Layer",
                    detail = "KeyboardCanvasView (120 FPS Hardware Canvas) • Material 3 Expressive UI"
                )
            }
        }

        // ── Open Source & Community ────────────────────────────────────────────
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
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isEnglish) "Open Source & Standards" else "ওপেন সোর্স ও স্ট্যান্ডার্ড",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isEnglish)
                        "Lekhani is open source under MIT / Apache-2.0. Compliant with BBS National Bengali Standard and OpenBangla keyboard corpus."
                    else
                        "লেখনী প্রজেক্ট MIT ও Apache-2.0 লাইসেন্সে উন্মুক্ত। বাংলাদেশ সরকারি বিবিএস জাতীয় মান এবং ওপেনবাংলা কিবোর্ড স্ট্যান্ডার্ড অনুযায়ী নির্মিত।",
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
