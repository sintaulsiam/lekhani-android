package com.lekhani.android.ui

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.lekhani.android.data.settings.KeyboardPreferences
import com.lekhani.android.theme.KeyboardTheme

@Composable
fun LekhaniAppTheme(
    theme: KeyboardTheme? = null,
    appThemeMode: KeyboardPreferences.AppThemeMode = KeyboardPreferences.AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isSystemDark = (context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES

    val shouldUseDark = when (appThemeMode) {
        KeyboardPreferences.AppThemeMode.LIGHT -> false
        KeyboardPreferences.AppThemeMode.DARK -> true
        KeyboardPreferences.AppThemeMode.SYSTEM,
        KeyboardPreferences.AppThemeMode.DYNAMIC -> isSystemDark
        KeyboardPreferences.AppThemeMode.MATCH_KEYBOARD -> theme?.isDark ?: isSystemDark
    }

    val colorScheme = remember(appThemeMode, isSystemDark, theme, shouldUseDark) {
        if (appThemeMode == KeyboardPreferences.AppThemeMode.MATCH_KEYBOARD && theme != null) {
            val primaryColor = Color(theme.accentColor)
            val isAccentLight = androidx.core.graphics.ColorUtils.calculateLuminance(theme.accentColor) > 0.45
            val onPrimaryColor = if (isAccentLight) Color(0xFF001F18) else Color.White

            if (shouldUseDark) {
                darkColorScheme(
                    primary = primaryColor,
                    onPrimary = onPrimaryColor,
                    primaryContainer = primaryColor.copy(alpha = 0.20f),
                    onPrimaryContainer = if (isAccentLight) primaryColor else Color(0xFFF1F5F9),
                    secondary = Color(theme.keyShiftColor),
                    onSecondary = Color(theme.labelColor),
                    secondaryContainer = Color(theme.keyNormalColor),
                    onSecondaryContainer = Color(theme.labelColor),
                    background = Color(0xFF090B0E),
                    onBackground = Color(0xFFF1F5F9),
                    surface = Color(0xFF111418),
                    onSurface = Color(0xFFF1F5F9),
                    surfaceVariant = Color(0xFF181C22),
                    onSurfaceVariant = Color(0xFF94A3B8),
                    outline = Color(theme.keyBorderColor).copy(alpha = 0.5f),
                    outlineVariant = Color(0xFF262D38)
                )
            } else {
                lightColorScheme(
                    primary = primaryColor,
                    onPrimary = onPrimaryColor,
                    primaryContainer = primaryColor.copy(alpha = 0.14f),
                    onPrimaryContainer = if (isAccentLight) Color(0xFF0F172A) else primaryColor,
                    secondary = Color(0xFF0284C7),
                    onSecondary = Color.White,
                    secondaryContainer = Color(0xFFE0F2FE),
                    onSecondaryContainer = Color(0xFF034466),
                    surface = Color(0xFFFFFFFF),
                    onSurface = Color(0xFF0F172A),
                    background = Color(0xFFFFFFFF),
                    onBackground = Color(0xFF0F172A),
                    surfaceVariant = Color(0xFFF1F5F9),
                    onSurfaceVariant = Color(0xFF475569),
                    outline = Color(0xFFE2E8F0),
                    outlineVariant = Color(0xFFEEF2F6)
                )
            }
        } else if (appThemeMode == KeyboardPreferences.AppThemeMode.DYNAMIC
            && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (shouldUseDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else if (shouldUseDark) {
            darkColorScheme(
                primary = Color(0xFF00F0B5),
                onPrimary = Color(0xFF002A1F),
                primaryContainer = Color(0xFF0F362A),
                onPrimaryContainer = Color(0xFF65FFD2),
                secondary = Color(0xFF38BDF8),
                onSecondary = Color(0xFF002235),
                secondaryContainer = Color(0xFF152A38),
                onSecondaryContainer = Color(0xFF7DD3FC),
                tertiary = Color(0xFFA78BFA),
                onTertiary = Color(0xFF27134A),
                background = Color(0xFF090B0E),
                onBackground = Color(0xFFF1F5F9),
                surface = Color(0xFF111418),
                onSurface = Color(0xFFF1F5F9),
                surfaceVariant = Color(0xFF181C22),
                onSurfaceVariant = Color(0xFF94A3B8),
                outline = Color(0xFF262D38),
                outlineVariant = Color(0xFF1B2028)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF007A55),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFE6F7F0),
                onPrimaryContainer = Color(0xFF003828),
                secondary = Color(0xFF0284C7),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFE0F2FE),
                onSecondaryContainer = Color(0xFF034466),
                tertiary = Color(0xFF7C3AED),
                onTertiary = Color.White,
                tertiaryContainer = Color(0xFFEDE9FE),
                onTertiaryContainer = Color(0xFF3B0764),
                background = Color(0xFFFFFFFF),
                onBackground = Color(0xFF0F172A),
                surface = Color(0xFFFFFFFF),
                onSurface = Color(0xFF0F172A),
                surfaceVariant = Color(0xFFF1F5F9),
                onSurfaceVariant = Color(0xFF475569),
                outline = Color(0xFFE2E8F0),
                outlineVariant = Color(0xFFEEF2F6)
            )
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
