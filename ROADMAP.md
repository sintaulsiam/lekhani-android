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
- [x] Cross-compilation pipeline via `cargo-ndk` (`arm64-v8a`, `armeabi-v7a`, `x86_64`) with 16 KB page-size ELF alignment.
- [x] Auto-generate idiomatic Kotlin bindings — `uniffi.toml` configured for `com.lekhani.android.ffi`.
- [x] **CI/CD Pipeline (set once, runs forever)**:
  - GitHub Actions workflow: `cargo clippy --all-targets -- -D warnings`, `cargo test`, and `cargo build --release` on every push.
  - Android emulator smoke-test job (API 29 + API 34) triggered on every PR.
  - Fail-fast: no PR merges unless all linting and tests pass.
- [x] **Native Library Size Budget**: Define and enforce a `< 4 MB` per-ABI size cap for `lekhani-android.so` (all three ABIs combined `< 10 MB` stripped). Add a CI size-check step that fails the build if exceeded.
- [x] **N-gram / AI Model Format Decision**: Binary trie + `mmap` chosen. Documented in `docs/MODEL_FORMAT.md`. Validate cold-load time and RSS impact before Phase 4 begins.

---

## Phase 2: Android IME Scaffolding & System Compatibility
- [x] Modern Android build setup (AGP 9.2, Gradle 9.4.1, Kotlin 2.0, NDK r28) with automated `cargoBuild` integration and Android Studio Run button readiness.
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
  - **Lekhani প্রবাহ (Flow)**: `ProbahLayout.kt` — full 3-row + spacebar row per LAYOUT_PROBAHO.md spec with high-frequency `হ` (~3.6%) on unshifted Row 3, long-press hints (`গ` on `ম`, `ঘ` on `ল`), relocated `ৌ` (<0.05%) to Shift of `ো`, and direct punctuation (`?` on `,` and `।`).
  - **Avro Phonetic**: `AvroPhoneticLayout` — QWERTY grid, transliteration in Rust engine.
  - **Fixed National (জাতীয়)**: `NationalLayout.kt` — BBS standard with Shift layers and Khanda Ta (`ৎ`).
  - **Fixed Probhat (प्रभात)**: `ProbhatLayout.kt` — 10-key standard with dedicated Hasanta (`্`) and Chandra Bindu (`ঁ`).
  - **Gboard Style Bengali**: `GboardBengaliLayout.kt` — normalized 10-9-10 grid with `প` and `ফ`.
  - **English (QWERTY)**: `EnglishQwertyLayout.kt` — bilingual alphanumeric typing layer.
  - **LayoutRegistry**: maps `LekhaniLayoutType` → `KeyboardLayout`; `cycleLayout()` in IME.
  - 20 JVM unit tests in `ProbahLayoutTest.kt` covering spec compliance.
- [x] **World-Class Visual & Tactile Polish**:
  - Combining vowel signs mapped to canonical independent vowels to eradicate `◌` (`\u25CC`) dotted circles on keycaps.
  - Zero-allocation vector paths for Hasanta (`্`) and Chandra Bindu (`ঁ`) keycap rendering.
  - Material 3 Expressive candidate strip with pill chips and accent highlighting (no pipe dividers).
  - Hardware-accelerated floating keypress preview bubbles (`KeyPopup`) with elevation drop shadows.
  - 3D tactile keycap depth with elevated shadows and physical keycap depression on touch.
  - Custom crisp vector globe icon for layout switching.


---

## Phase 4: Candidate Strip & Contextual AI Intelligence
- [x] Jetpack Compose horizontal candidate strip with fluid slide-in transitions (`CandidateStripView.kt`).
- [x] Center-pinned primary candidate selection committed instantly with Spacebar tap.
- [x] Contextual homophone disambiguation badges with preview (*পড়া* vs *পরা*, *খাব* vs *যাব*) via `HomophoneAnnotator`.
- [x] Real-time next-word continuations upon committing tokens.
- [x] Colloquial Bengali suffix peeling & grammar morphology.
- [x] Candidate Blacklisting: Long-press any candidate in the strip to remove accidental typos from memory (`CandidateBlacklist.kt`).
- [x] **100% Offline English Suggestions & Autocorrect (Phase 4 Extension)**:
  - Compact ~60,000-word binary PrefixTrie (`data/dictionaries/english_dict.bin`, 0.97 MB) compiled into APK assets.
  - QWERTY proximity fat-finger correction and character transposition recovery in Rust.
  - Casing preservation (lowercase, Titlecase, UPPERCASE).
  - **Option B Conservative Spacebar**: Spacebar commits typed text verbatim without force-correcting; suggestions remain active in candidate strip.
  - Conversational next-word bigram predictive pairs.
  - Unit tests in `english.rs` and `session.rs` passing cleanly.
