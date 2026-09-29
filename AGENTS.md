# AGENTS.md — Development Guidelines & Architectural Guardrails

Welcome to **Lekhani Android** (`lekhani-android`). This document defines the engineering standards, architecture rules, performance budgets, and contribution workflows that all human contributors and AI agents **must strictly follow**.

---

## 1. 🛡️ Inviolable Core Principles (Non-Negotiable)

Any proposed change that violates these principles will be rejected immediately:

1. **Zero Network / Absolute Privacy**:
   - The application **must never** request or declare `android.permission.INTERNET` in `AndroidManifest.xml`.
   - All models (N-gram LM, morphology, offline ASR voice engine) and user dictionaries must execute **100% locally on-device**.
   - Keystrokes, clipboard entries, audio streams, and user typing frequencies must never leave the device.
2. **Sub-Millisecond & Zero-Allocation Performance Budget**:
   - Touch-to-screen keystroke latency must stay **under 3 ms**.
   - Keyboard canvas must render at a sustained **120 FPS** (frame budget: < 8.3 ms).
   - Zero runtime memory allocations (`malloc` / object instantiation) inside the hot path: `onDraw()`, `onTouchEvent()`, or `process_key()`.
   - Cold boot time to first interactive frame must stay **under 40 ms**.
   - **Tiered Memory Budget (Android LMK Resilient)**:
     - **Idle / Background**: **< 30 MB** Private Dirty RAM.
     - **Active Typing**: **< 55 MB** Private Dirty RAM (< 80 MB Total RSS including clean zero-copy `mmap` pages) for rich 4-gram LM, bilingual tries, and spatial touch matrices.
     - **Voice ASR Active**: **< 95 MB** Ephemeral Peak (offline streaming acoustic weights, auto-freed after 30s idle).
     - **Device Adaptive**: Low-RAM devices (`isLowRamDevice`) fall back to compact pruned profiles (< 35 MB).
3. **Canonical Unicode & Script Integrity**:
   - Text committed to Android `InputConnection` must always be canonical Unicode (NFC).
   - Never corrupt Bengali grapheme clusters, conjuncts (`ক্ষ`, `জ্ঞ`, etc.), or modifier diacritics.

---

## 2. 🏗️ Three-Tier Architecture

```
┌────────────────────────────────────────────────────────┐
│     Tier 3: Android Native Layer (Kotlin 2.0)          │
│  - LekhaniInputMethodService (IME Lifecycle)           │
│  - KeyboardCanvasView (Hardware Canvas 120 FPS Grid)   │
│  - CandidateStripView (Jetpack Compose / SurfaceView)  │
│  - Settings & Theme Studio (Material 3 Expressive)     │
└──────────────────────────▲─────────────────────────────┘
                           │ UniFFI Auto-Generated Kotlin Bindings
┌──────────────────────────▼─────────────────────────────┐
│     Tier 2: Native FFI Bridge (Rust / cargo-ndk)       │
│  - crates/lekhani-android (C-ABI / JNI Bridge)         │
│  - AndroidLekhaniSession (Thread-safe lifecycle wrapper│
│  - AudioRecord Streaming Buffer (Sherpa / Vosk ASR)    │
└──────────────────────────▲─────────────────────────────┘
                           │ Direct Rust Crate Dependencies
┌──────────────────────────▼─────────────────────────────┐
│     Tier 1: Upstream Engine Crates (Pure Rust)         │
│  - lekhani-core: Headless IME state machine            │
│  - lekhani-parser: 11 ns/char Avro Trie grammar engine │
│  - lekhani-ai: Local N-gram scorer & homophone ranker  │
└────────────────────────────────────────────────────────┘
```

### Supported Layouts:
- **Lekhani প্রবাহ (Flow)**: Custom two-thumb ergonomic layout with left-hand vowel pairing and right-hand consonant engine ([LAYOUT_PROBAHO.md](LAYOUT_PROBAHO.md)).
- **Avro Phonetic**: Classic muscle memory transliteration via `lekhani-parser`.
- **জাতীয় (National)**: Official BBS fixed standard with Shift/AltGr layers.
- **Probhat (प्रभात)**: Popular fixed layout with dead-key combinations.
- **Gboard Style**: Fixed Bengali layout mapping familiar to Gboard switchers.
- **English (QWERTY)**: Bilingual alphanumeric typing.

