package com.lekhani.android.ui.about

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lekhani.android.ui.components.LekhaniBrandLogo

private const val SUPPORT_EMAIL = "sintaulsiam@gmail.com"
private const val GITHUB_REPO_URL = "https://github.com/sintaulsiam/lekhani-android"

/**
 * AboutPrivacyTab
 * Polished, Material 3 Expressive About screen:
 * 1. Brand Hero Card (M3 circle logo, headline, version badge with accessible dev unlock)
 * 2. 100% Offline & Privacy Assurance (Icon badges, zero network transmission)
 * 3. What's New in v0.2.0 (Icon-driven interactive highlights)
 * 4. Creator & Institution Credits + Responsive Actions
 * 5. Open Source & Standards Compliance
 * 6. Collapsible Technical Specifications (Dev Unlocked)
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
        // ── 1. Brand Hero Card ──────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LekhaniBrandLogo(size = 72.dp)

                Text(
                    text = if (isEnglish) "Lekhani Keyboard" else "লেখনী কীবোর্ড",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )

                Text(
                    text = if (isEnglish) "Offline Bengali & English keyboard for Android"
                           else "অ্যান্ড্রয়েডের জন্য অফলাইন বাংলা ও ইংরেজি কীবোর্ড",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                Spacer(modifier = Modifier.height(2.dp))

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable {
                            if (devTapCount < 7) {
                                devTapCount++
                                if (devTapCount == 7) {
                                    Toast.makeText(
                                        context,
                                        if (isEnglish) "Developer info unlocked"
                                        else "ডেভেলপার তথ্য উন্মুক্ত হয়েছে",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                        .semantics {
                            role = Role.Button
                            contentDescription = "Version ${com.lekhani.android.BuildConfig.VERSION_NAME}, offline keyboard"
                        }
                ) {
                    Text(
                        text = "v${com.lekhani.android.BuildConfig.VERSION_NAME}" +
                            if (devTapCount in 1..6) " (${7 - devTapCount})" else "",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // ── 2. In-App Updates (GitHub Releases) ─────────────────────────────────
        AppUpdateSection(
            isEnglish = isEnglish
        )

        // ── 3. Primary: 100% Offline & Privacy Assurance ────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VerifiedUser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isEnglish) "Privacy" else "গোপনীয়তা",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isEnglish) "Keystrokes never leave your device" else "টাইপিংয়ের কোনো তথ্য ডিভাইসের বাইরে যায় না",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )

                PrivacyFeatureItem(
                    icon = Icons.Filled.Security,
                    title = if (isEnglish) "No Analytics or Tracking" else "কোনো ট্র্যাকিং নেই",
                    desc = if (isEnglish) "Network access is only used when you check for updates or download offline assets."
                           else "ইন্টারনেট শুধুমাত্র আপনি আপডেট বা অফলাইন ফাইল ডাউনলোড করলে ব্যবহৃত হয়।"
                )
                PrivacyFeatureItem(
                    icon = Icons.Filled.Memory,
                    title = if (isEnglish) "On-Device Typing" else "অন-ডিভাইস ইঞ্জিন",
                    desc = if (isEnglish) "Transliteration, predictions, and voice input run locally."
                           else "বাংলা লেখা, সাজেশন ও ভয়েস টাইপিং সরাসরি ফোনেই চলে।"
                )
                PrivacyFeatureItem(
                    icon = Icons.Filled.Lock,
                    title = if (isEnglish) "Local Storage" else "স্থানীয় সংরক্ষণ",
                    desc = if (isEnglish) "Learned words and clipboard items are saved only on your device."
                           else "ব্যক্তিগত শব্দ ও ক্লিপবোর্ড শুধু আপনার ডিভাইসেই সংরক্ষিত থাকে।"
                )
            }
        }

        // ── 3. What's New in v0.2.0 ────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showWhatsNew = !showWhatsNew }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.NewReleases,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isEnglish) "What's New in v${com.lekhani.android.BuildConfig.VERSION_NAME}"
                                       else "নতুন কী কী যোগ হয়েছে (v${com.lekhani.android.BuildConfig.VERSION_NAME})",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = if (isEnglish) "Explore recent enhancements" else "সাম্প্রতিক আপডেট ও নতুন ফিচারসমূহ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = if (showWhatsNew) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (showWhatsNew) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedVisibility(visible = showWhatsNew) {
                    Column(
                        modifier = Modifier.padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        HighlightItem(
                            icon = Icons.Filled.TouchApp,
                            title = if (isEnglish) "Backspace Swipe-to-Delete"
                                    else "ব্যাকস্পেস সোয়াইপ ডিলিট",
                            detail = if (isEnglish) "Drag left from Backspace to select and delete words."
                                     else "ব্যাকস্পেস থেকে বামে টেনে শব্দ সিলেক্ট করে মুছুন।"
                        )
                        HighlightItem(
                            icon = Icons.Filled.AutoAwesome,
                            title = if (isEnglish) "Selection Toolbar"
                                    else "সিলেকশন টুলবার",
                            detail = if (isEnglish) "Cut, Copy, Paste, and Select All appear when text is selected."
                                     else "টেক্সট সিলেক্ট করলে কাট, কপি, পেস্ট ও সিলেক্ট অল বাটন দেখাবে।"
                        )
                        HighlightItem(
                            icon = Icons.Filled.Mic,
                            title = if (isEnglish) "Offline Voice Typing"
                                    else "অফলাইন ভয়েস টাইপিং",
                            detail = if (isEnglish) "On-device Bengali and English voice transcription."
                                     else "ইন্টারনেট ছাড়াই বাংলা ও ইংরেজি ভয়েস টাইপিং।"
                        )
                        HighlightItem(
                            icon = Icons.Filled.Palette,
                            title = if (isEnglish) "Themes & Wallpapers"
                                    else "থিম ও ওয়ালপেপার",
                            detail = if (isEnglish) "Dynamic RGB chroma, custom themes, and keyboard wallpapers."
                                     else "আরজিবি ক্রোমা, কাস্টম থিম ও কীবোর্ড ওয়ালপেপার।"
                        )
                    }
                }
            }
        }

        // ── 4. Creator, Institution & Actions ──────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Business,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isEnglish) "Creator & Organization" else "ডেভেলপার ও প্রতিষ্ঠান",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isEnglish) "Syntenium • BRUR CSE" else "সিনটেনিয়াম • বেরোবি সিএসই",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 2.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
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
                    Spacer(modifier = Modifier.width(10.dp))
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
                    Spacer(modifier = Modifier.width(10.dp))
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
                    FilledTonalButton(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
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

                    FilledTonalButton(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
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
                    shape = RoundedCornerShape(10.dp),
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
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isEnglish) "Open Source & Standards" else "ওপেন সোর্স ও লাইসেন্স",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isEnglish)
                        "Lekhani is open source under Apache 2.0 / MIT. Built compliant with BBS National Bengali Standard and OpenBangla keyboard corpus."
                    else
                        "লেখনী Apache 2.0 ও MIT লাইসেন্সে সম্পূর্ণ ওপেন সোর্স। বাংলাদেশ সরকারি বিবিএস জাতীয় মান এবং ওপেনবাংলা স্ট্যান্ডার্ড অনুযায়ী নির্মিত।",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                )
            }
        }

        // ── 6. Technical Specifications (Developer Unlocked) ───────────────────
        if (isDevUnlocked) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { showTechSpecs = !showTechSpecs }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Memory,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (isEnglish) "Technical Architecture Details" else "ইঞ্জিন ও টেকনিক্যাল স্পেসিফিকেশন",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
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
                            modifier = Modifier.padding(top = 12.dp),
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
private fun PrivacyFeatureItem(
    icon: ImageVector,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            )
        }
    }
}

@Composable
private fun HighlightItem(
    icon: ImageVector,
    title: String,
    detail: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            )
        }
    }
}

@Composable
private fun ArchitectureTierItem(tier: String, detail: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = tier,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            )
        }
    }
}
