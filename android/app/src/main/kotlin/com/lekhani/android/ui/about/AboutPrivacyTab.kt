package com.lekhani.android.ui.about

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lekhani.android.ui.components.LekhaniBrandLogo

private const val SUPPORT_EMAIL = "sintaulsiam@gmail.com"
private const val GITHUB_REPO_URL = "https://github.com/sintaulsiam/lekhani-android"

/**
 * AboutPrivacyTab
 * ══════════════════════════════════════════════════════════════════════════════
 * Clean, user-centric About screen:
 * 1. Brand Header (M3 circle logo, version badge with accessible dev unlock)
 * 2. 100% Offline & Privacy Guarantee (Primary user assurance)
 * 3. What's New in v0.2.0 (Interactive feature highlights)
 * 4. Compact Creator & Institution Credits + Share App Action
 * 5. Open Source & Licensing
 * 6. Collapsible Engineering & Architecture Specifications
 */
@Composable
fun AboutPrivacyTab(
    isEnglish: Boolean = false,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    var showWhatsNew by remember { mutableStateOf(false) }
    var showTechSpecs by remember { mutableStateOf(false) }
    var devTapCount by remember { mutableIntStateOf(0) }
    val isDevUnlocked = devTapCount >= 7

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── 1. Brand Header ─────────────────────────────────────────────────────
        LekhaniBrandLogo(size = 68.dp)

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (isEnglish) "Lekhani Keyboard" else "লেখনী কীবোর্ড",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
            Text(
                text = if (isEnglish) "Fast, private Bengali keyboard for Android"
                       else "সম্পূর্ণ অফলাইন, নিরাপদ ও দ্রুত বাংলা কীবোর্ড",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        if (devTapCount < 7) {
                            devTapCount++
                            if (devTapCount == 7) {
                                Toast.makeText(
                                    context,
                                    if (isEnglish) "Developer specifications unlocked"
                                    else "ডেভেলপার স্পেসিফিকেশন উন্মুক্ত করা হয়েছে",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                    .semantics {
                        role = Role.Button
                        contentDescription = "Version ${com.lekhani.android.BuildConfig.VERSION_NAME}, 100 percent offline, zero telemetry"
                    }
            ) {
                Text(
                    text = "v${com.lekhani.android.BuildConfig.VERSION_NAME} • 100% Offline • Zero Telemetry" +
                        if (devTapCount in 1..6) " (${7 - devTapCount})" else "",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }

        // ── 2. Primary: 100% Offline & Privacy Assurance ────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.VerifiedUser,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isEnglish) "100% Offline & Private" else "১০০% অফলাইন ও সম্পূর্ণ গোপনীয়",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                PrivacyFeatureItem(
                    title = if (isEnglish) "No Internet Permission" else "ইন্টারনেট পারমিশনহীন",
                    desc = if (isEnglish) "Lekhani declares zero network access. No keystrokes or data can ever leave your phone."
                           else "অ্যাপটিতে কোনো ইন্টারনেট পারমিশন নেই। আপনার কোনো টাইピング বা ক্লিপবোর্ড ডেটা বাইরে যাওয়া সম্ভব নয়।"
                )
                PrivacyFeatureItem(
                    title = if (isEnglish) "On-Device Engine" else "অন-ডিভাইস প্রসেসিং",
                    desc = if (isEnglish) "Grammar parsing and next-word suggestions run 100% locally on your phone's processor."
                           else "শব্দ সাজেশন ও ব্যাকরণ পার্সিং সবকিছু সরাসরি আপনার ফোনেই প্রসেস হয়।"
                )
                PrivacyFeatureItem(
                    title = if (isEnglish) "Device-Protected Storage" else "ডিভাইসেই সুরক্ষিত সেভ",
                    desc = if (isEnglish) "Personal learned words and clipboard items are stored securely on your device and never shared with anyone."
                           else "ব্যক্তিগত ডিকশনারি ও ক্লিপবোর্ড আপনার ফোনেই সুরক্ষিত থাকে এবং কখনোই কারো সাথে শেয়ার করা হয় না।"
                )
            }
        }

        // ── 3. What's New in v0.2.0 ────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showWhatsNew = !showWhatsNew }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.NewReleases,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isEnglish) "What's New in v0.2.0" else "নতুন কী কী যোগ হয়েছে (v0.2.0)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                    Icon(
                        imageVector = if (showWhatsNew) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (showWhatsNew) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedVisibility(visible = showWhatsNew) {
                    Column(
                        modifier = Modifier.padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        HighlightItem(
                            title = if (isEnglish) "Smart Swipe-to-Delete & Two-Thumb Actions"
                                    else "স্মার্ট সোয়াইপ ডিলিট ও টু-থাম্ব অ্যাকশন",
                            detail = if (isEnglish) "Drag left from Backspace to preview tokens before deleting. Use your second thumb to Copy or Cut selected text instantly."
                                     else "ব্যাকস্পেস চেপে বামে টেনে শব্দ সিলেক্ট করুন। সিলেক্ট থাকা অবস্থায় দ্বিতীয় হাত দিয়ে কপি বা কাট করুন।"
                        )
                        HighlightItem(
                            title = if (isEnglish) "Contextual Selection Toolbar"
                                    else "কনটেক্সচুয়াল টেক্সট টুলবার",
                            detail = if (isEnglish) "Instant Cut, Copy, Paste, and Select All buttons appear directly in the suggestion strip when text is selected."
                                     else "টেক্সট সিলেক্ট করা থাকলে ক্যান্ডিডেট বারে স্বয়ংক্রিয়ভাবে কাট, কপি, পেস্ট ও সিলেক্ট অল বাটন ভেসে ওঠে।"
                        )
                        HighlightItem(
                            title = if (isEnglish) "100% Offline Streaming Voice Typing"
                                    else "১০০% অফলাইন ভয়েস টাইপিং",
                            detail = if (isEnglish) "Speak Bengali or English naturally with live animated audio waveform and real-time on-device transcription."
                                     else "অডিও অ্যানিমেশন ও লাইভ টেক্সট প্রিভিউসহ সম্পূর্ণ ইন্টারনেট ছাড়া ফোনে ভয়েস টাইপিং করুন।"
                        )
                        HighlightItem(
                            title = if (isEnglish) "Dynamic RGB Chroma & Custom Themes"
                                    else "ডাইনামিক আরজিবি ও কাস্টম থিম",
                            detail = if (isEnglish) "120 FPS chromatic wave animation, custom wallpaper backgrounds, and refined Material 3 Expressive styling."
                                     else "১২০ এফপিএস স্মুথ আরজিবি লাইটিং, নিজস্ব ওয়ালপেপার এবং ম্যাটেরিয়াল ৩ কালার প্যালেট।"
                        )
                    }
                }
            }
        }

        // ── 4. Creator, Institution & Actions ──────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Business,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isEnglish) "Creator & Organization" else "ডেভেলপার ও প্রতিষ্ঠান",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 2.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isEnglish) "Developer: Sintaul Mahdi Siam" else "ডেভেলপার: সিনতাউল মাহদী সিয়াম",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.School,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isEnglish) "Begum Rokeya University, Rangpur (BRUR) • CSE"
                               else "বেগম রোকেয়া বিশ্ববিদ্যালয়, রংপুর (BRUR) • CSE বিভাগ",
                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Business,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isEnglish) "Organization: Syntenium" else "প্রতিষ্ঠান: সিনটেনিয়াম (Syntenium)",
                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:$SUPPORT_EMAIL")
                                putExtra(Intent.EXTRA_SUBJECT, "Lekhani Keyboard - Feedback & Inquiry")
                            }
                            val launched = runCatching { context.startActivity(intent) }
                            if (launched.isFailure) {
                                Toast.makeText(
                                    context,
                                    if (isEnglish) "No email app found. Contact: $SUPPORT_EMAIL"
                                    else "কোনো ইমেইল অ্যাপ পাওয়া যায়নি। যোগাযোগ: $SUPPORT_EMAIL",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    ) {
                        Icon(imageVector = Icons.Filled.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isEnglish) "Contact" else "ইমেইল")
                    }

                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_REPO_URL))
                            val launched = runCatching { context.startActivity(intent) }
                            if (launched.isFailure) {
                                Toast.makeText(
                                    context,
                                    if (isEnglish) "Unable to open browser" else "ব্রাউজার খোলা সম্ভব হয়নি",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("GitHub")
                    }
                }

                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                if (isEnglish)
                                    "Check out Lekhani - 100% offline, private, and ultra-fast Bengali keyboard for Android!\n$GITHUB_REPO_URL"
                                else
                                    "লেখনী কীবোর্ড ব্যবহার করে দেখুন - ১০০% অফলাইন, নিরাপদ ও দ্রুতগতির বাংলা কীবোর্ড!\n$GITHUB_REPO_URL"
                            )
                        }
                        runCatching {
                            context.startActivity(
                                Intent.createChooser(
                                    shareIntent,
                                    if (isEnglish) "Share Lekhani Keyboard" else "লেখনী কীবোর্ড শেয়ার করুন"
                                )
                            )
                        }
                    }
                ) {
                    Icon(imageVector = Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isEnglish) "Share App with Friends" else "বন্ধুদের সাথে শেয়ার করুন")
                }
            }
        }

        // ── 5. Open Source & Standards ─────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isEnglish) "Open Source & Standards" else "ওপেন সোর্স ও লাইসেন্স",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isEnglish)
                        "Lekhani is open source under Apache 2.0 / MIT. Built compliant with BBS National Bengali Standard and OpenBangla keyboard corpus."
                    else
                        "লেখনী Apache 2.0 ও MIT লাইসেন্সে সম্পূর্ণ ওপেন সোর্স। বাংলাদেশ সরকারি বিবিএস জাতীয় মান এবং ওপেনবাংলা স্ট্যান্ডার্ড অনুযায়ী নির্মিত।",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }

        // ── 6. Technical Specifications (Developer Unlocked) ───────────────────
        if (isDevUnlocked) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showTechSpecs = !showTechSpecs }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Memory,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isEnglish) "Technical Architecture Details" else "ইঞ্জিন ও টেকনিক্যাল স্পেসিফিকেশন",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                        Icon(
                            imageVector = if (showTechSpecs) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (showTechSpecs) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(visible = showTechSpecs) {
                        Column(
                            modifier = Modifier.padding(top = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

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
                            ArchitectureTierItem(
                                tier = "Performance & Memory Budget",
                                detail = "< 30 MB Private Dirty RAM Idle • < 55 MB Active Typing • < 95 MB Voice ASR • Zero GC on touch"
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PrivacyFeatureItem(title: String, desc: String) {
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            text = "• $title",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        )
        Text(
            text = desc,
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.padding(start = 14.dp, top = 2.dp)
        )
    }
}

@Composable
private fun HighlightItem(title: String, detail: String) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
        )
    }
}

@Composable
private fun ArchitectureTierItem(tier: String, detail: String) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = tier,
            style = MaterialTheme.typography.labelLarge.copy(
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
            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
        )
    }
}