- [x] Unit test suite (`HomophoneAnnotatorTest.kt`) covering pair detection, reverse mappings, and primary selection.
- [x] **Dual-Speed Hybrid AI Architecture (v2.1 Supercharged Intelligence)**:
  - **Bivariate Gaussian Spatial Touch Correction** (`spatial.rs`): Precomputed inverse covariance matrices and log-determinants in `onSizeChanged` providing fat-finger boundary correction with zero allocations.
  - **Sentence-Boundary Context Reset**: Context truncation on punctuation (`।`, `?`, `!`, `\n`) preventing stale cross-sentence n-gram contamination.
  - **Zero-Allocation Hot-Vocab Personal Overlay**: 4096-slot FNV-1a direct hash table with Ebbinghaus exponential recency decay ($\lambda = 0.05$, $3\times$ boost in 24h).
  - **Revert-on-Backspace Signal**: Instant mistake penalty on backspace within 1500ms of spacebar commit.
  - **Asynchronous Micro-Neural Predictor Worker**: Background coroutine dispatch (`predictNextWords`) on Spacebar and typing pauses, preserving 120 FPS UI frame budget.
  - **Zero-Allocation Beam Search Arena**: Parent-pointer node arena eliminating inner-loop heap allocations during transliteration decoding.


---

## Phase 5: 100% Local / On-Device Voice Typing (Offline ASR)
> ⚠️ Moved before Emoji/Clipboard — ASR is a core differentiator with significant FFI, binary size, and latency risk that must be validated early rather than deferred to Phase 7.
- [x] Embedded offline Bengali & English Speech-to-Text engine (`OfflineAsrEngine` contract and `AsrAudioProcessor` native FFI).
- [x] **Model Delivery — Zero-Network Compliant**: ASR model structure defined for local APK asset loading or on-device storage. On-demand internet downloads strictly prohibited.
- [x] Android `AudioRecord` streaming pipeline with zero network calls (`AudioStreamingManager.kt`).
- [x] Voice Activity Detection (VAD) with 1.5s automatic silence auto-stop implemented in Rust FFI.
- [x] Bengali punctuation auto-restoration (automatic `।`, `,`, `?`) via native `restore_bengali_punctuation`.
- [x] Spacebar long-press voice trigger with haptic feedback on `KeyboardCanvasView`.
- [x] Visual audio waveform feedback overlay (`VoiceWaveformOverlay.kt`) with animated RMS bars during voice transcription.
- [x] Unit test suite in `audio.rs` (14 Rust tests passing) and `AudioStreamingTest.kt`.

---

## Phase 6: Emoji, Kaomoji, Symbols & Clipboard Suite
- [x] Full Unicode 15.1+ emoji palette with category tabs (Smileys, People, Nature, Food, Travel, Activities, Objects, Symbols, Flags) in `EmojiData.kt`.
- [x] **4-Row High-Density Emoji Redesign**:
  - Maximize vertical emoji viewport to 184 dp by reducing header chrome to slim 32 dp.
  - Expand visible grid to 4 full rows of 40 dp emoji cells (+137% density) without clipping.
  - On-demand search: search input field and bilingual tag chips toggle via `[ 🔍 ]` icon button.
  - Ergonomic 44 dp bottom navigation bar: `[ ⌨ ABC ]`, on-canvas search toggle, wide `[ ── Space ── ]`, and repeating hold `[ ⌫ ]`.
- [x] Instant bilingual live search (Bengali e.g. "হাসি", "আগুন" + English keywords) with clear button and quick recommendation chips (`🔥 আগুন`, `❤️ প্রেম`, `😂 হাসি`, etc.).
- [x] Recents & favorites shelf with local DPS persistence (`EmojiRecentsManager.kt`).
- [x] Long-press skin-tone and gender modifiers popup with globally persisted default skin tone applied across all grids and search results.
- [x] Kaomoji & emoticons picker (`(◕‿◕)`, `¯\_(ツ)_/¯`, `(ノಠ益ಠ)ノ彡┻━┻`) in `KaomojiData.kt`.
- [x] Specialized math, currency (`৳`, `$`, `€`, `¥`, `₹`), and Bengali typographical symbols in `SymbolData.kt`.
- [x] **High-Priority Clipboard Suite & Vault**:
  - Dedicated primary navigation tab in settings and quick-access Hero Card on Home tab.
  - Keyboard clipboard sheet with inline clip editor dialog, "+ New Clip" modal, and direct "Editor" jump button.
  - Zero-crash thread-safe storage with atomic monotonic ID generation, duplicate key protection in Compose `LazyColumn`, and auto-purging of sensitive passwords.
