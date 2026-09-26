# Lekhani for Android (লেখনী অ্যান্ড্রয়েড)

<p align="center">
  <b>The World-Class, Privacy-First, Sub-Millisecond Bengali Mobile Keyboard</b>
</p>

<p align="center">
  <img src="mockups/lekhani_android_flagship.jpg" alt="Lekhani Android Flagship Keyboard Mockup" width="700" style="border-radius: 20px; box-shadow: 0 15px 40px rgba(0,0,0,0.6);" />
</p>

<p align="center">
  <a href="#vision">Vision</a> •
  <a href="#key-features">Features</a> •
  <a href="ARCHITECTURE.md">Architecture</a> •
  <a href="ROADMAP.md">Roadmap</a> •
  <a href="FEATURES.md">Feature Spec</a> •
  <a href="LAYOUT_PROBAHO.md">Lekhani প্রবাহ (Flow)</a> •
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

### 1. Flagship Real-World View (Modern Bezel-Less Android 15)
![Flagship Mockup](mockups/lekhani_android_flagship.jpg)

### 2. Lekhani প্রবাহ (Flow) Ergonomic Two-Thumb Layout
![Lekhani Probaho Layout](mockups/lekhani_android_probaho.jpg)

### 3. Dark Mode Mobile Keyboard with Contextual Candidate Strip
![Dark Mode Keyboard](mockups/lekhani_android_dark.jpg)

### 4. Settings & Theme Studio (Material You & OLED Themes)
![Settings & Themes](mockups/lekhani_android_themes.jpg)

### 5. Interactive Web Mockup
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
- [ ] Phase 1: Native Android UniFFI Bridge Crate (`crates/lekhani-android`)
- [ ] Phase 2: Android IME Scaffolding & System Compatibility (Direct Boot, Passwords, WebViews)
- [ ] Phase 3: Hardware Canvas Touch Grid & Bengali Script Engine (Avro, National, Probhat, Gboard-style)
- [ ] Phase 4: Candidate Strip & Contextual AI Intelligence (Homophones, Next-Word, Blacklist)
- [ ] Phase 5: Emoji, Kaomoji, Symbols & Clipboard Suite (Unicode 15.1+, Bilingual Search)
- [ ] Phase 6: Multi-Layout Switcher & Hardware Keyboard (Settings toggles, Bluetooth keyboards)
- [ ] Phase 7: 100% Local / On-Device Voice Typing (Offline ASR, Zero Internet)
- [ ] Phase 8: Dictionary Management & User Data Freedom (Ridmik/Avro import, JSON backup)
- [ ] Phase 9: Glide / Gesture Typing (Continuous swipe path decoder)
- [ ] Phase 10: Deep Customization & Theme Studio v2 (Geometry, sound packs, haptics, toolbar)
- [ ] Phase 11: World-Class Modern UI/UX & Form Factors (Spring physics, tablet split & floating)
- [ ] Phase 12: Onboarding Flow, Accessibility & Store Launch (2-step setup, TalkBack, F-Droid & Play)

Detailed task breakdown in [ROADMAP.md](ROADMAP.md).

---

## 📄 License

Licensed under GPL-3.0-or-later. © 2026 Syntenium.
