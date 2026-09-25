# Lekhani Android: Development Roadmap

## Phase 0: Foundations & Upstream Engines (COMPLETED)
- [x] Zero-allocation `lekhani-parser` crate (11 ns/char Avro transliteration).
- [x] Local N-gram predictor & language model crate (`lekhani-ai`).
- [x] Headless IME state machine crate (`lekhani-core`).
- [x] Crates published to crates.io (`v1.0.0`).

---

## Phase 1: Native Android UniFFI Bridge
- [x] Create `crates/lekhani-android` Rust crate.
- [x] UniFFI interface definition (`lekhani.udl` / proc-macros) — `uniffi::setup_scaffolding!()` + `uniffi.toml`.
- [x] Thread-safe mobile session lifecycle wrappers (`AndroidLekhaniSession`).
- [x] Surrounding text context extraction (`getTextBeforeCursor`) for in-place sentence re-scoring.
- [ ] Cross-compilation pipeline via `cargo-ndk` (`arm64-v8a`, `armeabi-v7a`, `x86_64`).
- [x] Auto-generate idiomatic Kotlin bindings — `uniffi.toml` configured for `com.lekhani.android.ffi`.
- [x] **CI/CD Pipeline (set once, runs forever)**:
  - GitHub Actions workflow: `cargo clippy --all-targets -- -D warnings`, `cargo test`, and `cargo build --release` on every push.
  - Android emulator smoke-test job (API 29 + API 34) triggered on every PR.
  - Fail-fast: no PR merges unless all linting and tests pass.
- [x] **Native Library Size Budget**: Define and enforce a `< 4 MB` per-ABI size cap for `lekhani-android.so` (all three ABIs combined `< 10 MB` stripped). Add a CI size-check step that fails the build if exceeded.
- [x] **N-gram / AI Model Format Decision**: Binary trie + `mmap` chosen. Documented in `docs/MODEL_FORMAT.md`. Validate cold-load time and RSS impact before Phase 4 begins.

---

## Phase 2: Android IME Scaffolding & System Compatibility
- [x] Android Studio project setup (Gradle 8.x, Kotlin 2.0) — `android/` module with `settings.gradle.kts`, `build.gradle.kts`, `libs.versions.toml`.
- [x] `LekhaniInputMethodService` implementation with resilient `InputConnection` lifecycle.
- [x] **Direct Boot Support (`directBootAware="true"`)**: Device Protected Storage prefs via `createDeviceProtectedStorageContext()`.
- [x] **Secure & Incognito Mode**:
  - Auto-switch to English QWERTY on password/PIN fields (`TYPE_TEXT_VARIATION_PASSWORD`).
  - Strict freeze on dictionary learning and clipboard logging via `session.setPrivateField(true)`.
- [x] **WebView & Chromium Compatibility**: Composing shadow buffer + `finishComposingText()` flush guard in `setComposingTextSafe()`.
- [x] **Fullscreen Mode Policy**: `onEvaluateFullscreenMode() -> false` — keyboard always overlays as a panel.
- [x] **Zero-Permission Audit**: `scripts/audit_permissions.sh` scans all manifests for `INTERNET`; added to CI pipeline.


---

## Phase 3: Hardware Canvas Touch Grid & Bengali Script Engine
- [x] Custom `KeyboardCanvasView` rendering at 120 FPS latency-free hardware draw loops (`LAYER_TYPE_HARDWARE`).
- [x] Multi-touch thumb tracking with Gaussian spatial key bounding boxes (weighted distance nearest-key lookup with row-Y bias).
- [x] Key touch-down ripple states (alpha-decay teal ripple overlay, 180 ms).
- [x] **Bengali Script Precision**:
  - Dedicated ZWJ (`\u200D`) and ZWNJ (`\u200C`) key access on Shift+ঁ and Shift+ঃ.
  - Conjunct-aware grapheme cluster backspace delegated to Rust engine (Phase 1).
  - Automatic Unicode NFC canonicalization on every commit (Phase 1).