- [x] Unit test suites in `EmojiSearchTest.kt` and `ClipboardStoreTest.kt`.

---

## Phase 7: Multi-Layout Switcher & Hardware Keyboard
- [x] **Tier 1 Pure Rust Crates Integration**:
  - `lekhani-parser` (11 ns/char Avro Trie grammar engine) workspace integration.
  - `lekhani-ai` (on-device N-gram contextual scorer & homophone ranker) integration.
  - `lekhani-core` typing engine & layout databases integrated.
- [x] **Layout Management & Dedicated Layout Flow Screen**:
  - Curated default 4-layout pack: `[Probaho, English, Probhat, Avro]` with English startup default.
  - Spacebar layout switch mode with clean standard chevron cues (`‹   English   ›`).
  - Dedicated **Layout Flow** screen (`LayoutFlowScreen.kt`):
    - Forward-curved cylindrical 3D carousel (`HorizontalPager` + `graphicsLayer` projection with `rotationY`, scale, and alpha interpolation).
    - Top speech-bubble `Current layout` pill on the active card, flanking `<` and `>` chevrons, and pagination dots.
    - De-cluttered layout order list with relative directional offset badges (`Home`, `1 ▸`, `◂ 1`, `2 ▸`) matching the spatial horizontal loop mental model.
    - Reorder items with fluid spring animations, tactile haptic feedback, and one-tap `Reset to default`.
  - Individual toggle switches to enable/disable each layout (Probaho, Avro, National, Probhat, Gboard-style, English).
  - Minimum layout protection (at least one layout remains active).
  - Device Protected Storage persistence (`pref_enabled_layouts_order`).
- [x] **Ergonomic Switching Controls**:
  - Dedicated Globe key (🌐) for cycling enabled layouts.
  - Horizontal swipe on Spacebar with visual layout indicator pill and haptic feedback.
  - Dynamic Spacebar layout label (`স্পেস • প্রবাহ`, `Space • অভ্র`, `স্পেস • জাতীয়`, `স্পেস • প্রভাত`, `স্পেস • জিবোর্ড`, `Space • English`).
  - Long-press Spacebar for quick layout selection dialog.
- [x] **Number & Symbol Panels**:
  - `?123` primary layer: 1..0 row with Bengali numeral hints (`১..০`), currency symbols (`৳`, `$`), and common punctuation.
  - `=\<` more symbols layer: mathematical operators, brackets, currency (`৳`, `€`, `¥`, `£`, `₹`), and typographical glyphs.
  - Quick digit toggle (`১২৩` / `123`) for 1-tap switching between Bengali and English digits.
- [x] **Selection-Aware Backspace**:
  - Immediate atomic deletion of highlighted selections on Backspace without corrupting text or leaving ghost characters.
- [x] **Physical / Bluetooth Keyboard Integration**:
  - Intercept physical USB/Bluetooth keyboard input on tablets & Android desktop (DeX).
  - Map physical typing directly to Avro Phonetic (using `lekhani-parser`), National Bengali, or English layout.
  - Shift + Space keyboard shortcut to toggle layouts.

---

## Phase 8: Glide / Gesture Typing (Swipe-to-Type)
- [x] Touch path vector capture (`ACTION_MOVE` continuous trajectory) on `KeyboardCanvasView`.
- [x] Spatial trajectory decoder over key centroids for both Bengali and English layouts (`AndroidLekhaniSession.decode_glide`).
- [x] Dynamic path trace visual effect with theme accent glow and zero-allocation 120 FPS Bezier smoothing.

---

## Phase 9: Dictionary Management & User Data Freedom
- [x] **One-Click Migration**: Import user dictionaries from Ridmik Keyboard and desktop Avro.
- [x] **Offline Backup & Export**: Export personal learned words to human-readable JSON.
- [x] **Personal Word Editor**: View, search, add, or delete learned words in Settings.
- [x] 100% offline local encryption: Personal vocabulary never leaves device storage.

---

