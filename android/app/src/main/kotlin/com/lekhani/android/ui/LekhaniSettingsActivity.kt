package com.lekhani.android.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

/**
 * LekhaniSettingsActivity
 * ══════════════════════════════════════════════════════════════════════════════
 * Entry point for the Settings & Theme Studio screen.
 * Also serves as the onboarding wizard launcher (Phase 12).
 *
 * Uses Jetpack Compose + Material 3 Expressive.
 * Phase 10 (Theme Studio) will fill [LekhaniSettingsScreen] with content.
 */
class LekhaniSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LekhaniTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LekhaniSettingsScreen()
                }
            }
        }
    }
}

// ── Composable stub (Phase 10 will expand this) ──────────────────────────────

@Composable
fun LekhaniSettingsScreen() {
    // Phase 10: Theme Studio, layout toggles, haptics, sound packs will live here.
    // Phase 12: Onboarding wizard (3-step) will be triggered from here when
    //           the keyboard is not yet enabled or set as default.
}

// ── Theme wrapper ─────────────────────────────────────────────────────────────

@Composable
fun LekhaniTheme(
    content: @Composable () -> Unit
) {
    // Phase 10 (Theme Studio) will wire Material You dynamic color here.
    // For now, apply the default Material 3 dark color scheme.
    MaterialTheme(
        content = content
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingsPreview() {
    LekhaniTheme {
        LekhaniSettingsScreen()
    }
}