- [x] **Core Layout Implementations**:
  - **Lekhani প্রবাহ (Flow)**: `ProbahLayout.kt` — full 3-row + spacebar row per LAYOUT_PROBAHO.md spec.
  - **Avro Phonetic**: `AvroPhoneticLayout` — QWERTY grid, transliteration in Rust engine.
  - **Fixed National (জাতীয়)**: `NationalLayout.kt` — BBS standard with Shift layers.
  - **Fixed Probhat / Gboard**: stub to National for Phase 3; full definitions next.
  - **English (QWERTY)**: `EnglishQwertyLayout.kt` — bilingual typing layer.
  - **LayoutRegistry**: maps `LekhaniLayoutType` → `KeyboardLayout`; `cycleLayout()` in IME.
  - 20 JVM unit tests in `ProbahLayoutTest.kt` covering spec compliance.


---

## Phase 4: Candidate Strip & Contextual AI Intelligence
- [x] Jetpack Compose horizontal candidate strip with fluid slide-in transitions (`CandidateStripView.kt`).
- [x] Center-pinned primary candidate selection committed instantly with Spacebar tap.
- [x] Contextual homophone disambiguation badges with preview (*পড়া* vs *পরা*, *খাব* vs *যাব*) via `HomophoneAnnotator`.
- [x] Real-time next-word continuations upon committing tokens.
- [x] Colloquial Bengali suffix peeling & grammar morphology.
- [x] **Candidate Blacklisting**: Long-press any candidate in the strip to remove accidental typos from memory (`CandidateBlacklist.kt`).
- [x] Unit test suite (`HomophoneAnnotatorTest.kt`) covering pair detection, reverse mappings, and primary selection.

---

## Phase 5: 100% Local / On-Device Voice Typing (Offline ASR)
> ⚠️ Moved before Emoji/Clipboard — ASR is a core differentiator with significant FFI, binary size, and latency risk that must be validated early rather than deferred to Phase 7.
- [ ] Embedded offline Bengali & English Speech-to-Text engine (`sherpa-onnx` / `vosk-android`).
- [ ] **Model Delivery — Zero-Network Compliant**: ASR model must be **bundled inside the APK** or sideloaded via a companion on-device asset pack. On-demand internet downloads are **strictly prohibited** (violates the Zero Network principle in AGENTS.md). Ultra-quantized model (`< 25 MB`) preferred for APK bundling.
- [ ] Android `AudioRecord` streaming pipeline with zero network calls.
- [ ] Voice Activity Detection (VAD) with 1.5s automatic silence auto-stop.
- [ ] Bengali punctuation auto-restoration (automatic `।`, `,`, `?`).
- [ ] Quick-access microphone button in toolbar and Spacebar long-press voice trigger.
- [ ] Visual audio waveform feedback overlay during voice transcription.

---

## Phase 6: Emoji, Kaomoji, Symbols & Clipboard Suite
- [ ] Full Unicode 15.1+ emoji palette with category tabs (Smileys, People, Nature, Food, Travel, Activities, Objects, Symbols, Flags).
- [ ] Instant bilingual search (Bengali e.g. "হাসি", "আগুন" + English keywords).
- [ ] Recents & favorites shelf with local persistence.
- [ ] Long-press skin-tone and gender modifiers.
- [ ] Kaomoji & emoticons picker (`(◕‿◕)`, `¯\_(ツ)_/¯`, `(ノಠ益ಠ)ノ彡┻━┻`).
- [ ] Specialized math, currency (`৳`, `$`, `€`, `¥`, `₹`), and Bengali typographical symbols.
- [ ] Smart local clipboard manager with clip pinning and auto-clearing sensitive content.

---

## Phase 7: Multi-Layout Switcher & Hardware Keyboard
- [ ] **Layout Management in Settings**:
  - Individual toggle switches to enable/disable each layout (Avro, National, Probhat, Gboard-style, English).
  - Drag-and-drop layout priority reordering.
  - Per-app or last-used layout memory.
