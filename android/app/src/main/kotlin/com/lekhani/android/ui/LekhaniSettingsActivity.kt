package com.lekhani.android.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.lekhani.android.data.dictionary.LekhaniDictionaryManager
import com.lekhani.android.data.settings.KeyboardPreferences
import com.lekhani.android.ffi.LekhaniLayoutType
import com.lekhani.android.model.LayoutRegistry
import com.lekhani.android.theme.ThemeRegistry
import com.lekhani.android.ui.dictionary.DictionaryManagementSheet
import com.lekhani.android.ui.theme.ErgonomicsSizingSheet
import com.lekhani.android.ui.theme.FormFactorGesturesSheet
import com.lekhani.android.ui.theme.HapticsSoundSheet
import com.lekhani.android.ui.theme.ThemeStudioSheet
import com.lekhani.android.ui.theme.ToolbarCustomizationSheet
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * LekhaniSettingsActivity
 * ══════════════════════════════════════════════════════════════════════════════
 * Main entry point and Onboarding Hub for Lekhani Keyboard.
 * Modern Material 3 Expressive UI for 2026.
 */
class LekhaniSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LekhaniAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LekhaniSettingsScreen(
                        onOpenImeSettings = {
                            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                        },
                        onOpenImePicker = {
                            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                            imm?.showInputMethodPicker()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LekhaniSettingsScreen(
    onOpenImeSettings: () -> Unit = {},
    onOpenImePicker: () -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val deviceContext = remember {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            context.createDeviceProtectedStorageContext()
        } else {
            context
        }
    }
    val prefs = remember {
        deviceContext.getSharedPreferences("lekhani_device_prefs", Context.MODE_PRIVATE)
    }

    var enabledLayouts by remember {
        mutableStateOf(
            LayoutRegistry.parseEnabledLayouts(prefs.getString(LayoutRegistry.PREF_ENABLED_LAYOUTS, null))
        )
    }

    // Determine IME enable/default status
    var isEnabled by remember { mutableStateOf(false) }
    var isDefault by remember { mutableStateOf(false) }
    var testText by remember { mutableStateOf("") }

    val dictManager = remember { LekhaniDictionaryManager() }
    var showDictionarySheet by remember { mutableStateOf(false) }
    var userWordCount by remember { mutableStateOf(dictManager.getUserWords().size) }

    val keyboardPrefs = remember { KeyboardPreferences.get(context) }
    var showThemeSheet by remember { mutableStateOf(false) }
    var showErgonomicsSheet by remember { mutableStateOf(false) }
    var showFormFactorSheet by remember { mutableStateOf(false) }
    var showHapticsSheet by remember { mutableStateOf(false) }
    var showToolbarSheet by remember { mutableStateOf(false) }
    var currentThemeName by remember {
        mutableStateOf(ThemeRegistry.resolveTheme(context, keyboardPrefs.themeId).nameBengali)
    }

    LaunchedEffect(Unit) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val enabledList = imm?.enabledInputMethodList ?: emptyList()
        val pkg = context.packageName
        isEnabled = enabledList.any { it.packageName == pkg }

        val defaultIme = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        ) ?: ""
        isDefault = defaultIme.contains(pkg)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // ── Brand Header ────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF006C50), Color(0xFF00A87E))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "লে",
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "লেখনি কীবোর্ড",
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

        Spacer(modifier = Modifier.height(24.dp))

        // ── Status & Activation Cards ───────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "কীবোর্ড সেটআপ (Setup Status)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Step 1: Enable
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "১. লেখনি সক্রিয় করুন (Enable Lekhani)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = if (isEnabled) "সক্রিয় করা আছে (Enabled)" else "সিস্টেম সেটিংসে সক্ষম করুন",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (isEnabled) Color(0xFF00A87E) else MaterialTheme.colorScheme.error
                            )
                        )
                    }
                    if (!isEnabled) {
                        Button(
                            onClick = onOpenImeSettings,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("সক্ষম করুন")
                        }
                    } else {
                        Text("✓", color = Color(0xFF00A87E), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Step 2: Set as Default
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "২. ডিফল্ট কীবোর্ড নির্বাচন করুন (Set Default)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = if (isDefault) "ডিফল্ট হিসেবে সক্রিয় (Active)" else "প্রধান কীবোর্ড হিসেবে বেছে নিন",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (isDefault) Color(0xFF00A87E) else MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                    if (!isDefault) {
                        FilledTonalButton(
                            onClick = onOpenImePicker,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("নির্বাচন")
                        }
                    } else {
                        Text("✓", color = Color(0xFF00A87E), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Layout Selection & Ordering Card ────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "কীবোর্ড লেআউট নির্বাচন (Keyboard Layouts)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = "যে লেআউটগুলো সক্রিয় রাখবেন সেগুলো নির্বাচন করুন (স্পেসবারে সোয়াইপ বা 🌐 বাটনে পরিবর্তন হবে):",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                LayoutRegistry.all.forEachIndexed { index, layoutType ->
                    val isChecked = enabledLayouts.contains(layoutType)
                    val canDisable = enabledLayouts.size > 1 || !isChecked

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "${LayoutRegistry.getBengaliName(layoutType)} (${LayoutRegistry.getEnglishName(layoutType)})",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = LayoutRegistry.getDescription(layoutType),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            )
                        }

                        Switch(
                            checked = isChecked,
                            enabled = canDisable,
                            onCheckedChange = { checked ->
                                val current = enabledLayouts.toMutableList()
                                if (checked) {
                                    if (!current.contains(layoutType)) current.add(layoutType)
                                } else {
                                    if (current.size > 1) current.remove(layoutType)
                                }
                                enabledLayouts = current
                                prefs.edit().putString(
                                    LayoutRegistry.PREF_ENABLED_LAYOUTS,
                                    LayoutRegistry.serializeEnabledLayouts(current)
                                ).apply()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF00A87E)
                            )
                        )
                    }

                    if (index < LayoutRegistry.all.size - 1) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Personal Dictionary & Data Freedom Card ───────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "ব্যক্তিগত অভিধান ও ব্যাকআপ",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = "User Dictionary • $userWordCount টি ব্যক্তিগত শব্দ সংরক্ষিত",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { showDictionarySheet = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A87E))
                ) {
                    Text("📖 শব্দতালিকা ও সম্পাদনা (Manage & Migrate)", fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Theme Studio v2 Card ───────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🎨 থিম ও কালার স্টুডিও (Theme Studio v2)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = "বর্তমান থিম: $currentThemeName • OLED Black, Avro Blue, Cyber Indigo ও ওয়ালপেপার",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { showThemeSheet = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A87E))
                ) {
                    Text("🎨 থিম ও ব্যাকগ্রাউন্ড পরিবর্তন করুন", fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Ergonomics & Sizing Card ──────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "📐 কীবোর্ড সাইজ ও আরগোনোমিক্স (Ergonomics)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = "উচ্চতা: ${(keyboardPrefs.heightScale * 100).toInt()}% • লং-প্রেস: ${keyboardPrefs.longPressDelayMs}ms • ফন্ট ও প্যাডিং",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { showErgonomicsSheet = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A87E))
                ) {
                    Text("📐 সাইজ, প্যাডিং ও ফন্ট কাস্টমাইজেশন", fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Form Factor & Gestures Card (Phase 11) ────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "📱 ফর্ম ফ্যাক্টর ও জেশ্চার (Form Factors & Gestures)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = "মোড: ${keyboardPrefs.formFactor.titleBengali} • স্পেসবার কার্সর ও সোয়াইপ ডিলিট",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { showFormFactorSheet = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A87E))
                ) {
                    Text("📱 একহাতে, ভাসমান, স্প্লিট ও জেশ্চার সেটিংস", fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Haptics & Sound Feedback Card ──────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "📳 হ্যাপটিক্স ও সাউন্ড ফিডব্যাক",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = "ভাইব্রেশন: ${if (keyboardPrefs.hapticEnabled) "চালু (${keyboardPrefs.hapticDurationMs}ms)" else "বন্ধ"} • সাউন্ড: ${if (keyboardPrefs.soundEnabled) keyboardPrefs.soundPack else "বন্ধ"}",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { showHapticsSheet = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A87E))
                ) {
                    Text("📳 ভাইব্রেশন ও সাউন্ড প্যাক টিউনিং", fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Toolbar Customization Card ────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🛠️ কুইক টুলবার কাস্টমাইজেশন",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = "কীবোর্ড স্ট্রিপের শর্টকাট টুল সক্রিয় করুন এবং ড্র্যাগ করে সাজান (ইমোজি, ভয়েস, ক্লিপবোর্ড, থিম, সেটিংস)",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { showToolbarSheet = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A87E))
                ) {
                    Text("🛠️ টুল সাজান ও নির্বাচন করুন", fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Live Test Typing Area ───────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "টাইপিং পরীক্ষা (Test Typing)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = "এখানে ট্যাপ করে লেখনি কীবোর্ড পরীক্ষা করুন:",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = testText,
                    onValueChange = { testText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("বাংলা লিখুন (Type here)...") },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00A87E),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Features & Privacy Badge ────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF00382B).copy(alpha = 0.25f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🛡️ ১০০% গোপনীয় ও সম্পূর্ণ অফলাইন (100% Offline)",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00A87E)
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "লেখনি কোনো ইন্টারনেট অনুমতি (android.permission.INTERNET) ব্যবহার করে না। আপনার টাইপিং ও ভয়েস ডেটা ডিভাইস থেকে কখনো বাইরে যাবে না।",
                    style = MaterialTheme.typography.bodySmall.copy(
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }

    if (showDictionarySheet) {
        Dialog(
            onDismissRequest = {
                showDictionarySheet = false
                userWordCount = dictManager.getUserWords().size
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                DictionaryManagementSheet(
                    dictManager = dictManager,
                    onClose = {
                        showDictionarySheet = false
                        userWordCount = dictManager.getUserWords().size
                    }
                )
            }
        }
    }

    if (showThemeSheet) {
        Dialog(
            onDismissRequest = {
                showThemeSheet = false
                currentThemeName = ThemeRegistry.resolveTheme(context, keyboardPrefs.themeId).nameBengali
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            ThemeStudioSheet(
                prefs = keyboardPrefs,
                onClose = {
                    showThemeSheet = false
                    currentThemeName = ThemeRegistry.resolveTheme(context, keyboardPrefs.themeId).nameBengali
                }
            )
        }
    }

    if (showErgonomicsSheet) {
        Dialog(
            onDismissRequest = { showErgonomicsSheet = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            ErgonomicsSizingSheet(
                prefs = keyboardPrefs,
                onClose = { showErgonomicsSheet = false }
            )
        }
    }

    if (showFormFactorSheet) {
        Dialog(
            onDismissRequest = { showFormFactorSheet = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            FormFactorGesturesSheet(
                prefs = keyboardPrefs,
                onClose = { showFormFactorSheet = false }
            )
        }
    }

    if (showHapticsSheet) {
        Dialog(
            onDismissRequest = { showHapticsSheet = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            HapticsSoundSheet(
                prefs = keyboardPrefs,
                onClose = { showHapticsSheet = false }
            )
        }
    }

    if (showToolbarSheet) {
        Dialog(
            onDismissRequest = { showToolbarSheet = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            ToolbarCustomizationSheet(
                prefs = keyboardPrefs,
                onClose = { showToolbarSheet = false }
            )
        }
    }
}

@Composable
fun LekhaniAppTheme(
    content: @Composable () -> Unit
) {
    val darkColorScheme = darkColorScheme(
        primary = Color(0xFF00A87E),
        onPrimary = Color(0xFF003829),
        primaryContainer = Color(0xFF00513C),
        onPrimaryContainer = Color(0xFF8CF4CB),
        secondary = Color(0xFFB1CCC0),
        surface = Color(0xFF191C1B),
        background = Color(0xFF101413),
        onSurface = Color(0xFFE1E3DF),
        onBackground = Color(0xFFE1E3DF)
    )

    MaterialTheme(
        colorScheme = darkColorScheme,
        content = content
    )
}
