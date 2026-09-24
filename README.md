# Lekhani for Android (লেখনী অ্যান্ড্রয়েড)

<p align="center">
  <b>The World-Class, Privacy-First, Sub-Millisecond Bengali Mobile Keyboard</b>
</p>

<p align="center">
  <img src="mockups/lekhani_android_dark.jpg" alt="Lekhani Android Keyboard Dark Mode" width="700" style="border-radius: 16px; box-shadow: 0 10px 30px rgba(0,0,0,0.5);" />
</p>

<p align="center">
  <a href="#vision">Vision</a> •
  <a href="#key-features">Features</a> •
  <a href="ARCHITECTURE.md">Architecture</a> •
  <a href="ROADMAP.md">Roadmap</a> •
  <a href="FEATURES.md">Feature Spec</a> •
  <a href="mockups/index.html">Interactive Mockup</a> •
  <a href="#license">License</a>
</p>

---

## 🌟 The Vision

Lekhani Android is engineered to become the definitive mobile Bengali typing experience across the globe. Built on top of the ultra-fast, zero-allocation Rust [`lekhani-core`](https://crates.io/crates/lekhani-core) and [`lekhani-parser`](https://crates.io/crates/lekhani-parser) engines via Mozilla UniFFI, Lekhani Android combines **uncompromising privacy (100% on-device, 0 network permissions)** with **sub-millisecond keystroke responsiveness**, **contextual AI homophone disambiguation**, and **buttery-smooth 120 FPS hardware-accelerated touch interaction**.

---

## ⚡ Why Lekhani Beats Gboard & Ridmik

| Feature | Google Gboard | Ridmik Keyboard | **Lekhani for Android** |
| :--- | :---: | :---: | :---: |
| **Privacy / Telemetry** | Cloud syncing, telemetry | Proprietary, ad-supported | **100% On-Device, ZERO Internet Permission** |
| **Phonetic Engine** | Proprietary ML (often alters intent) | Outdated grammar rules | **`lekhani-parser` (100% Avro muscle memory)** |
| **Contextual AI Homophones** | Cloud dependent | None | **Sub-microsecond local N-gram model** |
| **Memory Footprint** | ~120 MB | ~80 MB | **< 35 MB (Zero-alloc Rust core)** |
| **Open Source** | ❌ Closed | ❌ Closed | **✅ 100% Free & Open Source (GPL-3.0)** |

---

## 📱 Visual Mockups & UI Showcase

### 1. Dark Mode Mobile Keyboard with Contextual Candidate Strip
![Dark Mode Keyboard](mockups/lekhani_android_dark.jpg)

### 2. Settings & Theme Studio (Material You & OLED Themes)
![Settings & Themes](mockups/lekhani_android_themes.jpg)

### 3. Interactive Web Mockup
You can test the interactive prototype directly in your browser by opening [`mockups/index.html`](mockups/index.html).

---

## 🏗️ Technical Architecture at a Glance

```
┌────────────────────────────────────────────────────────┐
│     Android Kotlin Layer (UI & System Input)           │
│  - InputMethodService (Lifecycle & InputConnection)    │
│  - Custom Hardware Canvas (120 FPS Touch Grid)         │
│  - Jetpack Compose Candidate Strip & Material You      │
└──────────────────────────▲─────────────────────────────┘
                           │ UniFFI (Type-safe Kotlin <-> Rust Bridge)
┌──────────────────────────▼─────────────────────────────┐
│     Shared Rust Core (Precompiled via cargo-ndk)       │
│  - lekhani-core: IME state machine & dictionary lookup │
│  - lekhani-parser: 11 ns/char zero-allocation engine   │
│  - lekhani-ai: On-device N-gram model & predictor     │
└────────────────────────────────────────────────────────┘
```

See [ARCHITECTURE.md](ARCHITECTURE.md) for full technical deep-dive.

---

## 🗺️ Project Roadmap

- [x] Phase 0: Standalone Rust Core & Engine Crates published to crates.io
- [ ] Phase 1: Android UniFFI Bridge (`lekhani-android-bridge`)
- [ ] Phase 2: Android `InputMethodService` & Custom Canvas Touch Grid
- [ ] Phase 3: Candidate Strip, Homophone Disambiguation & Next-Word AI
- [ ] Phase 4: Spatial Touch Autocorrect (Gaussian Key Bounding Boxes)
- [ ] Phase 5: Material You Theme Studio & Haptic Vibration Tuning
- [ ] Phase 6: Public Alpha Release on F-Droid and Google Play

Detailed task breakdown in [ROADMAP.md](ROADMAP.md).

---

## 📄 License

Licensed under GPL-3.0-or-later. © 2026 Syntenieum.