- [ ] **Ergonomic Switching Controls**:
  - Dedicated Globe key (🌐) for cycling enabled layouts.
  - Horizontal swipe on Spacebar with visual layout indicator pill.
  - Long-press Spacebar for quick layout selection bottom sheet.
- [ ] **Physical / Bluetooth Keyboard Integration**:
  - Intercept physical USB/Bluetooth keyboard input on tablets & Android desktop (DeX).
  - Map physical typing directly to Avro Phonetic or National Bengali layout.

---

## Phase 8: Glide / Gesture Typing (Swipe-to-Type)
- [ ] Touch path vector capture (`ACTION_MOVE` continuous trajectory) on `KeyboardCanvasView`.
- [ ] Spatial trajectory decoder over key centroids for both Bengali and English layouts.
- [ ] Dynamic path trace visual effect with theme accent glow.

---

## Phase 9: Dictionary Management & User Data Freedom
- [ ] **One-Click Migration**: Import user dictionaries from Ridmik Keyboard and desktop Avro.
- [ ] **Offline Backup & Export**: Export personal learned words to human-readable JSON.
- [ ] **Personal Word Editor**: View, search, add, or delete learned words in Settings.
- [ ] 100% offline local encryption: Personal vocabulary never leaves device storage.

---

## Phase 10: Deep Customization & Theme Studio v2
- [ ] **Ergonomics & Sizing**:
  - Independent sliders for keyboard height, key margins, row spacing, and bottom chin padding.
  - Long-press delay slider (100 ms – 700 ms).
  - Key border and elevation drop-shadow toggles.
  - Bengali font selection (Kalpurush, SolaimanLipi, System default) and font size multiplier.
- [ ] **Haptics & Sound Profiles**:
  - Granular vibration duration and amplitude curve via Android `VibratorManager`.
  - Sound packs (Classic Keypress, Modern Bubble, Mechanical Click, Typewriter, Soft Woodblock, Mute).
- [ ] **Customizable Toolbar**:
  - Drag-and-drop to reorder/toggle quick tools (Emoji, Local Mic, Clipboard, Themes, One-Handed, Settings).
- [ ] **Theme Studio**:
  - Material You Dynamic Color extraction from wallpaper.
  - Deep OLED Pure Black mode.
  - Classic Avro Blue and Cyber Indigo presets.
  - Custom background image and gradient wallpaper support with adjustable opacity and blur.

---

## Phase 11: World-Class Modern UI/UX & Form Factors
- [ ] Material 3 Expressive aesthetic with fluid 120 FPS spring physics and key glow/ripple effects.
- [ ] One-handed mode with quick-dock arrows (left/right handed).
- [ ] Floating keyboard mode (freely movable, resizable anywhere on screen).
- [ ] Split keyboard mode optimized for foldables and tablets.
- [ ] Spacebar cursor slide navigation (fine-grained cursor tracking).
- [ ] Swipe-to-delete gesture on Backspace with word highlight preview.

---

## Phase 12: Onboarding Flow, Accessibility & Store Launch
- [ ] **Frictionless 3-Step Onboarding Wizard**:
  - Step 1: Enable Lekhani in Android System Settings (deep-linked directly).
  - Step 2: Set Lekhani as default keyboard (deep-linked directly).
  - Step 3: Interactive typing playground with live theme preview and layout intro.
- [ ] **TalkBack & Accessibility (WCAG 2.1)**:
  - Full screen reader accessibility nodes for all keys and candidates.
  - Phonetic Bengali letter readout (e.g., "ক" -> "Ka").
- [ ] Automated integration test suite on Android emulator matrix (API 24 to 34+).
- [ ] Latency profiling (< 3 ms keystroke-to-display, < 40 ms cold boot, < 35 MB RSS budget).
- [ ] F-Droid reproducible build recipe and metadata submission.
- [ ] Google Play Store listing with verified zero-data-collection privacy nutrition label.
