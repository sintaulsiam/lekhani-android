# Lekhani Android v0.3.0 — Project Bodh & Adaptive Canvas Polish 🚀

**Lekhani Android `v0.3.0`** is a major feature and refinement release bringing OEM-adaptive bottom chin clearance, Project Retro-Correction clause-level repair, high-DPI subpixel typography, interactive Material 3 Live Resize overlay, and multi-tier agglutinative morphological parsing.

---

## 🌟 What's New & Highlighted Capabilities

### 1. 🛡️ OEM-Adaptive Navigation & Bottom Clearance
- **Smart Hardware & OS Detection**: Automatically defaults bottom clearance to `0dp` on Xiaomi / HyperOS / MIUI, Samsung OneUI, BBK (Oppo, Realme, Vivo, OnePlus), Transsion, and Android 3-Button navigation mode to eliminate double bottom gaps.
- **Pixel & Pure Gesture Optimization**: Defaults to comfortable `8dp` clearance on Google Pixel / AOSP / Motorola gesture navigation to keep the spacebar elevated above the transparent pill bar.
- **One-Time Versioned Migration**: Automatically normalizes existing device installs on update without requiring manual app data wipes.

### 2. 🎚️ Interactive Material 3 Live Resizer & Responsive Sliders
- **Tactile Drag Handle**: Drag vertically on the handle bar to live-scale keyboard height at 120 FPS.
- **Quick Bottom Clearance Presets**: Dedicated chips for `0dp`, `8dp`, `14dp`, and `20dp` with real-time feedback.
- **Responsive 360dp Settings Slider**: Seamlessly fine-tune bottom space (0–24 dp) and height scale in Settings without text truncation.
- **Hardware-Adaptive Reset**: One-tap reset button instantly restores device-optimal clearance.

### 3. ✍️ Clause-Level Retro-Correction & Anvaya Concordance
- **Project Bodh**: Dynamic retro-correction analyzes preceding and subsequent word boundaries to automatically correct honorific verbs and contextual homophones (e.g. `করছে` ↔ `করছেন` based on subject pronoun `তিনি`/`সে`).
- **One-Tap Candidate Strip Chip**: Interactive `RetroCorrectionChip` surfaces clause-level grammatical corrections with full TalkBack accessibility.

### 4. 🎨 High-DPI Subpixel Typography & Keycap Proportions
- **Subpixel Text & Dithering**: Enabled `SUBPIXEL_TEXT_FLAG` and `DITHER_FLAG` across all hardware canvas text paints for razor-sharp Bengali conjuncts and diacritics on 1080p/1440p displays.
- **Calibrated Proportions**: Standard `218dp` base height with balanced `0.36x` font scaling matching modern gold-standard mobile keyboards.

### 5. 🧩 Probaho 2.0 Rupmul Agglutination Engine
- Multi-tier suffix stripping and agglutinative expansion (`-গুলো`, `-দের`, `-খানা`, `-খানি`, `-গুলোই`).
- Instant consonant gemination and dual-thumb ergonomic vowel realm auroras.

---

## 📦 Attached APK Artifacts

| APK File | Architecture | Target Devices | Size |
|---|---|---|---|
| `app-universal-release.apk` | Universal (Fat binary) | All Android devices (arm64, armv7, x86_64) | ~35 MB |
| `app-arm64-v8a-release.apk` | `arm64-v8a` (64-bit ARM) | Modern smartphones (Snapdragon, Dimensity, Tensor) | ~32 MB |
| `app-armeabi-v7a-release.apk` | `armeabi-v7a` (32-bit ARM) | Legacy / Budget 32-bit Android smartphones | ~32 MB |
| `app-x86_64-release.apk` | `x86_64` (64-bit Intel/AMD) | Emulators, Chromebooks & Android x86 devices | ~32 MB |

---

## 🔒 Privacy & Performance Guarantees
- **100% Offline & On-Device**: Zero telemetry, zero analytics, zero keystroke logging.
- **Zero Allocations in Hot Path**: Sub-millisecond keystroke latency at 120 FPS.
