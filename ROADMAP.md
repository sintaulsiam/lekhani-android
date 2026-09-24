# Lekhani Android: Development Roadmap

## Phase 0: Foundations & Upstream Engines (COMPLETED)
- [x] Zero-allocation `lekhani-parser` crate (11 ns/char Avro transliteration).
- [x] Local N-gram predictor & language model crate (`lekhani-ai`).
- [x] Headless IME state machine crate (`lekhani-core`).
- [x] Crates published to crates.io (`v1.0.0`).

---

## Phase 1: Native Android UniFFI Bridge
- [ ] Create `crates/lekhani-android` Rust crate.
- [ ] UniFFI interface definition (`lekhani.udl` / proc-macros).
- [ ] Thread-safe mobile session lifecycle wrappers (`AndroidLekhaniSession`).
- [ ] Surrounding text context extraction (`getTextBeforeCursor`) for in-place sentence re-scoring.
- [ ] Cross-compilation pipeline via `cargo-ndk` (`arm64-v8a`, `armeabi-v7a`, `x86_64`).
- [ ] Auto-generate idiomatic Kotlin bindings.

---

## Phase 2: Android IME Scaffolding & System Compatibility
- [ ] Android Studio project setup (Gradle 8.x, Kotlin 2.0).
- [ ] `LekhaniInputMethodService` implementation with resilient `InputConnection` lifecycle.
- [ ] **Direct Boot Support (`directBootAware="true"`)**: Ensure the keyboard works on lockscreen reboots before device decryption.
- [ ] **Secure & Incognito Mode**:
  - Auto-switch to English QWERTY on password/PIN fields (`TYPE_TEXT_VARIATION_PASSWORD`).
  - Strict freeze on dictionary learning and clipboard logging in private/incognito fields.
- [ ] **WebView & Chromium Compatibility**: Resilient composing text buffer to prevent ghost letters and cursor jitter.
- [ ] **Fullscreen Mode Policy**: Suppress legacy fullscreen extract UI in landscape (`onEvaluateFullscreenMode() -> false`).
- [ ] **Zero-Permission Audit**: Verify complete absence of `android.permission.INTERNET`.

---

## Phase 3: Hardware Canvas Touch Grid & Bengali Script Engine
- [ ] Custom `KeyboardCanvasView` rendering at 120 FPS latency-free hardware draw loops.
- [ ] Multi-touch thumb tracking with Gaussian spatial key bounding boxes.
- [ ] Key touch-down ripple states and spring popups.
- [ ] **Bengali Script Precision**:
  - Dedicated ZWJ (`\u200D`) and ZWNJ (`\u200C`) key access for clean Hasanta, Khanda-Ta (`ৎ`), and Ya-phala (`্য`).
  - Conjunct-aware grapheme cluster backspace (cleanly delete complex conjuncts like `ক্ষ`).
  - Automatic Unicode NFC canonicalization before text commitment.
- [ ] **Core Layout Implementations**:
  - **Avro Phonetic**: Dynamic phonetic transliteration engine.
  - **Fixed National (জাতীয়)**: Standard layout with Shift & AltGr states.
  - **Fixed Probhat (प्रभात)**: Popular layout with dead-key combinations.
  - **Fixed Gboard-style Layout**: Standard Google Gboard Bengali key mapping for effortless switching.
  - **English (QWERTY)**: Clean bilingual typing layer.
  - **Numbers & Typographic Symbols**: Bengali digits (`০-৯`), currency (`৳`), Dari (`।`, `॥`), and punctuation.

---

## Phase 4: Candidate Strip & Contextual AI Intelligence
- [ ] Jetpack Compose horizontal candidate strip with fluid slide-in transitions.
- [ ] Center-pinned primary candidate selection committed instantly with Spacebar tap.
- [ ] Contextual homophone disambiguation badges with preview (*পড়া* vs *পরা*, *খাব* vs *যাব*).
- [ ] Real-time next-word continuations upon committing tokens.
- [ ] Colloquial Bengali suffix peeling & grammar morphology.
- [ ] **Candidate Blacklisting**: Long-press any candidate in the strip to remove accidental typos from memory.

---

## Phase 5: Emoji, Kaomoji, Symbols & Clipboard Suite
- [ ] Full Unicode 15.1+ emoji palette with category tabs (Smileys, People, Nature, Food, Travel, Activities, Objects, Symbols, Flags).
- [ ] Instant bilingual search (Bengali e.g. "হাসি", "আগুন" + English keywords).
- [ ] Recents & favorites shelf with local persistence.
- [ ] Long-press skin-tone and gender modifiers.
- [ ] Kaomoji & emoticons picker (`(◕‿◕)`, `¯\_(ツ)_/¯`, `(ノಠ益ಠ)ノ彡┻━┻`).
- [ ] Specialized math, currency (`৳`, `$`, `€`, `¥`, `₹`), and Bengali typographical symbols.
- [ ] Smart local clipboard manager with clip pinning and auto-clearing sensitive content.

---

## Phase 6: Multi-Layout Switcher & Hardware Keyboard
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

## Phase 7: 100% Local / On-Device Voice Typing (Offline ASR)
- [ ] Embedded offline Bengali & English Speech-to-Text engine (`sherpa-onnx` / `vosk-android`).
- [ ] **Lightweight Model Delivery Strategy**:
  - Ultra-quantized model option (< 25 MB) or one-time on-demand model asset download.
- [ ] Android `AudioRecord` streaming pipeline with zero network calls.
- [ ] Voice Activity Detection (VAD) with 1.5s automatic silence auto-stop.
- [ ] Bengali punctuation auto-restoration (automatic `।`, `,`, `?`).
- [ ] Quick-access microphone button in toolbar and Spacebar long-press voice trigger.
- [ ] Visual audio waveform feedback overlay during voice transcription.

---

## Phase 8: Dictionary Management & User Data Freedom
- [ ] **One-Click Migration**: Import user dictionaries from Ridmik Keyboard and desktop Avro.
- [ ] **Offline Backup & Export**: Export personal learned words to human-readable JSON.
- [ ] **Personal Word Editor**: View, search, add, or delete learned words in Settings.
- [ ] 100% offline local encryption: Personal vocabulary never leaves device storage.

---

## Phase 9: Glide / Gesture Typing (Swipe-to-Type)
- [ ] Touch path vector capture (`ACTION_MOVE` continuous trajectory) on `KeyboardCanvasView`.
- [ ] Spatial trajectory decoder over key centroids for both Bengali and English layouts.
- [ ] Dynamic path trace visual effect with theme accent glow.

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
- [ ] **Frictionless 2-Step Onboarding Wizard**:
  - Step 1: Enable Lekhani in Android System Settings.
  - Step 2: Set Lekhani as default keyboard.
  - Step 3: Interactive instant typing playground with theme preview.
- [ ] **TalkBack & Accessibility (WCAG 2.1)**:
  - Full screen reader accessibility nodes for all keys and candidates.
  - Phonetic Bengali letter readout (e.g., "ক" -> "Ka").
- [ ] Automated integration test suite on Android emulator matrix (API 24 to 34+).
- [ ] Latency profiling (< 3 ms keystroke-to-display, < 40 ms cold boot, < 35 MB RSS budget).
- [ ] F-Droid reproducible build recipe and metadata submission.
- [ ] Google Play Store listing with verified zero-data-collection privacy nutrition label.