## Phase 10: Deep Customization & Theme Studio v2
- [x] **Ergonomics & Sizing**:
  - Independent sliders for keyboard height, key margins, row spacing, and bottom chin padding.
  - Long-press delay slider (100 ms – 700 ms).
  - Key border and elevation drop-shadow toggles.
  - Bengali font selection (Kalpurush, SolaimanLipi, System default) and font size multiplier.
- [x] **Haptics & Sound Profiles**:
  - Granular vibration duration and amplitude curve via Android `VibratorManager`.
  - Sound packs (Classic Keypress, Modern Bubble, Mechanical Click, Typewriter, Soft Woodblock, Mute).
- [x] **Tool Vault & Interactive Customization Drawer**:
  - Unimportant/secondary tools partitioned into an expandable **Tool Vault** drawer with single-tap launcher cards.
  - Dedicated Tool Vault icon on the keyboard toolbar (`GridView`).
  - Interactive Customize Mode allowing users to promote tools from vault to toolbar (`+`), demote tools back to vault (`-`), and reorder within toolbar and vault with direction controls.
  - Live StateFlow reactivity updating the candidate strip toolbar without keyboard restart.
- [x] **Theme Studio & Brand Identity**:
  - Official brand icon and launcher glyph updated to authentic Bengali 'লে' (Le) across all adaptive densities.
  - Decoupled App Theme Mode (`System Default`, `Force Light`, `Force Dark`, `Match Keyboard Theme`).
  - 11 Curated Keyboard Presets: System Dynamic (Material You), Light Clean, Dark Sleek, OLED Pure Black, Avro Blue, Cyber Indigo, Sakura Dusk, Forest Emerald, Nordic Frost, Sunset Amber, and Mocha Latte.
  - **Custom Theme Studio & Engine**:
    - Create custom themes from scratch or duplicate any existing preset / custom theme.
    - Full color palette customization: Background, Key Background, Text, Accent, Candidate Bar, and Key Borders.
    - Live interactive mini-keyboard preview canvas rendering real theme styles.
    - Built-in real-time WCAG 2.1 AA luminance contrast validation ratio checker (`ContrastChecker`).
    - Device Protected Storage JSON persistence (`CustomThemeManager.kt`).
  - Custom background image and gradient wallpaper support with adjustable opacity and blur.


---

## Phase 11: World-Class Modern UI/UX & Form Factors
- [x] Material 3 Expressive aesthetic with fluid 120 FPS spring physics and key glow/ripple effects.
- [x] Bilingual UI toggle (English | বাংলা) across all Settings tabs and sheets.
- [x] Developer & Organization showcase: Syntenium profile and BRUR CSE developer credentials.
- [x] One-handed mode with quick-dock arrows (left/right handed).
- [x] Floating keyboard mode (freely movable, resizable anywhere on screen).
- [x] Split keyboard mode optimized for foldables and tablets.
- [x] Mutually exclusive Spacebar gesture mode (`SpacebarSwipeMode`: Cursor Slide vs Layout Switch).
- [x] **Dedicated Text & Cursor Editor Sheet**:
  - Full 4-way D-Pad (Up, Down, Left, Right) with Home/End jumps for character and line navigation.
  - Text selection mode toggle for keyboard-driven range highlighting.
  - Quick Select All, Cut, Copy, Paste, Backspace, and Enter actions.
  - Seamless navigation fallback when spacebar is assigned to layout switching.
- [x] Persistent candidate strip chevron toggle between active word predictions and quick toolbar shortcuts.
- [x] Swipe-to-delete gesture on Backspace with word highlight preview.

---

## Phase 12: Onboarding Flow, Accessibility & Store Launch
- [x] **Frictionless 3-Step Onboarding Wizard**:
  - Step 1: Enable Lekhani in Android System Settings (deep-linked directly).
  - Step 2: Set Lekhani as default keyboard (deep-linked directly).
  - Step 3: Interactive typing playground with live theme preview and layout intro.
- [x] **TalkBack & Accessibility (WCAG 2.1)**:
  - Full screen reader accessibility nodes for all keys on custom hardware canvas via `KeyboardAccessibilityHelper` (`ExploreByTouchHelper`).
  - Phonetic Bengali letter readout (e.g., "ক", "আ-কার", "হসন্ত", "দাঁড়ি", "স্পেসবার").
- [ ] Automated integration test suite on Android emulator matrix (API 24 to 34+).
- [ ] Latency profiling (< 3 ms keystroke-to-display, < 40 ms cold boot, < 35 MB RSS budget).
- [ ] F-Droid reproducible build recipe and metadata submission.
- [ ] Google Play Store listing with verified zero-data-collection privacy nutrition label.
