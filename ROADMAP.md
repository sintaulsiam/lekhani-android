# Lekhani Android: Development Roadmap

## Phase 0: Foundations & Upstream Engines (COMPLETED)
- [x] Create standalone zero-allocation `lekhani-parser` crate.
- [x] Create standalone `lekhani-ai` language model and predictor crate.
- [x] Create standalone `lekhani-core` headless IME crate.
- [x] Publish crates to crates.io (`lekhani-parser v1.0.0`, `lekhani-ai v1.0.0`, `lekhani-core v1.0.0`).

---

## Phase 1: Native Android UniFFI Bridge Crate
- [ ] Create `crates/lekhani-android` Rust crate.
- [ ] Define UniFFI interface definition (`lekhani.udl` or proc-macros).
- [ ] Wrap `lekhani_core::InputSession` with thread-safe mobile lifecycle methods.
- [ ] Set up cross-compilation with `cargo-ndk` targeting:
  - `arm64-v8a` (Modern phones)
  - `armeabi-v7a` (Older 32-bit phones)
  - `x86_64` (Android Emulators)
- [ ] Auto-generate Kotlin wrapper bindings.

---

## Phase 2: Android Project Scaffolding
- [ ] Initialize Android Studio project (`android/`) with Gradle 8.x and Kotlin 2.0.
- [ ] Implement `LekhaniInputMethodService` extending `android.inputmethodservice.InputMethodService`.
- [ ] Wire `InputConnection.setComposingText()` and `commitText()`.
- [ ] Integrate native `.so` shared libraries into `app/src/main/jniLibs`.

---

## Phase 3: Hardware Canvas Touch Grid View
- [ ] Implement custom `KeyboardCanvasView` for 120 FPS latency-free rendering.
- [ ] Multi-touch thumb tracking with spatial bounding boxes.
- [ ] Key popups and touch down animation states.
- [ ] Implement layout definitions (Avro QWERTY, National, Probhat, Number/Symbol).

---

## Phase 4: Candidate Strip & AI Integration
- [ ] Jetpack Compose horizontal candidate strip above keyboard.
- [ ] Center-pinned primary candidate selection with Spacebar tap.
- [ ] Homophone disambiguation badges with context preview.
- [ ] Next-word continuations upon committing tokens.

---

## Phase 5: Haptics, Themes, and Settings
- [ ] VibratorManager integration for tactile click feedback.
- [ ] Material 3 Theme Studio (Material You, OLED Black, Avro Blue).
- [ ] Spacebar cursor slide navigation.
- [ ] Swipe-to-delete gesture on Backspace.
- [ ] Settings screen (height adjustment, sound, vibration, layout default).

---

## Phase 6: Testing & App Store Publishing
- [ ] Automated integration tests on Android emulator matrix (API 24 to 34).
- [ ] F-Droid reproducible build recipe and metadata submission.
- [ ] Google Play Store listing and privacy declaration (0 data collected).