---

## 3. 📱 Android System Constraints & IME Quirks

Agents implementing the Android service layer must respect these specific platform behaviors:

1. **Direct Boot Readiness (`directBootAware="true"`)**:
   - `LekhaniInputMethodService` must declare `android:directBootAware="true"` in the manifest.
   - When the device is booted into locked state before user decryption, store preferences in Device Protected Storage (`createDeviceProtectedStorageContext()`).
2. **Password & Incognito Field Policy**:
   - When `InputType.TYPE_TEXT_VARIATION_PASSWORD` or `TYPE_TEXT_FLAG_NO_SUGGESTIONS` is encountered:
     - Automatically switch to English QWERTY.
     - Freeze `lekhani-ai` context updates, user dictionary learning, and clipboard capture.
3. **Landscape Non-Fullscreen Mode**:
   - Override `onEvaluateFullscreenMode()` to return `false` so the keyboard never covers the active app with full-screen extract UI.
4. **WebView & Chromium Composing Resilience**:
   - Chromium and social media comment inputs handle `setComposingText()` inconsistently. Maintain an internal pre-edit shadow buffer and verify cursor position before committing.
5. **No GC in Touch Loops**:
   - Pre-allocate all `Paint`, `RectF`, `Path`, and `Matrix` instances in `KeyboardCanvasView` during `init` / `onSizeChanged`.
   - Never allocate objects inside `onDraw()` or `onTouchEvent()`.

---

## 4. 🧪 Code Quality, Testing & Linting Standards

### Rust Code:
- Must adhere to Rust 2021 edition idioms.
- Must compile cleanly with zero warnings: `cargo clippy --all-targets -- -D warnings`.
- No `.unwrap()` or `.expect()` in the native FFI boundary—always return a structured `Result<T, LekhaniError>`.
- Write unit tests for all state transitions and parser outputs.

### Kotlin Code:
- Use Kotlin 2.0 with Jetpack Compose Material 3 Expressive guidelines.
- Use Kotlin Coroutines (`Dispatchers.Default` / `Dispatchers.IO`) for background dictionary indexing or audio processing.
- The UI thread and `onKey()` methods must remain non-blocking.
- Maintain WCAG 2.1 accessibility compliance (TalkBack announcements with phonetic Bengali pronunciations).

---

## 5. 📦 Git Workflow & Atomic Commit Policy

All agents and contributors must follow **Atomic Commits**:

1. **Single Logical Change**:
   - Each commit must represent a single, well-scoped change (e.g. implementing one layout, adding a specific UI component, or fixing a bug).
   - Never mix documentation rewrites, engine refactors, and UI changes in a single commit.
2. **Working State Guarantee**:
   - Every commit must compile, pass all linters, and leave the repository in a clean, functional state.
3. **Conventional Commits**:
   - Format: `<type>(<scope>): <short imperative description>`
   - Allowed Types:
     - `feat`: A new user-facing feature or layout capability.
     - `fix`: A bug fix or input correction.
     - `perf`: A code change that improves execution speed or eliminates allocations.
     - `refactor`: Code restructuring without changing external behavior.
     - `docs`: Documentation updates only (`README`, `ROADMAP`, `ARCHITECTURE`, etc.).
     - `test`: Adding or updating test suites.
     - `chore`: Build scripts, dependency updates, or CI configuration.
4. **Commit Body**:
   - Include a concise bulleted description of *what* was changed and *why*.

---

## 6. 🗺️ Roadmap & Documentation Sync

- Check [ROADMAP.md](ROADMAP.md) before starting any new phase or feature.
- When completing a milestone or adding a new architectural capability:
  - Update the corresponding task in [ROADMAP.md](ROADMAP.md).
  - Update [FEATURES.md](FEATURES.md) if user-facing specifications change.
  - Update [ARCHITECTURE.md](ARCHITECTURE.md) if system architecture or data flows evolve.
  - Keep [README.md](README.md) synchronized with the current project status.
